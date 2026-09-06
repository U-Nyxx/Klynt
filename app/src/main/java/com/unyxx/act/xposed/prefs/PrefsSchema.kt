package com.unyxx.act.xposed.prefs

object PrefsSchema {
    const val MODULE_PACKAGE = "com.unyxx.act"
    const val PREFS_FILE = "klynt_prefs"
    const val REMOTE_PREFS_GROUP = "klynt_config"

    // Global toggles
    const val GLOBAL_LIQUID_GLASS_ENABLED = "global_liquid_glass_enabled"
    const val MODULE_ACTIVE = "module_active"
    const val LOG_LEVEL = "log_level"

    // Per-app keys: "app:{package}:{feature}"
    private const val APP_PREFIX = "app:"

    fun appKey(packageName: String, feature: Feature): String =
        "$APP_PREFIX$packageName:${feature.name}"

    enum class Feature(val defaultValue: Boolean) {
        LIQUID_GLASS_ENABLED(true),
        BLUR_ENABLED(false)
    }
}