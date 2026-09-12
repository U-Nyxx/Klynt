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
    fun `shader declares every uniform the view packs`() {
        val required = listOf(
            "uniform shader backdrop",
            "uniform float2 resolution",
            "uniform float4 barRect",
            "uniform float cornerRadius",
            "uniform float intensity",
            "uniform float dark",
            "uniform float2 press",
            "uniform float pressAmount"
        )
        required.forEach { assertTrue(KLYNT_GLASS_SHADER.contains(it), it) }
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
}
