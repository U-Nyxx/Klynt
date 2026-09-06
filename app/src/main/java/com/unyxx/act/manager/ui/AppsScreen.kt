package com.unyxx.act.manager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unyxx.act.manager.viewmodel.AppsViewModel
import com.unyxx.act.xposed.scope.AppFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KLYNT", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            LiquidGlassTabBar(
                items = listOf(
                    TabItem("Apps", Icons.Default.Menu, "apps"),
                    TabItem("Settings", Icons.Default.Settings, "settings"),
                    TabItem("Logs", Icons.Default.Star, "logs")
                ),
                selectedIndex = 0,
                onTabSelected = { /* nav */ }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                AppFamilySection(
                    title = "Telegram Ecosystem",
                    icon = Icons.Default.Email,
                    apps = uiState.apps.filter { app -> app.family == AppFamily.TELEGRAM },
                    onToggle = { pkg, enabled -> viewModel.toggleLiquidGlass(pkg, enabled) }
                )
            }

            item {
                AppFamilySection(
                    title = "TikTok",
                    icon = Icons.Default.Star,
                    apps = uiState.apps.filter { app -> app.family == AppFamily.TIKTOK },
                    onToggle = { pkg, enabled -> viewModel.toggleLiquidGlass(pkg, enabled) }
                )
            }

            item {
                AppFamilySection(
                    title = "YouTube",
                    icon = Icons.Default.PlayArrow,
                    apps = uiState.apps.filter { app -> app.family == AppFamily.OTHER && app.packageName.startsWith("com.google.android.youtube") },
                    onToggle = { pkg, enabled -> viewModel.toggleLiquidGlass(pkg, enabled) }
                )
            }

            item {
                AppFamilySection(
                    title = "Instagram",
                    icon = Icons.Default.Face,
                    apps = uiState.apps.filter { app -> app.packageName == "com.instagram.android" },
                    onToggle = { pkg, enabled -> viewModel.toggleLiquidGlass(pkg, enabled) }
                )
            }

            item {
                AppFamilySection(
                    title = "Twitter/X",
                    icon = Icons.Default.MailOutline,
                    apps = uiState.apps.filter { app -> app.packageName == "com.twitter.android" },
                    onToggle = { pkg, enabled -> viewModel.toggleLiquidGlass(pkg, enabled) }
                )
            }

            item {
                AppFamilySection(
                    title = "Others",
                    icon = Icons.Default.List,
                    apps = uiState.apps.filter { app -> app.family == AppFamily.OTHER && !app.packageName.startsWith("com.google.android.youtube") && app.packageName != "com.instagram.android" && app.packageName != "com.twitter.android" },
                    onToggle = { pkg, enabled -> viewModel.toggleLiquidGlass(pkg, enabled) }
                )
            }
        }
    }
}

@Composable
fun AppFamilySection(
    title: String,
    icon: ImageVector,
    apps: List<com.unyxx.act.manager.viewmodel.AppUiState>,
    onToggle: (String, Boolean) -> Unit
) {
    if (apps.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.size(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            apps.forEach { app ->
                AppRow(
                    app = app,
                    onToggle = onToggle
                )
            }
        }
    }
}

@Composable
fun AppRow(
    app: com.unyxx.act.manager.viewmodel.AppUiState,
    onToggle: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Image(
                        painter = painterResource(id = android.R.drawable.sym_def_app_icon),
                        contentDescription = app.label,
                        modifier = Modifier.size(40.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Switch(
                checked = app.liquidGlassEnabled,
                onCheckedChange = { enabled -> onToggle(app.packageName, enabled) }
            )
        }
    }
}
