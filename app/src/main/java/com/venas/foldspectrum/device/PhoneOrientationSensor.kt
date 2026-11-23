package com.venas.foldspectrum.device

import android.content.Context
import android.hardware.SensorManager
import android.view.OrientationEventListener

/** Keeps reporting physical posture even while the Activity or system rotation is locked. */
class PhoneOrientationSensor(private val context: Context, private val onOrientation: (Int) -> Unit) {
    private fun createListener() = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_UI) {
        override fun onOrientationChanged(orientation: Int) = onOrientation(orientation)
    }
    private var listener = createListener()
    val available get() = listener.canDetectOrientation()
    fun start() {
        listener.disable()
        // The framework listener retains its last angle across disable/enable.
        // A fresh listener delivers an initial reading when resuming without moving the phone.
        listener = createListener()
        if (available) listener.enable()
    }
    fun stop() = listener.disable()
}
