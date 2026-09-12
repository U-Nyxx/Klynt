#!/usr/bin/env python3
"""KLYNT APK gate: fail the build when packaging regresses.

Checks (all learned from real shipped bugs):
  - META-INF/xposed/{java_init.list,module.prop,scope.list} present
  - minApiVersion/targetApiVersion == 101, staticScope == false
  - scope.list has exactly 27 entries (26 Telegram + Twitter)
  - no legacy leftovers (assets/xposed_init, assets/module.prop, arrays.xml)
  - dex contains the hook entry + Fase-3 symbols, and NOT dead classes
  - resources.arsc contains the tune strings (EN base; ID overlay separate)
  - reports APK + dex size (diet tracking)

Usage: python3 tools/verify_apk.py <apk>
Exit 0 = pass, 1 = fail (prints FAIL lines).
"""
import sys
import zipfile

EXPECTED_SCOPE = 27
REQUIRED_DEX_SYMBOLS = [
    b"com/unyxx/act/xposed/KlyntModule",
    b"GLASS_CORNER_DP",
    b"TunePanel",
    b"GlassPreview",
    b"glassSettings",
]
ABSENT_DEX_SYMBOLS = [
    b"KlyntModuleBase",
    b"BottomPagerTabsWrapper",
]
REQUIRED_RES_SYMBOLS = [
    b"tune_corner",
    b"tune_blur",
    b"tune_intensity",
    b"action_mark_restarted",
    b"banner_inactive_title",
]
LEGACY_PATHS = [
    "assets/xposed_init",
    "assets/module.prop",
    "res/values/arrays.xml",
]


def fail(msg):
    print(f"FAIL: {msg}")


def main(apk_path):
    errors = 0
    try:
        z = zipfile.ZipFile(apk_path)
    except Exception as e:
        fail(f"cannot open {apk_path}: {e}")
        return 1
    names = z.namelist()

    def need(path):
        nonlocal errors
        if path not in names:
            fail(f"missing {path}")
            errors += 1
            return None
        return z.read(path)

    entry = need("META-INF/xposed/java_init.list")
    prop = need("META-INF/xposed/module.prop")
    scope = need("META-INF/xposed/scope.list")

    if entry is not None and b"KlyntModule" not in entry:
        fail("java_init.list does not point at KlyntModule")
        errors += 1
    if prop is not None:
        text = prop.decode()
        for want in ("minApiVersion=101", "targetApiVersion=101", "staticScope=false"):
            if want not in text:
                fail(f"module.prop missing '{want}'")
                errors += 1
    if scope is not None:
        count = len([l for l in scope.decode().splitlines() if l.strip()])
        if count != EXPECTED_SCOPE:
            fail(f"scope.list has {count} entries, expected {EXPECTED_SCOPE}")
            errors += 1

    for legacy in LEGACY_PATHS:
        if legacy in names:
            fail(f"legacy leftover packaged: {legacy}")
            errors += 1

    dex = b"".join(z.read(n) for n in names if n.startswith("classes") and n.endswith(".dex"))
    for sym in REQUIRED_DEX_SYMBOLS:
        if sym not in dex:
            fail(f"dex missing symbol {sym.decode()}")
            errors += 1
    for sym in ABSENT_DEX_SYMBOLS:
        if sym in dex:
            fail(f"dex still contains dead class {sym.decode()}")
            errors += 1

    try:
        arsc = z.read("resources.arsc")
    except KeyError:
        arsc = b""
        fail("no resources.arsc")
        errors += 1
    for sym in REQUIRED_RES_SYMBOLS:
        if sym not in arsc:
            fail(f"resources missing string {sym.decode()}")
            errors += 1

    apk_mb = sum(i.file_size for i in z.infolist()) / 1048576
    dex_mb = len(dex) / 1048576
    print(f"size: APK={apk_mb:.1f}MB dex={dex_mb:.1f}MB entries={len(names)}")

    if errors == 0:
        print("PASS: APK gate green")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
