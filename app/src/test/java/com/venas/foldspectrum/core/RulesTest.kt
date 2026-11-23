package com.venas.foldspectrum.core

import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test fun foldingHasSeparateEntryAndExitBounds() {
        assertFalse(FoldRules.isEntry(9.9f)); assertTrue(FoldRules.isEntry(10f))
        assertTrue(FoldRules.isEntry(120.1f)); assertTrue(FoldRules.isEntry(150f))
        assertFalse(FoldRules.isEntry(150.1f))
        assertFalse(FoldRules.isExit(165f)); assertTrue(FoldRules.isExit(165.1f))
        assertTrue(FoldRules.isExit(5f)); assertFalse(FoldRules.isExit(5.1f))
        assertFalse(FoldRules.isEntry(Float.NaN)); assertFalse(FoldRules.isExit(Float.NaN))
        assertEquals(1, FoldRules.coverState("OPPO"))
        assertEquals(0, FoldRules.coverState("Xiaomi")); assertEquals(4, FoldRules.coverState("HONOR"))
    }
    @Test fun playingSessionWinsButPauseRetainsTheSelectedPlayer() {
        val paused = SessionCandidate("netease", false, 20)
        val playing = SessionCandidate("poweramp", true, 10)
        assertEquals("poweramp", chooseSession(listOf(paused, playing), "netease"))
        assertEquals("poweramp", chooseSession(listOf(paused, playing.copy(playing = false)), "poweramp"))
        assertEquals("netease", chooseSession(listOf(paused, playing.copy(playing = false)), null))
    }
    @Test fun transitionAndPauseNeverBecomeUnsupported() {
        assertEquals(DisplayStatus.LOADING, displayStatus(Playback.PLAYING, Capture.UNAVAILABLE, true))
        assertEquals(DisplayStatus.PAUSED, displayStatus(Playback.PAUSED, Capture.UNAVAILABLE, false))
        assertEquals(DisplayStatus.UNSUPPORTED, displayStatus(Playback.PLAYING, Capture.UNAVAILABLE, false))
        assertEquals(DisplayStatus.PLAYING, displayStatus(Playback.PLAYING, Capture.READY, false))
    }
    @Test fun progressUsesMonotonicClockPlaybackSpeedAndClampsToDuration() {
        assertEquals(3000L, positionAt(1000, 100, 1100, 2f, true, 5000))
        assertEquals(1000L, positionAt(1000, 100, 1100, 2f, false, 5000))
        assertEquals(1500L, positionAt(1000, 100, 1100, 2f, true, 1500))
        assertEquals(1000L, positionAt(1000, 0, 1100, 2f, true, 5000))
    }
    @Test fun silenceNeverGeneratesSyntheticWaveOrSpectrum() {
        assertTrue(SpectrumMath.envelope(ByteArray(1024) { 128.toByte() }).all { it == 0f })
        assertTrue(SpectrumMath.bands(ByteArray(1024), SpectrumMath.bandLayout(1024, 48000)).all { it == -90f })
    }
    @Test fun fftUsesSignedComplexPairsAndRealFrequencyBins() {
        val fft = ByteArray(1024)
        fft[42] = -100 // 21 * 48000 / 1024 = 984.375 Hz
        val layout = SpectrumMath.bandLayout(1024, 48000)
        val bands = SpectrumMath.bands(fft, layout)
        val strongest = bands.indices.maxBy { bands[it] }
        assertTrue(21 in layout[strongest].firstBin..layout[strongest].lastBin)
        assertEquals(1, bands.count { it > SpectrumMath.FLOOR_DB })
        assertTrue(bands[strongest] > -10f)
        assertEquals(-90f, bands.last(), 0f)
    }
    @Test fun crestWindowDoesNotInventASilenceValue() {
        val window = CrestWindow()
        assertEquals(12f, window.add(0, -6f, -18f)!!, .001f)
        assertEquals(6f, window.add(2000, -12f, -18f)!!, .001f)
        assertNull(window.add(2100, -96f, -96f))
    }
}
