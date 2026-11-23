package com.venas.foldspectrum.ui

import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.venas.foldspectrum.DeskState
import com.venas.foldspectrum.core.*
import com.venas.foldspectrum.media.NowPlaying
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

internal val Background = Color(0xFF0E1012)
private val Surface = Color(0xFF191C20)
internal val Ink = Color(0xFFF3F4F5)
internal val Muted = Color(0xFF7E818A)
private val Grid = Color(0xFF26292E)
private val Red = Color(0xFFFF4D60)
private const val SpectrumFloorDb = -60f
private const val WaveformDisplayGain = 3f
private val VenasColors = darkColorScheme(primary = Ink, background = Background, surface = Surface, onSurface = Ink)

@Composable
fun VenasApp(state: DeskState, onAudioPermission: () -> Unit, onNotificationPermission: () -> Unit,
             settings: SettingsActions, onToggle: () -> Unit, onSkip: (Boolean) -> Unit,
             onSeek: (Long) -> Unit, onRetryCover: () -> Unit, onRetryAudio: () -> Unit) {
    MaterialTheme(colorScheme = VenasColors) {
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().deskRotation(state).background(Background)) {
            if (state.desk) DeskScreen(state, onToggle, onSkip, onSeek, settingsOpen) { settingsOpen = true }
            else Preparation(state, onAudioPermission, onNotificationPermission, settings, onRetryCover, onRetryAudio)
        }
        if (settingsOpen) SettingsDialog(state, settings) {
            settings.saveBrightness()
            settingsOpen = false
        }
    }
}

/** Transform the shared coordinate space so rendering and all child hit targets
 * turn together. Dialogs have a separate root and must apply this there too.
 * Preparation stays upright, so its direction switch only affects the desk.
 */
internal fun Modifier.deskRotation(state: DeskState): Modifier = graphicsLayer {
    rotationZ = if (state.desk && state.flip) 180f else 0f
}

@Composable
private fun Preparation(state: DeskState, audio: () -> Unit, notifications: () -> Unit,
                        settings: SettingsActions, retryCover: () -> Unit, retryAudio: () -> Unit) {
    val songFont = rememberSongTypeface()
    var debug by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("VENAS / FOLD SPECTRUM", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(30.dp))
        AlbumCover(state.media?.artwork, Modifier.size(176.dp))
        Spacer(Modifier.height(20.dp))
        SongText(state.media?.title ?: "未检测到正在播放的音频", songFont,
            color = Ink, fontSize = 23.sp, lineHeight = 29.sp, centered = true, maxLines = 2)
        state.media?.artist?.let {
            Spacer(Modifier.height(6.dp))
            SongText(it, songFont, color = Muted, fontSize = 16.sp, lineHeight = 21.sp,
                centered = true, maxLines = 2)
        }
        Spacer(Modifier.height(34.dp))
        Text(if (state.nonFoldMode) "请将手机横放\n以显示音频可视化" else "请半折手机\n以在外屏显示可视化", fontSize = 22.sp, lineHeight = 33.sp,
            color = Ink, textAlign = TextAlign.Center, fontWeight = FontWeight.Light)
        if (state.entering) Text("正在切换外屏…", color = Muted, modifier = Modifier.padding(top = 12.dp))
        if (state.nonFoldMode && !state.hasOrientationSensor) Text("无法检测手机方向", color = Red, modifier = Modifier.padding(top = 12.dp))
        else if (!state.nonFoldMode && !state.hasHinge) Text("未检测到转轴角度传感器，可开启非折叠模式", color = Muted, modifier = Modifier.padding(top = 12.dp))
        Column(Modifier.widthIn(max = 520.dp).padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.notificationGranted) PermissionCard("读取歌曲信息", "开启通知使用权，以读取封面、曲名、艺人和播放进度。", "开启通知使用权", notifications)
            if (!state.audioGranted) PermissionCard("读取实时音频", "系统会显示麦克风／录音授权。仅用于获取播放声音的波形和频谱，不录制麦克风。", "开启音频权限", audio)
            state.coverError?.let {
                PermissionCard("外屏切换未完成", it, "重试外屏切换", retryCover)
            }
            ExpandableSettings(state, settings)
            ProfileLinks()
            Text("调试信息", color = Muted, fontSize = 12.sp,
                modifier = Modifier.clickable { debug = !debug }.padding(vertical = 8.dp))
            if (debug) {
                val details = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n" +
                    "Hinge ${state.angle?.let { "%.1f°".format(it) } ?: "—"} · state ${FoldRules.coverState(Build.MANUFACTURER)}\n" +
                    "Phone ${state.phonePosture} · nonFold ${state.nonFoldMode}\n" +
                    "Player ${state.media?.player ?: "—"}\n" +
                    "Capture ${state.audio.capture} · frames ${state.audio.frames} · nonzero ${state.audio.nonzeroFrames}\n" +
                    "${state.displayDetail}\n${state.audio.error.orEmpty()}"
                Text(details, color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                TextButton(onClick = retryAudio) { Text("重试音频读取") }
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(18.dp)) {
        Text(title, color = Ink, fontWeight = FontWeight.Medium)
        Text(body, color = Muted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 7.dp))
        TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text(action, color = Ink) }
    }
}

