package com.venas.foldspectrum.core

enum class PhonePosture { UNKNOWN, PORTRAIT, LANDSCAPE }

/** Physical sensor angle, not the app's requested/locked screen orientation. */
object PhoneOrientation {
    fun posture(degrees: Int, naturalLandscape: Boolean = false): PhonePosture {
        if (degrees !in 0..359) return PhonePosture.UNKNOWN
        val angle = degrees % 180
        val alongNaturalTop = angle <= 30 || angle >= 150
        val quarterTurn = angle in 60..120
        if (!alongNaturalTop && !quarterTurn) return PhonePosture.UNKNOWN
        val landscape = if (quarterTurn) !naturalLandscape else naturalLandscape
        return if (landscape) PhonePosture.LANDSCAPE else PhonePosture.PORTRAIT
    }
}
