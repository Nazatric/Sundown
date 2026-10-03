package com.sundown.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sundown.player.ui.theme.*

/**
 * `.segmented-control` — the five library tabs.
 *
 * Each segment is `flex: 1 1 0` with `min-width: 0`, which is what stopped the
 * labels colliding on narrow phones in the web build. The Compose equivalent is
 * `weight(1f)` + `Ellipsis`, so the same guarantee holds at 320 dp.
 */
@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    fontSize: Float = 13f,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = modifier
            .height(D.segmentHeight)
            .clip(RoundedCornerShape(D.segmentRadius))
            .border(1.dp, P.TabBorder, RoundedCornerShape(D.segmentRadius)),
    ) {
        items.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val (clickModifier, _) = rippleless({ onSelect(index) })
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) G.tabSelected else G.tabIdle)
                    .then(clickModifier)
                    .padding(horizontal = D.segmentPadH),
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        color = P.InkStrong,
                        fontSize = fontSize.cssSp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SundownFontFamily,
                        shadow = Shadow(
                            if (selected) Color(0xFF17212A) else Color(0xFF36434E),
                            Offset(0f, -1f), 1f,
                        ),
                    ),
                )
            }
            if (index != items.lastIndex) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(P.TabDivider))
            }
        }
    }
}
