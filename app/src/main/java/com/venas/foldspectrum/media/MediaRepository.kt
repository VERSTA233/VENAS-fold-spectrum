package com.venas.foldspectrum.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.venas.foldspectrum.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.InputStream
import java.io.ByteArrayOutputStream

data class NowPlaying(
    val player: String,
    val trackKey: String,
    val title: String,
    val artist: String,
    val artwork: Bitmap? = null,
    val duration: Long = 0,
    val position: Long = 0,
    val positionUpdatedAt: Long = 0,
    val speed: Float = 1f,
    val playback: Playback = Playback.PAUSED,
    val actions: Long = 0,
    val sessionKey: String = player,
    val positionRevision: Long = 0,
    val positionReceivedAt: Long = 0,
) {
    val canSeek get() = duration > 0 && actions and PlaybackState.ACTION_SEEK_TO != 0L
    fun seekTimeline() = SeekTimeline(
        source = "$sessionKey|$trackKey", duration = duration, position = position,
        updatedAt = positionUpdatedAt, revision = positionRevision, receivedAt = positionReceivedAt,
        speed = speed, playing = playback == Playback.PLAYING, loading = playback == Playback.LOADING, canSeek = canSeek,
    )
}

class MediaRepository(private val context: Context, private val scope: CoroutineScope) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaAccessService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow<NowPlaying?>(null)
    val state = mutable.asStateFlow()
    private var selected: MediaController? = null
    private val controllers = linkedMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private data class PositionObservation(val state: PlaybackState?, val revision: Long, val receivedAt: Long)
    private val positions = mutableMapOf<MediaSession.Token, PositionObservation>()
    private var positionRevision = 0L
    private var running = false
    private var changesJob: Job? = null
    private var artworkJob: Job? = null
    private var artworkRevision = 0
    private var artworkSignature: String? = null
    private var selectedKey: String? = null
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { updateControllers(it.orEmpty()) }

    fun start() {
        if (running) return
        running = true
        try {
            manager.addOnActiveSessionsChangedListener(listener, component, handler)
            refresh()
            changesJob = scope.launch { MediaAccessService.changes.collect { refresh() } }
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access unavailable", e)
            stop()
        }
    }

    private fun refresh() {
        if (!running) return
        try { updateControllers(manager.getActiveSessions(component)) }
        catch (e: SecurityException) { stop() }
    }

    private fun updateControllers(list: List<MediaController>) {
        val tokens = list.map { it.sessionToken }.toSet()
        controllers.keys.filter { it !in tokens }.forEach { token ->
            controllers.remove(token)?.let { (c, cb) -> c.unregisterCallback(cb) }
            positions.remove(token)
        }
        list.forEach { controller ->
            if (controllers.containsKey(controller.sessionToken)) return@forEach
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    if (controller.sessionToken !in controllers) return
                    val previousTime = positions[controller.sessionToken]?.state?.lastPositionUpdateTime ?: 0L
                    if (state != null && !acceptsPositionUpdate(previousTime, state.lastPositionUpdateTime)) return
                    positions[controller.sessionToken] = PositionObservation(state, ++positionRevision, SystemClock.elapsedRealtime())
                    publish()
                }
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                override fun onSessionDestroyed() {
                    controllers.remove(controller.sessionToken)?.let { (c, cb) -> c.unregisterCallback(cb) }
                    positions.remove(controller.sessionToken)
                    publish()
                }
            }
            controller.registerCallback(callback, handler)
            controllers[controller.sessionToken] = controller to callback
            positions[controller.sessionToken] = PositionObservation(controller.playbackState, ++positionRevision, SystemClock.elapsedRealtime())
        }
        publish()
    }

    private fun key(c: MediaController) = "${c.packageName}:${c.sessionToken.hashCode()}"
    private fun playbackState(c: MediaController) = positions[c.sessionToken]?.state

    private fun publish() {
        val usable = controllers.values.map { it.first }.filter {
            val ps = playbackState(it)
            val title = it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: it.metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ps != null && ps.state != PlaybackState.STATE_NONE && ps.state != PlaybackState.STATE_STOPPED &&
                ps.state != PlaybackState.STATE_ERROR && !title.isNullOrBlank()
        }
        val candidates = usable.map { c ->
            val ps = playbackState(c)!!
            SessionCandidate(key(c), ps.state in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING,
                PlaybackState.STATE_CONNECTING, PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS), ps.lastPositionUpdateTime)
        }
        val chosen = chooseSession(candidates, selectedKey)
        selected = usable.firstOrNull { key(it) == chosen }
        val c = selected
        if (c == null) {
            artworkJob?.cancel(); artworkSignature = null; artworkRevision++
            mutable.value = null
            return
        }
        selectedKey = chosen
        val md = c.metadata ?: return
        val observation = positions[c.sessionToken] ?: return
        val ps = observation.state ?: return
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "未知曲目"
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "未知艺人"
        val trackKey = trackIdentity(c.packageName, md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID), title, artist)
        val bitmap = sequenceOf(MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            .mapNotNull { runCatching { md.getBitmap(it) }.getOrNull() }.firstOrNull()
        val uri = sequenceOf(MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            .mapNotNull { md.getString(it) }.firstOrNull()
        val old = mutable.value
        val sameTrack = old?.trackKey == trackKey && old.sessionKey == key(c)
        val previousArtwork = old?.takeIf { sameTrack }?.artwork
        // Media apps can parcel identical pixels into a fresh Bitmap with each lyric.
        // generationId is object-local, not an artwork identity. Preserve the old image
        // if pixels match, while still accepting genuinely revised artwork for this song.
        val artwork = when {
            bitmap == null -> previousArtwork
            bitmap === previousArtwork -> previousArtwork
            previousArtwork != null && runCatching { bitmap.sameAs(previousArtwork) }.getOrDefault(false) -> previousArtwork
            else -> bitmap
        }
        val playback = when (ps.state) {
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> Playback.PLAYING
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> Playback.LOADING
            else -> Playback.PAUSED
        }
        mutable.value = NowPlaying(c.packageName, trackKey, title, artist,
            artwork = artwork,
            duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            position = ps.position.coerceAtLeast(0), positionUpdatedAt = ps.lastPositionUpdateTime,
            speed = ps.playbackSpeed, playback = playback, actions = ps.actions,
            sessionKey = key(c), positionRevision = observation.revision, positionReceivedAt = observation.receivedAt)
        if (old?.trackKey != trackKey || old.playback != playback) {
            Log.i(TAG, "player=${c.packageName} playback=$playback duration=${mutable.value?.duration} actions=${ps.actions} art=${bitmap != null || uri != null}")
        }
        val sessionKey = key(c)
        val signature = "$sessionKey|$trackKey|$uri|${bitmap != null}"
        if (signature != artworkSignature) {
            artworkSignature = signature
            val revision = ++artworkRevision
            artworkJob?.cancel()
            if (bitmap == null && uri != null) artworkJob = scope.launch {
                val loaded = withContext(Dispatchers.IO) { loadArtwork(uri) }
                if (revision == artworkRevision && mutable.value?.trackKey == trackKey && mutable.value?.sessionKey == sessionKey) {
                    // A failed refresh must not erase the current song's valid cover.
                    if (loaded != null) mutable.value = mutable.value?.copy(artwork = loaded)
                }
            }
        }
    }

    private fun loadArtwork(value: String): Bitmap? = runCatching {
        val uri = Uri.parse(value)
        val bytes = when (uri.scheme) {
            "content", "android.resource", "file" -> context.contentResolver.openInputStream(uri)?.use { stream ->
                readArtworkBytes(stream)
            }
            "https" -> (URL(value).openConnection() as HttpURLConnection).let { connection ->
                connection.connectTimeout = 4000; connection.readTimeout = 4000
                try { connection.inputStream.use { readArtworkBytes(it) } }
                finally { connection.disconnect() }
            }
            else -> null
        } ?: return@runCatching null
        if (bytes.size > 6 * 1024 * 1024) return@runCatching null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1024) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()

    private fun readArtworkBytes(stream: InputStream): ByteArray {
        val limit = 6 * 1024 * 1024 + 1
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val read = stream.read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    fun toggle(): Boolean {
        val c = selected ?: return false
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val action = if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
        val actions = c.playbackState?.actions ?: 0
        if (actions and (action or PlaybackState.ACTION_PLAY_PAUSE) == 0L) return false
        return runCatching {
            if (playing) c.transportControls.pause() else c.transportControls.play()
            Log.i(TAG, "CONTROL ${if (playing) "pause" else "play"} player=${c.packageName}")
            true
        }.getOrDefault(false)
    }

    fun skip(next: Boolean): Boolean {
        val c = selected ?: return false
        val action = if (next) PlaybackState.ACTION_SKIP_TO_NEXT else PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if ((c.playbackState?.actions ?: 0) and action == 0L) return false
        return runCatching {
            if (next) c.transportControls.skipToNext() else c.transportControls.skipToPrevious()
            Log.i(TAG, "CONTROL ${if (next) "next" else "previous"} player=${c.packageName}")
            true
        }.getOrDefault(false)
    }

    fun seek(position: Long): Boolean {
        val item = mutable.value ?: return false
        val c = selected ?: return false
        if (!item.canSeek) return false
        return runCatching {
            val target = position.coerceIn(0, item.duration)
            // ColorOS can freeze a paused player's process and defer seekTo until
            // playback resumes. A targeted, idempotent PAUSE media key wakes it
            // for this user action without starting playback or changing volume.
            if (c.playbackState?.state == PlaybackState.STATE_PAUSED) {
                c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
                c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
            }
            c.transportControls.seekTo(target)
            Log.i(TAG, "CONTROL seek=$target player=${c.packageName}")
            true
        }.getOrDefault(false)
    }

    fun stop() {
        running = false
        runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
        controllers.values.forEach { (c, cb) -> c.unregisterCallback(cb) }
        controllers.clear(); positions.clear(); changesJob?.cancel(); artworkJob?.cancel()
        selected = null
    }

    companion object { const val TAG = "VenasMedia" }
}
