package com.venas.foldspectrum

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.widget.Toast
import com.venas.foldspectrum.audio.*
import com.venas.foldspectrum.core.*
import com.venas.foldspectrum.device.*
import com.venas.foldspectrum.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DeskState(
    val notificationGranted: Boolean = false,
    val audioGranted: Boolean = false,
    val hasHinge: Boolean = false,
    val angle: Float? = null,
    val desk: Boolean = false,
    val entering: Boolean = false,
    val manualLandscape: Boolean = false,
    val flip: Boolean = false,
    val nonFoldMode: Boolean = false,
    val keepScreenOn: Boolean = true,
    val followSystemBrightness: Boolean = true,
    val brightness: Float = .5f,
    val burnInEnabled: Boolean = true,
    val spectrumGainEnabled: Boolean = false,
    val spectrumGainDb: Float = 0f,
    val mediaVolume: Int = 0,
    val minMediaVolume: Int = 0,
    val maxMediaVolume: Int = 0,
    val volumeFixed: Boolean = true,
    val hasOrientationSensor: Boolean = false,
    val phonePosture: PhonePosture = PhonePosture.UNKNOWN,
    val coverError: String? = null,
    val displayDetail: String = "尚未请求外屏",
    val media: NowPlaying? = null,
    val changingTrack: Boolean = false,
    val mediaDisappeared: Boolean = false,
    val audio: AudioFrame = AudioFrame(),
) {
    val displayGainDb get() = if (spectrumGainEnabled) spectrumGainDb else 0f
    val permissionsReady get() = notificationGranted && audioGranted
    val status: DisplayStatus get() = displayStatus(
        if (mediaDisappeared) Playback.LOADING else media?.playback ?: Playback.STOPPED,
        audio.capture, changingTrack)
}

