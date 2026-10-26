package com.hypergaruda.hgpg

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage

class HGPGSettingsPage(private val pluginSettings: SettingsAPI) : SettingsPage() {
    override fun onViewBound(view: View) {
        // SettingsPage initializes its internal layout here. This must run before addView().
        super.onViewBound(view)

        val context = view.context
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        container.addView(TextView(context).apply {
            text = "HG PG · Theme Profile"
            textSize = 20f
        })

        container.addView(TextView(context).apply {
            text = "Choose a saved visual profile. This MVP stores the preference; it does not recolor the full Discord interface."
            textSize = 14f
            setPadding(0, 12, 0, 16)
        })

        val savedProfile = toRealString(pluginSettings.getString("theme_profile", "garuda"))
        val current = TextView(context).apply {
            text = "Current profile: $savedProfile"
            textSize = 16f
        }
        container.addView(current)

        listOf("garuda", "midnight", "forest", "aurora").forEach { profile ->
            container.addView(Button(context).apply {
                text = "Use $profile"
                setOnClickListener {
                    pluginSettings.setString("theme_profile", profile)
                    current.text = "Current profile: $profile"
                }
            })
        }

        addView(container)
    }

    /**
     * Aliucord may expose settings strings as an obfuscated CharSequence wrapper.
     * Read it through the interface reflectively before treating it as a String.
     */
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
