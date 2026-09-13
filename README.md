# Klynt

LSPosed module that replaces the bottom bar in Telegram and Twitter/X with a floating glass pill. No network hooks. Just the bar.

<p>
  <a href="https://github.com/U-Nyxx/Klynt/releases/latest"><img src="https://img.shields.io/github/v/release/U-Nyxx/Klynt?style=flat&label=download&color=111111" alt="release"></a>
  <a href="https://github.com/U-Nyxx/Klynt/actions/workflows/release.yml"><img src="https://github.com/U-Nyxx/Klynt/actions/workflows/release.yml/badge.svg" alt="build"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/U-Nyxx/Klynt?style=flat&label=license&color=111111" alt="license"></a>
  <a href="#requirements"><img src="https://img.shields.io/badge/API-33%2B-111111?style=flat" alt="api"></a>
</p>

<img src="hero.png" alt="Klynt hero" width="100%">

Klynt is a rebuild, not a theme. The bar is rendered with `AGSL + RenderEffect` (GPU) and a `C++` bridge for thermal/SOC checks. Spring is `Choreographer`, not `ValueAnimator`.

<p align="center">
  <img src="screenshots/demo-pill.png" alt="pill" width="360">
</p>

Pill is `68×56` inside a `#1C1B20 @0.82` scrim. Blur is `18` on flagships, `12` mid, `8` low. `FULL` is 4 taps (3 CA + center), `LITE` is 2.

## Install

1. Download `klynt-<ver>-<code>.apk` from [Releases](https://github.com/U-Nyxx/Klynt/releases/latest)
2. Install
3. LSPosed → Modules → enable **Klynt** → tick Telegram / X (or “Aktifkan scope” in app)
4. Force-stop target → open again

Root + LSPosed 101+ required. `arm64` only. Android 13+ (AGSL needs 33). Older stays on `v1.0.7`.

## Build

```bash
git clone https://github.com/U-Nyxx/Klynt.git
cd Klynt
./gradlew assembleDebug
./gradlew testDebugUnitTest
python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

`JDK 17 / SDK 36 / Kotlin 2.2 / NDK 27 / CMake 3.22` — `compileSdk 37` for libxposed 102, `target 35`, `min 33`.

## Stack

- `Kotlin` + `C++` (NDK) + `AGSL` — `GlassMotion` spring `170/0.72`, `KlyntGlassView` `RenderEffect` chain, `klynt_hook.cpp` `__system_property_get`
- `Compose` `BOM 2024.08` + `OkHttp` + `Gson` — no Coil/Retrofit/QWEA0
- `Velra` `io.github.u-nyxx:velra` is the same engine as a library: https://github.com/U-Nyxx/velra

## How it finds the bar

Ghost first (role: clickable + labeled + bottom 12% + 3 siblings), then wrapper (`BottomNavigationView` → semantics → size `48..80dp`). `DISARM` on toggle off, `reconfigure` live, `WeakHashMap` + `1500ms` throttle. Details in `docs/HOOKS.md`.

SOC: `SocDetector` → `FULL` Snapdragon 8/Tensor `18/0.10`, `LITE` SD 6/7 Dimensity 8k/9k Exynos `12`, `SCRIM` Dimensity 700/low-RAM/thermal `8`. `ThermalListener` live.

## Docs

- `docs/RESEARCH.md` — where the glass idea comes from
- `docs/GLASS_ENGINE.md` — AGSL cost `4/2` taps, SDF normal
- `docs/ARCHITECTURE.md` — `decorView → Discovery → Wrapper → GlassView / GhostBar`
- `docs/SOC_TIERS.md` — table per `ro.hardware`
- `docs/LIMITATIONS.md` — no wallpaper blur without SystemUI hook

## Supported

Telegram: Official, Beta, Web, Plus, Nekogram, NekoX, Nagram/X, Cherrygram, Forkgram, Turrit, Octogram, Mercurygram, Nullgram, iMe, exteraGram, Telega, Yukigram, Nicegram, TGConnect
Twitter/X: `com.twitter.android`

FAQ: module not showing → LSPosed 101+, glass not showing → checklist + force-stop, Play Protect → Install anyway.

## License

MIT — see [LICENSE](LICENSE)
