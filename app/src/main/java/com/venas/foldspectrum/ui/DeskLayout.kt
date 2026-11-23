package com.venas.foldspectrum.ui

/** Layout policy uses the active window, independently of physical fold detection. */
internal data class DeskLayout(
    val isWideFold: Boolean,
    val unit: Float,
    val coverDp: Float,
    val horizontalGapDp: Float,
) {
    val waveformWeight: Float get() = if (isWideFold) 1.25f else 2f
    val metricInsetDp: Float get() = (if (isWideFold) 6f else 14f) * unit
    val metricValueSp: Float get() = (if (isWideFold) 18f else 20f) * unit
    val metricUnitSp: Float get() = (if (isWideFold) 8f else 8.5f) * unit
}

internal fun deskLayout(widthDp: Float, heightDp: Float): DeskLayout {
    val wideFold = heightDp > 0f && widthDp / heightDp in 1.20f..1.60f
    if (wideFold) return DeskLayout(
        isWideFold = true,
        unit = minOf(heightDp / 410f, widthDp / 580f).coerceIn(.5f, 1.35f),
        coverDp = minOf(heightDp * .52f, widthDp * .35f),
        horizontalGapDp = widthDp * .04f,
    )
    // Preserve the existing long-landscape layout outside the explicit band.
    return DeskLayout(
        isWideFold = false,
        unit = (heightDp / 410f).coerceIn(.65f, 1.35f),
        coverDp = minOf(heightDp * .57f, widthDp * .27f),
        horizontalGapDp = widthDp * .08f,
    )
}
