package com.unyxx.act.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unyxx.act.R
import com.unyxx.act.manager.di.ServiceLocator
import com.unyxx.act.manager.viewmodel.CheckKey
import com.unyxx.act.manager.viewmodel.HomeViewModel
import com.unyxx.act.manager.viewmodel.SetupCheck

/** Landing dashboard: identity, module status, target stats. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val stats by viewModel.stats.collectAsState()
    val isModuleActive by viewModel.isModuleActive.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val checks by viewModel.checks.collectAsState()
    val context = LocalContext.current
    val appVersion = remember {
        val v = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.1"
        } catch (_: Exception) {
            "1.0.1"
        }
        "$v/${com.unyxx.act.BuildConfig.BUILD_CODENAME}"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KLYNT", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroCard(appVersion = appVersion, isModuleActive = isModuleActive, loaded = loaded)
            }
            item {
                val needsUsage by viewModel.needsUsagePermission.collectAsState()
                SetupChecklistCard(
                    checks = checks,
                    onRestartRowClick = {
                        if (needsUsage) {
                            try {
                                context.startActivity(
                                    com.unyxx.act.util.RestartDetector.usageAccessIntent()
                                )
                            } catch (_: Throwable) {
                            }
                        }
                    }
                )
            }
            item {
                StatsCard(stats = stats)
            }
        }
    }
}

/**
 * Status hero in the LSPosed-summary style: big state title + version
 * lines left, oversized status glyph right. Merges the old hero and
 * status cards so state lives in exactly one place.
 *
 * While the binder read is in flight ([loaded] == false) the card stays
 * neutral instead of flashing red — cold start used to paint
 * `errorContainer` for a frame before `refresh()` returned.
 */
@Composable
private fun HeroCard(appVersion: String, isModuleActive: Boolean, loaded: Boolean) {
    val container = when {
        !loaded -> MaterialTheme.colorScheme.surfaceContainerHigh
        isModuleActive -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when {
        !loaded -> MaterialTheme.colorScheme.onSurface
        isModuleActive -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    val title = when {
        !loaded -> stringResource(R.string.hero_checking)
        isModuleActive -> stringResource(R.string.hero_active)
        else -> stringResource(R.string.hero_inactive)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    "KLYNT v$appVersion",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onContainer.copy(alpha = 0.85f)
                )
                Text(
                    "libxposed API 101",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.7f)
                )
                Text(
                    if (isModuleActive && loaded) {
                        stringResource(R.string.hero_ready)
                    } else if (!loaded) {
                        stringResource(R.string.hero_checking)
                    } else {
                        stringResource(R.string.hero_enable_scope)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.7f)
                )
            }
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(onContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isModuleActive && loaded) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.Error
                    },
                    contentDescription = stringResource(R.string.hero_status_desc),
                    tint = onContainer,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

@Composable
private fun SetupChecklistCard(
    checks: List<SetupCheck>,
    onRestartRowClick: () -> Unit
) {
    if (checks.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(12.dp))
            checks.forEach { check ->
                val (label, hint) = when (check.key) {
                    CheckKey.BINDER -> stringResource(R.string.check_binder) to
                        stringResource(R.string.check_binder_hint)
                    CheckKey.SCOPE -> stringResource(R.string.check_scope) to
                        stringResource(R.string.check_scope_hint)
                    CheckKey.INSTALLED -> stringResource(R.string.check_installed) to
                        stringResource(R.string.check_installed_hint)
                    CheckKey.RESTART -> stringResource(R.string.check_restart) to
                        stringResource(R.string.check_restart_hint)
                }
                // Restart is auto-detected (UsageStats: foregrounded since
                // boot == hooks live). The row is tappable only to grant
                // the one-time usage-access permission — never a manual
                // "did you restart" quiz.
                val clickableRow = check.key == CheckKey.RESTART &&
                    check.detail == null && !check.done
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .then(
                            if (clickableRow) {
                                Modifier.clickable { onRestartRowClick() }
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (check.done) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        contentDescription = null,
                        tint = if (check.done) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (check.key == CheckKey.RESTART) {
                            Text(
                                if (check.done && !check.detail.isNullOrBlank()) {
                                    stringResource(
                                        R.string.restart_auto_seen,
                                        check.detail
                                    )
                                } else if (!check.done && !check.detail.isNullOrBlank()) {
                                    stringResource(
                                        R.string.restart_auto_waiting,
                                        check.detail
                                    )
                                } else {
                                    stringResource(R.string.restart_auto_noperm)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: ServiceLocator.Stats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Targets",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    StatItem("Scoped", stats.totalTargets.toString(), Icons.Filled.Menu)
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatItem("Installed", stats.installedTargets.toString(), Icons.Filled.CheckCircle)
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatItem("Enabled", stats.enabledTargets.toString(), Icons.Filled.Home)
                }
                Box(modifier = Modifier.weight(1f)) {
                    StatItem("Telegram", stats.telegramCount.toString(), Icons.Filled.Email)
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
