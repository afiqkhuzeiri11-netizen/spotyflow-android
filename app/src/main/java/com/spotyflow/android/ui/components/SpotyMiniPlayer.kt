package com.spotyflow.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyFloatingGlass
import com.spotyflow.android.ui.theme.SpotyColors
import kotlinx.coroutines.delay

@Composable
fun SpotyMiniPlayer(
    uiState: PlayerUiState,
    onOpenNowPlaying: () -> Unit,
    onToggleLike: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val track = uiState.currentTrack
    val accent = uiState.ambientPalette.accent

    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "miniProgress"
    )

    var heartPop by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (heartPop) 1.32f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "miniHeartScale"
    )
    LaunchedEffect(heartPop) {
        if (heartPop) {
            delay(180L)
            heartPop = false
        }
    }

    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (playPressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "miniPlayScale"
    )

    SpotyFloatingGlass(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(64.dp),
        shape = RoundedCornerShape(24.dp),
        tintAlpha = 0.58f,
        elevation = 16.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (track != null) onOpenNowPlaying() else onTogglePlayPause()
                    }
                )
        ) {
            // Ultra-fine 2.5dp progress bar flush along inner top edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .height(2.5.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(99.dp))
                    .background(SpotyColors.Slate900.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(99.dp))
                        .background(accent)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpotyArtwork(
                    coverUrl = track?.coverUrl.orEmpty(),
                    accentColor = accent,
                    size = 44.dp,
                    cornerRadius = 14.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track?.title ?: "Siap Memutar",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SpotyColors.Slate900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            uiState.isBuffering -> "Memuat audio HD..."
                            track != null -> track.artist
                            else -> "Ketuk putar atau pilih lagu"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = SpotyColors.Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        if (track != null) {
                            heartPop = true
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleLike()
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isCurrentTrackLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Suka",
                        tint = if (uiState.isCurrentTrackLiked) SpotyColors.HeartRed else SpotyColors.Slate700,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            }
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            scaleX = playScale
                            scaleY = playScale
                        }
                        .clip(CircleShape)
                        .background(SpotyColors.Slate900)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isBuffering) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Jeda" else "Putar",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNext()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Berikutnya",
                        tint = SpotyColors.Slate900,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
