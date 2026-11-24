package com.venas.foldspectrum.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.venas.foldspectrum.DeskState
import com.venas.foldspectrum.core.*
import com.venas.foldspectrum.media.NowPlaying
import java.util.Locale
import kotlin.math.*

private const val SpectrumFloorDb = -60f
private const val WaveformDisplayGain = 3f
private val FrequencyLabels = listOf(20 to "20", 200 to "200", 500 to "500", 1000 to "1k",
    2000 to "2k", 4000 to "4k", 8000 to "8k", 16000 to "16k")

@Composable
internal fun StatusLabel(status: DisplayStatus, u: Float) {
    val color = when (status) {
        DisplayStatus.PLAYING -> Ink
        DisplayStatus.UNSUPPORTED -> Red
        else -> Muted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((6 * u).dp)) {
        Box(Modifier.size((4 * u).dp).background(if (status == DisplayStatus.PLAYING) Red else color, RoundedCornerShape(50)))
        Text(status.name, color = color, fontSize = (10 * u).sp, letterSpacing = (.4f * u).sp,
            fontFamily = InstrumentFont, fontWeight = FontWeight.Medium)
    }
}

internal data class DrawingFrame(val bands: FloatArray, val wave: FloatArray, val layout: List<SpectrumBand> = emptyList())

@Composable
internal fun smoothAudio(state: DeskState): DrawingFrame {
    val latest by rememberUpdatedState(state)
    var rendered by remember { mutableStateOf(DrawingFrame(FloatArray(0), FloatArray(96))) }
    LaunchedEffect(Unit) {
        var previous = 0L
        while (true) withFrameNanos { frame ->
            val dt = if (previous == 0L) .016f else ((frame - previous) / 1e9f).coerceAtMost(.1f)
            previous = frame
            val active = latest.status == DisplayStatus.PLAYING
            val targetBands = if (active) latest.audio.bands else null
            val targetWave = if (active) latest.audio.wave else null
            val gain = latest.displayGainDb
            val waveGain = WaveformDisplayGain * DisplayGain.amplitude(gain)
            // Retain the previous geometry while paused so the bars can decay.
            val layout = latest.audio.bandLayout.ifEmpty { rendered.layout }
            val sameLayout = layout == rendered.layout
            val rise = 1f - exp(-dt / .10f)
            val fall = 1f - exp(-dt / .20f)
            val waveBlend = 1f - exp(-dt / (if (active) .035f else .09f))
            val nextBands = FloatArray(layout.size) { index ->
                val target = targetBands?.getOrNull(index)?.let {
                    DisplayGain.decibels(it, gain, SpectrumMath.FLOOR_DB)
                }?.coerceAtLeast(SpectrumFloorDb) ?: SpectrumFloorDb
                val old = if (sameLayout) rendered.bands[index] else SpectrumFloorDb
                val next = old + (target - old) * (if (target > old) rise else fall)
                if (!active && abs(next - target) < .05f) target else next
            }
            val nextWave = FloatArray(96) { index ->
                val target = ((targetWave?.getOrNull(index) ?: 0f) * waveGain).coerceIn(-1f, 1f)
                val next = rendered.wave[index] + (target - rendered.wave[index]) * waveBlend
                if (!active && abs(next - target) < .001f) target else next
            }
            if (!sameLayout || !nextBands.contentEquals(rendered.bands) || !nextWave.contentEquals(rendered.wave)) {
                rendered = DrawingFrame(nextBands, nextWave, layout)
            }
        }
    }
    return rendered
}

