package com.spotyflow.android.data

import androidx.compose.ui.graphics.Color
import com.spotyflow.android.ui.theme.SpotyColors

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

enum class SearchFilter(val label: String, val ytmParams: String?) {
    ALL("Semua", null),
    SONGS("Lagu", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
    VIDEOS("Video", "EgWKAQIQAWoKEAkQBRAKEAMQBA%3D%3D"),
    ARTISTS("Artis", "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D"),
    ALBUMS("Album", "EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D"),
    PLAYLISTS("Playlist", "EgeKAQQoAEABagwQDhAKEAMQBRAJEAQ%3D")
}

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class TrackItem(
    val id: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val artistBrowseId: String = "",
    val album: String = "",
    val albumBrowseId: String = "",
    val durationSec: Int = 0,
    val mood: String = "Semua",
    val coverUrl: String = "",
    val localUri: String = "",
    val accentColor: Color = SpotyColors.EmeraldCrystal,
    val orbPrimary: Color = SpotyColors.DefaultMintMist,
    val orbSecondary: Color = SpotyColors.DefaultSkyCrystal,
    val orbTertiary: Color = SpotyColors.DefaultSoftLilac
)

data class ArtistSummary(
    val browseId: String,
    val name: String,
    val subscribersText: String = "",
    val avatarUrl: String = ""
)

data class CollectionSummary(
    val browseId: String,
    val title: String,
    val subtitle: String = "",
    val year: String = "",
    val coverUrl: String = "",
    val isAlbum: Boolean = true
)

data class ArtistProfileData(
    val browseId: String,
    val name: String,
    val subscribersText: String = "",
    val monthlyListenersText: String = "",
    val bannerUrl: String = "",
    val avatarUrl: String = "",
    val description: String = "",
    val topSongs: List<TrackItem> = emptyList(),
    val albums: List<CollectionSummary> = emptyList(),
    val singles: List<CollectionSummary> = emptyList(),
    val relatedArtists: List<ArtistSummary> = emptyList()
)

data class CollectionDetailData(
    val browseId: String,
    val title: String,
    val subtitle: String = "",
    val artistName: String = "",
    val artistBrowseId: String = "",
    val year: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val totalDurationText: String = "",
    val isAlbum: Boolean = true,
    val isCustomPlaylist: Boolean = false,
    val tracks: List<TrackItem> = emptyList()
)

data class SearchResults(
    val topResult: TrackItem? = null,
    val topArtist: ArtistSummary? = null,
    val tracks: List<TrackItem> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val albums: List<CollectionSummary> = emptyList(),
    val playlists: List<CollectionSummary> = emptyList()
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()
}

data class UserProfile(
    val displayName: String = "Pendengar SpotyFlow",
    val handle: String = "@spotyflow",
    val bio: String = "Menikmati musik jernih 120 FPS di SpotyFlow Native",
    val avatarPath: String = ""
)

data class CustomPlaylist(
    val id: String,
    val title: String,
    val description: String = "",
    val tracks: List<TrackItem> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis()
)

data class DailyStreak(
    val count: Int = 1,
    val best: Int = 1,
    val lastDate: String = "",
    val minutesToday: Int = 0
)

data class AmbientPalette(
    val accent: Color = SpotyColors.EmeraldCrystal,
    val orbPrimary: Color = SpotyColors.DefaultMintMist,
    val orbSecondary: Color = SpotyColors.DefaultSkyCrystal,
    val orbTertiary: Color = SpotyColors.DefaultSoftLilac
)

sealed interface DetailDestination {
    data class Artist(val browseId: String, val artistName: String) : DetailDestination
    data class Collection(val browseId: String, val fallbackTitle: String = "", val isCustom: Boolean = false, val localKey: String? = null) : DetailDestination
}

data class PreviewSheetData(
    val title: String,
    val subtitle: String,
    val badge: String,
    val bullets: List<String>
)

data class MoodCategory(
    val id: String,
    val label: String,
    val subtitle: String,
    val glowColor: Color,
    val accentColor: Color
)

fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

fun formatDurationMs(ms: Long): String = formatDuration((ms / 1000L).toInt())

fun formatRemainingMs(currentMs: Long, totalMs: Long): String {
    val remSec = ((totalMs - currentMs).coerceAtLeast(0L) / 1000L).toInt()
    return "-${formatDuration(remSec)}"
}

fun formatTotalDuration(tracks: List<TrackItem>): String {
    val totalSec = tracks.sumOf { it.durationSec.coerceAtLeast(0) }
    if (totalSec <= 0) return "${tracks.size} lagu"
    val mins = totalSec / 60
    val hrs = mins / 60
    return if (hrs > 0) "$hrs jam ${mins % 60} mnt" else "$mins menit"
}

private val ACCENT_POOL = listOf(
    Color(0xFF10B981),
    Color(0xFF0EA5E9),
    Color(0xFF6366F1),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFFF59E0B),
    Color(0xFF14B8A6)
)

fun deterministicAccent(seed: String): Color {
    val idx = (seed.hashCode().and(0x7FFFFFFF)) % ACCENT_POOL.size
    return ACCENT_POOL[idx]
}
