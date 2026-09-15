package com.unyxx.act.manager.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.unyxx.act.R
import com.unyxx.act.manager.di.ServiceLocator
import com.unyxx.act.xposed.prefs.PrefsSchema
import com.unyxx.act.manager.update.UpdateErrorCause
import com.unyxx.act.manager.update.UpdateRepository
import com.unyxx.act.manager.update.UpdateState
import com.unyxx.act.manager.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val globalEnabled by viewModel.globalEnabled.collectAsState()
    val autoStart by viewModel.autoStart.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val pureBlackOled by viewModel.pureBlackOled.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val followSystemAccent by viewModel.followSystemAccent.collectAsState()
    val language by viewModel.language.collectAsState()
    val logVerbose by viewModel.logVerbose.collectAsState()
    val logAutoscroll by viewModel.logAutoscroll.collectAsState()
    val logPaused by viewModel.logPaused.collectAsState()
    val logWordWrap by viewModel.logWordWrap.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val appVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.1" }
        catch (_: Exception) { "1.0.1" }
    }
    var exportDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_settings), fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
            item { SetupSection(viewModel, globalEnabled, autoStart) }
            item { ThemeSection(viewModel, themeMode, pureBlackOled, accentColor, followSystemAccent) }
            item { LanguageSection(viewModel, language) }
            item { UpdateSection(updateState, viewModel, context) }
            item { LogSection(viewModel, logVerbose, logAutoscroll, logPaused, logWordWrap) }
            item { BackupSection(viewModel, context, clipboard) { exportDialogOpen = true } }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        SettingInfo(title = "Version", subtitle = "v$appVersion · libxposed API 101")
                        SettingInfo(title = "Scope", subtitle = "26 Telegram variants + Twitter/X")
                        DiagnosticsButton()
                    }
                }
            }
        }
    }
    if (exportDialogOpen) {
        BackupDialog(viewModel, context, clipboard, onDismiss = { exportDialogOpen = false })
    }
}

@Composable
private fun SetupSection(viewModel: SettingsViewModel, globalEnabled: Boolean, autoStart: Boolean) {
    SettingsSection(title = stringResource(R.string.setup_title), icon = Icons.Filled.Tune) {
        SettingSwitch(
            title = stringResource(R.string.check_binder),
            subtitle = stringResource(R.string.check_binder_hint),
            icon = Icons.Filled.Tune,
            checked = globalEnabled,
            onChecked = { viewModel.setGlobalEnabled(it) }
        )
        SettingSwitch(
            title = stringResource(R.string.action_mark_restarted),
            subtitle = "Re-apply hooks after reboot",
            icon = Icons.Filled.RestartAlt,
            checked = autoStart,
            onChecked = { viewModel.setAutoStart(it) }
        )
    }
}

@Composable
private fun ThemeSection(viewModel: SettingsViewModel, themeMode: PrefsSchema.ThemeMode, pureBlackOled: Boolean, accentColor: PrefsSchema.AccentColor, followSystemAccent: Boolean) {
    SettingsSection(title = stringResource(R.string.section_theme), icon = Icons.Filled.Palette) {
        SegmentedControl(
            options = PrefsSchema.ThemeMode.values().toList(),
            labels = PrefsSchema.ThemeMode.values().map {
                when (it) {
                    PrefsSchema.ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                    PrefsSchema.ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                    PrefsSchema.ThemeMode.DARK -> stringResource(R.string.theme_dark)
                    PrefsSchema.ThemeMode.PURE_BLACK -> stringResource(R.string.theme_pure_black)
                }
            },
            selected = themeMode,
            onSelected = { viewModel.setThemeMode(it) }
        )
        if (themeMode != PrefsSchema.ThemeMode.PURE_BLACK) {
            SettingSwitch(
                title = stringResource(R.string.pure_black_oled),
                subtitle = stringResource(R.string.pure_black_oled_sub),
                icon = Icons.Filled.Contrast,
                checked = pureBlackOled,
                onChecked = { viewModel.setPureBlackOled(it) }
            )
        }
        AccentGrid(
            colors = PrefsSchema.AccentColor.values().toList(),
            selected = accentColor,
            onSelected = { viewModel.setAccentColor(it) },
            followSystemAccent = followSystemAccent
        )
        SettingSwitch(
            title = stringResource(R.string.follow_system_accent),
            subtitle = stringResource(R.string.follow_system_accent_sub),
            icon = Icons.Filled.Palette,
            checked = followSystemAccent,
            onChecked = { viewModel.setFollowSystemAccent(it) }
        )
    }
}

@Composable
private fun LanguageSection(viewModel: SettingsViewModel, language: PrefsSchema.Language) {
    SettingsSection(title = stringResource(R.string.section_language), icon = Icons.Filled.Language) {
        SegmentedControl(
            options = PrefsSchema.Language.values().toList(),
            labels = PrefsSchema.Language.values().map {
                when (it) {
                    PrefsSchema.Language.SYSTEM -> stringResource(R.string.lang_system)
                    PrefsSchema.Language.ENGLISH -> stringResource(R.string.lang_english)
                    PrefsSchema.Language.INDONESIAN -> stringResource(R.string.lang_indonesian)
                }
            },
            selected = language,
            onSelected = { viewModel.setLanguage(it) }
        )
    }
}

