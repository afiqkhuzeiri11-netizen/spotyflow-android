package com.spotyflow.android.data

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotyflow.android.data.local.AppSettings
import com.spotyflow.android.data.local.AppSettingsStore
import com.spotyflow.android.data.local.CoverPaletteExtractor
import com.spotyflow.android.data.local.DeviceAudioScanner
import com.spotyflow.android.data.local.LocalLibraryStore
import com.spotyflow.android.data.local.OfflineDownloadManager
import com.spotyflow.android.data.remote.InnerTubeMusicClient
import com.spotyflow.android.data.remote.LyricsEnhancer
import com.spotyflow.android.data.remote.LyricsRepository
import com.spotyflow.android.data.remote.LyricsResult
import com.spotyflow.android.data.remote.PlaylistImporter
import com.spotyflow.android.player.DynamicIslandCommand
import com.spotyflow.android.player.DynamicIslandStateBus
import com.spotyflow.android.player.SpotyFlowPlaybackService
import com.spotyflow.android.widget.SpotyFlowBaseWidgetProvider
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
    val sleepTimerRemainingSec: Int = 0,
    val sleepStopAtEndOfTrack: Boolean = false,
    val volume: Float = 0.8f,
    val ambientPalette: AmbientPalette = AmbientPalette(),
    val lyricsResult: LyricsResult = LyricsResult(),
    val translatedLyrics: List<String> = emptyList(),
    val romanizedLyrics: List<String> = emptyList(),
    val isLyricsLoading: Boolean = false,
    val isNowPlayingOpen: Boolean = false,
    val isLyricsVisible: Boolean = false,
    val isQueueSheetOpen: Boolean = false,
    val isManualLyricsOpen: Boolean = false,
    val searchQuery: String = "",
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val hasSubmittedSearch: Boolean = false,
    val searchSuggestions: SearchSuggestionsResult = SearchSuggestionsResult(),
    val isSuggestionsLoading: Boolean = false,
    val searchResults: SearchResults = SearchResults(),
    val isSearching: Boolean = false,
    val isLoadingMoreSearch: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val userProfile: UserProfile = UserProfile(),
    val likedTracks: List<TrackItem> = emptyList(),
    val downloadedTracks: List<TrackItem> = emptyList(),
    val downloadProgress: Map<String, Int> = emptyMap(),
    val deviceTracks: List<TrackItem> = emptyList(),
    val uploadedTracks: List<TrackItem> = emptyList(),
    val followedArtists: List<ArtistSummary> = emptyList(),
    val savedCollections: List<CollectionSummary> = emptyList(),
    val customPlaylists: List<CustomPlaylist> = emptyList(),
    val recentTracks: List<TrackItem> = emptyList(),
    val dailyStreak: DailyStreak = DailyStreak(),
    val appSettings: AppSettings = AppSettings(),
    val waterDropsEnabled: Boolean = true,
    val detailStack: List<DetailDestination> = emptyList(),
    val loadedArtistProfile: ArtistProfileData? = null,
    val isArtistLoading: Boolean = false,
    val loadedCollection: CollectionDetailData? = null,
    val isCollectionLoading: Boolean = false,
    val menuTrack: TrackItem? = null,
    val creditsTrack: TrackItem? = null,
    val loadedTrackCredits: TrackCreditsData? = null,
    val isCreditsLoading: Boolean = false,
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
            val adjustedPos = positionMs + appSettings.lyricsOffsetMs
            var idx = 0
            for (i in lines.indices) {
                if (adjustedPos >= lines[i].timeMs) idx = i
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
    private var suggestionsJob: Job? = null
    private var loadMoreJob: Job? = null
    private var creditsJob: Job? = null
    private var lyricsJob: Job? = null
    private var translateJob: Job? = null
    private var paletteJob: Job? = null
    private var detailJob: Job? = null
    private var lastLoadedTrackId: String = ""

    init {
        AppSettingsStore.load(appContext)
        OfflineDownloadManager.init(appContext)
        observeAppSettings()
        observeOfflineDownloads()
        observePlaybackService()
        observeIslandLikeCommand()
        loadHomeMood("Semua")
        scanDeviceMusicAsync()
    }

    private fun loadInitialState(): PlayerUiState {
        val recent = store.getRecentTracks()
        val liked = store.getLikedTracks()
        val settings = AppSettingsStore.load(appContext)
        return PlayerUiState(
            currentTrack = recent.firstOrNull() ?: liked.firstOrNull(),
            volume = readSystemVolumeFraction(),
            searchHistory = store.getSearchHistory(),
            userProfile = store.getUserProfile(),
            likedTracks = liked,
            uploadedTracks = store.getUploadedTracks(),
            followedArtists = store.getFollowedArtists(),
            savedCollections = store.getSavedCollections(),
            customPlaylists = store.getCustomPlaylists(),
            recentTracks = recent,
            dailyStreak = store.getDailyStreak(),
            appSettings = settings,
            waterDropsEnabled = settings.waterDropsEnabled
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

    private fun observeAppSettings() {
        viewModelScope.launch {
            AppSettingsStore.settings.collect { s ->
                val prevTranslate = _uiState.value.appSettings.lyricsAutoTranslate
                val prevRomanize = _uiState.value.appSettings.lyricsAutoRomanize
                _uiState.update { it.copy(appSettings = s, waterDropsEnabled = s.waterDropsEnabled) }
                if (s.lyricsAutoTranslate != prevTranslate || s.lyricsAutoRomanize != prevRomanize) {
                    refreshLyricEnhancements(_uiState.value.lyricsResult)
                }
            }
        }
    }

    private fun observeOfflineDownloads() {
        viewModelScope.launch {
            OfflineDownloadManager.downloadedTracks.collect { list ->
                _uiState.update { it.copy(downloadedTracks = list) }
            }
        }
        viewModelScope.launch {
            OfflineDownloadManager.progressMap.collect { prog ->
                _uiState.update { it.copy(downloadProgress = prog) }
            }
        }
    }

    private fun observeIslandLikeCommand() {
        viewModelScope.launch {
            DynamicIslandStateBus.commands.collect { cmd ->
                if (cmd == DynamicIslandCommand.ToggleLike) {
                    _uiState.update { it.copy(likedTracks = store.getLikedTracks()) }
                }
            }
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
                        playerMode = svc.playerMode,
                        sleepTimerRemainingSec = svc.sleepTimerRemainingSec,
                        sleepStopAtEndOfTrack = svc.sleepStopAtEndOfTrack
                    )
                }

                val curUi = _uiState.value
                val lines = curUi.lyricsResult.activeList
                val activeLine = if (curUi.appSettings.lyricsSyncIsland && curUi.lyricsResult.isSynced && lines.isNotEmpty()) {
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

                if (activeTrack != null && activeTrack.id != lastLoadedTrackId) {
                    lastLoadedTrackId = activeTrack.id
                    onActiveTrackChanged(activeTrack)
                }
            }
        }
    }

    private fun onActiveTrackChanged(track: TrackItem) {
        val s = _uiState.value.appSettings
        if (!s.pauseListenHistory && !s.incognitoSession) {
            val (recent, streak) = store.recordTrackPlay(track, 1)
            _uiState.update { it.copy(recentTracks = recent, dailyStreak = streak) }
        }

        paletteJob?.cancel()
        paletteJob = viewModelScope.launch {
            val pal = CoverPaletteExtractor.extract(appContext, track.coverUrl, track.accentColor)
            _uiState.update { it.copy(ambientPalette = pal) }
            DynamicIslandStateBus.update { it.copy(accentColor = pal.accent) }
            SpotyFlowBaseWidgetProvider.updateAllWidgetsAsync(appContext)
        }

        lyricsJob?.cancel()
        _uiState.update {
            it.copy(
                isLyricsLoading = true,
                lyricsResult = LyricsResult(),
                translatedLyrics = emptyList(),
                romanizedLyrics = emptyList()
            )
        }
        lyricsJob = viewModelScope.launch {
            val res = LyricsRepository.fetchLyrics(track)
            if (_uiState.value.currentTrack?.id == track.id) {
                _uiState.update { it.copy(isLyricsLoading = false, lyricsResult = res) }
                refreshLyricEnhancements(res)
            }
        }
    }

    private fun refreshLyricEnhancements(res: LyricsResult) {
        translateJob?.cancel()
        val lines = res.activeList
        val s = _uiState.value.appSettings
        if (lines.isEmpty()) {
            _uiState.update { it.copy(translatedLyrics = emptyList(), romanizedLyrics = emptyList()) }
            return
        }
        translateJob = viewModelScope.launch {
            val rom = if (s.lyricsAutoRomanize && LyricsEnhancer.needsRomanization(lines)) {
                lines.map { LyricsEnhancer.romanizeText(it.text) }
            } else {
                emptyList()
            }
            _uiState.update { it.copy(romanizedLyrics = rom) }

            if (s.lyricsAutoTranslate) {
                val tr = LyricsEnhancer.translateLines(lines, s.lyricsTranslateTarget)
                _uiState.update { it.copy(translatedLyrics = tr) }
            } else {
                _uiState.update { it.copy(translatedLyrics = emptyList()) }
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

    fun scanDeviceMusicAsync() {
        viewModelScope.launch {
            val found = DeviceAudioScanner.scanDeviceAudio(appContext)
            _uiState.update { it.copy(deviceTracks = found) }
        }
    }

    // =========================================================================
    // REAL MEDIA3 PLAYBACK CONTROLS
    // =========================================================================
    fun playTrack(track: TrackItem, queue: List<TrackItem> = emptyList()) {
        // Resolve rescued metadata-only track to full YouTube videoId (0% 30-second previews!)
        if (track.videoId.length != 11 && track.localUri.isBlank()) {
            _uiState.update {
                it.copy(
                    currentTrack = track,
                    isPlaying = true,
                    isBuffering = true,
                    positionMs = 0L,
                    durationMs = track.durationSec * 1000L
                )
            }
            viewModelScope.launch {
                val resolved = InnerTubeMusicClient.resolveFullTrack(track)
                if (resolved.videoId.length != 11) {
                    _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
                    showToast("Audio penuh tidak ditemukan untuk \"${track.title}\"")
                    return@launch
                }
                val updatedQueue = if (queue.isNotEmpty()) {
                    queue.map { if (it.id == track.id) resolved else it }
                } else {
                    listOf(resolved) + _uiState.value.homeTracks.filter { it.id != resolved.id }.take(15)
                }
                playResolvedQueue(resolved, updatedQueue)
            }
            return
        }
        val list = if (queue.isNotEmpty()) queue else {
            val pool = _uiState.value.homeTracks
            if (pool.any { it.id == track.id }) pool else listOf(track) + pool.take(15)
        }
        playResolvedQueue(track, list)
    }

    private fun playResolvedQueue(track: TrackItem, list: List<TrackItem>) {
        val startIdx = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
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

    fun startSleepTimer(minutes: Int, stopAtTrackEnd: Boolean = false) {
        SpotyFlowPlaybackService.dispatch(appContext) { it.startSleepTimer(minutes, stopAtTrackEnd) }
        val msg = when {
            stopAtTrackEnd -> "Musik akan berhenti di akhir lagu ini"
            minutes > 0 -> "Timer tidur aktif ($minutes menit)"
            else -> "Timer tidur dimatikan"
        }
        showToast(msg)
    }

    fun setVolume(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        _uiState.update { it.copy(volume = clamped) }
        try {
            val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, (clamped * max).toInt(), 0)
        } catch (_: Throwable) {}
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
    // OFFLINE DOWNLOADS, UPLOAD LOCAL AUDIO & SPOTIFY/YTM PLAYLIST IMPORT
    // =========================================================================
    fun downloadTrackOffline(track: TrackItem) {
        if (track.videoId.length != 11) {
            showToast("Lagu ini sudah tersimpan lokal")
            return
        }
        if (OfflineDownloadManager.isDownloaded(appContext, track.videoId)) {
            showToast("Sudah diunduh untuk offline")
            return
        }
        showToast("Mengunduh ${track.title}...")
        OfflineDownloadManager.downloadTrack(appContext, track) { ok ->
            showToast(if (ok) "Berhasil diunduh ke Offline" else "Gagal mengunduh lagu")
        }
    }

    fun downloadTracksBatch(tracks: List<TrackItem>) {
        if (tracks.isEmpty()) return
        showToast("Mengunduh ${tracks.size} lagu untuk offline...")
        OfflineDownloadManager.downloadBatch(appContext, tracks) { count ->
            showToast("$count lagu berhasil diunduh")
        }
    }

    fun deleteOfflineTrack(videoId: String) {
        OfflineDownloadManager.deleteTrack(appContext, videoId)
        showToast("Dihapus dari unduhan offline")
    }

    fun importPlaylistFromUrl(url: String) {
        showToast("Mengimpor playlist...")
        viewModelScope.launch {
            val imported = PlaylistImporter.importFromUrl(url)
            if (imported != null && imported.tracks.isNotEmpty()) {
                val updated = store.createPlaylist("${imported.title} (${imported.source})", imported.tracks)
                _uiState.update { it.copy(customPlaylists = updated) }
                showToast("Berhasil mengimpor ${imported.tracks.size} lagu dari ${imported.source}")
            } else {
                showToast("Tautan playlist tidak dapat dibaca")
            }
        }
    }

    fun addUploadedAudioUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = mutableListOf<TrackItem>()
            uris.forEach { uri ->
                try {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {}
                val title = queryDisplayName(uri).substringBeforeLast(".").ifBlank { "Audio Unggahan" }
                added.add(
                    TrackItem(
                        id = "up_${uri.toString().hashCode()}",
                        videoId = "",
                        title = title,
                        artist = "Koleksi Diunggah",
                        localUri = uri.toString(),
                        accentColor = deterministicAccent(title)
                    )
                )
            }
            val updated = store.addUploadedTracks(added)
            _uiState.update { it.copy(uploadedTracks = updated) }
            showToast("${added.size} file audio ditambahkan ke Diunggah")
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        return try {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0).orEmpty() else ""
            }.orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    // =========================================================================
    // LIVE TYPING AUTOCOMPLETE (180ms) + SUPER-COMPLETE SEARCH + INFINITE SCROLL
    // =========================================================================
    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                hasSubmittedSearch = false
            )
        }
        suggestionsJob?.cancel()
        searchJob?.cancel()
        loadMoreJob?.cancel()

        val clean = query.trim()
        if (clean.isEmpty()) {
            _uiState.update {
                it.copy(
                    searchSuggestions = SearchSuggestionsResult(),
                    isSuggestionsLoading = false,
                    searchResults = SearchResults(),
                    isSearching = false,
                    isLoadingMoreSearch = false
                )
            }
            return
        }

        suggestionsJob = viewModelScope.launch {
            _uiState.update { it.copy(isSuggestionsLoading = true) }
            delay(180L)
            val res = InnerTubeMusicClient.fetchSearchSuggestions(clean)
            if (_uiState.value.searchQuery.trim().equals(clean, ignoreCase = true) && !_uiState.value.hasSubmittedSearch) {
                _uiState.update { it.copy(searchSuggestions = res, isSuggestionsLoading = false) }
            }
        }
    }

    fun fillSearchQueryWithoutSubmit(text: String) {
        val next = text.trim()
        if (next.isEmpty()) return
        updateSearchQuery("$next ")
    }

    fun submitSearchQuery(query: String) {
        val clean = query.trim()
        _uiState.update {
            it.copy(
                searchQuery = clean,
                hasSubmittedSearch = clean.isNotEmpty(),
                isSuggestionsLoading = false
            )
        }
        if (clean.isEmpty()) return
        suggestionsJob?.cancel()
        searchJob?.cancel()
        loadMoreJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch(clean, _uiState.value.searchFilter, saveHistory = true)
        }
    }

    fun selectSearchFilter(filter: SearchFilter) {
        _uiState.update { it.copy(searchFilter = filter) }
        val q = _uiState.value.searchQuery.trim()
        if (q.isNotEmpty() && _uiState.value.hasSubmittedSearch) {
            searchJob?.cancel()
            loadMoreJob?.cancel()
            searchJob = viewModelScope.launch {
                executeSearch(q, filter, saveHistory = false)
            }
        }
    }

    private suspend fun executeSearch(query: String, filter: SearchFilter, saveHistory: Boolean) {
        _uiState.update { it.copy(isSearching = true, isLoadingMoreSearch = false) }
        val s = _uiState.value.appSettings
        if (saveHistory && !s.pauseSearchHistory && !s.incognitoSession) {
            val nextHist = store.addSearchHistory(query)
            _uiState.update { it.copy(searchHistory = nextHist) }
        }
        val res = InnerTubeMusicClient.searchAll(query, filter)
        if (_uiState.value.searchQuery.trim().equals(query.trim(), ignoreCase = true)) {
            _uiState.update { it.copy(searchResults = res, isSearching = false) }
        }
    }

    fun loadMoreSearchResults() {
        val cur = _uiState.value
        val token = cur.searchResults.continuationToken ?: return
        if (cur.isLoadingMoreSearch || cur.isSearching) return
        val activeQuery = cur.searchQuery.trim()
        val activeFilter = cur.searchFilter

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreSearch = true) }
            val next = InnerTubeMusicClient.searchContinuation(token, activeFilter)
            _uiState.update { st ->
                if (!st.searchQuery.trim().equals(activeQuery, ignoreCase = true)) {
                    return@update st.copy(isLoadingMoreSearch = false)
                }
                val prev = st.searchResults
                val existingTrackIds = prev.tracks.map { it.id }.toHashSet()
                val existingVideoIds = prev.videos.map { it.id }.toHashSet()
                val existingArtistIds = prev.artists.map { it.browseId }.toHashSet()
                val existingAlbumIds = prev.albums.map { it.browseId }.toHashSet()
                val existingPlaylistIds = prev.playlists.map { it.browseId }.toHashSet()

                val merged = prev.copy(
                    tracks = prev.tracks + next.tracks.filter { existingTrackIds.add(it.id) },
                    videos = prev.videos + next.videos.filter { existingVideoIds.add(it.id) },
                    artists = prev.artists + next.artists.filter { existingArtistIds.add(it.browseId) },
                    albums = prev.albums + next.albums.filter { existingAlbumIds.add(it.browseId) },
                    playlists = prev.playlists + next.playlists.filter { existingPlaylistIds.add(it.browseId) },
                    continuationToken = next.continuationToken?.takeIf { it != token }
                )
                st.copy(searchResults = merged, isLoadingMoreSearch = false)
            }
        }
    }

    fun openAlbumForTrack(track: TrackItem) {
        closeTrackMenu()
        closeTrackCredits()
        if (track.albumBrowseId.isNotBlank()) {
            openCollectionDetail(track.albumBrowseId, track.album.ifBlank { track.title })
            return
        }
        showToast("Mencari album ${track.album.ifBlank { track.title }}...")
        viewModelScope.launch {
            val found = InnerTubeMusicClient.findAlbumBrowseIdForTrack(track)
            if (found != null) {
                openCollectionDetail(found.first, found.second)
            } else {
                showToast("Album tidak tersedia untuk lagu ini")
            }
        }
    }

    fun openTrackCredits(track: TrackItem) {
        closeTrackMenu()
        val s = _uiState.value.appSettings
        val quality = when {
            track.localUri.isNotBlank() -> "File Audio Lokal • Lossless / Device"
            s.playDataSaverCellular -> "Opus / M4A 96kbps • Hemat Kuota"
            s.playHighBitrateWifi -> "Opus / M4A 256kbps • High Quality ExoPlayer"
            else -> "Opus / M4A Adaptif • Media3 ExoPlayer"
        }
        _uiState.update {
            it.copy(
                creditsTrack = track,
                loadedTrackCredits = null,
                isCreditsLoading = true
            )
        }
        creditsJob?.cancel()
        creditsJob = viewModelScope.launch {
            val data = InnerTubeMusicClient.fetchTrackCredits(track, quality)
            if (_uiState.value.creditsTrack?.id == track.id) {
                _uiState.update { it.copy(loadedTrackCredits = data, isCreditsLoading = false) }
            }
        }
    }

    fun closeTrackCredits() {
        creditsJob?.cancel()
        _uiState.update { it.copy(creditsTrack = null, loadedTrackCredits = null, isCreditsLoading = false) }
    }

    fun removeSearchHistory(item: String) {
        _uiState.update { it.copy(searchHistory = store.removeSearchHistory(item)) }
    }

    fun clearAllHistory() {
        store.clearRecentHistory()
        store.clearSearchHistory()
        _uiState.update { it.copy(recentTracks = emptyList(), searchHistory = emptyList()) }
        showToast("Riwayat putar & pencarian dihapus")
    }

    // =========================================================================
    // NAVIGATION TO ALL FULL-SCREEN STAGE 2 / 3 / 4 DESTINATIONS
    // =========================================================================
    fun openSettings() = pushDetail(DetailDestination.Settings)
    fun openDynamicIslandSettings() = pushDetail(DetailDestination.DynamicIsland)
    fun openWidgetSettings() = pushDetail(DetailDestination.WidgetSettings)
    fun openEqualizer() = pushDetail(DetailDestination.Equalizer)
    fun openSongRecognize() = pushDetail(DetailDestination.SongRecognize)
    fun openListenTogether() = pushDetail(DetailDestination.ListenTogether)
    fun openStatsRecap() = pushDetail(DetailDestination.StatsRecap)
    fun openPoster3D(track: TrackItem? = _uiState.value.currentTrack) = pushDetail(DetailDestination.Poster3D(track))

    fun getTop50Tracks(): List<TrackItem> = store.getTop50Tracks()

    private fun pushDetail(dest: DetailDestination) {
        _uiState.update {
            it.copy(
                isNowPlayingOpen = false,
                menuTrack = null,
                detailStack = it.detailStack + dest
            )
        }
    }

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
        val dest = DetailDestination.Collection(browseId = playlist.id, fallbackTitle = playlist.title, isCustom = true)
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
        nextStack.lastOrNull()?.let {
            if (it is DetailDestination.Artist || it is DetailDestination.Collection) {
                loadDetailDestination(it)
            }
        }
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
                            "downloaded" -> OfflineDownloadManager.downloadedTracks.value
                            "device" -> {
                                val scanned = DeviceAudioScanner.scanDeviceAudio(appContext)
                                _uiState.update { it.copy(deviceTracks = scanned) }
                                scanned
                            }
                            "uploaded" -> _uiState.value.uploadedTracks
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
                else -> Unit
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
        val liked = updated.any { it.id == target.id || (target.videoId.isNotEmpty() && it.videoId == target.videoId) }
        DynamicIslandStateBus.update { it.copy(isLiked = liked) }
        SpotyFlowBaseWidgetProvider.updateAllWidgetsAsync(appContext)
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

    fun renamePlaylist(playlistId: String, newTitle: String) {
        val updated = store.renamePlaylist(playlistId, newTitle)
        _uiState.update { it.copy(customPlaylists = updated) }
        showToast("Nama playlist diperbarui")
    }

    fun deletePlaylist(playlistId: String) {
        val updated = store.deletePlaylist(playlistId)
        _uiState.update { it.copy(customPlaylists = updated) }
        showToast("Playlist dihapus")
    }

    fun removeDuplicatesFromPlaylist(playlistId: String) {
        val updated = store.removeDuplicatesFromPlaylist(playlistId)
        _uiState.update { it.copy(customPlaylists = updated) }
        showToast("Duplikat dibersihkan")
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
            refreshLyricEnhancements(res)
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
