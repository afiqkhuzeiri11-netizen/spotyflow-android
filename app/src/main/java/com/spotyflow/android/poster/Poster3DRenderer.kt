package com.spotyflow.android.poster

import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.spotyflow.android.data.TrackItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class PosterAspectRatio(val label: String, val exportW: Int, val exportH: Int) {
    RATIO_9_16("9:16 TikTok", 1080, 1920),
    RATIO_16_9("16:9 YouTube", 1920, 1080),
    RATIO_4_5("4:5 IG", 1080, 1350),
    RATIO_1_1("1:1 Kotak", 1080, 1080)
}

enum class PosterFormation(val label: String) {
    MIRROR_SHOULDERS("Bahu Kiri-Kanan (Mirror Selfie)"),
    COLLAGE("Kolase"),
    FAN_3D("Kipas 3D"),
    GRID("Grid")
}

enum class PosterCardStyle(val label: String) {
    CRYSTAL_LIGHT("Kaca Terang Kristal"),
    DARK_GLASS("Kaca Gelap"),
    LIQUID_GLASS("Liquid Glass"),
    PURE_BLACK("Hitam Pekat"),
    COVER_COLOR("Warna Sampul"),
    NEON_FRAME("Bingkai Neon")
}

enum class PosterBgPreset(val label: String, val topArgb: Int, val botArgb: Int) {
    PURE_WHITE_PEARL("Pure White Pearl", 0xFFFAFCFF.toInt(), 0xFFE0F2FE.toInt()),
    DEEP_BURGUNDY("Burgundy Gelap", 0xFF4C0519.toInt(), 0xFF1F0612.toInt()),
    AUTO_COVER("Auto Warna Sampul", 0xFF064E3B.toInt(), 0xFF0F172A.toInt()),
    OBSIDIAN("Hitam Obsidian", 0xFF111827.toInt(), 0xFF030712.toInt()),
    MIDNIGHT_BLUE("Biru Malam", 0xFF0C4A6E.toInt(), 0xFF082F49.toInt()),
    VELVET_PURPLE("Ungu Velvet", 0xFF3B0764.toInt(), 0xFF1E1B4B.toInt()),
    CUSTOM("Warna Kustom", 0xFF10B981.toInt(), 0xFF0F172A.toInt())
}

// Upgrade #2: Giant Depth Typography Enums & Model
enum class PosterFontStyle(val label: String) {
    BOLD_SANS("Bold Sans"),
    EDITORIAL_SERIF("Editorial Serif"),
    ITALIC_CONDENSED("Italic Condensed")
}

enum class DepthTextColorMode(val label: String) {
    AUTO_WHITE("Putih"),
    BLACK("Hitam"),
    COVER_COLOR("Warna Sampul")
}

data class DepthTypographyConfig(
    val enabled: Boolean = false,
    val text: String = "",
    val fontStyle: PosterFontStyle = PosterFontStyle.BOLD_SANS,
    val behindSubject: Boolean = true,
    val scale: Float = 1.0f,
    val posX: Float = 0.5f,
    val posY: Float = 0.24f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 0.92f,
    val colorMode: DepthTextColorMode = DepthTextColorMode.AUTO_WHITE,
    val letterSpacing: Float = -0.02f
)

// Upgrade #4: 6 Cinematic Photo Color Filters (including viral B&W Color-Pop)
enum class PhotoColorFilter(val label: String) {
    ORIGINAL("Asli"),
    FILM_ANALOG("Film Analog"),
    MOODY_DARK("Moody Gelap"),
    SOFT_CLEAN("Soft Clean"),
    BW_COLOR_POP("B&W Color-Pop"),
    COOL_MIDNIGHT("Cool Midnight")
}

// Upgrade #6: 5 One-Tap Pro Presets
enum class PosterProPreset(val label: String) {
    MIRROR_SELFIE_3D("Mirror Selfie 3D"),
    COLOR_POP_BW("Color-Pop B&W"),
    MAGAZINE_COVER("Sampul Majalah"),
    COLLAGE_4_SONGS("Kolase 4 Lagu"),
    PURE_WHITE_CRYSTAL("Pure White Crystal")
}

enum class BuiltInStickerKind(val label: String) {
    BIRD("Burung"),
    SPARKLE("Kilau"),
    DOUBLE_SPARKLE("Kilau Ganda"),
    STAR("Bintang"),
    BUTTERFLY("Kupu-kupu"),
    HEART("Hati"),
    CLOUD("Awan"),
    RIBBON("Pita"),
    MUSIC_NOTE("Not Musik")
}

data class PosterCardTransform(
    val trackId: String,
    val centerXFrac: Float, // 0..1
    val centerYFrac: Float, // 0..1
    val rotateYDeg: Float = -18f,
    val rotateXDeg: Float = 6f,
    val rotateZDeg: Float = -8f,
    val scale: Float = 1.0f,
    val inFrontOfSubject: Boolean = false // Upgrade #3: Per-card Front/Back Layering
)

data class PosterStickerItem(
    val id: String,
    val kind: BuiltInStickerKind? = BuiltInStickerKind.SPARKLE,
    val customFilePath: String = "",
    val xFrac: Float = 0.5f,
    val yFrac: Float = 0.25f,
    val scale: Float = 1.0f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 0.94f,
    val behindSubject: Boolean = false
)

data class PosterStudioConfig(
    val aspectRatio: PosterAspectRatio = PosterAspectRatio.RATIO_9_16,
    val depthEnabled: Boolean = true,
    val sensitivity: Float = 0.48f,
    val edgeFeather: Float = 0.45f,
    val bgDimPct: Float = 0.14f,
    val bgBlurPct: Float = 0f,
    val photoZoom: Float = 1.0f,
    val photoPanX: Float = 0f,
    val photoPanY: Float = 0f,
    val photoFilter: PhotoColorFilter = PhotoColorFilter.ORIGINAL, // Upgrade #4
    val rimLightPct: Float = 0.45f, // Upgrade #1 (default 45%)
    val depthText: DepthTypographyConfig = DepthTypographyConfig(), // Upgrade #2
    val cardWaterDrops: Boolean = false, // Upgrade #7
    val bgPreset: PosterBgPreset = PosterBgPreset.PURE_WHITE_PEARL,
    val customBgArgb: Int = 0xFF10B981.toInt(),
    val formation: PosterFormation = PosterFormation.MIRROR_SHOULDERS,
    val cardStyle: PosterCardStyle = PosterCardStyle.LIQUID_GLASS,
    val cardGlowPct: Float = 0.55f,
    val thickness3dPct: Float = 0.65f,
    val cardAlpha: Float = 0.94f,
    val cardCornerPct: Float = 0.58f,
    val showCover: Boolean = true,
    val showTitleArtist: Boolean = true,
    val showControls: Boolean = true,
    val showSeekbar: Boolean = true,
    val lensFlarePct: Float = 0.72f,
    val shadow3dPct: Float = 0.68f,
    val vignettePct: Float = 0.22f,
    val filmGrainPct: Float = 0.08f,
    val globalRotateY: Float = 0f,
    val globalRotateX: Float = 0f,
    val globalRotateZ: Float = 0f,
    val globalCardScale: Float = 1.0f,
    val cards: List<PosterCardTransform> = emptyList(),
    val stickers: List<PosterStickerItem> = emptyList()
)

