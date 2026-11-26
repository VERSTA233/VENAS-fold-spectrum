package com.venas.foldspectrum.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import org.junit.Assert.*
import org.junit.Test

class SpectrumBandsTest {
    @Test fun oneCodeQuantizationResidueCannotCreateAWidebandPedestal() {
        val layout = SpectrumMath.bandLayout(1024, 48000)
        for (re in -1..1) for (im in -1..1) {
            val fft = ByteArray(1024)
            for (bin in 1 until 512) {
                fft[bin * 2] = re.toByte()
                fft[bin * 2 + 1] = im.toByte()
            }
            SpectrumMath.bands(fft, layout).forEach { assertEquals(SpectrumMath.FLOOR_DB, it, 0f) }
        }
    }

    @Test fun signalsAboveQuantizationFloorRetainTheirAmplitudeAtEveryFrequency() {
        val layout = SpectrumMath.bandLayout(1024, 48000)
        for (bin in listOf(1, 21, 107, 213, 426)) {
            for (code in listOf(-100, -2, 2, 100)) {
                val fft = ByteArray(1024) { -1 }
                fft[bin * 2] = code.toByte()
                fft[bin * 2 + 1] = 0
                val bands = SpectrumMath.bands(fft, layout)
                val expected = (20 * kotlin.math.log10(kotlin.math.abs(code) / (128 * kotlin.math.sqrt(2.0)))).toFloat()
                assertEquals(1, bands.count { it > SpectrumMath.FLOOR_DB })
                assertEquals(expected, bands.max(), .0001f)
            }
        }
    }

    @Test fun everyVisibleFftBinBelongsToExactlyOneBarAcrossCaptureFormats() {
        for (rate in listOf(8000, 44100, 48000, 96000)) {
            for (size in listOf(128, 512, 1024)) {
                val layout = SpectrumMath.bandLayout(size, rate)
                val first = maxOf(1, ceil(20.0 * size / rate).toInt())
                val last = minOf(size / 2, floor(20000.0 * size / rate).toInt())
                val bins = layout.flatMap { (it.firstBin..it.lastBin).toList() }
                assertEquals("$rate Hz / $size", (first..last).toList(), bins)
                assertEquals(20.0, layout.first().lowHz, 0.0)
                assertTrue(layout.all { it.lowHz >= 20 && it.highHz <= 20000 && it.lowHz < it.highHz })
                layout.zipWithNext().forEach { (a, b) -> assertEquals(a.highHz, b.lowHz, 0.0) }
                layout.forEachIndexed { index, band ->
                    assertEquals(index.toFloat() / layout.size, SpectrumMath.frequencyPosition(band.lowHz, layout)!!, .00001f)
                    assertEquals((index + 1f) / layout.size, SpectrumMath.frequencyPosition(band.highHz, layout)!!, .00001f)
                }
            }
        }
    }

    @Test fun equalWidthBarsCompressLowFrequencyTicksWithoutReusingBins() {
        val layout = SpectrumMath.bandLayout(1024, 48000)
        assertEquals(1, layout[0].firstBin)
        assertEquals(1, layout[0].lastBin)
        assertEquals(2, layout[1].firstBin)
        assertEquals(2, layout[1].lastBin)
        assertEquals(70.3125, layout[0].highHz, 0.0)
        assertEquals(0f, SpectrumMath.frequencyPosition(20.0, layout)!!, 0f)
        assertEquals(1f, SpectrumMath.frequencyPosition(20000.0, layout)!!, 0f)
        for (hz in listOf(100.0, 200.0, 1000.0, 5000.0)) {
            val formerLogPosition = ln(hz / 20) / ln(20000.0 / 20)
            assertTrue(SpectrumMath.frequencyPosition(hz, layout)!! < formerLogPosition)
        }
        assertNull(SpectrumMath.frequencyPosition(100.0, emptyList()))
        assertNull(SpectrumMath.frequencyPosition(20000.0, SpectrumMath.bandLayout(1024, 8000)))
    }

    @Test fun anIsolatedToneCannotLightNeighbouringBarsAndDcIsExcluded() {
        val layout = SpectrumMath.bandLayout(1024, 48000)
        for (bin in 1..426) {
            val fft = ByteArray(1024)
            fft[0] = 127 // DC must not leak into the first bar.
            fft[bin * 2] = 80
            val levels = SpectrumMath.bands(fft, layout)
            assertEquals("bin $bin", 1, levels.count { it > SpectrumMath.FLOOR_DB })
        }
        val dc = ByteArray(1024).apply { this[0] = 127 }
        assertTrue(SpectrumMath.bands(dc, layout).all { it == SpectrumMath.FLOOR_DB })
        val lowRateLayout = SpectrumMath.bandLayout(1024, 8000)
        val nyquist = ByteArray(1024).apply { this[1] = -100 }
        val levels = SpectrumMath.bands(nyquist, lowRateLayout)
        assertEquals(1, levels.count { it > SpectrumMath.FLOOR_DB })
        assertTrue(levels.last() > -10f)
    }
}
