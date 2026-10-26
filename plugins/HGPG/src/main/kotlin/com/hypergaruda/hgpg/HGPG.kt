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
                """
                **HG PG · HyperGaruda Plugin**
                • Local utility notes: available
                • Fursona card formatter: available
                • Network status diagnostics: available
                • Theme profile manager: available (profile only; full Discord recoloring is not enabled)
                • Community shortcuts: available
                Privacy: no message/member/channel/server scraping and no telemetry.
                """.trimIndent(),
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
            val text = ctx.getStringOrDefault("text", "").trim()
            when {
                text.equals("clear", ignoreCase = true) -> {
                    settings.setString("local_note", "")
                    CommandsAPI.CommandResult("HG PG: saved local note cleared.")
                }
                text.isNotEmpty() -> {
                    val saved = text.take(MAX_NOTE_LENGTH)
                    settings.setString("local_note", saved)
                    CommandsAPI.CommandResult("HG PG: note saved locally (${saved.length} characters).")
                }
                else -> {
                    val note = settings.getString("local_note", "")
                    CommandsAPI.CommandResult(
                        if (note.isBlank()) "HG PG: no local note saved yet."
                        else "**Your saved HG PG note**\n$note",
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
            val name = ctx.getStringOrDefault("name", "Unnamed fursona").take(80)
            val species = ctx.getStringOrDefault("species", "Not specified").take(100)
            val colors = ctx.getStringOrDefault("colors", "Not specified").take(120)
            CommandsAPI.CommandResult(
                """
                **FURSONA CARD**
                **Name:** $name
                **Species:** $species
                **Palette:** $colors
                **Made with:** HG PG · HyperGaruda Plugin
                """.trimIndent(),
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
            val requested = ctx.getStringOrDefault("profile", "").trim().lowercase()
            val allowed = setOf("garuda", "midnight", "forest", "aurora")
            if (requested.isBlank()) {
                CommandsAPI.CommandResult(
                    "Current HG PG theme profile: **${settings.getString("theme_profile", "garuda")}**. " +
                        "Choose garuda, midnight, forest, or aurora. This saves a profile preference; it does not recolor Discord globally yet.",
                )
            } else if (requested !in allowed) {
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
                """
                **HG PG · Community Shortcuts**
                • Indonesia Furs
                • Anthro Indonesia

                Open each community through its verified official page or invite. HG PG does not scrape communities or automatically join servers.
                """.trimIndent(),
            )
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
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
