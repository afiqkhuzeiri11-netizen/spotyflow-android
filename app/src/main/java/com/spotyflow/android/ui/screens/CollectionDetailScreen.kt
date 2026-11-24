package com.spotyflow.android.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.CollectionDetailData
import com.spotyflow.android.data.CollectionSummary
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.formatDuration
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.glass.SpotyGlassCircleButton
import com.spotyflow.android.ui.glass.SpotyMiniEqBars
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing

@Composable
fun CollectionDetailScreen(
    fallbackTitle: String,
    detail: CollectionDetailData?,
    isLoading: Boolean,
    isSaved: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: (List<TrackItem>, Boolean) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onToggleSave: (CollectionSummary) -> Unit,
    onOpenArtist: (String, String) -> Unit,
    onOpenTrackMenu: (TrackItem) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { onBack() }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading && detail == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = SpotyColors.EmeraldCrystal)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Memuat $fallbackTitle...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SpotyColors.Slate500
                )
            }
        } else if (detail == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Detail koleksi belum tersedia",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotyColors.Slate900
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 96.dp,
                    bottom = SpotySpacing.BottomScrollClearance
                ),
                verticalArrangement = Arrangement.spacedBy(SpotySpacing.SectionGap)
            ) {
                // Header Glass Card with Big HD Cover, Title, Clickable Artist, Duration & Actions
                item(key = "col_header") {
                    SpotyGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = SpotySpacing.ScreenHorizontal),
                        shape = RoundedCornerShape(28.dp),
                        tintAlpha = 0.68f,
                        elevation = 12.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SpotyArtwork(
                                coverUrl = detail.coverUrl,
                                accentColor = SpotyColors.EmeraldCrystal,
                                size = 186.dp,
                                cornerRadius = 24.dp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = detail.title,
                                fontSize = 22.sp,
                                lineHeight = 27.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SpotyColors.Slate900,
                                textAlign = TextAlign.Center
                            )

                            if (detail.artistName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = detail.artistName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SpotyColors.DeepEmerald,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenArtist(detail.artistBrowseId, detail.artistName) }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = buildString {
                                    if (detail.year.isNotBlank()) append("${detail.year} • ")
                                    append("${detail.tracks.size} lagu")
                                    if (detail.totalDurationText.isNotBlank()) append(" • ${detail.totalDurationText}")
                                },
                                fontSize = 12.5.sp,
                                color = SpotyColors.Slate500
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .shadow(
                                            elevation = 10.dp,
                                            shape = RoundedCornerShape(999.dp),
                                            ambientColor = SpotyColors.EmeraldCrystal.copy(alpha = 0.35f)
                                        )
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(SpotyColors.EmeraldCrystal)
                                        .clickable {
                                            if (detail.tracks.isNotEmpty()) onPlayAll(detail.tracks, false)
                                        }
                                        .padding(horizontal = 18.dp, vertical = 11.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Putar Semua",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Putar Semua",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                SpotyGlassCard(
                                    shape = RoundedCornerShape(999.dp),
                                    tintAlpha = 0.72f,
                                    elevation = 5.dp,
                                    onClick = {
                                        if (detail.tracks.isNotEmpty()) onPlayAll(detail.tracks, true)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shuffle,
                                            contentDescription = "Acak",
                                            tint = SpotyColors.Slate900,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Acak",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SpotyColors.Slate900
                                        )
                                    }
                                }

                                if (!detail.isCustomPlaylist && (detail.browseId.startsWith("MPREb_") || detail.browseId.startsWith("VL"))) {
                                    SpotyGlassCircleButton(
                                        onClick = {
                                            onToggleSave(
                                                CollectionSummary(
                                                    browseId = detail.browseId,
                                                    title = detail.title,
                                                    subtitle = detail.artistName,
                                                    year = detail.year,
                                                    coverUrl = detail.coverUrl,
                                                    isAlbum = detail.isAlbum
                                                )
                                            )
                                        },
                                        size = 42.dp,
                                        useFloatingLayer = false
                                    ) {
                                        Icon(
                                            imageVector = if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = "Simpan",
                                            tint = if (isSaved) SpotyColors.HeartRed else SpotyColors.Slate900,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Track List
                item(key = "col_tracks") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpotySpacing.ScreenHorizontal)
                    ) {
                        if (detail.tracks.isEmpty()) {
                            SpotyGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                tintAlpha = 0.64f
                            ) {
                                Text(
                                    text = "Belum ada lagu di koleksi ini.",
                                    fontSize = 13.5.sp,
                                    color = SpotyColors.Slate500,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(26.dp)
                                )
                            }
                        } else {
                            SpotyGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                tintAlpha = 0.66f
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    detail.tracks.forEachIndexed { idx, track ->
                                        val isActive = track.id == currentTrackId
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onPlayTrack(track, detail.tracks) }
                                                .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${idx + 1}",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActive) SpotyColors.DeepEmerald else SpotyColors.Slate500,
                                                modifier = Modifier.width(24.dp)
                                            )
                                            SpotyArtwork(
                                                coverUrl = track.coverUrl,
                                                accentColor = track.accentColor,
                                                size = 44.dp,
                                                cornerRadius = 11.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = track.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    color = if (isActive) SpotyColors.DeepEmerald else SpotyColors.Slate900,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = track.artist,
                                                    fontSize = 12.sp,
                                                    color = SpotyColors.Slate500,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isActive) {
                                                SpotyMiniEqBars(isPlaying = isPlaying)
                                                Spacer(modifier = Modifier.width(4.dp))
                                            } else if (track.durationSec > 0) {
                                                Text(
                                                    text = formatDuration(track.durationSec),
                                                    fontSize = 12.sp,
                                                    color = SpotyColors.Slate500
                                                )
                                            }
                                            IconButton(
                                                onClick = { onOpenTrackMenu(track) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MoreVert,
                                                    contentDescription = "Opsi",
                                                    tint = SpotyColors.Slate500,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        if (idx < detail.tracks.lastIndex) {
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
        }

        // Floating Back Button
        SpotyGlassCircleButton(
            onClick = onBack,
            size = 42.dp,
            useFloatingLayer = false,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Kembali",
                tint = SpotyColors.Slate900,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
