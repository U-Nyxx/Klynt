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
 * Called on every resumed activity (not just `onCreate`), so late
 * enables and recreated views are covered. Reads prefs fresh on each
 * call and unwraps when disabled — no target restart required.
 */
object TelegramBottomNavHook {

    private val NAV_CLASS_HINTS = listOf(
        "BottomNavigationView",
        "NavigationBarView",
        "BottomNav",
        "BottomTabs",
        "TabLayout"
    )

    /** Sheets/dialogs/popups live at the bottom too — never wrap those. */
    private val DENY_HINTS = listOf(
        "BottomSheet",
        "Dialog",
        "Popup",
        "Snackbar",
        "Toast",
        "Tooltip",
        "AlertDialog",
        "ActionMenu"
    )

    fun onResumed(
        activity: Activity,
        packageName: String,
        prefs: RemotePrefs,
        log: (String) -> Unit = {}
    ) {
        if (!TelegramVariants.isTelegram(packageName)) return
        if (activity.packageName != packageName) return

        val decorView = activity.window?.decorView as? ViewGroup ?: return
        if (!isActiveForApp(prefs, packageName)) {
            BottomNavWrapper.unwrapAll(decorView, packageName)
            return
        }
        BottomNavDiscovery.discover(
            decorView,
            find = { tryWrap(decorView, packageName, prefs, log) },
            onExhausted = {
                log("No bottom bar found in $packageName (target ${targetVersion(decorView, packageName)})")
            }
        )
    }

    private fun isActiveForApp(prefs: RemotePrefs, packageName: String): Boolean {
        if (!prefs.getBoolean(PrefsSchema.GLOBAL_LIQUID_GLASS_ENABLED, true)) return false
        return prefs.isFeatureEnabled(packageName, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)
    }

    private fun tryWrap(root: ViewGroup, pkg: String, prefs: RemotePrefs, log: (String) -> Unit): Boolean {
        if (root.width <= 0 || root.height <= 0) return false
        // Tablets/foldables use a side rail instead of a bottom bar —
        // wrapping here would only break layout, so stand down loudly.
        if (root.resources.configuration.smallestScreenWidthDp >= 600) {
            log("Skipping large-screen layout in $pkg (no bottom bar expected)")
            return true
        }
        val density = root.resources.displayMetrics.density

        // 0) Previously wrapped view still valid? If it outgrew nav size
        //    (wrapped too early while loading), unwrap and keep looking.
        BottomNavWrapper.findWrapper(root, pkg)?.let { w ->
            val orig = w.originalView()
            if (orig != null && isBottomAnchored(orig, root) && isNavSized(orig, root, density)) {
                return true
            }
            BottomNavWrapper.unwrapAll(root, pkg)
        }

        val all = mutableListOf<View>()
        BottomNavDiscovery.collectAll(root, all)

        // 0b) Official main tabs: BottomSheetTabs is the real bottom bar
        //     ONLY when hosted directly by ActionBarLayout (dialog sheets
        //     reuse the same class and must stay excluded).
        val main = all
            .filter { v -> v.isLaidOut && !BottomNavWrapper.isOurs(v) && isMainTabs(v) }
            .maxByOrNull { screenBottom(it) }
        if (main != null) return wrap(main, pkg, log, prefs)

        val candidates = all.filter { v ->
            v.isLaidOut && !BottomNavWrapper.isOurs(v) && !isDenied(v)
        }

        // 1) Known navigation widget classes anchored at the bottom.
        val byClass = candidates
            .filter { v ->
                NAV_CLASS_HINTS.any { v.javaClass.simpleName.contains(it, ignoreCase = true) } &&
                    isBottomAnchored(v, root)
            }
            .maxByOrNull { screenBottom(it) }
        if (byClass != null) return wrap(byClass, pkg, log, prefs)

        // 2) Density-independent fallback: 48–80dp tall, near-full width,
        //    bottom edge inside the lower 15% of the screen.
        val target = candidates
            .filter { v -> isBottomAnchored(v, root) && isNavSized(v, root, density) }
            .maxByOrNull { screenBottom(it) }
        if (target != null) return wrap(target, pkg, log, prefs)

        return false
    }

    private fun isMainTabs(view: View): Boolean {
        if (view.javaClass.simpleName != "BottomSheetTabs") return false
        val parent = view.parent as? ViewGroup ?: return false
        return parent.javaClass.simpleName == "ActionBarLayout"
    }

    /** Screen-space bottom edge — parent-relative [View.getBottom] lies for nested views. */
    private fun screenBottom(view: View): Int {
        return try {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            loc[1] + view.height
        } catch (_: Throwable) {
            view.bottom
        }
    }

    /**
     * Walks the full ancestor chain (not just 4 levels): sheets can nest
     * arbitrarily deep and a cut-off lets them slip through as nav bars.
     * Bounded by the decor root in practice; every step is a cheap
     * string check inside try/catch-free code that cannot throw.
     */
    private fun isDenied(view: View): Boolean {
        var v: View? = view
        var depth = 0
        while (v != null && depth < 32) {
            if (DENY_HINTS.any { v.javaClass.simpleName.contains(it, ignoreCase = true) }) return true
            v = v.parent as? View
            depth++
        }
        return false
    }

    private fun wrap(
        view: View,
        pkg: String,
        log: (String) -> Unit,
        prefs: RemotePrefs? = null
    ): Boolean {
        if (BottomNavWrapper.isInjected(view, pkg)) return true
        val intensity = prefs?.getFloat(PrefsSchema.intensityKey(pkg), 1f) ?: 1f
        val corner = prefs?.getFloat(PrefsSchema.cornerKey(pkg), 999f) ?: 999f
        val blur = prefs?.getBoolean(
            PrefsSchema.appKey(pkg, PrefsSchema.Feature.BLUR_ENABLED), true
        ) ?: true
        BottomNavWrapper(view.context).wrap(view, pkg, intensity, corner, blur)
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
        val screenH = root.resources.displayMetrics.heightPixels
        return screenBottom(view) >= screenH * 0.85
    }

    private fun isNavSized(view: View, root: ViewGroup, density: Float): Boolean {
        val (w, h) = laidOutSize(view)
        if (w <= 0 || h <= 0) return false
        // Hard cap: a bottom bar is never taller than 30% of the screen.
        // This alone kills the giant-lens false positive.
        if (h > root.height * 0.3) return false
        val hDp = BottomNavDiscovery.pxToDp(h, density)
        return hDp in 48f..80f && w >= root.width * 0.85
    }
}
