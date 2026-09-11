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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.unyxx.act.liquidglass.KlyntLiquidGlassView
import kotlin.math.roundToInt

/**
 * Floating glass capsule bottom bar (LSPosed-manager look, iOS feel).
 *
 * Layers bottom-to-top: live QWEA0 glass (real backdrop blur, same
 * engine as the hook overlay) → 1dp light border → top specular
 * highlight → one shared pill tracking the page fractionally (spring
 * on tap, 1:1 while dragging) → icon + label tabs.
 * Active tab uses filled glyphs in lavender, inactive outlined in white.
 *
 * @param selectedPage fractional page position (page + offset) so the
 * pill follows the finger during pager swipes.
 */
@Composable
fun LiquidGlassTabBar(
    tabs: List<TabItem>,
    selectedPage: Float,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val capsule = RoundedCornerShape(50)
    val selectedIndex = selectedPage.roundToInt().coerceIn(0, tabs.size - 1)
    val selectedLavender = Color(0xFFD7C6FF)
    val idleWhite = Color.White.copy(alpha = 0.87f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .height(76.dp)
            .clip(capsule)
    ) {
        // Live glass backdrop (own engine, zero new dependencies).
        AndroidView(
            factory = { context -> KlyntLiquidGlassView(context) },
            modifier = Modifier.matchParentSize()
        )
        // 1dp light border.
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, Color.White.copy(alpha = 0.15f), capsule)
        )
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
        // One shared pill tracking the page fractionally.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val indicatorWidth = 68.dp
            val targetX = maxWidth * (selectedPage + 0.5f) / tabs.size - indicatorWidth / 2
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
                            onPageSelected(index)
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
