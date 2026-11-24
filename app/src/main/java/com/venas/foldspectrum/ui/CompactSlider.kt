package com.venas.foldspectrum.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** Small visual controls with the standard slider's gestures and accessibility. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactSlider(value: Float, onValueChange: (Float) -> Unit,
                           valueRange: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier,
                           enabled: Boolean = true, onValueChangeFinished: (() -> Unit)? = null,
                           interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val tint = if (enabled) Ink else Muted
    Slider(value = value, onValueChange = onValueChange, valueRange = valueRange,
        enabled = enabled, onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource, modifier = modifier.heightIn(min = 48.dp),
        thumb = {
            // Material measures the thumb slot with the slider's minimum height.
            // Center the visible dot inside that slot instead of its top edge.
            Box(Modifier.width(10.dp).height(48.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp).background(tint, CircleShape))
            }
        },
        track = {
            Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                val y = size.height / 2
                drawLine(Muted.copy(alpha = .35f), Offset(0f, y), Offset(size.width, y),
                    size.height, StrokeCap.Round)
                if (fraction > 0f) drawLine(tint, Offset(0f, y), Offset(size.width * fraction, y),
                    size.height, StrokeCap.Round)
            }
        })
}
