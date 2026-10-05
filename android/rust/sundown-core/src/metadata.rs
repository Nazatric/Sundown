//! Bounded, container-aware tag readers for offline local audio: ID3, MP4/M4A,
//! FLAC, OGG, and RIFF/WAV.

use crate::model::{ParsedFile, Tags};
use crate::text::{clean_genre, decode_id3, first_number, latin1};
use base64::Engine;

pub struct Picture {
    pub data: Vec<u8>,
    pub mime: String,
}

fn u32be(b: &[u8], o: usize) -> u32 {
    if o + 4 > b.len() {
        return 0;
    }
    u32::from_be_bytes([b[o], b[o + 1], b[o + 2], b[o + 3]])
}

fn u32le(b: &[u8], o: usize) -> u32 {
    if o + 4 > b.len() {
        return 0;
    }
    u32::from_le_bytes([b[o], b[o + 1], b[o + 2], b[o + 3]])
}

fn u24be(b: &[u8], o: usize) -> u32 {
    if o + 3 > b.len() {
        return 0;
    }
    ((b[o] as u32) << 16) | ((b[o + 1] as u32) << 8) | b[o + 2] as u32
}

fn syncsafe(b: &[u8], o: usize) -> u32 {
    if o + 4 > b.len() {
        return 0;
    }
    ((b[o] as u32 & 0x7F) << 21)
        | ((b[o + 1] as u32 & 0x7F) << 14)
        | ((b[o + 2] as u32 & 0x7F) << 7)
        | (b[o + 3] as u32 & 0x7F)
}

fn fourcc(b: &[u8], o: usize) -> String {
    if o + 4 > b.len() {
        return String::new();
    }
    latin1(&b[o..o + 4])
}

#[derive(PartialEq, Debug)]
enum Container {
    Mp3,
    Mp4,
    Flac,
    Ogg,
    Wav,
    Unknown,
}

fn sniff(b: &[u8]) -> Container {
    if b.len() < 12 {
        return Container::Unknown;
    }
    if &b[0..3] == b"ID3" {
        return Container::Mp3;
    }
    if &b[0..4] == b"fLaC" {
        return Container::Flac;
    }
    if &b[0..4] == b"OggS" {
        return Container::Ogg;
    }
    if &b[0..4] == b"RIFF" && &b[8..12] == b"WAVE" {
        return Container::Wav;
    }
    if &b[4..8] == b"ftyp" {
        return Container::Mp4;
    }
    if b[0] == 0xFF && (b[1] & 0xE0) == 0xE0 {
        return Container::Mp3;
    }
    Container::Unknown
}

// ---------------------------------------------------------------- ID3v2 ----

fn parse_id3v2(b: &[u8], tags: &mut Tags) -> Option<Picture> {
    if b.len() < 10 || &b[0..3] != b"ID3" {
        return None;
    }
    let version = b[3];
    if !(2..=4).contains(&version) {
        return None;
    }
    let flags = b[5];
    // ID3v2.2 compression is not supported by this bounded slice parser.
    if version == 2 && flags & 0x40 != 0 {
        return None;
    }
    let size = syncsafe(b, 6) as usize;
    let end = (10 + size).min(b.len());
    let mut body: Vec<u8> = b[10..end].to_vec();

    // Extended headers are present in v2.3/v2.4 only.
    if version >= 3 && flags & 0x40 != 0 && body.len() > 4 {
        let ext = if version == 4 {
            syncsafe(&body, 0) as usize
        } else {
            u32be(&body, 0) as usize + 4
        };
        if ext > 0 && ext <= body.len() {
            body.drain(0..ext);
        }
    }
    // Undo tag-level unsynchronisation before walking frames.
    if flags & 0x80 != 0 {
        let mut out = Vec::with_capacity(body.len());
        let mut i = 0;
        while i < body.len() {
            out.push(body[i]);
            if body[i] == 0xFF && i + 1 < body.len() && body[i + 1] == 0x00 {
                i += 1;
            }
            i += 1;
        }
        body = out;
    }

    let header_size = if version == 2 { 6 } else { 10 };
    let mut picture = None;
    let mut off = 0usize;
    while off + header_size <= body.len() {
        if body[off] == 0 {
            break; // padding
        }
        let id = if version == 2 {
            latin1(&body[off..off + 3])
        } else {
            fourcc(&body, off)
        };
        let frame_size = if version == 2 {
            u24be(&body, off + 3) as usize
        } else if version == 3 {
            u32be(&body, off + 4) as usize
        } else {
            syncsafe(&body, off + 4) as usize
        };
        let Some(frame_end) = off
            .checked_add(header_size)
            .and_then(|start| start.checked_add(frame_size))
            .filter(|end| frame_size > 0 && *end <= body.len())
        else {
            break;
        };
        let frame = &body[off + header_size..frame_end];
        if frame.is_empty() {
            break;
        }
        let enc = frame[0];
        let text = || decode_id3(enc, &frame[1..]);
        match id.as_str() {
            "TIT2" | "TT2" => tags.title = text(),
            "TPE1" | "TP1" => tags.artist = text(),
            "TPE2" | "TP2" => tags.album_artist = text(),
            "TALB" | "TAL" => tags.album = text(),
            "TRCK" | "TRK" => tags.track_no = first_number(&text()),
            "TPOS" | "TPA" => tags.disc_no = first_number(&text()),
            "TYER" | "TYE" | "TDRC" => tags.year = first_number(&text()),
            "TCON" | "TCO" => tags.genre = clean_genre(&text()),
            "TXXX" | "TXX" => {
                if let Some((description, value)) = parse_user_text(frame) {
                    if matches!(description.trim().to_ascii_uppercase().as_str(), "ALBUMARTIST" | "ALBUM ARTIST")
                        && tags.album_artist.is_empty()
                    {
                        tags.album_artist = value;
                    }
                }
            }
            "APIC" if picture.is_none() => picture = parse_apic(frame),
            "PIC" if picture.is_none() => picture = parse_pic(frame),
            _ => {}
        }
        off += header_size + frame_size;
    }
    picture
}

