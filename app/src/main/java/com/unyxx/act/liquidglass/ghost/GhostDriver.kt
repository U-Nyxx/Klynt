package com.unyxx.act.liquidglass.ghost

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import com.unyxx.act.liquidglass.injection.BottomNavWrapper
import java.util.Collections
import java.util.WeakHashMap

/**
 * Ghost-driver: hides the target's real tab bar and floats [KlyntGhostBar]
 * in its place, forwarding taps to the real tabs.
 *
 * Mapping (update-proof by construction):
 * - Tabs are found by ROLE, not class: clickable views in the bottom 12%
 *   of the screen, each carrying a non-blank text label, grouped as
 *   3+ siblings in one row. Order = left-to-right slot order.
 * - Taps forward via `performClick()` on the real tab view — Telegram
 *   drives navigation itself. No internal APIs, no synthesized touches,
 *   no obfuscation-sensitive names.
 *
 * Safety (the original is never harmed):
 * - Hidden with `INVISIBLE`, never `GONE`/`removeView`: layout slot,
 *   insets and Telegram's own measuring stay intact.
 * - Hidden views are tracked per decor; [restore] sets them VISIBLE
 *   again and removes our overlay. Disable path calls restore first.
 * - Mapping failure → returns false → caller falls back to glass
 *   overlay. The screen can never go tab-less because of us.
 */
object GhostDriver {

    private data class GhostState(
        val overlay: FrameLayout,
        val bar: KlyntGhostBar,
        val hidden: List<View>,
        val tabs: List<View>,
        var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null,
        var observer: ViewTreeObserver? = null
    )

    private val states: MutableMap<ViewGroup, GhostState> =
        Collections.synchronizedMap(WeakHashMap())

    private data class RetryState(
        var listener: ViewTreeObserver.OnGlobalLayoutListener? = null,
        var observer: ViewTreeObserver? = null,
        var attachListener: View.OnAttachStateChangeListener? = null
    )

    private val retries: MutableMap<ViewGroup, RetryState> =
        Collections.synchronizedMap(WeakHashMap())

