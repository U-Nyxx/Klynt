package com.unyxx.act.xposed.prefs

import android.content.SharedPreferences

/**
 * Read-only view of the module preferences from inside a hooked app
 * process (libxposed Remote Preferences, backed by the framework —
 * supports change listeners).
 *
 * NOTE: remote prefs are read-only in hooked apps. All writes happen
 * in the manager app via the Xposed service (Batch 4).
 */
class RemotePrefs private constructor(
    private val prefs: SharedPreferences
) {
    companion object {
        @Volatile
        private var INSTANCE: RemotePrefs? = null

        fun init(prefs: SharedPreferences) {
            INSTANCE = RemotePrefs(prefs)
        }

        fun getInstance(): RemotePrefs =
            INSTANCE ?: error("RemotePrefs not initialized. Call init() first.")
    }

    /**
     * One consistent snapshot of everything a resume pass needs.
     *
     * Read ONCE per `onResumed` and passed down as [GlassSettings]:
     * repeated IPC reads mid-pass could straddle a manager write and
     * mix old intensity with new blur. Snapshot-then-use keeps a pass
     * self-consistent and cuts binder round-trips per resume to five.
     */
    fun glassSettings(packageName: String): GlassSettings {
        val globalOn = prefs.getBoolean(PrefsSchema.GLOBAL_LIQUID_GLASS_ENABLED, true)
        val appOn = prefs.getBoolean(
            PrefsSchema.appKey(packageName, PrefsSchema.Feature.LIQUID_GLASS_ENABLED),
            PrefsSchema.Feature.LIQUID_GLASS_ENABLED.defaultValue
        )
        return GlassSettings(
            globalOn = globalOn,
            appOn = appOn,
            intensity = prefs.getFloat(PrefsSchema.intensityKey(packageName), 1f),
            cornerDp = prefs.getFloat(PrefsSchema.cornerKey(packageName), 999f),
            blur = prefs.getBoolean(
                PrefsSchema.appKey(packageName, PrefsSchema.Feature.BLUR_ENABLED),
                PrefsSchema.Feature.BLUR_ENABLED.defaultValue
            ),
            managerVersion = prefs.getString(PrefsSchema.MANAGER_VERSION_KEY, "?")
        )
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun getInt(key: String, default: Int = 0): Int =
        prefs.getInt(key, default)

    fun getFloat(key: String, default: Float): Float =
        prefs.getFloat(key, default)

    fun isFeatureEnabled(packageName: String, feature: PrefsSchema.Feature): Boolean {
        val key = PrefsSchema.appKey(packageName, feature)
        return prefs.getBoolean(key, feature.defaultValue)
    }
}

/**
 * Consistent per-resume snapshot — see [RemotePrefs.glassSettings].
 */
data class GlassSettings(
    val globalOn: Boolean,
    val appOn: Boolean,
    val intensity: Float,
    val cornerDp: Float,
    val blur: Boolean,
    val managerVersion: String = "?"
) {
    val active: Boolean get() = globalOn && appOn

    /** Compact render context for inject logs (version + tuning). */
    fun describe(): String =
        " mgr=$managerVersion i=${(intensity * 100).toInt()} " +
            "c=${if (cornerDp >= 999f) "pill" else cornerDp.toInt().toString()} " +
            "b=${if (blur) 1 else 0}"
}
