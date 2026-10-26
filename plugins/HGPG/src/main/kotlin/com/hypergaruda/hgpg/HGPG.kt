package com.hypergaruda.hgpg

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.discord.api.commands.ApplicationCommandType

@AliucordPlugin(requiresRestart = false)
@Suppress("unused")
class HGPG : Plugin() {
    override fun start(context: Context) {
        settingsTab = com.aliucord.entities.Plugin.SettingsTab(HGPGSettingsPage::class.java)
            .withArgs(settings)

        commands.registerCommand("hgpg-status", "Show HG PG module status") {
            CommandsAPI.CommandResult(
                "**HG PG · HyperGaruda Plugin**\n" +
                    "• Local utility notes: available\n" +
                    "• Fursona card formatter: available\n" +
                    "• Network status diagnostics: available\n" +
                    "• Theme profile manager: available (profile only; full Discord recoloring is not enabled)\n" +
                    "• Community shortcuts: available\n" +
                    "Privacy: no message/member/channel/server scraping and no telemetry.",
            )
        }

        commands.registerCommand("hgpg-help", "List HG PG commands and privacy notes") {
            CommandsAPI.CommandResult(
                "**HG PG · Command Guide**\n" +
                    "• /hgpg-status — show module status.\n" +
                    "• /hgpg-note — save, view, or clear a local note.\n" +
                    "• /hgpg-fursona — format a fursona text card.\n" +
                    "• /hgpg-network — inspect Android network status.\n" +
                    "• /hgpg-theme — view or save a theme profile.\n" +
                    "• /hgpg-community — show community shortcut guidance.\n\n" +
                    "Notes stay in local plugin settings. HG PG does not scrape Discord content or send telemetry.",
            )
        }

        commands.registerCommand(
            "hgpg-note",
            "Save, view, or clear your private local note",
            listOf(
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "text",
                    "Text to save, or type clear to remove the saved note",
                ),
            ),
        ) { ctx ->
            val text = toRealString(ctx.getStringOrDefault("text", "")).trim()
            when {
                text.equals("clear", ignoreCase = true) -> {
                    settings.setString("local_note", "")
                    CommandsAPI.CommandResult("HG PG: saved local note cleared.")
                }
                text.length > 0 -> {
                    val saved = if (text.length > MAX_NOTE_LENGTH) text.substring(0, MAX_NOTE_LENGTH) else text
                    settings.setString("local_note", saved)
                    CommandsAPI.CommandResult("HG PG: note saved locally (${saved.length} characters).")
                }
                else -> {
                    val note = toRealString(settings.getString("local_note", ""))
                    CommandsAPI.CommandResult(
                        if (isBlankCompat(note)) "HG PG: no local note saved yet."
                        else "**Your saved HG PG note**\n${escapeDiscordText(note)}",
                    )
                }
            }
        }

        commands.registerCommand(
            "hgpg-fursona",
            "Format a fursona card from details you provide",
            listOf(
                Utils.createCommandOption(ApplicationCommandType.STRING, "name", "Fursona name"),
                Utils.createCommandOption(ApplicationCommandType.STRING, "species", "Species or character type"),
                Utils.createCommandOption(ApplicationCommandType.STRING, "colors", "Main colors or palette"),
            ),
        ) { ctx ->
            val name = safeInlineText(ctx.getStringOrDefault("name", "Unnamed fursona"), 80, "Unnamed fursona")
            val species = safeInlineText(ctx.getStringOrDefault("species", "Not specified"), 100, "Not specified")
            val colors = safeInlineText(ctx.getStringOrDefault("colors", "Not specified"), 120, "Not specified")
            CommandsAPI.CommandResult(
                "**FURSONA CARD**\n" +
                    "**Name:** $name\n" +
                    "**Species:** $species\n" +
                    "**Palette:** $colors\n" +
                    "**Made with:** HG PG · HyperGaruda Plugin",
            )
        }

        commands.registerCommand("hgpg-network", "Show basic network connection diagnostics") {
            CommandsAPI.CommandResult(networkSummary(context))
        }

