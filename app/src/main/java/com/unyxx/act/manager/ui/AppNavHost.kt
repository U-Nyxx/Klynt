package com.unyxx.act.manager.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.unyxx.act.manager.viewmodel.AppsViewModel
import com.unyxx.act.manager.viewmodel.HomeViewModel
import com.unyxx.act.manager.viewmodel.LogsViewModel
import com.unyxx.act.manager.viewmodel.SettingsViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    appsViewModel: AppsViewModel,
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    logsViewModel: LogsViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(navController = navController, startDestination = "home", modifier = modifier) {
        composable("home") {
            HomeScreen(homeViewModel)
        }
        composable("apps") {
            AppsScreen(appsViewModel)
        }
        composable("settings") {
            SettingsScreen(settingsViewModel)
        }
        composable("logs") {
            LogsScreen(logsViewModel)
        }
    }
}
