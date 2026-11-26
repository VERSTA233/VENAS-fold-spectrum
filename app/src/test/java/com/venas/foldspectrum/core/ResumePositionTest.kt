package com.venas.foldspectrum.core

import org.junit.Assert.*
import org.junit.Test

class ResumePositionTest {
    private val paused = SeekTimeline("poweramp|song", 300_000, 100_611, 10_000,
        1, 10_000, 1f, playing = false, loading = false, canSeek = true)

    @Test fun powerampWakeupZeroNeverReachesThePlayhead() {
        val guard = ResumePosition()
        guard.resolve(paused, 10_000)
        val zero = paused.copy(position = 0, updatedAt = 44_623, receivedAt = 44_623, revision = 2)
        val held = guard.resolve(zero, 44_623)
        assertEquals(100_611L, held.positionNow(44_623))
        assertEquals(paused.revision, held.revision)
        val playing = zero.copy(position = 100_612, playing = true, updatedAt = 44_815,
            receivedAt = 44_815, revision = 3)
        assertEquals(playing, guard.resolve(playing, 44_815))
        assertNull(guard.retryAt)
    }

    @Test fun zeroDuringBufferingAndResumeDoesNotAddTheTimeSpentPaused() {
        val guard = ResumePosition()
        guard.resolve(paused, 10_000)
        val loading = paused.copy(position = 0, loading = true, revision = 2, receivedAt = 90_000)
        assertEquals(100_611L, guard.resolve(loading, 90_000).positionNow(90_000))
        val playing = loading.copy(playing = true, loading = false, revision = 3, receivedAt = 90_200)
        val held = guard.resolve(playing, 90_200)
        assertEquals(100_611L, held.positionNow(90_200))
        assertEquals(100_811L, held.positionNow(90_400))
        assertEquals(paused.revision, held.revision)
    }

    @Test fun repeatedZeroOrMetadataUpdatesCannotExtendTheDeadline() {
        val guard = ResumePosition()
        guard.resolve(paused, 10_000)
        val zero = paused.copy(position = 0, revision = 2, updatedAt = 20_000, receivedAt = 20_000)
        assertEquals(paused.position, guard.resolve(zero, 20_000).position)
        val deadline = guard.retryAt!!
        for (now in listOf(20_200L, 20_500L, deadline - 1)) {
            assertEquals(paused.position, guard.resolve(zero, now).position)
            assertEquals(deadline, guard.retryAt)
        }
        // The repository retries at this deadline even if there are no new callbacks.
        assertEquals(zero, guard.resolve(zero, deadline))
        assertNull(guard.retryAt)
        assertEquals(zero, guard.resolve(zero, deadline + 100))
    }

    @Test fun explicitSeekOrPreviousTrackCanResetToZeroImmediately() {
        val guard = ResumePosition()
        guard.resolve(paused, 10_000)
        guard.allowReset(20_000)
        guard.resolve(paused, 20_100) // Metadata can refresh before the control is applied.
        val zero = paused.copy(position = 0, updatedAt = 21_000, receivedAt = 21_000, revision = 2)
        assertEquals(zero, guard.resolve(zero, 21_000))
        assertNull(guard.retryAt)
    }

    @Test fun newTrackOrSessionNeverInheritsThePreviousPosition() {
        for (source in listOf("poweramp|other-song", "cloudmusic|song")) {
            val guard = ResumePosition()
            guard.resolve(paused, 10_000)
            guard.resolve(paused.copy(position = 0), 20_000)
            val newSong = paused.copy(source = source, position = 0, playing = true)
            assertEquals(newSong, guard.resolve(newSong, 20_100))
            assertNull(guard.retryAt)
        }
    }

    @Test fun normalPlaybackRestartAndSmallPositionsRemainUntouched() {
        val guard = ResumePosition()
        val playing = paused.copy(playing = true)
        guard.resolve(playing, 10_000)
        val restart = playing.copy(position = 0, updatedAt = 20_000, receivedAt = 20_000, revision = 2)
        assertEquals(restart, guard.resolve(restart, 20_000))
        guard.clear()
        guard.resolve(paused.copy(position = 900), 30_000)
        assertEquals(paused.copy(position = 0), guard.resolve(paused.copy(position = 0), 31_000))
    }
}