class DeskController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = context.getSharedPreferences("venas", Context.MODE_PRIVATE)
    val cover = CoverDisplay()
    private val sensor = HingeSensor(context, ::onAngle)
    private val phoneSensor = PhoneOrientationSensor(context, ::onPhoneOrientation)
    private val repository = MediaRepository(context, scope)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val capture = AudioCapture(audioManager)
    private val mutable = MutableStateFlow(DeskState(hasHinge = sensor.available,
        hasOrientationSensor = phoneSensor.available,
        flip = preferences.getBoolean("flip", false), nonFoldMode = preferences.getBoolean("nonFoldMode", false),
        keepScreenOn = preferences.getBoolean("keepScreenOn", true),
        burnInEnabled = preferences.getBoolean("burnInEnabled", true),
        spectrumGainEnabled = preferences.getBoolean("spectrumGainEnabled", false),
        spectrumGainDb = preferences.getFloat("spectrumGainDb", 0f).coerceIn(-30f, 30f),
        followSystemBrightness = preferences.getBoolean("followSystemBrightness", true),
        brightness = preferences.getFloat("brightness", .5f).coerceIn(.1f, 1f)))
    val state = mutable.asStateFlow()
    private var visible = false
    private var suppressed = false
    private var entryGeneration = 0
    private var foldExitJob: Job? = null
    private var phoneExitJob: Job? = null
    private var naturalLandscape = false
    private var phoneAngle = -1
    private var initialPostureJob: Job? = null
    private var initialPhysicalTent = false
    private var disappearJob: Job? = null
    private var changeJob: Job? = null
    private var changeStarted = 0L
    private var previousTrack: String? = null
    private var lastPlayer: String? = null
    private var captureWanted = false
    private val routing = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = routeChanged()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = routeChanged()
    }
    private var routeJob: Job? = null

    init {
        scope.launch { repository.state.collect(::onMedia) }
        scope.launch {
            capture.state.collect { audio ->
                mutable.update { it.copy(audio = audio) }
                if (mutable.value.changingTrack && audio.lastFrameAt > changeStarted &&
                    repository.state.value?.playback == Playback.PLAYING && repository.state.value?.trackKey != previousTrack) {
                    endTrackChange()
                }
            }
        }
    }

    fun foreground() {
        if (!visible) {
            // A reading retained from a prior foreground session is not current.
            mutable.update { it.copy(angle = null, phonePosture = PhonePosture.UNKNOWN) }
            phoneAngle = -1
            initialPhysicalTent = false
        }
        visible = true
        suppressed = false
        refreshPermissions()
        startPostureDetection()
        audioManager.registerAudioDeviceCallback(routing, Handler(Looper.getMainLooper()))
        evaluateEntry()
        updateCapture()
    }

    private fun startPostureDetection() {
        initialPostureJob?.cancel()
        sensor.stop()
        phoneSensor.stop()
        if (mutable.value.nonFoldMode) {
            phoneSensor.start()
            return
        }
        sensor.start()
        initialPostureJob = scope.launch {
            val tent = cover.isPhysicallyTent()
            if (visible && !mutable.value.nonFoldMode && mutable.value.angle == null) {
                initialPhysicalTent = tent
                Log.i("VenasFold", "initial physical TENT=$tent; hinge sample pending")
                evaluateEntry()
            }
        }
    }

    fun refreshPermissions() {
        val component = ComponentName(context, MediaAccessService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            .split(':').any { ComponentName.unflattenFromString(it) == component }
        val audio = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        mutable.update { it.copy(notificationGranted = enabled, audioGranted = audio) }
        if (visible && enabled) {
            NotificationListenerService.requestRebind(component)
            repository.start()
        } else repository.stop()
        if (!mutable.value.permissionsReady) exitDesk(false)
        evaluateEntry()
    }

    private fun onAngle(angle: Float) {
        if (!visible || mutable.value.nonFoldMode) return
        initialPhysicalTent = false
        mutable.update { it.copy(angle = angle) }
        if (!FoldRules.isEntry(angle)) suppressed = false
        evaluateEntry()
    }

    private fun onPhoneOrientation(degrees: Int) {
        if (!visible || !mutable.value.nonFoldMode) return
        phoneAngle = degrees
        val posture = PhoneOrientation.posture(degrees, naturalLandscape)
        mutable.update { it.copy(phonePosture = posture,
            manualLandscape = it.manualLandscape && posture != PhonePosture.LANDSCAPE) }
        if (posture == PhonePosture.PORTRAIT) suppressed = false
        evaluateEntry()
    }

    private fun evaluateEntry() {
        val s = mutable.value
        if (!visible || !s.permissionsReady) return
        if (s.nonFoldMode) {
            evaluatePhonePosture()
            return
        }
        val angle = s.angle
        if (angle == null) {
            if (initialPhysicalTent && !suppressed && !s.desk && !s.entering) enterDesk()
            return
        }
        if (FoldRules.isExit(angle)) {
            if ((s.desk || s.entering) && foldExitJob?.isActive != true) {
                foldExitJob = scope.launch {
                    delay(400)
                    if (visible && mutable.value.angle?.let(FoldRules::isExit) == true) {
                        Log.i("VenasFold", "exit: stable hinge angle=${mutable.value.angle}")
                        exitDesk(false)
                    }
                }
            }
        } else {
            foldExitJob?.cancel()
            foldExitJob = null
            if (FoldRules.isEntry(angle) && !suppressed && !s.desk && !s.entering) enterDesk()
        }
    }

    private fun evaluatePhonePosture() {
        val s = mutable.value
        // A deliberate manual entry works while upright. Once physically horizontal,
        // onPhoneOrientation releases this override and normal portrait exit resumes.
        if (s.manualLandscape) {
            phoneExitJob?.cancel()
            phoneExitJob = null
            if (!suppressed && !s.desk) enterDesk()
            return
        }
        if (s.phonePosture == PhonePosture.PORTRAIT) {
            if (s.desk && phoneExitJob?.isActive != true) {
                phoneExitJob = scope.launch {
                    delay(400)
                    if (visible && mutable.value.nonFoldMode && mutable.value.phonePosture == PhonePosture.PORTRAIT) {
                        Log.i("VenasFold", "exit: phone returned to portrait")
                        exitDesk(false)
                    }
                }
            }
        } else {
            phoneExitJob?.cancel()
            phoneExitJob = null
            if (s.phonePosture == PhonePosture.LANDSCAPE && !suppressed && !s.desk) enterDesk()
        }
    }

    /** UI rotation never exits fold mode; phone mode uses the independent physical sensor. */
    fun displayChanged(naturalLandscape: Boolean) {
        this.naturalLandscape = naturalLandscape
        if (mutable.value.nonFoldMode) onPhoneOrientation(phoneAngle)
        else evaluateEntry()
    }

    private fun enterDesk() {
        val generation = ++entryGeneration
        if (mutable.value.nonFoldMode) {
            // A normal phone uses its current display; never request a vendor cover state.
            Log.i("VenasFold", "enter: phone landscape")
            mutable.update { it.copy(desk = true, entering = false, coverError = null,
                displayDetail = "非折叠模式 · 当前屏幕") }
            updateCapture()
            return
        }
        Log.i("VenasFold", "enter: angle=${mutable.value.angle}")
        mutable.update { it.copy(entering = true, coverError = null) }
        scope.launch {
            val result = cover.enter()
            if (generation != entryGeneration || !visible) {
                // exitDesk/background already queued the reset. A second stale reset could
                // otherwise cancel a newer foreground request.
                return@launch
            }
            mutable.update { it.copy(entering = false, desk = result.success,
                displayDetail = result.detail, coverError = if (result.success) null else "无法切换至外屏，请查看调试信息") }
            if (!result.success) suppressed = true
            updateCapture()
        }
    }

    fun retryCover() { suppressed = false; evaluateEntry() }

    fun rotateIntoDesk() {
        if (mutable.value.desk || mutable.value.entering) return
        mutable.update { it.copy(manualLandscape = !it.manualLandscape) }
        if (mutable.value.manualLandscape) {
            suppressed = false
            // Fold mode retains the existing hinge/physical-TENT gate and cover request.
            // Phone mode may enter on this explicit action without a physical rotation.
            evaluateEntry()
        }
    }

    fun exitDesk(userInitiated: Boolean = true) {
        ++entryGeneration
        foldExitJob?.cancel()
        foldExitJob = null
        phoneExitJob?.cancel()
        phoneExitJob = null
        if (userInitiated) suppressed = true
        mutable.update { it.copy(desk = false, entering = false, manualLandscape = false) }
        cover.reset()
        updateCapture()
    }

    fun background() {
        saveBrightness()
        Log.i("VenasFold", "exit: application background")
        visible = false
        initialPostureJob?.cancel()
        initialPhysicalTent = false
        sensor.stop()
        phoneSensor.stop()
        phoneAngle = -1
        audioManager.unregisterAudioDeviceCallback(routing)
        routeJob?.cancel()
        exitDesk(false)
        repository.stop()
    }

    fun setFlip(value: Boolean) {
        preferences.edit().putBoolean("flip", value).apply()
        mutable.update { it.copy(flip = value) }
    }

    fun setKeepScreenOn(value: Boolean) {
        preferences.edit().putBoolean("keepScreenOn", value).apply()
        mutable.update { it.copy(keepScreenOn = value) }
    }

    fun setBurnInEnabled(value: Boolean) {
        preferences.edit().putBoolean("burnInEnabled", value).apply()
        mutable.update { it.copy(burnInEnabled = value) }
    }

    fun setSpectrumGainEnabled(value: Boolean) {
        preferences.edit().putBoolean("spectrumGainEnabled", value).apply()
        mutable.update { it.copy(spectrumGainEnabled = value) }
    }

    fun setSpectrumGainDb(value: Float) {
        val gain = kotlin.math.round(value).coerceIn(-30f, 30f)
        if (gain == mutable.value.spectrumGainDb) return
        preferences.edit().putFloat("spectrumGainDb", gain).apply()
        mutable.update { it.copy(spectrumGainDb = gain) }
    }

    /** Read the active route's system media volume; never restore a saved volume. */
    fun refreshMediaVolume() {
        val stream = AudioManager.STREAM_MUSIC
        mutable.update { it.copy(mediaVolume = audioManager.getStreamVolume(stream),
            minMediaVolume = audioManager.getStreamMinVolume(stream),
            maxMediaVolume = audioManager.getStreamMaxVolume(stream),
            volumeFixed = audioManager.isVolumeFixed) }
    }

    /** Called only by an explicit slider gesture, never by initialization or polling. */
    fun setMediaVolume(value: Int) {
        val stream = AudioManager.STREAM_MUSIC
        if (audioManager.isVolumeFixed) return
        val target = value.coerceIn(audioManager.getStreamMinVolume(stream), audioManager.getStreamMaxVolume(stream))
        try {
            if (target != audioManager.getStreamVolume(stream)) audioManager.setStreamVolume(stream, target, 0)
        } catch (_: SecurityException) {
            Toast.makeText(context, "系统暂不允许调整媒体音量", Toast.LENGTH_SHORT).show()
        }
        refreshMediaVolume()
    }

    fun setFollowSystemBrightness(value: Boolean) {
        preferences.edit().putBoolean("followSystemBrightness", value).apply()
        mutable.update { it.copy(followSystemBrightness = value) }
    }

    /** Preview slider movement immediately; persist on release instead of writing every frame. */
    fun setBrightness(value: Float) {
        mutable.update { it.copy(brightness = value.coerceIn(.1f, 1f)) }
    }

    fun saveBrightness() {
        preferences.edit().putFloat("brightness", mutable.value.brightness).apply()
    }

    fun setNonFoldMode(value: Boolean) {
        if (value == mutable.value.nonFoldMode) return
        exitDesk(false)
        suppressed = false
        initialPhysicalTent = false
        phoneAngle = -1
        preferences.edit().putBoolean("nonFoldMode", value).apply()
        mutable.update { it.copy(nonFoldMode = value, angle = null, phonePosture = PhonePosture.UNKNOWN,
            coverError = null, displayDetail = if (value) "非折叠模式 · 等待横放" else "尚未请求外屏") }
        if (visible) startPostureDetection()
    }

    private fun onMedia(item: NowPlaying?) {
        val old = mutable.value.media
        if (item == null) {
            if (old != null && disappearJob?.isActive != true) {
                mutable.update { it.copy(mediaDisappeared = true, changingTrack = true) }
                disappearJob = scope.launch {
                    delay(3000)
                    if (repository.state.value == null) {
                        mutable.update { it.copy(media = null, mediaDisappeared = false, changingTrack = false) }
                        updateCapture()
                    }
                }
            }
            updateCapture()
            return
        }
        disappearJob?.cancel()
        val newTrack = old?.trackKey != item.trackKey
        val changedPlayer = lastPlayer != item.player
        lastPlayer = item.player
        mutable.update { it.copy(media = item, mediaDisappeared = false) }
        if (item.playback == Playback.PAUSED) endTrackChange()
        else if (newTrack) {
            beginTrackChange(old?.trackKey)
            capture.clearTrackMeasurements()
        }
        updateCapture()
        if (changedPlayer && captureWanted || newTrack && mutable.value.audio.capture == Capture.UNAVAILABLE && captureWanted) {
            capture.restart()
        }
    }

    private fun beginTrackChange(oldKey: String?) {
        previousTrack = oldKey
        changeStarted = SystemClock.elapsedRealtime()
        mutable.update { it.copy(changingTrack = true) }
        changeJob?.cancel()
        changeJob = scope.launch {
            delay(3000)
            // A rejected skip or unavailable capture must not leave the label stuck at LOADING.
            if (repository.state.value?.playback != Playback.LOADING && !mutable.value.mediaDisappeared) endTrackChange()
        }
    }

    private fun endTrackChange() {
        changeJob?.cancel()
        mutable.update { it.copy(changingTrack = false) }
    }

    private fun updateCapture() {
        val s = mutable.value
        val wanted = visible && s.desk && s.permissionsReady && !s.mediaDisappeared &&
            s.media?.playback in setOf(Playback.PLAYING, Playback.LOADING)
        if (wanted == captureWanted) return
        captureWanted = wanted
        // The verified Bluetooth fix lives in AudioCapture. Retaining a paused
        // effect did not solve stalls and no longer needs a second lifecycle.
        if (wanted) capture.start() else capture.stop()
    }

    private fun routeChanged() {
        routeJob?.cancel()
        routeJob = scope.launch {
            delay(350)
            if (captureWanted) {
                Log.i("VenasAudio", "Output route changed, rebuilding Visualizer")
                capture.restart()
            }
        }
    }

    fun toggle() { if (mutable.value.media != null && !repository.toggle()) unsupportedControl() }
    fun skip(next: Boolean) {
        if (mutable.value.media == null) return
        if (repository.skip(next)) beginTrackChange(mutable.value.media?.trackKey) else unsupportedControl()
    }
    fun seek(position: Long) { if (!repository.seek(position)) unsupportedControl() }
    private fun unsupportedControl() = Toast.makeText(context, "当前播放器不支持此操作", Toast.LENGTH_SHORT).show()

    fun retryAudio() { if (captureWanted) capture.restart() }

    fun close() {
        background()
        capture.close()
        cover.close()
        scope.cancel()
    }
}