@Composable
private fun LogSection(viewModel: SettingsViewModel, verbose: Boolean, autoscroll: Boolean, paused: Boolean, wordWrap: Boolean) {
    SettingsSection(title = stringResource(R.string.section_log), icon = Icons.Filled.Article) {
        SettingSwitch(title = stringResource(R.string.log_verbose), subtitle = stringResource(R.string.log_verbose_sub), icon = Icons.Filled.BugReport, checked = verbose, onChecked = { viewModel.setLogVerbose(it) })
        SettingSwitch(title = stringResource(R.string.log_autoscroll), subtitle = stringResource(R.string.log_autoscroll_sub), icon = Icons.Filled.KeyboardArrowDown, checked = autoscroll, onChecked = { viewModel.setLogAutoscroll(it) })
        SettingSwitch(title = stringResource(R.string.log_pause), subtitle = stringResource(R.string.log_pause_sub), icon = Icons.Filled.PauseCircle, checked = paused, onChecked = { viewModel.setLogPaused(it) })
        SettingSwitch(title = stringResource(R.string.log_word_wrap), subtitle = stringResource(R.string.log_word_wrap_sub), icon = Icons.Filled.FormatAlignLeft, checked = wordWrap, onChecked = { viewModel.setLogWordWrap(it) })
        SettingAction(title = stringResource(R.string.log_export), subtitle = stringResource(R.string.log_export_sub), icon = Icons.Filled.ContentCopy, onClick = { /* export handled in parent */ })
    }
}

@Composable
private fun BackupSection(viewModel: SettingsViewModel, context: Context, clipboard: androidx.compose.ui.platform.ClipboardManager, onBackup: () -> Unit) {
    SettingsSection(title = stringResource(R.string.section_backup), icon = Icons.Filled.Backup) {
        SettingAction(title = stringResource(R.string.backup_settings), subtitle = stringResource(R.string.backup_settings_sub), icon = Icons.Filled.CloudUpload, onClick = onBackup)
        SettingAction(title = stringResource(R.string.restore_settings), subtitle = stringResource(R.string.restore_settings_sub), icon = Icons.Filled.CloudDownload, onClick = { /* TODO */ })
    }
}

