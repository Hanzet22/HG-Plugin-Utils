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

    fun attach(activity: Activity, settings: SettingsAPI) {
        try {
            val host = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
            val previous = host.findViewWithTag<View>(TAG)
            if (previous != null) {
                launchers[activity] = previous as? TextView ?: return
                return
            }
            val launcher = TextView(activity).apply {
                tag = TAG
                text = "HG"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                contentDescription = "Open HG PG control panel"
                background = shape(activity, accent(settings), 100f, 0x99FFFFFF.toInt())
                elevation = dp(activity, 8).toFloat()
                isClickable = true
                isFocusable = true
                setOnClickListener { showPanel(activity, settings) }
            }
            val size = dp(activity, 54)
            host.addView(launcher, FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = dp(activity, 16)
                bottomMargin = dp(activity, 24)
            })
            launcher.bringToFront()
            launchers[activity] = launcher
        } catch (_: Throwable) {
            // Protect the host if a future Aliucord layout changes.
        }
    }

    fun dismiss() {
        try { panel?.dismiss() } catch (_: Throwable) {}
        panel = null
        launchers.forEach { (_, button) -> (button.parent as? ViewGroup)?.removeView(button) }
        launchers.clear()
    }

    private fun showPanel(activity: Activity, settings: SettingsAPI) {
        if (activity.isFinishing || activity.isDestroyed) return
        try { panel?.dismiss() } catch (_: Throwable) {}
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16))
            background = shape(activity, BG, 22f, 0xFF303B4B.toInt())
        }
        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(TextView(activity).apply {
            text = "HG PG"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(FG)
        })
        titleBox.addView(TextView(activity).apply {
            text = "HyperGaruda · Control Panel"
            textSize = 12f
            setTextColor(MUTED)
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(activity).apply {
            text = "×"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(FG)
            contentDescription = "Close panel"
            setPadding(dp(activity, 12), 0, dp(activity, 8), 0)
            setOnClickListener { panel?.dismiss() }
        })
        root.addView(header)
        root.addView(View(activity).apply { setBackgroundColor(0xFF303B4B.toInt()) },
            LinearLayout.LayoutParams(-1, dp(activity, 1)).apply {
                topMargin = dp(activity, 12); bottomMargin = dp(activity, 12)
            })
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val options = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(options, ScrollView.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addText(options, "Your utilities, one panel.", 18f, FG, true, 0, 4)
        addText(options, "No slash commands required. Choose a tool below.", 13f, MUTED, false, 0, 14)
        addCard(options, "📝  Private Notes", "Save or clear a note on this device.") {
            panel?.dismiss(); showNote(activity, settings)
        }
        addCard(options, "🐉  Fursona Creator", "Build a text card and copy it to clipboard.") {
            panel?.dismiss(); showFursona(activity)
        }
        addCard(options, "📶  Network Diagnostics", "Check Android connection and validated-internet state.") {
            panel?.dismiss(); showNetwork(activity)
        }
        addCard(options, "🎨  Panel Theme", "Choose the accent color for the HG launcher.") {
            panel?.dismiss(); showThemes(activity, settings)
        }
        addCard(options, "🌐  Community Shortcuts", "Indonesia Furs and Anthro Indonesia.") {
            panel?.dismiss()
            AlertDialog.Builder(activity).setTitle("Community Shortcuts")
                .setMessage("Indonesia Furs\nAnthro Indonesia\n\nUse each community's verified official page or invite. HG PG does not automatically join servers.")
                .setPositiveButton("Close", null).show()
        }
        addCard(options, "🔒  Privacy & About", "Local-first utilities, no telemetry.") {
            panel?.dismiss()
            AlertDialog.Builder(activity).setTitle("HG PG · Privacy")
                .setMessage("HG PG does not scrape Discord messages, members, channels, or servers. It does not send telemetry or automate Discord user actions. Notes and profile preferences stay in Aliucord plugin settings on this device. Network diagnostics read Android's local connection state. Fursona cards are generated on-device.\n\nThe floating button is part of Aliucord's own UI; no draw-over-other-apps permission is requested.")
                .setPositiveButton("Close", null).show()
        }
        val current = Dialog(activity)
        panel = current
        current.requestWindowFeature(Window.FEATURE_NO_TITLE)
        current.setContentView(root)
        current.setCanceledOnTouchOutside(true)
        current.setOnDismissListener { if (panel === current) panel = null }
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
        val names = arrayOf("Garuda Red", "Midnight Blue", "Forest Green", "Aurora Teal")
        val profiles = arrayOf("garuda", "midnight", "forest", "aurora")
        val dialog = AlertDialog.Builder(activity).setTitle("Panel Theme")
            .setItems(names) { d, which ->
                val profile = profiles[which]
                settings.setString("theme_profile", profile)
                launchers[activity]?.background = shape(activity, accent(settings), 100f, 0x99FFFFFF.toInt())
                toast(activity, "Panel accent saved: " + profile)
                d.dismiss()
            }.setNegativeButton("Close", null).create()
        dialog.setOnShowListener {
            dialog.setMessage("This changes the HG PG launcher accent only, not Discord's full interface.")
        }
        dialog.show()
    }

    private fun addCard(parent: LinearLayout, title: String, description: String, click: () -> Unit) {
        val ctx = parent.context
        val tile = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            background = shape(ctx, CARD, 15f)
            isClickable = true; isFocusable = true
            setOnClickListener { click() }
        }
        tile.addView(TextView(ctx).apply { text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(FG) })
        tile.addView(TextView(ctx).apply { text = description; textSize = 12f; setTextColor(MUTED); setPadding(0, dp(ctx, 4), 0, 0) })
        parent.addView(tile, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(ctx, 10) })
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

    private fun accent(settings: SettingsAPI): Int = when (realString(settings.getString("theme_profile", "garuda")).lowercase()) {
        "midnight" -> Color.rgb(101, 132, 255)
        "forest" -> Color.rgb(52, 173, 113)
        "aurora" -> Color.rgb(45, 183, 177)
        else -> Color.rgb(204, 34, 0)
    }

    private fun shape(ctx: Context, color: Int, radius: Float, stroke: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            cornerRadius = radius * ctx.resources.displayMetrics.density
            setColor(color)
            if (stroke != Color.TRANSPARENT) setStroke(dp(ctx, 1), stroke)
        }

    private fun dp(ctx: Context, value: Int) = (value * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
