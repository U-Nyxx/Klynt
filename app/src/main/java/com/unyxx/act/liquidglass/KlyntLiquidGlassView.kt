package com.unyxx.act.liquidglass

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView
import com.unyxx.act.util.SocDetector

/**
 * Liquid-glass overlay tuned per SoC ([SocDetector]).
 *
 * Bottom navigation always sits over scrolling content, therefore the
 * backdrop is dynamic and refraction must track the pill shape in px
 * (converted from dp at runtime — never hardcoded pixels).
 */
class KlyntLiquidGlassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LiquidGlassView(context, attrs, defStyleAttr) {

    private var referenceView: View? = null

    init {
        // No configure() here: the wrapper configures exactly once with
        // the resolved profile + user settings. Double-configure (init
        // then wrap) built the blur pipeline twice per injection.
    }

    /**
     * Applies a [SocDetector.Profile]; callable again if conditions change.
     *
     * @param intensity 0..1 user multiplier on lens strength (per-app).
     * @param cornerDp corner radius in dp; 999+ means pill (per-app).
     * @param blur backdrop blur on/off (per-app).
     */
    fun configure(
        profile: SocDetector.Profile,
        context: Context,
        intensity: Float = 1f,
        cornerDp: Float = 999f,
        blur: Boolean = true
    ) {
        val fallback = profile.frostedFallback || isReducedMotion(context)
        val k = intensity.coerceIn(0f, 1f)
        material = GlassMaterial.REGULAR
        cornerRadius = if (cornerDp >= 999f) 999f else cornerDp.dpToPx(context)
        // The SoC-chosen blur backend (was computed but never applied).
        try {
            blurMethod = profile.preferredBlurMethod
        } catch (_: Throwable) {
        }
        // Cheaper capture + pipeline on weak tiers. Verified against the
        // v2.0.8 AAR surface (setEnableOptimizedCapture/setEnableShadow/
        // setEnableChromaticDispersion exist). NOTE: no highQuality knob
        // exists in v2.0.8 despite the getter-sounding name pattern — the
        // Profile.highQuality flag is tier documentation until the lib
        // (or a vendored fork) exposes it.
        try {
            enableOptimizedCapture = true
        } catch (_: Throwable) {
        }
        if (fallback || k <= 0f) {
            // Frosted fallback: blur only, no lens — safe on Mali
            // mid-range, battery saver, reduced motion and high contrast.
            refractionHeight = 0f
            bevelWidth = 10f.dpToPx(context)
            dispersionStrength = 0f
            enableSensorHighlight = false
            enableAdaptiveTint = false
            try {
                enableChromaticDispersion = false
                enableShadow = false
            } catch (_: Throwable) {
            }
        } else {
            refractionHeight = profile.refractionDp.dpToPx(context) * k
            bevelWidth = profile.bevelDp.dpToPx(context)
            dispersionStrength = profile.dispersion * k
            enableSensorHighlight = false
            enableAdaptiveTint = true
        }
        // Static backdrop on weak tiers: per-frame re-capture is the top
        // RAM/GC cost and frosted blur barely changes while scrolling.
        // Full-lens tiers keep dynamic (content must track under glass).
        enableDynamicBackground = profile.dynamicBackdrop && !fallback
        // Blur toggle is user-controlled; the frosted path always blurs.
        enableBackdropBlur = blur || fallback || k <= 0f
        saturation = 140f
    }

    /** Mirrors padding of the wrapped navigation view and re-measures. */
    fun attachToReference(reference: View) {
        referenceView = reference
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

    /** Honors battery saver, animator-off and high-contrast settings. */
    private fun isReducedMotion(context: Context): Boolean {
        try {
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (power?.isPowerSaveMode == true) return true
            val animatorScale = Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
            if (animatorScale == 0f) return true
            if (Settings.Secure.getInt(
                    context.contentResolver, "high_text_contrast_enabled", 0
                ) == 1
            ) return true
        } catch (_: Throwable) {
            // Settings read failed — prefer full quality over false fallback.
        }
        return false
    }

    private fun Number.dpToPx(context: Context): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics)
    }
}
