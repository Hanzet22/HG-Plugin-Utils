# HG PG — HyperGaruda Plugin

A privacy-conscious Aliucord utility plugin, built with Kotlin and compiled through GitHub Actions.

## Current version: 0.3.1 — Draggable Animated Floating Panel

HG PG now uses an **in-app floating panel instead of slash commands**. Tap the round **HG** launcher anywhere you place it in Aliucord to open it. Drag the launcher with your finger to move it; its normalized position is saved in plugin settings and restored later.

### Panel tools

- **Private Notes** — save, edit, and clear a local note in Aliucord plugin settings.
- **Fursona Creator** — generate a simple text card from details you provide and copy it to the clipboard.
- **Network Diagnostics** — report Android's current network transport and validated-internet status. This is not a speed test or latency measurement.
- **Panel Theme** — choose a Garuda, Midnight, Forest, or Aurora accent for the HG PG launcher. This does not recolor Discord's entire interface.
- **Community Shortcuts** — shows Indonesia Furs and Anthro Indonesia as community names and recommends using verified official pages or invites.
- **Privacy & About** — explains the plugin's local-first behavior.

The launcher appears above Aliucord's normal content and remains available while you navigate the app. It has entrance/press feedback; the panel scales and fades in/out; tiles animate on interaction. The launcher is inserted into Aliucord's own content view. It does **not** request Android's “draw over other apps” permission. Restart Aliucord after installing or updating version 0.3.1 so the activity hook is active.

## Privacy and safety

HG PG does not scrape messages, members, channels, or servers; does not send telemetry; and does not automate Discord user actions. Notes and theme-profile preferences are stored in Aliucord's plugin settings on the device. Network diagnostics only read Android's local connectivity state. Fursona cards are generated on-device.

## Build

The Build HG PG GitHub Actions workflow runs on pushes to main and manual dispatch. It builds with JDK 21 and Gradle 9.3.1, then uploads the plugin ZIP as a workflow artifact. Open Actions, choose Build HG PG, open the latest run, then download the artifact.

A successful commit is not proof of a successful build: check the workflow result before installing anything.

## License

GNU Affero General Public License v3.0 or later. See LICENSE.
