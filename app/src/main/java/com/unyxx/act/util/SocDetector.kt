package com.unyxx.act.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
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
        val thermalListenerRequired: Boolean,
        /**
         * False → static backdrop (captured on layout, not per frame).
         * The per-frame re-capture (~0.8 MB transient per frame on a
         * 1080p bar) is the #1 RAM/GC cost — only full-lens tiers pay it.
         */
        val dynamicBackdrop: Boolean,
        /** False → QWEA0 low-quality path (cheaper blur pipeline). */
        val highQuality: Boolean
    )

    fun detect(): Profile =
        profileFor(Build.HARDWARE.lowercase(), Build.BRAND.lowercase())

    /**
     * Pure SoC → profile mapping (no framework access), unit-testable on
     * plain JVM. Callers must pass already-lowercased strings.
     */
    fun profileFor(hardware: String, brand: String): Profile {
        // NOTE: compare LOWERCASED on both sides — the old code lowercased
        // HARDWARE then matched "MT8"/"MT6" uppercase, so every Dimensity
        // silently fell through to the unknown bucket.
        return when {
            // Snapdragon 8-series + 8 Elite (Adreno + Hexagon): full lens.
            hardware.startsWith("sm8") || hardware.startsWith("sun") ||
                hardware.startsWith("taro") || hardware.startsWith("kalama") ->
                Profile(false, 66f, 14f, 0.10f, BlurMethod.SMART, false, true, true)
            // Snapdragon 6/7/4-series: slightly reduced lens.
            hardware.startsWith("sm6") || hardware.startsWith("sm7") ||
                hardware.startsWith("sm4") || hardware.startsWith("cedar") ||
                hardware.startsWith("tundra") ->
                Profile(false, 48f, 12f, 0.08f, BlurMethod.SMART, false, true, true)
            // Dimensity 8000/9000 (Mali flagship): reduced lens.
            hardware.startsWith("mt8") || hardware.startsWith("mt9") ->
                Profile(false, 48f, 12f, 0.08f, BlurMethod.SMART, false, true, true)
            // Dimensity 6000/7000 (Mali mid-range): aggressive throttling,
            // older Vulkan drivers — frosted fallback, no refraction.
            hardware.startsWith("mt6") || hardware.startsWith("mt7") ->
                Profile(true, 32f, 10f, 0f, BlurMethod.IIR_GAUSSIAN_NEON, false, false, false)
            // Samsung Exynos (Xclipse, incl. legacy exynos*): lower power
            // efficiency, mandatory conservative profile.
            hardware.startsWith("s5e") || hardware.startsWith("exynos") || brand == "samsung" ->
                Profile(false, 40f, 10f, 0.06f, BlurMethod.SMART, true, true, true)
            // Google Tensor (Edge TPU, Mali GPU): full lens, moderate bevel.
            hardware.startsWith("gs") || hardware.startsWith("tensor") ||
                hardware.startsWith("zuma") || hardware.startsWith("laguna") ->
                Profile(false, 66f, 14f, 0.10f, BlurMethod.SMART, false, true, true)
            // Unknown: frosted fallback is always safe.
            else -> Profile(true, 32f, 10f, 0f, BlurMethod.SMART, false, false, false)
        }
    }

    /**
     * Effective profile for [context]: base [detect] downgraded to frosted
     * when the device is low-RAM or thermally throttled. A lens that OOMs
     * or thermally trips the target is worse than no lens — frosted blur
     * stays smooth where refraction would jank or crash.
     */
    fun resolve(context: Context): Profile {
        val base = detect()
        if (base.frostedFallback) return base
        if (isLowRam(context) || isThermallyThrottled(context)) {
            return base.copy(
                frostedFallback = true,
                dispersion = 0f,
                dynamicBackdrop = false,
                highQuality = false
            )
        }
        return base
    }

    private fun isLowRam(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            (am?.isLowRamDevice == true) || ((am?.memoryClass ?: 256) < 192)
        } catch (_: Throwable) {
            false
        }
    }

    private fun isThermallyThrottled(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            // MODERATE+ means the skin is hot / throttling is active.
            (pm?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE) >=
                PowerManager.THERMAL_STATUS_MODERATE
        } catch (_: Throwable) {
            false
        }
    }
}
