package com.sundown.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 1 CSS px == 1 dp. Justified in docs/PIXEL_SPEC.md: the web app ships
 * `viewport width=device-width`, which makes the CSS pixel equal the dp on
 * Android browsers.
 */
object D {
    // Status strip
    val statusHeight = 20.dp
    val statusNameLeft = 8.dp
    val batteryWidth = 22.dp
    val batteryHeight = 10.dp
    val batteryRight = 7.dp
    val batteryBottom = 4.dp

    // Toolbar
    val toolbarGap = 8.dp
    val toolbarPadH = 8.dp
    val toolbarPadTop = 8.dp
    val toolbarPadBottom = 10.dp
    val toolbarPadHWide = 12.dp

    val metalHeight = 35.dp
    val metalPadH = 11.dp
    val metalRadius = 6.dp
    val metalIcon = 15.dp
    val sourcesIcon = 16.dp
    val metalBigHeight = 44.dp
    val metalBigPadH = 18.dp
    val metalBigRadius = 7.dp

    val segmentHeight = 35.dp
    val segmentRadius = 6.dp
    val segmentPadH = 6.dp

    val searchHeight = 34.dp
    val searchRadius = 22.dp
    val searchPadH = 9.dp
    val searchIcon = 16.dp
    val searchWidthWide = 210.dp

    // Scan bar
    val scanPadV = 9.dp
    val scanPadH = 14.dp
    val scanGap = 12.dp
    val scanSpinner = 18.dp
    val scanMeterWidth = 90.dp
    val scanMeterHeight = 7.dp
    val scanMeterRadius = 5.dp

    // Filter toolbar
    val filterMinHeight = 46.dp
    val filterPadV = 6.dp
    val filterPadH = 16.dp
    val filterClear = 32.dp

    // Library surface
    val surfaceRadius = 9.dp
    val bottomFade = 46.dp

    // Grid
    val gridGapY = 30.dp
    val gridGapX = 10.dp
    val gridPadTop = 26.dp
    val gridPadRight = 30.dp
    val gridPadBottom = 30.dp
    val gridPadLeft = 14.dp
    val gridGapYWide = 36.dp
    val gridGapXWide = 14.dp

    // Album stack
    val sleeveRadius = 2.dp
    val tileNameTop = 20.dp
    val tileNameTopWide = 24.dp
    val groundShadowHeight = 12.dp
    val groundShadowInsetL = 4.dp
    val groundShadowInsetR = 3.dp
    val groundShadowBottom = 6.dp

    // A-Z rail
    val indexWidth = 24.dp
    val indexWidthWide = 20.dp
    val indexRight = 1.dp
    val indexRightWide = 5.dp

    // Songs
    val rowHeight = 62.dp
    val songsHeaderMinHeight = 52.dp
    val songsHeaderPadLeft = 14.dp
    val songsHeaderPadRight = 30.dp
    val rowLead = 40.dp
    val rowLeadWide = 44.dp
    val rowGap = 10.dp
    val rowGapWide = 12.dp
    val rowPadLeft = 10.dp
    val rowPadRight = 8.dp
    val rowArt = 40.dp
    val rowArtWide = 44.dp
    val rowAdd = 42.dp
    val rowScrollInset = 26.dp

    // Player
    val playerHeight = 108.dp
    val playerHeightWide = 126.dp
    val playerHeightLandscape = 84.dp
    val playerPadH = 10.dp
    val playerPadTop = 10.dp
    val playerPadBottom = 8.dp
    val npArt = 52.dp
    val npArtWide = 100.dp
    val npArtRadius = 5.dp

    val transport = 42.dp
    val transportPlay = 54.dp
    val transportWide = 48.dp
    val transportPlayWide = 64.dp
    val transportGap = 7.dp
    val transportIcon = 21.dp
    val transportPlayIcon = 27.dp
    val transportIconWide = 24.dp
    val transportPlayIconWide = 31.dp

    val timelineGap = 9.dp
    val elapsedWidth = 30.dp
    val remainingWidth = 34.dp
    val progressTrack = 8.dp
    val progressRadius = 6.dp
    val progressThumb = 19.dp

    val modePillHeight = 36.dp
    val modePillRadius = 20.dp
    val modeButton = 46.dp
    val modeIcon = 21.dp

    val volumeHeight = 34.dp
    val volumeRadius = 20.dp
    val volumeKnob = 32.dp
    val muteButton = 36.dp
    val muteIcon = 20.dp

    // Sheets
    val sheetRadius = 14.dp
    val sheetGrabW = 44.dp
    val sheetGrabH = 5.dp
    val sheetBarMinHeight = 50.dp
    val sheetBarPadV = 8.dp
    val sheetBarPadH = 10.dp
    val sheetClose = 34.dp
    val sheetCloseIcon = 16.dp

    val albumHeadingPadV = 20.dp
    val albumHeadingPadH = 18.dp
    val albumHeadingGap = 16.dp
    val detailArt = 112.dp
    val silverSmallMinHeight = 32.dp
    val silverSmallPadH = 10.dp
    val silverSmallPadV = 6.dp
    val silverSmallRadius = 5.dp
    val silverSmallIcon = 13.dp
    val favoriteWidth = 34.dp
    val favoriteIcon = 17.dp

    val albumTrackMinHeight = 52.dp
    val albumTrackNo = 30.dp
    val albumTrackPadV = 7.dp
    val albumTrackPadH = 10.dp

    val sheetActionMinHeight = 52.dp
    val sheetActionPadV = 10.dp
    val sheetActionPadH = 18.dp
    val sheetActionIcon = 20.dp

    val settingRowMinHeight = 60.dp
    val startScreenMinHeight = 48.dp
    val switchW = 52.dp
    val switchH = 30.dp
    val switchRadius = 16.dp
    val switchKnob = 25.dp
    val switchKnobOff = 2.dp
    val switchKnobOn = 22.dp

    // Empty state
    val emptyBadge = 84.dp
    val emptyBadgeIcon = 38.dp
    val emptyBodyMaxWidth = 330.dp

    // Toast
    val noticePadV = 11.dp
    val noticePadH = 14.dp
    val noticeRadius = 7.dp
    val noticeGap = 14.dp
    val noticeBottomGap = 14.dp

    // Equalizer
    val eqWidth = 15.dp
    val eqHeight = 16.dp
    val eqBar = 3.dp
}

/** Breakpoints mirroring the stylesheet's media queries. */
enum class WidthClass { Compact, Medium, Expanded, Wide }

@Composable
@ReadOnlyComposable
fun coverSize(widthDp: Dp): Dp {
    // phone: min(41vw, 216px) | >=860: clamp(150, 21vw, 224)
    val w = widthDp.value
    return if (w >= 860f) {
        (w * 0.21f).coerceIn(150f, 224f).dp
    } else {
        minOf(w * 0.41f, 216f).dp
    }
}
