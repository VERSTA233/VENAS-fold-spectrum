package com.venas.foldspectrum.ui

import android.app.Activity
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.doOnPreDraw
import com.venas.foldspectrum.R
import kotlin.math.roundToInt

/** The first visible logo already occupies its full-size, fixed launch area. */
internal class SplashLogo(activity: Activity) : AutoCloseable {
    private val screen = activity.splashScreen
    private val host = activity.findViewById<ViewGroup>(android.R.id.content)
    private var logoDrawable: AnimatedImageDrawable? =
        activity.getDrawable(R.drawable.splash_logo_animated) as AnimatedImageDrawable
    private val image = ImageView(activity).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setImageDrawable(logoDrawable)
    }
    private val overlay = FrameLayout(activity).apply {
        setBackgroundColor(Background.toArgb())
        isClickable = true
        contentDescription = "VENAS Fold Spectrum 正在启动"
    }
    private var closed = false
    private var firstDraw: OneShotPreDrawListener? = null
    private val callback = object : Animatable2.AnimationCallback() {
        override fun onAnimationEnd(drawable: Drawable?) {
            if (!closed) overlay.animate().alpha(0f).setDuration(100)
                .withEndAction { close() }.start()
        }
    }

    init {
        // A matching, empty system starting window avoids showing a smaller logo first.
        screen.setOnExitAnimationListener { it.remove() }
        val size = (390 * activity.resources.displayMetrics.density).roundToInt()
        overlay.addView(image, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        host.addView(overlay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        logoDrawable?.repeatCount = 0
        logoDrawable?.registerAnimationCallback(callback)
        firstDraw = overlay.doOnPreDraw {
            firstDraw = null
            if (!closed) logoDrawable?.start()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        firstDraw?.removeListener()
        firstDraw = null
        screen.clearOnExitAnimationListener()
        overlay.animate().cancel()
        logoDrawable?.unregisterAnimationCallback(callback)
        logoDrawable?.stop()
        logoDrawable = null
        image.setImageDrawable(null)
        host.removeView(overlay)
    }
}
