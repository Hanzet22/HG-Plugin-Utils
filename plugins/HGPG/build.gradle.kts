version = "0.3.0"
description = "HyperGaruda Plugin toolkit for Aliucord"

aliucord {
    changelog.set(
        """
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
