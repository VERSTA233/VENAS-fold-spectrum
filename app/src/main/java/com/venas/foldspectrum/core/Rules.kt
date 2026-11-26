package com.venas.foldspectrum.core

import kotlin.math.*

/** Entry and exit thresholds intentionally have a dead band. */
object FoldRules {
    fun isEntry(angle: Float) = angle.isFinite() && angle in 10f..150f
    fun isExit(angle: Float) = angle.isFinite() && (angle <= 5f || angle > 165f)
    /** With no override Android prints only Committed; otherwise use Base exclusively. */
    fun isPhysicalTent(stateReport: String): Boolean {
        val lines = stateReport.lineSequence().map(String::trim).toList()
        val base = lines.firstOrNull { it.startsWith("Base state:") }
        val physical = base ?: if (lines.any { it.startsWith("Override state:") }) return false
            else lines.firstOrNull { it.startsWith("Committed state:") || it.startsWith("state exit=0 Committed state:") }
        return physical != null && Regex("\\bname=['\"]TENT['\"]").containsMatchIn(physical)
    }
    fun coverState(manufacturer: String): Int = when {
        manufacturer.contains("honor", true) -> 4
        manufacturer.contains("samsung", true) || manufacturer.contains("oppo", true) ||
            manufacturer.contains("oneplus", true) || manufacturer.contains("vivo", true) -> 1
        manufacturer.contains("xiaomi", true) -> 0
        else -> 3
    }
}

enum class Playback { PLAYING, PAUSED, LOADING, STOPPED }
enum class Capture { IDLE, STARTING, READY, UNAVAILABLE }
enum class DisplayStatus { PLAYING, PAUSED, LOADING, UNSUPPORTED }

fun displayStatus(playback: Playback, capture: Capture, changingTrack: Boolean): DisplayStatus = when {
    playback == Playback.PAUSED -> DisplayStatus.PAUSED
    playback == Playback.LOADING || changingTrack -> DisplayStatus.LOADING
    capture == Capture.UNAVAILABLE -> DisplayStatus.UNSUPPORTED
    capture != Capture.READY -> DisplayStatus.LOADING
    else -> DisplayStatus.PLAYING
}

data class SessionCandidate(val key: String, val playing: Boolean, val updatedAt: Long)

fun chooseSession(candidates: List<SessionCandidate>, previous: String?): String? =
    candidates.filter { it.playing }.maxByOrNull { it.updatedAt }?.key
        ?: candidates.firstOrNull { it.key == previous }?.key
        ?: candidates.maxByOrNull { it.updatedAt }?.key

fun positionAt(position: Long, updatedAt: Long, now: Long, speed: Float, playing: Boolean, duration: Long): Long {
    val delta = if (playing && updatedAt > 0) ((now - updatedAt).coerceAtLeast(0) * speed).toLong() else 0L
    val result = (position.coerceAtLeast(0) + delta).coerceAtLeast(0)
    return if (duration > 0) result.coerceAtMost(duration) else result
}

/** Android's packed FFT: DC at [0], Nyquist at [1], complex pairs thereafter. */
data class SpectrumBand(val firstBin: Int, val lastBin: Int, val lowHz: Double, val highHz: Double)
data class SpectrumLayout(val bands: List<SpectrumBand>, val referenceBandCount: Int)

object SpectrumMath {
    private const val TARGET_BANDS = 64
    const val FLOOR_DB = -90f
    const val BAR_WIDTH_FRACTION = .62f

    /** Keep the previous frequency limits and column width; split real bins for more detail. */
    fun displayLayout(fftSize: Int, sampleRateHz: Int): SpectrumLayout {
        val original = bandLayout(fftSize, sampleRateHz)
        val visible = original.dropLast(2).toMutableList()
        val binHz = sampleRateHz.toDouble() / fftSize
        // Sparse capture formats must retain gaps as well as the original bar width.
        val limit = minOf(50, (original.size * .9f / BAR_WIDTH_FRACTION).toInt())
        while (visible.size < limit) {
            val index = visible.indices.maxByOrNull { visible[it].lastBin - visible[it].firstBin } ?: break
            val band = visible[index]
            if (band.firstBin == band.lastBin) break // Never reuse a bin to fill a column.
            val middle = (band.firstBin + band.lastBin) / 2
            val boundary = (middle + .5) * binHz
            visible[index] = SpectrumBand(band.firstBin, middle, band.lowHz, boundary)
            visible.add(index + 1, SpectrumBand(middle + 1, band.lastBin, boundary, band.highHz))
        }
        return SpectrumLayout(visible, original.size)
    }

