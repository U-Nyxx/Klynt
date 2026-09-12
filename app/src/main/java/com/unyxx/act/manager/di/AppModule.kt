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
                    if (service != null) {
                        logEvent("Framework binder connected")
                        maintainActiveFlag(service)
                        flushPendingRemote(service)
                    } else {
                        setActiveFlag(false)
                        logEvent("Framework binder lost")
                    }
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
     * Falls back to the maintained local marker (written on every
     * binder connect/disconnect) when the service is unreachable.
     */
    fun isModuleActive(): Boolean {
        val service = KlyntApplication.xposedService
        if (service != null) {
            return try {
                scopeHasTarget(service.scope)
            } catch (_: Throwable) {
                localActiveFlag()
            }
        }
        return localActiveFlag()
    }

    private fun scopeHasTarget(scope: Collection<String>): Boolean =
        scope.any { pkg ->
            TelegramVariants.isTelegram(pkg) || TwitterVariants.isTwitter(pkg)
        }

    private fun maintainActiveFlag(service: io.github.libxposed.service.XposedService) {
        try {
            setActiveFlag(scopeHasTarget(service.scope))
        } catch (_: Throwable) {
        }
    }

    private fun setActiveFlag(active: Boolean) {
        try {
            context?.getSharedPreferences(PrefsSchema.PREFS_FILE, Context.MODE_PRIVATE)
                ?.edit()?.putBoolean(PrefsSchema.MODULE_ACTIVE, active)?.apply()
        } catch (_: Throwable) {
        }
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

    /** Queued remote write, replayed on the next binder connect. */
    private data class PendingWrite(val isFloat: Boolean, val key: String, val b: Boolean, val f: Float)

    private val pendingLock = Any()
    private val pendingRemote = ArrayDeque<PendingWrite>()

    private fun enqueueRemote(write: PendingWrite) {
        synchronized(pendingLock) {
            pendingRemote.removeAll { it.key == write.key }
            pendingRemote.addLast(write)
            while (pendingRemote.size > 200) pendingRemote.removeFirst()
        }
    }

    private fun flushPendingRemote(service: io.github.libxposed.service.XposedService) {
        val batch: List<PendingWrite>
        synchronized(pendingLock) {
            if (pendingRemote.isEmpty()) return
            batch = pendingRemote.toList()
            pendingRemote.clear()
        }
        try {
            val remote = service.getRemotePreferences(PrefsSchema.PREFS_FILE)?.edit() ?: return
            batch.forEach {
                if (it.isFloat) remote.putFloat(it.key, it.f) else remote.putBoolean(it.key, it.b)
            }
            remote.apply()
            Logger.d { "Replayed ${batch.size} queued remote writes" }
        } catch (_: Throwable) {
            synchronized(pendingLock) {
                batch.forEach { enqueueRemote(it) }
            }
        }
    }

    /**
     * Mirrors a boolean into framework Remote Preferences so hooked apps
     * observe it via `getRemotePreferences`. When the service is down the
     * write is queued and replayed on reconnect — never silently lost.
     */
    private fun writeRemoteBoolean(key: String, value: Boolean) {
        val service = KlyntApplication.xposedService
        if (service == null) {
            enqueueRemote(PendingWrite(false, key, value, 0f))
            return
        }
        try {
            service.getRemotePreferences(PrefsSchema.PREFS_FILE)
                ?.edit()
                ?.putBoolean(key, value)
                ?.apply()
        } catch (_: Throwable) {
            enqueueRemote(PendingWrite(false, key, value, 0f))
        }
    }

    private fun writeRemoteFloat(key: String, value: Float) {
        val service = KlyntApplication.xposedService
        if (service == null) {
            enqueueRemote(PendingWrite(true, key, false, value))
            return
        }
        try {
            service.getRemotePreferences(PrefsSchema.PREFS_FILE)
                ?.edit()
                ?.putFloat(key, value)
                ?.apply()
        } catch (_: Throwable) {
            enqueueRemote(PendingWrite(true, key, false, value))
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

    /**
     * Single safe pattern for "is this package visible to us". Centralizes
     * the try/catch so no call-site can reintroduce the launch-crash class
     * where a throwing getPackageInfo escaped into the UI thread.
     */
    fun isInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    /** Last captured manager crash (written by the global handler), if any. */
    fun readCrashLog(): String? {
        return try {
            val f = java.io.File(context?.filesDir, "crash.log")
            if (f.exists()) f.readText().takeLast(4000) else null
        } catch (_: Throwable) {
            null
        }
    }

    fun clearCrashLog() {
        try {
            java.io.File(context?.filesDir, "crash.log").delete()
        } catch (_: Throwable) {
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
        // getInstallableTargetApps only returns installed packages, but
        // re-check defensively: getPackageInfo THROWS (never returns null)
        // for missing/invisible packages — an uncaught throw here used to
        // kill the manager on launch.
        val installed = apps.keys.count { pkg -> isInstalled(pm, pkg) }
        val enabled = apps.entries.count { (_, info) ->
            isFeatureEnabled(info.packageName, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)
        }
        val telegram = apps.entries.count { it.value.family == AppFamily.TELEGRAM }
        val twitter = apps.entries.count { it.value.family == AppFamily.TWITTER }
        return Stats(apps.size, installed, enabled, telegram, twitter)
    }
}