## What

## Checks

- [ ] `./gradlew testDebugUnitTest` green
- [ ] `python3 tools/verify_apk.py` PASS (scope 27 ↔ queries sync)
- [ ] No hard-coded class as sole hook (strategy chain kept)
- [ ] SOC tiers tested (Thermal + lowRam path)
