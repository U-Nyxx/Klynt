package com.unyxx.act.liquidglass.engine

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.PowerManager
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import java.util.concurrent.Executor

/**
 * Proprietary KlyntGlass background: live GPU glass over real content.
 *
 * Pipeline (all ours, zero third-party): the view itself draws nothing;
 * a RenderEffect chain processes its backdrop — GPU blur node first
 * (radius per-SOC), then [KLYNT_GLASS_SHADER] (SDF lens, SDF-normal
 * refraction, chromatic aberration, rim bevel, specular, tint,
 * vibrancy) in one pass. No bitmap capture, no per-frame allocation,
 * no native `.so`, no HWUI-blur dependency (this is a custom shader,
 * not the system's blur path — ROM blur kill-switches don't apply).
 *
 * Tiers ([KlyntTier]): [KlyntTier.SHADER] full pipeline (3-tap CA);
 * [KlyntTier.LITE] same shader with `dispersion = 0` (1 tap, dim
 * Mali / Exynos-safe); [KlyntTier.SCRIM] plain Canvas tint for
 * low-RAM / thermal / reduced transparency / shader failure. Never
 * crashes, never blanks: worst case is a calm translucent pill.
 *
 * Thermal (Exynos/Dimensity can heat *after* wrap): a
 * [PowerManager.OnThermalStatusChangedListener] downgrades live to
 * SCRIM at MODERATE+ and restores the previous tier when the skin
 * cools. Listener is bound to the window (attach/detach), never leaked.
 *
 * Motion (Apple rule): the element materializes by springing lens
 * bending 0→target via [GlassMotion], never by opacity crossfade.
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

    /** Chromatic-aberration gain; 0 collapses to the achromatic tap. */
    var dispersion: Float = 0.10f
        set(value) {
            field = value.coerceIn(0f, 0.30f)
            pushUniforms()
        }

    /** 0..1 specular rim width (wide glow flagship, thin line mid). */
    var bevel: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            pushUniforms()
        }

    /** Backdrop blur radius px (per-SOC: 18 flagship / 12 mid / 8 low). */
    var blurRadius: Float = 18f
        set(value) {
            val v = value.coerceIn(4f, 28f)
            if (v != field) {
                field = v
                rebuildEffect()
            }
        }

    var tier: KlyntTier = KlyntTier.SHADER
        set(value) {
            field = value
            if (value != KlyntTier.SCRIM) preThermalTier = value
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

    // ---- thermal live-downgrade (register window-bound, never leak) ----
    private var preThermalTier: KlyntTier = KlyntTier.SHADER
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    // ---- spring motion state ----
    private var springCallback: Choreographer.FrameCallback? = null
    private var springPos = 1f
    private var springVel = 0f
    private var springTarget = 1f
    private var lastFrameNs = 0L

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

    /**
     * Materialize transition (Apple rule): springs lens bending
     * 0→target with one soft overshoot. Call right after attach.
     */
    fun animateIntensityTo(target: Float) {
        val to = target.coerceIn(0f, 1f)
        stopSpring()
        springPos = 0f
        springVel = 0f
        springTarget = to
        intensity = 0f
        lastFrameNs = 0L
        val choreographer = try {
            Choreographer.getInstance()
        } catch (_: Throwable) {
            intensity = to
            return
        }
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (lastFrameNs == 0L) lastFrameNs = frameTimeNanos
                val dt = ((frameTimeNanos - lastFrameNs) / 1_000_000_000f)
                    .coerceIn(0.001f, 0.05f)
                lastFrameNs = frameTimeNanos
                // Two half-steps keep 120Hz phones stable without
                // shrinking the feel tuned at 60Hz.
                repeat(2) {
                    val (p, v) = GlassMotion.springStep(
                        springPos, springVel, springTarget, 170f, 0.72f, dt / 2f
                    )
                    springPos = p
                    springVel = v
                }
                intensity = springPos.coerceIn(0f, 1.08f).coerceAtMost(1f)
                if (GlassMotion.isSettled(springPos, springVel, springTarget)) {
                    intensity = springTarget
                    stopSpring()
                } else {
                    choreographer.postFrameCallback(this)
                }
            }
        }
        springCallback = cb
        choreographer.postFrameCallback(cb)
    }

    private fun stopSpring() {
        springCallback?.let {
            try {
                Choreographer.getInstance().removeFrameCallback(it)
            } catch (_: Throwable) {
            }
        }
        springCallback = null
    }

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
            // LITE forces the achromatic single tap in-shader.
            s.setFloatUniform("dispersion", if (tier == KlyntTier.LITE) 0f else dispersion)
            s.setFloatUniform("bevel", bevel)
            invalidate()
        } catch (_: Throwable) {
            degradeToScrim()
        }
    }

    private fun rebuildEffect() {
        if (tier == KlyntTier.SCRIM) {
            shaderOk = false
            setRenderEffect(null)
            invalidate()
            return
        }
        try {
            val s = RuntimeShader(KLYNT_GLASS_SHADER)
            shader = s
            val blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bindThermal()
    }

    override fun onDetachedFromWindow() {
        stopSpring()
        unbindThermal()
        super.onDetachedFromWindow()
    }

    private fun bindThermal() {
        if (thermalListener != null) return
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return
            val mainExecutor = Executor { it.run() }
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                post {
                    if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                        if (tier != KlyntTier.SCRIM) tier = KlyntTier.SCRIM
                    } else if (tier == KlyntTier.SCRIM && preThermalTier != KlyntTier.SCRIM) {
                        tier = preThermalTier
                    }
                }
            }
            pm.addThermalStatusListener(mainExecutor, listener)
            thermalListener = listener
        } catch (_: Throwable) {
            thermalListener = null
        }
    }

    private fun unbindThermal() {
        val listener = thermalListener ?: return
        thermalListener = null
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.removeThermalStatusListener(listener)
        } catch (_: Throwable) {
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // SHADER/LITE tiers draw nothing themselves (the RenderEffect IS
        // the output). SCRIM paints a calm translucent pill so the bar
        // area is never empty on any device.
        if (!shaderOk && !barRect.isEmpty) {
            val r = barRect.height() / 2f
            canvas.drawRoundRect(barRect, r, r, scrimPaint)
        }
    }
}

