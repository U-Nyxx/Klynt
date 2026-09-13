# Research — How Platform Liquid Glass Is Made (and how KLYNT maps it)

## 1. Native Framework (SwiftUI / UIKit)

Platform: `View.ultraThinMaterial` / `regularMaterial` / `UIVisualEffectView` automatically handles light/dark tint, blur radius, saturation. Dev does not implement blur from scratch.

KLYNT: `KlyntGlassView.kt:148` `RenderEffect.createChainEffect(glass, blur)` with `tintAmt mix(0.30,0.50,dark)*(1-clearMode*0.55)` + `vibrancy 1.35`. No manual bitmap capture.

## 2. Pipeline Rendering GPU (Core Animation + Render Server)

Platform: background pixels → `Gaussian Blur` (pyramidal, multiple sigma) → contrast/saturation → specular highlight on edges → HDR tone-map → compositing in `backboardd` / `CA Render Server` off-main-thread at 120Hz ProMotion.

KLYNT: `createBlurEffect(18/12/8 per SOC)` GPU node → `KlyntGlassShader.kt:91` luma `dot(0.299,0.587,0.114)` → `mix(lum,c.rgb,1.35)` → `tintCol` → `blum` adaptive `shadeGain 0.6-1.25` → `top 1-uvN.y*3` + `rim smoothstep` + `stroke` + `bot shade`. Single-pass fragment, loop-free (Mali compiler strict).

## 3. Spatial Ray Tracing & Refraction (visionOS)

Platform Vision Pro: glass responds to real-world lighting via sensors, ray-traced reflections, dynamic shadows, refraction via environment cubemap + thickness-dependent IOR 1.0-1.5.

KLYNT: SDF-gradient normal `dx/dy = sdRoundBox(lp±e)` (`KlyntGlassShader.kt:64`), bend `edge²*14*intensity`, CA split `n*bend*dispersion`. No cubemap/ray tracing — limited to screen-space `backdrop.eval`. Future: Vulkan RT + HDR probe on Tensor (best).

## 4. Custom Shader (SwiftUI + Metal)

Platform: `ShaderLibrary` MSL (C++14) fragment shaders, `feDisplacementMap` + time-based turbulence advected per frame, fluid particles.

KLYNT: AGSL `KLYNT_GLASS_SHADER` string inline (keep APK diet, no `.so`), 11 uniforms (`resolution/barRect/cornerRadius/intensity/dark/press/clearMode/dispersion/bevel`), `if(dispersion>0.001)` 3-tap CA else 1-tap, `press` bulge `exp(-r²/16200)*10`, `clearMode` tints 55% thinner. Cost ledger `FULL 4 taps / LITE 2 taps`.

## 5. What we cannot replicate without SystemUI hook

- Wallpaper blur across windows (Platform `backboardd` out-of-process). KLYNT blurs only inside target `decorView`. Fix: hook `com.android.systemui` (LSPosed, planned).
- Temporal fluid accumulation (Metal loops). AGSL Mali forbids dynamic loops.
- Multi-sample HDR pyramid (Platform). We use single blur sigma for diet.