fn parse_user_text(frame: &[u8]) -> Option<(String, String)> {
    let encoding = *frame.first()?;
    let bytes = &frame[1..];
    let separator = if encoding == 1 || encoding == 2 {
        bytes
            .chunks_exact(2)
            .position(|unit| unit[0] == 0 && unit[1] == 0)
            .map(|unit| unit * 2)?
    } else {
        bytes.iter().position(|byte| *byte == 0)?
    };
    let separator_size = if encoding == 1 || encoding == 2 { 2 } else { 1 };
    let description = decode_id3(encoding, &bytes[..separator]);
    let value_start = separator.checked_add(separator_size)?;
    if value_start > bytes.len() {
        return None;
    }
    Some((description, decode_id3(encoding, &bytes[value_start..])))
}

fn picture_description_end(frame: &[u8], encoding: u8, mut offset: usize) -> Option<usize> {
    if encoding == 1 || encoding == 2 {
        while offset + 1 < frame.len() && !(frame[offset] == 0 && frame[offset + 1] == 0) {
            offset += 2;
        }
        if offset + 1 >= frame.len() {
            return None;
        }
        Some(offset + 2)
    } else {
        while offset < frame.len() && frame[offset] != 0 {
            offset += 1;
        }
        if offset >= frame.len() {
            return None;
        }
        Some(offset + 1)
    }
}

fn parse_apic(frame: &[u8]) -> Option<Picture> {
    if frame.len() < 4 {
        return None;
    }
    let encoding = frame[0];
    let mut offset = 1usize;
    let mime_start = offset;
    while offset < frame.len() && frame[offset] != 0 {
        offset += 1;
    }
    if offset >= frame.len() {
        return None;
    }
    let mime = latin1(&frame[mime_start..offset]);
    offset += 1; // MIME terminator
    if offset >= frame.len() {
        return None;
    }
    offset += 1; // picture type
    let data_offset = picture_description_end(frame, encoding, offset)?;
    Some(Picture {
        data: frame[data_offset..].to_vec(),
        mime: if mime.is_empty() { "image/jpeg".into() } else { mime },
    })
}

/// ID3v2.2 PIC stores a three-byte image format instead of a MIME string.
fn parse_pic(frame: &[u8]) -> Option<Picture> {
    if frame.len() < 6 {
        return None;
    }
    let encoding = frame[0];
    let format = latin1(&frame[1..4]).to_ascii_uppercase();
    let mime = match format.as_str() {
        "PNG" => "image/png",
        "JPG" => "image/jpeg",
        _ => "image/jpeg",
    };
    let data_offset = picture_description_end(frame, encoding, 5)?; // format, type, description
    Some(Picture {
        data: frame[data_offset..].to_vec(),
        mime: mime.into(),
    })
}

