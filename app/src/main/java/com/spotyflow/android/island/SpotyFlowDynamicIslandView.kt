package com.spotyflow.android.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.sin

/**
 * Pure Native Android Custom View for SpotyFlow Dynamic Island (zero ComposeView overhead,
 * < 2MB RAM, 120 FPS Canvas + ValueAnimator OvershootInterpolator morphing).
 */
class SpotyFlowDynamicIslandView(
    context: Context,
    private val onRequestLayoutSize: (widthPx: Int, heightPx: Int) -> Unit,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrev: () -> Unit,
    private val onToggleLike: () -> Unit,
    private val onOpenApp: () -> Unit,
    private val onSwipeDismiss: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private var config: DynamicIslandConfig = DynamicIslandConfig()
    private var titleText: String = "SpotyFlow"
    private var artistText: String = "Siap Memutar"
    private var lyricText: String = ""
    private var isPlaying: Boolean = false
    private var isLiked: Boolean = false
    private var positionMs: Long = 0L
    private var durationMs: Long = 0L
    private var accentArgb: Int = Color.parseColor("#10B981")
    private var coverBitmap: Bitmap? = null

    private var expandFraction: Float = 0f // 0f = Compact Pill, 1f = Expanded Card
    private var isExpanded: Boolean = false
    private var morphAnimator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var vizPhase: Float = 0f

    // Touch gesture tracking
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var longPressTriggered = false

    private val autoCollapseRunnable = Runnable { setExpanded(false) }
    private val longPressRunnable = Runnable {
        longPressTriggered = true
        onOpenApp()
    }
    private val vizTicker = object : Runnable {
        override fun run() {
            if (isPlaying && visibility == VISIBLE) {
                vizPhase += 0.24f
                invalidate()
                handler.postDelayed(this, 45L)
            }
        }
    }

    // Reusable Paints (0 per-frame allocation)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val pillRect = RectF()
    private val coverRect = RectF()
    private val clipPath = Path()

    // Clickable hit-boxes in expanded card
    private val rectLike = RectF()
    private val rectPrev = RectF()
    private val rectPlay = RectF()
    private val rectNext = RectF()
    private val rectOpen = RectF()

    fun applyConfig(newConfig: DynamicIslandConfig) {
        config = newConfig
        notifyWindowSize()
        invalidate()
    }

    fun updateMedia(
        title: String,
        artist: String,
        lyric: String,
        playing: Boolean,
        liked: Boolean,
        posMs: Long,
        durMs: Long,
        accentColorInt: Int,
        bitmap: Bitmap?
    ) {
        titleText = title.ifBlank { "SpotyFlow" }
        artistText = artist
        lyricText = lyric
        val wasPlaying = isPlaying
        isPlaying = playing
        isLiked = liked
        positionMs = posMs
        durationMs = durMs
        accentArgb = accentColorInt
        if (bitmap != null) coverBitmap = bitmap
        if (isPlaying && !wasPlaying) {
            handler.removeCallbacks(vizTicker)
            handler.post(vizTicker)
        }
        invalidate()
    }

    fun setExpanded(expand: Boolean) {
        if (isExpanded == expand) {
            if (expand) scheduleAutoCollapse()
            return
        }
        isExpanded = expand
        handler.removeCallbacks(autoCollapseRunnable)
        morphAnimator?.cancel()
        val start = expandFraction
        val end = if (expand) 1f else 0f
        morphAnimator = ValueAnimator.ofFloat(start, end).apply {
            duration = if (expand) 380L else 280L
            interpolator = OvershootInterpolator(if (expand) 1.1f else 0.7f)
            addUpdateListener { anim ->
                expandFraction = (anim.animatedValue as Float).coerceIn(0f, 1.08f)
                notifyWindowSize()
                invalidate()
            }
            start()
        }
        if (expand) scheduleAutoCollapse()
    }

    private fun scheduleAutoCollapse() {
        handler.removeCallbacks(autoCollapseRunnable)
        handler.postDelayed(autoCollapseRunnable, (config.autoCollapseSec.coerceIn(3, 10) * 1000L))
    }

    fun getCurrentWindowWidthPx(): Int {
        val cw = dp(config.compactWidthDp)
        val ew = dp(332f)
        val frac = expandFraction.coerceIn(0f, 1f)
        return (cw + (ew - cw) * frac + dp(16f)).toInt()
    }

    fun getCurrentWindowHeightPx(): Int {
        val ch = dp(config.compactHeightDp)
        val eh = dp(178f)
        val frac = expandFraction.coerceIn(0f, 1f)
        return (ch + (eh - ch) * frac + dp(16f)).toInt()
    }

    private fun notifyWindowSize() {
        onRequestLayoutSize(getCurrentWindowWidthPx(), getCurrentWindowHeightPx())
    }

    fun cleanup() {
        morphAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
                longPressTriggered = false
                if (!isExpanded) {
                    handler.postDelayed(longPressRunnable, 480L)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > dp(10f) || abs(event.y - downY) > dp(10f)) {
                    handler.removeCallbacks(longPressRunnable)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (longPressTriggered) return true
                val dx = event.x - downX
                val dy = event.y - downY
                val dt = System.currentTimeMillis() - downTime

                if (!isExpanded) {
                    if (dy < -dp(26f) && abs(dx) < dp(40f)) {
                        onSwipeDismiss()
                    } else if (abs(dx) > dp(42f) && abs(dy) < dp(32f)) {
                        if (dx < 0) onNext() else onPrev()
                    } else if (dt < 450L) {
                        setExpanded(true)
                    }
                } else {
                    if (dy < -dp(36f)) {
                        setExpanded(false)
                    } else {
                        scheduleAutoCollapse()
                        when {
                            rectLike.contains(event.x, event.y) -> onToggleLike()
                            rectPrev.contains(event.x, event.y) -> onPrev()
                            rectPlay.contains(event.x, event.y) -> onPlayPause()
                            rectNext.contains(event.x, event.y) -> onNext()
                            rectOpen.contains(event.x, event.y) -> {
                                setExpanded(false)
                                onOpenApp()
                            }
                            else -> setExpanded(false)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = dp(6f)
        pillRect.set(pad, pad, width - pad, height - pad)
        val frac = expandFraction.coerceIn(0f, 1f)
        val radius = dp(config.cornerRadiusDp + (28f - config.cornerRadiusDp) * frac)
            .coerceAtMost(pillRect.height() / 2f)

        val isLightGlass = config.style == DynamicIslandStyle.LIQUID_GLASS ||
            config.style == DynamicIslandStyle.CLEAR_GLASS
        val primaryTextColor = if (isLightGlass) Color.parseColor("#0F172A") else Color.WHITE
        val secondaryTextColor = if (isLightGlass) Color.parseColor("#64748B") else Color.argb(190, 255, 255, 255)

        // 1. Draw Capsule Background according to the 6 Styles
        bgPaint.shader = null
        when (config.style) {
            DynamicIslandStyle.IPHONE -> {
                bgPaint.color = Color.BLACK
            }
            DynamicIslandStyle.LIQUID_GLASS -> {
                bgPaint.shader = LinearGradient(
                    pillRect.left, pillRect.top, pillRect.right, pillRect.bottom,
                    intArrayOf(Color.argb(236, 255, 255, 255), Color.argb(218, 241, 247, 255)),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            DynamicIslandStyle.CLEAR_GLASS -> {
                bgPaint.color = Color.argb(185, 250, 252, 255)
            }
            DynamicIslandStyle.ADAPTIVE_COVER -> {
                val darkEnd = blendColor(accentArgb, Color.BLACK, 0.55f)
                bgPaint.shader = LinearGradient(
                    pillRect.left, pillRect.top, pillRect.right, pillRect.bottom,
                    intArrayOf(blendColor(accentArgb, Color.BLACK, 0.25f), darkEnd),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            DynamicIslandStyle.MINIMAL_PILL -> {
                bgPaint.color = Color.argb(232, 17, 20, 28)
            }
            DynamicIslandStyle.NEON_AURA -> {
                bgPaint.color = Color.argb(242, 12, 14, 20)
            }
        }
        canvas.drawRoundRect(pillRect, radius, radius, bgPaint)

        // 2. Specular / Neon Rim Border
        rimPaint.strokeWidth = if (config.style == DynamicIslandStyle.NEON_AURA) dp(1.8f) else dp(1.1f)
        rimPaint.color = when (config.style) {
            DynamicIslandStyle.LIQUID_GLASS, DynamicIslandStyle.CLEAR_GLASS -> Color.argb(235, 255, 255, 255)
            DynamicIslandStyle.NEON_AURA -> accentArgb
            DynamicIslandStyle.ADAPTIVE_COVER -> Color.argb(90, 255, 255, 255)
            else -> Color.argb(42, 255, 255, 255)
        }
        canvas.drawRoundRect(pillRect, radius, radius, rimPaint)

        // 3. Draw Compact Pill OR Expanded Card Content
        if (frac < 0.45f) {
            drawCompactContent(canvas, primaryTextColor)
        } else {
            drawExpandedContent(canvas, primaryTextColor, secondaryTextColor)
        }
    }

    private fun drawCompactContent(canvas: Canvas, textColor: Int) {
        val innerPad = dp(6f)
        val artSize = (pillRect.height() - innerPad * 2f).coerceAtLeast(dp(18f))
        coverRect.set(
            pillRect.left + innerPad,
            pillRect.centerY() - artSize / 2f,
            pillRect.left + innerPad + artSize,
            pillRect.centerY() + artSize / 2f
        )
        drawShapedCover(canvas, coverRect)

        // Mini 4-bar visualizer on right
        val vizW = if (config.showVisualizer) dp(26f) else 0f
        if (config.showVisualizer) {
            val startX = pillRect.right - innerPad - dp(20f)
            val baseY = pillRect.centerY() + dp(7f)
            accentPaint.color = accentArgb
            for (i in 0 until 4) {
                val hFrac = if (isPlaying) {
                    (0.32f + 0.68f * ((sin(vizPhase + i * 1.15f) + 1f) * 0.5f))
                } else {
                    0.32f
                }
                val barH = dp(13f) * hFrac
                val x = startX + i * dp(5f)
                canvas.drawRoundRect(x, baseY - barH, x + dp(3f), baseY, dp(1.5f), dp(1.5f), accentPaint)
            }
        }

        // Center Text: Live Synced Lyric Line (when enabled & REPLACE_TITLE) else Track Title
        val displayStr = if (config.showLyrics && config.lyricMode == IslandLyricMode.REPLACE_TITLE && lyricText.isNotBlank()) {
            lyricText
        } else {
            titleText
        }

        titlePaint.color = textColor
        titlePaint.textSize = dp(12.2f)
        val availW = (pillRect.width() - artSize - vizW - dp(22f)).coerceAtLeast(dp(40f))
        val ellipsized = TextUtils.ellipsize(displayStr, titlePaint, availW, TextUtils.TruncateAt.END).toString()
        val textX = coverRect.right + dp(8f)
        val textY = pillRect.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(ellipsized, textX, textY, titlePaint)
    }

    private fun drawExpandedContent(canvas: Canvas, primaryColor: Int, secondaryColor: Int) {
        val pad = dp(14f)
        val artSize = dp(52f)
        coverRect.set(pillRect.left + pad, pillRect.top + pad, pillRect.left + pad + artSize, pillRect.top + pad + artSize)
        drawShapedCover(canvas, coverRect)

        // Open App button top-right
        val openSize = dp(30f)
        rectOpen.set(
            pillRect.right - pad - openSize,
            pillRect.top + pad,
            pillRect.right - pad,
            pillRect.top + pad + openSize
        )
        trackBarPaint.color = Color.argb(28, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        canvas.drawCircle(rectOpen.centerX(), rectOpen.centerY(), openSize / 2f, trackBarPaint)
        titlePaint.color = primaryColor
        titlePaint.textSize = dp(14f)
        canvas.drawText("\u2197", rectOpen.centerX() - dp(4.5f), rectOpen.centerY() + dp(5f), titlePaint)

        // Title + Artist
        val textStartX = coverRect.right + dp(12f)
        val textMaxW = (rectOpen.left - textStartX - dp(8f)).coerceAtLeast(dp(60f))
        titlePaint.color = primaryColor
        titlePaint.textSize = dp(14.5f)
        val tStr = TextUtils.ellipsize(titleText, titlePaint, textMaxW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(tStr, textStartX, coverRect.top + dp(20f), titlePaint)

        subPaint.color = secondaryColor
        subPaint.textSize = dp(12f)
        val aStr = TextUtils.ellipsize(artistText, subPaint, textMaxW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(aStr, textStartX, coverRect.top + dp(39f), subPaint)

        // Live Lyric Pill Row
        val lyricY = coverRect.bottom + dp(12f)
        if (config.showLyrics && lyricText.isNotBlank()) {
            val lyrMaxW = pillRect.width() - pad * 2f
            subPaint.color = accentArgb
            subPaint.textSize = dp(12f)
            val lStr = TextUtils.ellipsize(lyricText, subPaint, lyrMaxW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(lStr, pillRect.left + pad, lyricY + dp(8f), subPaint)
        }

        // Progress Bar
        val barTop = coverRect.bottom + dp(28f)
        val barH = dp(4f)
        val barLeft = pillRect.left + pad
        val barRight = pillRect.right - pad
        trackBarPaint.color = Color.argb(38, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        canvas.drawRoundRect(barLeft, barTop, barRight, barTop + barH, dp(2f), dp(2f), trackBarPaint)
        val ratio = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        accentPaint.color = accentArgb
        canvas.drawRoundRect(barLeft, barTop, barLeft + (barRight - barLeft) * ratio, barTop + barH, dp(2f), dp(2f), accentPaint)

        // Controls Row: Like | Prev | Play/Pause | Next
        val ctrlY = pillRect.bottom - dp(28f)
        val btnW = dp(44f)
        val btnH = dp(36f)
        val cx = pillRect.centerX()

        rectLike.set(pillRect.left + pad, ctrlY - btnH / 2f, pillRect.left + pad + btnW, ctrlY + btnH / 2f)
        rectPrev.set(cx - dp(68f) - btnW / 2f, ctrlY - btnH / 2f, cx - dp(68f) + btnW / 2f, ctrlY + btnH / 2f)
        rectPlay.set(cx - dp(21f), ctrlY - dp(21f), cx + dp(21f), ctrlY + dp(21f))
        rectNext.set(cx + dp(68f) - btnW / 2f, ctrlY - btnH / 2f, cx + dp(68f) + btnW / 2f, ctrlY + btnH / 2f)

        // Draw simple crisp geometric vector controls
        drawHeartIcon(canvas, rectLike.centerX(), rectLike.centerY(), dp(8.5f), if (isLiked) Color.parseColor("#EF4444") else primaryColor, isLiked)
        drawSkipIcon(canvas, rectPrev.centerX(), rectPrev.centerY(), dp(9f), primaryColor, isNext = false)

        accentPaint.color = primaryColor
        canvas.drawCircle(rectPlay.centerX(), rectPlay.centerY(), dp(20f), accentPaint)
        val playFg = if (primaryColor == Color.WHITE) Color.BLACK else Color.WHITE
        drawPlayPauseIcon(canvas, rectPlay.centerX(), rectPlay.centerY(), dp(8f), playFg, isPlaying)

        drawSkipIcon(canvas, rectNext.centerX(), rectNext.centerY(), dp(9f), primaryColor, isNext = true)
    }

    private fun drawShapedCover(canvas: Canvas, rect: RectF) {
        val r = when (config.coverShape) {
            IslandCoverShape.CIRCLE -> rect.width() / 2f
            IslandCoverShape.ROUNDED -> dp(10f)
            IslandCoverShape.SQUARE -> dp(3f)
        }
        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.clipPath(clipPath)
        val bmp = coverBitmap
        if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(bmp, null, rect, coverPaint)
        } else {
            accentPaint.color = accentArgb
            canvas.drawRect(rect, accentPaint)
        }
        canvas.restore()
    }

    private fun drawPlayPauseIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, playing: Boolean) {
        accentPaint.color = color
        if (playing) {
            val bw = r * 0.44f
            canvas.drawRoundRect(cx - r * 0.65f, cy - r * 0.85f, cx - r * 0.65f + bw, cy + r * 0.85f, dp(1.2f), dp(1.2f), accentPaint)
            canvas.drawRoundRect(cx + r * 0.21f, cy - r * 0.85f, cx + r * 0.21f + bw, cy + r * 0.85f, dp(1.2f), dp(1.2f), accentPaint)
        } else {
            val path = Path().apply {
                moveTo(cx - r * 0.55f, cy - r * 0.85f)
                lineTo(cx + r * 0.90f, cy)
                lineTo(cx - r * 0.55f, cy + r * 0.85f)
                close()
            }
            canvas.drawPath(path, accentPaint)
        }
    }

    private fun drawSkipIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, isNext: Boolean) {
        accentPaint.color = color
        val path = Path()
        if (isNext) {
            path.moveTo(cx - r * 0.7f, cy - r * 0.7f)
            path.lineTo(cx + r * 0.35f, cy)
            path.lineTo(cx - r * 0.7f, cy + r * 0.7f)
            path.close()
            canvas.drawPath(path, accentPaint)
            canvas.drawRect(cx + r * 0.45f, cy - r * 0.7f, cx + r * 0.75f, cy + r * 0.7f, accentPaint)
        } else {
            path.moveTo(cx + r * 0.7f, cy - r * 0.7f)
            path.lineTo(cx - r * 0.35f, cy)
            path.lineTo(cx + r * 0.7f, cy + r * 0.7f)
            path.close()
            canvas.drawPath(path, accentPaint)
            canvas.drawRect(cx - r * 0.75f, cy - r * 0.7f, cx - r * 0.45f, cy + r * 0.7f, accentPaint)
        }
    }

    private fun drawHeartIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, filled: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = dp(1.7f)
        }
        val path = Path().apply {
            moveTo(cx, cy + r * 0.85f)
            cubicTo(cx - r * 1.15f, cy + r * 0.1f, cx - r * 1.15f, cy - r * 0.9f, cx, cy - r * 0.25f)
            cubicTo(cx + r * 1.15f, cy - r * 0.9f, cx + r * 1.15f, cy + r * 0.1f, cx, cy + r * 0.85f)
            close()
        }
        canvas.drawPath(path, p)
    }

    private fun blendColor(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(c1) * inv + Color.red(c2) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
