//! Text decoding helpers shared by the tag parsers.

pub fn latin1(bytes: &[u8]) -> String {
    bytes.iter().map(|&b| b as char).collect::<String>()
}

pub fn utf16(bytes: &[u8], big_endian: bool) -> String {
    let units: Vec<u16> = bytes
        .chunks_exact(2)
        .map(|c| {
            if big_endian {
                u16::from_be_bytes([c[0], c[1]])
            } else {
                u16::from_le_bytes([c[0], c[1]])
            }
        })
        .collect();
    String::from_utf16_lossy(&units)
}

/// ID3v2 text-encoding byte: 0 = Latin-1, 1 = UTF-16 + BOM, 2 = UTF-16BE, 3 = UTF-8.
pub fn decode_id3(encoding: u8, bytes: &[u8]) -> String {
    if bytes.is_empty() {
        return String::new();
    }
    let decoded = match encoding {
        0 => latin1(bytes),
        1 => {
            if bytes.len() >= 2 && bytes[0] == 0xFF && bytes[1] == 0xFE {
                utf16(&bytes[2..], false)
            } else if bytes.len() >= 2 && bytes[0] == 0xFE && bytes[1] == 0xFF {
                utf16(&bytes[2..], true)
            } else {
                utf16(bytes, false)
            }
        }
        2 => utf16(bytes, true),
        _ => String::from_utf8_lossy(bytes).into_owned(),
    };
    decoded.trim_end_matches('\u{0}').trim().to_string()
}

pub fn first_number(value: &str) -> u32 {
    let digits: String = value
        .chars()
        .skip_while(|c| !c.is_ascii_digit())
        .take_while(|c| c.is_ascii_digit())
        .collect();
    digits.parse().unwrap_or(0)
}

/// ID3v1 numeric genres, kept because plenty of older MP3s only carry these.
pub fn id3v1_genre(index: u8) -> Option<&'static str> {
    const NAMES: [&str; 26] = [
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz",
        "Metal", "New Age", "Oldies", "Other", "Pop", "Rhythm and Blues", "Rap", "Reggae", "Rock",
        "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack",
        "Euro-Techno",
    ];
    NAMES.get(index as usize).copied()
}

/// Strips the `(17)` style wrapper and maps bare numbers to their name.
pub fn clean_genre(value: &str) -> String {
    let trimmed = value.trim().trim_end_matches('\u{0}').trim();
    let inner = trimmed
        .strip_prefix('(')
        .and_then(|rest| rest.split(')').next())
        .unwrap_or(trimmed);
    if let Ok(index) = inner.parse::<u8>() {
        if let Some(name) = id3v1_genre(index) {
            return name.to_string();
        }
    }
    trimmed.to_string()
}
