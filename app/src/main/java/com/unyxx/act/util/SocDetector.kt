package com.unyxx.act.util

import android.os.Build
import com.example.liquidglass.BlurMethod

/**
 * Runtime SoC detection for adaptive Liquid Glass quality.
 *
 * Apple can assume uniform GPU behavior; Android cannot — Adreno, Mali
 * and Xclipse throttle and tile very differently. Every value below is
 * in dp and converted to px at the call site.
 */
object SocDetector {

    data class Profile(
        /** False → frosted fallback: no refraction/dispersion, blur only. */
        val frostedFallback: Boolean,
        val refractionDp: Float,
        val bevelDp: Float,
        val dispersion: Float,
        val preferredBlurMethod: BlurMethod,
        /** True on Exynos: caller should keep effects conservative. */
        val thermalListenerRequired: Boolean
    )

    fun detect(): Profile {
        val hardware = Build.HARDWARE.lowercase()
        val brand = Build.BRAND.lowercase()

        return when {
            // Snapdragon 8-series (Adreno + Hexagon): full lens pipeline.
            hardware.startsWith("sm8") || hardware.startsWith("taro") || hardware.startsWith("kalama") ->
                Profile(false, 66f, 14f, 0.10f, BlurMethod.SMART, false)
            // Snapdragon 6/7-series: slightly reduced lens.
            hardware.startsWith("sm6") || hardware.startsWith("sm7") || hardware.startsWith("cedar") || hardware.startsWith("tundra") ->
                Profile(false, 48f, 12f, 0.08f, BlurMethod.SMART, false)
            // Dimensity 8000+ (Mali flagship): reduced lens.
            hardware.startsWith("mt8") || hardware.startsWith("MT8") ->
                Profile(false, 48f, 12f, 0.08f, BlurMethod.SMART, false)
            // Dimensity 6000/7000 (Mali mid-range): aggressive throttling,
            // older Vulkan drivers — frosted fallback, no refraction.
            hardware.startsWith("mt6") || hardware.startsWith("MT6") ->
                Profile(true, 32f, 10f, 0f, BlurMethod.IIR_GAUSSIAN_NEON, false)
            // Samsung Exynos (Xclipse): lower power efficiency, mandatory
            // conservative profile.
            hardware.startsWith("s5e") || brand == "samsung" ->
                Profile(false, 40f, 10f, 0.06f, BlurMethod.SMART, true)
            // Google Tensor (Edge TPU, Mali GPU): full lens, moderate bevel.
            hardware.startsWith("gs") || hardware.startsWith("tensor") ->
                Profile(false, 66f, 14f, 0.10f, BlurMethod.SMART, false)
            // Unknown: frosted fallback is always safe.
            else -> Profile(true, 32f, 10f, 0f, BlurMethod.SMART, false)
        }
    }
}