@Composable
private fun DeskScreen(state: DeskState, onToggle: () -> Unit, onSkip: (Boolean) -> Unit, onSeek: (Long) -> Unit,
                       settingsOpen: Boolean, onOpenSettings: () -> Unit) {
    val songFont = rememberSongTypeface()
    val scope = rememberCoroutineScope()
    var lastTouch by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.burnInEnabled, settingsOpen) { lastTouch = SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var settingsUntil by remember { mutableLongStateOf(0L) }
    val settingsInteraction = remember { MutableInteractionSource() }
    val settingsPressed by settingsInteraction.collectIsPressedAsState()
    val settingsVisible = settingsUntil != 0L || settingsPressed
    val settingsAlpha = animateFloatAsState(if (settingsVisible) 1f else 0f,
        tween(durationMillis = 180), label = "settings shortcut fade")
    LaunchedEffect(settingsUntil) {
        val deadline = settingsUntil
        if (deadline != 0L) {
            delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            // An older timeout must not dismiss a shortcut renewed by another tap.
            if (settingsUntil == deadline) settingsUntil = 0L
        }
    }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(200) } }
    var progressRect by remember { mutableStateOf(Rect.Zero) }
    var coverRect by remember { mutableStateOf(Rect.Zero) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var seekProgress by remember { mutableStateOf(SeekProgress()) }
    val timeline = state.media?.seekTimeline()
    val displayedPosition = timeline?.let { seekProgress.position(it, now) } ?: 0L
    val newestTimeline by rememberUpdatedState(timeline)
    val newestDisplayedPosition by rememberUpdatedState(displayedPosition)
    val context = LocalContext.current
    val artwork = CoverArtwork("${state.media?.sessionKey}|${state.media?.trackKey}", state.media?.artwork)
    val albumMotion = remember { AlbumMotion(scope, artwork) }
    LaunchedEffect(artwork) { albumMotion.update(artwork) }
    var compactCover by remember { mutableStateOf(state.media?.playback == Playback.PAUSED) }
    LaunchedEffect(state.media?.playback) {
        when (state.media?.playback) {
            Playback.PAUSED -> compactCover = true
            Playback.PLAYING -> compactCover = false
            else -> Unit // A buffering update must not undo the paused size.
        }
    }
    val coverScale = animateFloatAsState(if (compactCover) .75f else 1f,
        spring(dampingRatio = 1f, stiffness = 220f, visibilityThreshold = .0005f), label = "playback cover size")
    val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val swipeThreshold = with(density) { 48.dp.toPx() }
    val newestState by rememberUpdatedState(state)
    val newestRect by rememberUpdatedState(progressRect)
    val newestCoverRect by rememberUpdatedState(coverRect)
    val toggle by rememberUpdatedState(onToggle)
    val skip by rememberUpdatedState(onSkip)
    val seek by rememberUpdatedState(onSeek)
    LaunchedEffect(timeline, now) {
        val result = seekProgress.reconcile(timeline, now)
        seekProgress = result.progress
        if (result.timedOut) Toast.makeText(context, "播放器未确认跳转，已恢复实际进度", Toast.LENGTH_SHORT).show()
    }
    val gestures = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            lastTouch = SystemClock.elapsedRealtime()
            // Child controls (notably the settings shortcut) own consumed presses.
            if (down.isConsumed) return@awaitEachGesture
            val item = newestState.media
            val inProgress = item != null && newestRect.contains(down.position)
            val coverInset = newestCoverRect.width * (1f - coverScale.value) / 2f
            val visibleCover = newestCoverRect.deflate(coverInset)
            val inCover = item != null && visibleCover.contains(down.position)
            // The down location owns the gesture, including drags leaving the cover.
            // Titles, spectrum, metrics and empty space are not playback controls.
            val downTimeline = newestTimeline
            val canDragProgress = inProgress && downTimeline?.canSeek == true && newestRect.width > 0
            if (canDragProgress) {
                // Pin exactly the rendered playhead, including any unconfirmed previous seek.
                seekProgress = seekProgress.begin(downTimeline!!, newestDisplayedPosition, down.position.x,
                    ProgressTrack.drawableWidth(newestRect.width))
            }
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var travel = Offset.Zero
            var moved = false
            var horizontal = false
            var canceled = false
            var finished = false
            var skipNext: Boolean? = null
            var releaseVelocity = 0f
            var turningPage = false
            try {
                while (!finished) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.changes.size != 1) { canceled = true; break }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) { canceled = true; break }
                    tracker.addPosition(change.uptimeMillis, change.position)
                    travel = change.position - down.position
                    if (!moved && travel.getDistance() > slop) {
                        moved = true
                        horizontal = abs(travel.x) > abs(travel.y)
                    }
                    if (moved && horizontal && (inProgress || inCover)) {
                        change.consume()
                        if (inProgress) {
                            val currentTimeline = newestTimeline
                            if (canDragProgress && currentTimeline != null) {
                                seekProgress = seekProgress.move(currentTimeline, change.position.x,
                                    ProgressTrack.drawableWidth(newestRect.width))
                            }
                        } else if (inCover) {
                            if (!turningPage) {
                                albumMotion.grab()
                                turningPage = true
                            }
                            albumMotion.move(travel.x, visibleCover.width)
                        }
                    }
                    if (!change.pressed) finished = true
                }
                if (!canceled && finished) {
                    if (!moved) {
                        if (inCover && visibleCover.contains(down.position + travel)) toggle()
                        else if (!inCover && !inProgress) settingsUntil = SystemClock.elapsedRealtime() + 3000
                    }
                    else if (horizontal && inProgress) {
                        newestTimeline?.let { current ->
                            seekProgress.dragTarget(current)?.let { target ->
                                seekProgress = seekProgress.request(current, target, SystemClock.elapsedRealtime())
                                seek(target)
                            }
                        }
                    } else if (horizontal && inCover) {
                        releaseVelocity = tracker.calculateVelocity().x
                        if (abs(travel.x) >= swipeThreshold || abs(releaseVelocity) > 1000f) {
                            skipNext = (if (abs(releaseVelocity) > 1000f) releaseVelocity else travel.x) < 0
                        }
                    }
                }
            } finally {
                seekProgress = seekProgress.cancelDrag()
                if (turningPage) {
                    albumMotion.release(skipNext, releaseVelocity, visibleCover.width)
                    skipNext?.let { skip(it) }
                }
            }
        }
    }
    val idle = !settingsOpen && now - lastTouch > 60_000
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { rootCoordinates = it }.then(gestures)
        .semantics { customActions = listOf(CustomAccessibilityAction("打开设置") { onOpenSettings(); true }) }) {
        val layout = deskLayout(maxWidth.value, maxHeight.value)
        val u = layout.unit
        val coverSize = layout.coverDp.dp
        // One shared gap: screen -> album, album -> instruments, instruments -> screen.
        val horizontalGap = layout.horizontalGapDp.dp
        Box(Modifier.fillMaxSize().burnInProtection(idle && state.burnInEnabled, lastTouch).padding(horizontal = horizontalGap, vertical = (24 * u).dp)) {
            if (state.media == null) ClockFace()
            else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val media = state.media
                Box(Modifier.width(coverSize).fillMaxHeight()) {
                    // Fixed top anchor; an extra title line only moves the artist.
                    // Allow that second line to use the small lower screen inset.
                    Column(Modifier.width(coverSize).wrapContentHeight(unbounded = true, align = Alignment.Top)
                        .padding(top = (9 * u).dp)) {
                        Box(Modifier.size(coverSize).onGloballyPositioned { child ->
                            rootCoordinates?.takeIf { it.isAttached && child.isAttached }?.let { root ->
                                coverRect = root.localBoundingBoxOf(child, clipBounds = false)
                            }
                        }.semantics {
                            onClick(label = "播放或暂停") { onToggle(); true }
                            customActions = listOf(
                                CustomAccessibilityAction("下一首") {
                                    albumMotion.grab(); albumMotion.release(true, 0f, coverRect.width); onSkip(true); true
                                },
                                CustomAccessibilityAction("上一首") {
                                    albumMotion.grab(); albumMotion.release(false, 0f, coverRect.width); onSkip(false); true
                                },
                            )
                        }) {
                            TurningAlbum(albumMotion, { coverScale.value }, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height((16 * u).dp))
                        Column(Modifier.padding(start = (8 * u).dp)) {
                            SongText(media.title, songFont, color = Ink, fontSize = (21 * u).sp,
                                lineHeight = (26 * u).sp, maxLines = 2)
                            Spacer(Modifier.height((5 * u).dp))
                            SongText(media.artist, songFont, color = Color(0xFFB9BBC0), fontSize = (15 * u).sp,
                                lineHeight = (20 * u).sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.width(horizontalGap))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().height((28 * u).dp), verticalAlignment = Alignment.Top) {
                        Text("SPECTRUM", color = Ink, fontSize = (12 * u).sp, letterSpacing = (1.2f * u).sp,
                            fontFamily = InstrumentFont, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.graphicsLayer { alpha = 1f - settingsAlpha.value }) {
                            StatusLabel(state.status, u)
                        }
                    }
                    val drawing = smoothAudio(state)
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        SpectrumChart(drawing.bands, drawing.layout, u, Modifier.fillMaxSize())
                        if (state.status == DisplayStatus.UNSUPPORTED) Text("当前音频源不支持实时可视化",
                            color = Muted, fontSize = (13 * u).sp, textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).background(Background).padding(12.dp))
                    }
                    HorizontalDivider(color = Grid, modifier = Modifier.padding(top = (10 * u).dp))
                    SongProgress(media, displayedPosition, u,
                        Modifier.fillMaxWidth().height((76 * u).dp).onGloballyPositioned { child ->
                            rootCoordinates?.takeIf { it.isAttached && child.isAttached }?.let { root ->
                                progressRect = root.localBoundingBoxOf(child, clipBounds = false)
                            }
                        }) { target ->
                            newestTimeline?.takeIf { it.canSeek }?.let { current ->
                                seekProgress = seekProgress.request(current, target, SystemClock.elapsedRealtime())
                                seek(target)
                            }
                        }
                    HorizontalDivider(color = Grid)
                    Metrics(state, drawing.wave, layout, Modifier.fillMaxWidth().height((70 * u).dp))
                }
            }
        }
        if (settingsVisible || settingsAlpha.value > 0f) {
            IconButton(onClick = { settingsUntil = 0L; onOpenSettings() }, interactionSource = settingsInteraction,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = horizontalGap)
                    .offset(x = 16.dp, y = (38 * u).dp - 24.dp).size(48.dp)
                    .graphicsLayer { alpha = settingsAlpha.value }
                    .semantics { contentDescription = "打开设置" }) {
                SettingsGlyph()
            }
        }
    }
}

