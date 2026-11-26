package com.venas.foldspectrum.core

import org.junit.Assert.*
import org.junit.Test

class SpectrumDisplayLayoutTest {
    @Test fun fiftyColumnsKeepLowFrequencyBinsAndTheExistingUpperLimit() {
        for (rate in listOf(44100, 48000)) {
            val original = SpectrumMath.bandLayout(1024, rate)
            val display = SpectrumMath.displayLayout(1024, rate)
            assertEquals(50, display.bands.size)
            assertEquals(original.size, display.referenceBandCount)
            assertEquals(original.take(4), display.bands.take(4))
            assertTrue(display.bands.take(4).all { it.firstBin == it.lastBin })
            assertEquals(original[original.lastIndex - 2].highHz, display.bands.last().highHz, 0.0)
            assertNotNull(SpectrumMath.frequencyPosition(16000.0, display.bands))
            // The existing width still leaves a positive gap between all 50 columns.
            assertTrue(SpectrumMath.BAR_WIDTH_FRACTION / display.referenceBandCount < 1f / display.bands.size)
        }
    }

    @Test fun addedColumnsPartitionTheOriginalBinsWithoutDuplicatesGapsOrFabricatedSamples() {
        for (rate in listOf(8000, 44100, 48000, 96000)) for (size in listOf(128, 512, 1024)) {
            val original = SpectrumMath.bandLayout(size, rate).dropLast(2)
            val display = SpectrumMath.displayLayout(size, rate).bands
            val expected = original.flatMap { (it.firstBin..it.lastBin).toList() }
            val actual = display.flatMap { (it.firstBin..it.lastBin).toList() }
            assertTrue(SpectrumMath.BAR_WIDTH_FRACTION / SpectrumMath.bandLayout(size, rate).size < 1f / display.size)
            assertEquals("$rate / $size", expected, actual)
            assertEquals(actual.size, actual.toSet().size)
            display.zipWithNext().forEach { (a, b) -> assertEquals(a.highHz, b.lowHz, 0.0) }
            for (bin in actual) {
                val fft = ByteArray(size)
                fft[bin * 2] = 80
                assertEquals(1, SpectrumMath.bands(fft, display).count { it > SpectrumMath.FLOOR_DB })
            }
        }
    }
}
