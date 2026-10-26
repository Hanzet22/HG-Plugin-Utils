package com.hypergaruda.hgpg

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage

class HGPGSettingsPage(private val pluginSettings: SettingsAPI) : SettingsPage() {
    override fun onViewBound(view: View) {
        super.onViewBound(view)
        val context = view.context
        val density = context.resources.displayMetrics.density
        val activeTheme = HGPGThemes.get(toRealString(pluginSettings.getString("theme_profile", "garuda")))
        var activeId = activeTheme.id

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
            background = HGPGAnimatedDrawable(activeTheme, cornerScale = 0.04f)
        }

        container.addView(TextView(context).apply {
            text = "HG PG · Animated Theme Garage"
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(activeTheme.text)
        })

        container.addView(TextView(context).apply {
            text = "Six built-in animated themes. HG PG also recolors supported Aliucord/Discord app resources directly; no separate theme plugin or JSON import is needed. Some hard-coded elements may remain unchanged."
            textSize = 14f
            setTextColor(activeTheme.muted)
            setPadding(0, (10 * density).toInt(), 0, (16 * density).toInt())
        })

        var ambientEnabled = pluginSettings.getBool("ambient_light_enabled", true)
        var ambientIntensity = pluginSettings.getInt("ambient_light_intensity", 72).coerceIn(0, 100)
        HGPGGlobalMotionOverlay.setAmbientEnabled(ambientEnabled)
        HGPGGlobalMotionOverlay.setAmbientIntensity(ambientIntensity)

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
        val ambientIntensitySlider = SeekBar(context).apply {
            max = 100
            progress = ambientIntensity
            contentDescription = "Ambient Light intensity"
        }
        ambientIntensitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                ambientIntensity = progress.coerceIn(0, 100)
                ambientIntensityLabel.text = "Ambient Light Intensity: $ambientIntensity%"
                HGPGGlobalMotionOverlay.setAmbientIntensity(ambientIntensity)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                pluginSettings.setInt("ambient_light_intensity", ambientIntensity)
            }
        })
        container.addView(ambientIntensitySlider, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (10 * density).toInt()
        })

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
                button.background = GradientDrawable().apply {
                    cornerRadius = 12f * density
                    setColor(if (selected) theme.accent else theme.surfaceRaised)
                    setStroke((1.2f * density).toInt().coerceAtLeast(1), if (selected) theme.accent else theme.border)
                }
            }
        }

        HGPGThemes.all.forEach { theme ->
            val button = Button(context).apply {
                isAllCaps = false
                textSize = 14f
                minHeight = (48 * density).toInt()
                setOnClickListener {
                    activeId = theme.id
                    pluginSettings.setString("theme_profile", theme.id)
                    HGPGGlobalThemeEngine.apply(theme)
                    HGPGMotionEngine.setTheme(theme)
                    HGPGGlobalMotionOverlay.setTheme(theme)
                    HGPGHighTechRenderer.setTheme(theme)
                    current.text = "Current native theme: " + theme.name
                    current.setTextColor(theme.text)
                    container.background = HGPGAnimatedDrawable(theme, cornerScale = 0.04f)
                    HGPGOverlay.refreshTheme(pluginSettings)
                    refreshButtons()
                    HGPGGlobalThemeEngine.refreshActivity(context)
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
        addView(container)
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
