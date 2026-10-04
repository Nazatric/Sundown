package com.sundown.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sundown.player.data.prefs.Prefs
import com.sundown.player.ui.components.*
import com.sundown.player.ui.icons.SIcon
import com.sundown.player.ui.icons.SundownIcon
import com.sundown.player.ui.theme.*

private val START_SCREENS = listOf("Artists", "Albums", "Songs", "Genres", "Playlists")

/**
 * Sources + Settings.
 *
 * SAF is the honest Android analogue of the File System Access API: the user
 * grants one tree, the grant is persisted, and it can be restored or revoked.
 * No fabricated "storage permission" is ever requested.
 */
@Composable
fun SourcesSheetContent(
    prefs: Prefs,
    trackCount: Int,
    albumCount: Int,
    sessionCount: Int,
    hasAccess: Boolean,
    checkingFolderAccess: Boolean = false,
    mediaStorePermission: Boolean,
    scanning: Boolean,
    onClose: () -> Unit,
    onPickFolder: () -> Unit,
    onRestoreAccess: () -> Unit,
    onRescan: () -> Unit,
    onDisconnect: () -> Unit,
    onAddFiles: () -> Unit,
    onGrantMediaAccess: () -> Unit,
    onScanDeviceMusic: () -> Unit,
    onSettings: ((Prefs) -> Prefs) -> Unit,
    onClearArtwork: () -> Unit,
    onEraseAll: () -> Unit,
) {
    SheetToolbar("Sources", onClose)
    Column(Modifier.verticalScroll(rememberScrollState())) {

        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            SundownIcon(SIcon.Library, 30.dp, Color(0xFFCFDDE7), strokeWidth = 1.5f)
            Column(Modifier.weight(1f)) {
                Text(
                    "$trackCount ${if (trackCount == 1) "Song" else "Songs"} · " +
                        "$albumCount ${if (albumCount == 1) "Album" else "Albums"}",
                    style = TextStyle(color = P.Ink, fontSize = 14.5f.cssSp, lineHeight = 20.cssSp, fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily),
                )
                Text(
                    prefs.treeName?.let { "Folder: $it" } ?: "No folder connected yet",
                    modifier = Modifier.padding(top = 3.dp),
                    style = TextStyle(color = Color(0xFFC2D2DD), fontSize = 12.cssSp, lineHeight = 17.cssSp, fontFamily = SundownFontFamily),
                )
                if (sessionCount > 0) {
                    Text(
                        "$sessionCount individually added ${if (sessionCount == 1) "file" else "files"}",
                        style = TextStyle(color = Color(0xFFC2D2DD), fontSize = 12.cssSp, lineHeight = 17.cssSp, fontFamily = SundownFontFamily),
                    )
                }
            }
        }

        SheetAction(
            label = if (prefs.treeUri != null) "Choose a Different Folder..." else "Choose Music Folder...",
            icon = SIcon.Folder, enabled = !scanning, onClick = onPickFolder,
        )
        if (prefs.treeUri != null && checkingFolderAccess) {
            SheetAction("Checking Folder Access...", SIcon.Folder, enabled = false, onClick = {})
        }
        if (prefs.treeUri != null && !hasAccess && !checkingFolderAccess) {
            SheetAction("Restore Folder Access", SIcon.Check, enabled = !scanning, highlight = true, onClick = onRestoreAccess)
        }
        if (prefs.treeUri != null && hasAccess) {
            SheetAction(
                label = if (scanning) "Scanning..." else "Rescan for Changes",
                icon = SIcon.Refresh, enabled = !scanning, onClick = onRescan,
            )
        }
        if (prefs.treeUri != null) {
            SheetAction("Disconnect Folder", SIcon.Trash, enabled = !scanning, danger = true, onClick = onDisconnect)
        }
        SheetAction("Add Individual Files...", SIcon.Files, enabled = !scanning, onClick = onAddFiles)
        if (mediaStorePermission) {
            SheetAction("Rescan Device Music", SIcon.Library, enabled = !scanning, onClick = onScanDeviceMusic)
        } else {
            SheetAction("Allow Device Music Access", SIcon.Library, enabled = !scanning, highlight = true, onClick = onGrantMediaAccess)
        }

        SheetSectionTitle("Start Screen")
        START_SCREENS.forEach { screen ->
            val selected = prefs.defaultTab == screen
            val (clickModifier, _) = rippleless({ onSettings { it.copy(defaultTab = screen) } })
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = D.startScreenMinHeight)
                    .background(if (selected) Color(0x59264C68) else Color.Transparent)
                    .then(clickModifier)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    screen,
                    style = TextStyle(
                        color = if (selected) Color(0xFFCDE9FF) else Color(0xFFEEF4F8),
                        fontSize = 14.cssSp, fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily,
                    ),
                )
                if (selected) SundownIcon(SIcon.Check, 18.dp, Color(0xFFBFE4FF))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x66253949)))
        }

        SheetSectionTitle("Playback & Library")
        SettingSwitchRow(
            "Rescan on launch",
            "Check the connected folder for new or removed music when the app opens.",
            prefs.autoRescan,
        ) { value -> onSettings { it.copy(autoRescan = value) } }
        SettingSwitchRow(
            "A–Z index",
            "Show the alphabetical jump bar beside Albums, Artists and Songs.",
            prefs.showIndex,
        ) { value -> onSettings { it.copy(showIndex = value) } }
        SettingSwitchRow(
            "High-quality artwork",
            "Use full-size covers in the grid. Turn off to save memory on huge libraries.",
            prefs.highArt,
        ) { value -> onSettings { it.copy(highArt = value) } }
        SettingSwitchRow(
            "Keep screen on",
            "Prevent the display from sleeping while music is playing.",
            prefs.keepAwake,
        ) { value -> onSettings { it.copy(keepAwake = value) } }
        SheetSectionTitle("Storage")
        SheetAction("Clear Artwork Cache", SIcon.Disc, enabled = !scanning, onClick = onClearArtwork)
        SheetAction("Erase Library Data...", SIcon.Trash, enabled = !scanning, danger = true, onClick = onEraseAll)

        Text(
            "Your folder stays on your device. Tags and artwork previews are cached only by this app.",
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp),
            style = TextStyle(color = Color(0xFFB9C9D4), fontSize = 11.5f.cssSp, lineHeight = 17.cssSp, fontFamily = SundownFontFamily),
        )
    }
}

@Composable
private fun SettingSwitchRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = D.settingRowMinHeight)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = TextStyle(color = Color(0xFFF0F5F9), fontSize = 14.cssSp, lineHeight = 19.cssSp, fontWeight = FontWeight.Bold, fontFamily = SundownFontFamily),
            )
            Text(
                description,
                modifier = Modifier.padding(top = 2.dp),
                style = TextStyle(color = Color(0xFFB9C9D4), fontSize = 11.5f.cssSp, lineHeight = 16.cssSp, fontFamily = SundownFontFamily),
            )
        }
        MetalSwitch(checked, onCheckedChange = onChange)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x66253949)))
}
