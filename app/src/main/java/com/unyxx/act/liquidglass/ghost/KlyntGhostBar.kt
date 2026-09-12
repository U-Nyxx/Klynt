package com.unyxx.act.liquidglass.ghost

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.min

/**
 * KLYNT's own bottom navigation bar, drawn fully on [Canvas].
 *
 * This is the "ghost" half of the ghost-driver pattern: the target app's
 * real tab bar is set `INVISIBLE` (slot + insets preserved) and this bar
 * floats in its place. Taps are forwarded to the real tabs via
 * `performClick()`, so Telegram itself keeps driving navigation — zero
 * internal-API coupling, immune to obfuscation and redesigns of the
 * target's visuals.
 *
 * Why Canvas instead of QWEA0/Compose here: this view runs inside
 * low-RAM target processes on ROMs that may disable HWUI blur entirely
 * (`debug.hwui.disable_blur`). Every pixel below is drawn with plain
 * Paint ops, so the bar is visible on ANY device. Guaranteed pixels
 * beat fancy pixels.
 *
 * Layout contract: this view is MATCH_PARENT inside its overlay; the
 * pill is drawn at [barRect] (overlay coordinates). Touches outside the
 * pill return false so underlying content stays interactive.
 */
class KlyntGhostBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Labels mirrored from the real tabs (localized by the target). */
    var labels: List<String> = listOf("Chats", "Contacts", "Settings", "Profile")
        set(value) {
            field = value.take(6).ifEmpty { listOf("1", "2", "3", "4") }
            slotCount = field.size
            invalidate()
        }

    /** Highlighted slot; follows forwarded taps, re-synced per resume. */
    var selectedIndex: Int = 0
        set(value) {
            val coerced = value.coerceIn(0, (slotCount - 1).coerceAtLeast(0))
            if (field == coerced && selectedCx >= 0f) return
            field = coerced
            animateSelection()
        }

    /** Animated selection center-x (slides instead of jumping). */
    private var selectedCx: Float = -1f
    private var slideAnim: ValueAnimator? = null

    /** Tap bounce scale (1 = rest). */
    private var tapScale: Float = 1f
    private var bounceAnim: ValueAnimator? = null

    private fun slotCenterX(index: Int): Float {
        if (pillRect.isEmpty || slotCount <= 0) return -1f
        val slotW = pillRect.width() / slotCount
        return pillRect.left + slotW * index + slotW / 2f
    }

    private fun animateSelection() {
        val target = slotCenterX(selectedIndex)
        if (target < 0f || width <= 0) {
            selectedCx = target
            invalidate()
            return
        }
        if (selectedCx < 0f) {
            selectedCx = target
            invalidate()
            return
        }
        slideAnim?.cancel()
        val from = selectedCx
        slideAnim = ValueAnimator.ofFloat(from, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                selectedCx = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun playBounce() {
        bounceAnim?.cancel()
        bounceAnim = ValueAnimator.ofFloat(1f, 0.94f, 1f).apply {
            duration = 180
            interpolator = OvershootInterpolator(2f)
            addUpdateListener {
                tapScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Called with the tapped slot index (driver forwards the click). */
    var onSlotTapped: ((Int) -> Unit)? = null

    /** Finger press position in view coordinates (gel effect wiring). */
    var onPressChanged: ((x: Float, y: Float, active: Boolean) -> Unit)? = null

    /**
     * Chrome-only mode: skips pill body/shadow/highlight/border and draws
     * just glyphs, labels, selection and press rings. Used over
     * [com.unyxx.act.liquidglass.engine.KlyntGlassView], which owns the
     * glass background — Apple's layer rule (glass ≠ overlay).
     */
    var chromeOnly: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var slotCount: Int = 4
    private var pressedIndex: Int = -1
    private val pillRect = RectF()

    /**
     * Pill bounds in overlay coordinates, set by the driver from the
     * hidden row's live geometry. Empty = nothing drawn, fully passthrough.
     */
    fun setBarRect(left: Int, top: Int, right: Int, bottom: Int) {
        pillRect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        invalidate()
    }

    private val dark: Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (dark) 0xE61E2A3A.toInt() else 0xE6FFFFFF.toInt()
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = if (dark) 0x26FFFFFF else 0x1F000000
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (dark) 0x19FFFFFF else 0x14000000
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF33A7E5.toInt() // Telegram blue, readable on both themes
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x4D000000.toInt()
        maskFilter = BlurMaskFilter(dp(10f), BlurMaskFilter.Blur.NORMAL)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = 0xFF33A7E5.toInt()
    }

    private val tmpPath = Path()

    init {
        isClickable = true
        isFocusable = true
        // BlurMaskFilter needs a software layer; draws happen only on
        // interaction (tap/slide), never per-frame, so the cost is trivial.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        // Screen-reader users get the real tab labels as actions.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun dp(v: Float): Float =
        v * context.resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pillRect.isEmpty || slotCount <= 0) return
        // First draw ever: snap the indicator (no slide from nowhere).
        if (selectedCx < 0f) selectedCx = slotCenterX(selectedIndex)

        // Tap bounce around the pill center.
        canvas.save()
        canvas.scale(tapScale, tapScale, pillRect.centerX(), pillRect.centerY())

        val radius = pillRect.height() / 2f
        if (!chromeOnly) {
            // Soft outer shadow (lifts the pill off content).
            canvas.drawRoundRect(
                pillRect.left, pillRect.top + dp(3f),
                pillRect.right, pillRect.bottom + dp(3f),
                radius, radius, shadowPaint
            )
            // Pill body.
            canvas.drawRoundRect(pillRect, radius, radius, bgPaint)
            // Top specular highlight (the "glass" cue that needs no blur).
            val save = canvas.save()
            canvas.clipRect(pillRect.left, pillRect.top, pillRect.right, pillRect.centerY())
            canvas.drawRoundRect(pillRect, radius, radius, highlightPaint)
            // Bright top edge line: the iPhone specular read.
            canvas.drawLine(
                pillRect.left + radius, pillRect.top + dp(1f),
                pillRect.right - radius, pillRect.top + dp(1f),
                ringPaint.apply { alpha = 90 }
            )
            ringPaint.alpha = 255
            canvas.restoreToCount(save)
            // Border last so it stays crisp.
            canvas.drawRoundRect(pillRect, radius, radius, borderPaint)
        }

        val slotW = pillRect.width() / slotCount
        for (i in 0 until slotCount) {
            val selected = i == selectedIndex
            val cx = pillRect.left + slotW * i + slotW / 2f
            val cy = pillRect.top + pillRect.height() * 0.36f
            // Selection indicator SLIDES (selectedCx) instead of jumping.
            if (i == selectedIndex && selectedCx >= 0f) {
                val r = min(slotW, pillRect.height()) * 0.30f
                canvas.drawCircle(selectedCx, cy, r, accentPaint)
            }
            // Pressed ring follows the finger.
            if (pressedIndex == i) {
                val r = min(slotW, pillRect.height()) * 0.36f
                ringPaint.alpha = 160
                canvas.drawCircle(cx, cy, r, ringPaint)
                ringPaint.alpha = 255
            }
            drawGlyph(canvas, i, cx, cy, selected)
            // Label under the glyph, mirrored from the real tab.
            textPaint.color = when {
                selected -> 0xFF33A7E5.toInt()
                dark -> 0xB8FFFFFF.toInt()
                else -> 0xB8000000.toInt()
            }
            if (pressedIndex == i) textPaint.alpha = 128
            canvas.drawText(
                labels.getOrElse(i) { "" },
                cx, cy + dp(24f), textPaint
            )
            textPaint.alpha = 255
        }
        canvas.restore()
    }

    /** Generic iOS-ish glyphs: bubble, person, gear, profile. */
    private fun drawGlyph(canvas: Canvas, index: Int, cx: Float, cy: Float, selected: Boolean) {
        glyphPaint.color = if (selected) 0xFFFFFFFF.toInt()
        else if (dark) 0xDEFFFFFF.toInt() else 0xDE000000.toInt()
        if (pressedIndex == index) glyphPaint.alpha = 128
        val s = dp(9f) // glyph half-size
        tmpPath.rewind()
        when (index % 4) {
            0 -> { // chat bubble
                tmpPath.addRoundRect(cx - s, cy - s * 0.8f, cx + s, cy + s * 0.6f, s * 0.5f, s * 0.5f, Path.Direction.CW)
                tmpPath.moveTo(cx - s * 0.4f, cy + s * 0.6f)
                tmpPath.lineTo(cx - s * 0.4f, cy + s * 1.1f)
                tmpPath.lineTo(cx + s * 0.1f, cy + s * 0.6f)
                canvas.drawPath(tmpPath, glyphPaint)
            }
            1 -> { // contacts person
                canvas.drawCircle(cx, cy - s * 0.35f, s * 0.42f, glyphPaint)
                tmpPath.moveTo(cx - s * 0.75f, cy + s * 0.8f)
                tmpPath.cubicTo(cx - s * 0.75f, cy, cx + s * 0.75f, cy, cx + s * 0.75f, cy + s * 0.8f)
                canvas.drawPath(tmpPath, glyphPaint)
            }
            2 -> { // settings gear (circle + spokes)
                canvas.drawCircle(cx, cy, s * 0.55f, glyphPaint)
                for (k in 0 until 6) {
                    val a = k * Math.PI / 3.0
                    val x1 = cx + Math.cos(a).toFloat() * s * 0.75f
                    val y1 = cy + Math.sin(a).toFloat() * s * 0.75f
                    val x2 = cx + Math.cos(a).toFloat() * s * 1.05f
                    val y2 = cy + Math.sin(a).toFloat() * s * 1.05f
                    canvas.drawLine(x1, y1, x2, y2, glyphPaint)
                }
            }
            else -> { // profile head + shoulders (selected: white, else blue)
                if (!selected) glyphPaint.color = 0xFF33A7E5.toInt()
                canvas.drawCircle(cx, cy - s * 0.4f, s * 0.4f, glyphPaint)
                tmpPath.moveTo(cx - s * 0.8f, cy + s * 0.85f)
                tmpPath.cubicTo(cx - s * 0.8f, cy + s * 0.1f, cx + s * 0.8f, cy + s * 0.1f, cx + s * 0.8f, cy + s * 0.85f)
                canvas.drawPath(tmpPath, glyphPaint)
            }
        }
        glyphPaint.alpha = 255
    }

    private fun slotAt(x: Float, y: Float): Int {
        if (pillRect.isEmpty || slotCount <= 0) return -1
        if (!pillRect.contains(x, y)) return -1
        return (((x - pillRect.left) / pillRect.width()) * slotCount)
            .toInt().coerceIn(0, slotCount - 1)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val slot = slotAt(event.x, event.y)
                if (slot < 0) return false // outside pill: passthrough
                pressedIndex = slot
                onPressChanged?.invoke(event.x, event.y, true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val now = slotAt(event.x, event.y)
                if (now != pressedIndex) {
                    pressedIndex = now
                    if (now >= 0) {
                        // Glide feedback: tick per slot crossed.
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onPressChanged?.invoke(event.x, event.y, true)
                    }
                    invalidate()
                } else if (now >= 0) {
                    onPressChanged?.invoke(event.x, event.y, true)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val tapped = slotAt(event.x, event.y)
                pressedIndex = -1
                onPressChanged?.invoke(0f, 0f, false)
                invalidate()
                if (tapped >= 0) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    selectedIndex = tapped
                    playBounce()
                    onSlotTapped?.invoke(tapped)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                onPressChanged?.invoke(0f, 0f, false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // Accessibility: expose one action per slot with the real tab label.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.TabWidget::class.java.name
        for (i in 0 until slotCount) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLICK,
                    labels.getOrElse(i) { "Tab ${i + 1}" }
                )
            )
        }
        if (Build.VERSION.SDK_INT >= 21) {
            info.contentDescription = labels.joinToString(", ")
        }
    }
}
