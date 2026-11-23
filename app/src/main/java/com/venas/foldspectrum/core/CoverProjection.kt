package com.venas.foldspectrum.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Four projected corners, TL/TR/BR/BL. The free edge recedes around a vertical hinge. */
object CoverProjection {
    fun corners(width: Float, height: Float, progress: Float): FloatArray {
        val amount = abs(progress).coerceIn(0f, 1f)
        val next = progress <= 0f
        val hinge = if (next) 0f else width
        val direction = if (next) 1f else -1f
        val angle = amount * 80f * PI.toFloat() / 180f
        val focal = width.coerceAtLeast(1f) * 1.8f
        val translation = (if (next) -1f else 1f) * amount * width * .25f
        val source = floatArrayOf(0f, 0f, width, 0f, width, height, 0f, height)
        return FloatArray(8).also { result ->
            for (i in 0..3) {
                val distance = abs(source[i * 2] - hinge)
                val perspective = focal / (focal + distance * sin(angle))
                result[i * 2] = hinge + direction * distance * cos(angle) * perspective + translation
                result[i * 2 + 1] = height / 2f + (source[i * 2 + 1] - height / 2f) * perspective
            }
        }
    }
}
