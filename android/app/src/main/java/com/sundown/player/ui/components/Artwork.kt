package com.sundown.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sundown.player.ui.theme.D
import com.sundown.player.ui.theme.G
import com.sundown.player.ui.theme.P
import com.sundown.player.ui.theme.cssShadow
import com.sundown.player.ui.theme.groundShadow

/** Supplies decoded high-resolution grid and compact row previews from the Rust core. */
interface ArtworkLoader {
    /** Non-blocking memory-cache lookup so prefetched covers can render on the first frame. */
    fun cached(artId: String, small: Boolean): ImageBitmap? = null

    suspend fun load(artId: String, small: Boolean): ImageBitmap?
}

val LocalArtworkLoader = staticCompositionLocalOf<ArtworkLoader?> { null }

/**
 * The "no embedded art" sleeve, drawn natively instead of shipping an asset so
 * it stays crisp at any density. Geometry copies sleeve-placeholder.svg.
 */
@Composable
fun SleevePlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFF89949D), Color(0xFF3E4A54)),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        val c = Offset(size.width / 2f, size.height / 2f)
        val unit = size.minDimension / 300f
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to Color(0xFF56616B), 0.30f to Color(0xFF232B32),
                0.75f to Color(0xFF333D45), 1.00f to Color(0xFF192128),
                center = c, radius = 115f * unit,
            ),
            radius = 115f * unit, center = c,
        )
        listOf(106f, 96f, 86f, 76f, 66f).forEach { r ->
            drawCircle(Color(0xFF7B8790).copy(alpha = 0.16f), r * unit, c, style = Stroke(1f))
        }
        drawCircle(Color(0xFFBAC5CB), 34f * unit, c)
        drawCircle(Color(0xFF28343E), 5f * unit, c)
    }
}

@Composable
fun ArtworkImage(
    artId: String?,
    small: Boolean,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
) {
    val loader = LocalArtworkLoader.current
    val initialBitmap = remember(artId, small, loader) {
        artId?.let { loader?.cached(it, small) }
    }
    var bitmap by remember(artId, small, loader) { mutableStateOf(initialBitmap) }
    val imageAlpha = remember(artId, small, loader) { Animatable(if (initialBitmap != null) 1f else 0f) }

    LaunchedEffect(artId, small, loader) {
        bitmap = if (artId != null && loader != null) {
            loader.cached(artId, small) ?: loader.load(artId, small)
        } else {
            null
        }
    }
    LaunchedEffect(bitmap, initialBitmap) {
        if (bitmap != null && initialBitmap == null) {
            imageAlpha.animateTo(1f, tween(durationMillis = 140))
        }
    }

    val image = bitmap
    val fade = imageAlpha.value
    Box(modifier) {
        if (image == null || fade < 1f) {
            SleevePlaceholder(Modifier.matchParentSize())
        }
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.matchParentSize().graphicsLayer { this.alpha = fade },
                // Square crop keeps every cover the same visual proportion even
                // when the embedded art is 3:2 or 1500x1000.
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                alpha = alpha,
            )
        }
    }
}

