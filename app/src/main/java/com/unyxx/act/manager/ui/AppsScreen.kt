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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.unyxx.act.liquidglass.engine.KlyntGlassView
import com.unyxx.act.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unyxx.act.manager.di.ServiceLocator
import com.unyxx.act.manager.viewmodel.AppsViewModel
import com.unyxx.act.xposed.prefs.PrefsSchema
import com.unyxx.act.xposed.scope.AppFamily
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    // Recomputed every composition (not remember-once): after returning
    // from LSPosed + onResume refresh, the banner must reflect the grant.
    val isModuleActive = ServiceLocator.isModuleActive()

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
                .padding(12.dp)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isModuleActive) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.banner_inactive_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = stringResource(R.string.banner_inactive_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                AppFamilySection(
                    title = "Telegram Ecosystem",
                    icon = Icons.Default.Email,
                    apps = uiState.apps.filter { app -> app.family == AppFamily.TELEGRAM },
                    onToggle = { pkg, enabled -> viewModel.toggleLiquidGlass(pkg, enabled) },
                    onRequestScope = { pkg -> viewModel.requestScope(pkg) },
                    onIntensity = { pkg, v -> viewModel.setGlassIntensity(pkg, v) },
                    onCorner = { pkg, v -> viewModel.setGlassCorner(pkg, v) },
                    onBlur = { pkg, v -> viewModel.toggleBlur(pkg, v) },
                    onClear = { pkg, v -> viewModel.toggleClear(pkg, v) },
                    onMode = { pkg, m -> viewModel.setGhostMode(pkg, m) }
                )
            }

            item {
                AppFamilySection(
                    title = "Twitter/X",
                    icon = Icons.Default.MailOutline,
                    apps = uiState.apps.filter { app -> app.family == AppFamily.TWITTER },
                    onToggle = { pkg, enabled -> viewModel.toggleLiquidGlass(pkg, enabled) },
                    onRequestScope = { pkg -> viewModel.requestScope(pkg) },
                    onIntensity = { pkg, v -> viewModel.setGlassIntensity(pkg, v) },
                    onCorner = { pkg, v -> viewModel.setGlassCorner(pkg, v) },
                    onBlur = { pkg, v -> viewModel.toggleBlur(pkg, v) },
                    onClear = { pkg, v -> viewModel.toggleClear(pkg, v) },
                    onMode = { pkg, m -> viewModel.setGhostMode(pkg, m) }
                )
            }
        }
    }
}

@Composable
fun AppFamilySection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    apps: List<com.unyxx.act.manager.viewmodel.AppUiState>,
    onToggle: (String, Boolean) -> Unit,
    onRequestScope: (String) -> Unit,
    onIntensity: (String, Float) -> Unit,
    onCorner: (String, Float) -> Unit,
    onBlur: (String, Boolean) -> Unit,
    onClear: (String, Boolean) -> Unit,
    onMode: (String, com.unyxx.act.xposed.prefs.PrefsSchema.GhostMode) -> Unit
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
                    onToggle = onToggle,
                    onRequestScope = onRequestScope,
                    onIntensity = onIntensity,
                    onCorner = onCorner,
                    onBlur = onBlur,
                    onClear = onClear,
                    onMode = onMode
                )
            }
        }
    }
}

@Composable
fun AppRow(
    app: com.unyxx.act.manager.viewmodel.AppUiState,
    onToggle: (String, Boolean) -> Unit,
    onRequestScope: (String) -> Unit,
    onIntensity: (String, Float) -> Unit = { _, _ -> },
    onCorner: (String, Float) -> Unit = { _, _ -> },
    onBlur: (String, Boolean) -> Unit = { _, _ -> },
    onClear: (String, Boolean) -> Unit = { _, _ -> },
    onMode: (String, com.unyxx.act.xposed.prefs.PrefsSchema.GhostMode) -> Unit = { _, _ -> }
) {
    var expanded by remember(app.packageName) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        // Column (not Row): TunePanel is a full-width Column and used to
        // be squeezed as a direct child of this Row when expanded.
        Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    val icon = app.icon
                    if (icon != null) {
                        val bitmap = if (icon is android.graphics.drawable.BitmapDrawable) {
                            icon.bitmap
                        } else {
                            val width = if (icon.intrinsicWidth > 0) icon.intrinsicWidth else 100
                            val height = if (icon.intrinsicHeight > 0) icon.intrinsicHeight else 100
                            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            icon.setBounds(0, 0, canvas.width, canvas.height)
                            icon.draw(canvas)
                            bitmap
                        }
                        val imageBitmap = bitmap.asImageBitmap()
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = app.label,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(id = android.R.drawable.sym_def_app_icon),
                            contentDescription = app.label,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        "${app.packageName} • v${app.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (!app.isScopeGranted) {
                    TextButton(onClick = { onRequestScope(app.packageName) }) {
                        Text("Aktifkan scope")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = stringResource(R.string.tune_title),
                            tint = if (expanded) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Switch(
                        checked = app.liquidGlassEnabled,
                        onCheckedChange = { enabled -> onToggle(app.packageName, enabled) }
                    )
                }
            }
            if (expanded) {
                TunePanel(
                    app = app,
                    onIntensity = onIntensity,
                    onCorner = onCorner,
                    onBlur = onBlur,
                    onClear = onClear,
                    onMode = onMode
                )
            }
        }
        }
    }
}

