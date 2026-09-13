# Limitations — Honest Disclosure

- **No wallpaper blur** without SystemUI hook: `KlyntGlassView` blurs only inside target `decorView`. Platform `backboardd` blurs wallpaper across windows. Fix planned: hook `com.android.systemui` (LSPosed) or Magisk RRO.
- **No temporal fluid**: AGSL single-pass fragment, Mali forbids dynamic loops. No particle advection per frame.
- **No HDR pyramid**: single blur sigma, luma mix only, not multi-sample HDR tone-map.
- **No Vulkan/RT**: unknown SOC fallback is SCRIM. Dimensity 700 Vulkan 700 driver older → avoid Vulkan 1.3, use SCRIM.
- **Fragment rebuild**: glass re-wraps on fragment `onResume` via `BottomNavDiscovery` sticky; layout `find()` throttled 1500ms, slider live `reconfigure()` handles intensity without re-wrap.
- **Min API 33**: `RuntimeShader` needs Tiramisu+. Older devices stay on v1.0.7 Frost.
