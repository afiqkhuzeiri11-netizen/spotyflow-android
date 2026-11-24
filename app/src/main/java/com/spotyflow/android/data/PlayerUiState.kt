package com.spotyflow.android.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SpotyTab(val label: String) {
    Home("Beranda"),
    Search("Cari"),
    Library("Pustaka"),
    Profile("Profil")
}

enum class RepeatMode {
    Off,
    All,
    One
}

enum class PlayerMode {
    Audio,
    Video
}

data class PreviewSheetData(
    val title: String,
    val subtitle: String,
    val badge: String,
    val bullets: List<String>
)

data class PlayerUiState(
    val activeTab: SpotyTab = SpotyTab.Home,
    val selectedMood: String = "Semua",
    val tracks: List<TrackItem> = SampleCatalog.tracks,
    val currentTrack: TrackItem = SampleCatalog.tracks.first(),
    val isPlaying: Boolean = true,
    val positionSec: Int = 48,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val playerMode: PlayerMode = PlayerMode.Audio,
    val volume: Float = 0.82f,
    val likedTrackIds: Set<String> = setOf("sf-01", "sf-03", "sf-06"),
    val isNowPlayingOpen: Boolean = false,
    val isLyricsVisible: Boolean = false,
    val searchQuery: String = "",
    val searchFilter: String = "Semua",
    val searchHistory: List<String> = SampleCatalog.initialSearchHistory,
    val playlists: List<SamplePlaylist> = SampleCatalog.playlists,
    val streakDays: Int = 3,
    val listenedMinutesToday: Int = 18,
    val activePreviewSheet: PreviewSheetData? = null
) {
    val progress: Float
        get() = if (currentTrack.durationSec <= 0) 0f
        else (positionSec.toFloat() / currentTrack.durationSec.toFloat()).coerceIn(0f, 1f)

    val isCurrentTrackLiked: Boolean
        get() = likedTrackIds.contains(currentTrack.id)

    val activeLyricIndex: Int
        get() {
            val lines = currentTrack.lyrics
            if (lines.isEmpty()) return 0
            var idx = 0
            for (i in lines.indices) {
                if (positionSec >= lines[i].timeSec) idx = i
            }
            return idx
        }

    val filteredHomeTracks: List<TrackItem>
        get() = if (selectedMood == "Semua") tracks
        else tracks.filter { it.mood.equals(selectedMood, ignoreCase = true) }.ifEmpty { tracks }

    val searchResults: List<TrackItem>
        get() {
            val q = searchQuery.trim()
            if (q.isEmpty()) return emptyList()
            return tracks.filter { t ->
                t.title.contains(q, ignoreCase = true) ||
                    t.artist.contains(q, ignoreCase = true) ||
                    t.album.contains(q, ignoreCase = true) ||
                    t.mood.contains(q, ignoreCase = true)
            }
        }
}

class SpotyViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        startPlaybackSimulationTicker()
    }

    private fun startPlaybackSimulationTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _uiState.update { state ->
                    if (!state.isPlaying) return@update state
                    val nextSec = state.positionSec + 1
                    if (nextSec >= state.currentTrack.durationSec) {
                        when (state.repeatMode) {
                            RepeatMode.One -> state.copy(positionSec = 0)
                            RepeatMode.All, RepeatMode.Off -> {
                                val nextTrack = pickNextTrack(state)
                                state.copy(
                                    currentTrack = nextTrack,
                                    positionSec = 0,
                                    isPlaying = true
                                )
                            }
                        }
                    } else {
                        state.copy(positionSec = nextSec)
                    }
                }
            }
        }
    }

    private fun pickNextTrack(state: PlayerUiState): TrackItem {
        val list = state.tracks
        if (list.isEmpty()) return state.currentTrack
        if (state.isShuffle && list.size > 1) {
            return list.filter { it.id != state.currentTrack.id }.random()
        }
        val idx = list.indexOfFirst { it.id == state.currentTrack.id }
        val nextIdx = if (idx < 0) 0 else (idx + 1) % list.size
        return list[nextIdx]
    }

    fun selectTab(tab: SpotyTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectMood(mood: String) {
        _uiState.update { it.copy(selectedMood = mood) }
    }

    fun playTrack(track: TrackItem) {
        _uiState.update { state ->
            val isSame = state.currentTrack.id == track.id
            state.copy(
                currentTrack = track,
                positionSec = if (isSame) state.positionSec else 0,
                isPlaying = true
            )
        }
    }

    fun togglePlayPause() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun nextTrack() {
        _uiState.update { state ->
            val next = pickNextTrack(state)
            state.copy(currentTrack = next, positionSec = 0, isPlaying = true)
        }
    }

    fun previousTrack() {
        _uiState.update { state ->
            if (state.positionSec > 4) {
                return@update state.copy(positionSec = 0)
            }
            val list = state.tracks
            val idx = list.indexOfFirst { it.id == state.currentTrack.id }
            val prevIdx = if (idx <= 0) list.lastIndex else idx - 1
            state.copy(currentTrack = list[prevIdx], positionSec = 0, isPlaying = true)
        }
    }

    fun seekToFraction(fraction: Float) {
        _uiState.update { state ->
            val sec = (fraction.coerceIn(0f, 1f) * state.currentTrack.durationSec).toInt()
            state.copy(positionSec = sec)
        }
    }

    fun seekToSecond(sec: Int) {
        _uiState.update { state ->
            state.copy(positionSec = sec.coerceIn(0, state.currentTrack.durationSec))
        }
    }

    fun toggleLike(trackId: String = _uiState.value.currentTrack.id) {
        _uiState.update { state ->
            val next = state.likedTrackIds.toMutableSet()
            if (next.contains(trackId)) next.remove(trackId) else next.add(trackId)
            state.copy(likedTrackIds = next)
        }
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun cycleRepeat() {
        _uiState.update { state ->
            val next = when (state.repeatMode) {
                RepeatMode.Off -> RepeatMode.All
                RepeatMode.All -> RepeatMode.One
                RepeatMode.One -> RepeatMode.Off
            }
            state.copy(repeatMode = next)
        }
    }

    fun setPlayerMode(mode: PlayerMode) {
        _uiState.update { it.copy(playerMode = mode) }
    }

    fun setVolume(vol: Float) {
        _uiState.update { it.copy(volume = vol.coerceIn(0f, 1f)) }
    }

    fun openNowPlaying(showLyrics: Boolean = false) {
        _uiState.update { it.copy(isNowPlayingOpen = true, isLyricsVisible = showLyrics) }
    }

    fun closeNowPlaying() {
        _uiState.update { it.copy(isNowPlayingOpen = false, isLyricsVisible = false) }
    }

    fun toggleLyricsView() {
        _uiState.update { it.copy(isLyricsVisible = !it.isLyricsVisible) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun submitSearchQuery(query: String) {
        val clean = query.trim()
        _uiState.update { state ->
            val updatedHistory = if (clean.isNotEmpty()) {
                (listOf(clean) + state.searchHistory.filterNot { it.equals(clean, ignoreCase = true) }).take(8)
            } else {
                state.searchHistory
            }
            state.copy(searchQuery = clean, searchHistory = updatedHistory)
        }
    }

    fun removeSearchHistory(item: String) {
        _uiState.update { state ->
            state.copy(searchHistory = state.searchHistory.filterNot { it == item })
        }
    }

    fun selectSearchFilter(filter: String) {
        _uiState.update { it.copy(searchFilter = filter) }
    }

    fun openPreviewSheet(data: PreviewSheetData) {
        _uiState.update { it.copy(activePreviewSheet = data) }
    }

    fun closePreviewSheet() {
        _uiState.update { it.copy(activePreviewSheet = null) }
    }
}

fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

fun formatRemaining(currentSec: Int, totalSec: Int): String {
    val rem = (totalSec - currentSec).coerceAtLeast(0)
    return "-${formatDuration(rem)}"
}
