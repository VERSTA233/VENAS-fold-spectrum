package com.venas.foldspectrum.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayGainTest {
    @Test fun disabledGainPreservesSignal() {
        assertEquals(-42f, DisplayGain.decibels(-42f, 0f, -90f), 0f)
        assertEquals(1f, DisplayGain.amplitude(0f), 0f)
    }

    @Test fun gainUsesAmplitudeDecibelsAndCapsAtFullScale() {
        assertEquals(10f, DisplayGain.amplitude(20f), .0001f)
        assertEquals(-30f, DisplayGain.decibels(-42f, 12f, -90f), 0f)
        assertEquals(-54f, DisplayGain.decibels(-42f, -12f, -90f), 0f)
        assertEquals(.2512f, DisplayGain.amplitude(-12f), .0001f)
        assertEquals(0f, DisplayGain.decibels(-10f, 30f, -90f), 0f)
    }

    @Test fun silenceMustNotBecomeVisibleSignal() {
        assertEquals(-90f, DisplayGain.decibels(-90f, 30f, -90f), 0f)
        assertEquals(-96f, DisplayGain.decibels(-96f, 30f, -96f), 0f)
        assertEquals(0f, 0f * DisplayGain.amplitude(30f), 0f)
    }
}
