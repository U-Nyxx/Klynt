package com.unyxx.act.liquidglass.engine

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class KlyntGlassInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun glassView_rendersWithoutCrash() {
        composeRule.setContent {
            com.unyxx.act.liquidglass.engine.KlyntGlassView(
                context = androidx.compose.ui.platform.LocalContext.current,
                params = com.unyxx.act.liquidglass.engine.GlassParams(
                    blurRadius = 18f,
                    dispersion = 0.05f,
                    bevel = 1f,
                    intensity = 1f,
                    dark = 0f,
                    barRect = floatArrayOf(0f, 0f, 720f, 108f),
                    cornerRadius = 54f,
                    clearMode = 0f,
                    press = floatArrayOf(360f, 54f),
                    pressAmount = 0f
                )
            )
        }
        composeRule.onNodeWithContentDescription("liquid_glass_pill").performClick()
    }
}