/**
 * 5-Layer Pro Depth Sandwich Renderer (100% Pixel-Aligned & Cached for 60-120 FPS):
 *   Layer 1: Full original photo (+ PhotoColorFilter + Blur + Dim) OR studio gradient
 *   Layer 2: Giant Depth Typography (if behindSubject) + 3D Music Cards with inFrontOfSubject == false
 *   Layer 3: Full-HD transparent subject cutout (+ IDENTICAL photoMatrix & PhotoColorFilter) + Colored Rim Light (SCREEN)
 *   Layer 3.5: 3D Music Cards with inFrontOfSubject == true + Giant Depth Typography (if !behindSubject)
 *   Layer 4: Front stickers (honoring sticker.behindSubject) + Vignette + Film Grain + SpotyFlow Watermark
 */
object Poster3DRenderer {

    // Small bounded flat-card face cache so 2-finger pinch/rotate/pan never redraws 340x524 faces every frame
    private val flatCardCache = LinkedHashMap<String, Bitmap>(16, 0.75f, true)
    private const val MAX_FLAT_CARD_CACHE = 12

    @Synchronized
    fun clearFlatCardCache() {
        flatCardCache.clear()
    }

    @Synchronized
    private fun getOrBuildFlatCard(
        track: TrackItem,
        coverBmp: Bitmap?,
        config: PosterStudioConfig,
        accentArgb: Int
    ): Bitmap {
        val coverId = if (coverBmp != null && !coverBmp.isRecycled) System.identityHashCode(coverBmp) else 0
        val key = "${track.id}|$coverId|${config.cardStyle.name}|${(config.cardCornerPct * 100).toInt()}|" +
            "${config.showCover}|${config.showTitleArtist}|${config.showSeekbar}|${config.showControls}|" +
            "${config.cardWaterDrops}|$accentArgb"
        val cached = flatCardCache[key]
        if (cached != null && !cached.isRecycled) return cached

        val built = renderFlatMusicCard(340, 524, track, coverBmp, config, accentArgb)
        flatCardCache[key] = built
        if (flatCardCache.size > MAX_FLAT_CARD_CACHE) {
            val eldestKey = flatCardCache.entries.firstOrNull()?.key
            if (eldestKey != null && eldestKey != key) {
                flatCardCache.remove(eldestKey)
            }
        }
        return built
    }

    fun applyProPreset(
        current: PosterStudioConfig,
        preset: PosterProPreset,
        tracks: List<TrackItem>
    ): PosterStudioConfig {
        val leadTitle = tracks.firstOrNull()?.title?.uppercase() ?: "SPOTYFLOW"
        return when (preset) {
            PosterProPreset.MIRROR_SELFIE_3D -> current.copy(
                formation = PosterFormation.MIRROR_SHOULDERS,
                cards = defaultCardsForFormation(tracks, PosterFormation.MIRROR_SHOULDERS).map { it.copy(inFrontOfSubject = false) },
                rimLightPct = 0.55f,
                lensFlarePct = 0.75f,
                bgDimPct = 0.18f,
                vignettePct = 0.20f,
                photoFilter = PhotoColorFilter.ORIGINAL,
                cardWaterDrops = false,
                globalRotateY = 0f,
                globalRotateX = 0f,
                globalRotateZ = 0f,
                depthText = current.depthText.copy(enabled = false)
            )
            PosterProPreset.COLOR_POP_BW -> current.copy(
                photoFilter = PhotoColorFilter.BW_COLOR_POP,
                cards = defaultCardsForFormation(tracks, current.formation).map { it.copy(inFrontOfSubject = false) },
                cardStyle = PosterCardStyle.LIQUID_GLASS,
                rimLightPct = 0.75f,
                cardGlowPct = 0.55f,
                vignettePct = 0.45f,
                lensFlarePct = 0.80f,
                bgDimPct = 0.16f,
                depthText = current.depthText.copy(enabled = false)
            )
            PosterProPreset.MAGAZINE_COVER -> {
                val baseCards = defaultCardsForFormation(tracks, PosterFormation.MIRROR_SHOULDERS).mapIndexed { idx, c ->
                    if (idx == 0) {
                        c.copy(
                            centerXFrac = 0.74f,
                            centerYFrac = 0.76f,
                            rotateYDeg = -14f,
                            rotateXDeg = 4f,
                            rotateZDeg = 4f,
                            scale = 0.76f,
                            inFrontOfSubject = true
                        )
                    } else {
                        c.copy(inFrontOfSubject = false)
                    }
                }
                current.copy(
                    photoFilter = PhotoColorFilter.FILM_ANALOG,
                    rimLightPct = 0.42f,
                    bgDimPct = 0.14f,
                    vignettePct = 0.28f,
                    cardWaterDrops = false,
                    globalRotateY = 0f,
                    globalRotateX = 0f,
                    globalRotateZ = 0f,
                    cards = baseCards,
                    depthText = DepthTypographyConfig(
                        enabled = true,
                        text = leadTitle,
                        fontStyle = PosterFontStyle.EDITORIAL_SERIF,
                        behindSubject = true,
                        scale = 1.12f,
                        posX = 0.5f,
                        posY = 0.22f,
                        rotationDeg = 0f,
                        opacity = 0.94f,
                        colorMode = DepthTextColorMode.AUTO_WHITE
                    )
                )
            }
            PosterProPreset.COLLAGE_4_SONGS -> current.copy(
                formation = PosterFormation.COLLAGE,
                cards = defaultCardsForFormation(tracks, PosterFormation.COLLAGE).map { it.copy(inFrontOfSubject = false) },
                shadow3dPct = 0.75f,
                lensFlarePct = 0.70f,
                globalRotateY = 20f,
                globalRotateX = 0f,
                globalRotateZ = 0f,
                photoFilter = PhotoColorFilter.MOODY_DARK,
                rimLightPct = 0.50f,
                bgDimPct = 0.22f,
                depthText = current.depthText.copy(enabled = false)
            )
            PosterProPreset.PURE_WHITE_CRYSTAL -> current.copy(
                cardStyle = PosterCardStyle.CRYSTAL_LIGHT,
                photoFilter = PhotoColorFilter.SOFT_CLEAN,
                bgPreset = PosterBgPreset.PURE_WHITE_PEARL,
                cardWaterDrops = true,
                rimLightPct = 0.40f,
                bgDimPct = 0.05f,
                vignettePct = 0.10f,
                lensFlarePct = 0.65f,
                globalRotateY = 0f,
                globalRotateX = 0f,
                globalRotateZ = 0f
            )
        }
    }

