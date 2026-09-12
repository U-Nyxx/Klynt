package com.unyxx.act.liquidglass.injection

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import com.unyxx.act.R
import com.unyxx.act.liquidglass.KlyntLiquidGlassView

/**
 * Wraps a target app's bottom navigation view with a liquid-glass overlay.
 *
 * The wrapper takes the original view's exact position in its parent
 * (same index, same LayoutParams), so the host layout is undisturbed.
 * The glass layer is explicitly non-clickable/non-focusable, therefore
 * touches fall through to the original navigation view untouched.
 */
class BottomNavWrapper @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private fun injectionKey(pkg: String) = "klynt_lg_${pkg.replace('.', '_')}_v1"

        /** True when [view] (or its wrapper parent) already carries our glass. */
        fun isInjected(view: View, pkg: String): Boolean {
            val key = injectionKey(pkg)
            return isOurs(view) ||
                view.getTag(R.id.klynt_tag_injected) == key ||
                (view.parent as? ViewGroup)?.getTag(R.id.klynt_tag_wrapper) == key
        }

        /** True for our own views — never wrap these, never treat as nav. */
        fun isOurs(view: View): Boolean {
            if (view is BottomNavWrapper) return true
            if (view is KlyntLiquidGlassView) return true
            return view.getTag(R.id.klynt_tag_injected) != null ||
                view.getTag(R.id.klynt_tag_wrapper) != null
        }

        /** Finds our wrapper for [pkg] under [root], if any. */
        fun findWrapper(root: ViewGroup, pkg: String): BottomNavWrapper? {
            val key = injectionKey(pkg)
            val stack = ArrayDeque<View>()
            stack.add(root)
            while (stack.isNotEmpty()) {
                val v = stack.removeLast()
                if (v is BottomNavWrapper && v.getTag(R.id.klynt_tag_wrapper) == key) return v
                if (v is ViewGroup) {
                    for (i in 0 until v.childCount) stack.add(v.getChildAt(i))
                }
            }
            return null
        }

        /**
         * Restores every wrapped navigation view under [root] to its
         * original slot. Called when the feature is toggled off so no
         * target restart is required.
         */
        fun unwrapAll(root: ViewGroup, pkg: String) {
            val key = injectionKey(pkg)
            val found = mutableListOf<BottomNavWrapper>()
            collectWrappers(root, found, key)
            found.forEach { it.restore() }
        }

        private fun collectWrappers(view: View, out: MutableList<BottomNavWrapper>, key: String) {
            if (view is BottomNavWrapper && view.getTag(R.id.klynt_tag_wrapper) == key) {
                out.add(view)
                return
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    collectWrappers(view.getChildAt(i), out, key)
                }
            }
        }
    }

    private var original: View? = null
    private var glass: KlyntLiquidGlassView? = null
    private var scrollListener: ViewTreeObserver.OnScrollChangedListener? = null
    private val scrollHandler = Handler(Looper.getMainLooper())
    private var showRunnable: Runnable? = null
    private var autoHideBound = false

    /** The wrapped navigation view, if still attached. */
    fun originalView(): View? = original

    /**
     * Re-applies glass parameters on an already-wrapped bar WITHOUT
     * unwrapping. Manager slider changes take effect live; previously
     * the early `return true` ignored them until a forced re-wrap.
     */
    fun reconfigure(intensity: Float, cornerDp: Float, blur: Boolean) {
        try {
            glass?.configure(
                com.unyxx.act.util.SocDetector.resolve(context),
                context,
                intensity.coerceIn(0f, 1f),
                cornerDp,
                blur
            )
        } catch (_: Throwable) {
            // Glass half-torn-down (detach race) — next pass re-wraps.
        }
    }

    /**
     * @param intensity 0..1 lens strength multiplier (per-app setting).
     * @param cornerDp corner radius in dp, 999+ means pill (per-app).
     * @param blur backdrop blur on/off (per-app).
     * @return the wrapper now occupying the original view's slot,
     * or the original view itself when wrapping is impossible.
     */
    fun wrap(
        originalView: View,
        pkg: String,
        intensity: Float = 1f,
        cornerDp: Float = 999f,
        blur: Boolean = true
    ): View {
        val key = injectionKey(pkg)
        if (isInjected(originalView, pkg)) return (originalView.parent as? View) ?: originalView

        val parent = originalView.parent as? ViewGroup ?: return originalView
        val index = parent.indexOfChild(originalView).coerceAtLeast(0)
        val params = originalView.layoutParams ?: return originalView
        parent.removeView(originalView)

        layoutParams = params
        id = View.generateViewId()

        original = originalView
        addView(originalView, 0, ViewGroup.LayoutParams(params.width, params.height))
        glass = KlyntLiquidGlassView(context).apply {
            isClickable = false
            isFocusable = false
            configure(
                com.unyxx.act.util.SocDetector.resolve(context),
                context,
                intensity.coerceIn(0f, 1f),
                cornerDp,
                blur
            )
            attachToReference(originalView)
        }
        addView(glass, 1, ViewGroup.LayoutParams(params.width, params.height))

        originalView.setTag(R.id.klynt_tag_injected, key)
        setTag(R.id.klynt_tag_wrapper, key)

        parent.addView(this, index, params)
        bindAutoHide()
        return this
    }

    /**
     * iOS-style minimize: slides the whole bar (content included) down
     * while the host scrolls, restores it 1.5s after scrolling stops.
     * Best-effort — never throws into the host app.
     */
    private fun bindAutoHide() {
        if (autoHideBound) return
        autoHideBound = true
        val observer = try {
            (parent as? View)?.viewTreeObserver?.takeIf { it.isAlive }
        } catch (_: Throwable) {
            null
        } ?: return
        scrollListener = ViewTreeObserver.OnScrollChangedListener {
            onHostScrolled()
        }
        try {
            observer.addOnScrollChangedListener(scrollListener)
        } catch (_: Throwable) {
            scrollListener = null
        }
    }

    private fun onHostScrolled() {
        if (!isAttachedToWindow) return
        showRunnable?.let { scrollHandler.removeCallbacks(it) }
        if (translationY == 0f && height > 0) {
            animate().translationY(height.toFloat()).alpha(0f).setDuration(220).start()
        }
        val show = Runnable {
            animate().translationY(0f).alpha(1f).setDuration(280).start()
        }
        showRunnable = show
        scrollHandler.postDelayed(show, 1500)
    }

    private fun unbindAutoHide() {
        autoHideBound = false
        showRunnable?.let { scrollHandler.removeCallbacks(it) }
        showRunnable = null
        scrollListener?.let { listener ->
            try {
                (parent as? View)?.viewTreeObserver
                    ?.removeOnScrollChangedListener(listener)
            } catch (_: Throwable) {
            }
            scrollListener = null
        }
    }

    /** Puts the original view back and drops the glass overlay. */
    private fun restore() {
        val orig = original ?: return
        val parent = parent as? ViewGroup
        val params = layoutParams
        if (parent != null && params != null) {
            val index = parent.indexOfChild(this).coerceAtLeast(0)
            parent.removeView(this)
            orig.layoutParams = params
            parent.addView(orig, index, params)
        }
        orig.setTag(R.id.klynt_tag_injected, null)
        original = null
        glass = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        glass?.visibility = original?.visibility ?: VISIBLE
    }

    /**
     * Follows the original view (e.g. auto-hide on scroll): when the
     * navigation hides itself, the glass hides with it instead of
     * floating orphaned on screen.
     */
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === original) {
            glass?.visibility = visibility
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unbindAutoHide()
        animate().cancel()
        translationY = 0f
        alpha = 1f
        original?.setTag(R.id.klynt_tag_injected, null)
        glass = null
        original = null
    }
}
