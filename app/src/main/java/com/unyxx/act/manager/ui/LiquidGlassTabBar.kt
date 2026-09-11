package com.unyxx.act.manager.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unyxx.act.R

/**
 * Floating glass capsule bottom bar (LSPosed-manager look, iOS feel).
 *
 * Layers bottom-to-top: dark scrim → 1dp light border → top specular
 * highlight → one shared sliding pill (spring) → icon + label tabs.
 * Active tab uses filled glyphs in lavender, inactive outlined in white.
 */
@Composable
fun LiquidGlassTabBar(
    currentRoute: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Enabled-target count shown as a badge on Apps; hidden when service is down. */
    appsBadge: Int = 0,
    showLogs: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val capsule = RoundedCornerShape(50)
    val tabs = listOf(
        TabItem(stringResource(R.string.tab_home), "home", Icons.Filled.Home, Icons.Outlined.Home),
        TabItem(stringResource(R.string.tab_apps), "apps", Icons.Filled.Menu, Icons.Outlined.Menu, appsBadge),
        TabItem(stringResource(R.string.tab_settings), "settings", Icons.Filled.Settings, Icons.Outlined.Settings),
        TabItem(stringResource(R.string.tab_logs), "logs", Icons.Filled.Article, Icons.Outlined.Article)
    ).filter { it.route != "logs" || showLogs }
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val selectedLavender = Color(0xFFD7C6FF)
    val idleWhite = Color.White.copy(alpha = 0.87f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .height(76.dp)
            .background(Color.Black.copy(alpha = 0.62f), capsule)
            .border(1.dp, Color.White.copy(alpha = 0.15f), capsule)
            .clip(capsule)
    ) {
        // Top specular highlight.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )
        // One shared sliding pill behind the active tab.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val indicatorWidth = 68.dp
            val targetX = maxWidth * (selectedIndex + 0.5f) / tabs.size - indicatorWidth / 2
            val pillX by animateDpAsState(
                targetValue = targetX,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "tab_pill"
            )
            Box(
                modifier = Modifier
                    .offset(x = pillX)
                    .width(indicatorWidth)
                    .height(56.dp)
                    .align(Alignment.CenterStart)
                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            )
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTabSelected(item.route)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally                        ) {
                            BadgedBox(
                                badge = {
                                    if (item.badge > 0) {
                                        Badge { Text(item.badge.toString()) }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) item.activeIcon else item.icon,
                                    contentDescription = item.label,
                                    tint = if (isSelected) selectedLavender else idleWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                item.label,
                                fontSize = 11.sp,
                                color = if (isSelected) selectedLavender else idleWhite
                            )
                        }
                    }
                }
            }
        }
    }
}

data class TabItem(
    val label: String,
    val route: String,
    val activeIcon: ImageVector,
    val icon: ImageVector,
    val badge: Int = 0
)