@Composable
internal fun SpectrumChart(bands: FloatArray, layout: List<SpectrumBand>, u: Float, modifier: Modifier) {
    val defaultLayout = remember { SpectrumMath.bandLayout(1024, 48000) }
    val axisLayout = layout.ifEmpty { defaultLayout }
    val textSize = with(LocalDensity.current) { (9 * u).sp.toPx() }
    val paint = rememberInstrumentPaint()
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(3f, 6f)) }
    Canvas(modifier.semantics { contentDescription = "实时频谱，相对幅值刻度" }) {
        paint.textSize = textSize; paint.color = android.graphics.Color.rgb(126, 129, 138)
        val left = textSize * 3.5f
        val top = textSize * 2.3f
        val right = size.width - textSize * .8f
        val bottom = size.height - textSize * .5f
        val width = right - left
        val height = (bottom - top).coerceAtLeast(1f)
        for (j in 0..4) {
            val y = top + height * j / 4f
            drawLine(Grid, Offset(left, y), Offset(right, y), 1f, pathEffect = dash)
            val label = if (j == 0) "0" else "−${j * 15}"
            drawIntoCanvas { it.nativeCanvas.drawText(label, left - textSize * .8f - paint.measureText(label), y + textSize * .35f, paint) }
        }
        FrequencyLabels.forEach { (hz, label) ->
            val fraction = SpectrumMath.frequencyPosition(hz.toDouble(), axisLayout) ?: return@forEach
            val x = left + fraction * width
            drawLine(Grid, Offset(x, top), Offset(x, bottom), 1f, pathEffect = dash)
            drawIntoCanvas { it.nativeCanvas.drawText(label, (x - paint.measureText(label) / 2).coerceIn(0f, right - paint.measureText(label)), textSize, paint) }
        }
        val step = width / bands.size.coerceAtLeast(1)
        bands.forEachIndexed { index, db ->
            val h = ((db - SpectrumFloorDb) / -SpectrumFloorDb).coerceIn(0f, 1f) * height
            if (h > .3f) drawRect(Color(0xFFB6B8BE), Offset(left + index * step + step * .18f, bottom - h), Size(step * .62f, h))
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
    else "%d:%02d".format(total / 60, total % 60)
}

@Composable
internal fun SongProgress(media: NowPlaying, position: Long, u: Float, modifier: Modifier, onSeek: (Long) -> Unit) {
    if (media.duration <= 0) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("无可用进度信息", color = Muted, fontSize = (12 * u).sp) }
        return
    }
    val fraction = (position.toDouble() / media.duration).toFloat().coerceIn(0f, 1f)
    val textSize = with(LocalDensity.current) { (10 * u).sp.toPx() }
    val paint = rememberInstrumentPaint()
    Canvas(modifier.semantics {
        contentDescription = "播放进度 ${formatTime(position)}，共 ${formatTime(media.duration)}"
        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        if (media.canSeek) setProgress { onSeek((it.coerceIn(0f, 1f) * media.duration).toLong()); true }
    }) {
        val inset = ProgressTrack.INSET_PX
        val width = ProgressTrack.drawableWidth(size.width)
        val x = inset + fraction * width
        val tickHeight = textSize
        val playheadHalfHeight = tickHeight * 1.45f
        val labelHeight = textSize * 1.7f
        val labelGap = textSize * .2f
        // Center the complete badge + playhead group, leaving equal outer margins.
        val groupHeight = labelHeight + labelGap + playheadHalfHeight * 2
        val labelTop = (size.height - groupHeight) / 2f
        val centerY = labelTop + labelHeight + labelGap + playheadHalfHeight
        val labelCenterY = labelTop + labelHeight / 2
        paint.textSize = textSize; paint.color = android.graphics.Color.rgb(126, 129, 138)
        // All three times share a row. The badge is drawn last and covers edge times.
        val edgeBaseline = labelCenterY - (paint.ascent() + paint.descent()) / 2
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText("0:00", inset, edgeBaseline, paint)
            val duration = formatTime(media.duration)
            canvas.nativeCanvas.drawText(duration, size.width - paint.measureText(duration) - inset, edgeBaseline, paint)
        }
        val ticks = 81
        for (i in 0 until ticks) {
            val tx = inset + width * i / (ticks - 1)
            val h = if (i % 5 == 0) tickHeight else tickHeight * .5f
            drawLine(if (tx <= x) Color(0xFFB0B3BA) else Color(0xFF393D44), Offset(tx, centerY - h), Offset(tx, centerY + h), 1.5f)
        }
        drawLine(Red, Offset(x, centerY - playheadHalfHeight), Offset(x, centerY + playheadHalfHeight), 3f)
        val label = formatTime(position)
        paint.textSize = textSize * 1.2f
        val labelWidth = paint.measureText(label) + textSize * 1.3f
        val labelLeft = (x - labelWidth / 2).coerceIn(0f, (size.width - labelWidth).coerceAtLeast(0f))
        drawRect(Ink, Offset(labelLeft, labelTop), Size(labelWidth, labelHeight))
        drawRect(Color.Black, Offset(labelLeft, labelTop), Size(labelWidth, labelHeight), style = Stroke(1.5f))
        paint.color = android.graphics.Color.BLACK
        drawIntoCanvas { it.nativeCanvas.drawText(label, labelLeft + (labelWidth - paint.measureText(label)) / 2,
            labelTop + labelHeight / 2 - (paint.ascent() + paint.descent()) / 2, paint) }
    }
}

