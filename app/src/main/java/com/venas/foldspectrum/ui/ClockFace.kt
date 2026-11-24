package com.venas.foldspectrum.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
internal fun ClockFace() {
    var time by remember { mutableStateOf(LocalTime.now()) }
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    LaunchedEffect(Unit) { while (true) { time = LocalTime.now(); delay(1000) } }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val clockStyle = FixedLineTextStyle.copy(fontFamily = InstrumentFont, fontWeight = FontWeight.Normal,
        fontSize = 144.sp, lineHeight = 160.sp, letterSpacing = 0.sp, textAlign = TextAlign.Center)
    val hint = "播放音乐可显示音频可视化"
    val hintStyle = FixedLineTextStyle.copy(fontSize = 14.sp, lineHeight = 20.sp,
        letterSpacing = 1.sp, textAlign = TextAlign.Center)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hintSize = measurer.measure(hint, hintStyle,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = with(density) { maxWidth.roundToPx() })).size
        val reference = measurer.measure("88:88", clockStyle, softWrap = false, maxLines = 1).size
        val gapPx = with(density) { 14.dp.toPx() }
        // Fit actual font metrics to both axes. Reserve room below the centered
        // clock for the hint, rather than letting the hint push the clock upward.
        val fit = minOf(1f, with(density) { maxWidth.toPx() } / reference.width,
            (with(density) { maxHeight.toPx() } - 2 * (gapPx + hintSize.height)) / reference.height)
            .coerceAtLeast(0f)
        val fittedStyle = clockStyle.copy(fontSize = (144 * fit).sp, lineHeight = (160 * fit).sp)
        val clockHeight = measurer.measure("88:88", fittedStyle, softWrap = false, maxLines = 1).size.height
        Text(time.format(formatter), color = Ink, style = fittedStyle, maxLines = 1, softWrap = false,
            modifier = Modifier.align(Alignment.Center))
        Text(hint, color = Muted, style = hintStyle,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center).offset(y = with(density) {
                (clockHeight / 2f + gapPx + hintSize.height / 2f).toDp()
            }))
    }
}

