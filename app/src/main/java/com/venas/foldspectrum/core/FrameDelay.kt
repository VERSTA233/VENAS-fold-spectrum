package com.venas.foldspectrum.core

import java.util.ArrayDeque

/** Keeps sample timing intact; a late consumer drops backlog rather than replaying it. */
class FrameDelay<T : Any> {
    private data class Entry<T>(var dueAt: Long, val value: T)
    private val pending = ArrayDeque<Entry<T>>()
    val nextDueAt: Long? get() = pending.peekFirst()?.dueAt

    fun add(value: T, capturedAt: Long, delayMs: Long) {
        require(delayMs >= 0)
        val dueAt = capturedAt + delayMs
        require(pending.peekLast()?.let { dueAt >= it.dueAt } != false)
        pending.addLast(Entry(dueAt, value))
    }

    fun takeDue(now: Long): T? {
        var latest: T? = null
        while (pending.peekFirst()?.let { it.dueAt <= now } == true) {
            latest = pending.removeFirst().value
        }
        return latest
    }

    fun clear() = pending.clear()

    /** Re-time queued samples together when the output's latency estimate changes. */
    fun shiftBy(deltaMs: Long) {
        pending.forEach { it.dueAt += deltaMs }
    }
}
