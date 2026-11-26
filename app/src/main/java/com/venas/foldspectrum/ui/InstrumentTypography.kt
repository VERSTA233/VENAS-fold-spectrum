package com.venas.foldspectrum.ui

import android.graphics.Paint
import android.graphics.Typeface
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

/** A shared Latin/CJK face keeps song text consistent across languages. */
@Composable
internal fun rememberSongTypeface(): Typeface {
    val resources = LocalContext.current.resources
    return remember(resources) { songTypeface(resources) }
}

// Fixed bundled fonts have no locale/night variants. Keep a single strong reference
// across page disposal and Activity recreation; never retain an Activity or Resources.
private var cachedSongTypeface: Typeface? = null

@Synchronized
internal fun songTypeface(resources: android.content.res.Resources): Typeface {
    cachedSongTypeface?.let { return it }
    return resources.getFont(R.font.sarasa_gothic_sc_regular).also { cachedSongTypeface = it }
}

/** Each Canvas owns its paint; font selection is shared with the Compose labels. */
@Composable
internal fun rememberInstrumentPaint(): Paint {
    val resources = LocalContext.current.resources
    return remember(resources) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = resources.getFont(R.font.sf_mono_medium) }
    }
}
