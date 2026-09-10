package com.unyxx.act.manager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unyxx.act.manager.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
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
}
