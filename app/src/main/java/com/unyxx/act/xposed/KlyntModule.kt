package com.unyxx.act.xposed

import com.unyxx.act.liquidglass.injection.ActivityLifecycleHook
import com.unyxx.act.liquidglass.injection.LayoutInflaterHook
import com.unyxx.act.util.Logger
import com.unyxx.act.xposed.hooks.telegram.TelegramBottomNavHook
import com.unyxx.act.xposed.hooks.telegram.TelegramVariants
import com.unyxx.act.xposed.hooks.tiktok.TikTokBottomNavHook
import com.unyxx.act.xposed.hooks.tiktok.TikTokVariants
import com.unyxx.act.xposed.prefs.RemotePrefs
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class KlyntModule : IXposedHookLoadPackage, IXposedHookZygoteInit {

    private val hookRegistry = HookRegistry()

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        Logger.init(startupParam.modulePath)
        Logger.i { "KLYNT module loaded: ${startupParam.modulePath}" }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.unyxx.act") {
            return
        }

        val prefs = RemotePrefs.getInstance()
        val cl = lpparam.classLoader
        val pkg = lpparam.packageName

        when {
            TelegramVariants.isTelegram(pkg) -> {
                hookRegistry.installTelegramHooks(pkg, cl, prefs, lpparam)
            }
            TikTokVariants.isTikTok(pkg) -> {
                hookRegistry.installTikTokHooks(pkg, cl, prefs, lpparam)
            }
        }
    }
}

class HookRegistry {
    private val installedHooks = mutableMapOf<String, Boolean>()

    fun installTelegramHooks(
        pkg: String,
        cl: ClassLoader,
        prefs: RemotePrefs,
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        val key = "telegram_$pkg"
        if (installedHooks[key] == true) return

        TelegramBottomNavHook.install(lpparam, prefs)
        installedHooks[key] = true
    }

    fun installTikTokHooks(
        pkg: String,
        cl: ClassLoader,
        prefs: RemotePrefs,
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        val key = "tiktok_$pkg"
        if (installedHooks[key] == true) return

        TikTokBottomNavHook.install(lpparam, prefs)
        installedHooks[key] = true
    }

    fun clear() {
        installedHooks.clear()
    }
}
