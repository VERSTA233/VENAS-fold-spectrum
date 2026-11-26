package com.venas.foldspectrum.ui

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venas.foldspectrum.R
import kotlin.math.roundToInt

/** Always budget two title lines; the current title and its wrapping are not inputs. */
internal fun coverTitleGapPx(unit: Float, density: Float, unitBottom: Float,
                             coverBottom: Float, titleLineHeight: Int, artistInkBottom: Float): Float =
    (unitBottom - coverBottom - 2 * titleLineHeight - (5 * unit * density).roundToInt() - artistInkBottom)
        .coerceIn(0f, 24 * unit * density)

@Composable
internal fun rememberCoverTitleGap(layout: DeskLayout, height: Dp, face: Typeface, artist: String): Dp {
    val density = LocalDensity.current
    val resources = LocalContext.current.resources
    val measurer = rememberTextMeasurer()
    val baseStyle = LocalTextStyle.current
    return remember(layout, height, face, artist, density, resources, measurer, baseStyle) {
        with(density) {
            val u = layout.unit
            fun dpPx(value: Float) = value.dp.roundToPx()
            fun measure(text: String, size: Float, line: Float) = measurer.measure(
                AnnotatedString(text), style = baseStyle.copy(fontFamily = InstrumentFont,
                    fontWeight = FontWeight.Medium, fontSize = size.sp, lineHeight = line.sp,
                    letterSpacing = if (layout.isWideFold) 0.sp else baseStyle.letterSpacing),
                maxLines = 1, softWrap = false)
            // Mirror the baseline-aligned value/unit row in Metrics, including its
            // vertical centering. Use stable digits; no live audio values affect layout.
            val value = measure("−00.0", layout.metricValueSp, 24 * u)
            val unit = measure("dBFS", layout.metricUnitSp, 11 * u)
            val label = measure("LEVEL", 9 * u, 12 * u)
            val rowBaseline = maxOf(value.firstBaseline, unit.firstBaseline)
            val rowHeight = rowBaseline + maxOf(value.size.height - value.firstBaseline,
                unit.size.height - unit.firstBaseline)
            val metricsBottom = height.roundToPx() - dpPx(24 * u) - dpPx(9 * u)
            val numericTop = height.roundToPx() - dpPx(24 * u) - dpPx(70 * u) +
                dpPx(10 * u) + label.size.height + dpPx(4 * u)
            val rowTop = numericTop + ((metricsBottom - numericTop - rowHeight) / 2).roundToInt()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = resources.getFont(R.font.sf_mono_medium)
                textSize = layout.metricUnitSp.sp.toPx()
            }
            val bounds = Rect()
            paint.getTextBounds("dBFS", 0, 4, bounds)
            val unitBottom = rowTop + rowBaseline + bounds.bottom
            paint.typeface = face
            paint.textSize = (15 * u).sp.toPx()
            val artistLine = (20 * u).sp.toPx().roundToInt()
            val artistBaseline = fixedSongBaseline(paint.fontMetricsInt, artistLine)
            // A descender belongs to the visible glyph: align its ink, not TextView padding.
            paint.getTextBounds(artist, 0, artist.length, bounds)
            val artistBottom = artistBaseline + bounds.bottom.toFloat()
            val coverBottom = dpPx(24 * u) + dpPx(9 * u) + layout.coverDp.dp.roundToPx()
            coverTitleGapPx(u, density.density, unitBottom, coverBottom.toFloat(),
                (26 * u).sp.toPx().roundToInt(), artistBottom).toDp()
        }
    }
}
