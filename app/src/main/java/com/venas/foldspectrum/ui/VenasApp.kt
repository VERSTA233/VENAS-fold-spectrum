package com.venas.foldspectrum.ui

import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.venas.foldspectrum.DeskState
import com.venas.foldspectrum.core.*
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun VenasApp(state: DeskState, onAudioPermission: () -> Unit, onNotificationPermission: () -> Unit,
             settings: SettingsActions, onToggle: () -> Unit, onSkip: (Boolean) -> Unit,
             onSeek: (Long) -> Unit, onRetryCover: () -> Unit, onRetryAudio: () -> Unit,
             onRotate: () -> Unit) {
    MaterialTheme(colorScheme = VenasColors) {
        var settingsOpen by rememberSaveable { mutableStateOf(false) }
        val context = LocalContext.current
        val preferences = remember(context) { context.getSharedPreferences("venas", android.content.Context.MODE_PRIVATE) }
        var tutorialCompleted by remember { mutableStateOf(preferences.getBoolean("visualizationTutorialCompleted", false)) }
        // The clock and preparation page must never consume the first visualization guide.
        val tutorialVisible = state.desk && state.media != null && !settingsOpen && !tutorialCompleted
        Box(Modifier.fillMaxSize().deskRotation(state).background(Background)) {
            if (state.desk) DeskScreen(state, onToggle, onSkip, onSeek, settingsOpen, settings,
                tutorialVisible, onFinishTutorial = {
                    preferences.edit().putBoolean("visualizationTutorialCompleted", true).apply()
                    tutorialCompleted = true
                }, onOpenSettings = { settingsOpen = true })
            else {
                Preparation(state, onAudioPermission, onNotificationPermission, settings, onRetryCover, onRetryAudio)
                IconButton(onClick = onRotate, enabled = !state.entering,
                    modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)
                        .semantics { contentDescription = if (state.manualLandscape) "恢复初始方向" else "横屏显示" }) {
                    RotationGlyph()
                }
            }
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
        Text("VENAS / FOLD SPECTRUM", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp,
            modifier = Modifier.padding(horizontal = 36.dp))
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
            if (!state.notificationGranted) PermissionCard("读取歌曲信息", "用于读取封面、曲名、艺人和播放进度，不会发送通知。", "开启通知权限", notifications)
            if (!state.audioGranted) PermissionCard("读取实时音频", "系统会显示麦克风／录音授权。仅用于获取播放音频的频谱和波形，不使用麦克风。", "开启音频权限", audio)
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(18.dp)) {
        Text(title, color = Ink, fontWeight = FontWeight.Medium)
        Text(body, color = Muted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 7.dp))
        TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text(action, color = Ink) }
    }
}

