package com.venas.foldspectrum.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.venas.foldspectrum.BuildConfig
import com.venas.foldspectrum.core.Capture
import com.venas.foldspectrum.core.CrestWindow
import com.venas.foldspectrum.core.FrameDelay
import com.venas.foldspectrum.core.SpectrumMath
import com.venas.foldspectrum.core.SpectrumBand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class AudioFrame(
    val capture: Capture = Capture.IDLE,
    val bands: FloatArray = FloatArray(0),
    val bandLayout: List<SpectrumBand> = emptyList(),
    val barReferenceCount: Int = 0,
    val wave: FloatArray = FloatArray(96),
    val levelDb: Float? = null,
    val dynamicDb: Float? = null,
    val lastFrameAt: Long = 0,
)

/** Captures session 0 output mix. Never opens the microphone or MediaProjection. */
class AudioCapture(private val audioManager: AudioManager) {
    private val thread = HandlerThread("VenasAudio").apply { start() }
    private val worker = Handler(thread.looper)
    private val mutable = MutableStateFlow(AudioFrame())
    val state = mutable.asStateFlow()
    private var captured = AudioFrame()
    private var presentationDelayMs = 0L
    private val presentation = FrameDelay<AudioFrame>()
    private val presentNext = object : Runnable {
        override fun run() {
            presentation.takeDue(SystemClock.elapsedRealtime())?.let { frame ->
                mutable.value = frame
            }
            schedulePresentation()
        }
    }
    private var visualizer: Visualizer? = null
    private var watchdog: Runnable? = null
    private var generation = 0
    private var alive = true
    private val crest = CrestWindow()
    private val measurement = Visualizer.MeasurementPeakRms()
    private var measuring = false
    private var lastDiagnostic = 0L
    private var lastMetricPublication = 0L
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun mediaOutputs(): List<AudioDeviceInfo> = if (Build.VERSION.SDK_INT >= 33) {
        audioManager.getAudioDevicesForAttributes(mediaAttributes)
    } else emptyList()

