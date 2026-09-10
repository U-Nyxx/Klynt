package com.unyxx.act.xposed

import android.app.Activity
import android.os.Bundle
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
import java.lang.reflect.Method

/**
 * KLYNT Xposed entry point (libxposed API 101).
 *
 * Lifecycle: [onModuleLoaded] once per process, then [onPackageLoaded]
 * per package. No work happens before [onModuleLoaded] — the framework
 * attaches itself automatically.
 */
class KlyntModule : XposedModule() {

    companion object {
        const val TAG = "KLYNT"
    }

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

        val prefs = runCatching { RemotePrefs.getInstance() }.getOrNull() ?: return
        // Framework-log sink threaded through discovery so every
        // successful injection lands in LSPosed logs with target version.
        val logSink: (String) -> Unit = { msg -> log(Log.INFO, TAG, msg) }
        try {
            when {
                TelegramVariants.isTelegram(pkg) ->
                    TelegramBottomNavHook.install(pkg, param.defaultClassLoader, prefs, ::hookAfterActivityCreate, logSink)
                TwitterVariants.isTwitter(pkg) ->
                    TwitterBottomNavHook.install(pkg, param.defaultClassLoader, prefs, ::hookAfterActivityCreate, logSink)
            }
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Hook install failed for $pkg: ${t.message}")
        }
    }

    /**
     * Hooks `Activity.onCreate` and runs [after] once the original
     * implementation has completed (interceptor-chain equivalent of
     * legacy `afterHookedMethod`).
     */
    private fun hookAfterActivityCreate(activityClass: Class<*>, after: (Activity) -> Unit) {
        val onCreate: Method = activityClass.getDeclaredMethod("onCreate", Bundle::class.java)
        hook(onCreate).intercept { chain ->
            val result = chain.proceed()
            try {
                (chain.thisObject as? Activity)?.let(after)
            } catch (t: Throwable) {
                log(Log.WARN, TAG, "afterCreate failed: ${t.message}")
            }
            result
        }
    }
}
