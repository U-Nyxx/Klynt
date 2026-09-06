package com.unyxx.act.liquidglass.injection

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.unyxx.act.liquidglass.injection.BottomNavWrapper
import com.unyxx.act.liquidglass.injection.BottomPagerTabsWrapper
import com.unyxx.act.util.Logger
import com.unyxx.act.xposed.prefs.RemotePrefs
import com.unyxx.act.xposed.prefs.PrefsSchema
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

class ActivityLifecycleHook(
    private val targetPkg: String,
    private val prefs: RemotePrefs,
    private val finder: (ViewGroup) -> View?
) {
    fun install(cl: ClassLoader) {
        if (!prefs.isFeatureEnabled(targetPkg, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)) return

        try {
            val activityClass = Class.forName("android.app.Activity", false, cl)
            XposedHelpers.findAndHookMethod(
                activityClass, "onCreate", Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        val activity = param.thisObject as Activity
                        if (activity.packageName != targetPkg) return

                        activity.window?.decorView?.viewTreeObserver
                            ?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                                override fun onGlobalLayout() {
                                    activity.window?.decorView?.viewTreeObserver
                                        ?.removeOnGlobalLayoutListener(this)
                                    injectIntoActivity(activity)
                                }
                            })
                    }
                }
            )
        } catch (e: Throwable) {
            Logger.e { "ActivityLifecycleHook install failed for $targetPkg: ${e.message}" }
        }
    }

    private fun injectIntoActivity(activity: Activity) {
        val decorView = activity.window?.decorView as? ViewGroup ?: return
        val targetView = finder(decorView) ?: return

        val pkg = activity.packageName
        if (BottomNavWrapper.isInjected(targetView, pkg) || BottomPagerTabsWrapper.isInjected(targetView, pkg)) return

        val wrapper = if (targetView.javaClass.name.contains("BottomPagerTabs")) {
            BottomPagerTabsWrapper(activity).wrap(targetView, pkg)
        } else {
            BottomNavWrapper(activity).wrap(targetView, pkg)
        }

        val parent = targetView.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(targetView)
        parent.removeView(targetView)
        parent.addView(wrapper, index)

        Logger.i { "[KLYNT] Injected Liquid Glass into $pkg at ${targetView.javaClass.simpleName}" }
    }
}