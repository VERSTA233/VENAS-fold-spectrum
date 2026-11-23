package com.venas.foldspectrum.core

import org.junit.Assert.*
import org.junit.Test

class SeekProgressTest {
    private val paused = SeekTimeline(
        source = "session-a|song-a", duration = 300_000, position = 30_000, updatedAt = 9000,
        revision = 1, receivedAt = 9000, speed = 1f, playing = false, loading = false, canSeek = true,
    )

    @Test fun touchingAwayFromPlayheadPreservesTheRenderedPositionAndGrabOffset() {
        val pressed = SeekProgress().begin(paused, 30_000, 850f, 1000f)
        assertEquals(30_000L, pressed.position(paused, 10_000))
        val moved = pressed.move(paused, 900f, 1000f)
        assertEquals(45_000L, moved.position(paused, 10_100))
        assertNull(moved.pending) // Movement alone never creates a seek request.
    }

    @Test fun aSecondDragStartsAtVisibleTargetEvenWhenPlayerStillReportsOldPosition() {
        val first = SeekProgress().begin(paused, 30_000, 200f, 1000f).move(paused, 400f, 1000f)
        val waiting = first.request(paused, first.dragTarget(paused)!!, 11_000)
        assertEquals(90_000L, waiting.position(paused, 11_100))
        val second = waiting.begin(paused, waiting.position(paused, 11_100), 700f, 1000f)
        assertEquals(90_000L, second.position(paused, 11_100))
        val moved = second.move(paused, 600f, 1000f)
        assertEquals(60_000L, moved.dragTarget(paused))
        val released = moved.request(paused, moved.dragTarget(paused)!!, 11_500)
        assertEquals(60_000L, released.pending!!.target)
        assertEquals(60_000L, released.position(paused, 11_600))
    }

    @Test fun delayedAcknowledgementForFirstSeekCannotReplaceLatestTarget() {
        val first = SeekProgress().request(paused, 90_000, 10_000)
        val latest = first.begin(paused, 90_000, 400f, 1000f).move(paused, 300f, 1000f)
            .request(paused, 60_000, 11_000)
        val oldReply = paused.copy(position = 90_000, updatedAt = 10_500, revision = 2, receivedAt = 11_100)
        val stillWaiting = latest.reconcile(oldReply, 11_100).progress
        assertEquals(60_000L, stillWaiting.position(oldReply, 11_100))
        assertNotNull(stillWaiting.pending)
        val latestReply = oldReply.copy(position = 60_000, updatedAt = 11_200, revision = 3, receivedAt = 11_250)
        val confirmed = stillWaiting.reconcile(latestReply, 11_250)
        assertNull(confirmed.progress.pending)
        assertFalse(confirmed.timedOut)
        assertEquals(60_000L, confirmed.progress.position(latestReply, 11_250))
    }

    @Test fun callbacksAndPreviousTimeoutCannotMoveAFingerHeldPlayhead() {
        val waiting = SeekProgress().request(paused, 100_000, 10_000)
        val held = waiting.begin(paused, 100_000, 500f, 1000f).move(paused, 600f, 1000f)
        val reply = paused.copy(position = 100_000, updatedAt = 11_000, revision = 2, receivedAt = 11_100)
        val ackWhileDragging = held.reconcile(reply, 11_200).progress
        assertEquals(130_000L, ackWhileDragging.position(reply, 11_200))
        val overdue = held.reconcile(paused, 18_000)
        assertFalse(overdue.timedOut)
        assertEquals(130_000L, overdue.progress.position(paused, 18_000))
        val latest = overdue.progress.request(paused, 130_000, 18_100)
        assertFalse(latest.reconcile(paused, 18_500).timedOut)
    }

    @Test fun playbackUsesTargetTimeWhileAwaitingSeekAndDragFreezesExactVisibleFrame() {
        val playing = paused.copy(playing = true)
        val pending = SeekProgress().request(playing, 90_000, 10_000)
        assertEquals(92_000L, pending.position(playing, 12_000))
        val held = pending.begin(playing, 91_800, 450f, 1000f)
        assertEquals(91_800L, held.position(playing, 13_000))
        val moved = held.move(playing, 550f, 1000f)
        val released = moved.request(playing, moved.dragTarget(playing)!!, 13_000)
        assertEquals(121_800L, released.pending!!.target)
        assertEquals(122_800L, released.position(playing, 14_000))
    }

    @Test fun pausedSeekDoesNotAdvanceAndResumingContinuesAtRequestedTarget() {
        val pending = SeekProgress().request(paused, 90_000, 10_000)
        assertEquals(90_000L, pending.position(paused, 13_000))
        val resumeBeforeAck = paused.copy(playing = true, updatedAt = 12_000, revision = 2, receivedAt = 12_000)
        val resumed = pending.reconcile(resumeBeforeAck, 12_000).progress
        assertEquals(91_000L, resumed.position(resumeBeforeAck, 13_000))
        val confirmed = resumeBeforeAck.copy(position = 90_000, updatedAt = 12_000, revision = 3, receivedAt = 13_000)
        assertNull(resumed.reconcile(confirmed, 13_000).progress.pending)
    }

