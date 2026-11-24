package com.spotyflow.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.island.DynamicIslandConfig
import com.spotyflow.android.island.DynamicIslandConfigStore
import com.spotyflow.android.island.DynamicIslandStyle
import com.spotyflow.android.island.IslandCoverShape
import com.spotyflow.android.island.IslandLyricMode
import com.spotyflow.android.island.SpotyFlowDynamicIslandService
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.glass.SpotyGlassCircleButton
import com.spotyflow.android.ui.glass.SpotyMiniEqBars
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing

@Composable
fun DynamicIslandSettingsScreen(
    uiState: PlayerUiState,
    onBack: () -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DynamicIslandConfigStore.load(context) }
    val cfg by DynamicIslandConfigStore.config.collectAsState()
    val serviceRunning by SpotyFlowDynamicIslandService.isRunning.collectAsState()
    val accessibilityEnabled = remember(serviceRunning) {
        serviceRunning || isIslandAccessibilityEnabled(context.contentResolver, context.packageName)
    }

    var previewExpanded by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    fun save(transform: (DynamicIslandConfig) -> DynamicIslandConfig) {
        DynamicIslandConfigStore.save(context, transform(cfg))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = SpotySpacing.BottomScrollClearance),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item(key = "di_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SpotyGlassCircleButton(onClick = onBack, size = 40.dp, useFloatingLayer = false) {
                        Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Kembali", tint = SpotyColors.Slate900, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Dynamic Island", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                        Text("Kapsul Aksesibilitas • 6 Gaya + Lirik Live", fontSize = 11.5.sp, color = SpotyColors.Slate500)
                    }
                }
                SpotyGlassCircleButton(
                    onClick = {
                        DynamicIslandConfigStore.reset(context)
                        onShowToast("Kalibrasi Dynamic Island direset")
                    },
                    size = 38.dp,
                    useFloatingLayer = false
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset", tint = SpotyColors.Slate900, modifier = Modifier.size(18.dp))
                }
            }
        }

        // 1. Accessibility Permission Card
        item(key = "di_perm") {
            SpotyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(24.dp),
                tintAlpha = 0.72f
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (accessibilityEnabled) SpotyColors.EmeraldTint else SpotyColors.FlameAmber.copy(alpha = 0.16f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (accessibilityEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.AccessibilityNew,
                                contentDescription = null,
                                tint = if (accessibilityEnabled) SpotyColors.DeepEmerald else SpotyColors.FlameOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (accessibilityEnabled) "Layanan Aksesibilitas Aktif" else "Izin Aksesibilitas Belum Aktif",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SpotyColors.Slate900
                            )
                            Text(
                                text = if (accessibilityEnabled) {
                                    "Dynamic Island siap muncul di atas lubang kamera"
                                } else {
                                    "Aktifkan SpotyFlow Dynamic Island di Pengaturan Aksesibilitas"
                                },
                                fontSize = 12.sp,
                                color = SpotyColors.Slate500
                            )
                        }
                    }

                    if (!accessibilityEnabled) {
                        Text(
                            text = "1. Ketuk tombol hijau di bawah -> pilih Aplikasi Terinstal\n2. Pilih SpotyFlow Dynamic Island lalu nyalakan\n3. Jika Android 13-15 memunculkan Setelan Dibatasi, buka Info Aplikasi -> menu titik 3 -> Izinkan setelan dibatasi.",
                            fontSize = 11.5.sp,
                            color = SpotyColors.Slate700,
                            lineHeight = 16.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(SpotyColors.EmeraldCrystal)
                                    .clickable {
                                        try {
                                            context.startActivity(
                                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        } catch (_: Throwable) {
                                            onShowToast("Buka Pengaturan -> Aksesibilitas secara manual")
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Aktifkan Aksesibilitas", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(SpotyColors.Slate900.copy(alpha = 0.08f))
                                    .clickable {
                                        try {
                                            context.startActivity(
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts("package", context.packageName, null)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        } catch (_: Throwable) {}
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Rounded.Info, contentDescription = null, tint = SpotyColors.Slate900, modifier = Modifier.size(14.dp))
                                    Text("Info Aplikasi", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tampilkan kapsul saat musik diputar", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = SpotyColors.Slate900)
                        Switch(
                            checked = cfg.enabled,
                            onCheckedChange = { save { c -> c.copy(enabled = it) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SpotyColors.EmeraldCrystal)
                        )
                    }
                }
            }
        }

        // 2. Interactive Live Camera-Cutout Preview (Tap to morph Compact <-> Expanded!)
        item(key = "di_live_preview") {
            val accent = uiState.ambientPalette.accent
            val trackTitle = uiState.currentTrack?.title ?: "Monokrom"
            val trackArtist = uiState.currentTrack?.artist ?: "Tulus"
            val sampleLyric = "Lembaran foto hitam putih aku coba ingat lagi..."
            val isLightCapsule = cfg.style == DynamicIslandStyle.LIQUID_GLASS || cfg.style == DynamicIslandStyle.CLEAR_GLASS
            val capText = if (isLightCapsule) SpotyColors.Slate900 else Color.White
            val capSub = if (isLightCapsule) SpotyColors.Slate500 else Color.White.copy(alpha = 0.75f)
            val capBg = when (cfg.style) {
                DynamicIslandStyle.IPHONE -> Brush.verticalGradient(listOf(Color.Black, Color(0xFF0A0B0E)))
                DynamicIslandStyle.LIQUID_GLASS -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.94f), accent.copy(alpha = 0.16f)))
                DynamicIslandStyle.CLEAR_GLASS -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.36f)))
                DynamicIslandStyle.ADAPTIVE_COVER -> Brush.linearGradient(listOf(accent.copy(alpha = 0.92f), Color(0xFF0F172A)))
                DynamicIslandStyle.MINIMAL_PILL -> Brush.verticalGradient(listOf(Color(0xFF141824), Color(0xFF0B0E17)))
                DynamicIslandStyle.NEON_AURA -> Brush.verticalGradient(listOf(Color(0xFF0D1019), Color(0xFF06080D)))
            }
            val capBorder = when (cfg.style) {
                DynamicIslandStyle.NEON_AURA -> accent
                DynamicIslandStyle.LIQUID_GLASS, DynamicIslandStyle.CLEAR_GLASS -> Color.White
                else -> Color.White.copy(alpha = 0.16f)
            }
            val coverCorner = when (cfg.coverShape) {
                IslandCoverShape.CIRCLE -> 999.dp
                IslandCoverShape.ROUNDED -> 8.dp
                IslandCoverShape.SQUARE -> 3.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFE0F2FE), Color(0xFFD1FAE5), Color(0xFFF3E8FF))))
                    .border(1.5.dp, Color.White, RoundedCornerShape(28.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Simulated Phone Status Bar + Punch Hole
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                )

                // Interactive Dynamic Island Capsule
                Box(
                    modifier = Modifier
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        .clip(RoundedCornerShape(if (previewExpanded) 28.dp else cfg.cornerRadiusDp.dp))
                        .background(capBg)
                        .border(
                            width = if (cfg.style == DynamicIslandStyle.NEON_AURA) 1.8.dp else 1.2.dp,
                            color = capBorder,
                            shape = RoundedCornerShape(if (previewExpanded) 28.dp else cfg.cornerRadiusDp.dp)
                        )
                        .clickable { previewExpanded = !previewExpanded }
                        .padding(
                            horizontal = if (previewExpanded) 16.dp else 12.dp,
                            vertical = if (previewExpanded) 14.dp else 7.dp
                        )
                ) {
                    if (!previewExpanded) {
                        Row(
                            modifier = Modifier.width(cfg.compactWidthDp.coerceIn(170f, 270f).dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpotyArtwork(
                                coverUrl = uiState.currentTrack?.coverUrl.orEmpty(),
                                accentColor = accent,
                                size = (cfg.compactHeightDp - 10f).coerceIn(22f, 34f).dp,
                                cornerRadius = coverCorner
                            )
                            Text(
                                text = if (cfg.showLyrics && cfg.lyricMode == IslandLyricMode.REPLACE_TITLE) sampleLyric else trackTitle,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = capText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (cfg.showVisualizer) {
                                SpotyMiniEqBars(isPlaying = true, color = accent)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SpotyArtwork(
                                    coverUrl = uiState.currentTrack?.coverUrl.orEmpty(),
                                    accentColor = accent,
                                    size = 50.dp,
                                    cornerRadius = coverCorner
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(trackTitle, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = capText, maxLines = 1)
                                    Text(trackArtist, fontSize = 11.5.sp, color = capSub, maxLines = 1)
                                }
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = capText, modifier = Modifier.size(18.dp))
                            }
                            if (cfg.showLyrics) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(capText.copy(alpha = 0.08f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(sampleLyric, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = capText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = SpotyColors.HeartRed, modifier = Modifier.size(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = capText, modifier = Modifier.size(24.dp))
                                    Icon(Icons.Rounded.Pause, contentDescription = null, tint = capText, modifier = Modifier.size(26.dp))
                                    Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = capText, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(20.dp))
                            }
                        }
                    }
                }

                Text(
                    text = if (previewExpanded) "Ketuk kapsul untuk menciutkan" else "Ketuk kapsul untuk mengetes animasi Ringkas <-> Mekar",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SpotyColors.Slate700
                )
            }
        }

        // 3. 2x3 Style Picker Grid (6 Styles)
        item(key = "di_styles") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Pilih 6 Gaya Bentuk", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                DynamicIslandStyle.entries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { st ->
                            val sel = cfg.style == st
                            SpotyGlassCard(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                tintAlpha = if (sel) 0.85f else 0.60f,
                                onClick = { save { c -> c.copy(style = st) } }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (sel) Modifier.border(1.8.dp, SpotyColors.EmeraldCrystal, RoundedCornerShape(18.dp))
                                            else Modifier
                                        )
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(st.label, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = if (sel) SpotyColors.DeepEmerald else SpotyColors.Slate900)
                                    Text(st.subtitle, fontSize = 11.sp, color = SpotyColors.Slate500, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Lyrics, Visualizer & Cover Shape Controls
        item(key = "di_behavior") {
            SpotyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(24.dp),
                tintAlpha = 0.66f
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lirik Live & Tampilan", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                    DiToggleRow("Tampilkan lirik sinkron real-time", cfg.showLyrics) { v -> save { it.copy(showLyrics = v) } }
                    DiToggleRow("Gelombang visualizer 4 batang", cfg.showVisualizer) { v -> save { it.copy(showVisualizer = v) } }
                    DiToggleRow("Tetap tampil saat di dalam SpotyFlow", cfg.showInsideSpotyFlow) { v -> save { it.copy(showInsideSpotyFlow = v) } }

                    Text("Mode Lirik Kapsul", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate500)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IslandLyricMode.entries.forEach { m ->
                            val sel = cfg.lyricMode == m
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) SpotyColors.EmeraldTint else SpotyColors.Slate900.copy(alpha = 0.06f))
                                    .clickable { save { it.copy(lyricMode = m) } }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(m.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sel) SpotyColors.DeepEmerald else SpotyColors.Slate900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    Text("Bentuk Sampul", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate500)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IslandCoverShape.entries.forEach { sh ->
                            val sel = cfg.coverShape == sh
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) SpotyColors.Slate900 else SpotyColors.Slate900.copy(alpha = 0.06f))
                                    .clickable { save { it.copy(coverShape = sh) } }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(sh.label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else SpotyColors.Slate900)
                            }
                        }
                    }
                }
            }
        }

        // 5. Precision Camera-Cutout Calibration Sliders
        item(key = "di_calibration") {
            SpotyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(24.dp),
                tintAlpha = 0.66f
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kalibrasi Posisi & Ukuran Lubang Kamera", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                    DiSliderRow("Posisi Vertikal (Y)", cfg.offsetYDp, -12f..48f, "dp") { v -> save { it.copy(offsetYDp = v) } }
                    DiSliderRow("Posisi Horizontal (X)", cfg.offsetXDp, -80f..80f, "dp") { v -> save { it.copy(offsetXDp = v) } }
                    DiSliderRow("Lebar Kapsul Ringkas", cfg.compactWidthDp, 140f..280f, "dp") { v -> save { it.copy(compactWidthDp = v) } }
                    DiSliderRow("Tinggi Kapsul Ringkas", cfg.compactHeightDp, 30f..48f, "dp") { v -> save { it.copy(compactHeightDp = v) } }
                    DiSliderRow("Radius Sudut", cfg.cornerRadiusDp, 12f..32f, "dp") { v -> save { it.copy(cornerRadiusDp = v) } }
                    DiSliderRow("Auto-ciut setelah", cfg.autoCollapseSec.toFloat(), 3f..10f, " dtk") { v -> save { it.copy(autoCollapseSec = v.toInt()) } }
                }
            }
        }
    }
}

private fun isIslandAccessibilityEnabled(resolver: android.content.ContentResolver, pkgName: String): Boolean {
    return try {
        val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        enabled.contains("$pkgName/com.spotyflow.android.island.SpotyFlowDynamicIslandService") ||
            enabled.contains("$pkgName/.island.SpotyFlowDynamicIslandService")
    } catch (_: Throwable) {
        false
    }
}

@Composable
private fun DiToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
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
private fun DiSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = SpotyColors.Slate900)
            Text("${value.toInt()}$unit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = SpotyColors.EmeraldCrystal, activeTrackColor = SpotyColors.EmeraldCrystal)
        )
    }
}
