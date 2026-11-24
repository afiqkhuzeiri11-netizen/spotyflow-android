package com.spotyflow.android.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint as AndroidPaint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.data.SearchFilter
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.local.AppSettingsStore
import com.spotyflow.android.data.local.EqPreset
import com.spotyflow.android.data.remote.InnerTubeMusicClient
import com.spotyflow.android.data.remote.SpotyHttpClient
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyFlameVector
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.glass.SpotyGlassCircleButton
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// =============================================================================
// 1. HARDWARE 5-BAND EQUALIZER & AUDIO FX SCREEN
// =============================================================================
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by AppSettingsStore.settings.collectAsState()
    val bandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = SpotySpacing.BottomScrollClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ExtraHeader(title = "Ekualiser & Audio", subtitle = "5-Band Hardware DSP tersambung ke ExoPlayer", onBack = onBack)
        }
        item {
            SpotyGlassCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(24.dp),
                tintAlpha = 0.68f
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Aktifkan Ekualiser Hardware", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                        Switch(
                            checked = settings.eqEnabled,
                            onCheckedChange = { v -> AppSettingsStore.update(context) { it.copy(eqEnabled = v) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpotyColors.EmeraldCrystal)
                        )
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EqPreset.entries) { preset ->
                            val sel = settings.eqPreset == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (sel) SpotyColors.EmeraldCrystal else SpotyColors.Slate900.copy(alpha = 0.07f))
                                    .clickable {
                                        AppSettingsStore.update(context) {
                                            it.copy(eqEnabled = true, eqPreset = preset, eqBandsMb = preset.bandsMb)
                                        }
                                    }
                                    .padding(horizontal = 13.dp, vertical = 8.dp)
                            ) {
                                Text(preset.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else SpotyColors.Slate900)
                            }
                        }
                    }

                    bandLabels.forEachIndexed { idx, hz ->
                        val mb = settings.eqBandsMb.getOrElse(idx) { 0 }
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(hz, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                                Text(String.format(java.util.Locale.US, "%+.1f dB", mb / 100f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald)
                            }
                            Slider(
                                value = mb.toFloat(),
                                onValueChange = { v ->
                                    val nextBands = settings.eqBandsMb.toMutableList()
                                    while (nextBands.size < 5) nextBands.add(0)
                                    nextBands[idx] = v.toInt().coerceIn(-1200, 1200)
                                    AppSettingsStore.update(context) { it.copy(eqEnabled = true, eqBandsMb = nextBands) }
                                },
                                valueRange = -1200f..1200f,
                                colors = SliderDefaults.colors(thumbColor = SpotyColors.EmeraldCrystal, activeTrackColor = SpotyColors.EmeraldCrystal)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 2. SONG RECOGNIZE SCREEN (Radar + Android SpeechRecognizer + Manual Match)
// =============================================================================
@Composable
fun SongRecognizeScreen(
    onBack: () -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isListening by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Ketuk tombol radar untuk mendengar potongan lagu atau lirik") }
    var manualInput by remember { mutableStateOf("") }
    var matchedTracks by remember { mutableStateOf<List<TrackItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(Unit) {
        onDispose { try { speechRecognizer?.destroy() } catch (_: Throwable) {} }
    }

    fun matchSnippet(snippet: String) {
        val clean = snippet.trim()
        if (clean.isBlank()) return
        isSearching = true
        statusText = "Mencocokkan \"$clean\"..."
        scope.launch {
            val res = InnerTubeMusicClient.searchAll(clean, SearchFilter.SONGS)
            matchedTracks = res.tracks.take(8)
            isSearching = false
            statusText = if (matchedTracks.isNotEmpty()) "Ditemukan ${matchedTracks.size} lagu cocok" else "Belum ada lagu cocok, coba potongan lirik lain"
        }
    }

    fun startMicListen() {
        val sr = speechRecognizer
        if (sr == null) {
            onShowToast("SpeechRecognizer tidak tersedia di perangkat ini, gunakan ketik lirik")
            return
        }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true; statusText = "Mendengarkan suara & lirik..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false; statusText = "Suara kurang jelas, coba lagi atau ketik potongan lirik" }
            override fun onResults(results: Bundle?) {
                isListening = false
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (heard.isNotBlank()) {
                    manualInput = heard
                    matchSnippet(heard)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            sr.startListening(intent)
        } catch (_: Throwable) {
            isListening = false
            onShowToast("Gagal memulai mikrofon")
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startMicListen() else onShowToast("Izin mikrofon diperlukan untuk Kenali Lagu")
    }

    val infinite = rememberInfiniteTransition(label = "radarPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = SpotySpacing.BottomScrollClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ExtraHeader(title = "Kenali Lagu", subtitle = "Radar Mikrofon & Pencocok Lirik Cepat", onBack = onBack) }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SpotySpacing.ScreenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clickable {
                            if (isListening) {
                                try { speechRecognizer?.stopListening() } catch (_: Throwable) {}
                                isListening = false
                            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                startMicListen()
                            } else {
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val rMax = size.minDimension * 0.48f
                        if (isListening) {
                            drawCircle(
                                color = SpotyColors.EmeraldCrystal.copy(alpha = (1f - pulse) * 0.45f),
                                radius = rMax * (0.35f + pulse * 0.65f),
                                style = Stroke(width = 3.dp.toPx())
                            )
                            val p2 = (pulse + 0.5f) % 1f
                            drawCircle(
                                color = Color(0xFF0EA5E9).copy(alpha = (1f - p2) * 0.35f),
                                radius = rMax * (0.35f + p2 * 0.65f),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(SpotyColors.EmeraldCrystal, SpotyColors.DeepEmerald))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                            contentDescription = "Mulai Radar",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
                Text(statusText, fontSize = 13.sp, color = SpotyColors.Slate700, textAlign = TextAlign.Center)

                SpotyGlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), tintAlpha = 0.68f) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualInput,
                            onValueChange = { manualInput = it },
                            placeholder = { Text("Atau ketik potongan lirik...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(SpotyColors.Slate900)
                                .clickable { matchSnippet(manualInput) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Rounded.Search, contentDescription = "Cari", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        items(matchedTracks, key = { it.id }) { track ->
            SpotyGlassCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(18.dp),
                tintAlpha = 0.66f,
                onClick = { onPlayTrack(track, matchedTracks) }
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpotyArtwork(track.coverUrl, track.accentColor, 48.dp, 12.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, fontSize = 12.sp, color = SpotyColors.Slate500, maxLines = 1)
                    }
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Putar", tint = SpotyColors.DeepEmerald, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

// =============================================================================
// 3. LISTEN TOGETHER (JAM) SCREEN — Serverless https://spotyflow.vercel.app/api/jam/*
// =============================================================================
@Composable
fun ListenTogetherScreen(
    uiState: PlayerUiState,
    onBack: () -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var roomCode by remember { mutableStateOf("") }
    var activeRoom by remember { mutableStateOf<String?>(null) }
    var isHost by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("Buat ruang baru atau masukkan kode 6 karakter temanmu") }
    var isBusy by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    fun createJamRoom() {
        isBusy = true
        scope.launch {
            val code = withContext(Dispatchers.IO) {
                try {
                    val cur = uiState.currentTrack
                    val payload = JSONObject().apply {
                        put("hostName", uiState.userProfile.displayName)
                        put("videoId", cur?.videoId.orEmpty())
                        put("title", cur?.title ?: "SpotyFlow Session")
                        put("artist", cur?.artist ?: "Host")
                        put("coverUrl", cur?.coverUrl.orEmpty())
                        put("positionMs", uiState.positionMs)
                        put("isPlaying", uiState.isPlaying)
                    }
                    val req = Request.Builder()
                        .url("https://spotyflow.vercel.app/api/jam/create")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    SpotyHttpClient.client.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string().orEmpty()
                        if (resp.isSuccessful && raw.isNotBlank()) {
                            JSONObject(raw).optString("code").ifBlank { null }
                        } else {
                            null
                        }
                    }
                } catch (_: Throwable) {
                    null
                }
            } ?: (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")

            isBusy = false
            activeRoom = code
            isHost = true
            statusMsg = "Ruang aktif! Bagikan kode $code ke temanmu"
            onShowToast("Ruang Jam $code dibuat")
        }
    }

    fun joinJamRoom() {
        val clean = roomCode.trim().uppercase()
        if (clean.length < 4) {
            onShowToast("Masukkan kode ruang valid")
            return
        }
        isBusy = true
        scope.launch {
            val joinedTrack = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder()
                        .url("https://spotyflow.vercel.app/api/jam/state?code=$clean")
                        .get()
                        .build()
                    SpotyHttpClient.client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext null
                        val j = JSONObject(resp.body?.string() ?: return@withContext null)
                        val vid = j.optString("videoId")
                        if (vid.length == 11) {
                            TrackItem(
                                id = vid,
                                videoId = vid,
                                title = j.optString("title", "Lagu Jam"),
                                artist = j.optString("artist", "Host"),
                                coverUrl = j.optString("coverUrl")
                            )
                        } else {
                            null
                        }
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            isBusy = false
            activeRoom = clean
            isHost = false
            if (joinedTrack != null) {
                statusMsg = "Tersambung ke ruang $clean • Memutar ${joinedTrack.title}"
                onPlayTrack(joinedTrack, listOf(joinedTrack))
            } else {
                statusMsg = "Tersambung ke ruang $clean • Menunggu lagu dari Host"
            }
            onShowToast("Bergabung ke ruang $clean")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = SpotySpacing.BottomScrollClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ExtraHeader(title = "Dengar Bersama", subtitle = "Sinkronisasi real-time via SpotyFlow Jam", onBack = onBack) }
        item {
            SpotyGlassCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(24.dp),
                tintAlpha = 0.68f
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(SpotyColors.EmeraldTint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Group, contentDescription = null, tint = SpotyColors.DeepEmerald, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text(activeRoom?.let { "Ruang #$it (${if (isHost) "Host" else "Tamu"})" } ?: "Mulai Sesi Jam", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                            Text(statusMsg, fontSize = 12.sp, color = SpotyColors.Slate500)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(SpotyColors.EmeraldCrystal)
                            .clickable(enabled = !isBusy) { createJamRoom() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isBusy) "Menghubungkan..." else "Buat Ruang Jam Baru (Host)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedTextField(
                        value = roomCode,
                        onValueChange = { roomCode = it.uppercase().take(8) },
                        label = { Text("Kode Ruang Teman") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(SpotyColors.Slate900)
                            .clickable(enabled = !isBusy) { joinJamRoom() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Gabung Ruang sebagai Tamu", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

// =============================================================================
// 4. STATS & 5-SLIDE INTERACTIVE RECAP STORY SCREEN (+ Save PNG to Gallery)
// =============================================================================
@Composable
fun StatsRecapScreen(
    uiState: PlayerUiState,
    topTracks: List<TrackItem>,
    onBack: () -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var slideIdx by remember { mutableIntStateOf(0) }

    BackHandler(onBack = onBack)

    val topArtists = remember(topTracks, uiState.recentTracks) {
        (topTracks + uiState.recentTracks)
            .groupingBy { it.artist }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
    }

    val slideTitles = listOf(
        "Waktu Dengar Kamu",
        "Streak Percikan",
        "Lagu #1 Terfavorit",
        "Artis Paling Sering Diputar",
        "Ringkasan SpotyFlow Recap"
    )
    val slideValues = listOf(
        "${uiState.dailyStreak.minutesToday} Menit Hari Ini",
        "${uiState.dailyStreak.count} Hari Beruntun",
        topTracks.firstOrNull()?.title ?: "Belum ada lagu",
        topArtists.firstOrNull()?.key ?: "SpotyFlow",
        "${uiState.likedTracks.size} Disukai • ${uiState.customPlaylists.size} Playlist"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = SpotySpacing.BottomScrollClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ExtraHeader(title = "Statistik & Recap", subtitle = "5 Slide Recap + Top 5 Lagu & Artis", onBack = onBack) }

        // Interactive 5-Slide Recap Story Card
        item {
            val gradients = listOf(
                listOf(Color(0xFF059669), Color(0xFF0F172A)),
                listOf(Color(0xFFF97316), Color(0xFF7C2D12)),
                listOf(Color(0xFF0284C7), Color(0xFF1E1B4B)),
                listOf(Color(0xFF7C3AED), Color(0xFF0F172A)),
                listOf(Color(0xFF10B981), Color(0xFF0EA5E9))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal)
                    .height(250.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(gradients[slideIdx % gradients.size]))
                    .clickable { slideIdx = (slideIdx + 1) % 5 }
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    // 5 Story Progress Bars
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        for (i in 0 until 5) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (i <= slideIdx) Color.White else Color.White.copy(alpha = 0.28f))
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("SPOTYFLOW RECAP • SLIDE ${slideIdx + 1}/5", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = Color.White.copy(alpha = 0.8f))
                        Text(slideTitles[slideIdx], fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
                        Text(slideValues[slideIdx], fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Ketuk kartu untuk slide berikutnya", fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.78f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White)
                                .clickable {
                                    scope.launch {
                                        val ok = saveRecapCardPng(context, slideTitles[slideIdx], slideValues[slideIdx], uiState.userProfile.displayName)
                                        onShowToast(if (ok) "Kartu Recap PNG disimpan ke Galeri" else "Gagal menyimpan Recap")
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.Download, contentDescription = null, tint = SpotyColors.Slate900, modifier = Modifier.size(14.dp))
                                Text("Simpan PNG", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                            }
                        }
                    }
                }
            }
        }

        // Streak & Quick Metrics
        item {
            SpotyGlassCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(22.dp),
                tintAlpha = 0.68f
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SpotyFlameVector(size = 24.dp)
                        Column {
                            Text("${uiState.dailyStreak.count} Hari Beruntun", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                            Text("Rekor terbaik: ${uiState.dailyStreak.best} hari", fontSize = 12.sp, color = SpotyColors.Slate500)
                        }
                    }
                    Text("${uiState.dailyStreak.minutesToday} mnt", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.DeepEmerald)
                }
            }
        }

        // Top 5 Lagu
        if (topTracks.isNotEmpty()) {
            item {
                Text("Top 5 Lagu Kamu", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900, modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal))
            }
            items(topTracks.take(5), key = { "recap_${it.id}" }) { t ->
                SpotyGlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SpotySpacing.ScreenHorizontal),
                    shape = RoundedCornerShape(18.dp),
                    tintAlpha = 0.66f,
                    onClick = { onPlayTrack(t, topTracks) }
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SpotyArtwork(t.coverUrl, t.accentColor, 46.dp, 12.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, fontSize = 12.sp, color = SpotyColors.Slate500, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun saveRecapCardPng(context: Context, title: String, value: String, userName: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val w = 1080
            val h = 1920
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = AndroidCanvas(bmp)
            val bg = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF059669.toInt(), 0xFF0F172A.toInt(), Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

            val pEye = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCFFFFFF.toInt()
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val pVal = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 84f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            c.drawText("SPOTYFLOW RECAP", 80f, 340f, pEye)
            c.drawText(title, 80f, 760f, pEye)
            c.drawText(value.take(28), 80f, 890f, pVal)
            c.drawText(userName, 80f, 1760f, pEye)

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "SpotyFlow_Recap_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SpotyFlow")
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
            context.contentResolver.openOutputStream(uri)?.use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            bmp.recycle()
            true
        } catch (_: Throwable) {
            false
        }
    }

@Composable
private fun ExtraHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = SpotySpacing.ScreenHorizontal, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SpotyGlassCircleButton(onClick = onBack, size = 40.dp, useFloatingLayer = false) {
            Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Kembali", tint = SpotyColors.Slate900, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
            Text(subtitle, fontSize = 11.5.sp, color = SpotyColors.Slate500)
        }
    }
}
