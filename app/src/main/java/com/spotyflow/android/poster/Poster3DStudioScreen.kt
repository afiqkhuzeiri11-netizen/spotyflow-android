package com.spotyflow.android.poster

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.data.SearchFilter
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.formatDuration
import com.spotyflow.android.data.remote.InnerTubeMusicClient
import com.spotyflow.android.data.remote.LyricsRepository
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.glass.SpotyGlassCircleButton
import com.spotyflow.android.ui.theme.LocalSpotyAppearance
import com.spotyflow.android.ui.theme.SpotyColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Poster Lagu Depth Effect Studio v2.5.0-native.
 *
 * ATURAN MUTLAK 4 LAYER (WYSIWYG 1:1 DENGAN PosterHdExporter.kt):
 * - Layer 1 (zIndex 0f): Foto Asli Pengguna (EXIF-safe upright, ContentScale.Crop + photoScale/pan)
 * - Layer 2 (zIndex 1f): Teks Besar + SEMUA Sampul Album (1-5 lagu) + Glow Adaptif -> 100% DI BELAKANG OBJEK!
 * - Layer 3 (zIndex 2f): Potongan Objek Transparan 1:1 (ContentScale.Crop + photoScale/pan IDENTIK Layer 1)
 * - Layer 4 (zIndex 3f): Gradasi Bawah + Kartu Mini-Player "Liquid Glass" (0 Thumbnail Sampul di Depan Objek!)
 */
