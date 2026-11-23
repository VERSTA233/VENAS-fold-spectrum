package com.venas.foldspectrum.ui

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AlbumMotionTest {
    @Test fun aTapDoesNotReplaceOrDimTheCoverLayer() = runBlocking {
        val motion = AlbumMotion(this, CoverArtwork("song", null))
        assertNull(motion.held)
        assertTrue(motion.baseVisible)
        motion.release(null, 0f, 400f)
        assertNull(motion.held)
        assertTrue(motion.departing.isEmpty())
        assertTrue(motion.baseVisible)
        assertEquals(1f, motion.arrival.value, 0f)
    }

    private class Frames {
        val clock = BroadcastFrameClock()
        private var time = 0L
        suspend fun advance(count: Int) {
            repeat(count) {
                yield()
                time += 16_666_667
                clock.sendFrame(time)
                yield()
            }
        }
    }

    @Test fun arrivingMetadataDoesNotReplaceThePageBeingTurned() = runBlocking {
        val frames = Frames()
        val scope = CoroutineScope(coroutineContext + frames.clock + SupervisorJob())
        try {
            val motion = AlbumMotion(scope, CoverArtwork("old", null))
            motion.grab()
            motion.move(-100f, 400f)
            motion.release(true, -600f, 400f)
            assertEquals("old", motion.waitingFrom)
            motion.update(CoverArtwork("new", null))
            assertEquals("old", motion.departing.single().artwork.source)
            assertEquals("new", motion.base.source)
            frames.advance(100)
            assertNull(motion.held)
            assertTrue(motion.departing.isEmpty())
            assertNull(motion.waitingFrom)
            assertEquals("new", motion.current.source)
            assertEquals(1f, motion.arrival.value, .001f)
        } finally { scope.cancel() }
    }

    @Test fun regrabbingASettlingPageStartsAtItsVisiblePosition() = runBlocking {
        val frames = Frames()
        val scope = CoroutineScope(coroutineContext + frames.clock + SupervisorJob())
        try {
            val motion = AlbumMotion(scope, CoverArtwork("song", null))
            motion.grab()
            motion.move(-120f, 400f)
            motion.release(null, 0f, 400f)
            frames.advance(5)
            val visible = (motion.held?.progress ?: 0f)
            assertTrue(visible in -.3f..0f)
            motion.grab()
            assertEquals(visible, (motion.held?.progress ?: 0f), .0001f)
            motion.move(20f, 400f)
            assertEquals(visible + .05f, (motion.held?.progress ?: 0f), .0001f)
            frames.advance(30)
            assertEquals(visible + .05f, (motion.held?.progress ?: 0f), .0001f)
            assertNotNull(motion.held)
        } finally { scope.cancel() }
    }

    @Test fun canceledSwipeReturnsToTheSameCoverWithoutWaitingForMetadata() = runBlocking {
        val frames = Frames()
        val scope = CoroutineScope(coroutineContext + frames.clock + SupervisorJob())
        try {
            val motion = AlbumMotion(scope, CoverArtwork("song", null))
            motion.grab()
            motion.move(40f, 400f)
            motion.release(null, 100f, 400f)
            frames.advance(100)
            assertEquals(0f, (motion.held?.progress ?: 0f), .001f)
            assertNull(motion.waitingFrom)
            assertNull(motion.held)
            assertTrue(motion.departing.isEmpty())
            assertTrue(motion.baseVisible)
            assertEquals("song", motion.base.source)
        } finally { scope.cancel() }
    }

    @Test fun rapidSkipsNeverGrabTheFirstDepartingCoverAgain() = runBlocking {
        val frames = Frames()
        val scope = CoroutineScope(coroutineContext + frames.clock + SupervisorJob())
        try {
            val motion = AlbumMotion(scope, CoverArtwork("first", null))
            motion.grab()
            motion.move(-100f, 400f)
            val firstPage = motion.held!!
            motion.release(true, -1500f, 400f)
            for (i in 1..25) {
                frames.advance(3)
                motion.update(CoverArtwork("song-$i", null))
                motion.grab()
                assertNotSame(firstPage, motion.held)
                assertEquals("song-$i", motion.held?.artwork?.source)
                motion.move(-100f, 400f)
                motion.release(true, -1500f, 400f)
                assertTrue(motion.departing.size <= 3)
                assertTrue(firstPage.progress <= -.25f)
            }
            motion.update(CoverArtwork("last", null))
            frames.advance(100)
            assertTrue(motion.departing.isEmpty())
            assertNull(motion.held)
            assertEquals("last", motion.base.source)
        } finally { scope.cancel() }
    }

    @Test fun metadataArrivingDuringACanceledDragCannotBringOldCoverBack() = runBlocking {
        val frames = Frames()
        val scope = CoroutineScope(coroutineContext + frames.clock + SupervisorJob())
        try {
            val motion = AlbumMotion(scope, CoverArtwork("old", null))
            motion.grab()
            motion.move(-100f, 400f)
            val oldPage = motion.held!!
            motion.update(CoverArtwork("new", null))
            motion.release(null, 0f, 400f)
            repeat(60) {
                frames.advance(1)
                assertTrue(oldPage.progress <= -.25f)
            }
            assertTrue(motion.departing.isEmpty())
            assertEquals("new", motion.base.source)
        } finally { scope.cancel() }
    }
}
