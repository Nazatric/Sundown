package com.sundown.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sundown.player.ui.theme.*
import kotlinx.coroutines.delay

/**
 * `.progress-range` — 8 dp inset track, concentric 19 dp thumb.
 * Material's Slider is not used: its track height, thumb and ripple all differ
 * from the original, and the thumb here has a two-ring gradient face.
 */
@Composable
fun ProgressSlider(
    fraction: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onScrub: (Float) -> Unit,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var previewActive by remember { mutableStateOf(false) }
    var previewRevision by remember { mutableIntStateOf(0) }
    var previewProgress by remember { mutableFloatStateOf(fraction.coerceIn(0f, 1f)) }
    val density = LocalDensity.current
    val clamped = fraction.coerceIn(0f, 1f)
    val latestClamped by rememberUpdatedState(clamped)
    val latestOnScrub by rememberUpdatedState(onScrub)

    fun scrubTo(value: Float) {
        previewProgress = value.coerceIn(0f, 1f)
        previewActive = true
        previewRevision += 1
        latestOnScrub(previewProgress)
    }

    LaunchedEffect(previewRevision) {
        if (previewActive) {
            delay(700)
            previewActive = false
        }
    }
    LaunchedEffect(clamped) {
        if (previewActive && kotlin.math.abs(clamped - previewProgress) <= 0.002f) {
            previewActive = false
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(250, easing = LinearEasing),
        label = "progress",
    )
    val displayedProgress = when {
        dragging -> previewProgress
        previewActive -> previewProgress
        else -> animatedProgress
    }

    Box(
        modifier = modifier
            .height(D.progressThumb)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(enabled, widthPx) {
                if (!enabled || widthPx == 0) return@pointerInput
                detectTapGestures { offset -> scrubTo(offset.x / widthPx) }
            }
            .pointerInput(enabled, widthPx) {
                if (!enabled || widthPx == 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        previewProgress = latestClamped
                        previewActive = false
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ -> scrubTo(change.position.x / widthPx) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(D.progressTrack)
                .clip(RoundedCornerShape(D.progressRadius))
                .background(P.ProgressRest)
                .border(1.dp, P.ProgressBorder, RoundedCornerShape(D.progressRadius)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(displayedProgress).background(P.ProgressFilled))
        }
        val travel = with(density) { (widthPx.toDp() - D.progressThumb).coerceAtLeast(0.dp) }
        Box(
            Modifier
                .offset(x = travel * displayedProgress)
                .size(D.progressThumb)
                .cssShadow(Color(0xAB0A1117), blur = 3.dp, offsetY = 1.dp, cornerRadius = D.progressThumb / 2)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFA4B3BF))))
                .border(1.dp, Color(0xFF293741), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // Concentric face from the CSS radial-gradient.
            Box(Modifier.size(11.dp).clip(CircleShape).background(Color(0xFFEAF1F5)))
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF8A98A2)))
        }
    }
}

/** `.volume-control` — pill-shaped track whose knob is the full pill height. */
@Composable
fun VolumePill(
    level: Float,
    muted: Boolean,
    modifier: Modifier = Modifier,
    onLevel: (Float) -> Unit,
    onToggleMute: () -> Unit,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val value = (if (muted) 0f else level).coerceIn(0f, 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(D.volumeHeight)
            .cssShadow(Color(0x66161F27), blur = 3.dp, offsetY = 2.dp, cornerRadius = D.volumeRadius)
            .clip(RoundedCornerShape(D.volumeRadius))
            .background(G.silver)
            .border(1.dp, Color(0xFF2B3741), RoundedCornerShape(D.volumeRadius))
            .innerTopHighlight(Color(0xD9FFFFFF), D.volumeRadius),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onSizeChanged { widthPx = it.width }
                .pointerInput(widthPx) {
                    if (widthPx == 0) return@pointerInput
                    detectTapGestures { offset -> onLevel((offset.x / widthPx).coerceIn(0f, 1f)) }
                }
                .pointerInput(widthPx) {
                    if (widthPx == 0) return@pointerInput
                    detectHorizontalDragGestures { change, _ ->
                        onLevel((change.position.x / widthPx).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(D.volumeRadius))
                    .background(Brush.horizontalGradient(listOf(P.VolumeRestA, P.VolumeRestB))),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(value)
                        .background(Brush.horizontalGradient(listOf(P.VolumeFilledA, P.VolumeFilledB))),
                )
            }
            val travel = with(density) { (widthPx.toDp() - D.volumeKnob).coerceAtLeast(0.dp) }
            Box(
                Modifier
                    .offset(x = travel * value)
                    .size(D.volumeKnob)
                    .cssShadow(Color(0xA60B151C), blur = 3.dp, offsetY = 1.dp, cornerRadius = D.volumeKnob / 2)
                    .clip(CircleShape)
                    .background(G.silver)
                    .border(1.dp, P.KnobBorder, CircleShape),
            )
        }
        GhostIconButton(
            icon = if (muted) com.sundown.player.ui.icons.SIcon.Mute else com.sundown.player.ui.icons.SIcon.Volume,
            size = D.muteButton,
            iconSize = D.muteIcon,
            tint = if (muted) P.Accent else P.ControlInk,
            contentDescription = if (muted) "Unmute" else "Mute",
            onClick = onToggleMute,
        )
    }
}

