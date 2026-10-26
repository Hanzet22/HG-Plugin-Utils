package com.hypergaruda.hgpg

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.view.animation.DecelerateInterpolator
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage

class HGPGSettingsPage(private val pluginSettings: SettingsAPI) : SettingsPage() {
    override fun onViewBound(view: View) {
        super.onViewBound(view)
        val context = view.context
        val density = context.resources.displayMetrics.density
        var activeTheme = HGPGThemes.get(toRealString(pluginSettings.getString("theme_profile", "garuda")))
        var activeId = activeTheme.id

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
            background = HGPGGlassUI.background(activeTheme, cornerScale = 0.04f, panelSurface = false, animatedUnderlay = true)
            alpha = 0f
            translationY = (10 * density).toFloat()
        }

        container.addView(TextView(context).apply {
            text = "HG PG · Animated Theme Garage"
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(activeTheme.text)
        })

        container.addView(TextView(context).apply {
            text = "Six animated colour themes plus Morph Glass, Liquid Glass, and 3D UI. Four display colour presets tune HG PG surfaces and highlights; they do not change the phone's physical display mode. Supported Aliucord/Discord resources are recoloured directly."
            textSize = 14f
            setTextColor(activeTheme.muted)
            setPadding(0, (10 * density).toInt(), 0, (16 * density).toInt())
        })

        var ambientEnabled = pluginSettings.getBool("ambient_light_enabled", true)
        var ambientIntensity = pluginSettings.getInt("ambient_light_intensity", 72).coerceIn(0, 100)
        HGPGGlobalMotionOverlay.setAmbientEnabled(ambientEnabled)
        HGPGGlobalMotionOverlay.setAmbientIntensity(ambientIntensity)

        container.addView(TextView(context).apply {
            text = "Floating Button"
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(activeTheme.text)
            setPadding(0, (6 * density).toInt(), 0, (2 * density).toInt())
        })
        container.addView(TextView(context).apply {
            text = "Show or hide the in-app HG launcher without requesting draw-over-other-apps permission."
            textSize = 13f
            setTextColor(activeTheme.muted)
            setPadding(0, 0, 0, (8 * density).toInt())
        })