    fun start() = worker.post {
        if (!alive || visualizer != null) return@post
        val run = ++generation
        captured = AudioFrame(capture = Capture.STARTING)
        mutable.value = captured
        crest.clear()
        lastMetricPublication = 0L
        try {
            val v = Visualizer(0)
            visualizer = v
            v.enabled = false
            val limits = Visualizer.getCaptureSizeRange()
            v.captureSize = 1024.coerceIn(limits[0], limits[1])
            v.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
            measuring = v.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS) == Visualizer.SUCCESS
            val rate = minOf(Visualizer.getMaxCaptureRate(), 30_000)
            var previousFft: ByteArray? = null
            var sameSince = 0L
            var previousAt = 0L
            var maxGap = 0L
            var maxUnchanged = 0L
            var maxNonzeroUnchanged = 0L
            var previousWave: ByteArray? = null
            var waveSameSince = 0L
            var previousWaveAt = 0L
            var maxWaveGap = 0L
            var maxWaveUnchanged = 0L
            var maxNonzeroWaveUnchanged = 0L
            var layoutRate = 0
            var layoutSize = 0
            var bandLayout = emptyList<SpectrumBand>()
            var barReferenceCount = 0
            var checkedSignalLatency = false
            var frames = 0L
            var nonzeroFrames = 0L
            val result = v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    if (run != generation || waveform == null) return
                    if (BuildConfig.DEBUG) {
                        val now = SystemClock.elapsedRealtime()
                        if (previousWaveAt != 0L) maxWaveGap = maxOf(maxWaveGap, now - previousWaveAt)
                        previousWaveAt = now
                        if (previousWave?.contentEquals(waveform) == true) {
                            maxWaveUnchanged = maxOf(maxWaveUnchanged, now - waveSameSince)
                            // Android's unsigned 8-bit PCM silence is 0x80.
                            if (waveform.any { it != 0x80.toByte() }) {
                                maxNonzeroWaveUnchanged = maxOf(maxNonzeroWaveUnchanged, now - waveSameSince)
                            }
                        } else {
                            previousWave = waveform.copyOf()
                            waveSameSince = now
                        }
                    }
                    // Waveform and FFT callbacks describe the same capture cycle.
                    // Publish them together, including the matching numeric values.
                    captured = captured.copy(wave = SpectrumMath.envelope(waveform))
                }
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (run != generation || fft == null) return
                    if (layoutRate != samplingRate || layoutSize != fft.size) {
                        val layout = SpectrumMath.displayLayout(fft.size, samplingRate / 1000)
                        bandLayout = layout.bands
                        barReferenceCount = layout.referenceBandCount
                        layoutRate = samplingRate
                        layoutSize = fft.size
                        Log.i(TAG, "FFT layout size=${fft.size} sampleRate=${samplingRate / 1000} bands=${bandLayout.size}")
                    }
                    val now = SystemClock.elapsedRealtime()
                    val nonzero = fft.any { it.toInt() != 0 }
                    if (nonzero && !checkedSignalLatency) {
                        checkedSignalLatency = true
                        refreshPresentationDelay()
                    }
                    if (BuildConfig.DEBUG) {
                        frames++
                        if (nonzero) nonzeroFrames++
                        if (previousAt != 0L) maxGap = maxOf(maxGap, now - previousAt)
                        previousAt = now
                        if (previousFft?.contentEquals(fft) == true) {
                            maxUnchanged = maxOf(maxUnchanged, now - sameSince)
                            if (nonzero) maxNonzeroUnchanged = maxOf(maxNonzeroUnchanged, now - sameSince)
                        } else {
                            previousFft = fft.copyOf()
                            sameSince = now
                        }
                    }
                    var level: Float? = null
                    var dynamic: Float? = null
                    if (measuring && v.getMeasurementPeakRms(measurement) == Visualizer.SUCCESS) {
                        level = measurement.mRms / 100f
                        dynamic = crest.add(now, measurement.mPeak / 100f, level)
                    }
                    val old = captured
                    // Keep measuring at capture cadence for the one-second crest
                    // window, but publish both numeric readouts only twice a second.
                    val publishMetrics = lastMetricPublication == 0L || now - lastMetricPublication >= 500
                    if (publishMetrics) lastMetricPublication = now
                    captured = old.copy(
                        capture = Capture.READY,
                        bands = SpectrumMath.bands(fft, bandLayout), bandLayout = bandLayout,
                        barReferenceCount = barReferenceCount,
                        levelDb = if (publishMetrics) level else old.levelDb,
                        dynamicDb = if (publishMetrics) dynamic else old.dynamicDb,
                        lastFrameAt = now,
                    )
                    publishCapturedFrame(now)
                    if (BuildConfig.DEBUG && now - lastDiagnostic > 10_000) {
                        lastDiagnostic = now
                        Log.i(TAG, "frames=$frames nonzero=$nonzeroFrames rate=${samplingRate / 1000} rms=$level peak=${measurement.mPeak / 100f} crest=$dynamic maxGapMs=$maxGap unchangedFftMs=$maxUnchanged nonzeroUnchangedFftMs=$maxNonzeroUnchanged waveGapMs=$maxWaveGap unchangedWaveMs=$maxWaveUnchanged nonzeroUnchangedWaveMs=$maxNonzeroWaveUnchanged")
                        maxGap = 0L
                        maxUnchanged = 0L
                        maxNonzeroUnchanged = 0L
                        maxWaveGap = 0L
                        maxWaveUnchanged = 0L
                        maxNonzeroWaveUnchanged = 0L
                    }
                }
            }, rate, true, true)
            check(result == Visualizer.SUCCESS) { "setDataCaptureListener=$result" }
            check(v.setEnabled(true) == Visualizer.SUCCESS) { "无法启用 Visualizer" }
            val outputIds = mediaOutputs().map { it.id }.sorted()
            presentationDelayMs = bluetoothPresentationDelay()
            Log.i(TAG, "START session=0 size=${v.captureSize} rate=$rate measurements=$measuring")
            val watch = object : Runnable {
                private val startedAt = SystemClock.elapsedRealtime()
                override fun run() {
                    if (run != generation || visualizer == null) return
                    // A connected Bluetooth device can remain available while the
                    // user selects the phone speaker. Device-added callbacks alone
                    // do not cover that switch; inspect media routing as well.
                    if (mediaOutputs().map { it.id }.sorted() != outputIds) {
                        Log.i(TAG, "Media output route changed; rebuilding capture")
                        restart()
                        return
                    }
                    refreshPresentationDelay()
                    val now = SystemClock.elapsedRealtime()
                    val last = captured.lastFrameAt.takeIf { it > 0 } ?: startedAt
                    // Missing callbacks are a transport failure. Zero-valued callbacks are valid silence.
                    if (now - last > 6000) {
                        fail("音频接口未返回采样回调")
                    } else worker.postDelayed(this, 1000)
                }
            }
            watchdog = watch
            worker.postDelayed(watch, 1000)
        } catch (e: Exception) {
            fail("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun restart() { stop(); start() }

    /**
     * Find N6 / Android 17 can repeat ~300 ms of nonzero samples each ring wrap
     * with A2DP. Bypass Visualizer's latency read-back on this tested configuration.
     * Queue the resulting samples for the system-reported output latency instead.
     * This changes analysis timing only, never the audio route or volume.
     * See docs/BLUETOOTH_CAPTURE.md for evidence and non-SDK API limitations.
     */
    private fun bluetoothPresentationDelay(): Long {
        if (!Build.MANUFACTURER.equals("OPPO", ignoreCase = true) ||
            Build.MODEL != "PLP110" || Build.VERSION.SDK_INT != 37) return 0
        var control: AudioEffect? = null
        return try {
            // Query media routing, not all connected devices. Speaker and mixed
            // speaker/Bluetooth routes keep the original native capture timing.
            val outputs = mediaOutputs()
            if (outputs.isEmpty() || outputs.any { it.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }) {
                Log.i(TAG, "Standard capture: media output types=${outputs.map { it.type }}")
                return 0
            }
            val latency = outputLatencyMs()
            // Without a usable timing estimate, keep the normal native path.
            // Never silently replace it with uncompensated, visibly early data.
            check(latency in 1..3000) { "invalid output latency=$latency" }
            val effectClass = AudioEffect::class.java
            val constructor = effectClass.getConstructor(UUID::class.java, UUID::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            // These non-SDK overloads remain accessible on the tested ROM. Do not
            // relax hidden-API enforcement; an inaccessible API leaves normal
            // Visualizer capture running. Two shorts encode a four-byte integer.
            val parameter = effectClass.getMethod("setParameter", IntArray::class.java, ShortArray::class.java)
            val visualizerType = UUID.fromString("e46b26a0-dddd-11db-8afd-0002a5d5c51b")
            control = constructor.newInstance(visualizerType,
                UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210"), 1, 0) as AudioEffect
            check(control.descriptor.type == visualizerType)
            // VISUALIZER_PARAM_LATENCY = 2. Attach briefly to the existing session
            // 0 analysis effect; do not enable/disable it through this handle.
            val status = parameter.invoke(control, intArrayOf(2), shortArrayOf(0, 0)) as Int
            check(status == AudioEffect.SUCCESS) { "setParameter=$status" }
            Log.i(TAG, "A2DP capture compatibility: read-back=0ms presentationDelay=${latency}ms effect=${control.id}")
            latency.toLong()
        } catch (e: Exception) {
            Log.w(TAG, "A2DP capture compatibility unavailable; using standard capture: ${e.javaClass.simpleName}: ${e.message}")
            0L
        } finally {
            runCatching { control?.release() }
        }
    }

    private fun outputLatencyMs(): Int = AudioManager::class.java
        .getMethod("getOutputLatency", Int::class.javaPrimitiveType)
        .invoke(audioManager, AudioManager.STREAM_MUSIC) as Int

    private fun refreshPresentationDelay() {
        if (presentationDelayMs == 0L) return
        runCatching {
            val latency = outputLatencyMs().toLong()
            // Ignore small estimate jitter; a real mode change can be hundreds
            // of milliseconds even when the output device ID stays the same.
            if (latency in 1..3000 && kotlin.math.abs(latency - presentationDelayMs) >= 20) {
                presentation.shiftBy(latency - presentationDelayMs)
                Log.i(TAG, "A2DP presentation delay updated: ${presentationDelayMs}ms -> ${latency}ms")
                presentationDelayMs = latency
                schedulePresentation()
            }
        }
    }

    fun clearTrackMeasurements() = worker.post {
        crest.clear()
        lastMetricPublication = 0L
        clearPresentation()
        captured = captured.copy(bands = FloatArray(0), wave = FloatArray(96), levelDb = null, dynamicDb = null)
        mutable.value = mutable.value.copy(bands = FloatArray(0), wave = FloatArray(96), levelDb = null, dynamicDb = null)
    }

    fun stop() = worker.post {
        release()
        captured = AudioFrame()
        mutable.value = captured
    }

    private fun fail(message: String) {
        Log.e(TAG, message)
        release()
        captured = AudioFrame(capture = Capture.UNAVAILABLE)
        mutable.value = captured
    }

    private fun publishCapturedFrame(now: Long) {
        if (presentationDelayMs == 0L) mutable.value = captured
        else {
            presentation.add(captured, now, presentationDelayMs)
            schedulePresentation()
        }
    }

    private fun schedulePresentation() {
        worker.removeCallbacks(presentNext)
        presentation.nextDueAt?.let { due ->
            worker.postDelayed(presentNext, (due - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        }
    }

    private fun clearPresentation() {
        worker.removeCallbacks(presentNext)
        presentation.clear()
    }

    private fun release() {
        generation++
        watchdog?.let(worker::removeCallbacks)
        watchdog = null
        clearPresentation()
        presentationDelayMs = 0L
        visualizer?.let {
            runCatching { it.setDataCaptureListener(null, 0, false, false) }
            runCatching { it.enabled = false }
            runCatching { it.release() }
        }
        visualizer = null
        crest.clear()
    }

    fun close() = worker.post {
        alive = false
        release()
        captured = AudioFrame()
        mutable.value = captured
        thread.quitSafely()
    }

    companion object { const val TAG = "VenasAudio" }
}
