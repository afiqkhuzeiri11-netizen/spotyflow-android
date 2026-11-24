package com.spotyflow.android.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.spotyflow.android.data.local.WaterDropDensity
import kotlin.math.PI
import kotlin.math.sin

/**
 * AGSL RuntimeShader (Android 13+ / API 33+) producing true convex glass-water lens refraction,
 * subtle prismatic edge dispersion (warm gold / crystal white / sky cyan), and dynamic water ripple
 * wave displacement across the Liquid Glass Navbar.
 */
private const val WATER_DROP_NAVBAR_AGSL = """
    uniform shader composable;
    uniform float2 resolution;
    uniform float2 lensCenter;
    uniform float2 lensHalfSize;
    uniform float2 rippleCenter;
    uniform float rippleProgress;

    float sdRoundBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + float2(r, r);
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }

    half4 main(float2 fragCoord) {
        float2 samplePos = fragCoord;

        // 1. Convex Water-Drop Lens Refraction + Subtle Prismatic Chromatic Edge
        float2 rel = fragCoord - lensCenter;
        float cornerR = min(lensHalfSize.x, lensHalfSize.y);
        float d = sdRoundBox(rel, lensHalfSize, cornerR);

        float edgeMask = 0.0;
        if (d < 0.0) {
            float normDepth = clamp(-d / max(cornerR, 1.0), 0.0, 1.0);
            float rimBase = max(1.0 - normDepth, 0.0001);
            float rimFactor = pow(rimBase, 2.2);
            float2 dir = length(rel) > 0.001 ? normalize(rel) : float2(0.0, 0.0);
            // Magnify center gently & curve edges like a real water dome
            samplePos -= dir * (rimFactor * 5.2);
            edgeMask = smoothstep(0.0, 0.42, rimFactor);
        }

        // 2. Expanding Concentric Water Ripple Ring on Tap / Release
        if (rippleProgress > 0.001 && rippleProgress < 0.999) {
            float2 rRel = fragCoord - rippleCenter;
            float dist = length(rRel);
            float waveRadius = rippleProgress * max(resolution.x * 0.42, 120.0);
            float bandDelta = (dist - waveRadius) / 14.0;
            float ringBand = exp(-(bandDelta * bandDelta));
            float fade = 1.0 - rippleProgress;
            float2 rDir = dist > 0.001 ? (rRel / dist) : float2(0.0, 0.0);
            samplePos += rDir * (sin((dist - waveRadius) * 0.35) * ringBand * fade * 3.8);
        }

        float2 safeMax = max(resolution - float2(1.0, 1.0), float2(1.0, 1.0));
        float2 clamped = clamp(samplePos, float2(0.0, 0.0), safeMax);

        if (edgeMask > 0.02) {
            float disp = edgeMask * 1.35;
            float2 rPos = clamp(clamped + float2(disp, -disp * 0.4), float2(0.0, 0.0), safeMax);
            float2 bPos = clamp(clamped - float2(disp, -disp * 0.4), float2(0.0, 0.0), safeMax);
            half4 base = composable.eval(clamped);
            half r = composable.eval(rPos).r;
            half b = composable.eval(bPos).b;
            return half4(r, base.g, b, base.a);
        }

        return composable.eval(clamped);
    }
"""

/**
 * Safely creates a Compose RenderEffect backed by AGSL RuntimeShader on Android 13+ (API 33+).
 * Returns null cleanly on Android 8-12 (API 26-32) or OEM drivers that decline RuntimeShader,
 * allowing the 4-layer Canvas optical water engine to render with 0% crash risk.
 */
@Composable
fun rememberWaterDropRenderEffect(
    widthPx: Float,
    heightPx: Float,
    lensCenterX: Float,
    lensCenterY: Float,
    lensHalfWidth: Float,
    lensHalfHeight: Float,
    rippleCenterX: Float,
    rippleCenterY: Float,
    rippleProgress: Float,
    enabled: Boolean = true
): ComposeRenderEffect? {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    if (widthPx <= 2f || heightPx <= 2f) return null

    val runtimeShader = remember {
        try {
            RuntimeShader(WATER_DROP_NAVBAR_AGSL)
        } catch (_: Throwable) {
            null
        }
    } ?: return null

    return try {
        runtimeShader.setFloatUniform("resolution", widthPx, heightPx)
        runtimeShader.setFloatUniform("lensCenter", lensCenterX, lensCenterY)
        runtimeShader.setFloatUniform(
            "lensHalfSize",
            lensHalfWidth.coerceAtLeast(10f),
            lensHalfHeight.coerceAtLeast(10f)
        )
        runtimeShader.setFloatUniform("rippleCenter", rippleCenterX, rippleCenterY)
        runtimeShader.setFloatUniform("rippleProgress", rippleProgress.coerceIn(0f, 1f))
        RenderEffect.createRuntimeShaderEffect(runtimeShader, "composable")
            .asComposeRenderEffect()
    } catch (_: Throwable) {
        null
    }
}