        val launcherSwitch = Switch(context).apply {
            text = "Enable Floating Button"
            setTextColor(activeTheme.text)
            isChecked = pluginSettings.getBool("launcher_enabled", true)
        }
        launcherSwitch.setOnCheckedChangeListener { _, checked ->
            pluginSettings.setBool("launcher_enabled", checked)
            (context as? Activity)?.let { activity ->
                HGPGOverlay.setLauncherEnabled(activity, pluginSettings, checked)
            }
        }
        container.addView(launcherSwitch, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (4 * density).toInt()
        })

        val resetLauncherButton = Button(context).apply {
            text = "Reset Floating Button Position"
            isAllCaps = false
            setTextColor(activeTheme.text)
            background = HGPGGlassUI.background(activeTheme, cornerScale = 0.14f, panelSurface = false)
            setOnClickListener {
                (context as? Activity)?.let { activity ->
                    HGPGOverlay.resetLauncherPosition(activity, pluginSettings)
                }
            }
        }
        container.addView(resetLauncherButton, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (14 * density).toInt()
        })

        container.addView(TextView(context).apply {
            text = "Ambient Light"
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(activeTheme.text)
            setPadding(0, (6 * density).toInt(), 0, (2 * density).toInt())
        })
        container.addView(TextView(context).apply {
            text = "Moving, theme-matched glow around the screen edges."
            textSize = 13f
            setTextColor(activeTheme.muted)
            setPadding(0, 0, 0, (8 * density).toInt())
        })

        val ambientSwitch = Switch(context).apply {
            text = "Enable Ambient Light"
            setTextColor(activeTheme.text)
            isChecked = ambientEnabled
        }
        ambientSwitch.setOnCheckedChangeListener { _, checked ->
            ambientEnabled = checked
            pluginSettings.setBool("ambient_light_enabled", checked)
            HGPGGlobalMotionOverlay.setAmbientEnabled(checked)
            (context as? Activity)?.let { activity ->
                HGPGInAppBubble.show(activity, "Ambient Light", if (checked) "Ambient lighting enabled." else "Ambient lighting disabled.")
            }
        }
        container.addView(ambientSwitch, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (4 * density).toInt()
        })

        val ambientIntensityLabel = TextView(context).apply {
            text = "Ambient Light Intensity: $ambientIntensity%"
            textSize = 14f
            setTextColor(activeTheme.muted)
        }
        container.addView(ambientIntensityLabel)
        val targetAmbientIntensity = ambientIntensity
        var sliderIntroAnimator: ValueAnimator? = null
        val ambientIntensitySlider = SeekBar(context).apply {
            max = 100
            progress = 0
            contentDescription = "Ambient Light intensity"
        }
        ambientIntensitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) sliderIntroAnimator?.cancel()
                ambientIntensity = progress.coerceIn(0, 100)
                ambientIntensityLabel.text = "Ambient Light Intensity: $ambientIntensity%"
                HGPGGlobalMotionOverlay.setAmbientIntensity(ambientIntensity)
                if (fromUser) {
                    ambientIntensityLabel.animate().cancel()
                    ambientIntensityLabel.animate().scaleX(1.045f).scaleY(1.045f).setDuration(70L)
                        .withEndAction {
                            ambientIntensityLabel.animate().scaleX(1f).scaleY(1f).setDuration(110L)
                                .setInterpolator(DecelerateInterpolator()).start()
                        }.start()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                sliderIntroAnimator?.cancel()
                seekBar?.let { slider ->
                    slider.animate().cancel()
                    slider.animate().scaleY(1.10f).translationY(-density)
                        .setDuration(100L).setInterpolator(DecelerateInterpolator()).start()
                }
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                pluginSettings.setInt("ambient_light_intensity", ambientIntensity)
                seekBar?.let { slider ->
                    slider.animate().scaleY(1f).translationY(0f)
                        .setDuration(150L).setInterpolator(DecelerateInterpolator()).start()
                }
                ambientIntensityLabel.animate().scaleX(1f).scaleY(1f)
                    .setDuration(120L).setInterpolator(DecelerateInterpolator()).start()
                (context as? Activity)?.let { activity ->
                    HGPGInAppBubble.show(activity, "Ambient Light", "Intensity saved at $ambientIntensity%.")
                }
            }
        })
        container.addView(ambientIntensitySlider, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (10 * density).toInt()
        })
        ambientIntensitySlider.alpha = 0f
        ambientIntensitySlider.scaleY = 0.86f
        ambientIntensitySlider.post {
            if (ambientIntensitySlider.isAttachedToWindow) {
                ambientIntensitySlider.animate().alpha(1f).scaleY(1f).setDuration(240L)
                    .setInterpolator(DecelerateInterpolator()).start()
                sliderIntroAnimator = ValueAnimator.ofInt(0, targetAmbientIntensity).apply {
                    duration = 430L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { animation ->
                        if (ambientIntensitySlider.isAttachedToWindow) {
                            ambientIntensitySlider.progress = animation.animatedValue as Int
                        } else {
                            cancel()
                        }
                    }
                    start()
                }
            }
        }

        val current = TextView(context).apply {
            text = "Current native theme: " + activeTheme.name
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(activeTheme.text)
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        container.addView(current)

        val buttons = mutableListOf<Pair<HGPGTheme, Button>>()
        fun refreshButtons() {
            buttons.forEach { entry ->
                val theme = entry.first
                val button = entry.second
                val selected = theme.id == activeId
                button.text = (if (selected) "✓ ACTIVE  ·  " else "APPLY  ·  ") + theme.name
                button.setTextColor(if (selected) Color.BLACK else theme.text)
                button.background = HGPGGlassUI.background(theme, cornerScale = 0.14f, panelSurface = false)
            }
        }

        HGPGThemes.all.forEach { theme ->
            val button = Button(context).apply {
                isAllCaps = false
                textSize = 14f
                minHeight = (48 * density).toInt()
                setOnClickListener {
                    activeId = theme.id
                    activeTheme = theme
                    pluginSettings.setString("theme_profile", theme.id)
                    HGPGGlobalThemeEngine.apply(theme)
                    HGPGMotionEngine.setTheme(theme)
                    HGPGGlobalMotionOverlay.setTheme(theme)
                    HGPGInAppBubble.setTheme(theme)
                    HGPGHighTechRenderer.setTheme(theme)
                    current.text = "Current native theme: " + theme.name
                    current.setTextColor(theme.text)
                    container.background = HGPGGlassUI.background(theme, cornerScale = 0.04f, panelSurface = false, animatedUnderlay = true)
                    HGPGOverlay.refreshTheme(pluginSettings)
                    refreshButtons()
                    HGPGGlobalThemeEngine.refreshActivity(context)
                    (context as? Activity)?.let { activity ->
                        HGPGInAppBubble.show(activity, "Theme applied", theme.name)
                    }
                    animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }.start()
                }
            }
            buttons.add(theme to button)
            container.addView(button, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * density).toInt()
            })
        }
        refreshButtons()
        lateinit var glassSettingsButton: Button
        glassSettingsButton = Button(context).apply {
            text = "🫧  Customize Glass & Display"
            isAllCaps = false
            textSize = 14f
            minHeight = (48 * density).toInt()
            setTextColor(activeTheme.text)
            background = HGPGGlassUI.background(activeTheme, cornerScale = 0.14f)
            setOnClickListener {
                (context as? Activity)?.let { activity ->
                    HGPGGlassUI.showSettingsDialog(activity, pluginSettings, activeTheme) {
                        val refreshedTheme = HGPGThemes.get(toRealString(pluginSettings.getString("theme_profile", "garuda")))
                        activeTheme = refreshedTheme
                        container.background = HGPGGlassUI.background(refreshedTheme, cornerScale = 0.04f, panelSurface = false, animatedUnderlay = true)
                        glassSettingsButton.background = HGPGGlassUI.background(refreshedTheme, cornerScale = 0.14f, panelSurface = false)
                        glassSettingsButton.setTextColor(refreshedTheme.text)
                        refreshButtons()
                    }
                }
            }
        }
        container.addView(glassSettingsButton, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (14 * density).toInt()
        })
        addView(container)
        container.animate().alpha(1f).translationY(0f).setDuration(280L)
            .setInterpolator(DecelerateInterpolator()).start()
        HGPGUIAnimations.fadeInTextTree(container, staggerMs = 12L)
    }

    /** Read settings through CharSequence to remain compatible with Aliucord's wrapper. */
    private fun toRealString(value: Any?): String {
        if (value == null) return ""
        return try {
            val charSequenceClass = CharSequence::class.java
            val lengthMethod = charSequenceClass.getMethod("length")
            val charAtMethod = charSequenceClass.getMethod("charAt", Integer.TYPE)
            val length = (lengthMethod.invoke(value) as Int).coerceAtLeast(0)
            val result = java.lang.StringBuilder(length)
            var index = 0
            while (index < length) {
                result.append(charAtMethod.invoke(value, index) as Char)
                index += 1
            }
            result.toString()
        } catch (_: Throwable) {
            try {
                val method = Any::class.java.getMethod("toString")
                val rendered = method.invoke(value)
                val bytes = java.io.ByteArrayOutputStream()
                java.io.PrintStream(bytes).use { stream -> stream.print(rendered) }
                bytes.toString("UTF-8")
            } catch (_: Throwable) {
                ""
            }
        }
    }
}
