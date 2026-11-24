package com.spotyflow.android.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.data.PreviewSheetData
import com.spotyflow.android.ui.glass.SpotyFlameVector
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing

private data class ProfileMenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val preview: PreviewSheetData
)

@Composable
fun ProfileScreen(
    uiState: PlayerUiState,
    topHeaderClearance: Dp,
    onOpenPreview: (PreviewSheetData) -> Unit,
    modifier: Modifier = Modifier
) {
    val creativeItems = listOf(
        ProfileMenuItem(
            title = "Poster 3D Studio",
            subtitle = "Kartu lagu 3D melayang di belakang subjek foto",
            icon = Icons.Rounded.AutoAwesome,
            preview = PreviewSheetData(
                title = "Poster 3D Studio",
                subtitle = "Bikin poster lagu bergaya depth-effect langsung di HP",
                badge = "TAHAP 4",
                bullets = listOf(
                    "Pilih 1–5 lagu dengan sudut perspektif 3D interaktif",
                    "Segmentasi subjek AI offline via Google ML Kit",
                    "Simpan Full HD langsung ke Galeri"
                )
            )
        ),
        ProfileMenuItem(
            title = "Statistik & Recap",
            subtitle = "Menit dengar, top artis & kartu recap",
            icon = Icons.Rounded.BarChart,
            preview = PreviewSheetData(
                title = "Statistik & Recap",
                subtitle = "${uiState.listenedMinutesToday} menit hari ini • ${uiState.streakDays} hari beruntun",
                badge = "STATISTIK",
                bullets = listOf(
                    "Lagu disukai: ${uiState.likedTrackIds.size} lagu",
                    "Kartu Recap 5-Slide siap simpan & bagikan"
                )
            )
        ),
        ProfileMenuItem(
            title = "Dengar Bersama",
            subtitle = "Sinkronkan pemutaran real-time bareng teman",
            icon = Icons.Rounded.Group,
            preview = PreviewSheetData(
                title = "Dengar Bersama (Jam)",
                subtitle = "Buat ruang kode 6 huruf & dengarkan serentak",
                badge = "SOSIAL",
                bullets = listOf(
                    "Host dan tamu selalu berada di detik lagu yang sama",
                    "Bagikan kode ruang dalam satu ketukan"
                )
            )
        )
    )

    val systemItems = listOf(
        ProfileMenuItem(
            title = "Ekualiser & Audio",
            subtitle = "5-Band Hardware DSP, Bass Boost & Varispeed",
            icon = Icons.Rounded.Equalizer,
            preview = PreviewSheetData(
                title = "Ekualiser & Audio Pro",
                subtitle = "Kontrol penuh suara native Media3 ExoPlayer",
                badge = "AUDIO DSP",
                bullets = listOf(
                    "5-Band Hardware Equalizer + Loudness Enhancer",
                    "Skip Silence, Crossfade, & Normalisasi Audio (Tahap 2)"
                )
            )
        ),
        ProfileMenuItem(
            title = "Pengaturan Widget",
            subtitle = "5 ukuran Widget Liquid Glass Home Screen",
            icon = Icons.Rounded.Widgets,
            preview = PreviewSheetData(
                title = "Pengaturan Widget",
                subtitle = "Kustomisasi kilau kaca, sudut & warna aksen",
                badge = "WIDGET",
                bullets = listOf(
                    "Mendukung 5 ukuran: 1x1, 2x2, 2x4, 4x1, dan 4x2",
                    "Preview live 120 FPS tersinkron ke Home Screen Android"
                )
            )
        ),
        ProfileMenuItem(
            title = "Dynamic Island",
            subtitle = "Kapsul media & lirik live di Origin Island",
            icon = Icons.Rounded.Layers,
            preview = PreviewSheetData(
                title = "Dynamic Island & Origin Island",
                subtitle = "Kompatibel penuh dengan kapsul media Vivo & Android",
                badge = "SISTEM",
                bullets = listOf(
                    "Package ID siap integrasi MediaSession Origin Island",
                    "Tampilkan baris lirik aktif secara real-time di bilah atas"
                )
            )
        ),
        ProfileMenuItem(
            title = "Pengaturan Aplikasi",
            subtitle = "Tampilan 120Hz, cache aman & versi 1.0.0-native",
            icon = Icons.Rounded.Settings,
            preview = PreviewSheetData(
                title = "SpotyFlow Native v1.0.0",
                subtitle = "Kotlin + Jetpack Compose + Haze 1.6.10 GPU Liquid Glass",
                badge = "NATIVE 120 FPS",
                bullets = listOf(
                    "True Edge-to-Edge dengan refresh rate maksimum layar",
                    "Tahap 2: Streaming YouTube/InnerTube + ExoPlayer Background"
                )
            )
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
        // Profile Header Card + Summary
        item(key = "profile_card") {
            SpotyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                shape = RoundedCornerShape(26.dp),
                tintAlpha = 0.66f,
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
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            SpotyColors.EmeraldCrystal,
                                            SpotyColors.DeepEmerald
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pendengar SpotyFlow",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SpotyColors.Slate900
                            )
                            Text(
                                text = "SpotyFlow Native • Pure White Glass",
                                fontSize = 12.5.sp,
                                color = SpotyColors.Slate500
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SpotyColors.Slate900.copy(alpha = 0.04f))
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SpotyFlameVector(size = 20.dp)
                                Column {
                                    Text(
                                        text = "${uiState.streakDays} Hari",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SpotyColors.Slate900
                                    )
                                    Text(
                                        text = "Streak aktif",
                                        fontSize = 11.sp,
                                        color = SpotyColors.Slate500
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SpotyColors.EmeraldTint)
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "${uiState.listenedMinutesToday} Menit",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SpotyColors.DeepEmerald
                                )
                                Text(
                                    text = "Waktu dengar hari ini",
                                    fontSize = 11.sp,
                                    color = SpotyColors.Slate500
                                )
                            }
                        }
                    }
                }
            }
        }

        // Creative & Social Menu Group
        item(key = "group_creative") {
            ProfileMenuGroup(
                eyebrow = "KREATIF & SOSIAL",
                items = creativeItems,
                onItemClick = { onOpenPreview(it.preview) }
            )
        }

        // Audio & System Menu Group
        item(key = "group_system") {
            ProfileMenuGroup(
                eyebrow = "AUDIO & SISTEM",
                items = systemItems,
                onItemClick = { onOpenPreview(it.preview) }
            )
        }
    }
}

@Composable
private fun ProfileMenuGroup(
    eyebrow: String,
    items: List<ProfileMenuItem>,
    onItemClick: (ProfileMenuItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpotySpacing.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = eyebrow,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = SpotyColors.Slate500,
            modifier = Modifier.padding(start = 4.dp)
        )

        SpotyGlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            tintAlpha = 0.64f,
            elevation = 10.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item) }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SpotyColors.Slate900.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = SpotyColors.Slate900,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = SpotyColors.Slate900
                            )
                            Text(
                                text = item.subtitle,
                                fontSize = 12.sp,
                                color = SpotyColors.Slate500
                            )
                        }

                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = SpotyColors.Slate500,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (idx < items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                            thickness = 0.5.dp,
                            color = SpotyColors.HairlineDivider
                        )
                    }
                }
            }
        }
    }
}
