package com.hypergaruda.hgpg

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

internal enum class HGPGMotion { DEEP_SEA, GARUDA, LED_ORANGE, GT_SPORT, GT_OLD, CRT_PS2 }

internal data class HGPGTheme(
    val id: String,
    val name: String,
    val subtitle: String,
    val background: Int,
    val surface: Int,
    val surfaceRaised: Int,
    val accent: Int,
    val secondary: Int,
    val text: Int,
    val muted: Int,
    val border: Int,
    val motion: HGPGMotion
)

internal object HGPGThemes {
    val all: List<HGPGTheme> = listOf(
        HGPGTheme("deep_sea_blue", "1. Deep Sea Blue", "Abyssal navy, cyan currents and floating bubbles",
            0xFF031426.toInt(), 0xFF0A2942.toInt(), 0xFF103651.toInt(),
            0xFF43D9F8.toInt(), 0xFF0E91C8.toInt(), 0xFFF0FAFF.toInt(), 0xFFA9C8DA.toInt(), 0xFF255C78.toInt(), HGPGMotion.DEEP_SEA),
        HGPGTheme("garuda", "2. Garuda Indonesia", "Confident red, clean white and golden flight trails",
            0xFF190509.toInt(), 0xFF351018.toInt(), 0xFF511721.toInt(),
            0xFFEF233C.toInt(), 0xFFF7F7F7.toInt(), 0xFFFFF4F4.toInt(), 0xFFDDB8BE.toInt(), 0xFFB9384B.toInt(), HGPGMotion.GARUDA),
        HGPGTheme("led_orange", "3. Light Emitting Orange", "Neon orange bloom with warm amber pulses",
            0xFF100700.toInt(), 0xFF291204.toInt(), 0xFF3A1B06.toInt(),
            0xFFFF6A00.toInt(), 0xFFFFCB69.toInt(), 0xFFFFF1DB.toInt(), 0xFFD5A47B.toInt(), 0xFFB84900.toInt(), HGPGMotion.LED_ORANGE),
        HGPGTheme("gt_sport", "4. Gran Turismo Sport", "Electric blue speed streaks and modern racing telemetry",
            0xFF04111F.toInt(), 0xFF0A2238.toInt(), 0xFF0C2D49.toInt(),
            0xFF00B8FF.toInt(), 0xFFDDF6FF.toInt(), 0xFFF2FBFF.toInt(), 0xFFA9C5D6.toInt(), 0xFF007EBD.toInt(), HGPGMotion.GT_SPORT),
        HGPGTheme("gt_old", "5. Gran Turismo Old", "Retro silver-blue grid, dashboard arcs and scan sweeps",
            0xFF0A1118.toInt(), 0xFF19252D.toInt(), 0xFF202F39.toInt(),
            0xFFB7D9FF.toInt(), 0xFFFF4B52.toInt(), 0xFFF1F6FA.toInt(), 0xFFA9BAC7.toInt(), 0xFF637B8C.toInt(), HGPGMotion.GT_OLD),
        HGPGTheme("crt_ps2", "7. CRT PS2", "Indigo glow, phosphor scanlines and a rolling CRT beam",
            0xFF08071A.toInt(), 0xFF15133B.toInt(), 0xFF201D53.toInt(),
            0xFF8277FF.toInt(), 0xFF30DAFF.toInt(), 0xFFF5F2FF.toInt(), 0xFFB8B3E8.toInt(), 0xFF5148A5.toInt(), HGPGMotion.CRT_PS2)
    )

    fun get(profile: String?): HGPGTheme {
        val key = profile.orEmpty().trim().lowercase().replace(' ', '_').replace('-', '_')
        val normalized = when (key) {
            "garuda_indonesia", "garuda_red" -> "garuda"
            "midnight" -> "deep_sea_blue"
            "forest" -> "gt_old"
            "aurora" -> "deep_sea_blue"
            "light_emitting_orange" -> "led_orange"
            "gran_turismo_sport" -> "gt_sport"
            "gran_turismo_old" -> "gt_old"
            "crt" -> "crt_ps2"
            else -> key
        }
        return all.firstOrNull { it.id == normalized } ?: all[1]
    }
}