@Composable
private fun ClockFace() {
    var time by remember { mutableStateOf(LocalTime.now()) }
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    LaunchedEffect(Unit) { while (true) { time = LocalTime.now(); delay(1000) } }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val clockStyle = SongTextStyle.copy(fontFamily = InstrumentFont, fontWeight = FontWeight.Normal,
        fontSize = 144.sp, lineHeight = 160.sp, letterSpacing = 0.sp, textAlign = TextAlign.Center)
    val hint = "播放音乐可显示音频可视化"
    val hintStyle = SongTextStyle.copy(fontSize = 14.sp, lineHeight = 20.sp,
        letterSpacing = 1.sp, textAlign = TextAlign.Center)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hintSize = measurer.measure(hint, hintStyle,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = with(density) { maxWidth.roundToPx() })).size
        val reference = measurer.measure("88:88", clockStyle, softWrap = false, maxLines = 1).size
        val gapPx = with(density) { 14.dp.toPx() }
        // Fit actual font metrics to both axes. Reserve room below the centered
        // clock for the hint, rather than letting the hint push the clock upward.
        val fit = minOf(1f, with(density) { maxWidth.toPx() } / reference.width,
            (with(density) { maxHeight.toPx() } - 2 * (gapPx + hintSize.height)) / reference.height)
            .coerceAtLeast(0f)
        val fittedStyle = clockStyle.copy(fontSize = (144 * fit).sp, lineHeight = (160 * fit).sp)
        val clockHeight = measurer.measure("88:88", fittedStyle, softWrap = false, maxLines = 1).size.height
        Text(time.format(formatter), color = Ink, style = fittedStyle, maxLines = 1, softWrap = false,
            modifier = Modifier.align(Alignment.Center))
        Text(hint, color = Muted, style = hintStyle,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center).offset(y = with(density) {
                (clockHeight / 2f + gapPx + hintSize.height / 2f).toDp()
            }))
    }
}

