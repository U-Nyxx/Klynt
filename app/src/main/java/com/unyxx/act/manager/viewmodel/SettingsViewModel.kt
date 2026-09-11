package com.unyxx.act.manager.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unyxx.act.manager.di.ServiceLocator
import com.unyxx.act.manager.update.UpdateRepository
import com.unyxx.act.manager.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state source for Settings (global kill-switch + auto-start). */
class SettingsViewModel : ViewModel() {

    private val _globalEnabled = MutableStateFlow(ServiceLocator.isGlobalEnabled())
    val globalEnabled: StateFlow<Boolean> = _globalEnabled.asStateFlow()

    private val _autoStart = MutableStateFlow(ServiceLocator.isAutoStartEnabled())
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    fun setGlobalEnabled(enabled: Boolean) {
        _globalEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ServiceLocator.setGlobalEnabled(enabled)
        }
    }

    fun setAutoStart(enabled: Boolean) {
        _autoStart.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ServiceLocator.setAutoStartEnabled(enabled)
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _globalEnabled.value = ServiceLocator.isGlobalEnabled()
            _autoStart.value = ServiceLocator.isAutoStartEnabled()
        }
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var downloadJob: Job? = null

    /** Auto-check used on Home open (24h cache); [force] bypasses cache. */
    fun checkForUpdates(context: Context, force: Boolean = false) {
        if (_updateState.value is UpdateState.Checking ||
            _updateState.value is UpdateState.Downloading
        ) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateRepository.check(
                context.applicationContext, force
            )
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
                _updateState.value = UpdateState.Failed(t.message ?: "Download failed")
                return@launch
            }
            _updateState.value = UpdateState.Downloading(null)
            while (true) {
                delay(500L)
                when {
                    UpdateRepository.isComplete(appCtx, id) -> {
                        _updateState.value = UpdateState.Downloaded(
                            UpdateRepository.updateFile(appCtx)
                        )
                        installUpdate(appCtx)
                        return@launch
                    }
                    UpdateRepository.isFailed(appCtx, id) -> {
                        _updateState.value = UpdateState.Failed("Download failed")
                        return@launch
                    }
                    else -> {
                        _updateState.value = UpdateState.Downloading(
                            UpdateRepository.queryProgress(appCtx, id)
                        )
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
            val intent = UpdateRepository.installIntent(context.applicationContext)
                ?: throw IllegalStateException("APK file missing")
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.applicationContext.startActivity(intent)
        } catch (t: Throwable) {
            _updateState.value = UpdateState.Failed(t.message ?: "Install failed")
        }
    }
}
