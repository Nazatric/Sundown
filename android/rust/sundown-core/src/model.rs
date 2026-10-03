//! Shared records crossing the UniFFI boundary.
//! Field names mirror the web app's `TrackRec` so the port stays 1:1.

#[derive(Debug, Clone, Default, uniffi::Record)]
pub struct Tags {
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub genre: String,
    pub track_no: u32,
    pub disc_no: u32,
    pub year: u32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct ParsedFile {
    pub tags: Tags,
    /// Raw embedded cover bytes exactly as stored in the file, if any.
    pub picture: Option<Vec<u8>>,
    pub picture_mime: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct ArtPreviews {
    /// Up to 1024 px square JPEG (grid / detail / media session).
    pub large: Vec<u8>,
    /// Up to 160 px square JPEG (song rows).
    pub small: Vec<u8>,
}

/// Minimal track shape needed for grouping and search.
#[derive(Debug, Clone, uniffi::Record)]
pub struct TrackLite {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub genre: String,
    pub track_no: u32,
    pub disc_no: u32,
    pub year: u32,
    pub duration: u32,
    pub art_id: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct AlbumGroup {
    pub key: String,
    pub title: String,
    pub artist: String,
    pub artist_key: String,
    pub year: u32,
    pub genre: String,
    pub art_id: Option<String>,
    pub track_ids: Vec<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct ArtistGroup {
    pub key: String,
    pub name: String,
    pub album_count: u32,
    pub track_count: u32,
    pub art_id: Option<String>,
    pub rear_art_id: Option<String>,
    pub album_keys: Vec<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct LibraryIndex {
    pub albums: Vec<AlbumGroup>,
    pub artists: Vec<ArtistGroup>,
    pub genres: Vec<String>,
}

/// `(path, size, mtime)` identity used for incremental rescans.
#[derive(Debug, Clone, uniffi::Record)]
pub struct Fingerprint {
    pub id: String,
    pub path: String,
    pub size: u64,
    pub mtime: i64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct ScanDiff {
    /// Paths that must be parsed (new or changed).
    pub to_parse: Vec<Fingerprint>,
    /// Ids present in the database but gone from storage.
    pub removed_ids: Vec<String>,
    pub unchanged: u32,
}
