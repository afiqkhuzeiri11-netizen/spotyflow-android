package com.spotyflow.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.local.AppSettings
import com.spotyflow.android.data.local.AppSettingsStore
import com.spotyflow.android.data.local.AppThemeMode
import com.spotyflow.android.data.local.LyricAlignMode
import com.spotyflow.android.data.local.MusicAlarm
import com.spotyflow.android.data.local.OfflineDownloadManager
import com.spotyflow.android.data.local.UiStyleMode
import com.spotyflow.android.data.local.WaterDropDensity
import com.spotyflow.android.player.SpotyFlowPlaybackService
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.glass.SpotyGlassCircleButton
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SettingsCategory(val title: String, val subtitle: String, val icon: ImageVector) {
    AUDIO("Suara dan pemutaran", "Audio FX, Timer tidur, Alarm, 13 Antrean & Lainnya", Icons.Rounded.Equalizer),
    APPEARANCE("Tampilan", "Tema, Kaca/Editorial, Real Water Drops, Dynamic Island & Widget", Icons.Rounded.Palette),
    LANGUAGE("Bahasa dan konten", "Bahasa, Wilayah Konten & Kualitas Audio", Icons.Rounded.Language),
    LYRICS("Lirik dan terjemahan", "Terjemahan Otomatis, Romanisasi, Offset & Posisi", Icons.Rounded.Lyrics),
    PRIVACY("Privasi dan riwayat", "Jeda riwayat, Mode Samaran & Hapus Riwayat", Icons.Rounded.Security),
    STORAGE("Penyimpanan", "Cache Aman & Kelola Unduhan Offline M4A", Icons.Rounded.Storage),
    BACKUP("Cadangan dan impor", "Ekspor/Impor .json & Impor Playlist Spotify/YTM", Icons.Rounded.Backup),
    ANDROID_AUTO("Android Auto", "Prioritas Offline & Kontrol Suara di Mobil", Icons.Rounded.DirectionsCar),
    LINKS("Tautan", "Situs Resmi SpotyFlow, Rilis APK & Bantuan", Icons.Rounded.Link),
    ABOUT("Tentang SpotyFlow", "v2.9.0-native (Build 13) • Media3 + Haze + ML Kit", Icons.Rounded.Info)
}

