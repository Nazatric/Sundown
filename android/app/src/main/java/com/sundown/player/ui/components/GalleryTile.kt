package com.sundown.player.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sundown.player.ui.icons.SIcon
import com.sundown.player.ui.theme.*
import kotlinx.coroutines.delay

/**
 * `.album-item` — stacked artwork with a two-line caption.
 *
 * The original reveals each tile once, on its first intersection with the
 * scroll viewport. Lazy composition alone is not treated as visibility, so a
 * prefetched/off-screen item does not animate early or replay on every revisit.
 */
@Composable
fun GalleryTile(
    label: String,
    detail: String,
    artId: String?,
    rearArtId: String?,
    coverSize: Dp,
    index: Int,
    wide: Boolean,
    revealKey: String,
    visible: Boolean,
    revealedItems: MutableSet<String>,
    modifier: Modifier = Modifier,
    smallArt: Boolean = false,
    onClick: () -> Unit,
) {
    TileFrame(label, detail, index, wide, revealKey, visible, revealedItems, modifier, onClick) {
        AlbumStack(artId = artId, rearArtId = rearArtId, size = coverSize, small = smallArt)
    }
}

@Composable
fun NewPlaylistTile(
    label: String,
    detail: String,
    coverSize: Dp,
    index: Int,
    wide: Boolean,
    revealKey: String,
    visible: Boolean,
    revealedItems: MutableSet<String>,
    modifier: Modifier = Modifier,
    icon: SIcon = SIcon.Plus,
    onClick: () -> Unit,
) {
    TileFrame(label, detail, index, wide, revealKey, visible, revealedItems, modifier, onClick) {
        NewStack(size = coverSize, icon = icon)
    }
}

@Composable
private fun TileFrame(
    label: String,
    detail: String,
    index: Int,
    wide: Boolean,
    revealKey: String,
    visible: Boolean,
    revealedItems: MutableSet<String>,
    modifier: Modifier,
    onClick: () -> Unit,
    artwork: @Composable () -> Unit,
) {
    val (clickModifier, pressed) = rippleless(onClick)
    val progress = remember(revealKey) { Animatable(if (revealKey in revealedItems) 1f else 0f) }

    LaunchedEffect(revealKey, index, visible) {
        if (!visible) return@LaunchedEffect
        if (!revealedItems.add(revealKey) || !ValueAnimator.areAnimatorsEnabled()) {
            progress.snapTo(1f)
        } else {
            val staggerMs = (index % 4) * 55L
            if (staggerMs > 0L) delay(staggerMs)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 460,
                    easing = CubicBezierEasing(0.2f, 0.65f, 0.3f, 1f),
                ),
            )
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * 12.dp.toPx()
                scaleX = if (pressed) 0.985f else 1f
                scaleY = if (pressed) 0.985f else 1f
            }
            .then(clickModifier),
    ) {
        artwork()
        Spacer(Modifier.height(if (wide) D.tileNameTopWide else D.tileNameTop))
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.98f),
            style = TextStyle(
                color = P.TileName,
                fontSize = if (wide) 15.cssSp else 14.cssSp,
                lineHeight = if (wide) 18.cssSp else 17.cssSp,
                fontWeight = FontWeight.Bold,
                fontFamily = SundownFontFamily,
                shadow = Shadow(androidx.compose.ui.graphics.Color(0xFF34414B), Offset(0f, -1f), 1f),
            ),
        )
        Spacer(Modifier.height(1.dp))
        Text(
            detail,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.98f),
            style = TextStyle(
                color = P.TileCount,
                fontSize = if (wide) 14.cssSp else 12.5f.cssSp,
                lineHeight = if (wide) 18.cssSp else 16.cssSp,
                fontFamily = SundownFontFamily,
                shadow = Shadow(androidx.compose.ui.graphics.Color(0xFF414C55), Offset(0f, -1f), 1f),
            ),
        )
    }
}

/** `.alphabet-index` — 27-key A–Z rail with tap, drag and visible-position state. */
@Composable
fun AlphabetIndex(
    letters: List<String>,
    currentLetter: String?,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    onJump: (letter: String, isDrag: Boolean) -> Unit,
) {
    val jumpHandler by rememberUpdatedState(onJump)
    Column(
        modifier = modifier
            .width(if (wide) D.indexWidthWide else D.indexWidth)
            .padding(vertical = 2.dp)
            .pointerInput(letters) {
                var lastDraggedLetter: String? = null
                fun letterAt(y: Float): String {
                    val height = size.height.coerceAtLeast(1)
                    val index = ((y.coerceIn(0f, height - 1f) / height) * letters.size)
                        .toInt()
                        .coerceIn(0, letters.lastIndex)
                    return letters[index]
                }
                detectVerticalDragGestures(
                    onDragStart = { position ->
                        val letter = letterAt(position.y)
                        lastDraggedLetter = letter
                        jumpHandler(letter, true)
                    },
                    onVerticalDrag = { change, _ ->
                        val letter = letterAt(change.position.y)
                        if (letter != lastDraggedLetter) {
                            lastDraggedLetter = letter
                            jumpHandler(letter, true)
                        }
                    },
                    onDragEnd = { lastDraggedLetter = null },
                    onDragCancel = { lastDraggedLetter = null },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            val active = letter == currentLetter
            val (clickModifier, pressed) = rippleless({ onJump(letter, false) })
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        if (active) androidx.compose.ui.graphics.Color(0x52202F3C)
                        else androidx.compose.ui.graphics.Color.Transparent,
                    )
                    .semantics {
                        contentDescription = "Jump to $letter"
                        selected = active
                        role = Role.Button
                    }
                    .then(clickModifier)
                    .alpha(if (pressed || active) 1f else 0.95f),
            ) {
                Text(
                    letter,
                    style = TextStyle(
                        color = if (pressed || active) androidx.compose.ui.graphics.Color(0xFFEEF7FF) else P.InkFaint,
                        fontSize = if (wide) 11.cssSp else 10.cssSp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SundownFontFamily,
                        shadow = Shadow(androidx.compose.ui.graphics.Color(0xB3273039), Offset(0f, -1f), 0f),
                    ),
                )
            }
        }
    }
}
