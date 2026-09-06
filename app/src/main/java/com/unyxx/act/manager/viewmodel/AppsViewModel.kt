package com.unyxx.act.manager.viewmodel

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unyxx.act.manager.di.ServiceLocator
import com.unyxx.act.util.Logger
import com.unyxx.act.xposed.prefs.PrefsSchema
import com.unyxx.act.xposed.scope.AppFamily
import com.unyxx.act.xposed.scope.ScopeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppsViewModel(
    private val context: Context
) : ViewModel() {

    private val scopeManager: ScopeManager = ServiceLocator.scopeManager()

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = scopeManager.getInstallableTargetApps()
            _uiState.update { state ->
                state.copy(
                    apps = apps.map { (pkg, info) ->
                        AppUiState(
                            packageName = pkg,
                            label = info.label,
                            icon = info.icon,
                            family = info.family,
                            liquidGlassEnabled = ServiceLocator.isFeatureEnabled(pkg, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)
                        )
                    }
                )
            }
        }
    }

    fun toggleLiquidGlass(packageName: String, enable: Boolean) {
        ServiceLocator.setFeatureEnabled(packageName, PrefsSchema.Feature.LIQUID_GLASS_ENABLED, enable)
        _uiState.update { state ->
            state.copy(
                apps = state.apps.map { app ->
                    if (app.packageName == packageName) {
                        app.copy(liquidGlassEnabled = enable)
                    } else app
                }
            )
        }
    }

    fun toggleScope(packageName: String, enable: Boolean) {
        Logger.i { "Scope toggle for $packageName: $enable" }
    }
}

data class AppsUiState(
    val apps: List<AppUiState> = emptyList()
)

data class AppUiState(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val family: AppFamily,
    val isInScope: Boolean = false,
    val liquidGlassEnabled: Boolean = true
)
