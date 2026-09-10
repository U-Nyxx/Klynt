package com.unyxx.act.liquidglass.injection

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
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
            return view.getTag(R.id.klynt_tag_injected) == key ||
                (view.parent as? ViewGroup)?.getTag(R.id.klynt_tag_wrapper) == key
        }
    }

    private var original: View? = null
    private var glass: KlyntLiquidGlassView? = null

    /**
     * @return the wrapper now occupying the original view's slot,
     * or the original view itself when wrapping is impossible.
     */
    fun wrap(originalView: View, pkg: String): View {
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
            attachToReference(originalView)
        }
        addView(glass, 1, ViewGroup.LayoutParams(params.width, params.height))

        originalView.setTag(R.id.klynt_tag_injected, key)
        setTag(R.id.klynt_tag_wrapper, key)

        parent.addView(this, index, params)
        return this
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
        original?.setTag(R.id.klynt_tag_injected, null)
        glass = null
        original = null
    }
}