        commands.registerCommand(
            "hgpg-theme",
            "View or save a theme profile preference",
            listOf(
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "profile",
                    "Profile: garuda, midnight, forest, or aurora",
                ),
            ),
        ) { ctx ->
            val requested = toRealString(ctx.getStringOrDefault("profile", "")).trim().lowercase(java.util.Locale.ROOT)
            val currentProfile = toRealString(settings.getString("theme_profile", "garuda"))
            if (isBlankCompat(requested)) {
                CommandsAPI.CommandResult(
                    "Current HG PG theme profile: **$currentProfile**. " +
                        "Choose garuda, midnight, forest, or aurora. This saves a profile preference; it does not recolor Discord globally yet.",
                )
            } else if (
                requested != "garuda" &&
                requested != "midnight" &&
                requested != "forest" &&
                requested != "aurora"
            ) {
                CommandsAPI.CommandResult("Unknown profile. Choose: garuda, midnight, forest, aurora.")
            } else {
                settings.setString("theme_profile", requested)
                CommandsAPI.CommandResult(
                    "Saved theme profile: **$requested**. This is a preference preset; full Discord-wide recoloring is not enabled in this MVP.",
                )
            }
        }

        commands.registerCommand("hgpg-community", "Show furry community shortcut guidance") {
            CommandsAPI.CommandResult(
                "**HG PG · Community Shortcuts**\n" +
                    "• Indonesia Furs\n" +
                    "• Anthro Indonesia\n\n" +
                    "Open each community through its verified official page or invite. HG PG does not scrape communities or automatically join servers.",
            )
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }

    /**
     * Discord/Aliucord may return an obfuscated CharSequence wrapper (for example d0.d0.b)
     * from APIs whose declared return type is String. Convert it via reflective CharSequence
     * methods before calling Kotlin string extensions, which can crash with IntIterator casts.
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
                val toStringMethod = Any::class.java.getMethod("toString")
                val rendered = toStringMethod.invoke(value)
                val bytes = java.io.ByteArrayOutputStream()
                java.io.PrintStream(bytes).use { stream -> stream.print(rendered) }
                bytes.toString("UTF-8")
            } catch (_: Throwable) {
                ""
            }
        }
    }

    private fun isBlankCompat(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            if (!Character.isWhitespace(value[index])) return false
            index += 1
        }
        return true
    }

    private fun safeInlineText(value: Any?, maxLength: Int, fallback: String): String {
        val concrete = toRealString(value)
        val normalized = concrete.replace(Regex("""\s+"""), " ").trim()
        val limited = if (normalized.length > maxLength) normalized.substring(0, maxLength) else normalized
        return escapeDiscordText(if (isBlankCompat(limited)) fallback else limited)
    }

    private fun escapeDiscordText(value: String): String = buildString(value.length) {
        for (character in value) {
            when (character) {
                '@' -> append("@\u200B")
                '\\' -> append("\\\\")
                '*' -> append("\\*")
                '_' -> append("\\_")
                '~' -> append("\\~")
                '|' -> append("\\|")
                '[' -> append("\\[")
                ']' -> append("\\]")
                '(' -> append("\\(")
                ')' -> append("\\)")
                '<' -> append("\\<")
                '>' -> append("\\>")
                else -> if (character.code == 96) append("\\u0060") else append(character)
            }
        }
    }

    private fun networkSummary(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "HG PG: Android did not provide a ConnectivityManager."

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val info = manager.activeNetworkInfo
            return if (info?.isConnected == true) {
                "HG PG network status: connected via ${info.typeName}. Android API is too old for validated-internet capability reporting."
            } else {
                "HG PG network status: no active connection detected."
            }
        }

        val network = manager.activeNetwork
            ?: return "HG PG network status: no active network detected."
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return "HG PG network status: network capabilities are unavailable."

        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN (underlying transport may be hidden)"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular/mobile data"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "other/unknown transport"
        }
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val internet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        return buildString {
            appendLine("**HG PG · Network Diagnostics**")
            appendLine("Transport: $transport")
            appendLine("Android reports internet capability: ${if (internet) "yes" else "no"}")
            appendLine("Android reports validated internet access: ${if (validated) "yes" else "no"}")
            append("Note: this is a local status check, not a speed test or measured latency.")
        }
    }

    private companion object {
        const val MAX_NOTE_LENGTH = 1500
    }
}
