package com.unyxx.act.manager.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unyxx.act.liquidglass.engine.configure
import com.unyxx.act.util.SocDetector
import kotlin.math.roundToInt

/**
 * Floating capsule bottom bar (LSPosed-manager look, iOS feel).
 *
 * Layers bottom-to-top: tonal scrim → 1dp border → top specular
 * highlight → one shared **glass pill** tracking the page fractionally
 * (spring on tap, 1:1 while dragging) → icon + label tabs.
 * Active tab uses filled glyphs, inactive outlined.
 *
 * RAM rule (learned from LSPosed's own manager): the bar background is
 * a cheap translucent scrim — the live [KlyntGlassView] lens lives ONLY
 * inside the ~68×56dp selected pill (frame 4 of the reference capture).
 * Shading 15x fewer pixels than a full-bar glass keeps this smooth on
 * Mali mid-range and costs nothing extra in APK size (same engine as
 * the hook overlay).
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
    val context = LocalContext.current
    val capsule = RoundedCornerShape(50)
    val pillShape = RoundedCornerShape(50)
    val selectedIndex = selectedPage.roundToInt().coerceIn(0, tabs.size - 1)
    // Dark: lavender-on-glass like the reference. Light: primary-on-glass
    // so the capsule stays readable instead of going muddy.
    val dark = isSystemInDarkTheme()
    val selectedTint = if (dark) {
        Color(0xFFD7C6FF)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val idleTint = if (dark) {
        Color.White.copy(alpha = 0.87f)
    } else {
        Color.Black.copy(alpha = 0.72f)
    }
    val borderTint = if (dark) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color.Black.copy(alpha = 0.12f)
    }
    val scrimTint = if (dark) {
        Color(0xFF1C1B20).copy(alpha = 0.82f)
    } else {
        Color.White.copy(alpha = 0.82f)
    }
    val highlightTop = if (dark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.05f)
    }

    // Profile once per composition: the pill lens follows the same
    // SOC tiers as the hook overlay (FULL/LITE/SCRIM).
    val profile = remember { SocDetector.resolve(context) }
    var glassView by remember { mutableStateOf<com.unyxx.act.liquidglass.engine.KlyntGlassView?>(null) }
    // Lens pop on every tab switch (platform rule: materialize, not fade).
    LaunchedEffect(selectedIndex) {
        glassView?.animateIntensityTo(1f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .height(76.dp)
            .clip(capsule)
            .background(scrimTint)
    ) {
        // 1dp light border.
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, borderTint, capsule)
        )
        // Top specular highlight.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(highlightTop, Color.Transparent)
                    )
                )
        )
        // One shared GLASS pill tracking the page fractionally.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val indicatorWidth = 68.dp
            val targetX = maxWidth * (selectedPage + 0.5f) / tabs.size - indicatorWidth / 2
            val pillX by animateDpAsState(
                targetValue = targetX,
                animationSpec = spring(
                    stiffness = Spring.StiffnessLow,
                    dampingRatio = Spring.DampingRatioMediumBouncy
                ),
                label = "tab_pill"
            )
            Box(
                modifier = Modifier
                    .offset(x = pillX)
                    .width(indicatorWidth)
                    .height(56.dp)
                    .align(Alignment.CenterStart)
                    .clip(pillShape)
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        com.unyxx.act.liquidglass.engine.KlyntGlassView(ctx).apply {
                            configure(profile, 1f, true)
                            addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
                                setBarRect(0, 0, r - l, b - t)
                            }
                            glassView = this
                            post { animateIntensityTo(1f) }
                        }
                    },
                    modifier = Modifier.matchParentSize()
                )
            }
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
                                    tint = if (isSelected) selectedTint else idleTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                item.label,
                                fontSize = 11.sp,
                                color = if (isSelected) selectedTint else idleTint
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
