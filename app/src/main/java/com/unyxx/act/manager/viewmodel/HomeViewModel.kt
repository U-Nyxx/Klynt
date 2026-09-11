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

    private val _checks = MutableStateFlow<List<SetupCheck>>(emptyList())
    val checks: StateFlow<List<SetupCheck>> = _checks.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = ServiceLocator.getStats()
            val active = ServiceLocator.isModuleActive()
            _stats.value = stats
            _isModuleActive.value = active
            _checks.value = listOf(
                SetupCheck(
                    key = CheckKey.BINDER,
                    done = ServiceLocator.isServiceAlive()
                ),
                SetupCheck(
                    key = CheckKey.SCOPE,
                    done = active
                ),
                SetupCheck(
                    key = CheckKey.INSTALLED,
                    done = stats.installedTargets > 0
                ),
                SetupCheck(
                    key = CheckKey.RESTART,
                    done = true
                )
            )
        }
    }
}

/** Setup checklist step shown on Home. Text resolved in UI for i18n. */
data class SetupCheck(
    val key: CheckKey,
    val done: Boolean
)

enum class CheckKey {
    BINDER, SCOPE, INSTALLED, RESTART
}