@Composable
private fun StatusLabel(status: DisplayStatus, u: Float) {
    val color = when (status) {
        DisplayStatus.PLAYING -> Ink
        DisplayStatus.UNSUPPORTED -> Red
        else -> Muted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((6 * u).dp)) {
        Box(Modifier.size((4 * u).dp).background(if (status == DisplayStatus.PLAYING) Red else color, RoundedCornerShape(50)))
        Text(status.name, color = color, fontSize = (10 * u).sp, letterSpacing = (.4f * u).sp,
            fontFamily = InstrumentFont, fontWeight = FontWeight.Medium)
    }
}

private data class DrawingFrame(val bands: FloatArray, val wave: FloatArray, val layout: List<SpectrumBand> = emptyList())

@Composable
private fun smoothAudio(state: DeskState): DrawingFrame {
    val latest by rememberUpdatedState(state)
    var rendered by remember { mutableStateOf(DrawingFrame(FloatArray(0), FloatArray(96))) }
    LaunchedEffect(Unit) {
        var previous = 0L
        while (true) withFrameNanos { frame ->
            val dt = if (previous == 0L) .016f else ((frame - previous) / 1e9f).coerceAtMost(.1f)
            previous = frame
            val active = latest.status == DisplayStatus.PLAYING
            val targetBands = if (active) latest.audio.bands else null
            val targetWave = if (active) latest.audio.wave else null
            // Retain the previous geometry while paused so the bars can decay.
            val layout = latest.audio.bandLayout.ifEmpty { rendered.layout }
            val sameLayout = layout == rendered.layout
            val nextBands = FloatArray(layout.size) { index ->
                val target = (targetBands?.getOrNull(index) ?: SpectrumFloorDb).coerceAtLeast(SpectrumFloorDb)
                val old = if (sameLayout) rendered.bands[index] else SpectrumFloorDb
                val time = if (target > old) .10f else .20f
                val next = old + (target - old) * (1f - exp(-dt / time))
                if (!active && abs(next - target) < .05f) target else next
            }
            val nextWave = FloatArray(96) { index ->
                val target = ((targetWave?.getOrNull(index) ?: 0f) * WaveformDisplayGain).coerceIn(-1f, 1f)
                val time = if (active) .035f else .09f
                val next = rendered.wave[index] + (target - rendered.wave[index]) * (1f - exp(-dt / time))
                if (!active && abs(next - target) < .001f) target else next
            }
            if (!sameLayout || !nextBands.contentEquals(rendered.bands) || !nextWave.contentEquals(rendered.wave)) {
                rendered = DrawingFrame(nextBands, nextWave, layout)
            }
        }
    }
    return rendered
}

