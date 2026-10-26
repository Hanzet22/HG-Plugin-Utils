package com.hypergaruda.hgpg

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.aliucord.api.SettingsAPI
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.sin

/**
 * A transparent native Canvas layer drawn above Aliucord's content.
 *
 * Unlike a root background Drawable, this remains visible when Discord places
 * opaque child backgrounds over its content root. The view is deliberately
 * non-interactive and always returns false for touch dispatch so underlying
 * Discord controls keep receiving taps and gestures.
 */
internal object HGPGGlobalMotionOverlay {
    private val overlays = WeakHashMap<ViewGroup, HGPGMotionOverlayView>()
    @Volatile private var currentTheme = HGPGThemes.get("garuda")

    fun start(settings: SettingsAPI) {
        currentTheme = HGPGThemes.get(readSetting(settings.getString("theme_profile", "garuda")))
    }

    fun setTheme(theme: HGPGTheme) {
        currentTheme = theme
        synchronized(overlays) {
            overlays.values.toList().forEach { it.setTheme(theme) }
        }
    }

    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        content.post {
            if (!activity.isFinishing && !activity.isDestroyed) attachTo(content)
        }
    }

    fun detach(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        synchronized(overlays) {
            overlays.remove(content)?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
            }
        }
    }

    fun stop() {
        synchronized(overlays) {
            overlays.values.toList().forEach { view ->
                (view.parent as? ViewGroup)?.removeView(view)
            }
            overlays.clear()
        }
    }

    private fun attachTo(root: ViewGroup) {
        synchronized(overlays) {
            val old = overlays[root]
            if (old != null && old.parent === root) {
                old.setTheme(currentTheme)
                old.bringToFront()
                old.invalidate()
                return
            }
            if (old?.parent is ViewGroup) (old.parent as ViewGroup).removeView(old)
            val overlay = HGPGMotionOverlayView(root.context, currentTheme).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isFocusable = false
                isClickable = false
                contentDescription = null
                elevation = 1000f * resources.displayMetrics.density
            }
            overlays[root] = overlay
            root.addView(
                overlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            overlay.bringToFront()
        }
    }

    private fun readSetting(value: Any?): String {
        if (value == null) return "garuda"
        return try {
            val type = CharSequence::class.java
            val length = type.getMethod("length").invoke(value) as Int
            val charAt = type.getMethod("charAt", Integer.TYPE)
            val out = StringBuilder(length.coerceAtLeast(0))
            var i = 0
            while (i < length) {
                out.append(charAt.invoke(value, i) as Char)
                i++
            }
            out.toString()
        } catch (_: Throwable) {
            try {
                Any::class.java.getMethod("toString").invoke(value)?.toString() ?: "garuda"
            } catch (_: Throwable) {
                "garuda"
            }
        }
    }
}

