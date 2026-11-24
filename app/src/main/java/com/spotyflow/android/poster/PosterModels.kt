package com.spotyflow.android.poster

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import com.spotyflow.android.data.TrackItem

/**
 * Gaya kartu Liquid Glass minimalis di Layer 4 (TANPA thumbnail sampul depan!).
 */
enum class GlassCardTheme(val label: String) {
    LIGHT_GLASS("Kaca Terang"),
    DARK_GLASS("Kaca Gelap"),
    ADAPTIVE_COVER("Kaca Warna Cover")
}

/**
 * 3 Tab kontrol ringkas di Poster Lagu Depth Effect v2.5.0.
 */
enum class PosterEditorTab(val label: String) {
    PHOTO("Foto"),
    COVER_TEXT("Sampul & Teks"),
    GLASS_STYLE("Kaca & Gaya")
}

/**
 * Hasil segmentasi subjek 1:1 dari PosterSubjectSegmenter.
 */
data class SegmentationResult(
    val cutoutBitmap: Bitmap,
    val subjectBounds: RectF,
    val engineLabel: String
)

/**
 * Warna adaptif yang diekstrak dari sampul utama via AndroidX Palette API.
 */
data class PosterCoverPalette(
    val glowColor: Int = 0xFF10B981.toInt(),
    val accentColor: Int = 0xFF10B981.toInt(),
    val darkMuted: Int = 0xFF0F172A.toInt()
) {
    val glowCompose: Color get() = Color(glowColor)
    val accentCompose: Color get() = Color(accentColor)
    val darkCompose: Color get() = Color(darkMuted)
}

/**
 * Slot posisi ternormalisasi untuk setiap sampul lagu di Layer 2 (100% DI BELAKANG OBJEK!).
 * Semua koordinat dalam fraksi dimensi kanvas 9:16 (0f..1f).
 */
data class CoverSlotTransform(
    val trackIndex: Int,
    val centerXFrac: Float,
    val centerYFrac: Float,
    val widthFrac: Float,
    val rotationDeg: Float,
    val alpha: Float = 1f
)

/**
 * State tunggal WYSIWYG untuk Live Preview (Compose) & HD Export (1080x1920).
 */
data class DepthPosterState(
    // Lagu (1-5 lagu — SEMUA sampul dikunci 100% di Layer 2!)
    val selectedTracks: List<TrackItem> = emptyList(),
    // Foto & Transform 1:1 (identik untuk Layer 1 dan Layer 3)
    val depthEnabled: Boolean = true,
    val showCutoutCheckerboard: Boolean = false,
    val photoScale: Float = 1.0f,
    val photoPanXFrac: Float = 0f,
    val photoPanYFrac: Float = 0f,
    val bgDim: Float = 0.16f,        // 0f..0.65f
    val bgBlur: Float = 0.0f,        // 0f..1.0f
    // Layer 2: Sampul & Teks Besar di Belakang Objek
    val coverOffsetXFrac: Float = 0.50f, // 0.15f..0.85f
    val coverOffsetYFrac: Float = 0.30f, // 0.14f..0.68f
    val coverScale: Float = 1.0f,        // 0.65f..1.35f
    val coverRotationDeg: Float = 0f,    // -15f..+15f
    val coverGlowEnabled: Boolean = true,
    val bigTextEnabled: Boolean = true,
    val bigTextContent: String = "",
    // Layer 4: Kartu Liquid Glass Bawah (TANPA thumbnail sampul depan!)
    val glassCardTheme: GlassCardTheme = GlassCardTheme.DARK_GLASS,
    val showBottomGlassCard: Boolean = true,
    val showProgressBar: Boolean = true,
    val showWatermark: Boolean = true,
    val progressFraction: Float = 0.38f,
    // Palette adaptif
    val palette: PosterCoverPalette = PosterCoverPalette()
)

/**
 * Menghitung posisi Y otomatis untuk sampul di Layer 2 agar 30%-42% bagian bawah sampul
 * langsung bertumpuk rapi DI BELAKANG kepala/bahu objek seketika setelah foto dipilih.
 */
fun computeAutoCoverPlacementY(subjectBounds: RectF, coverScale: Float = 1.0f): Float {
    // Pada rasio 9:16, tinggi kotak sampul = widthFrac * (9/16)
    val baseWidthFrac = (0.56f * coverScale).coerceIn(0.36f, 0.74f)
    val coverHeightFrac = baseWidthFrac * (9f / 16f)
    val headTop = subjectBounds.top.coerceIn(0.10f, 0.58f)
    // Tempatkan bagian bawah sampul ~36% masuk melewati batas atas kepala/bahu
    val desiredCenterY = headTop - coverHeightFrac * 0.14f
    return desiredCenterY.coerceIn(0.19f, 0.48f)
}

/**
 * Menghasilkan daftar transformasi sampul di Layer 2 (1 sampai 5 sampul).
 * ATURAN MUTLAK: Seluruh slot ini HANYA dirender di Layer 2 (DI BELAKANG Layer 3 cutout!).
 */
