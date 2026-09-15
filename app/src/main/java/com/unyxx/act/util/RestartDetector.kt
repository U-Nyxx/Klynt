package com.unyxx.act.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings

/**
 * Automatic target-restart detection — the manual "mark restarted"
 * button is gone for good.
 *
 * How: a hook can only be live if its target process started *after*
 * the current boot (LSPosed injects at process start). So "restarted"
 * == "foregrounded since boot", read from [UsageStatsManager] — a
 * documented API, no root, no log scraping, no guessing.
 *
 * Cost: one system Settings toggle (usage access), granted once, then
 * forever automatic. Without it we degrade to optimistic (binder +
 * scope + installed) and never ask twice — no manual state, ever.
 */
object RestartDetector {

    /** Epoch ms of the current boot. Anything foregrounded after this restarted. */
    fun bootMs(): Long =
        System.currentTimeMillis() - SystemClock.elapsedRealtime()

    fun hasUsagePermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    fun usageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Last foreground epoch-ms per package in [packages] (0 = unseen).
     * Windowed to [sinceMs]..now so very first launch after install
     * doesn't scan all history. Never throws.
     */
    fun lastForegroundMs(
        context: Context,
        packages: Set<String>,
        sinceMs: Long = bootMs()
    ): Map<String, Long> {
        if (packages.isEmpty() || !hasUsagePermission(context)) return emptyMap()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyMap()
            val now = System.currentTimeMillis()
            val out = mutableMapOf<String, Long>()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                sinceMs.coerceAtMost(now), now
            ) ?: return emptyMap()
            for (s in stats) {
                val pkg = s.packageName
                if (pkg in packages && s.lastTimeUsed >= sinceMs) {
                    out[pkg] = maxOf(out[pkg] ?: 0L, s.lastTimeUsed)
                }
            }
            out
        } catch (_: Throwable) {
            emptyMap()
        }
    }
}
