package com.venas.foldspectrum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumPeakTest {
    @Test fun followsNewPeaksImmediatelyEvenWhileFalling() {
        val falling = SpectrumPeak.advance(-10f, -40f, .1f)
        assertTrue(falling > -40f && falling < -10f)
        assertEquals(-5f, SpectrumPeak.advance(falling, -5f, 1f / 60f), 0f)
    }

    @Test fun fallingCapNeverPassesThroughTheCurrentBar() {
        var peak = 0f
        repeat(600) {
            peak = SpectrumPeak.advance(peak, -30f, 1f / 60f)
            assertTrue(peak >= -30f)
        }
        assertEquals(-30f, peak, 0f)
    }

    @Test fun silenceSettlesAtTheFloorAndStaysThere() {
        var peak = 0f
        repeat(600) {
            val next = SpectrumPeak.advance(peak, -90f, 1f / 60f)
            assertTrue(next <= peak && next >= -90f)
            peak = next
        }
        assertEquals(-90f, peak, 0f)
        assertEquals(-90f, SpectrumPeak.advance(peak, -90f, 1f / 60f), 0f)
    }

    @Test fun fallingSpeedMatchesAcrossRefreshRates() {
        fun afterOneSecond(hz: Int): Float {
            var peak = 0f
            repeat(hz) { peak = SpectrumPeak.advance(peak, -60f, 1f / hz) }
            return peak
        }
        assertEquals(afterOneSecond(30), afterOneSecond(60), .001f)
        assertEquals(afterOneSecond(60), afterOneSecond(120), .001f)
    }
}
