<p align="center">
  <img src="https://img.shields.io/github/v/release/U-Nyxx/Klynt?style=flat&color=6C63FF" alt="Release">
  <img src="https://img.shields.io/github/downloads/U-Nyxx/Klynt/total?style=flat&color=6C63FF" alt="Downloads">
  <img src="https://img.shields.io/badge/API-30%2B-00D4AA?style=flat" alt="API">
  <img src="https://img.shields.io/badge/libxposed-101%2F102-FF6B35?style=flat" alt="libxposed">
  <img src="https://img.shields.io/github/license/U-Nyxx/Klynt?style=flat&color=6C63FF" alt="License">
</p>

<h1 align="center">KLYNT</h1>

<p align="center">
  <b>Liquid Glass Floating UI for Android</b><br>
  <sub>LSPosed module — iOS-inspired glass pill on Telegram + Twitter/X bottom bars</sub>
</p>

---

> KLYNT hooks into target apps and overlays their bottom navigation bar with a **floating, translucent Liquid Glass pill**. Visual-only: no traffic touched, no messages read, no API calls. No privacy hooks. No feature modifications. Just UI.

## Features

- **Glass Engine** — Real-time backdrop blur (AGSL + RenderEffect + NEON)
- **Floating Pill** — Translucent navigation pill with adaptive tint
- **Reactive Hooks** — Toggles apply without restarting the target app
- **SOC-Aware** — Detects Snapdragon/Dimensity/Exynos/Tensor at runtime (auto quality, hidden from UI)
- **Per-App Toggle** — Enable/disable glass effect individually
- **In-App Update** — Check GitHub releases and install from inside the manager

## Supported Apps

| Ecosystem | Apps |
|-----------|------|
| **Telegram** | Official, Beta, Web, Plus, Nekogram (PS + FOSS), NekoX, Nagram, NagramX, Cherrygram, Forkgram (+Beta/Classic), Turrit, Octogram, Mercurygram, Nullgram, iMe (+Web), exteraGram, Telega, Yukigram, Nicegram, TGConnect |
| **Twitter/X** | Twitter/X |

> Root required (LSPosed + libxposed API 101+). Non-root (LSPatch) is not supported yet.

## Requirements

- Android 11+ (API 30)
- Root + LSPosed with libxposed API 101 or newer
- `api101` APK works on 101 and 102 frameworks; `api102` APK needs a 102 framework (hot-reload)

## Installation

1. Download `klynt-<ver>-api101-ArJk.apk` (any API 101+ framework) or `klynt-<ver>-api102-ArJk.apk` (newest LSPosed, hot-reload) from [Releases](https://github.com/U-Nyxx/Klynt/releases/latest)
2. Install the APK
3. Open **LSPosed Manager** → **Modules**
4. Enable **KLYNT** → tick target apps (or tap “Aktifkan scope” inside the manager)
5. Force stop target app → reopen → glass nav appears

## Build

```bash
git clone https://github.com/U-Nyxx/Klynt.git
cd Klynt
./gradlew assembleDebug
```

Requires: JDK 17, Android SDK 36, Kotlin 2.2, Compose BOM 2024.08

## FAQ

<details>
<summary><b>Module not showing in LSPosed?</b></summary>

Needs LSPosed with libxposed API 101+. Update LSPosed, then force stop the manager and reopen.
</details>

<details>
<summary><b>Glass effect not appearing?</b></summary>

Check Home checklist in the manager: framework connected, target in scope, target installed. Then force stop the target app and reopen. Copy diagnostics from Settings if reporting a bug.
</details>

<details>
<summary><b>Play Protect warning?</b></summary>

Normal for side-loaded APKs. Tap "Install anyway" or disable Play Protect temporarily.
</details>

<details>
<summary><b>Device not supported?</b></summary>

KLYNT falls back to frosted blur on weaker SoCs. Performance may vary.
</details>

## Credits

- [QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android) — Glass blur engine
- [LSPosed](https://github.com/LSPosed/LSPosed) + [libxposed](https://github.com/libxposed/api) — Module framework
- [Material Design 3](https://m3.material.io/) — UI components

## License

MIT License — see [LICENSE](LICENSE)

---

<p align="center">
  Made on Earth by Humans
</p>
