package com.venas.foldspectrum

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.venas.foldspectrum.media.MediaAccessService
import com.venas.foldspectrum.ui.VenasApp
import com.venas.foldspectrum.ui.SettingsActions
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private lateinit var controller: DeskController
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stoppedJob: Job? = null
    private var isStarted = false
    private var appliedDesk = false
    private var appliedNonFoldMode = false
    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        controller.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.black)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller = DeskController(applicationContext)
        activityScope.launch {
            controller.state.collect { state ->
                if (state.desk != appliedDesk || state.nonFoldMode != appliedNonFoldMode) {
                    applyWindow(state.desk, state.nonFoldMode)
                }
                applyDisplayPreferences(window, state)
            }
        }
        setContent {
            val state by controller.state.collectAsState()
            val settings = remember(controller) {
                SettingsActions(controller::setFlip, controller::setNonFoldMode,
                    controller::setKeepScreenOn, controller::setFollowSystemBrightness,
                    controller::setBrightness, controller::saveBrightness,
                    controller::setBurnInEnabled, controller::refreshMediaVolume, controller::setMediaVolume)
            }
            BackHandler(state.desk || state.entering) { controller.exitDesk() }
            VenasApp(state, onAudioPermission = ::requestAudioPermission,
                onNotificationPermission = ::requestNotificationPermission,
                settings = settings, onToggle = controller::toggle,
                onSkip = controller::skip, onSeek = controller::seek,
                onRetryCover = controller::retryCover, onRetryAudio = controller::retryAudio)
        }
    }

    override fun onStart() {
        super.onStart()
        isStarted = true
        stoppedJob?.cancel()
        updateDisplayPosture()
        controller.foreground()
    }

    override fun onResume() {
        super.onResume()
        controller.refreshPermissions()
        updateDisplayPosture()
    }

    override fun onStop() {
        super.onStop()
        isStarted = false
        stoppedJob?.cancel()
        stoppedJob = activityScope.launch {
            // Cover hand-off AND later display rotations may briefly stop the
            // Activity. Keep the desk until an actual background stay is confirmed.
            delay(1500)
            if (!isStarted) controller.background()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && appliedDesk) hideBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        android.util.Log.i("VenasFold", "display=${display?.displayId} size=${newConfig.screenWidthDp}x${newConfig.screenHeightDp} orientation=${newConfig.orientation}")
        updateDisplayPosture()
    }

    private fun updateDisplayPosture() {
        // Display.Mode reports native panel dimensions, independent of the app's rotation lock.
        val mode = display?.mode
        controller.displayChanged(mode != null && mode.physicalWidth > mode.physicalHeight)
    }

    private fun applyWindow(desk: Boolean, nonFoldMode: Boolean) {
        appliedDesk = desk; appliedNonFoldMode = nonFoldMode
        if (desk) {
            // ColorOS can pin the display rotation in TENT even when the Activity
            // requests the opposite landscape. Keep one base orientation and flip
            // the app content (including its Dialog) instead, in both desk modes.
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            hideBars()
        } else {
            requestedOrientation = if (nonFoldMode) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun hideBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun requestAudioPermission() {
        val prefs = getSharedPreferences("venas", MODE_PRIVATE)
        if (prefs.getBoolean("audioAsked", false) && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            prefs.edit().putBoolean("audioAsked", true).apply()
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestNotificationPermission() {
        val component = ComponentName(this, MediaAccessService::class.java)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        runCatching { startActivity(detail) }.onFailure {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun onDestroy() {
        controller.close()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activityScope.cancel()
        super.onDestroy()
    }
}
