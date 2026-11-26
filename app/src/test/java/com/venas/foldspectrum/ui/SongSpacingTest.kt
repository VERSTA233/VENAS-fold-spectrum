package com.venas.foldspectrum.ui

import org.junit.Assert.*
import org.junit.Test

class SongSpacingTest {
    @Test fun twoLinesAlignButOneLineUsesTheSameGap() {
        val gap = coverTitleGapPx(1f, 1f, 340f, 250f, 26, 15f)
        assertEquals(18f, gap, 0f)
        assertEquals(340f, 250 + gap + 2 * 26 + 5 + 15, 0f)
        assertEquals(314f, 250 + gap + 26 + 5 + 15, 0f)
    }

    @Test fun tallScreensStopAtMaximumWithoutStretchingTextSpacing() {
        val gap = coverTitleGapPx(1f, 1f, 500f, 250f, 26, 15f)
        assertEquals(24f, gap, 0f)
    }

    @Test fun smallScreensNeverRequestNegativeSpacing() {
        assertEquals(0f, coverTitleGapPx(1f, 1f, 280f, 250f, 26, 15f), 0f)
    }

    @Test fun maximumScalesWithLayoutAndPixelDensity() {
        assertEquals(90f, coverTitleGapPx(1.25f, 3f, 2000f, 500f, 98, 60f), 0f)
    }
}
