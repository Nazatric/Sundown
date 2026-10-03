package com.sundown.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sundown.player.ui.icons.SIcon
import com.sundown.player.ui.icons.SundownIcon
import com.sundown.player.ui.theme.*

/** `.loading-spinner` / `.scan-spinner` — 700 ms linear sweep. */
@Composable
fun Spinner(size: Dp, color: Color, modifier: Modifier = Modifier, trackAlpha: Float = 0.28f) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(modifier.size(size).rotate(angle)) {
        val stroke = 2.dp.toPx()
        drawCircle(color.copy(alpha = trackAlpha), radius = (this.size.minDimension - stroke) / 2f, style = Stroke(stroke))
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(stroke, cap = StrokeCap.Butt),
        )
    }
}

/** `.equalizer` — three bars, 720 ms alternate, staggered -380 / -190 ms. */
@Composable
fun Equalizer(playing: Boolean, modifier: Modifier = Modifier) {
    val heights = listOf(13f, 16f, 11f)
    val delays = listOf(0, 340, 530)
    val transition = rememberInfiniteTransition(label = "eq")
    Row(
        modifier = modifier.size(D.eqWidth, D.eqHeight),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        heights.forEachIndexed { index, full ->
            val scale by transition.animateFloat(
                initialValue = if (playing) 0.3f else 1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(720, delayMillis = 0, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(delays[index]),
                ),
                label = "bar",
            )
            Box(
                Modifier
                    .width(D.eqBar)
                    .height((full * (if (playing) scale else 1f)).dp)
                    .background(P.EqBlue),
            )
        }
    }
}

/** `.scan-bar` — indexing progress strip under the toolbar. */
@Composable
fun ScanBar(title: String, subtitle: String, done: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(D.scanGap),
        modifier = modifier
            .fillMaxWidth()
            .background(G.scan)
            .padding(horizontal = D.scanPadH, vertical = D.scanPadV),
    ) {
        Spinner(D.scanSpinner, Color(0xFFDFEEFF), trackAlpha = 0.30f)
        Column(Modifier.weight(1f)) {
            Text(
                title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = P.Ink, fontSize = 13.cssSp, lineHeight = 17.cssSp, fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily),
            )
            Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = P.ScanSub, fontSize = 11.cssSp, lineHeight = 15.cssSp, fontFamily = SundownFontFamily),
            )
        }
        if (total > 0) {
            val fraction = (done.toFloat() / total).coerceIn(0f, 1f)
            Box(
                Modifier
                    .width(D.scanMeterWidth)
                    .height(D.scanMeterHeight)
                    .clip(RoundedCornerShape(D.scanMeterRadius))
                    .background(P.ScanMeterBg)
                    .border(1.dp, P.ScanMeterBorder, RoundedCornerShape(D.scanMeterRadius)),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(G.scanMeter))
            }
        }
    }
}

/** `.app-notice` — transient toast above the player. */
@Composable
fun NoticeToast(message: String, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(D.noticeGap),
        modifier = modifier
            .widthIn(max = 540.dp)
            .clip(RoundedCornerShape(D.noticeRadius))
            .background(G.notice)
            .border(1.dp, P.NoticeBorder, RoundedCornerShape(D.noticeRadius))
            .padding(horizontal = D.noticePadH, vertical = D.noticePadV),
    ) {
        Text(
            message,
            modifier = Modifier.weight(1f, fill = false),
            style = TextStyle(
                color = Color(0xFFF4F8FC), fontSize = 13.cssSp, lineHeight = 19.cssSp,
                fontFamily = SundownFontFamily,
                shadow = Shadow(Color(0xFF344C5F), Offset(0f, -1f), 0f),
            ),
        )
        GhostIconButton(SIcon.Close, 24.dp, 14.dp, Color(0xFFDEEDF7), "Dismiss notification", onClick = onDismiss)
    }
}

/** `.empty-state` — badge, headline, body, optional action. */
@Composable
fun EmptyState(
    icon: SIcon,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionIcon: SIcon = SIcon.Folder,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(D.emptyBadge)
                .clip(CircleShape)
                .background(G.emptyBadge())
                .border(1.dp, P.EmptyBadgeBorder, CircleShape),
        ) {
            SundownIcon(icon, D.emptyBadgeIcon, Color(0xFFD9E6EF), strokeWidth = 1.4f)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title, textAlign = TextAlign.Center,
            style = TextStyle(
                color = P.Ink, fontSize = 20.cssSp, lineHeight = 25.cssSp,
                fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily,
                shadow = Shadow(Color(0xFF3E4F5B), Offset(0f, -1f), 0f),
            ),
        )
        Spacer(Modifier.height(9.dp))
        Text(
            body, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = D.emptyBodyMaxWidth),
            style = TextStyle(
                color = P.EmptyBody, fontSize = 13.5f.cssSp, lineHeight = 21.cssSp,
                fontFamily = SundownFontFamily,
            ),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            MetalButton(label = actionLabel, icon = actionIcon, big = true, onClick = onAction)
        }
    }
}

/** Native confirmation dialog styled with Sundown's metal controls. */
@Composable
fun ConfirmSundownDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(D.sheetRadius))
                .background(P.SheetBg)
                .border(1.dp, P.SheetBorder, RoundedCornerShape(D.sheetRadius))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = TextStyle(
                    color = P.Ink,
                    fontSize = 18.cssSp,
                    lineHeight = 24.cssSp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SundownFontFamily,
                ),
            )
            Text(
                message,
                style = TextStyle(
                    color = P.EmptyBody,
                    fontSize = 13.cssSp,
                    lineHeight = 20.cssSp,
                    fontFamily = SundownFontFamily,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetalButton("Cancel", icon = SIcon.Close, onClick = onDismiss)
                MetalButton(confirmLabel, icon = SIcon.Trash, onClick = onConfirm)
            }
        }
    }
}
