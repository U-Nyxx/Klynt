package com.unyxx.act.xposed.hooks.telegram

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.unyxx.act.liquidglass.injection.BottomNavDiscovery
import com.unyxx.act.liquidglass.injection.BottomNavWrapper
import com.unyxx.act.xposed.prefs.RemotePrefs
import com.unyxx.act.xposed.prefs.PrefsSchema

/**
 * Installs the liquid-glass overlay on Telegram-family bottom navigation.
 *
 * Discovery is layout-driven ([BottomNavDiscovery]) rather than a single
 * fixed delay: class-name match first, density-independent size heuristic
 * as fallback. Safe to run repeatedly — injection is idempotent.
 */
object TelegramBottomNavHook {
    private const val LAUNCH_ACTIVITY = "org.telegram.ui.LaunchActivity"

    private val NAV_CLASS_HINTS = listOf(
        "BottomNavigationView",
        "NavigationBarView",
        "BottomNav",
        "BottomTabs",
        "TabLayout"
    )

    fun install(
        packageName: String,
        classLoader: ClassLoader,
        prefs: RemotePrefs,
        hookAfterCreate: (Class<*>, (Activity) -> Unit) -> Unit,
        log: (String) -> Unit = {}
    ) {
        if (!TelegramVariants.isTelegram(packageName)) return
        if (!prefs.isFeatureEnabled(packageName, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)) return

        val activityClass = try {
            Class.forName(LAUNCH_ACTIVITY, false, classLoader)
        } catch (_: ClassNotFoundException) {
            return
        }

        hookAfterCreate(activityClass) { activity ->
            if (activity.packageName != packageName) return@hookAfterCreate

            val decorView = activity.window?.decorView as? ViewGroup ?: return@hookAfterCreate
            BottomNavDiscovery.discover(decorView) { tryWrap(decorView, packageName, log) }
        }
    }

    private fun tryWrap(root: ViewGroup, pkg: String, log: (String) -> Unit): Boolean {
        if (root.width <= 0 || root.height <= 0) return false
        val density = root.resources.displayMetrics.density

        val all = mutableListOf<View>()
        BottomNavDiscovery.collectAll(root, all)

        // 1) Known navigation widget classes anchored at the bottom.
        val byClass = all
            .filter { v ->
                NAV_CLASS_HINTS.any { v.javaClass.simpleName.contains(it, ignoreCase = true) } &&
                    isBottomAnchored(v, root)
            }
            .maxByOrNull { it.bottom }
        if (byClass != null) return wrap(byClass, pkg, log)

        // 2) Density-independent fallback: 48–80dp tall, near-full width,
        //    bottom edge inside the lower 15% of the screen.
        val target = all
            .filter { v -> isBottomAnchored(v, root) && isNavSized(v, root, density) }
            .maxByOrNull { it.bottom }
        if (target != null) return wrap(target, pkg, log)

        return false
    }

    private fun wrap(view: View, pkg: String, log: (String) -> Unit): Boolean {
        if (BottomNavWrapper.isInjected(view, pkg)) return true
        BottomNavWrapper(view.context).wrap(view, pkg)
        log("Injected Liquid Glass into $pkg at ${view.javaClass.name} (target ${targetVersion(view, pkg)})")
        return true
    }

    /** Target app version for diagnostics (bug reports, obfuscation drift). */
    private fun targetVersion(view: View, pkg: String): String {
        return try {
            view.context.packageManager.getPackageInfo(pkg, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    private fun laidOutSize(view: View): Pair<Int, Int> {
        val w = if (view.width > 0) view.width else view.measuredWidth
        val h = if (view.height > 0) view.height else view.measuredHeight
        return w to h
    }

    private fun isBottomAnchored(view: View, root: ViewGroup): Boolean {
        if (view.height <= 0 && view.measuredHeight <= 0) return false
        return view.bottom >= root.height * 0.85
    }

    private fun isNavSized(view: View, root: ViewGroup, density: Float): Boolean {
        val (w, h) = laidOutSize(view)
        if (w <= 0 || h <= 0) return false
        val hDp = BottomNavDiscovery.pxToDp(h, density)
        return hDp in 48f..80f && w >= root.width * 0.85
    }
}
