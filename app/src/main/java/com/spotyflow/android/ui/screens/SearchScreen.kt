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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.ArtistSummary
import com.spotyflow.android.data.CollectionSummary
import com.spotyflow.android.data.PlayerUiState
import com.spotyflow.android.data.QuickSearchEntity
import com.spotyflow.android.data.QuickSuggestionType
import com.spotyflow.android.data.SampleCatalog
import com.spotyflow.android.data.SearchFilter
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.formatDuration
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyGlassCard
import com.spotyflow.android.ui.glass.SpotyMiniEqBars
import com.spotyflow.android.ui.theme.LocalSpotyAppearance
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing

@Composable
fun SearchScreen(
    uiState: PlayerUiState,
    topHeaderClearance: Dp,
    onQueryChange: (String) -> Unit,
    onFillQuery: (String) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onSelectFilter: (SearchFilter) -> Unit,
    onLoadMore: () -> Unit,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenCollection: (CollectionSummary) -> Unit,
    onOpenTrackMenu: (TrackItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = LocalSpotyAppearance.current
    val ink = app.textPrimary
    val sub = app.textSecondary
    val accent = app.accent
    val dividerColor = if (app.isDark) Color.White.copy(alpha = 0.09f) else SpotyColors.HairlineDivider
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val filters = SearchFilter.entries
    val results = uiState.searchResults
    var showAllSongsInAllTab by remember(uiState.searchQuery, uiState.searchFilter) { mutableStateOf(false) }

    // Infinite scroll trigger when user scrolls near the bottom of submitted search results
    val shouldLoadMore by remember(uiState.hasSubmittedSearch, results.hasMore, uiState.isLoadingMoreSearch, uiState.isSearching) {
        derivedStateOf {
            if (!uiState.hasSubmittedSearch || !results.hasMore || uiState.isLoadingMoreSearch || uiState.isSearching) {
                false
            } else {
                val layoutInfo = listState.layoutInfo
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val total = layoutInfo.totalItemsCount
                total > 2 && lastVisible >= total - 2
            }
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
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
                tintAlpha = 0.74f,
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
                        tint = sub,
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
                                color = sub,
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
                                color = ink
                            ),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    onSubmitQuery(uiState.searchQuery)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (uiState.searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(ink.copy(alpha = 0.09f))
                                .clickable { onQueryChange("") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Hapus",
                                tint = ink,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // =====================================================================
        // MODE 1: EMPTY QUERY -> History & Mood Discovery Grid
        // =====================================================================
        if (uiState.searchQuery.isBlank()) {
            if (uiState.searchHistory.isNotEmpty()) {
                item(key = "search_history") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Riwayat Pencarian",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ink,
                            modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = SpotySpacing.ScreenHorizontal),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.searchHistory, key = { it }) { item ->
                                SpotyGlassCard(
                                    shape = RoundedCornerShape(999.dp),
                                    tintAlpha = 0.64f,
                                    elevation = 5.dp,
                                    onClick = {
                                        focusManager.clearFocus()
                                        onSubmitQuery(item)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.History,
                                            contentDescription = null,
                                            tint = sub,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = item,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ink
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Hapus riwayat",
                                            tint = sub,
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
                        color = ink
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
                                    tintAlpha = 0.64f,
                                    elevation = 8.dp,
                                    onClick = {
                                        focusManager.clearFocus()
                                        onSubmitQuery(card.id)
                                    }
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
                                                    color = ink,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = card.subtitle,
                                                    fontSize = 11.5.sp,
                                                    color = sub,
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
        }
        // =====================================================================
        // MODE 2: LIVE TYPING OVERLAY (Persis YouTube / YouTube Music!)
        // =====================================================================
        else if (!uiState.hasSubmittedSearch) {
            val typed = uiState.searchQuery.trim()
            val matchingHistory = uiState.searchHistory
                .filter { it.contains(typed, ignoreCase = true) }
                .take(3)
            val textSuggestions = uiState.searchSuggestions.textSuggestions
                .filter { kw -> matchingHistory.none { it.equals(kw, ignoreCase = true) } }
                .take(8)
            val quickEntities = uiState.searchSuggestions.quickEntities

            // Bagian 1: Saran Kata Kunci (Riwayat Jam + Kaca Pembesar Bold + Panah Diagonal ↖)
            item(key = "live_suggestions_keywords") {
                SpotyGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpotySpacing.ScreenHorizontal),
                    shape = RoundedCornerShape(24.dp),
                    tintAlpha = 0.72f,
                    elevation = 10.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Matching History Rows (Icons.Rounded.History + ↖)
                        matchingHistory.forEachIndexed { idx, hist ->
                            KeywordSuggestionRow(
                                text = hist,
                                typedPrefix = typed,
                                isHistory = true,
                                ink = ink,
                                sub = sub,
                                onSelect = {
                                    focusManager.clearFocus()
                                    onSubmitQuery(hist)
                                },
                                onFill = { onFillQuery(hist) }
                            )
                            if (idx < matchingHistory.lastIndex || textSuggestions.isNotEmpty()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                                    thickness = 0.5.dp,
                                    color = dividerColor
                                )
                            }
                        }

                        // Live Magnifying-Glass Suggestions (Icons.Rounded.Search + Bold suffix + ↖)
                        if (textSuggestions.isNotEmpty()) {
                            textSuggestions.forEachIndexed { idx, kw ->
                                KeywordSuggestionRow(
                                    text = kw,
                                    typedPrefix = typed,
                                    isHistory = false,
                                    ink = ink,
                                    sub = sub,
                                    onSelect = {
                                        focusManager.clearFocus()
                                        onSubmitQuery(kw)
                                    },
                                    onFill = { onFillQuery(kw) }
                                )
                                if (idx < textSuggestions.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 52.dp, end = 16.dp),
                                        thickness = 0.5.dp,
                                        color = dividerColor
                                    )
                                }
                            }
                        } else if (matchingHistory.isEmpty()) {
                            // Direct "Cari '<typed>'" action row while suggestions load
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        onSubmitQuery(typed)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Cari \"$typed\"",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ink,
                                    modifier = Modifier.weight(1f)
                                )
                                if (uiState.isSuggestionsLoading) {
                                    CircularProgressIndicator(
                                        color = accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bagian 2: Hasil Instan Musik, Album & Artis (Persis YouTube Music!)
            if (quickEntities.isNotEmpty() || uiState.isSuggestionsLoading) {
                item(key = "live_suggestions_entities") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpotySpacing.ScreenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hasil Instan",
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ink
                            )
                            if (uiState.isSuggestionsLoading) {
                                CircularProgressIndicator(
                                    color = accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        if (quickEntities.isNotEmpty()) {
                            SpotyGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                tintAlpha = 0.70f,
                                elevation = 9.dp
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    quickEntities.forEachIndexed { idx, entity ->
                                        QuickEntityRow(
                                            entity = entity,
                                            ink = ink,
                                            sub = sub,
                                            accent = accent,
                                            onClick = {
                                                focusManager.clearFocus()
                                                when (entity.type) {
                                                    QuickSuggestionType.SONG, QuickSuggestionType.VIDEO -> {
                                                        entity.trackItem?.let { t ->
                                                            onPlayTrack(t, listOf(t))
                                                        } ?: onSubmitQuery(entity.title)
                                                    }
                                                    QuickSuggestionType.ARTIST -> {
                                                        onOpenArtist(
                                                            ArtistSummary(
                                                                browseId = entity.artistBrowseId.orEmpty(),
                                                                name = entity.title,
                                                                subscribersText = entity.subtitle,
                                                                avatarUrl = entity.coverUrl
                                                            )
                                                        )
                                                    }
                                                    QuickSuggestionType.ALBUM, QuickSuggestionType.PLAYLIST -> {
                                                        val bId = entity.albumBrowseId ?: entity.id
                                                        onOpenCollection(
                                                            CollectionSummary(
                                                                browseId = bId,
                                                                title = entity.title,
                                                                subtitle = entity.subtitle,
                                                                coverUrl = entity.coverUrl,
                                                                isAlbum = entity.type == QuickSuggestionType.ALBUM
                                                            )
                                                        )
                                                    }
                                                }
                                            },
                                            onMoreClick = entity.trackItem?.let { t -> { onOpenTrackMenu(t) } }
                                        )
                                        if (idx < quickEntities.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 74.dp, end = 16.dp),
                                                thickness = 0.5.dp,
                                                color = dividerColor
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
        // =====================================================================
        // MODE 3: SUBMITTED COMPLETE SEARCH RESULTS (Hero + Songs + Albums + Artists + Videos + Playlists + Infinite Scroll)
        // =====================================================================
        else {
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
                                    .background(if (app.isDark) Color.White else SpotyColors.Slate900)
                                    .clickable { onSelectFilter(filter) }
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = filter.label,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (app.isDark) SpotyColors.Slate900 else Color.White
                                )
                            }
                        } else {
                            SpotyGlassCard(
                                shape = RoundedCornerShape(999.dp),
                                tintAlpha = 0.60f,
                                elevation = 5.dp,
                                onClick = { onSelectFilter(filter) }
                            ) {
                                Text(
                                    text = filter.label,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ink,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isSearching) {
                item(key = "search_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = accent)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Mencari \"${uiState.searchQuery}\"...",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = sub
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
                                color = ink
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Coba kata kunci lagu, artis, atau album lain.",
                                fontSize = 12.5.sp,
                                color = sub
                            )
                        }
                    }
                }
            } else {
                // 1. Hero Top Result (shown in ALL filter)
                if (uiState.searchFilter == SearchFilter.ALL) {
                    val heroTrack = results.topResult
                    val heroArtist = results.topArtist
                    val heroAlbum = results.topAlbum
                    if (heroTrack != null || heroArtist != null || heroAlbum != null) {
                        item(key = "sr_hero_top") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = SpotySpacing.ScreenHorizontal),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Hasil Teratas",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ink
                                )
                                HeroTopResultCard(
                                    topTrack = heroTrack,
                                    topArtist = heroArtist,
                                    topAlbum = heroAlbum,
                                    allTracks = results.tracks,
                                    ink = ink,
                                    sub = sub,
                                    accent = accent,
                                    onPlayTrack = onPlayTrack,
                                    onOpenArtist = onOpenArtist,
                                    onOpenCollection = onOpenCollection
                                )
                            }
                        }
                    }
                }

                // 2. Seksi Lagu / Musik (20-100+ tracks + Lihat semua)
                if (results.tracks.isNotEmpty() && uiState.searchFilter != SearchFilter.VIDEOS) {
                    val displayedTracks = if (uiState.searchFilter == SearchFilter.ALL && !showAllSongsInAllTab) {
                        results.tracks.take(20)
                    } else {
                        results.tracks
                    }
                    item(key = "sr_tracks") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SpotySpacing.ScreenHorizontal),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Musik (${results.tracks.size})",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ink
                                )
                                if (uiState.searchFilter == SearchFilter.ALL) {
                                    Text(
                                        text = if (showAllSongsInAllTab) "Filter Lagu" else "Lihat semua",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable {
                                                if (!showAllSongsInAllTab && results.tracks.size > 20) {
                                                    showAllSongsInAllTab = true
                                                } else {
                                                    onSelectFilter(SearchFilter.SONGS)
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            SpotyGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                tintAlpha = 0.68f
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    displayedTracks.forEachIndexed { idx, track ->
                                        val isActive = uiState.currentTrack?.id == track.id
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
                                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    color = if (isActive) accent else ink,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = buildString {
                                                        append(track.artist)
                                                        if (track.album.isNotBlank()) append(" • ${track.album}")
                                                        if (track.durationSec > 0) append(" • ${formatDuration(track.durationSec)}")
                                                    },
                                                    fontSize = 12.sp,
                                                    color = sub,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isActive) {
                                                SpotyMiniEqBars(isPlaying = uiState.isPlaying)
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            IconButton(
                                                onClick = { onOpenTrackMenu(track) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MoreVert,
                                                    contentDescription = "Opsi",
                                                    tint = sub,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        if (idx < displayedTracks.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 74.dp, end = 16.dp),
                                                thickness = 0.5.dp,
                                                color = dividerColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Seksi Album (Rak Album HD -> klik buka Full Album Screen)
                if (results.albums.isNotEmpty()) {
                    item(key = "sr_albums") {
                        SearchCollectionRow(
                            title = "Album",
                            items = results.albums,
                            ink = ink,
                            sub = sub,
                            accent = accent,
                            onItemClick = onOpenCollection
                        )
                    }
                }

                // 4. Seksi Artis (Avatar Bulat HD -> klik buka Full Artist Banner HD)
                if (results.artists.isNotEmpty()) {
                    item(key = "sr_artists") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Artis",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ink,
                                modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = SpotySpacing.ScreenHorizontal),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(results.artists, key = { it.browseId.ifBlank { it.name } }) { artist ->
                                    Column(
                                        modifier = Modifier
                                            .width(94.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onOpenArtist(artist) }
                                            .padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        SpotyArtwork(
                                            coverUrl = artist.avatarUrl,
                                            accentColor = accent,
                                            size = 82.dp,
                                            cornerRadius = 999.dp
                                        )
                                        Spacer(modifier = Modifier.height(7.dp))
                                        Text(
                                            text = artist.name,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = artist.subscribersText.ifBlank { "Artis" },
                                            fontSize = 11.sp,
                                            color = sub,
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

                // 5. Seksi Video (MV)
                val videoList = if (uiState.searchFilter == SearchFilter.VIDEOS) {
                    results.videos.ifEmpty { results.tracks }
                } else {
                    results.videos
                }
                if (videoList.isNotEmpty()) {
                    item(key = "sr_videos") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Video (MV)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ink,
                                modifier = Modifier.padding(horizontal = SpotySpacing.ScreenHorizontal)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = SpotySpacing.ScreenHorizontal),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(videoList, key = { "vid_${it.id}" }) { vid ->
                                    SpotyGlassCard(
                                        modifier = Modifier.width(210.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        tintAlpha = 0.66f,
                                        onClick = { onPlayTrack(vid, videoList) }
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(16f / 9f)
                                                    .clip(RoundedCornerShape(14.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                SpotyArtwork(
                                                    coverUrl = vid.coverUrl,
                                                    accentColor = vid.accentColor,
                                                    size = null,
                                                    cornerRadius = 14.dp,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.54f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.PlayArrow,
                                                        contentDescription = "Putar MV",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = vid.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ink,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = buildString {
                                                    append(vid.artist)
                                                    if (vid.durationSec > 0) append(" • ${formatDuration(vid.durationSec)}")
                                                },
                                                fontSize = 11.5.sp,
                                                color = sub,
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

                // 6. Seksi Playlist
                if (results.playlists.isNotEmpty()) {
                    item(key = "sr_playlists") {
                        SearchCollectionRow(
                            title = "Playlist",
                            items = results.playlists,
                            ink = ink,
                            sub = sub,
                            accent = accent,
                            onItemClick = onOpenCollection
                        )
                    }
                }

                // 7. Infinite Scroll Footer (40, 60, 100+ hasil!)
                if (results.hasMore || uiState.isLoadingMoreSearch) {
                    item(key = "sr_load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SpotySpacing.ScreenHorizontal, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isLoadingMoreSearch) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = accent,
                                        strokeWidth = 2.4.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Memuat lebih banyak hasil...",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = sub
                                    )
                                }
                            } else {
                                SpotyGlassCard(
                                    shape = RoundedCornerShape(999.dp),
                                    tintAlpha = 0.72f,
                                    elevation = 6.dp,
                                    onClick = onLoadMore
                                ) {
                                    Text(
                                        text = "Muat Lebih Banyak Hasil",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accent,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
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

@Composable
private fun KeywordSuggestionRow(
    text: String,
    typedPrefix: String,
    isHistory: Boolean,
    ink: Color,
    sub: Color,
    onSelect: () -> Unit,
    onFill: () -> Unit
) {
    val styledText = remember(text, typedPrefix, ink) {
        buildAnnotatedString {
            val idx = if (typedPrefix.isNotBlank()) text.indexOf(typedPrefix, ignoreCase = true) else -1
            if (idx == 0 && typedPrefix.length < text.length) {
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = ink)) {
                    append(text.substring(0, typedPrefix.length))
                }
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = ink)) {
                    append(text.substring(typedPrefix.length))
                }
            } else {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = ink)) {
                    append(text)
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isHistory) Icons.Rounded.History else Icons.Rounded.Search,
            contentDescription = null,
            tint = sub,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = styledText,
            fontSize = 14.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onFill,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.NorthWest,
                contentDescription = "Lengkapi teks",
                tint = sub,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun QuickEntityRow(
    entity: QuickSearchEntity,
    ink: Color,
    sub: Color,
    accent: Color,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)?
) {
    val isArtist = entity.type == QuickSuggestionType.ARTIST
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = if (onMoreClick != null) 6.dp else 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpotyArtwork(
            coverUrl = entity.coverUrl,
            accentColor = accent,
            size = 48.dp,
            cornerRadius = if (isArtist) 999.dp else 11.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entity.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entity.subtitle,
                fontSize = 12.sp,
                color = sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onMoreClick != null) {
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Opsi",
                    tint = sub,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HeroTopResultCard(
    topTrack: TrackItem?,
    topArtist: ArtistSummary?,
    topAlbum: CollectionSummary?,
    allTracks: List<TrackItem>,
    ink: Color,
    sub: Color,
    accent: Color,
    onPlayTrack: (TrackItem, List<TrackItem>) -> Unit,
    onOpenArtist: (ArtistSummary) -> Unit,
    onOpenCollection: (CollectionSummary) -> Unit
) {
    val showArtistHero = topArtist != null && topTrack == null
    val showAlbumHero = topAlbum != null && topTrack == null && topArtist == null

    SpotyGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        tintAlpha = 0.72f,
        elevation = 10.dp,
        onClick = {
            when {
                topTrack != null -> onPlayTrack(topTrack, allTracks.ifEmpty { listOf(topTrack) })
                topArtist != null -> onOpenArtist(topArtist)
                topAlbum != null -> onOpenCollection(topAlbum)
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpotyArtwork(
                coverUrl = topTrack?.coverUrl ?: topArtist?.avatarUrl ?: topAlbum?.coverUrl.orEmpty(),
                accentColor = topTrack?.accentColor ?: accent,
                size = 66.dp,
                cornerRadius = if (showArtistHero) 999.dp else 16.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (showAlbumHero) Icons.Rounded.Album else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = when {
                            topTrack != null -> "LAGU TERATAS"
                            showArtistHero -> "ARTIS TERATAS"
                            else -> "ALBUM TERATAS"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = accent
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = topTrack?.title ?: topArtist?.name ?: topAlbum?.title.orEmpty(),
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        topTrack != null -> buildString {
                            append(topTrack.artist)
                            if (topTrack.album.isNotBlank()) append(" • ${topTrack.album}")
                        }
                        topArtist != null -> topArtist.subscribersText.ifBlank { "Profil Artis HD" }
                        else -> topAlbum?.subtitle.orEmpty().ifBlank { "Album" }
                    },
                    fontSize = 12.5.sp,
                    color = sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Putar",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchCollectionRow(
    title: String,
    items: List<CollectionSummary>,
    ink: Color,
    sub: Color,
    accent: Color,
    onItemClick: (CollectionSummary) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ink,
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
                    tintAlpha = 0.66f,
                    onClick = { onItemClick(col) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        SpotyArtwork(
                            coverUrl = col.coverUrl,
                            accentColor = accent,
                            size = 126.dp,
                            cornerRadius = 16.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = col.title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = col.year.ifBlank { col.subtitle.ifBlank { if (col.isAlbum) "Album" else "Playlist" } },
                            fontSize = 11.5.sp,
                            color = sub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