/** Render tiers for [KlyntGlassView]. */
enum class KlyntTier { SHADER, LITE, SCRIM }

/**
 * Pure tier selection (unit-testable): anything weak or hostile gets
 * SCRIM — a visible calm pill — instead of a janky or dead shader.
 * Mid-range silicon ([highQuality] == false) gets LITE: full lens
 * geometry, achromatic single tap, cheaper blur.
 */
fun selectGlassTier(
    frostedProfile: Boolean,
    lowRam: Boolean,
    thermalThrottled: Boolean,
    highQuality: Boolean = true
): KlyntTier =
    if (frostedProfile || lowRam || thermalThrottled) KlyntTier.SCRIM
    else if (!highQuality) KlyntTier.LITE
    else KlyntTier.SHADER

/**
 * GPU knobs derived from a [SocDetector.Profile].
 *
 * RAM/GPU budget per tier: FULL blur 18 + CA, LITE blur 12
 * achromatic, SCRIM blur 8 (only used if thermal cools back into a
 * shader tier — SCRIM itself paints Canvas, no GPU effect at all).
 */
data class GlassParams(val blurRadius: Float, val dispersion: Float, val bevel: Float)

fun glassParamsFor(profile: com.unyxx.act.util.SocDetector.Profile): GlassParams =
    when {
        profile.frostedFallback -> GlassParams(8f, 0f, 0.3f)
        !profile.highQuality -> GlassParams(12f, 0f, 0.35f)
        else -> GlassParams(
            18f,
            profile.dispersion.coerceIn(0f, 0.20f),
            (profile.bevelDp / 14f).coerceIn(0f, 1f)
        )
    }

/**
 * One-call SOC setup: blur + CA + bevel + tier from [profile].
 * Honors [blurEnabled] (manager per-app toggle → SCRIM, keeps bounds).
 */
fun KlyntGlassView.configure(
    profile: com.unyxx.act.util.SocDetector.Profile,
    intensity: Float,
    blurEnabled: Boolean
) {
    val params = glassParamsFor(profile)
    // Blur first: the setter rebuilds only on change, so ordering it
    // before `tier` avoids compiling the RuntimeShader twice on wrap.
    blurRadius = params.blurRadius
    this.intensity = intensity.coerceIn(0f, 1f)
    dispersion = params.dispersion
    bevel = params.bevel
    tier = if (!blurEnabled) {
        KlyntTier.SCRIM
    } else {
        selectGlassTier(profile.frostedFallback, false, false, profile.highQuality)
    }
}
