package com.unyxx.act.manager.di

import android.content.Context
import android.content.SharedPreferences
import com.unyxx.act.xposed.prefs.PrefsSchema
import com.unyxx.act.xposed.scope.ScopeManager

object ServiceLocator {
    private var scopeManager: ScopeManager? = null
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        scopeManager = ScopeManager(context)
        prefs = context.getSharedPreferences(PrefsSchema.PREFS_FILE, Context.MODE_PRIVATE)
    }

    fun scopeManager(): ScopeManager = scopeManager!!
    fun prefs(): SharedPreferences = prefs!!

    fun isFeatureEnabled(packageName: String, feature: PrefsSchema.Feature): Boolean {
        val key = PrefsSchema.appKey(packageName, feature)
        return prefs?.getBoolean(key, feature.defaultValue) ?: feature.defaultValue
    }

    fun setFeatureEnabled(packageName: String, feature: PrefsSchema.Feature, enabled: Boolean) {
        val key = PrefsSchema.appKey(packageName, feature)
        prefs?.edit()?.putBoolean(key, enabled)?.apply()
    }
}
