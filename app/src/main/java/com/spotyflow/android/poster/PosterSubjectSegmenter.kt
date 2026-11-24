package com.spotyflow.android.poster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.palette.graphics.Palette
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.spotyflow.android.data.remote.SpotyHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.nio.FloatBuffer
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Mesin Pemisah Objek Otomatis 3-Lapis Anti-Gagal untuk Poster Lagu Depth Effect (v2.5.0).
 *
 * Urutan prioritas:
 * 1. Opsional Pro: Clipdrop Remove Background API (jika user mengisi API Key di Pengaturan AI Potong)
 * 2. Utama: Google ML Kit Subject Segmentation (auto-install modul via ModuleInstallClient untuk APK sideload)
 * 3. Fallback Offline Instan: Google ML Kit Selfie Segmentation (100% offline tanpa Play Services)
 *
 * Dilengkapi 4-Stage Studio Matte Refinement agar potongan objek transparan 100% rapi,
 * bebas gerigi, bebas bercak halo di atas sampul, dan bebas lubang transparan di badan/pakaian!
 */
object PosterSubjectSegmenter {
    private const val PREFS_NAME = "sf_poster_prefs"
    private const val KEY_CLIPDROP_API = "clipdrop_api_key"
    private const val CLIPDROP_ENDPOINT = "https://clipdrop-api.co/remove-background/v1"

