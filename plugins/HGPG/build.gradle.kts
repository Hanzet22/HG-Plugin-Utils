version = "0.8.0"
description = "HyperGaruda Plugin toolkit for Aliucord"

aliucord {
    changelog.set(
        """
        # 0.8.0
        * Added a native animated background across the actual Aliucord screen root, with six theme-specific moving backdrops.
        * Made screen transitions replay on activity resume and dialog show rather than stopping after the first visit.
        * Strengthened tap/press/release feedback and cleared entrance tracking as views detach during navigation.
        * Kept animation rendering native to Android View/Drawable APIs; CSS effects remain limited to WebView content.

        # 0.7.0
        * Added WebView load transitions with blur-to-sharp rendering on Android 12+ and graceful fallback on older Android versions.
        * Added staggered character-level reveal for short non-input TextView updates, preserving existing spans and excluding editors and numeric counters.
        * Added transient hardware-layer acceleration for animated views and native render effects where supported.
        * Kept the app-wide color and motion engines and six theme-specific animation styles.

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
