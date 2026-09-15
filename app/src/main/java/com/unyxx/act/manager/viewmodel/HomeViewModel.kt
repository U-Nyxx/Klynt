package com.unyxx.act.manager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unyxx.act.manager.di.ServiceLocator
import com.unyxx.act.util.RestartDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** UI state source for the Home dashboard (module status + target stats). */
class HomeViewModel : ViewModel() {

    private val _stats = MutableStateFlow(ServiceLocator.Stats(0, 0, 0, 0, 0))
    val stats: StateFlow<ServiceLocator.Stats> = _stats.asStateFlow()

    private val _isModuleActive = MutableStateFlow(false)
    val isModuleActive: StateFlow<Boolean> = _isModuleActive.asStateFlow()

    /** True once the first binder/scope read finished (kills red flash). */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _checks = MutableStateFlow<List<SetupCheck>>(emptyList())
    val checks: StateFlow<List<SetupCheck>> = _checks.asStateFlow()

    /** True when usage access is missing (one-tap grant, then forever auto). */
    private val _needsUsagePermission = MutableStateFlow(false)
    val needsUsagePermission: StateFlow<Boolean> = _needsUsagePermission.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = ServiceLocator.getStats()
            val active = ServiceLocator.isModuleActive()
            // SCOPE is an independent signal (grants exist), NOT an alias
            // of ACTIVE (hooks live) — conflating them hid real state.
            val scopeGranted = try {
                ServiceLocator.getServiceScope().any { pkg ->
                    pkg != "com.unyxx.act" && (
                        com.unyxx.act.xposed.hooks.telegram.TelegramVariants.isTelegram(pkg) ||
                            com.unyxx.act.xposed.hooks.twitter.TwitterVariants.isTwitter(pkg) ||
                            pkg == "org.lsposed.manager"
                        )
                }
            } catch (_: Throwable) {
                false
            }
            val (restartDone, restartDetail) = detectRestart()
            _needsUsagePermission.value = restartDetail == RESTART_NEEDS_PERMISSION
            _stats.value = stats
            _isModuleActive.value = active
            _loaded.value = true
            _checks.value = listOf(
                SetupCheck(
                    key = CheckKey.BINDER,
                    done = ServiceLocator.isServiceAlive()
                ),
                SetupCheck(
                    key = CheckKey.SCOPE,
                    done = scopeGranted
                ),
                SetupCheck(
                    key = CheckKey.INSTALLED,
                    done = stats.installedTargets > 0
                ),
                SetupCheck(
                    key = CheckKey.RESTART,
                    done = restartDone,
                    detail = restartDetail.takeIf { it != RESTART_NEEDS_PERMISSION }
                )
            )
        }
    }

    /**
     * Automatic restart state — no manual button, ever.
     *
     * A hook is live only if its process started after boot (framework
     * injects at process start), so "restarted" == "foregrounded since
     * boot" via UsageStats. Returns (done, detail); detail doubles as
     * the permission-missing signal for the UI tap handler.
     */
    private fun detectRestart(): Pair<Boolean, String> {
        val ctx = ServiceLocator.appContext()
        val targets = try {
            val apps = ServiceLocator.scopeManager().getInstallableTargetApps()
            apps.entries
                .filter { (_, info) ->
                    ServiceLocator.isFeatureEnabled(
                        info.packageName,
                        com.unyxx.act.xposed.prefs.PrefsSchema.Feature.LIQUID_GLASS_ENABLED
                    )
                }
                .map { it.key }
                .toSet()
        } catch (_: Throwable) {
            emptySet()
        }
        if (targets.isEmpty()) return true to "–"
        if (ctx == null || !RestartDetector.hasUsagePermission(ctx)) {
            return false to RESTART_NEEDS_PERMISSION
        }
        val boot = RestartDetector.bootMs()
        val seen = RestartDetector.lastForegroundMs(ctx, targets, boot)
        val missing = targets.filter { (seen[it] ?: 0L) <= boot }
        if (missing.isEmpty()) {
            val times = targets.sorted().mapNotNull { seen[it] }.map { fmtTime(it) }
            return true to times.distinct().joinToString(", ")
        }
        val short = missing.map { it.substringAfterLast('.') }
        return false to short.joinToString(", ")
    }

    private fun fmtTime(ms: Long): String {
        return try {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
        } catch (_: Throwable) {
            ""
        }
    }

    companion object {
        const val RESTART_NEEDS_PERMISSION = "NEEDS_USAGE_PERMISSION"
    }
}

/** Setup checklist step shown on Home. Text resolved in UI for i18n. */
data class SetupCheck(
    val key: CheckKey,
    val done: Boolean,
    /** Live detail (e.g. hook-seen times); null hides the line. */
    val detail: String? = null
)

enum class CheckKey {
    BINDER, SCOPE, INSTALLED, RESTART
}
