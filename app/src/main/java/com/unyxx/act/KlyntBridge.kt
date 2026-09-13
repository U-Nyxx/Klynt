package com.unyxx.act

/**
 * C++ NDK bridge — hardware↔software direct page.
 * Proves Klynt is not full Kotlin: AGSL (GPU) + C++ (CPU/thermal/SOC) + Kotlin (orchestration).
 * Research: platform glass uses Render Server + C++14; we map to AGSL + RenderEffect + NDK.
 */
object KlyntBridge {
    init {
        try { System.loadLibrary("klynt") } catch (_: Throwable) {}
    }

    @JvmStatic external fun stringFromJNI(): String
}
