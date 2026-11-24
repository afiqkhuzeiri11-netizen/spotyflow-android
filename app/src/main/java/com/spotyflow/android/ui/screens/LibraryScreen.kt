package com.spotyflow.android.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.data.PreviewSheetData
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing

private data class LibraryTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badgeColor: Color
)

@Composable
fun LibraryScreen(
    uiState: PlayerUiState,
    topHeaderClearance: Dp,
    onOpenPreview: (PreviewSheetData) -> Unit,
    modifier: Modifier = Modifier
) {
    val tiles = listOf(
        LibraryTile(
            title = "Disukai",
            subtitle = "${uiState.likedTrackIds.size} lagu favorit",
            icon = Icons.Rounded.Favorite,
            badgeColor = SpotyColors.HeartRed
        ),
        LibraryTile(
            title = "Diunduh",
            subtitle = "Siap offline M4A",
            icon = Icons.Rounded.DownloadDone,
            badgeColor = SpotyColors.EmeraldCrystal
        ),
        LibraryTile(
            title = "Tersimpan",
            subtitle = "Cache instan",
            icon = Icons.Rounded.CloudDone,
            badgeColor = Color(0xFF0284C7)
        ),
        LibraryTile(
            title = "Di Perangkat",
            subtitle = "Audio lokal HP",
            icon = Icons.Rounded.Folder,
            badgeColor = Color(0xFFF59E0B)
        ),
        LibraryTile(
            title = "Top 50",
            subtitle = "Paling sering diputar",
            icon = Icons.Rounded.TrendingUp,
            badgeColor = Color(0xFF7C3AED)
        ),
        LibraryTile(
            title = "Diunggah",
            subtitle = "Koleksi pribadi",
            icon = Icons.Rounded.CloudUpload,
            badgeColor = Color(0xFFEC4899)
        )
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = topHeaderClearance,
            bottom = SpotySpacing.BottomScrollClearance
        ),
        verticalArrangement = Arrangement.spacedBy(SpotySpacing.SectionGap)
    ) {
        // Header Row: Pustaka + 2 Glass Capsule Action Buttons
        item(key = "lib_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pustaka",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SpotyColors.Slate900
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpotyGlassCard(
                        shape = RoundedCornerShape(999.dp),
                        tintAlpha = 0.68f,
                        elevation = 6.dp,
                        onClick = {
                            onOpenPreview(
                                PreviewSheetData(
                                    title = "Buat Playlist Baru",
                                    subtitle = "Susun playlist pribadimu di SpotyFlow Native",
                                    badge = "PUSTAKA",
                                    bullets = listOf(
                                        "Simpan lokal super cepat di database Room (Tahap 2)",
                                        "Urutkan lagu dengan drag & drop mulus"
                                    )
                                )
                            )
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Baru",
                                tint = SpotyColors.Slate900,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Baru",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = SpotyColors.Slate900
                            )
                        }
                    }

                    SpotyGlassCard(
                        shape = RoundedCornerShape(999.dp),
                        tintAlpha = 0.68f,
                        elevation = 6.dp,
                        onClick = {
                            onOpenPreview(
                                PreviewSheetData(
                                    title = "Impor Playlist",
                                    subtitle = "Bawa playlist dari Spotify & YouTube Music",
                                    badge = "IMPOR",
                                    bullets = listOf(
                                        "Tempel tautan playlist publik untuk menyalin semua lagu",
                                        "Otomatis cocokkan ke stream audio HD di Tahap 2"
                                    )
                                )
                            )
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = "Impor",
                                tint = SpotyColors.DeepEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Impor",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = SpotyColors.Slate900
                            )
                        }
                    }
                }
            }
        }

        // 2x3 Glass Collection Grid (Original SpotyFlow Signature Layout)
        item(key = "lib_grid") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(SpotySpacing.CardGap)
            ) {
                tiles.chunked(2).forEach { rowPair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SpotySpacing.CardGap)
                    ) {
                        rowPair.forEach { tile ->
                            SpotyGlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(106.dp),
                                shape = RoundedCornerShape(22.dp),
                                tintAlpha = 0.64f,
                                elevation = 8.dp,
                                onClick = {
                                    onOpenPreview(
                                        PreviewSheetData(
                                            title = tile.title,
                                            subtitle = tile.subtitle,
                                            badge = "KOLEKSI",
                                            bullets = listOf(
                                                "Koleksi pintar otomatis yang selalu sinkron",
                                                "Mendukung putar acak & unduh offline satu sentuhan"
                                            )
                                        )
                                    )
                                }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(tile.badgeColor.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = tile.icon,
                                            contentDescription = tile.title,
                                            tint = tile.badgeColor,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = tile.title,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = SpotyColors.Slate900,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = tile.subtitle,
                                            fontSize = 11.5.sp,
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
            }
        }

        // User Playlists Section
        item(key = "lib_playlists") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Playlist Kamu",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SpotyColors.Slate900
                )

                uiState.playlists.forEach { playlist ->
                    SpotyGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        tintAlpha = 0.64f,
                        elevation = 8.dp,
                        onClick = {
                            onOpenPreview(
                                PreviewSheetData(
                                    title = playlist.title,
                                    subtitle = "${playlist.trackCount} lagu • ${playlist.subtitle}",
                                    badge = "PLAYLIST",
                                    bullets = listOf(
                                        "Putar seluruh antrean dengan transisi tanpa jeda",
                                        "Bagikan sebagai Poster 3D Studio"
                                    )
                                )
                            )
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(playlist.accentColor, playlist.secondaryColor)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SpotyColors.Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${playlist.trackCount} lagu • ${playlist.subtitle}",
                                    fontSize = 12.sp,
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
    }
}
