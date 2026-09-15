package com.unyxx.act.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unyxx.act.manager.di.ServiceLocator
import com.unyxx.act.manager.viewmodel.LogsViewModel
import com.unyxx.act.xposed.prefs.PrefsSchema
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: LogsViewModel) {
    val events by viewModel.events.collectAsState()
    val lastSeq by viewModel.lastSequenceId.collectAsState()
    var autoScroll by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Realtime polling: fetch new events every 1 second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            viewModel.pollNewEvents()
        }
    }

    // Auto-scroll to bottom when new events arrive
    LaunchedEffect(events.size) {
        if (autoScroll) {
            // handled by LazyColumn state
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Logs", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.size(8.dp))
                        if (events.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${events.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                actions = {
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear logs")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (events.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Article, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.size(12.dp))
                Text("No events yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Hook diagnostics: LSPosed logs, tag KLYNT", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.size(16.dp))
                Button(onClick = { viewModel.refresh() }) { Text("Refresh") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp).padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(events) { event ->
                    LogRow(event = event, autoScroll = autoScroll)
                }
            }
        }
    }
}

@Composable
private fun LogRow(event: PrefsSchema.LogEvent, autoScroll: Boolean) {
    val color = when (event.type) {
        PrefsSchema.LogEventType.HOOK_SUCCESS -> Color(0xFF4CAF50)
        PrefsSchema.LogEventType.HOOK_FAIL -> Color(0xFFE53935)
        PrefsSchema.LogEventType.DISARM -> Color(0xFFE53935)
        PrefsSchema.LogEventType.FALLBACK -> Color(0xFFFB8C00)
        PrefsSchema.LogEventType.THERMAL_DOWNGRADE -> Color(0xFFFB8C00)
        PrefsSchema.LogEventType.SCOPE_CHANGE -> Color(0xFF2196F3)
        PrefsSchema.LogEventType.BINDER_CONNECT -> Color(0xFF4CAF50)
        PrefsSchema.LogEventType.BINDER_DISCONNECT -> Color(0xFFE53935)
        PrefsSchema.LogEventType.GHOST_APPEAR -> Color(0xFF00BCD4)
        PrefsSchema.LogEventType.GHOST_DISMISS -> Color(0xFF9E9E9E)
        PrefsSchema.LogEventType.TAB_SYNC -> Color(0xFF9C27B0)
        PrefsSchema.LogEventType.DETAIL_EXPAND -> Color(0xFF9E9E9E)
        PrefsSchema.LogEventType.STATUS_INFO -> Color(0xFF2196F3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
                // Header: package chain + timestamp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = color.copy(alpha = 0.2f)
                    ) {
                        Text(
                            event.type.name.take(8).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        event.packageChain.ifEmpty { "system" },
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        event.timestamp.toFormattedTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            Spacer(modifier = Modifier.size(4.dp))
            // Short description
            Text(
                event.shortDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Detail (expandable)
            if (event.detail != null) {
                var expanded by remember { mutableStateOf(false) }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Collapse" else "Detail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (expanded) {
                    Text(
                        event.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun Long.toFormattedTime(): String {
    return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(this))
}
