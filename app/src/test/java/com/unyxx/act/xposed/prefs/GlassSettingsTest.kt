package com.unyxx.act.xposed.prefs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A pass must never mix a disabled gate with live settings. */
class GlassSettingsTest {

    @Test
    fun `active only when both gates are on`() {
        assertTrue(GlassSettings(true, true, 1f, 999f, true).active)
        assertFalse(GlassSettings(false, true, 1f, 999f, true).active)
        assertFalse(GlassSettings(true, false, 1f, 999f, true).active)
    }

    @Test
    fun `prefs schema defaults keep existing installs on pill glass`() {
        assertTrue(PrefsSchema.Feature.LIQUID_GLASS_ENABLED.defaultValue)
        assertTrue(PrefsSchema.Feature.BLUR_ENABLED.defaultValue)
    }
}
