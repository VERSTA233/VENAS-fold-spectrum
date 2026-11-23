package com.venas.foldspectrum.core

import org.junit.Assert.*
import org.junit.Test

class PhoneOrientationTest {
    @Test fun bothQuarterTurnsEnterLandscapeRegardlessOfUiRotationLock() {
        for (degrees in listOf(60, 90, 120, 240, 270, 300)) {
            assertEquals(PhonePosture.LANDSCAPE, PhoneOrientation.posture(degrees))
        }
    }

    @Test fun uprightAndUpsideDownPositionsReturnToPortrait() {
        for (degrees in listOf(0, 30, 150, 180, 210, 330, 359)) {
            assertEquals(PhonePosture.PORTRAIT, PhoneOrientation.posture(degrees))
        }
    }

    @Test fun diagonalPositionsDoNotDemandAnImmediateModeChange() {
        for (degrees in listOf(31, 45, 59, 121, 149, 211, 239, 301, 329)) {
            assertEquals(PhonePosture.UNKNOWN, PhoneOrientation.posture(degrees))
        }
    }

    @Test fun flatOrMissingSensorValuesAreNotMistakenForPortrait() {
        for (degrees in listOf(-1, -90, 360, Int.MAX_VALUE)) {
            assertEquals(PhonePosture.UNKNOWN, PhoneOrientation.posture(degrees))
        }
    }

    @Test fun naturallyLandscapePanelsUseTheirLongSideInsteadOfAssumingPortrait() {
        assertEquals(PhonePosture.LANDSCAPE, PhoneOrientation.posture(0, true))
        assertEquals(PhonePosture.LANDSCAPE, PhoneOrientation.posture(180, true))
        assertEquals(PhonePosture.PORTRAIT, PhoneOrientation.posture(90, true))
        assertEquals(PhonePosture.PORTRAIT, PhoneOrientation.posture(270, true))
        assertEquals(PhonePosture.UNKNOWN, PhoneOrientation.posture(45, true))
        assertEquals(PhonePosture.UNKNOWN, PhoneOrientation.posture(-1, true))
    }
}