/** `.metal-switch` — settings toggle with a travelling silver knob. */
@Composable
fun MetalSwitch(checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    val (clickModifier, _) = rippleless({ onCheckedChange(!checked) })
    val knobX by animateDpAsState(
        targetValue = if (checked) D.switchKnobOn else D.switchKnobOff,
        animationSpec = tween(180),
        label = "knob",
    )
    Box(
        modifier = modifier
            .size(D.switchW, D.switchH)
            .cssShadow(Color(0x2ED7E5F0), blur = 0.dp, offsetY = 1.dp, cornerRadius = D.switchRadius)
            .clip(RoundedCornerShape(D.switchRadius))
            .background(if (checked) G.switchOn else G.switchOff)
            .border(1.dp, if (checked) P.SwitchOnBorder else P.SwitchBorder, RoundedCornerShape(D.switchRadius))
            .innerTopHighlight(Color(0x66080F16), D.switchRadius)
            .then(clickModifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobX)
                .size(D.switchKnob)
                .cssShadow(Color(0xA60B151C), blur = 3.dp, offsetY = 1.dp, cornerRadius = D.switchKnob / 2)
                .clip(CircleShape)
                .background(G.silver)
                .border(1.dp, P.KnobBorder, CircleShape),
        )
    }
}

/** Mode pill holding repeat + shuffle, with the `1` badge for repeat-one. */
@Composable
fun ModePill(
    repeatMode: String,
    shuffleOn: Boolean,
    modifier: Modifier = Modifier,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(D.modePillHeight)
            .cssShadow(Color(0x73121B23), blur = 3.dp, offsetY = 2.dp, cornerRadius = D.modePillRadius)
            .clip(RoundedCornerShape(D.modePillRadius))
            .background(G.silver)
            .border(1.dp, Color(0xFF293640), RoundedCornerShape(D.modePillRadius))
            .innerTopHighlight(Color(0xD9FFFFFF), D.modePillRadius),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            GhostIconButton(
                icon = com.sundown.player.ui.icons.SIcon.Repeat,
                size = D.modeButton, iconSize = D.modeIcon,
                tint = if (repeatMode != "off") P.Accent else P.ControlInk,
                contentDescription = "Repeat: " + repeatMode,
                onClick = onRepeat,
            )
            if (repeatMode == "one") {
                androidx.compose.material3.Text(
                    "1",
                    modifier = Modifier.offset(x = 8.dp, y = (-2).dp),
                    style = androidx.compose.ui.text.TextStyle(
                        color = P.Accent,
                        fontSize = 9.cssSp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontFamily = SundownFontFamily,
                    ),
                )
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF7F8E99)))
        GhostIconButton(
            icon = com.sundown.player.ui.icons.SIcon.Shuffle,
            size = D.modeButton, iconSize = D.modeIcon,
            tint = if (shuffleOn) P.Accent else P.ControlInk,
            contentDescription = "Shuffle",
            onClick = onShuffle,
        )
    }
}
