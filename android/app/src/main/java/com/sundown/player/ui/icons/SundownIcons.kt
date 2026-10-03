package com.sundown.player.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/**
 * Icons are the original SVG primitives from the web app, re-rendered natively.
 * Path strings are parsed with Compose's own [PathParser], so curve geometry is
 * byte-identical to the reference instead of being approximated by hand.
 *
 * Everything is authored on a 24x24 grid and scaled to the requested size.
 */
sealed interface Glyph {
    data class Spline(val d: String) : Glyph
    data class Circle(val cx: Float, val cy: Float, val r: Float) : Glyph
    data class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : Glyph
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) : Glyph
}

enum class SIcon(val solid: Boolean, val glyphs: List<Glyph>) {
    Play(true, listOf(Glyph.Spline("M6 3.5 21 12 6 20.5Z"))),
    Pause(true, listOf(Glyph.Rect(5f, 4f, 5f, 16f, 0.7f), Glyph.Rect(14f, 4f, 5f, 16f, 0.7f))),
    Previous(true, listOf(Glyph.Spline("m12 4-10 8 10 8V4Zm10 0-10 8 10 8V4Z"))),
    Next(true, listOf(Glyph.Spline("m2 4 10 8-10 8V4Zm10 0 10 8-10 8V4Z"))),
    Shuffle(false, listOf(Glyph.Spline("M3 6h2c5 0 7 12 12 12h4M17 14l4 4-4 4M3 18h2c2.2 0 3.8-2.3 5.2-5M12.5 8c1.3-1.3 2.7-2 4.5-2h4M17 2l4 4-4 4"))),
    Repeat(false, listOf(Glyph.Spline("M20 8a8 8 0 0 0-14-3L3 8M3 3v5h5M4 16a8 8 0 0 0 14 3l3-3M16 16h5v5"))),
    Volume(false, listOf(Glyph.Spline("M3 9h4l5-4v14l-5-4H3Z"), Glyph.Spline("M15 8a6 6 0 0 1 0 8M18 5a10 10 0 0 1 0 14"))),
    Mute(false, listOf(Glyph.Spline("M3 9h4l5-4v14l-5-4H3Z"), Glyph.Spline("m16.5 9.5 5 5M21.5 9.5l-5 5"))),
    Search(false, listOf(Glyph.Circle(10f, 10f, 6f), Glyph.Spline("m14.5 14.5 5 5"))),
    Close(false, listOf(Glyph.Spline("m6 6 12 12M18 6 6 18"))),
    Chevron(false, listOf(Glyph.Spline("m14 5-7 7 7 7"))),
    Star(true, listOf(Glyph.Spline("m12 3 2.8 5.7 6.3.9-4.6 4.5 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9Z"))),
    Plus(false, listOf(Glyph.Spline("M12 5v14M5 12h14"))),
    Music(false, listOf(Glyph.Spline("M9 18V5l11-2v13M9 8l11-2"), Glyph.Oval(6f, 18f, 3f, 2f), Glyph.Oval(17f, 16f, 3f, 2f))),
    Check(false, listOf(Glyph.Spline("m5 12 4 4L19 6"))),
    Folder(false, listOf(Glyph.Spline("M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"))),
    Refresh(false, listOf(Glyph.Spline("M20 11a8 8 0 1 0-1 5"), Glyph.Spline("M20 4v7h-7"))),
    Note(false, listOf(Glyph.Spline("M9 18V5l11-2v13"), Glyph.Oval(6f, 18f, 3f, 2f), Glyph.Oval(17f, 16f, 3f, 2f))),
    Clock(false, listOf(Glyph.Circle(12f, 12f, 9f), Glyph.Spline("M12 7v5l3 2"))),
    Library(false, listOf(Glyph.Spline("M4 4v16M9 4v16M15 5l5-1 3 15-5 1Z"))),
    Files(false, listOf(Glyph.Spline("M6 2h8l5 5v15H6Z"), Glyph.Spline("M14 2v5h5"))),
    Trash(false, listOf(Glyph.Spline("M4 7h16M9 7V4h6v3M6 7l1 14h10l1-14M10 11v6M14 11v6"))),
    Disc(false, listOf(Glyph.Circle(12f, 12f, 9f), Glyph.Circle(12f, 12f, 2.5f))),
}

private fun SIcon.toPath(): Path {
    val combined = Path()
    glyphs.forEach { glyph ->
        when (glyph) {
            is Glyph.Spline -> combined.addPath(PathParser().parsePathString(glyph.d).toPath())
            is Glyph.Circle -> combined.addOval(
                androidx.compose.ui.geometry.Rect(
                    glyph.cx - glyph.r, glyph.cy - glyph.r, glyph.cx + glyph.r, glyph.cy + glyph.r,
                ),
            )
            is Glyph.Oval -> combined.addOval(
                androidx.compose.ui.geometry.Rect(
                    glyph.cx - glyph.rx, glyph.cy - glyph.ry, glyph.cx + glyph.rx, glyph.cy + glyph.ry,
                ),
            )
            is Glyph.Rect -> combined.addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    glyph.x, glyph.y, glyph.x + glyph.w, glyph.y + glyph.h,
                    androidx.compose.ui.geometry.CornerRadius(glyph.r, glyph.r),
                ),
            )
        }
    }
    return combined
}

@Composable
fun SundownIcon(
    icon: SIcon,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f,
) {
    val path = remember(icon) { icon.toPath() }
    Canvas(modifier.size(size)) {
        val factor = this.size.minDimension / 24f
        scale(factor, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(
                path = path,
                color = tint,
                style = if (icon.solid) Fill else Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}
