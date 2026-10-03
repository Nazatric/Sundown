package com.sundown.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * CSS-pixel text tokens map to Android sp at the default scale. Compose then
 * honours the user's system font scale and accessibility settings natively.
 */
val Int.cssSp: TextUnit
    @Composable @ReadOnlyComposable get() = toFloat().cssSp

val Float.cssSp: TextUnit
    @Composable @ReadOnlyComposable get() = sp

val Double.cssSp: TextUnit
    @Composable @ReadOnlyComposable get() = toFloat().cssSp

val Int.cssLh: TextUnit
    @Composable @ReadOnlyComposable get() = toFloat().cssSp

/** Arial/Helvetica fall back to Android's system sans (Roboto). */
val SundownFontFamily: FontFamily = FontFamily.SansSerif
