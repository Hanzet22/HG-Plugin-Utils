package com.hypergaruda.hgpg

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.aliucord.api.SettingsAPI
import kotlin.math.min

internal enum class HGPGGlassStyle(val key: String, val title: String, val description: String) {
    MORPH_GLASS("morph_glass", "Morph Glass", "Soft, layered surfaces with rounded contours and shifting-looking highlights."),
    LIQUID_GLASS("liquid_glass", "Liquid Glass", "Layered translucent surfaces with plugin-rendered gloss, refraction bands, and highlights."),
    THREE_D_UI("3d_ui", "3D UI", "Beveled edges, depth shading, and stronger press-friendly surfaces.");

    companion object {
        fun from(value: String?): HGPGGlassStyle =
            values().firstOrNull { it.key == value.orEmpty().trim().lowercase() } ?: LIQUID_GLASS
    }
}

internal enum class HGPGDisplayProfile(val key: String, val title: String, val description: String) {
    IPS("ips", "IPS Support Color", "Balanced surface brightness and softer contrast."),
    AMOLED("amoled", "AMOLED Support Color", "Deeper dark surfaces with clear accent separation."),
    AMOLED_PLUS("amoled_plus", "AMOLED+ Support Color", "Deeper shadows and stronger accent highlights."),
    SUPER_AMOLED("super_amoled", "Super AMOLED Support Color", "Near-black surfaces with vivid edge highlights.");

    companion object {
        fun from(value: String?): HGPGDisplayProfile =
            values().firstOrNull { it.key == value.orEmpty().trim().lowercase() } ?: AMOLED
    }
}

/**
 * Plugin-owned material rendering for HG PG controls.
 *
 * All material styles are drawn by HG PG; no platform blur or vendor UI
 * effect is required. Display profiles tune plugin surface colours only.
 */
internal object HGPGGlassUI {
    @Volatile private var activeStyle = HGPGGlassStyle.LIQUID_GLASS
    @Volatile private var activeDisplay = HGPGDisplayProfile.AMOLED

    fun start(settings: SettingsAPI) {
        activeStyle = HGPGGlassStyle.from(readSetting(settings.getString("glass_style", "liquid_glass")))
        activeDisplay = HGPGDisplayProfile.from(readSetting(settings.getString("display_profile", "amoled")))
    }

    fun background(
        theme: HGPGTheme,
        cornerScale: Float = 0.14f,
        panelSurface: Boolean = false,
        animatedUnderlay: Boolean = false
    ): Drawable {
        val material = HGPGGlassDrawable(theme, activeStyle, activeDisplay, cornerScale, panelSurface)
        return if (animatedUnderlay) {
            LayerDrawable(arrayOf(HGPGAnimatedDrawable(theme, cornerScale = cornerScale), material))
        } else {
            material
        }
    }

    /** Apply HG PG's own material skin to an Aliucord plugin dialog. */
    fun styleDialog(dialog: AlertDialog, context: Context, theme: HGPGTheme) {
        dialog.window?.setBackgroundDrawable(
            background(theme, cornerScale = 0.055f, panelSurface = true)
        )
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(theme.text)
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(theme.muted)
        val buttons = listOf(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        )
        buttons.filterNotNull().forEach { button ->
            button.setTextColor(theme.text)
            button.background = background(theme, cornerScale = 0.14f, panelSurface = false)
            button.setPadding(dp(context, 12), button.paddingTop, dp(context, 12), button.paddingBottom)
        }
    }

