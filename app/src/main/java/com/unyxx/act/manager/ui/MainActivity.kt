package com.unyxx.act.manager.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.unyxx.act.R
import com.unyxx.act.manager.di.ServiceLocator
import com.unyxx.act.manager.viewmodel.AppsViewModel
import com.unyxx.act.manager.viewmodel.HomeViewModel
import com.unyxx.act.manager.viewmodel.LogsViewModel
import com.unyxx.act.manager.viewmodel.SettingsViewModel
import com.unyxx.act.ui.theme.KlyntTheme
import kotlinx.coroutines.launch

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

    override fun onResume() {
        super.onResume()
        // User may return from LSPosed after ticking scope: re-read
        // binder state, scope grants and stats instead of showing stale red.
        homeViewModel.refresh()
        appsViewModel.refresh()
        settingsViewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge so list content shows through the glass bar.
        enableEdgeToEdge()
        setContent {
            KlyntTheme {
                // Silent daily update check (24h cache inside).
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    settingsViewModel.checkForUpdates(applicationContext, force = false)
                }
                MainScreen(
                    appsViewModel = appsViewModel,
                    homeViewModel = homeViewModel,
                    settingsViewModel = settingsViewModel,
                    logsViewModel = logsViewModel
                )
            }
        }
    }
}

/**
 * Full-bleed pager with a floating glass bar overlaid at the bottom.
 * Swiping pages and tapping tabs drive the same pager state, so the
 * pill indicator tracks finger position fractionally.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@androidx.compose.runtime.Composable
fun MainScreen(
    appsViewModel: AppsViewModel,
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    logsViewModel: LogsViewModel
) {
    val appsUiState by appsViewModel.uiState.collectAsState()
    val showLogs = ServiceLocator.isServiceAlive()
    val tabs = listOf(
        TabItem(
            stringResource(R.string.tab_home), "home",
            Icons.Filled.Home, Icons.Outlined.Home
        ),
        TabItem(
            stringResource(R.string.tab_apps), "apps",
            Icons.Filled.Menu, Icons.Outlined.Menu,
            appsUiState.apps.count { it.liquidGlassEnabled }
        ),
        TabItem(
            stringResource(R.string.tab_settings), "settings",
            Icons.Filled.Settings, Icons.Outlined.Settings
        ),
        TabItem(
            stringResource(R.string.tab_logs), "logs",
            Icons.Filled.Article, Icons.Outlined.Article
        )
    ).filter { it.route != "logs" || showLogs }

    // Recreate pager state when the tab COUNT changes (Logs tab appears
    // once the binder connects): a stale currentPage beyond the new size
    // used to crash the pager instead of clamping.
    androidx.compose.runtime.key(tabs.size) {
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            pageCount = { tabs.size }
        )
        val scope = rememberCoroutineScope()
        val selectedPage = pagerState.currentPage +
            pagerState.currentPageOffsetFraction

        Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (tabs.getOrNull(page)?.route) {
                    "home" -> HomeScreen(homeViewModel)
                    "apps" -> AppsScreen(appsViewModel)
                    "settings" -> SettingsScreen(settingsViewModel)
                    "logs" -> LogsScreen(logsViewModel)
                }
            }
            LiquidGlassTabBar(
                tabs = tabs,
                selectedPage = selectedPage,
                onPageSelected = { page ->
                    scope.launch {
                        pagerState.animateScrollToPage(page)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}
