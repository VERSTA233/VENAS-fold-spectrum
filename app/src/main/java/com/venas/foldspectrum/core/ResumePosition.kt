package com.venas.foldspectrum.core

/** Some players briefly publish PAUSED at zero while waking up to resume a song. */
internal class ResumePosition {
    private var previous: SeekTimeline? = null
    private var allowResetUntil = 0L
    var retryAt: Long? = null
        private set

    fun resolve(incoming: SeekTimeline, now: Long): SeekTimeline {
        val old = previous
        val sameTrack = old != null && old.source == incoming.source && old.duration == incoming.duration
        val provisionalZero = sameTrack && incoming.position <= 0 && old!!.positionNow(now) > 1000 &&
            (!incoming.playing || !old.playing || retryAt != null)
        if (!provisionalZero || now < allowResetUntil) {
            retryAt = null
            previous = incoming
            return incoming
        }
        val deadline = retryAt ?: (now + 750).also { retryAt = it }
        if (now >= deadline) {
            // A genuine restart must still appear if no corrected position arrives.
            retryAt = null
            previous = incoming
            return incoming
        }
        // Re-anchor at receipt time: a long pause must never count as elapsed playback.
        // Retain the old revision so this provisional value cannot acknowledge a seek.
        return incoming.copy(position = old!!.positionNow(now), updatedAt = now,
            revision = old.revision, receivedAt = old.receivedAt).also { previous = it }
    }

    fun allowReset(now: Long) {
        allowResetUntil = now + SeekProgress.CONFIRM_TIMEOUT_MS
        retryAt = null
    }

    fun clear() {
        previous = null
        retryAt = null
        allowResetUntil = 0
    }
}
