use lofty::{Accessor, AudioFile, Probe, TaggedFileExt};
use serde::{Deserialize, Serialize};
use std::io::Cursor;

#[derive(Serialize, Deserialize, uniffi::Record)]
pub struct TrackMetadata {
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub genre: String,
    pub year: i32,
    pub track_no: i32,
    pub disc_no: i32,
    pub duration_ms: i32,
    pub has_art: bool,
}

#[derive(uniffi::Record)]
pub struct MetadataResult {
    pub metadata: TrackMetadata,
    pub picture: Option<Vec<u8>>,
}

#[uniffi::export]
pub fn parse_metadata(bytes: Vec<u8>, fallback_title: String) -> Option<MetadataResult> {
    let mut cursor = Cursor::new(bytes);
    let probe = Probe::new(&mut cursor).guess_file_type().ok()?;
    let tagged_file = probe.read().ok()?;
    
    let properties = tagged_file.properties();
    let duration = properties.duration().as_millis() as i32;
    
    let tag = tagged_file.primary_tag().or_else(|| tagged_file.first_tag())?;
    
    let picture = tag.pictures().first().map(|p| p.data().to_vec());
    
    Some(MetadataResult {
        metadata: TrackMetadata {
            title: tag.title().map(|s| s.to_string()).unwrap_or(fallback_title),
            artist: tag.artist().map(|s| s.to_string()).unwrap_or_else(|| "Unknown Artist".to_string()),
            album: tag.album().map(|s| s.to_string()).unwrap_or_else(|| "Unknown Album".to_string()),
            album_artist: tag.get_string(&lofty::ItemKey::AlbumArtist).map(|s| s.to_string()).unwrap_or_else(|| "".to_string()),
            genre: tag.genre().map(|s| s.to_string()).unwrap_or_else(|| "".to_string()),
            year: tag.year().map(|y| y as i32).unwrap_or(0),
            track_no: tag.track().map(|n| n as i32).unwrap_or(0),
            disc_no: tag.disk().map(|n| n as i32).unwrap_or(0),
            duration_ms: duration,
            has_art: picture.is_some(),
        },
        picture,
    })
}

#[uniffi::export]
pub fn calculate_hash(bytes: Vec<u8>) -> String {
    blake3::hash(&bytes).to_hex().to_string()
}
