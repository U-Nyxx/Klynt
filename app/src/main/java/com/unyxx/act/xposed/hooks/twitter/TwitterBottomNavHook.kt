package com.unyxx.act.xposed.hooks.twitter

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.unyxx.act.liquidglass.injection.BottomNavDiscovery
import com.unyxx.act.liquidglass.injection.BottomNavWrapper
import com.unyxx.act.xposed.prefs.RemotePrefs
import com.unyxx.act.xposed.prefs.PrefsSchema

/**
 * Installs the liquid-glass overlay on the X/Twitter bottom bar.
 *
 * X renders its chrome with Compose, so discovery also matches Compose
 * host views and taller bar heights. Called on every resumed activity
 * and unwraps when disabled — no target restart required.
 */
object TwitterBottomNavHook {

    // NOTE: bare "ComposeView" deliberately absent — a full-screen
    // Compose root matches it and produces a giant lens. Compose bars
    // are still caught by the dp fallback below.
    private val NAV_CLASS_HINTS = listOf(
        "BottomNavigation",
        "BottomBar",
        "NavigationBar",
        "TabBar",
        "PivotBar"
    )

    private val AD_HINTS = listOf("ad", "promot", "sponsor")

    fun onResumed(
        activity: Activity,
        packageName: String,
        prefs: RemotePrefs,
        log: (String) -> Unit = {}
    ) {
        if (!TwitterVariants.isTwitter(packageName)) return
        if (activity.packageName != packageName) return

        val decorView = activity.window?.decorView as? ViewGroup ?: return
        if (!isActiveForApp(prefs, packageName)) {
            BottomNavWrapper.unwrapAll(decorView, packageName)
            return
        }
        BottomNavDiscovery.discover(decorView) { tryWrap(decorView, packageName, prefs, log) }
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
            if (orig != null && isBottomAnchored(orig, root, 0.90) && isNavSized(orig, root, density)) {
                return true
            }
            BottomNavWrapper.unwrapAll(root, pkg)
        }

        val all = mutableListOf<View>()
        BottomNavDiscovery.collectAll(root, all)
        val candidates = all.filter { v ->
            v.isLaidOut && !BottomNavWrapper.isOurs(v) && !looksLikeAd(v)
        }

        // 1) Navigation-ish widget classes anchored at the bottom.
        val byClass = candidates
            .filter { v ->
                NAV_CLASS_HINTS.any { v.javaClass.simpleName.contains(it, ignoreCase = true) } &&
                    isBottomAnchored(v, root, 0.90) &&
                    isWideEnough(v, root)
            }
            .maxByOrNull { it.bottom }
        if (byClass != null) return wrap(byClass, pkg, log, prefs)

        // 2) Density-independent fallback: 56–120dp tall (Compose bars run
        //    taller), near-full width, bottom edge in the lower 20%.
        val target = candidates
            .filter { v ->
                isBottomAnchored(v, root, 0.80) && isNavSized(v, root, density)
            }
            .maxByOrNull { it.bottom }
        if (target != null) return wrap(target, pkg, log, prefs)

        return false
    }

    /**
     * Sponsored cards also sit at the bottom and match the size heuristic,
     * so reject anything advertising itself as promoted content.
     */
    private fun looksLikeAd(view: View): Boolean {
        val label = view.contentDescription?.toString() ?: return false
        return AD_HINTS.any { label.contains(it, ignoreCase = true) }
    }

    private fun wrap(
        view: View,
        pkg: String,
        log: (String) -> Unit,
        prefs: RemotePrefs? = null
    ): Boolean {
        if (BottomNavWrapper.isInjected(view, pkg)) return true
        val intensity = prefs?.getFloat(PrefsSchema.intensityKey(pkg), 1f) ?: 1f
        BottomNavWrapper(view.context).wrap(view, pkg, intensity)
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

    private fun isBottomAnchored(view: View, root: ViewGroup, fraction: Double = 0.85): Boolean {
        if (view.height <= 0 && view.measuredHeight <= 0) return false
        return view.bottom >= root.height * fraction
    }

    private fun isWideEnough(view: View, root: ViewGroup): Boolean {
        val (w, _) = laidOutSize(view)
        return w > 0 && w >= root.width * 0.85
    }

    private fun isNavSized(view: View, root: ViewGroup, density: Float): Boolean {
        val (w, h) = laidOutSize(view)
        if (w <= 0 || h <= 0) return false
        // Hard cap: a bottom bar is never taller than 30% of the screen.
        // This alone kills the giant-lens false positive.
        if (h > root.height * 0.3) return false
        val hDp = BottomNavDiscovery.pxToDp(h, density)
        return hDp in 56f..120f && w >= root.width * 0.85
    }
}
