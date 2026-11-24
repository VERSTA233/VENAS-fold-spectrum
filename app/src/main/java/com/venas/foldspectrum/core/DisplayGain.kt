package com.venas.foldspectrum.core

import kotlin.math.pow

/** Display compensation only. Never fed back into capture or the audio output. */
internal object DisplayGain {
    fun decibels(value: Float, gain: Float, silenceFloor: Float): Float =
        if (value <= silenceFloor) value else (value + gain).coerceAtMost(0f)

    fun amplitude(gain: Float): Float = 10f.pow(gain / 20f)
}
