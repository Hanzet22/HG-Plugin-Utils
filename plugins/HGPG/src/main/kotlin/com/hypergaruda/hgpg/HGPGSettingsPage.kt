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

        val current = TextView(context).apply {
            text = "Current profile: ${pluginSettings.getString("theme_profile", "garuda")}"
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
}
