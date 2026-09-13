# Contributing to KLYNT

## Dev Setup

- JDK 17, Android SDK 36 (compileSdk 37 for libxposed 102), Kotlin 2.2.10, Compose BOM 2024.08
- `git clone https://github.com/U-Nyxx/Klynt.git && cd Klynt`
- `local.properties`: `sdk.dir=/path/to/android-sdk`
- TrustStore workaround (ID region where mavenCentral 403): `export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$JAVA_HOME/lib/security/cacerts -Djavax.net.ssl.trustStorePassword=changeit"`

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

Release (CI only): `APP_SIGN_KEY` base64 `klynt-release.jks` + `APP_SIGN_PWD` + `APP_SIGN_ALIAS`.

## AGSL Rules

- Loop-free: `for (` / `while (` forbidden (Mali). Blur from `RenderEffect` chain, not shader.
- Cost ledger: `FULL 4 taps / LITE 2 taps`. `LITE` forces `dispersion=0`.
- Uniforms packed by `KlyntGlassView.pushUniforms()` — keep names sync with `KlyntGlassShader.kt:29`.

## SOC Tiers

Edit `SocDetector.kt` + `KlyntGlassView.kt:glassParamsFor`. Test with `SocDetectorTest` + `KlyntGlassTest`.

## Codename Rotation

- `release-codename.txt` single line `[A-Za-z0-9_-]` (e.g. `lVaA`). Sanitized in `app/build.gradle.kts:33` and `release.yml:68` `tr -cd`.
- Commit it, tag `vX.Y.Z`, push tag — CI renames `klynt-<ver>-<CODE>.apk`.

## Checks Before PR

- `./gradlew testDebugUnitTest` green
- `python3 tools/verify_apk.py` PASS (scope 27 ↔ queries sync)
- No hard-coded `BottomSheetTabs` as sole finder — use strategy chain + semantics
