package com.venas.foldspectrum.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.venas.foldspectrum.DeskState
import com.venas.foldspectrum.applyDisplayPreferences
import kotlin.math.roundToInt

data class SettingsActions(
    val flip: (Boolean) -> Unit,
    val nonFoldMode: (Boolean) -> Unit,
    val keepScreenOn: (Boolean) -> Unit,
    val followSystemBrightness: (Boolean) -> Unit,
    val brightness: (Float) -> Unit,
    val saveBrightness: () -> Unit,
    val burnInEnabled: (Boolean) -> Unit,
    val refreshMediaVolume: () -> Unit,
    val mediaVolume: (Int) -> Unit,
    val spectrumGainEnabled: (Boolean) -> Unit,
    val spectrumGainDb: (Float) -> Unit,
)

private val Divider = Color(0xFF30343B)

@Composable
internal fun ExpandableSettings(state: DeskState, actions: SettingsActions) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = !expanded }, shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Divider), colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (expanded) "收起设置" else "展开设置", fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.size(16.dp)) {
                val y1 = size.height * if (expanded) .65f else .35f
                val y2 = size.height - y1
                drawLine(Ink, Offset(size.width * .2f, y1), Offset(size.width * .5f, y2),
                    1.5.dp.toPx(), StrokeCap.Round)
                drawLine(Ink, Offset(size.width * .5f, y2), Offset(size.width * .8f, y1),
                    1.5.dp.toPx(), StrokeCap.Round)
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                SettingsControls(state, actions, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
internal fun SettingsDialog(state: DeskState, actions: SettingsActions, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val dialogWindow = (LocalView.current.parent as DialogWindowProvider).window
        // A Dialog has its own Window. Apply preferences here as well so the
        // brightness slider previews correctly while the panel has focus.
        SideEffect { applyDisplayPreferences(dialogWindow, state) }
        BoxWithConstraints(Modifier.fillMaxSize().deskRotation(state).safeDrawingPadding().padding(20.dp), contentAlignment = Alignment.Center) {
            Surface(color = Panel, shape = RoundedCornerShape(20.dp),
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(max = maxHeight)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("设置", color = Ink, fontSize = 20.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("完成", color = Ink) }
                    }
                    HorizontalDivider(color = Divider)
                    SettingsControls(state, actions, Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
                }
            }
        }
    }
}

@Composable
private fun SettingsControls(state: DeskState, actions: SettingsActions, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        SettingSwitch("旋转 180°", "反转横屏显示方向", state.flip, actions.flip)
        HorizontalDivider(color = Divider)
        SettingSwitch("非折叠模式", "横放显示可视化，竖起返回初始页", state.nonFoldMode, actions.nonFoldMode)
        HorizontalDivider(color = Divider)
        SettingSwitch("屏幕常亮", "可视化显示时保持亮屏", state.keepScreenOn, actions.keepScreenOn)
        HorizontalDivider(color = Divider)
        SettingSwitch("界面亮度", if (state.followSystemBrightness) "跟随系统" else "手动 · ${(state.brightness * 100).roundToInt()}%",
            !state.followSystemBrightness, { manual -> actions.followSystemBrightness(!manual) },
            if (state.followSystemBrightness) "跟随系统亮度" else "手动亮度")
        AnimatedVisibility(!state.followSystemBrightness, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            CompactSlider(value = state.brightness, onValueChange = actions.brightness,
                onValueChangeFinished = actions.saveBrightness, valueRange = .1f..1f,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }
        HorizontalDivider(color = Divider)
        SettingSwitch("频谱增益调节", "改变频谱波动幅度，不改变音频音量",
            state.spectrumGainEnabled, actions.spectrumGainEnabled)
        AnimatedVisibility(state.spectrumGainEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                val gain = state.spectrumGainDb.roundToInt()
                val gainLabel = when {
                    gain > 0 -> "+$gain dB"
                    else -> "$gain dB"
                }
                Text("频谱显示 · $gainLabel", color = Muted, fontSize = 12.sp)
                CompactSlider(value = state.spectrumGainDb, onValueChange = actions.spectrumGainDb,
                    valueRange = -30f..30f, modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "频谱显示增益，单位分贝"
                    })
            }
        }
        HorizontalDivider(color = Divider)
        val supportsMask = Build.VERSION.SDK_INT >= 33
        SettingSwitch("防烧屏模式",
            if (supportsMask) "闲置1分钟后启用棋盘遮罩，画面会变暗"
            else "棋盘遮罩需要 Android 13 或更高版本",
            state.burnInEnabled && supportsMask, actions.burnInEnabled, enabled = supportsMask)
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit,
                          stateLabel: String? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Ink, fontSize = 16.sp)
            Text(description, color = Muted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.semantics {
            contentDescription = title
            if (stateLabel != null) stateDescription = stateLabel
        })
    }
}