@Composable
fun SettingsRootScreen(
    onBack: () -> Unit,
    onOpenDynamicIsland: () -> Unit,
    onOpenWidgetSettings: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onImportPlaylistUrl: (String) -> Unit,
    onClearHistory: () -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppSettingsStore.settings.collectAsState()
    var activeCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    BackHandler(enabled = true) {
        if (activeCategory != null) activeCategory = null else onBack()
    }

    fun updateS(transform: (AppSettings) -> AppSettings) {
        AppSettingsStore.update(context, transform)
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val json = AppSettingsStore.exportFullBackupJson(context)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            onShowToast(if (ok) "Cadangan .json berhasil diekspor" else "Gagal mengekspor cadangan")
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@withContext false
                    AppSettingsStore.importFullBackupJson(context, text)
                } catch (_: Throwable) {
                    false
                }
            }
            onShowToast(if (ok) "Cadangan .json berhasil dipulihkan" else "File cadangan tidak valid")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = SpotySpacing.BottomScrollClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item(key = "set_hdr") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpotyGlassCircleButton(
                    onClick = { if (activeCategory != null) activeCategory = null else onBack() },
                    size = 40.dp,
                    useFloatingLayer = false
                ) {
                    Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Kembali", tint = SpotyColors.Slate900, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(
                        text = activeCategory?.title ?: "Pengaturan",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SpotyColors.Slate900
                    )
                    Text(
                        text = activeCategory?.subtitle ?: "10 Kategori Lengkap SpotyFlow Native",
                        fontSize = 11.5.sp,
                        color = SpotyColors.Slate500
                    )
                }
            }
        }

        if (activeCategory == null) {
            // 3 Clean Glass Card Groups (10 Categories)
            item(key = "grp_1") {
                SettingsCategoryGroup(
                    eyebrow = "PENGALAMAN UTAMA",
                    categories = listOf(SettingsCategory.AUDIO, SettingsCategory.APPEARANCE, SettingsCategory.LANGUAGE, SettingsCategory.LYRICS),
                    onSelect = { activeCategory = it }
                )
            }
            item(key = "grp_2") {
                SettingsCategoryGroup(
                    eyebrow = "DATA & INTEGRASI",
                    categories = listOf(SettingsCategory.PRIVACY, SettingsCategory.STORAGE, SettingsCategory.BACKUP, SettingsCategory.ANDROID_AUTO),
                    onSelect = { activeCategory = it }
                )
            }
            item(key = "grp_3") {
                SettingsCategoryGroup(
                    eyebrow = "INFORMASI",
                    categories = listOf(SettingsCategory.LINKS, SettingsCategory.ABOUT),
                    onSelect = { activeCategory = it }
                )
            }
        } else {
            item(key = "sub_${activeCategory!!.name}") {
                when (activeCategory!!) {
                    SettingsCategory.AUDIO -> AudioSettingsPage(
                        s = settings,
                        onUpdate = ::updateS,
                        onOpenEqualizer = onOpenEqualizer,
                        onShowToast = onShowToast
                    )
                    SettingsCategory.APPEARANCE -> AppearanceSettingsPage(
                        s = settings,
                        onUpdate = ::updateS,
                        onOpenDynamicIsland = onOpenDynamicIsland,
                        onOpenWidgetSettings = onOpenWidgetSettings
                    )
                    SettingsCategory.LANGUAGE -> LanguageSettingsPage(s = settings, onUpdate = ::updateS)
                    SettingsCategory.LYRICS -> LyricsSettingsPage(s = settings, onUpdate = ::updateS)
                    SettingsCategory.PRIVACY -> PrivacySettingsPage(
                        s = settings,
                        onUpdate = ::updateS,
                        onClearHistory = onClearHistory
                    )
                    SettingsCategory.STORAGE -> StorageSettingsPage(onShowToast = onShowToast)
                    SettingsCategory.BACKUP -> BackupSettingsPage(
                        onExportJson = { exportBackupLauncher.launch("spotyflow_backup.json") },
                        onImportJson = { importBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
                        onImportPlaylistUrl = onImportPlaylistUrl
                    )
                    SettingsCategory.ANDROID_AUTO -> AndroidAutoSettingsPage(s = settings, onUpdate = ::updateS)
                    SettingsCategory.LINKS -> LinksSettingsPage(onShowToast = onShowToast)
                    SettingsCategory.ABOUT -> AboutSettingsPage()
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryGroup(
    eyebrow: String,
    categories: List<SettingsCategory>,
    onSelect: (SettingsCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpotySpacing.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = eyebrow,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = SpotyColors.Slate500,
            modifier = Modifier.padding(start = 4.dp)
        )
        SpotyGlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), tintAlpha = 0.68f) {
            Column(modifier = Modifier.fillMaxWidth()) {
                categories.forEachIndexed { idx, cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(cat) }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SpotyColors.Slate900.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(cat.icon, contentDescription = cat.title, tint = SpotyColors.Slate900, modifier = Modifier.size(19.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cat.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                            Text(cat.subtitle, fontSize = 11.5.sp, color = SpotyColors.Slate500)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SpotyColors.Slate500, modifier = Modifier.size(20.dp))
                    }
                    if (idx < categories.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp, end = 16.dp), thickness = 0.5.dp, color = SpotyColors.HairlineDivider)
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSettingsPage(
    s: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpenEqualizer: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    var alarmHour by remember { mutableIntStateOf(7) }
    var alarmMinute by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsCard("Mesin Audio Hardware & Efek") {
            SettingsActionLink("Ekualiser 5-Band & Preset DSP", if (s.eqEnabled) "Aktif (${s.eqPreset.label})" else "Nonaktif", Icons.Rounded.Equalizer, onOpenEqualizer)
            SettingsSwitch("Lewati keheningan (Skip Silence)", s.skipSilence) { v -> onUpdate { it.copy(skipSilence = v) } }
            SettingsSwitch("Normalisasi audio (Loudness)", s.normalizeAudio) { v -> onUpdate { it.copy(normalizeAudio = v) } }
            SettingsSlider("Tingkat kelantangan", s.loudnessGainMb.toFloat(), 0f..1000f, " mB") { v -> onUpdate { it.copy(loudnessGainMb = v.toInt()) } }
            SettingsSwitch("Audio offload hemat baterai", s.audioOffload) { v -> onUpdate { it.copy(audioOffload = v) } }
            SettingsSwitch("Varispeed (Kecepatan & Nada)", s.varispeedEnabled) { v -> onUpdate { it.copy(varispeedEnabled = v) } }
            if (s.varispeedEnabled) {
                SettingsSlider("Kecepatan", s.playbackSpeed, 0.5f..2.0f, "x") { v -> onUpdate { it.copy(playbackSpeed = v) } }
                SettingsSlider("Nada (Pitch)", s.playbackPitch, 0.5f..2.0f, "x") { v -> onUpdate { it.copy(playbackPitch = v) } }
            }
            SettingsSlider("Crossfade antar lagu", s.crossfadeSec.toFloat(), 0f..12f, " dtk") { v -> onUpdate { it.copy(crossfadeSec = v.toInt()) } }
            SettingsSwitch("Lompatan progresif (+5s kumulatif)", s.progressiveSeek) { v -> onUpdate { it.copy(progressiveSeek = v) } }
            SettingsSwitch("Tetap putar saat bermain game", s.keepPlayingInGames) { v -> onUpdate { it.copy(keepPlayingInGames = v) } }
            SettingsSwitch("Hentikan musik saat aplikasi ditutup", s.stopOnAppClose) { v -> onUpdate { it.copy(stopOnAppClose = v) } }
            SettingsSwitch("Jeda otomatis saat volume bisu", s.pauseOnMute) { v -> onUpdate { it.copy(pauseOnMute = v) } }
            SettingsSwitch("Lanjutkan saat Bluetooth tersambung", s.resumeOnBluetooth) { v -> onUpdate { it.copy(resumeOnBluetooth = v) } }
        }

        SettingsCard("Timer Tidur & Alarm Musik") {
            SettingsSwitch("Memudar lembut 8 detik sebelum berhenti", s.sleepFadeOut) { v -> onUpdate { it.copy(sleepFadeOut = v) } }
            SettingsSwitch("Berhenti di akhir lagu", s.sleepStopAtTrackEnd) { v -> onUpdate { it.copy(sleepStopAtTrackEnd = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(15, 30, 45, 60).forEach { mins ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SpotyColors.EmeraldTint)
                            .clickable {
                                SpotyFlowPlaybackService.dispatch(context) { it.startSleepTimer(mins) }
                                onShowToast("Timer tidur disetel $mins menit")
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${mins}m", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Alarm Musik (Fade-in Lembut)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = String.format(java.util.Locale.US, "%02d:%02d", alarmHour, alarmMinute),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SpotyColors.Slate900
                )
                SettingsChip("+1 Jam") { alarmHour = (alarmHour + 1) % 24 }
                SettingsChip("+15 Mnt") { alarmMinute = (alarmMinute + 15) % 60 }
                SettingsChip("Set Alarm", active = true) {
                    val alarm = MusicAlarm(id = (s.alarms.size + 1), hour = alarmHour, minute = alarmMinute, enabled = true)
                    onUpdate { it.copy(alarms = it.alarms + alarm) }
                    SpotyFlowPlaybackService.scheduleMusicAlarm(context, alarm)
                    onShowToast("Alarm disetel pukul ${String.format(java.util.Locale.US, "%02d:%02d", alarmHour, alarmMinute)}")
                }
            }
            s.alarms.forEach { a ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Alarm, contentDescription = null, tint = SpotyColors.DeepEmerald, modifier = Modifier.size(18.dp))
                        Text(String.format(java.util.Locale.US, "%02d:%02d • %s", a.hour, a.minute, a.label), fontSize = 12.5.sp, color = SpotyColors.Slate900)
                    }
                    Switch(
                        checked = a.enabled,
                        onCheckedChange = { en ->
                            val updated = a.copy(enabled = en)
                            onUpdate { st -> st.copy(alarms = st.alarms.map { if (it.id == a.id) updated else it }) }
                            SpotyFlowPlaybackService.scheduleMusicAlarm(context, updated)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpotyColors.EmeraldCrystal)
                    )
                }
            }
        }

        SettingsCard("13 Sakelar Antrean") {
            SettingsSwitch("Simpan antrean saat keluar aplikasi", s.queuePersist) { v -> onUpdate { it.copy(queuePersist = v) } }
            SettingsSwitch("Radio otomatis saat antrean habis", s.queueAutoRadio) { v -> onUpdate { it.copy(queueAutoRadio = v) } }
            SettingsSwitch("Cegah lagu duplikat di antrean", s.queuePreventDuplicates) { v -> onUpdate { it.copy(queuePreventDuplicates = v) } }
            SettingsSwitch("Tampilkan lencana Berikutnya", s.queueShowNextUp) { v -> onUpdate { it.copy(queueShowNextUp = v) } }
            SettingsSwitch("Geser untuk hapus dari antrean", s.queueSwipeToRemove) { v -> onUpdate { it.copy(queueSwipeToRemove = v) } }
            SettingsSwitch("Reset antrean saat pilih lagu baru", s.queueResetOnNewPlay) { v -> onUpdate { it.copy(queueResetOnNewPlay = v) } }
            SettingsSwitch("Crossfade saat lompat manual", s.queueCrossfadeManualSkip) { v -> onUpdate { it.copy(queueCrossfadeManualSkip = v) } }
            SettingsSwitch("Acak pintar (hindari artis sama berurutan)", s.queueSmartShuffle) { v -> onUpdate { it.copy(queueSmartShuffle = v) } }
            SettingsSwitch("Simpan riwayat antrean sebelumnya", s.queueKeepHistory) { v -> onUpdate { it.copy(queueKeepHistory = v) } }
            SettingsSwitch("Prioritaskan file offline M4A", s.queuePrioritizeOffline) { v -> onUpdate { it.copy(queuePrioritizeOffline = v) } }
            SettingsSwitch("Prefetch otomatis lagu berikutnya", s.queueAutoPrefetchNext) { v -> onUpdate { it.copy(queueAutoPrefetchNext = v) } }
            SettingsSwitch("Getar haptik saat urut ulang antrean", s.queueHapticReorder) { v -> onUpdate { it.copy(queueHapticReorder = v) } }
            SettingsSwitch("Bersihkan antrean setelah Timer Tidur", s.queueClearOnSleep) { v -> onUpdate { it.copy(queueClearOnSleep = v) } }
        }

        SettingsCard("10 Sakelar Lainnya (Pemutaran)") {
            SettingsSwitch("Pemutaran tanpa jeda (Gapless)", s.playGapless) { v -> onUpdate { it.copy(playGapless = v) } }
            SettingsSwitch("Bitrate tertinggi di Wi-Fi", s.playHighBitrateWifi) { v -> onUpdate { it.copy(playHighBitrateWifi = v) } }
            SettingsSwitch("Penghemat kuota di jaringan seluler", s.playDataSaverCellular) { v -> onUpdate { it.copy(playDataSaverCellular = v) } }
            SettingsSwitch("Lanjutkan otomatis saat headset terpasang", s.playHeadsetAutoResume) { v -> onUpdate { it.copy(playHeadsetAutoResume = v) } }
            SettingsSwitch("Tekan lama tombol volume untuk skip", s.playVolumeKeySkip) { v -> onUpdate { it.copy(playVolumeKeySkip = v) } }
            SettingsSwitch("Ketuk ganda sampul untuk maju/mundur", s.playDoubleTapSeek) { v -> onUpdate { it.copy(playDoubleTapSeek = v) } }
            SettingsSwitch("Utamakan mode Audio saat buka lagu", s.playPreferAudioOnly) { v -> onUpdate { it.copy(playPreferAudioOnly = v) } }
            SettingsSwitch("Tampilkan lencana codec audio", s.playShowAudioCodecBadge) { v -> onUpdate { it.copy(playShowAudioCodecBadge = v) } }
            SettingsSwitch("Umpan balik getar kontrol pemutar", s.playVibrationFeedback) { v -> onUpdate { it.copy(playVibrationFeedback = v) } }
            SettingsSwitch("Auto-retry NewPipe saat stream error", s.playAutoRetryFallback) { v -> onUpdate { it.copy(playAutoRetryFallback = v) } }
        }
    }
}

@Composable
private fun AppearanceSettingsPage(
    s: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpenDynamicIsland: () -> Unit,
    onOpenWidgetSettings: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsCard("Tema & Gaya Antarmuka") {
            Text("Gaya Tampilan", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate500)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UiStyleMode.entries.forEach { st ->
                    SettingsChip(st.label, active = s.uiStyle == st, modifier = Modifier.weight(1f)) {
                        onUpdate { it.copy(uiStyle = st) }
                    }
                }
            }
            Text("Tema Warna", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate500)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppThemeMode.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { tm ->
                            SettingsChip(tm.label, active = s.themeMode == tm, modifier = Modifier.weight(1f)) {
                                onUpdate { it.copy(themeMode = tm) }
                            }
                        }
                    }
                }
            }
            SettingsSwitch("Efek kaca (Haze Blur 120 FPS)", s.glassBlurEnabled) { v -> onUpdate { it.copy(glassBlurEnabled = v) } }
            SettingsSwitch("Efek lensa kaca AGSL", s.glassLensEnabled) { v -> onUpdate { it.copy(glassLensEnabled = v) } }
            SettingsSwitch("Tetesan Air di Navbar (Real Water Drops)", s.waterDropsEnabled) { v -> onUpdate { it.copy(waterDropsEnabled = v) } }
            if (s.waterDropsEnabled) {
                Text("Kepadatan Tetesan Air", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate500)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WaterDropDensity.entries.forEach { d ->
                        SettingsChip(d.label, active = s.waterDropsDensity == d, modifier = Modifier.weight(1f)) {
                            onUpdate { it.copy(waterDropsDensity = d) }
                        }
                    }
                }
            }
            SettingsSwitch("Ciutkan bilah navigasi saat menggulir", s.collapseNavOnScroll) { v -> onUpdate { it.copy(collapseNavOnScroll = v) } }
            SettingsSwitch("Animasi pegas iOS", s.iosSpringAnimations) { v -> onUpdate { it.copy(iosSpringAnimations = v) } }
            SettingsSwitch("Tampilkan Streak Percikan di Beranda", s.showStreakOnHome) { v -> onUpdate { it.copy(showStreakOnHome = v) } }
            SettingsSwitch("Refresh rate tinggi (120Hz)", s.highRefreshRate120Hz) { v -> onUpdate { it.copy(highRefreshRate120Hz = v) } }

            Text("Warna Aksen", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate500)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0xFF10B981L, 0xFF0EA5E9L, 0xFF6366F1L, 0xFF8B5CF6L, 0xFFEC4899L, 0xFFF97316L).forEach { hex ->
                    val sel = s.accentHex == hex
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(if (sel) 2.5.dp else 1.dp, Color.White, CircleShape)
                            .clickable { onUpdate { it.copy(accentHex = hex) } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sel) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        SettingsCard("Dynamic Island & Widget Layar Utama") {
            SettingsActionLink("Custom Dynamic Island", "6 Gaya + Lirik Live + Kalibrasi Lubang Kamera", Icons.Rounded.Layers, onOpenDynamicIsland)
            SettingsActionLink("Pengaturan Widget", "Live Preview 5 Ukuran Widget Native", Icons.Rounded.Widgets, onOpenWidgetSettings)
        }

        SettingsCard("Pemutar Mini, Lirik & 6 Playlist Otomatis") {
            SettingsSwitch("Bilah progres di Pemutar Mini", s.miniPlayerShowProgress) { v -> onUpdate { it.copy(miniPlayerShowProgress = v) } }
            SettingsSwitch("Geser Pemutar Mini untuk ganti lagu", s.miniPlayerSwipeSkip) { v -> onUpdate { it.copy(miniPlayerSwipeSkip = v) } }
            SettingsSwitch("Lirik kecil di Pemutar Penuh", s.showMiniLyricsInPlayer) { v -> onUpdate { it.copy(showMiniLyricsInPlayer = v) } }
            SettingsSwitch("Lirik di Layar Kunci", s.showLockscreenLyrics) { v -> onUpdate { it.copy(showLockscreenLyrics = v) } }
            HorizontalDivider(thickness = 0.5.dp, color = SpotyColors.HairlineDivider)
            SettingsSwitch("Koleksi: Disukai", s.showColLiked) { v -> onUpdate { it.copy(showColLiked = v) } }
            SettingsSwitch("Koleksi: Diunduh", s.showColDownloaded) { v -> onUpdate { it.copy(showColDownloaded = v) } }
            SettingsSwitch("Koleksi: Tersimpan di cache", s.showColCached) { v -> onUpdate { it.copy(showColCached = v) } }
            SettingsSwitch("Koleksi: Di Perangkat", s.showColDevice) { v -> onUpdate { it.copy(showColDevice = v) } }
            SettingsSwitch("Koleksi: Top 50", s.showColTop50) { v -> onUpdate { it.copy(showColTop50 = v) } }
            SettingsSwitch("Koleksi: Diunggah", s.showColUploaded) { v -> onUpdate { it.copy(showColUploaded = v) } }
        }
    }
}