    /**
     * Upgrade #4: 6 Cinematic Photo Color Filters built with pure Android ColorMatrix.
     * Applied strictly to Layer 1 (Original Photo) & Layer 3 (Subject Cutout) — NEVER to 3D Music Cards or Rim Light!
     */
    fun buildPhotoColorFilter(filter: PhotoColorFilter): ColorMatrixColorFilter? {
        return when (filter) {
            PhotoColorFilter.ORIGINAL -> null
            PhotoColorFilter.FILM_ANALOG -> {
                val cm = ColorMatrix()
                cm.setSaturation(0.90f)
                // Warm red/yellow analog lift + gentle faded shadows
                val warmFade = ColorMatrix(
                    floatArrayOf(
                        1.05f, 0.02f, 0f, 0f, 8f,
                        0f, 1.01f, 0f, 0f, 5f,
                        0f, 0f, 0.93f, 0f, 4f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(warmFade)
                ColorMatrixColorFilter(cm)
            }
            PhotoColorFilter.MOODY_DARK -> {
                val cm = ColorMatrix()
                cm.setSaturation(0.85f)
                val c = 1.18f
                val t = (-0.5f * c + 0.5f) * 255f - 18f
                val darkMood = ColorMatrix(
                    floatArrayOf(
                        c * 0.97f, 0f, 0f, 0f, t,
                        0f, c * 0.99f, 0f, 0f, t,
                        0f, 0f, c * 1.04f, 0f, t + 6f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(darkMood)
                ColorMatrixColorFilter(cm)
            }
            PhotoColorFilter.SOFT_CLEAN -> {
                val cm = ColorMatrix()
                cm.setSaturation(1.04f)
                val c = 0.95f
                val t = (-0.5f * c + 0.5f) * 255f + 12f
                val clean = ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(clean)
                ColorMatrixColorFilter(cm)
            }
            PhotoColorFilter.BW_COLOR_POP -> {
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                val c = 1.22f
                val t = (-0.5f * c + 0.5f) * 255f
                val crispBw = ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(crispBw)
                ColorMatrixColorFilter(cm)
            }
            PhotoColorFilter.COOL_MIDNIGHT -> {
                val cm = ColorMatrix()
                cm.setSaturation(0.88f)
                val c = 1.12f
                val t = (-0.5f * c + 0.5f) * 255f
                val cool = ColorMatrix(
                    floatArrayOf(
                        c * 0.93f, 0f, 0f, 0f, t - 4f,
                        0f, c * 0.99f, 0f, 0f, t + 3f,
                        0f, 0f, c * 1.10f, 0f, t + 14f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(cool)
                ColorMatrixColorFilter(cm)
            }
        }
    }

    fun defaultCardsForFormation(
        tracks: List<TrackItem>,
        formation: PosterFormation
    ): List<PosterCardTransform> {
        if (tracks.isEmpty()) return emptyList()
        return when (formation) {
            PosterFormation.MIRROR_SHOULDERS -> {
                val templates = listOf(
                    Triple(0.24f, 0.34f, floatArrayOf(22f, 6f, -12f, 0.94f)),
                    Triple(0.76f, 0.36f, floatArrayOf(-22f, 6f, 11f, 0.94f)),
                    Triple(0.22f, 0.62f, floatArrayOf(18f, -5f, -7f, 0.84f)),
                    Triple(0.78f, 0.62f, floatArrayOf(-18f, -5f, 8f, 0.84f)),
                    Triple(0.50f, 0.22f, floatArrayOf(0f, 10f, -2f, 0.86f))
                )
                tracks.take(5).mapIndexed { i, t ->
                    val tpl = templates[i % templates.size]
                    PosterCardTransform(
                        trackId = t.id,
                        centerXFrac = tpl.first,
                        centerYFrac = tpl.second,
                        rotateYDeg = tpl.third[0],
                        rotateXDeg = tpl.third[1],
                        rotateZDeg = tpl.third[2],
                        scale = tpl.third[3],
                        inFrontOfSubject = false
                    )
                }
            }
            PosterFormation.COLLAGE -> {
                val coords = listOf(
                    floatArrayOf(0.50f, 0.44f, -14f, 8f, -6f, 1.02f),
                    floatArrayOf(0.26f, 0.30f, 20f, 6f, -14f, 0.84f),
                    floatArrayOf(0.74f, 0.58f, -20f, -6f, 12f, 0.84f),
                    floatArrayOf(0.28f, 0.64f, 16f, -4f, 8f, 0.78f),
                    floatArrayOf(0.74f, 0.28f, -18f, 6f, -10f, 0.78f)
                )
                tracks.take(5).mapIndexed { i, t ->
                    val c = coords[i % coords.size]
                    PosterCardTransform(t.id, c[0], c[1], c[2], c[3], c[4], c[5], inFrontOfSubject = false)
                }
            }
            PosterFormation.FAN_3D -> {
                val n = tracks.take(5).size
                tracks.take(5).mapIndexed { i, t ->
                    val offset = if (n == 1) 0f else (i - (n - 1) / 2f)
                    PosterCardTransform(
                        trackId = t.id,
                        centerXFrac = (0.50f + offset * 0.14f).coerceIn(0.18f, 0.82f),
                        centerYFrac = (0.44f + kotlin.math.abs(offset) * 0.04f).coerceIn(0.22f, 0.76f),
                        rotateYDeg = -offset * 16f,
                        rotateXDeg = 6f,
                        rotateZDeg = offset * 10f,
                        scale = (1.0f - kotlin.math.abs(offset) * 0.06f).coerceAtLeast(0.76f),
                        inFrontOfSubject = false
                    )
                }
            }
            PosterFormation.GRID -> {
                val pts = listOf(
                    0.30f to 0.33f,
                    0.70f to 0.33f,
                    0.30f to 0.65f,
                    0.70f to 0.65f,
                    0.50f to 0.49f
                )
                tracks.take(5).mapIndexed { i, t ->
                    val (x, y) = pts[i % pts.size]
                    PosterCardTransform(t.id, x, y, if (x < 0.5f) 10f else -10f, 4f, 0f, 0.84f, inFrontOfSubject = false)
                }
            }
        }
    }

    /**
     * Single shared centerCrop matrix used identically by Layer 1 (Original Photo),
     * Layer 3 (Subject Cutout), and Colored Rim Light so alignment is 1:1 pixel-exact.
     */
    fun computeCenterCropMatrix(
        photoW: Int,
        photoH: Int,
        canvasW: Int,
        canvasH: Int,
        zoom: Float,
        panXFrac: Float,
        panYFrac: Float
    ): Matrix {
        val baseScale = max(canvasW.toFloat() / photoW.toFloat(), canvasH.toFloat() / photoH.toFloat())
        val finalScale = baseScale * zoom.coerceIn(0.8f, 2.5f)
        val scaledW = photoW * finalScale
        val scaledH = photoH * finalScale
        val dx = (canvasW - scaledW) * 0.5f + panXFrac * canvasW * 0.35f
        val dy = (canvasH - scaledH) * 0.5f + panYFrac * canvasH * 0.35f
        return Matrix().apply {
            setScale(finalScale, finalScale)
            postTranslate(dx, dy)
        }
    }

    fun renderPoster(
        canvasW: Int,
        canvasH: Int,
        config: PosterStudioConfig,
        tracks: List<TrackItem>,
        coverBitmaps: Map<String, Bitmap>,
        customStickerBitmaps: Map<String, Bitmap>,
        uprightFullPhoto: Bitmap?,
        cutoutBmp: Bitmap?,
        rimLightOverlayBmp: Bitmap?,
        dominantAccentArgb: Int
    ): Bitmap {
        val out = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val sharedPhotoMatrix = if (uprightFullPhoto != null && !uprightFullPhoto.isRecycled) {
            computeCenterCropMatrix(
                photoW = uprightFullPhoto.width,
                photoH = uprightFullPhoto.height,
                canvasW = canvasW,
                canvasH = canvasH,
                zoom = config.photoZoom,
                panXFrac = config.photoPanX,
                panYFrac = config.photoPanY
            )
        } else {
            null
        }

        val photoColorFilter = buildPhotoColorFilter(config.photoFilter)

        // =====================================================================
        // LAYER 1: FULL ORIGINAL PHOTO BACKGROUND (+ Color Filter + Blur + Dim)
        // =====================================================================
        if (uprightFullPhoto != null && sharedPhotoMatrix != null) {
            val bgSource = if (config.bgBlurPct > 0.03f) {
                fastSoftBlur(uprightFullPhoto, config.bgBlurPct)
            } else {
                uprightFullPhoto
            }
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = photoColorFilter
            }
            canvas.drawBitmap(bgSource, sharedPhotoMatrix, bgPaint)
            if (bgSource !== uprightFullPhoto) bgSource.recycle()

            if (config.bgDimPct > 0.01f) {
                canvas.drawColor(Color.argb((config.bgDimPct * 180f).toInt().coerceIn(0, 210), 6, 10, 20))
            }
        } else {
            drawStudioBackground(canvas, canvasW, canvasH, config, dominantAccentArgb)
        }

        // Stickers behind subject
        config.stickers.filter { it.behindSubject }.forEach { st ->
            drawSticker(canvas, canvasW, canvasH, st, customStickerBitmaps)
        }

        val cardList = if (config.cards.isNotEmpty()) {
            config.cards
        } else {
            defaultCardsForFormation(tracks, config.formation)
        }
        val trackById = tracks.associateBy { it.id }

        // =====================================================================
        // LAYER 2: GIANT DEPTH TYPOGRAPHY (behindSubject) + BACK 3D MUSIC CARDS
        // =====================================================================
        if (config.depthText.enabled && config.depthText.behindSubject) {
            drawDepthTypography(canvas, canvasW, canvasH, config.depthText, dominantAccentArgb)
        }

        cardList.forEachIndexed { idx, cardTf ->
            if (cardTf.inFrontOfSubject) return@forEachIndexed
            val track = trackById[cardTf.trackId] ?: tracks.getOrNull(idx) ?: return@forEachIndexed
            val coverBmp = coverBitmaps[track.id] ?: coverBitmaps[track.videoId]
            drawProjected3DMusicCard(
                canvas = canvas,
                canvasW = canvasW,
                canvasH = canvasH,
                track = track,
                coverBmp = coverBmp,
                tf = cardTf,
                config = config,
                accentArgb = dominantAccentArgb
            )
        }

        // =====================================================================
        // LAYER 3: SUBJECT CUTOUT (IDENTICAL photoMatrix + ColorFilter) + RIM LIGHT
        // =====================================================================
        if (uprightFullPhoto != null && config.depthEnabled && cutoutBmp != null && !cutoutBmp.isRecycled && sharedPhotoMatrix != null) {
            val subjectPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = photoColorFilter
            }
            canvas.drawBitmap(cutoutBmp, sharedPhotoMatrix, subjectPaint)

            // Upgrade #1: True Colored Inner Rim Light hugging subject hair, cheeks, shoulders & hands
            if (config.rimLightPct > 0.02f && rimLightOverlayBmp != null && !rimLightOverlayBmp.isRecycled) {
                val rimMatrix = Matrix().apply {
                    setScale(
                        uprightFullPhoto.width.toFloat() / rimLightOverlayBmp.width.toFloat(),
                        uprightFullPhoto.height.toFloat() / rimLightOverlayBmp.height.toFloat()
                    )
                    postConcat(sharedPhotoMatrix)
                }
                val layerId = canvas.saveLayer(0f, 0f, canvasW.toFloat(), canvasH.toFloat(), null)
                val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (config.rimLightPct * 255f).toInt().coerceIn(0, 255)
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                }
                canvas.drawBitmap(rimLightOverlayBmp, rimMatrix, rimPaint)
                canvas.restoreToCount(layerId)
            }
        }

        // =====================================================================
        // LAYER 3.5: FRONT 3D MUSIC CARDS (inFrontOfSubject == true) + FRONT TEXT
        // =====================================================================
        cardList.forEachIndexed { idx, cardTf ->
            if (!cardTf.inFrontOfSubject) return@forEachIndexed
            val track = trackById[cardTf.trackId] ?: tracks.getOrNull(idx) ?: return@forEachIndexed
            val coverBmp = coverBitmaps[track.id] ?: coverBitmaps[track.videoId]
            drawProjected3DMusicCard(
                canvas = canvas,
                canvasW = canvasW,
                canvasH = canvasH,
                track = track,
                coverBmp = coverBmp,
                tf = cardTf,
                config = config,
                accentArgb = dominantAccentArgb
            )
        }

        if (config.depthText.enabled && !config.depthText.behindSubject) {
            drawDepthTypography(canvas, canvasW, canvasH, config.depthText, dominantAccentArgb)
        }

        // Vignette & Subtle Film Grain
        if (config.vignettePct > 0.02f) {
            val vig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    canvasW * 0.5f,
                    canvasH * 0.5f,
                    max(canvasW, canvasH) * 0.72f,
                    intArrayOf(Color.TRANSPARENT, Color.argb((config.vignettePct * 185f).toInt().coerceIn(0, 200), 4, 7, 16)),
                    floatArrayOf(0.45f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, canvasW.toFloat(), canvasH.toFloat(), vig)
        }
        if (config.filmGrainPct > 0.02f) {
            drawSubtleFilmGrain(canvas, canvasW, canvasH, config.filmGrainPct)
        }

        // =====================================================================
        // LAYER 4: FRONT STICKERS + SPOTYFLOW WATERMARK
        // =====================================================================
        config.stickers.filter { !it.behindSubject }.forEach { st ->
            drawSticker(canvas, canvasW, canvasH, st, customStickerBitmaps)
        }

        drawWatermark(canvas, canvasW, canvasH, config.bgPreset == PosterBgPreset.PURE_WHITE_PEARL && uprightFullPhoto == null)
        return out
    }

    fun renderCheckerboardCutoutPreview(
        canvasW: Int,
        canvasH: Int,
        uprightFullPhoto: Bitmap,
        cutoutBmp: Bitmap?,
        config: PosterStudioConfig
    ): Bitmap {
        val out = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val tile = max(14, canvasW / 24)
        val pLight = Paint().apply { color = 0xFFE2E8F0.toInt() }
        val pDark = Paint().apply { color = 0xFFCBD5E1.toInt() }
        var y = 0
        while (y < canvasH) {
            var x = 0
            while (x < canvasW) {
                val even = ((x / tile) + (y / tile)) % 2 == 0
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + tile).toFloat(), (y + tile).toFloat(), if (even) pLight else pDark)
                x += tile
            }
            y += tile
        }

        if (cutoutBmp != null && !cutoutBmp.isRecycled) {
            val mat = computeCenterCropMatrix(
                uprightFullPhoto.width,
                uprightFullPhoto.height,
                canvasW,
                canvasH,
                config.photoZoom,
                config.photoPanX,
                config.photoPanY
            )
            canvas.drawBitmap(cutoutBmp, mat, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    /**
     * Upgrade #2: Giant Editorial Depth Typography behind/in front of subject.
     */
    private fun drawDepthTypography(
        canvas: Canvas,
        canvasW: Int,
        canvasH: Int,
        cfg: DepthTypographyConfig,
        dominantAccentArgb: Int
    ) {
        val rawText = cfg.text.trim()
        if (rawText.isBlank()) return

        val tf = when (cfg.fontStyle) {
            PosterFontStyle.BOLD_SANS -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            PosterFontStyle.EDITORIAL_SERIF -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            PosterFontStyle.ITALIC_CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
        }
        val baseColor = when (cfg.colorMode) {
            DepthTextColorMode.AUTO_WHITE -> Color.WHITE
            DepthTextColorMode.BLACK -> 0xFF0F172A.toInt()
            DepthTextColorMode.COVER_COLOR -> blendArgb(dominantAccentArgb, Color.WHITE, 0.22f)
        }
        val alphaInt = (cfg.opacity * 255f).toInt().coerceIn(25, 255)
        val baseSize = min(canvasW, canvasH) * 0.165f * cfg.scale.coerceIn(0.45f, 2.2f)

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = baseSize
            letterSpacing = cfg.letterSpacing
            textAlign = Paint.Align.CENTER
            color = withAlpha(baseColor, alphaInt)
            setShadowLayer(
                baseSize * 0.14f,
                0f,
                baseSize * 0.05f,
                if (cfg.colorMode == DepthTextColorMode.BLACK) 0x55FFFFFF else 0x66040710
            )
        }

        // Split into clean editorial lines (max width ~92% canvas)
        val maxLineW = canvasW * 0.92f
        val lines = wrapEditorialLines(rawText, paint, maxLineW).take(4)
        if (lines.isEmpty()) return

        val lineHeight = baseSize * 1.04f
        val totalHeight = lineHeight * lines.size
        val cx = canvasW * cfg.posX
        val cy = canvasH * cfg.posY

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(cfg.rotationDeg)
        var drawY = -totalHeight * 0.5f + baseSize * 0.82f
        lines.forEach { line ->
            canvas.drawText(line, 0f, drawY, paint)
            drawY += lineHeight
        }
        canvas.restore()
    }

    private fun wrapEditorialLines(text: String, paint: TextPaint, maxW: Float): List<String> {
        val result = mutableListOf<String>()
        text.split("\n").forEach { paragraph ->
            val words = paragraph.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.isEmpty()) return@forEach
            var cur = words.first()
            for (i in 1 until words.size) {
                val cand = "$cur ${words[i]}"
                if (paint.measureText(cand) <= maxW) {
                    cur = cand
                } else {
                    result.add(cur)
                    cur = words[i]
                }
            }
            result.add(cur)
        }
        return result
    }

    private fun drawStudioBackground(
        canvas: Canvas,
        w: Int,
        h: Int,
        config: PosterStudioConfig,
        accentArgb: Int
    ) {
        val top = when (config.bgPreset) {
            PosterBgPreset.AUTO_COVER -> blendArgb(accentArgb, 0xFF0F172A.toInt(), 0.28f)
            PosterBgPreset.CUSTOM -> config.customBgArgb
            else -> config.bgPreset.topArgb
        }
        val bot = when (config.bgPreset) {
            PosterBgPreset.AUTO_COVER -> blendArgb(accentArgb, 0xFF030712.toInt(), 0.72f)
            PosterBgPreset.CUSTOM -> blendArgb(config.customBgArgb, 0xFF0F172A.toInt(), 0.65f)
            else -> config.bgPreset.botArgb
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), top, bot, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        val orb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.22f, h * 0.24f, w * 0.75f,
                withAlpha(accentArgb, 75), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(w * 0.22f, h * 0.24f, w * 0.75f, orb)
    }

    private fun drawProjected3DMusicCard(
        canvas: Canvas,
        canvasW: Int,
        canvasH: Int,
        track: TrackItem,
        coverBmp: Bitmap?,
        tf: PosterCardTransform,
        config: PosterStudioConfig,
        accentArgb: Int
    ) {
        val cardW = 340
        val cardH = 524
        val cardBmp = getOrBuildFlatCard(track, coverBmp, config, accentArgb)

        val scaleFactor = (min(canvasW, canvasH) * 0.42f / cardW.toFloat()) *
            tf.scale * config.globalCardScale.coerceIn(0.6f, 1.5f)
        val cx = canvasW * tf.centerXFrac
        val cy = canvasH * tf.centerYFrac

        val rotX = tf.rotateXDeg + config.globalRotateX
        val rotY = tf.rotateYDeg + config.globalRotateY
        val rotZ = tf.rotateZDeg + config.globalRotateZ

        // 1. Backlight glow behind card
        if (config.cardGlowPct > 0.03f) {
            val glowRadius = cardW * scaleFactor * 0.88f
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, glowRadius,
                    withAlpha(accentArgb, (config.cardGlowPct * 145f).toInt().coerceIn(0, 175)),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, glowRadius, glowPaint)
        }

        fun build3DMatrix(zOffset: Float, shiftX: Float = 0f, shiftY: Float = 0f): Matrix {
            val cam = Camera()
            val m = Matrix()
            cam.save()
            cam.setLocation(0f, 0f, -18f)
            cam.translate(0f, 0f, zOffset)
            cam.rotateX(rotX)
            cam.rotateY(rotY)
            cam.rotateZ(rotZ)
            cam.getMatrix(m)
            cam.restore()
            m.preTranslate(-cardW * 0.5f, -cardH * 0.5f)
            m.postScale(scaleFactor, scaleFactor)
            m.postTranslate(cx + shiftX, cy + shiftY)
            return m
        }

        // 2. Projected 3D Drop Shadow
        if (config.shadow3dPct > 0.03f) {
            val shadowMat = build3DMatrix(26f, shiftX = canvasW * 0.014f, shiftY = canvasH * 0.022f)
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((config.shadow3dPct * 110f).toInt().coerceIn(0, 140), 4, 8, 18)
            }
            canvas.save()
            canvas.concat(shadowMat)
            val r = 28f * (0.4f + config.cardCornerPct * 0.8f)
            canvas.drawRoundRect(RectF(0f, 0f, cardW.toFloat(), cardH.toFloat()), r, r, shadowPaint)
            canvas.restore()
        }

        // 3. 5-Layer 3D Slab Extrusion along Z-back axis
        val extLayers = if (config.thickness3dPct > 0.05f) 5 else 0
        val slabStep = 3.6f * config.thickness3dPct
        val slabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (config.cardStyle) {
                PosterCardStyle.CRYSTAL_LIGHT, PosterCardStyle.LIQUID_GLASS -> 0xCCE2E8F0.toInt()
                PosterCardStyle.NEON_FRAME -> withAlpha(accentArgb, 210)
                else -> 0xDD1E293B.toInt()
            }
        }
        val cardRadius = 30f * (0.35f + config.cardCornerPct * 0.85f)
        for (layer in extLayers downTo 1) {
            val slabMat = build3DMatrix(layer * slabStep)
            canvas.save()
            canvas.concat(slabMat)
            canvas.drawRoundRect(RectF(0f, 0f, cardW.toFloat(), cardH.toFloat()), cardRadius, cardRadius, slabPaint)
            canvas.restore()
        }

