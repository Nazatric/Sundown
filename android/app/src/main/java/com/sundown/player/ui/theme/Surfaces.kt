package com.sundown.player.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Multi-stop gradients transcribed from the stylesheet. */
object G {
    val silver = Brush.verticalGradient(
        0.00f to P.SilverHi, 0.38f to P.SilverMid, 0.62f to P.SilverLoMid, 1.00f to P.SilverLo,
    )
    val metal = Brush.verticalGradient(
        0.00f to P.MetalHi, 0.48f to P.MetalMid, 1.00f to P.MetalLo,
    )
    val metalPressed = Brush.verticalGradient(listOf(Color(0xFF495864), Color(0xFF687783)))
    val toolbar = Brush.verticalGradient(
        0.00f to P.ToolbarA, 0.22f to P.ToolbarB, 0.63f to P.ToolbarC, 1.00f to P.ToolbarD,
    )
    val tabSelected = Brush.verticalGradient(
        0.00f to P.TabSelA, 0.62f to P.TabSelB, 1.00f to P.TabSelC,
    )
    val tabIdle = Brush.verticalGradient(listOf(Color(0x17E1E8ED), Color(0x1F212D39)))
    val search = Brush.verticalGradient(
        0.00f to P.SearchA, 0.64f to P.SearchB, 1.00f to P.SearchC,
    )
    val player = Brush.verticalGradient(
        0.00f to P.PlayerTop, 0.44f to P.PlayerMid, 1.00f to P.PlayerBottom,
    )
    val sheetBar = Brush.verticalGradient(
        0.00f to P.SheetBarA, 0.65f to P.SheetBarB, 1.00f to P.SheetBarC,
    )
    val scan = Brush.verticalGradient(listOf(P.ScanA, P.ScanB))
    val scanMeter = Brush.verticalGradient(listOf(P.ScanMeterA, P.ScanMeterB))
    val notice = Brush.verticalGradient(listOf(P.NoticeA, P.NoticeB))
    val npArt = Brush.verticalGradient(listOf(Color(0xFFF9FCFE), Color(0xFFA9B6BF)))
    val switchOff = Brush.verticalGradient(listOf(P.SwitchTrackA, P.SwitchTrackB))
    val switchOn = Brush.verticalGradient(listOf(P.SwitchOnA, P.SwitchOnB))
    val songsHeader = Brush.verticalGradient(listOf(Color(0xF7606C76), Color(0xF7545F69)))
    val paper = Brush.linearGradient(
        0.00f to P.Paper1, 0.45f to P.Paper2, 1.00f to P.Paper3,
        start = Offset.Zero, end = Offset(120f, 420f), // ~105deg
    )

    fun bottomFade() = Brush.verticalGradient(
        0.00f to Color(0x005C6770), 0.44f to Color(0x735C6770), 1.00f to P.Library,
    )

    fun emptyBadge() = Brush.radialGradient(
        listOf(P.EmptyBadgeA, P.EmptyBadgeB), center = Offset(0.5f, 0.32f), radius = Float.POSITIVE_INFINITY,
    )
}

/**
 * CSS `box-shadow` with a real Gaussian blur.
 *
 * Compose's `shadow()` only produces Material elevation, which does not match
 * the hand-tuned offsets in the original. This draws the exact shape through
 * the framework canvas instead. CSS blur radius is ~2x the Gaussian sigma that
 * `setShadowLayer` expects, hence the halving.
 */
fun Modifier.cssShadow(
    color: Color,
    blur: Dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    cornerRadius: Dp = 0.dp,
    spread: Dp = 0.dp,
): Modifier = drawBehind {
    val blurPx = (blur.toPx() / 2f).coerceAtLeast(0.01f)
    val spreadPx = spread.toPx()
    val radiusPx = cornerRadius.toPx()
    val paint = Paint()
    paint.asFrameworkPaint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.TRANSPARENT
        setShadowLayer(blurPx, offsetX.toPx(), offsetY.toPx(), color.toArgb())
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            -spreadPx,
            -spreadPx,
            size.width + spreadPx,
            size.height + spreadPx,
            radiusPx,
            radiusPx,
            paint.asFrameworkPaint(),
        )
    }
}

/** Soft elliptical ground shadow under each album stack. */
fun DrawScope.groundShadow(insetLeft: Float, insetRight: Float, top: Float, height: Float, color: Color) {
    val paint = Paint()
    paint.asFrameworkPaint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        maskFilter = android.graphics.BlurMaskFilter(4.dp.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawOval(
            insetLeft, top, size.width - insetRight, top + height, paint.asFrameworkPaint(),
        )
    }
}

/** 1 px top highlight used on every metal / silver control. */
fun Modifier.innerTopHighlight(color: Color = Color(0x3DE7EDF2), radius: Dp = 0.dp): Modifier =
    drawBehind {
        val r = radius.toPx()
        drawRoundRect(
            color = color,
            size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
    }

val ZeroPadding = PaddingValues(0.dp)
