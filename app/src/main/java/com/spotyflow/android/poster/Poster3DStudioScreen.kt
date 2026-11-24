package com.spotyflow.android.poster

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.LyricLine
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.remote.LyricsRepository
import com.spotyflow.android.data.remote.SpotyHttpClient
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.glass.SpotyGlassCircleButton
import com.spotyflow.android.ui.theme.SpotyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.random.Random

private enum class PosterTab(val label: String) {
    PHOTO("Foto"),
    CARDS("Kartu"),
    CARD_STYLE("Gaya kartu"),
    EFFECTS("Efek"),
    STICKERS("Stiker")
}

private sealed interface SelectedPosterObject {
    data class Card(val index: Int) : SelectedPosterObject
    data class Sticker(val id: String) : SelectedPosterObject
    data object DepthText : SelectedPosterObject
}

private data class BrushStroke(
    val points: List<Pair<Float, Float>>,
    val addForeground: Boolean,
    val radiusFrac: Float
)

@Composable
fun Poster3DStudioScreen(
    uiState: PlayerUiState,
    initialTrack: TrackItem? = null,
    onBack: () -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    val initialTracks = remember(initialTrack?.id, uiState.currentTrack?.id) {
        val base = listOfNotNull(initialTrack ?: uiState.currentTrack)
        if (base.isNotEmpty()) base else uiState.homeTracks.take(1)
    }

    var selectedTracks by remember { mutableStateOf(initialTracks) }
    var config by remember {
        mutableStateOf(
            PosterStudioConfig(
                cards = Poster3DRenderer.defaultCardsForFormation(initialTracks, PosterFormation.MIRROR_SHOULDERS),
                depthText = DepthTypographyConfig(
                    enabled = false,
                    text = initialTracks.firstOrNull()?.title?.uppercase() ?: "SPOTYFLOW"
                )
            )
        )
    }

    var activeTab by remember { mutableStateOf(PosterTab.PHOTO) }
    var activePreset by remember { mutableStateOf<PosterProPreset?>(null) }
    var selectedObject by remember { mutableStateOf<SelectedPosterObject>(SelectedPosterObject.Card(0)) }

    var showSongPicker by remember { mutableStateOf(false) }
    var showLyricPicker by remember { mutableStateOf(false) }
    var showCustomTextDialog by remember { mutableStateOf(false) }
    var customTextDraft by remember { mutableStateOf("") }
    var fetchedLyricLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var isFetchingLyrics by remember { mutableStateOf(false) }

    var showCutoutPreview by remember { mutableStateOf(false) }
    var isBrushMode by remember { mutableStateOf(false) }
    var brushAddForeground by remember { mutableStateOf(true) }
    var brushStrokes by remember { mutableStateOf<List<BrushStroke>>(emptyList()) }
    var currentStrokePoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var isSegmenting by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    var uprightPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var depthResult by remember { mutableStateOf<DepthCutoutResult?>(null) }
    var cutoutBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var alphaMaskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rimLightOverlayBmp by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var coverBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var customStickerFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var customStickerBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var previewBoxSize by remember { mutableStateOf(IntSize(540, 960)) }

    val dominantAccentArgb = selectedTracks.firstOrNull()?.accentColor?.toArgb()
        ?: uiState.ambientPalette.accent.toArgb()

    // Release cached flat card bitmaps cleanly on exit
    DisposableEffect(Unit) {
        onDispose {
            Poster3DRenderer.clearFlatCardCache()
        }
    }

    // Load custom stickers from filesDir/custom_stickers
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "custom_stickers").apply { mkdirs() }
            val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
            val bmps = mutableMapOf<String, Bitmap>()
            files.forEach { f ->
                BitmapFactory.decodeFile(f.absolutePath)?.let { bmps[f.absolutePath] = it }
            }
            customStickerFiles = files
            customStickerBitmaps = bmps
        }
    }

    // Download cover bitmaps for selectedTracks
    LaunchedEffect(selectedTracks) {
        withContext(Dispatchers.IO) {
            val updated = coverBitmaps.toMutableMap()
            selectedTracks.forEach { t ->
                if (!updated.containsKey(t.id) && t.coverUrl.isNotBlank()) {
                    try {
                        val req = Request.Builder().url(t.coverUrl).get().build()
                        SpotyHttpClient.client.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val bytes = resp.body?.bytes()
                                if (bytes != null) {
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let {
                                        updated[t.id] = it
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }
            coverBitmaps = updated
        }
    }

    // Rebuild Full-HD transparent cutout & alphaMask when sensitivity / edgeFeather / manual brush changes
    LaunchedEffect(uprightPhoto, depthResult, config.sensitivity, config.edgeFeather, brushStrokes) {
        val photo = uprightPhoto ?: return@LaunchedEffect
        val seg = depthResult ?: return@LaunchedEffect
        if (seg.maskWidth <= 0 || seg.maskHeight <= 0) return@LaunchedEffect

        val bundle = withContext(Dispatchers.Default) {
            val brushBmp = if (brushStrokes.isNotEmpty()) {
                val b = Bitmap.createBitmap(seg.maskWidth, seg.maskHeight, Bitmap.Config.ARGB_8888)
                val c = AndroidCanvas(b)
                brushStrokes.forEach { stroke ->
                    val p = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        color = if (stroke.addForeground) android.graphics.Color.argb(255, 0, 255, 0) else android.graphics.Color.argb(255, 255, 0, 0)
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = stroke.radiusFrac * seg.maskWidth
                        strokeCap = AndroidPaint.Cap.ROUND
                        strokeJoin = AndroidPaint.Join.ROUND
                    }
                    if (stroke.points.size == 1) {
                        val pt = stroke.points.first()
                        c.drawCircle(pt.first * seg.maskWidth, pt.second * seg.maskHeight, p.strokeWidth * 0.5f, AndroidPaint(p).apply { style = AndroidPaint.Style.FILL })
                    } else {
                        for (i in 1 until stroke.points.size) {
                            val a = stroke.points[i - 1]
                            val d = stroke.points[i]
                            c.drawLine(a.first * seg.maskWidth, a.second * seg.maskHeight, d.first * seg.maskWidth, d.second * seg.maskHeight, p)
                        }
                    }
                }
                b
            } else {
                null
            }
            val res = SpotyFlowDepthEngine.buildTransparentObjectCutoutWithMask(
                uprightFullPhoto = photo,
                rawConfidence = seg.rawConfidence,
                maskW = seg.maskWidth,
                maskH = seg.maskHeight,
                sensitivity = config.sensitivity,
                edgeFeather = config.edgeFeather,
                manualBrushMask = brushBmp
            )
            brushBmp?.recycle()
            res
        }
        cutoutBitmap = bundle?.cutoutBitmap
        alphaMaskBitmap = bundle?.alphaMaskBitmap
    }

    // Upgrade #1: Cached Colored Inner Rim Light overlay — rebuilt ONLY when alphaMask or accent color changes!
    LaunchedEffect(alphaMaskBitmap, dominantAccentArgb) {
        val alpha = alphaMaskBitmap
        if (alpha == null || alpha.isRecycled) {
            rimLightOverlayBmp = null
            return@LaunchedEffect
        }
        val built = withContext(Dispatchers.Default) {
            SpotyFlowDepthEngine.buildInnerRimLightOverlay(
                alphaMask = alpha,
                rimColor = dominantAccentArgb,
                spreadPx = 15f
            )
        }
        rimLightOverlayBmp = built
    }

    // Re-render live preview bitmap off main thread (flat cards & rim light are cached!)
    LaunchedEffect(
        config,
        selectedTracks,
        coverBitmaps,
        uprightPhoto,
        cutoutBitmap,
        rimLightOverlayBmp,
        showCutoutPreview,
        customStickerBitmaps
    ) {
        val ratio = config.aspectRatio
        val prevW = if (ratio.exportW >= ratio.exportH) 640 else 480
        val prevH = ((prevW.toFloat() * ratio.exportH.toFloat()) / ratio.exportW.toFloat()).toInt().coerceAtLeast(240)

        val rendered = withContext(Dispatchers.Default) {
            if (showCutoutPreview && uprightPhoto != null) {
                Poster3DRenderer.renderCheckerboardCutoutPreview(prevW, prevH, uprightPhoto!!, cutoutBitmap, config)
            } else {
                Poster3DRenderer.renderPoster(
                    canvasW = prevW,
                    canvasH = prevH,
                    config = config,
                    tracks = selectedTracks,
                    coverBitmaps = coverBitmaps,
                    customStickerBitmaps = customStickerBitmaps,
                    uprightFullPhoto = uprightPhoto,
                    cutoutBmp = cutoutBitmap,
                    rimLightOverlayBmp = rimLightOverlayBmp,
                    dominantAccentArgb = dominantAccentArgb
                )
            }
        }
        previewBitmap = rendered
    }

    // Pick Photo Launcher -> EXIF-upright decode + Offline ML Kit Selfie Segmentation
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isSegmenting = true
            brushStrokes = emptyList()
            val photo = SpotyFlowDepthEngine.loadUprightPhoto(context, uri, maxSide = 1600)
            if (photo != null) {
                uprightPhoto = photo
                val seg = SpotyFlowDepthEngine.segmentSubject(context, photo)
                depthResult = seg
                val uBox = seg.unionBox
                val updatedCards = if (uBox != null && uBox.size == 4 && config.cards.isNotEmpty()) {
                    val pw = config.aspectRatio.exportW.toFloat()
                    val ph = config.aspectRatio.exportH.toFloat()
                    val iw = photo.width.toFloat().coerceAtLeast(1f)
                    val ih = photo.height.toFloat().coerceAtLeast(1f)
                    val baseScale = maxOf(pw / iw, ph / ih) * config.photoZoom.coerceIn(0.8f, 2.5f)
                    val sw = iw * baseScale
                    val sh = ih * baseScale
                    val dx = (pw - sw) * 0.5f + config.photoPanX * pw * 0.35f
                    val dy = (ph - sh) * 0.5f + config.photoPanY * ph * 0.35f
                    fun mapX(v: Int) = ((dx + (v / 1000f) * sw) / pw).coerceIn(0.06f, 0.94f)
                    fun mapY(v: Int) = ((dy + (v / 1000f) * sh) / ph).coerceIn(0.06f, 0.94f)
                    val yMin = mapY(uBox[0])
                    val xMin = mapX(uBox[1])
                    val yMax = mapY(uBox[2])
                    val xMax = mapX(uBox[3])
                    val cx = (xMin + xMax) * 0.5f
                    val objW = (xMax - xMin).coerceAtLeast(0.16f)
                    val objH = (yMax - yMin).coerceAtLeast(0.18f)
                    val leftX = (xMin + objW * 0.04f - 0.07f).coerceIn(0.19f, 0.42f)
                    val rightX = (xMax - objW * 0.04f + 0.07f).coerceIn(0.58f, 0.81f)
                    val topY = (yMin + objH * 0.24f).coerceIn(0.24f, 0.48f)
                    val midY = (yMin + objH * 0.56f).coerceIn(0.45f, 0.70f)
                    val crownY = (yMin + objH * 0.06f).coerceIn(0.20f, 0.38f)
                    val slots = listOf(
                        floatArrayOf(leftX, topY, 20f, 6f, -11f, 0.92f),
                        floatArrayOf(rightX, (topY + 0.04f).coerceIn(0.24f, 0.52f), -20f, 6f, 11f, 0.92f),
                        floatArrayOf((leftX - 0.02f).coerceIn(0.17f, 0.40f), midY, 16f, -5f, -7f, 0.84f),
                        floatArrayOf((rightX + 0.02f).coerceIn(0.60f, 0.83f), midY, -16f, -5f, 8f, 0.84f),
                        floatArrayOf(cx.coerceIn(0.36f, 0.64f), crownY, 0f, 9f, -2f, 0.85f)
                    )
                    config.cards.mapIndexed { idx, card ->
                        val sl = slots[idx % slots.size]
                        card.copy(
                            centerXFrac = sl[0],
                            centerYFrac = sl[1],
                            rotateYDeg = sl[2],
                            rotateXDeg = sl[3],
                            rotateZDeg = sl[4],
                            scale = sl[5],
                            inFrontOfSubject = false
                        )
                    }
                } else {
                    config.cards.map { it.copy(inFrontOfSubject = false) }
                }
                config = config.copy(depthEnabled = seg.hasSubject, cards = updatedCards)
                onShowToast(
                    if (seg.hasSubject) "Objek dipotong rapi & sampul diatur di belakang objek"
                    else "Foto dipasang (gunakan Kuas jika ingin memotong objek)"
                )
            } else {
                onShowToast("Gagal memuat foto")
            }
            isSegmenting = false
        }
    }

    // Upload Custom PNG Sticker Launcher
    val stickerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val savedFile = withContext(Dispatchers.IO) {
                try {
                    val dir = File(context.filesDir, "custom_stickers").apply { mkdirs() }
                    val f = File(dir, "stk_${System.currentTimeMillis()}.png")
                    context.contentResolver.openInputStream(uri)?.use { inp ->
                        FileOutputStream(f).use { out -> inp.copyTo(out) }
                    }
                    f
                } catch (_: Throwable) {
                    null
                }
            }
            if (savedFile != null) {
                val bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(savedFile.absolutePath) }
                if (bmp != null) {
                    customStickerFiles = listOf(savedFile) + customStickerFiles
                    customStickerBitmaps = customStickerBitmaps + (savedFile.absolutePath to bmp)
                    if (config.stickers.size < 30) {
                        val st = PosterStickerItem(
                            id = "stk_${System.currentTimeMillis()}",
                            kind = null,
                            customFilePath = savedFile.absolutePath,
                            xFrac = 0.5f,
                            yFrac = 0.25f
                        )
                        config = config.copy(stickers = config.stickers + st)
                        selectedObject = SelectedPosterObject.Sticker(st.id)
                    }
                    onShowToast("Stiker kustom ditambahkan")
                }
            }
        }
    }

    fun openLyricLinePicker() {
        val lead = selectedTracks.firstOrNull() ?: return
        showLyricPicker = true
        if (uiState.currentTrack?.id == lead.id && uiState.lyricsResult.activeList.isNotEmpty()) {
            fetchedLyricLines = uiState.lyricsResult.activeList
            return
        }
        isFetchingLyrics = true
        scope.launch {
            val res = LyricsRepository.fetchLyrics(lead)
            fetchedLyricLines = res.activeList
            isFetchingLyrics = false
        }
    }

    fun exportPoster(shareAfter: Boolean) {
        if (isExporting) return
        scope.launch {
            isExporting = true
            val savedUri = withContext(Dispatchers.IO) {
                val bmp = Poster3DRenderer.renderPoster(
                    canvasW = config.aspectRatio.exportW,
                    canvasH = config.aspectRatio.exportH,
                    config = config,
                    tracks = selectedTracks,
                    coverBitmaps = coverBitmaps,
                    customStickerBitmaps = customStickerBitmaps,
                    uprightFullPhoto = uprightPhoto,
                    cutoutBmp = cutoutBitmap,
                    rimLightOverlayBmp = rimLightOverlayBmp,
                    dominantAccentArgb = dominantAccentArgb
                )
                val uri = savePosterPngToGallery(context, bmp)
                bmp.recycle()
                uri
            }
            isExporting = false
            if (savedUri != null) {
                if (shareAfter) {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, savedUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Bagikan Poster SpotyFlow"))
                    } catch (_: Throwable) {
                        onShowToast("Poster tersimpan di Galeri Pictures/SpotyFlow")
                    }
                } else {
                    onShowToast("Poster Full HD disimpan ke Galeri Pictures/SpotyFlow")
                }
            } else {
                onShowToast("Gagal menyimpan poster")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpotyColors.PureWhiteCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // =====================================================================
        // HEADER BAR (< Poster lagu | Reset | Bagikan | Simpan)
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpotyGlassCircleButton(onClick = onBack, size = 38.dp, useFloatingLayer = false) {
                    Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Kembali", tint = SpotyColors.Slate900, modifier = Modifier.size(16.dp))
                }
                Text("Poster lagu 3D", fontSize = 17.5.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SpotyGlassCircleButton(
                    onClick = {
                        brushStrokes = emptyList()
                        showCutoutPreview = false
                        isBrushMode = false
                        activePreset = null
                        config = PosterStudioConfig(
                            cards = Poster3DRenderer.defaultCardsForFormation(selectedTracks, PosterFormation.MIRROR_SHOULDERS),
                            depthText = DepthTypographyConfig(
                                enabled = false,
                                text = selectedTracks.firstOrNull()?.title?.uppercase() ?: "SPOTYFLOW"
                            )
                        )
                        onShowToast("Desain direset")
                    },
                    size = 36.dp,
                    useFloatingLayer = false
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset", tint = SpotyColors.Slate900, modifier = Modifier.size(17.dp))
                }
                SpotyGlassCircleButton(onClick = { exportPoster(shareAfter = true) }, size = 36.dp, useFloatingLayer = false) {
                    Icon(Icons.Rounded.Share, contentDescription = "Bagikan", tint = SpotyColors.Slate900, modifier = Modifier.size(17.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(SpotyColors.Slate900)
                        .clickable { exportPoster(shareAfter = false) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                        Text("Simpan", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // =====================================================================
        // INTERACTIVE LIVE PREVIEW (2-Finger Pinch/Rotate/Pan OR 1-Finger Brush)
        // =====================================================================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            val ratioFloat = config.aspectRatio.exportW.toFloat() / config.aspectRatio.exportH.toFloat()
            val previewGesturesModifier = if (isBrushMode) {
                Modifier.pointerInput(brushAddForeground) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val w = previewBoxSize.width.coerceAtLeast(1).toFloat()
                            val h = previewBoxSize.height.coerceAtLeast(1).toFloat()
                            currentStrokePoints = listOf(
                                (startOffset.x / w).coerceIn(0f, 1f) to (startOffset.y / h).coerceIn(0f, 1f)
                            )
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val w = previewBoxSize.width.coerceAtLeast(1).toFloat()
                            val h = previewBoxSize.height.coerceAtLeast(1).toFloat()
                            currentStrokePoints = currentStrokePoints + (
                                (change.position.x / w).coerceIn(0f, 1f) to (change.position.y / h).coerceIn(0f, 1f)
                            )
                        },
                        onDragEnd = {
                            if (currentStrokePoints.isNotEmpty()) {
                                brushStrokes = brushStrokes + BrushStroke(
                                    points = currentStrokePoints,
                                    addForeground = brushAddForeground,
                                    radiusFrac = 0.065f
                                )
                                currentStrokePoints = emptyList()
                            }
                        }
                    )
                }
            } else {
                // Upgrade #5: Hit-test tap selection + 2-finger Pinch/Rotate + 1-finger Pan (stable keys = zero gesture restart!)
                Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { tapOffset ->
                            val w = previewBoxSize.width.coerceAtLeast(1).toFloat()
                            val h = previewBoxSize.height.coerceAtLeast(1).toFloat()
                            val nx = (tapOffset.x / w).coerceIn(0f, 1f)
                            val ny = (tapOffset.y / h).coerceIn(0f, 1f)

                            val hitSticker = config.stickers.lastOrNull { st ->
                                hypot(st.xFrac - nx, st.yFrac - ny) < 0.11f
                            }
                            if (hitSticker != null) {
                                selectedObject = SelectedPosterObject.Sticker(hitSticker.id)
                                return@detectTapGestures
                            }

                            if (config.depthText.enabled &&
                                kotlin.math.abs(config.depthText.posY - ny) < 0.11f &&
                                kotlin.math.abs(config.depthText.posX - nx) < 0.36f
                            ) {
                                selectedObject = SelectedPosterObject.DepthText
                                return@detectTapGestures
                            }

                            val cardIdx = config.cards.indices.minByOrNull { i ->
                                hypot(config.cards[i].centerXFrac - nx, config.cards[i].centerYFrac - ny)
                            }
                            if (cardIdx != null) {
                                selectedObject = SelectedPosterObject.Card(cardIdx)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, rotation ->
                            val w = previewBoxSize.width.coerceAtLeast(1).toFloat()
                            val h = previewBoxSize.height.coerceAtLeast(1).toFloat()
                            val dx = pan.x / w
                            val dy = pan.y / h

                            when (val sel = selectedObject) {
                                is SelectedPosterObject.Sticker -> {
                                    val idx = config.stickers.indexOfFirst { it.id == sel.id }
                                    if (idx >= 0) {
                                        val list = config.stickers.toMutableList()
                                        val s = list[idx]
                                        list[idx] = s.copy(
                                            xFrac = (s.xFrac + dx).coerceIn(0.05f, 0.95f),
                                            yFrac = (s.yFrac + dy).coerceIn(0.05f, 0.95f),
                                            scale = (s.scale * zoom).coerceIn(0.45f, 2.2f),
                                            rotationDeg = s.rotationDeg + rotation
                                        )
                                        config = config.copy(stickers = list)
                                    }
                                }
                                SelectedPosterObject.DepthText -> {
                                    if (config.depthText.enabled) {
                                        val dt = config.depthText
                                        config = config.copy(
                                            depthText = dt.copy(
                                                posX = (dt.posX + dx).coerceIn(0.10f, 0.90f),
                                                posY = (dt.posY + dy).coerceIn(0.08f, 0.92f),
                                                scale = (dt.scale * zoom).coerceIn(0.45f, 2.2f),
                                                rotationDeg = dt.rotationDeg + rotation
                                            )
                                        )
                                    }
                                }
                                is SelectedPosterObject.Card -> {
                                    val idx = sel.index.coerceIn(0, (config.cards.size - 1).coerceAtLeast(0))
                                    if (idx in config.cards.indices) {
                                        val list = config.cards.toMutableList()
                                        val c = list[idx]
                                        list[idx] = c.copy(
                                            centerXFrac = (c.centerXFrac + dx).coerceIn(0.08f, 0.92f),
                                            centerYFrac = (c.centerYFrac + dy).coerceIn(0.10f, 0.90f),
                                            scale = (c.scale * zoom).coerceIn(0.45f, 2.2f),
                                            rotateZDeg = c.rotateZDeg + rotation
                                        )
                                        config = config.copy(cards = list)
                                    }
                                }
                            }
                        }
                    }
            }

            Box(
                modifier = Modifier
                    .aspectRatio(ratioFloat)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFE2E8F0))
                    .border(1.5.dp, Color.White, RoundedCornerShape(22.dp))
                    .onSizeChanged { previewBoxSize = it }
                    .then(previewGesturesModifier),
                contentAlignment = Alignment.Center
            ) {
                previewBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Preview Poster 3D",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isSegmenting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                            Text("Memotong objek & Cahaya Tepi...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Brush Mode Floating Controls Banner
                if (isBrushMode) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(SpotyColors.Slate900.copy(alpha = 0.88f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrushPill("+ Depan", brushAddForeground, Color(0xFF10B981)) { brushAddForeground = true }
                        BrushPill("- Belakang", !brushAddForeground, Color(0xFFEF4444)) { brushAddForeground = false }
                        Icon(
                            imageVector = Icons.Rounded.Undo,
                            contentDescription = "Undo",
                            tint = Color.White,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable {
                                    if (brushStrokes.isNotEmpty()) brushStrokes = brushStrokes.dropLast(1)
                                }
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White)
                                .clickable { isBrushMode = false }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Selesai", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                        }
                    }
                }
            }
        }

        // =====================================================================
        // UPGRADE #3: CONTEXTUAL LAYER MINI-BAR (Belakang Objek <-> Depan Objek)
        // =====================================================================
        if (!isBrushMode) {
            ContextualLayerBar(
                selectedObject = selectedObject,
                config = config,
                tracks = selectedTracks,
                onToggleCardLayer = { idx ->
                    if (idx in config.cards.indices) {
                        val updated = config.cards.toMutableList()
                        val nextFront = !updated[idx].inFrontOfSubject
                        updated[idx] = updated[idx].copy(inFrontOfSubject = nextFront)
                        config = config.copy(cards = updated)
                    }
                },
                onToggleTextLayer = {
                    config = config.copy(depthText = config.depthText.copy(behindSubject = !config.depthText.behindSubject))
                },
                onToggleStickerLayer = { id ->
                    config = config.copy(
                        stickers = config.stickers.map { if (it.id == id) it.copy(behindSubject = !it.behindSubject) else it }
                    )
                }
            )
        }

        // =====================================================================
        // UPGRADE #6: 5 ONE-TAP PRO PRESETS + QUICK PILL BAR
        // =====================================================================
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(PosterProPreset.entries) { preset ->
                QuickStudioPill(
                    label = preset.label,
                    selected = activePreset == preset,
                    accentFill = true,
                    onClick = {
                        activePreset = preset
                        config = Poster3DRenderer.applyProPreset(config, preset, selectedTracks)
                        onShowToast("Preset ${preset.label} diterapkan")
                    }
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            item {
                QuickStudioPill(
                    label = "Pilih Lagu (${selectedTracks.size}/5)",
                    selected = true,
                    onClick = { showSongPicker = true }
                )
            }
            items(PosterAspectRatio.entries) { ratio ->
                QuickStudioPill(
                    label = ratio.label,
                    selected = config.aspectRatio == ratio,
                    onClick = { config = config.copy(aspectRatio = ratio) }
                )
            }
            item {
                QuickStudioPill(
                    label = "Acak desain",
                    selected = false,
                    onClick = {
                        activePreset = null
                        val nextForm = PosterFormation.entries.random()
                        val nextStyle = PosterCardStyle.entries.random()
                        val nextBg = PosterBgPreset.entries.random()
                        config = config.copy(
                            formation = nextForm,
                            cardStyle = nextStyle,
                            bgPreset = nextBg,
                            cards = Poster3DRenderer.defaultCardsForFormation(selectedTracks, nextForm),
                            lensFlarePct = Random.nextFloat().coerceIn(0.3f, 0.95f)
                        )
                    }
                )
            }
        }

        // =====================================================================
        // BOTTOM 5-TAB CONTROL PANEL (Foto | Kartu | Gaya kartu | Efek | Stiker)
        // =====================================================================
        SpotyGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(256.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(24.dp),
            tintAlpha = 0.88f,
            elevation = 14.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PosterTab.entries.forEach { tab ->
                        val sel = activeTab == tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (sel) SpotyColors.Slate900 else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tab.label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) Color.White else SpotyColors.Slate500
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    when (activeTab) {
                        PosterTab.PHOTO -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                ActionStudioChip(
                                    label = if (uprightPhoto == null) "Pilih foto sendiri" else "Ganti foto",
                                    highlighted = true,
                                    onClick = {
                                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (uprightPhoto != null) {
                                    ActionStudioChip(
                                        label = if (showCutoutPreview) "Mode Poster" else "Lihat Potongan",
                                        highlighted = showCutoutPreview,
                                        onClick = { showCutoutPreview = !showCutoutPreview },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionStudioChip(
                                        label = if (isBrushMode) "Kuas Aktif" else "Rapikan Kuas",
                                        highlighted = isBrushMode,
                                        onClick = {
                                            showCutoutPreview = false
                                            isBrushMode = !isBrushMode
                                            if (isBrushMode) onShowToast("Usap jari di preview untuk rapikan objek")
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Upgrade #4: 6 Cinematic Photo Color Filters + B&W Color-Pop
                            Text("Filter Warna Foto", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate700)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(PhotoColorFilter.entries) { flt ->
                                    QuickStudioPill(
                                        label = flt.label,
                                        selected = config.photoFilter == flt,
                                        onClick = { config = config.copy(photoFilter = flt) }
                                    )
                                }
                            }

                            PosterSwitchRow(
                                label = "Objek di Depan Sampul (Potong Latar Otomatis)",
                                checked = config.depthEnabled,
                                onChecked = { config = config.copy(depthEnabled = it) }
                            )
                            PosterSliderRow("Sensitivitas Potongan", config.sensitivity, 0.2f..0.8f) { config = config.copy(sensitivity = it) }
                            PosterSliderRow("Halus Tepi", config.edgeFeather, 0f..1f) { config = config.copy(edgeFeather = it) }
                            PosterSliderRow("Gelap Latar", config.bgDimPct, 0f..0.85f) { config = config.copy(bgDimPct = it) }
                            PosterSliderRow("Kabur Latar", config.bgBlurPct, 0f..1f) { config = config.copy(bgBlurPct = it) }
                            PosterSliderRow("Cahaya Kartu", config.cardGlowPct, 0f..1f) { config = config.copy(cardGlowPct = it) }
                            // Upgrade #1: Rim Light Slider right below Cahaya Kartu
                            PosterSliderRow("Cahaya Tepi (Rim Light)", config.rimLightPct, 0f..1f, pctDisplay = true) {
                                config = config.copy(rimLightPct = it)
                            }
                            PosterSliderRow("Zoom Foto", config.photoZoom, 1.0f..2.2f) { config = config.copy(photoZoom = it) }
                            PosterSliderRow("Geser Foto X", config.photoPanX, -1f..1f) { config = config.copy(photoPanX = it) }
                            PosterSliderRow("Geser Foto Y", config.photoPanY, -1f..1f) { config = config.copy(photoPanY = it) }
                        }

                        PosterTab.CARDS -> {
                            Text("Tip: Ketuk kartu di preview lalu cubit/putar pakai 2 jari!", fontSize = 11.5.sp, color = SpotyColors.DeepEmerald, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(PosterFormation.entries) { form ->
                                    QuickStudioPill(
                                        label = form.label,
                                        selected = config.formation == form,
                                        onClick = {
                                            config = config.copy(
                                                formation = form,
                                                cards = Poster3DRenderer.defaultCardsForFormation(selectedTracks, form)
                                            )
                                        }
                                    )
                                }
                            }
                            PosterSliderRow("Putar 3D Y", config.globalRotateY, -40f..40f) { config = config.copy(globalRotateY = it) }
                            PosterSliderRow("Dongak 3D X", config.globalRotateX, -30f..30f) { config = config.copy(globalRotateX = it) }
                            PosterSliderRow("Miring Z", config.globalRotateZ, -35f..35f) { config = config.copy(globalRotateZ = it) }
                            PosterSliderRow("Ukuran Kartu", config.globalCardScale, 0.65f..1.4f) { config = config.copy(globalCardScale = it) }
                            PosterSwitchRow("Tampilkan sampul", config.showCover) { config = config.copy(showCover = it) }
                            PosterSwitchRow("Tampilkan judul & artis", config.showTitleArtist) { config = config.copy(showTitleArtist = it) }
                            PosterSwitchRow("Tampilkan seekbar", config.showSeekbar) { config = config.copy(showSeekbar = it) }
                            PosterSwitchRow("Tampilkan tombol kontrol", config.showControls) { config = config.copy(showControls = it) }
                        }

                        PosterTab.CARD_STYLE -> {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(PosterCardStyle.entries) { style ->
                                    QuickStudioPill(
                                        label = style.label,
                                        selected = config.cardStyle == style,
                                        onClick = { config = config.copy(cardStyle = style) }
                                    )
                                }
                            }
                            // Upgrade #7: Glass Water Drops toggle on 3D cards
                            PosterSwitchRow("Tetesan Air Kaca (SpotyFlow Dew Optics)", config.cardWaterDrops) {
                                config = config.copy(cardWaterDrops = it)
                            }
                            PosterSliderRow("Ketebalan 3D", config.thickness3dPct, 0f..1f) { config = config.copy(thickness3dPct = it) }
                            PosterSliderRow("Transparansi Kartu", config.cardAlpha, 0.45f..1f) { config = config.copy(cardAlpha = it) }
                            PosterSliderRow("Kebulatan Sudut", config.cardCornerPct, 0f..1f) { config = config.copy(cardCornerPct = it) }
                        }

                        PosterTab.EFFECTS -> {
                            // Upgrade #2: Giant Depth Typography Behind Head Controls
                            PosterSwitchRow("Teks Raksasa di Belakang Kepala", config.depthText.enabled) { en ->
                                val defaultTxt = config.depthText.text.ifBlank {
                                    selectedTracks.firstOrNull()?.title?.uppercase() ?: "SPOTYFLOW"
                                }
                                config = config.copy(depthText = config.depthText.copy(enabled = en, text = defaultTxt))
                                if (en) selectedObject = SelectedPosterObject.DepthText
                            }

                            if (config.depthText.enabled) {
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                                    ActionStudioChip(
                                        label = "Judul Lagu",
                                        highlighted = false,
                                        onClick = {
                                            val t = selectedTracks.firstOrNull()?.title?.uppercase() ?: "SPOTYFLOW"
                                            config = config.copy(depthText = config.depthText.copy(text = t))
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionStudioChip(
                                        label = "Ambil Lirik",
                                        highlighted = true,
                                        onClick = { openLyricLinePicker() },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionStudioChip(
                                        label = "Ketik Sendiri",
                                        highlighted = false,
                                        onClick = {
                                            customTextDraft = config.depthText.text
                                            showCustomTextDialog = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(PosterFontStyle.entries) { fs ->
                                        QuickStudioPill(fs.label, config.depthText.fontStyle == fs) {
                                            config = config.copy(depthText = config.depthText.copy(fontStyle = fs))
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    DepthTextColorMode.entries.forEach { cm ->
                                        QuickStudioPill(cm.label, config.depthText.colorMode == cm) {
                                            config = config.copy(depthText = config.depthText.copy(colorMode = cm))
                                        }
                                    }
                                }

                                PosterSwitchRow("Teks di Belakang Objek", config.depthText.behindSubject) { b ->
                                    config = config.copy(depthText = config.depthText.copy(behindSubject = b))
                                }
                                PosterSliderRow("Ukuran Teks", config.depthText.scale, 0.5f..2.1f) { v ->
                                    config = config.copy(depthText = config.depthText.copy(scale = v))
                                }
                                PosterSliderRow("Posisi Teks Y", config.depthText.posY, 0.10f..0.88f) { v ->
                                    config = config.copy(depthText = config.depthText.copy(posY = v))
                                }
                                PosterSliderRow("Transparansi Teks", config.depthText.opacity, 0.2f..1.0f, pctDisplay = true) { v ->
                                    config = config.copy(depthText = config.depthText.copy(opacity = v))
                                }
                            }

                            // Upgrade #7: Also accessible directly inside Tab Efek
                            PosterSwitchRow("Tetesan Air Kaca di Kartu", config.cardWaterDrops) {
                                config = config.copy(cardWaterDrops = it)
                            }
                            PosterSliderRow("Kilau Sudut", config.lensFlarePct, 0f..1f, pctDisplay = true) { config = config.copy(lensFlarePct = it) }
                            PosterSliderRow("Bayangan 3D", config.shadow3dPct, 0f..1f, pctDisplay = true) { config = config.copy(shadow3dPct = it) }
                            PosterSliderRow("Vignette", config.vignettePct, 0f..1f, pctDisplay = true) { config = config.copy(vignettePct = it) }
                            PosterSliderRow("Film Grain", config.filmGrainPct, 0f..0.8f, pctDisplay = true) { config = config.copy(filmGrainPct = it) }
                            Text("Latar Studio (Tanpa Foto)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(PosterBgPreset.entries) { bg ->
                                    QuickStudioPill(bg.label, config.bgPreset == bg) {
                                        config = config.copy(bgPreset = bg)
                                    }
                                }
                            }
                        }

                        PosterTab.STICKERS -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${config.stickers.size} dari 30 stiker", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate500)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (config.stickers.isNotEmpty()) {
                                        QuickStudioPill("Hapus terakhir", false) {
                                            config = config.copy(stickers = config.stickers.dropLast(1))
                                        }
                                    }
                                    QuickStudioPill("+ Tambah stiker PNG", true) {
                                        stickerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                }
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(BuiltInStickerKind.entries) { kind ->
                                    QuickStudioPill(kind.label, false) {
                                        if (config.stickers.size < 30) {
                                            val st = PosterStickerItem(
                                                id = "st_${System.currentTimeMillis()}_${kind.name}",
                                                kind = kind,
                                                xFrac = 0.25f + Random.nextFloat() * 0.5f,
                                                yFrac = 0.16f + Random.nextFloat() * 0.35f
                                            )
                                            config = config.copy(stickers = config.stickers + st)
                                            selectedObject = SelectedPosterObject.Sticker(st.id)
                                        }
                                    }
                                }
                            }

                            if (customStickerFiles.isNotEmpty()) {
                                Text("Stiker saya", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(customStickerFiles, key = { it.absolutePath }) { f ->
                                        val bmp = customStickerBitmaps[f.absolutePath]
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(SpotyColors.Slate900.copy(alpha = 0.08f))
                                                .clickable {
                                                    if (config.stickers.size < 30) {
                                                        val st = PosterStickerItem(
                                                            id = "cst_${System.currentTimeMillis()}",
                                                            kind = null,
                                                            customFilePath = f.absolutePath
                                                        )
                                                        config = config.copy(stickers = config.stickers + st)
                                                        selectedObject = SelectedPosterObject.Sticker(st.id)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (bmp != null) {
                                                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Stiker kustom", modifier = Modifier.fillMaxSize().padding(4.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            val activeStId = (selectedObject as? SelectedPosterObject.Sticker)?.id
                            val activeSt = config.stickers.firstOrNull { it.id == activeStId } ?: config.stickers.lastOrNull()
                            if (activeSt != null) {
                                PosterSwitchRow("Taruh stiker di belakang objek", activeSt.behindSubject) { behind ->
                                    config = config.copy(
                                        stickers = config.stickers.map { if (it.id == activeSt.id) it.copy(behindSubject = behind) else it }
                                    )
                                }
                                PosterSliderRow("Ukuran stiker", activeSt.scale, 0.45f..2.2f) { sc ->
                                    config = config.copy(
                                        stickers = config.stickers.map { if (it.id == activeSt.id) it.copy(scale = sc) else it }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // Multi-Track Song Picker (1-5 Songs without interrupting active playback!)
    if (showSongPicker) {
        val pool = remember(uiState.currentTrack, uiState.likedTracks, uiState.recentTracks, uiState.homeTracks) {
            val map = LinkedHashMap<String, TrackItem>()
            (listOfNotNull(uiState.currentTrack) + uiState.likedTracks + uiState.recentTracks + uiState.homeTracks).forEach {
                if (!map.containsKey(it.id)) map[it.id] = it
            }
            map.values.toList()
        }
        AlertDialog(
            onDismissRequest = { showSongPicker = false },
            containerColor = Color.White,
            title = { Text("Pilih 1-5 Lagu untuk Poster", fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pool, key = { it.id }) { t ->
                        val picked = selectedTracks.any { it.id == t.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (picked) SpotyColors.EmeraldTint else SpotyColors.Slate900.copy(alpha = 0.04f))
                                .clickable {
                                    val next = if (picked) {
                                        if (selectedTracks.size > 1) selectedTracks.filterNot { it.id == t.id } else selectedTracks
                                    } else {
                                        if (selectedTracks.size < 5) selectedTracks + t else selectedTracks
                                    }
                                    selectedTracks = next
                                    config = config.copy(
                                        cards = Poster3DRenderer.defaultCardsForFormation(next, config.formation)
                                    )
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SpotyArtwork(t.coverUrl, t.accentColor, 40.dp, 10.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(t.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, fontSize = 11.5.sp, color = SpotyColors.Slate500, maxLines = 1)
                            }
                            if (picked) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = SpotyColors.DeepEmerald, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSongPicker = false }) {
                    Text("Selesai (${selectedTracks.size})", fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald)
                }
            }
        )
    }

    // Upgrade #2: 1-Tap Lyric Picker Dialog (from LyricsRepository)
    if (showLyricPicker) {
        AlertDialog(
            onDismissRequest = { showLyricPicker = false },
            containerColor = Color.White,
            title = { Text("Pilih 1-2 Baris Lirik Favorit", fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900) },
            text = {
                when {
                    isFetchingLyrics -> {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SpotyColors.EmeraldCrystal)
                        }
                    }
                    fetchedLyricLines.isEmpty() -> {
                        Text("Lirik tidak ditemukan untuk lagu ini. Gunakan Ketik Sendiri untuk menulis teks estetikmu.", fontSize = 13.sp, color = SpotyColors.Slate500)
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(fetchedLyricLines) { idx, line ->
                                val nextLine = fetchedLyricLines.getOrNull(idx + 1)?.text.orEmpty()
                                val twoLines = if (nextLine.isNotBlank()) "${line.text}\n$nextLine" else line.text
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SpotyColors.Slate900.copy(alpha = 0.05f))
                                        .clickable {
                                            config = config.copy(
                                                depthText = config.depthText.copy(enabled = true, text = twoLines.uppercase())
                                            )
                                            selectedObject = SelectedPosterObject.DepthText
                                            showLyricPicker = false
                                        }
                                        .padding(10.dp)
                                ) {
                                    Text(line.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SpotyColors.Slate900)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLyricPicker = false }) { Text("Tutup", color = SpotyColors.Slate500) }
            }
        )
    }

    // Upgrade #2: Custom Depth Text Dialog
    if (showCustomTextDialog) {
        AlertDialog(
            onDismissRequest = { showCustomTextDialog = false },
            containerColor = Color.White,
            title = { Text("Ketik Teks Raksasa", fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900) },
            text = {
                OutlinedTextField(
                    value = customTextDraft,
                    onValueChange = { customTextDraft = it },
                    placeholder = { Text("Tulis judul atau potongan lirik...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customTextDraft.isNotBlank()) {
                        config = config.copy(
                            depthText = config.depthText.copy(enabled = true, text = customTextDraft.trim())
                        )
                        selectedObject = SelectedPosterObject.DepthText
                    }
                    showCustomTextDialog = false
                }) { Text("Terapkan", fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTextDialog = false }) { Text("Batal", color = SpotyColors.Slate500) }
            }
        )
    }
}

@Composable
private fun ContextualLayerBar(
    selectedObject: SelectedPosterObject,
    config: PosterStudioConfig,
    tracks: List<TrackItem>,
    onToggleCardLayer: (Int) -> Unit,
    onToggleTextLayer: () -> Unit,
    onToggleStickerLayer: (String) -> Unit
) {
    val label: String
    val isInFront: Boolean
    val onToggle: () -> Unit

    when (selectedObject) {
        is SelectedPosterObject.Card -> {
            val card = config.cards.getOrNull(selectedObject.index) ?: return
            val track = tracks.firstOrNull { it.id == card.trackId } ?: tracks.getOrNull(selectedObject.index)
            label = "Kartu #${selectedObject.index + 1}: ${track?.title ?: "Lagu"}"
            isInFront = card.inFrontOfSubject
            onToggle = { onToggleCardLayer(selectedObject.index) }
        }
        SelectedPosterObject.DepthText -> {
            if (!config.depthText.enabled) return
            label = "Teks 3D"
            isInFront = !config.depthText.behindSubject
            onToggle = onToggleTextLayer
        }
        is SelectedPosterObject.Sticker -> {
            val st = config.stickers.firstOrNull { it.id == selectedObject.id } ?: return
            label = "Stiker: ${st.kind?.label ?: "Kustom"}"
            isInFront = !st.behindSubject
            onToggle = { onToggleStickerLayer(st.id) }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SpotyColors.Slate900.copy(alpha = 0.055f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = SpotyColors.Slate900,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (isInFront) SpotyColors.Slate900 else SpotyColors.EmeraldCrystal)
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = if (isInFront) Icons.Rounded.FlipToFront else Icons.Rounded.FlipToBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isInFront) "Depan Objek" else "Belakang Objek",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

private fun savePosterPngToGallery(context: Context, bmp: Bitmap): Uri? {
    return try {
        val fileName = "SpotyFlow_Poster_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SpotyFlow")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
        }
        uri
    } catch (_: Throwable) {
        null
    }
}

@Composable
private fun BrushPill(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) activeColor else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun QuickStudioPill(
    label: String,
    selected: Boolean,
    accentFill: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        selected -> SpotyColors.EmeraldCrystal
        accentFill -> SpotyColors.Slate900.copy(alpha = 0.085f)
        else -> SpotyColors.Slate900.copy(alpha = 0.065f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else SpotyColors.Slate900
        )
    }
}

@Composable
private fun ActionStudioChip(
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) SpotyColors.EmeraldCrystal else SpotyColors.Slate900.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) Color.White else SpotyColors.Slate900,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PosterSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    pctDisplay: Boolean = false,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SpotyColors.Slate900)
            val txt = if (pctDisplay) "${(value * 100f).toInt()}%" else String.format(java.util.Locale.US, "%.2f", value)
            Text(txt, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = SpotyColors.EmeraldCrystal, activeTrackColor = SpotyColors.EmeraldCrystal)
        )
    }
}

@Composable
private fun PosterSwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = SpotyColors.Slate900, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpotyColors.EmeraldCrystal)
        )
    }
}
