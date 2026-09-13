# SOC Tiers — Adaptive Quality

## Profiles (`SocDetector.profileFor(hardware.lowercased, brand.lowercased)`)

| Family | `hardware` prefix | `frostedFallback` | `refractionDp` | `bevelDp` | `dispersion` | `thermalListener` | `dynamicBackdrop` | `highQuality` |
|--------|-------------------|-------------------|----------------|-----------|--------------|-------------------|-------------------|---------------|
| Snapdragon 8/Elite | `sm8/sun/taro/kalama` | false | 66 | 14 | 0.10 | false | true | true |
| Snapdragon 6/7/4 | `sm6/sm7/sm4/cedar/tundra` | false | 48 | 12 | 0.08 | false | true | true |
| Dimensity 8k/9k | `mt8/mt9` | false | 48 | 12 | 0.08 | false | true | true |
| Dimensity 700 | `mt6/mt7` | **true** | 32 | 10 | 0 | false | false | false |
| Exynos (Xclipse) | `s5e/exynos` or `brand==samsung` | false | 40 | 10 | 0.06 | **true** | true | true |
| Tensor | `gs/tensor/zuma/laguna` | false | 66 | 14 | 0.10 | false | true | true |
| Unknown | — | **true** | 32 | 10 | 0 | false | false | false |

## Resolution (`SocDetector.resolve(context)`)

If `base.frostedFallback` → keep. Else if `isLowRam(memoryClass<192 || isLowRamDevice)` or `currentThermalStatus>=MODERATE` → `copy(frosted=true,dispersion=0,dynamicBackdrop=false,highQuality=false)`.

## Glass Params (`glassParamsFor(profile)`)

- `frosted` → `8/0/0.3` SCRIM
- `!highQuality` → `12/0/0.35` LITE
- else → `18/dispersion/ bevelDp/14` FULL

## Thermal Live Downgrade

`KlyntGlassView` registers `OnThermalStatusChangedListener` on `onAttachedToWindow`, `MODERATE+ → tier=SCRIM` (remember `preThermalTier`), cool → restore (spring, not jump). No leak (unregister on detach).