    /**
     * @return true when the ghost bar is up (or already was and got
     * re-synced); false when no mappable tab row exists.
     */
    fun tryGhost(
        decor: ViewGroup,
        pkg: String,
        log: (String) -> Unit = {}
    ): Boolean {
        if (decor.width <= 0 || decor.height <= 0) return false
        val mapping = mapTabs(decor) ?: return false

        val existing = states[decor]
        if (existing != null && sameTabs(existing, mapping)) {
            // Still valid: re-sync geometry (rotation, keyboard) + labels.
            syncGeometry(decor, existing)
            existing.bar.labels = mapping.labels
            return true
        }
        // Stale mapping (rebuilt hierarchy): tear down, rebuild below.
        if (existing != null) restore(decor)

        val hidden = mutableListOf<View>()
        // Hide the pill ROW (common parent) when it wraps only the tabs;
        // otherwise hide the tab views individually. INVISIBLE keeps layout.
        val rowParent = commonParent(mapping.tabs)
        val hideTarget: View? = if (rowParent != null && wrapsOnlyTabs(rowParent, mapping.tabs)) {
            rowParent
        } else null
        if (hideTarget != null) {
            hideTarget.visibility = View.INVISIBLE
            hidden.add(hideTarget)
        } else {
            mapping.tabs.forEach {
                it.visibility = View.INVISIBLE
                hidden.add(it)
            }
        }

        val overlay = FrameLayout(decor.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
        }
        val bar = KlyntGhostBar(decor.context).apply {
            labels = mapping.labels
            onSlotTapped = { index ->
                try {
                    mapping.tabs.getOrNull(index)?.performClick()
                } catch (_: Throwable) {
                }
            }
        }
        overlay.addView(
            bar,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        try {
            decor.addView(overlay)
        } catch (_: Throwable) {
            hidden.forEach { safeVisible(it) }
            return false
        }
        val state = GhostState(overlay, bar, hidden, mapping.tabs)
        states[decor] = state
        syncGeometry(decor, state)
        bindTracking(decor, state)
        disarmRetry(decor)
        // Ghost won after glass wrapped (retry race): exactly one visual.
        try {
            BottomNavWrapper.unwrapAll(decor, pkg)
        } catch (_: Throwable) {
        }
        log("Ghost bar active in $pkg: ${mapping.tabs.size} tabs [${mapping.labels.joinToString("/")}]")
        return true
    }

    /**
     * Arms a throttled re-attempt for decors whose tabs aren't mapped yet
     * (still loading) or rebuilt later. Without this, ghost was one-shot
     * per resume: a miss meant the glass fallback won permanently and the
     * original bar stayed visible forever ("muncul lalu menetap").
     * Disarms on success, on [restore], or on decor detach.
     */
    fun ensureRetryArmed(decor: ViewGroup, pkg: String, log: (String) -> Unit = {}) {
        if (states.containsKey(decor) || retries.containsKey(decor)) return
        if (!decor.isAttachedToWindow) return
        val retry = RetryState()
        retries[decor] = retry
        var last = 0L
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - last < 2000L) return@OnGlobalLayoutListener
            last = now
            try {
                if (!decor.isAttachedToWindow || states.containsKey(decor)) {
                    disarmRetry(decor)
                    return@OnGlobalLayoutListener
                }
                tryGhost(decor, pkg, log)
            } catch (_: Throwable) {
            }
        }
        retry.listener = listener
        val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) = disarmRetry(decor)
        }
        retry.attachListener = attach
        try {
            val observer = decor.viewTreeObserver
            if (observer.isAlive) {
                retry.observer = observer
                observer.addOnGlobalLayoutListener(listener)
            }
        } catch (_: Throwable) {
        }
        try {
            decor.addOnAttachStateChangeListener(attach)
        } catch (_: Throwable) {
        }
    }

    /** Stops a pending retry (ghost up, feature off, or superseded). */
    fun disarmRetry(decor: ViewGroup) {
        val retry = retries.remove(decor) ?: return
        try {
            retry.listener?.let { listener ->
                retry.observer?.takeIf { it.isAlive }
                    ?.removeOnGlobalLayoutListener(listener)
            }
        } catch (_: Throwable) {
        }
        try {
            retry.attachListener?.let { decor.removeOnAttachStateChangeListener(it) }
        } catch (_: Throwable) {
        }
    }

    /** Removes our overlay and un-hides everything we hid. Never throws. */
    fun restore(decor: ViewGroup) {
        val state = states.remove(decor) ?: return
        try {
            state.layoutListener?.let { listener ->
                try {
                    state.observer?.takeIf { it.isAlive }
                        ?.removeOnGlobalLayoutListener(listener)
                } catch (_: Throwable) {
                }
            }
            try {
                decor.removeView(state.overlay)
            } catch (_: Throwable) {
            }
        } finally {
            state.hidden.forEach { safeVisible(it) }
        }
    }

    /** True when a ghost overlay currently lives under [decor]. */
    fun isActive(decor: ViewGroup): Boolean = states.containsKey(decor)

    // ---- mapping ----

    private data class Mapping(val tabs: List<View>, val labels: List<String>)

    private fun sameTabs(state: GhostState, mapping: Mapping): Boolean {
        if (state.tabs.size != mapping.tabs.size) return false
        return state.tabs.zip(mapping.tabs).all { (a, b) -> a === b } &&
            state.tabs.all { it.isAttachedToWindow }
    }

    /**
     * Finds the tab row: clickable views in the bottom band, each with a
     * text label, sharing one parent, laid out left-to-right.
     */
    private fun mapTabs(decor: ViewGroup): Mapping? {
        val screenH = decor.resources.displayMetrics.heightPixels
        val screenW = decor.resources.displayMetrics.widthPixels
        if (screenH <= 0 || screenW <= 0) return null

        val all = mutableListOf<View>()
        BottomNavCollect.collect(decor, all)
        val labeled = all.filter { v ->
            v !== decor && v.isLaidOut && v.isClickable &&
                v.visibility == View.VISIBLE &&
                screenBottom(v) >= screenH * 0.88 &&
                labelOf(v).isNotBlank()
        }
        if (labeled.size < 3) return null

        // Group siblings by parent; pick the row with most tabs whose
        // combined span covers a bar-like width.
        val byParent = labeled.groupBy { it.parent as? ViewGroup }
        var best: List<View>? = null
        for ((parent, views) in byParent) {
            if (parent == null || views.size < 3) continue
            val sorted = views.sortedBy { leftOf(it) }
            if (!isRowOrdered(sorted)) continue
            val span = rightOf(sorted.last()) - leftOf(sorted.first())
            if (span < screenW * 0.5) continue
            if (best == null || sorted.size > best!!.size) best = sorted
        }
        best = (best ?: return null).take(6)
        return Mapping(best, best.map { labelOf(it) })
    }

    private fun commonParent(tabs: List<View>): ViewGroup? {
        val p = tabs.firstOrNull()?.parent as? ViewGroup ?: return null
        return if (tabs.all { it.parent === p }) p else null
    }

    /** True when [parent]'s laid-out children are all our tabs. */
    private fun wrapsOnlyTabs(parent: ViewGroup, tabs: List<View>): Boolean {
        if (parent.childCount != tabs.size) return false
        return (0 until parent.childCount).all { i -> parent.getChildAt(i) in tabs }
    }

    private fun isRowOrdered(views: List<View>): Boolean {
        for (i in 1 until views.size) {
            if (leftOf(views[i]) < rightOf(views[i - 1]) - 2) return false
            val (_, t1, _, b1) = boundsOf(views[i - 1])
            val (_, t2, _, b2) = boundsOf(views[i])
            // Same row: vertical centers within half a tab height.
            val h = (b1 - t1).coerceAtLeast(1)
            if (kotlin.math.abs((t1 + b1) / 2 - (t2 + b2) / 2) > h / 2 + 2) return false
        }
        return true
    }

    private fun labelOf(view: View): String {
        // Tab button itself rarely carries text; its TextView child does.
        if (view is android.widget.TextView) {
            return view.text?.toString()?.trim().orEmpty()
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val c = view.getChildAt(i) as? android.widget.TextView
                val t = c?.text?.toString()?.trim().orEmpty()
                if (t.isNotBlank()) return t
            }
        }
        return view.contentDescription?.toString()?.trim().orEmpty()
    }

    // ---- geometry tracking ----

    private fun syncGeometry(decor: ViewGroup, state: GhostState) {
        try {
            // Union of the live tabs' window rects, converted to decor
            // coordinates (= overlay coordinates, overlay fills decor).
            var l = Int.MAX_VALUE; var t = Int.MAX_VALUE
            var rgt = Int.MIN_VALUE; var b = Int.MIN_VALUE
            var any = false
            for (tab in state.tabs) {
                if (!tab.isAttachedToWindow) continue
                val loc = IntArray(2)
                tab.getLocationInWindow(loc)
                val w = if (tab.width > 0) tab.width else tab.measuredWidth
                val h = if (tab.height > 0) tab.height else tab.measuredHeight
                if (w <= 0 || h <= 0) continue
                l = minOf(l, loc[0]); t = minOf(t, loc[1])
                rgt = maxOf(rgt, loc[0] + w); b = maxOf(b, loc[1] + h)
                any = true
            }
            if (!any) return
            val decorLoc = IntArray(2)
            decor.getLocationInWindow(decorLoc)
            val r = Rect(
                l - decorLoc[0], t - decorLoc[1],
                rgt - decorLoc[0], b - decorLoc[1]
            )
            // Slightly pad so our pill breathes around the original slot.
            val pad = (decor.resources.displayMetrics.density * 4).toInt()
            r.inset(-pad, -pad)
            state.bar.setBarRect(r.left, r.top, r.right, r.bottom)
            state.bar.labels = state.tabs.map { labelOf(it) }
        } catch (_: Throwable) {
        }
    }

    private fun bindTracking(decor: ViewGroup, state: GhostState) {
        var last = 0L
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - last < 750L) return@OnGlobalLayoutListener
            last = now
            try {
                if (!state.overlay.isAttachedToWindow) {
                    restore(decor)
                    return@OnGlobalLayoutListener
                }
                // Tabs rebuilt (fragment change)? Mapping dies → restore so
                // the next pass re-ghosts instead of floating orphaned.
                if (state.tabs.any { !it.isAttachedToWindow }) {
                    restore(decor)
                    return@OnGlobalLayoutListener
                }
                syncGeometry(decor, state)
            } catch (_: Throwable) {
            }
        }
        state.layoutListener = listener
        try {
            val observer = decor.viewTreeObserver
            if (observer.isAlive) {
                state.observer = observer
                observer.addOnGlobalLayoutListener(listener)
            }
        } catch (_: Throwable) {
        }
    }

    private fun safeVisible(v: View) {
        try {
            if (v.visibility != View.VISIBLE) v.visibility = View.VISIBLE
        } catch (_: Throwable) {
        }
    }

    // ---- screen-space helpers (parent-relative lies for nested views) ----

    private fun screenBottom(view: View): Int {
        return try {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            loc[1] + (if (view.height > 0) view.height else view.measuredHeight)
        } catch (_: Throwable) {
            view.bottom
        }
    }

    private fun leftOf(view: View): Int {
        return try {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            loc[0]
        } catch (_: Throwable) {
            view.left
        }
    }

    private fun rightOf(view: View): Int {
        return try {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            loc[0] + (if (view.width > 0) view.width else view.measuredWidth)
        } catch (_: Throwable) {
            view.right
        }
    }

    private fun boundsOf(view: View): IntArray {
        return try {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val w = if (view.width > 0) view.width else view.measuredWidth
            val h = if (view.height > 0) view.height else view.measuredHeight
            intArrayOf(loc[0], loc[1], loc[0] + w, loc[1] + h)
        } catch (_: Throwable) {
            intArrayOf(view.left, view.top, view.right, view.bottom)
        }
    }
}

/**
 * Shared budgeted traversal (single copy used by ghost mapping so the
 * discovery budget policy can't drift between call sites).
 */
internal object BottomNavCollect {
    private const val BUDGET = 4000

    fun collect(view: View, out: MutableList<View>) {
        var remaining = BUDGET
        val stack = ArrayDeque<View>()
        stack.add(view)
        while (stack.isNotEmpty() && remaining > 0) {
            val v = stack.removeLast()
            remaining--
            out.add(v)
            if (v is ViewGroup) {
                for (i in v.childCount - 1 downTo 0) {
                    try {
                        stack.add(v.getChildAt(i))
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }
}
