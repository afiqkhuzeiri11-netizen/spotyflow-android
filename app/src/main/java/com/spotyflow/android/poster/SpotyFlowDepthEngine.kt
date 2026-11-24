package com.spotyflow.android.poster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DepthCutoutResult(
    val rawConfidence: FloatArray,
    val maskWidth: Int,
    val maskHeight: Int,
    val coverage: Float,
    val hasSubject: Boolean,
    val engineLabel: String = "ML Kit Subject AI"
)

data class CutoutBundle(
    val cutoutBitmap: Bitmap,
    val alphaMaskBitmap: Bitmap
)

/**
 * Super-Accurate Auralys-Style Subject Cutter (v2.7.0-native):
 * ONLY cuts the real subject/object from the user's background photo (Layer 1) and overlays it
 * 1:1 pixel-aligned on Layer 3 so 3D music cards sit cleanly sandwiched in Layer 2!
 *
 * Dual-AI Pipeline:
 * 1. Primary: Google ML Kit Subject Segmentation (handles motorcycles, cars, people, pets, dark scenes!)
 *    with automatic ModuleInstallClient download for sideloaded APKs.
 * 2. Fallback: Offline Google ML Kit Selfie Segmentation (100% offline without Play Services).
 *
 * 4-Stage Studio Matte Refinement:
 * - Smoothstep S-curve (solid interior, clean 0x00000000 noise floor)
 * - 3x3 Morphological Speckle & Interior Pinhole Cleanup
 * - Connected-Component Solid Filter (kills disconnected shredded floating scraps!)
 * - 1-Pass Border-Only Anti-Aliasing + exact 1:1 (uprightPhoto.width, uprightPhoto.height) ARGB_8888 output
 */
object SpotyFlowDepthEngine {

    suspend fun loadUprightPhoto(
        context: Context,
        uri: Uri,
        maxSide: Int = 1600
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return@withContext null

            var sampleSize = 1
            val longest = max(rawW, rawH)
            while (longest / sampleSize > maxSide * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext null

            val mat = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> mat.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> mat.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> mat.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> mat.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> mat.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    mat.postRotate(90f)
                    mat.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    mat.postRotate(270f)
                    mat.postScale(-1f, 1f)
                }
            }

