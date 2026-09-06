package com.unyxx.act.xposed.scope

import android.content.Context
import android.content.pm.PackageManager
import com.unyxx.act.util.Logger
import com.unyxx.act.xposed.hooks.telegram.TelegramVariants
import com.unyxx.act.xposed.hooks.tiktok.TikTokVariants
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
                    family = AppFamily.TELEGRAM
                )
            } catch (_: PackageManager.NameNotFoundException) {}
        }

        // TikTok variants
        TikTokVariants.ALL.forEach { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                result[pkg] = TargetAppInfo(
                    packageName = pkg,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = pm.getApplicationIcon(info),
                    family = AppFamily.TIKTOK
                )
            } catch (_: PackageManager.NameNotFoundException) {}
        }

        // Add other families here as needed
        val otherPackages = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.google.android.youtube" to "YouTube",
            "com.google.android.apps.youtube.music" to "YouTube Music",
            "com.instagram.android" to "Instagram",
            "com.twitter.android" to "Twitter/X",
            "com.reddit.frontpage" to "Reddit"
        )

        otherPackages.forEach { (pkg, label) ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                result[pkg] = TargetAppInfo(
                    packageName = pkg,
                    label = label,
                    icon = pm.getApplicationIcon(info),
                    family = AppFamily.OTHER
                )
            } catch (_: PackageManager.NameNotFoundException) {}
        }

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
        // XposedService integration would go here when available
        Logger.d { "Scope sync requested" }
    }
}

data class TargetAppInfo(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable,
    val family: AppFamily
)

enum class AppFamily {
    TELEGRAM, TIKTOK, OTHER
}