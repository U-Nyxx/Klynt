package com.unyxx.act.liquidglass.injection

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.unyxx.act.liquidglass.KlyntLiquidGlassView
import com.unyxx.act.util.Logger

class BottomNavWrapper @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG_INJECTED = 0x7F0A0001
        private const val TAG_WRAPPER = 0x7F0A0002

        fun isInjected(view: View, pkg: String): Boolean {
            val key = "klynt_lg_${pkg.replace(".", "_")}_v1"
            return view.getTag(TAG_INJECTED) == key ||
                   (view.parent as? ViewGroup)?.getTag(TAG_WRAPPER) == key
        }

        fun markInjected(view: View, pkg: String) {
            val key = "klynt_lg_${pkg.replace(".", "_")}_v1"
            view.setTag(TAG_INJECTED, key)
            (view.parent as? ViewGroup)?.setTag(TAG_WRAPPER, key)
        }
    }

    private var original: View? = null
    private var glass: KlyntLiquidGlassView? = null
    private var targetPkg: String = ""

    fun wrap(originalView: View, pkg: String): View {
        targetPkg = pkg
        if (isInjected(originalView, pkg)) return (originalView.parent as? View) ?: originalView

        original = originalView
        val params = originalView.layoutParams
        (originalView.parent as? ViewGroup)?.removeView(originalView)

        layoutParams = params
        id = View.generateViewId()
        setTag(TAG_WRAPPER, "klynt_lg_${pkg.replace(".", "_")}_v1")

        addView(originalView, 0, params)
        glass = KlyntLiquidGlassView(context).apply {
            attachToReference(originalView)
            setZ(1f)
        }
        addView(glass, 1, params)

        markInjected(this, pkg)
        return this
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        glass?.visibility = original?.visibility ?: VISIBLE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        original?.setTag(TAG_INJECTED, null)
        (parent as? ViewGroup)?.setTag(TAG_WRAPPER, null)
    }

    override fun dispatchTouchEvent(e: android.view.MotionEvent): Boolean {
        original?.dispatchTouchEvent(e)
        glass?.dispatchTouchEvent(e)
        return true
    }
}