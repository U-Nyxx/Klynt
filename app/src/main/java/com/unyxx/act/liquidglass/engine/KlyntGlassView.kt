package com.unyxx.act.liquidglass.engine

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Proprietary KlyntGlass background: live GPU glass over real content.
 *
 * Pipeline (all ours, zero third-party): the view itself draws nothing;
 * a RenderEffect chain processes its backdrop — GPU blur node first,
 * then [KLYNT_GLASS_SHADER] (SDF lens, rim light, specular, tint,
 * vibrancy) in one pass. No bitmap capture, no per-frame allocation,
 * no QWEA0, no native `.so`, no HWUI-blur dependency (this is a custom
 * shader, not the system's blur path — ROM blur kill-switches don't
 * apply).
 *
 * Tiers ([KlyntTier]): [KlyntTier.SHADER] full pipeline;
 * [KlyntTier.SCRIM] plain Canvas tint for low-RAM / thermal / reduced
 * transparency / shader failure. Never crashes, never blanks: worst
 * case is a calm translucent pill.
 */
class KlyntGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barRect = RectF()
    private var shader: RuntimeShader? = null
    private var shaderOk: Boolean = false

    /** 0..1 lens/refraction strength (manager per-app intensity). */
    var intensity: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            pushUniforms()
        }

    private var materializeAnim: android.animation.ValueAnimator? = null

    /**
     * Materialize transition (Apple rule): the element appears by
     * modulating lens bending 0→target, never by opacity crossfade.
     * Call right after the overlay is attached.
     */
    fun animateIntensityTo(target: Float) {
        val to = target.coerceIn(0f, 1f)
        materializeAnim?.cancel()
        intensity = 0f
        materializeAnim = android.animation.ValueAnimator.ofFloat(0f, to).apply {
            duration = 280
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                intensity = it.animatedValue as Float
            }
            start()
        }
    }

    var tier: KlyntTier = KlyntTier.SHADER
        set(value) {
            field = value
            rebuildEffect()
        }

    /** Apple's Clear variant: max transparency, full refraction. */
    var clearMode: Boolean = false
        set(value) {
            field = value
            try {
                shader?.setFloatUniform("clearMode", if (value) 1f else 0f)
                invalidate()
            } catch (_: Throwable) {
            }
        }

    private val dark: Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (dark) 0xD61E2A3A.toInt() else 0xD6FFFFFF.toInt()
    }

    init {
        isClickable = false
        isFocusable = false
        rebuildEffect()
    }

    /** Pill bounds in parent coordinates (set by the driver). */
    fun setBarRect(left: Int, top: Int, right: Int, bottom: Int) {
        barRect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        pushUniforms()
    }

    /** Gel-press bulge center in parent coordinates; amount 0..1. */
    fun setPress(x: Float, y: Float, amount: Float) {
        val s = shader ?: return
        try {
            s.setFloatUniform("press", x, y)
            s.setFloatUniform("pressAmount", amount.coerceIn(0f, 1f))
            invalidate()
        } catch (_: Throwable) {
        }
    }

    fun clearPress() = setPress(-1f, -1f, 0f)

    private fun pushUniforms() {
        val s = shader ?: return
        if (barRect.isEmpty) return
        try {
            s.setFloatUniform("resolution", width.toFloat(), height.toFloat())
            s.setFloatUniform(
                "barRect",
                barRect.left, barRect.top, barRect.right, barRect.bottom
            )
            val r = barRect.height() / 2f
            s.setFloatUniform("cornerRadius", r)
            s.setFloatUniform("intensity", intensity)
            s.setFloatUniform("dark", if (dark) 1f else 0f)
            s.setFloatUniform("clearMode", if (clearMode) 1f else 0f)
            invalidate()
        } catch (_: Throwable) {
            degradeToScrim()
        }
    }

    private fun rebuildEffect() {
        if (tier != KlyntTier.SHADER) {
            shaderOk = false
            setRenderEffect(null)
            invalidate()
            return
        }
        try {
            val s = RuntimeShader(KLYNT_GLASS_SHADER)
            shader = s
            val blur = RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP)
            val glass = RenderEffect.createRuntimeShaderEffect(s, "backdrop")
            setRenderEffect(RenderEffect.createChainEffect(glass, blur))
            shaderOk = true
            pushUniforms()
        } catch (_: Throwable) {
            degradeToScrim()
        }
    }

    private fun degradeToScrim() {
        shaderOk = false
        shader = null
        try {
            setRenderEffect(null)
        } catch (_: Throwable) {
        }
        tier = KlyntTier.SCRIM
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pushUniforms()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // SHADER tier draws nothing itself (the RenderEffect IS the
        // output). SCRIM tier paints a calm translucent pill so the bar
        // area is never empty on any device.
        if (!shaderOk && !barRect.isEmpty) {
            val r = barRect.height() / 2f
            canvas.drawRoundRect(barRect, r, r, scrimPaint)
        }
    }
}

/** Render tiers for [KlyntGlassView]. */
enum class KlyntTier { SHADER, SCRIM }

/**
 * Pure tier selection (unit-testable): anything weak or hostile gets
 * SCRIM — a visible calm pill — instead of a janky or dead shader.
 */
fun selectGlassTier(frostedProfile: Boolean, lowRam: Boolean, thermalThrottled: Boolean): KlyntTier =
    if (frostedProfile || lowRam || thermalThrottled) KlyntTier.SCRIM else KlyntTier.SHADER