/** Shared geometry and accessibility for the two adjacent desk shortcuts. */
@Composable
internal fun DeskActionButton(label: String, interaction: MutableInteractionSource,
                              onClick: () -> Unit, icon: @Composable () -> Unit) {
    IconButton(onClick = onClick, interactionSource = interaction,
        modifier = Modifier.width(32.dp).height(48.dp).semantics { contentDescription = label },
        content = icon)
}

/** 16dp visible glyph; the containing button owns its hit target. */
@Composable
internal fun RotationGlyph() {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 1.6.dp.toPx()
        val w = size.width
        val h = size.height
        drawRoundRect(Ink, Offset(w * .32f, h * .2f),
            androidx.compose.ui.geometry.Size(w * .36f, h * .6f),
            androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(stroke))
        drawArc(Ink, 195f, 100f, false, Offset(w * .04f, h * .04f),
            androidx.compose.ui.geometry.Size(w * .92f, h * .92f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(Ink, Offset(w * .69f, h * .08f), Offset(w * .52f, h * .04f), stroke, StrokeCap.Round)
        drawLine(Ink, Offset(w * .69f, h * .08f), Offset(w * .63f, h * .24f), stroke, StrokeCap.Round)
        drawArc(Ink, 15f, 100f, false, Offset(w * .04f, h * .04f),
            androidx.compose.ui.geometry.Size(w * .92f, h * .92f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(Ink, Offset(w * .31f, h * .92f), Offset(w * .48f, h * .96f), stroke, StrokeCap.Round)
        drawLine(Ink, Offset(w * .31f, h * .92f), Offset(w * .37f, h * .76f), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun SettingsGlyph() {
    Canvas(Modifier.size(16.dp)) {
        for ((i, x) in listOf(.35f, .7f, .45f).withIndex()) {
            val y = size.height * (.22f + i * .28f)
            val radius = size.width * .09f
            val cx = size.width * x
            val stroke = 1.2.dp.toPx()
            drawLine(Ink, Offset(stroke / 2, y), Offset(cx - radius, y), stroke, StrokeCap.Round)
            drawLine(Ink, Offset(cx + radius, y), Offset(size.width - stroke / 2, y), stroke, StrokeCap.Round)
            drawCircle(Ink, radius, Offset(cx, y), style = Stroke(stroke))
        }
    }
}

@Composable
internal fun VolumeGlyph() {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val speaker = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * .08f, h * .35f)
            lineTo(w * .28f, h * .35f)
            lineTo(w * .52f, h * .13f)
            lineTo(w * .52f, h * .87f)
            lineTo(w * .28f, h * .65f)
            lineTo(w * .08f, h * .65f)
            close()
        }
        drawPath(speaker, Ink, style = Stroke(1.2.dp.toPx()))
        drawArc(Ink, -55f, 110f, false, Offset(w * .39f, h * .2f),
            androidx.compose.ui.geometry.Size(w * .5f, h * .6f),
            style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
    }
}
