package com.unyxx.act.liquidglass.engine

/**
 * KlyntGlass AGSL source v2 — true Liquid Glass (iOS 26 language).
 *
 * What v1 had: rounded-box SDF lens, radial refraction, uniform RGB,
 * specular top + rim, vibrancy luma mix.
 *
 * What v2 adds:
 * - **Chromatic aberration**: 3 backdrop taps split along the local
 *   refract normal (R/G/B), scaled by `edge² * dispersion`. Mali-safe:
 *   3 taps, still loop-free. `dispersion == 0` collapses to 1 tap
 *   (LITE tier) with zero branching cost on the texture unit.
 * - **SDF-gradient normal**: refraction bends along the true lens
 *   normal (2 extra SDF evals = pure ALU, no texture cost) instead of
 *   the radial-to-center hack, so pill ends lens correctly.
 * - **Bevel rim**: `bevel` uniform widens/narrows the specular band
 *   per-SOC (flagship wide glow, mid-range thin line).
 * - **Inner stroke**: 1px light line just inside the edge, the iOS
 *   "cut glass" signature.
 *
 * Cost ledger (texture taps, the only thing Mali bills hard):
 * FULL = 3 (CA) + 1 (center luminance) = 4; LITE = 1 + 1 = 2.
 * Backdrop blur always comes from the RenderEffect chain (GPU blur
 * node), never from in-shader taps.
 *
 * Uniforms are packed by [KlyntGlassView]; keep names in sync.
 */
const val KLYNT_GLASS_SHADER = """
uniform shader backdrop;
uniform float2 resolution;
uniform float4 barRect;
uniform float cornerRadius;
uniform float intensity;
uniform float dark;
uniform float2 press;
uniform float pressAmount;
uniform float clearMode;
uniform float dispersion;
uniform float bevel;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 fragCoord) {
    vec2 size = vec2(barRect.z - barRect.x, barRect.w - barRect.y);
    if (size.x <= 0.0 || size.y <= 0.0) {
        return backdrop.eval(fragCoord);
    }
    vec2 center = vec2(barRect.x + size.x * 0.5, barRect.y + size.y * 0.5);
    vec2 halfSize = size * 0.5;
    float r = min(cornerRadius, min(halfSize.x, halfSize.y));
    vec2 lp = fragCoord - center;
    float d = sdRoundBox(lp, halfSize, r);
    if (d > 1.0) {
        return half4(0.0, 0.0, 0.0, 0.0);
    }
    float edge = clamp(d / max(r, 1.0) + 0.5, 0.0, 1.0);

    // Lens normal from the SDF gradient (ALU only): correct at the
    // pill caps, where radial-to-center visibly smears.
    float e = 1.5;
    float dx = sdRoundBox(lp + vec2(e, 0.0), halfSize, r) - sdRoundBox(lp - vec2(e, 0.0), halfSize, r);
    float dy = sdRoundBox(lp + vec2(0.0, e), halfSize, r) - sdRoundBox(lp - vec2(0.0, e), halfSize, r);
    vec2 n = vec2(dx, dy) / max(length(vec2(dx, dy)), 0.001);

    float bend = edge * edge * 14.0 * intensity * (1.0 + clearMode * 0.6);
    vec2 uv = fragCoord - n * bend;
    if (pressAmount > 0.0 && press.x >= 0.0) {
        vec2 pd = fragCoord - press;
        float pl = max(length(pd), 1.0);
        float infl = exp(-pl * pl / 16200.0) * pressAmount;
        uv -= (pd / pl) * infl * 10.0;
    }

    // Chromatic aberration: one tap per channel along the normal.
    // FULL = 3 taps; dispersion == 0 (LITE) → single achromatic tap.
    float ca = bend * dispersion;
    half4 c;
    if (dispersion > 0.001) {
        float cr = backdrop.eval(uv - n * ca).r;
        float cg = backdrop.eval(uv).g;
        float cb = backdrop.eval(uv + n * ca).b;
        c = half4(cr, cg, cb, 1.0);
    } else {
        c = backdrop.eval(uv);
    }

    float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    c.rgb = mix(vec3(lum), c.rgb, 1.35);
    // Platform tint rule: ~30% light, ~50% dark. Crystal variant thins
    // the tint so content richness comes through.
    float tintAmt = mix(0.30, 0.50, dark) * (1.0 - clearMode * 0.55);
    vec3 tintCol = mix(vec3(1.0), vec3(0.75, 0.83, 1.0), dark * 0.5);
    c.rgb = mix(c.rgb, tintCol, tintAmt * 0.5);
    // Adaptive shadow: brighter backdrop earns a deeper grounding shade
    // (sampled once — a single extra tap, not a kernel).
    float blum = dot(backdrop.eval(center).rgb, vec3(0.299, 0.587, 0.114));
    float shadeGain = mix(0.6, 1.25, clamp(blum, 0.0, 1.0));
    vec2 uvN = (fragCoord - barRect.xy) / max(size, vec2(1.0));
    float top = clamp(1.0 - uvN.y * 3.0, 0.0, 1.0) * (1.0 - edge * 0.5);
    c.rgb += top * 0.10 * mix(1.0, 0.6, dark) * shadeGain;
    // Bevel rim: width scales per-SOC so mid-range keeps a crisp thin
    // line where flagship gets a wide glow.
    float rimLo = clamp(0.55 - bevel * 0.5, 0.0, 0.9);
    float rim = smoothstep(rimLo, 1.0, edge);
    c.rgb += rim * 0.12 * shadeGain;
    // Inner stroke: the cut-glass hairline just inside the edge.
    float stroke = smoothstep(0.86, 0.97, edge) * (1.0 - smoothstep(0.97, 1.0, edge));
    c.rgb += stroke * mix(0.22, 0.14, dark);
    float bot = clamp((uvN.y - 0.75) * 4.0, 0.0, 1.0);
    c.rgb *= 1.0 - bot * 0.10 * shadeGain;
    c.a = 1.0;
    return c;
}
"""
