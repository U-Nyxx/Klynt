package com.unyxx.act.manager.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.unyxx.act.manager.viewmodel.AppsViewModel
import com.unyxx.act.manager.viewmodel.HomeViewModel
import com.unyxx.act.manager.viewmodel.LogsViewModel
import com.unyxx.act.manager.viewmodel.SettingsViewModel
import com.unyxx.act.ui.theme.KlyntTheme

class MainActivity : ComponentActivity() {

    private val appsViewModel: AppsViewModel by lazy {
        @Suppress("UNCHECKED_CAST")
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppsViewModel(applicationContext) as T
            }
        })[AppsViewModel::class.java]
    }

    private val homeViewModel: HomeViewModel by lazy {
        ViewModelProvider(this)[HomeViewModel::class.java]
    }

    private val settingsViewModel: SettingsViewModel by lazy {
        ViewModelProvider(this)[SettingsViewModel::class.java]
    }

    private val logsViewModel: LogsViewModel by lazy {
        ViewModelProvider(this)[LogsViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KlyntTheme {
                // Silent daily update check (24h cache inside).
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    settingsViewModel.checkForUpdates(applicationContext, force = false)
                }
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: "home"

                MainScreen(
                    navController = navController,
                    currentRoute = currentRoute,
                    appsViewModel = appsViewModel,
                    homeViewModel = homeViewModel,
                    settingsViewModel = settingsViewModel,
                    logsViewModel = logsViewModel
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun MainScreen(
    navController: androidx.navigation.NavHostController,
    currentRoute: String,
    appsViewModel: AppsViewModel,
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    logsViewModel: LogsViewModel
) {
    val appsUiState by appsViewModel.uiState.collectAsState()
    val enabledCount = appsUiState.apps.count { it.liquidGlassEnabled }

    androidx.compose.material3.Scaffold(
        bottomBar = {
            LiquidGlassTabBar(
                currentRoute = currentRoute,
                onTabSelected = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                },
                appsBadge = enabledCount,
                showLogs = com.unyxx.act.manager.di.ServiceLocator.isServiceAlive()
            )
        }
    ) { innerPadding ->
        AppNavHost(navController, appsViewModel, homeViewModel, settingsViewModel, logsViewModel, Modifier.padding(innerPadding))
    }
}