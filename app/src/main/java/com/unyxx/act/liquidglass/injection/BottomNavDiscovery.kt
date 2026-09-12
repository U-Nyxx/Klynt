package com.unyxx.act.liquidglass.injection

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Robust bottom-nav discovery for injected processes.
 *
 * Runs [find] immediately, on layout passes (throttled) and on a backoff
 * schedule. The layout probe stays armed until the decor detaches, so
 * views that are destroyed and recreated later (e.g. Telegram rebuilding
 * its tabs on fragment changes) get wrapped without waiting for the next
 * activity resume. Timed retries only bound the [onExhausted] log.
 * [find] must be idempotent — it is called many times.
 *
 * One arming per decor root: repeat resumes reuse the existing probe
 * instead of piling up handlers + layout listeners (each retained
 * listener pins the whole hierarchy — a slow leak on every resume).
 */
object BottomNavDiscovery {

    private val DELAYS_MS = longArrayOf(500L, 1000L, 2000L, 3000L, 5000L)
    private const val PROBE_THROTTLE_MS = 1500L

    /** Max nodes per traversal — Telegram/X trees reach thousands. */
    private const val COLLECT_BUDGET = 4000

    private val armed = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<ViewGroup, Boolean>())
    )

    /**
     * Iterative, budgeted depth-first collection. The old recursion blew
     * the stack (`StackOverflowError`, swallowed as silent `false`) on
     * heavy chat hierarchies — exactly where the bar matters most.
     */
    fun collectAll(view: View, out: MutableList<View>) {
        var remaining = COLLECT_BUDGET
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
                        // Mutated mid-walk — skip the child.
                    }
                }
            }
        }
    }

    /** Pixels to dp using the host view hierarchy density. */
    fun pxToDp(px: Int, density: Float): Float =
        if (density > 0f) px / density else px.toFloat()

    /**
     * @param root decor view of the target activity.
     * @param find returns true once the glass is injected (or already present).
     * @param onExhausted runs once when timed retries end with no match, so
     * a missing nav is visible in logs instead of silent.
     */
    fun discover(root: ViewGroup, find: () -> Boolean, onExhausted: () -> Unit = {}) {
        // Same decor re-armed by a later resume: reuse, don't pile up.
        if (!armed.add(root)) return
        val handler = Handler(Looper.getMainLooper())
        val finished = AtomicBoolean(false)
        val foundSticky = AtomicBoolean(false)
        val logged = AtomicBoolean(false)
        val attempts = AtomicInteger(0)
        val lastProbe = AtomicLong(0)
        lateinit var layoutListener: ViewTreeObserver.OnGlobalLayoutListener
        lateinit var attachListener: View.OnAttachStateChangeListener
        // The observer instance at REGISTER time — removing from
        // root.viewTreeObserver at cleanup can hit a fresh (wrong)
        // instance after detach and leak the listener instead.
        var registeredObserver: ViewTreeObserver? = null

        fun cleanup() {
            armed.remove(root)
            handler.removeCallbacksAndMessages(null)
            try {
                registeredObserver
                    ?.takeIf { it.isAlive }
                    ?.removeOnGlobalLayoutListener(layoutListener)
            } catch (_: Throwable) {
                // Observer dead or never registered — nothing to clean up.
            }
            registeredObserver = null
            try {
                root.removeOnAttachStateChangeListener(attachListener)
            } catch (_: Throwable) {
            }
        }

        fun finish() {
            if (finished.compareAndSet(false, true)) cleanup()
        }

        fun probeTimer() {
            if (finished.get() || foundSticky.get()) return
            lastProbe.set(SystemClock.uptimeMillis())
            val found = try {
                find()
            } catch (_: Throwable) {
                false
            }
            if (found) {
                // Found: stop the timers but STAY ARMED on layout passes
                // so a rebuilt bar re-wraps without waiting for resume.
                // (Previously finish() disarmed here and the glass died
                // with the first fragment rebuild.)
                foundSticky.set(true)
                return
            }
            val n = attempts.incrementAndGet()
            if (n >= DELAYS_MS.size) {
                if (logged.compareAndSet(false, true)) {
                    try {
                        onExhausted()
                    } catch (_: Throwable) {
                    }
                }
                return
            }
            handler.postDelayed({ probeTimer() }, DELAYS_MS[n - 1])
        }

        // Layout passes don't consume the retry budget — they just probe,
        // throttled so animations don't hammer the tree walk.
        layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (finished.get()) return@OnGlobalLayoutListener
            val now = SystemClock.uptimeMillis()
            if (now - lastProbe.get() < PROBE_THROTTLE_MS) return@OnGlobalLayoutListener
            lastProbe.set(now)
            try {
                if (find()) finish()
            } catch (_: Throwable) {
                // Probe failures are expected before first layout.
            }
        }
        attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) = finish()
        }
        try {
            val observer = root.viewTreeObserver
            if (observer.isAlive) {
                registeredObserver = observer
                observer.addOnGlobalLayoutListener(layoutListener)
            }
        } catch (_: Throwable) {
            // Fall through to timed retries.
        }
        try {
            root.addOnAttachStateChangeListener(attachListener)
        } catch (_: Throwable) {
        }
        probeTimer()
    }
}
