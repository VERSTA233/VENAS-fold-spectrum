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
        // Keep the test process visible: ColorOS freezes background instrumentation.
        startActivitySync(android.content.Intent(targetContext, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        runOnMainSync {
            try {
                val face = songTypeface(targetContext.resources)
                repeat(100) {
                    val configuration = android.content.res.Configuration(targetContext.resources.configuration)
                    configuration.orientation = if (it % 2 == 0) 1 else 2
                    val recreatedResources = targetContext.createConfigurationContext(configuration).resources
                    check(songTypeface(recreatedResources) === face) { "Recreated UI allocated another song font" }
                }
                report.appendLine("Font reused across 100 configuration contexts")
                verifyArtworkMemory(report)
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
                    for (text in listOf("都市之蓝", "City Blue (都市之蓝)", "音楽の時間", "カタカナ", "한글 음악", "City Blue")) {
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
        if (failure == null) {
            try { verifyAudioCleanup(report) } catch (t: Throwable) { failure = t }
        }
        val result = Bundle().apply {
            putString("stream", report.toString() + (failure?.stackTraceToString() ?: "PASS\n"))
        }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }

    private fun verifyArtworkMemory(report: StringBuilder) {
        val source = android.graphics.Bitmap.createBitmap(4096, 2048, android.graphics.Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.BLUE)
        val bounded = com.venas.foldspectrum.media.boundedArtwork(source, null)
        check(bounded.width == 1024 && bounded.height == 512)
        check(bounded.allocationByteCount <= 4 * 1024 * 1024)
        check(!source.isRecycled)
        repeat(30) {
            check(com.venas.foldspectrum.media.boundedArtwork(source, bounded) === bounded)
            check(!source.isRecycled && !bounded.isRecycled)
        }
        source.eraseColor(Color.RED)
        val changed = com.venas.foldspectrum.media.boundedArtwork(source, bounded)
        check(changed !== bounded && changed.getPixel(0, 0) == Color.RED)
        val output = java.io.ByteArrayOutputStream()
        source.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        val decoded = requireNotNull(com.venas.foldspectrum.media.decodeArtwork(output.toByteArray()))
        check(decoded.width <= 1024 && decoded.height <= 1024)
        check(com.venas.foldspectrum.media.decodeArtwork(byteArrayOf(1, 2, 3)) == null)
        // Test fixtures are exclusively owned here and no longer rendered anywhere.
        source.recycle(); bounded.recycle(); changed.recycle(); decoded.recycle()
        report.appendLine("Artwork: 4096px bounded to 1024px; 30 duplicate updates reused; changed cover/URI decode passed")
    }

    /** Uses only the visualization effect; never sends playback or volume commands. */
    private fun verifyAudioCleanup(report: StringBuilder) {
        val manager = targetContext.getSystemService(android.media.AudioManager::class.java)
        fun workers() = Thread.getAllStackTraces().keys.filter { it.isAlive && it.name == "VenasAudio" }.toSet()
        val baseline = workers()
        repeat(10) {
            val capture = com.venas.foldspectrum.audio.AudioCapture(manager)
            capture.start()
            Thread.sleep(120)
            capture.stop()
            capture.close()
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            while ((workers() - baseline).isNotEmpty() && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(20)
            }
            val retained = workers() - baseline
            check(retained.isEmpty()) { "Audio worker retained after close #$it: " +
                retained.joinToString("\n") { thread -> thread.stackTrace.joinToString("\n") } }
            check(capture.state.value.capture == com.venas.foldspectrum.core.Capture.IDLE)
        }
        report.appendLine("Audio: 10 start/stop/close cycles, no retained VenasAudio workers")
    }
}
