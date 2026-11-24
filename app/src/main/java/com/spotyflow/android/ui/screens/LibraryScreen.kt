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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.spotyflow.android.data.CollectionSummary
import com.spotyflow.android.data.CustomPlaylist
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.data.PreviewSheetData
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing

private data class LibraryTile(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badgeColor: Color
)

@Composable
fun LibraryScreen(
    uiState: PlayerUiState,
    topHeaderClearance: Dp,
    onOpenLocalCollection: (String, String) -> Unit,
    onOpenCustomPlaylist: (CustomPlaylist) -> Unit,
    onOpenSavedCollection: (CollectionSummary) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onOpenPreview: (PreviewSheetData) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlTitle by remember { mutableStateOf("") }

    val tiles = listOf(
        LibraryTile("liked", "Disukai", "${uiState.likedTracks.size} lagu favorit", Icons.Rounded.Favorite, SpotyColors.HeartRed),
        LibraryTile("downloaded", "Diunduh", "Siap di Tahap 3", Icons.Rounded.DownloadDone, SpotyColors.EmeraldCrystal),
        LibraryTile("cached", "Tersimpan", "${uiState.recentTracks.size} riwayat", Icons.Rounded.CloudDone, Color(0xFF0284C7)),
        LibraryTile("device", "Di Perangkat", "Audio lokal HP", Icons.Rounded.Folder, Color(0xFFF59E0B)),
        LibraryTile("top50", "Top 50", "Paling sering diputar", Icons.Rounded.TrendingUp, Color(0xFF7C3AED)),
        LibraryTile("uploaded", "Diunggah", "Koleksi pribadi", Icons.Rounded.CloudUpload, Color(0xFFEC4899))
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = topHeaderClearance,
            bottom = SpotySpacing.BottomScrollClearance
        ),
        verticalArrangement = Arrangement.spacedBy(SpotySpacing.SectionGap)
    ) {
        // Header Row: Pustaka + Baru & Impor
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
                        onClick = { showCreateDialog = true }
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
                                    title = "Impor Spotify & YouTube Music",
                                    subtitle = "Hadir penuh di Tahap 3 bersama Offline Download",
                                    badge = "TAHAP 3",
                                    bullets = listOf(
                                        "Tempel tautan playlist Spotify / YTM langsung ke Pustaka",
                                        "Resolusi otomatis stream HD di perangkat"
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

        // 2x3 Glass Collection Grid
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
                                    if (tile.key in listOf("liked", "cached", "top50")) {
                                        onOpenLocalCollection(tile.key, tile.title)
                                    } else {
                                        onOpenPreview(
                                            PreviewSheetData(
                                                title = tile.title,
                                                subtitle = tile.subtitle,
                                                badge = "TAHAP 3",
                                                bullets = listOf(
                                                    "Offline Download M4A & MediaStore Scanner hadir penuh di Tahap 3"
                                                )
                                            )
                                        )
                                    }
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

        // Saved Albums / Playlists
        if (uiState.savedCollections.isNotEmpty()) {
            item(key = "lib_saved") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpotySpacing.ScreenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Album & Playlist Tersimpan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SpotyColors.Slate900
                    )
                    uiState.savedCollections.forEach { col ->
                        SpotyGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            tintAlpha = 0.64f,
                            onClick = { onOpenSavedCollection(col) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SpotyArtwork(
                                    coverUrl = col.coverUrl,
                                    accentColor = SpotyColors.EmeraldCrystal,
                                    size = 50.dp,
                                    cornerRadius = 13.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = col.title,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SpotyColors.Slate900,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = col.subtitle.ifBlank { if (col.isAlbum) "Album" else "Playlist" },
                                        fontSize = 12.sp,
                                        color = SpotyColors.Slate500,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom Playlists
        item(key = "lib_playlists") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Playlist Kamu (${uiState.customPlaylists.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SpotyColors.Slate900
                )

                if (uiState.customPlaylists.isEmpty()) {
                    SpotyGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        tintAlpha = 0.62f,
                        onClick = { showCreateDialog = true }
                    ) {
                        Text(
                            text = "Belum ada playlist pribadi. Ketuk + Baru untuk membuat playlist pertamamu.",
                            fontSize = 13.sp,
                            color = SpotyColors.Slate500,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                } else {
                    uiState.customPlaylists.forEach { playlist ->
                        SpotyGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            tintAlpha = 0.64f,
                            onClick = { onOpenCustomPlaylist(playlist) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(SpotyColors.EmeraldCrystal, Color(0xFF0EA5E9))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(23.dp)
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
                                    Text(
                                        text = "${playlist.tracks.size} lagu",
                                        fontSize = 12.sp,
                                        color = SpotyColors.Slate500
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color.White,
            title = { Text("Playlist Baru", fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900) },
            text = {
                OutlinedTextField(
                    value = newPlTitle,
                    onValueChange = { newPlTitle = it },
                    placeholder = { Text("Nama playlist...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlTitle.isNotBlank()) {
                            onCreatePlaylist(newPlTitle.trim())
                            newPlTitle = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Buat", fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Batal", color = SpotyColors.Slate500)
                }
            }
        )
    }
}
