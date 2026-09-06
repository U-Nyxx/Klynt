package com.unyxx.act.liquidglass

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import com.example.liquidglass.BlurMethod
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView
import com.unyxx.act.util.SocDetector

class KlyntLiquidGlassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LiquidGlassView(context, attrs, defStyleAttr) {

    private var referenceView: View? = null
    private val socProfile = SocDetector.detect()

    init { applySocProfile() }

    private fun applySocProfile() {
        material = GlassMaterial.REGULAR
        cornerRadius = 999f
        bevelWidth = 8.dpToPx(context)
        refractionHeight = socProfile.maxRefractionHeight.dpToPx(context)
        dispersionStrength = 0.04f
        enableSensorHighlight = true
        enableAdaptiveTint = true
        enableDynamicBackground = true

        enableBackdropBlur = false
        saturation = 100f

        useShaderPipeline = socProfile.useAgslLens
        blurMethod = socProfile.preferredBlurMethod

        if (socProfile.thermalListenerRequired && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            registerThermalListener()
        }
    }

    private fun registerThermalListener() {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.addThermalStatusListener { status ->
            when (status) {
                PowerManager.THERMAL_STATUS_LIGHT -> refractionHeight = 6.dpToPx(context)
                PowerManager.THERMAL_STATUS_MODERATE -> refractionHeight = 3.dpToPx(context)
                PowerManager.THERMAL_STATUS_SEVERE -> {
                    refractionHeight = 0f
                    enableSensorHighlight = false
                }
                PowerManager.THERMAL_STATUS_CRITICAL -> visibility = View.GONE
            }
            invalidate()
        }
    }

    fun attachToReference(reference: View) {
        referenceView = reference
        layoutParams = reference.layoutParams.apply {
            width = reference.layoutParams.width
            height = reference.layoutParams.height
        }
        setPadding(
            reference.paddingLeft, reference.paddingTop,
            reference.paddingRight, reference.paddingBottom
        )
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        referenceView?.let { ref ->
            val w = ref.measuredWidth
            val h = ref.measuredHeight
            if (w > 0 && h > 0) {
                super.onMeasure(
                    android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY)
                )
                return
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        referenceView?.let { ref ->
            (ref as? ViewGroup)?.let { refGroup ->
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    val refChild = refGroup.getChildAt(i)
                    refChild?.let { rc ->
                        child.layout(rc.left, rc.top, rc.right, rc.bottom)
                    }
                }
            }
        }
    }

    private fun Number.dpToPx(context: Context): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics)
    }
}