        // 4. Front Face 3D Projected Music Card (cached!)
        val frontMat = build3DMatrix(0f)
        val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (config.cardAlpha * 255f).toInt().coerceIn(90, 255)
        }
        canvas.drawBitmap(cardBmp, frontMat, frontPaint)

        // 5. Sharp Optical Lens Flare (4-point star) on top-right projected corner
        if (config.lensFlarePct > 0.05f) {
            val pt = floatArrayOf(cardW * 0.90f, cardH * 0.08f)
            frontMat.mapPoints(pt)
            drawOpticalLensFlare(canvas, pt[0], pt[1], cardW * scaleFactor * 0.24f * config.lensFlarePct)
        }
    }

    private fun renderFlatMusicCard(
        w: Int,
        h: Int,
        track: TrackItem,
        coverBmp: Bitmap?,
        config: PosterStudioConfig,
        accentArgb: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val radius = 30f * (0.35f + config.cardCornerPct * 0.85f)
        val rect = RectF(2f, 2f, w - 2f, h - 2f)

        val isLight = config.cardStyle == PosterCardStyle.CRYSTAL_LIGHT || config.cardStyle == PosterCardStyle.LIQUID_GLASS
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (config.cardStyle) {
            PosterCardStyle.CRYSTAL_LIGHT -> {
                bgPaint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xF5FFFFFF.toInt(), 0xEBF1F5F9.toInt(), Shader.TileMode.CLAMP)
            }
            PosterCardStyle.LIQUID_GLASS -> {
                bgPaint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xEBF8FAFC.toInt(), withAlpha(blendArgb(0xFFFFFFFF.toInt(), accentArgb, 0.14f), 225), Shader.TileMode.CLAMP)
            }
            PosterCardStyle.DARK_GLASS -> {
                bgPaint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xE0181C28.toInt(), 0xEA0B0E17.toInt(), Shader.TileMode.CLAMP)
            }
            PosterCardStyle.PURE_BLACK -> {
                bgPaint.color = 0xFA050608.toInt()
            }
            PosterCardStyle.COVER_COLOR -> {
                bgPaint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), blendArgb(accentArgb, 0xFF0F172A.toInt(), 0.25f), blendArgb(accentArgb, 0xFF05070E.toInt(), 0.65f), Shader.TileMode.CLAMP)
            }
            PosterCardStyle.NEON_FRAME -> {
                bgPaint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xEB0E111B.toInt(), 0xF507090F.toInt(), Shader.TileMode.CLAMP)
            }
        }
        c.drawRoundRect(rect, radius, radius, bgPaint)

        // Diagonal crystal glass sheen
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w * 0.72f, h * 0.55f,
                intArrayOf(0x55FFFFFF, 0x12FFFFFF, Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(rect, radius, radius, sheen)

        val pad = 22f
        val coverSize = w - pad * 2f

        if (config.showCover) {
            val coverRect = RectF(pad, pad, pad + coverSize, pad + coverSize)
            val clipPath = Path().apply { addRoundRect(coverRect, 20f, 20f, Path.Direction.CW) }
            c.save()
            c.clipPath(clipPath)
            if (coverBmp != null && !coverBmp.isRecycled) {
                val cm = computeCenterCropMatrix(coverBmp.width, coverBmp.height, coverSize.toInt(), coverSize.toInt(), 1f, 0f, 0f)
                cm.postTranslate(pad, pad)
                c.drawBitmap(coverBmp, cm, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            } else {
                val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(pad, pad, pad + coverSize, pad + coverSize, accentArgb, 0xFF0F172A.toInt(), Shader.TileMode.CLAMP)
                }
                c.drawRect(coverRect, ph)
            }
            c.restore()
        }

        val textPrimary = if (isLight) 0xFF0F172A.toInt() else Color.WHITE
        val textSecondary = if (isLight) 0xFF475569.toInt() else 0xB8FFFFFF.toInt()
        var cursorY = pad + coverSize + 34f

        if (config.showTitleArtist) {
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimary
                textSize = 23f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val ellipTitle = TextUtils.ellipsize(track.title, titlePaint, w - pad * 2f - 36f, TextUtils.TruncateAt.END).toString()
            c.drawText(ellipTitle, pad, cursorY, titlePaint)

            val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textSecondary
                textSize = 16.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val ellipArtist = TextUtils.ellipsize(track.artist, artistPaint, w - pad * 2f - 36f, TextUtils.TruncateAt.END).toString()
            c.drawText(ellipArtist, pad, cursorY + 24f, artistPaint)

            drawVectorHeart(c, w - pad - 14f, cursorY + 4f, 11f, accentArgb)
            cursorY += 46f
        }

        if (config.showSeekbar) {
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isLight) 0x240F172A else 0x33FFFFFF
            }
            val barRect = RectF(pad, cursorY, w - pad, cursorY + 5f)
            c.drawRoundRect(barRect, 3f, 3f, barPaint)

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isLight) 0xFF0F172A.toInt() else Color.WHITE
            }
            c.drawRoundRect(RectF(pad, cursorY, pad + (w - pad * 2f) * 0.42f, cursorY + 5f), 3f, 3f, fillPaint)
            cursorY += 28f
        }

        if (config.showControls) {
            val ctrlY = min(cursorY + 26f, h - 42f)
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimary
                style = Paint.Style.FILL
            }
            val midX = w * 0.5f
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textPrimary }
            c.drawCircle(midX, ctrlY, 22f, pillPaint)
            val barInside = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isLight) Color.WHITE else 0xFF0F172A.toInt()
            }
            c.drawRoundRect(RectF(midX - 7f, ctrlY - 9f, midX - 2f, ctrlY + 9f), 2f, 2f, barInside)
            c.drawRoundRect(RectF(midX + 2f, ctrlY - 9f, midX + 7f, ctrlY + 9f), 2f, 2f, barInside)

            val prevX = midX - 66f
            val prevPath = Path().apply {
                moveTo(prevX + 8f, ctrlY - 9f)
                lineTo(prevX - 6f, ctrlY)
                lineTo(prevX + 8f, ctrlY + 9f)
                close()
            }
            c.drawPath(prevPath, iconPaint)
            c.drawRect(prevX - 10f, ctrlY - 9f, prevX - 7f, ctrlY + 9f, iconPaint)

            val nextX = midX + 66f
            val nextPath = Path().apply {
                moveTo(nextX - 8f, ctrlY - 9f)
                lineTo(nextX + 6f, ctrlY)
                lineTo(nextX - 8f, ctrlY + 9f)
                close()
            }
            c.drawPath(nextPath, iconPaint)
            c.drawRect(nextX + 7f, ctrlY - 9f, nextX + 10f, ctrlY + 9f, iconPaint)
        }

        // Upgrade #7: Signature SpotyFlow 4-Layer Optical Glass Water Drops on card edges/corners
        // Drawn BEFORE 3D Camera projection so droplets tilt naturally in 3D space!
        if (config.cardWaterDrops) {
            drawCardOpticalWaterDrops(c, w.toFloat(), h.toFloat())
        }

        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (config.cardStyle == PosterCardStyle.NEON_FRAME) 3.6f else 2.2f
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                if (config.cardStyle == PosterCardStyle.NEON_FRAME) accentArgb else 0xF0FFFFFF.toInt(),
                if (config.cardStyle == PosterCardStyle.NEON_FRAME) 0xFF38BDF8.toInt() else 0x55CBD5E1,
                Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(rect, radius, radius, rimPaint)

        return bmp
    }

    /**
     * Upgrade #7: 6 Organic 4-Layer Optical Water Droplets along card glass edges/corners.
     */
    private fun drawCardOpticalWaterDrops(c: Canvas, w: Float, h: Float) {
        // (xFrac, yFrac, rx, ry) carefully placed around margins/corners without obscuring title text
        val drops = arrayOf(
            floatArrayOf(0.085f, 0.065f, 11.5f, 9.8f),
            floatArrayOf(0.145f, 0.112f, 6.2f, 5.8f),
            floatArrayOf(0.915f, 0.088f, 13.5f, 11.2f),
            floatArrayOf(0.892f, 0.625f, 9.5f, 8.2f),
            floatArrayOf(0.075f, 0.875f, 10.8f, 9.2f),
            floatArrayOf(0.910f, 0.905f, 12.2f, 10.4f)
        )
        for (d in drops) {
            drawSingleOpticalWaterDrop(c, w * d[0], h * d[1], d[2], d[3])
        }
    }

    private fun drawSingleOpticalWaterDrop(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        val oval = RectF(cx - rx, cy - ry, cx + rx, cy + ry)

        // Layer 1a: Soft curved shadow underneath lower-right edge
        val shOval = RectF(oval.left + rx * 0.24f, oval.top + ry * 0.30f, oval.right + rx * 0.24f, oval.bottom + ry * 0.30f)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx + rx * 0.24f, cy + ry * 0.30f, max(rx, ry) * 1.35f,
                intArrayOf(0x280F172A, 0x0A0F172A, Color.TRANSPARENT),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawOval(shOval, shadowPaint)

        // Layer 1b: Focused Caustic Light Dot (refracted light inside lower-right dome)
        val causX = cx + rx * 0.34f
        val causY = cy + ry * 0.38f
        val causR = max(rx, ry) * 0.56f
        val causticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                causX, causY, causR,
                intArrayOf(0xEBFFFFFF.toInt(), 0x78E0F2FE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(causX, causY, causR, causticPaint)

        // Layer 2: Translucent 3D Refractive Dome Body
        val domePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                oval.left, oval.top, oval.right, oval.bottom,
                intArrayOf(0x1C0F172A, 0x26FFFFFF, 0x62FFFFFF),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawOval(oval, domePaint)

        // Layer 3: Pure-White Crisp Primary Specular Glint (upper-left) + Tiny Secondary Sparkle
        val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF5FFFFFF.toInt() }
        c.drawCircle(cx - rx * 0.34f, cy - ry * 0.34f, (min(rx, ry) * 0.30f).coerceAtLeast(1.8f), glintPaint)
        val secGlintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD0FFFFFF.toInt() }
        c.drawCircle(cx - rx * 0.08f, cy - ry * 0.48f, (min(rx, ry) * 0.14f).coerceAtLeast(1.0f), secGlintPaint)

        // Layer 4: Thin Crisp Fresnel Rim Border
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
            shader = LinearGradient(
                oval.left, oval.top, oval.right, oval.bottom,
                intArrayOf(0xE0FFFFFF.toInt(), 0x70BAE6FD, 0x200F172A, 0xC8FFFFFF.toInt()),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawOval(oval, rimPaint)
    }

    private fun drawOpticalLensFlare(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        if (radius <= 2f) return
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(0xF5FFFFFF.toInt(), 0x88E0F2FE.toInt(), Color.TRANSPARENT),
                floatArrayOf(0f, 0.28f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, radius, core)

        val star = Path().apply {
            moveTo(cx, cy - radius * 0.92f)
            quadTo(cx, cy, cx + radius * 0.92f, cy)
            quadTo(cx, cy, cx, cy + radius * 0.92f)
            quadTo(cx, cy, cx - radius * 0.92f, cy)
            quadTo(cx, cy, cx, cy - radius * 0.92f)
            close()
        }
        canvas.drawPath(star, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEEFFFFFF.toInt() })
    }

    private fun drawSticker(
        canvas: Canvas,
        canvasW: Int,
        canvasH: Int,
        st: PosterStickerItem,
        customBitmaps: Map<String, Bitmap>
    ) {
        val cx = canvasW * st.xFrac
        val cy = canvasH * st.yFrac
        val baseSize = min(canvasW, canvasH) * 0.14f * st.scale.coerceIn(0.4f, 2.4f)

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(st.rotationDeg)

        val alphaInt = (st.opacity * 255f).toInt().coerceIn(20, 255)
        if (st.customFilePath.isNotBlank()) {
            val bmp = customBitmaps[st.customFilePath]
            if (bmp != null && !bmp.isRecycled) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = alphaInt }
                val half = baseSize * 0.65f
                canvas.drawBitmap(bmp, null, RectF(-half, -half, half, half), p)
            }
            canvas.restore()
            return
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = alphaInt
            style = Paint.Style.FILL
        }
        val r = baseSize * 0.5f

        when (st.kind ?: BuiltInStickerKind.SPARKLE) {
            BuiltInStickerKind.SPARKLE -> drawFourPointStar(canvas, 0f, 0f, r, paint)
            BuiltInStickerKind.DOUBLE_SPARKLE -> {
                drawFourPointStar(canvas, -r * 0.25f, -r * 0.15f, r * 0.78f, paint)
                drawFourPointStar(canvas, r * 0.45f, r * 0.35f, r * 0.45f, paint)
            }
            BuiltInStickerKind.STAR -> {
                val p = Path()
                for (i in 0 until 10) {
                    val rad = if (i % 2 == 0) r else r * 0.44f
                    val ang = (-90.0 + i * 36.0) * PI / 180.0
                    val x = (cos(ang) * rad).toFloat()
                    val y = (sin(ang) * rad).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                canvas.drawPath(p, paint)
            }
            BuiltInStickerKind.HEART -> drawVectorHeart(canvas, 0f, 0f, r * 0.85f, withAlpha(0xFFFB7185.toInt(), alphaInt))
            BuiltInStickerKind.BIRD -> {
                val stroke = Paint(paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = r * 0.16f
                    strokeCap = Paint.Cap.ROUND
                }
                val p = Path().apply {
                    moveTo(-r, r * 0.1f)
                    quadTo(-r * 0.45f, -r * 0.65f, 0f, r * 0.05f)
                    quadTo(r * 0.45f, -r * 0.65f, r, r * 0.1f)
                }
                canvas.drawPath(p, stroke)
            }
            BuiltInStickerKind.BUTTERFLY -> {
                canvas.drawOval(RectF(-r, -r * 0.7f, -r * 0.08f, r * 0.15f), paint)
                canvas.drawOval(RectF(r * 0.08f, -r * 0.7f, r, r * 0.15f), paint)
                canvas.drawOval(RectF(-r * 0.72f, 0f, -r * 0.08f, r * 0.72f), paint)
                canvas.drawOval(RectF(r * 0.08f, 0f, r * 0.72f, r * 0.72f), paint)
            }
            BuiltInStickerKind.CLOUD -> {
                canvas.drawCircle(-r * 0.42f, r * 0.1f, r * 0.42f, paint)
                canvas.drawCircle(0f, -r * 0.12f, r * 0.52f, paint)
                canvas.drawCircle(r * 0.46f, r * 0.12f, r * 0.38f, paint)
                canvas.drawRoundRect(RectF(-r * 0.82f, 0f, r * 0.82f, r * 0.52f), r * 0.25f, r * 0.25f, paint)
            }
            BuiltInStickerKind.RIBBON -> {
                val p = Path().apply {
                    moveTo(-r, -r * 0.45f)
                    lineTo(0f, 0f)
                    lineTo(-r, r * 0.45f)
                    close()
                    moveTo(r, -r * 0.45f)
                    lineTo(0f, 0f)
                    lineTo(r, r * 0.45f)
                    close()
                }
                canvas.drawPath(p, paint)
                canvas.drawCircle(0f, 0f, r * 0.22f, paint)
            }
            BuiltInStickerKind.MUSIC_NOTE -> {
                canvas.drawOval(RectF(-r * 0.65f, r * 0.2f, -r * 0.05f, r * 0.72f), paint)
                canvas.drawOval(RectF(r * 0.18f, r * 0.05f, r * 0.78f, r * 0.57f), paint)
                val stem = Paint(paint).apply { strokeWidth = r * 0.14f; style = Paint.Style.STROKE }
                canvas.drawLine(-r * 0.1f, r * 0.42f, -r * 0.1f, -r * 0.65f, stem)
                canvas.drawLine(r * 0.72f, r * 0.28f, r * 0.72f, -r * 0.8f, stem)
                canvas.drawLine(-r * 0.14f, -r * 0.65f, r * 0.76f, -r * 0.8f, stem)
            }
        }
        canvas.restore()
    }

    private fun drawFourPointStar(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val p = Path().apply {
            moveTo(cx, cy - r)
            quadTo(cx, cy, cx + r, cy)
            quadTo(cx, cy, cx, cy + r)
            quadTo(cx, cy, cx - r, cy)
            quadTo(cx, cy, cx, cy - r)
            close()
        }
        canvas.drawPath(p, paint)
    }

    private fun drawVectorHeart(canvas: Canvas, cx: Float, cy: Float, r: Float, colorArgb: Int) {
        val p = Path().apply {
            moveTo(cx, cy + r * 0.82f)
            cubicTo(cx - r * 1.15f, cy + r * 0.1f, cx - r * 1.05f, cy - r * 0.82f, cx, cy - r * 0.28f)
            cubicTo(cx + r * 1.05f, cy - r * 0.82f, cx + r * 1.15f, cy + r * 0.1f, cx, cy + r * 0.82f)
            close()
        }
        canvas.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb })
    }

    private fun drawSubtleFilmGrain(canvas: Canvas, w: Int, h: Int, pct: Float) {
        val paint = Paint().apply {
            color = Color.argb((pct * 48f).toInt().coerceIn(0, 60), 255, 255, 255)
            strokeWidth = 1.6f
        }
        var seed = 1337
        val count = ((w * h) / 2600).coerceIn(120, 900)
        for (i in 0 until count) {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            val x = (seed % w).toFloat()
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            val y = (seed % h).toFloat()
            canvas.drawPoint(x, y, paint)
        }
    }

    private fun drawWatermark(canvas: Canvas, w: Int, h: Int, darkInk: Boolean) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (darkInk) 0x660F172A else 0x88FFFFFF.toInt()
            textSize = max(14f, min(w, h) * 0.022f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.16f
        }
        val txt = "SPOTYFLOW"
        val tw = p.measureText(txt)
        canvas.drawText(txt, (w - tw) * 0.5f, h - max(24f, h * 0.028f), p)
    }

    private fun fastSoftBlur(src: Bitmap, blurPct: Float): Bitmap {
        val factor = (1f / (2f + blurPct * 14f)).coerceIn(0.05f, 0.5f)
        val sw = (src.width * factor).toInt().coerceAtLeast(16)
        val sh = (src.height * factor).toInt().coerceAtLeast(16)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val back = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        small.recycle()
        return back
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
