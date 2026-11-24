package com.spotyflow.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.spotyflow.android.data.PlayerMode
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.data.PreviewSheetData
import com.spotyflow.android.data.RepeatMode
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.formatDurationMs
import com.spotyflow.android.data.formatRemainingMs
import com.spotyflow.android.player.SpotyFlowPlaybackService
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

@OptIn(UnstableApi::class)
@Composable
fun SpotyNowPlayingSheet(
    uiState: PlayerUiState,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSeekMs: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetPlayerMode: (PlayerMode) -> Unit,
    onSetVolume: (Float) -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenManualLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenArtist: (String, String) -> Unit,
    onOpenTrackMenu: (TrackItem) -> Unit,
    onOpenPreview: (PreviewSheetData) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = uiState.currentTrack ?: return
    val pal = uiState.ambientPalette

    BackHandler(enabled = true) {
        if (uiState.isLyricsVisible) onToggleLyrics() else onDismiss()
    }

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }
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
        // Layer 0 (Sibling): Adaptive Pure White Pastel Ambient Canvas
        PureWhiteAmbientCanvas(
            orbPrimaryTarget = pal.orbPrimary,
            orbSecondaryTarget = pal.orbSecondary,
            orbTertiaryTarget = pal.orbTertiary,
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
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(SpotyColors.Slate900.copy(alpha = 0.16f))
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Top Header Bar: Down | Audio/Video Pill | More
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
                        onClick = { onOpenTrackMenu(track) },
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

                // Center Stage: Hero Artwork / Live ExoPlayer VideoView / Live Synced Lyrics
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
                                onTapLine = { timeMs ->
                                    if (timeMs >= 0L) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSeekMs(timeMs)
                                    }
                                },
                                onManualSearch = onOpenManualLyrics
                            )
                        } else if (uiState.playerMode == PlayerMode.Video) {
                            // Live Native Media3 PlayerView inside 28.dp Glass Frame
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 10f)
                                    .shadow(
                                        elevation = 20.dp,
                                        shape = RoundedCornerShape(28.dp),
                                        ambientColor = pal.accent.copy(alpha = 0.35f)
                                    )
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            useController = false
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            player = SpotyFlowPlaybackService.getActiveExoPlayer()
                                        }
                                    },
                                    update = { view ->
                                        val active = SpotyFlowPlaybackService.getActiveExoPlayer()
                                        if (view.player !== active) view.player = active
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(0.92f)
                                        .shadow(
                                            elevation = 28.dp,
                                            shape = RoundedCornerShape(30.dp),
                                            ambientColor = pal.accent.copy(alpha = 0.45f),
                                            spotColor = pal.accent.copy(alpha = 0.55f)
                                        )
                                )
                                SpotyArtwork(
                                    coverUrl = track.coverUrl,
                                    accentColor = pal.accent,
                                    size = null,
                                    cornerRadius = 28.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Song Info Row: Title + Clickable Artist -> Full Artist Profile!
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
                            fontWeight = FontWeight.SemiBold,
                            color = SpotyColors.DeepEmerald,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenArtist(track.artistBrowseId, track.artist) }
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
                                        title = "Poster 3D Studio (Tahap 4)",
                                        subtitle = "Copy objek/manusia fotomu di depan sampul musik ini",
                                        badge = "ML KIT DEPTH",
                                        bullets = listOf(
                                            "Foto sendiri otomatis jadi latar belakang + objek manusia di depan kartu 3D",
                                            "Kuas Perapi Jari + simpan Full HD ke Galeri"
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
                        Slider(
                            value = uiState.progress,
                            onValueChange = onSeekFraction,
                            colors = SliderDefaults.colors(
                                thumbColor = SpotyColors.Slate900,
                                activeTrackColor = pal.accent,
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
                                text = formatDurationMs(uiState.positionMs),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SpotyColors.Slate500
                            )
                            Text(
                                text = formatRemainingMs(uiState.positionMs, uiState.durationMs),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SpotyColors.Slate500
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

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
                                    tint = if (uiState.isShuffle) pal.accent else SpotyColors.Slate500,
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
                                        spotColor = pal.accent.copy(alpha = 0.38f)
                                    )
                                    .clip(CircleShape)
                                    .background(SpotyColors.Slate900)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onTogglePlayPause()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (uiState.isBuffering) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.8.dp,
                                        modifier = Modifier.size(28.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (uiState.isPlaying) "Jeda" else "Putar",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
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
                                    tint = if (uiState.repeatMode != RepeatMode.Off) pal.accent else SpotyColors.Slate500,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Real System Volume Slider + Bluetooth Output
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
                                            subtitle = "Volume tersinkron langsung ke AudioManager Android",
                                            badge = "MEDIA OUTPUT",
                                            bullets = listOf(
                                                "Mendukung Speaker, Headset Kabel, TWS Bluetooth, & Android Auto"
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            PlayerBottomAction(
                                icon = Icons.Rounded.Lyrics,
                                label = "Lirik",
                                active = uiState.isLyricsVisible,
                                accentColor = pal.accent,
                                onClick = onToggleLyrics
                            )
                            PlayerBottomAction(
                                icon = Icons.Rounded.QueueMusic,
                                label = "Antrean",
                                active = false,
                                accentColor = pal.accent,
                                onClick = onOpenQueue
                            )
                            PlayerBottomAction(
                                icon = Icons.Rounded.Share,
                                label = "Bagikan",
                                active = false,
                                accentColor = pal.accent,
                                onClick = { onOpenTrackMenu(track) }
                            )
                            PlayerBottomAction(
                                icon = Icons.Rounded.Bedtime,
                                label = "Timer Tidur",
                                active = false,
                                accentColor = pal.accent,
                                onClick = {
                                    onOpenPreview(
                                        PreviewSheetData(
                                            title = "Timer Tidur",
                                            subtitle = "Kontrol penuh 15–60 mnt & memudar hadir di Tahap 3",
                                            badge = "TIMER",
                                            bullets = listOf("Opsi berhenti otomatis di akhir lagu saat ini")
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
    onTapLine: (Long) -> Unit,
    onManualSearch: () -> Unit
) {
    val lines = uiState.lyricsResult.activeList
    val activeIdx = uiState.activeLyricIndex
    val listState = rememberLazyListState()

    LaunchedEffect(activeIdx, uiState.lyricsResult.isSynced) {
        if (lines.isNotEmpty() && uiState.lyricsResult.isSynced) {
            listState.animateScrollToItem((activeIdx - 1).coerceAtLeast(0))
        }
    }

    SpotyGlassCard(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(28.dp),
        tintAlpha = 0.68f,
        elevation = 12.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLyricsLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = SpotyColors.EmeraldCrystal)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Mencari lirik sinkron...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SpotyColors.Slate500
                        )
                    }
                }
                lines.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Lirik belum ditemukan",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SpotyColors.Slate900
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ketuk Cari Manual di bawah untuk memasukkan judul & artis.",
                            fontSize = 13.sp,
                            color = SpotyColors.Slate500,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(lines) { idx, line ->
                            val isActive = uiState.lyricsResult.isSynced && idx == activeIdx
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
                                color = if (isActive || !uiState.lyricsResult.isSynced) SpotyColors.Slate900
                                else SpotyColors.Slate500.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onTapLine(line.timeMs) }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Bottom floating Manual Search pill inside Lyrics card
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(SpotyColors.Slate900)
                    .clickable(onClick = onManualSearch)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Cari Manual",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Cari Manual",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
