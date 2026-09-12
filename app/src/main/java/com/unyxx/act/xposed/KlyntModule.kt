package com.unyxx.act.xposed

import android.app.Activity
import android.util.Log
import com.unyxx.act.xposed.hooks.telegram.TelegramBottomNavHook
import com.unyxx.act.xposed.hooks.telegram.TelegramVariants
import com.unyxx.act.xposed.hooks.twitter.TwitterBottomNavHook
import com.unyxx.act.xposed.hooks.twitter.TwitterVariants
import com.unyxx.act.xposed.prefs.PrefsSchema
import com.unyxx.act.xposed.prefs.RemotePrefs
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.atomic.AtomicBoolean

/**
 * KLYNT Xposed entry point (libxposed API 101).
 *
 * Runs on API 101 runtimes and newer (101 modules load fine on 102
 * frameworks). A single `Instrumentation.callActivityOnResume` hook per
 * target process covers every activity (late enables, recreations, all
 * screens) instead of one-shot `onCreate` hooks. Discovery itself is
 * idempotent.
 */
class KlyntModule : XposedModule() {

    companion object {
        const val TAG = "KLYNT"
    }

    private val resumeHookInstalled = AtomicBoolean(false)

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "onModuleLoaded: ${param.processName} framework=$frameworkName api=$apiVersion")
        try {
            RemotePrefs.init(getRemotePreferences(PrefsSchema.PREFS_FILE))
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "RemotePrefs init failed: ${t.message}")
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val pkg = param.packageName
        if (pkg == PrefsSchema.MODULE_PACKAGE) return
        if (!TelegramVariants.isTelegram(pkg) && !TwitterVariants.isTwitter(pkg)) return
        if (!resumeHookInstalled.compareAndSet(false, true)) return

        val prefs = runCatching { RemotePrefs.getInstance() }.getOrNull() ?: return
        try {
            val instrumentation = Class.forName("android.app.Instrumentation")
            val resume = instrumentation.getDeclaredMethod(
                "callActivityOnResume", Activity::class.java
            )
            hook(resume).intercept { chain ->
                val result = chain.proceed()
                try {
                    val activity = chain.args.getOrNull(0) as? Activity
                    if (activity != null && activity.packageName == pkg) {
                        onTargetActivityResumed(activity, pkg, prefs)
                    }
                } catch (t: Throwable) {
                    log(Log.WARN, TAG, "resume dispatch failed: ${t.message}")
                }
                result
            }
            log(Log.INFO, TAG, "Resume hook installed for $pkg")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Hook install failed for $pkg: ${t.message}")
        }
    }

    private fun onTargetActivityResumed(activity: Activity, pkg: String, prefs: RemotePrefs) {
        val sink: (String) -> Unit = { msg -> log(Log.INFO, TAG, msg) }
        try {
            when {
                TelegramVariants.isTelegram(pkg) ->
                    TelegramBottomNavHook.onResumed(activity, pkg, prefs, sink)
                TwitterVariants.isTwitter(pkg) ->
                    TwitterBottomNavHook.onResumed(activity, pkg, prefs, sink)
            }
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Resume handling failed for $pkg: ${t.message}")
        }
    }
}
