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
        val maxWorkSide = 640
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
        return withTimeoutOrNull(25_000L) {
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
     * Builds Full-HD 1:1 transparent subject cutout + alphaMaskBitmap with 4-Stage Studio Matte Refinement:
     * 1. Smoothstep S-curve + manual brush overrides
     * 2. 3x3 Morphological Speckle & Interior Pinhole Cleanup (solid body, zero card bleed-through!)
     * 3. Connected-Component Solid Filter (removes stray shredded scraps < 3.5% of main object!)
     * 4. 1-Pass Border-Only Anti-Aliasing + exact (uprightFullPhoto.width, uprightFullPhoto.height) ARGB_8888
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
        val featherSpan = 0.03f + edgeFeather.coerceIn(0f, 1f) * 0.22f
        val low = (sens - featherSpan).coerceAtLeast(0.04f)
        val high = (sens + featherSpan).coerceAtMost(0.96f)
        val invRange = 1f / (high - low).coerceAtLeast(0.01f)
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

        // Stage 1: Smoothstep S-curve + manual brush
        val work = FloatArray(total)
        for (i in 0 until total) {
            val c = rawConfidence[i]
            var a = when {
                c <= low -> 0f
                c >= high -> 1f
                else -> {
                    val t = ((c - low) * invRange).coerceIn(0f, 1f)
                    t * t * (3f - 2f * t)
                }
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
            work[i] = a
        }

        // Stage 2: 3x3 Morphological Speckle & Pinhole Cleanup
        val cleaned = FloatArray(total)
        for (y in 0 until maskH) {
            val y0 = (y - 1).coerceAtLeast(0)
            val y1 = (y + 1).coerceAtMost(maskH - 1)
            for (x in 0 until maskW) {
                val idx = y * maskW + x
                val cur = work[idx]
                val x0 = (x - 1).coerceAtLeast(0)
                val x1 = (x + 1).coerceAtMost(maskW - 1)
                var highN = 0
                var sum = 0f
                for (ny in y0..y1) {
                    val nr = ny * maskW
                    for (nx in x0..x1) {
                        val v = work[nr + nx]
                        sum += v
                        if (v >= 0.55f) highN++
                    }
                }
                cleaned[idx] = when {
                    cur > 0.10f && highN <= 2 -> 0f
                    cur < 0.85f && highN >= 7 -> 1f
                    cur in 0.04f..0.96f -> (((sum / 9f) - 0.18f) / 0.64f).coerceIn(0f, 1f)
                    else -> cur
                }
            }
        }

        // Stage 3: Connected-Component Solid Filter (kills disconnected floating scraps!)
        val labels = IntArray(total)
        val queue = IntArray(total)
        val compSizes = ArrayList<Int>(64).apply { add(0) }
        var nextLabel = 1
        var maxComp = 0
        for (i in 0 until total) {
            if (cleaned[i] < 0.45f || labels[i] != 0) continue
            val lbl = nextLabel++
            var hd = 0
            var tl = 0
            labels[i] = lbl
            queue[tl++] = i
            while (hd < tl) {
                val cur = queue[hd++]
                val cx = cur % maskW
                val cy = cur / maskW
                if (cx > 0 && cleaned[cur - 1] >= 0.45f && labels[cur - 1] == 0) { labels[cur - 1] = lbl; queue[tl++] = cur - 1 }
                if (cx < maskW - 1 && cleaned[cur + 1] >= 0.45f && labels[cur + 1] == 0) { labels[cur + 1] = lbl; queue[tl++] = cur + 1 }
                if (cy > 0 && cleaned[cur - maskW] >= 0.45f && labels[cur - maskW] == 0) { labels[cur - maskW] = lbl; queue[tl++] = cur - maskW }
                if (cy < maskH - 1 && cleaned[cur + maskW] >= 0.45f && labels[cur + maskW] == 0) { labels[cur + maskW] = lbl; queue[tl++] = cur + maskW }
            }
            compSizes.add(tl)
            if (tl > maxComp) maxComp = tl
        }
        val minKeep = max(24, (maxComp * 0.035f).toInt())
        for (i in 0 until total) {
            val lbl = labels[i]
            if (lbl > 0 && compSizes[lbl] < minKeep) cleaned[i] = 0f
        }

        // Stage 4: 1-Pass Border-Only Anti-Aliasing
        val maskPixels = IntArray(total)
        for (y in 0 until maskH) {
            val yp = (y - 1).coerceAtLeast(0)
            val yn = (y + 1).coerceAtMost(maskH - 1)
            val row = y * maskW
            for (x in 0 until maskW) {
                val i = row + x
                val c = cleaned[i]
                val up = cleaned[yp * maskW + x]
                val dn = cleaned[yn * maskW + x]
                val lf = cleaned[row + (x - 1).coerceAtLeast(0)]
                val rt = cleaned[row + (x + 1).coerceAtMost(maskW - 1)]
                val v = if (c >= 0.99f && min(min(up, dn), min(lf, rt)) >= 0.99f) {
                    1f
                } else if (c <= 0.01f && max(max(up, dn), max(lf, rt)) <= 0.01f) {
                    0f
                } else {
                    val b = c * 0.46f + (up + dn + lf + rt) * 0.135f
                    when {
                        b < 0.06f -> 0f
                        b > 0.94f -> 1f
                        else -> b
                    }
                }
                val alphaInt = (v * 255f).roundToInt().coerceIn(0, 255)
                maskPixels[i] = if (alphaInt <= 4) 0x00000000 else (alphaInt shl 24) or 0x00FFFFFF
            }
        }

        val alphaMaskBmp = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888).apply {
            setHasAlpha(true)
            setPixels(maskPixels, 0, maskW, 0, 0, maskW, maskH)
        }

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
