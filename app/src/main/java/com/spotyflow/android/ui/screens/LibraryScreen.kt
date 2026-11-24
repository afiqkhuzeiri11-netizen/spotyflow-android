package com.spotyflow.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.ArtistSummary
import com.spotyflow.android.data.CollectionSummary
import com.spotyflow.android.data.CustomPlaylist
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.local.OfflineDownloadManager
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing

private data class LibraryTile(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badgeColor: Color,
    val visible: Boolean
)

private enum class LibraryFilterTab(val label: String) {
    ALL("Semua"),
    PLAYLISTS("Playlist"),
    SONGS("Lagu"),
    ALBUMS("Album"),
    ARTISTS("Artis")
}

@Composable
fun LibraryScreen(
    uiState: PlayerUiState,
    topHeaderClearance: Dp,
    onOpenLocalCollection: (String, String) -> Unit,
    onOpenCustomPlaylist: (CustomPlaylist) -> Unit,
    onOpenSavedCollection: (CollectionSummary) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onImportPlaylistUrl: (String) -> Unit,
    onAddUploadedUris: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = uiState.appSettings

    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var newPlTitle by remember { mutableStateOf("") }
    var importUrl by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(LibraryFilterTab.ALL) }

    val uploadAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddUploadedUris(uris)
        }
    }

    val offlineMb = remember(uiState.downloadedTracks.size) {
        OfflineDownloadManager.formatSizeMb(OfflineDownloadManager.totalOfflineBytes(context))
    }

    val tiles = listOf(
        LibraryTile("liked", "Disukai", "${uiState.likedTracks.size} lagu favorit", Icons.Rounded.Favorite, SpotyColors.HeartRed, settings.showColLiked),
        LibraryTile("downloaded", "Diunduh", "${uiState.downloadedTracks.size} lagu • $offlineMb", Icons.Rounded.DownloadDone, SpotyColors.EmeraldCrystal, settings.showColDownloaded),
        LibraryTile("cached", "Tersimpan", "${uiState.recentTracks.size} riwayat", Icons.Rounded.CloudDone, Color(0xFF0284C7), settings.showColCached),
        LibraryTile("device", "Di Perangkat", "${uiState.deviceTracks.size} audio lokal", Icons.Rounded.Folder, Color(0xFFF59E0B), settings.showColDevice),
        LibraryTile("top50", "Top 50", "Paling sering diputar", Icons.Rounded.TrendingUp, Color(0xFF7C3AED), settings.showColTop50),
        LibraryTile("uploaded", "Diunggah", "${uiState.uploadedTracks.size} file pribadi", Icons.Rounded.CloudUpload, Color(0xFFEC4899), settings.showColUploaded)
    ).filter { it.visible }

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
                Text("Pustaka", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)

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
                            Icon(Icons.Rounded.Add, contentDescription = "Baru", tint = SpotyColors.Slate900, modifier = Modifier.size(16.dp))
                            Text("Baru", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                        }
                    }

                    SpotyGlassCard(
                        shape = RoundedCornerShape(999.dp),
                        tintAlpha = 0.68f,
                        elevation = 6.dp,
                        onClick = { showImportDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = "Impor", tint = SpotyColors.DeepEmerald, modifier = Modifier.size(16.dp))
                            Text("Impor", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                        }
                    }
                }
            }
        }

        // Sub-tab Filter Pills (Semua | Playlist | Lagu | Album | Artis)
        item(key = "lib_filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = SpotySpacing.ScreenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LibraryFilterTab.entries) { tab ->
                    val sel = activeFilter == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (sel) SpotyColors.Slate900 else SpotyColors.Slate900.copy(alpha = 0.07f))
                            .clickable { activeFilter = tab }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(tab.label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else SpotyColors.Slate900)
                    }
                }
            }
        }

        // 2x3 Glass Collection Grid (All 6 Real Collections!)
        if ((activeFilter == LibraryFilterTab.ALL || activeFilter == LibraryFilterTab.SONGS) && tiles.isNotEmpty()) {
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
                                        if (tile.key == "uploaded" && uiState.uploadedTracks.isEmpty()) {
                                            uploadAudioLauncher.launch(arrayOf("audio/*"))
                                        } else {
                                            onOpenLocalCollection(tile.key, tile.title)
                                        }
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(14.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(tile.badgeColor.copy(alpha = 0.14f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(tile.icon, contentDescription = tile.title, tint = tile.badgeColor, modifier = Modifier.size(19.dp))
                                            }
                                            if (tile.key == "uploaded") {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(SpotyColors.Slate900.copy(alpha = 0.08f))
                                                        .clickable { uploadAudioLauncher.launch(arrayOf("audio/*")) }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("+ File", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                                                }
                                            }
                                        }
                                        Column {
                                            Text(tile.title, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900, maxLines = 1)
                                            Text(tile.subtitle, fontSize = 11.5.sp, color = SpotyColors.Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            if (rowPair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Followed Artists Filter View
        if ((activeFilter == LibraryFilterTab.ALL || activeFilter == LibraryFilterTab.ARTISTS) && uiState.followedArtists.isNotEmpty()) {
            item(key = "lib_artists") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SpotySpacing.ScreenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Artis Diikuti (${uiState.followedArtists.size})", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                    uiState.followedArtists.forEach { artist ->
                        SpotyGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            tintAlpha = 0.64f,
                            onClick = { onOpenArtist(artist) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                SpotyArtwork(artist.avatarUrl, SpotyColors.EmeraldCrystal, 48.dp, 999.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(artist.name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                                    Text(artist.subscribersText.ifBlank { "Artis" }, fontSize = 12.sp, color = SpotyColors.Slate500)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Saved Albums / Collections
        if ((activeFilter == LibraryFilterTab.ALL || activeFilter == LibraryFilterTab.ALBUMS) && uiState.savedCollections.isNotEmpty()) {
            item(key = "lib_saved") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SpotySpacing.ScreenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Album & Playlist Tersimpan", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                    uiState.savedCollections.forEach { col ->
                        SpotyGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            tintAlpha = 0.64f,
                            onClick = { onOpenSavedCollection(col) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                SpotyArtwork(col.coverUrl, SpotyColors.EmeraldCrystal, 50.dp, 13.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(col.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(col.subtitle.ifBlank { if (col.isAlbum) "Album" else "Playlist" }, fontSize = 12.sp, color = SpotyColors.Slate500, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom Playlists
        if (activeFilter == LibraryFilterTab.ALL || activeFilter == LibraryFilterTab.PLAYLISTS) {
            item(key = "lib_playlists") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SpotySpacing.ScreenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Playlist Kamu (${uiState.customPlaylists.size})", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)

                    if (uiState.customPlaylists.isEmpty()) {
                        SpotyGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            tintAlpha = 0.62f,
                            onClick = { showCreateDialog = true }
                        ) {
                            Text(
                                text = "Belum ada playlist pribadi. Ketuk + Baru atau Impor dari Spotify/YouTube Music.",
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
                                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Brush.linearGradient(listOf(SpotyColors.EmeraldCrystal, Color(0xFF0EA5E9)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(playlist.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${playlist.tracks.size} lagu", fontSize = 12.sp, color = SpotyColors.Slate500)
                                    }
                                    IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Hapus", tint = SpotyColors.Slate500, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Songs quick list when Lagu filter selected
        if (activeFilter == LibraryFilterTab.SONGS && uiState.likedTracks.isNotEmpty()) {
            item(key = "lib_liked_songs_preview") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SpotySpacing.ScreenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Lagu Disukai", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
                    uiState.likedTracks.take(15).forEach { track ->
                        SpotyGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            tintAlpha = 0.64f,
                            onClick = { onPlayTrack(track, uiState.likedTracks) }
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                SpotyArtwork(track.coverUrl, track.accentColor, 44.dp, 10.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, fontSize = 12.sp, color = SpotyColors.Slate500, maxLines = 1)
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
                OutlinedTextField(value = newPlTitle, onValueChange = { newPlTitle = it }, placeholder = { Text("Nama playlist...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlTitle.isNotBlank()) {
                        onCreatePlaylist(newPlTitle.trim())
                        newPlTitle = ""
                        showCreateDialog = false
                    }
                }) { Text("Buat", fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Batal", color = SpotyColors.Slate500) }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = Color.White,
            title = { Text("Impor Spotify / YouTube Music", fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tempel tautan playlist publik Spotify atau YouTube Music (list=...):", fontSize = 12.5.sp, color = SpotyColors.Slate500)
                    OutlinedTextField(
                        value = importUrl,
                        onValueChange = { importUrl = it },
                        placeholder = { Text("https://open.spotify.com/playlist/...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (importUrl.isNotBlank()) {
                        onImportPlaylistUrl(importUrl.trim())
                        importUrl = ""
                        showImportDialog = false
                    }
                }) { Text("Impor", fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald) }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Batal", color = SpotyColors.Slate500) }
            }
        )
    }
}
