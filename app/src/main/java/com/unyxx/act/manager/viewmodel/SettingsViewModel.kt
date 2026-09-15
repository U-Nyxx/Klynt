package com.unyxx.act.manager.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.unyxx.act.manager.di.ServiceLocator
import com.unyxx.act.manager.update.UpdateErrorCause
import com.unyxx.act.manager.update.UpdateRepository
import com.unyxx.act.manager.update.UpdateState
import com.unyxx.act.xposed.prefs.PrefsSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val _globalEnabled = MutableStateFlow(ServiceLocator.isGlobalEnabled())
    val globalEnabled: StateFlow<Boolean> = _globalEnabled.asStateFlow()

    private val _autoStart = MutableStateFlow(ServiceLocator.isAutoStartEnabled())
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private val _themeMode = MutableStateFlow(PrefsSchema.ThemeMode.SYSTEM)
    val themeMode: StateFlow<PrefsSchema.ThemeMode> = _themeMode.asStateFlow()

    private val _pureBlackOled = MutableStateFlow(false)
    val pureBlackOled: StateFlow<Boolean> = _pureBlackOled.asStateFlow()

    private val _accentColor = MutableStateFlow(PrefsSchema.AccentColor.BLUE)
    val accentColor: StateFlow<PrefsSchema.AccentColor> = _accentColor.asStateFlow()

    private val _followSystemAccent = MutableStateFlow(false)
    val followSystemAccent: StateFlow<Boolean> = _followSystemAccent.asStateFlow()

    private val _language = MutableStateFlow(PrefsSchema.Language.SYSTEM)
    val language: StateFlow<PrefsSchema.Language> = _language.asStateFlow()

    private val _logVerbose = MutableStateFlow(false)
    val logVerbose: StateFlow<Boolean> = _logVerbose.asStateFlow()

    private val _logAutoscroll = MutableStateFlow(true)
    val logAutoscroll: StateFlow<Boolean> = _logAutoscroll.asStateFlow()

    private val _logPaused = MutableStateFlow(false)
    val logPaused: StateFlow<Boolean> = _logPaused.asStateFlow()

    private val _logWordWrap = MutableStateFlow(true)
    val logWordWrap: StateFlow<Boolean> = _logWordWrap.asStateFlow()

    fun setGlobalEnabled(enabled: Boolean) {
        _globalEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) { ServiceLocator.setGlobalEnabled(enabled) }
    }

    fun setAutoStart(enabled: Boolean) {
        _autoStart.value = enabled
        viewModelScope.launch(Dispatchers.IO) { ServiceLocator.setAutoStartEnabled(enabled) }
    }

    fun setThemeMode(mode: PrefsSchema.ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch(Dispatchers.IO) { ServiceLocator.setThemeMode(mode) }
    }

    fun setPureBlackOled(enabled: Boolean) {
        _pureBlackOled.value = enabled
        viewModelScope.launch(Dispatchers.IO) { ServiceLocator.setPureBlackOled(enabled) }
    }

    fun setAccentColor(color: PrefsSchema.AccentColor) {
        _accentColor.value = color
        viewModelScope.launch(Dispatchers.IO) { ServiceLocator.setAccentColor(color) }
    }

    fun setFollowSystemAccent(enabled: Boolean) {
        _followSystemAccent.value = enabled
        viewModelScope.launch(Dispatchers.IO) { ServiceLocator.setFollowSystemAccent(enabled) }
    }

    fun setLanguage(lang: PrefsSchema.Language) {
        _language.value = lang
        viewModelScope.launch(Dispatchers.IO) { ServiceLocator.setLanguage(lang) }
    }

    fun setLogVerbose(enabled: Boolean) {
        _logVerbose.value = enabled
        viewModelScope.launch(Dispatchers.IO) { ServiceLocator.setLogVerbose(enabled) }
    }

    fun setLogAutoscroll(enabled: Boolean) {
        _logAutoscroll.value = enabled
        viewModelScope.launch(Dispatchers.IO) { ServiceLocator.setLogAutoscroll(enabled) }
    }

    fun setLogPaused(enabled: Boolean) {
        _logPaused.value = enabled
        viewModelScope.launch(Dispatchers.IO) { ServiceLocator.setLogPaused(enabled) }
    }

    fun setLogWordWrap(enabled: Boolean) {
        _logWordWrap.value = enabled
        viewModelScope.launch(Dispatchers.IO) { ServiceLocator.setLogWordWrap(enabled) }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _globalEnabled.value = ServiceLocator.isGlobalEnabled()
            _autoStart.value = ServiceLocator.isAutoStartEnabled()
            _themeMode.value = ServiceLocator.getThemeMode()
            _pureBlackOled.value = ServiceLocator.isPureBlackOled()
            _accentColor.value = ServiceLocator.getAccentColor()
            _followSystemAccent.value = ServiceLocator.isFollowSystemAccent()
            _language.value = ServiceLocator.getLanguage()
            _logVerbose.value = ServiceLocator.isLogVerbose()
            _logAutoscroll.value = ServiceLocator.isLogAutoscroll()
            _logPaused.value = ServiceLocator.isLogPaused()
            _logWordWrap.value = ServiceLocator.isLogWordWrap()
        }
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    private var downloadJob: Job? = null

    fun checkForUpdates(context: Context, force: Boolean = false) {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateRepository.check(context.applicationContext, force)
        }
    }

    fun startDownload(context: Context) {
        val info = (_updateState.value as? UpdateState.Available)?.info ?: return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            val appCtx = context.applicationContext
            val id = try {
                UpdateRepository.enqueueDownload(appCtx, info)
            } catch (t: Throwable) {
                val cause = UpdateErrorCause.from(t.message)
                _updateState.value = UpdateState.Failed(t.message ?: "Download failed", cause)
                return@launch
            }
            _updateState.value = UpdateState.Downloading(null)
            while (true) {
                delay(500L)
                when {
                    UpdateRepository.isComplete(appCtx, id) -> {
                        _updateState.value = UpdateState.Downloaded(UpdateRepository.updateFile(appCtx))
                        installUpdate(appCtx)
                        return@launch
                    }
                    UpdateRepository.isFailed(appCtx, id) -> {
                        _updateState.value = UpdateState.Failed("Download failed", UpdateErrorCause.Unknown)
                        return@launch
                    }
                    else -> {
                        _updateState.value = UpdateState.Downloading(UpdateRepository.queryProgress(appCtx, id))
                    }
                }
            }
        }
    }

    fun cancelDownload(context: Context) {
        downloadJob?.cancel()
        downloadJob = null
        _updateState.value = UpdateState.Idle
    }

    fun installUpdate(context: Context) {
        try {
            val intent = UpdateRepository.installIntent(context.applicationContext) ?: throw IllegalStateException("APK file missing")
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.applicationContext.startActivity(intent)
        } catch (t: Throwable) {
            val cause = UpdateErrorCause.from(t.message)
            _updateState.value = UpdateState.Failed(t.message ?: "Install failed", cause)
        }
    }

    fun exportSettings(context: Context): String {
        val prefs = context.getSharedPreferences(PrefsSchema.PREFS_FILE, Context.MODE_PRIVATE)
        val all = prefs.all
        val map = mutableMapOf<String, Any>()
        all.forEach { (k, v) -> map[k] = v as Any }
        return Gson().toJson(map)
    }

    fun importSettings(context: Context, json: String) {
        val prefs = context.getSharedPreferences(PrefsSchema.PREFS_FILE, Context.MODE_PRIVATE)
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map = Gson().fromJson(json, type) ?: emptyMap<String, Any>()
        prefs.edit().clear().apply()
        map.forEach { entry ->
            val k = entry.key
            val v = entry.value
            when (v) {
                is Boolean -> prefs.edit().putBoolean(k, v).apply()
                is Int -> prefs.edit().putInt(k, v).apply()
                is Long -> prefs.edit().putLong(k, v).apply()
                is Float -> prefs.edit().putFloat(k, v).apply()
                is String -> prefs.edit().putString(k, v).apply()
            }
        }
        refresh()
    }
}
