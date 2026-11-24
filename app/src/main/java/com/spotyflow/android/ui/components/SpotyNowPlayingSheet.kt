package com.spotyflow.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.PlayerMode
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.data.PreviewSheetData
import com.spotyflow.android.data.RepeatMode
import com.spotyflow.android.data.formatDuration
import com.spotyflow.android.data.formatRemaining
import com.spotyflow.android.ui.glass.LocalBgHazeState
import com.spotyflow.android.ui.glass.LocalContentHazeState
import com.spotyflow.android.ui.glass.PureWhiteAmbientCanvas
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyFloatingGlass
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.glass.SpotyGlassCircleButton
import com.spotyflow.android.ui.theme.SpotyColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SpotyNowPlayingSheet(
    uiState: PlayerUiState,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSeekSecond: (Int) -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetPlayerMode: (PlayerMode) -> Unit,
    onSetVolume: (Float) -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenPreview: (PreviewSheetData) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) {
        if (uiState.isLyricsVisible) onToggleLyrics() else onDismiss()
    }

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }
    val track = uiState.currentTrack

    // Isolated sibling HazeState for Now Playing sheet (0% crash risk!)
    val sheetBgHaze = remember { HazeState() }

    val coverScale by animateFloatAsState(
        targetValue = if (uiState.isPlaying) 1.0f else 0.93f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heroCoverScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(x = 0, y = dragOffsetY.value.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        val next = (dragOffsetY.value + dragAmount).coerceAtLeast(0f)
                        scope.launch { dragOffsetY.snapTo(next) }
                    },
                    onDragEnd = {
                        if (dragOffsetY.value > 220f) {
                            onDismiss()
                        } else {
                            scope.launch {
                                dragOffsetY.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffsetY.animateTo(0f) }
                    }
                )
            }
    ) {
        // Layer 0 (Sibling): Pure White Pastel Ambient Canvas
        PureWhiteAmbientCanvas(
            orbPrimaryTarget = track.orbPrimary,
            orbSecondaryTarget = track.orbSecondary,
            orbTertiaryTarget = track.orbTertiary,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = sheetBgHaze)
        )

        // Layer 1 (Sibling): Fullscreen Pure White Glass UI
        CompositionLocalProvider(
            LocalBgHazeState provides sheetBgHaze,
            LocalContentHazeState provides sheetBgHaze
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Drag indicator pill
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(SpotyColors.Slate900.copy(alpha = 0.16f))
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Top Header Bar: Down Button | Audio/Video Pill | More
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpotyGlassCircleButton(onClick = onDismiss, size = 42.dp) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Tutup Pemutar",
                            tint = SpotyColors.Slate900,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Audio | Video Liquid Glass Pill Switch
                    SpotyFloatingGlass(
                        shape = RoundedCornerShape(999.dp),
                        tintAlpha = 0.62f,
                        elevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ModeSwitchPill(
                                label = "Audio",
                                selected = uiState.playerMode == PlayerMode.Audio,
                                onClick = { onSetPlayerMode(PlayerMode.Audio) }
                            )
                            ModeSwitchPill(
                                label = "Video",
                                selected = uiState.playerMode == PlayerMode.Video,
                                onClick = { onSetPlayerMode(PlayerMode.Video) }
                            )
                        }
                    }

                    SpotyGlassCircleButton(
                        onClick = {
                            onOpenPreview(
                                PreviewSheetData(
                                    title = track.title,
                                    subtitle = "${track.artist} • ${track.album}",
                                    badge = "OPSI LAGU",
                                    bullets = listOf(
                                        "Tambahkan ke Antrean & Putar Berikutnya",
                                        "Unduh Offline Audio M4A (Hadir di Tahap 2)",
                                        "Mulai Radio Lagu Serupa & Lihat Profil Artis"
                                    )
                                )
                            )
                        },
                        size = 42.dp
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Menu Lagu",
                            tint = SpotyColors.Slate900,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Center Stage: Smooth Crossfade between Hero Album Art & Pure White Synced Lyrics
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = uiState.isLyricsVisible,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "stageLyricsSwitch"
                    ) { showLyrics ->
                        if (showLyrics) {
                            SyncedLyricsGlassCard(
                                uiState = uiState,
                                onTapLine = { sec ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSeekSecond(sec)
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.84f)
                                    .aspectRatio(1f)
                                    .graphicsLayer {
                                        scaleX = coverScale
                                        scaleY = coverScale
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Colored pastel halo underneath hero artwork
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(0.92f)
                                        .shadow(
                                            elevation = 28.dp,
                                            shape = RoundedCornerShape(30.dp),
                                            ambientColor = track.accentColor.copy(alpha = 0.45f),
                                            spotColor = track.accentColor.copy(alpha = 0.55f)
                                        )
                                )
                                SpotyArtwork(
                                    coverUrl = track.coverUrl,
                                    accentColor = track.accentColor,
                                    size = null,
                                    cornerRadius = 28.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (uiState.playerMode == PlayerMode.Video) {
                                    SpotyFloatingGlass(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(14.dp),
                                        shape = RoundedCornerShape(999.dp),
                                        tintAlpha = 0.72f
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Videocam,
                                                contentDescription = null,
                                                tint = SpotyColors.Slate900,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Mode Video HD siap di Tahap 2",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = SpotyColors.Slate900
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Song Info Row: Title + Artist on left | Like + 3D Poster on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SpotyColors.Slate900,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = SpotyColors.Slate500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SpotyGlassCircleButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleLike()
                            },
                            size = 44.dp
                        ) {
                            Icon(
                                imageVector = if (uiState.isCurrentTrackLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Suka",
                                tint = if (uiState.isCurrentTrackLiked) SpotyColors.HeartRed else SpotyColors.Slate900,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        SpotyGlassCircleButton(
                            onClick = {
                                onOpenPreview(
                                    PreviewSheetData(
                                        title = "Poster 3D Studio",
                                        subtitle = "Kartu lagu 3D melayang di belakang subjek fotomu",
                                        badge = "ML KIT DEPTH",
                                        bullets = listOf(
                                            "Pilih 1–5 lagu favorit dengan sudut 3D interaktif",
                                            "Segmentasi subjek AI offline (ML Kit)",
                                            "Simpan Full HD langsung ke Galeri"
                                        )
                                    )
                                )
                            },
                            size = 44.dp
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Poster 3D",
                                tint = SpotyColors.Slate900,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Liquid Glass Control Capsule
                SpotyFloatingGlass(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    tintAlpha = 0.62f,
                    elevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        // Interactive Live Seekbar + Positive / Negative Remaining Timestamps
                        Slider(
                            value = uiState.progress,
                            onValueChange = onSeekFraction,
                            colors = SliderDefaults.colors(
                                thumbColor = SpotyColors.Slate900,
                                activeTrackColor = track.accentColor,
                                inactiveTrackColor = SpotyColors.Slate900.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(uiState.positionSec),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SpotyColors.Slate500
                            )
                            Text(
                                text = formatRemaining(uiState.positionSec, track.durationSec),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SpotyColors.Slate500
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 5 Symmetric Primary Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleShuffle()
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Acak",
                                    tint = if (uiState.isShuffle) track.accentColor else SpotyColors.Slate500,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onPrevious()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = "Sebelumnya",
                                    tint = SpotyColors.Slate900,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .shadow(
                                        elevation = 14.dp,
                                        shape = CircleShape,
                                        ambientColor = SpotyColors.Slate900.copy(alpha = 0.30f),
                                        spotColor = track.accentColor.copy(alpha = 0.38f)
                                    )
                                    .clip(CircleShape)
                                    .background(SpotyColors.Slate900)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onTogglePlayPause()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (uiState.isPlaying) "Jeda" else "Putar",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNext()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = "Berikutnya",
                                    tint = SpotyColors.Slate900,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCycleRepeat()
                            }) {
                                Icon(
                                    imageVector = if (uiState.repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = "Ulangi",
                                    tint = if (uiState.repeatMode != RepeatMode.Off) track.accentColor else SpotyColors.Slate500,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Volume Slider + Bluetooth Output Icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "Volume",
                                tint = SpotyColors.Slate500,
                                modifier = Modifier.size(18.dp)
                            )
                            Slider(
                                value = uiState.volume,
                                onValueChange = onSetVolume,
                                colors = SliderDefaults.colors(
                                    thumbColor = SpotyColors.Slate900,
                                    activeTrackColor = SpotyColors.Slate900,
                                    inactiveTrackColor = SpotyColors.Slate900.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .height(24.dp)
                            )
                            IconButton(
                                onClick = {
                                    onOpenPreview(
                                        PreviewSheetData(
                                            title = "Output Audio & Bluetooth",
                                            subtitle = "Perangkat saat ini: Speaker Perangkat",
                                            badge = "MEDIA OUTPUT",
                                            bullets = listOf(
                                                "Pindah cepat antara Speaker, TWS Bluetooth, & Android Auto",
                                                "Mendukung Volume Hardware & Audio Offload di Tahap 2"
                                            )
                                        )
                                    )
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BluetoothAudio,
                                    contentDescription = "Output Audio",
                                    tint = SpotyColors.Slate900,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 4 Symmetric Bottom Utility Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            PlayerBottomAction(
                                icon = Icons.Rounded.Lyrics,
                                label = "Lirik",
                                active = uiState.isLyricsVisible,
                                accentColor = track.accentColor,
                                onClick = onToggleLyrics
                            )
                            PlayerBottomAction(
                                icon = Icons.Rounded.QueueMusic,
                                label = "Antrean",
                                active = false,
                                accentColor = track.accentColor,
                                onClick = {
                                    onOpenPreview(
                                        PreviewSheetData(
                                            title = "Antrean Berikutnya (${uiState.tracks.size} lagu)",
                                            subtitle = "Sedang diputar: ${track.title}",
                                            badge = "ANTREAN",
                                            bullets = uiState.tracks.take(4).map { "${it.title} — ${it.artist}" }
                                        )
                                    )
                                }
                            )
                            PlayerBottomAction(
                                icon = Icons.Rounded.Share,
                                label = "Bagikan",
                                active = false,
                                accentColor = track.accentColor,
                                onClick = {
                                    onOpenPreview(
                                        PreviewSheetData(
                                            title = "Bagikan Lagu",
                                            subtitle = "${track.title} — ${track.artist}",
                                            badge = "BAGIKAN",
                                            bullets = listOf(
                                                "Buat kartu Poster 3D untuk Story & TikTok",
                                                "Salin tautan dengar cepat ke teman"
                                            )
                                        )
                                    )
                                }
                            )
                            PlayerBottomAction(
                                icon = Icons.Rounded.Bedtime,
                                label = "Timer Tidur",
                                active = false,
                                accentColor = track.accentColor,
                                onClick = {
                                    onOpenPreview(
                                        PreviewSheetData(
                                            title = "Timer Tidur",
                                            subtitle = "Hentikan musik otomatis dengan efek memudar",
                                            badge = "TIMER",
                                            bullets = listOf(
                                                "Pilihan cepat: 15, 30, 45, 60 menit",
                                                "Opsi berhenti tepat di akhir lagu saat ini"
                                            )
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSwitchPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) SpotyColors.Slate900 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else SpotyColors.Slate500
        )
    }
}

@Composable
private fun PlayerBottomAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) accentColor else SpotyColors.Slate700,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) accentColor else SpotyColors.Slate500
        )
    }
}

@Composable
private fun SyncedLyricsGlassCard(
    uiState: PlayerUiState,
    onTapLine: (Int) -> Unit
) {
    val lines = uiState.currentTrack.lyrics
    val activeIdx = uiState.activeLyricIndex
    val listState = rememberLazyListState()

    LaunchedEffect(activeIdx) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem((activeIdx - 1).coerceAtLeast(0))
        }
    }

    SpotyGlassCard(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(28.dp),
        tintAlpha = 0.66f,
        elevation = 12.dp
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(lines) { idx, line ->
                val isActive = idx == activeIdx
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.03f else 0.98f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "lyricScale_$idx"
                )
                Text(
                    text = line.text,
                    fontSize = if (isActive) 22.sp else 18.sp,
                    lineHeight = if (isActive) 29.sp else 24.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (isActive) SpotyColors.Slate900 else SpotyColors.Slate500.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTapLine(line.timeSec) }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}
