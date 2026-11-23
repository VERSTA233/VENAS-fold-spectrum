package com.venas.foldspectrum.core

import org.junit.Assert.*
import org.junit.Test

class CoverProjectionTest {
    @Test fun unturnedCardKeepsItsOriginalCorners() {
        assertArrayEquals(floatArrayOf(0f, 0f, 600f, 0f, 600f, 600f, 0f, 600f),
            CoverProjection.corners(600f, 600f, 0f), .001f)
    }

    @Test fun nextPageHasARealShorterFarEdgeAndSlantedTopAndBottom() {
        val p = CoverProjection.corners(600f, 600f, -.5f)
        assertEquals(600f, p[7] - p[1], .001f)
        assertTrue(p[5] - p[3] < 480f)
        assertTrue(p[3] > p[1])
        assertTrue(p[5] < p[7])
        assertTrue(p[2] > p[0])
    }

    @Test fun previousPageMirrorsNextWithoutFlippingTheImage() {
        val left = CoverProjection.corners(600f, 600f, -.5f)
        val right = CoverProjection.corners(600f, 600f, .5f)
        assertEquals(600f - left[2], right[0], .001f)
        assertEquals(left[3], right[1], .001f)
        assertEquals(600f, right[5] - right[3], .001f)
        assertTrue(right[2] > right[0])
    }
}
