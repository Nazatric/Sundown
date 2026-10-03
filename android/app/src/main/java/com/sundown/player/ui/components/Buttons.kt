package com.sundown.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sundown.player.ui.icons.SIcon
import com.sundown.player.ui.icons.SundownIcon
import com.sundown.player.ui.theme.*

/** Shared clickable that never shows a Material ripple (the web has none). */
@Composable
fun rippleless(onClick: () -> Unit, enabled: Boolean = true): Pair<Modifier, Boolean> {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val modifier = Modifier.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
    return modifier to pressed
}

/**
 * `.metal-button` — toolbar / sheet action button.
 * h 35, r 6, metal gradient, 1px #3F4B56 border, inset top highlight,
 * 13/700 label with a -1px dark text shadow.
 */
@Composable
fun MetalButton(
    label: String?,
    modifier: Modifier = Modifier,
    icon: SIcon? = null,
    enabled: Boolean = true,
    big: Boolean = false,
    forcedPressed: Boolean = false,
    onClick: () -> Unit,
) {
    val (clickModifier, pressed) = rippleless(onClick, enabled)
    val isPressed = pressed || forcedPressed
    val height = if (big) D.metalBigHeight else D.metalHeight
    val radius = if (big) D.metalBigRadius else D.metalRadius

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .height(height)
            .cssShadow(Color(0x36DDE6EB), blur = 0.dp, offsetY = 1.dp, cornerRadius = radius)
            .clip(RoundedCornerShape(radius))
            .then(if (isPressed) Modifier.background(G.metalPressed) else Modifier.background(G.metal))
            .border(BorderStroke(1.dp, Color(0xFF3F4B56)), RoundedCornerShape(radius))
            .then(if (!isPressed) Modifier.innerTopHighlight(Color(0x3DE7EDF2), radius) else Modifier)
            .then(clickModifier)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = if (big) D.metalBigPadH else D.metalPadH),
    ) {
        if (icon != null) {
            SundownIcon(icon, if (big) D.sourcesIcon else D.metalIcon, P.Ink)
        }
        if (label != null) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = P.Ink,
                    fontSize = (if (big) 14 else 13).cssSp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SundownFontFamily,
                    shadow = Shadow(Color(0xFF3B4650), Offset(0f, -1f), 1f),
                ),
            )
        }
    }
}

/**
 * `.silver-small-button` — in-sheet action (Play Album, Shuffle, favourite).
 * Silver gradient with a light text shadow; glyph ink is #344B5B.
 */
@Composable
fun SilverSmallButton(
    label: String?,
    modifier: Modifier = Modifier,
    icon: SIcon? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    width: Dp? = null,
    onClick: () -> Unit,
) {
    val (clickModifier, pressed) = rippleless(onClick, enabled)
    val ink = if (selected) P.FavoriteOn else Color(0xFF344B5B)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .heightIn(min = D.silverSmallMinHeight)
            .cssShadow(Color(0x73292A38), blur = 2.dp, offsetY = 1.dp, cornerRadius = D.silverSmallRadius)
            .clip(RoundedCornerShape(D.silverSmallRadius))
            .background(G.silver)
            .border(BorderStroke(1.dp, Color(0xFF2F4455)), RoundedCornerShape(D.silverSmallRadius))
            .innerTopHighlight(Color(0xD9FFFFFF), D.silverSmallRadius)
            .then(clickModifier)
            .alpha(if (enabled) 1f else 0.45f)
            .scale(if (pressed) 0.98f else 1f)
            .padding(horizontal = D.silverSmallPadH, vertical = D.silverSmallPadV),
    ) {
        if (icon != null) SundownIcon(icon, D.silverSmallIcon, ink)
        if (label != null) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = ink,
                    fontSize = 12.cssSp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SundownFontFamily,
                    shadow = Shadow(Color(0xBFFFFFFF), Offset(0f, 1f), 0f),
                ),
            )
        }
    }
}

/**
 * `.transport-button` — circular silver control. 42 dp (54 for play) on phones,
 * 48/64 at >=860 dp. Pressed state scales to .94 like the CSS `:active`.
 */
@Composable
fun TransportButton(
    icon: SIcon,
    diameter: Dp,
    iconSize: Dp,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val (clickModifier, pressed) = rippleless(onClick, enabled)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(diameter)
            .scale(if (pressed) 0.94f else 1f)
            .cssShadow(Color(0x8C101820), blur = 3.dp, offsetY = 2.dp, cornerRadius = diameter / 2)
            .clip(CircleShape)
            .background(G.silver)
            .border(BorderStroke(1.dp, P.ControlBorder), CircleShape)
            .innerTopHighlight(Color.White, diameter / 2)
            .then(clickModifier)
            .alpha(if (enabled) 1f else 0.45f)
            .semanticsLabel(contentDescription),
    ) {
        if (busy) {
            Spinner(size = iconSize, color = P.ControlInk)
        } else {
            SundownIcon(icon, iconSize, P.ControlInk)
        }
    }
}

/** Flat icon-only button (sheet close, clear filter, row add). */
@Composable
fun GhostIconButton(
    icon: SIcon,
    size: Dp,
    iconSize: Dp,
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (clickModifier, _) = rippleless(onClick)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .then(clickModifier)
            .semanticsLabel(contentDescription),
    ) {
        SundownIcon(icon, iconSize, tint)
    }
}

@Composable
fun CenteredLabel(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text, modifier, style = style, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** TalkBack label for the custom-drawn controls, which have no text child. */
fun Modifier.semanticsLabel(label: String): Modifier =
    this.semantics { contentDescription = label }
