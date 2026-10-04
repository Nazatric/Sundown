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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sundown.player.data.media.MediaStoreSource
import com.sundown.player.ui.LibraryViewModel
import com.sundown.player.ui.SundownRoot
import com.sundown.player.ui.components.LocalArtworkLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var onTreePicked: ((Uri) -> Unit)? = null
    private var onFilesPicked: ((List<Uri>, Boolean) -> Unit)? = null
    private var libraryViewModel: LibraryViewModel? = null
    private var keepScreenOnRequested = false

    private val pickTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { onTreePicked?.invoke(it) }
    }

    private var permissionResult: ((Boolean) -> Unit)? = null
    private var audioPermissionRequestInFlight = false

    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        audioPermissionRequestInFlight = false
        val result = permissionResult
        permissionResult = null
        libraryViewModel?.refreshMediaStorePermission(scanIfAlreadyGranted = granted)
        result?.invoke(granted)
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.attributes = window.attributes.apply {
            // No app-level 60 Hz preference: let Android use each device's native refresh rate.
            preferredRefreshRate = 0f
        }
        hideSystemStatusBar()

        setContent {
            val vm: LibraryViewModel = viewModel()
            libraryViewModel = vm
            val state by vm.state.collectAsStateWithLifecycle()
            val playback by vm.playerState.collectAsStateWithLifecycle()
            val playbackProgress = vm.playbackProgress
            val libraryPlayback by vm.libraryPlaybackState.collectAsStateWithLifecycle()

            // The first launch asks for local audio access once. A denied request
            // leaves the user in the real empty state and can be retried from Sources.
            LaunchedEffect(state.booted) {
                if (state.booted && vm.consumeStartupAudioPermissionPrompt()) {
                    requestDeviceMusicAccess(vm)
                }
            }

            // Match the source setting: keep the display awake only while audio
            // is actually playing, and always release the flag on pause.
            val keepAwake = state.prefs.keepAwake && playback.playing
            SideEffect {
                keepScreenOnRequested = keepAwake
                applyKeepScreenOn()
            }

            CompositionLocalProvider(LocalArtworkLoader provides vm.artwork) {
                SundownRoot(
                    vm = vm,
                    state = state,
                    playback = playback,
                    playbackProgress = playbackProgress,
                    libraryPlayback = libraryPlayback,
                    onPickFolder = {
                        onTreePicked = { uri -> vm.connectFolder(uri); Unit }
                        runCatching { pickTree.launch(null) }
                            .onFailure { vm.toast("No file manager available to choose a folder.") }
                    },
                    onGrantMediaAccess = { requestDeviceMusicAccess(vm) },
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

    private fun requestDeviceMusicAccess(vm: LibraryViewModel) {
        if (MediaStoreSource.hasReadPermission(this)) {
            vm.refreshMediaStorePermission(scanIfAlreadyGranted = true)
            return
        }
        if (audioPermissionRequestInFlight) return

        audioPermissionRequestInFlight = true
        lifecycleScope.launch {
            try {
                vm.markAudioPermissionPrompted()
            } catch (cancelled: CancellationException) {
                audioPermissionRequestInFlight = false
                throw cancelled
            } catch (_: Exception) {
                vm.toast("Sundown could not save the permission-request state.")
            }
            permissionResult = { granted ->
                if (!granted) {
                    vm.toast("Music library access was denied. You can grant it later in Android Settings or Sources.")
                }
            }
            runCatching { requestAudioPermission.launch(MediaStoreSource.permissionForCurrentApi()) }
                .onFailure {
                    audioPermissionRequestInFlight = false
                    permissionResult = null
                    vm.toast("Android could not request music library access.")
                }
        }
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOn()
        // Always query PackageManager again after returning from a permission
        // dialog or Settings; the DataStore flag is only a prompt guard.
        if (permissionResult == null) libraryViewModel?.refreshMediaStorePermission()
    }

    override fun onPause() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemStatusBar()
            if (permissionResult == null) libraryViewModel?.refreshMediaStorePermission()
        }
    }

    private fun applyKeepScreenOn() {
        val shouldKeepOn = keepScreenOnRequested && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (shouldKeepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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
