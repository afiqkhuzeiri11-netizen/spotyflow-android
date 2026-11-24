package com.spotyflow.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import com.spotyflow.android.ui.glass.SpotyWaterDropsOverlay
import com.spotyflow.android.ui.glass.rememberWaterDropRenderEffect
import com.spotyflow.android.ui.theme.SpotyColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val PrismaticWaterRim = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.98f),
        Color(0xFFFEF3C7).copy(alpha = 0.88f),
        Color(0xFFBAE6FD).copy(alpha = 0.88f),
        Color(0xFFA7F3D0).copy(alpha = 0.82f),
        Color.White.copy(alpha = 0.96f)
    )
)

private val ConvexWaterDomeFill = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.94f),
        Color.White.copy(alpha = 0.64f),
        Color(0xFFE0F2FE).copy(alpha = 0.56f),
        Color.White.copy(alpha = 0.84f)
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
    modifier: Modifier = Modifier,
    waterDropsEnabled: Boolean = true
) {
    val tabs = remember { SpotyTab.entries }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val activeIndex = tabs.indexOf(activeTab).coerceAtLeast(0)
    var isDragging by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(activeIndex) }
    var dragVelocityPx by remember { mutableFloatStateOf(0f) }

    // Primary water bubble X position + 3 lagging metaball trailing droplets
    val lensOffsetPx = remember { Animatable(0f) }
    val trail1X = remember { Animatable(0f) }
    val trail2X = remember { Animatable(0f) }
    val trail3X = remember { Animatable(0f) }

    // Expanding translucent water ripple ring on tap/release (380ms)
    val rippleProgress = remember { Animatable(0f) }
    var rippleCenterX by remember { mutableFloatStateOf(0f) }

    // Separate scaleX / scaleY for authentic jelly wobble & velocity squash-and-stretch
    val velocityStretch = (abs(dragVelocityPx) / 38f).coerceIn(0f, 0.14f)
    val targetScaleX = if (isDragging) 1.08f + velocityStretch else 1.0f
    val targetScaleY = if (isDragging) (1.06f - velocityStretch * 1.15f).coerceIn(0.88f, 1.12f) else 1.0f

    val bubbleScaleX by animateFloatAsState(
        targetValue = targetScaleX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "waterScaleX"
    )
    val bubbleScaleY by animateFloatAsState(
        targetValue = targetScaleY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "waterScaleY"
    )

    val trailingAlpha by animateFloatAsState(
        targetValue = if (isDragging && abs(dragVelocityPx) > 2.2f) 0.92f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "trailingDropAlpha"
    )

    SpotyFloatingGlass(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(68.dp),
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
            val innerHeightPx = with(density) { maxHeight.toPx() }
            val slotWidthPx = (innerWidthPx / tabs.size).coerceAtLeast(1f)
            val slotWidthDp = maxWidth / tabs.size

            // Keep lens & trailing droplets synced when activeTab changes
            LaunchedEffect(activeIndex, slotWidthPx) {
                if (!isDragging) {
                    hoveredIndex = activeIndex
                    val targetX = activeIndex * slotWidthPx
                    val center = targetX + slotWidthPx / 2f
                    launch {
                        lensOffsetPx.animateTo(
                            targetValue = targetX,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                    launch { trail1X.animateTo(center, spring(stiffness = Spring.StiffnessLow)) }
                    launch { trail2X.animateTo(center, spring(stiffness = Spring.StiffnessVeryLow)) }
                    launch { trail3X.animateTo(center, spring(stiffness = Spring.StiffnessVeryLow)) }
                }
            }

            // Optional AGSL RuntimeShader refraction on Android 13+ (0% crash fallback on API 26-32)
            val waterRenderEffect = rememberWaterDropRenderEffect(
                widthPx = innerWidthPx,
                heightPx = innerHeightPx,
                lensCenterX = lensOffsetPx.value + slotWidthPx * 0.5f,
                lensCenterY = innerHeightPx * 0.5f,
                lensHalfWidth = slotWidthPx * 0.46f * bubbleScaleX,
                lensHalfHeight = innerHeightPx * 0.44f * bubbleScaleY,
                rippleCenterX = rippleCenterX,
                rippleCenterY = innerHeightPx * 0.5f,
                rippleProgress = rippleProgress.value,
                enabled = waterDropsEnabled
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tabs.size, slotWidthPx, innerWidthPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downX = down.position.x
                            var moved = false
                            isDragging = true
                            dragVelocityPx = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                            val initialIdx = ((downX / slotWidthPx).toInt()).coerceIn(0, tabs.lastIndex)
                            hoveredIndex = initialIdx
                            val initialTarget = (downX - slotWidthPx / 2f).coerceIn(0f, innerWidthPx - slotWidthPx)
                            val initialCenter = initialTarget + slotWidthPx / 2f
                            scope.launch {
                                lensOffsetPx.animateTo(
                                    targetValue = initialTarget,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessHigh
                                    )
                                )
                            }
                            scope.launch { trail1X.snapTo(initialCenter) }
                            scope.launch { trail2X.snapTo(initialCenter) }
                            scope.launch { trail3X.snapTo(initialCenter) }

                            var lastPointerX = downX
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break

                                val curX = change.position.x
                                val delta = curX - lastPointerX
                                dragVelocityPx = dragVelocityPx * 0.55f + delta * 0.45f
                                if (abs(curX - downX) > 10f) moved = true
                                lastPointerX = curX

                                val clampedX = (curX - slotWidthPx / 2f).coerceIn(0f, innerWidthPx - slotWidthPx)
                                val bubbleCenter = clampedX + slotWidthPx / 2f
                                val nextIdx = (clampedX / slotWidthPx).roundToInt().coerceIn(0, tabs.lastIndex)
                                if (nextIdx != hoveredIndex) {
                                    hoveredIndex = nextIdx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }

                                scope.launch { lensOffsetPx.snapTo(clampedX) }
                                // Lagged metaball trailing droplets follow behind drag direction
                                val dirSign = if (dragVelocityPx >= 0f) -1f else 1f
                                scope.launch {
                                    trail1X.animateTo(
                                        targetValue = bubbleCenter + dirSign * (slotWidthPx * 0.34f),
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                                scope.launch {
                                    trail2X.animateTo(
                                        targetValue = bubbleCenter + dirSign * (slotWidthPx * 0.54f),
                                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                                    )
                                }
                                scope.launch {
                                    trail3X.animateTo(
                                        targetValue = bubbleCenter + dirSign * (slotWidthPx * 0.70f),
                                        animationSpec = spring(stiffness = Spring.StiffnessVeryLow)
                                    )
                                }
                                change.consume()
                            }

                            isDragging = false
                            dragVelocityPx = 0f
                            val finalIdx = if (moved) {
                                hoveredIndex
                            } else {
                                ((lastPointerX / slotWidthPx).toInt()).coerceIn(0, tabs.lastIndex)
                            }
                            hoveredIndex = finalIdx
                            val finalTarget = finalIdx * slotWidthPx
                            val finalCenter = finalTarget + slotWidthPx / 2f
                            rippleCenterX = finalCenter

                            onSelectTab(tabs[finalIdx])

                            // Settle water bubble with jelly wobble + merge trailing droplets + launch 380ms ripple
                            scope.launch {
                                lensOffsetPx.animateTo(
                                    targetValue = finalTarget,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            scope.launch { trail1X.animateTo(finalCenter, spring(stiffness = Spring.StiffnessMedium)) }
                            scope.launch { trail2X.animateTo(finalCenter, spring(stiffness = Spring.StiffnessMediumLow)) }
                            scope.launch { trail3X.animateTo(finalCenter, spring(stiffness = Spring.StiffnessLow)) }
                            scope.launch {
                                rippleProgress.snapTo(0f)
                                rippleProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                                )
                                rippleProgress.snapTo(0f)
                            }
                        }
                    }
            ) {
                // Active Convex Liquid Water-Drop Bubble (Jelly Wobble + Specular Dome Highlight)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = lensOffsetPx.value.roundToInt(), y = 0) }
                        .width(slotWidthDp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = bubbleScaleX
                            scaleY = bubbleScaleY
                            translationY = if (isDragging) -3.5f else 0f
                        }
                        .shadow(
                            elevation = if (isDragging) 14.dp else 8.dp,
                            shape = RoundedCornerShape(999.dp),
                            ambientColor = Color(0x1E0F172A),
                            spotColor = Color(0x2A10B981)
                        )
                        .clip(RoundedCornerShape(999.dp))
                        .background(ConvexWaterDomeFill)
                        .border(
                            width = 1.6.dp,
                            brush = PrismaticWaterRim,
                            shape = RoundedCornerShape(999.dp)
                        )
                ) {
                    // Upper-left crisp water dome specular highlight & bottom caustic catch
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawOval(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.92f),
                                    Color.White.copy(alpha = 0.0f)
                                ),
                                center = Offset(size.width * 0.28f, size.height * 0.22f),
                                radius = size.width * 0.36f
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFBAE6FD).copy(alpha = 0.42f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.76f, size.height * 0.82f),
                                radius = size.height * 0.45f
                            ),
                            radius = size.height * 0.45f,
                            center = Offset(size.width * 0.76f, size.height * 0.82f)
                        )
                    }
                }

                // 4 Crisp Monochrome Vector Tabs (Refracted softly via AGSL RenderEffect on API 33+)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (waterRenderEffect != null) {
                                renderEffect = waterRenderEffect
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isHighlighted = if (isDragging) hoveredIndex == index else activeIndex == index
                        val iconScale by animateFloatAsState(
                            targetValue = if (isHighlighted && isDragging) 1.24f else if (isHighlighted) 1.09f else 0.96f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "tabScale_$index"
                        )
                        val iconTilt by animateFloatAsState(
                            targetValue = if (isHighlighted && isDragging) {
                                when (tab) {
                                    SpotyTab.Home -> -7f
                                    SpotyTab.Search -> -15f
                                    SpotyTab.Library -> 9f
                                    SpotyTab.Profile -> 7f
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

                // Real Optical Water Drops Overlay (11 edge dew drops + metaball trails + water ripple ring)
                SpotyWaterDropsOverlay(
                    trailingDropXs = listOf(trail1X.value, trail2X.value, trail3X.value),
                    trailingDropAlpha = trailingAlpha,
                    rippleCenterX = rippleCenterX,
                    rippleProgress = rippleProgress.value,
                    enabled = waterDropsEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
