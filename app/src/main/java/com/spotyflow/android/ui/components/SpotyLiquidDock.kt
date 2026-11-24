package com.spotyflow.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.SpotyTab
import com.spotyflow.android.ui.glass.SpotyFloatingGlass
import com.spotyflow.android.ui.theme.SpotyColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val PrismaticLensRim = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.98f),
        Color(0xFFFEF3C7).copy(alpha = 0.85f),
        Color(0xFFBAE6FD).copy(alpha = 0.86f),
        Color(0xFFA7F3D0).copy(alpha = 0.80f),
        Color.White.copy(alpha = 0.96f)
    )
)

private val ConvexLensFill = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.94f),
        Color.White.copy(alpha = 0.68f),
        Color(0xFFF1F5F9).copy(alpha = 0.82f)
    )
)

private fun iconForTab(tab: SpotyTab): ImageVector = when (tab) {
    SpotyTab.Home -> Icons.Rounded.Home
    SpotyTab.Search -> Icons.Rounded.Search
    SpotyTab.Library -> Icons.Rounded.LibraryMusic
    SpotyTab.Profile -> Icons.Rounded.Person
}

@Composable
fun SpotyLiquidDock(
    activeTab: SpotyTab,
    onSelectTab: (SpotyTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember { SpotyTab.entries }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val activeIndex = tabs.indexOf(activeTab).coerceAtLeast(0)
    var isDragging by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(activeIndex) }
    val lensOffsetPx = remember { Animatable(0f) }

    val lensScale by animateFloatAsState(
        targetValue = if (isDragging) 1.12f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lensScale"
    )

    SpotyFloatingGlass(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(66.dp),
        shape = RoundedCornerShape(999.dp),
        tintAlpha = 0.56f,
        elevation = 18.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
        ) {
            val innerWidthPx = with(density) { maxWidth.toPx() }
            val slotWidthPx = (innerWidthPx / tabs.size).coerceAtLeast(1f)
            val slotWidthDp = maxWidth / tabs.size

            // Keep lens aligned when activeTab changes programmatically or on first layout
            LaunchedEffect(activeIndex, slotWidthPx) {
                if (!isDragging) {
                    hoveredIndex = activeIndex
                    lensOffsetPx.animateTo(
                        targetValue = activeIndex * slotWidthPx,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }

            // Full-dock pointer gesture handler: press, drag 120 FPS, spring snap, or instant tap
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tabs.size, slotWidthPx, innerWidthPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downX = down.position.x
                            var moved = false
                            isDragging = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                            val initialIdx = ((downX / slotWidthPx).toInt()).coerceIn(0, tabs.lastIndex)
                            hoveredIndex = initialIdx
                            val initialTarget = (downX - slotWidthPx / 2f).coerceIn(0f, innerWidthPx - slotWidthPx)
                            scope.launch {
                                lensOffsetPx.animateTo(
                                    targetValue = initialTarget,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessHigh
                                    )
                                )
                            }

                            var lastPointerX = downX
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    break
                                }
                                val curX = change.position.x
                                if (abs(curX - downX) > 10f) {
                                    moved = true
                                }
                                lastPointerX = curX
                                val clampedX = (curX - slotWidthPx / 2f).coerceIn(0f, innerWidthPx - slotWidthPx)
                                val nextIdx = (clampedX / slotWidthPx).roundToInt().coerceIn(0, tabs.lastIndex)
                                if (nextIdx != hoveredIndex) {
                                    hoveredIndex = nextIdx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                scope.launch { lensOffsetPx.snapTo(clampedX) }
                                change.consume()
                            }

                            isDragging = false
                            val finalIdx = if (moved) {
                                hoveredIndex
                            } else {
                                ((lastPointerX / slotWidthPx).toInt()).coerceIn(0, tabs.lastIndex)
                            }
                            hoveredIndex = finalIdx
                            onSelectTab(tabs[finalIdx])
                            scope.launch {
                                lensOffsetPx.animateTo(
                                    targetValue = finalIdx * slotWidthPx,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    }
            ) {
                // Interactive Convex Liquid Lens Bubble
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = lensOffsetPx.value.roundToInt(), y = 0) }
                        .width(slotWidthDp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = lensScale
                            scaleY = lensScale
                            translationY = if (isDragging) -4f else 0f
                        }
                        .shadow(
                            elevation = if (isDragging) 14.dp else 8.dp,
                            shape = RoundedCornerShape(999.dp),
                            ambientColor = Color(0x1E0F172A),
                            spotColor = Color(0x2810B981)
                        )
                        .clip(RoundedCornerShape(999.dp))
                        .background(ConvexLensFill)
                        .border(
                            width = 1.6.dp,
                            brush = PrismaticLensRim,
                            shape = RoundedCornerShape(999.dp)
                        )
                )

                // 4 Crisp Monochrome Vector Tabs with per-icon micro-animation
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isHighlighted = if (isDragging) hoveredIndex == index else activeIndex == index
                        val iconScale by animateFloatAsState(
                            targetValue = if (isHighlighted && isDragging) 1.22f else if (isHighlighted) 1.08f else 0.96f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "tabScale_$index"
                        )
                        val iconTilt by animateFloatAsState(
                            targetValue = if (isHighlighted && isDragging) {
                                when (tab) {
                                    SpotyTab.Home -> -6f
                                    SpotyTab.Search -> -14f
                                    SpotyTab.Library -> 8f
                                    SpotyTab.Profile -> 6f
                                }
                            } else {
                                0f
                            },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "tabTilt_$index"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = iconForTab(tab),
                                contentDescription = tab.label,
                                tint = if (isHighlighted) SpotyColors.Slate900 else SpotyColors.Slate500,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                        rotationZ = iconTilt
                                    }
                            )
                            Text(
                                text = tab.label,
                                fontSize = 10.5.sp,
                                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                                color = if (isHighlighted) SpotyColors.Slate900 else SpotyColors.Slate500,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
