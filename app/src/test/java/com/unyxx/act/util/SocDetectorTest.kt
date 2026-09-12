package com.unyxx.act.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression guard for the SoC mapping. The MT8/MT6 case-compare bug once
 * demoted every Dimensity to the frosted bucket — these tests pin the
 * intended tier per family on plain JVM (no device needed).
 */
class SocDetectorTest {

    @Test
    fun `snapdragon 8 series and 8 elite get full lens`() {
        listOf("sm8650", "sm8750", "sun", "taro", "kalama").forEach { hw ->
            val p = SocDetector.profileFor(hw, "samsung")
            assertFalse(p.frostedFallback, hw)
        }
    }

    @Test
    fun `dimensity flagship gets lens, mid-range gets frosted`() {
        val flagship = SocDetector.profileFor("mt8792", "xiaomi")
        assertFalse(flagship.frostedFallback)
        val mid = SocDetector.profileFor("mt6765", "xiaomi")
        assertTrue(mid.frostedFallback)
        assertFalse(mid.dynamicBackdrop)
    }

    @Test
    fun `exynos and tensor keep their tiers`() {
        val exynos = SocDetector.profileFor("s5e9945", "samsung")
        assertFalse(exynos.frostedFallback)
        assertTrue(exynos.thermalListenerRequired)
        val tensor = SocDetector.profileFor("zumapro", "google")
        assertFalse(tensor.frostedFallback)
    }

    @Test
    fun `unknown hardware always falls back to frosted`() {
        val p = SocDetector.profileFor("mystery-soc-999", "acme")
        assertTrue(p.frostedFallback)
        assertFalse(p.dynamicBackdrop)
        assertFalse(p.highQuality)
    }
}
