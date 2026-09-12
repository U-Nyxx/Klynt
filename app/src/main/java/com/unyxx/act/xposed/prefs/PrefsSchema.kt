package com.unyxx.act.xposed.prefs

object PrefsSchema {
    const val MODULE_PACKAGE = "com.unyxx.act"
    const val PREFS_FILE = "klynt_prefs"
    const val REMOTE_PREFS_GROUP = "klynt_config"

    // Global toggles
    const val GLOBAL_LIQUID_GLASS_ENABLED = "global_liquid_glass_enabled"
    const val MODULE_ACTIVE = "module_active"
    const val LOG_LEVEL = "log_level"
    const val AUTO_START_ENABLED = "auto_start_enabled"

    // Per-app keys: "app:{package}:{feature}"
    private const val APP_PREFIX = "app:"

    fun appKey(packageName: String, feature: Feature): String =
        "$APP_PREFIX$packageName:${feature.name}"

    /** Per-app glass intensity 0..1 (default 1 = full SoC profile). */
    fun intensityKey(packageName: String): String =
        "$APP_PREFIX$packageName:GLASS_INTENSITY"

    /**
     * Per-app corner radius in dp (default 999 = pill). Read with
     * default 999f so existing installs keep the pill shape.
     */
    fun cornerKey(packageName: String): String =
        "$APP_PREFIX$packageName:GLASS_CORNER_DP"

    enum class Feature(val defaultValue: Boolean) {
        LIQUID_GLASS_ENABLED(true),
        // Default ON: matches the long-standing hook behavior (blur was
        // always applied); the toggle now truthfully reflects reality.
        BLUR_ENABLED(true)
    }
}
