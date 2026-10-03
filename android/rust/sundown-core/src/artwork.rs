//! Embedded cover art -> two high-quality square JPEG previews.
//! Center-crops directly from the embedded source, caps the grid preview at 1024 px
//! and the compact row preview at 160 px, and avoids upscaling smaller sources.

use crate::model::ArtPreviews;
use image::codecs::jpeg::JpegEncoder;
use image::imageops::FilterType;
use image::{DynamicImage, GenericImageView, ImageReader, Limits};

const LARGE: u32 = 1024;
const SMALL: u32 = 160;
const QUALITY: u8 = 92;
const MAX_SOURCE_DIMENSION: u32 = 8192;
const MAX_SOURCE_PIXELS: u64 = 16_000_000;
const MAX_DECODE_BYTES: u64 = 128 * 1024 * 1024;
const JPEG_MATTE: [u8; 3] = [0xC5, 0xC4, 0xBD];

fn center_square(source: &DynamicImage) -> Option<DynamicImage> {
    let (width, height) = source.dimensions();
    let side = width.min(height);
    if side == 0 {
        return None;
    }
    Some(source.crop_imm((width - side) / 2, (height - side) / 2, side, side))
}

fn encode(image: &DynamicImage, target: u32, filter: FilterType) -> Option<Vec<u8>> {
    let side = target.min(image.width()).min(image.height()).max(1);
    let resized = image.resize_exact(side, side, filter);
    let rgba = resized.to_rgba8();
    let rgb = image::RgbImage::from_fn(side, side, |x, y| {
        let pixel = rgba.get_pixel(x, y);
        let alpha = pixel[3] as u32;
        let channels = std::array::from_fn(|channel| {
            let foreground = pixel[channel] as u32 * alpha;
            let background = JPEG_MATTE[channel] as u32 * (255 - alpha);
            ((foreground + background + 127) / 255) as u8
        });
        image::Rgb(channels)
    });
    let mut out = Vec::with_capacity((side * side / 4) as usize);
    let mut encoder = JpegEncoder::new_with_quality(&mut out, QUALITY);
    encoder
        .encode(rgb.as_raw(), side, side, image::ExtendedColorType::Rgb8)
        .ok()?;
    Some(out)
}

pub fn previews(bytes: &[u8]) -> Option<ArtPreviews> {
    if bytes.is_empty() {
        return None;
    }
    let reader = ImageReader::new(std::io::Cursor::new(bytes)).with_guessed_format().ok()?;
    let format = reader.format()?;
    let (width, height) = reader.into_dimensions().ok()?;
    if width > MAX_SOURCE_DIMENSION
        || height > MAX_SOURCE_DIMENSION
        || (width as u64).saturating_mul(height as u64) > MAX_SOURCE_PIXELS
    {
        return None;
    }

    let mut limits = Limits::default();
    limits.max_image_width = Some(MAX_SOURCE_DIMENSION);
    limits.max_image_height = Some(MAX_SOURCE_DIMENSION);
    limits.max_alloc = Some(MAX_DECODE_BYTES);
    let mut reader = ImageReader::with_format(std::io::Cursor::new(bytes), format);
    reader.limits(limits);
    let decoded = reader.decode().ok()?;
    let square = center_square(&decoded)?;
    // Lanczos for the grid preview; triangle remains crisp and inexpensive at 160 px.
    let large = encode(&square, LARGE, FilterType::Lanczos3)?;
    let small = encode(&square, SMALL, FilterType::Triangle)?;
    Some(ArtPreviews { large, small })
}

#[cfg(test)]
mod tests {
    use super::*;
    use image::{GenericImageView, ImageFormat, Rgb, RgbImage, Rgba, RgbaImage};
    use std::io::Cursor;

    fn encoded_test_image(width: u32, height: u32) -> Vec<u8> {
        let image = DynamicImage::ImageRgb8(RgbImage::from_fn(width, height, |x, y| {
            Rgb([(x % 251) as u8, (y % 251) as u8, ((x + y) % 251) as u8])
        }));
        let mut output = Cursor::new(Vec::new());
        image.write_to(&mut output, ImageFormat::Png).unwrap();
        output.into_inner()
    }

    #[test]
    fn previews_are_square_high_quality_and_do_not_upscale() {
        let generated = previews(&encoded_test_image(1_400, 1_200)).unwrap();
        let large = image::load_from_memory(&generated.large).unwrap();
        let small = image::load_from_memory(&generated.small).unwrap();
        assert_eq!(large.dimensions(), (LARGE, LARGE));
        assert_eq!(small.dimensions(), (SMALL, SMALL));

        let small_source = previews(&encoded_test_image(80, 50)).unwrap();
        let large = image::load_from_memory(&small_source.large).unwrap();
        let small = image::load_from_memory(&small_source.small).unwrap();
        assert_eq!(large.dimensions(), (50, 50));
        assert_eq!(small.dimensions(), (50, 50));
    }

    #[test]
    fn composites_transparency_on_the_original_sleeve_fill() {
        let image = DynamicImage::ImageRgba8(RgbaImage::from_pixel(16, 16, Rgba([255, 0, 255, 0])));
        let mut source = Cursor::new(Vec::new());
        image.write_to(&mut source, ImageFormat::Png).unwrap();

        let generated = previews(source.get_ref()).unwrap();
        let jpeg = image::load_from_memory(&generated.large).unwrap().to_rgb8();
        let pixel = jpeg.get_pixel(8, 8);
        for channel in 0..3 {
            assert!(pixel[channel].abs_diff(JPEG_MATTE[channel]) <= 3);
        }
    }

    #[test]
    fn rejects_empty_or_unreadable_artwork() {
        assert!(previews(&[]).is_none());
        assert!(previews(b"not an image").is_none());
    }
}
