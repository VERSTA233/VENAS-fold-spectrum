package com.venas.foldspectrum.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import com.venas.foldspectrum.core.FoldRules
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class HingeSensor(context: Context, private val onAngle: (Float) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    val available get() = sensor != null
    fun start() { sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }
    fun stop() = manager.unregisterListener(this)
    override fun onSensorChanged(event: SensorEvent) {
        event.values.firstOrNull()?.takeIf { it.isFinite() }?.let(onAngle)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

data class DisplayResult(val success: Boolean, val detail: String)

/** Serialized app-UID commands; ADB/shell privileges are never used by the product. */
internal class CoverDisplay(
    private val manufacturer: String = Build.MANUFACTURER,
    private val execute: (Array<out String>) -> DisplayResult = ::deviceStateCommand,
) {
    private val stateId = FoldRules.coverState(manufacturer)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var ownsOverride = false

    suspend fun enter(): DisplayResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            execute(arrayOf("state", stateId.toString())).also {
                // The command may succeed after the caller is cancelled. Record
                // ownership before withContext's cancellable return, so reset can undo it.
                ownsOverride = ownsOverride || it.success
            }
        }
    }

    /** Read the physical base posture once at entry, using the app's own UID. */
    suspend fun isPhysicallyTent(): Boolean = mutex.withLock {
        val result = withContext(Dispatchers.IO) { execute(arrayOf("state")) }
        result.success && FoldRules.isPhysicalTent(result.detail)
    }

    fun reset(): Job = scope.launch {
        mutex.withLock {
            if (ownsOverride) {
                var reset = execute(arrayOf("state", "reset")).success
                if (manufacturer.contains("honor", true)) {
                    delay(120)
                    reset = execute(arrayOf("state", "reset")).success || reset
                }
                ownsOverride = !reset
            }
        }
    }

    fun close() { reset().invokeOnCompletion { scope.cancel() } }
}

private fun deviceStateCommand(arguments: Array<out String>): DisplayResult = try {
    val process = ProcessBuilder("/system/bin/cmd", "device_state", *arguments).redirectErrorStream(true).start()
    try {
        if (!process.waitFor(3, TimeUnit.SECONDS)) DisplayResult(false, "外屏状态指令超时") else {
            val message = process.inputStream.bufferedReader().use { it.readText().take(1600) }
            val result = DisplayResult(process.exitValue() == 0 && !message.contains("SecurityException"),
                "${arguments.joinToString(" ")} exit=${process.exitValue()} $message".trim())
            Log.i("VenasFold", result.detail)
            result
        }
    } finally {
        if (process.isAlive) process.destroyForcibly()
        runCatching { process.inputStream.close() }
        runCatching { process.outputStream.close() }
        runCatching { process.errorStream.close() }
    }
} catch (e: Exception) {
    Log.e("VenasFold", "device_state ${arguments.joinToString(" ")} failed", e)
    DisplayResult(false, "${e.javaClass.simpleName}: ${e.message}")
}
