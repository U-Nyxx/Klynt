# Hooks — How We Find the Bottom Bar Without Hardcoding

## Ghost-first (ROM-proof)

`GhostDriver.tryGhost(decor)`:

- Tabs by **role**, not class: `clickable + labeled (TextView.text) + bottom 12% + 3+ siblings in one row + span ≥50% width`. Order = `left-to-right`.
- Tap via `performClick()` on real tab — no internal API.
- `findCover` guard: `height ≤25% screen, width ≥60%, ≤3× union` — ancestor highest that passes. Hides `INVISIBLE` (never GONE) keeps insets.
- `ensureRetryArmed` retries 2000ms if hierarchy not yet built.

## Glass Wrapper (fallback)

`TelegramBottomNavHook` / `TwitterBottomNavHook` via `BottomNavDiscovery`:

- Tier priority: `main (BottomSheetTabs in ActionBarLayout) → class (BottomNavigationView etc + bottomAnchored 0.85) → semantics (wide 0.85 + labeled 3) → size (48..80dp Tele, 56..120dp X, bottom 0.85)`
- `isDenied` walks 32 ancestors for `BottomSheet/Dialog/Popup`.
- `BottomNavWrapper.findWrapper` reuse → `reconfigure()` live; else `unwrapAll` + `collectAll` budgeted 4000 nodes.

## Resilience

- `BottomNavDiscovery.armed` WeakHashMap + `Probe(finished,cleanup)` + `disarm()` on toggle off — no re-wrap after disable.
- `GhostDriver.syncSelection` re-sync `isSelected/isActivated` → `selectedIndex` (swipe/detour, not only tap).

## Future-proof

Planned `HookStrategy` chain: `ClassNameStrategy → SemanticStrategy → DexPatternStrategy` (bytecode scan), plus `assets/nav_ids.json` per version and beta scanner `schedule cron`.

## Variants

26 Telegram + Twitter `scope.list` 27 entries, synced with `AndroidManifest.xml <queries>` (NekoX fix) and `verify_apk.py` gate.
