package com.spotyflow.android.widget

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyFlameVector
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.glass.SpotyGlassCircleButton
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing

@Composable
fun WidgetSettingsScreen(
    uiState: PlayerUiState,
    onBack: () -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { WidgetConfigStore.load(context) }
    val cfg by WidgetConfigStore.config.collectAsState()
    var previewPlaying by remember { mutableStateOf(true) }
    var previewLiked by remember { mutableStateOf(true) }

    BackHandler(onBack = onBack)

    fun updateCfg(transform: (WidgetConfig) -> WidgetConfig) {
        WidgetConfigStore.save(context, transform(cfg))
    }

    val activeAccent = if (cfg.accentMode == WidgetAccentMode.CUSTOM) {
        Color(cfg.customAccentArgb)
    } else {
        uiState.ambientPalette.accent
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = SpotySpacing.BottomScrollClearance
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item(key = "widget_hdr") {
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
                        Icon(
                            imageVector = Icons.Rounded.ArrowBackIosNew,
                            contentDescription = "Kembali",
                            tint = SpotyColors.Slate900,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Pengaturan Widget",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SpotyColors.Slate900
                        )
                        Text(
                            text = "5 Ukuran Native (1x1, 2x2, 2x4, 4x1, 4x2)",
                            fontSize = 11.5.sp,
                            color = SpotyColors.Slate500
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(SpotyColors.EmeraldCrystal)
                        .clickable {
                            SpotyFlowBaseWidgetProvider.updateAllWidgetsAsync(context, forceImmediate = true)
                            onShowToast("Semua widget layar utama diperbarui")
                            onBack()
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "Selesai",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Live 4x2 Interactive Preview
        item(key = "widget_preview") {
            val darkText = SpotyFlowWidgetRenderer.isDarkTextStyle(cfg.style)
            val cornerDp = (8 + (cfg.cornerRadiusPct / 100f) * 24f).dp
            val titleColor = if (darkText) SpotyColors.Slate900 else Color.White
            val subColor = if (darkText) SpotyColors.Slate500 else Color.White.copy(alpha = 0.76f)
            val cardBrush = when (cfg.style) {
                WidgetStyle.GLASS -> Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.88f), activeAccent.copy(alpha = 0.14f))
                )
                WidgetStyle.CLEAR_GLASS -> Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.56f), Color.White.copy(alpha = 0.34f))
                )
                WidgetStyle.LIGHT -> Brush.verticalGradient(listOf(Color.White, Color(0xFFF8FAFC)))
                WidgetStyle.DARK -> Brush.verticalGradient(listOf(Color(0xFF151822), Color(0xFF0A0C12)))
                WidgetStyle.COVER_COLOR -> Brush.linearGradient(
                    listOf(activeAccent.copy(alpha = 0.88f), Color(0xFF0F172A))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFDBEAFE), Color(0xFFD1FAE5), Color(0xFFF3E8FF))
                        )
                    )
                    .padding(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cornerDp))
                        .background(cardBrush)
                        .border(
                            width = (1f + cfg.glassShinePct / 100f).dp,
                            color = if (darkText) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(cornerDp)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (cfg.showCover) {
                            val coverRadius = when (cfg.coverShape) {
                                WidgetCoverShape.CIRCLE -> 999.dp
                                WidgetCoverShape.ROUNDED -> 12.dp
                                WidgetCoverShape.SQUARE -> 4.dp
                            }
                            SpotyArtwork(
                                coverUrl = uiState.currentTrack?.coverUrl.orEmpty(),
                                accentColor = activeAccent,
                                size = 52.dp,
                                cornerRadius = coverRadius
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.currentTrack?.title ?: "Lagu Favorit Kamu",
                                fontSize = (14.5f * cfg.textSize.scale).sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = uiState.currentTrack?.artist ?: "SpotyFlow Native",
                                fontSize = (12f * cfg.textSize.scale).sp,
                                color = subColor,
                                maxLines = 1
                            )
                        }
                        if (cfg.showStreak) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                SpotyFlameVector(size = 16.dp)
                                Text(
                                    text = "${uiState.dailyStreak.count}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SpotyColors.FlameOrange
                                )
                            }
                        }
                    }

                    if (cfg.showProgress) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(titleColor.copy(alpha = 0.14f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.42f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(activeAccent)
                            )
                        }
                    }

                    if (cfg.showTime) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1:24", fontSize = 10.sp, color = subColor)
                            Text("-2:18", fontSize = 10.sp, color = subColor)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (cfg.showLike) {
                            Icon(
                                imageVector = if (previewLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Suka",
                                tint = if (previewLiked) SpotyColors.HeartRed else titleColor,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable { previewLiked = !previewLiked }
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (cfg.showPrevNext) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = "Sebelumnya",
                                    tint = titleColor,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (cfg.playBtnStyle == WidgetPlayBtnStyle.ROUND) activeAccent else Color.Transparent
                                    )
                                    .clickable { previewPlaying = !previewPlaying },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (previewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Putar",
                                    tint = if (cfg.playBtnStyle == WidgetPlayBtnStyle.ROUND) Color.White else titleColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            if (cfg.showPrevNext) {
                                Spacer(modifier = Modifier.width(14.dp))
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = "Berikutnya",
                                    tint = titleColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Style Selector
        item(key = "widget_style") {
            SpotyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(24.dp),
                tintAlpha = 0.66f
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Gaya Widget", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(WidgetStyle.entries) { st ->
                            val sel = cfg.style == st
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (sel) SpotyColors.Slate900 else SpotyColors.Slate900.copy(alpha = 0.07f))
                                    .clickable { updateCfg { it.copy(style = st) } }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = st.label,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sel) Color.White else SpotyColors.Slate900
                                )
                            }
                        }
                    }

                    Text("Warna Aksen", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetAccentMode.entries.forEach { m ->
                            val sel = cfg.accentMode == m
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (sel) SpotyColors.EmeraldTint else SpotyColors.Slate900.copy(alpha = 0.05f))
                                    .border(
                                        1.dp,
                                        if (sel) SpotyColors.EmeraldCrystal else Color.Transparent,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { updateCfg { it.copy(accentMode = m) } }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = m.label,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sel) SpotyColors.DeepEmerald else SpotyColors.Slate900
                                )
                            }
                        }
                    }

                    if (cfg.accentMode == WidgetAccentMode.CUSTOM) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            WidgetConfigStore.ACCENT_PRESETS.forEach { argb ->
                                val selected = cfg.customAccentArgb == argb
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(if (selected) 2.5.dp else 1.dp, Color.White, CircleShape)
                                        .clickable { updateCfg { it.copy(customAccentArgb = argb) } },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3 Sliders
        item(key = "widget_sliders") {
            SpotyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(24.dp),
                tintAlpha = 0.66f
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Material & Bentuk Kaca", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                    WidgetSliderRow("Kilau kaca", cfg.glassShinePct) { v -> updateCfg { it.copy(glassShinePct = v) } }
                    WidgetSliderRow("Ketebalan kaca", cfg.glassThicknessPct) { v -> updateCfg { it.copy(glassThicknessPct = v) } }
                    WidgetSliderRow("Kebulatan sudut", cfg.cornerRadiusPct) { v -> updateCfg { it.copy(cornerRadiusPct = v) } }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetCoverShape.entries.forEach { sh ->
                            val sel = cfg.coverShape == sh
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) SpotyColors.Slate900 else SpotyColors.Slate900.copy(alpha = 0.06f))
                                    .clickable { updateCfg { it.copy(coverShape = sh) } }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(sh.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else SpotyColors.Slate900)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetTextSize.entries.forEach { ts ->
                            val sel = cfg.textSize == ts
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) SpotyColors.EmeraldTint else SpotyColors.Slate900.copy(alpha = 0.06f))
                                    .clickable { updateCfg { it.copy(textSize = ts) } }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Teks ${ts.label}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) SpotyColors.DeepEmerald else SpotyColors.Slate900)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetPlayBtnStyle.entries.forEach { ps ->
                            val sel = cfg.playBtnStyle == ps
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) SpotyColors.EmeraldTint else SpotyColors.Slate900.copy(alpha = 0.06f))
                                    .clickable { updateCfg { it.copy(playBtnStyle = ps) } }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Tombol ${ps.label}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) SpotyColors.DeepEmerald else SpotyColors.Slate900)
                            }
                        }
                    }
                }
            }
        }

        // 6 Boolean Element Switches
        item(key = "widget_toggles") {
            SpotyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(24.dp),
                tintAlpha = 0.66f
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Elemen Widget", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                    WidgetToggleRow("Tampilkan sampul album", cfg.showCover) { v -> updateCfg { it.copy(showCover = v) } }
                    WidgetToggleRow("Tampilkan bilah progres", cfg.showProgress) { v -> updateCfg { it.copy(showProgress = v) } }
                    WidgetToggleRow("Tampilkan waktu lagu", cfg.showTime) { v -> updateCfg { it.copy(showTime = v) } }
                    WidgetToggleRow("Tampilkan tombol Suka", cfg.showLike) { v -> updateCfg { it.copy(showLike = v) } }
                    WidgetToggleRow("Tampilkan tombol Prev & Next", cfg.showPrevNext) { v -> updateCfg { it.copy(showPrevNext = v) } }
                    WidgetToggleRow("Tampilkan lencana Streak (4x2)", cfg.showStreak) { v -> updateCfg { it.copy(showStreak = v) } }
                }
            }
        }
    }
}

@Composable
private fun WidgetSliderRow(label: String, valuePct: Int, onChange: (Int) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SpotyColors.Slate900)
            Text("$valuePct%", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald)
        }
        Slider(
            value = valuePct.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = SpotyColors.EmeraldCrystal,
                activeTrackColor = SpotyColors.EmeraldCrystal
            )
        )
    }
}

@Composable
private fun WidgetToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = SpotyColors.Slate900)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SpotyColors.EmeraldCrystal
            )
        )
    }
}
