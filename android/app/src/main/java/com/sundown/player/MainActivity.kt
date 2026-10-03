package com.sundown.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.sundown.player.data.media.MediaStoreSource
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import com.sundown.player.ui.LibraryViewModel
import com.sundown.player.ui.SundownRoot
import com.sundown.player.ui.components.LocalArtworkLoader
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {

    private var onTreePicked: ((Uri) -> Unit)? = null
    private var onFilesPicked: ((List<Uri>, Boolean) -> Unit)? = null

    private val pickTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { onTreePicked?.invoke(it) }
    }

    private var permissionResult: ((Boolean) -> Unit)? = null

    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionResult?.invoke(granted)
        permissionResult = null
    }

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            var allGrantsPersisted = true
            uris.forEach { uri ->
                val persisted = runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
                }.getOrDefault(false)
                allGrantsPersisted = allGrantsPersisted && persisted
            }
            onFilesPicked?.invoke(uris, allGrantsPersisted)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            // No app-level 60 Hz preference: let Android use each device's native refresh rate.
            preferredRefreshRate = 0f
        }
        hideSystemStatusBar()

        setContent {
            val vm: LibraryViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            val playback by vm.playerState.collectAsStateWithLifecycle()
            val libraryPlayback by vm.libraryPlaybackState.collectAsStateWithLifecycle()


            // Match the source setting: keep the display awake only while audio
            // is actually playing, and always release the flag on pause.
            val keepAwake = state.prefs.keepAwake && playback.playing
            if (keepAwake) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            CompositionLocalProvider(LocalArtworkLoader provides vm.artwork) {
                SundownRoot(
                    vm = vm,
                    state = state,
                    playback = playback,
                    libraryPlayback = libraryPlayback,
                    onPickFolder = {
                        onTreePicked = { uri -> vm.connectFolder(uri); Unit }
                        runCatching { pickTree.launch(null) }
                            .onFailure { vm.toast("No file manager available to choose a folder.") }
                    },
                    onGrantMediaAccess = {
                        if (MediaStoreSource.hasReadPermission(this)) {
                            vm.rescanDeviceMusic()
                        } else {
                            permissionResult = { granted ->
                                if (granted) vm.rescanDeviceMusic() else vm.toast("Music library access was denied. You can grant it later in Android Settings.")
                            }
                            runCatching { requestAudioPermission.launch(MediaStoreSource.permissionForCurrentApi()) }
                                .onFailure { vm.toast("Android could not request music library access.") }
                        }
                    },
                    onPickFiles = {
                        onFilesPicked = { uris, persisted ->
                            vm.ingestFiles(uris)
                            if (!persisted) {
                                vm.toast("Some file access could not be saved; those files may need to be added again after restart.")
                            }
                        }
                        runCatching { pickFiles.launch(arrayOf("audio/*")) }
                            .onFailure { vm.toast("No file manager available to pick files.") }
                    },
                )
            }
        }
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemStatusBar()
    }

    private fun hideSystemStatusBar() {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            // Keep Android's navigation/gesture bar available for native Back;
            // only the Compose status strip is drawn by the app.
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
            isAppearanceLightNavigationBars = false
        }
    }
}
