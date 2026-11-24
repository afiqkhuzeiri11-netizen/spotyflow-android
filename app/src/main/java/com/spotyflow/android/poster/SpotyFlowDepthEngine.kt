package com.spotyflow.android.poster

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DepthCutoutResult(
    val rawConfidence: FloatArray,
    val maskWidth: Int,
    val maskHeight: Int,
    val coverage: Float,
    val hasSubject: Boolean,
    val engineLabel: String = "BRIA RMBG-1.4 HD"
)

data class CutoutBundle(
    val cutoutBitmap: Bitmap,
    val alphaMaskBitmap: Bitmap
)

/**
 * Super-Accurate Auralys-Style Background Remover (v2.9.0-native):
 * - Layer 1 = Original Full Photo
 * - Layer 2 = 3D Music Cards
 * - Layer 3 = Exact Layer 1 photo with ONLY the background cleanly erased (1:1 pixel-aligned)!
 *
 * Engine Hierarchy:
 * 1. Primary: BRIA RMBG-1.4 (onnx/model_quantized.onnx, 44MB downloaded ONCE & cached forever)
 *    running natively via ONNX Runtime C++ (4 threads) -> Studio remove.bg / Auralys accuracy!
 * 2. Fallback: Google ML Kit Subject Segmentation (1024px)
 * 3. Offline Fallback: ML Kit Selfie Segmentation
 */
object SpotyFlowDepthEngine {

    private const val RMBG_MODEL_URL =
        "https://huggingface.co/briaai/RMBG-1.4/resolve/main/onnx/model_quantized.onnx"
    private const val RMBG_FILE_NAME = "bria_rmbg_14_q8.onnx"
    private const val RMBG_INPUT_SIZE = 1024

