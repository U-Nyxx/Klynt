package com.unyxx.act.manager.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unyxx.act.manager.di.ServiceLocator
import com.unyxx.act.util.Logger
import com.unyxx.act.xposed.prefs.PrefsSchema
import com.unyxx.act.xposed.scope.AppFamily
import com.unyxx.act.xposed.scope.ScopeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        viewModelScope.launch(Dispatchers.IO) {
            val appsMap = scopeManager.getInstallableTargetApps()
            val scope = ServiceLocator.getServiceScope()
            val appsList = appsMap.entries.map { entry ->
                val pkg = entry.key
                val info = entry.value
                val icon = ServiceLocator.loadAppIcon(pkg)
                val liquidGlassEnabled = ServiceLocator.isFeatureEnabled(pkg, PrefsSchema.Feature.LIQUID_GLASS_ENABLED)

                AppUiState(
                    packageName = pkg,
                    label = info.label,
                    icon = icon,
                    family = info.family,
                    liquidGlassEnabled = liquidGlassEnabled,
                    isScopeGranted = pkg in scope,
                    version = info.version,
                    intensity = ServiceLocator.getGlassIntensity(pkg),
                    cornerDp = ServiceLocator.getGlassCorner(pkg),
                    blurEnabled = ServiceLocator.isFeatureEnabled(pkg, PrefsSchema.Feature.BLUR_ENABLED),
                    ghostMode = ServiceLocator.getGhostMode(pkg),
                    clearGlass = ServiceLocator.isFeatureEnabled(pkg, PrefsSchema.Feature.GLASS_CLEAR)
                )
            }
            _uiState.value = _uiState.value.copy(apps = appsList)
        }
    }

    /** Asks LSPosed to enable [packageName], then refreshes scope state. */
    fun requestScope(packageName: String) {
        ServiceLocator.requestScope(packageName) { approved, message ->
            viewModelScope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                if (approved) refresh()
            }
        }
    }

    fun toggleLiquidGlass(packageName: String, enable: Boolean) {
        ServiceLocator.setFeatureEnabled(packageName, PrefsSchema.Feature.LIQUID_GLASS_ENABLED, enable)
        _uiState.value = _uiState.value.copy(
            apps = _uiState.value.apps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(liquidGlassEnabled = enable)
                } else app
            }
        )
    }

    fun setGlassIntensity(packageName: String, intensity: Float) {
        ServiceLocator.setGlassIntensity(packageName, intensity)
        _uiState.value = _uiState.value.copy(
            apps = _uiState.value.apps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(intensity = intensity.coerceIn(0f, 1f))
                } else app
            }
        )
    }

    fun setGlassCorner(packageName: String, cornerDp: Float) {
        ServiceLocator.setGlassCorner(packageName, cornerDp)
        _uiState.value = _uiState.value.copy(
            apps = _uiState.value.apps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(cornerDp = cornerDp.coerceIn(0f, 999f))
                } else app
            }
        )
    }

    fun setGhostMode(packageName: String, mode: PrefsSchema.GhostMode) {
        ServiceLocator.setGhostMode(packageName, mode)
        _uiState.value = _uiState.value.copy(
            apps = _uiState.value.apps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(ghostMode = mode)
                } else app
            }
        )
    }

    fun toggleClear(packageName: String, enable: Boolean) {
        ServiceLocator.setFeatureEnabled(packageName, PrefsSchema.Feature.GLASS_CLEAR, enable)
        _uiState.value = _uiState.value.copy(
            apps = _uiState.value.apps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(clearGlass = enable)
                } else app
            }
        )
    }

    fun toggleBlur(packageName: String, enable: Boolean) {
        ServiceLocator.setFeatureEnabled(packageName, PrefsSchema.Feature.BLUR_ENABLED, enable)
        _uiState.value = _uiState.value.copy(
            apps = _uiState.value.apps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(blurEnabled = enable)
                } else app
            }
        )
    }

    fun refresh() {
        loadApps()
    }
}

data class AppsUiState(
    val apps: List<AppUiState> = emptyList()
)

data class AppUiState(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?,
    val family: AppFamily,
    val liquidGlassEnabled: Boolean,
    val isScopeGranted: Boolean = false,
    val version: String = "?",
    val intensity: Float = 1f,
    val cornerDp: Float = 999f,
    val blurEnabled: Boolean = true,
    val ghostMode: PrefsSchema.GhostMode = PrefsSchema.GhostMode.AUTO,
    val clearGlass: Boolean = false
)
