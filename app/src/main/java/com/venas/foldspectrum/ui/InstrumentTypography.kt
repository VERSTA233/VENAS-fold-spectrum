package com.venas.foldspectrum.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font as NativeFont
import android.graphics.fonts.FontFamily as NativeFontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import com.venas.foldspectrum.R

/** Bundled face keeps the instrument panel consistent across vendor font settings. */
internal val InstrumentFont = FontFamily(
    Font(R.font.sf_mono_regular, FontWeight.Normal),
    Font(R.font.sf_mono_medium, FontWeight.Medium),
    Font(R.font.sf_mono_semibold, FontWeight.SemiBold),
)

/** Keep the declared line height even for a single line, across Latin/CJK fallback. */
internal val FixedLineTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/** Android's glyph fallback keeps mixed Latin/CJK text in one measured text run. */
@Composable
internal fun rememberSongTypeface(): Typeface {
    val resources = LocalContext.current.resources
    return remember(resources) { songTypeface(resources) }
}

internal fun songTypeface(resources: android.content.res.Resources): Typeface {
    val latin = NativeFontFamily.Builder(NativeFont.Builder(resources, R.font.sf_pro)
        .setFontVariationSettings("'wght' 400, 'opsz' 17").build()).build()
    val chinese = NativeFontFamily.Builder(NativeFont.Builder(resources, R.font.source_han_sans_sc_regular).build()).build()
    return Typeface.CustomFallbackBuilder(latin).addCustomFallback(chinese)
        .setSystemFallback("sans-serif").build()
}

/** Each Canvas owns its paint; font selection is shared with the Compose labels. */
@Composable
internal fun rememberInstrumentPaint(): Paint {
    val resources = LocalContext.current.resources
    return remember(resources) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = resources.getFont(R.font.sf_mono_medium) }
    }
}
