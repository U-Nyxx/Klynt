package com.unyxx.act.xposed.prefs

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Read-only view of the module preferences from inside a hooked app
 * process (libxposed Remote Preferences, backed by the framework —
 * supports change listeners).
 *
 * NOTE: remote prefs are read-only in hooked apps. All writes happen
 * in the manager app via the Xposed service (Batch 4).
 */
class RemotePrefs private constructor(
    private val prefs: SharedPreferences
) {
    companion object {
        @Volatile
        private var INSTANCE: RemotePrefs? = null

        fun init(prefs: SharedPreferences) {
            INSTANCE = RemotePrefs(prefs)
        }

        fun getInstance(): RemotePrefs =
            INSTANCE ?: error("RemotePrefs not initialized. Call init() first.")
    }

    private val changeCallbacks = ConcurrentHashMap<String, MutableSet<(Boolean) -> Unit>>()
    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            changeCallbacks[key]?.forEach { it(prefs.getBoolean(key, false)) }
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Best-effort refresh of the backing store before a read burst.
     * Framework-backed remote prefs push changes via listener; this
     * extra round-trip guards file-backed implementations against
     * stale reads. Never throws.
     */
    fun reload() {
        try {
            prefs.all
        } catch (_: Throwable) {
        }
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun getInt(key: String, default: Int = 0): Int =
        prefs.getInt(key, default)

    fun getFloat(key: String, default: Float): Float =
        prefs.getFloat(key, default)

    fun observeBoolean(key: String): StateFlow<Boolean> {
        val initial = prefs.getBoolean(key, false)
        val stateFlow = MutableStateFlow(initial)

        val scope = CoroutineScope(Dispatchers.IO)
        val flow = callbackFlow {
            val channel = this
            channel.trySend(initial)

            val callback: (Boolean) -> Unit = { value ->
                channel.trySend(value)
            }
            changeCallbacks.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }
                .add(callback)

            awaitClose {
                changeCallbacks[key]?.remove(callback)
            }
        }

        scope.launch {
            flow.collect { stateFlow.value = it }
        }

        return stateFlow
    }

    fun isFeatureEnabled(packageName: String, feature: PrefsSchema.Feature): Boolean {
        val key = PrefsSchema.appKey(packageName, feature)
        return prefs.getBoolean(key, feature.defaultValue)
    }

    fun observeFeature(
        packageName: String,
        feature: PrefsSchema.Feature
    ): StateFlow<Boolean> =
        observeBoolean(PrefsSchema.appKey(packageName, feature))
}
