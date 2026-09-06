package com.unyxx.act.util

import android.os.Build
import com.example.liquidglass.BlurMethod

object SocDetector {

    data class Profile(
        val useAgslLens: Boolean,
        val preferredBlurMethod: BlurMethod,
        val thermalListenerRequired: Boolean,
        val maxRefractionHeight: Float
    )

    fun detect(): Profile {
        val hardware = Build.HARDWARE.lowercase()
        val brand = Build.BRAND.lowercase()

        // Build.SOC support varies — use hardware/board as primary, fallback to board
        val soc = try {
            Build.SOC_MANUFACTURER.lowercase() + " " + Build.SOC_MODEL.lowercase()
        } catch (_: NoSuchFieldError) {
            Build.BOARD.lowercase()
        }

        return when {
            hardware.startsWith("sm8") || hardware.startsWith("taro") || hardware.startsWith("kalama") ->
                Profile(true, BlurMethod.SMART, false, 8f)
            hardware.startsWith("sm6") || hardware.startsWith("sm7") || hardware.startsWith("cedar") || hardware.startsWith("tundra") ->
                Profile(true, BlurMethod.SMART, false, 6f)
            hardware.startsWith("mt6") || hardware.startsWith("MT6") ->
                Profile(false, BlurMethod.IIR_GAUSSIAN_NEON, false, 4f)
            hardware.startsWith("mt8") || hardware.startsWith("MT8") ->
                Profile(true, BlurMethod.SMART, false, 6f)
            hardware.startsWith("s5e") || brand == "samsung" ->
                Profile(true, BlurMethod.SMART, true, 4f)
            hardware.startsWith("gs") || hardware.startsWith("tensor") ->
                Profile(true, BlurMethod.SMART, false, 8f)
            else -> Profile(false, BlurMethod.SMART, false, 4f)
        }
    }
}
