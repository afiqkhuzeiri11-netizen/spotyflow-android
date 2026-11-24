package com.spotyflow.android.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.ArtistSummary
import com.spotyflow.android.data.CollectionSummary
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.data.SampleCatalog
import com.spotyflow.android.data.SearchFilter
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.formatDuration
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.glass.SpotyMiniEqBars
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing

@Composable
fun SearchScreen(
    uiState: PlayerUiState,
    topHeaderClearance: Dp,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onSelectFilter: (SearchFilter) -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenCollection: (CollectionSummary) -> Unit,
    onOpenTrackMenu: (TrackItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = SearchFilter.entries
    val results = uiState.searchResults

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = topHeaderClearance,
            bottom = SpotySpacing.BottomScrollClearance
        ),
        verticalArrangement = Arrangement.spacedBy(SpotySpacing.SectionGap)
    ) {
        // Crystal Glass Search Capsule
        item(key = "search_bar") {
            SpotyGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal)
                    .height(54.dp),
                shape = RoundedCornerShape(999.dp),
                tintAlpha = 0.72f,
                elevation = 10.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Cari",
                        tint = SpotyColors.Slate500,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (uiState.searchQuery.isEmpty()) {
                            Text(
                                text = "Cari lagu, artis, album, atau playlist...",
                                fontSize = 14.5.sp,
                                color = SpotyColors.Slate500,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SpotyColors.Slate900
                            ),
                            cursorBrush = SolidColor(SpotyColors.EmeraldCrystal),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSubmitQuery(uiState.searchQuery) }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (uiState.searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(SpotyColors.Slate900.copy(alpha = 0.08f))
                                .clickable { onQueryChange("") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Hapus",
                                tint = SpotyColors.Slate700,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Filter Chips: Semua | Lagu | Video | Artis | Album | Playlist
        item(key = "search_filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = SpotySpacing.ScreenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters, key = { it.name }) { filter ->
                    val selected = uiState.searchFilter == filter
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(SpotyColors.Slate900)
                                .clickable { onSelectFilter(filter) }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = filter.label,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        SpotyGlassCard(
                            shape = RoundedCornerShape(999.dp),
                            tintAlpha = 0.58f,
                            elevation = 5.dp,
                            onClick = { onSelectFilter(filter) }
                        ) {
                            Text(
                                text = filter.label,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SpotyColors.Slate700,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.searchQuery.isBlank()) {
            // Search History Chips
            if (uiState.searchHistory.isNotEmpty()) {
                item(key = "search_history") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Riwayat Pencarian",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SpotyColors.Slate900,
                            modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = SpotySpacing.ScreenHorizontal),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.searchHistory, key = { it }) { item ->
                                SpotyGlassCard(
                                    shape = RoundedCornerShape(999.dp),
                                    tintAlpha = 0.62f,
                                    elevation = 5.dp,
                                    onClick = { onSubmitQuery(item) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.History,
                                            contentDescription = null,
                                            tint = SpotyColors.Slate500,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = item,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = SpotyColors.Slate900
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Hapus riwayat",
                                            tint = SpotyColors.Slate500,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .clickable { onRemoveHistory(item) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2-Column Mood Grid
            item(key = "mood_grid") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpotySpacing.ScreenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Telusuri Suasana",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SpotyColors.Slate900
                    )

                    SampleCatalog.moodCards.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SpotySpacing.CardGap)
                        ) {
                            pair.forEach { card ->
                                SpotyGlassCard(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(104.dp),
                                    shape = RoundedCornerShape(22.dp),
                                    tintAlpha = 0.62f,
                                    elevation = 8.dp,
                                    onClick = { onSubmitQuery(card.id) }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawCircle(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(card.glowColor, Color.Transparent),
                                                    center = Offset(size.width * 0.88f, size.height * 0.22f),
                                                    radius = size.width * 0.72f
                                                ),
                                                radius = size.width * 0.72f,
                                                center = Offset(size.width * 0.88f, size.height * 0.22f)
                                            )
                                        }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(card.accentColor)
                                            )
                                            Column {
                                                Text(
                                                    text = card.label,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = SpotyColors.Slate900,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = card.subtitle,
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
            }
        } else if (uiState.isSearching) {
            item(key = "search_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = SpotyColors.EmeraldCrystal)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Mencari \"${uiState.searchQuery}\"...",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SpotyColors.Slate500
                        )
                    }
                }
            }
        } else if (results.isEmpty) {
            item(key = "search_empty") {
                SpotyGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpotySpacing.ScreenHorizontal),
                    shape = RoundedCornerShape(22.dp),
                    tintAlpha = 0.64f
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Tidak ada hasil untuk \"${uiState.searchQuery}\"",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotyColors.Slate900
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Coba kata kunci lagu, artis, atau album lain.",
                            fontSize = 12.5.sp,
                            color = SpotyColors.Slate500
                        )
                    }
                }
            }
        } else {
            // Artists Carousel (Clickable -> Full Artist Profile!)
            if (results.artists.isNotEmpty()) {
                item(key = "sr_artists") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Artis",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SpotyColors.Slate900,
                            modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = SpotySpacing.ScreenHorizontal),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(results.artists, key = { it.browseId }) { artist ->
                                Column(
                                    modifier = Modifier
                                        .width(92.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onOpenArtist(artist) }
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    SpotyArtwork(
                                        coverUrl = artist.avatarUrl,
                                        accentColor = SpotyColors.EmeraldCrystal,
                                        size = 82.dp,
                                        cornerRadius = 999.dp
                                    )
                                    Spacer(modifier = Modifier.height(7.dp))
                                    Text(
                                        text = artist.name,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SpotyColors.Slate900,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Artis",
                                        fontSize = 11.sp,
                                        color = SpotyColors.Slate500
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tracks Section
            if (results.tracks.isNotEmpty()) {
                item(key = "sr_tracks") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpotySpacing.ScreenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Lagu (${results.tracks.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SpotyColors.Slate900
                        )
                        SpotyGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            tintAlpha = 0.64f
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                results.tracks.take(20).forEachIndexed { idx, track ->
                                    val isActive = track.id == uiState.currentTrack?.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onPlayTrack(track, results.tracks) }
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
                                                fontWeight = FontWeight.Bold,
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
                                            SpotyMiniEqBars(isPlaying = uiState.isPlaying)
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
                                    if (idx < minOf(20, results.tracks.size) - 1) {
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

            // Albums Carousel (Clickable -> Full Album Profile!)
            if (results.albums.isNotEmpty()) {
                item(key = "sr_albums") {
                    SearchCollectionRow(
                        title = "Album",
                        items = results.albums,
                        onOpen = onOpenCollection
                    )
                }
            }

            // Playlists Carousel (Clickable -> Full Playlist Profile!)
            if (results.playlists.isNotEmpty()) {
                item(key = "sr_playlists") {
                    SearchCollectionRow(
                        title = "Playlist",
                        items = results.playlists,
                        onOpen = onOpenCollection
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchCollectionRow(
    title: String,
    items: List<CollectionSummary>,
    onOpen: (CollectionSummary) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
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
                    onClick = { onOpen(col) }
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
                            text = col.subtitle.ifBlank { col.year },
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
