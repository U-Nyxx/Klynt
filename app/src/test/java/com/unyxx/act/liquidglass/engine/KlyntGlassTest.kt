package com.unyxx.act.liquidglass.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** KlyntGlass engine contracts (pure JVM, no device). */
class KlyntGlassTest {

    @Test
    fun `full strength only when nothing is weak`() {
        assertEquals(KlyntTier.SHADER, selectGlassTier(false, false, false))
    }

    @Test
    fun `any weakness degrades to calm scrim, never blank`() {
        assertEquals(KlyntTier.SCRIM, selectGlassTier(true, false, false))
        assertEquals(KlyntTier.SCRIM, selectGlassTier(false, true, false))
        assertEquals(KlyntTier.SCRIM, selectGlassTier(false, false, true))
        assertEquals(KlyntTier.SCRIM, selectGlassTier(true, true, true))
    }

    @Test
    fun `mid-range silicon gets LITE achromatic lens, not scrim`() {
        assertEquals(KlyntTier.LITE, selectGlassTier(false, false, false, highQuality = false))
        // Weakness still wins over mid-range: calm pill beats jank.
        assertEquals(KlyntTier.SCRIM, selectGlassTier(true, false, false, highQuality = false))
        assertEquals(KlyntTier.SCRIM, selectGlassTier(false, true, false, highQuality = false))
        assertEquals(KlyntTier.SHADER, selectGlassTier(false, false, false, highQuality = true))
    }

    @Test
    fun `shader declares every uniform the view packs`() {
        val required = listOf(
            "uniform shader backdrop",
            "uniform float2 resolution",
            "uniform float4 barRect",
            "uniform float cornerRadius",
            "uniform float intensity",
            "uniform float dark",
            "uniform float2 press",
            "uniform float pressAmount",
            "uniform float clearMode",
            "uniform float dispersion",
            "uniform float bevel"
        )
        required.forEach { assertTrue(KLYNT_GLASS_SHADER.contains(it), it) }
    }

    @Test
    fun `v2 refraction is SDF-normal based with chromatic split`() {
        // Gradient normal (correct pill caps), per-channel taps (CA),
        // inner stroke (cut-glass hairline) — the v2 signature.
        assertTrue(KLYNT_GLASS_SHADER.contains("sdRoundBox(lp + vec2(e, 0.0)"))
        assertTrue(KLYNT_GLASS_SHADER.contains("backdrop.eval(uv - n * ca).r"))
        assertTrue(KLYNT_GLASS_SHADER.contains("backdrop.eval(uv + n * ca).b"))
        assertTrue(KLYNT_GLASS_SHADER.contains("stroke"))
    }

    @Test
    fun `shader stays Mali-safe with no loops and balanced braces`() {
        // Blur comes from the RenderEffect chain, never in-shader taps:
        // dynamic loops break strict AGSL compilers (Mali).
        val code = KLYNT_GLASS_SHADER.replace("uniform shader backdrop", "")
        assertTrue(!code.contains("for ("), "no loops allowed in shader")
        assertTrue(!code.contains("while ("), "no loops allowed in shader")
        assertEquals(code.count { it == '{' }, code.count { it == '}' })
    }

    @Test
    fun `glass params follow the SOC tier without touching SocDetector`() {
        val full = glassParamsFor(
            com.unyxx.act.util.SocDetector.profileFor("sm8650", "xiaomi")
        )
        assertEquals(18f, full.blurRadius)
        assertTrue(full.dispersion > 0f)
        val lite = glassParamsFor(
            // Dimensity 7000-class: frosted per mapping → SCRIM params.
            com.unyxx.act.util.SocDetector.profileFor("mt6789", "xiaomi")
        )
        assertEquals(8f, lite.blurRadius)
        assertEquals(0f, lite.dispersion)
    }

    @Test
    fun `spring converges and reports rest`() {
        var pos = 0f
        var vel = 0f
        repeat(600) {
            val (p, v) = GlassMotion.springStep(pos, vel, 1f, 170f, 0.72f, 1f / 120f)
            pos = p
            vel = v
        }
        assertTrue(GlassMotion.isSettled(pos, vel, 1f))
        assertTrue(pos > 0.95f)
    }
}
