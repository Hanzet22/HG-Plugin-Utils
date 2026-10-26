package com.hypergaruda.hgpg

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import androidx.appcompat.app.AlertDialog
import com.aliucord.api.SettingsAPI
import java.util.WeakHashMap

/** In-app floating launcher and tool panel, hosted inside Aliucord's own view tree. */
internal object HGPGOverlay {
    private const val TAG = "com.hypergaruda.hgpg.overlay"
    private const val BG = 0xFF11151D.toInt()
    private const val CARD = 0xFF1C2430.toInt()
    private const val FG = 0xFFF2F5F9.toInt()
    private const val MUTED = 0xFFADB8C7.toInt()
    private const val MAX_NOTE = 1500
    private val launchers = WeakHashMap<Activity, TextView>()
    private var panel: Dialog? = null
    private var panelRoot: View? = null
    private var closingPanel = false

    fun attach(activity: Activity, settings: SettingsAPI) {
        try {
            val host = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
            host.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                val existing = host.findViewWithTag<View>(TAG)
                if (existing != null) {
                    launchers[activity] = existing as? TextView ?: return@post
                    return@post
                }

                val size = dp(activity, 54)
                val button = TextView(activity).apply {
                    tag = TAG
                    text = "HG"
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    contentDescription = "Drag to move HG PG button; tap to open the control panel"
                    background = HGPGAnimatedDrawable(HGPGThemes.get(realString(settings.getString("theme_profile", "garuda"))), cornerScale = 0.5f)
                    elevation = dp(activity, 8).toFloat()
                    isClickable = true
                    isFocusable = true
                    alpha = 0f
                    scaleX = 0.72f
                    scaleY = 0.72f
                    setOnClickListener { showPanel(activity, settings) }
                }

                val maxX = (host.width - size).coerceAtLeast(0)
                val maxY = (host.height - size).coerceAtLeast(0)
                val savedX = settings.getInt("launcher_x_percent", -1)
                val savedY = settings.getInt("launcher_y_percent", -1)
                val initialX = if (savedX in 0..10000) (maxX * savedX / 10000f).toInt() else (maxX - dp(activity, 16)).coerceAtLeast(0)
                val initialY = if (savedY in 0..10000) (maxY * savedY / 10000f).toInt() else (maxY - dp(activity, 104)).coerceAtLeast(0)
                val params = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START).apply {
                    leftMargin = initialX
                    topMargin = initialY
                }
                host.addView(button, params)
                button.bringToFront()
                launchers[activity] = button

                var downRawX = 0f
                var downRawY = 0f
                var originX = initialX
                var originY = initialY
                var moved = false
                button.setOnTouchListener { view, event ->
                    val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return@setOnTouchListener false
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            downRawX = event.rawX
                            downRawY = event.rawY
                            originX = lp.leftMargin
                            originY = lp.topMargin
                            moved = false
                            view.animate().scaleX(0.91f).scaleY(0.91f).setDuration(90)
                                .setInterpolator(DecelerateInterpolator()).start()
                            true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val deltaX = event.rawX - downRawX
                            val deltaY = event.rawY - downRawY
                            if (!moved && (abs(deltaX) > dp(activity, 6) || abs(deltaY) > dp(activity, 6))) moved = true
                            if (moved) {
                                val boundX = (host.width - view.width).coerceAtLeast(0)
                                val boundY = (host.height - view.height).coerceAtLeast(0)
                                lp.leftMargin = (originX + deltaX.toInt()).coerceIn(0, boundX)
                                lp.topMargin = (originY + deltaY.toInt()).coerceIn(0, boundY)
                                view.layoutParams = lp
                            }
                            true
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            view.animate().scaleX(1f).scaleY(1f).setDuration(150)
                                .setInterpolator(DecelerateInterpolator()).start()
                            if (moved) {
                                val boundX = (host.width - view.width).coerceAtLeast(0)
                                val boundY = (host.height - view.height).coerceAtLeast(0)
                                val current = view.layoutParams as FrameLayout.LayoutParams
                                settings.setInt("launcher_x_percent", if (boundX == 0) 0 else (current.leftMargin * 10000f / boundX).toInt().coerceIn(0, 10000))
                                settings.setInt("launcher_y_percent", if (boundY == 0) 0 else (current.topMargin * 10000f / boundY).toInt().coerceIn(0, 10000))
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                view.animate().translationY(-dp(activity, 3).toFloat()).setDuration(80).withEndAction {
                                    view.animate().translationY(0f).setDuration(110).start()
                                }.start()
                            } else {
                                view.performClick()
                            }
                            true
                        }
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                            true
                        }
                        else -> false
                    }
                }

                button.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        } catch (_: Throwable) {
            // Protect the host if a future Aliucord layout changes.
        }
    }

    fun dismiss() {
        try { panel?.dismiss() } catch (_: Throwable) {}
        panel = null
        panelRoot = null
        closingPanel = false
        launchers.forEach { (_, button) -> (button.parent as? ViewGroup)?.removeView(button) }
        launchers.clear()
    }

    /** Refresh existing launcher(s) immediately after a theme is selected. */
    fun refreshTheme(settings: SettingsAPI) {
        val selected = HGPGThemes.get(realString(settings.getString("theme_profile", "garuda")))
        launchers.forEach { (activity, button) ->
            if (!activity.isFinishing && !activity.isDestroyed) {
                button.background = HGPGAnimatedDrawable(selected, cornerScale = 0.5f)
                button.animate().cancel()
                button.animate().scaleX(1.12f).scaleY(1.12f).setDuration(140)
                    .withEndAction {
                        button.animate().scaleX(1f).scaleY(1f).setDuration(180)
                            .setInterpolator(DecelerateInterpolator()).start()
                    }.start()
            }
        }
        panelRoot?.background = HGPGAnimatedDrawable(selected, cornerScale = 0.06f)
    }

    private fun showPanel(activity: Activity, settings: SettingsAPI) {
        if (activity.isFinishing || activity.isDestroyed) return
        val theme = HGPGThemes.get(realString(settings.getString("theme_profile", "garuda")))
        try { panel?.dismiss() } catch (_: Throwable) {}
        panelRoot = null
        closingPanel = false
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16))
            background = HGPGAnimatedDrawable(theme, cornerScale = 0.06f)
            alpha = 0f
            scaleX = 0.94f
            scaleY = 0.94f
        }
        panelRoot = root
        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(TextView(activity).apply {
            text = "HG PG"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.text)
        })
        titleBox.addView(TextView(activity).apply {
            text = "HyperGaruda · " + theme.name
            textSize = 12f
            setTextColor(theme.muted)
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(activity).apply {
            text = "×"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(FG)
            contentDescription = "Close panel"
            setPadding(dp(activity, 12), 0, dp(activity, 8), 0)
            setOnClickListener { dismissPanelAnimated() }
        })
        root.addView(header)
        root.addView(View(activity).apply { setBackgroundColor(theme.border) },
            LinearLayout.LayoutParams(-1, dp(activity, 1)).apply {
                topMargin = dp(activity, 12); bottomMargin = dp(activity, 12)
            })
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val options = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(options)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addText(options, "Your utilities, one panel.", 18f, theme.text, true, 0, 4)
        addText(options, "Six animated visual systems. Choose a tool below.", 13f, theme.muted, false, 0, 14)
        addCard(options, "📝  Private Notes", "Save or clear a note on this device.", theme) {
            dismissPanelAnimated { showNote(activity, settings) }
        }
        addCard(options, "🐉  Fursona Creator", "Build a text card and copy it to clipboard.", theme) {
            dismissPanelAnimated { showFursona(activity) }
        }
        addCard(options, "📶  Network Diagnostics", "Check Android connection and validated-internet state.", theme) {
            dismissPanelAnimated { showNetwork(activity) }
        }
        addCard(options, "🎨  Animated Theme Garage", "Six custom palettes with their own moving backdrop and launcher animation.", theme) {
            dismissPanelAnimated { showThemes(activity, settings) }
        }
        addCard(options, "🌐  Community Shortcuts", "Indonesia Furs and Anthro Indonesia.", theme) {
            dismissPanelAnimated {
                AlertDialog.Builder(activity).setTitle("Community Shortcuts")
                    .setMessage("Indonesia Furs\nAnthro Indonesia\n\nUse each community's verified official page or invite. HG PG does not automatically join servers.")
                    .setPositiveButton("Close", null).show()
            }
        }
        addCard(options, "🔒  Privacy & About", "Local-first utilities, no telemetry.", theme) {
            dismissPanelAnimated {
                AlertDialog.Builder(activity).setTitle("HG PG · Privacy")
                    .setMessage("HG PG does not scrape Discord messages, members, channels, or servers. It does not send telemetry or automate Discord user actions. Notes and profile preferences stay in Aliucord plugin settings on this device. Network diagnostics read Android's local connection state. Fursona cards are generated on-device.\n\nThe floating button is part of Aliucord's own UI; no draw-over-other-apps permission is requested.")
                    .setPositiveButton("Close", null).show()
            }
        }
        val current = Dialog(activity)
        panel = current
        current.requestWindowFeature(Window.FEATURE_NO_TITLE)
        current.setContentView(root)
        current.setCanceledOnTouchOutside(false)
        current.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismissPanelAnimated()
                true
            } else false
        }
        current.setOnDismissListener {
            if (panel === current) {
                panel = null
                panelRoot = null
            }
        }
        current.show()
        current.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.55f)
            val width = minOf(activity.resources.displayMetrics.widthPixels - dp(activity, 24), dp(activity, 520)).coerceAtLeast(dp(activity, 260))
            val height = minOf(activity.resources.displayMetrics.heightPixels - dp(activity, 80), dp(activity, 760)).coerceAtLeast(dp(activity, 380))
            setLayout(width, height)
            setGravity(Gravity.CENTER)
        }
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(230)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun dismissPanelAnimated(after: (() -> Unit)? = null) {
        val current = panel
        val root = panelRoot
        if (current == null || root == null) {
            try { current?.dismiss() } catch (_: Throwable) {}
            if (panel === current) panel = null
            panelRoot = null
            after?.invoke()
            return
        }
        if (closingPanel) return
        closingPanel = true
        root.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(150)
            .setInterpolator(DecelerateInterpolator()).withEndAction {
                closingPanel = false
                if (panel === current) panel = null
                if (panelRoot === root) panelRoot = null
                try { current.dismiss() } catch (_: Throwable) {}
                after?.invoke()
            }.start()
    }

    private fun showNote(activity: Activity, settings: SettingsAPI) {
        val field = EditText(activity).apply {
            hint = "Write a private local note…"
            setTextColor(FG); setHintTextColor(MUTED)
            setText(realString(settings.getString("local_note", "")))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 4; maxLines = 8; gravity = Gravity.TOP
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 4), dp(activity, 18), 0)
            addView(field, LinearLayout.LayoutParams(-1, dp(activity, 150)))
        }
        val dialog = AlertDialog.Builder(activity).setTitle("Private Notes").setView(box)
            .setPositiveButton("Save", null)
            .setNeutralButton("Clear", null)
            .setNegativeButton("Close", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = field.text?.toString()?.trim().orEmpty()
                val saved = if (raw.length > MAX_NOTE) raw.substring(0, MAX_NOTE) else raw
                settings.setString("local_note", saved)
                toast(activity, "Note saved locally.")
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                settings.setString("local_note", "")
                field.setText("")
                toast(activity, "Local note cleared.")
            }
        }
        dialog.show()
    }

    private fun showFursona(activity: Activity) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), 0, dp(activity, 18), 0)
        }
        fun field(hint: String): EditText = EditText(activity).apply {
            this.hint = hint; setTextColor(FG); setHintTextColor(MUTED)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
        }
        val name = field("Fursona name")
        val species = field("Species / character type")
        val colors = field("Main colors / palette")
        val result = TextView(activity).apply { text = "Generate a card to preview it here."; setTextColor(FG); textSize = 13f }
        listOf(name, species, colors).forEach { box.addView(it, LinearLayout.LayoutParams(-1, dp(activity, 52))) }
        box.addView(result, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(activity, 8) })
        val dialog = AlertDialog.Builder(activity).setTitle("Fursona Creator").setView(box)
            .setPositiveButton("Generate & Copy", null).setNegativeButton("Close", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cardName = safeText(name.text?.toString(), "Unnamed fursona", 80)
                val cardSpecies = safeText(species.text?.toString(), "Not specified", 100)
                val cardColors = safeText(colors.text?.toString(), "Not specified", 120)
                val card = listOf("FURSONA CARD", "", "Name: " + cardName, "Species: " + cardSpecies,
                    "Palette: " + cardColors, "", "Made with HG PG · HyperGaruda").joinToString("\n")
                result.text = card
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("HG PG fursona card", card))
                toast(activity, "Card generated and copied.")
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showNetwork(activity: Activity) {
        AlertDialog.Builder(activity).setTitle("Network Diagnostics")
            .setMessage(networkSummary(activity) + "\n\nThis is a status check, not a speed or latency test.")
            .setPositiveButton("Refresh", null).setNegativeButton("Close", null).create().also { d ->
                d.setOnShowListener {
                    d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        d.setMessage(networkSummary(activity) + "\n\nThis is a status check, not a speed or latency test.")
                    }
                }
                d.show()
            }
    }

    private fun showThemes(activity: Activity, settings: SettingsAPI) {
        val themes = HGPGThemes.all
        val active = HGPGThemes.get(realString(settings.getString("theme_profile", "garuda")))
        val labels = themes.map { theme ->
            (if (theme.id == active.id) "✓  " else "◉  ") + theme.name + " — " + theme.subtitle
        }.toTypedArray()
        val dialog = AlertDialog.Builder(activity).setTitle("HG PG · Animated Theme Garage")
            .setMessage("Choose a built-in visual system. HG PG applies supported app-wide color resources itself; no separate theme plugin or JSON import is needed.")
            .setItems(labels) { d, which ->
                val chosen = themes[which]
                settings.setString("theme_profile", chosen.id)
                HGPGGlobalThemeEngine.apply(chosen)
                refreshTheme(settings)
                toast(activity, "Native app theme applied: " + chosen.name)
                d.dismiss()
                activity.window?.decorView?.postDelayed({
                    HGPGGlobalThemeEngine.refreshActivity(activity)
                }, 250L)
            }.setNegativeButton("Close", null).create()
        dialog.setOnShowListener {
            dialog.setMessage("Applies to supported Aliucord/Discord resource colors and HG PG's animated panel. Some hard-coded or third-party elements may remain unchanged.")
        }
        dialog.show()
    }

    private fun addCard(parent: LinearLayout, title: String, description: String, theme: HGPGTheme, click: () -> Unit) {
        val ctx = parent.context
        val tile = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            background = shape(ctx, theme.surfaceRaised, 15f, theme.border)
            isClickable = true; isFocusable = true
            alpha = 0f
            translationY = dp(ctx, 9).toFloat()
            setOnClickListener {
                animate().scaleX(0.98f).scaleY(0.98f).setDuration(65).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    click()
                }.start()
            }
        }
        tile.addView(TextView(ctx).apply { text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(theme.text) })
        tile.addView(TextView(ctx).apply { text = description; textSize = 12f; setTextColor(theme.muted); setPadding(0, dp(ctx, 4), 0, 0) })
        val stagger = parent.childCount * 42L
        parent.addView(tile, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(ctx, 10) })
        tile.postDelayed({
            if (tile.parent != null) tile.animate().alpha(1f).translationY(0f).setDuration(260)
                .setInterpolator(DecelerateInterpolator()).start()
        }, stagger)
    }

    private fun addText(parent: LinearLayout, value: String, size: Float, color: Int, bold: Boolean, top: Int, bottom: Int) {
        val ctx = parent.context
        parent.addView(TextView(ctx).apply {
            text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(ctx, top), 0, dp(ctx, bottom))
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun networkSummary(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Android did not provide a ConnectivityManager."
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val info = manager.activeNetworkInfo
            return if (info?.isConnected == true) "Connected via " + info.typeName else "No active connection detected."
        }
        val network = manager.activeNetwork ?: return "No active network detected."
        val caps = manager.getNetworkCapabilities(network) ?: return "Network capabilities are unavailable."
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN (underlying transport may be hidden)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular / mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Other / unknown transport"
        }
        val internet = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) "yes" else "no"
        val validated = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "yes" else "no"
        return "Transport: " + transport + "\nAndroid internet capability: " + internet + "\nValidated internet access: " + validated
    }

    private fun realString(value: Any?): String {
        if (value == null) return ""
        return try {
            val type = CharSequence::class.java
            val length = type.getMethod("length").invoke(value) as Int
            val charAt = type.getMethod("charAt", Integer.TYPE)
            val out = StringBuilder(length.coerceAtLeast(0))
            var index = 0
            while (index < length) { out.append(charAt.invoke(value, index) as Char); index++ }
            out.toString()
        } catch (_: Throwable) {
            try {
                val rendered = Any::class.java.getMethod("toString").invoke(value)
                val out = java.io.ByteArrayOutputStream()
                java.io.PrintStream(out).use { it.print(rendered) }
                out.toString("UTF-8")
            } catch (_: Throwable) { "" }
        }
    }

    private fun safeText(value: String?, fallback: String, limit: Int): String {
        val clean = value.orEmpty().replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return fallback
        return if (clean.length > limit) clean.substring(0, limit) else clean
    }

    private fun toast(activity: Activity, message: String) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()

    private fun accent(settings: SettingsAPI): Int = HGPGThemes.get(realString(settings.getString("theme_profile", "garuda"))).accent

    private fun shape(ctx: Context, color: Int, radius: Float, stroke: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            cornerRadius = radius * ctx.resources.displayMetrics.density
            setColor(color)
            if (stroke != Color.TRANSPARENT) setStroke(dp(ctx, 1), stroke)
        }

    private fun dp(ctx: Context, value: Int) = (value * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