private fun toneFilter(brightness: Float, saturation: Float): ColorFilter {
    val matrix = ColorMatrix().apply { setToSaturation(saturation) }
    matrix.timesAssign(
        ColorMatrix(
            floatArrayOf(
                brightness, 0f, 0f, 0f, 0f,
                0f, brightness, 0f, 0f, 0f,
                0f, 0f, brightness, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    return ColorFilter.colorMatrix(matrix)
}

@Composable
private fun Sleeve(
    rotation: Float,
    dx: Dp,
    dy: Dp,
    modifier: Modifier = Modifier,
    borderColor: Color = P.SleeveEdge,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationZ = rotation
                translationX = dx.toPx()
                translationY = dy.toPx()
                // CSS: transform-origin: 50% 55%
                transformOrigin = TransformOrigin(0.5f, 0.55f)
            }
            .cssShadow(Color(0x9E0E1419), blur = 3.dp, offsetY = 1.dp, cornerRadius = D.sleeveRadius)
            .clip(RoundedCornerShape(D.sleeveRadius))
            .background(P.SleeveFill)
            .border(1.dp, borderColor, RoundedCornerShape(D.sleeveRadius)),
        content = content,
    )
}

/**
 * The five-layer album stack. This is the app's signature element, so the
 * rotations, offsets and per-layer tone adjustments are copied exactly:
 *
 *   paper  +4.0deg  ( 1,-3)  paper gradient, no art
 *   rear   -4.0deg  (-1,-2)  alpha .65  saturate .45  brightness 1.2
 *   left   -3.7deg  (-3, 3)  brightness .79  saturate .60
 *   right  +3.0deg  ( 3, 3)  brightness .90  saturate .75
 *   front   0       ( 0, 0)  full art + hairline inner highlight
 */
@Composable
fun AlbumStack(
    artId: String?,
    rearArtId: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val rearTone = remember { toneFilter(1.2f, 0.45f) }
    val leftTone = remember { toneFilter(0.79f, 0.6f) }
    val rightTone = remember { toneFilter(0.9f, 0.75f) }
    Box(modifier.size(size)) {
        Spacer(
            Modifier.matchParentSize().groundShadow(
                insetLeft = D.groundShadowInsetL,
                insetRight = D.groundShadowInsetR,
                bottom = D.groundShadowBottom,
                height = D.groundShadowHeight,
                color = Color(0x6E0C151D),
            ),
        )
        Sleeve(4f, 1.dp, (-3).dp, borderColor = P.PaperEdge) {
            Box(Modifier.fillMaxSize().background(G.paper))
        }
        Sleeve(-4f, (-1).dp, (-2).dp) {
            ArtworkImage(rearArtId ?: artId, small, Modifier.fillMaxSize(), rearTone, alpha = 0.65f)
        }
        Sleeve(-3.7f, (-3).dp, 3.dp) {
            ArtworkImage(rearArtId ?: artId, small, Modifier.fillMaxSize(), leftTone)
        }
        Sleeve(3f, 3.dp, 3.dp) {
            ArtworkImage(artId, small, Modifier.fillMaxSize(), rightTone)
        }
        Box(
            Modifier
                .fillMaxSize()
                .cssShadow(Color(0xB80A1219), blur = 4.dp, offsetY = 2.dp, cornerRadius = D.sleeveRadius)
                .clip(RoundedCornerShape(D.sleeveRadius))
                .background(P.SleeveFill)
                .border(1.dp, P.SleeveEdge, RoundedCornerShape(D.sleeveRadius)),
        ) {
            ArtworkImage(artId, small, Modifier.fillMaxSize())
            // A restrained, diagonal specular sheen restores the glossy front-sleeve finish
            // without obscuring the cover artwork or rasterizing a functional layer.
            Canvas(Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        0.00f to Color.White.copy(alpha = 0.10f),
                        0.18f to Color.White.copy(alpha = 0.05f),
                        0.42f to Color.Transparent,
                        1.00f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(this.size.width, this.size.height),
                    ),
                )
            }
            // .sleeve--front::after — 1px warm hairline inside the edge.
            Box(
                Modifier
                    .matchParentSize()
                    .border(1.dp, Color(0x6BF8F8F3), RoundedCornerShape(1.dp)),
            )
        }
    }
}

/** Dashed-free "New Playlist" tile: same stack silhouette, glyph instead of art. */
@Composable
fun NewStack(size: Dp, icon: com.sundown.player.ui.icons.SIcon, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Sleeve(4f, 1.dp, (-3).dp, borderColor = P.PaperEdge) {
            Box(Modifier.fillMaxSize().background(G.paper))
        }
        Sleeve(-4f, (-1).dp, (-2).dp) { Box(Modifier.fillMaxSize().background(Color(0xFFA9B6C0))) }
        Sleeve(-3.7f, (-3).dp, 3.dp) { Box(Modifier.fillMaxSize().background(Color(0xFF9FAEB9))) }
        Sleeve(3f, 3.dp, 3.dp) { Box(Modifier.fillMaxSize().background(Color(0xFFB6C2CB))) }
        Box(
            Modifier
                .fillMaxSize()
                .cssShadow(Color(0xB80A1219), blur = 4.dp, offsetY = 2.dp, cornerRadius = D.sleeveRadius)
                .clip(RoundedCornerShape(D.sleeveRadius))
                .background(Brush.verticalGradient(listOf(Color(0xFFE8EEF3), Color(0xFFC3CED7), Color(0xFF9FADB8))))
                .border(1.dp, P.SleeveEdge, RoundedCornerShape(D.sleeveRadius)),
            contentAlignment = Alignment.Center,
        ) {
            com.sundown.player.ui.icons.SundownIcon(icon, 44.dp, Color(0xFF4C5E6C), strokeWidth = 1.2f)
        }
    }
}
