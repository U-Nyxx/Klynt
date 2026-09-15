package com.unyxx.act.xposed.prefs

object PrefsSchema {
    const val MODULE_PACKAGE = "com.unyxx.act"
    const val PREFS_FILE = "klynt_prefs"
    const val REMOTE_PREFS_GROUP = "klynt_config"

    // Global toggles
    const val GLOBAL_LIQUID_GLASS_ENABLED = "global_liquid_glass_enabled"
    const val MODULE_ACTIVE = "module_active"
    /** Manager versionName, written at manager start, read by hooks for logs. */
    const val MANAGER_VERSION_KEY = "manager_version"
    const val LOG_LEVEL = "log_level"
    const val AUTO_START_ENABLED = "auto_start_enabled"

    // UI / Theme
    const val THEME_MODE = "theme_mode"
    const val PURE_BLACK_OLED = "pure_black_oled"
    const val ACCENT_COLOR = "accent_color"
    const val LANGUAGE = "language"
    const val FOLLOW_SYSTEM_ACCENT = "follow_system_accent"

    // Log settings
    const val LOG_VERBOSE = "log_verbose"
    const val LOG_AUTOSCROLL = "log_autoscroll"
    const val LOG_PAUSED = "log_paused"
    const val LOG_WORD_WRAP = "log_word_wrap"

    // Per-app keys: "app:{package}:{feature}"
    private const val APP_PREFIX = "app:"

    fun appKey(packageName: String, feature: Feature): String =
        "$APP_PREFIX$packageName:${feature.name}"

    /**
     * Per-app bar mode: AUTO tries ghost first (glass fallback), FORCE_GHOST
     * skips glass entirely and keeps retrying, GLASS_ONLY skips ghost.
     * Default AUTO preserves old behavior for existing installs.
     */
    fun ghostModeKey(packageName: String): String =
        "$APP_PREFIX$packageName:BAR_MODE"

    enum class GhostMode { AUTO, FORCE_GHOST, GLASS_ONLY }

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
        BLUR_ENABLED(true),
        // Platform's Clear variant: max transparency + full refraction +
        // dimming for legibility. Default OFF (Regular).
        GLASS_CLEAR(false)
    }

    /** Theme mode: 0 = System, 1 = Light, 2 = Dark, 3 = Pure Black OLED. */
    enum class ThemeMode(val defaultValue: Int = 0) {
        SYSTEM(0), LIGHT(1), DARK(2), PURE_BLACK(3)
    }

    /** Language: 0 = System, 1 = English, 2 = Indonesian. */
    enum class Language(val defaultValue: Int = 0) {
        SYSTEM(0), ENGLISH(1), INDONESIAN(2)
    }

    // Realtime log transport
    const val EVENT_SEQUENCE_ID = "event_sequence_id"
    const val EVENT_RING_BUFFER_SIZE = 2000
    const val EVENT_BATCH_LIMIT = 50

    /** Predefined accent colors (Material 3 tonal palette keys). */
    enum class AccentColor(val defaultValue: String = "blue") {
        BLUE("blue"), GREEN("green"), PURPLE("purple"), ORANGE("orange"),
        TEAL("teal"), PINK("pink"), RED("red"), AMBER("amber")
    }

    // Realtime log transport types
    enum class LogEventType(val label: String) {
        HOOK_SUCCESS("Hook OK"), HOOK_FAIL("Hook FAIL"), DISARM("Disarm"),
        FALLBACK("Fallback"), THERMAL_DOWNGRADE("Thermal"), SCOPE_CHANGE("Scope"),
        BINDER_CONNECT("Binder ON"), BINDER_DISCONNECT("Binder OFF"),
        GHOST_APPEAR("Ghost"), GHOST_DISMISS("Ghost dismiss"),
        TAB_SYNC("Tab sync"), DETAIL_EXPAND("Detail"), STATUS_INFO("Info")
    }

    enum class LogEventColor(val hex: String) {
        GREEN("#FF4CAF50"), RED("#FFE53935"), YELLOW("#FFFB8C00"),
        BLUE("#FF2196F3"), CYAN("#FF00BCD4"), MAGENTA("#FFE040FB"), GRAY("#FF888888")
    }

    data class LogEvent(
        val sequenceId: Long,
        val timestamp: Long,
        val type: LogEventType,
        val packageChain: String,
        val shortDescription: String,
        val detail: String? = null,
        val color: LogEventColor = LogEventColor.GRAY
    )
}
