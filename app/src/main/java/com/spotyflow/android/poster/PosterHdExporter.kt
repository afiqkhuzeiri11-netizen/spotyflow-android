package com.spotyflow.android.poster

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import com.spotyflow.android.data.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renderer Ekspor Resolusi Tinggi True 1080x1920 px (9:16) Anti-Pecah untuk Poster Lagu Depth Effect v2.5.0.
 *
 * ATURAN MUTLAK 4 LAYER (100% IDENTIK DENGAN COMPOSE LIVE PREVIEW):
 * - Layer 1: Foto Asli Pengguna (photoMatrix 1:1 + Kabur/Gelap Latar)
 * - Layer 2: SEMUA Sampul Album (1-5 lagu) + Glow Adaptif + Teks Besar Belakang Objek
 * - Layer 3: Potongan Objek Transparan (photoMatrix 1:1 IDENTIK dengan Layer 1 — menutup sampul secara rapi!)
 * - Layer 4: Gradasi Bawah + Kartu Mini-Player "Liquid Glass" Blur Asli (0 Thumbnail Sampul di Depan Objek!)
 */
object PosterHdExporter {
    const val EXPORT_WIDTH = 1080
    const val EXPORT_HEIGHT = 1920

    /**
     * Menghitung SATU Matrix tunggal yang 100% identik dengan Compose ContentScale.Crop + graphicsLayer.
     * Dipakai persis sama untuk Layer 1 (uprightPhoto) dan Layer 3 (foregroundCutoutBitmap) -> 0% pergeseran!
     */
    fun computeCropPanZoomMatrix(
        srcW: Int,
        srcH: Int,
        dstW: Float,
        dstH: Float,
        photoScale: Float,
        panXFrac: Float,
        panYFrac: Float
    ): Matrix {
        val safeW = srcW.coerceAtLeast(1).toFloat()
        val safeH = srcH.coerceAtLeast(1).toFloat()
        val baseScale = max(dstW / safeW, dstH / safeH)
        val totalScale = baseScale * photoScale.coerceIn(1.0f, 3.0f)
        val drawW = safeW * totalScale
        val drawH = safeH * totalScale
        val tx = (dstW - drawW) * 0.5f + panXFrac * dstW
        val ty = (dstH - drawH) * 0.5f + panYFrac * dstH
        return Matrix().apply {
            setScale(totalScale, totalScale)
            postTranslate(tx, ty)
        }
    }