    @Volatile
    private var cachedOrtSession: OrtSession? = null

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

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
     * Super-Accurate Neural Background Removal (Primary: BRIA RMBG-1.4 Native ONNX -> Fallback: ML Kit Subject AI).
     */
    suspend fun segmentSubject(
        context: Context,
        uprightFullPhoto: Bitmap,
        onStatus: (String) -> Unit = {}
    ): DepthCutoutResult = withContext(Dispatchers.Default) {
        // Tier 1: BRIA RMBG-1.4 Native C++ ONNX (Studio-Grade Full-Scene Background Removal!)
        val rmbgRes = tryBriaRmbgOnnx(context, uprightFullPhoto, onStatus)
        if (rmbgRes != null && rmbgRes.hasSubject) {
            return@withContext rmbgRes
        }

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
            // Tier 2: Google ML Kit Subject Segmentation
            val subjectRes = trySubjectSegmentation(context, workBmp, onStatus)
            if (subjectRes != null && subjectRes.hasSubject) {
                return@withContext subjectRes
            }

            // Tier 3: Offline ML Kit Selfie Segmentation Fallback
            onStatus("Memproses AI Potong Offline...")
            trySelfieSegmentation(workBmp)
        } finally {
            if (workBmp !== uprightFullPhoto) workBmp.recycle()
        }
    }

    private suspend fun ensureRmbgModelFile(
        context: Context,
        onStatus: (String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, RMBG_FILE_NAME)
        if (target.exists() && target.length() > 35_000_000L) {
            return@withContext target
        }
        val tmp = File(context.filesDir, "$RMBG_FILE_NAME.tmp")
        try {
            onStatus("Mengunduh AI BRIA RMBG-1.4 (sekali saja)...")
            val req = Request.Builder().url(RMBG_MODEL_URL).build()
            downloadClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val totalBytes = body.contentLength().coerceAtLeast(1L)
                var downloaded = 0L
                var lastPct = -1
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            output.write(buf, 0, read)
                            downloaded += read
                            val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 99)
                            if (pct != lastPct && pct % 4 == 0) {
                                lastPct = pct
                                onStatus("Mengunduh AI BRIA RMBG-1.4 ($pct%)...")
                            }
                        }
                        output.flush()
                    }
                }
            }
            if (tmp.length() > 35_000_000L) {
                if (target.exists()) target.delete()
                if (tmp.renameTo(target)) target else null
            } else {
                tmp.delete()
                null
            }
        } catch (_: Throwable) {
            try { tmp.delete() } catch (_: Throwable) {}
            null
        }
    }

    private suspend fun tryBriaRmbgOnnx(
        context: Context,
        uprightFullPhoto: Bitmap,
        onStatus: (String) -> Unit
    ): DepthCutoutResult? {
        val modelFile = ensureRmbgModelFile(context, onStatus) ?: return null
        return try {
            onStatus("Menghapus latar belakang (BRIA RMBG-1.4 HD)...")
            val env = OrtEnvironment.getEnvironment()
            val session = cachedOrtSession ?: synchronized(this) {
                cachedOrtSession ?: run {
                    val opts = OrtSession.SessionOptions().apply {
                        setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
                        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    }
                    env.createSession(modelFile.absolutePath, opts).also { cachedOrtSession = it }
                }
            }

            // Official BRIA RMBG-1.4 preprocessing: 1024x1024, RGB/255 - 0.5 (mean=0.5, std=1.0), NCHW
            val size = RMBG_INPUT_SIZE
            val resized = Bitmap.createScaledBitmap(uprightFullPhoto, size, size, true)
            val totalSq = size * size
            val pixels = IntArray(totalSq)
            resized.getPixels(pixels, 0, size, 0, 0, size, size)
            if (resized !== uprightFullPhoto) resized.recycle()

            val nchw = FloatArray(3 * totalSq)
            val inv255 = 1f / 255f
            for (i in 0 until totalSq) {
                val c = pixels[i]
                nchw[i] = ((c ushr 16) and 0xFF) * inv255 - 0.5f
                nchw[totalSq + i] = ((c ushr 8) and 0xFF) * inv255 - 0.5f
                nchw[2 * totalSq + i] = (c and 0xFF) * inv255 - 0.5f
            }

            val inputName = session.inputNames.iterator().next()
            val rawOut = FloatArray(totalSq)
            OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(nchw),
                longArrayOf(1L, 3L, size.toLong(), size.toLong())
            ).use { inputTensor ->
                session.run(mapOf(inputName to inputTensor)).use { result ->
                    val outTensor = result[0] as OnnxTensor
                    val fb = outTensor.floatBuffer
                    fb.rewind()
                    if (fb.remaining() < totalSq) return null
                    fb.get(rawOut)
                }
            }

            // Official BRIA min-max normalization
            var mn = Float.POSITIVE_INFINITY
            var mx = Float.NEGATIVE_INFINITY
            for (i in 0 until totalSq) {
                val v = rawOut[i]
                if (v < mn) mn = v
                if (v > mx) mx = v
            }
            val invRange = 1f / (mx - mn).coerceAtLeast(1e-5f)

            // Resample 1024x1024 square probability map back to photo's exact aspect ratio (max side 1024)
            val longest = max(uprightFullPhoto.width, uprightFullPhoto.height)
            val sc = min(1f, 1024f / longest.toFloat())
            val outW = (uprightFullPhoto.width * sc).roundToInt().coerceAtLeast(64)
            val outH = (uprightFullPhoto.height * sc).roundToInt().coerceAtLeast(64)
            val outTotal = outW * outH
            val conf = FloatArray(outTotal)
            var fgCount = 0

            val scaleX = (size - 1).toFloat() / (outW - 1).coerceAtLeast(1).toFloat()
            val scaleY = (size - 1).toFloat() / (outH - 1).coerceAtLeast(1).toFloat()
            for (y in 0 until outH) {
                val sy = y * scaleY
                val y0 = sy.toInt().coerceIn(0, size - 1)
                val y1 = (y0 + 1).coerceAtMost(size - 1)
                val fy = sy - y0
                val row0 = y0 * size
                val row1 = y1 * size
                val dstRow = y * outW
                for (x in 0 until outW) {
                    val sx = x * scaleX
                    val x0 = sx.toInt().coerceIn(0, size - 1)
                    val x1 = (x0 + 1).coerceAtMost(size - 1)
                    val fx = sx - x0
                    val v00 = rawOut[row0 + x0]
                    val v10 = rawOut[row0 + x1]
                    val v01 = rawOut[row1 + x0]
                    val v11 = rawOut[row1 + x1]
                    val top = v00 + (v10 - v00) * fx
                    val bot = v01 + (v11 - v01) * fx
                    val v = (((top + (bot - top) * fy) - mn) * invRange).coerceIn(0f, 1f)
                    conf[dstRow + x] = v
                    if (v > 0.5f) fgCount++
                }
            }

            val cov = fgCount.toFloat() / outTotal.coerceAtLeast(1).toFloat()
            if (cov in 0.005f..0.99f) {
                DepthCutoutResult(
                    rawConfidence = conf,
                    maskWidth = outW,
                    maskHeight = outH,
                    coverage = cov,
                    hasSubject = true,
                    engineLabel = "BRIA RMBG-1.4 HD"
                )
            } else {
                null
            }
        } catch (_: Throwable) {
            null
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
                    if (cov in 0.015f..0.97f) {
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
            if (cov in 0.015f..0.97f) {
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
     * Builds Full-HD 1:1 transparent subject cutout + alphaMaskBitmap directly from pure neural alpha:
     * - ZERO destructive border flood-purge and ZERO blurring guided filter!
     * - At neutral sensitivity (0.48..0.52): exact BRIA RMBG-1.4 alpha with crisp background floor (<8% -> 0)
     *   and solid interior (>94% -> 1).
     * - Manual Rapikan Kuas brush always overrides cleanly on top.
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

        val isNeutral = abs(sens - 0.50f) < 0.035f
        val span = 0.04f + feather * 0.18f
        val low = (sens - span).coerceIn(0.03f, 0.82f)
        val high = (sens + span).coerceIn(low + 0.05f, 0.97f)
        val invRange = 1f / (high - low).coerceAtLeast(0.01f)

        val maskPixels = IntArray(total)
        for (i in 0 until total) {
            var a = rawConfidence[i].coerceIn(0f, 1f)
            if (isNeutral) {
                a = when {
                    a <= 0.08f -> 0f
                    a >= 0.94f -> 1f
                    else -> (a - 0.08f) / 0.86f
                }
            } else {
                a = when {
                    a <= low -> 0f
                    a >= high -> 1f
                    else -> {
                        val t = ((a - low) * invRange).coerceIn(0f, 1f)
                        t * t * (3f - 2f * t)
                    }
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