fn parse_id3v1(b: &[u8], tags: &mut Tags) {
    if b.len() < 128 {
        return;
    }
    let o = b.len() - 128;
    if &b[o..o + 3] != b"TAG" {
        return;
    }
    let field = |from: usize, to: usize| latin1(&b[o + from..o + to]).trim_end_matches('\u{0}').trim().to_string();
    if tags.title.is_empty() {
        tags.title = field(3, 33);
    }
    if tags.artist.is_empty() {
        tags.artist = field(33, 63);
    }
    if tags.album.is_empty() {
        tags.album = field(63, 93);
    }
    if tags.year == 0 {
        tags.year = first_number(&field(93, 97));
    }
    if tags.genre.is_empty() {
        if let Some(name) = crate::text::id3v1_genre(b[o + 127]) {
            tags.genre = name.to_string();
        }
    }
}

// ------------------------------------------------------------------ MP4 ----

fn parse_mp4(b: &[u8], tags: &mut Tags) -> Option<Picture> {
    let mut picture = None;
    scan_atoms(b, 0, b.len(), "", tags, &mut picture, 0);
    picture
}

fn scan_atoms(
    b: &[u8],
    mut off: usize,
    end: usize,
    path: &str,
    tags: &mut Tags,
    picture: &mut Option<Picture>,
    depth: u32,
) {
    if depth > 8 {
        return;
    }
    while off + 8 <= end {
        let mut size = u32be(b, off) as usize;
        let kind = fourcc(b, off + 4);
        let mut head = 8usize;
        if size == 1 {
            // 64-bit size: high word is almost always zero for tag atoms.
            size = u32be(b, off + 12) as usize;
            head = 16;
        } else if size == 0 {
            size = end - off;
        }
        if size < head || off + size > end {
            break;
        }
        let inner = off + head;
        let inner_end = off + size;
        match kind.as_str() {
            "moov" | "udta" | "ilst" | "trak" | "mdia" => {
                let next = if path.is_empty() { kind.clone() } else { format!("{}.{}", path, kind) };
                scan_atoms(b, inner, inner_end, &next, tags, picture, depth + 1);
            }
            "meta" => {
                // `meta` carries a 4-byte version/flags prefix before children.
                let next = if path.is_empty() { kind.clone() } else { format!("{}.{}", path, kind) };
                scan_atoms(b, inner + 4, inner_end, &next, tags, picture, depth + 1);
            }
            _ if path.ends_with("ilst") => {
                if let Some(data) = find_data_atom(b, inner, inner_end) {
                    apply_mp4_atom(&kind, data, tags, picture);
                }
            }
            _ => {}
        }
        off += size;
    }
}

fn parse_ilst_contents(
    b: &[u8],
    mut off: usize,
    end: usize,
    tags: &mut Tags,
    picture: &mut Option<Picture>,
) -> bool {
    let mut found = false;
    while off + 8 <= end {
        let size = u32be(b, off) as usize;
        let kind = fourcc(b, off + 4);
        if size < 8 || off + size > end {
            break;
        }
        if let Some(data) = find_data_atom(b, off + 8, off + size) {
            apply_mp4_atom(&kind, data, tags, picture);
            found = true;
        }
        off += size;
    }
    found
}

/// A tail slice may begin in the middle of `moov`/`udta`; the parent atom
/// headers are then outside the slice. Search for a complete, size-validated
/// `ilst` atom and parse its child metadata atoms directly.
fn parse_mp4_tail(b: &[u8], tags: &mut Tags) -> Option<Picture> {
    let mut local = Tags::default();
    let mut picture = parse_mp4(b, &mut local);
    let mut found = !local.title.is_empty()
        || !local.artist.is_empty()
        || !local.album.is_empty()
        || !local.album_artist.is_empty()
        || !local.genre.is_empty();

    if b.len() >= 8 {
        for off in 0..=b.len() - 8 {
            if &b[off + 4..off + 8] != b"ilst" {
                continue;
            }
            let size = u32be(b, off) as usize;
            if size < 8 || off + size > b.len() {
                continue;
            }
            found |= parse_ilst_contents(b, off + 8, off + size, &mut local, &mut picture);
        }
    }

    if found {
        merge_missing(tags, local);
    }
    picture
}

