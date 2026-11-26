package com.venas.foldspectrum.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** Reference behavior: idle after 1 minute; alternate checkerboard phase every 5 minutes. */
internal fun Modifier.burnInProtection(idle: Boolean, lastTouch: Long): Modifier = composed {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(idle, lastTouch) {
        step = 0
        if (idle) while (true) {
            step = ((SystemClock.elapsedRealtime() - lastTouch - 60_000).coerceAtLeast(0) / 300_000).toInt()
            delay(1000)
        }
    }
    if (Build.VERSION.SDK_INT >= 33) this.pixelMask(idle, step % 2) else this
}

@RequiresApi(33)
private fun Modifier.pixelMask(idle: Boolean, phase: Int): Modifier = composed {
    val shader = remember {
        RuntimeShader("""
            uniform shader content;
            uniform float phase;
            half4 main(float2 p) {
                float check = mod(floor(p.x) + floor(p.y) + phase, 2.0);
                return check < 1.0 ? content.eval(p) : half4(0.0, 0.0, 0.0, 1.0);
            }
        """.trimIndent())
    }
    val effect = remember(shader) {
        RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
    this.graphicsLayer {
        renderEffect = if (idle) {
            shader.setFloatUniform("phase", phase.toFloat())
            effect
        } else null
    }
}