@Composable
private fun SpectrumChart(bands: FloatArray, layout: List<SpectrumBand>, u: Float, modifier: Modifier) {
    val defaultLayout = remember { SpectrumMath.bandLayout(1024, 48000) }
    val axisLayout = layout.ifEmpty { defaultLayout }
    val textSize = with(LocalDensity.current) { (9 * u).sp.toPx() }
    val instrumentTypeface = rememberInstrumentTypeface()
    val paint = remember(instrumentTypeface) { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = instrumentTypeface } }
    Canvas(modifier.semantics { contentDescription = "实时频谱，相对幅值刻度" }) {
        paint.textSize = textSize; paint.color = android.graphics.Color.rgb(126, 129, 138)
        val left = textSize * 3.5f
        val top = textSize * 2.3f
        val right = size.width - textSize * .8f
        val bottom = size.height - textSize * .5f
        val width = right - left
        val height = (bottom - top).coerceAtLeast(1f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 6f))
        for (j in 0..4) {
            val y = top + height * j / 4f
            drawLine(Grid, Offset(left, y), Offset(right, y), 1f, pathEffect = dash)
            val label = if (j == 0) "0" else "−${j * 15}"
            drawIntoCanvas { it.nativeCanvas.drawText(label, left - textSize * .8f - paint.measureText(label), y + textSize * .35f, paint) }
        }
        listOf(20 to "20", 200 to "200", 500 to "500", 1000 to "1k", 2000 to "2k", 4000 to "4k", 8000 to "8k", 16000 to "16k").forEach { (hz, label) ->
            val fraction = SpectrumMath.frequencyPosition(hz.toDouble(), axisLayout) ?: return@forEach
            val x = left + fraction * width
            drawLine(Grid, Offset(x, top), Offset(x, bottom), 1f, pathEffect = dash)
            drawIntoCanvas { it.nativeCanvas.drawText(label, (x - paint.measureText(label) / 2).coerceIn(0f, right - paint.measureText(label)), textSize, paint) }
        }
        val step = width / bands.size.coerceAtLeast(1)
        bands.forEachIndexed { index, db ->
            val h = ((db - SpectrumFloorDb) / -SpectrumFloorDb).coerceIn(0f, 1f) * height
            if (h > .3f) drawRect(Color(0xFFB6B8BE), Offset(left + index * step + step * .18f, bottom - h), Size(step * .62f, h))
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
    else "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun SongProgress(media: NowPlaying, position: Long, u: Float, modifier: Modifier, onSeek: (Long) -> Unit) {
    if (media.duration <= 0) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("无可用进度信息", color = Muted, fontSize = (12 * u).sp) }
        return
    }
    val fraction = (position.toDouble() / media.duration).toFloat().coerceIn(0f, 1f)
    val textSize = with(LocalDensity.current) { (10 * u).sp.toPx() }
    val instrumentTypeface = rememberInstrumentTypeface()
    val paint = remember(instrumentTypeface) { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = instrumentTypeface } }
    Canvas(modifier.semantics {
        contentDescription = "播放进度 ${formatTime(position)}，共 ${formatTime(media.duration)}"
        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        if (media.canSeek) setProgress { onSeek((it.coerceIn(0f, 1f) * media.duration).toLong()); true }
    }) {
        val inset = ProgressTrack.INSET_PX
        val width = ProgressTrack.drawableWidth(size.width)
        val x = inset + fraction * width
        val tickHeight = textSize
        val playheadHalfHeight = tickHeight * 1.45f
        val labelHeight = textSize * 1.7f
        val labelGap = textSize * .2f
        // Center the complete badge + playhead group, leaving equal outer margins.
        val groupHeight = labelHeight + labelGap + playheadHalfHeight * 2
        val labelTop = (size.height - groupHeight) / 2f
        val centerY = labelTop + labelHeight + labelGap + playheadHalfHeight
        val labelCenterY = labelTop + labelHeight / 2
        paint.textSize = textSize; paint.color = android.graphics.Color.rgb(126, 129, 138)
        // All three times share a row. The badge is drawn last and covers edge times.
        val edgeBaseline = labelCenterY - (paint.ascent() + paint.descent()) / 2
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText("0:00", inset, edgeBaseline, paint)
            val duration = formatTime(media.duration)
            canvas.nativeCanvas.drawText(duration, size.width - paint.measureText(duration) - inset, edgeBaseline, paint)
        }
        val ticks = 81
        for (i in 0 until ticks) {
            val tx = inset + width * i / (ticks - 1)
            val h = if (i % 5 == 0) tickHeight else tickHeight * .5f
            drawLine(if (tx <= x) Color(0xFFB0B3BA) else Color(0xFF393D44), Offset(tx, centerY - h), Offset(tx, centerY + h), 1.5f)
        }
        drawLine(Red, Offset(x, centerY - playheadHalfHeight), Offset(x, centerY + playheadHalfHeight), 3f)
        val label = formatTime(position)
        paint.textSize = textSize * 1.2f
        val labelWidth = paint.measureText(label) + textSize * 1.3f
        val labelLeft = (x - labelWidth / 2).coerceIn(0f, (size.width - labelWidth).coerceAtLeast(0f))
        drawRect(Ink, Offset(labelLeft, labelTop), Size(labelWidth, labelHeight))
        drawRect(Color.Black, Offset(labelLeft, labelTop), Size(labelWidth, labelHeight), style = Stroke(1.5f))
        paint.color = android.graphics.Color.BLACK
        drawIntoCanvas { it.nativeCanvas.drawText(label, labelLeft + (labelWidth - paint.measureText(label)) / 2,
            labelTop + labelHeight / 2 - (paint.ascent() + paint.descent()) / 2, paint) }
    }
}