fun computeLayer2CoverSlots(state: DepthPosterState): List<CoverSlotTransform> {
    val count = state.selectedTracks.size.coerceIn(0, 5)
    if (count == 0) return emptyList()

    val cx = state.coverOffsetXFrac.coerceIn(0.14f, 0.86f)
    val cy = state.coverOffsetYFrac.coerceIn(0.14f, 0.72f)
    val sc = state.coverScale.coerceIn(0.65f, 1.35f)
    val baseRot = state.coverRotationDeg.coerceIn(-15f, 15f)

    return when (count) {
        1 -> listOf(
            CoverSlotTransform(
                trackIndex = 0,
                centerXFrac = cx,
                centerYFrac = cy,
                widthFrac = (0.58f * sc).coerceIn(0.34f, 0.78f),
                rotationDeg = baseRot
            )
        )
        2 -> listOf(
            CoverSlotTransform(
                trackIndex = 0,
                centerXFrac = (cx - 0.19f * sc).coerceIn(0.16f, 0.84f),
                centerYFrac = (cy + 0.015f).coerceIn(0.14f, 0.72f),
                widthFrac = (0.43f * sc).coerceIn(0.28f, 0.56f),
                rotationDeg = baseRot - 7.5f
            ),
            CoverSlotTransform(
                trackIndex = 1,
                centerXFrac = (cx + 0.19f * sc).coerceIn(0.16f, 0.84f),
                centerYFrac = (cy - 0.015f).coerceIn(0.14f, 0.72f),
                widthFrac = (0.43f * sc).coerceIn(0.28f, 0.56f),
                rotationDeg = baseRot + 7.5f
            )
        )
        3 -> listOf(
            CoverSlotTransform(
                trackIndex = 1,
                centerXFrac = (cx - 0.24f * sc).coerceIn(0.14f, 0.86f),
                centerYFrac = (cy + 0.045f).coerceIn(0.14f, 0.74f),
                widthFrac = (0.37f * sc).coerceIn(0.25f, 0.48f),
                rotationDeg = baseRot - 11f,
                alpha = 0.96f
            ),
            CoverSlotTransform(
                trackIndex = 2,
                centerXFrac = (cx + 0.24f * sc).coerceIn(0.14f, 0.86f),
                centerYFrac = (cy + 0.045f).coerceIn(0.14f, 0.74f),
                widthFrac = (0.37f * sc).coerceIn(0.25f, 0.48f),
                rotationDeg = baseRot + 11f,
                alpha = 0.96f
            ),
            CoverSlotTransform(
                trackIndex = 0,
                centerXFrac = cx,
                centerYFrac = (cy - 0.025f).coerceIn(0.13f, 0.70f),
                widthFrac = (0.44f * sc).coerceIn(0.28f, 0.56f),
                rotationDeg = baseRot
            )
        )
        4 -> listOf(
            CoverSlotTransform(0, (cx - 0.18f * sc).coerceIn(0.15f, 0.85f), (cy - 0.085f).coerceIn(0.13f, 0.68f), (0.36f * sc).coerceIn(0.24f, 0.46f), baseRot - 6f),
            CoverSlotTransform(1, (cx + 0.18f * sc).coerceIn(0.15f, 0.85f), (cy - 0.065f).coerceIn(0.13f, 0.68f), (0.36f * sc).coerceIn(0.24f, 0.46f), baseRot + 6f),
            CoverSlotTransform(2, (cx - 0.19f * sc).coerceIn(0.15f, 0.85f), (cy + 0.115f).coerceIn(0.18f, 0.76f), (0.35f * sc).coerceIn(0.24f, 0.45f), baseRot + 4.5f),
            CoverSlotTransform(3, (cx + 0.19f * sc).coerceIn(0.15f, 0.85f), (cy + 0.105f).coerceIn(0.18f, 0.76f), (0.35f * sc).coerceIn(0.24f, 0.45f), baseRot - 4.5f)
        )
        else -> listOf(
            CoverSlotTransform(1, (cx - 0.27f * sc).coerceIn(0.14f, 0.86f), (cy + 0.08f).coerceIn(0.16f, 0.76f), (0.32f * sc).coerceIn(0.22f, 0.42f), baseRot - 14f, 0.94f),
            CoverSlotTransform(2, (cx + 0.27f * sc).coerceIn(0.14f, 0.86f), (cy + 0.08f).coerceIn(0.16f, 0.76f), (0.32f * sc).coerceIn(0.22f, 0.42f), baseRot + 14f, 0.94f),
            CoverSlotTransform(3, (cx - 0.16f * sc).coerceIn(0.14f, 0.86f), (cy - 0.04f).coerceIn(0.13f, 0.70f), (0.34f * sc).coerceIn(0.23f, 0.44f), baseRot - 6.5f, 0.98f),
            CoverSlotTransform(4, (cx + 0.16f * sc).coerceIn(0.14f, 0.86f), (cy - 0.04f).coerceIn(0.13f, 0.70f), (0.34f * sc).coerceIn(0.23f, 0.44f), baseRot + 6.5f, 0.98f),
            CoverSlotTransform(0, cx, (cy + 0.02f).coerceIn(0.14f, 0.72f), (0.38f * sc).coerceIn(0.26f, 0.48f), baseRot, 1f)
        )
    }
}