@Composable
internal fun Metrics(state: DeskState, wave: FloatArray, layout: DeskLayout, modifier: Modifier) {
    val u = layout.unit
    val inset = layout.metricInsetDp.dp
    val active = state.status == DisplayStatus.PLAYING
    val gain = state.displayGainDb
    val level = if (!active) "—" else state.audio.levelDb?.let {
        if (it <= -96f) "−∞" else String.format(Locale.US, "%.1f",
            DisplayGain.decibels(it, gain, -96f)).replace('-', '−')
    } ?: "—"
    val dynamic = if (active) state.audio.dynamicDb?.let { String.format(Locale.US, "%.1f", it) } ?: "—" else "—"
    Row(modifier.padding(top = (10 * u).dp, bottom = (9 * u).dp)) {
        Column(Modifier.weight(layout.waveformWeight).fillMaxHeight().padding(end = inset)) {
            MetricLabel("WAVEFORM", u)
            Spacer(Modifier.height((4 * u).dp))
            Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "实时声音波形" }) {
                val pad = 1.dp.toPx()
                val count = wave.size / 2
                val step = (size.width - 2 * pad) / (count - 1).coerceAtLeast(1)
                for (i in 0 until count) {
                    val low = wave[2 * i]; val high = wave[2 * i + 1]
                    if (high - low > .005f) drawLine(Ink.copy(alpha = .8f),
                        Offset(pad + i * step, center.y - high * size.height * .42f),
                        Offset(pad + i * step, center.y - low * size.height * .42f),
                        min(1.2.dp.toPx(), step * .3f), StrokeCap.Round)
                }
            }
        }
        VerticalDivider(color = Grid, thickness = .5.dp)
        val gainLabel = if (gain > 0f) "+${gain.roundToInt()}" else "${gain.roundToInt()}"
        Metric(if (gain != 0f) "LEVEL $gainLabel" else "LEVEL", level, "dBFS", layout,
            Modifier.weight(1f).padding(horizontal = inset).semantics {
                contentDescription = if (gain != 0f) "显示电平，显示增益 $gainLabel 分贝" else "音频电平"
            })
        VerticalDivider(color = Grid, thickness = .5.dp)
        Metric("DYNAMIC", dynamic, "dB", layout, Modifier.weight(1f).padding(start = inset))
    }
}

@Composable
private fun MetricLabel(label: String, u: Float) {
    Text(label, color = Muted, fontSize = (9 * u).sp, lineHeight = (12 * u).sp,
        fontFamily = InstrumentFont, fontWeight = FontWeight.Medium, maxLines = 1)
}

@Composable
private fun Metric(label: String, value: String, unit: String, layout: DeskLayout, modifier: Modifier) {
    val u = layout.unit
    Column(modifier.fillMaxHeight()) {
        MetricLabel(label, u)
        Spacer(Modifier.height((4 * u).dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
            Row(horizontalArrangement = Arrangement.spacedBy((3 * u).dp)) {
                Text(value, color = Ink, fontSize = layout.metricValueSp.sp, lineHeight = (24 * u).sp, fontFamily = InstrumentFont,
                    letterSpacing = if (layout.isWideFold) 0.sp else TextUnit.Unspecified,
                    fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.alignByBaseline())
                Text(unit, color = Muted, fontSize = layout.metricUnitSp.sp, lineHeight = (11 * u).sp, fontFamily = InstrumentFont,
                    letterSpacing = if (layout.isWideFold) 0.sp else TextUnit.Unspecified,
                    fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.alignByBaseline())
            }
        }
    }
}