@Composable
private fun DeskScreen(state: DeskState, onToggle: () -> Unit, onSkip: (Boolean) -> Unit, onSeek: (Long) -> Unit,
                       settingsOpen: Boolean, settings: SettingsActions, tutorialVisible: Boolean,
                       onFinishTutorial: () -> Unit, onOpenSettings: () -> Unit) {
    val songFont = rememberSongTypeface()
    val scope = rememberCoroutineScope()
    var lastTouch by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.burnInEnabled, settingsOpen, tutorialVisible) { lastTouch = SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var settingsUntil by remember { mutableLongStateOf(0L) }
    var volumeOpen by remember { mutableStateOf(false) }
    LaunchedEffect(tutorialVisible) {
        if (tutorialVisible) { settingsUntil = 0L; volumeOpen = false }
    }
    val settingsInteraction = remember { MutableInteractionSource() }
    val volumeInteraction = remember { MutableInteractionSource() }
    val sliderInteraction = remember { MutableInteractionSource() }
    val settingsPressed by settingsInteraction.collectIsPressedAsState()
    val volumePressed by volumeInteraction.collectIsPressedAsState()
    val sliderPressed by sliderInteraction.collectIsPressedAsState()
    val sliderDragged by sliderInteraction.collectIsDraggedAsState()
    val interacting = settingsPressed || volumePressed || sliderPressed || sliderDragged
    fun renewShortcuts() { settingsUntil = SystemClock.elapsedRealtime() + 3000 }
    val settingsVisible = settingsUntil != 0L || interacting
    val settingsAlpha = animateFloatAsState(if (settingsVisible) 1f else 0f,
        tween(durationMillis = 180), label = "desk shortcuts fade")
    LaunchedEffect(interacting) {
        if (interacting || settingsUntil != 0L) renewShortcuts()
    }
    LaunchedEffect(settingsUntil, interacting) {
        if (!interacting) {
            val deadline = settingsUntil
            if (deadline != 0L) {
                delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                if (settingsUntil == deadline) settingsUntil = 0L
            }
        }
    }
    LaunchedEffect(settingsVisible, settingsAlpha.value == 0f) {
        if (!settingsVisible && settingsAlpha.value == 0f) volumeOpen = false
    }
    LaunchedEffect(volumeOpen, settings) {
        if (volumeOpen) while (true) { settings.refreshMediaVolume(); delay(500) }
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
    val newestTutorialVisible by rememberUpdatedState(tutorialVisible)
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
            if (newestTutorialVisible) return@awaitEachGesture
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
                        else if (!inCover && !inProgress) {
                            volumeOpen = false
                            settingsUntil = SystemClock.elapsedRealtime() + 3000
                        }
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
    val idle = !settingsOpen && !tutorialVisible && now - lastTouch > 60_000
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { rootCoordinates = it }.then(gestures)
        .semantics { customActions = if (tutorialVisible) emptyList() else listOf(CustomAccessibilityAction("打开设置") { onOpenSettings(); true }) }) {
        val layout = deskLayout(maxWidth.value, maxHeight.value)
        val u = layout.unit
        val coverSize = layout.coverDp.dp
        // One shared gap: screen -> album, album -> instruments, instruments -> screen.
        val horizontalGap = layout.horizontalGapDp.dp
        Box(Modifier.fillMaxSize()
            .then(if (tutorialVisible) Modifier.clearAndSetSemantics {} else Modifier)
            .burnInProtection(idle && state.burnInEnabled, lastTouch).padding(horizontal = horizontalGap, vertical = (24 * u).dp)) {
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
        if (!tutorialVisible && (settingsVisible || settingsAlpha.value > 0f)) {
            val adjustable = !state.volumeFixed && state.maxMediaVolume > state.minMediaVolume
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                // Right anchor keeps both icons stationary as the slider grows to the left.
                Row(Modifier.align(Alignment.TopEnd).padding(end = horizontalGap)
                    .offset(x = 8.dp, y = (38 * u).dp - 24.dp)
                    .graphicsLayer { alpha = settingsAlpha.value },
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (volumeOpen) Panel else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            renewShortcuts()
                        }, verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(volumeOpen,
                            enter = expandHorizontally(tween(180), expandFrom = Alignment.End) + fadeIn(tween(180)),
                            exit = shrinkHorizontally(tween(180), shrinkTowards = Alignment.End) + fadeOut(tween(180))) {
                            CompactSlider(value = state.mediaVolume.toFloat(), enabled = adjustable,
                                onValueChange = { settings.mediaVolume(it.roundToInt()); renewShortcuts() },
                                onValueChangeFinished = { renewShortcuts() },
                                valueRange = state.minMediaVolume.toFloat()..maxOf(state.maxMediaVolume, state.minMediaVolume + 1).toFloat(),
                                interactionSource = sliderInteraction,
                                modifier = Modifier.width(124.dp).padding(start = 12.dp, end = 4.dp).semantics {
                                    contentDescription = if (adjustable) "系统媒体音量" else "当前输出不支持调整音量"
                                })
                        }
                        DeskActionButton("调整音量", volumeInteraction, onClick = {
                            settings.refreshMediaVolume()
                            volumeOpen = !volumeOpen
                            renewShortcuts()
                        }) {
                            VolumeGlyph()
                        }
                    }
                    DeskActionButton("打开设置", settingsInteraction,
                        onClick = { volumeOpen = false; settingsUntil = 0L; onOpenSettings() }) {
                        SettingsGlyph()
                    }
                }
            }
        }
        if (tutorialVisible && coverRect.width > 0 && progressRect.width > 0) {
            VisualizationTutorial(coverRect, progressRect, layout, onFinishTutorial)
        }
    }
}
