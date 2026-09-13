package com.unyxx.act.xposed.hooks

import android.view.View
import android.view.ViewGroup

/**
 * Future-proof hook chain for bottom-bar discovery.
 *
 * Apple never hard-codes a single class — everything via traits.
 * KLYNT used single-pass `NAV_CLASS_HINTS contains` (brittle on obfuscation).
 * This chain tries strategies in order, each pure and testable:
 *  - ClassName (fast, exact)
 *  - Semantic (wide + labeled 3+, obfuscation-proof)
 *  - DexPattern (bytecode scan, future — not yet scanning dex, but shape ready)
 *
 * Nothing here touches traffic or privacy — only view hierarchy.
 */
fun interface HookStrategy {
    fun find(root: ViewGroup, candidates: List<View>, density: Float): View?
}

/** Registry used by hooks to avoid hard-coded if-else drift. */
object HookStrategies {
    fun chain(vararg strategies: HookStrategy): HookStrategy = HookStrategy { root, candidates, density ->
        for (s in strategies) {
            val v = s.find(root, candidates, density)
            if (v != null) return@HookStrategy v
        }
        null
    }
}