/** Animated, low-overhead theme backdrop. It draws in the plugin's own view tree. */
internal class HGPGAnimatedDrawable(
    private val theme: HGPGTheme,
    private val cornerScale: Float = 0.06f,
    private val intensity: Float = 1f
) : Drawable(), Runnable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var requestedAlpha = 255
    private var requestedFilter: ColorFilter? = null

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val rect = RectF(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        val width = rect.width().coerceAtLeast(1f)
        val height = rect.height().coerceAtLeast(1f)
        val now = SystemClock.uptimeMillis()
        val phase = (now % 12000L).toFloat() / 12000f
        val save = canvas.save()
        val radius = min(width, height) * cornerScale.coerceIn(0f, 0.5f)
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)

        paint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(theme.background, theme.surface, theme.background),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = requestedAlpha
        paint.colorFilter = requestedFilter
        paint.style = Paint.Style.FILL
        canvas.drawRect(rect, paint)
        paint.shader = null

        when (theme.motion) {
            HGPGMotion.DEEP_SEA -> drawDeepSea(canvas, rect, phase)
            HGPGMotion.GARUDA -> drawGaruda(canvas, rect, phase)
            HGPGMotion.LED_ORANGE -> drawLedOrange(canvas, rect, phase)
            HGPGMotion.GT_SPORT -> drawGtSport(canvas, rect, phase)
            HGPGMotion.GT_OLD -> drawGtOld(canvas, rect, phase)
            HGPGMotion.CRT_PS2 -> drawCrt(canvas, rect, phase)
        }

        setPaint(theme.accent, 120, Paint.Style.STROKE, 1.2f)
        canvas.drawRoundRect(rect, radius, radius, paint)
        canvas.restoreToCount(save)
        if (isVisible) scheduleSelf(this, now + 40L)
    }

    private fun drawDeepSea(canvas: Canvas, r: RectF, phase: Float) {
        val w = r.width()
        val h = r.height()
        for (layer in 0..3) {
            val yBase = r.top + h * (0.37f + layer * 0.16f)
            val path = Path()
            for (step in 0..40) {
                val x = r.left + w * step / 40f
                val wave = sin(step / 40.0 * PI * 2.2 + phase * PI * 2.0 + layer * 1.1).toFloat()
                val y = yBase + wave * h * (0.025f + layer * 0.004f)
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            setPaint(if (layer % 2 == 0) theme.accent else theme.secondary, 35 + layer * 9, Paint.Style.STROKE, 1.2f + layer * 0.4f)
            canvas.drawPath(path, paint)
        }
        for (i in 0..9) {
            val x = r.left + ((i * 0.157f + phase * 0.19f) % 1f) * w
            val y = r.bottom - (((i * 0.137f + phase * 0.55f) % 1f) * h)
            setPaint(theme.accent, 28 + (i % 3) * 10, Paint.Style.STROKE, 1.2f)
            canvas.drawCircle(x, y, h * (0.0045f + (i % 3) * 0.0015f), paint)
        }
    }

    private fun drawGaruda(canvas: Canvas, r: RectF, phase: Float) {
        val h = r.height()
        val w = r.width()
        for (i in 0..5) {
            val x = r.left + ((i * 0.22f + phase * 0.43f) % 1.45f) * w
            setPaint(if (i % 2 == 0) theme.accent else theme.secondary, 16 + (i % 3) * 7, Paint.Style.STROKE, h * 0.025f)
            canvas.drawLine(x, r.bottom, x + h * 0.78f, r.top, paint)
        }
        val cx = r.left + w * 0.82f
        val cy = r.top + h * 0.22f
        for (i in 0..3) {
            val ring = h * (0.04f + ((phase + i * 0.23f) % 1f) * 0.22f)
            setPaint(0xFFFFC857.toInt(), 65 - i * 9, Paint.Style.STROKE, 1.4f)
            canvas.drawCircle(cx, cy, ring, paint)
        }
        for (i in 0..4) {
            val y = r.top + h * (0.15f + i * 0.075f)
            setPaint(theme.secondary, 19, Paint.Style.STROKE, 1.5f)
            canvas.drawLine(r.left + w * 0.52f, y, r.right, y - h * 0.035f, paint)
        }
    }

    private fun drawLedOrange(canvas: Canvas, r: RectF, phase: Float) {
        val w = r.width()
        val h = r.height()
        val cx = r.left + w * (0.22f + 0.56f * (0.5f + 0.5f * sin(phase * PI * 2.0).toFloat()))
        val cy = r.top + h * 0.38f
        paint.shader = RadialGradient(
            cx, cy, maxOf(w, h) * 0.82f,
            intArrayOf(translucent(theme.accent, 90), translucent(theme.secondary, 28), translucent(theme.background, 0)),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = 255
        paint.colorFilter = requestedFilter
        canvas.drawRect(r, paint)
        paint.shader = null
        for (i in 0..5) {
            val y = r.top + (((i * 0.19f + phase * 0.66f) % 1f) * h)
            setPaint(if (i % 2 == 0) theme.accent else theme.secondary, 29 + (i % 3) * 8, Paint.Style.STROKE, 1.2f + i % 2)
            canvas.drawLine(r.left, y, r.right, y - h * 0.045f, paint)
        }
    }

    private fun drawGtSport(canvas: Canvas, r: RectF, phase: Float) {
        val w = r.width()
        val h = r.height()
        for (i in 0..20) {
            val travel = ((i / 21f + phase * 0.86f) % 1.35f)
            val x = r.left + w * (1.12f - travel)
            val y = r.top + h * (0.10f + (i % 7) * 0.125f)
            val length = h * (0.22f + (i % 4) * 0.09f)
            setPaint(if (i % 4 == 0) theme.secondary else theme.accent, 22 + (i % 4) * 8, Paint.Style.STROKE, if (i % 4 == 0) 2.2f else 1.2f)
            canvas.drawLine(x, y, x + length, y - length * 0.16f, paint)
        }
        setPaint(theme.accent, 22, Paint.Style.FILL)
        val stripeX = r.left + ((phase * 1.6f) % 1f) * w
        canvas.drawRect(RectF(stripeX - w * 0.04f, r.top, stripeX + w * 0.04f, r.bottom), paint)
    }

    private fun drawGtOld(canvas: Canvas, r: RectF, phase: Float) {
        val w = r.width()
        val h = r.height()
        val center = r.left + w * 0.5f
        val horizon = r.top + h * 0.55f
        for (i in 0..12) {
            val x = r.left + w * i / 12f
            val bottomX = center + (x - center) * 1.22f
            setPaint(theme.accent, 29, Paint.Style.STROKE, 1f)
            canvas.drawLine(center + (x - center) * 0.08f, horizon, bottomX, r.bottom, paint)
        }
        for (i in 0..7) {
            val ratio = i / 7f
            val y = horizon + (r.bottom - horizon) * ratio * ratio
            setPaint(if (i % 2 == 0) theme.accent else theme.secondary, 25, Paint.Style.STROKE, 1f)
            canvas.drawLine(r.left, y, r.right, y, paint)
        }
        val sweepY = r.top + phase * h
        setPaint(theme.secondary, 82, Paint.Style.STROKE, 1.8f)
        canvas.drawLine(r.left, sweepY, r.right, sweepY, paint)
        val arc = RectF(r.left + w * 0.62f, r.top + h * 0.08f, r.right + w * 0.04f, r.top + h * 0.45f)
        setPaint(theme.accent, 48, Paint.Style.STROKE, 1.6f)
        canvas.drawArc(arc, 180f, 180f, false, paint)
    }

    private fun drawCrt(canvas: Canvas, r: RectF, phase: Float) {
        val h = r.height()
        val scanCount = min(68, (h / 5f).toInt().coerceAtLeast(1))
        for (i in 0..scanCount) {
            val y = r.top + h * i / scanCount
            setPaint(theme.secondary, 15, Paint.Style.STROKE, 1f)
            canvas.drawLine(r.left, y, r.right, y, paint)
        }
        val beamY = r.top + ((phase * 1.15f) % 1f) * h
        setPaint(theme.accent, 24, Paint.Style.FILL)
        canvas.drawRect(RectF(r.left, beamY, r.right, beamY + h * 0.035f), paint)
        val w = r.width()
        for (i in 0..10) {
            val x = r.left + ((i * 0.113f + phase * 0.28f) % 1f) * w
            val y = r.top + h * (0.12f + (i % 5) * 0.15f)
            setPaint(if (i % 2 == 0) theme.accent else theme.secondary, 33, Paint.Style.FILL)
            canvas.drawRect(RectF(x, y, x + w * 0.018f, y + h * 0.018f), paint)
        }
    }

    private fun setPaint(color: Int, alpha: Int, style: Paint.Style, strokeWidth: Float = 1f) {
        paint.shader = null
        paint.color = color
        paint.alpha = (alpha * intensity * requestedAlpha / 255f).toInt().coerceIn(0, 255)
        paint.colorFilter = requestedFilter
        paint.style = style
        paint.strokeWidth = strokeWidth.coerceAtLeast(0.6f)
        paint.strokeCap = Paint.Cap.ROUND
    }

    private fun translucent(color: Int, alpha: Int): Int {
        val a = (alpha * intensity * requestedAlpha / 255f).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    override fun run() {
        if (isVisible) invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        requestedAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        requestedFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (visible) scheduleSelf(this, SystemClock.uptimeMillis() + 40L) else unscheduleSelf(this)
        return changed
    }
}