/** Lightweight, theme-specific ambient effects; no opaque background is drawn. */
private class HGPGMotionOverlayView(
    context: Context,
    theme: HGPGTheme
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    @Volatile private var currentTheme = theme
    private val tick = object : Runnable {
        override fun run() {
            if (isAttachedToWindow && visibility == VISIBLE && windowVisibility == VISIBLE) {
                invalidate()
                postDelayed(this, FRAME_MS)
            }
        }
    }

    init {
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setTheme(theme: HGPGTheme) {
        currentTheme = theme
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(tick)
        postDelayed(tick, FRAME_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 1f || h <= 1f) return

        val theme = currentTheme
        val phase = (SystemClock.uptimeMillis() % LOOP_MS).toFloat() / LOOP_MS.toFloat()
        when (theme.motion) {
            HGPGMotion.DEEP_SEA -> drawDeepSea(canvas, w, h, phase, theme)
            HGPGMotion.GARUDA -> drawGaruda(canvas, w, h, phase, theme)
            HGPGMotion.LED_ORANGE -> drawLedOrange(canvas, w, h, phase, theme)
            HGPGMotion.GT_SPORT -> drawGtSport(canvas, w, h, phase, theme)
            HGPGMotion.GT_OLD -> drawGtOld(canvas, w, h, phase, theme)
            HGPGMotion.CRT_PS2 -> drawCrt(canvas, w, h, phase, theme)
        }
    }

    private fun drawDeepSea(canvas: Canvas, w: Float, h: Float, phase: Float, theme: HGPGTheme) {
        for (layer in 0..3) {
            val base = h * (0.30f + layer * 0.16f)
            val path = Path()
            for (step in 0..48) {
                val x = w * step / 48f
                val y = base + sin(step / 48.0 * PI * 2.2 + phase * PI * 2.0 + layer * 0.9).toFloat() * h * 0.018f
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            stroke(theme.accent, 40 + layer * 6, 1.2f + layer * 0.35f)
            canvas.drawPath(path, paint)
        }
        for (i in 0..11) {
            val x = ((i * 0.137f + phase * 0.17f) % 1f) * w
            val y = h - ((i * 0.113f + phase * 0.48f) % 1f) * h
            stroke(theme.accent, 54 + (i % 3) * 8, 1.2f)
            canvas.drawCircle(x, y, (3f + (i % 3) * 1.5f) * density, paint)
        }
    }

    private fun drawGaruda(canvas: Canvas, w: Float, h: Float, phase: Float, theme: HGPGTheme) {
        for (i in 0..7) {
            val x = ((i * 0.19f + phase * 0.38f) % 1.35f) * w
            stroke(theme.accent, 27 + (i % 3) * 7, 1.8f + (i % 2) * 0.5f)
            canvas.drawLine(x, h, x + h * 0.30f, 0f, paint)
        }
        val cx = w * 0.82f
        val cy = h * 0.22f
        for (i in 0..4) {
            val radius = (0.025f + ((phase + i * 0.19f) % 1f) * 0.18f) * minOf(w, h)
            stroke(0xFFFFC857.toInt(), 48 - i * 6, 1.5f)
            canvas.drawCircle(cx, cy, radius, paint)
        }
        for (i in 0..4) {
            val y = h * (0.15f + i * 0.075f)
            stroke(theme.secondary, 25, 1.1f)
            canvas.drawLine(w * 0.45f, y, w, y - h * 0.025f, paint)
        }
    }

    private fun drawLedOrange(canvas: Canvas, w: Float, h: Float, phase: Float, theme: HGPGTheme) {
        val cx = w * (0.18f + 0.64f * (0.5f + 0.5f * sin(phase * PI * 2.0).toFloat()))
        val cy = h * (0.35f + 0.12f * sin(phase * PI * 4.0).toFloat())
        paint.shader = RadialGradient(
            cx, cy, maxOf(w, h) * 0.65f,
            intArrayOf(withAlpha(theme.accent, 45), withAlpha(theme.secondary, 23), 0x00000000),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        for (i in 0..7) {
            val y = ((i * 0.16f + phase * 0.55f) % 1f) * h
            stroke(if (i % 2 == 0) theme.accent else theme.secondary, 37 + (i % 3) * 6, 1.4f)
            canvas.drawLine(0f, y, w, y - h * 0.018f, paint)
        }
    }

    private fun drawGtSport(canvas: Canvas, w: Float, h: Float, phase: Float, theme: HGPGTheme) {
        for (i in 0..21) {
            val travel = (i / 22f + phase * 0.88f) % 1.35f
            val x = w * (1.1f - travel)
            val y = h * (0.08f + (i % 9) * 0.105f)
            val length = (24f + (i % 4) * 17f) * density
            stroke(if (i % 4 == 0) theme.secondary else theme.accent, 44 + (i % 4) * 5, if (i % 4 == 0) 2.1f else 1.3f)
            canvas.drawLine(x, y, x + length, y - length * 0.13f, paint)
        }
        val x = ((phase * 1.7f) % 1f) * w
        stroke(theme.accent, 23, 1f)
        canvas.drawRect(RectF(x - 10f * density, 0f, x + 10f * density, h), paint)
    }

    private fun drawGtOld(canvas: Canvas, w: Float, h: Float, phase: Float, theme: HGPGTheme) {
        val center = w * 0.5f
        val horizon = h * 0.55f
        for (i in 0..12) {
            val x = w * i / 12f
            stroke(theme.accent, 31, 1f)
            canvas.drawLine(center + (x - center) * 0.08f, horizon, center + (x - center) * 1.22f, h, paint)
        }
        for (i in 0..8) {
            val ratio = i / 8f
            val y = horizon + (h - horizon) * ratio * ratio
            stroke(if (i % 2 == 0) theme.accent else theme.secondary, 27, 1f)
            canvas.drawLine(0f, y, w, y, paint)
        }
        val sweepY = ((phase * 1.12f) % 1f) * h
        stroke(theme.secondary, 75, 1.8f)
        canvas.drawLine(0f, sweepY, w, sweepY, paint)
        val arc = RectF(w * 0.63f, h * 0.08f, w * 1.04f, h * 0.45f)
        stroke(theme.accent, 48, 1.6f)
        canvas.drawArc(arc, 180f, 180f, false, paint)
    }

    private fun drawCrt(canvas: Canvas, w: Float, h: Float, phase: Float, theme: HGPGTheme) {
        val count = (h / (7f * density)).toInt().coerceIn(1, 100)
        for (i in 0..count) {
            val y = h * i / count
            stroke(theme.secondary, 17, 1f)
            canvas.drawLine(0f, y, w, y, paint)
        }
        val beamY = ((phase * 1.15f) % 1f) * h
        paint.shader = LinearGradient(
            0f, beamY, 0f, beamY + 14f * density,
            intArrayOf(0x00000000, withAlpha(theme.accent, 48), 0x00000000),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        canvas.drawRect(0f, beamY, w, beamY + 14f * density, paint)
        paint.shader = null
        for (i in 0..12) {
            val x = ((i * 0.113f + phase * 0.26f) % 1f) * w
            val y = h * (0.12f + (i % 5) * 0.15f)
            fill(if (i % 2 == 0) theme.accent else theme.secondary, 48)
            val size = (4f + i % 3) * density
            canvas.drawRect(x, y, x + size, y + size, paint)
        }
    }

    private fun stroke(color: Int, alpha: Int, widthDp: Float) {
        paint.shader = null
        paint.color = color
        paint.alpha = alpha.coerceIn(0, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (widthDp * density).coerceAtLeast(0.7f)
        paint.strokeCap = Paint.Cap.ROUND
    }

    private fun fill(color: Int, alpha: Int) {
        paint.shader = null
        paint.color = color
        paint.alpha = alpha.coerceIn(0, 255)
        paint.style = Paint.Style.FILL
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    companion object {
        private const val FRAME_MS = 42L
        private const val LOOP_MS = 14000L
    }
}
