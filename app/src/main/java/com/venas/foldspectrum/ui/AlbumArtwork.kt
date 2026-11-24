package com.venas.foldspectrum.ui

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.venas.foldspectrum.R
import com.venas.foldspectrum.core.CoverProjection
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class CoverArtwork(val source: String, val bitmap: Bitmap?)

@Stable
internal class TurningCover(val artwork: CoverArtwork) {
    val turn = Animatable(0f)
    var finger by mutableStateOf<Float?>(null)
    var grabbedAt = 0f
    var job: Job? = null
    val progress: Float get() = finger ?: turn.value
}

/** Owns only cover presentation. Playback commands still belong to the media controller. */
@Stable
internal class AlbumMotion(private val scope: CoroutineScope, initial: CoverArtwork) {
    var current by mutableStateOf(initial)
        private set
    var held by mutableStateOf<TurningCover?>(null)
        private set
    val departing = mutableStateListOf<TurningCover>()
    var waitingFrom by mutableStateOf<String?>(null)
        private set
    var baseVisible by mutableStateOf(true)
        private set
    val arrival = Animatable(1f)
    private var arrivalJob: Job? = null
    private var responseJob: Job? = null
    val base: CoverArtwork get() = if (waitingFrom == current.source) CoverArtwork("waiting", null) else current

    fun update(artwork: CoverArtwork) {
        val changedTrack = artwork.source != current.source
        current = artwork
        if (changedTrack) {
            waitingFrom = null
            responseJob?.cancel()
            // A return-to-rest animation is only valid for the current track.
            // A page already committed to departure is never grabbed again.
            held?.takeIf { it.artwork.source != current.source && it.finger == null }?.let {
                depart(it, if (it.progress > 0f) 1f else -1f, 0f)
            }
            reveal()
        }
    }

    fun grab() {
        val old = held
        if (old != null && old.artwork.source != current.source) {
            depart(old, if (old.progress > 0f) 1f else -1f, 0f)
        }
        val page = held ?: TurningCover(current)
        val visible = page.progress
        page.job?.cancel()
        page.grabbedAt = visible
        page.finger = visible
        held = page
    }

    fun move(distancePx: Float, widthPx: Float) {
        val page = held ?: return
        baseVisible = false
        page.finger = (page.grabbedAt + distancePx / widthPx.coerceAtLeast(1f)).coerceIn(-1f, 1f)
    }

    fun release(next: Boolean?, velocityPx: Float, widthPx: Float) {
        val page = held ?: return
        val velocity = (velocityPx / widthPx.coerceAtLeast(1f)).coerceIn(-5f, 5f)
        if (next != null) {
            waitingFrom = current.source
            responseJob?.cancel()
            responseJob = scope.launch {
                delay(3000)
                if (waitingFrom != null) {
                    // Rejected/unsupported skip: restore the actual current cover.
                    waitingFrom = null
                    reveal()
                }
            }
            depart(page, if (next) -1f else 1f, velocity)
        } else if (page.artwork.source != current.source) {
            depart(page, if (page.progress > 0f) 1f else -1f, velocity)
        } else {
            val from = page.progress
            page.job?.cancel()
            page.job = scope.launch {
                page.turn.snapTo(from)
                page.finger = null
                page.turn.animateTo(0f, spring(dampingRatio = 1f, stiffness = 260f,
                    visibilityThreshold = .001f), initialVelocity = velocity)
                if (held === page) {
                    held = null
                    baseVisible = true
                }
            }
        }
    }

    private fun depart(page: TurningCover, target: Float, velocity: Float) {
        val from = page.progress
        page.job?.cancel()
        if (held === page) held = null
        if (page !in departing) departing.add(page)
        // Bound overlapping bitmap layers during very rapid repeated skips.
        while (departing.size > 3) departing.removeAt(0).job?.cancel()
        page.job = scope.launch {
            try {
                page.turn.snapTo(from)
                page.finger = null
                page.turn.animateTo(target, spring(dampingRatio = 1f, stiffness = 260f,
                    visibilityThreshold = .001f), initialVelocity = velocity)
            } finally {
                departing.remove(page)
            }
        }
        if (!baseVisible) reveal()
    }

    private fun reveal() {
        arrivalJob?.cancel()
        arrivalJob = scope.launch {
            // Crossfade handles a late bitmap; this gentle entrance belongs to
            // the new track, rather than pretending its cover was preloaded.
            arrival.snapTo(0f)
            baseVisible = true
            arrival.animateTo(1f, spring(dampingRatio = 1f, stiffness = 220f))
        }
    }
}

@Composable
internal fun AlbumCover(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Panel)
        .semantics { contentDescription = "专辑封面" }) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Image(painterResource(R.drawable.album_placeholder), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    }
}

@Composable
internal fun TurningAlbum(motion: AlbumMotion, scale: () -> Float, modifier: Modifier) {
    val spatialMotion = ValueAnimator.areAnimatorsEnabled()
    Box(modifier.graphicsLayer {
        transformOrigin = TransformOrigin.Center
        scaleX = scale(); scaleY = scale()
    }) {
        val base = motion.base
        // A player may parcel a new Bitmap instance on every play/pause update.
        // That is not a new picture and must never restart a fade to black.
        val identity = base.source to (base.bitmap != null)
        Crossfade(identity, Modifier.fillMaxSize().graphicsLayer {
            val reveal = motion.arrival.value
            alpha = if (motion.baseVisible) reveal else 0f
            scaleX = if (spatialMotion) .96f + .04f * reveal else 1f
            scaleY = scaleX
        }, animationSpec = tween(180), label = "loaded artwork") { key ->
            val previous = remember(key) { base }
            AlbumCover((if (key == identity) base else previous).bitmap, Modifier.fillMaxSize())
        }
        (motion.departing.toList() + listOfNotNull(motion.held)).forEach { page ->
            key(page) {
                ProjectedAlbum(page.artwork.bitmap, { page.progress }, spatialMotion)
            }
        }
    }
}

@Composable
private fun ProjectedAlbum(artwork: Bitmap?, progress: () -> Float, spatialMotion: Boolean) {
    val context = LocalContext.current
    val bitmap = artwork ?: remember(context) {
        requireNotNull(context.getDrawable(R.drawable.album_placeholder)).toBitmap(512, 512)
    }
    val shader = remember(bitmap) { BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val projection = remember { Matrix() }
    val crop = remember { Matrix() }
    Canvas(Modifier.fillMaxSize()) {
        val page = progress()
        val amount = abs(page).coerceIn(0f, 1f)
        val bounds = RectF(0f, 0f, size.width, size.height)
        val fit = maxOf(size.width / bitmap.width, size.height / bitmap.height)
        crop.setScale(fit, fit)
        crop.postTranslate((size.width - bitmap.width * fit) / 2f, (size.height - bitmap.height * fit) / 2f)
        shader.setLocalMatrix(crop)
        paint.shader = shader
        paint.alpha = ((1f - amount * amount) * 255).toInt().coerceIn(0, 255)
        projection.setPolyToPoly(floatArrayOf(0f, 0f, size.width, 0f, size.width, size.height, 0f, size.height),
            0, CoverProjection.corners(size.width, size.height, if (spatialMotion) page else 0f), 0, 4)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.concat(projection)
            // Project the rounded shape and its bitmap together. The free edge
            // really changes height; this is not a horizontal scale effect.
            native.drawRoundRect(bounds, 12.dp.toPx(), 12.dp.toPx(), paint)
            native.restore()
        }
    }
}
