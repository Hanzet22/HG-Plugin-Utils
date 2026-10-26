version = "0.2.1"
description = "HyperGaruda Plugin toolkit for Aliucord"

aliucord {
    changelog.set(
        """
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
