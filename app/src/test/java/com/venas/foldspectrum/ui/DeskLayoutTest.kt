package com.venas.foldspectrum.ui

import org.junit.Assert.*
import org.junit.Test

class DeskLayoutTest {
    @Test fun wideLayoutOnlyAppliesInsideTheLandscapeAspectBand() {
        for (ratio in listOf(1.20f, 4f / 3, 1.41f, 1.5f, 1.60f)) {
            assertTrue("ratio=$ratio", deskLayout(ratio * 500, 500f).isWideFold)
        }
        for (ratio in listOf(.7f, 1f, 1.199f, 1.601f, 16f / 9, 2f, 2616f / 1140)) {
            assertFalse("ratio=$ratio", deskLayout(ratio * 500, 500f).isWideFold)
        }
    }

    @Test fun ordinaryScreensRetainTheirPreviousGeometryAndMetricSizes() {
        // Recorded inputs/outputs of the original policy, including both scale clamps.
        val fixtures = listOf(
            floatArrayOf(940.8f, 410f, 1f, 233.7f, 75.264f),
            floatArrayOf(640f, 320f, .7804878f, 172.8f, 51.2f),
            floatArrayOf(360f, 180f, .65f, 97.2f, 28.8f),
            floatArrayOf(1280f, 720f, 1.35f, 345.6f, 102.4f),
            floatArrayOf(410f, 800f, 1.35f, 110.7f, 32.8f),
        )
        for ((width, height, unit, cover, gap) in fixtures) {
            val result = deskLayout(width, height)
            assertFalse(result.isWideFold)
            assertEquals(unit, result.unit, .0001f)
            assertEquals(cover, result.coverDp, .001f)
            assertEquals(gap, result.horizontalGapDp, .001f)
            assertEquals(2f, result.waveformWeight, 0f)
            assertEquals(14 * unit, result.metricInsetDp, .0001f)
            assertEquals(20 * unit, result.metricValueSp, .0001f)
            assertEquals(8.5f * unit, result.metricUnitSp, .0001f)
        }
    }

    @Test fun wideScreensHaveRoomForTheFullLevelValueAndUnitAtDefaultFontScale() {
        // Actual bundled SF Mono glyph advance: 1266 / 2048 em; wide values use zero tracking.
        // −95.9 dBFS is the longest normal LEVEL readout (below −96 becomes −∞).
        val advance = 1266f / 2048
        for (height in listOf(240f, 320f, 410f, 580f, 850f)) {
            for (ratio in listOf(1.20f, 4f / 3, 1.41f, 1.5f, 1.60f)) {
                val width = height * ratio
                val result = deskLayout(width, height)
                val rightWidth = width - result.coverDp - 3 * result.horizontalGapDp
                val levelWidth = (rightWidth - 1) / (result.waveformWeight + 2) - 2 * result.metricInsetDp
                val needed = 5 * advance * result.metricValueSp + 3 * result.unit +
                    4 * advance * result.metricUnitSp
                assertTrue("$width x $height: $levelWidth < $needed", levelWidth >= needed)
            }
        }
    }

    @Test fun wideCoverAndTwoLineTitleLeaveRoomForTheArtist() {
        for (height in listOf(240f, 410f, 850f)) {
            for (ratio in listOf(1.20f, 1.41f, 1.60f)) {
                val result = deskLayout(height * ratio, height)
                val occupied = result.coverDp + (9 + 16 + 2 * 26 + 5 + 20) * result.unit
                assertTrue(occupied <= height - 48 * result.unit)
            }
        }
    }
}
