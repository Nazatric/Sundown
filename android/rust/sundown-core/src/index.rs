//! Library grouping, sorting, search and incremental-scan diffing.
//! Direct port of the `useMemo` pipelines in the web `App.tsx` so ordering and
//! grouping rules stay byte-for-byte identical.

use crate::model::{AlbumGroup, ArtistGroup, Fingerprint, LibraryIndex, ScanDiff, TrackLite};
use std::collections::{HashMap, HashSet};

const SEP: &str = " :: ";

pub fn album_key(album_artist: &str, artist: &str, album: &str) -> String {
    let who = if album_artist.trim().is_empty() { artist } else { album_artist };
    let what = if album.trim().is_empty() { "Unknown Album" } else { album };
    format!("{}{}{}", who.trim().to_lowercase(), SEP, what.trim().to_lowercase())
}

pub fn artist_key(album_artist: &str, artist: &str) -> String {
    let who = if album_artist.trim().is_empty() { artist } else { album_artist };
    let key = who.trim().to_lowercase();
    if key.is_empty() { "unknown artist".to_string() } else { key }
}

/// "The Killers" sorts under K, matching the web `sortName`.
pub fn sort_name(name: &str) -> String {
    let lower = name.trim().to_lowercase();
    lower.strip_prefix("the ").map(|s| s.to_string()).unwrap_or(lower)
}

pub fn build_index(tracks: &[TrackLite]) -> LibraryIndex {
    let mut albums: HashMap<String, AlbumGroup> = HashMap::new();
    let mut order: Vec<String> = Vec::new();

    for track in tracks {
        let key = album_key(&track.album_artist, &track.artist, &track.album);
        let entry = albums.entry(key.clone()).or_insert_with(|| {
            order.push(key.clone());
            AlbumGroup {
                key: key.clone(),
                title: if track.album.trim().is_empty() { "Unknown Album".into() } else { track.album.clone() },
                artist: if !track.album_artist.trim().is_empty() {
                    track.album_artist.clone()
                } else if !track.artist.trim().is_empty() {
                    track.artist.clone()
                } else {
                    "Unknown Artist".into()
                },
                artist_key: artist_key(&track.album_artist, &track.artist),
                year: track.year,
                genre: track.genre.clone(),
                art_id: None,
                track_ids: Vec::new(),
            }
        });
        if entry.art_id.is_none() {
            entry.art_id = track.art_id.clone();
        }
        if entry.year == 0 {
            entry.year = track.year;
        }
        if entry.genre.is_empty() {
            entry.genre = track.genre.clone();
        }
        entry.track_ids.push(track.id.clone());
    }

    // Disc, then track, then title — same comparator as the web album sheet.
    let by_id: HashMap<&str, &TrackLite> = tracks.iter().map(|t| (t.id.as_str(), t)).collect();
    for album in albums.values_mut() {
        album.track_ids.sort_by(|a, b| {
            let ta = by_id.get(a.as_str());
            let tb = by_id.get(b.as_str());
            match (ta, tb) {
                (Some(x), Some(y)) => x
                    .disc_no
                    .cmp(&y.disc_no)
                    .then(x.track_no.cmp(&y.track_no))
                    .then_with(|| x.title.to_lowercase().cmp(&y.title.to_lowercase())),
                _ => std::cmp::Ordering::Equal,
            }
        });
    }

    let mut album_list: Vec<AlbumGroup> = order
        .iter()
        .filter_map(|k| albums.get(k).cloned())
        .collect();
    album_list.sort_by(|a, b| {
        sort_name(&a.artist)
            .cmp(&sort_name(&b.artist))
            .then_with(|| a.title.to_lowercase().cmp(&b.title.to_lowercase()))
    });

    // Artists roll up from albums, newest release first inside each artist.
    let mut artists: HashMap<String, ArtistGroup> = HashMap::new();
    let mut artist_order: Vec<String> = Vec::new();
    for album in &album_list {
        let entry = artists.entry(album.artist_key.clone()).or_insert_with(|| {
            artist_order.push(album.artist_key.clone());
            ArtistGroup {
                key: album.artist_key.clone(),
                name: album.artist.clone(),
                album_count: 0,
                track_count: 0,
                art_id: None,
                rear_art_id: None,
                album_keys: Vec::new(),
            }
        });
        entry.album_count += 1;
        entry.track_count += album.track_ids.len() as u32;
        entry.album_keys.push(album.key.clone());
        if entry.art_id.is_none() {
            entry.art_id = album.art_id.clone();
        } else if entry.rear_art_id.is_none() {
            entry.rear_art_id = album.art_id.clone();
        }
    }
    let mut artist_list: Vec<ArtistGroup> = artist_order
        .iter()
        .filter_map(|k| artists.get(k).cloned())
        .collect();
    artist_list.sort_by(|a, b| sort_name(&a.name).cmp(&sort_name(&b.name)));

    let mut genres: Vec<String> = tracks
        .iter()
        .map(|t| t.genre.trim().to_string())
        .filter(|g| !g.is_empty())
        .collect::<HashSet<_>>()
        .into_iter()
        .collect();
    genres.sort_by_key(|g| g.to_lowercase());

    LibraryIndex { albums: album_list, artists: artist_list, genres }
}

