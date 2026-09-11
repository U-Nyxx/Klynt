package com.unyxx.act.xposed.scope

import android.content.Context
import android.content.pm.PackageManager
import com.unyxx.act.util.Logger
import com.unyxx.act.xposed.hooks.telegram.TelegramVariants
import java.util.concurrent.ConcurrentHashMap

class ScopeManager(private val context: Context) {

    fun getInstallableTargetApps(): Map<String, TargetAppInfo> {
        val pm = context.packageManager
        val result = ConcurrentHashMap<String, TargetAppInfo>()

        // Telegram variants
        TelegramVariants.ALL.forEach { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                result[pkg] = TargetAppInfo(
                    packageName = pkg,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = pm.getApplicationIcon(info),
                    family = AppFamily.TELEGRAM,
                    version = versionOf(pm, pkg)
                )
            } catch (_: PackageManager.NameNotFoundException) {}
        }

        // Twitter/X
        try {
            val pkg = "com.twitter.android"
            val info = pm.getApplicationInfo(pkg, 0)
            result[pkg] = TargetAppInfo(
                packageName = pkg,
                label = pm.getApplicationLabel(info).toString(),
                icon = pm.getApplicationIcon(info),
                family = AppFamily.TWITTER,
                version = versionOf(pm, pkg)
            )
        } catch (_: PackageManager.NameNotFoundException) {}

        return result
    }

    fun getInstalledTelegramVariants(): Set<String> {
        val pm = context.packageManager
        return TelegramVariants.ALL.filter { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }.toSet()
    }

    fun syncTelegramScope(xposedService: Any?) {
        Logger.d { "Scope sync requested" }
    }

    /** Installed target version for diagnostics; "?" when unreadable. */
    private fun versionOf(pm: PackageManager, pkg: String): String {
        return try {
            pm.getPackageInfo(pkg, 0).versionName ?: "?"
        } catch (_: PackageManager.NameNotFoundException) {
            "?"
        }
    }
}

data class TargetAppInfo(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable,
    val family: AppFamily,
    val version: String = "?"
)

enum class AppFamily {
    TELEGRAM, TWITTER
}