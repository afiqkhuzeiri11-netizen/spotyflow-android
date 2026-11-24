package com.spotyflow.android.ui.glass

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.spotyflow.android.ui.theme.SpotyColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Strictly separated sibling HazeStates to guarantee 0% runtime crash in Haze 1.6.10:
 * - LocalBgHazeState captures ONLY Layer 0 (PureWhiteAmbientCanvas) for cards inside scroll views.
 * - LocalContentHazeState captures Layer 0 + Layer 1 (Scroll Content) for floating top/bottom chrome.
 */
val LocalBgHazeState = compositionLocalOf<HazeState?> { null }
val LocalContentHazeState = compositionLocalOf<HazeState?> { null }

private val CardRimBrush = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.96f),
        Color.White.copy(alpha = 0.45f),
        Color(0xFFE2E8F0).copy(alpha = 0.55f)
    )
)

private val FloatingRimBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.98f),
        Color(0xFFE0F2FE).copy(alpha = 0.72f),
        Color.White.copy(alpha = 0.58f),
        Color(0xFFD1FAE5).copy(alpha = 0.65f)
    )
)

@Composable
fun SpotyGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    hazeState: HazeState? = LocalBgHazeState.current,
    tintAlpha: Float = 0.62f,
    elevation: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val style = remember(tintAlpha) {
        HazeStyle(
            backgroundColor = SpotyColors.PureWhiteCanvas,
            tints = listOf(HazeTint(color = Color.White.copy(alpha = tintAlpha))),
            blurRadius = 18.dp,
            noiseFactor = 0.02f
        )
    }

    val baseModifier = modifier
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color(0x140F172A),
            spotColor = Color(0x180F172A)
        )
        .clip(shape)
        .then(
            if (hazeState != null) {
                Modifier.hazeEffect(state = hazeState, style = style)
            } else {
                Modifier.background(Color.White.copy(alpha = 0.76f))
            }
        )
        .border(width = 1.2.dp, brush = CardRimBrush, shape = shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Box(modifier = baseModifier, content = content)
}

@Composable
fun SpotyFloatingGlass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    hazeState: HazeState? = LocalContentHazeState.current,
    tintAlpha: Float = 0.54f,
    elevation: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val style = remember(tintAlpha) {
        HazeStyle(
            backgroundColor = SpotyColors.PureWhiteCanvas,
            tints = listOf(HazeTint(color = Color.White.copy(alpha = tintAlpha))),
            blurRadius = 24.dp,
            noiseFactor = 0.03f
        )
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color(0x1A0F172A),
                spotColor = Color(0x220F172A)
            )
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState, style = style)
                } else {
                    Modifier.background(Color.White.copy(alpha = 0.82f))
                }
            )
            .border(width = 1.4.dp, brush = FloatingRimBrush, shape = shape),
        content = content
    )
}

@Composable
fun SpotyGlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    useFloatingLayer: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val hazeState = if (useFloatingLayer) LocalContentHazeState.current else LocalBgHazeState.current
    val style = remember {
        HazeStyle(
            backgroundColor = SpotyColors.PureWhiteCanvas,
            tints = listOf(HazeTint(color = Color.White.copy(alpha = 0.60f))),
            blurRadius = 20.dp,
            noiseFactor = 0.02f
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = Color(0x160F172A),
                spotColor = Color(0x1C0F172A)
            )
            .clip(CircleShape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState, style = style)
                } else {
                    Modifier.background(Color.White.copy(alpha = 0.80f))
                }
            )
            .border(width = 1.2.dp, brush = FloatingRimBrush, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun SpotyArtwork(
    coverUrl: String,
    accentColor: Color,
    size: Dp?,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(cornerRadius)
    val context = LocalContext.current
    val fallbackIconSize = if (size != null) size * 0.42f else 64.dp

    Box(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = accentColor.copy(alpha = 0.25f),
                spotColor = accentColor.copy(alpha = 0.30f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.78f),
                        accentColor.copy(alpha = 0.38f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(fallbackIconSize)
        )
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun SpotyMiniEqBars(
    isPlaying: Boolean,
    color: Color = SpotyColors.EmeraldCrystal,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "eqBars")
    val bar1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq3"
    )

    val heights = if (isPlaying) listOf(bar1, bar2, bar3) else listOf(0.35f, 0.55f, 0.35f)
    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp * frac)
                    .clip(RoundedCornerShape(99.dp))
                    .background(color)
            )
        }
    }
}

/**
 * Pure vector gradient flame icon for Streak Bar (zero cartoon emojis!).
 */
@Composable
fun SpotyFlameVector(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val outer = Path().apply {
            moveTo(w * 0.52f, h * 0.08f)
            cubicTo(w * 0.78f, h * 0.30f, w * 0.86f, h * 0.54f, w * 0.82f, h * 0.70f)
            cubicTo(w * 0.78f, h * 0.87f, w * 0.65f, h * 0.95f, w * 0.50f, h * 0.95f)
            cubicTo(w * 0.33f, h * 0.95f, w * 0.20f, h * 0.83f, w * 0.20f, h * 0.66f)
            cubicTo(w * 0.20f, h * 0.51f, w * 0.28f, h * 0.38f, w * 0.36f, h * 0.30f)
            cubicTo(w * 0.36f, h * 0.42f, w * 0.42f, h * 0.48f, w * 0.47f, h * 0.49f)
            cubicTo(w * 0.42f, h * 0.34f, w * 0.46f, h * 0.18f, w * 0.52f, h * 0.08f)
            close()
        }
        drawPath(
            path = outer,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFDE047),
                    SpotyColors.FlameAmber,
                    SpotyColors.FlameOrange
                )
            )
        )
        val inner = Path().apply {
            moveTo(w * 0.50f, h * 0.50f)
            cubicTo(w * 0.62f, h * 0.60f, w * 0.66f, h * 0.72f, w * 0.62f, h * 0.82f)
            cubicTo(w * 0.58f, h * 0.89f, w * 0.54f, h * 0.91f, w * 0.50f, h * 0.91f)
            cubicTo(w * 0.42f, h * 0.91f, w * 0.36f, h * 0.84f, w * 0.36f, h * 0.74f)
            cubicTo(w * 0.36f, h * 0.64f, w * 0.43f, h * 0.56f, w * 0.50f, h * 0.50f)
            close()
        }
        drawPath(path = inner, color = Color(0xFFFEF08A))
    }
}