    suspend fun renderFullHdBitmap(
        state: DepthPosterState,
        uprightPhoto: Bitmap?,
        foregroundCutout: Bitmap?,
        coverBitmaps: Map<String, Bitmap>
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = EXPORT_WIDTH
        val h = EXPORT_HEIGHT
        val wf = w.toFloat()
        val hf = h.toFloat()

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // SATU Matrix foto tunggal untuk Layer 1 & Layer 3
        val photoMatrix = if (uprightPhoto != null) {
            computeCropPanZoomMatrix(
                srcW = uprightPhoto.width,
                srcH = uprightPhoto.height,
                dstW = wf,
                dstH = hf,
                photoScale = state.photoScale,
                panXFrac = state.photoPanXFrac,
                panYFrac = state.photoPanYFrac
            )
        } else {
            null
        }

        // =====================================================================
        // LAYER 1: FOTO ASLI PENGGUNA (+ KABUR & GELAP LATAR) / GRADIENT ADAPTIF
        // =====================================================================
        if (uprightPhoto != null && photoMatrix != null) {
            val bgSource = if (state.bgBlur > 0.02f) {
                PosterSubjectSegmenter.buildBlurredBackdrop(uprightPhoto, state.bgBlur)
            } else {
                uprightPhoto
            }
            canvas.drawBitmap(bgSource, photoMatrix, bmpPaint)
            if (bgSource !== uprightPhoto) bgSource.recycle()

            if (state.bgDim > 0.01f) {
                val dimAlpha = (state.bgDim.coerceIn(0f, 0.70f) * 255f).roundToInt()
                canvas.drawColor(Color.argb(dimAlpha, 6, 10, 18))
            }
        } else {
            val bgGrad = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, wf, hf,
                    intArrayOf(
                        blendColor(state.palette.darkMuted, state.palette.glowColor, 0.35f),
                        state.palette.darkMuted,
                        0xFF090C14.toInt()
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, wf, hf, bgGrad)
        }

        // =====================================================================
        // LAYER 2: TEKS BESAR + SEMUA SAMPUL ALBUM (100% DI BELAKANG OBJEK!)
        // =====================================================================
        val leadTrack = state.selectedTracks.firstOrNull()
        val slots = computeLayer2CoverSlots(state)

        // 2A. Teks Besar di Belakang Objek (opsional, terpotong rapi oleh Layer 3!)
        if (state.bigTextEnabled) {
            val rawText = state.bigTextContent.ifBlank { leadTrack?.title.orEmpty() }.trim()
            if (rawText.isNotBlank()) {
                drawBigDepthTypography(
                    canvas = canvas,
                    text = rawText,
                    w = wf,
                    h = hf,
                    anchorYFrac = (state.coverOffsetYFrac - 0.16f).coerceIn(0.08f, 0.34f)
                )
            }
        }

        // 2B. SEMUA Sampul Album (1-5 lagu) + Glow Adaptif + Sudut Membulat 48px + Drop Shadow
        for (slot in slots) {
            val track = state.selectedTracks.getOrNull(slot.trackIndex) ?: continue
            val coverBmp = coverBitmaps[track.id] ?: coverBitmaps[track.coverUrl]
            drawLayer2AlbumCover(
                canvas = canvas,
                coverBmp = coverBmp,
                slot = slot,
                canvasW = wf,
                canvasH = hf,
                glowColor = state.palette.glowColor,
                glowEnabled = state.coverGlowEnabled
            )
        }

        // =====================================================================
        // LAYER 3: POTONGAN OBJEK TRANSPARAN 1:1 (DI DEPAN SEMUA SAMPUL!)
        // =====================================================================
        if (state.depthEnabled && uprightPhoto != null && foregroundCutout != null && photoMatrix != null) {
            // Gunakan Matrix IDENTIK 100% dengan Layer 1 -> 0 piksel geser!
            canvas.drawBitmap(foregroundCutout, photoMatrix, bmpPaint)
        }

        // =====================================================================
        // LAYER 4: GRADASI BAWAH + KARTU LIQUID GLASS (0 THUMBNAIL SAMPUL DEPAN!)
        // =====================================================================
        val gradTop = hf * 0.62f
        val bottomGradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, gradTop, 0f, hf,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(58, 0, 0, 0),
                    Color.argb(188, 0, 0, 0)
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, gradTop, wf, hf, bottomGradPaint)

        if (state.showBottomGlassCard && leadTrack != null) {
            drawLayer4LiquidGlassCard(
                canvas = canvas,
                backingBitmap = out,
                state = state,
                title = leadTrack.title,
                artist = leadTrack.artist,
                durationSec = leadTrack.durationSec.takeIf { it > 0 } ?: 214
            )
        }

        if (state.showWatermark) {
            val wmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(115, 255, 255, 255)
                textSize = 27f
                letterSpacing = 0.14f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("SPOTYFLOW", wf * 0.5f, hf - 26f, wmPaint)
        }

