package com.hypergaruda.hgpg

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.api.SettingsAPI
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Theme-aware notification bubbles rendered in Aliucord's own content view.
 * They do not use Android's notification shade, overlays, or Toast window.
 */
internal object HGPGInAppBubble {
    private val activeBubbles = WeakHashMap<FrameLayout, WeakReference<View>>()
    @Volatile private var currentTheme = HGPGThemes.get("garuda")
    private var lastNotificationKey: String? = null
    private var lastNotificationAt = 0L

    fun start(settings: SettingsAPI) {
        currentTheme = HGPGThemes.get(readString(settings.getString("theme_profile", "garuda")))
    }

    fun setTheme(theme: HGPGTheme) {
        currentTheme = theme
    }

    fun show(activity: Activity, title: String, message: String, duration: Long = 2800L) {
        if (activity.isFinishing || activity.isDestroyed) return
        // Several UI callbacks can report the same successful action (for example,
        // a setting listener plus a panel refresh). Collapse identical back-to-back
        // notifications while still allowing a deliberate repeat shortly afterwards.
        val key = "$title\u0000$message"
        synchronized(activeBubbles) {
            val now = SystemClock.uptimeMillis()
            if (key == lastNotificationKey && now - lastNotificationAt < 1100L) return
            lastNotificationKey = key
            lastNotificationAt = now
        }
        val host = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        host.post {
            if (activity.isFinishing || activity.isDestroyed || !host.isAttachedToWindow) return@post

            val previous = synchronized(activeBubbles) {
                activeBubbles.remove(host)?.get()
            }
            previous?.let {
                it.animate().cancel()
                (it.parent as? ViewGroup)?.removeView(it)
            }

            val theme = currentTheme
            val density = activity.resources.displayMetrics.density
            val bubble = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 13), dp(activity, 11), dp(activity, 14), dp(activity, 11))
                background = HGPGGlassUI.background(theme, cornerScale = 0.18f, panelSurface = false)
                alpha = 0f
                translationY = -dp(activity, 22).toFloat()
                scaleX = 0.97f
                scaleY = 0.97f
                elevation = 1200f * density
                isClickable = true
                isFocusable = true
                contentDescription = "$title. $message. Tap to dismiss."
                setOnClickListener { dismiss(host, this) }
            }

            val accentBar = View(activity).apply {
                setBackgroundColor(theme.accent)
            }
            bubble.addView(accentBar, LinearLayout.LayoutParams(dp(activity, 4), dp(activity, 38)).apply {
                rightMargin = dp(activity, 12)
            })

            val textColumn = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            textColumn.addView(TextView(activity).apply {
                text = title
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.accent)
                maxLines = 1
            })
            textColumn.addView(TextView(activity).apply {
                text = message
                textSize = 13f
                setTextColor(theme.text)
                maxLines = 2
                setPadding(0, dp(activity, 2), 0, 0)
            })
            bubble.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                leftMargin = dp(activity, 14)
                rightMargin = dp(activity, 14)
                topMargin = dp(activity, 12)
            }

            synchronized(activeBubbles) {
                activeBubbles[host] = WeakReference(bubble)
            }
            host.addView(bubble, params)
            bubble.bringToFront()
            bubble.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(230L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            bubble.postDelayed({ dismiss(host, bubble) }, duration.coerceIn(1400L, 6000L))
        }
    }

    fun stop() {
        val toRemove = synchronized(activeBubbles) {
            val items = activeBubbles.entries.mapNotNull { entry ->
                entry.value.get()?.let { entry.key to it }
            }
            activeBubbles.clear()
            items
        }
        toRemove.forEach { (host, bubble) ->
            host.post {
                bubble.animate().cancel()
                (bubble.parent as? ViewGroup)?.removeView(bubble)
            }
        }
    }

    private fun dismiss(host: FrameLayout, bubble: View) {
        val isActive = synchronized(activeBubbles) {
            if (activeBubbles[host]?.get() !== bubble) false
            else {
                activeBubbles.remove(host)
                true
            }
        }
        if (!isActive) return
        bubble.animate().cancel()
        bubble.animate().alpha(0f).translationY(-dp(bubble.context, 10).toFloat())
            .scaleX(0.98f).scaleY(0.98f).setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { (bubble.parent as? ViewGroup)?.removeView(bubble) }
            .start()
    }

    private fun readString(value: Any?): String {
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

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
