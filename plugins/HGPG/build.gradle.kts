version = "0.13.0"
description = "HyperGaruda Plugin toolkit for Aliucord"

aliucord {
    changelog.set(
        """
        # 0.13.0
        * Added animated Morph/Liquid Glass highlights that follow the active theme palette.
        * Added subtle Android 12+ native motion blur to the non-interactive decorative motion layer, with automatic compatibility fallback.
        * Kept ambient edge lighting theme-driven and isolated blur from Discord controls.

        # 0.12.0
        * Replaced Android window blur dependency with fully HG PG-rendered Morph Glass, Liquid Glass, and 3D UI materials.
        * Fixed the animated overlay being moved above Discord controls when an activity resumed.
        * Reduced material opacity and corrected gradient bounds so glass surfaces remain readable and visually distinct.
        * Restyled Private Notes and dialog controls with plugin-rendered materials.
        * Rebuilt Animated Theme Garage as a directly selectable, animated in-plugin theme gallery.
        * Applied HG PG materials consistently to theme and settings controls.

        # 0.11.0
        * Added Morph Glass, Liquid Glass, and 3D UI materials for HG PG-owned surfaces and controls.
        * Added four display colour presets: IPS, AMOLED, AMOLED+, and Super AMOLED.
        * Enabled Android 12+ background blur for Liquid Glass when the device reports support, with a readable non-blur fallback.
        * Added Glass & Display Studio to the floating panel and plugin settings.
        * Kept glass material and display colour tuning independent from the six existing animated colour themes.

        # 0.10.0
        * Added launcher enable/disable control and a reset-position action in HG PG settings.
        * Added validated local JSON backup export/import through the clipboard, with overwrite confirmation and range/theme validation.
        * Added an optional asynchronous HTTPS reachability test to Network Diagnostics with timeout handling.
        * Connected Community Shortcuts to verified public Indonesia Furs and IWAG pages; avoids fabricating an unverified Anthro Indonesia URL.
        * Expanded Privacy & About messaging to disclose the optional network check and clipboard-only backups.

        # 0.9.0
        * Added theme-aware in-app notification bubbles with fade-and-slide entry, timed fade-out, and tap-to-dismiss behavior.
        * Added staggered fade-in text transitions to HG PG-owned panel and settings UI without animating input fields or Discord chat text.
        * Animated the Ambient Light slider entrance, value feedback, touch tracking, and saved-value notification.
        * Unified UI feedback with native Android view animations and cleaned up active bubbles when HG PG stops.

        # 0.8.2
        * Added theme-matched ambient edge lighting with softly moving radial glows and a breathing light pulse.
        * Added persistent Ambient Light toggle and 0–100% intensity control in HG PG settings.
        * Fixed the animation ticker so it resumes after window or view visibility returns, rather than silently stopping.

        # 0.8.1
        * Added a non-interactive native Canvas motion overlay above Aliucord content so animation is visible even when Discord child views have opaque backgrounds.
        * Added theme-aware ambient effects for all six built-in profiles, automatic activity reattachment, touch pass-through, and lifecycle cleanup.

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
