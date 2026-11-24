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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

data class DepthCutoutResult(
    val rawConfidence: FloatArray,
    val maskWidth: Int,
    val maskHeight: Int,
    val coverage: Float,
    val hasSubject: Boolean
)

data class CutoutBundle(
    val cutoutBitmap: Bitmap,
    val alphaMaskBitmap: Bitmap
)

/**
 * 100% Offline Subject/Human Background Cutout & Colored Inner Rim Light Engine
 * using Google ML Kit Selfie Segmentation (16.0.0-beta6).
 * - Guarantees EXIF-upright photo decoding (max 1600px, ARGB_8888)
 * - Preserves raw float confidence map so Sensitivity & Edge Feather sliders re-render instantaneously
 * - Supports interactive finger Manual Brush Mask (+ Foreground / - Background)
 * - Copies full-resolution upright subject onto 100% transparent canvas via PorterDuff.Mode.DST_IN
 * - Generates true inner-only Colored Rim Light glow hugging subject edges without bleeding outside
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

            // Step 1: Read bounds only
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

            // Step 2: Decode safely into ARGB_8888
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext null

            // Step 3: Apply exact EXIF rotation/flip + cap to maxSide
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

    suspend fun segmentSubject(uprightFullPhoto: Bitmap): DepthCutoutResult =
        withContext(Dispatchers.Default) {
            val maxWorkSide = 768
            val longest = max(uprightFullPhoto.width, uprightFullPhoto.height)
            val workBmp = if (longest > maxWorkSide) {
                val scale = maxWorkSide.toFloat() / longest.toFloat()
                val w = (uprightFullPhoto.width * scale).roundToInt().coerceAtLeast(1)
                val h = (uprightFullPhoto.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(uprightFullPhoto, w, h, true)
            } else {
                uprightFullPhoto
            }

            val options = SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()
            val segmenter = Segmentation.getClient(options)

            try {
                val inputImage = InputImage.fromBitmap(workBmp, 0)
                val mask = suspendCancellableCoroutine { cont ->
                    segmenter.process(inputImage)
                        .addOnSuccessListener { result ->
                            if (cont.isActive) cont.resume(result)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                }

                if (mask == null) {
                    return@withContext DepthCutoutResult(FloatArray(0), 0, 0, 0f, false)
                }

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
                val hasSubject = coverage in 0.03f..0.96f
                DepthCutoutResult(
                    rawConfidence = conf,
                    maskWidth = maskW,
                    maskHeight = maskH,
                    coverage = coverage,
                    hasSubject = hasSubject
                )
            } catch (_: Throwable) {
                DepthCutoutResult(FloatArray(0), 0, 0, 0f, false)
            } finally {
                try { segmenter.close() } catch (_: Throwable) {}
                if (workBmp !== uprightFullPhoto) workBmp.recycle()
            }
        }

    /**
     * Builds Full-HD transparent subject cutout AND retains the lightweight alphaMaskBitmap
     * so Colored Inner Rim Light can be rebuilt instantaneously without re-running ML Kit.
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
        val featherSpan = 0.02f + edgeFeather.coerceIn(0f, 1f) * 0.24f
        val low = (sens - featherSpan).coerceAtLeast(0.01f)
        val high = (sens + featherSpan).coerceAtMost(0.99f)
        val invRange = 1f / (high - low).coerceAtLeast(0.01f)

        val total = maskW * maskH
        val maskPixels = IntArray(total)

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

        for (i in 0 until total) {
            val c = rawConfidence[i]
            var alpha = when {
                c <= low -> 0
                c >= high -> 255
                else -> {
                    val t = ((c - low) * invRange).coerceIn(0f, 1f)
                    val smooth = t * t * (3f - 2f * t)
                    (smooth * 255f).roundToInt().coerceIn(0, 255)
                }
            }

            if (brushPixels != null) {
                val bp = brushPixels[i]
                val ba = (bp ushr 24) and 0xFF
                if (ba > 32) {
                    val br = (bp ushr 16) and 0xFF
                    val bg = (bp ushr 8) and 0xFF
                    alpha = if (bg > br) 255 else 0
                }
            }

            maskPixels[i] = (alpha shl 24) or 0x00FFFFFF
        }

        val alphaMaskBmp = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888)
        alphaMaskBmp.setPixels(maskPixels, 0, maskW, 0, 0, maskW, maskH)

        val cutoutBmp = Bitmap.createBitmap(
            uprightFullPhoto.width,
            uprightFullPhoto.height,
            Bitmap.Config.ARGB_8888
        )
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
     * Upgrade #1: True Colored Inner-Rim Light Overlay (0% bleed outside subject!).
     * 1. Downscale alphaMask to <= 512px preserving exact aspect ratio.
     * 2. Build invertedMask (outside body = solid white alpha 255, inside body = transparent alpha 0).
     * 3. Blur invertedMask softly on software canvas & tint with rimColor via PorterDuffColorFilter(SRC_IN).
     * 4. Clip strictly inside the subject with original workAlpha via PorterDuffXfermode(DST_IN).
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

        // Step 1: Inverted mask (solid white outside body, transparent inside body)
        val invertedMask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val invCanvas = Canvas(invertedMask)
        invCanvas.drawColor(Color.WHITE)
        val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        invCanvas.drawBitmap(workAlpha, 0f, 0f, erasePaint)

        // Step 2: Softly blurred colored halo flowing inward from the inverted outside region
        val rimOut = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val rimCanvas = Canvas(rimOut)
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            maskFilter = BlurMaskFilter(safeSpread, BlurMaskFilter.Blur.NORMAL)
            colorFilter = PorterDuffColorFilter(rimColor, PorterDuff.Mode.SRC_IN)
        }
        // Draw slightly expanded inverted border glow so edges near canvas boundaries glow evenly
        rimCanvas.drawBitmap(invertedMask, 0f, 0f, blurPaint)
        // Second tighter core pass for crisp specular edge catch right along hair/cheeks/shoulders
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            maskFilter = BlurMaskFilter((safeSpread * 0.42f).coerceAtLeast(2.5f), BlurMaskFilter.Blur.NORMAL)
            colorFilter = PorterDuffColorFilter(blendTowardWhite(rimColor, 0.35f), PorterDuff.Mode.SRC_IN)
            alpha = 185
        }
        rimCanvas.drawBitmap(invertedMask, 0f, 0f, corePaint)

        // Step 3: Clip strictly INSIDE subject silhouette (0% outside bleed!)
        val clipInPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        rimCanvas.drawBitmap(workAlpha, 0f, 0f, clipInPaint)

        // Immediately recycle temporary bitmaps
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
}
