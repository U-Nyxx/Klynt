package com.unyxx.act.xposed.hooks.telegram

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.unyxx.act.liquidglass.ghost.GhostDriver
import com.unyxx.act.liquidglass.injection.BottomNavDiscovery
import com.unyxx.act.liquidglass.injection.BottomNavWrapper
import com.unyxx.act.xposed.prefs.GlassSettings
import com.unyxx.act.xposed.prefs.RemotePrefs

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
        val settings = prefs.glassSettings(packageName)
        if (!settings.active) {
            GhostDriver.disarmRetry(decorView)
            GhostDriver.restore(decorView)
            BottomNavWrapper.unwrapAll(decorView, packageName)
            return
        }
        val forceGhost =
            settings.ghostMode == com.unyxx.act.xposed.prefs.PrefsSchema.GhostMode.FORCE_GHOST
        val glassOnly =
            settings.ghostMode == com.unyxx.act.xposed.prefs.PrefsSchema.GhostMode.GLASS_ONLY
        if (!glassOnly) {
            // Ghost-first: our own bar driving the real tabs (ROM-proof
            // pixels). FORCE_GHOST never falls through to glass.
            try {
                if (GhostDriver.tryGhost(decorView, packageName, log)) return
            } catch (_: Throwable) {
            }
            if (forceGhost) {
                GhostDriver.ensureRetryArmed(decorView, packageName, log)
                return
            }
            // AUTO: arm the retry so a later-built tab row still ghosts
            // (instead of glass winning permanently on an early miss).
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
        //    Re-apply settings live — slider changes must show without
        //    a target restart.
        BottomNavWrapper.findWrapper(root, pkg)?.let { w ->
            val orig = w.originalView()
            if (orig != null && isBottomAnchored(orig, root) && isNavSized(orig, root, density)) {
                w.reconfigure(settings.intensity, settings.cornerDp, settings.blur)
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
        if (main != null) return wrap(main, pkg, log, settings, "tier=main")

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
        if (byClass != null) return wrap(byClass, pkg, log, settings, "tier=class")

        // 1.5) Semantics signal (obfuscation-proof): a real tab bar is a
        // row of labeled buttons (Chats/Contacts/Settings…); sticker
        // panels and sheets don't expose three-plus labels.
        val bySemantics = candidates
            .filter { v ->
                isBottomAnchored(v, root) &&
                    isWideEnough(v, root) &&
                    countLabeled(v) >= 3
            }
            .maxByOrNull { screenBottom(it) }
        if (bySemantics != null) return wrap(bySemantics, pkg, log, settings, "tier=semantics")

        // 2) Density-independent fallback: 48–80dp tall, near-full width,
        //    bottom edge inside the lower 15% of the screen.
        val target = candidates
            .filter { v -> isBottomAnchored(v, root) && isNavSized(v, root, density) }
            .maxByOrNull { screenBottom(it) }
        if (target != null) return wrap(target, pkg, log, settings, "tier=size")

        // Data-driven miss: log the nearest laid-out view so the next
        // iteration knows exactly what the bar looks like on this build.
        logNearMiss(root, candidates, density, log)
        return false
    }

    private fun logNearMiss(root: ViewGroup, candidates: List<View>, density: Float, log: (String) -> Unit) {
        try {
            val best = candidates.maxByOrNull { screenBottom(it) } ?: run {
                log("Miss: zero laid-out candidates under decor ${root.width}x${root.height}")
                return
            }
            val (w, h) = laidOutSize(best)
            val hDp = BottomNavDiscovery.pxToDp(h, density)
            val screenH = root.resources.displayMetrics.heightPixels
            val anchor = if (screenH > 0) screenBottom(best).toFloat() / screenH else -1f
            log("Miss: nearest=${best.javaClass.name} ${w}x${h} (${hDp.toInt()}dp) anchor=${"%.2f".format(anchor)} wFrac=${"%.2f".format(w.toFloat() / root.width.coerceAtLeast(1))}")
        } catch (_: Throwable) {
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
                for (i in v.childCount - 1 downTo 0) {
                    try {
                        stack.add(v.getChildAt(i))
                    } catch (_: Throwable) {
                    }
                }
            }
        }
        return count
    }

    /**
     * True when this ROM disables HWUI blur system-wide
     * (`debug.hwui.disable_blur`, seen on Infinix XOS). Blur-based glass
     * renders NOTHING there — the ghost bar exists precisely for this.
     * Read-only prop access, never throws.
     */
    fun isSystemBlurDisabled(): Boolean {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getDeclaredMethod(
                "get", String::class.java, String::class.java
            )
            get.invoke(null, "debug.hwui.disable_blur", "false") == "true"
        } catch (_: Throwable) {
            false
        }
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
        settings: GlassSettings,
        tier: String = "tier=?"
    ): Boolean {
        if (BottomNavWrapper.isInjected(view, pkg)) return true
        BottomNavWrapper(view.context).wrap(
            view, pkg, settings.intensity, settings.cornerDp, settings.blur
        )
        val blurOff = try {
            isSystemBlurDisabled()
        } catch (_: Throwable) {
            false
        }
        log("Injected Liquid Glass into $pkg at ${view.javaClass.name} ($tier target ${targetVersion(view, pkg)}${settings.describe()} sysBlurOff=$blurOff)")
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
        return hDp in 48f..80f && w >= root.width * 0.85
    }
}
