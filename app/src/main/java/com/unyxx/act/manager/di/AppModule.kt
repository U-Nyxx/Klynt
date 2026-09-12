package com.unyxx.act.manager.di

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.unyxx.act.KlyntApplication
import com.unyxx.act.util.Logger
import com.unyxx.act.xposed.hooks.telegram.TelegramVariants
import com.unyxx.act.xposed.hooks.twitter.TwitterVariants
import com.unyxx.act.xposed.prefs.PrefsSchema
import com.unyxx.act.xposed.scope.ScopeManager
import com.unyxx.act.xposed.scope.AppFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ServiceLocator {
    private var scopeManager: ScopeManager? = null
    private var prefs: SharedPreferences? = null
    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
        scopeManager = ScopeManager(context!!)
        prefs = context!!.getSharedPreferences(PrefsSchema.PREFS_FILE, Context.MODE_PRIVATE)
        Logger.d { "ServiceLocator initialized" }
        KlyntApplication.addServiceStateListener(
            object : KlyntApplication.ServiceStateListener {
                override fun onServiceStateChanged(service: io.github.libxposed.service.XposedService?) {
                    logEvent(
                        if (service != null) "Framework binder connected" else "Framework binder lost"
                    )
                }
            },
            notifyImmediately = false
        )
        logEvent("Manager started")
    }

    fun scopeManager(): ScopeManager = scopeManager!!
    fun prefs(): SharedPreferences = prefs!!

    /** True when the LSPosed framework binder is connected. */
    fun isServiceAlive(): Boolean = KlyntApplication.xposedService != null

    /**
     * Reliable "module active" signal: the framework is alive AND at
     * least one KLYNT target is inside the enabled scope.
     * Falls back to the local marker when the service is unreachable.
     */
    fun isModuleActive(): Boolean {
        val service = KlyntApplication.xposedService
        if (service != null) {
            return try {
                service.scope.any { pkg ->
                    TelegramVariants.isTelegram(pkg) || TwitterVariants.isTwitter(pkg)
                }
            } catch (_: Throwable) {
                localActiveFlag()
            }
        }
        return localActiveFlag()
    }

    private fun localActiveFlag(): Boolean {
        val ctx = context ?: return false
        val p = ctx.getSharedPreferences(PrefsSchema.PREFS_FILE, Context.MODE_PRIVATE)
        return p.getBoolean(PrefsSchema.MODULE_ACTIVE, false)
    }

    /** Packages currently enabled in LSPosed scope (empty when service is down). */
    fun getServiceScope(): Set<String> {
        return try {
            KlyntApplication.xposedService?.scope?.toSet() ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    /**
     * Asks the framework to enable [packageName] in scope.
     * Result arrives async via [onResult]; approved grants still need
     * a target restart to take effect.
     */
    fun requestScope(packageName: String, onResult: (approved: Boolean, message: String) -> Unit) {
        val service = KlyntApplication.xposedService
        if (service == null) {
            onResult(false, "Framework tidak terhubung")
            return
        }
        try {
            service.requestScope(
                listOf(packageName),
                object : io.github.libxposed.service.XposedService.OnScopeEventListener {
                    override fun onScopeRequestApproved(approved: List<String>) {
                        logEvent("Scope disetujui: $packageName")
                        onResult(true, "Scope disetujui — restart target")
                    }

                    override fun onScopeRequestFailed(message: String) {
                        logEvent("Scope ditolak: $packageName ($message)")
                        onResult(false, message)
                    }
                }
            )
        } catch (t: Throwable) {
            onResult(false, t.message ?: "gagal")
        }
    }

    // Global Feature Toggles
    fun isGlobalEnabled(): Boolean {
        val p = prefs ?: return true
        return p.getBoolean(PrefsSchema.GLOBAL_LIQUID_GLASS_ENABLED, true)
    }

    fun setGlobalEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.GLOBAL_LIQUID_GLASS_ENABLED, enabled)?.apply()
        writeRemoteBoolean(PrefsSchema.GLOBAL_LIQUID_GLASS_ENABLED, enabled)
        Logger.d { "Global liquid glass enabled = $enabled" }
        logEvent("Global Liquid Glass ${if (enabled) "enabled" else "disabled"}")
    }

    /** Manager-local: re-apply hooks after reboot (no remote effect). */
    fun isAutoStartEnabled(): Boolean {
        val p = prefs ?: return true
        return p.getBoolean(PrefsSchema.AUTO_START_ENABLED, true)
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.AUTO_START_ENABLED, enabled)?.apply()
        Logger.d { "Auto-start enabled = $enabled" }
    }

    // Per-app Feature
    fun isFeatureEnabled(packageName: String, feature: PrefsSchema.Feature): Boolean {
        val key = PrefsSchema.appKey(packageName, feature)
        return prefs?.getBoolean(key, feature.defaultValue) ?: feature.defaultValue
    }

    fun setFeatureEnabled(packageName: String, feature: PrefsSchema.Feature, enabled: Boolean) {
        val key = PrefsSchema.appKey(packageName, feature)
        prefs?.edit()?.putBoolean(key, enabled)?.apply()
        writeRemoteBoolean(key, enabled)
        Logger.d { "Set feature $key = $enabled for $packageName" }
        logEvent("$packageName ${feature.name} ${if (enabled) "enabled" else "disabled"}")
    }

    /** Per-app glass intensity 0..1, mirrored to hooks. */
    fun getGlassIntensity(packageName: String): Float {
        val key = PrefsSchema.intensityKey(packageName)
        return prefs?.getFloat(key, 1f) ?: 1f
    }

    fun setGlassIntensity(packageName: String, intensity: Float) {
        val clamped = intensity.coerceIn(0f, 1f)
        val key = PrefsSchema.intensityKey(packageName)
        prefs?.edit()?.putFloat(key, clamped)?.apply()
        writeRemoteFloat(key, clamped)
        Logger.d { "Set intensity $key = $clamped" }
    }

    /** Per-app corner radius in dp (999 = pill), mirrored to hooks. */
    fun getGlassCorner(packageName: String): Float {
        val key = PrefsSchema.cornerKey(packageName)
        return prefs?.getFloat(key, 999f) ?: 999f
    }

    fun setGlassCorner(packageName: String, cornerDp: Float) {
        val clamped = cornerDp.coerceIn(0f, 999f)
        val key = PrefsSchema.cornerKey(packageName)
        prefs?.edit()?.putFloat(key, clamped)?.apply()
        writeRemoteFloat(key, clamped)
        Logger.d { "Set corner $key = $clamped" }
    }

    /**
     * Mirrors a boolean into framework Remote Preferences so hooked apps
     * observe it via `getRemotePreferences`. Silent when the service is
     * down — the local write above already persisted.
     */
    private fun writeRemoteBoolean(key: String, value: Boolean) {
        try {
            KlyntApplication.xposedService
                ?.getRemotePreferences(PrefsSchema.PREFS_FILE)
                ?.edit()
                ?.putBoolean(key, value)
                ?.apply()
        } catch (_: Throwable) {
            // Service dead — local write already persisted.
        }
    }

    private fun writeRemoteFloat(key: String, value: Float) {
        try {
            KlyntApplication.xposedService
                ?.getRemotePreferences(PrefsSchema.PREFS_FILE)
                ?.edit()
                ?.putFloat(key, value)
                ?.apply()
        } catch (_: Throwable) {
            // Service dead — local write already persisted.
        }
    }

    // App Icon Loading
    fun loadAppIcon(packageName: String): Drawable? {
        val ctx = context ?: return null
        val pm = ctx.packageManager
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationIcon(info)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    // App Scope Check
    fun isAppInScope(packageName: String): Boolean {
        val pm = context?.packageManager ?: return false
        return try {
            pm.getPackageInfo(packageName, 0)
            isFeatureEnabled(packageName, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    // Manager-side event log (ring buffer, newest first, max 100).
    data class Event(
        val timestamp: Long,
        val message: String
    ) {
        val formattedTime: String
            get() = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
    }

    private val eventScope = CoroutineScope(Dispatchers.IO)
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    fun logEvent(message: String) {
        eventScope.launch {
            _events.value = (listOf(Event(System.currentTimeMillis(), message)) + _events.value).take(100)
        }
    }

    fun clearEvents() {
        eventScope.launch { _events.value = emptyList() }
    }

    // Stats
    data class Stats(
        val totalTargets: Int,
        val installedTargets: Int,
        val enabledTargets: Int,
        val telegramCount: Int,
        val twitterCount: Int
    )

    fun getStats(): Stats {
        val sm = scopeManager()
        val apps = sm.getInstallableTargetApps()
        val pm = context?.packageManager ?: return Stats(0, 0, 0, 0, 0)
        val installed = apps.keys.count { pm.getPackageInfo(it, 0) != null }
        val enabled = apps.entries.count { (_, info) ->
            isFeatureEnabled(info.packageName, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)
        }
        val telegram = apps.entries.count { it.value.family == AppFamily.TELEGRAM }
        val twitter = apps.entries.count { it.value.family == AppFamily.TWITTER }
        return Stats(apps.size, installed, enabled, telegram, twitter)
    }
}