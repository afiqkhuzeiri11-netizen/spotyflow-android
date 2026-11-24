package com.spotyflow.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.spotyflow.android.data.ArtistSummary
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyFlameVector
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing
import java.io.File

private data class ProfileMenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClickAction: () -> Unit
)

@Composable
fun ProfileScreen(
    uiState: PlayerUiState,
    topHeaderClearance: Dp,
    onUpdateProfile: (String, String, String) -> Unit,
    onUpdateAvatar: (Uri) -> Unit,
    onOpenLocalCollection: (String, String) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenLibraryTab: () -> Unit,
    onOpenPoster3D: () -> Unit,
    onOpenStatsRecap: () -> Unit,
    onOpenListenTogether: () -> Unit,
    onOpenSongRecognize: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenDynamicIsland: () -> Unit,
    onOpenWidgetSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prof = uiState.userProfile
    val streak = uiState.dailyStreak

    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember(prof.displayName) { mutableStateOf(prof.displayName) }
    var editHandle by remember(prof.handle) { mutableStateOf(prof.handle) }
    var editBio by remember(prof.bio) { mutableStateOf(prof.bio) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onUpdateAvatar(uri)
    }

    val creativeItems = listOf(
        ProfileMenuItem(
            title = "Poster 3D Studio",
            subtitle = "Foto sendiri + Copy Objek/Manusia di depan sampul musik",
            icon = Icons.Rounded.AutoAwesome,
            onClickAction = onOpenPoster3D
        ),
        ProfileMenuItem(
            title = "Statistik & Recap",
            subtitle = "${streak.minutesToday} mnt hari ini • ${streak.count} hari beruntun",
            icon = Icons.Rounded.BarChart,
            onClickAction = onOpenStatsRecap
        ),
        ProfileMenuItem(
            title = "Dengar Bersama",
            subtitle = "Sinkronkan musik real-time bareng teman",
            icon = Icons.Rounded.Group,
            onClickAction = onOpenListenTogether
        ),
        ProfileMenuItem(
            title = "Kenali Lagu",
            subtitle = "Radar mikrofon & pencocok potongan lirik",
            icon = Icons.Rounded.GraphicEq,
            onClickAction = onOpenSongRecognize
        )
    )

    val systemItems = listOf(
        ProfileMenuItem(
            title = "Ekualiser & Audio",
            subtitle = "5-Band Hardware DSP, Skip Silence & Varispeed",
            icon = Icons.Rounded.Equalizer,
            onClickAction = onOpenEqualizer
        ),
        ProfileMenuItem(
            title = "Dynamic Island",
            subtitle = "Custom Dynamic Island via Aksesibilitas (6 Gaya + Lirik Live)",
            icon = Icons.Rounded.Layers,
            onClickAction = onOpenDynamicIsland
        ),
        ProfileMenuItem(
            title = "Pengaturan Widget",
            subtitle = "Live Preview 5 ukuran Widget Liquid Glass Home Screen",
            icon = Icons.Rounded.Widgets,
            onClickAction = onOpenWidgetSettings
        ),
        ProfileMenuItem(
            title = "Pengaturan Lengkap",
            subtitle = "10 Kategori • Real Water Drops, Tema, Cache & Backup .json",
            icon = Icons.Rounded.Settings,
            onClickAction = onOpenSettings
        ),
        ProfileMenuItem(
            title = "Izin Notifikasi & Baterai",
            subtitle = "Pastikan pemutaran latar belakang tidak dihentikan sistem",
            icon = Icons.Rounded.NotificationsActive,
            onClickAction = {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Throwable) {}
            }
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
        item(key = "user_profile_card") {
            SpotyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(26.dp),
                tintAlpha = 0.68f,
                elevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clickable {
                                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(SpotyColors.EmeraldCrystal, SpotyColors.DeepEmerald)))
                                    .border(2.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                                val cleanPath = prof.avatarPath.substringBefore("?")
                                if (cleanPath.isNotBlank() && File(cleanPath).exists()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(File(cleanPath)).crossfade(true).build(),
                                        contentDescription = "Foto Profil",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(SpotyColors.Slate900)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = "Ganti Foto", tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(prof.displayName, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(prof.handle, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = SpotyColors.DeepEmerald)
                            if (prof.bio.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(prof.bio, fontSize = 12.sp, color = SpotyColors.Slate500, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(SpotyColors.Slate900.copy(alpha = 0.08f))
                                .clickable { showEditDialog = true }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit Profil", tint = SpotyColors.Slate900, modifier = Modifier.size(14.dp))
                                Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileStatPill("Disukai", "${uiState.likedTracks.size}", { onOpenLocalCollection("liked", "Disukai") }, Modifier.weight(1f))
                        ProfileStatPill("Diikuti", "${uiState.followedArtists.size}", { uiState.followedArtists.firstOrNull()?.let { onOpenArtist(it) } }, Modifier.weight(1f))
                        ProfileStatPill("Playlist", "${uiState.customPlaylists.size}", onOpenLibraryTab, Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SpotyColors.FlameOrange.copy(alpha = 0.08f))
                            .clickable(onClick = onOpenStatsRecap)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SpotyFlameVector(size = 20.dp)
                            Text("${streak.count} hari beruntun • Percikan", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                        }
                        Text("${streak.minutesToday} mnt", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.DeepEmerald)
                    }
                }
            }
        }

        if (uiState.followedArtists.isNotEmpty()) {
            item(key = "followed_artists") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Artis yang Kamu Ikuti",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SpotyColors.Slate900,
                        modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = SpotySpacing.ScreenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(uiState.followedArtists, key = { it.browseId.ifBlank { it.name } }) { artist ->
                            Column(
                                modifier = Modifier
                                    .width(84.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onOpenArtist(artist) }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                SpotyArtwork(artist.avatarUrl, SpotyColors.EmeraldCrystal, 74.dp, 999.dp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(artist.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }

        item(key = "group_creative") {
            ProfileMenuGroup(eyebrow = "KREATIF & SOSIAL", items = creativeItems)
        }

        item(key = "group_system") {
            ProfileMenuGroup(eyebrow = "AUDIO, ISLAND & SISTEM", items = systemItems)
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = Color.White,
            title = { Text("Edit Profil Kamu", fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Nama Tampilan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editHandle, onValueChange = { editHandle = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editBio, onValueChange = { editBio = it }, label = { Text("Bio Singkat") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = { onUpdateProfile(editName, editHandle, editBio); showEditDialog = false }) {
                    Text("Simpan", fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Batal", color = SpotyColors.Slate500)
                }
            }
        )
    }
}

@Composable
private fun ProfileStatPill(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SpotyColors.Slate900.copy(alpha = 0.045f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900)
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = SpotyColors.Slate500)
    }
}

@Composable
private fun ProfileMenuGroup(eyebrow: String, items: List<ProfileMenuItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpotySpacing.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(eyebrow, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = SpotyColors.Slate500, modifier = Modifier.padding(start = 4.dp))
        SpotyGlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), tintAlpha = 0.64f, elevation = 10.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = item.onClickAction)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(SpotyColors.Slate900.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, contentDescription = item.title, tint = SpotyColors.Slate900, modifier = Modifier.size(19.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = SpotyColors.Slate900)
                            Text(item.subtitle, fontSize = 12.sp, color = SpotyColors.Slate500)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SpotyColors.Slate500, modifier = Modifier.size(20.dp))
                    }
                    if (idx < items.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp, end = 16.dp), thickness = 0.5.dp, color = SpotyColors.HairlineDivider)
                    }
                }
            }
        }
    }
}
