<p align="center">
  <img src="https://img.shields.io/github/v/release/U-Nyxx/Klynt?style=flat&color=6C63FF" alt="Release">
  <img src="https://img.shields.io/github/downloads/U-Nyxx/Klynt/total?style=flat&color=6C63FF" alt="Downloads">
  <img src="https://img.shields.io/badge/API-30%2B-00D4AA?style=flat" alt="API">
  <img src="https://img.shields.io/badge/LSPosed-102-FF6B35?style=flat" alt="LSPosed">
  <img src="https://img.shields.io/github/license/U-Nyxx/Klynt?style=flat&color=6C63FF" alt="License">
</p>

<h1 align="center">KLYNT</h1>

<p align="center">
  <b>Liquid Glass Floating UI for Android</b><br>
  <sub>Xposed/LSPosed module — iOS-inspired glass effect on 30+ apps</sub>
</p>

---

> KLYNT hooks into target apps and replaces their standard bottom navigation bar with a **floating, translucent Liquid Glass pill**. No privacy hooks. No feature modifications. Just UI.

## Screenshots

| Main | Apps | Glass Effect |
|:---:|:---:|:---:|
| ![Main](screenshots/shot1.png) | ![Apps](screenshots/shot2.png) | ![Glass](screenshots/shot3.png) |

## Features

- **Glass Engine** — Real-time backdrop blur (AGSL + RenderEffect + NEON)
- **Floating Pill** — Translucent navigation bar with adaptive tint
- **30+ Apps** — Telegram (20+ variants), WhatsApp, YouTube, Instagram, Twitter/X, TikTok, Reddit
- **SOC-Aware** — Detects Snapdragon/Dimensity/Exynos/Tensor at runtime
- **Per-App Toggle** — Enable/disable glass effect individually
- **Thermal Scaling** — Exynos devices auto-reduce intensity under load

## Supported Apps

| Ecosystem | Apps |
|-----------|------|
| **Telegram** | Official, Beta, Nekogram, Nagram, NekoX, Cherrygram, Forkgram, Octogram, Mercurygram, Nullgram, iMe, exteraGram, Telega, Yukigram, Plus, Turrit, Web |
| **WhatsApp** | WhatsApp, WhatsApp Business |
| **YouTube** | YouTube, YouTube Music |
| **Instagram** | Instagram |
| **Twitter/X** | Twitter/X |
| **TikTok** | TikTok, Trill |
| **Reddit** | Reddit |

> Telegram X and Threads — coming v1.1

## Requirements

- Android 11+ (API 30)
- LSPosed (root) or LSPatch (non-root)

## Installation

### Root — LSPosed

1. Download `klynt-v1.0.ArJk.apk` from [Releases](https://github.com/U-Nyxx/Klynt/releases/latest)
2. Install the APK
3. Open **LSPosed Manager** → **Modules**
4. Enable **KLYNT** → select target apps
5. Force stop target app → reopen → glass nav appears

### Non-Root — LSPatch

1. Download `klynt-v1.0.ArJk.apk` from [Releases](https://github.com/U-Nyxx/Klynt/releases/latest)
2. Patch target app with **LSPatch** (Inject loader dex)
3. Install patched APK
4. Open KLYNT → configure toggles

## Build

```bash
git clone https://github.com/U-Nyxx/Klynt.git
cd Klynt
./gradlew assembleDebug
```

Requires: JDK 17, Android SDK 35, Kotlin 2.0, Compose BOM 2024.02

## FAQ

<details>
<summary><b>Module not showing in LSPosed?</b></summary>

Ensure LSPosed API 102+. Force stop LSPosed Manager and reopen.
</details>

<details>
<summary><b>Glass effect not appearing?</b></summary>

Force stop target app and reopen. Check LSPosed scope includes the target app.
</details>

<details>
<summary><b>Play Protect warning?</b></summary>

Normal for side-loaded APKs. Tap "Install anyway" or disable Play Protect temporarily.
</details>

<details>
<summary><b>Device not supported?</b></summary>

KLYNT falls back to software rendering on unsupported SoCs. Performance may vary.
</details>

## Credits

- [QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android) — Glass blur engine
- [LSPosed](https://github.com/LSPosed/LSPosed) — Module framework
- [Material Design 3](https://m3.material.io/) — UI components

## License

MIT License — see [LICENSE](LICENSE)

---

<p align="center">
  Made on Earth by Humans
</p>
