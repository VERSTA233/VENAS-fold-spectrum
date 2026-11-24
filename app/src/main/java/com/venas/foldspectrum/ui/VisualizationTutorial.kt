package com.venas.foldspectrum.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venas.foldspectrum.R

private val GuideFont = FontFamily(Font(R.font.source_han_sans_sc_regular))

/** Anchored to the measured controls, including wide-fold layouts and root rotation.
 * This layer owns all touches; reading the guide must not pause, skip or seek.
 */
@Composable
internal fun VisualizationTutorial(cover: Rect, progress: Rect, layout: DeskLayout, onDone: () -> Unit) {
    val density = LocalDensity.current
    val u = layout.unit
    val coverLeft = with(density) { cover.left.toDp() }
    val coverTop = with(density) { cover.top.toDp() }
    val coverWidth = with(density) { cover.width.toDp() }
    val progressLeft = with(density) { progress.left.toDp() }
    val progressTop = with(density) { progress.top.toDp() }
    val progressWidth = with(density) { progress.width.toDp() }
    val progressHeight = with(density) { progress.height.toDp() }
    BackHandler(onBack = onDone)
    Box(Modifier.fillMaxSize().semantics { paneTitle = "可视化操作指南" }
        .background(Color.Black.copy(alpha = .68f))
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false).consume()
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }) {
        Box(Modifier.offset(coverLeft, coverTop).size(coverWidth)
            .border(1.dp, Ink.copy(alpha = .35f), RoundedCornerShape(coverWidth * .06f))) {
            Row(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = (8 * u).dp),
                verticalAlignment = Alignment.CenterVertically) {
                CoverHint("单击暂停/播放", tap = true, u, Modifier.weight(1f))
                CoverHint("滑动切歌", tap = false, u, Modifier.weight(1f))
            }
        }
        // The progress callout sits on the actual seek region, not a guessed screen fraction.
        Box(Modifier.offset(progressLeft, progressTop).size(progressWidth, progressHeight)
            .border(1.dp, Ink.copy(alpha = .25f), RoundedCornerShape((8 * u).dp)),
            contentAlignment = Alignment.Center) {
            Row(Modifier.background(Panel, RoundedCornerShape((12 * u).dp))
                .padding(horizontal = (18 * u).dp, vertical = (10 * u).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((10 * u).dp)) {
                GestureGlyph(false, Modifier.size((26 * u).dp))
                GuideText("拖动跳转进度", u)
            }
        }
        Box(Modifier.align(Alignment.TopEnd)
            .padding(end = layout.horizontalGapDp.dp, top = (28 * u).dp)
            .widthIn(max = progressWidth)
            .background(Panel, RoundedCornerShape((12 * u).dp))
            .padding(horizontal = (14 * u).dp, vertical = (10 * u).dp)) {
            GuideText("单击空白区域显示设置图标", u)
        }
        Box(Modifier.align(Alignment.BottomStart)
            .padding(start = progressLeft, bottom = (30 * u).dp).width(progressWidth),
            contentAlignment = Alignment.Center) {
            Button(onClick = onDone, shape = RoundedCornerShape((14 * u).dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Background),
                contentPadding = PaddingValues(horizontal = (32 * u).dp, vertical = (10 * u).dp),
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text("知道了", fontFamily = GuideFont, fontSize = (14 * u).sp)
            }
        }
    }
}

@Composable
private fun CoverHint(label: String, tap: Boolean, u: Float, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((12 * u).dp)) {
        GestureGlyph(tap, Modifier.size((36 * u).dp))
        GuideText(label, u)
    }
}

@Composable
private fun GuideText(label: String, u: Float) {
    Text(label, color = Ink, fontFamily = GuideFont, fontSize = (13 * u).sp,
        lineHeight = (20 * u).sp, textAlign = TextAlign.Center)
}

/** Small vector gestures keep the APK independent of an icon library or raster assets. */
@Composable
private fun GestureGlyph(tap: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val scale = size.minDimension / 36f
        fun point(x: Float, y: Float) = Offset(x * scale, y * scale)
        val stroke = Stroke(1.6f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (tap) {
            val hand = Path().apply {
                moveTo(12 * scale, 20 * scale)
                lineTo(12 * scale, 10 * scale)
                cubicTo(12 * scale, 6 * scale, 17 * scale, 6 * scale, 17 * scale, 10 * scale)
                lineTo(17 * scale, 17 * scale)
                cubicTo(23 * scale, 15 * scale, 29 * scale, 18 * scale, 28 * scale, 23 * scale)
                lineTo(26 * scale, 31 * scale)
                lineTo(15 * scale, 31 * scale)
                lineTo(8 * scale, 22 * scale)
                cubicTo(6 * scale, 19 * scale, 9 * scale, 17 * scale, 12 * scale, 20 * scale)
                close()
            }
            drawPath(hand, Ink, style = stroke)
            for ((a, b) in listOf(point(14.5f, 1f) to point(14.5f, 3f),
                point(5f, 5f) to point(7f, 7f), point(22f, 5f) to point(24f, 3f))) {
                drawLine(Ink, a, b, stroke.width, StrokeCap.Round)
            }
        } else {
            drawLine(Ink, point(3f, 18f), point(33f, 18f), stroke.width, StrokeCap.Round)
            for (direction in listOf(-1, 1)) {
                val x = if (direction == -1) 3f else 33f
                drawLine(Ink, point(x, 18f), point(x - direction * 6, 12f), stroke.width, StrokeCap.Round)
                drawLine(Ink, point(x, 18f), point(x - direction * 6, 24f), stroke.width, StrokeCap.Round)
            }
            drawCircle(Panel, 4 * scale, point(18f, 18f))
            drawCircle(Ink, 4 * scale, point(18f, 18f), style = stroke)
        }
    }
}
