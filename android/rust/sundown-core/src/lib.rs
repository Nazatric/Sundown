//! Sundown native core.
//!
//! Kotlin owns Android UI, storage, SAF and playback; this crate provides
//! bounded metadata parsing, artwork processing and library helper routines.

uniffi::setup_scaffolding!();

pub mod artwork;
pub mod index;
pub mod metadata;
pub mod model;
pub mod text;

use model::{ArtPreviews, Fingerprint, LibraryIndex, ParsedFile, ScanDiff, TrackLite};

/// Reads tags and embedded art.
///
/// `head` is the first slice of the file, `tail` the last ~256 KB (used for
/// ID3v1 trailers and MP4 files whose `moov` atom is written last). Passing an
/// empty `tail` is fine and simply skips those fallbacks.
#[uniffi::export]
pub fn parse_tags(head: Vec<u8>, tail: Vec<u8>, fallback_title: String) -> ParsedFile {
    metadata::parse(&head, &tail, &fallback_title)
}

/// Decodes embedded art and returns up to 1024 px + 160 px square JPEG previews.
#[uniffi::export]
pub fn make_art_previews(bytes: Vec<u8>) -> Option<ArtPreviews> {
    artwork::previews(&bytes)
}

#[uniffi::export]
pub fn build_index(tracks: Vec<TrackLite>) -> LibraryIndex {
    index::build_index(&tracks)
}

#[uniffi::export]
pub fn search_tracks(tracks: Vec<TrackLite>, query: String) -> Vec<String> {
    index::search_tracks(&tracks, &query)
}

#[uniffi::export]
pub fn sort_track_ids(tracks: Vec<TrackLite>) -> Vec<String> {
    index::sort_track_ids(&tracks)
}

#[uniffi::export]
pub fn diff_scan(existing: Vec<Fingerprint>, found: Vec<Fingerprint>) -> ScanDiff {
    index::diff(&existing, &found)
}

#[uniffi::export]
pub fn album_key_of(album_artist: String, artist: String, album: String) -> String {
    index::album_key(&album_artist, &artist, &album)
}

#[uniffi::export]
pub fn artist_key_of(album_artist: String, artist: String) -> String {
    index::artist_key(&album_artist, &artist)
}

#[uniffi::export]
pub fn sort_name_of(name: String) -> String {
    index::sort_name(&name)
}

#[uniffi::export]
pub fn blake3_key(value: String) -> String {
    blake3::hash(value.as_bytes()).to_hex().to_string()
}

/// Hashes raw artwork bytes without expanding them into a temporary text encoding.
#[uniffi::export]
pub fn blake3_bytes_key(bytes: Vec<u8>) -> String {
    blake3::hash(&bytes).to_hex().to_string()
}

#[uniffi::export]
pub fn jump_index(keys: Vec<String>, letter: String) -> Option<u32> {
    index::jump_index(&keys, &letter)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn album_keys_fold_case_and_fall_back_to_artist() {
        assert_eq!(album_key_of("".into(), "Kings of Leon".into(), "Come Around Sundown".into()),
                   "kings of leon :: come around sundown");
        assert_eq!(album_key_of("Various".into(), "Someone".into(), "".into()),
                   "various :: unknown album");
    }

    #[test]
    fn sort_name_drops_leading_the() {
        assert_eq!(sort_name_of("The Killers".into()), "killers");
        assert_eq!(sort_name_of("Theory".into()), "theory");
    }

    #[test]
    fn blake3_artwork_key_hashes_bytes_directly() {
        assert_eq!(
            blake3_bytes_key(b"abc".to_vec()),
            "6437b3ac38465133ffb63b75273a8db548c558465d79db03fd359c6cd5bd9d85",
        );
    }

    #[test]
    fn diff_detects_added_changed_and_removed() {
        let existing = vec![
            Fingerprint { id: "a".into(), path: "a.mp3".into(), size: 10, mtime: 1 },
            Fingerprint { id: "b".into(), path: "b.mp3".into(), size: 20, mtime: 2 },
        ];
        let found = vec![
            Fingerprint { id: "a".into(), path: "a.mp3".into(), size: 10, mtime: 1 },
            Fingerprint { id: "c".into(), path: "c.mp3".into(), size: 30, mtime: 3 },
        ];
        let result = diff_scan(existing, found);
        assert_eq!(result.unchanged, 1);
        assert_eq!(result.to_parse.len(), 1);
        assert_eq!(result.removed_ids, vec!["b".to_string()]);
    }
}
