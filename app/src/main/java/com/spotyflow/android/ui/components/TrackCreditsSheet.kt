package com.spotyflow.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.TrackCreditsData
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.formatDuration
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyFloatingGlass
import com.spotyflow.android.ui.theme.LocalSpotyAppearance
import com.spotyflow.android.ui.theme.SpotyColors

@Composable
fun TrackCreditsSheet(
    track: TrackItem,
    credits: TrackCreditsData?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onOpenAlbum: (TrackItem) -> Unit,
    onOpenArtist: (String, String) -> Unit
) {
    BackHandler(enabled = true) { onDismiss() }

    val app = LocalSpotyAppearance.current
    val ink = app.textPrimary
    val sub = app.textSecondary
    val accent = app.accent
    val dividerColor = if (app.isDark) Color.White.copy(alpha = 0.08f) else SpotyColors.HairlineDivider
    val chipFill = if (app.isDark) Color.White.copy(alpha = 0.09f) else SpotyColors.Slate900.copy(alpha = 0.06f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (app.isDark) 0.52f else 0.28f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        SpotyFloatingGlass(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(14.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(28.dp),
            tintAlpha = 0.92f,
            elevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpotyArtwork(
                        coverUrl = credits?.coverUrl?.ifBlank { track.coverUrl } ?: track.coverUrl,
                        accentColor = track.accentColor,
                        size = 54.dp,
                        cornerRadius = 14.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "KREDIT & INFO LAGU",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.1.sp,
                                color = accent
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = credits?.title?.ifBlank { track.title } ?: track.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append(credits?.artist?.ifBlank { track.artist } ?: track.artist)
                                val alb = credits?.album?.ifBlank { track.album } ?: track.album
                                if (alb.isNotBlank()) append(" • $alb")
                            },
                            fontSize = 12.5.sp,
                            color = sub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(chipFill)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup",
                            tint = ink,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(thickness = 0.6.dp, color = dividerColor)
                Spacer(modifier = Modifier.height(10.dp))

                if (isLoading && credits == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Mengambil kredit resmi lagu...",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = sub
                            )
                        }
                    }
                } else {
                    val data = credits ?: TrackCreditsData(
                        videoId = track.videoId,
                        title = track.title,
                        artist = track.artist,
                        artistBrowseId = track.artistBrowseId,
                        album = track.album,
                        albumBrowseId = track.albumBrowseId,
                        coverUrl = track.coverUrl,
                        performers = listOf(track.artist),
                        durationSec = track.durationSec
                    )

                    val writersMerged = (data.composers + data.lyricists).distinct()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CreditRowItem(
                            icon = Icons.Rounded.Person,
                            label = "Penampil",
                            value = data.performers.ifEmpty { listOf(track.artist) }.joinToString(", "),
                            ink = ink,
                            sub = sub,
                            chipFill = chipFill
                        )
                        CreditRowItem(
                            icon = Icons.Rounded.EditNote,
                            label = "Komposer / Penulis Lirik",
                            value = writersMerged.joinToString(", ").ifBlank { "Tidak tercantum di metadata publik" },
                            ink = ink,
                            sub = sub,
                            chipFill = chipFill
                        )
                        CreditRowItem(
                            icon = Icons.Rounded.Tune,
                            label = "Produser",
                            value = data.producers.joinToString(", ").ifBlank { "Tidak tercantum di metadata publik" },
                            ink = ink,
                            sub = sub,
                            chipFill = chipFill
                        )
                        CreditRowItem(
                            icon = Icons.Rounded.Business,
                            label = "Label Rekaman",
                            value = data.recordLabel.ifBlank { "Rilisan Independen / Distribusi Digital" },
                            ink = ink,
                            sub = sub,
                            chipFill = chipFill
                        )
                        if (data.copyright.isNotBlank()) {
                            CreditRowItem(
                                icon = Icons.Rounded.Copyright,
                                label = "Hak Cipta",
                                value = data.copyright,
                                ink = ink,
                                sub = sub,
                                chipFill = chipFill
                            )
                        }
                        if (data.releaseDate.isNotBlank()) {
                            CreditRowItem(
                                icon = Icons.Rounded.CalendarToday,
                                label = "Tanggal Rilis",
                                value = data.releaseDate,
                                ink = ink,
                                sub = sub,
                                chipFill = chipFill
                            )
                        }
                        if (data.genre.isNotBlank()) {
                            CreditRowItem(
                                icon = Icons.Rounded.Category,
                                label = "Genre",
                                value = data.genre,
                                ink = ink,
                                sub = sub,
                                chipFill = chipFill
                            )
                        }
                        if (data.bpm > 0) {
                            CreditRowItem(
                                icon = Icons.Rounded.Speed,
                                label = "Tempo (BPM)",
                                value = "${data.bpm} BPM",
                                ink = ink,
                                sub = sub,
                                chipFill = chipFill
                            )
                        }
                        CreditRowItem(
                            icon = Icons.Rounded.Schedule,
                            label = "Durasi",
                            value = if (data.durationSec > 0) formatDuration(data.durationSec) else "Adaptif",
                            ink = ink,
                            sub = sub,
                            chipFill = chipFill
                        )
                        CreditRowItem(
                            icon = Icons.Rounded.GraphicEq,
                            label = "Kualitas Stream Aktif",
                            value = data.streamQualityLabel,
                            ink = ink,
                            sub = sub,
                            chipFill = chipFill
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Navigation Actions: Buka Album & Buka Profil Artis
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val albumTarget = track.copy(
                        album = credits?.album?.ifBlank { track.album } ?: track.album,
                        albumBrowseId = credits?.albumBrowseId?.ifBlank { track.albumBrowseId } ?: track.albumBrowseId
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent)
                            .clickable { onOpenAlbum(albumTarget) }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Album,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Buka Album",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(chipFill)
                            .clickable {
                                val aId = credits?.artistBrowseId?.ifBlank { track.artistBrowseId } ?: track.artistBrowseId
                                onOpenArtist(aId, track.artist)
                            }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = ink,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Profil Artis",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ink,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditRowItem(
    icon: ImageVector,
    label: String,
    value: String,
    ink: Color,
    sub: Color,
    chipFill: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(chipFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = ink,
                modifier = Modifier.size(17.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = sub
            )
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = ink,
                lineHeight = 18.sp
            )
        }
    }
}