fn merge_missing(target: &mut Tags, source: Tags) {
    if target.title.is_empty() { target.title = source.title; }
    if target.artist.is_empty() { target.artist = source.artist; }
    if target.album.is_empty() { target.album = source.album; }
    if target.album_artist.is_empty() { target.album_artist = source.album_artist; }
    if target.genre.is_empty() { target.genre = source.genre; }
    if target.track_no == 0 { target.track_no = source.track_no; }
    if target.disc_no == 0 { target.disc_no = source.disc_no; }
    if target.year == 0 { target.year = source.year; }
}

fn find_data_atom(b: &[u8], mut off: usize, end: usize) -> Option<&[u8]> {
    while off + 8 <= end {
        let size = u32be(b, off) as usize;
        let kind = fourcc(b, off + 4);
        if size < 8 || off + size > end {
            return None;
        }
        if kind == "data" && off + 16 <= off + size {
            return Some(&b[off + 16..off + size]);
        }
        off += size;
    }
    None
}

fn apply_mp4_atom(kind: &str, data: &[u8], tags: &mut Tags, picture: &mut Option<Picture>) {
    let text = || String::from_utf8_lossy(data).trim_end_matches('\u{0}').trim().to_string();
    // The iTunes atoms begin with the 0xA9 copyright sign.
    match kind {
        "\u{a9}nam" => tags.title = text(),
        "\u{a9}ART" => tags.artist = text(),
        "aART" => tags.album_artist = text(),
        "\u{a9}alb" => tags.album = text(),
        "\u{a9}gen" | "gnre" => tags.genre = clean_genre(&text()),
        "\u{a9}day" => tags.year = first_number(&text()),
        "trkn" if data.len() >= 4 => tags.track_no = u16::from_be_bytes([data[2], data[3]]) as u32,
        "disk" if data.len() >= 4 => tags.disc_no = u16::from_be_bytes([data[2], data[3]]) as u32,
        "covr" if picture.is_none() => {
            let mime = if data.starts_with(&[0x89, 0x50]) { "image/png" } else { "image/jpeg" };
            *picture = Some(Picture { data: data.to_vec(), mime: mime.into() });
        }
        _ => {}
    }
}

// ----------------------------------------------------------- Vorbis/FLAC ----

fn apply_vorbis_comments(b: &[u8], mut off: usize, end: usize, tags: &mut Tags, picture: &mut Option<Picture>) {
    if off + 4 > end {
        return;
    }
    let vendor_len = u32le(b, off) as usize;
    off += 4 + vendor_len;
    if off + 4 > end {
        return;
    }
    let count = u32le(b, off) as usize;
    off += 4;
    for _ in 0..count.min(512) {
        if off + 4 > end {
            break;
        }
        let len = u32le(b, off) as usize;
        off += 4;
        if off + len > end {
            break;
        }
        let pair = String::from_utf8_lossy(&b[off..off + len]).into_owned();
        off += len;
        let Some(eq) = pair.find('=') else { continue };
        let key = pair[..eq].to_ascii_uppercase();
        let value = pair[eq + 1..].trim().to_string();
        match key.as_str() {
            "TITLE" => tags.title = value,
            "ARTIST" => tags.artist = value,
            "ALBUM" => tags.album = value,
            "ALBUMARTIST" | "ALBUM ARTIST" => tags.album_artist = value,
            "GENRE" => tags.genre = value,
            "TRACKNUMBER" => tags.track_no = first_number(&value),
            "DISCNUMBER" => tags.disc_no = first_number(&value),
            "DATE" | "YEAR" => tags.year = first_number(&value),
            "METADATA_BLOCK_PICTURE" if picture.is_none() => {
                if let Ok(raw) = base64::engine::general_purpose::STANDARD.decode(value.as_bytes()) {
                    *picture = parse_flac_picture(&raw, 0, raw.len());
                }
            }
            _ => {}
        }
    }
}

fn parse_flac_picture(b: &[u8], off: usize, end: usize) -> Option<Picture> {
    if off + 32 > end {
        return None;
    }
    let mut p = off + 4; // picture type
    let mime_len = u32be(b, p) as usize;
    p += 4;
    if p + mime_len > end {
        return None;
    }
    let mime = latin1(&b[p..p + mime_len]);
    p += mime_len;
    let desc_len = u32be(b, p) as usize;
    p += 4 + desc_len + 16; // description + w/h/depth/colors
    if p + 4 > end {
        return None;
    }
    let data_len = u32be(b, p) as usize;
    p += 4;
    if p + data_len > end {
        return None;
    }
    Some(Picture {
        data: b[p..p + data_len].to_vec(),
        mime: if mime.is_empty() { "image/jpeg".into() } else { mime },
    })
}

