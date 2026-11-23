package com.venas.foldspectrum

import android.view.Window
import android.view.WindowManager

/** Activity and settings dialog use the same per-window overrides; no global settings writes. */
internal fun applyDisplayPreferences(window: Window, state: DeskState) {
    val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    val keepOn = state.desk && state.keepScreenOn
    if ((window.attributes.flags and flag != 0) != keepOn) {
        if (keepOn) window.addFlags(flag) else window.clearFlags(flag)
    }
    val brightness = if (state.followSystemBrightness) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        else state.brightness
    if (window.attributes.screenBrightness != brightness) {
        window.attributes = window.attributes.apply { screenBrightness = brightness }
    }
}
