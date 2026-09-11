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
 */
object BottomNavDiscovery {

    private val DELAYS_MS = longArrayOf(500L, 1000L, 2000L, 3000L, 5000L)
    private const val PROBE_THROTTLE_MS = 1500L

    /** Depth-first collection of a view and all descendants. */
    fun collectAll(view: View, out: MutableList<View>) {
        out.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectAll(view.getChildAt(i), out)
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
        val handler = Handler(Looper.getMainLooper())
        val finished = AtomicBoolean(false)
        val logged = AtomicBoolean(false)
        val attempts = AtomicInteger(0)
        val lastProbe = AtomicLong(0)
        lateinit var layoutListener: ViewTreeObserver.OnGlobalLayoutListener
        lateinit var attachListener: View.OnAttachStateChangeListener

        fun cleanup() {
            handler.removeCallbacksAndMessages(null)
            try {
                root.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            } catch (_: Throwable) {
                // Observer dead or never registered — nothing to clean up.
            }
            try {
                root.removeOnAttachStateChangeListener(attachListener)
            } catch (_: Throwable) {
            }
        }

        fun finish() {
            if (finished.compareAndSet(false, true)) cleanup()
        }

        fun probeTimer() {
            if (finished.get()) return
            lastProbe.set(SystemClock.uptimeMillis())
            val found = try {
                find()
            } catch (_: Throwable) {
                false
            }
            if (found) {
                finish()
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
            if (root.viewTreeObserver.isAlive) {
                root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
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
