# Glass Engine — AGSL + RenderEffect

## Pipeline

`isClickable=false` View draws nothing; output is `RenderEffect`:

```kotlin
val blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, CLAMP)
val glass = RenderEffect.createRuntimeShaderEffect(RuntimeShader(KLYNT_GLASS_SHADER), "backdrop")
setRenderEffect(createChainEffect(glass, blur))
```

Blur node first (GPU), then fragment shader. No bitmap capture, no per-frame allocation, no `.so`.

## Shader Uniforms

`backdrop (shader), resolution (float2), barRect (float4), cornerRadius, intensity, dark, press (float2), pressAmount, clearMode, dispersion, bevel` — packed by `KlyntGlassView.pushUniforms()`.

## Cost Ledger

- `FULL` (Snapdragon 8 / Tensor): 3 taps CA (`R/G/B` split) + 1 center luma = 4 taps, blur 18, dispersion 0.10
- `LITE` (SD 6/7, Dimensity 8k/9k, Exynos): 1 tap achromatic + 1 center = 2 taps, blur 12, dispersion 0
- `SCRIM` (Dimensity 700, lowRam, thermal): 0 taps, Canvas `drawRoundRect` `0xD61E2A3A / 0xD6FFFFFF`, blur 8 (for cool-down restore)

Loop-free: `for (` and `while (` banned, verified in `KlyntGlassTest.kt`.

## SDF Lens

`sdRoundBox(lp, halfSize, r)` → `edge = clamp(d/r +0.5,0,1)`. Normal via gradient `dx/dy = sdRoundBox(lp±e)` (ALU only, 0 texture). `bend = edge²*14*intensity*(1+clearMode*0.6)` along `n`. Press bulge `exp(-r²/16200)*10`.

## Chromatic Aberration

`ca = bend*dispersion`, `if(dispersion>0.001)` → `cr@uv-n*ca, cg@uv, cb@uv+n*ca` else `c@uv`.

## Specular & Tint

Vibrancy `mix(lum,c.rgb,1.35)`, tint `mix(0.30,0.50,dark)*(1-clearMode*0.55)*0.5` toward `white / 0.75,0.83,1.0`, `shadeGain 0.6-1.25` from center, `top 1-uvN.y*3`, `rim smoothstep(0.55-bevel*0.5,1,edge)*0.12`, `stroke 0.86-0.97` hairline, `bot (uvN.y-0.75)*4*0.10`.

## Motion

`GlassMotion.springStep` semi-implicit Euler `damping=2*ratio*sqrt(stiffness)`, `stiffness 170 damping 0.72`, 2 half-steps for 120Hz, `isSettled 0.002/0.01`.

## Thermal

`PowerManager.OnThermalStatusChangedListener` window-bound `onAttached/onDetached`, `MODERATE+ → SCRIM` (save tier, restore on cool).
