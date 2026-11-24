package com.spotyflow.android.data

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotyflow.android.data.local.CoverPaletteExtractor
import com.spotyflow.android.data.local.LocalLibraryStore
import com.spotyflow.android.data.remote.InnerTubeMusicClient
import com.spotyflow.android.data.remote.LyricsRepository
import com.spotyflow.android.data.remote.LyricsResult
import com.spotyflow.android.player.DynamicIslandStateBus
import com.spotyflow.android.player.SpotyFlowPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val activeTab: SpotyTab = SpotyTab.Home,
    val selectedMood: String = "Semua",
    val homeTracks: List<TrackItem> = emptyList(),
    val isHomeLoading: Boolean = true,
    val queue: List<TrackItem> = emptyList(),
    val queueIndex: Int = -1,
    val currentTrack: TrackItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val playerMode: PlayerMode = PlayerMode.Audio,
    val volume: Float = 0.8f,
    val ambientPalette: AmbientPalette = AmbientPalette(),
    val lyricsResult: LyricsResult = LyricsResult(),
    val isLyricsLoading: Boolean = false,
    val isNowPlayingOpen: Boolean = false,
    val isLyricsVisible: Boolean = false,
    val isQueueSheetOpen: Boolean = false,
    val isManualLyricsOpen: Boolean = false,
    val searchQuery: String = "",
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val searchResults: SearchResults = SearchResults(),
    val isSearching: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val userProfile: UserProfile = UserProfile(),
    val likedTracks: List<TrackItem> = emptyList(),
    val followedArtists: List<ArtistSummary> = emptyList(),
    val savedCollections: List<CollectionSummary> = emptyList(),
    val customPlaylists: List<CustomPlaylist> = emptyList(),
    val recentTracks: List<TrackItem> = emptyList(),
    val dailyStreak: DailyStreak = DailyStreak(),
    val waterDropsEnabled: Boolean = true,
    val detailStack: List<DetailDestination> = emptyList(),
    val loadedArtistProfile: ArtistProfileData? = null,
    val isArtistLoading: Boolean = false,
    val loadedCollection: CollectionDetailData? = null,
    val isCollectionLoading: Boolean = false,
    val menuTrack: TrackItem? = null,
    val activePreviewSheet: PreviewSheetData? = null,
    val toastMessage: String? = null
) {
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val isCurrentTrackLiked: Boolean
        get() {
            val cur = currentTrack ?: return false
            return likedTracks.any { (cur.videoId.isNotEmpty() && it.videoId == cur.videoId) || it.id == cur.id }
        }

    val activeLyricIndex: Int
        get() {
            val lines = lyricsResult.activeList
            if (lines.isEmpty() || !lyricsResult.isSynced) return 0
            var idx = 0
            for (i in lines.indices) {
                if (positionMs >= lines[i].timeMs) idx = i
            }
            return idx
        }

    val currentDetail: DetailDestination?
        get() = detailStack.lastOrNull()
}

class SpotyViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val store = LocalLibraryStore(appContext)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var lyricsJob: Job? = null
    private var paletteJob: Job? = null
    private var detailJob: Job? = null
    private var lastLoadedTrackId: String = ""

    init {
        observePlaybackService()
        loadHomeMood("Semua")
    }

    private fun loadInitialState(): PlayerUiState {
        val recent = store.getRecentTracks()
        val liked = store.getLikedTracks()
        val currentVol = readSystemVolumeFraction()
        return PlayerUiState(
            currentTrack = recent.firstOrNull() ?: liked.firstOrNull(),
            volume = currentVol,
            searchHistory = store.getSearchHistory(),
            userProfile = store.getUserProfile(),
            likedTracks = liked,
            followedArtists = store.getFollowedArtists(),
            savedCollections = store.getSavedCollections(),
            customPlaylists = store.getCustomPlaylists(),
            recentTracks = recent,
            dailyStreak = store.getDailyStreak(),
            waterDropsEnabled = store.isWaterDropsEnabled()
        )
    }

    private fun readSystemVolumeFraction(): Float {
        return try {
            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            val cur = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 12
            if (max > 0) (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0.8f
        } catch (_: Throwable) {
            0.8f
        }
    }

    private fun observePlaybackService() {
        viewModelScope.launch {
            SpotyFlowPlaybackService.playbackState.collect { svc ->
                val activeTrack = svc.currentTrack ?: _uiState.value.currentTrack
                _uiState.update { st ->
                    st.copy(
                        queue = svc.queue.ifEmpty { st.queue },
                        queueIndex = if (svc.queue.isNotEmpty()) svc.queueIndex else st.queueIndex,
                        currentTrack = activeTrack,
                        isPlaying = svc.isPlaying,
                        isBuffering = svc.isBuffering,
                        positionMs = svc.positionMs,
                        durationMs = svc.durationMs.takeIf { it > 0L } ?: ((activeTrack?.durationSec ?: 0) * 1000L),
                        isShuffle = svc.isShuffle,
                        repeatMode = svc.repeatMode,
                        playerMode = svc.playerMode
                    )
                }

                // Sync active synced lyric line to DynamicIslandStateBus
                val curUi = _uiState.value
                val lines = curUi.lyricsResult.activeList
                val activeLine = if (curUi.lyricsResult.isSynced && lines.isNotEmpty()) {
                    lines.getOrNull(curUi.activeLyricIndex)?.text.orEmpty()
                } else {
                    ""
                }
                DynamicIslandStateBus.update { bus ->
                    bus.copy(
                        currentLyricLine = activeLine,
                        accentColor = curUi.ambientPalette.accent,
                        isLiked = curUi.isCurrentTrackLiked
                    )
                }

                // Trigger palette + lyrics fetch whenever track changes
                if (activeTrack != null && activeTrack.id != lastLoadedTrackId) {
                    lastLoadedTrackId = activeTrack.id
                    onActiveTrackChanged(activeTrack)
                }
            }
        }
    }

    private fun onActiveTrackChanged(track: TrackItem) {
        // Record history & daily streak
        val (recent, streak) = store.recordTrackPlay(track, 1)
        _uiState.update { it.copy(recentTracks = recent, dailyStreak = streak) }

        // Extract adaptive pastel cover palette in background
        paletteJob?.cancel()
        paletteJob = viewModelScope.launch {
            val pal = CoverPaletteExtractor.extract(appContext, track.coverUrl, track.accentColor)
            _uiState.update { it.copy(ambientPalette = pal) }
            DynamicIslandStateBus.update { it.copy(accentColor = pal.accent) }
        }

        // Fetch real-time synced lyrics in background
        lyricsJob?.cancel()
        _uiState.update { it.copy(isLyricsLoading = true, lyricsResult = LyricsResult()) }
        lyricsJob = viewModelScope.launch {
            val res = LyricsRepository.fetchLyrics(track)
            if (_uiState.value.currentTrack?.id == track.id) {
                _uiState.update { it.copy(isLyricsLoading = false, lyricsResult = res) }
            }
        }
    }

    // =========================================================================
    // NAVIGATION & HOME MOOD FEEDS
    // =========================================================================
    fun selectTab(tab: SpotyTab) {
        _uiState.update { it.copy(activeTab = tab, detailStack = emptyList()) }
    }

    fun selectMood(mood: String) {
        _uiState.update { it.copy(selectedMood = mood) }
        loadHomeMood(mood)
    }

    fun loadHomeMood(mood: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isHomeLoading = true) }
            val fetched = InnerTubeMusicClient.fetchMoodTracks(mood)
            _uiState.update { st ->
                val curTrack = st.currentTrack ?: fetched.firstOrNull()
                st.copy(
                    homeTracks = fetched.ifEmpty { st.homeTracks },
                    currentTrack = curTrack,
                    isHomeLoading = false
                )
            }
        }
    }

    // =========================================================================
    // REAL MEDIA3 PLAYBACK CONTROLS (OPTIMISTIC 0ms UI)
    // =========================================================================
    fun playTrack(track: TrackItem, queue: List<TrackItem> = emptyList()) {
        val list = if (queue.isNotEmpty()) queue else {
            val pool = _uiState.value.homeTracks
            if (pool.any { it.id == track.id }) pool else listOf(track) + pool.take(15)
        }
        val startIdx = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        // Optimistic 0ms UI update
        _uiState.update {
            it.copy(
                currentTrack = track,
                queue = list,
                queueIndex = startIdx,
                isPlaying = true,
                isBuffering = true,
                positionMs = 0L,
                durationMs = track.durationSec * 1000L
            )
        }
        SpotyFlowPlaybackService.dispatch(appContext) { svc ->
            svc.playQueue(list, startIdx)
        }
    }

    fun playQueue(tracks: List<TrackItem>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val finalQueue = if (shuffle) tracks.shuffled() else tracks
        playTrack(finalQueue[startIndex.coerceIn(0, finalQueue.lastIndex)], finalQueue)
    }

    fun togglePlayPause() {
        val st = _uiState.value
        val cur = st.currentTrack
        if (SpotyFlowPlaybackService.activeInstance == null && cur != null) {
            playTrack(cur, st.queue.ifEmpty { st.homeTracks })
            return
        }
        if (cur == null && st.homeTracks.isNotEmpty()) {
            playTrack(st.homeTracks.first(), st.homeTracks)
            return
        }
        SpotyFlowPlaybackService.dispatch(appContext) { it.togglePlayPause() }
    }

    fun nextTrack() {
        SpotyFlowPlaybackService.dispatch(appContext) { it.nextTrack(auto = false) }
    }

    fun previousTrack() {
        SpotyFlowPlaybackService.dispatch(appContext) { it.previousTrack() }
    }

    fun seekToFraction(fraction: Float) {
        val dur = _uiState.value.durationMs
        if (dur <= 0L) return
        val targetMs = (fraction.coerceIn(0f, 1f) * dur).toLong()
        _uiState.update { it.copy(positionMs = targetMs) }
        SpotyFlowPlaybackService.dispatch(appContext) { it.seekTo(targetMs) }
    }

    fun seekToMs(ms: Long) {
        _uiState.update { it.copy(positionMs = ms.coerceAtLeast(0L)) }
        SpotyFlowPlaybackService.dispatch(appContext) { it.seekTo(ms) }
    }

    fun toggleShuffle() {
        SpotyFlowPlaybackService.dispatch(appContext) { it.toggleShuffle() }
    }

    fun cycleRepeat() {
        SpotyFlowPlaybackService.dispatch(appContext) { it.cycleRepeat() }
    }

    fun setPlayerMode(mode: PlayerMode) {
        _uiState.update { it.copy(playerMode = mode) }
        SpotyFlowPlaybackService.dispatch(appContext) { it.setPlayerMode(mode) }
    }

    fun setVolume(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        _uiState.update { it.copy(volume = clamped) }
        try {
            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, (clamped * max).toInt(), 0)
        } catch (_: Throwable) {
            // Ignore audio manager restrictions
        }
    }

    fun addTrackNext(track: TrackItem) {
        SpotyFlowPlaybackService.dispatch(appContext) { it.addNext(track) }
        showToast("Akan diputar berikutnya")
    }

    fun addTrackToQueue(track: TrackItem) {
        SpotyFlowPlaybackService.dispatch(appContext) { it.addToQueueEnd(track) }
        showToast("Ditambahkan ke antrean")
    }

    fun startRadioForTrack(track: TrackItem) {
        showToast("Menyiapkan Radio ${track.artist}...")
        viewModelScope.launch {
            val related = InnerTubeMusicClient.fetchRelatedTracks(track)
            val radioQueue = listOf(track) + related
            playTrack(track, radioQueue)
        }
    }

    // =========================================================================
    // LIVE SEARCH (MULTI-TYPE: Songs, Videos, Artists, Albums, Playlists)
    // =========================================================================
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = SearchResults(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(360L)
            executeSearch(query, _uiState.value.searchFilter, saveHistory = false)
        }
    }

    fun submitSearchQuery(query: String) {
        val clean = query.trim()
        _uiState.update { it.copy(searchQuery = clean) }
        if (clean.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch(clean, _uiState.value.searchFilter, saveHistory = true)
        }
    }

    fun selectSearchFilter(filter: SearchFilter) {
        _uiState.update { it.copy(searchFilter = filter) }
        val q = _uiState.value.searchQuery.trim()
        if (q.isNotEmpty()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                executeSearch(q, filter, saveHistory = false)
            }
        }
    }

    private suspend fun executeSearch(query: String, filter: SearchFilter, saveHistory: Boolean) {
        _uiState.update { it.copy(isSearching = true) }
        if (saveHistory) {
            val nextHist = store.addSearchHistory(query)
            _uiState.update { it.copy(searchHistory = nextHist) }
        }
        val res = InnerTubeMusicClient.searchAll(query, filter)
        if (_uiState.value.searchQuery.trim().equals(query.trim(), ignoreCase = true)) {
            _uiState.update { it.copy(searchResults = res, isSearching = false) }
        }
    }

    fun removeSearchHistory(item: String) {
        _uiState.update { it.copy(searchHistory = store.removeSearchHistory(item)) }
    }

    // =========================================================================
    // FULL PROFILES: ARTIST PROFILE, ALBUM/PLAYLIST DETAIL & USER PROFILE
    // =========================================================================
    fun openArtistProfile(browseId: String, artistName: String) {
        val dest = DetailDestination.Artist(browseId, artistName)
        _uiState.update {
            it.copy(
                isNowPlayingOpen = false,
                menuTrack = null,
                detailStack = it.detailStack + dest,
                loadedArtistProfile = null,
                isArtistLoading = true
            )
        }
        loadDetailDestination(dest)
    }

    fun openCollectionDetail(browseId: String, fallbackTitle: String = "") {
        val dest = DetailDestination.Collection(browseId = browseId, fallbackTitle = fallbackTitle)
        _uiState.update {
            it.copy(
                isNowPlayingOpen = false,
                menuTrack = null,
                detailStack = it.detailStack + dest,
                loadedCollection = null,
                isCollectionLoading = true
            )
        }
        loadDetailDestination(dest)
    }

    fun openLocalCollection(localKey: String, title: String) {
        val dest = DetailDestination.Collection(browseId = localKey, fallbackTitle = title, localKey = localKey)
        _uiState.update {
            it.copy(
                isNowPlayingOpen = false,
                detailStack = it.detailStack + dest,
                loadedCollection = null,
                isCollectionLoading = true
            )
        }
        loadDetailDestination(dest)
    }

    fun openCustomPlaylist(playlist: CustomPlaylist) {
        val dest = DetailDestination.Collection(
            browseId = playlist.id,
            fallbackTitle = playlist.title,
            isCustom = true
        )
        _uiState.update {
            it.copy(
                detailStack = it.detailStack + dest,
                loadedCollection = CollectionDetailData(
                    browseId = playlist.id,
                    title = playlist.title,
                    subtitle = "Playlist Kamu",
                    artistName = it.userProfile.displayName,
                    coverUrl = playlist.tracks.firstOrNull()?.coverUrl.orEmpty(),
                    totalDurationText = formatTotalDuration(playlist.tracks),
                    isAlbum = false,
                    isCustomPlaylist = true,
                    tracks = playlist.tracks
                ),
                isCollectionLoading = false
            )
        }
    }

    fun popDetail(): Boolean {
        val stack = _uiState.value.detailStack
        if (stack.isEmpty()) return false
        val nextStack = stack.dropLast(1)
        _uiState.update { it.copy(detailStack = nextStack) }
        nextStack.lastOrNull()?.let { loadDetailDestination(it) }
        return true
    }

    private fun loadDetailDestination(dest: DetailDestination) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            when (dest) {
                is DetailDestination.Artist -> {
                    _uiState.update { it.copy(isArtistLoading = true) }
                    val profile = InnerTubeMusicClient.fetchArtistProfile(dest.browseId, dest.artistName)
                    _uiState.update { it.copy(loadedArtistProfile = profile, isArtistLoading = false) }
                }
                is DetailDestination.Collection -> {
                    if (dest.localKey != null) {
                        val tracks = when (dest.localKey) {
                            "liked" -> _uiState.value.likedTracks
                            "top50" -> store.getTop50Tracks()
                            "recent", "cached" -> _uiState.value.recentTracks
                            else -> emptyList()
                        }
                        _uiState.update {
                            it.copy(
                                loadedCollection = CollectionDetailData(
                                    browseId = dest.localKey,
                                    title = dest.fallbackTitle,
                                    subtitle = "Koleksi Pustaka",
                                    artistName = it.userProfile.displayName,
                                    coverUrl = tracks.firstOrNull()?.coverUrl.orEmpty(),
                                    totalDurationText = formatTotalDuration(tracks),
                                    isAlbum = false,
                                    tracks = tracks
                                ),
                                isCollectionLoading = false
                            )
                        }
                    } else if (!dest.isCustom) {
                        _uiState.update { it.copy(isCollectionLoading = true) }
                        val detail = InnerTubeMusicClient.fetchCollectionDetail(dest.browseId, dest.fallbackTitle)
                        _uiState.update { it.copy(loadedCollection = detail, isCollectionLoading = false) }
                    }
                }
            }
        }
    }

    // =========================================================================
    // LIBRARY MUTATIONS, USER PROFILE & LYRICS MANUAL SEARCH
    // =========================================================================
    fun toggleLike(track: TrackItem? = _uiState.value.currentTrack) {
        val target = track ?: return
        val updated = store.toggleLike(target)
        _uiState.update { it.copy(likedTracks = updated) }
        val liked = updated.any { it.id == target.id }
        showToast(if (liked) "Ditambahkan ke Disukai" else "Dihapus dari Disukai")
    }

    fun toggleFollowArtist(artist: ArtistSummary) {
        val updated = store.toggleFollowArtist(artist)
        _uiState.update { it.copy(followedArtists = updated) }
        val nowFollowed = updated.any { it.name.equals(artist.name, ignoreCase = true) }
        showToast(if (nowFollowed) "Mengikuti ${artist.name}" else "Berhenti mengikuti ${artist.name}")
    }

    fun isArtistFollowed(browseId: String, name: String): Boolean {
        return _uiState.value.followedArtists.any {
            (browseId.isNotEmpty() && it.browseId == browseId) || it.name.equals(name, ignoreCase = true)
        }
    }

    fun toggleSaveCollection(col: CollectionSummary) {
        val updated = store.toggleSaveCollection(col)
        _uiState.update { it.copy(savedCollections = updated) }
        val saved = updated.any { it.browseId == col.browseId }
        showToast(if (saved) "Disimpan ke Pustaka" else "Dihapus dari Pustaka")
    }

    fun isCollectionSaved(browseId: String): Boolean {
        return _uiState.value.savedCollections.any { it.browseId == browseId }
    }

    fun createPlaylist(title: String, initialTrack: TrackItem? = null) {
        if (title.isBlank()) return
        val updated = store.createPlaylist(title, listOfNotNull(initialTrack))
        _uiState.update { it.copy(customPlaylists = updated) }
        showToast("Playlist \"$title\" dibuat")
    }

    fun addTrackToPlaylist(playlistId: String, track: TrackItem) {
        val updated = store.addTrackToPlaylist(playlistId, track)
        _uiState.update { it.copy(customPlaylists = updated) }
        showToast("Ditambahkan ke playlist")
    }

    fun updateUserProfile(displayName: String, handle: String, bio: String) {
        val updated = _uiState.value.userProfile.copy(
            displayName = displayName.trim().ifBlank { "Pendengar SpotyFlow" },
            handle = handle.trim().ifBlank { "@spotyflow" },
            bio = bio.trim()
        )
        store.saveUserProfile(updated)
        _uiState.update { it.copy(userProfile = updated) }
        showToast("Profil disimpan")
    }

    fun updateUserAvatar(uri: Uri) {
        viewModelScope.launch {
            val savedPath = store.saveAvatarFromUri(uri)
            if (savedPath != null) {
                _uiState.update { it.copy(userProfile = it.userProfile.copy(avatarPath = savedPath)) }
                showToast("Foto profil diperbarui")
            }
        }
    }

    fun searchLyricsManual(title: String, artist: String) {
        _uiState.update { it.copy(isManualLyricsOpen = false, isLyricsLoading = true) }
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            val res = LyricsRepository.searchManual(title, artist)
            _uiState.update { it.copy(isLyricsLoading = false, lyricsResult = res) }
            if (res.activeList.isEmpty()) showToast("Lirik tidak ditemukan")
        }
    }

    // =========================================================================
    // UI SHEETS & OVERLAYS
    // =========================================================================
    fun openNowPlaying(showLyrics: Boolean = false) {
        _uiState.update { it.copy(isNowPlayingOpen = true, isLyricsVisible = showLyrics) }
    }

    fun closeNowPlaying() {
        _uiState.update { it.copy(isNowPlayingOpen = false, isLyricsVisible = false, isQueueSheetOpen = false) }
    }

    fun toggleLyricsView() {
        _uiState.update { it.copy(isLyricsVisible = !it.isLyricsVisible) }
    }

    fun setQueueSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isQueueSheetOpen = open) }
    }

    fun setManualLyricsOpen(open: Boolean) {
        _uiState.update { it.copy(isManualLyricsOpen = open) }
    }

    fun openTrackMenu(track: TrackItem) {
        _uiState.update { it.copy(menuTrack = track) }
    }

    fun closeTrackMenu() {
        _uiState.update { it.copy(menuTrack = null) }
    }

    fun openPreviewSheet(data: PreviewSheetData) {
        _uiState.update { it.copy(activePreviewSheet = data) }
    }

    fun closePreviewSheet() {
        _uiState.update { it.copy(activePreviewSheet = null) }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
        viewModelScope.launch {
            delay(2200L)
            if (_uiState.value.toastMessage == msg) {
                _uiState.update { it.copy(toastMessage = null) }
            }
        }
    }
}