@Composable
private fun Metrics(state: DeskState, wave: FloatArray, layout: DeskLayout, modifier: Modifier) {
    val u = layout.unit
    val inset = layout.metricInsetDp.dp
    val active = state.status == DisplayStatus.PLAYING
    val level = if (!active) "—" else state.audio.levelDb?.let { if (it <= -96f) "−∞" else String.format(Locale.US, "%.1f", it).replace('-', '−') } ?: "—"
    val dynamic = if (active) state.audio.dynamicDb?.let { String.format(Locale.US, "%.1f", it) } ?: "—" else "—"
    Row(modifier.padding(top = (10 * u).dp, bottom = (9 * u).dp)) {
        Column(Modifier.weight(layout.waveformWeight).fillMaxHeight().padding(end = inset)) {
            MetricLabel("WAVEFORM", u)
            Spacer(Modifier.height((4 * u).dp))
            Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "实时声音波形" }) {
                val pad = 1.dp.toPx()
                val count = wave.size / 2
                val step = (size.width - 2 * pad) / (count - 1).coerceAtLeast(1)
                for (i in 0 until count) {
                    val low = wave[2 * i]; val high = wave[2 * i + 1]
                    if (high - low > .005f) drawLine(Ink.copy(alpha = .8f),
                        Offset(pad + i * step, center.y - high * size.height * .42f),
                        Offset(pad + i * step, center.y - low * size.height * .42f),
                        min(1.2.dp.toPx(), step * .3f), StrokeCap.Round)
                }
            }
        }
        VerticalDivider(color = Grid, thickness = .5.dp)
        Metric("LEVEL", level, "dBFS", layout, Modifier.weight(1f).padding(horizontal = inset))
        VerticalDivider(color = Grid, thickness = .5.dp)
        Metric("DYNAMIC", dynamic, "dB", layout, Modifier.weight(1f).padding(start = inset))
    }
}

@Composable
private fun MetricLabel(label: String, u: Float) {
    Text(label, color = Muted, fontSize = (9 * u).sp, lineHeight = (12 * u).sp,
        fontFamily = InstrumentFont, fontWeight = FontWeight.Medium, maxLines = 1)
}

@Composable
private fun Metric(label: String, value: String, unit: String, layout: DeskLayout, modifier: Modifier) {
    val u = layout.unit
    Column(modifier.fillMaxHeight()) {
        MetricLabel(label, u)
        Spacer(Modifier.height((4 * u).dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
            Row(horizontalArrangement = Arrangement.spacedBy((3 * u).dp)) {
                Text(value, color = Ink, fontSize = layout.metricValueSp.sp, lineHeight = (24 * u).sp, fontFamily = InstrumentFont,
                    letterSpacing = if (layout.isWideFold) 0.sp else TextUnit.Unspecified,
                    fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.alignByBaseline())
                Text(unit, color = Muted, fontSize = layout.metricUnitSp.sp, lineHeight = (11 * u).sp, fontFamily = InstrumentFont,
                    letterSpacing = if (layout.isWideFold) 0.sp else TextUnit.Unspecified,
                    fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.alignByBaseline())
            }
        }
    }
}
