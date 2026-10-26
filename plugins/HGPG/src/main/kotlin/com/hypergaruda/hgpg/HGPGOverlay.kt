package com.hypergaruda.hgpg

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
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
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import androidx.appcompat.app.AlertDialog
import com.aliucord.api.SettingsAPI
import java.util.WeakHashMap
import org.json.JSONObject

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
            if (!settings.getBool("launcher_enabled", true)) {
                host.findViewWithTag<View>(TAG)?.let { (it.parent as? ViewGroup)?.removeView(it) }
                launchers.remove(activity)
                return
            }
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
                    background = HGPGGlassUI.background(HGPGThemes.get(realString(settings.getString("theme_profile", "garuda"))), cornerScale = 0.5f)
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

    /** Enable or disable the floating launcher without requiring an app restart. */
    fun setLauncherEnabled(activity: Activity, settings: SettingsAPI, enabled: Boolean) {
        settings.setBool("launcher_enabled", enabled)
        if (enabled) {
            attach(activity, settings)
            toast(activity, "Floating button enabled.")
            return
        }

        try { panel?.dismiss() } catch (_: Throwable) {}
        panel = null
        panelRoot = null
        closingPanel = false
        launchers.toMap().forEach { (_, button) ->
            (button.parent as? ViewGroup)?.removeView(button)
        }
        launchers.clear()
        activity.findViewById<ViewGroup>(android.R.id.content)
            ?.findViewWithTag<View>(TAG)
            ?.let { (it.parent as? ViewGroup)?.removeView(it) }
        HGPGInAppBubble.show(activity, "HG PG", "Floating button disabled.")
    }

    /** Return the floating launcher to its default lower-right position. */
    fun resetLauncherPosition(activity: Activity, settings: SettingsAPI) {
        settings.setInt("launcher_x_percent", -1)
        settings.setInt("launcher_y_percent", -1)
        val host = activity.findViewById<FrameLayout>(android.R.id.content)
        if (host == null) {
            toast(activity, "Position reset; reopen Aliucord to reattach the button.")
            return
        }
        host.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            val button = host.findViewWithTag<View>(TAG) as? TextView
            if (button == null) {
                attach(activity, settings)
                toast(activity, "Default launcher position restored.")
                return@post
            }
            val maxX = (host.width - button.width).coerceAtLeast(0)
            val maxY = (host.height - button.height).coerceAtLeast(0)
            val x = (maxX - dp(activity, 16)).coerceAtLeast(0)
            val y = (maxY - dp(activity, 104)).coerceAtLeast(0)
            val params = button.layoutParams as? FrameLayout.LayoutParams
            if (params != null) {
                params.leftMargin = x
                params.topMargin = y
                button.layoutParams = params
            }
            settings.setInt("launcher_x_percent", if (maxX == 0) 0 else (x * 10000f / maxX).toInt().coerceIn(0, 10000))
            settings.setInt("launcher_y_percent", if (maxY == 0) 0 else (y * 10000f / maxY).toInt().coerceIn(0, 10000))
            button.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120L).withEndAction {
                button.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
            }.start()
            toast(activity, "Default launcher position restored.")
        }
    }

    /** Refresh existing launcher(s) immediately after a theme is selected. */
    fun refreshTheme(settings: SettingsAPI) {
        HGPGGlassUI.start(settings)
        val selected = HGPGThemes.get(realString(settings.getString("theme_profile", "garuda")))
        HGPGInAppBubble.setTheme(selected)
        launchers.forEach { (activity, button) ->
            if (!activity.isFinishing && !activity.isDestroyed) {
                button.background = HGPGGlassUI.background(selected, cornerScale = 0.5f)
                button.animate().cancel()
                button.animate().scaleX(1.12f).scaleY(1.12f).setDuration(140)
                    .withEndAction {
                        button.animate().scaleX(1f).scaleY(1f).setDuration(180)
                            .setInterpolator(DecelerateInterpolator()).start()
                    }.start()
            }
        }
        panelRoot?.background = HGPGGlassUI.background(selected, cornerScale = 0.06f, panelSurface = true, animatedUnderlay = true)
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
            background = HGPGGlassUI.background(theme, cornerScale = 0.06f, panelSurface = true, animatedUnderlay = true)
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
        addText(options, "Useful tools, local data controls, and visual settings.", 13f, theme.muted, false, 0, 14)
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
        addCard(options, "🫧  Glass & Display Studio", "Morph Glass, Liquid Glass, 3D UI, and four display colour profiles.", theme) {
            dismissPanelAnimated { HGPGGlassUI.showSettingsDialog(activity, settings, theme) }
        }
        addCard(options, "🌐  Community Shortcuts", "Open verified Indonesia Furs community pages.", theme) {
            dismissPanelAnimated { showCommunity(activity) }
        }
        addCard(options, "🧰  Local Data & Controls", "Back up, restore, and manage local HG PG data.", theme) {
            dismissPanelAnimated { showDataControls(activity, settings) }
        }
        addCard(options, "🔒  Privacy & About", "Review privacy details and manage local data.", theme) {
            dismissPanelAnimated {
                AlertDialog.Builder(activity).setTitle("HG PG · Privacy & About")
                    .setMessage("HG PG does not scrape Discord messages, members, channels, or servers, send telemetry, or automate Discord user actions. Notes and preferences are stored in Aliucord plugin settings on this device.\n\nThe optional reachability check runs only when you tap it and sends one HTTPS request to Google's connectivity-check endpoint; it does not transmit your note, profile, or Discord data. Backup export copies a JSON file to your clipboard only when requested; HG PG does not upload it.\n\nThe floating button lives inside Aliucord's own view tree and does not request Android's draw-over-other-apps permission.")
                    .setPositiveButton("Manage local data") { _, _ -> showDataControls(activity, settings) }
                    .setNegativeButton("Close", null).show()
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
        HGPGUIAnimations.fadeInTextTree(root, staggerMs = 16L)
        current.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.55f)
            val width = minOf(activity.resources.displayMetrics.widthPixels - dp(activity, 24), dp(activity, 520)).coerceAtLeast(dp(activity, 260))
            val height = minOf(activity.resources.displayMetrics.heightPixels - dp(activity, 80), dp(activity, 760)).coerceAtLeast(dp(activity, 380))
            setLayout(width, height)
            setGravity(Gravity.CENTER)
            // The panel is fully painted by HG PG; no OS blur dependency is used.
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.42f)
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
        val theme = HGPGThemes.get(realString(settings.getString("theme_profile", "garuda")))
        val field = EditText(activity).apply {
            hint = "Write a private local note…"
            setTextColor(theme.text)
            setHintTextColor(theme.muted)
            setText(realString(settings.getString("local_note", "")))
            background = HGPGGlassUI.background(theme, cornerScale = 0.10f, panelSurface = false)
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 4; maxLines = 8; gravity = Gravity.TOP
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 8), dp(activity, 18), dp(activity, 8))
            background = HGPGGlassUI.background(theme, cornerScale = 0.06f, panelSurface = true)
            addView(field, LinearLayout.LayoutParams(-1, dp(activity, 150)))
        }
        val dialog = AlertDialog.Builder(activity).setTitle("HG PG · Private Notes").setView(box)
            .setPositiveButton("Save", null)
            .setNeutralButton("Clear", null)
            .setNegativeButton("Close", null).create()
        dialog.setOnShowListener {
            HGPGGlassUI.styleDialog(dialog, activity, theme)
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
        val dialog = AlertDialog.Builder(activity).setTitle("Network Diagnostics")
            .setMessage(networkSummary(activity) + "\n\nStatus is read from Android. Tap Test reachability to make one optional HTTPS request.")
            .setPositiveButton("Refresh status", null)
            .setNeutralButton("Test reachability", null)
            .setNegativeButton("Close", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.setMessage(networkSummary(activity) + "\n\nStatus is read from Android. Tap Test reachability to make one optional HTTPS request.")
            }
            val testButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            testButton.setOnClickListener {
                testButton.isEnabled = false
                dialog.setMessage(networkSummary(activity) + "\n\nChecking HTTPS reachability…")
                Thread({
                    val startedAt = System.currentTimeMillis()
                    val result = try {
                        val connection = java.net.URL("https://connectivitycheck.gstatic.com/generate_204")
                            .openConnection() as java.net.HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.requestMethod = "GET"
                        connection.useCaches = false
                        try {
                            connection.connect()
                            val code = connection.responseCode
                            val elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                            if (code == 204) {
                                "HTTPS reachability: success (HTTP 204, $elapsedMs ms)."
                            } else {
                                "HTTPS endpoint responded with HTTP $code ($elapsedMs ms)."
                            }
                        } finally {
                            connection.disconnect()
                        }
                    } catch (error: Throwable) {
                        val elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                        "HTTPS reachability failed ($elapsedMs ms): " +
                            (error.localizedMessage?.take(140) ?: error.javaClass.simpleName)
                    }
                    activity.runOnUiThread {
                        if (dialog.isShowing && !activity.isFinishing && !activity.isDestroyed) {
                            dialog.setMessage(networkSummary(activity) +
                                "\n\n$result\n\nThis is a reachability check, not a speed test. No plugin data is sent.")
                            testButton.isEnabled = true
                        }
                    }
                }, "HGPG-network-check").apply {
                    isDaemon = true
                    start()
                }
            }
        }
        dialog.show()
    }

    private fun showCommunity(activity: Activity) {
        AlertDialog.Builder(activity).setTitle("Community Shortcuts")
            .setMessage("Open public pages that can be verified. Anthro Indonesia remains listed as a community reference; this build does not guess a separate URL for it.")
            .setItems(arrayOf(
                "Indonesia Furs — official website",
                "Indonesia Furs — official links",
                "Indonesia Furs Community Platform",
                "IWAG — official event information"
            )) { _, which ->
                val target = when (which) {
                    0 -> "https://furries.id/id/"
                    1 -> "https://furries.id/id/links/"
                    2 -> "https://community.furries.id/id/"
                    else -> "https://iwag.furries.id/id/about/"
                }
                openWebPage(activity, target)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openWebPage(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
            AlertDialog.Builder(activity).setTitle("Unable to open link")
                .setMessage("No browser or compatible app could open this verified URL:\n$url")
                .setPositiveButton("Close", null).show()
        }
    }

    private fun showDataControls(activity: Activity, settings: SettingsAPI) {
        AlertDialog.Builder(activity).setTitle("Local Data & Controls")
            .setMessage("Backups contain your private note and HG PG preferences. Export copies JSON to the clipboard; import reads only clipboard text and never uploads it.")
            .setItems(arrayOf(
                "Export local backup to clipboard",
                "Import backup from clipboard",
                "Reset floating button position",
                "Clear saved private note"
            )) { _, which ->
                when (which) {
                    0 -> exportLocalBackup(activity, settings)
                    1 -> importLocalBackup(activity, settings)
                    2 -> resetLauncherPosition(activity, settings)
                    3 -> AlertDialog.Builder(activity).setTitle("Clear private note?")
                        .setMessage("This permanently clears the saved local note from HG PG settings on this device.")
                        .setPositiveButton("Clear note") { _, _ ->
                            settings.setString("local_note", "")
                            toast(activity, "Saved private note cleared.")
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun exportLocalBackup(activity: Activity, settings: SettingsAPI) {
        val backup = JSONObject()
        try {
            backup.put("format", "hgpg-local-backup")
            backup.put("version", 1)
            backup.put("local_note", realString(settings.getString("local_note", "")))
            backup.put("theme_profile", realString(settings.getString("theme_profile", "garuda")))
            backup.put("ambient_light_enabled", settings.getBool("ambient_light_enabled", true))
            backup.put("ambient_light_intensity", settings.getInt("ambient_light_intensity", 72).coerceIn(0, 100))
            backup.put("launcher_enabled", settings.getBool("launcher_enabled", true))
            backup.put("launcher_x_percent", settings.getInt("launcher_x_percent", -1))
            backup.put("launcher_y_percent", settings.getInt("launcher_y_percent", -1))
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null) {
                toast(activity, "Clipboard service is unavailable.")
                return
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("HG PG local backup", backup.toString(2)))
            toast(activity, "Local backup copied to clipboard. Keep it private.")
        } catch (_: Throwable) {
            toast(activity, "Could not export the local backup.")
        }
    }

    private fun importLocalBackup(activity: Activity, settings: SettingsAPI) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val raw = try {
            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.toString()
        } catch (_: Throwable) {
            null
        }
        if (raw.isNullOrBlank()) {
            toast(activity, "Clipboard is empty; copy a HG PG backup JSON first.")
            return
        }

        try {
            val backup = JSONObject(raw)
            if (backup.optString("format") != "hgpg-local-backup" || backup.optInt("version", -1) != 1) {
                throw IllegalArgumentException("This is not a supported HG PG backup.")
            }
            val note = backup.optString("local_note", realString(settings.getString("local_note", "")))
            if (note.length > MAX_NOTE) throw IllegalArgumentException("The backup note is longer than 1,500 characters.")
            val themeId = backup.optString("theme_profile", realString(settings.getString("theme_profile", "garuda")))
            if (HGPGThemes.all.none { it.id == themeId }) throw IllegalArgumentException("The backup contains an unknown theme.")
            val intensity = backup.optInt("ambient_light_intensity", settings.getInt("ambient_light_intensity", 72))
            if (intensity !in 0..100) throw IllegalArgumentException("Ambient Light intensity must be between 0 and 100.")
            val x = backup.optInt("launcher_x_percent", settings.getInt("launcher_x_percent", -1))
            val y = backup.optInt("launcher_y_percent", settings.getInt("launcher_y_percent", -1))
            if (!(x == -1 || x in 0..10000) || !(y == -1 || y in 0..10000)) {
                throw IllegalArgumentException("The backup contains an invalid launcher position.")
            }
            val ambientEnabled = backup.optBoolean("ambient_light_enabled", settings.getBool("ambient_light_enabled", true))
            val launcherEnabled = backup.optBoolean("launcher_enabled", settings.getBool("launcher_enabled", true))

            AlertDialog.Builder(activity).setTitle("Import HG PG backup?")
                .setMessage("This will replace the saved note, theme, Ambient Light settings, and floating-button preferences on this device. Only continue if you trust the clipboard contents.")
                .setPositiveButton("Import backup") { _, _ ->
                    settings.setString("local_note", note)
                    settings.setString("theme_profile", themeId)
                    settings.setBool("ambient_light_enabled", ambientEnabled)
                    settings.setInt("ambient_light_intensity", intensity)
                    settings.setBool("launcher_enabled", launcherEnabled)
                    settings.setInt("launcher_x_percent", x)
                    settings.setInt("launcher_y_percent", y)

                    val theme = HGPGThemes.get(themeId)
                    HGPGGlobalThemeEngine.apply(theme)
                    HGPGMotionEngine.setTheme(theme)
                    HGPGGlobalMotionOverlay.setTheme(theme)
                    HGPGGlobalMotionOverlay.setAmbientEnabled(ambientEnabled)
                    HGPGGlobalMotionOverlay.setAmbientIntensity(intensity)
                    HGPGInAppBubble.setTheme(theme)
                    HGPGHighTechRenderer.setTheme(theme)
                    refreshTheme(settings)
                    if (launcherEnabled) attach(activity, settings) else setLauncherEnabled(activity, settings, false)
                    activity.window?.decorView?.postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) HGPGGlobalThemeEngine.refreshActivity(activity)
                    }, 250L)
                    toast(activity, "HG PG backup imported.")
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (error: Throwable) {
            val message = if (error is IllegalArgumentException) {
                error.message ?: "The backup is invalid."
            } else {
                "Clipboard text is not valid HG PG backup JSON."
            }
            AlertDialog.Builder(activity).setTitle("Backup import failed")
                .setMessage(message + "\n\nNo HG PG settings were changed.")
                .setPositiveButton("Close", null).show()
        }
    }

    private fun showThemes(activity: Activity, settings: SettingsAPI) {
        if (activity.isFinishing || activity.isDestroyed) return
        val active = HGPGThemes.get(realString(settings.getString("theme_profile", "garuda")))
        val scroll = ScrollView(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 12))
            background = HGPGGlassUI.background(active, cornerScale = 0.045f, panelSurface = true, animatedUnderlay = true)
        }
        content.addView(TextView(activity).apply {
            text = "ANIMATED THEME GARAGE"
            textSize = 12f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(active.accent)
            setPadding(dp(activity, 2), dp(activity, 4), dp(activity, 2), dp(activity, 4))
        })
        content.addView(TextView(activity).apply {
            text = "Pick a visual system. Each card previews its own HG PG palette and motion identity."
            textSize = 13f
            setTextColor(active.muted)
            setPadding(dp(activity, 2), 0, dp(activity, 2), dp(activity, 12))
        })
        val dialog = AlertDialog.Builder(activity)
            .setTitle("HG PG · Animated Theme Garage")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .create()
        HGPGThemes.all.forEach { theme ->
            val selected = theme.id == active.id
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(activity, 14), dp(activity, 11), dp(activity, 14), dp(activity, 11))
                background = HGPGGlassUI.background(theme, cornerScale = 0.14f, panelSurface = false)
                isClickable = true
                isFocusable = true
                contentDescription = "Apply ${theme.name} theme"
                setOnClickListener {
                    settings.setString("theme_profile", theme.id)
                    HGPGGlobalThemeEngine.apply(theme)
                    HGPGMotionEngine.setTheme(theme)
                    HGPGGlobalMotionOverlay.setTheme(theme)
                    HGPGInAppBubble.setTheme(theme)
                    HGPGHighTechRenderer.setTheme(theme)
                    refreshTheme(settings)
                    dialog.dismiss()
                    toast(activity, "Theme applied: " + theme.name)
                    activity.window?.decorView?.postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            HGPGGlobalThemeEngine.refreshActivity(activity)
                        }
                    }, 250L)
                }
            }
            card.addView(TextView(activity).apply {
                text = (if (selected) "✓  ACTIVE  ·  " else "◈  APPLY  ·  ") + theme.name
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(theme.text)
            })
            card.addView(TextView(activity).apply {
                text = theme.subtitle
                textSize = 12f
                setTextColor(theme.muted)
                setPadding(0, dp(activity, 4), 0, 0)
            })
            content.addView(card, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(activity, 9)
            })
            card.alpha = 0f
            card.translationY = dp(activity, 7).toFloat()
            card.postDelayed({
                if (card.isAttachedToWindow) {
                    card.animate().alpha(1f).translationY(0f).setDuration(210L)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
            }, content.childCount * 32L)
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        dialog.setOnShowListener { HGPGGlassUI.styleDialog(dialog, activity, active) }
        dialog.show()
        dialog.window?.setLayout(
            minOf(activity.resources.displayMetrics.widthPixels - dp(activity, 24), dp(activity, 520)).coerceAtLeast(dp(activity, 260)),
            minOf(activity.resources.displayMetrics.heightPixels - dp(activity, 80), dp(activity, 760)).coerceAtLeast(dp(activity, 380))
        )
    }

    private fun addCard(parent: LinearLayout, title: String, description: String, theme: HGPGTheme, click: () -> Unit) {
        val ctx = parent.context
        val tile = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            background = HGPGGlassUI.background(theme, cornerScale = 0.16f, panelSurface = true)
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

    private fun toast(activity: Activity, message: String) =
        HGPGInAppBubble.show(activity, "HG PG · Notification", message)

    private fun accent(settings: SettingsAPI): Int = HGPGThemes.get(realString(settings.getString("theme_profile", "garuda"))).accent

    private fun shape(ctx: Context, color: Int, radius: Float, stroke: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            cornerRadius = radius * ctx.resources.displayMetrics.density
            setColor(color)
            if (stroke != Color.TRANSPARENT) setStroke(dp(ctx, 1), stroke)
        }

    private fun dp(ctx: Context, value: Int) = (value * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