@Composable
private fun LanguageSettingsPage(s: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard("Bahasa & Wilayah Musik") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Indonesia", "English").forEach { lang ->
                    SettingsChip(lang, active = s.appLanguage == lang, modifier = Modifier.weight(1f)) {
                        onUpdate { it.copy(appLanguage = lang) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ID", "US", "JP", "KR").forEach { reg ->
                    SettingsChip(reg, active = s.contentRegion == reg, modifier = Modifier.weight(1f)) {
                        onUpdate { it.copy(contentRegion = reg) }
                    }
                }
            }
            SettingsSwitch("Saring konten eksplisit", s.filterExplicit) { v -> onUpdate { it.copy(filterExplicit = v) } }
        }
    }
}

@Composable
private fun LyricsSettingsPage(s: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard("Lirik & Terjemahan Pro") {
            SettingsSwitch("Terjemahkan lirik otomatis (Indonesia)", s.lyricsAutoTranslate) { v -> onUpdate { it.copy(lyricsAutoTranslate = v) } }
            SettingsSwitch("Romanisasi otomatis (Korea/Jepang/Rusia)", s.lyricsAutoRomanize) { v -> onUpdate { it.copy(lyricsAutoRomanize = v) } }
            SettingsSwitch("Blur baris lirik non-aktif", s.lyricsBlurInactive) { v -> onUpdate { it.copy(lyricsBlurInactive = v) } }
            SettingsSwitch("Kirim lirik ke Custom Dynamic Island", s.lyricsSyncIsland) { v -> onUpdate { it.copy(lyricsSyncIsland = v) } }
            SettingsSlider("Offset sinkronisasi lirik", s.lyricsOffsetMs / 1000f, -2.0f..2.0f, " dtk") { v ->
                onUpdate { it.copy(lyricsOffsetMs = (v * 1000f).toLong()) }
            }
            Text("Posisi Teks Lirik", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate500)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LyricAlignMode.entries.forEach { al ->
                    SettingsChip(al.label, active = s.lyricsAlign == al, modifier = Modifier.weight(1f)) {
                        onUpdate { it.copy(lyricsAlign = al) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacySettingsPage(
    s: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard("Privasi & Riwayat") {
            SettingsSwitch("Jeda riwayat pemutaran", s.pauseListenHistory) { v -> onUpdate { it.copy(pauseListenHistory = v) } }
            SettingsSwitch("Jeda riwayat pencarian", s.pauseSearchHistory) { v -> onUpdate { it.copy(pauseSearchHistory = v) } }
            SettingsSwitch("Sesi Samaran (Incognito)", s.incognitoSession) { v -> onUpdate { it.copy(incognitoSession = v) } }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SpotyColors.HeartRed.copy(alpha = 0.12f))
                    .clickable(onClick = onClearHistory)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Hapus Riwayat Putar & Pencarian", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SpotyColors.HeartRed)
            }
        }
    }
}

@Composable
private fun StorageSettingsPage(onShowToast: (String) -> Unit) {
    val context = LocalContext.current
    var cacheSizeText by remember { mutableStateOf(OfflineDownloadManager.formatSizeMb(AppSettingsStore.calculateCacheSizeBytes(context))) }
    var offlineSizeText by remember { mutableStateOf(OfflineDownloadManager.formatSizeMb(OfflineDownloadManager.totalOfflineBytes(context))) }

    Column(modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard("Penyimpanan & Cache Aman") {
            Text("Cache Aplikasi & Gambar: $cacheSizeText", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
            Text("Unduhan Offline M4A: $offlineSizeText", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SpotyColors.EmeraldCrystal)
                    .clickable {
                        val freed = AppSettingsStore.clearSafeCache(context)
                        cacheSizeText = OfflineDownloadManager.formatSizeMb(AppSettingsStore.calculateCacheSizeBytes(context))
                        onShowToast("Cache aman dibersihkan (${OfflineDownloadManager.formatSizeMb(freed)})")
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Bersihkan Cache Aman (Tanpa Hapus Offline)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SpotyColors.HeartRed.copy(alpha = 0.12f))
                    .clickable {
                        OfflineDownloadManager.deleteAll(context)
                        offlineSizeText = "0.0 MB"
                        onShowToast("Semua file offline dihapus")
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Hapus Semua Unduhan Offline", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SpotyColors.HeartRed)
            }
        }
    }
}

@Composable
private fun BackupSettingsPage(
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onImportPlaylistUrl: (String) -> Unit
) {
    var importUrl by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard("Cadangan Pengaturan & Pustaka (.json)") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsChip("Ekspor .json", active = true, modifier = Modifier.weight(1f), onClick = onExportJson)
                SettingsChip("Impor .json", active = false, modifier = Modifier.weight(1f), onClick = onImportJson)
            }
        }
        SettingsCard("Impor Playlist Spotify & YouTube Music") {
            OutlinedTextField(
                value = importUrl,
                onValueChange = { importUrl = it },
                placeholder = { Text("Tempel tautan Spotify / YouTube Music...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SettingsChip("Impor ke Pustaka", active = true, modifier = Modifier.fillMaxWidth()) {
                if (importUrl.isNotBlank()) {
                    onImportPlaylistUrl(importUrl.trim())
                    importUrl = ""
                }
            }
        }
    }
}

@Composable
private fun AndroidAutoSettingsPage(s: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard("Android Auto") {
            SettingsSwitch("Prioritaskan lagu Offline di mobil", s.androidAutoShowOfflineFirst) { v -> onUpdate { it.copy(androidAutoShowOfflineFirst = v) } }
            SettingsSwitch("Dukungan pencarian suara", s.androidAutoVoiceSearch) { v -> onUpdate { it.copy(androidAutoVoiceSearch = v) } }
            SettingsSwitch("Tampilkan kartu lirik di layar mobil", s.androidAutoShowLyricsCard) { v -> onUpdate { it.copy(androidAutoShowLyricsCard = v) } }
        }
    }
}

@Composable
private fun LinksSettingsPage(onShowToast: (String) -> Unit) {
    val context = LocalContext.current
    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (_: Throwable) {
            onShowToast("Tidak dapat membuka tautan")
        }
    }
    Column(modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard("Tautan Resmi") {
            SettingsActionLink("Portal Web & Unduh APK", "https://spotyflow.vercel.app/download", Icons.Rounded.Link) {
                openUrl("https://spotyflow.vercel.app/download")
            }
            SettingsActionLink("Repositori GitHub SpotyFlow", "github.com/afiqkhuzeiri11-netizen/spotyflow-android", Icons.Rounded.Link) {
                openUrl("https://github.com/afiqkhuzeiri11-netizen/spotyflow-android")
            }
        }
    }
}

