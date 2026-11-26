package com.venas.foldspectrum.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.LineHeightSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

/** Full-size glyphs with a shared baseline grid, independent of fallback font metrics.
 * Compose 1.7 always enables fallback line spacing, so use the platform text view
 * here to opt out. TextView retains shaping, wrapping, ellipsis and accessibility.
 */
@Composable
internal fun SongText(text: String, typeface: Typeface, color: Color, fontSize: TextUnit,
                      lineHeight: TextUnit, maxLines: Int, centered: Boolean = false) {
    val density = LocalDensity.current
    val sizePx = with(density) { fontSize.toPx() }
    val linePx = with(density) { lineHeight.toPx() }.roundToInt()
    AndroidView(factory = { context -> SongTextView(context) }, modifier = Modifier,
        update = { it.bind(text, typeface, color.toArgb(), sizePx, linePx, maxLines, centered) })
}

internal class SongTextView(context: android.content.Context) : TextView(context) {
    private var lastLineHeight = -1

    init {
        includeFontPadding = false
        isFallbackLineSpacing = false
        setPadding(0, 0, 0, 0)
        ellipsize = TextUtils.TruncateAt.END
        // Match Material's previous default body letter spacing (0.5sp / 16sp).
        letterSpacing = .03125f
    }

    fun bind(value: String, face: Typeface, color: Int, sizePx: Float, linePx: Int,
             lines: Int, centered: Boolean) {
        val metricsChanged = typeface !== face || textSize != sizePx || lastLineHeight != linePx
        if (typeface !== face) typeface = face
        if (textSize != sizePx) setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
        if (currentTextColor != color) setTextColor(color)
        if (maxLines != lines) maxLines = lines
        val alignment = Gravity.TOP or if (centered) Gravity.CENTER_HORIZONTAL else Gravity.START
        if (gravity != alignment) gravity = alignment
        if (metricsChanged || text.toString() != value) {
            val metrics = paint.fontMetricsInt // Bundled face metrics, independent of the current string.
            val baseline = fixedSongBaseline(metrics, linePx)
            text = SpannableString(value).apply {
                setSpan(FixedSongLineHeight(-baseline, linePx - baseline), 0, length,
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            }
            lastLineHeight = linePx
        }
    }
}

internal fun fixedSongBaseline(metrics: Paint.FontMetricsInt, linePx: Int): Int {
    val extra = linePx - (metrics.descent - metrics.ascent)
    return linePx - metrics.descent - (extra / 2f).roundToInt()
}

private class FixedSongLineHeight(private val ascent: Int, private val descent: Int) : LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int,
                              fm: Paint.FontMetricsInt) {
        fm.ascent = ascent
        fm.descent = descent
        fm.top = ascent
        fm.bottom = descent
        fm.leading = 0
    }
}