    @Test fun oldMetadataAndStaleTimeCannotFalselyAcknowledgeASeek() {
        val p = SeekProgress().request(paused, 60_000, 12_000)
        assertNotNull(p.reconcile(paused.copy(position = 60_000), 12_500).progress.pending)
        val staleTimestamp = paused.copy(position = 60_000, revision = 2, receivedAt = 12_300)
        assertNotNull(p.reconcile(staleTimestamp, 12_500).progress.pending)
        val loading = staleTimestamp.copy(updatedAt = 12_200, loading = true)
        assertNotNull(p.reconcile(loading, 12_500).progress.pending)
    }

    @Test fun wholeSecondAndMissingTimestampRepliesCanConfirmARealSeek() {
        val pending = SeekProgress().request(paused, 60_400, 12_250)
        val coarse = paused.copy(position = 60_000, updatedAt = 12_000, revision = 2, receivedAt = 12_500)
        assertNull(pending.reconcile(coarse, 12_500).progress.pending)
        assertNull(pending.reconcile(coarse.copy(updatedAt = 0), 12_500).progress.pending)
    }

    @Test fun aMissingAcknowledgementIsReportedInsteadOfSilentTwoSecondSnapback() {
        val p = SeekProgress().request(paused, 90_000, 10_000)
        assertEquals(90_000L, p.reconcile(paused, 12_500).progress.position(paused, 12_500))
        val failed = p.reconcile(paused, 16_000)
        assertTrue(failed.timedOut)
        assertEquals(30_000L, failed.progress.position(paused, 16_000))
        assertFalse(failed.progress.reconcile(paused, 16_500).timedOut)
    }

    @Test fun seekAppliedAfterBufferingConfirmsActualPositionWithoutFalseTimeout() {
        val playing = paused.copy(playing = true)
        val p = SeekProgress().request(playing, 90_000, 10_000)
        val delayedReply = playing.copy(position = 90_000, updatedAt = 14_500, revision = 2, receivedAt = 14_600)
        val confirmed = p.reconcile(delayedReply, 14_600)
        assertFalse(confirmed.timedOut)
        assertNull(confirmed.progress.pending)
        assertEquals(90_100L, confirmed.progress.position(delayedReply, 14_600))
    }

    @Test fun lateOldPlaybackCallbackCannotRollBackConfirmedPosition() {
        assertFalse(acceptsPositionUpdate(14_500, 12_000))
        assertTrue(acceptsPositionUpdate(14_500, 14_500))
        assertTrue(acceptsPositionUpdate(14_500, 15_000))
        assertTrue(acceptsPositionUpdate(0, 15_000))
        assertTrue(acceptsPositionUpdate(15_000, 0))
    }

    @Test fun changingSessionTrackOrDurationInvalidatesOldGestureAndRequest() {
        val drag = SeekProgress().begin(paused, 30_000, 100f, 1000f).move(paused, 200f, 1000f)
        for (changed in listOf(paused.copy(source = "session-b|song-a"),
            paused.copy(source = "session-a|song-b"), paused.copy(duration = 280_000))) {
            assertNull(drag.move(changed, 250f, 1000f).dragTarget(changed))
            assertNull(drag.reconcile(changed, 10_000).progress.drag)
            assertNull(SeekProgress().request(paused, 50_000, 10_000).reconcile(changed, 10_100).progress.pending)
        }
        assertEquals(SeekProgress(), drag.reconcile(null, 10_000).progress)
    }

    @Test fun bufferingCanDisableNewDragsWithoutDiscardingAnAlreadySentSeek() {
        val pending = SeekProgress().request(paused, 90_000, 10_000)
        val buffering = paused.copy(canSeek = false, loading = true, revision = 2, receivedAt = 10_100, updatedAt = 10_100)
        val waiting = pending.reconcile(buffering, 10_200).progress
        assertEquals(90_000L, waiting.position(buffering, 10_200))
        assertNotNull(waiting.pending)
        assertNull(waiting.begin(buffering, 90_000, 100f, 1000f).drag)
        val drag = pending.begin(paused, 90_000, 100f, 1000f)
        assertNull(drag.move(buffering, 200f, 1000f).dragTarget(buffering))
        val confirmed = buffering.copy(position = 90_000, loading = false, revision = 3, updatedAt = 10_300, receivedAt = 10_300)
        assertNull(waiting.reconcile(confirmed, 10_300).progress.pending)
    }

    @Test fun cancelAndLayoutChangeNeverCommitAnAccidentalSeek() {
        val p = SeekProgress().request(paused, 90_000, 10_000)
        val d = p.begin(paused, 90_000, 100f, 1000f).move(paused, 200f, 1000f)
        assertNull(d.cancelDrag().dragTarget(paused))
        assertEquals(90_000L, d.cancelDrag().position(paused, 11_000))
        assertNull(d.move(paused, 300f, 800f).dragTarget(paused))
    }

    @Test fun gestureAndDrawUseTheSameTrackWidthAndClampAtBothEnds() {
        val width = ProgressTrack.drawableWidth(1004f)
        val drag = SeekProgress().begin(paused, 30_000, 100f, width)
        assertEquals(33000L, drag.move(paused, 110f, width).dragTarget(paused))
        assertEquals(0L, drag.move(paused, -900f, width).dragTarget(paused))
        assertEquals(300_000L, drag.move(paused, 1100f, width).dragTarget(paused))
        assertNull(SeekProgress().begin(paused, 0, 0f, 0f).drag)
    }
}
