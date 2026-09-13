package com.unyxx.act.liquidglass.engine

/**
 * iOS-spring equivalent for the View-system glass (hook overlay).
 *
 * Reference platform drives lens pops with spring animation; the Android twin
 * inside a hooked process is a hand-rolled damped spring over
 * `Choreographer` — zero dependencies (no dynamicanimation AAR, APK
 * stays lean) and pure functions so the integrator is unit-testable
 * on plain JVM.
 *
 * Convention: `stiffness` ≈ 140–220 (snappy lens), `dampingRatio`
 * ≈ 0.7 (one soft overshoot, never a wobble-fest on 60Hz Mali).
 */
object GlassMotion {

    /** One semi-implicit Euler step. Returns (position, velocity). */
    fun springStep(
        position: Float,
        velocity: Float,
        target: Float,
        stiffness: Float,
        dampingRatio: Float,
        dt: Float
    ): Pair<Float, Float> {
        val damping = 2f * dampingRatio * kotlin.math.sqrt(stiffness)
        val accel = stiffness * (target - position) - damping * velocity
        val v = velocity + accel * dt
        return (position + v * dt) to v
    }

    /** True when visually at rest (stops the frame loop, saves battery). */
    fun isSettled(position: Float, velocity: Float, target: Float): Boolean =
        kotlin.math.abs(target - position) < 0.002f &&
            kotlin.math.abs(velocity) < 0.01f
}