    /** Partition real FFT bins once, merging only bins that share a logarithmic slot. */
    fun bandLayout(fftSize: Int, sampleRateHz: Int): List<SpectrumBand> {
        if (fftSize < 4 || sampleRateHz <= 0) return emptyList()
        val binHz = sampleRateHz.toDouble() / fftSize
        val first = max(1, ceil(20.0 / binHz).toInt())
        val last = min(fftSize / 2, floor(20000.0 / binHz).toInt())
        if (first > last) return emptyList()
        fun x(hz: Double) = (ln(hz.coerceIn(20.0, 20000.0) / 20.0) / ln(20000.0 / 20.0)).toFloat()
        fun slot(bin: Int) = (x(bin * binHz) * TARGET_BANDS).toInt().coerceAtMost(TARGET_BANDS - 1)
        val result = mutableListOf<SpectrumBand>()
        var start = first
        for (bin in first..last) {
            if (bin == last || slot(bin + 1) != slot(start)) {
                // Each group occupies one equal-width column. Keep its real
                // frequency boundaries so the tick marks use the same mapping.
                result += SpectrumBand(start, bin,
                    if (start == first) 20.0 else (start - .5) * binHz,
                    if (bin == last) minOf(20000.0, sampleRateHz / 2.0) else (bin + .5) * binHz)
                start = bin + 1
            }
        }
        return result
    }

    /** Low frequencies use fewer columns; tick marks track the equal-width groups. */
    fun frequencyPosition(hz: Double, layout: List<SpectrumBand>): Float? {
        if (layout.isEmpty() || hz < layout.first().lowHz || hz > layout.last().highHz) return null
        val index = layout.indexOfFirst { hz <= it.highHz }
        val band = layout[index]
        val withinBand = (hz - band.lowHz) / (band.highHz - band.lowHz)
        return ((index + withinBand) / layout.size).toFloat()
    }

    fun bands(fft: ByteArray, layout: List<SpectrumBand>): FloatArray {
        val result = FloatArray(layout.size) { FLOOR_DB }
        val nyquistBin = fft.size / 2
        layout.forEachIndexed { band, range ->
            var magnitude = 0.0
            for (bin in range.firstBin..range.lastBin) {
                val re = if (bin == nyquistBin) fft[1].toInt() else fft[2 * bin].toInt()
                val im = if (bin == nyquistBin) 0 else fft[2 * bin + 1].toInt()
                // Single-code FFT residue can otherwise form a high-frequency pedestal.
                if (abs(re) <= 1 && abs(im) <= 1) continue
                magnitude = max(magnitude, hypot(re.toDouble(), im.toDouble()))
            }
            // Fixed reference, never per-frame auto-normalization. Relative visual scale only.
            result[band] = if (magnitude == 0.0) FLOOR_DB else
                (20.0 * log10(magnitude / (128.0 * sqrt(2.0)))).toFloat().coerceIn(FLOOR_DB, 0f)
        }
        return result
    }

    /** Min/max envelopes preserve real polarity and peaks; silence stays empty. */
    fun envelope(wave: ByteArray, count: Int = 48): FloatArray {
        val output = FloatArray(count * 2)
        if (wave.isEmpty()) return output
        for (i in 0 until count) {
            var low = 0f
            var high = 0f
            val start = i * wave.size / count
            val end = ((i + 1) * wave.size / count).coerceAtLeast(start + 1).coerceAtMost(wave.size)
            for (j in start until end) {
                val value = ((wave[j].toInt() and 255) - 128) / 128f
                low = min(low, value); high = max(high, value)
            }
            output[i * 2] = low; output[i * 2 + 1] = high
        }
        return output
    }
}

/** Short-window crest factor from the platform's measured peak and RMS levels. */
class CrestWindow(private val windowMs: Long = 1000) {
    private data class Sample(val time: Long, val peak: Double, val power: Double)
    private val samples = ArrayDeque<Sample>()
    fun clear() = samples.clear()
    fun add(time: Long, peakDb: Float, rmsDb: Float): Float? {
        if (!peakDb.isFinite() || !rmsDb.isFinite() || rmsDb <= -96f) { clear(); return null }
        samples.addLast(Sample(time, peakDb.toDouble(), 10.0.pow(rmsDb / 10.0)))
        while (samples.size > 1 && samples.first().time < time - windowMs) samples.removeFirst()
        val rms = 10.0 * log10(samples.sumOf { it.power } / samples.size)
        return (samples.maxOf { it.peak } - rms).toFloat().coerceAtLeast(0f)
    }
}
