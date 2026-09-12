package com.unyxx.act.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unyxx.act.manager.update.UpdateState
import com.unyxx.act.manager.viewmodel.SettingsViewModel

/** Functional settings: global kill-switch, auto-start, about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val globalEnabled by viewModel.globalEnabled.collectAsState()
    val autoStart by viewModel.autoStart.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current
    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.1"
        } catch (_: Exception) {
            "1.0.1"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
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
                SettingsSection(title = "Core", icon = Icons.Filled.Tune) {
                    SettingSwitch(
                        title = "Global Liquid Glass",
                        subtitle = "Master switch — mirrored to hooks, no restart needed for new screens",
                        icon = Icons.Filled.Tune,
                        checked = globalEnabled,
                        onChecked = { viewModel.setGlobalEnabled(it) }
                    )
                    SettingSwitch(
                        title = "Auto-start on Boot",
                        subtitle = "Re-apply hooks after reboot (manager-local)",
                        icon = Icons.Filled.RestartAlt,
                        checked = autoStart,
                        onChecked = { viewModel.setAutoStart(it) }
                    )
                }
            }
            item {
                UpdateSection(
                    state = updateState,
                    onCheck = { viewModel.checkForUpdates(context, force = true) },
                    onDownload = { viewModel.startDownload(context) },
                    onCancel = { viewModel.cancelDownload(context) },
                    onInstall = { viewModel.installUpdate(context) }
                )
            }
            item {
                SettingsSection(title = "Preview", icon = Icons.Filled.Tune) {
                    GlassPreview()
                }
            }
            item {
                SettingsSection(title = "About", icon = Icons.Filled.Info) {
                    SettingInfo(
                        title = "Version",
                        subtitle = "v$appVersion · libxposed API 101"
                    )
                    SettingInfo(
                        title = "Scope",
                        subtitle = "26 Telegram variants + Twitter/X"
                    )
                    DiagnosticsButton()
                }
            }
        }
    }
}

/**
 * In-app update from GitHub releases: manual check, changelog preview,
 * system download with progress, package installer launch.
 */
