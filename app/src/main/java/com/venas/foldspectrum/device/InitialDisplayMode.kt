package com.venas.foldspectrum.device

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager

/** Decide once, preserving both earlier manual choices and this first-run default. */
internal fun initialNonFoldMode(context: Context, preferences: SharedPreferences, hasHinge: Boolean): Boolean {
    if (preferences.contains("nonFoldMode")) return preferences.getBoolean("nonFoldMode", false)

    val foldable = hasHinge ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE) ||
        hasSystemFoldStates(context)
    return (!foldable).also { preferences.edit().putBoolean("nonFoldMode", it).apply() }
}

/** Optional platform configuration used by Android's fold-state listener.
 * Some foldables declare folded states without exposing a hinge-angle sensor.
 * This internal resource is not guaranteed on vendor builds; absent/inaccessible
 * configuration is simply no evidence, and the mode switch remains available.
 */
@SuppressLint("DiscouragedApi")
private fun hasSystemFoldStates(context: Context): Boolean = runCatching {
    val resources = context.resources
    val id = resources.getIdentifier("config_foldedDeviceStates", "array", "android")
    id != 0 && resources.getIntArray(id).isNotEmpty()
}.getOrDefault(false)
