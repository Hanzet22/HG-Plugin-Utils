# HG PG — HyperGaruda Plugin

A privacy-conscious Aliucord utility plugin, built with Kotlin and compiled through GitHub Actions.

## Current plugin version: 0.7.0 — High-Tech UI Rendering

HG PG includes six custom animated themes, a **built-in app-wide resource color engine**, an **app-wide motion engine**, and a **high-tech rendering layer**. It adds theme-specific screen and interaction motion, WebView load transitions, character-by-character reveals for short non-input text updates, and temporary hardware-accelerated layers. On Android 12+, supported WebViews can use a brief blur-to-sharp RenderEffect transition.

**No separate Themer plugin, external theme plugin, or theme JSON import is required.** The motion engine animates supported native views as they attach, adds screen/dialog entrance transitions, and applies theme-specific press/release and click feedback without replacing existing listeners. The high-tech renderer adds WebView load transitions without injecting scripts into page content, plus staggered character reveals for short non-input text changes. Editors, frequently changing numeric counters, video/surface views, and HG PG's own custom UI are excluded to avoid interfering with typing, playback, or live values. Android 12+ blur effects are optional and fall back automatically when unsupported. Hardware layers are temporary and released after transitions.

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
- **Animated Theme Garage** — choose one of six built-in app-wide color themes and theme-specific motion styles, WebView transitions, and text reveals across supported app views.
- **Community Shortcuts** — shows Indonesia Furs and Anthro Indonesia as community names and recommends using verified official pages or invites.
- **Privacy & About** — explains the plugin's local-first behavior.

The launcher appears above Aliucord's normal content and remains available while you navigate the app. It has entrance/press feedback; the panel scales and fades in/out; tiles animate on interaction. The launcher is inserted into Aliucord's own content view. It does **not** request Android's “draw over other apps” permission.

## Privacy and safety

HG PG does not scrape messages, members, channels, or servers; does not send telemetry; and does not automate Discord user actions. Notes and theme-profile preferences are stored in Aliucord plugin settings on the device. Network diagnostics only read Android's local connectivity state. Fursona cards are generated on-device.

## Build

The Build HG PG GitHub Actions workflow builds the plugin with JDK 21 and Gradle 9.3.1, then uploads the installable plugin ZIP as a workflow artifact. Open Actions, choose Build HG PG, open the latest successful run, then download **hg-pg-plugin**.

The build workflow uploads a single installable plugin ZIP named **hg-pg-plugin**. A successful commit is not proof of a successful build: check the workflow result before installing anything.

## License

GNU Affero General Public License v3.0 or later. See LICENSE.