    fun getClipdropApiKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CLIPDROP_API, "")
            .orEmpty()
            .trim()
    }

    fun setClipdropApiKey(context: Context, apiKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLIPDROP_API, apiKey.trim())
            .apply()
    }

    // =========================================================================
    // 1. EXIF-SAFE UPRIGHT PHOTO DECODER (Sisi terpanjang maks 1920px, ARGB_8888)
    // =========================================================================
    suspend fun loadUprightPhoto(context: Context, uri: Uri, maxSidePx: Int = 1920): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                // Baca orientasi EXIF asli
                val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL

                // Ukur dimensi tanpa memuat piksel ke RAM
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                val rawW = bounds.outWidth
                val rawH = bounds.outHeight
                if (rawW <= 0 || rawH <= 0) return@withContext null

                var sampleSize = 1
                var maxDim = max(rawW, rawH)
                while (maxDim / sampleSize > maxSidePx * 2) {
                    sampleSize *= 2
                }

                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                } ?: return@withContext null

                val matrix = Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                        ExifInterface.ORIENTATION_TRANSPOSE -> {
                            postRotate(90f)
                            postScale(-1f, 1f)
                        }
                        ExifInterface.ORIENTATION_TRANSVERSE -> {
                            postRotate(270f)
                            postScale(-1f, 1f)
                        }
                    }
                }

                val rotated = if (!matrix.isIdentity) {
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                        if (it !== decoded) decoded.recycle()
                    }
                } else {
                    decoded
                }

                // Skala presisi jika sisi terpanjang > maxSidePx
                val longest = max(rotated.width, rotated.height)
                val finalBmp = if (longest > maxSidePx) {
                    val ratio = maxSidePx.toFloat() / longest.toFloat()
                    val tw = (rotated.width * ratio).roundToInt().coerceAtLeast(1)
                    val th = (rotated.height * ratio).roundToInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(rotated, tw, th, true).also {
                        if (it !== rotated) rotated.recycle()
                    }
                } else {
                    rotated
                }

                if (finalBmp.config != Bitmap.Config.ARGB_8888) {
                    finalBmp.copy(Bitmap.Config.ARGB_8888, false).also { finalBmp.recycle() }
                } else {
                    finalBmp
                }
            } catch (_: Throwable) {
                null
            }
        }

    // =========================================================================
    // 2. MAIN 3-TIER SEGMENTATION PIPELINE
    // =========================================================================
    suspend fun segmentSubject(
        context: Context,
        uprightPhoto: Bitmap,
        onStatus: (String) -> Unit = {}
    ): SegmentationResult? = withContext(Dispatchers.IO) {
        // Tier 0 (Opsional Pro): Clipdrop Remove-Background API jika user mengisi key sendiri
        val clipdropKey = getClipdropApiKey(context)
        if (clipdropKey.isNotBlank()) {
            onStatus("Memotong objek HD via Clipdrop Pro...")
            val clipResult = tryClipdropSegmentation(uprightPhoto, clipdropKey)
            if (clipResult != null) return@withContext clipResult
            onStatus("Clipdrop gagal, beralih ke Google ML Kit...")
        }

        // Tier 1 (Utama): Google ML Kit Subject Segmentation (Manusia + HP di Cermin + Semua Objek)
        val subjectResult = tryMlKitSubjectSegmentation(context, uprightPhoto, onStatus)
        if (subjectResult != null) return@withContext subjectResult

        // Tier 2 (Fallback 100% Offline): Google ML Kit Selfie Segmentation
        onStatus("Memproses potongan AI Offline...")
        tryMlKitSelfieSegmentation(uprightPhoto)
    }

    private suspend fun tryClipdropSegmentation(
        uprightPhoto: Bitmap,
        apiKey: String
    ): SegmentationResult? = withContext(Dispatchers.IO) {
        try {
            val baos = ByteArrayOutputStream()
            uprightPhoto.compress(Bitmap.CompressFormat.JPEG, 92, baos)
            val jpegBytes = baos.toByteArray()

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image_file",
                    "photo.jpg",
                    jpegBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val req = Request.Builder()
                .url(CLIPDROP_ENDPOINT)
                .header("x-api-key", apiKey)
                .post(body)
                .build()

            SpotyHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val pngBytes = resp.body?.bytes() ?: return@withContext null
                val decoded = BitmapFactory.decodeByteArray(
                    pngBytes,
                    0,
                    pngBytes.size,
                    BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                ) ?: return@withContext null

                val exact = ensureExactSizeArgb(decoded, uprightPhoto.width, uprightPhoto.height)
                if (exact !== decoded) decoded.recycle()

                // Bersihkan residual alpha < 12 menjadi murni 0x00000000 & ukur bounds
                val (cleaned, bounds, coverage) = cleanExternalCutoutAndMeasure(exact)
                if (cleaned !== exact) exact.recycle()
                if (coverage < 0.015f || coverage > 0.985f) {
                    cleaned.recycle()
                    return@withContext null
                }
                SegmentationResult(
                    cutoutBitmap = cleaned,
                    subjectBounds = bounds,
                    engineLabel = "Clipdrop HD"
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun tryMlKitSubjectSegmentation(
        context: Context,
        uprightPhoto: Bitmap,
        onStatus: (String) -> Unit
    ): SegmentationResult? = withContext(Dispatchers.IO) {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .enableForegroundConfidenceMask()
            .build()
        val segmenter = try {
            SubjectSegmentation.getClient(options)
        } catch (_: Throwable) {
            return@withContext null
        }

        try {
            // Wajib cek & unduh modul otomatis untuk APK sideload via ModuleInstallClient
            val moduleInstallClient = ModuleInstall.getClient(context)
            val avail = withTimeoutOrNull(4_500L) {
                moduleInstallClient.areModulesAvailable(segmenter).awaitSafe()
            }
            if (avail == null || !avail.areModulesAvailable()) {
                onStatus("Menyiapkan AI Potong Objek...")
                val installed = installSubjectModuleWithTimeout(context, segmenter, onStatus)
                if (!installed) return@withContext null
            }

            onStatus("Memisahkan objek dari latar...")
            val inputImage = InputImage.fromBitmap(uprightPhoto, 0)
            val result = withTimeoutOrNull(14_000L) {
                segmenter.process(inputImage).awaitSafe()
            } ?: return@withContext null

            val confMask: FloatBuffer? = result.foregroundConfidenceMask
            if (confMask != null) {
                confMask.rewind()
                val expected = uprightPhoto.width * uprightPhoto.height
                if (confMask.remaining() >= expected) {
                    val floats = FloatArray(expected)
                    confMask.get(floats)
                    val refined = withContext(Dispatchers.Default) {
                        buildRefinedCutoutFromMask(
                            uprightPhoto = uprightPhoto,
                            mask = floats,
                            maskW = uprightPhoto.width,
                            maskH = uprightPhoto.height
                        )
                    }
                    if (refined != null) {
                        return@withContext SegmentationResult(
                            cutoutBitmap = refined.first,
                            subjectBounds = refined.second,
                            engineLabel = "ML Kit Subject"
                        )
                    }
                }
            }

            // Fallback ke foregroundBitmap langsung jika confidence mask kosong
            val fgBmp = result.foregroundBitmap ?: return@withContext null
            val exact = ensureExactSizeArgb(fgBmp, uprightPhoto.width, uprightPhoto.height)
            val (cleaned, bounds, coverage) = withContext(Dispatchers.Default) {
                cleanExternalCutoutAndMeasure(exact)
            }
            if (cleaned !== exact) exact.recycle()
            if (coverage < 0.018f || coverage > 0.965f) {
                cleaned.recycle()
                return@withContext null
            }
            SegmentationResult(
                cutoutBitmap = cleaned,
                subjectBounds = bounds,
                engineLabel = "ML Kit Subject"
            )
        } catch (_: Throwable) {
            null
        } finally {
            try {
                segmenter.close()
            } catch (_: Throwable) {}
        }
    }

    private suspend fun installSubjectModuleWithTimeout(
        context: Context,
        segmenter: com.google.mlkit.vision.segmentation.subject.SubjectSegmenter,
        onStatus: (String) -> Unit
    ): Boolean {
        return withTimeoutOrNull(25_000L) {
            suspendCancellableCoroutine { cont ->
                val client = ModuleInstall.getClient(context)
                var listenerRef: InstallStatusListener? = null
                val listener = InstallStatusListener { update ->
                    val state = update.installState
                    val info = update.progressInfo
                    if (info != null && info.totalBytesToDownload > 0L) {
                        val pct = ((info.bytesDownloaded * 100L) / info.totalBytesToDownload).toInt().coerceIn(0, 100)
                        onStatus("Mengunduh model AI Potong ($pct%)...")
                    }
                    if (state == ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED) {
                        listenerRef?.let { try { client.unregisterListener(it) } catch (_: Throwable) {} }
                        if (cont.isActive) cont.resume(true)
                    } else if (
                        state == ModuleInstallStatusUpdate.InstallState.STATE_FAILED ||
                        state == ModuleInstallStatusUpdate.InstallState.STATE_CANCELED
                    ) {
                        listenerRef?.let { try { client.unregisterListener(it) } catch (_: Throwable) {} }
                        if (cont.isActive) cont.resume(false)
                    }
                }
                listenerRef = listener

                val request = ModuleInstallRequest.newBuilder()
                    .addApi(segmenter)
                    .setListener(listener)
                    .build()

                client.installModules(request)
                    .addOnSuccessListener { response ->
                        if (response.areModulesAlreadyInstalled()) {
                            try { client.unregisterListener(listener) } catch (_: Throwable) {}
                            if (cont.isActive) cont.resume(true)
                        }
                    }
                    .addOnFailureListener {
                        try { client.unregisterListener(listener) } catch (_: Throwable) {}
                        if (cont.isActive) cont.resume(false)
                    }

                cont.invokeOnCancellation {
                    try { client.unregisterListener(listener) } catch (_: Throwable) {}
                }
            }
        } ?: false
    }

    private suspend fun tryMlKitSelfieSegmentation(uprightPhoto: Bitmap): SegmentationResult? =
        withContext(Dispatchers.IO) {
            val options = SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()
            val segmenter = try {
                Segmentation.getClient(options)
            } catch (_: Throwable) {
                return@withContext null
            }

            try {
                val inputImage = InputImage.fromBitmap(uprightPhoto, 0)
                val segMask = withTimeoutOrNull(12_000L) {
                    segmenter.process(inputImage).awaitSafe()
                } ?: return@withContext null

                val mw = segMask.width
                val mh = segMask.height
                val buf = segMask.buffer
                buf.rewind()
                val floats = FloatArray(mw * mh)
                for (i in floats.indices) {
                    floats[i] = if (buf.remaining() >= 4) buf.float else 0f
                }

                val refined = withContext(Dispatchers.Default) {
                    buildRefinedCutoutFromMask(
                        uprightPhoto = uprightPhoto,
                        mask = floats,
                        maskW = mw,
                        maskH = mh
                    )
                } ?: return@withContext null

                SegmentationResult(
                    cutoutBitmap = refined.first,
                    subjectBounds = refined.second,
                    engineLabel = "ML Kit Offline"
                )
            } catch (_: Throwable) {
                null
            } finally {
                try {
                    segmenter.close()
                } catch (_: Throwable) {}
            }
        }

    // =========================================================================
    // 3. 4-STAGE STUDIO MATTE REFINEMENT (RAPIH, ANTI-HALO, ANTI-TEMBUS BADAN!)
    // =========================================================================
    private fun buildRefinedCutoutFromMask(
        uprightPhoto: Bitmap,
        mask: FloatArray,
        maskW: Int,
        maskH: Int
    ): Pair<Bitmap, RectF>? {
        val w = uprightPhoto.width
        val h = uprightPhoto.height
        if (w <= 0 || h <= 0 || maskW <= 0 || maskH <= 0) return null

        // Tahap 1: Bilinear upsample mask ke ukuran kerja yang rapi + S-Curve Hard Core & Clean Floor
        // Bekerja pada resolusi mask (atau max 512px) untuk morphological speckle cleanup agar super cepat!
        val workMax = 512
        val longest = max(w, h)
        val scaleToWork = if (longest > workMax) workMax.toFloat() / longest.toFloat() else 1f
        val ww = (w * scaleToWork).roundToInt().coerceAtLeast(32)
        val wh = (h * scaleToWork).roundToInt().coerceAtLeast(32)

        val workAlpha = FloatArray(ww * wh)
        var fgCount = 0
        for (y in 0 until wh) {
            val fy = (y.toFloat() / (wh - 1).coerceAtLeast(1)) * (maskH - 1)
            val y0 = fy.toInt().coerceIn(0, maskH - 1)
            val y1 = (y0 + 1).coerceAtMost(maskH - 1)
            val wy = fy - y0
            val row0 = y0 * maskW
            val row1 = y1 * maskW

            for (x in 0 until ww) {
                val fx = (x.toFloat() / (ww - 1).coerceAtLeast(1)) * (maskW - 1)
                val x0 = fx.toInt().coerceIn(0, maskW - 1)
                val x1 = (x0 + 1).coerceAtMost(maskW - 1)
                val wx = fx - x0

                val c00 = mask[row0 + x0]
                val c10 = mask[row0 + x1]
                val c01 = mask[row1 + x0]
                val c11 = mask[row1 + x1]
                val top = c00 + (c10 - c00) * wx
                val bot = c01 + (c11 - c01) * wx
                val conf = top + (bot - top) * wy

                // Hard noise floor (< 0.34 -> 0) & solid interior (> 0.70 -> 1.0)
                val a = smoothstep(0.34f, 0.70f, conf)
                workAlpha[y * ww + x] = a
                if (a > 0.5f) fgCount++
            }
        }

        val coverage = fgCount.toFloat() / (ww * wh).toFloat()
        if (coverage < 0.018f || coverage > 0.965f) return null

        // Tahap 2: 3x3 Morphological Speckle & Pinhole Cleanup (hilangkan bintik nyasar & lubang kecil!)
        val cleaned = FloatArray(ww * wh)
        for (y in 0 until wh) {
            val yPrev = (y - 1).coerceAtLeast(0)
            val yNext = (y + 1).coerceAtMost(wh - 1)
            for (x in 0 until ww) {
                val idx = y * ww + x
                val cur = workAlpha[idx]
                val xPrev = (x - 1).coerceAtLeast(0)
                val xNext = (x + 1).coerceAtMost(ww - 1)

                var highNeighbors = 0
                var sum = 0f
                for (ny in yPrev..yNext) {
                    val nRow = ny * ww
                    for (nx in xPrev..xNext) {
                        val v = workAlpha[nRow + nx]
                        sum += v
                        if (v >= 0.55f) highNeighbors++
                    }
                }
                cleaned[idx] = when {
                    // Bintik terisolasi di background -> hapus total jadi 0
                    cur > 0.1f && highNeighbors <= 2 -> 0f
                    // Lubang kecil di dalam badan/rambut -> tutup rapat jadi 1.0
                    cur < 0.85f && highNeighbors >= 7 -> 1f
                    // Tepi transisi -> haluskan 1 pass anti-aliasing
                    cur in 0.04f..0.96f -> smoothstep(0.18f, 0.82f, sum / 9f)
                    else -> cur
                }
            }
        }

        // Tahap 3: 1-Pass Separable Anti-Aliasing Edge Soften (hanya tepi 1-2px, isi tetap solid 100%!)
        val finalWorkAlpha = FloatArray(ww * wh)
        for (y in 0 until wh) {
            val yPrev = (y - 1).coerceAtLeast(0)
            val yNext = (y + 1).coerceAtMost(wh - 1)
            val row = y * ww
            for (x in 0 until ww) {
                val c = cleaned[row + x]
                // Jika interior solid (semua 4 tetangga = 1) atau background murni (semua 4 tetangga = 0), skip
                val up = cleaned[yPrev * ww + x]
                val dn = cleaned[yNext * ww + x]
                val lf = cleaned[row + (x - 1).coerceAtLeast(0)]
                val rt = cleaned[row + (x + 1).coerceAtMost(ww - 1)]
                val minN = min(min(up, dn), min(lf, rt))
                val maxN = max(max(up, dn), max(lf, rt))
                finalWorkAlpha[row + x] = if (c >= 0.99f && minN >= 0.99f) {
                    1f
                } else if (c <= 0.01f && maxN <= 0.01f) {
                    0f
                } else {
                    // Gaussian 3x3 cross blend halus khusus garis tepi
                    val blended = c * 0.44f + (up + dn + lf + rt) * 0.14f
                    if (blended < 0.06f) 0f else if (blended > 0.94f) 1f else blended
                }
            }
        }

        // Tahap 4: Komposisi Pixel-Perfect 1:1 ke Bitmap exact (uprightPhoto.width, uprightPhoto.height)
        val photoPixels = IntArray(w * h)
        uprightPhoto.getPixels(photoPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)

        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0

        val scaleX = (ww - 1).toFloat() / (w - 1).coerceAtLeast(1).toFloat()
        val scaleY = (wh - 1).toFloat() / (h - 1).coerceAtLeast(1).toFloat()

        for (y in 0 until h) {
            val fy = y * scaleY
            val y0 = fy.toInt().coerceIn(0, wh - 1)
            val y1 = (y0 + 1).coerceAtMost(wh - 1)
            val wy = fy - y0
            val r0 = y0 * ww
            val r1 = y1 * ww
            val pRow = y * w

            for (x in 0 until w) {
                val fx = x * scaleX
                val x0 = fx.toInt().coerceIn(0, ww - 1)
                val x1 = (x0 + 1).coerceAtMost(ww - 1)
                val wx = fx - x0

                val a00 = finalWorkAlpha[r0 + x0]
                val a10 = finalWorkAlpha[r0 + x1]
                val a01 = finalWorkAlpha[r1 + x0]
                val a11 = finalWorkAlpha[r1 + x1]
                val top = a00 + (a10 - a00) * wx
                val bot = a01 + (a11 - a01) * wx
                val alphaF = top + (bot - top) * wy

                val alphaInt = (alphaF * 255f).roundToInt().coerceIn(0, 255)
                val outIdx = pRow + x
                if (alphaInt <= 6) {
                    // Murni transparan 0x00000000 (anti halo & anti kotor!)
                    outPixels[outIdx] = 0x00000000
                } else {
                    val finalA = if (alphaInt >= 248) 255 else alphaInt
                    val rgb = photoPixels[outIdx] and 0x00FFFFFF
                    outPixels[outIdx] = (finalA shl 24) or rgb
                    if (finalA > 128) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
        }

        if (maxX <= minX || maxY <= minY) return null

        val cutout = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        cutout.setHasAlpha(true)
        cutout.setPixels(outPixels, 0, w, 0, 0, w, h)

        val bounds = RectF(
            minX.toFloat() / w.toFloat(),
            minY.toFloat() / h.toFloat(),
            maxX.toFloat() / w.toFloat(),
            maxY.toFloat() / h.toFloat()
        )
        return Pair(cutout, bounds)
    }

    private fun cleanExternalCutoutAndMeasure(src: Bitmap): Triple<Bitmap, RectF, Float> {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        var opaqueCount = 0

        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val p = pixels[row + x]
                val a = (p ushr 24) and 0xFF
                if (a <= 12) {
                    pixels[row + x] = 0x00000000
                } else {
                    if (a >= 246) {
                        pixels[row + x] = (0xFF shl 24) or (p and 0x00FFFFFF)
                    }
                    if (a > 128) {
                        opaqueCount++
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setHasAlpha(true)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        val bounds = if (maxX > minX && maxY > minY) {
            RectF(minX.toFloat() / w, minY.toFloat() / h, maxX.toFloat() / w, maxY.toFloat() / h)
        } else {
            RectF(0.2f, 0.25f, 0.8f, 1.0f)
        }
        val cov = opaqueCount.toFloat() / (w * h).coerceAtLeast(1).toFloat()
        return Triple(out, bounds, cov)
    }

    private fun ensureExactSizeArgb(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scaled = if (src.width != targetW || src.height != targetH) {
            Bitmap.createScaledBitmap(src, targetW, targetH, true)
        } else {
            src
        }
        val argb = if (scaled.config != Bitmap.Config.ARGB_8888) {
            scaled.copy(Bitmap.Config.ARGB_8888, false).also {
                if (scaled !== src) scaled.recycle()
            }
        } else {
            scaled
        }
        argb.setHasAlpha(true)
        return argb
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // =========================================================================
    // 4. ADAPTIVE PALETTE EXTRACTION & CROSS-API BLUR BACKDROP (API 26..35!)
    // =========================================================================
    suspend fun extractCoverPalette(coverBitmap: Bitmap?): PosterCoverPalette =
        withContext(Dispatchers.Default) {
            if (coverBitmap == null) return@withContext PosterCoverPalette()
            try {
                val p = Palette.from(coverBitmap).maximumColorCount(12).generate()
                val vibrant = p.getVibrantColor(0)
                val lightVibrant = p.getLightVibrantColor(0)
                val dominant = p.getDominantColor(0xFF10B981.toInt())
                val darkMuted = p.getDarkMutedColor(0xFF0F172A.toInt())

                val rawAccent = when {
                    vibrant != 0 -> vibrant
                    lightVibrant != 0 -> lightVibrant
                    else -> dominant
                }
                // Pastikan accent cukup cerah terbaca di atas kartu kaca gelap
                val hsv = FloatArray(3)
                Color.colorToHSV(rawAccent, hsv)
                if (hsv[2] < 0.62f) hsv[2] = 0.78f
                if (hsv[1] < 0.35f) hsv[1] = 0.55f
                val brightAccent = Color.HSVToColor(hsv)

                PosterCoverPalette(
                    glowColor = brightAccent,
                    accentColor = brightAccent,
                    darkMuted = darkMuted
                )
            } catch (_: Throwable) {
                PosterCoverPalette()
            }
        }

    /**
     * Blur latar yang bekerja 100% identik di Android 8 s/d Android 15 (API 26..35)
     * tanpa bergantung RenderEffect API 31+!
     */
    fun buildBlurredBackdrop(src: Bitmap, blurFraction: Float): Bitmap {
        val f = blurFraction.coerceIn(0f, 1f)
        if (f <= 0.02f) return src
        // Downsample 2-pass lalu upscale bilinear halus -> super cepat & mulus tanpa artefak kotak
        val divisor = (3f + f * 20f).roundToInt().coerceIn(3, 24)
        val sw = (src.width / divisor).coerceAtLeast(18)
        val sh = (src.height / divisor).coerceAtLeast(18)
        val pass1 = Bitmap.createScaledBitmap(src, sw, sh, true)
        val midW = (sw * 1.6f).roundToInt().coerceAtLeast(24)
        val midH = (sh * 1.6f).roundToInt().coerceAtLeast(24)
        val pass2 = Bitmap.createScaledBitmap(pass1, midW, midH, true)
        pass1.recycle()
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(pass2, null, RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), p)
        pass2.recycle()
        return out
    }

    /**
     * Mengunduh sampul album HD sebagai Bitmap via OkHttp (untuk Palette API & HD Export).
     */
    suspend fun loadCoverBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        try {
            val req = Request.Builder().url(url).get().build()
            SpotyHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun <T> Task<T>.awaitSafe(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res -> if (cont.isActive) cont.resume(res) }
        addOnFailureListener { if (cont.isActive) cont.resume(null) }
        addOnCanceledListener { if (cont.isActive) cont.resume(null) }
    }
}
