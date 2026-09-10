package com.unyxx.act.manager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unyxx.act.manager.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state source for the Home dashboard (module status + target stats). */
class HomeViewModel : ViewModel() {

    private val _stats = MutableStateFlow(ServiceLocator.Stats(0, 0, 0, 0, 0))
    val stats: StateFlow<ServiceLocator.Stats> = _stats.asStateFlow()

    private val _isModuleActive = MutableStateFlow(false)
    val isModuleActive: StateFlow<Boolean> = _isModuleActive.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _stats.value = ServiceLocator.getStats()
            _isModuleActive.value = ServiceLocator.isModuleActive()
        }
    }
}
