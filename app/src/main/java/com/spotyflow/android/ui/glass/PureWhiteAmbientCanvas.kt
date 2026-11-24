package com.spotyflow.android.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.spotyflow.android.ui.theme.SpotyColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PureWhiteAmbientCanvas(
    orbPrimaryTarget: Color = SpotyColors.DefaultMintMist,
    orbSecondaryTarget: Color = SpotyColors.DefaultSkyCrystal,
    orbTertiaryTarget: Color = SpotyColors.DefaultSoftLilac,
    modifier: Modifier = Modifier
) {
    val orb1 by animateColorAsState(
        targetValue = orbPrimaryTarget,
        animationSpec = tween(durationMillis = 700),
        label = "ambientOrb1"
    )
    val orb2 by animateColorAsState(
        targetValue = orbSecondaryTarget,
        animationSpec = tween(durationMillis = 700),
        label = "ambientOrb2"
    )
    val orb3 by animateColorAsState(
        targetValue = orbTertiaryTarget,
        animationSpec = tween(durationMillis = 700),
        label = "ambientOrb3"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "ambientBreath")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientPhase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Pure crystal white base
        drawRect(color = SpotyColors.PureWhiteCanvas)

        val driftX = sin(phase) * w * 0.06f
        val driftY = cos(phase) * h * 0.04f

        // Orb 1: Top-left Mint Mist / Active Pastel
        val c1 = Offset(x = w * 0.16f + driftX, y = h * 0.12f + driftY)
        val r1 = w * 0.78f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orb1, Color.Transparent),
                center = c1,
                radius = r1
            ),
            radius = r1,
            center = c1
        )

        // Orb 2: Upper-right Sky Crystal
        val c2 = Offset(x = w * 0.88f - driftX * 0.8f, y = h * 0.24f - driftY * 0.9f)
        val r2 = w * 0.74f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orb2, Color.Transparent),
                center = c2,
                radius = r2
            ),
            radius = r2,
            center = c2
        )

        // Orb 3: Lower-center Soft Lilac (makes bottom floating glass dock refract softly)
        val c3 = Offset(x = w * 0.48f + driftX * 0.6f, y = h * 0.86f + driftY * 0.7f)
        val r3 = w * 0.82f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orb3, Color.Transparent),
                center = c3,
                radius = r3
            ),
            radius = r3,
            center = c3
        )
    }
}