/// Case-insensitive match across title + artist + album, as the web search does.
pub fn search_tracks(tracks: &[TrackLite], query: &str) -> Vec<String> {
    let needle = query.trim().to_lowercase();
    if needle.is_empty() {
        return tracks.iter().map(|t| t.id.clone()).collect();
    }
    tracks
        .iter()
        .filter(|t| {
            format!("{} {} {}", t.title, t.artist, t.album)
                .to_lowercase()
                .contains(&needle)
        })
        .map(|t| t.id.clone())
        .collect()
}

/// Songs ordering used by the Songs tab: artist, album, disc, track.
pub fn sort_track_ids(tracks: &[TrackLite]) -> Vec<String> {
    let mut list: Vec<&TrackLite> = tracks.iter().collect();
    list.sort_by(|a, b| {
        sort_name(&a.artist)
            .cmp(&sort_name(&b.artist))
            .then_with(|| a.album.to_lowercase().cmp(&b.album.to_lowercase()))
            .then(a.disc_no.cmp(&b.disc_no))
            .then(a.track_no.cmp(&b.track_no))
            .then_with(|| a.title.to_lowercase().cmp(&b.title.to_lowercase()))
    });
    list.into_iter().map(|t| t.id.clone()).collect()
}

/// Compares what is on disk with what is indexed. Only new or changed files
/// are returned for parsing; everything else reuses its cached row.
pub fn diff(existing: &[Fingerprint], found: &[Fingerprint]) -> ScanDiff {
    let known: HashMap<&str, &Fingerprint> = existing.iter().map(|f| (f.id.as_str(), f)).collect();
    let mut to_parse = Vec::new();
    let mut unchanged = 0u32;
    let mut seen: HashSet<&str> = HashSet::new();

    for file in found {
        seen.insert(file.id.as_str());
        match known.get(file.id.as_str()) {
            Some(prev) if prev.size == file.size && prev.mtime == file.mtime => unchanged += 1,
            _ => to_parse.push(file.clone()),
        }
    }
    let removed_ids = existing
        .iter()
        .filter(|f| !seen.contains(f.id.as_str()))
        .map(|f| f.id.clone())
        .collect();

    ScanDiff { to_parse, removed_ids, unchanged }
}

/// First index whose sort key starts with `letter` ('#' = non-alphabetic).
pub fn jump_index(keys: &[String], letter: &str) -> Option<u32> {
    let letter = letter.to_lowercase();
    keys.iter().position(|k| {
        let first = k.trim_start().chars().next().unwrap_or(' ').to_ascii_lowercase();
        if letter == "#" {
            !first.is_ascii_alphabetic()
        } else {
            first.to_string() == letter
        }
    }).map(|i| i as u32)
}