fn parse_flac(b: &[u8], tags: &mut Tags) -> Option<Picture> {
    let mut picture = None;
    let mut off = 4usize;
    while off + 4 <= b.len() {
        let header = b[off];
        let last = header & 0x80 != 0;
        let block_type = header & 0x7F;
        let len = u24be(b, off + 1) as usize;
        let body = off + 4;
        if body + len > b.len() {
            break;
        }
        match block_type {
            4 => apply_vorbis_comments(b, body, body + len, tags, &mut picture),
            6 if picture.is_none() => picture = parse_flac_picture(b, body, body + len),
            _ => {}
        }
        off = body + len;
        if last {
            break;
        }
    }
    picture
}

fn parse_ogg(b: &[u8], tags: &mut Tags) -> Option<Picture> {
    let mut picture = None;
    // Comment header packet starts with 0x03 "vorbis" (or "OpusTags").
    let limit = b.len().min(512 * 1024);
    let mut found = None;
    for i in 0..limit.saturating_sub(7) {
        if b[i] == 0x03 && &b[i + 1..i + 7] == b"vorbis" {
            found = Some(i + 7);
            break;
        }
        if &b[i..i + 8.min(limit - i)] == b"OpusTags" {
            found = Some(i + 8);
            break;
        }
    }
    if let Some(start) = found {
        apply_vorbis_comments(b, start, b.len(), tags, &mut picture);
    }
    picture
}

// ------------------------------------------------------------------ WAV ----

fn parse_wav(b: &[u8], tags: &mut Tags) -> Option<Picture> {
    let mut picture = None;
    let mut off = 12usize;
    while off + 8 <= b.len() {
        let id = fourcc(b, off);
        let size = u32le(b, off + 4) as usize;
        let body = off + 8;
        if body + size > b.len() {
            break;
        }
        if id == "LIST" && fourcc(b, body) == "INFO" {
            let mut p = body + 4;
            let end = body + size;
            while p + 8 <= end {
                let cid = fourcc(b, p);
                let csz = u32le(b, p + 4) as usize;
                if p + 8 + csz > end {
                    break;
                }
                let value = latin1(&b[p + 8..p + 8 + csz]).trim_end_matches('\u{0}').trim().to_string();
                match cid.as_str() {
                    "INAM" => tags.title = value,
                    "IART" => tags.artist = value,
                    "IPRD" => tags.album = value,
                    "ICRD" | "IYER" => tags.year = first_number(&value),
                    "IGNR" => tags.genre = clean_genre(&value),
                    "ITRK" => tags.track_no = first_number(&value),
                    _ => {}
                }
                p += 8 + csz + (csz & 1);
            }
        } else if id == "id3 " || id == "ID3 " {
            // Some encoders embed a full ID3v2 chunk inside WAV.
            if picture.is_none() {
                picture = parse_id3v2(&b[body..body + size], tags);
            }
        }
        off = body + size + (size & 1);
    }
    picture
}

// --------------------------------------------------------------- public ----

/// Parses tags from the head of a file, falling back to the tail for MP4 files
/// whose `moov` atom is written last, and for ID3v1 trailers.
pub fn parse(head: &[u8], tail: &[u8], fallback_title: &str) -> ParsedFile {
    let mut tags = Tags::default();
    let container = sniff(head);
    let mut picture = match container {
        Container::Mp3 => {
            let p = parse_id3v2(head, &mut tags);
            if !tail.is_empty() {
                parse_id3v1(tail, &mut tags);
            }
            p
        }
        Container::Flac => parse_flac(head, &mut tags),
        Container::Ogg => parse_ogg(head, &mut tags),
        Container::Wav => parse_wav(head, &mut tags),
        Container::Mp4 => {
            let head_picture = parse_mp4(head, &mut tags);
            if tail.is_empty() {
                head_picture
            } else {
                let mut tail_tags = Tags::default();
                let tail_picture = parse_mp4_tail(tail, &mut tail_tags);
                merge_missing(&mut tags, tail_tags);
                head_picture.or(tail_picture)
            }
        }
        Container::Unknown => None,
    };

    if picture.as_ref().map(|p| p.data.is_empty()).unwrap_or(false) {
        picture = None;
    }
    if tags.title.trim().is_empty() {
        tags.title = fallback_title.to_string();
    }

    let (data, mime) = match picture {
        Some(p) => (Some(p.data), p.mime),
        None => (None, String::new()),
    };
    ParsedFile { tags, picture: data, picture_mime: mime }
}