/**
 * Live miniature preview of the per-app glass tune.
 *
 * Renders the real [KlyntGlassView] — not a mock gradient — over high-frequency
 * sample content (refraction is invisible over a flat color), driven by the same
 * [intensity], [cornerDp], [blurEnabled] and [clearMode] values the hook consumes.
 * What the user sees here is what the hooked app gets.
 *
 * SOC note: the preview inherits [KlyntGlassView]'s own tiering — full SHADER on
 * Adreno flagships, dispersion-free LITE on Mali/Dimensity, calm SCRIM tint when
 * the device is hot, low-RAM, or on reduced-transparency. No per-SOC branching
 * needed at this layer.
 *
 * @param intensity 0..1 lens strength.
 * @param cornerDp corner radius in dp; >= 999 renders as pill.
 * @param blurEnabled true for full backdrop blur, false for minimum radius.
 * @param clearMode Crystal Clear variant (max transparency, full refraction).
 */
@Composable
fun GlassPreview(
    intensity: Float,
    cornerDp: Float,
    blurEnabled: Boolean,
    clearMode: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = if (cornerDp >= 999f) CircleShape else RoundedCornerShape(cornerDp.coerceIn(0f, 64f).dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        // Sample content with hard color edges: gives the SDF lens something to bend.
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFF6C63FF)))
            Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFF00D4AA)))
            Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFFFF9500)))
            Box(Modifier.weight(1f).fillMaxSize().background(Color(0xFFFF4081)))
        }
        AndroidView(
            factory = { ctx ->
                KlyntGlassView(ctx).apply {
                    this.intensity = intensity
                    blurRadius = if (blurEnabled) 18f else 4f
                    this.clearMode = clearMode
                }
            },
            update = { v ->
                v.intensity = intensity
                v.blurRadius = if (blurEnabled) 18f else 4f
                v.clearMode = clearMode
            },
            modifier = Modifier.fillMaxSize()
        )
        Text(
            "Aa",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/** Expanded per-app tuning: live preview, bar mode, intensity, corner radius, blur. */
@Composable
private fun TunePanel(
    app: com.unyxx.act.manager.viewmodel.AppUiState,
    onIntensity: (String, Float) -> Unit,
    onCorner: (String, Float) -> Unit,
    onBlur: (String, Boolean) -> Unit,
    onClear: (String, Boolean) -> Unit,
    onMode: (String, com.unyxx.act.xposed.prefs.PrefsSchema.GhostMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GlassPreview(
            intensity = app.intensity,
            cornerDp = app.cornerDp,
            blurEnabled = app.blurEnabled,
            clearMode = app.clearGlass
        )
        ModeSelector(
            current = app.ghostMode,
            onSelect = { onMode(app.packageName, it) }
        )
        TuneSlider(
            label = stringResource(R.string.tune_intensity),
            valueLabel = "${(app.intensity * 100).toInt()}%",
            value = app.intensity,
            range = 0f..1f,
            onChange = { onIntensity(app.packageName, it) }
        )
        TuneSlider(
            label = stringResource(R.string.tune_corner),
            valueLabel = if (app.cornerDp >= 999f) {
                stringResource(R.string.tune_pill)
            } else {
                stringResource(R.string.unit_dp, app.cornerDp.toInt())
            },
            value = if (app.cornerDp >= 999f) 64f else app.cornerDp.coerceIn(0f, 64f),
            range = 0f..64f,
            onChange = { onCorner(app.packageName, it) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.cornerDp < 999f) {
                    TextButton(onClick = { onCorner(app.packageName, 999f) }) {
                        Text(stringResource(R.string.tune_pill))
                    }
                }
                Text(
                    stringResource(R.string.tune_blur),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(
                checked = app.blurEnabled,
                onCheckedChange = { onBlur(app.packageName, it) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.tune_clear),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = app.clearGlass,
                onCheckedChange = { onClear(app.packageName, it) }
            )
        }
    }
}

/** Three-way bar mode: ghost-first auto, forced custom, glass only. */
@Composable
private fun ModeSelector(
    current: com.unyxx.act.xposed.prefs.PrefsSchema.GhostMode,
    onSelect: (com.unyxx.act.xposed.prefs.PrefsSchema.GhostMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.tune_mode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val modes: List<com.unyxx.act.xposed.prefs.PrefsSchema.GhostMode> = listOf(
                com.unyxx.act.xposed.prefs.PrefsSchema.GhostMode.AUTO,
                com.unyxx.act.xposed.prefs.PrefsSchema.GhostMode.FORCE_GHOST,
                com.unyxx.act.xposed.prefs.PrefsSchema.GhostMode.GLASS_ONLY
            )
            val modeLabels = listOf(
                stringResource(R.string.mode_auto),
                stringResource(R.string.mode_ghost),
                stringResource(R.string.mode_glass)
            )
            modes.forEachIndexed { index, mode ->
                val label = modeLabels[index]
                val selected = mode == current
                if (selected) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { onSelect(mode) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, maxLines = 1)
                    }
                } else {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onSelect(mode) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TuneSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}