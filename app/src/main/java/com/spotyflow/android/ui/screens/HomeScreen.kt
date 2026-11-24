package com.spotyflow.android.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.data.PreviewSheetData
import com.spotyflow.android.data.SampleCatalog
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.formatDuration
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyFlameVector
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.glass.SpotyMiniEqBars
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing

@Composable
fun HomeScreen(
    uiState: PlayerUiState,
    topHeaderClearance: Dp,
    onSelectMood: (String) -> Unit,
    onPlayTrack: (TrackItem) -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenPreview: (PreviewSheetData) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTrack = uiState.currentTrack
    val displayTracks = uiState.filteredHomeTracks

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = topHeaderClearance,
            bottom = SpotySpacing.BottomScrollClearance
        ),
        verticalArrangement = Arrangement.spacedBy(SpotySpacing.SectionGap)
    ) {
        // 1. Hero Glass Card — "Lanjut Dengar"
        item(key = "hero_card") {
            SpotyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(26.dp),
                tintAlpha = 0.66f,
                elevation = 12.dp,
                onClick = onOpenNowPlaying
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SpotySpacing.CardPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpotyArtwork(
                        coverUrl = activeTrack.coverUrl,
                        accentColor = activeTrack.accentColor,
                        size = 72.dp,
                        cornerRadius = 18.dp
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "LANJUT DENGAR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                                color = SpotyColors.DeepEmerald
                            )
                            if (uiState.isPlaying) {
                                SpotyMiniEqBars(
                                    isPlaying = true,
                                    color = SpotyColors.EmeraldCrystal
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeTrack.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SpotyColors.Slate900,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = activeTrack.artist,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = SpotyColors.Slate500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = CircleShape,
                                ambientColor = SpotyColors.EmeraldCrystal.copy(alpha = 0.35f),
                                spotColor = SpotyColors.EmeraldCrystal.copy(alpha = 0.45f)
                            )
                            .clip(CircleShape)
                            .background(SpotyColors.EmeraldCrystal)
                            .clickable(onClick = onTogglePlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Jeda" else "Putar",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // 2. Mood Glass Pills (Horizontal Scroll)
        item(key = "mood_pills") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = SpotySpacing.ScreenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(SampleCatalog.moodPills, key = { it }) { mood ->
                    val selected = uiState.selectedMood == mood
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.03f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "moodScale_$mood"
                    )

                    if (selected) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(999.dp),
                                    ambientColor = SpotyColors.Slate900.copy(alpha = 0.22f)
                                )
                                .clip(RoundedCornerShape(999.dp))
                                .background(SpotyColors.Slate900)
                                .clickable { onSelectMood(mood) }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = mood,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        SpotyGlassCard(
                            shape = RoundedCornerShape(999.dp),
                            tintAlpha = 0.58f,
                            elevation = 6.dp,
                            onClick = { onSelectMood(mood) }
                        ) {
                            Text(
                                text = mood,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SpotyColors.Slate700,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Inline Glass Streak Bar (Compact Single Row + Vector Flame)
        item(key = "streak_bar") {
            SpotyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(20.dp),
                tintAlpha = 0.62f,
                elevation = 8.dp,
                onClick = {
                    onOpenPreview(
                        PreviewSheetData(
                            title = "Streak & Percikan Harian",
                            subtitle = "${uiState.streakDays} hari beruntun • ${uiState.listenedMinutesToday} menit hari ini",
                            badge = "STREAK AKTIF",
                            bullets = listOf(
                                "Target harian: 30 menit mendengarkan musik",
                                "Tersinkron otomatis dengan Statistik & Recap"
                            )
                        )
                    )
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SpotyColors.FlameOrange.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        SpotyFlameVector(size = 22.dp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${uiState.streakDays} hari beruntun",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotyColors.Slate900
                        )
                        Text(
                            text = "${uiState.listenedMinutesToday} mnt hari ini",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = SpotyColors.Slate500
                        )
                    }

                    // Slim daily goal capsule bar
                    val goalRatio = (uiState.listenedMinutesToday / 30f).coerceIn(0.15f, 1f)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${(goalRatio * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotyColors.DeepEmerald
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(68.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(SpotyColors.Slate900.copy(alpha = 0.08f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(goalRatio)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(SpotyColors.EmeraldCrystal)
                            )
                        }
                    }
                }
            }
        }

        // 4. Section "Andalanmu" — Horizontal Glass Album Cards
        item(key = "andalanmu_section") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpotySpacing.ScreenHorizontal),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.selectedMood == "Semua") "Andalanmu" else "Pilihan ${uiState.selectedMood}",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SpotyColors.Slate900
                    )
                    Text(
                        text = "Lihat semua",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SpotyColors.DeepEmerald,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectMood("Semua") }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = SpotySpacing.ScreenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(SpotySpacing.CardGap)
                ) {
                    items(displayTracks, key = { "card_${it.id}" }) { track ->
                        SpotyGlassCard(
                            modifier = Modifier.width(148.dp),
                            shape = RoundedCornerShape(22.dp),
                            tintAlpha = 0.62f,
                            elevation = 10.dp,
                            onClick = { onPlayTrack(track) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                SpotyArtwork(
                                    coverUrl = track.coverUrl,
                                    accentColor = track.accentColor,
                                    size = 128.dp,
                                    cornerRadius = 16.dp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = track.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (track.id == activeTrack.id) SpotyColors.DeepEmerald else SpotyColors.Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = track.artist,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = SpotyColors.Slate500,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Section "Baru Diputar" — Clean Spacious Glass Track List
        item(key = "recent_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Baru Diputar",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SpotyColors.Slate900
                )

                SpotyGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    tintAlpha = 0.64f,
                    elevation = 10.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        uiState.tracks.forEachIndexed { idx, track ->
                            val isActive = track.id == activeTrack.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayTrack(track) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SpotyArtwork(
                                    coverUrl = track.coverUrl,
                                    accentColor = track.accentColor,
                                    size = 48.dp,
                                    cornerRadius = 12.dp
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        fontSize = 14.5.sp,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = if (isActive) SpotyColors.DeepEmerald else SpotyColors.Slate900,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${track.artist} • ${track.album}",
                                        fontSize = 12.sp,
                                        color = SpotyColors.Slate500,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                if (isActive) {
                                    SpotyMiniEqBars(
                                        isPlaying = uiState.isPlaying,
                                        color = SpotyColors.EmeraldCrystal
                                    )
                                } else {
                                    Text(
                                        text = formatDuration(track.durationSec),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SpotyColors.Slate500
                                    )
                                }
                            }

                            if (idx < uiState.tracks.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 74.dp, end = 16.dp),
                                    thickness = 0.5.dp,
                                    color = SpotyColors.HairlineDivider
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