@Composable
private fun BackupDialog(viewModel: SettingsViewModel, context: Context, clipboard: androidx.compose.ui.platform.ClipboardManager, onDismiss: () -> Unit) {
    val json = remember { viewModel.exportSettings(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_settings)) },
        text = { Text("JSON backup copied to clipboard", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(json)); onDismiss() }) { Text(stringResource(R.string.update_check_btn)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// ===== SEGMENTED CONTROL (LSPosed-parity pill selector) =====
@Composable
private fun <T : Enum<T>> SegmentedControl(
    options: List<T>,
    labels: List<String>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selected
                val weight = 1f / options.size
                Button(
                    onClick = { onSelected(option) },
                    modifier = Modifier
                        .weight(weight)
                        .padding(horizontal = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(labels[index], fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

// ===== ACCENT COLOR GRID (8 circles) =====
@Composable
private fun AccentGrid(
    colors: List<PrefsSchema.AccentColor>,
    selected: PrefsSchema.AccentColor,
    onSelected: (PrefsSchema.AccentColor) -> Unit,
    followSystemAccent: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = stringResource(R.string.accent_color), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.size(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            colors.forEach { color ->
                val isSelected = color == selected
                val colorValue = when (color) {
                    PrefsSchema.AccentColor.BLUE -> Color(0xFF6C63FF)
                    PrefsSchema.AccentColor.GREEN -> Color(0xFF00D4AA)
                    PrefsSchema.AccentColor.PURPLE -> Color(0xFFB066FF)
                    PrefsSchema.AccentColor.ORANGE -> Color(0xFFFF9500)
                    PrefsSchema.AccentColor.TEAL -> Color(0xFF00BFA5)
                    PrefsSchema.AccentColor.PINK -> Color(0xFFFF4081)
                    PrefsSchema.AccentColor.RED -> Color(0xFFE53935)
                    PrefsSchema.AccentColor.AMBER -> Color(0xFFFFC107)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colorValue)
                        .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { onSelected(color) }
                )
            }
        }
        Spacer(modifier = Modifier.size(4.dp))
        if (followSystemAccent) {
            Text("Following system accent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ===== REUSABLE COMPONENTS =====
@Composable
private fun SettingsSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.size(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(12.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingInfo(title: String, subtitle: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.update_check_btn)) }
    }
}

@Composable
private fun UpdateSection(
    state: UpdateState,
    viewModel: SettingsViewModel,
    context: Context
) {
    val localVersion = remember { UpdateRepository.localVersion(context) }
    val checkedAt = remember(state) {
        val ms = UpdateRepository.lastCheckMs(context)
        if (ms > 0L) {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(ms))
        } else null
    }
    SettingsSection(title = "Update", icon = Icons.Filled.Download) {
        when (state) {
            is UpdateState.Idle, is UpdateState.Checking -> {
                SettingInfo(
                    title = if (state is UpdateState.Checking) stringResource(R.string.update_checking) else stringResource(R.string.update_idle),
                    subtitle = stringResource(R.string.update_check_sub)
                )
                UpdateButton(
                    label = if (state is UpdateState.Checking) stringResource(R.string.update_checking) else stringResource(R.string.update_check_btn),
                    icon = Icons.Filled.Refresh,
                    enabled = state !is UpdateState.Checking,
                    onClick = { viewModel.checkForUpdates(context, force = true) }
                )
            }
            is UpdateState.UpToDate -> {
                SettingInfo(
                    title = stringResource(R.string.update_uptodate),
                    subtitle = stringResource(R.string.update_local_remote, "v$localVersion", "v${state.version}") + (checkedAt?.let { " · " + stringResource(R.string.update_checked_at, it) } ?: "")
                )
                UpdateButton(label = stringResource(R.string.update_check_again), icon = Icons.Filled.Refresh, enabled = true, onClick = { viewModel.checkForUpdates(context, force = true) })
            }
            is UpdateState.Available -> {
                val info = state.info
                SettingInfo(
                    title = stringResource(R.string.update_available, info.version),
                    subtitle = stringResource(R.string.update_local_remote, "v$localVersion", "v${info.version}") + " · " + stringResource(R.string.update_size_mb, info.sizeBytes / 1048576f)
                )
                Text(info.notes.ifBlank { stringResource(R.string.update_notes_fallback) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 10, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 6.dp))
                UpdateButton(label = stringResource(R.string.update_download_install), icon = Icons.Filled.Download, enabled = true, onClick = { viewModel.startDownload(context) })
            }
            is UpdateState.Downloading -> {
                val progress = state.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                }
                UpdateButton(label = stringResource(R.string.update_cancel), icon = Icons.Filled.Close, enabled = true, onClick = { viewModel.cancelDownload(context) })
            }
            is UpdateState.Downloaded -> {
                SettingInfo(title = stringResource(R.string.update_downloaded), subtitle = stringResource(R.string.update_downloaded_sub))
                UpdateButton(label = stringResource(R.string.update_install_now), icon = Icons.Filled.Download, enabled = true, onClick = { viewModel.installUpdate(context) })
            }
            is UpdateState.Failed -> {
                val hint = when (state.cause) {
                    UpdateErrorCause.Network -> stringResource(R.string.update_hint_network)
                    UpdateErrorCause.RateLimited -> stringResource(R.string.update_hint_rate)
                    UpdateErrorCause.NotFound -> stringResource(R.string.update_hint_notfound)
                    UpdateErrorCause.AssetMismatch -> stringResource(R.string.update_hint_asset)
                    else -> state.message
                }
                SettingInfo(title = stringResource(R.string.update_failed), subtitle = hint)
                UpdateButton(label = stringResource(R.string.update_retry), icon = Icons.Filled.Refresh, enabled = true, onClick = { viewModel.checkForUpdates(context, force = true) })
            }
        }
    }
}

@Composable
private fun UpdateButton(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text(label)
    }
}

@Composable
private fun DiagnosticsButton() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val diagnostics = remember { buildDiagnostics(context) }
    Button(onClick = { clipboard.setText(AnnotatedString(diagnostics)) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text("Salin info diagnostik")
    }
}

private fun buildDiagnostics(context: android.content.Context): String {
    val sb = StringBuilder()
    sb.appendLine("KLYNT diagnostics")
    try { val pi = context.packageManager.getPackageInfo(context.packageName, 0); sb.appendLine("manager=${pi.versionName} (${pi.longVersionCode})") } catch (_: Exception) { sb.appendLine("manager=?") }
    sb.appendLine("service=${ServiceLocator.isServiceAlive()}")
    sb.appendLine("active=${ServiceLocator.isModuleActive()}")
    sb.appendLine("scope=${ServiceLocator.getServiceScope().sorted()}")
    try { val targets = ServiceLocator.scopeManager().getInstallableTargetApps(); targets.forEach { entry -> sb.appendLine("${entry.key} v${entry.value.version} :: ${entry.value.label}") } } catch (_: Exception) { sb.appendLine("targets=?") }
    ServiceLocator.readCrashLog()?.let { crash -> sb.appendLine("--- last crash ---"); sb.appendLine(crash) }
    try { val proc = Runtime.getRuntime().exec(arrayOf("getprop", "debug.hwui.disable_blur")); val out = proc.inputStream.bufferedReader().readText().trim(); proc.waitFor(); sb.appendLine("sysBlurDisabled=${out == "true"}") } catch (_: Throwable) { sb.appendLine("sysBlurDisabled=?") }
    return sb.toString()
}