private data class WaterDropSpec(
    val normX: Float,
    val normY: Float,
    val radiusDp: Float,
    val aspectX: Float,
    val aspectY: Float,
    val phaseOffset: Float
)

/**
 * 11 organic micro water droplets positioned carefully along outer glass edges & corners
 * so they never obscure tab icons or text labels.
 */
private val NAVBAR_MICRO_DROPS = listOf(
    WaterDropSpec(0.045f, 0.24f, 4.4f, 1.18f, 0.92f, 0.0f),
    WaterDropSpec(0.072f, 0.74f, 2.8f, 1.05f, 1.10f, 1.2f),
    WaterDropSpec(0.185f, 0.14f, 3.2f, 1.22f, 0.88f, 2.1f),
    WaterDropSpec(0.290f, 0.86f, 5.1f, 1.25f, 0.86f, 0.7f),
    WaterDropSpec(0.415f, 0.13f, 2.7f, 1.08f, 0.96f, 3.0f),
    WaterDropSpec(0.515f, 0.88f, 3.6f, 1.15f, 0.90f, 1.7f),
    WaterDropSpec(0.640f, 0.15f, 5.4f, 1.20f, 0.88f, 2.6f),
    WaterDropSpec(0.755f, 0.85f, 3.1f, 1.12f, 0.94f, 0.4f),
    WaterDropSpec(0.845f, 0.16f, 3.8f, 1.16f, 0.90f, 3.8f),
    WaterDropSpec(0.935f, 0.28f, 6.2f, 1.14f, 0.92f, 1.5f),
    WaterDropSpec(0.952f, 0.74f, 3.0f, 1.06f, 1.08f, 2.9f)
)

/**
 * Draws a single physically inspired 4-layer optical water droplet:
 * 1. Bottom-right diffused drop shadow + focused bright caustic light dot
 * 2. Translucent 3D refractive dome body (upper-left cool shade -> lower-right bright catch)
 * 3. Crisp pure-white primary specular glint + tiny secondary sparkle
 * 4. Fine 0.6dp Fresnel rim highlight
 */
fun DrawScope.drawOpticalWaterDrop(
    center: Offset,
    rx: Float,
    ry: Float,
    alphaScale: Float = 1f
) {
    if (rx <= 0.5f || ry <= 0.5f || alphaScale <= 0.01f) return
    val a = alphaScale.coerceIn(0f, 1f)
    val topLeft = Offset(center.x - rx, center.y - ry)
    val ovalSize = Size(rx * 2f, ry * 2f)

    // Layer 1a: Soft curved shadow underneath lower-right edge
    val shadowOffset = Offset(rx * 0.24f, ry * 0.30f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF0F172A).copy(alpha = 0.14f * a),
                Color(0xFF0F172A).copy(alpha = 0.04f * a),
                Color.Transparent
            ),
            center = center + shadowOffset,
            radius = maxOf(rx, ry) * 1.35f
        ),
        topLeft = topLeft + shadowOffset,
        size = ovalSize
    )

    // Layer 1b: Focused Caustic Light Dot (light refracted through the water dome to bottom-right)
    val causticCenter = Offset(center.x + rx * 0.36f, center.y + ry * 0.40f)
    val causticRadius = maxOf(rx, ry) * 0.52f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.88f * a),
                Color(0xFFE0F2FE).copy(alpha = 0.45f * a),
                Color.Transparent
            ),
            center = causticCenter,
            radius = causticRadius
        ),
        radius = causticRadius,
        center = causticCenter
    )

    // Layer 2: Transparent 3D Refractive Dome Body
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF0F172A).copy(alpha = 0.08f * a),
                Color.White.copy(alpha = 0.14f * a),
                Color.White.copy(alpha = 0.34f * a)
            ),
            start = topLeft,
            end = Offset(center.x + rx, center.y + ry)
        ),
        topLeft = topLeft,
        size = ovalSize
    )

    // Layer 3: Crisp Primary Specular Glint (upper-left) + Tiny Secondary Sparkle
    val primaryGlint = Offset(center.x - rx * 0.34f, center.y - ry * 0.34f)
    val primaryR = (minOf(rx, ry) * 0.32f).coerceAtLeast(1.1f)
    drawCircle(
        color = Color.White.copy(alpha = 0.95f * a),
        radius = primaryR,
        center = primaryGlint
    )

    val secondaryGlint = Offset(center.x - rx * 0.08f, center.y - ry * 0.48f)
    val secondaryR = (minOf(rx, ry) * 0.14f).coerceAtLeast(0.7f)
    drawCircle(
        color = Color.White.copy(alpha = 0.80f * a),
        radius = secondaryR,
        center = secondaryGlint
    )

    // Layer 4: Subtle Fresnel Rim Border (0.6.dp)
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.86f * a),
                Color(0xFFBAE6FD).copy(alpha = 0.42f * a),
                Color(0xFF0F172A).copy(alpha = 0.10f * a),
                Color.White.copy(alpha = 0.72f * a)
            ),
            start = topLeft,
            end = Offset(center.x + rx, center.y + ry)
        ),
        topLeft = topLeft,
        size = ovalSize,
        style = Stroke(width = 0.6.dp.toPx())
    )
}