            val rotated = if (!mat.isIdentity) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, mat, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val curLong = max(rotated.width, rotated.height)
            if (curLong > maxSide) {
                val scale = maxSide.toFloat() / curLong.toFloat()
                val targetW = (rotated.width * scale).roundToInt().coerceAtLeast(1)
                val targetH = (rotated.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(rotated, targetW, targetH, true).also {
                    if (it !== rotated) rotated.recycle()
                }
            } else {
                rotated
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Super-Accurate Dual-AI Subject Segmentation (Motorcycles, Cars, People, Pets, Objects).
     */
    suspend fun segmentSubject(
        context: Context,
        uprightFullPhoto: Bitmap,
        onStatus: (String) -> Unit = {}
    ): DepthCutoutResult = withContext(Dispatchers.Default) {
        val maxWorkSide = 1024
        val longest = max(uprightFullPhoto.width, uprightFullPhoto.height)
        val workBmp = if (longest > maxWorkSide) {
            val scale = maxWorkSide.toFloat() / longest.toFloat()
            val w = (uprightFullPhoto.width * scale).roundToInt().coerceAtLeast(32)
            val h = (uprightFullPhoto.height * scale).roundToInt().coerceAtLeast(32)
            Bitmap.createScaledBitmap(uprightFullPhoto, w, h, true)
        } else {
            uprightFullPhoto
        }

        try {
            // Tier 1: Google ML Kit Subject Segmentation (All Objects: Motorcycles, Cars, People, Dark Scenes!)
            val subjectRes = trySubjectSegmentation(context, workBmp, onStatus)
            if (subjectRes != null && subjectRes.hasSubject) {
                return@withContext subjectRes
            }

            // Tier 2: Offline ML Kit Selfie Segmentation Fallback
            onStatus("Memproses AI Potong Offline...")
            trySelfieSegmentation(workBmp)
        } finally {
            if (workBmp !== uprightFullPhoto) workBmp.recycle()
        }
    }

    private suspend fun trySubjectSegmentation(
        context: Context,
        workBmp: Bitmap,
        onStatus: (String) -> Unit
    ): DepthCutoutResult? {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .enableForegroundBitmap()
            .build()
        val segmenter = try {
            SubjectSegmentation.getClient(options)
        } catch (_: Throwable) {
            return null
        }

        return try {
            val installClient = ModuleInstall.getClient(context)
            val avail = withTimeoutOrNull(4_500L) {
                installClient.areModulesAvailable(segmenter).awaitSafe()
            }
            if (avail == null || !avail.areModulesAvailable()) {
                onStatus("Menyiapkan AI Potong Objek...")
                val ready = installSubjectModule(context, segmenter, onStatus)
                if (!ready) return null
            }

            onStatus("Memisahkan objek dari latar...")
            val input = InputImage.fromBitmap(workBmp, 0)
            val result = withTimeoutOrNull(14_000L) {
                segmenter.process(input).awaitSafe()
            } ?: return null

            val w = workBmp.width
            val h = workBmp.height
            val total = w * h

            val confBuf = result.foregroundConfidenceMask
            if (confBuf != null) {
                confBuf.rewind()
                if (confBuf.remaining() >= total) {
                    val conf = FloatArray(total)
                    confBuf.get(conf)
                    var fgCount = 0
                    for (i in 0 until total) if (conf[i] > 0.5f) fgCount++
                    val cov = fgCount.toFloat() / total.coerceAtLeast(1).toFloat()
                    if (cov in 0.018f..0.965f) {
                        return DepthCutoutResult(
                            rawConfidence = conf,
                            maskWidth = w,
                            maskHeight = h,
                            coverage = cov,
                            hasSubject = true,
                            engineLabel = "ML Kit Subject AI"
                        )
                    }
                }
            }

            val fgBmp = result.foregroundBitmap ?: return null
            val scaledFg = if (fgBmp.width != w || fgBmp.height != h) {
                Bitmap.createScaledBitmap(fgBmp, w, h, true)
            } else {
                fgBmp
            }
            val px = IntArray(total)
            scaledFg.getPixels(px, 0, w, 0, 0, w, h)
            if (scaledFg !== fgBmp) scaledFg.recycle()

            val conf = FloatArray(total)
            var fgCount = 0
            for (i in 0 until total) {
                val a = ((px[i] ushr 24) and 0xFF) / 255f
                conf[i] = a
                if (a > 0.5f) fgCount++
            }
            val cov = fgCount.toFloat() / total.coerceAtLeast(1).toFloat()
            if (cov in 0.018f..0.965f) {
                DepthCutoutResult(
                    rawConfidence = conf,
                    maskWidth = w,
                    maskHeight = h,
                    coverage = cov,
                    hasSubject = true,
                    engineLabel = "ML Kit Subject AI"
                )
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            try { segmenter.close() } catch (_: Throwable) {}
        }
    }

    private suspend fun installSubjectModule(
        context: Context,
        segmenter: SubjectSegmenter,
        onStatus: (String) -> Unit
    ): Boolean {
        return withTimeoutOrNull(45_000L) {
            suspendCancellableCoroutine { cont ->
                val client = ModuleInstall.getClient(context)
                var listenerRef: InstallStatusListener? = null
                val listener = InstallStatusListener { update ->
                    val info = update.progressInfo
                    if (info != null && info.totalBytesToDownload > 0L) {
                        val pct = ((info.bytesDownloaded * 100L) / info.totalBytesToDownload).toInt().coerceIn(0, 100)
                        onStatus("Mengunduh model AI Potong ($pct%)...")
                    }
                    when (update.installState) {
                        ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> {
                            listenerRef?.let { try { client.unregisterListener(it) } catch (_: Throwable) {} }
                            if (cont.isActive) cont.resume(true)
                        }
                        ModuleInstallStatusUpdate.InstallState.STATE_FAILED,
                        ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> {
                            listenerRef?.let { try { client.unregisterListener(it) } catch (_: Throwable) {} }
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                }
                listenerRef = listener

                val request = ModuleInstallRequest.newBuilder()
                    .addApi(segmenter)
                    .setListener(listener)
                    .build()

                client.installModules(request)
                    .addOnSuccessListener { resp ->
                        if (resp.areModulesAlreadyInstalled()) {
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

    private suspend fun trySelfieSegmentation(workBmp: Bitmap): DepthCutoutResult {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        return try {
            val inputImage = InputImage.fromBitmap(workBmp, 0)
            val mask = withTimeoutOrNull(10_000L) {
                segmenter.process(inputImage).awaitSafe()
            } ?: return DepthCutoutResult(FloatArray(0), 0, 0, 0f, false, "Offline")

            val maskW = mask.width
            val maskH = mask.height
            val total = maskW * maskH
            val buffer = mask.buffer
            buffer.rewind()

            val conf = FloatArray(total)
            var fgCount = 0
            for (i in 0 until total) {
                val c = if (buffer.remaining() >= 4) buffer.getFloat() else 0f
                conf[i] = c
                if (c > 0.5f) fgCount++
            }
            val coverage = if (total > 0) fgCount.toFloat() / total.toFloat() else 0f
            DepthCutoutResult(
                rawConfidence = conf,
                maskWidth = maskW,
                maskHeight = maskH,
                coverage = coverage,
                hasSubject = coverage in 0.02f..0.96f,
                engineLabel = "ML Kit Offline"
            )
        } catch (_: Throwable) {
            DepthCutoutResult(FloatArray(0), 0, 0, 0f, false, "Offline")
        } finally {
            try { segmenter.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Builds Full-HD 1:1 transparent subject cutout + alphaMaskBitmap (v2.8.0-native Studio Matte):
     * - Layer 3 = Exact Layer 1 photo with ONLY the background removed (pure 0x00000000)!
     * - Uses Fast O(N) Edge-Aware Guided Filter guided by the actual Layer 1 RGB luminance so boundaries
     *   snap pixel-accurately onto motorcycles, mirrors, handlebars, hair, and dark objects WITHOUT
     *   destructive morphological erosion!
     * - Respects Sensitivity slider, Edge Feather slider, and manual Rapikan Kuas brush strokes.
     */
    fun buildTransparentObjectCutoutWithMask(
        uprightFullPhoto: Bitmap,
        rawConfidence: FloatArray,
        maskW: Int,
        maskH: Int,
        sensitivity: Float = 0.48f,
        edgeFeather: Float = 0.45f,
        manualBrushMask: Bitmap? = null
    ): CutoutBundle? {
        if (maskW <= 0 || maskH <= 0 || rawConfidence.size < maskW * maskH) return null

        val sens = sensitivity.coerceIn(0.15f, 0.85f)
        val feather = edgeFeather.coerceIn(0f, 1f)
        val total = maskW * maskH

        val brushPixels: IntArray? = if (manualBrushMask != null && !manualBrushMask.isRecycled) {
            val scaledBrush = if (manualBrushMask.width != maskW || manualBrushMask.height != maskH) {
                Bitmap.createScaledBitmap(manualBrushMask, maskW, maskH, false)
            } else {
                manualBrushMask
            }
            IntArray(total).also { arr ->
                scaledBrush.getPixels(arr, 0, maskW, 0, 0, maskW, maskH)
                if (scaledBrush !== manualBrushMask) scaledBrush.recycle()
            }
        } else {
            null
        }

        // Stage 1: Border-Connected Low-Confidence Background Purge (erases glowing floors & wall haze!)
        val bgPurgeThresh = (sens + 0.14f).coerceIn(0.28f, 0.76f)
        val bgVisited = BooleanArray(total)
        val bfsQueue = IntArray(total)
        var qHead = 0
        var qTail = 0
        fun seedBg(idx: Int) {
            if (!bgVisited[idx] && rawConfidence[idx] < bgPurgeThresh) {
                bgVisited[idx] = true
                bfsQueue[qTail++] = idx
            }
        }
        for (x in 0 until maskW) {
            seedBg(x)
            seedBg((maskH - 1) * maskW + x)
        }
        for (y in 1 until maskH - 1) {
            seedBg(y * maskW)
            seedBg(y * maskW + (maskW - 1))
        }
        while (qHead < qTail) {
            val cur = bfsQueue[qHead++]
            val cx = cur % maskW
            val cy = cur / maskW
            if (cx > 0) seedBg(cur - 1)
            if (cx < maskW - 1) seedBg(cur + 1)
            if (cy > 0) seedBg(cur - maskW)
            if (cy < maskH - 1) seedBg(cur + maskW)
        }

        // Stage 2: Strict Background Cutoff Smoothstep + Fringe Tightening (kills 5%-35% floor/wall smudge!)
        val halfSpan = 0.08f + feather * 0.16f
        val low = (sens - halfSpan).coerceIn(0.14f, 0.75f)
        val high = (sens + halfSpan + 0.08f).coerceIn(low + 0.06f, 0.95f)
        val invRange = 1f / (high - low).coerceAtLeast(0.01f)

        val work = FloatArray(total)
        for (i in 0 until total) {
            if (bgVisited[i]) {
                work[i] = 0f
                continue
            }
            val c = rawConfidence[i]
            var a = when {
                c <= low -> 0f
                c >= high -> 1f
                else -> {
                    val t = ((c - low) * invRange).coerceIn(0f, 1f)
                    val s = t * t * (3f - 2f * t)
                    Math.pow(s.toDouble(), 1.18).toFloat()
                }
            }
            work[i] = a
        }

        // Stage 3: Main-Subject Connected-Component Filter (kills disconnected floor debris/bags/lamps; keeps connected handlebars/mirrors!)
        val labels = IntArray(total)
        val compSizes = ArrayList<Int>(64).apply { add(0) }
        var nextLabel = 1
        var maxComp = 0
        for (i in 0 until total) {
            if (work[i] < 0.35f || labels[i] != 0) continue
            val lbl = nextLabel++
            var hd = 0
            var tl = 0
            labels[i] = lbl
            bfsQueue[tl++] = i
            while (hd < tl) {
                val cur = bfsQueue[hd++]
                val cx = cur % maskW
                val cy = cur / maskW
                if (cx > 0 && work[cur - 1] >= 0.35f && labels[cur - 1] == 0) { labels[cur - 1] = lbl; bfsQueue[tl++] = cur - 1 }
                if (cx < maskW - 1 && work[cur + 1] >= 0.35f && labels[cur + 1] == 0) { labels[cur + 1] = lbl; bfsQueue[tl++] = cur + 1 }
                if (cy > 0 && work[cur - maskW] >= 0.35f && labels[cur - maskW] == 0) { labels[cur - maskW] = lbl; bfsQueue[tl++] = cur - maskW }
                if (cy < maskH - 1 && work[cur + maskW] >= 0.35f && labels[cur + maskW] == 0) { labels[cur + maskW] = lbl; bfsQueue[tl++] = cur + maskW }
            }
            compSizes.add(tl)
            if (tl > maxComp) maxComp = tl
        }
        val minKeep = max(48, (maxComp * 0.05f).toInt())
        for (i in 0 until total) {
            val lbl = labels[i]
            if (lbl == 0 || compSizes[lbl] < minKeep) {
                if (work[i] < 0.90f || (lbl > 0 && compSizes[lbl] < minKeep)) {
                    work[i] = 0f
                }
            }
        }

        // Stage 4: Fast O(N) Guided Edge-Aware Alpha Matting guided by Layer 1 RGB luminance
        val guideLum = extractLuminance(uprightFullPhoto, maskW, maskH)
        val guided = guidedFilterAlpha(
            guide = guideLum,
            srcAlpha = work,
            w = maskW,
            h = maskH,
            radius = if (max(maskW, maskH) >= 720) 4 else 3,
            eps = 0.002f
        )

        // Stage 5: Final Clean Alpha + Manual Rapikan Kuas Override (ALWAYS wins!)
        val maskPixels = IntArray(total)
        for (i in 0 until total) {
            var a = guided[i].coerceIn(0f, 1f)
            a = when {
                a < 0.06f -> 0f
                a > 0.95f -> 1f
                else -> a
            }
            if (brushPixels != null) {
                val bp = brushPixels[i]
                val ba = (bp ushr 24) and 0xFF
                if (ba > 32) {
                    val br = (bp ushr 16) and 0xFF
                    val bg = (bp ushr 8) and 0xFF
                    a = if (bg > br) 1f else 0f
                }
            }
            val alphaInt = (a * 255f).roundToInt().coerceIn(0, 255)
            maskPixels[i] = if (alphaInt <= 4) 0x00000000 else (alphaInt shl 24) or 0x00FFFFFF
        }

        val alphaMaskBmp = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888).apply {
            setHasAlpha(true)
            setPixels(maskPixels, 0, maskW, 0, 0, maskW, maskH)
        }

        // Layer 3: Exact 1:1 pixel-aligned copy of Layer 1 with ONLY the background removed
        val cutoutBmp = Bitmap.createBitmap(
            uprightFullPhoto.width,
            uprightFullPhoto.height,
            Bitmap.Config.ARGB_8888
        ).apply {
            setHasAlpha(true)
        }
        val canvas = Canvas(cutoutBmp)
        canvas.drawBitmap(uprightFullPhoto, 0f, 0f, null)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        val dstRect = Rect(0, 0, uprightFullPhoto.width, uprightFullPhoto.height)
        canvas.drawBitmap(alphaMaskBmp, null, dstRect, maskPaint)

        return CutoutBundle(cutoutBitmap = cutoutBmp, alphaMaskBitmap = alphaMaskBmp)
    }

    private fun extractLuminance(photo: Bitmap, w: Int, h: Int): FloatArray {
        val scaled = if (photo.width != w || photo.height != h) {
            Bitmap.createScaledBitmap(photo, w, h, true)
        } else {
            photo
        }
        val total = w * h
        val px = IntArray(total)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== photo) scaled.recycle()
        val lum = FloatArray(total)
        for (i in 0 until total) {
            val c = px[i]
            val r = ((c ushr 16) and 0xFF) / 255f
            val g = ((c ushr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return lum
    }

    private fun boxMean(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        // Horizontal pass
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            var cnt = 0
            for (x in 0..min(r, w - 1)) {
                sum += src[row + x]
                cnt++
            }
            for (x in 0 until w) {
                tmp[row + x] = sum / cnt
                val addX = x + r + 1
                if (addX < w) { sum += src[row + addX]; cnt++ }
                val remX = x - r
                if (remX >= 0) { sum -= src[row + remX]; cnt-- }
            }
        }
        // Vertical pass
        for (x in 0 until w) {
            var sum = 0f
            var cnt = 0
            for (y in 0..min(r, h - 1)) {
                sum += tmp[y * w + x]
                cnt++
            }
            for (y in 0 until h) {
                out[y * w + x] = sum / cnt
                val addY = y + r + 1
                if (addY < h) { sum += tmp[addY * w + x]; cnt++ }
                val remY = y - r
                if (remY >= 0) { sum -= tmp[remY * w + x]; cnt-- }
            }
        }
        return out
    }

    private fun guidedFilterAlpha(
        guide: FloatArray,
        srcAlpha: FloatArray,
        w: Int,
        h: Int,
        radius: Int,
        eps: Float
    ): FloatArray {
        val total = w * h
        val meanI = boxMean(guide, w, h, radius)
        val meanP = boxMean(srcAlpha, w, h, radius)
        val ii = FloatArray(total) { guide[it] * guide[it] }
        val ip = FloatArray(total) { guide[it] * srcAlpha[it] }
        val corrI = boxMean(ii, w, h, radius)
        val corrIP = boxMean(ip, w, h, radius)

        val a = FloatArray(total)
        val b = FloatArray(total)
        for (i in 0 until total) {
            val varI = (corrI[i] - meanI[i] * meanI[i]).coerceAtLeast(0f)
            val covIP = corrIP[i] - meanI[i] * meanP[i]
            val ai = covIP / (varI + eps)
            a[i] = ai
            b[i] = meanP[i] - ai * meanI[i]
        }
        val meanA = boxMean(a, w, h, radius)
        val meanB = boxMean(b, w, h, radius)

        val result = FloatArray(total)
        for (i in 0 until total) {
            val orig = srcAlpha[i]
            // Only apply edge-snapping in boundary transition band; preserve solid interior & pure bg 100%!
            result[i] = when {
                orig >= 0.985f -> 1f
                orig <= 0.015f -> 0f
                else -> (meanA[i] * guide[i] + meanB[i]).coerceIn(0f, 1f)
            }
        }
        return result
    }

    /**
     * True Colored Inner-Rim Light Overlay (0% bleed outside subject!).
     */
    fun buildInnerRimLightOverlay(
        alphaMask: Bitmap,
        rimColor: Int,
        spreadPx: Float = 14f
    ): Bitmap? {
        if (alphaMask.isRecycled || alphaMask.width <= 0 || alphaMask.height <= 0) return null
        val maxSide = 512
        val longest = max(alphaMask.width, alphaMask.height)
        val workAlpha = if (longest > maxSide) {
            val scale = maxSide.toFloat() / longest.toFloat()
            val w = (alphaMask.width * scale).roundToInt().coerceAtLeast(8)
            val h = (alphaMask.height * scale).roundToInt().coerceAtLeast(8)
            Bitmap.createScaledBitmap(alphaMask, w, h, true)
        } else {
            alphaMask
        }

        val w = workAlpha.width
        val h = workAlpha.height
        val safeSpread = spreadPx.coerceIn(4f, 36f)

        val invertedMask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val invCanvas = Canvas(invertedMask)
        invCanvas.drawColor(Color.WHITE)
        val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        invCanvas.drawBitmap(workAlpha, 0f, 0f, erasePaint)

        val rimOut = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rimCanvas = Canvas(rimOut)
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            maskFilter = BlurMaskFilter(safeSpread, BlurMaskFilter.Blur.NORMAL)
            colorFilter = PorterDuffColorFilter(rimColor, PorterDuff.Mode.SRC_IN)
        }
        rimCanvas.drawBitmap(invertedMask, 0f, 0f, blurPaint)

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            maskFilter = BlurMaskFilter((safeSpread * 0.42f).coerceAtLeast(2.5f), BlurMaskFilter.Blur.NORMAL)
            colorFilter = PorterDuffColorFilter(blendTowardWhite(rimColor, 0.35f), PorterDuff.Mode.SRC_IN)
            alpha = 185
        }
        rimCanvas.drawBitmap(invertedMask, 0f, 0f, corePaint)

        val clipInPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        rimCanvas.drawBitmap(workAlpha, 0f, 0f, clipInPaint)

        invertedMask.recycle()
        if (workAlpha !== alphaMask) workAlpha.recycle()

        return rimOut
    }

    private fun blendTowardWhite(argb: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(argb) * inv + 255f * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(argb) * inv + 255f * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(argb) * inv + 255f * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private suspend fun <T> Task<T>.awaitSafe(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res -> if (cont.isActive) cont.resume(res) }
        addOnFailureListener { if (cont.isActive) cont.resume(null) }
        addOnCanceledListener { if (cont.isActive) cont.resume(null) }
    }
}
