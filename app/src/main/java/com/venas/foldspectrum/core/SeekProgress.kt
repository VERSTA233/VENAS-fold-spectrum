package com.venas.foldspectrum.core

import kotlin.math.abs
import kotlin.math.roundToLong

/** One observation of the selected session, independent of artwork/metadata refreshes. */
data class SeekTimeline(
    val source: String,
    val duration: Long,
    val position: Long,
    val updatedAt: Long,
    val revision: Long,
    val receivedAt: Long,
    val speed: Float,
    val playing: Boolean,
    val loading: Boolean,
    val canSeek: Boolean,
) {
    fun positionNow(now: Long) = positionAt(position, updatedAt, now, speed, playing, duration)
}

object ProgressTrack {
    const val INSET_PX = 2f
    fun drawableWidth(width: Float) = (width - 2 * INSET_PX).coerceAtLeast(0f)
}

data class ProgressDrag(
    val source: String,
    val duration: Long,
    val startPosition: Long,
    val pointerDownX: Float,
    val width: Float,
    val position: Long = startPosition,
)

data class PendingSeek(
    val source: String,
    val duration: Long,
    val target: Long,
    val requestedAt: Long,
    val sourceRevision: Long,
    val sourceUpdatedAt: Long,
    val anchorPosition: Long = target,
    val anchorAt: Long = requestedAt,
    val speed: Float,
    val playing: Boolean,
) {
    fun positionNow(now: Long) = positionAt(anchorPosition, anchorAt, now, speed, playing, duration)
}

data class SeekReconciliation(val progress: SeekProgress, val timedOut: Boolean = false)

/** Missing timestamps are supported; otherwise position observations must be monotonic. */
fun acceptsPositionUpdate(previousUpdatedAt: Long, incomingUpdatedAt: Long) =
    previousUpdatedAt <= 0 || incomingUpdatedAt <= 0 || incomingUpdatedAt >= previousUpdatedAt

/** Shared by gesture rendering and release. Only a release sends a transport command. */
data class SeekProgress(val drag: ProgressDrag? = null, val pending: PendingSeek? = null) {
    fun position(timeline: SeekTimeline, now: Long): Long =
        drag?.takeIf { it.source == timeline.source && it.duration == timeline.duration }?.position
            ?: pending?.takeIf { it.source == timeline.source && it.duration == timeline.duration }?.positionNow(now)
            ?: timeline.positionNow(now)

    fun begin(timeline: SeekTimeline, displayedPosition: Long, x: Float, width: Float): SeekProgress {
        if (!timeline.canSeek || timeline.duration <= 0 || !width.isFinite() || width <= 0) return copy(drag = null)
        return copy(drag = ProgressDrag(timeline.source, timeline.duration,
            displayedPosition.coerceIn(0, timeline.duration), x, width))
    }

    fun move(timeline: SeekTimeline, x: Float, width: Float): SeekProgress {
        val d = drag ?: return this
        if (!valid(timeline, d.source, d.duration) || !x.isFinite() || abs(width - d.width) > .5f) return copy(drag = null)
        val next = (d.startPosition + (x - d.pointerDownX).toDouble() / d.width * d.duration)
            .roundToLong().coerceIn(0, d.duration)
        return copy(drag = d.copy(position = next))
    }

    fun dragTarget(timeline: SeekTimeline): Long? =
        drag?.takeIf { valid(timeline, it.source, it.duration) }?.position

    fun request(timeline: SeekTimeline, target: Long, now: Long): SeekProgress {
        if (!timeline.canSeek || timeline.duration <= 0) return copy(drag = null)
        return SeekProgress(pending = PendingSeek(timeline.source, timeline.duration,
            target.coerceIn(0, timeline.duration), now, timeline.revision, timeline.updatedAt,
            speed = timeline.speed, playing = timeline.playing))
    }

    fun cancelDrag() = copy(drag = null)

    fun reconcile(timeline: SeekTimeline?, now: Long): SeekReconciliation {
        if (timeline == null) return SeekReconciliation(SeekProgress())
        val d = drag?.takeIf { valid(timeline, it.source, it.duration) }
        // A player can temporarily remove SEEK_TO while applying an already-sent seek.
        var p = pending?.takeIf { timeline.source == it.source && timeline.duration == it.duration && it.duration > 0 }
            ?: return SeekReconciliation(copy(drag = d, pending = null))
        // Artwork refreshes, old callbacks, and an earlier seek target are not acknowledgements.
        val fresh = timeline.revision > p.sourceRevision && timeline.receivedAt >= p.requestedAt &&
            (timeline.updatedAt == 0L ||
                timeline.updatedAt >= maxOf(p.sourceUpdatedAt, p.requestedAt - ACK_TOLERANCE_MS))
        val expected = p.positionNow(now)
        // A player may spend seconds buffering before applying seekTo. Its first real
        // position can still be at the requested target rather than our projected time.
        val matchesRequest = abs(timeline.position - p.target) <= ACK_TOLERANCE_MS ||
            abs(timeline.positionNow(now) - expected) <= ACK_TOLERANCE_MS
        if (fresh && !timeline.loading && matchesRequest) {
            return SeekReconciliation(copy(drag = d, pending = null))
        }
        // Do not let an old deadline interrupt a new drag. A release starts a new request.
        if (d == null && now - p.requestedAt >= CONFIRM_TIMEOUT_MS) {
            return SeekReconciliation(SeekProgress(), timedOut = true)
        }
        if (p.playing != timeline.playing || p.speed != timeline.speed) {
            p = p.copy(anchorPosition = expected, anchorAt = now, playing = timeline.playing, speed = timeline.speed)
        }
        return SeekReconciliation(copy(drag = d, pending = p))
    }

    private fun valid(t: SeekTimeline, source: String, duration: Long) =
        t.source == source && t.duration == duration && duration > 0 && t.canSeek

    companion object {
        const val ACK_TOLERANCE_MS = 1000L // Accepted low-precision, whole-second media positions.
        const val CONFIRM_TIMEOUT_MS = 6000L
    }
}
