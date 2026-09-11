package com.unyxx.act.xposed

import android.util.Log
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam

/**
 * KLYNT entry for libxposed API 102 frameworks.
 *
 * Same hooks as [KlyntModuleBase] plus hot-reload hygiene: the old
 * generation retires its hooks cleanly so LSPosed can swap code without
 * a target restart. Fresh processes always load current code, so reopen
 * the target if glass is missing right after an update.
 */
class KlyntModule : KlyntModuleBase() {

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        log(Log.INFO, TAG, "onHotReloading: old generation ready to retire")
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        hookHandles.forEach { handle ->
            runCatching { handle.unhook() }.onFailure {
                log(Log.WARN, TAG, "unhook failed: ${it.message}")
            }
        }
        hookHandles.clear()
        log(Log.INFO, TAG, "onHotReloaded: old hooks retired")
    }
}
