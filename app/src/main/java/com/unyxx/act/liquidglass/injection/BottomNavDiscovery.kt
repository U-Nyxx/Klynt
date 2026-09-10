package com.unyxx.act.liquidglass.injection

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Robust bottom-nav discovery for injected processes.
 *
 * Runs [find] immediately, on every global layout pass, and on a
 * backoff schedule until it returns true or attempts are exhausted.
 * [find] must be idempotent — it is called many times.
 */
object BottomNavDiscovery {

    private val DELAYS_MS = longArrayOf(500L, 1000L, 2000L, 3000L, 5000L)

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
     */
    fun discover(root: ViewGroup, find: () -> Boolean) {
        val handler = Handler(Looper.getMainLooper())
        val done = AtomicBoolean(false)
        val attempts = AtomicInteger(0)
        lateinit var layoutListener: ViewTreeObserver.OnGlobalLayoutListener

        fun finish() {
            if (done.compareAndSet(false, true)) {
                handler.removeCallbacksAndMessages(null)
                try {
                    root.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                } catch (_: Throwable) {
                    // Observer dead or never registered — nothing to clean up.
                }
            }
        }

        fun attempt() {
            if (done.get()) return
            val n = attempts.incrementAndGet()
            val found = try {
                find()
            } catch (_: Throwable) {
                false
            }
            if (found || n > DELAYS_MS.size) {
                finish()
                return
            }
            handler.postDelayed({ attempt() }, DELAYS_MS[n - 1])
        }

        // Layout passes don't consume the retry budget — they just probe.
        layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (done.get()) return@OnGlobalLayoutListener
            try {
                if (find()) finish()
            } catch (_: Throwable) {
                // Probe failures are expected before first layout.
            }
        }
        try {
            if (root.viewTreeObserver.isAlive) {
                root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            }
        } catch (_: Throwable) {
            // Fall through to timed retries.
        }
        attempt()
    }
}