        out
    }

    private fun drawBigDepthTypography(
        canvas: Canvas,
        text: String,
        w: Float,
        h: Float,
        anchorYFrac: Float
    ) {
        val display = text.uppercase()
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(218, 255, 255, 255)
            textSize = if (display.length > 16) 94f else 118f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = -0.02f
            textAlign = Paint.Align.CENTER
            setShadowLayer(26f, 0f, 10f, Color.argb(110, 0, 0, 0))
        }

        val maxLineW = w * 0.88f
        val lines = wrapLines(display, tp, maxLineW, maxLines = 2)
        val lineH = tp.textSize * 1.06f
        val startY = h * anchorYFrac + tp.textSize * 0.82f

        lines.forEachIndexed { idx, line ->
            canvas.drawText(line, w * 0.5f, startY + idx * lineH, tp)
        }
    }

    private fun drawLayer2AlbumCover(
        canvas: Canvas,
        coverBmp: Bitmap?,
        slot: CoverSlotTransform,
        canvasW: Float,
        canvasH: Float,
        glowColor: Int,
        glowEnabled: Boolean
    ) {
        val side = canvasW * slot.widthFrac
        val cx = canvasW * slot.centerXFrac
        val cy = canvasH * slot.centerYFrac
        val half = side * 0.5f
        val corner = 48f * (side / 620f).coerceIn(0.65f, 1.15f)

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(slot.rotationDeg)

        // 1. Glow adaptif lembut di belakang sampul
        if (glowEnabled) {
            val glowR = side * 0.82f
            val r = Color.red(glowColor)
            val g = Color.green(glowColor)
            val b = Color.blue(glowColor)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    0f, 0f, glowR,
                    intArrayOf(
                        Color.argb((125 * slot.alpha).roundToInt(), r, g, b),
                        Color.argb((42 * slot.alpha).roundToInt(), r, g, b),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(0f, 0f, glowR, glowPaint)
        }

        val rect = RectF(-half, -half, half, half)

        // 2. Drop shadow lembut di belakang kotak sampul
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((95 * slot.alpha).roundToInt(), 0, 0, 0)
            setShadowLayer(44f, 0f, 22f, Color.argb((130 * slot.alpha).roundToInt(), 0, 0, 0))
        }
        canvas.drawRoundRect(rect, corner, corner, shadowPaint)

        // 3. Clip sudut membulat 16dp (48px) & gambar sampul album HD center-crop
        val clipPath = Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clipPath)
        if (coverBmp != null) {
            val srcCrop = centerCropRect(coverBmp.width, coverBmp.height)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (255f * slot.alpha).roundToInt()
            }
            canvas.drawBitmap(coverBmp, srcCrop, rect, p)
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    -half, -half, half, half,
                    glowColor,
                    0xFF0F172A.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(rect, fallbackPaint)
        }
        canvas.restore()

        // 4. Specular hairline rim tipis agar sampul terasa tajam & premium
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.argb((90 * slot.alpha).roundToInt(), 255, 255, 255)
        }
        canvas.drawRoundRect(rect, corner, corner, rimPaint)

        canvas.restore()
    }

    /**
     * LAYER 4: Kartu Mini-Player "Liquid Glass" Blur Asli — 0 THUMBNAIL SAMPUL DI DEPAN OBJEK!
     * Hanya Judul Lagu, Nama Artis, Progress Bar adaptif + waktu, dan tombol lingkaran Play vektor.
     */
    private fun drawLayer4LiquidGlassCard(
        canvas: Canvas,
        backingBitmap: Bitmap,
        state: DepthPosterState,
        title: String,
        artist: String,
        durationSec: Int
    ) {
        val wf = backingBitmap.width.toFloat()
        val hf = backingBitmap.height.toFloat()
        val marginH = 56f
        val bottomMargin = if (state.showWatermark) 82f else 56f
        val cardH = if (state.showProgressBar) 276f else 196f
        val cardRect = RectF(marginH, hf - bottomMargin - cardH, wf - marginH, hf - bottomMargin)
        val corner = 66f

        // 1. Real 2-Pass Backdrop Blur dari hasil komposit Layer 1-3 tepat di belakang kartu!
        val cropL = cardRect.left.roundToInt().coerceIn(0, backingBitmap.width - 1)
        val cropT = cardRect.top.roundToInt().coerceIn(0, backingBitmap.height - 1)
        val cropW = (cardRect.width().roundToInt()).coerceIn(1, backingBitmap.width - cropL)
        val cropH = (cardRect.height().roundToInt()).coerceIn(1, backingBitmap.height - cropT)

        val clipPath = Path().apply { addRoundRect(cardRect, corner, corner, Path.Direction.CW) }

        val cropped = Bitmap.createBitmap(backingBitmap, cropL, cropT, cropW, cropH)
        val blurred = PosterSubjectSegmenter.buildBlurredBackdrop(cropped, 0.72f)
        cropped.recycle()

        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(blurred, null, cardRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        blurred.recycle()

        // 2. Tint Kaca Liquid Glass sesuai pilihan user
        val tintColor = when (state.glassCardTheme) {
            GlassCardTheme.LIGHT_GLASS -> Color.argb(72, 255, 255, 255)
            GlassCardTheme.DARK_GLASS -> Color.argb(115, 10, 14, 24)
            GlassCardTheme.ADAPTIVE_COVER -> {
                val c = state.palette.darkMuted
                Color.argb(128, Color.red(c), Color.green(c), Color.blue(c))
            }
        }
        canvas.drawRect(cardRect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tintColor })

        // Top specular glass highlight
        val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardRect.left, cardRect.top, cardRect.left, cardRect.top + cardH * 0.42f,
                Color.argb(58, 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(cardRect, specPaint)
        canvas.restore()

        // 3. Border putih tipis 3px (1dp di skala 1080px)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.2f
            color = Color.argb(102, 255, 255, 255)
        }
        canvas.drawRoundRect(cardRect, corner, corner, borderPaint)

        // 4. Konten Teks & Play Vektor (TANPA THUMBNAIL SAMPUL!)
        val padH = 54f
        val padTop = 48f
        val playR = 46f
        val playCx = cardRect.right - padH - playR
        val playCy = cardRect.top + padTop + 50f
        val textMaxW = (playCx - playR - 36f) - (cardRect.left + padH)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(8f, 0f, 3f, Color.argb(65, 0, 0, 0))
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(198, 255, 255, 255)
            textSize = 35f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val ellipTitle = TextUtils.ellipsize(title, titlePaint, textMaxW, TextUtils.TruncateAt.END).toString()
        val ellipArtist = TextUtils.ellipsize(artist, artistPaint, textMaxW, TextUtils.TruncateAt.END).toString()

        canvas.drawText(ellipTitle, cardRect.left + padH, cardRect.top + padTop + 42f, titlePaint)
        canvas.drawText(ellipArtist, cardRect.left + padH, cardRect.top + padTop + 94f, artistPaint)

        // Tombol lingkaran Play vektor bersih di kanan
        val playCircleBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(16f, 0f, 6f, Color.argb(75, 0, 0, 0))
        }
        canvas.drawCircle(playCx, playCy, playR, playCircleBg)

        val triPath = Path().apply {
            val s = playR * 0.42f
            moveTo(playCx - s * 0.55f, playCy - s)
            lineTo(playCx + s * 1.15f, playCy)
            lineTo(playCx - s * 0.55f, playCy + s)
            close()
        }
        canvas.drawPath(triPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() })

        // 5. Progress Bar ramping berwarna accentColor + angka waktu tipis
        if (state.showProgressBar) {
            val barLeft = cardRect.left + padH
            val barRight = cardRect.right - padH
            val barY = cardRect.bottom - 76f
            val barH = 11f
            val frac = state.progressFraction.coerceIn(0.05f, 0.95f)

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(66, 255, 255, 255)
            }
            canvas.drawRoundRect(RectF(barLeft, barY, barRight, barY + barH), barH * 0.5f, barH * 0.5f, trackPaint)

            val fillRight = barLeft + (barRight - barLeft) * frac
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = state.palette.accentColor
            }
            canvas.drawRoundRect(RectF(barLeft, barY, fillRight, barY + barH), barH * 0.5f, barH * 0.5f, fillPaint)

            // Small glowing thumb dot
            canvas.drawCircle(fillRight, barY + barH * 0.5f, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

            val elapsedSec = (durationSec * frac).roundToInt().coerceIn(0, durationSec)
            val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(175, 255, 255, 255)
                textSize = 27f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText(formatDuration(elapsedSec), barLeft, cardRect.bottom - 30f, timePaint)
            timePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatDuration(durationSec), barRight, cardRect.bottom - 30f, timePaint)
        }
    }

    // =========================================================================
    // SIMPAN KE GALERI (Pictures/SpotyFlow) & BAGIKAN
    // =========================================================================
    suspend fun savePosterToGallery(context: Context, bitmap: Bitmap): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val fileName = "SpotyFlow_Poster_${System.currentTimeMillis()}.png"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SpotyFlow")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null

                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                } ?: return@withContext null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri
            } catch (_: Throwable) {
                null
            }
        }

    fun shareExportedPoster(context: Context, uri: Uri, title: String = "Poster Lagu SpotyFlow") {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "$title — dibuat di SpotyFlow")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Bagikan Poster Lagu"))
        } catch (_: Throwable) {
            // Non-fatal
        }
    }

    private fun centerCropRect(w: Int, h: Int): Rect {
        val side = min(w, h)
        val left = (w - side) / 2
        val top = (h - side) / 2
        return Rect(left, top, left + side, top + side)
    }

    private fun blendColor(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(c1) * inv + Color.red(c2) * ratio).roundToInt().coerceIn(0, 255)
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).roundToInt().coerceIn(0, 255)
        val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).roundToInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun wrapLines(text: String, paint: TextPaint, maxW: Float, maxLines: Int): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val cand = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(cand) <= maxW || current.isEmpty()) {
                current = cand
            } else {
                lines.add(current)
                current = word
                if (lines.size >= maxLines - 1) break
            }
        }
        if (current.isNotBlank() && lines.size < maxLines) {
            lines.add(TextUtils.ellipsize(current, paint, maxW, TextUtils.TruncateAt.END).toString())
        }
        return lines
    }
}
