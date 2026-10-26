# HG PG — HyperGaruda Plugin

A privacy-conscious Aliucord plugin toolkit, built with Kotlin and compiled through GitHub Actions.

## Current MVP

- **Local utility notes:** save, view, and clear a private note in Aliucord plugin settings.
- **Fursona card generator:** format a shareable text card from details supplied by the user. User-entered display text is escaped to neutralize mentions and Markdown formatting.
- **Network diagnostics:** report the device's current network transport (including VPN detection) and Android's validated-network status. This is a status check, not an internet speed test or a latency measurement.
- **Theme profiles:** save a named visual preference profile and preview it in the plugin settings page. This initial version does not recolor Discord's entire interface; full UI theme application requires a separately verified UI integration.
- **Community shortcuts:** show community names and a reminder to use verified official links. No scraping or automatic actions.

## Commands

- `/hgpg-help` — list commands and privacy notes.
- `/hgpg-status` — plugin overview.
- `/hgpg-note` — show the saved note; pass text to save it, or `clear` to remove it.
- `/hgpg-fursona` — create a text card using the provided name, species, and colors.
- `/hgpg-network` — inspect current network status.
- `/hgpg-theme` — view or set a theme profile (`garuda`, `midnight`, `forest`, `aurora`).
- `/hgpg-community` — show community shortcut guidance.

## Build

The `Build HG PG` GitHub Actions workflow runs on pushes to `main` and manual dispatch. It builds with JDK 21 and Gradle 9.3.1, then uploads the plugin ZIP as a workflow artifact. Open **Actions → Build HG PG → latest run → Artifacts** to download it.

A successful commit is not proof of a successful build: check the workflow result before installing anything.

## Privacy and safety

HG PG does not scrape messages, members, channels, or servers; does not send telemetry; and does not automate Discord user actions. Notes and theme-profile preferences are stored in Aliucord's plugin settings on the device.

## License

GNU Affero General Public License v3.0 or later. See [LICENSE](LICENSE).
