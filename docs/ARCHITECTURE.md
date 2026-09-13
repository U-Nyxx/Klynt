# Architecture — KLYNT

## Overview

```
Target App (Telegram / X) — root + LSPosed
  └─ Window.decorView (ViewGroup)
      ├─ BottomNavDiscovery (WeakHashMap armed, 1500ms throttle, 4000 node budget, DISARM)
      │   └─ BottomNavWrapper (FrameLayout, preserve index/LayoutParams, reconfigure live, ThermalListener)
      │       └─ KlyntGlassView (RenderEffect chain: blur 18/12/8 → AGSL RuntimeShader, tier FULL/LITE/SCRIM, spring)
      └─ GhostDriver (INVISIBLE not GONE, findCover 25%/60%/3× guard, mapTabs 3+ labels, syncSelection)
          └─ KlyntGhostBar (Canvas chromeOnly, 68×56dp pill, droplet stretch 1+v*4, haptic)
Manager (Compose, not in target process):
  └─ MainActivity → HorizontalPager (fractional selectedPage) → LiquidGlassTabBar (scrim full + glass pill 68×56 clipped)
SOC: SocDetector.profileFor(Build.HARDWARE lowercased) → glassParamsFor → KlyntGlassView.configure()
```

## Modules

- `liquidglass/engine`: `KlyntGlassShader.kt` (AGSL), `KlyntGlassView.kt` (View + RenderEffect), `GlassMotion.kt` (spring integrator), `GlassParams` (SOC → blur/disp/density)
- `liquidglass/injection`: `BottomNavWrapper.kt`, `BottomNavDiscovery.kt`
- `liquidglass/ghost`: `GhostDriver.kt`, `KlyntGhostBar.kt`
- `util`: `SocDetector.kt`, `Extensions.kt`
- `xposed`: `KlyntModule.kt`, `hooks/telegram/*`, `hooks/twitter/*`, `prefs/*`, `scope/*`
- `manager`: `ui/*`, `viewmodel/*`, `di/*`, `update/*`
- `network`: `GitHubApi.kt` (ETag), `ReleaseInfo.kt` (strict regex)

## Data Flow

Prefs `RemotePrefs` → `GlassSettings(active,intensity,cornerDp,blur,ghostMode,clear)` → `onResumed()` → `tryGhost()` → `BottomNavDiscovery.discover(find=tryWrap)` → `wrap()` → `setBarRect()` → `pushUniforms()` → `invalidate()`.

Update: `UpdateRepository.check(force)` ETag conditional → `ReleaseInfo.from(strict)` → `DownloadManager` → `FileProvider installIntent`.

## Threading

- Hook runs on target main thread (LSPosed). Probes via `ViewTreeObserver.OnGlobalLayoutListener` + `Handler` delays, not blocking. `GhostDriver.states/retries` `WeakHashMap` synchronized.
- Manager uses `Dispatchers.IO` for prefs + `StateFlow` + `Turbine` tests.
