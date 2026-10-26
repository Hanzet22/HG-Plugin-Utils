version = "0.5.0"
description = "HyperGaruda Plugin toolkit for Aliucord"

aliucord {
    changelog.set(
        """
        # 0.5.0
        * Integrated a built-in app-wide color theme engine into HG PG; no separate Themer plugin or external theme JSON is required.
        * Theme selection now updates supported Aliucord/Discord resource colors, selected drawable tints, and theme attributes.
        * Preserved the six animated palette systems for the HG PG launcher and panel.

        # 0.4.0
        * Added six custom animated themes: Deep Sea Blue, Garuda Indonesia, Light Emitting Orange, Gran Turismo Sport, Gran Turismo Old, and CRT PS2.
        * Added moving theme backdrops, palette-aware panel cards, staggered tile entrances, and animated launcher theme transitions.
        * Added theme selection to both the floating panel and plugin settings page.

        # 0.3.2
        * Reattach the floating launcher from Android Activity lifecycle callbacks whenever an Aliucord activity resumes.
        * Fix launcher visibility when navigating between screens or when the host Activity is recreated.
        * Preserve draggable positioning and panel animations from 0.3.1.

        # 0.3.1
        * Added drag-and-drop positioning for the floating HG launcher.
        * Persisted launcher position as a normalized screen-relative location in plugin settings.
        * Added button entrance/press feedback and animated panel opening, closing, and tile interaction.
        * Kept the launcher within Aliucord's own view tree; no draw-over-other-apps permission is requested.

        # 0.3.0
        * Replaced slash commands with an in-app floating HG PG control panel.
        * Added panel actions for local notes, fursona cards, network diagnostics, accent presets, community guidance, and privacy.
        * The launcher is attached inside Aliucord's own view and does not request draw-over-other-apps permission.

        # 0.2.2
        * Fixed d0.d0.b / IntIterator command crashes by converting Aliucord-provided text to a real String before processing.
        * Replaced vulnerable blank checks and multiline string trimming with compatibility-safe implementations.

        # 0.2.1
        * Fixed a crash when opening HG PG settings by initializing the base SettingsPage first.

        # 0.2.0
        * Added an in-plugin command guide.
        * Escaped user-provided display text to neutralize mentions and markdown.
        * Improved network transport reporting with VPN detection.

        # 0.1.0
        * Initial privacy-conscious toolkit MVP.
        * Added local notes, fursona card formatting, network status, and theme profiles.
        """.trimIndent(),
    )
    deploy.set(false)
}
