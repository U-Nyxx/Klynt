package com.unyxx.act.xposed.hooks.tiktok

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.unyxx.act.liquidglass.injection.BottomNavWrapper
import com.unyxx.act.liquidglass.injection.LayoutInflaterHook
import com.unyxx.act.liquidglass.injection.ActivityLifecycleHook
import com.unyxx.act.util.Logger
import com.unyxx.act.xposed.prefs.RemotePrefs
import com.unyxx.act.xposed.prefs.PrefsSchema
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object TikTokBottomNavHook {
    private const val TAG = "KLYNT:TikTok"
    // TikTok bottom nav classes - may vary by version
    private const val CLASS_BOTTOM_NAV = "com.google.android.material.bottomnavigation.BottomNavigationView"
    private const val CLASS_TIKTOK_TAB_BAR = "com.ss.android.ugc.aweme.main.MainTabBar"

    fun install(lpparam: XC_LoadPackage.LoadPackageParam, prefs: RemotePrefs) {
        if (!TikTokVariants.isTikTok(lpparam.packageName)) return
        if (!prefs.isFeatureEnabled(lpparam.packageName, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)) return

        val cl = lpparam.classLoader

        // Approach 1: LayoutInflater.Factory2
        LayoutInflaterHook(lpparam.packageName, prefs, setOf(CLASS_BOTTOM_NAV, CLASS_TIKTOK_TAB_BAR)).install(cl)

        // Approach 2: Activity lifecycle
        ActivityLifecycleHook(lpparam.packageName, prefs) { root ->
            findTikTokBottomNav(root)
        }.install(cl)

        Logger.i { "[KLYNT] TikTok bottom nav hooks installed for ${lpparam.packageName}" }
    }

    private fun findTikTokBottomNav(root: ViewGroup): View? {
        val queue = java.util.ArrayDeque<ViewGroup>().apply { addLast(root) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (i in 0 until current.childCount) {
                val child = current.getChildAt(i)
                val cn = child.javaClass.name
                if (cn == CLASS_BOTTOM_NAV || cn == CLASS_TIKTOK_TAB_BAR || cn.contains("BottomNavigation") || cn.contains("TabBar")) {
                    return child
                }
                if (child is ViewGroup) queue.addLast(child)
            }
        }
        return null
    }
}