package com.spotyflow.android.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.spotyflow.android.data.ArtistProfileData
import com.spotyflow.android.data.ArtistSummary
import com.spotyflow.android.data.CollectionSummary
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.formatDuration
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.glass.SpotyGlassCircleButton
import com.spotyflow.android.ui.glass.SpotyMiniEqBars
import com.spotyflow.android.ui.theme.LocalSpotyAppearance
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing

@Composable
fun ArtistProfileScreen(
    fallbackName: String,
    profile: ArtistProfileData?,
    isLoading: Boolean,
    isFollowed: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: (List<TrackItem>, Boolean) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onToggleFollow: (ArtistSummary) -> Unit,
    onOpenCollection: (CollectionSummary) -> Unit,
    onOpenRelatedArtist: (ArtistSummary) -> Unit,
    onOpenTrackMenu: (TrackItem) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val app = LocalSpotyAppearance.current
    val ink = app.textPrimary
    val sub = app.textSecondary
    val accent = app.accent
    val canvasBg = app.canvasBackground
    var bioExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading && profile == null) {
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
                    text = "Memuat profil $fallbackName...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SpotyColors.Slate500
                )
            }
        } else if (profile == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Profil $fallbackName belum tersedia",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotyColors.Slate900
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = SpotySpacing.BottomScrollClearance),
                verticalArrangement = Arrangement.spacedBy(SpotySpacing.SectionGap)
            ) {
                // 1. Hero Artist HD Banner fading smoothly into active theme canvas + Glass Avatar + Metadata + Actions
                item(key = "artist_hero") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Full-width HD Banner (268.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(268.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            accent.copy(alpha = if (app.isDark) 0.36f else 0.24f),
                                            canvasBg
                                        )
                                    )
                                )
                        ) {
                            if (profile.bannerUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(profile.bannerUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = profile.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            // Seamless theme-aware melt gradient into active canvas (Pure White / Dark / AMOLED)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                canvasBg.copy(alpha = if (app.isDark) 0.16f else 0.08f),
                                                canvasBg.copy(alpha = if (app.isDark) 0.56f else 0.44f),
                                                canvasBg
                                            )
                                        )
                                    )
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(top = 72.dp, start = 20.dp, end = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Crystal Glass Framed Circular Avatar
                            Box(
                                modifier = Modifier
                                    .size(116.dp)
                                    .shadow(
                                        elevation = 16.dp,
                                        shape = CircleShape,
                                        ambientColor = Color(0x220F172A)
                                    )
                                    .clip(CircleShape)
                                    .background(if (app.isDark) Color(0xFF1A1E2A) else Color.White)
                                    .border(3.dp, if (app.isDark) Color.White.copy(alpha = 0.25f) else Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = SpotyColors.Slate300,
                                    modifier = Modifier.size(48.dp)
                                )
                                if (profile.avatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(profile.avatarUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = profile.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = profile.name,
                                fontSize = 28.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ink,
                                textAlign = TextAlign.Center
                            )

                            val metaSub = listOf(
                                profile.subscribersText,
                                profile.monthlyListenersText.takeIf { it != profile.subscribersText }.orEmpty()
                            ).filter { it.isNotBlank() }.joinToString(" • ")

                            if (metaSub.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = metaSub,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = sub,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Country, Debut Year & Genre pills (enriched from TheAudioDB / Deezer)
                            val badges = buildList {
                                if (profile.country.isNotBlank()) add(profile.country)
                                if (profile.debutYear.isNotBlank()) add("Debut ${profile.debutYear}")
                                if (profile.genre.isNotBlank()) add(profile.genre)
                            }
                            if (badges.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    badges.forEach { badge ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(
                                                    if (app.isDark) Color.White.copy(alpha = 0.10f)
                                                    else SpotyColors.Slate900.copy(alpha = 0.06f)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = badge,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = sub,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 3 Aligned Action Pills: Putar | Acak | Ikuti
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
                                            if (profile.topSongs.isNotEmpty()) {
                                                onPlayAll(profile.topSongs, false)
                                            }
                                        }
                                        .padding(horizontal = 20.dp, vertical = 11.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Putar",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Text(
                                            text = "Putar",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                SpotyGlassCard(
                                    shape = RoundedCornerShape(999.dp),
                                    tintAlpha = 0.72f,
                                    elevation = 6.dp,
                                    onClick = {
                                        if (profile.topSongs.isNotEmpty()) {
                                            onPlayAll(profile.topSongs, true)
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shuffle,
                                            contentDescription = "Acak",
                                            tint = SpotyColors.Slate900,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = "Acak",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SpotyColors.Slate900
                                        )
                                    }
                                }

                                SpotyGlassCard(
                                    shape = RoundedCornerShape(999.dp),
                                    tintAlpha = if (isFollowed) 0.84f else 0.72f,
                                    elevation = 6.dp,
                                    onClick = {
                                        onToggleFollow(
                                            ArtistSummary(
                                                browseId = profile.browseId,
                                                name = profile.name,
                                                subscribersText = profile.subscribersText,
                                                avatarUrl = profile.avatarUrl
                                            )
                                        )
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFollowed) Icons.Rounded.Check else Icons.Rounded.PersonAdd,
                                            contentDescription = null,
                                            tint = if (isFollowed) SpotyColors.DeepEmerald else SpotyColors.Slate900,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isFollowed) "Mengikuti" else "Ikuti",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFollowed) SpotyColors.DeepEmerald else SpotyColors.Slate900
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Top Songs
                if (profile.topSongs.isNotEmpty()) {
                    item(key = "artist_top_songs") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SpotySpacing.ScreenHorizontal),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Lagu Teratas",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SpotyColors.Slate900
                            )
                            SpotyGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                tintAlpha = 0.66f
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    profile.topSongs.take(12).forEachIndexed { idx, track ->
                                        val isActive = track.id == currentTrackId
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onPlayTrack(track, profile.topSongs) }
                                                .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SpotyArtwork(
                                                coverUrl = track.coverUrl,
                                                accentColor = track.accentColor,
                                                size = 48.dp,
                                                cornerRadius = 12.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = track.title,
                                                    fontSize = 14.5.sp,
                                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    color = if (isActive) SpotyColors.DeepEmerald else SpotyColors.Slate900,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = track.album.ifBlank { track.artist },
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
                                        if (idx < minOf(12, profile.topSongs.size) - 1) {
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

                // 3. Albums Carousel
                if (profile.albums.isNotEmpty()) {
                    item(key = "artist_albums") {
                        CollectionCarouselSection(
                            title = "Album",
                            items = profile.albums,
                            onItemClick = onOpenCollection
                        )
                    }
                }

                // 4. Singles & EP Carousel
                if (profile.singles.isNotEmpty()) {
                    item(key = "artist_singles") {
                        CollectionCarouselSection(
                            title = "Single & EP",
                            items = profile.singles,
                            onItemClick = onOpenCollection
                        )
                    }
                }

                // 5. About Artist (Expandable Bio)
                if (profile.description.isNotBlank()) {
                    item(key = "artist_bio") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SpotySpacing.ScreenHorizontal),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Tentang Artis",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SpotyColors.Slate900
                            )
                            SpotyGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                tintAlpha = 0.64f,
                                onClick = { bioExpanded = !bioExpanded }
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = profile.description,
                                        fontSize = 13.5.sp,
                                        lineHeight = 20.sp,
                                        color = SpotyColors.Slate700,
                                        maxLines = if (bioExpanded) 40 else 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (bioExpanded) "Tampilkan lebih sedikit" else "Baca selengkapnya",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SpotyColors.DeepEmerald
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Related Artists
                if (profile.relatedArtists.isNotEmpty()) {
                    item(key = "artist_related") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Artis Serupa",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SpotyColors.Slate900,
                                modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = SpotySpacing.ScreenHorizontal),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(profile.relatedArtists, key = { it.browseId }) { rel ->
                                    Column(
                                        modifier = Modifier
                                            .width(92.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onOpenRelatedArtist(rel) }
                                            .padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        SpotyArtwork(
                                            coverUrl = rel.avatarUrl,
                                            accentColor = SpotyColors.EmeraldCrystal,
                                            size = 80.dp,
                                            cornerRadius = 999.dp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = rel.name,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SpotyColors.Slate900,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Back Button (Always crisp & accessible)
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

@Composable
private fun CollectionCarouselSection(
    title: String,
    items: List<CollectionSummary>,
    onItemClick: (CollectionSummary) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SpotyColors.Slate900,
            modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = SpotySpacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.browseId }) { col ->
                SpotyGlassCard(
                    modifier = Modifier.width(146.dp),
                    shape = RoundedCornerShape(22.dp),
                    tintAlpha = 0.64f,
                    onClick = { onItemClick(col) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        SpotyArtwork(
                            coverUrl = col.coverUrl,
                            accentColor = SpotyColors.EmeraldCrystal,
                            size = 126.dp,
                            cornerRadius = 16.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = col.title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotyColors.Slate900,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = col.year.ifBlank { col.subtitle.ifBlank { "Album" } },
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
