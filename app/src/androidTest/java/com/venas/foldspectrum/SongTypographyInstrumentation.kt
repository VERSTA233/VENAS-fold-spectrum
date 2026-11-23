package com.venas.foldspectrum

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import com.venas.foldspectrum.ui.SongTextView
import com.venas.foldspectrum.ui.songTypeface

/** Exercises the real Android font/shaping engine; JVM stubs cannot catch fallback spacing. */
class SongTypographyInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val report = StringBuilder()
        var failure: Throwable? = null
        runOnMainSync {
            try {
                val face = songTypeface(targetContext.resources)
                val view = SongTextView(targetContext)
                view.layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                for (scale in listOf(1f, 1.5f)) {
                    val size = 58.4f * scale
                    val line = (72.3f * scale).toInt()
                    val width = (627 * scale).toInt()
                    fun measure(text: String, lines: Int = 2): List<Int> {
                        view.bind(text, face, Color.WHITE, size, line, lines, false)
                        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                        check(view.textSize == size) { "Text size changed" }
                        return listOf(view.measuredHeight, view.baseline, view.lineCount)
                    }
                    val reference = measure("City Blue")
                    for (text in listOf("都市之蓝", "City Blue (都市之蓝)", "音楽の時間", "City Blue")) {
                        val actual = measure(text)
                        check(actual == reference) { "$text $actual != $reference" }
                        val bounds = Rect()
                        view.paint.getTextBounds(text, 0, text.length, bounds)
                        check(view.baseline + bounds.top >= 0 && view.baseline + bounds.bottom <= view.measuredHeight) {
                            "Clipped glyphs: $text $bounds within $actual"
                        }
                        report.appendLine("scale=$scale text=$text height/baseline/lines=$actual")
                    }
                    val two = measure("City Blue\n都市之蓝")
                    val inverse = measure("都市之蓝\nCity Blue")
                    check(two == inverse && two[2] == 2) { "Mixed two-line metrics differ" }
                    check(view.layout.getLineBaseline(1) - view.layout.getLineBaseline(0) == line)
                    check(two[0] == reference[0] + line) { "Second line did not add one fixed line" }
                    val long = measure("都市之蓝 City Blue 都市之蓝 City Blue 都市之蓝 City Blue 都市之蓝 City Blue")
                    check(long[2] == 2 && view.layout.getEllipsisCount(1) > 0) { "Two-line ellipsis missing" }
                    val one = measure("都市之蓝 City Blue 都市之蓝 City Blue", 1)
                    check(one[2] == 1 && view.layout.getEllipsisCount(0) > 0) { "Artist ellipsis missing" }
                    check(measure("City Blue") == reference) { "Rebinding retained old line height" }
                    report.appendLine("scale=$scale multiline/ellipsis/rebind passed")
                }
            } catch (t: Throwable) { failure = t }
        }
        val result = Bundle().apply {
            putString("stream", report.toString() + (failure?.stackTraceToString() ?: "PASS\n"))
        }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
