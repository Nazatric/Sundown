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
    fun cached(artId: String, small: Boolean): ImageBitmap?
    suspend fun load(artId: String, small: Boolean): ImageBitmap?
}

val LocalArtworkLoader = staticCompositionLocalOf<ArtworkLoader?> { null }

@Composable
fun SleevePlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFF89949D), Color(0xFF3E4A54)),
                start = Offset.Zero, end = Offset(size.width, size.height),
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
        if (artId != null) loader?.cached(artId, small) else null
    }
    var bitmap by remember(artId, small, loader) { mutableStateOf(initialBitmap) }
    val imageAlpha = remember(artId, small, loader) { Animatable(if (initialBitmap != null) 1f else 0f) }

    LaunchedEffect(artId, small, loader) {
        if (artId == null) {
            bitmap = null
            imageAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        val memory = loader?.cached(artId, small)
        if (memory != null) {
            bitmap = memory
            imageAlpha.snapTo(1f)
        } else {
            val loaded = loader?.load(artId, small)
            bitmap = loaded
            if (loaded != null) {
                imageAlpha.animateTo(1f, tween(60))
            }
        }
    }

    Box(modifier) {
        val current = bitmap
        val fade = imageAlpha.value
        if (current == null || fade < 1f) {
            SleevePlaceholder(Modifier.matchParentSize())
        }
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                modifier = Modifier.matchParentSize().graphicsLayer { this.alpha = fade },
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
                transformOrigin = TransformOrigin(0.5f, 0.55f)
            }
            .cssShadow(Color(0x9E0E1419), blur = 3.dp, offsetY = 1.dp, cornerRadius = D.sleeveRadius)
            .clip(RoundedCornerShape(D.sleeveRadius))
            .background(P.SleeveFill)
            .border(1.dp, borderColor, RoundedCornerShape(D.sleeveRadius)),
        content = content,
    )
}

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
        
        Sleeve(4f, 1.dp, (-3.5).dp, borderColor = P.PaperEdge) {
            Box(Modifier.fillMaxSize().background(G.paper))
        }
        Sleeve(-4.2f, (-1.2).dp, (-2.5).dp) {
            ArtworkImage(rearArtId ?: artId, small, Modifier.fillMaxSize(), rearTone, alpha = 0.68f)
        }
        Sleeve(-3.8f, (-3.5).dp, 3.5.dp) {
            ArtworkImage(rearArtId ?: artId, small, Modifier.fillMaxSize(), leftTone)
        }
        Sleeve(3.2f, 3.5.dp, 3.5.dp) {
            ArtworkImage(artId, small, Modifier.fillMaxSize(), rightTone)
        }
        
        Box(
            Modifier
                .fillMaxSize()
                .cssShadow(Color(0xB80A1219), blur = 5.dp, offsetY = 2.5.dp, cornerRadius = D.sleeveRadius)
                .clip(RoundedCornerShape(D.sleeveRadius))
                .background(P.SleeveFill)
                .border(1.dp, P.SleeveEdge, RoundedCornerShape(D.sleeveRadius)),
        ) {
            ArtworkImage(artId, small, Modifier.fillMaxSize())
            
            // PIXEL-PERFECT CURVED SPECUAR GLOSS
            Canvas(Modifier.matchParentSize()) {
                val glossPath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height * 0.45f)
                    quadraticTo(
                        size.width * 0.5f, size.height * 0.58f,
                        0f, size.height * 0.45f
                    )
                    close()
                }
                
                drawPath(
                    path = glossPath,
                    brush = Brush.verticalGradient(
                        0.00f to Color.White.copy(alpha = 0.32f),
                        0.40f to Color.White.copy(alpha = 0.12f),
                        1.00f to Color.White.copy(alpha = 0.02f),
                    )
                )
                
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(0f, 0.75f),
                    end = Offset(size.width, 0.75f),
                    strokeWidth = 1.5f
                )
                
                drawRect(
                    brush = Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.85f to Color.Transparent,
                        1.00f to Color.Black.copy(alpha = 0.10f),
                    )
                )
            }
            
            Box(
                Modifier
                    .matchParentSize()
                    .border(1.dp, Color(0x6BF8F8F3), RoundedCornerShape(D.sleeveRadius)),
            )
        }
    }
}

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
