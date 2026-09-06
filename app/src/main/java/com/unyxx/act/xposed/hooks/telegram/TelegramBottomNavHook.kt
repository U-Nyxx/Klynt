package com.unyxx.act.xposed.hooks.telegram

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.unyxx.act.liquidglass.injection.BottomPagerTabsWrapper
import com.unyxx.act.liquidglass.injection.LayoutInflaterHook
import com.unyxx.act.liquidglass.injection.ActivityLifecycleHook
import com.unyxx.act.util.Logger
import com.unyxx.act.xposed.prefs.RemotePrefs
import com.unyxx.act.xposed.prefs.PrefsSchema
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object TelegramBottomNavHook {
    private const val TAG = "KLYNT:Telegram"
    private const val CLASS_BOTTOM_PAGER_TABS = "org.telegram.ui.Components.BottomPagerTabs"
    private const val CLASS_BOTTOM_PAGER_TABS_VIEW = "org.telegram.ui.Components.BottomPagerTabsView"

    fun install(lpparam: XC_LoadPackage.LoadPackageParam, prefs: RemotePrefs) {
        if (!TelegramVariants.isTelegram(lpparam.packageName)) return
        if (!prefs.isFeatureEnabled(lpparam.packageName, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)) return

        val cl = lpparam.classLoader

        // Approach 1: LayoutInflater.Factory2 (XML inflation)
        LayoutInflaterHook(lpparam.packageName, prefs, setOf(CLASS_BOTTOM_PAGER_TABS, CLASS_BOTTOM_PAGER_TABS_VIEW)).install(cl)

        // Approach 2: Activity lifecycle (programmatic creation)
        ActivityLifecycleHook(lpparam.packageName, prefs) { root ->
            findBottomPagerTabs(root)
        }.install(cl)

        Logger.i { "[KLYNT] Telegram bottom nav hooks installed for ${lpparam.packageName}" }
    }

    private fun findBottomPagerTabs(root: ViewGroup): View? {
        val queue = java.util.ArrayDeque<ViewGroup>().apply { addLast(root) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (i in 0 until current.childCount) {
                val child = current.getChildAt(i)
                val cn = child.javaClass.name
                if (cn == CLASS_BOTTOM_PAGER_TABS || cn == CLASS_BOTTOM_PAGER_TABS_VIEW || cn.contains("BottomPagerTab")) {
                    return child
                }
                if (child is ViewGroup) queue.addLast(child)
            }
        }
        return null
    }
}