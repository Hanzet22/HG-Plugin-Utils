# HG PG — HyperGaruda Plugin

A privacy-conscious Aliucord utility plugin, built with Kotlin and compiled through GitHub Actions.

## Current plugin version: 0.5.0 — Native App Theme Garage

HG PG includes six custom animated themes and a **built-in app-wide resource color engine**. Pick a theme from HG PG's floating panel or plugin settings; it applies supported Aliucord/Discord color resources, theme attributes, and selected drawable tints directly from HG PG.

**No separate Themer plugin, external theme plugin, or theme JSON import is required.** Because some colors are hard-coded or belong to third-party components, a few UI elements may remain unchanged. The plugin preserves its animated visual effects in the HG PG launcher and panel.

The six built-in themes are:

1. Deep Sea Blue
2. Garuda Indonesia
3. Light Emitting Orange
4. Gran Turismo Sport
5. Gran Turismo Old
7. CRT PS2

HG PG uses an **in-app floating panel instead of slash commands**. Tap the round **HG** launcher anywhere you place it in Aliucord to open it. Drag the launcher with your finger to move it; its normalized position is saved in plugin settings and restored later.

### Panel tools

- **Private Notes** — save, edit, and clear a local note.
- **Fursona Creator** — generate a simple text card from details you provide and copy it to the clipboard.
- **Network Diagnostics** — report Android's current network transport and validated-internet status. This is not a speed test or latency measurement.
- **Animated Theme Garage** — choose one of six built-in app-wide color themes and animated HG PG styles.
- **Community Shortcuts** — shows Indonesia Furs and Anthro Indonesia as community names and recommends using verified official pages or invites.
- **Privacy & About** — explains the plugin's local-first behavior.

The launcher appears above Aliucord's normal content and remains available while you navigate the app. It has entrance/press feedback; the panel scales and fades in/out; tiles animate on interaction. The launcher is inserted into Aliucord's own content view. It does **not** request Android's “draw over other apps” permission.

## Privacy and safety

HG PG does not scrape messages, members, channels, or servers; does not send telemetry; and does not automate Discord user actions. Notes and theme-profile preferences are stored in Aliucord plugin settings on the device. Network diagnostics only read Android's local connectivity state. Fursona cards are generated on-device.

## Build

The Build HG PG GitHub Actions workflow builds the plugin with JDK 21 and Gradle 9.3.1, then uploads the installable plugin ZIP as a workflow artifact. Open Actions, choose Build HG PG, open the latest successful run, then download **hg-pg-plugin**.

A successful commit is not proof of a successful build: check the workflow result before installing anything.

## License

GNU Affero General Public License v3.0 or later. See LICENSE.
