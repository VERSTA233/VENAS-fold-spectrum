package com.venas.foldspectrum.core

import org.junit.Assert.*
import org.junit.Test

class PhysicalPostureTest {
    @Test fun physicalTentCanBootstrapBeforeAHingeEvent() {
        // Android omits Base/Override when there is no software override at all.
        assertTrue(FoldRules.isPhysicalTent("state exit=0 Committed state: DeviceState{identifier=1, name='TENT'}"))
        assertTrue(FoldRules.isPhysicalTent("""
            state exit=0 Committed state: DeviceState{identifier=1, name='TENT'}
            ----------------------
            Base state: DeviceState{identifier=1, name='TENT', app_accessible=true}
            Override state: null
        """.trimIndent()))
    }

    @Test fun forcedCoverStateNeverPretendsThePhysicalDeviceIsFolded() {
        for (name in listOf("CLOSED", "OPENED", "HALF_OPENED")) {
            assertFalse(FoldRules.isPhysicalTent("""
                Committed state: DeviceState{identifier=1, name='TENT'}
                Base state: DeviceState{identifier=1, name='$name'}
                Override state: DeviceState{identifier=1, name='TENT'}
            """.trimIndent()))
        }
    }

    @Test fun missingUnknownOrRejectedReportsDoNotInventAPosture() {
        for (report in listOf("", "1", "SecurityException", "Base state: null",
            "Committed state: DeviceState{identifier=3, name='OPENED'}",
            "Committed state: DeviceState{identifier=1, name='TENT'}\nOverride state: unknown")) {
            assertFalse(FoldRules.isPhysicalTent(report))
        }
    }
}