#[cfg(test)]
mod tests {
    use super::*;

    fn atom(kind: &[u8; 4], body: &[u8]) -> Vec<u8> {
        let mut bytes = Vec::with_capacity(body.len() + 8);
        bytes.extend_from_slice(&((body.len() + 8) as u32).to_be_bytes());
        bytes.extend_from_slice(kind);
        bytes.extend_from_slice(body);
        bytes
    }

    fn mp4_text_item(kind: &[u8; 4], text: &str) -> Vec<u8> {
        let mut data_body = vec![0, 0, 0, 1, 0, 0, 0, 0]; // type, flags, and locale
        data_body.extend_from_slice(text.as_bytes());
        atom(kind, &atom(b"data", &data_body))
    }

    #[test]
    fn parses_mp4_metadata_when_tail_starts_at_ilst_inside_moov() {
        let mut head = vec![0, 0, 0, 12];
        head.extend_from_slice(b"ftypM4A ");
        let mut items = mp4_text_item(b"\xa9nam", "Tail Title");
        items.extend(mp4_text_item(b"\xa9ART", "Tail Artist"));
        let tail = atom(b"ilst", &items);

        let parsed = parse(&head, &tail, "fallback");
        assert_eq!(parsed.tags.title, "Tail Title");
        assert_eq!(parsed.tags.artist, "Tail Artist");
    }

    #[test]
    fn head_mp4_tags_take_precedence_over_tail_fallback_values() {
        let mut head = vec![0, 0, 0, 12];
        head.extend_from_slice(b"ftypM4A ");
        let mut tail_items = mp4_text_item(b"\xa9nam", "tail title");
        tail_items.extend(mp4_text_item(b"\xa9alb", "tail album"));
        let tail = atom(b"ilst", &tail_items);
        let mut local_tags = Tags::default();
        local_tags.title = "head title".into();
        let mut tags = local_tags;
        let _ = parse_mp4_tail(&tail, &mut tags);
        assert_eq!(tags.title, "head title");
        assert_eq!(tags.album, "tail album");
    }

    fn id3v2_tag(version: u8, body: &[u8]) -> Vec<u8> {
        let size = body.len();
        let mut bytes = b"ID3".to_vec();
        bytes.extend_from_slice(&[version, 0, 0]);
        bytes.extend_from_slice(&[
            ((size >> 21) & 0x7F) as u8,
            ((size >> 14) & 0x7F) as u8,
            ((size >> 7) & 0x7F) as u8,
            (size & 0x7F) as u8,
        ]);
        bytes.extend_from_slice(body);
        bytes
    }

    #[test]
    fn parses_id3v22_three_character_text_frames() {
        let text = b"\0Older title";
        let mut frame = b"TT2".to_vec();
        frame.extend_from_slice(&[0, 0, text.len() as u8]);
        frame.extend_from_slice(text);

        let parsed = parse(&id3v2_tag(2, &frame), &[], "fallback");
        assert_eq!(parsed.tags.title, "Older title");
    }

    #[test]
    fn parses_id3v22_pic_frames() {
        let image = [0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A];
        let mut payload = vec![0];
        payload.extend_from_slice(b"PNG");
        payload.push(3); // front cover
        payload.push(0); // empty description
        payload.extend_from_slice(&image);
        let mut frame = b"PIC".to_vec();
        frame.extend_from_slice(&[
            ((payload.len() >> 16) & 0xFF) as u8,
            ((payload.len() >> 8) & 0xFF) as u8,
            (payload.len() & 0xFF) as u8,
        ]);
        frame.extend_from_slice(&payload);

        let parsed = parse(&id3v2_tag(2, &frame), &[], "fallback");
        assert_eq!(parsed.picture, Some(image.to_vec()));
        assert_eq!(parsed.picture_mime, "image/png");
    }

    #[test]
    fn parses_album_artist_from_id3_user_text() {
        let payload = b"\0ALBUM ARTIST\0Kings of Leon";
        let mut frame = b"TXX".to_vec();
        frame.extend_from_slice(&[0, 0, payload.len() as u8]);
        frame.extend_from_slice(payload);

        let parsed = parse(&id3v2_tag(2, &frame), &[], "fallback");
        assert_eq!(parsed.tags.album_artist, "Kings of Leon");
    }
}