/**
 * Renders 9-12 static & subtly breathing organic micro water droplets clinging to the navbar glass,
 * plus any active metaball trailing droplets and expanding water ripple ring.
 */
@Composable
fun SpotyWaterDropsOverlay(
    trailingDropXs: List<Float>,
    trailingDropAlpha: Float,
    rippleCenterX: Float,
    rippleProgress: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    density: WaterDropDensity = WaterDropDensity.FULL
) {
    if (!enabled) return

    val infiniteTransition = rememberInfiniteTransition(label = "waterDropletBreath")
    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dropletBreathPhase"
    )

    val visibleDrops = remember(density) {
        NAVBAR_MICRO_DROPS.take(density.dropCount.coerceIn(1, NAVBAR_MICRO_DROPS.size))
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // 1. Static & Breathing Organic Edge Dew Droplets (4-layer real water optics)
        visibleDrops.forEach { drop ->
            val breath = 1f + sin(breathPhase + drop.phaseOffset) * 0.045f
            val baseR = drop.radiusDp.dp.toPx() * breath
            val cx = w * drop.normX
            val cy = h * drop.normY
            drawOpticalWaterDrop(
                center = Offset(cx, cy),
                rx = baseR * drop.aspectX,
                ry = baseR * drop.aspectY,
                alphaScale = 0.94f * density.alphaScale
            )
        }

        // 2. Metaball Liquid Trailing Droplets when dragging the active water bubble
        if (trailingDropAlpha > 0.02f) {
            val baseSizes = listOf(10.5.dp.toPx(), 7.2.dp.toPx(), 4.6.dp.toPx())
            trailingDropXs.forEachIndexed { idx, tx ->
                val r = baseSizes.getOrElse(idx) { 4.dp.toPx() }
                val yOff = when (idx) {
                    0 -> 0f
                    1 -> -3.dp.toPx()
                    else -> 4.dp.toPx()
                }
                drawOpticalWaterDrop(
                    center = Offset(tx.coerceIn(r, w - r), h * 0.5f + yOff),
                    rx = r * 1.24f,
                    ry = r * 0.88f,
                    alphaScale = trailingDropAlpha * (1f - idx * 0.18f)
                )
            }
        }

        // 3. Expanding Translucent Water Ripple Ring on Tap / Release (380ms)
        if (rippleProgress in 0.01f..0.98f) {
            val fade = (1f - rippleProgress).coerceIn(0f, 1f)
            val radius = (18.dp.toPx()) + rippleProgress * (w * 0.24f)
            val center = Offset(rippleCenterX.coerceIn(0f, w), h * 0.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.65f * fade),
                        Color(0xFFBAE6FD).copy(alpha = 0.35f * fade),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center,
                style = Stroke(width = (2.4.dp.toPx() * fade).coerceAtLeast(0.8f))
            )
        }
    }
}
