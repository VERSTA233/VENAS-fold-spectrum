package com.venas.foldspectrum.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class WaveformTest {
    @Test fun silenceAndInactivePlaybackKeepTheDotBaseline() {
        for (i in 0 until 96) {
            assertEquals(0f, waveformTarget(FloatArray(96), i), 0f)
            assertEquals(0f, waveformTarget(null, i), 0f)
        }
    }

    @Test fun neighboringColumnsSoftenAnIsolatedSpikeWithoutMixingPolarity() {
        val source = floatArrayOf(-.02f, .04f, -.3f, .2f, -.02f, .04f)
        val original = source.copyOf()
        val shown = FloatArray(source.size) { waveformTarget(source, it) }
        assertTrue(abs(shown[2] - shown[0]) < abs(source[2] - source[0]))
        assertTrue(abs(shown[3] - shown[1]) < abs(source[3] - source[1]))
        assertTrue(shown[2] < shown[0])
        assertTrue(shown[3] > shown[1])
        for (i in shown.indices step 2) {
            assertTrue(shown[i] <= 0f)
            assertTrue(shown[i + 1] >= 0f)
        }
        assertArrayEquals(original, source, 0f)
    }

    @Test fun uniformEnvelopeKeepsItsAmplitudeIncludingBothEdges() {
        val source = FloatArray(96) { if (it % 2 == 0) -.6f else .8f }
        for (i in source.indices) {
            assertEquals(source[i], waveformTarget(source, i), .00001f)
        }
        assertEquals(-1f, waveformTarget(floatArrayOf(-1f, 1f), 0), 0f)
        assertEquals(1f, waveformTarget(floatArrayOf(-1f, 1f), 1), 0f)
    }
}
