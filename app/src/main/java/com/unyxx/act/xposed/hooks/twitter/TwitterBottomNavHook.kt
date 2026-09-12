package com.unyxx.act.xposed.hooks.twitter

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.unyxx.act.liquidglass.ghost.GhostDriver
import com.unyxx.act.liquidglass.injection.BottomNavDiscovery
import com.unyxx.act.liquidglass.injection.BottomNavWrapper
import com.unyxx.act.xposed.prefs.GlassSettings
import com.unyxx.act.xposed.prefs.PrefsSchema
import com.unyxx.act.xposed.prefs.RemotePrefs

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
        val settings = prefs.glassSettings(packageName)
        if (!settings.active) {
            GhostDriver.disarmRetry(decorView)
            GhostDriver.restore(decorView)
            BottomNavWrapper.unwrapAll(decorView, packageName)
            return
        }
        val forceGhost = settings.ghostMode == PrefsSchema.GhostMode.FORCE_GHOST
        val glassOnly = settings.ghostMode == PrefsSchema.GhostMode.GLASS_ONLY
        if (!glassOnly) {
            try {
                if (GhostDriver.tryGhost(decorView, packageName, log)) return
            } catch (_: Throwable) {
            }
            if (forceGhost) {
                GhostDriver.ensureRetryArmed(decorView, packageName, log)
                return
            }
            GhostDriver.ensureRetryArmed(decorView, packageName, log)
        }
        BottomNavDiscovery.discover(
            decorView,
            find = { tryWrap(decorView, packageName, settings, log) },
            onExhausted = {
                log("No bottom bar found in $packageName (target ${targetVersion(decorView, packageName)})")
            }
        )
    }

    private fun tryWrap(root: ViewGroup, pkg: String, settings: GlassSettings, log: (String) -> Unit): Boolean {
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
                w.reconfigure(settings.intensity, settings.cornerDp, settings.blur)
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
            .maxByOrNull { screenBottom(it) }
        if (byClass != null) return wrap(byClass, pkg, log, settings)

        // 1.5) Semantics signal: a real tab bar is a row of labeled
        // buttons (Home/Search/Notifications/…). Media cards and sheets
        // don't expose three-plus labels. View-only signal, no Compose
        // dependency needed in the target process.
        val bySemantics = candidates
            .filter { v ->
                isBottomAnchored(v, root, 0.85) &&
                    isWideEnough(v, root) &&
                    countLabeled(v) >= 3
            }
            .maxByOrNull { screenBottom(it) }
        if (bySemantics != null) return wrap(bySemantics, pkg, log, settings)

        // 2) Density-independent fallback: 56–120dp tall (Compose bars run
        //    taller), near-full width, bottom edge in the lower 20%.
        val target = candidates
            .filter { v ->
                isBottomAnchored(v, root, 0.80) && isNavSized(v, root, density)
            }
            .maxByOrNull { screenBottom(it) }
        if (target != null) return wrap(target, pkg, log, settings)

        // Data-driven miss: nearest laid-out view so the next iteration
        // knows what X's bar looks like on this build.
        try {
            val density = root.resources.displayMetrics.density
            val screenH = root.resources.displayMetrics.heightPixels
            val best = candidates.maxByOrNull { screenBottom(it) }
            if (best == null) {
                log("Miss: zero laid-out candidates under decor ${root.width}x${root.height} in $pkg")
            } else {
                val (w, h) = laidOutSize(best)
                val hDp = BottomNavDiscovery.pxToDp(h, density)
                val anchor = if (screenH > 0) screenBottom(best).toFloat() / screenH else -1f
                log("Miss: nearest=${best.javaClass.name} ${w}x${h} (${hDp.toInt()}dp) anchor=${"%.2f".format(anchor)} labeled=${countLabeled(best)}")
            }
        } catch (_: Throwable) {
        }
        return false
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
     * Counts descendants carrying a non-blank content description.
     * Capped traversal with early exit: tab bars hit the threshold
     * within a handful of nodes, huge media trees bail out fast.
     */
    private fun countLabeled(view: View, need: Int = 3, budget: Int = 400): Int {
        var count = 0
        var remaining = budget
        val stack = ArrayDeque<View>()
        stack.add(view)
        while (stack.isNotEmpty() && remaining > 0 && count < need) {
            val v = stack.removeLast()
            remaining--
            if (!v.contentDescription.isNullOrBlank()) count++
            if (v is ViewGroup) {
                for (i in v.childCount - 1 downTo 0) stack.add(v.getChildAt(i))
            }
        }
        return count
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
        settings: GlassSettings
    ): Boolean {
        if (BottomNavWrapper.isInjected(view, pkg)) return true
        BottomNavWrapper(view.context).wrap(
            view, pkg, settings.intensity, settings.cornerDp, settings.blur
        )
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
        val screenH = root.resources.displayMetrics.heightPixels
        return screenBottom(view) >= screenH * fraction
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
