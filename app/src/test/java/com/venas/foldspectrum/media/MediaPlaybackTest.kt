package com.venas.foldspectrum.media

import android.media.session.PlaybackState
import com.venas.foldspectrum.core.Playback
import com.venas.foldspectrum.core.SessionCandidate
import com.venas.foldspectrum.core.chooseSession
import org.junit.Assert.*
import org.junit.Test

class MediaPlaybackTest {
    @Test fun seekingOrQueueSkippingPlayerTakesPriorityOverTheRetainedPausedPlayer() {
        for (state in listOf(PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM)) {
            val candidates = listOf(SessionCandidate("old", false, 100),
                SessionCandidate("active", mediaPlayback(state) != Playback.PAUSED, 200))
            assertEquals("active", chooseSession(candidates, "old"))
        }
    }

    @Test fun twoPausedPlayersStillRetainTheSelectedOne() {
        val candidates = listOf(SessionCandidate("old", mediaPlayback(PlaybackState.STATE_PAUSED) != Playback.PAUSED, 100),
            SessionCandidate("new", mediaPlayback(PlaybackState.STATE_PAUSED) != Playback.PAUSED, 200))
        assertEquals("old", chooseSession(candidates, "old"))
        assertEquals(Playback.LOADING, mediaPlayback(PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM))
    }
}
