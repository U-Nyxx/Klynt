package com.unyxx.act.liquidglass.injection

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.unyxx.act.liquidglass.injection.BottomNavWrapper
import com.unyxx.act.liquidglass.injection.BottomPagerTabsWrapper
import com.unyxx.act.util.Logger
import com.unyxx.act.xposed.prefs.RemotePrefs
import com.unyxx.act.xposed.prefs.PrefsSchema
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

class LayoutInflaterHook(
    private val targetPkg: String,
    private val prefs: RemotePrefs,
    private val targetClasses: Set<String>
) {
    fun install(cl: ClassLoader) {
        if (!prefs.isFeatureEnabled(targetPkg, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)) return

        try {
            val inflaterClass = Class.forName("android.view.LayoutInflater", false, cl)

            // Hook setFactory2 (API 11+)
            XposedHelpers.findAndHookMethod(
                inflaterClass, "setFactory2", LayoutInflater.Factory2::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        val inflater = param.thisObject as LayoutInflater
                        val original = param.args[0] as? LayoutInflater.Factory2
                        if (original is Factory2Wrapper) return
                        val wrapper = Factory2Wrapper(original, targetPkg, prefs, targetClasses)
                        XposedHelpers.callMethod(inflater, "setFactory2", wrapper)
                    }
                }
            )

            // Hook setFactory (legacy)
            XposedHelpers.findAndHookMethod(
                inflaterClass, "setFactory", LayoutInflater.Factory::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                        val inflater = param.thisObject as LayoutInflater
                        val original = param.args[0] as? LayoutInflater.Factory
                        if (original != null && !(original is FactoryWrapper)) {
                            val wrapper = FactoryWrapper(original, targetPkg, prefs, targetClasses)
                            XposedHelpers.callMethod(inflater, "setFactory", wrapper)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Logger.e { "LayoutInflaterHook install failed for $targetPkg: ${e.message}" }
        }
    }

    private class Factory2Wrapper(
        private val original: LayoutInflater.Factory2?,
        private val targetPkg: String,
        private val prefs: RemotePrefs,
        private val targetClasses: Set<String>
    ) : LayoutInflater.Factory2 {

        override fun onCreateView(
            parent: View?, name: String, context: Context, attrs: android.util.AttributeSet
        ): View? {
            if (context.packageName != targetPkg) {
                return original?.onCreateView(parent, name, context, attrs)
            }

            if (targetClasses.contains(name)) {
                val view = original?.onCreateView(parent, name, context, attrs)
                    ?: createViewFallback(name, context, attrs)
                if (view != null && prefs.isFeatureEnabled(targetPkg, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)) {
                    return if (name.contains("BottomPagerTabs")) {
                        BottomPagerTabsWrapper(context).wrap(view, targetPkg)
                    } else {
                        BottomNavWrapper(context).wrap(view, targetPkg)
                    }
                }
                return view
            }

            return original?.onCreateView(parent, name, context, attrs)
        }

        override fun onCreateView(name: String, context: Context, attrs: android.util.AttributeSet): View? {
            return onCreateView(null, name, context, attrs)
        }

        private fun createViewFallback(
            name: String, context: Context, attrs: android.util.AttributeSet
        ): View? {
            try {
                val inflater = LayoutInflater.from(context)
                return XposedHelpers.callMethod(inflater, "createView", name, "com.unyxx.act", attrs) as View
            } catch (_: Throwable) { return null }
        }
    }

    private class FactoryWrapper(
        private val original: LayoutInflater.Factory?,
        private val targetPkg: String,
        private val prefs: RemotePrefs,
        private val targetClasses: Set<String>
    ) : LayoutInflater.Factory {

        override fun onCreateView(name: String, context: Context, attrs: android.util.AttributeSet): View? {
            if (context.packageName != targetPkg) {
                return original?.onCreateView(name, context, attrs)
            }

            if (targetClasses.contains(name)) {
                val view = original?.onCreateView(name, context, attrs)
                    ?: createViewFallback(name, context, attrs)
                if (view != null && prefs.isFeatureEnabled(targetPkg, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)) {
                    return if (name.contains("BottomPagerTabs")) {
                        BottomPagerTabsWrapper(context).wrap(view, targetPkg)
                    } else {
                        BottomNavWrapper(context).wrap(view, targetPkg)
                    }
                }
                return view
            }

            return original?.onCreateView(name, context, attrs)
        }

        private fun createViewFallback(
            name: String, context: Context, attrs: android.util.AttributeSet
        ): View? {
            try {
                val inflater = LayoutInflater.from(context)
                return XposedHelpers.callMethod(inflater, "createView", name, "com.unyxx.act", attrs) as View
            } catch (_: Throwable) { return null }
        }
    }
}