@Composable
private fun UpdateSection(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit
) {
    val context = LocalContext.current
    // Installed version + last-check stamp: update info must show
    // BOTH sides (local → remote), never remote alone.
    val localVersion = remember {
        com.unyxx.act.manager.update.UpdateRepository.localVersion(context)
    }
    val checkedAt = remember(state) {
        val ms = com.unyxx.act.manager.update.UpdateRepository.lastCheckMs(context)
        if (ms > 0L) {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(ms))
        } else null
    }
    SettingsSection(title = "Update", icon = Icons.Filled.Download) {
        when (state) {
            is UpdateState.Idle, is UpdateState.Checking -> {
                SettingInfo(
                    title = if (state is UpdateState.Checking) {
                        stringResource(R.string.update_checking)
                    } else {
                        stringResource(R.string.update_idle)
                    },
                    subtitle = stringResource(R.string.update_check_sub)
                )
                UpdateButton(
                    label = if (state is UpdateState.Checking) {
                        stringResource(R.string.update_checking)
                    } else {
                        stringResource(R.string.update_check_btn)
                    },
                    icon = Icons.Filled.Refresh,
                    enabled = state !is UpdateState.Checking,
                    onClick = onCheck
                )
            }
            is UpdateState.UpToDate -> {
                SettingInfo(
                    title = stringResource(R.string.update_uptodate),
                    subtitle = stringResource(
                        R.string.update_local_remote, "v$localVersion", "v${state.version}"
                    ) + (checkedAt?.let { " · " + stringResource(R.string.update_checked_at, it) } ?: "")
                )
                UpdateButton(
                    label = stringResource(R.string.update_check_again),
                    icon = Icons.Filled.Refresh,
                    enabled = true,
                    onClick = onCheck
                )
            }
            is UpdateState.Available -> {
                val info = state.info
                SettingInfo(
                    title = stringResource(R.string.update_available, info.version),
                    subtitle = stringResource(
                        R.string.update_local_remote, "v$localVersion", "v${info.version}"
                    ) + " · " + stringResource(
                        R.string.update_size_mb, info.sizeBytes / 1048576f
                    )
                )
                // Signing-key rotation (v1.0.4+): in-app install from
                // v1.0.3 or older fails silently at the package installer.
                // Warn instead of letting the user tap into a dead end.
                // The check sunsets itself once everyone is past 1.0.3.
                if (com.unyxx.act.network.ReleaseInfo.compareVersions(localVersion, "1.0.3") <= 0) {
                    Text(
                        stringResource(R.string.update_rotation_warn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                Text(
                    info.notes.ifBlank { stringResource(R.string.update_notes_fallback) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 10,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                UpdateButton(
                    label = stringResource(R.string.update_download_install),
                    icon = Icons.Filled.Download,
                    enabled = true,
                    onClick = onDownload
                )
            }
            is UpdateState.Downloading -> {
                val progress = state.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }
                UpdateButton(
                    label = stringResource(R.string.update_cancel),
                    icon = Icons.Filled.Close,
                    enabled = true,
                    onClick = onCancel
                )
            }
            is UpdateState.Downloaded -> {
                SettingInfo(
                    title = stringResource(R.string.update_downloaded),
                    subtitle = stringResource(R.string.update_downloaded_sub)
                )
                UpdateButton(
                    label = stringResource(R.string.update_install_now),
                    icon = Icons.Filled.Download,
                    enabled = true,
                    onClick = onInstall
                )
            }
            is UpdateState.Failed -> {
                // Raw errors ("GitHub HTTP 403") mean nothing to users:
                // translate the common ones, keep the raw text as detail.
                val hint = when {
                    "403" in state.message ->
                        stringResource(R.string.update_hint_rate)
                    state.message.contains("resolve", ignoreCase = true) ||
                        state.message.contains("UnknownHost", ignoreCase = true) ||
                        state.message.contains("Connect", ignoreCase = true) ||
                        state.message.contains("timeout", ignoreCase = true) ||
                        state.message.contains("Network", ignoreCase = true) ->
                        stringResource(R.string.update_hint_network)
                    else -> state.message
                }
                SettingInfo(
                    title = stringResource(R.string.update_failed),
                    subtitle = hint
                )
                UpdateButton(
                    label = stringResource(R.string.update_retry),
                    icon = Icons.Filled.Refresh,
                    enabled = true,
                    onClick = onCheck
                )
            }
        }
    }
}

@Composable
private fun UpdateButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(label)
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

/**
 * One-tap bug-report bundle: scope, installed targets with versions,
 * service state. Paste it into an issue instead of "nggak jalan bang".
 */
@Composable
private fun DiagnosticsButton() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val diagnostics = remember {
        buildDiagnostics(context)
    }
    Button(
        onClick = { clipboard.setText(AnnotatedString(diagnostics)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text("Salin info diagnostik")
    }
}

private fun buildDiagnostics(context: android.content.Context): String {
    val sb = StringBuilder()
    sb.appendLine("KLYNT diagnostics")
    try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        sb.appendLine("manager=${pi.versionName} (${pi.longVersionCode})")
    } catch (_: Exception) {
        sb.appendLine("manager=?")
    }
    sb.appendLine("service=${com.unyxx.act.manager.di.ServiceLocator.isServiceAlive()}")
    sb.appendLine("active=${com.unyxx.act.manager.di.ServiceLocator.isModuleActive()}")
    sb.appendLine("scope=${com.unyxx.act.manager.di.ServiceLocator.getServiceScope().sorted()}")
    try {
        val targets = com.unyxx.act.manager.di.ServiceLocator.scopeManager()
            .getInstallableTargetApps()
        targets.forEach { (pkg, info) ->
            sb.appendLine("$pkg v${info.version} :: ${info.label}")
        }
    } catch (_: Exception) {
        sb.appendLine("targets=?")
    }
    com.unyxx.act.manager.di.ServiceLocator.readCrashLog()?.let { crash ->
        sb.appendLine("--- last crash ---")
        sb.appendLine(crash)
    }
    // ROM-level blur kill-switch (no root needed to READ the prop).
    // blur=off explains invisible glass better than any hook theory.
    try {
        val proc = Runtime.getRuntime().exec(arrayOf("getprop", "debug.hwui.disable_blur"))
        val out = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        sb.appendLine("sysBlurDisabled=${out == "true"}")
    } catch (_: Throwable) {
        sb.appendLine("sysBlurDisabled=?")
    }
    return sb.toString()
}

/**
 * Live preview of the REAL ghost bar (KlyntGhostBar) on dummy content.
 * What you see here is what hooked targets get — no native view, so no
 * preflight/fallback dance needed (the TabBar lesson, finally applied).
 */
@Composable
private fun GlassPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(10.dp)
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f))
                )
            }
        }
        AndroidView(
            factory = { ctx ->
                com.unyxx.act.liquidglass.ghost.KlyntGhostBar(ctx).apply {
                    labels = listOf("Obrolan", "Kontak", "Setelan", "Profil")
                    selectedIndex = 0
                    // Pill fills the preview box (inset a little).
                    post {
                        val pad = (resources.displayMetrics.density * 8).toInt()
                        setBarRect(pad, pad, width - pad, height - pad)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.85f).height(72.dp)
        )
    }
}

@Composable
private fun SettingInfo(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