    fun showSettingsDialog(
        activity: Activity,
        settings: SettingsAPI,
        theme: HGPGTheme,
        onApplied: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        start(settings)
        val density = activity.resources.displayMetrics.density
        val scroller = ScrollView(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 8), dp(activity, 18), dp(activity, 12))
            background = background(theme, cornerScale = 0.05f, panelSurface = false)
        }
        scroller.addView(content, ViewGroup.LayoutParams(-1, -2))

        fun sectionTitle(label: String) {
            content.addView(TextView(activity).apply {
                text = label
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(theme.text)
                setPadding(0, dp(activity, 10), 0, dp(activity, 3))
            })
        }
        fun sectionNote(label: String) {
            content.addView(TextView(activity).apply {
                text = label
                textSize = 12f
                setTextColor(theme.muted)
                setPadding(0, 0, 0, dp(activity, 6))
            })
        }
        fun addOptions(
            group: RadioGroup,
            labels: List<String>,
            descriptions: List<String>,
            ids: MutableMap<Int, Int>,
            selectedIndex: Int
        ) {
            labels.forEachIndexed { index, label ->
                val radioId = View.generateViewId()
                val radio = RadioButton(activity).apply {
                    id = radioId
                    text = label + "\n" + descriptions[index]
                    textSize = 13f
                    setTextColor(theme.text)
                    isChecked = index == selectedIndex
                    setPadding(0, dp(activity, 4), 0, dp(activity, 4))
                }
                ids[radioId] = index
                group.addView(radio, RadioGroup.LayoutParams(-1, -2))
            }
        }

        sectionTitle("Glass material")
        sectionNote("Choose the visual layer used by HG PG panels, tiles, the launcher and status bubbles.")
        val styleGroup = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL }
        val styleIds = mutableMapOf<Int, Int>()
        addOptions(
            styleGroup,
            HGPGGlassStyle.values().map { it.title },
            HGPGGlassStyle.values().map { it.description },
            styleIds,
            HGPGGlassStyle.values().indexOf(activeStyle)
        )
        content.addView(styleGroup)

        sectionTitle("Display colour profile")
        sectionNote("These presets tune the plugin's surface darkness and highlight strength; they do not identify or reconfigure your physical panel.")
        val displayGroup = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL }
        val displayIds = mutableMapOf<Int, Int>()
        addOptions(
            displayGroup,
            HGPGDisplayProfile.values().map { it.title },
            HGPGDisplayProfile.values().map { it.description },
            displayIds,
            HGPGDisplayProfile.values().indexOf(activeDisplay)
        )
        content.addView(displayGroup)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("HG PG · Glass & Display Studio")
            .setView(scroller)
            .setPositiveButton("Apply", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            styleDialog(dialog, activity, theme)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val styleIndex = styleIds[styleGroup.checkedRadioButtonId]
                val displayIndex = displayIds[displayGroup.checkedRadioButtonId]
                val chosenStyle = styleIndex?.let { HGPGGlassStyle.values().getOrNull(it) } ?: HGPGGlassStyle.LIQUID_GLASS
                val chosenDisplay = displayIndex?.let { HGPGDisplayProfile.values().getOrNull(it) } ?: HGPGDisplayProfile.AMOLED
                settings.setString("glass_style", chosenStyle.key)
                settings.setString("display_profile", chosenDisplay.key)
                activeStyle = chosenStyle
                activeDisplay = chosenDisplay
                HGPGOverlay.refreshTheme(settings)
                onApplied?.invoke()
                HGPGInAppBubble.show(activity, "Glass UI applied", chosenStyle.title + " · " + chosenDisplay.title)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun readSetting(value: Any?): String {
        if (value == null) return ""
        return try {
            value.toString()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}

private class HGPGGlassDrawable(
    private val theme: HGPGTheme,
    private val style: HGPGGlassStyle,
    private val display: HGPGDisplayProfile,
    private val cornerScale: Float,
    private val panelSurface: Boolean
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var alpha = 255
    private var filter: ColorFilter? = null

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val rect = RectF(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        val width = rect.width().coerceAtLeast(1f)
        val height = rect.height().coerceAtLeast(1f)
        val radius = min(width, height) * cornerScale.coerceIn(0f, 0.5f)
        val base = surfaceColor()
        val save = canvas.save()
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)

        if (style == HGPGGlassStyle.THREE_D_UI) {
            setPaint(withAlpha(Color.BLACK, if (panelSurface) 45 else 72), Paint.Style.FILL, null)
            canvas.drawRoundRect(RectF(rect.left + width * 0.012f, rect.top + height * 0.018f,
                rect.right, rect.bottom), radius, radius, paint)
        }

        // Translucent surfaces let the plugin-drawn underlay show through.
        // 3D UI stays denser; Morph and Liquid have separate opacity ranges.
        val fillAlpha = when {
            panelSurface && style == HGPGGlassStyle.LIQUID_GLASS -> 112
            panelSurface && style == HGPGGlassStyle.MORPH_GLASS -> 154
            panelSurface -> 216
            style == HGPGGlassStyle.LIQUID_GLASS -> 142
            style == HGPGGlassStyle.MORPH_GLASS -> 178
            else -> 232
        }
        paint.shader = LinearGradient(
            rect.left, rect.top, rect.left + width * 0.84f, rect.bottom,
            intArrayOf(
                withAlpha(blend(Color.WHITE, base, if (style == HGPGGlassStyle.THREE_D_UI) 0.12f else 0.23f), scaled(fillAlpha)),
                withAlpha(base, scaled(fillAlpha)),
                withAlpha(blend(base, Color.BLACK, if (display == HGPGDisplayProfile.IPS) 0.12f else 0.28f), scaled(fillAlpha))
            ),
            floatArrayOf(0f, 0.46f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = 255
        paint.colorFilter = filter
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        val glowRadius = maxOf(width, height) * if (style == HGPGGlassStyle.MORPH_GLASS) 0.85f else 0.63f
        paint.shader = RadialGradient(
            rect.left + width * 0.22f, rect.top + height * 0.04f, glowRadius,
            intArrayOf(withAlpha(theme.accent, scaled(if (display == HGPGDisplayProfile.IPS) 28 else 48)),
                withAlpha(theme.secondary, scaled(16)), withAlpha(theme.background, 0)),
            floatArrayOf(0f, 0.43f, 1f), Shader.TileMode.CLAMP
        )
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        when (style) {
            HGPGGlassStyle.MORPH_GLASS -> drawMorph(canvas, rect, radius, width, height)
            HGPGGlassStyle.LIQUID_GLASS -> drawLiquid(canvas, rect, radius, width, height)
            HGPGGlassStyle.THREE_D_UI -> drawThreeD(canvas, rect, radius, width, height)
        }
        drawRim(canvas, rect, radius, width, height)
        canvas.restoreToCount(save)
    }

    private fun drawMorph(canvas: Canvas, r: RectF, radius: Float, w: Float, h: Float) {
        val inner = RectF(r.left + w * 0.035f, r.top + h * 0.04f, r.right - w * 0.035f, r.bottom - h * 0.04f)
        setPaint(withAlpha(Color.WHITE, scaled(24)), Paint.Style.STROKE, null, w * 0.024f)
        canvas.drawRoundRect(inner, radius * 0.84f, radius * 0.84f, paint)
        val wave = Path().apply {
            moveTo(r.left + w * 0.08f, r.top + h * 0.30f)
            cubicTo(r.left + w * 0.30f, r.top - h * 0.04f, r.left + w * 0.60f, r.top + h * 0.18f,
                r.right - w * 0.06f, r.top + h * 0.10f)
        }
        paint.shader = LinearGradient(r.left, r.top, r.right, r.top + h * 0.3f,
            intArrayOf(withAlpha(Color.WHITE, scaled(92)), withAlpha(theme.accent, scaled(48)), withAlpha(Color.WHITE, 0)),
            null, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, h * 0.008f)
        canvas.drawPath(wave, paint)
        paint.shader = null
        // A second soft contour makes Morph Glass distinct from the Liquid profile.
        val lowerWave = Path().apply {
            moveTo(r.left + w * 0.06f, r.top + h * 0.72f)
            cubicTo(r.left + w * 0.30f, r.top + h * 0.52f, r.left + w * 0.56f, r.top + h * 0.88f,
                r.right - w * 0.07f, r.top + h * 0.67f)
        }
        paint.shader = LinearGradient(r.left, r.top + h * 0.55f, r.right, r.bottom,
            intArrayOf(withAlpha(theme.secondary, scaled(38)), withAlpha(Color.WHITE, scaled(20)), withAlpha(theme.accent, 0)),
            null, Shader.TileMode.CLAMP)
        paint.strokeWidth = maxOf(1f, h * 0.006f)
        canvas.drawPath(lowerWave, paint)
        paint.shader = null
    }

    private fun drawLiquid(canvas: Canvas, r: RectF, radius: Float, w: Float, h: Float) {
        val shine = Path().apply {
            moveTo(r.left + w * 0.04f, r.top + h * 0.16f)
            cubicTo(r.left + w * 0.26f, r.top + h * 0.02f, r.left + w * 0.54f, r.top + h * 0.16f,
                r.left + w * 0.70f, r.top + h * 0.075f)
            cubicTo(r.left + w * 0.84f, r.top + h * 0.03f, r.right - w * 0.10f, r.top + h * 0.07f,
                r.right - w * 0.04f, r.top + h * 0.15f)
        }
        paint.shader = LinearGradient(r.left, r.top, r.right, r.top + h * 0.18f,
            intArrayOf(withAlpha(Color.WHITE, scaled(112)), withAlpha(Color.WHITE, scaled(32)), withAlpha(theme.accent, 0)),
            null, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, h * 0.007f)
        canvas.drawPath(shine, paint)
        paint.shader = null
        setPaint(withAlpha(Color.WHITE, scaled(13)), Paint.Style.FILL, null)
        canvas.drawRoundRect(RectF(r.left + w * 0.12f, r.top + h * 0.23f, r.right - w * 0.12f, r.top + h * 0.26f),
            h * 0.03f, h * 0.03f, paint)
    }

    private fun drawThreeD(canvas: Canvas, r: RectF, radius: Float, w: Float, h: Float) {
        setPaint(withAlpha(Color.WHITE, scaled(88)), Paint.Style.STROKE, null, maxOf(1f, w * 0.003f))
        canvas.drawRoundRect(RectF(r.left + 1f, r.top + 1f, r.right - 1f, r.bottom - h * 0.012f),
            radius, radius, paint)
        setPaint(withAlpha(Color.BLACK, scaled(70)), Paint.Style.STROKE, null, maxOf(1f, w * 0.005f))
        canvas.drawLine(r.left + radius * 0.5f, r.bottom - 1f, r.right - radius * 0.5f, r.bottom - 1f, paint)
        setPaint(withAlpha(theme.accent, scaled(44)), Paint.Style.STROKE, null, maxOf(1f, w * 0.004f))
        canvas.drawRoundRect(RectF(r.left + w * 0.018f, r.top + h * 0.018f, r.right - w * 0.018f,
            r.bottom - h * 0.018f), radius * 0.90f, radius * 0.90f, paint)
    }

    private fun drawRim(canvas: Canvas, r: RectF, radius: Float, w: Float, h: Float) {
        val edgeAlpha = when (display) {
            HGPGDisplayProfile.IPS -> 86
            HGPGDisplayProfile.AMOLED -> 112
            HGPGDisplayProfile.AMOLED_PLUS -> 142
            HGPGDisplayProfile.SUPER_AMOLED -> 168
        }
        paint.shader = LinearGradient(r.left, r.top, r.right, r.bottom,
            intArrayOf(withAlpha(Color.WHITE, scaled(edgeAlpha)), withAlpha(theme.accent, scaled(110)),
                withAlpha(theme.secondary, scaled(80)), withAlpha(Color.WHITE, scaled(28))),
            null, Shader.TileMode.CLAMP)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, min(w, h) * 0.004f)
        paint.alpha = 255
        paint.colorFilter = filter
        canvas.drawRoundRect(r, radius, radius, paint)
        paint.shader = null

        setPaint(withAlpha(Color.WHITE, scaled(27)), Paint.Style.STROKE, null, maxOf(1f, min(w, h) * 0.002f))
        val inner = RectF(r.left + min(w, h) * 0.012f, r.top + min(w, h) * 0.012f,
            r.right - min(w, h) * 0.012f, r.bottom - min(w, h) * 0.012f)
        canvas.drawRoundRect(inner, maxOf(0f, radius - min(w, h) * 0.012f),
            maxOf(0f, radius - min(w, h) * 0.012f), paint)
    }

    private fun surfaceColor(): Int = when (display) {
        HGPGDisplayProfile.IPS -> blend(theme.surfaceRaised, 0xFF536578.toInt(), 0.12f)
        HGPGDisplayProfile.AMOLED -> blend(theme.surfaceRaised, Color.BLACK, 0.30f)
        HGPGDisplayProfile.AMOLED_PLUS -> blend(theme.surfaceRaised, Color.BLACK, 0.47f)
        HGPGDisplayProfile.SUPER_AMOLED -> blend(theme.surfaceRaised, Color.BLACK, 0.68f)
    }

    private fun setPaint(color: Int, paintStyle: Paint.Style, shader: Shader?, width: Float = 1f) {
        paint.shader = shader
        paint.color = color
        paint.alpha = 255
        paint.colorFilter = filter
        paint.style = paintStyle
        paint.strokeWidth = width
    }

    private fun scaled(value: Int): Int = (value * alpha / 255).coerceIn(0, 255)

    private fun withAlpha(color: Int, a: Int): Int =
        Color.argb((Color.alpha(color) * a / 255).coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun blend(first: Int, second: Int, amount: Float): Int {
        val f = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(first) * (1f - f) + Color.red(second) * f).toInt().coerceIn(0, 255),
            (Color.green(first) * (1f - f) + Color.green(second) * f).toInt().coerceIn(0, 255),
            (Color.blue(first) * (1f - f) + Color.blue(second) * f).toInt().coerceIn(0, 255)
        )
    }

    override fun setAlpha(alpha: Int) { this.alpha = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
