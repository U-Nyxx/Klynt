# Changelog

## v1.0.12 (2026-09-14) — codename `a16v` — Polished repo + version catalog

- **Version catalog**: added `gradle/libs.versions.toml`, migrated `build.gradle.kts` + `settings.gradle.kts` to use `libs.*` aliases
- **Instrumented tests**: added `androidTest/` — `KlyntGlassInstrumentedTest` (Compose rule), `ScopeManagerInstrumentedTest` (scope list ↔ queries 27)
- **Screenshots**: added `demo-manager.png`, `demo-settings.png`, `demo-hero-large.png`, updated `screenshots/README.md`
- **CI**: enhanced `release.yml` — `generate_release_notes: true`, action-gh-release v2, APK rename with codename
- **Project metadata**: added `FUNDING.yml`, `CODEOWNERS`, `renovate.json`, `Dangerfile`

## v1.0.11 (2026-09-13) — codename `Bv8Q`

- C++ NDK bridge: `klynt_hook.cpp` `__system_property_get` + `sysconf`
- "apple" trademark removed throughout repo
- Branch protection: `Build KLYNT` status check + 1 approval
- `release.yml`: `set -euo pipefail`, codename sanitization

## v1.0.10 (2026-09-13) — codename `lVaA` — True Liquid Glass v2

- AGSL v2: SDF-gradient normal, 3-tap CA, inner stroke, bevel per-SOC, cost 4/2 taps
- KlyntGlassView: FULL/LITE/SCRIM + GlassMotion spring 170/0.72 + ThermalListener live
- Hook: BottomNavDiscovery.disarm() + GhostDriver.syncSelection + reconfigure()
- Manager: LiquidGlassTabBar glass pill only 68×56, Home hero i18n + loading
- Update: strict asset regex, ETag 304 fix, offline fallback
- Scope: nekox queries fix, Twitter indirection
- CI: codename path + sanitization, verify scope/queries sync

## v1.0.9 — iPhone behaviors + Clear

## v1.0.8 — KlyntGlass proprietary engine

## v1.0.7 and below — frosted fallback for API <33
