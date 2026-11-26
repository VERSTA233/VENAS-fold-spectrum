package com.venas.foldspectrum.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max
import kotlin.math.roundToInt

internal const val MAX_ARTWORK_EDGE = 1024

/** Metadata bitmaps are borrowed from MediaController: never recycle those images.
 * Only a newly scaled, unpublished duplicate belongs exclusively to this function.
 */
internal fun boundedArtwork(source: Bitmap, previous: Bitmap?): Bitmap {
    if (source === previous) return source
    val longest = max(source.width, source.height)
    val result = if (longest <= MAX_ARTWORK_EDGE) source else {
        val scale = MAX_ARTWORK_EDGE.toFloat() / longest
        Bitmap.createScaledBitmap(source, (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1), true)
    }
    if (previous != null && result.sameAs(previous)) {
        if (result !== source) result.recycle()
        return previous
    }
    return result
}

internal fun decodeArtwork(bytes: ByteArray): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    options.inSampleSize = 1
    while (max(options.outWidth, options.outHeight).toLong() > MAX_ARTWORK_EDGE.toLong() * options.inSampleSize) {
        options.inSampleSize *= 2
    }
    options.inJustDecodeBounds = false
    options.inPreferredConfig = Bitmap.Config.ARGB_8888
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
