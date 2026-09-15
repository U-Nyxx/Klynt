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
        try {
            writeManagerVersion(
                "${com.unyxx.act.BuildConfig.VERSION_NAME}/" +
                    com.unyxx.act.BuildConfig.BUILD_CODENAME
            )
        } catch (_: Throwable) {
        }
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
            pkg != PrefsSchema.MODULE_PACKAGE && (
                TelegramVariants.isTelegram(pkg) || TwitterVariants.isTwitter(pkg) ||
                    pkg == "org.lsposed.manager"
                )
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
     *
     * Since v1.0.13 the module ships `staticScope=true`: the framework
     * enforces `scope.list` itself, so per-app requests are rejected by
     * design. On any failure we deep-link into LSPosed Manager instead —
     * the user lands exactly where scope lives, zero manual hunting.
     */
    fun requestScope(packageName: String, onResult: (approved: Boolean, message: String) -> Unit) {
        val service = KlyntApplication.xposedService
        if (service == null) {
            openLsposedManager()
            onResult(false, "Scope otomatis — buka LSPosed Manager")
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
                        logEvent("Scope statis aktif ($message) — buka Manager")
                        openLsposedManager()
                        onResult(false, "Scope otomatis aktif — restart target")
                    }
                }
            )
        } catch (t: Throwable) {
            logEvent("requestScope gagal (${t.message}) — buka Manager")
            openLsposedManager()
            onResult(false, "Scope otomatis aktif — restart target")
        }
    }

    /** Deep-link to LSPosed Manager; silent no-op when not installed. */
    private fun openLsposedManager() {
        try {
            val ctx = context ?: return
            val launch = ctx.packageManager.getLaunchIntentForPackage("org.lsposed.manager")
                ?: return
            launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(launch)
        } catch (_: Throwable) {
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
    private data class PendingWrite(
        val kind: Int, // 0 = boolean, 1 = float, 2 = string
        val key: String,
        val b: Boolean = false,
        val f: Float = 0f,
        val s: String = ""
    )

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
                when (it.kind) {
                    1 -> remote.putFloat(it.key, it.f)
                    2 -> remote.putString(it.key, it.s)
                    else -> remote.putBoolean(it.key, it.b)
                }
            }
            remote.apply()
            Logger.d { "Replayed ${batch.size} queued remote writes" }
        } catch (_: Throwable) {
            synchronized(pendingLock) {
                batch.forEach { enqueueRemote(it) }
            }
        }
    }

    /** Manager version stamp so hook logs identify the driving build. */
    fun writeManagerVersion(version: String) {
        writeRemoteString(PrefsSchema.MANAGER_VERSION_KEY, version)
    }

    /** Per-app ghost bar mode, mirrored to hooks. */
    fun getGhostMode(packageName: String): PrefsSchema.GhostMode {
        return try {
            PrefsSchema.GhostMode.valueOf(
                prefs?.getString(PrefsSchema.ghostModeKey(packageName), "AUTO") ?: "AUTO"
            )
        } catch (_: Throwable) {
            PrefsSchema.GhostMode.AUTO
        }
    }

    fun setGhostMode(packageName: String, mode: PrefsSchema.GhostMode) {
        prefs?.edit()?.putString(PrefsSchema.ghostModeKey(packageName), mode.name)?.apply()
        writeRemoteString(PrefsSchema.ghostModeKey(packageName), mode.name)
        Logger.d { "Set ghost mode $packageName = $mode" }
    }

    private fun writeRemoteString(key: String, value: String) {
        val service = KlyntApplication.xposedService
        if (service == null) {
            enqueueRemote(PendingWrite(2, key, s = value))
            return
        }
        try {
            service.getRemotePreferences(PrefsSchema.PREFS_FILE)
                ?.edit()
                ?.putString(key, value)
                ?.apply()
        } catch (_: Throwable) {
            enqueueRemote(PendingWrite(2, key, s = value))
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
            enqueueRemote(PendingWrite(0, key, b = value))
            return
        }
        try {
            service.getRemotePreferences(PrefsSchema.PREFS_FILE)
                ?.edit()
                ?.putBoolean(key, value)
                ?.apply()
        } catch (_: Throwable) {
            enqueueRemote(PendingWrite(0, key, b = value))
        }
    }

    private fun writeRemoteFloat(key: String, value: Float) {
        val service = KlyntApplication.xposedService
        if (service == null) {
            enqueueRemote(PendingWrite(1, key, f = value))
            return
        }
        try {
            service.getRemotePreferences(PrefsSchema.PREFS_FILE)
                ?.edit()
                ?.putFloat(key, value)
                ?.apply()
        } catch (_: Throwable) {
            enqueueRemote(PendingWrite(1, key, f = value))
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

    // Theme / Appearance
    fun getThemeMode(): PrefsSchema.ThemeMode {
        val p = prefs ?: return PrefsSchema.ThemeMode.SYSTEM
        return try {
            PrefsSchema.ThemeMode.values().first { it.defaultValue == p.getInt(PrefsSchema.THEME_MODE, 0) }
        } catch (_: Throwable) {
            PrefsSchema.ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: PrefsSchema.ThemeMode) {
        prefs?.edit()?.putInt(PrefsSchema.THEME_MODE, mode.defaultValue)?.apply()
        Logger.d { "Theme mode = $mode" }
    }

    fun isPureBlackOled(): Boolean {
        val p = prefs ?: return false
        return p.getBoolean(PrefsSchema.PURE_BLACK_OLED, false)
    }

    fun setPureBlackOled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.PURE_BLACK_OLED, enabled)?.apply()
        Logger.d { "Pure black OLED = $enabled" }
    }

    fun getAccentColor(): PrefsSchema.AccentColor {
        val p = prefs ?: return PrefsSchema.AccentColor.BLUE
        return try {
            PrefsSchema.AccentColor.values().first { it.defaultValue == p.getString(PrefsSchema.ACCENT_COLOR, "blue") }
        } catch (_: Throwable) {
            PrefsSchema.AccentColor.BLUE
        }
    }

    fun setAccentColor(color: PrefsSchema.AccentColor) {
        prefs?.edit()?.putString(PrefsSchema.ACCENT_COLOR, color.defaultValue)?.apply()
        Logger.d { "Accent color = $color" }
    }

    fun isFollowSystemAccent(): Boolean {
        val p = prefs ?: return false
        return p.getBoolean(PrefsSchema.FOLLOW_SYSTEM_ACCENT, false)
    }

    fun setFollowSystemAccent(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.FOLLOW_SYSTEM_ACCENT, enabled)?.apply()
        Logger.d { "Follow system accent = $enabled" }
    }

    // Language
    fun getLanguage(): PrefsSchema.Language {
        val p = prefs ?: return PrefsSchema.Language.SYSTEM
        return try {
            PrefsSchema.Language.values().first { it.defaultValue == p.getInt(PrefsSchema.LANGUAGE, 0) }
        } catch (_: Throwable) {
            PrefsSchema.Language.SYSTEM
        }
    }

    fun setLanguage(lang: PrefsSchema.Language) {
        prefs?.edit()?.putInt(PrefsSchema.LANGUAGE, lang.defaultValue)?.apply()
        Logger.d { "Language = $lang" }
    }

    // Log settings
    fun isLogVerbose(): Boolean {
        val p = prefs ?: return false
        return p.getBoolean(PrefsSchema.LOG_VERBOSE, false)
    }

    fun setLogVerbose(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.LOG_VERBOSE, enabled)?.apply()
        Logger.d { "Log verbose = $enabled" }
    }

    fun isLogAutoscroll(): Boolean {
        val p = prefs ?: return true
        return p.getBoolean(PrefsSchema.LOG_AUTOSCROLL, true)
    }

    fun setLogAutoscroll(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.LOG_AUTOSCROLL, enabled)?.apply()
        Logger.d { "Log autoscroll = $enabled" }
    }

    fun isLogPaused(): Boolean {
        val p = prefs ?: return false
        return p.getBoolean(PrefsSchema.LOG_PAUSED, false)
    }

    fun setLogPaused(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.LOG_PAUSED, enabled)?.apply()
        Logger.d { "Log paused = $enabled" }
    }

    fun isLogWordWrap(): Boolean {
        val p = prefs ?: return true
        return p.getBoolean(PrefsSchema.LOG_WORD_WRAP, true)
    }

    fun setLogWordWrap(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.LOG_WORD_WRAP, enabled)?.apply()
        Logger.d { "Log word wrap = $enabled" }
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
        val twitterCount: Int,
        val managerCount: Int = 0
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
        val manager = apps.entries.count { it.value.family == AppFamily.MANAGER }
        return Stats(apps.size, installed, enabled, telegram, twitter, manager)
    }
}