@Composable
private fun AboutSettingsPage() {
    Column(modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard("SpotyFlow Native v2.9.0-native") {
            Text("Build 13 • 100% Native Kotlin + Jetpack Compose 120 FPS", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
            Text(
                text = "• Pure White x Full Liquid Glass (Haze 1.6.10) + Real Water Drops AGSL\n• Poster Lagu 3D Pro + ML Kit Subject AI (Motor, Mobil, Orang, Objek Gelap + 4-Stage Matte)\n• Mesin Pencari Live ala YouTube Music (< 200ms Autocomplete + Infinite Scroll)\n• Banner Artis HD (YTM + TheAudioDB + Deezer), Full Album & Kredit Lagu Resmi\n• Streaming Cepat On-Device (InnerTube + NewPipe + Media3 ExoPlayer 1.6.1)\n• Custom Dynamic Island Pro via AccessibilityService (6 Gaya + Lirik Live)\n• 5 Ukuran Widget Layar Utama Native (1x1 s.d. 4x2)",
                fontSize = 12.sp,
                color = SpotyColors.Slate700,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    SpotyGlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), tintAlpha = 0.68f) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
            content()
        }
    }
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SpotyColors.Slate900, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpotyColors.EmeraldCrystal)
        )
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = SpotyColors.Slate900)
            val txt = if (unit == "x" || unit == " dtk") String.format(java.util.Locale.US, "%.1f%s", value, unit) else "${value.toInt()}$unit"
            Text(txt, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald)
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
private fun SettingsActionLink(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SpotyColors.Slate900.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = SpotyColors.DeepEmerald, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
            Text(subtitle, fontSize = 11.5.sp, color = SpotyColors.Slate500)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SpotyColors.Slate500, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsChip(label: String, active: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) SpotyColors.EmeraldCrystal else SpotyColors.Slate900.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else SpotyColors.Slate900)
    }
}
