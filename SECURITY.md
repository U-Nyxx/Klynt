# Security Policy

## Reporting a Vulnerability

Open a GitHub issue with label `security` or email the maintainer. Do not disclose exploits publicly before fix.

## Scope

- KLYNT is **visual-only**: no traffic, no message read, no API calls. Hooks overlay `decorView` only.
- `scope.list` 27 packages only. `verify_apk.py` enforces `META-INF/xposed/scope.list` + `<queries>` sync.
- Release APK asset is `klynt-<ver>-<CODE>.apk` (`ReleaseInfo.kt:16` strict regex + https + size>0). Any other APK in release is ignored.

## Signing

- Release key `CN=KLYNT, OU=Mobile, O=Unyxx, C=ID` SHA256 `0db015c931e148a5a4fcd245514d8f198f8d79c69b4b37c66b478050f916bfdd` — verified in CI `apksigner verify --print-certs`.
- Rotation from v1.0.3: uninstall old then install new or silent fail.
