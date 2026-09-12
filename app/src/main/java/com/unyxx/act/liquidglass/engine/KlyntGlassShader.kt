package com.unyxx.act.liquidglass.engine

/**
 * KlyntGlass AGSL source — written from scratch for this project.
 *
 * Technique notes (learned, not copied): rounded-box SDF for the lens
 * mask, rim mask as outer-minus-inner band, refraction by bending sample
 * coordinates toward the center proportional to edge², RGB treated
 * uniformly (no chromatic taps — Mali-safe), vibrancy via luma mix,
 * specular top gradient + rim light + bottom inner shade, dither-free
 * (banding is negligible over live content at these radii).
 *
 * Deliberately loop-free: backdrop blur comes from the RenderEffect
 * chain (GPU blur node), never from in-shader taps. This keeps the
 * shader cheap on Mali compilers and avoids AGSL's strict loop rules.
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
    float d = sdRoundBox(fragCoord - center, halfSize, r);
    if (d > 1.0) {
        return half4(0.0, 0.0, 0.0, 0.0);
    }
    float edge = clamp(d / max(r, 1.0) + 0.5, 0.0, 1.0);
    vec2 toFrag = fragCoord - center;
    float dist = max(length(toFrag), 1.0);
    vec2 dir = toFrag / dist;
    float bend = edge * edge * 14.0 * intensity * (1.0 + clearMode * 0.6);
    vec2 uv = fragCoord - dir * bend;
    if (pressAmount > 0.0 && press.x >= 0.0) {
        vec2 pd = fragCoord - press;
        float pl = max(length(pd), 1.0);
        float infl = exp(-pl * pl / 16200.0) * pressAmount;
        uv -= (pd / pl) * infl * 10.0;
    }
    half4 c = backdrop.eval(uv);
    float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    c.rgb = mix(vec3(lum), c.rgb, 1.35);
    // Apple's tint rule: ~30% light, ~50% dark. Clear variant thins
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
    float rim = smoothstep(0.55, 1.0, edge);
    c.rgb += rim * 0.12 * shadeGain;
    float bot = clamp((uvN.y - 0.75) * 4.0, 0.0, 1.0);
    c.rgb *= 1.0 - bot * 0.10 * shadeGain;
    c.a = 1.0;
    return c;
}
"""
