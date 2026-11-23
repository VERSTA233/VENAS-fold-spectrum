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
class CoverDisplay {
    val stateId = FoldRules.coverState(Build.MANUFACTURER)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var ownsOverride = false

    suspend fun enter(): DisplayResult = mutex.withLock {
        val result = withContext(Dispatchers.IO) { command("state", stateId.toString()) }
        ownsOverride = result.success
        result
    }

    /** Read the physical base posture once at entry, using the app's own UID. */
    suspend fun isPhysicallyTent(): Boolean = mutex.withLock {
        val result = withContext(Dispatchers.IO) { command("state") }
        result.success && FoldRules.isPhysicalTent(result.detail)
    }

    fun reset(onComplete: (() -> Unit)? = null) {
        scope.launch {
            mutex.withLock {
                if (ownsOverride) {
                    val first = command("state", "reset")
                    if (Build.MANUFACTURER.contains("honor", true)) { delay(120); command("state", "reset") }
                    ownsOverride = !first.success
                }
            }
            onComplete?.invoke()
        }
    }

    private fun command(vararg arguments: String): DisplayResult = try {
        val process = ProcessBuilder("/system/bin/cmd", "device_state", *arguments).redirectErrorStream(true).start()
        val exited = process.waitFor(3, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            DisplayResult(false, "外屏状态指令超时")
        } else {
            val message = process.inputStream.bufferedReader().readText().take(1600)
            val result = DisplayResult(process.exitValue() == 0 && !message.contains("SecurityException"),
                "${arguments.joinToString(" ")} exit=${process.exitValue()} $message".trim())
            Log.i("VenasFold", result.detail)
            result
        }
    } catch (e: Exception) {
        Log.e("VenasFold", "device_state ${arguments.joinToString(" ")} failed", e)
        DisplayResult(false, "${e.javaClass.simpleName}: ${e.message}")
    }

    fun close() { reset { scope.cancel() } }
}
