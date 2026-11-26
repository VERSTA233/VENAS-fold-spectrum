package com.venas.foldspectrum.core

import kotlin.math.max

/** A display-only peak cap: immediate attack, then a frame-rate-independent fall. */
internal object SpectrumPeak {
    private const val FallDbPerSecond = 24f

    fun advance(previousDb: Float, bandDb: Float, elapsedSeconds: Float): Float =
        max(bandDb, previousDb - FallDbPerSecond * elapsedSeconds.coerceAtLeast(0f))
}
