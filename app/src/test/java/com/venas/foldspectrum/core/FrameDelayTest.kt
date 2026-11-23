package com.venas.foldspectrum.core

import org.junit.Assert.*
import org.junit.Test

class FrameDelayTest {
    @Test fun delaysAnImpulseWithoutDampingOrChangingSampleIntervals() {
        val buffer = FrameDelay<Int>()
        buffer.add(0, 1000, 369)
        buffer.add(100, 1051, 369)
        buffer.add(0, 1102, 369)
        assertNull(buffer.takeDue(1368))
        assertEquals(0, buffer.takeDue(1369))
        assertEquals(1420L, buffer.nextDueAt)
        assertNull(buffer.takeDue(1419))
        assertEquals(100, buffer.takeDue(1420))
        assertEquals(0, buffer.takeDue(1471))
        assertNull(buffer.nextDueAt)
    }

    @Test fun lateRenderingSkipsExpiredFramesInsteadOfBuildingUpLatency() {
        val buffer = FrameDelay<Int>()
        for (i in 0..9) buffer.add(i, i * 50L, 369)
        assertEquals(8, buffer.takeDue(800))
        assertEquals(819L, buffer.nextDueAt)
        assertEquals(9, buffer.takeDue(819))
        assertNull(buffer.takeDue(1000))
    }

    @Test fun trackChangeOrResumeFlushesEveryOldFrame() {
        val buffer = FrameDelay<String>()
        buffer.add("old track", 1000, 369)
        buffer.clear()
        assertNull(buffer.nextDueAt)
        buffer.add("new track", 1200, 369)
        assertNull(buffer.takeDue(1369))
        assertEquals("new track", buffer.takeDue(1569))
        buffer.clear()
        assertNull(buffer.takeDue(9999))
    }

    @Test fun noDelayHasNoWarmupAndEqualTimestampsUseLatest() {
        val buffer = FrameDelay<Int>()
        buffer.add(1, 1000, 0)
        buffer.add(2, 1000, 0)
        assertEquals(2, buffer.takeDue(1000))
        assertNull(buffer.nextDueAt)
    }

    @Test fun revisedOutputLatencyRetimesQueuedAndNewFramesWithoutReordering() {
        val buffer = FrameDelay<Int>()
        buffer.add(1, 1000, 129)
        buffer.add(2, 1050, 129)
        buffer.shiftBy(369 - 129L)
        buffer.add(3, 1100, 369)
        assertNull(buffer.takeDue(1129))
        assertEquals(1, buffer.takeDue(1369))
        buffer.shiftBy(129 - 369L)
        assertEquals(3, buffer.takeDue(1370))
        assertNull(buffer.nextDueAt)
    }
}