@Composable
fun Poster3DStudioScreen(
    uiState: PlayerUiState,
    initialTrack: TrackItem?,
    onBack: () -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = LocalSpotyAppearance.current
    val ink = app.textPrimary
    val sub = app.textSecondary
    val accent = app.accent

    val seedTrack = remember(initialTrack, uiState.currentTrack, uiState.likedTracks, uiState.homeTracks) {
        initialTrack ?: uiState.currentTrack ?: uiState.likedTracks.firstOrNull() ?: uiState.homeTracks.firstOrNull()
    }

    var state by remember {
        mutableStateOf(
            DepthPosterState(
                selectedTracks = listOfNotNull(seedTrack),
                bigTextContent = seedTrack?.title.orEmpty(),
                progressFraction = if (uiState.durationMs > 0L) uiState.progress.coerceIn(0.08f, 0.92f) else 0.38f
            )
        )
    }

    var activeTab by remember { mutableStateOf(PosterEditorTab.PHOTO) }
    var uprightPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var blurredPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var foregroundCutout by remember { mutableStateOf<Bitmap?>(null) }
    var activeEngineLabel by remember { mutableStateOf("Belum ada foto") }
    var segmentStatusText by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    // Cache cover bitmaps untuk Palette API & HD Export (1080x1920)
    var coverBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    // Modal states
    var showSongPicker by remember { mutableStateOf(false) }
    var showAiSettings by remember { mutableStateOf(false) }
    var showLyricPicker by remember { mutableStateOf(false) }
    var showCustomTextDialog by remember { mutableStateOf(false) }
    var lyricOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingLyrics by remember { mutableStateOf(false) }

    // Bebaskan memori bitmap saat keluar dari Poster Studio
    DisposableEffect(Unit) {
        onDispose {
            try { blurredPhoto?.let { if (it !== uprightPhoto && !it.isRecycled) it.recycle() } } catch (_: Throwable) {}
            try { foregroundCutout?.let { if (!it.isRecycled) it.recycle() } } catch (_: Throwable) {}
            try { uprightPhoto?.let { if (!it.isRecycled) it.recycle() } } catch (_: Throwable) {}
        }
    }

    // Muat bitmap sampul lagu terpilih & ekstrak warna Palette adaptif secara paralel
    LaunchedEffect(state.selectedTracks.map { "${it.id}|${it.coverUrl}" }) {
        val loaded = LinkedHashMap(coverBitmaps)
        for (t in state.selectedTracks) {
            if (t.coverUrl.isNotBlank() && !loaded.containsKey(t.id)) {
                PosterSubjectSegmenter.loadCoverBitmap(t.coverUrl)?.let { bmp ->
                    loaded[t.id] = bmp
                }
            }
        }
        coverBitmaps = loaded
        val leadBmp = state.selectedTracks.firstOrNull()?.let { loaded[it.id] }
        if (leadBmp != null) {
            val extracted = PosterSubjectSegmenter.extractCoverPalette(leadBmp)
            state = state.copy(palette = extracted)
        }
    }

    // Bangun cache blurred backdrop lintas versi Android (API 26..35)
    LaunchedEffect(uprightPhoto, (state.bgBlur * 20f).roundToInt()) {
        val src = uprightPhoto
        val prev = blurredPhoto
        if (src == null || state.bgBlur <= 0.02f) {
            blurredPhoto = null
            if (prev != null && prev !== src && !prev.isRecycled) {
                try { prev.recycle() } catch (_: Throwable) {}
            }
            return@LaunchedEffect
        }
        val nextBlur = withContext(Dispatchers.Default) {
            PosterSubjectSegmenter.buildBlurredBackdrop(src, state.bgBlur)
        }
        blurredPhoto = nextBlur
        if (prev != null && prev !== src && prev !== nextBlur && !prev.isRecycled) {
            try { prev.recycle() } catch (_: Throwable) {}
        }
    }

    fun runSegmentation(photo: Bitmap) {
        scope.launch {
            segmentStatusText = "Menyiapkan AI Potong Objek..."
            val result = PosterSubjectSegmenter.segmentSubject(
                context = context,
                uprightPhoto = photo,
                onStatus = { status -> segmentStatusText = status }
            )
            segmentStatusText = null
            if (result != null) {
                val oldCutout = foregroundCutout
                foregroundCutout = result.cutoutBitmap
                activeEngineLabel = result.engineLabel
                if (oldCutout != null && oldCutout !== result.cutoutBitmap && !oldCutout.isRecycled) {
                    try { oldCutout.recycle() } catch (_: Throwable) {}
                }
                // Posisikan sampul otomatis agar 30%-42% bagian bawah langsung masuk DI BELAKANG kepala/bahu objek!
                val autoY = computeAutoCoverPlacementY(result.subjectBounds, state.coverScale)
                state = state.copy(
                    depthEnabled = true,
                    coverOffsetXFrac = 0.50f,
                    coverOffsetYFrac = autoY
                )
                onShowToast("Objek terpotong rapi (${result.engineLabel})")
            } else {
                activeEngineLabel = "Objek tidak terdeteksi"
                onShowToast("Objek tidak terdeteksi jelas, coba foto lain dengan subjek lebih terang")
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            segmentStatusText = "Memuat foto EXIF-safe..."
            val decoded = PosterSubjectSegmenter.loadUprightPhoto(context, uri, maxSidePx = 1920)
            if (decoded == null) {
                segmentStatusText = null
                onShowToast("Gagal membaca foto dari galeri")
                return@launch
            }
            val oldPhoto = uprightPhoto
            val oldBlur = blurredPhoto
            val oldCut = foregroundCutout
            uprightPhoto = decoded
            blurredPhoto = null
            foregroundCutout = null
            state = state.copy(
                photoScale = 1.0f,
                photoPanXFrac = 0f,
                photoPanYFrac = 0f,
                showCutoutCheckerboard = false
            )
            if (oldBlur != null && oldBlur !== oldPhoto && !oldBlur.isRecycled) try { oldBlur.recycle() } catch (_: Throwable) {}
            if (oldCut != null && !oldCut.isRecycled) try { oldCut.recycle() } catch (_: Throwable) {}
            if (oldPhoto != null && oldPhoto !== decoded && !oldPhoto.isRecycled) try { oldPhoto.recycle() } catch (_: Throwable) {}

            runSegmentation(decoded)
        }
    }

    fun exportAndSaveOrShare(shareAfterSave: Boolean) {
        if (isExporting) return
        if (state.selectedTracks.isEmpty()) {
            onShowToast("Pilih minimal 1 lagu terlebih dahulu")
            return
        }
        scope.launch {
            isExporting = true
            try {
                // Pastikan semua cover bitmap termuat untuk HD Export
                val covers = LinkedHashMap(coverBitmaps)
                for (t in state.selectedTracks) {
                    if (t.coverUrl.isNotBlank() && !covers.containsKey(t.id)) {
                        PosterSubjectSegmenter.loadCoverBitmap(t.coverUrl)?.let { covers[t.id] = it }
                    }
                }
                val hdBmp = PosterHdExporter.renderFullHdBitmap(
                    state = state,
                    uprightPhoto = uprightPhoto,
                    foregroundCutout = foregroundCutout,
                    coverBitmaps = covers
                )
                val savedUri = PosterHdExporter.savePosterToGallery(context, hdBmp)
                hdBmp.recycle()

                if (savedUri != null) {
                    if (shareAfterSave) {
                        val leadTitle = state.selectedTracks.firstOrNull()?.title ?: "Poster Lagu SpotyFlow"
                        PosterHdExporter.shareExportedPoster(context, savedUri, leadTitle)
                    } else {
                        onShowToast("Disimpan Full HD (1080×1920) ke Pictures/SpotyFlow")
                    }
                } else {
                    onShowToast("Gagal menyimpan poster ke galeri")
                }
            } catch (_: Throwable) {
                onShowToast("Terjadi kendala saat merender poster HD")
            } finally {
                isExporting = false
            }
        }
    }

    fun openLyricsSheet() {
        val lead = state.selectedTracks.firstOrNull() ?: return
        showLyricPicker = true
        val existing = uiState.lyricsResult.activeList.map { it.text }.filter { it.isNotBlank() }
        if (existing.isNotEmpty() && uiState.currentTrack?.id == lead.id) {
            lyricOptions = existing
            return
        }
        scope.launch {
            isLoadingLyrics = true
            val res = LyricsRepository.fetchLyrics(lead)
            lyricOptions = res.activeList.map { it.text }.filter { it.isNotBlank() }
            isLoadingLyrics = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(app.canvasBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // =====================================================================
        // A. HEADER ATAS BERSIH & RINGKAS
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpotyGlassCircleButton(
                    onClick = onBack,
                    size = 38.dp,
                    useFloatingLayer = false
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Kembali",
                        tint = ink,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Text(
                    text = "Poster Lagu",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ink,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Pilih Lagu (1-5)
                SpotyGlassCircleButton(
                    onClick = { showSongPicker = true },
                    size = 36.dp,
                    useFloatingLayer = false
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = "Pilih Lagu",
                        tint = ink,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Pengaturan AI Potong (ML Kit + Slot Clipdrop Opsional)
                SpotyGlassCircleButton(
                    onClick = { showAiSettings = true },
                    size = 36.dp,
                    useFloatingLayer = false
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "AI Potong",
                        tint = ink,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Bagikan
                SpotyGlassCircleButton(
                    onClick = { exportAndSaveOrShare(shareAfterSave = true) },
                    size = 36.dp,
                    useFloatingLayer = false
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Bagikan",
                        tint = ink,
                        modifier = Modifier.size(17.dp)
                    )
                }
                // Tombol Pil Tegas: Simpan HD
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent)
                        .clickable(enabled = !isExporting) { exportAndSaveOrShare(shareAfterSave = false) }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "Simpan HD",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Simpan HD",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // =====================================================================
        // B. KANVAS PREVIEW 9:16 — 4 LAYER STACK WYSIWYG (SEMUA SAMPUL DI LAYER 2!)
        // =====================================================================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            DepthPosterPreviewCanvas(
                state = state,
                uprightPhoto = uprightPhoto,
                blurredPhoto = blurredPhoto,
                foregroundCutout = foregroundCutout,
                isPlaying = uiState.isPlaying,
                statusOverlayText = segmentStatusText,
                onPickPhoto = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onDragCover = { dxFrac, dyFrac ->
                    state = state.copy(
                        coverOffsetXFrac = (state.coverOffsetXFrac + dxFrac).coerceIn(0.14f, 0.86f),
                        coverOffsetYFrac = (state.coverOffsetYFrac + dyFrac).coerceIn(0.14f, 0.72f)
                    )
                },
                onTransformPhoto = { zoomChange, panDxFrac, panDyFrac ->
                    val nextScale = (state.photoScale * zoomChange).coerceIn(1.0f, 2.8f)
                    val maxPan = ((nextScale - 1f) * 0.48f).coerceAtLeast(0.08f)
                    state = state.copy(
                        photoScale = nextScale,
                        photoPanXFrac = (state.photoPanXFrac + panDxFrac).coerceIn(-maxPan, maxPan),
                        photoPanYFrac = (state.photoPanYFrac + panDyFrac).coerceIn(-maxPan, maxPan)
                    )
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f)
            )
        }

        // =====================================================================
        // C. PANEL KONTROL BAWAH SUPER RINGKAS (3 TAB: Foto | Sampul & Teks | Kaca & Gaya)
        // =====================================================================
        SpotyGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(26.dp),
            tintAlpha = 0.78f,
            elevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 3 Tab Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PosterEditorTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (selected) {
                                        if (app.isDark) Color.White else SpotyColors.Slate900
                                    } else {
                                        ink.copy(alpha = 0.07f)
                                    }
                                )
                                .clickable { activeTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) {
                                    if (app.isDark) SpotyColors.Slate900 else Color.White
                                } else ink,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Isi Panel per Tab (scroll vertikal rapi agar tetap kompak di semua ukuran layar)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 212.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (activeTab) {
                        PosterEditorTab.PHOTO -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionChipPill(
                                    icon = Icons.Rounded.AddPhotoAlternate,
                                    label = if (uprightPhoto == null) "Pilih Foto Galeri" else "Ganti Foto",
                                    highlighted = true,
                                    accent = accent,
                                    ink = ink,
                                    onClick = {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.weight(1.3f)
                                )
                                ActionChipPill(
                                    icon = Icons.Rounded.GridOn,
                                    label = if (state.showCutoutCheckerboard) "Mode Poster" else "Cek Potongan",
                                    highlighted = state.showCutoutCheckerboard,
                                    accent = accent,
                                    ink = ink,
                                    onClick = {
                                        if (foregroundCutout == null) {
                                            onShowToast("Pilih foto galeri dulu untuk mengecek potongan objek")
                                        } else {
                                            state = state.copy(showCutoutCheckerboard = !state.showCutoutCheckerboard)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (state.photoScale > 1.01f || abs(state.photoPanXFrac) > 0.01f || abs(state.photoPanYFrac) > 0.01f) {
                                    ActionChipPill(
                                        icon = Icons.Rounded.Refresh,
                                        label = "Reset",
                                        highlighted = false,
                                        accent = accent,
                                        ink = ink,
                                        onClick = {
                                            state = state.copy(photoScale = 1.0f, photoPanXFrac = 0f, photoPanYFrac = 0f)
                                        }
                                    )
                                }
                            }

                            CompactToggleRow(
                                title = "Semua Sampul di Belakang Objek",
                                subtitle = "Mesin AI: $activeEngineLabel",
                                checked = state.depthEnabled,
                                accent = accent,
                                ink = ink,
                                sub = sub,
                                onCheckedChange = { state = state.copy(depthEnabled = it) }
                            )

                            CompactSliderRow(
                                label = "Gelap Latar",
                                valueText = "${(state.bgDim * 100f).roundToInt()}%",
                                value = state.bgDim,
                                range = 0f..0.65f,
                                accent = accent,
                                ink = ink,
                                sub = sub,
                                onValueChange = { state = state.copy(bgDim = it) }
                            )

                            CompactSliderRow(
                                label = "Kabur Latar",
                                valueText = "${(state.bgBlur * 100f).roundToInt()}%",
                                value = state.bgBlur,
                                range = 0f..1.0f,
                                accent = accent,
                                ink = ink,
                                sub = sub,
                                onValueChange = { state = state.copy(bgBlur = it) }
                            )
                        }

                        PosterEditorTab.COVER_TEXT -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionChipPill(
                                    icon = Icons.Rounded.LibraryMusic,
                                    label = "Pilih Lagu (${state.selectedTracks.size}/5)",
                                    highlighted = true,
                                    accent = accent,
                                    ink = ink,
                                    onClick = { showSongPicker = true },
                                    modifier = Modifier.weight(1f)
                                )
                                ActionChipPill(
                                    icon = Icons.Rounded.Refresh,
                                    label = "Posisi Tengah",
                                    highlighted = false,
                                    accent = accent,
                                    ink = ink,
                                    onClick = {
                                        state = state.copy(
                                            coverOffsetXFrac = 0.50f,
                                            coverOffsetYFrac = 0.30f,
                                            coverScale = 1.0f,
                                            coverRotationDeg = 0f
                                        )
                                    }
                                )
                            }

                            CompactSliderRow(
                                label = "Posisi Sampul (Atas-Bawah)",
                                valueText = "${(state.coverOffsetYFrac * 100f).roundToInt()}%",
                                value = state.coverOffsetYFrac,
                                range = 0.14f..0.68f,
                                accent = accent,
                                ink = ink,
                                sub = sub,
                                onValueChange = { state = state.copy(coverOffsetYFrac = it) }
                            )

                            CompactSliderRow(
                                label = "Ukuran Sampul",
                                valueText = "${(state.coverScale * 100f).roundToInt()}%",
                                value = state.coverScale,
                                range = 0.65f..1.35f,
                                accent = accent,
                                ink = ink,
                                sub = sub,
                                onValueChange = { state = state.copy(coverScale = it) }
                            )

                            CompactSliderRow(
                                label = "Kemiringan Sampul",
                                valueText = "${state.coverRotationDeg.roundToInt()}°",
                                value = state.coverRotationDeg,
                                range = -15f..15f,
                                accent = accent,
                                ink = ink,
                                sub = sub,
                                onValueChange = { state = state.copy(coverRotationDeg = it) }
                            )

                            CompactToggleRow(
                                title = "Teks Besar di Belakang Objek",
                                subtitle = "Tipografi tebal terpotong rapi di belakang kepala",
                                checked = state.bigTextEnabled,
                                accent = accent,
                                ink = ink,
                                sub = sub,
                                onCheckedChange = { state = state.copy(bigTextEnabled = it) }
                            )

                            if (state.bigTextEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ActionChipPill(
                                        icon = Icons.Rounded.AutoAwesome,
                                        label = "Judul Lagu",
                                        highlighted = false,
                                        accent = accent,
                                        ink = ink,
                                        onClick = {
                                            state = state.copy(
                                                bigTextContent = state.selectedTracks.firstOrNull()?.title.orEmpty()
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionChipPill(
                                        icon = Icons.Rounded.Lyrics,
                                        label = "Ambil Lirik",
                                        highlighted = false,
                                        accent = accent,
                                        ink = ink,
                                        onClick = { openLyricsSheet() },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionChipPill(
                                        icon = Icons.Rounded.Tune,
                                        label = "Ketik Sendiri",
                                        highlighted = false,
                                        accent = accent,
                                        ink = ink,
                                        onClick = { showCustomTextDialog = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        PosterEditorTab.GLASS_STYLE -> {
                            Text(
                                text = "Gaya Kartu Liquid Glass Bawah",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = sub
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GlassCardTheme.entries.forEach { theme ->
                                    val sel = state.glassCardTheme == theme
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (sel) accent else ink.copy(alpha = 0.08f))
                                            .clickable { state = state.copy(glassCardTheme = theme) }
                                            .padding(vertical = 8.dp, horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = theme.label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sel) Color.White else ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            CompactToggleRow(
                                title = "Kartu Pemutar Bawah",
                                subtitle = "Teks judul, artis, progress bar & tombol Play (tanpa sampul depan)",
                                checked = state.showBottomGlassCard,
                                accent = accent,
                                ink = ink,
                                sub = sub,
                                onCheckedChange = { state = state.copy(showBottomGlassCard = it) }
                            )
                            CompactToggleRow(
                                title = "Glow Sampul Adaptif",
                                subtitle = "Pendar tipis warna sampul di belakang cover",
                                checked = state.coverGlowEnabled,
                                accent = accent,
                                ink = ink,
                                sub = sub,
                                onCheckedChange = { state = state.copy(coverGlowEnabled = it) }
                            )
                            CompactToggleRow(
                                title = "Progress Bar & Waktu",
                                subtitle = "Baris durasi berwarna adaptif di kartu bawah",
                                checked = state.showProgressBar,
                                accent = accent,
                                ink = ink,
                                sub = sub,
                                onCheckedChange = { state = state.copy(showProgressBar = it) }
                            )
                            CompactToggleRow(
                                title = "Watermark SpotyFlow",
                                subtitle = "Label mikro halus di margin paling bawah",
                                checked = state.showWatermark,
                                accent = accent,
                                ink = ink,
                                sub = sub,
                                onCheckedChange = { state = state.copy(showWatermark = it) }
                            )
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOGS: PILIH LAGU (1-5), PENGATURAN AI POTONG, AMBIL LIRIK, KETIK TEKS
    // =========================================================================
    if (showSongPicker) {
        PosterSongPickerDialog(
            uiState = uiState,
            selectedTracks = state.selectedTracks,
            onDismiss = { showSongPicker = false },
            onConfirm = { picked ->
                val safe = picked.take(5)
                val leadTitle = safe.firstOrNull()?.title.orEmpty()
                state = state.copy(
                    selectedTracks = safe,
                    bigTextContent = if (state.bigTextContent.isBlank() ||
                        state.bigTextContent == state.selectedTracks.firstOrNull()?.title
                    ) leadTitle else state.bigTextContent
                )
                showSongPicker = false
            }
        )
    }

    if (showAiSettings) {
        var apiKeyInput by remember { mutableStateOf(PosterSubjectSegmenter.getClipdropApiKey(context)) }
        AlertDialog(
            onDismissRequest = { showAiSettings = false },
            containerColor = if (app.isDark) Color(0xFF161923) else Color.White,
            title = {
                Text("Pengaturan AI Potong Objek", fontWeight = FontWeight.ExtraBold, color = ink)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Mesin aktif: $activeEngineLabel\nOtomatis menggunakan Google ML Kit Subject Segmentation (gratis, manusia + HP + objek) dengan fallback ML Kit Offline.",
                        fontSize = 12.5.sp,
                        color = sub,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("API Key Clipdrop (Opsional Pro)") },
                        placeholder = { Text("Kosongkan untuk Google ML Kit gratis") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        PosterSubjectSegmenter.setClipdropApiKey(context, apiKeyInput)
                        showAiSettings = false
                        val p = uprightPhoto
                        if (p != null) {
                            runSegmentation(p)
                        } else {
                            onShowToast("Pengaturan AI disimpan")
                        }
                    }
                ) {
                    Text(
                        text = if (uprightPhoto != null) "Simpan & Potong Ulang" else "Simpan",
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiSettings = false }) {
                    Text("Tutup", color = sub)
                }
            }
        )
    }

    if (showLyricPicker) {
        AlertDialog(
            onDismissRequest = { showLyricPicker = false },
            containerColor = if (app.isDark) Color(0xFF161923) else Color.White,
            title = { Text("Pilih Baris Lirik", fontWeight = FontWeight.ExtraBold, color = ink) },
            text = {
                if (isLoadingLyrics) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                } else if (lyricOptions.isEmpty()) {
                    Text(
                        text = "Lirik lagu ini belum tersedia. Gunakan tombol \"Ketik Sendiri\".",
                        fontSize = 13.sp,
                        color = sub
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(lyricOptions.take(50)) { line ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ink.copy(alpha = 0.06f))
                                    .clickable {
                                        state = state.copy(bigTextEnabled = true, bigTextContent = line)
                                        showLyricPicker = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = line,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ink
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLyricPicker = false }) {
                    Text("Tutup", color = accent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showCustomTextDialog) {
        var customInput by remember { mutableStateOf(state.bigTextContent) }
        AlertDialog(
            onDismissRequest = { showCustomTextDialog = false },
            containerColor = if (app.isDark) Color(0xFF161923) else Color.White,
            title = { Text("Teks Besar di Belakang Objek", fontWeight = FontWeight.ExtraBold, color = ink) },
            text = {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it.take(48) },
                    label = { Text("Teks atau Kutipan Lirik") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state = state.copy(bigTextEnabled = true, bigTextContent = customInput.trim())
                        showCustomTextDialog = false
                    }
                ) {
                    Text("Terapkan", fontWeight = FontWeight.Bold, color = accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTextDialog = false }) {
                    Text("Batal", color = sub)
                }
            }
        )
    }
}

// =========================================================================
// 4-LAYER WYSIWYG COMPOSE PREVIEW CANVAS (SEMUA SAMPUL 100% DI LAYER 2!)
// =========================================================================
@Composable
private fun DepthPosterPreviewCanvas(
    state: DepthPosterState,
    uprightPhoto: Bitmap?,
    blurredPhoto: Bitmap?,
    foregroundCutout: Bitmap?,
    isPlaying: Boolean,
    statusOverlayText: String?,
    onPickPhoto: () -> Unit,
    onDragCover: (Float, Float) -> Unit,
    onTransformPhoto: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // HazeState terpisah khusus untuk kartu Liquid Glass Layer 4 (sibling ketat dari Layer 1-3!)
    val posterHazeState = remember { HazeState() }
    val leadTrack = state.selectedTracks.firstOrNull()
    val slots = remember(state) { computeLayer2CoverSlots(state) }

    BoxWithConstraints(
        modifier = modifier
            .shadow(elevation = 20.dp, shape = RoundedCornerShape(24.dp), ambientColor = Color(0x330F172A))
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0C1018))
            .pointerInput(uprightPhoto != null, state.showCutoutCheckerboard) {
                if (state.showCutoutCheckerboard) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    val wPx = size.width.coerceAtLeast(1).toFloat()
                    val hPx = size.height.coerceAtLeast(1).toFloat()
                    if (uprightPhoto != null && abs(zoom - 1f) > 0.004f) {
                        // Cubit 2 jari -> zoom & geser foto Layer 1 & Layer 3 sinkron 1:1
                        onTransformPhoto(zoom, pan.x / wPx, pan.y / hPx)
                    } else if (pan != Offset.Zero) {
                        // Geser 1 jari -> pindahkan posisi sampul di Layer 2 (di belakang objek!)
                        onDragCover(pan.x / wPx, pan.y / hPx)
                    }
                }
            }
    ) {
        val canvasW = constraints.maxWidth.coerceAtLeast(1).toFloat()
        val canvasH = constraints.maxHeight.coerceAtLeast(1).toFloat()
        val canvasWDp = with(density) { canvasW.toDp() }
        val photoTxPx = state.photoPanXFrac * canvasW
        val photoTyPx = state.photoPanYFrac * canvasH

        if (state.showCutoutCheckerboard && foregroundCutout != null) {
            // MODE CEK POTONGAN (Checkerboard Transparan + Potongan Objek Murni!)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tile = 14.dp.toPx()
                var y = 0f
                var row = 0
                while (y < size.height) {
                    var x = 0f
                    var col = 0
                    while (x < size.width) {
                        drawRect(
                            color = if ((row + col) % 2 == 0) Color(0xFFE2E8F0) else Color(0xFFCBD5E1),
                            topLeft = Offset(x, y),
                            size = Size(tile, tile)
                        )
                        x += tile
                        col++
                    }
                    y += tile
                    row++
                }
            }
            Image(
                bitmap = foregroundCutout.asImageBitmap(),
                contentDescription = "Cek Potongan Objek",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = state.photoScale,
                        scaleY = state.photoScale,
                        translationX = photoTxPx,
                        translationY = photoTyPx
                    )
            )
        } else {
            // =================================================================
            // SIBLING A (hazeSource): LAYER 1 + LAYER 2 + LAYER 3
            // =================================================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = posterHazeState)
            ) {
                // -------------------------------------------------------------
                // LAYER 1 (zIndex 0f): FOTO ASLI PENGGUNA (+ KABUR & GELAP)
                // -------------------------------------------------------------
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f)
                ) {
                    val activeBgBmp = blurredPhoto ?: uprightPhoto
                    if (activeBgBmp != null) {
                        Image(
                            bitmap = activeBgBmp.asImageBitmap(),
                            contentDescription = "Foto Latar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = state.photoScale,
                                    scaleY = state.photoScale,
                                    translationX = photoTxPx,
                                    translationY = photoTyPx
                                )
                        )
                        if (state.bgDim > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF060A12).copy(alpha = state.bgDim.coerceIn(0f, 0.70f)))
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            state.palette.glowCompose.copy(alpha = 0.42f),
                                            state.palette.darkCompose,
                                            Color(0xFF090C14)
                                        )
                                    )
                                )
                        )
                    }
                }

                // -------------------------------------------------------------
                // LAYER 2 (zIndex 1f): TEKS BESAR + SEMUA SAMPUL ALBUM (1-5 LAGU)
                // 100% DI BELAKANG LAYER 3 (POTONGAN OBJEK TRANSPARAN)!
                // -------------------------------------------------------------
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                ) {
                    // Teks Besar Belakang Objek
                    if (state.bigTextEnabled) {
                        val displayText = state.bigTextContent.ifBlank { leadTrack?.title.orEmpty() }.trim()
                        if (displayText.isNotBlank()) {
                            val anchorYFrac = (state.coverOffsetYFrac - 0.16f).coerceIn(0.08f, 0.34f)
                            val scaledFontSp = ((canvasW / 320f) * if (displayText.length > 16) 28f else 36f)
                                .coerceIn(22f, 44f)
                            Text(
                                text = displayText.uppercase(),
                                style = TextStyle(
                                    fontSize = scaledFontSp.sp,
                                    lineHeight = (scaledFontSp * 1.06f).sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.45f),
                                        offset = Offset(0f, 4f),
                                        blurRadius = 12f
                                    )
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp)
                                    .offset { IntOffset(0, (canvasH * anchorYFrac).roundToInt()) }
                            )
                        }
                    }

                    // SEMUA Sampul Album (1 sampai 5 sampul — 100% terkunci di Layer 2!)
                    for (slot in slots) {
                        val track = state.selectedTracks.getOrNull(slot.trackIndex) ?: continue
                        val sideDp = canvasWDp * slot.widthFrac
                        val sidePx = canvasW * slot.widthFrac
                        val leftPx = (canvasW * slot.centerXFrac - sidePx * 0.5f).roundToInt()
                        val topPx = (canvasH * slot.centerYFrac - sidePx * 0.5f).roundToInt()

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(leftPx, topPx) }
                                .size(sideDp)
                                .graphicsLayer(
                                    rotationZ = slot.rotationDeg,
                                    alpha = slot.alpha
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Glow tipis adaptif warna sampul di belakang cover
                            if (state.coverGlowEnabled) {
                                Canvas(modifier = Modifier.requiredSize(sideDp * 1.48f)) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                state.palette.glowCompose.copy(alpha = 0.48f),
                                                state.palette.glowCompose.copy(alpha = 0.16f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(
                                        elevation = 18.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        ambientColor = Color.Black.copy(alpha = 0.55f),
                                        spotColor = Color.Black.copy(alpha = 0.55f)
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(state.palette.glowCompose, state.palette.darkCompose)
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.34f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                if (track.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(track.coverUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = track.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // LAYER 3 (zIndex 2f): POTONGAN OBJEK TRANSPARAN 1:1
                // ContentScale.Crop + graphicsLayer IDENTIK 100% DENGAN LAYER 1!
                // -------------------------------------------------------------
                if (state.depthEnabled && uprightPhoto != null && foregroundCutout != null) {
                    Image(
                        bitmap = foregroundCutout.asImageBitmap(),
                        contentDescription = "Objek Depan",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(2f)
                            .graphicsLayer(
                                scaleX = state.photoScale,
                                scaleY = state.photoScale,
                                translationX = photoTxPx,
                                translationY = photoTyPx
                            )
                    )
                }
            }

            // =================================================================
            // SIBLING B (zIndex 3f): LAYER 4 — GRADASI BAWAH + KARTU LIQUID GLASS
            // 0 THUMBNAIL SAMPUL DI DEPAN OBJEK!
            // =================================================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
            ) {
                // Gradasi hitam transparan di 38% bagian bawah
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.38f)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.22f),
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                )

                // CTA ajakan pilih foto jika user belum memilih foto galeri
                if (uprightPhoto == null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = 58.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.20f))
                            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                            .clickable(onClick = onPickPhoto)
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Pilih Foto Sendiri untuk Efek Kedalaman",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Kartu Mini-Player "Liquid Glass" (Bersih — TANPA Thumbnail Sampul Depan!)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.showBottomGlassCard && leadTrack != null) {
                        val cardTint = when (state.glassCardTheme) {
                            GlassCardTheme.LIGHT_GLASS -> Color.White.copy(alpha = 0.24f)
                            GlassCardTheme.DARK_GLASS -> Color(0xFF0A0E18).copy(alpha = 0.42f)
                            GlassCardTheme.ADAPTIVE_COVER -> state.palette.darkCompose.copy(alpha = 0.48f)
                        }
                        val glassHazeStyle = remember(cardTint, state.palette.darkMuted) {
                            HazeStyle(
                                backgroundColor = Color(0xFF0C1018),
                                tints = listOf(HazeTint(color = cardTint)),
                                blurRadius = 20.dp,
                                noiseFactor = 0.02f
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .hazeEffect(state = posterHazeState, style = glassHazeStyle)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.36f),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Baris Atas: Judul & Artis (kiri) + Tombol Play/Pause (kanan) — 0 SAMPUL!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = leadTrack.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = leadTrack.artist,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "Putar",
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            // Baris Bawah: Progress Bar ramping berwarna accentColor + waktu tipis
                            if (state.showProgressBar) {
                                val durSec = leadTrack.durationSec.takeIf { it > 0 } ?: 214
                                val elapsedSec = (durSec * state.progressFraction).roundToInt().coerceIn(0, durSec)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(Color.White.copy(alpha = 0.25f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(state.progressFraction.coerceIn(0.05f, 0.95f))
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(99.dp))
                                                .background(state.palette.accentCompose)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = formatDuration(elapsedSec),
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.70f)
                                        )
                                        Text(
                                            text = formatDuration(durSec),
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.70f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.showWatermark) {
                        Text(
                            text = "SPOTYFLOW",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }

        // Overlay status proses segmentasi AI
        if (statusOverlayText != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .background(Color.Black.copy(alpha = 0.48f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.88f))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.4.dp, modifier = Modifier.size(24.dp))
                    Text(
                        text = statusOverlayText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// =========================================================================
// SONG PICKER DIALOG (1-5 LAGU TANPA MEMUTUS AUDIO AKTIF!)
// =========================================================================
@Composable
private fun PosterSongPickerDialog(
    uiState: PlayerUiState,
    selectedTracks: List<TrackItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<TrackItem>) -> Unit
) {
    val app = LocalSpotyAppearance.current
    val ink = app.textPrimary
    val sub = app.textSecondary
    val accent = app.accent
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf(selectedTracks) }
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<TrackItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val basePool = remember(uiState.currentTrack, uiState.likedTracks, uiState.recentTracks, uiState.homeTracks) {
        val seen = HashSet<String>()
        buildList {
            listOfNotNull(uiState.currentTrack).forEach { if (seen.add(it.id)) add(it) }
            uiState.likedTracks.forEach { if (seen.add(it.id)) add(it) }
            uiState.recentTracks.forEach { if (seen.add(it.id)) add(it) }
            uiState.homeTracks.forEach { if (seen.add(it.id)) add(it) }
        }
    }

    val displayedPool = if (searchText.isNotBlank() && searchResults.isNotEmpty()) searchResults else basePool

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (app.isDark) Color(0xFF161923) else Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pilih Lagu (${draft.size}/5)", fontWeight = FontWeight.ExtraBold, color = ink)
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Tutup",
                    tint = sub,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("Cari lagu lain...", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent)
                            .clickable {
                                val q = searchText.trim()
                                if (q.isNotEmpty()) {
                                    scope.launch {
                                        isSearching = true
                                        searchResults = InnerTubeMusicClient.searchAll(q, SearchFilter.SONGS).tracks.take(20)
                                        isSearching = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = "Cari", tint = Color.White)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 310.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(displayedPool, key = { it.id }) { track ->
                        val isSelected = draft.any { it.id == track.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) accent.copy(alpha = 0.14f) else ink.copy(alpha = 0.05f))
                                .clickable {
                                    draft = if (isSelected) {
                                        if (draft.size > 1) draft.filterNot { it.id == track.id } else draft
                                    } else {
                                        if (draft.size < 5) draft + track else draft
                                    }
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SpotyArtwork(
                                coverUrl = track.coverUrl,
                                accentColor = track.accentColor,
                                size = 40.dp,
                                cornerRadius = 10.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    fontSize = 11.5.sp,
                                    color = sub,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Dipilih",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) {
                Text("Gunakan (${draft.size})", fontWeight = FontWeight.Bold, color = accent)
            }
        }
    )
}

// =========================================================================
// COMPACT CONTROL PRIMITIVES
// =========================================================================
@Composable
private fun ActionChipPill(
    icon: ImageVector,
    label: String,
    highlighted: Boolean,
    accent: Color,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlighted) accent else ink.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (highlighted) Color.White else ink,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) Color.White else ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    ink: Color,
    sub: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink)
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent
            )
        )
    }
}

@Composable
private fun CompactSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    ink: Color,
    sub: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink)
            Text(text = valueText, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = sub)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = ink.copy(alpha = 0.14f)
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}
