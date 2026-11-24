package com.spotyflow.android.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min

/**
 * Compact, anti-TransactionTooLargeException bitmap renderer for all 5 SpotyFlow Home Screen Widgets.
 * Maximum sizes strictly capped:
 * 1x1 (160x160), 2x2 (260x260), 2x4 (260x460), 4x1 (420x110), 4x2 (420x220), cover <= 140x140.
 */
object SpotyFlowWidgetRenderer {

    fun isDarkTextStyle(style: WidgetStyle): Boolean {
        return style == WidgetStyle.GLASS || style == WidgetStyle.CLEAR_GLASS || style == WidgetStyle.LIGHT
    }

    fun renderBackground(
        widthPx: Int,
        heightPx: Int,
        config: WidgetConfig,
        accentArgb: Int
    ): Bitmap {
        val w = widthPx.coerceIn(64, 420)
        val h = heightPx.coerceIn(64, 460)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f)
        val maxRadius = min(w, h) * 0.42f
        val radius = (maxRadius * (config.cornerRadiusPct / 100f)).coerceAtLeast(8f)

        val thickFrac = config.glassThicknessPct / 100f
        val shineFrac = config.glassShinePct / 100f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (config.style) {
            WidgetStyle.GLASS -> {
                val baseAlpha = (155 + (thickFrac * 80f)).toInt().coerceIn(145, 240)
                val tintBg = blendArgb(0xFFFAFCFF.toInt(), accentArgb, 0.08f)
                fillPaint.shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    withAlpha(0xFFFFFFFF.toInt(), baseAlpha),
                    withAlpha(tintBg, (baseAlpha * 0.92f).toInt()),
                    Shader.TileMode.CLAMP
                )
            }
            WidgetStyle.CLEAR_GLASS -> {
                val baseAlpha = (75 + (thickFrac * 95f)).toInt().coerceIn(65, 175)
                fillPaint.shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    withAlpha(0xFFFFFFFF.toInt(), (baseAlpha + 25).coerceAtMost(200)),
                    withAlpha(0xFFF1F5F9.toInt(), baseAlpha),
                    Shader.TileMode.CLAMP
                )
            }
            WidgetStyle.LIGHT -> {
                fillPaint.color = 0xFAFFFFFF.toInt()
            }
            WidgetStyle.DARK -> {
                val darkTop = 0xEB151822.toInt()
                val darkBot = 0xF20B0D14.toInt()
                fillPaint.shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    darkTop, darkBot, Shader.TileMode.CLAMP
                )
            }
            WidgetStyle.COVER_COLOR -> {
                val rich = blendArgb(accentArgb, 0xFF0F172A.toInt(), 0.36f)
                val deeper = blendArgb(accentArgb, 0xFF090D16.toInt(), 0.62f)
                fillPaint.shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    withAlpha(rich, 240),
                    withAlpha(deeper, 248),
                    Shader.TileMode.CLAMP
                )
            }
        }
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        // Diagonal crystal glass specular shine
        if (shineFrac > 0.02f && config.style != WidgetStyle.LIGHT) {
            val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, w * 0.65f, h * 0.72f,
                    intArrayOf(
                        withAlpha(Color.WHITE, (shineFrac * 125f).toInt().coerceIn(0, 145)),
                        withAlpha(Color.WHITE, (shineFrac * 24f).toInt().coerceIn(0, 40)),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.42f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(rect, radius, radius, shinePaint)
        }

        // Specular rim border
        val rimAlpha = (90 + (shineFrac * 150f)).toInt().coerceIn(70, 245)
        val borderColor = if (isDarkTextStyle(config.style)) {
            withAlpha(Color.WHITE, rimAlpha)
        } else {
            withAlpha(Color.WHITE, (rimAlpha * 0.45f).toInt())
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            color = borderColor
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        return bmp
    }

    fun renderShapedCover(
        source: Bitmap?,
        sizePx: Int,
        shape: WidgetCoverShape,
        accentArgb: Int
    ): Bitmap {
        val sz = sizePx.coerceIn(48, 140)
        val out = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val rect = RectF(0f, 0f, sz.toFloat(), sz.toFloat())
        val radius = when (shape) {
            WidgetCoverShape.CIRCLE -> sz / 2f
            WidgetCoverShape.ROUNDED -> sz * 0.22f
            WidgetCoverShape.SQUARE -> sz * 0.06f
        }

        if (source != null && !source.isRecycled) {
            val scaled = Bitmap.createScaledBitmap(source, sz, sz, true)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            canvas.drawRoundRect(rect, radius, radius, paint)
            if (scaled !== source) scaled.recycle()
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, sz.toFloat(), sz.toFloat(),
                    accentArgb,
                    blendArgb(accentArgb, 0xFF0F172A.toInt(), 0.35f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        return out
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)
    }

    private fun blendArgb(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(c1) * inv + Color.red(c2) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
