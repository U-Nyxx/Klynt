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

    private const val MAX_EVENTS = 2000
    private const val BATCH_READ_LIMIT = 50

    private val eventLock = Any()
    private val _logEvents = MutableStateFlow<List<PrefsSchema.LogEvent>>(emptyList())
    val logEvents: StateFlow<List<PrefsSchema.LogEvent>> = _logEvents.asStateFlow()

    private var nextSequenceId: Long = 0
        get() {
            val p = prefs ?: return 0
            return p.getLong(PrefsSchema.EVENT_SEQUENCE_ID, 0)
        }
        set(value) {
            prefs?.edit()?.putLong(PrefsSchema.EVENT_SEQUENCE_ID, value)?.apply()
            field = value
        }

    fun init(ctx: Context) {
        context = ctx.applicationContext
        scopeManager = ScopeManager(context!!)
        prefs = context!!.getSharedPreferences(PrefsSchema.PREFS_FILE, Context.MODE_PRIVATE)
        nextSequenceId = prefs?.getLong(PrefsSchema.EVENT_SEQUENCE_ID, 0) ?: 0
        Logger.d { "ServiceLocator initialized (sequenceId=$nextSequenceId)" }
        KlyntApplication.addServiceStateListener(
            object : KlyntApplication.ServiceStateListener {
                override fun onServiceStateChanged(service: io.github.libxposed.service.XposedService?) {
                    if (service != null) {
                        logEvent(PrefsSchema.LogEventType.BINDER_CONNECT, "", "Framework binder connected")
                        maintainActiveFlag(service)
                        flushPendingRemote(service)
                    } else {
                        setActiveFlag(false)
                        logEvent(PrefsSchema.LogEventType.BINDER_DISCONNECT, "", "Framework binder lost")
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
        logEvent(PrefsSchema.LogEventType.STATUS_INFO, "", "Manager started")
    }

    fun scopeManager(): ScopeManager = scopeManager!!
    fun prefs(): SharedPreferences = prefs!!

    fun isServiceAlive(): Boolean = KlyntApplication.xposedService != null

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

    fun getServiceScope(): Set<String> {
        return try {
            KlyntApplication.xposedService?.scope?.toSet() ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

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
                        logEvent(PrefsSchema.LogEventType.SCOPE_CHANGE, "", "Scope disetujui: $packageName")
                        onResult(true, "Scope disetujui — restart target")
                    }

                    override fun onScopeRequestFailed(message: String) {
                        logEvent(PrefsSchema.LogEventType.HOOK_FAIL, "", "Scope ditolak: $packageName ($message)")
                        onResult(false, message)
                    }
                }
            )
        } catch (t: Throwable) {
            onResult(false, t.message ?: "gagal")
        }
    }

    fun isGlobalEnabled(): Boolean {
        val p = prefs ?: return true
        return p.getBoolean(PrefsSchema.GLOBAL_LIQUID_GLASS_ENABLED, true)
    }

    fun setGlobalEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.GLOBAL_LIQUID_GLASS_ENABLED, enabled)?.apply()
        writeRemoteBoolean(PrefsSchema.GLOBAL_LIQUID_GLASS_ENABLED, enabled)
        logEvent(PrefsSchema.LogEventType.STATUS_INFO, "", "Global Liquid Glass ${if (enabled) "enabled" else "disabled"}")
    }

    fun isAutoStartEnabled(): Boolean {
        val p = prefs ?: return true
        return p.getBoolean(PrefsSchema.AUTO_START_ENABLED, true)
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.AUTO_START_ENABLED, enabled)?.apply()
    }

    fun isFeatureEnabled(packageName: String, feature: PrefsSchema.Feature): Boolean {
        val key = PrefsSchema.appKey(packageName, feature)
        return prefs?.getBoolean(key, feature.defaultValue) ?: feature.defaultValue
    }

    fun setFeatureEnabled(packageName: String, feature: PrefsSchema.Feature, enabled: Boolean) {
        val key = PrefsSchema.appKey(packageName, feature)
        prefs?.edit()?.putBoolean(key, enabled)?.apply()
        writeRemoteBoolean(key, enabled)
        logEvent(PrefsSchema.LogEventType.STATUS_INFO, "", "$packageName ${feature.name} ${if (enabled) "enabled" else "disabled"}")
    }

    fun getGlassIntensity(packageName: String): Float {
        val key = PrefsSchema.intensityKey(packageName)
        return prefs?.getFloat(key, 1f) ?: 1f
    }

    fun setGlassIntensity(packageName: String, intensity: Float) {
        val clamped = intensity.coerceIn(0f, 1f)
        val key = PrefsSchema.intensityKey(packageName)
        prefs?.edit()?.putFloat(key, clamped)?.apply()
        writeRemoteFloat(key, clamped)
    }

    fun getGlassCorner(packageName: String): Float {
        val key = PrefsSchema.cornerKey(packageName)
        return prefs?.getFloat(key, 999f) ?: 999f
    }

    fun setGlassCorner(packageName: String, cornerDp: Float) {
        val clamped = cornerDp.coerceIn(0f, 999f)
        val key = PrefsSchema.cornerKey(packageName)
        prefs?.edit()?.putFloat(key, clamped)?.apply()
        writeRemoteFloat(key, clamped)
    }

    private val pendingLock = Any()
    private val pendingRemote = ArrayDeque<PendingWrite>()

    private data class PendingWrite(
        val kind: Int,
        val key: String,
        val b: Boolean = false,
        val f: Float = 0f,
        val s: String = ""
    )

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

    fun writeManagerVersion(version: String) {
        writeRemoteString(PrefsSchema.MANAGER_VERSION_KEY, version)
    }

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
    }

    private fun writeRemoteString(key: String, value: String) {
        val service = KlyntApplication.xposedService
        if (service == null) {
            enqueueRemote(PendingWrite(2, key, s = value))
            return
        }
        try {
            service.getRemotePreferences(PrefsSchema.PREFS_FILE)
                ?.edit()?.putString(key, value)?.apply()
        } catch (_: Throwable) {
            enqueueRemote(PendingWrite(2, key, s = value))
        }
    }

    private fun writeRemoteBoolean(key: String, value: Boolean) {
        val service = KlyntApplication.xposedService
        if (service == null) {
            enqueueRemote(PendingWrite(0, key, b = value))
            return
        }
        try {
            service.getRemotePreferences(PrefsSchema.PREFS_FILE)
                ?.edit()?.putBoolean(key, value)?.apply()
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
                ?.edit()?.putFloat(key, value)?.apply()
        } catch (_: Throwable) {
            enqueueRemote(PendingWrite(1, key, f = value))
        }
    }

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

    fun readCrashLog(): String? {
        return try {
            val f = java.io.File(context?.filesDir, "crash.log")
            if (f.exists()) f.readText().takeLast(4000) else null
        } catch (_: Throwable) {
            null
        }
    }

    fun clearCrashLog() {
        try { java.io.File(context?.filesDir, "crash.log").delete() } catch (_: Throwable) { }
    }

    fun getThemeMode(): PrefsSchema.ThemeMode {
        val p = prefs ?: return PrefsSchema.ThemeMode.SYSTEM
        return try {
            PrefsSchema.ThemeMode.values().first { it.defaultValue == p.getInt(PrefsSchema.THEME_MODE, 0) }
        } catch (_: Throwable) { PrefsSchema.ThemeMode.SYSTEM }
    }

    fun setThemeMode(mode: PrefsSchema.ThemeMode) {
        prefs?.edit()?.putInt(PrefsSchema.THEME_MODE, mode.defaultValue)?.apply()
    }

    fun isPureBlackOled(): Boolean {
        val p = prefs ?: return false
        return p.getBoolean(PrefsSchema.PURE_BLACK_OLED, false)
    }

    fun setPureBlackOled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.PURE_BLACK_OLED, enabled)?.apply()
    }

    fun getAccentColor(): PrefsSchema.AccentColor {
        val p = prefs ?: return PrefsSchema.AccentColor.BLUE
        return try {
            PrefsSchema.AccentColor.values().first { it.defaultValue == p.getString(PrefsSchema.ACCENT_COLOR, "blue") }
        } catch (_: Throwable) { PrefsSchema.AccentColor.BLUE }
    }

    fun setAccentColor(color: PrefsSchema.AccentColor) {
        prefs?.edit()?.putString(PrefsSchema.ACCENT_COLOR, color.defaultValue)?.apply()
    }

    fun isFollowSystemAccent(): Boolean {
        val p = prefs ?: return false
        return p.getBoolean(PrefsSchema.FOLLOW_SYSTEM_ACCENT, false)
    }

    fun setFollowSystemAccent(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.FOLLOW_SYSTEM_ACCENT, enabled)?.apply()
    }

    fun getLanguage(): PrefsSchema.Language {
        val p = prefs ?: return PrefsSchema.Language.SYSTEM
        return try {
            PrefsSchema.Language.values().first { it.defaultValue == p.getInt(PrefsSchema.LANGUAGE, 0) }
        } catch (_: Throwable) { PrefsSchema.Language.SYSTEM }
    }

    fun setLanguage(lang: PrefsSchema.Language) {
        prefs?.edit()?.putInt(PrefsSchema.LANGUAGE, lang.defaultValue)?.apply()
    }

    fun isLogVerbose(): Boolean {
        val p = prefs ?: return false
        return p.getBoolean(PrefsSchema.LOG_VERBOSE, false)
    }

    fun setLogVerbose(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.LOG_VERBOSE, enabled)?.apply()
    }

    fun isLogAutoscroll(): Boolean {
        val p = prefs ?: return true
        return p.getBoolean(PrefsSchema.LOG_AUTOSCROLL, true)
    }

    fun setLogAutoscroll(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.LOG_AUTOSCROLL, enabled)?.apply()
    }

    fun isLogPaused(): Boolean {
        val p = prefs ?: return false
        return p.getBoolean(PrefsSchema.LOG_PAUSED, false)
    }

    fun setLogPaused(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.LOG_PAUSED, enabled)?.apply()
    }

    fun isLogWordWrap(): Boolean {
        val p = prefs ?: return true
        return p.getBoolean(PrefsSchema.LOG_WORD_WRAP, true)
    }

    fun setLogWordWrap(enabled: Boolean) {
        prefs?.edit()?.putBoolean(PrefsSchema.LOG_WORD_WRAP, enabled)?.apply()
    }

    fun isAppInScope(packageName: String): Boolean {
        val pm = context?.packageManager ?: return false
        return try {
            pm.getPackageInfo(packageName, 0)
            isFeatureEnabled(packageName, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)
        } catch (_: PackageManager.NameNotFoundException) { false }
    }

    // ===== REALTIME LOG TRANSPORT =====

    /**
     * Structured log event with sequence ID, type, package chain, and color.
     * Adds to bounded ring buffer (max 2000 events), auto-prunes oldest.
     */
    fun logEvent(
        type: PrefsSchema.LogEventType,
        packageChain: String,
        shortDescription: String,
        detail: String? = null,
        color: PrefsSchema.LogEventColor = PrefsSchema.LogEventColor.GRAY
    ) {
        val seq = nextSequenceId++
        val event = PrefsSchema.LogEvent(
            sequenceId = seq,
            timestamp = System.currentTimeMillis(),
            type = type,
            packageChain = packageChain,
            shortDescription = shortDescription,
            detail = detail,
            color = color
        )
        synchronized(eventLock) {
            val current = _logEvents.value
            val updated = listOf(event) + current
            _logEvents.value = if (updated.size > MAX_EVENTS) updated.take(MAX_EVENTS) else updated
        }
    }

    /** Backward-compatible simple log event. */
    fun logEvent(message: String) {
        logEvent(PrefsSchema.LogEventType.STATUS_INFO, "", message)
    }

    /**
     * Batch read: returns up to [limit] events with sequenceId < [afterSequenceId].
     * Used by the manager to read new events in small batches (no per-frame IPC).
     */
    fun getEventsBatch(afterSequenceId: Long, limit: Int = BATCH_READ_LIMIT): List<PrefsSchema.LogEvent> {
        synchronized(eventLock) {
            return _logEvents.value
                .filter { it.sequenceId > afterSequenceId }
                .sortedByDescending { it.sequenceId }
                .take(limit)
        }
    }

    /** Returns all events (for initial load). */
    fun getAllLogEvents(): List<PrefsSchema.LogEvent> {
        synchronized(eventLock) { return _logEvents.value }
    }

    /** Returns the highest sequence ID currently in the buffer. */
    fun getLastSequenceId(): Long = nextSequenceId

    fun clearLogEvents() {
        synchronized(eventLock) { _logEvents.value = emptyList() }
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
        val installed = apps.keys.count { pkg -> isInstalled(pm, pkg) }
        val enabled = apps.entries.count { (_, info) ->
            isFeatureEnabled(info.packageName, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)
        }
        val telegram = apps.entries.count { it.value.family == AppFamily.TELEGRAM }
        val twitter = apps.entries.count { it.value.family == AppFamily.TWITTER }
        return Stats(apps.size, installed, enabled, telegram, twitter)
    }
}
