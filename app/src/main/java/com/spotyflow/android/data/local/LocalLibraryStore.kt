package com.spotyflow.android.data.local

import android.content.Context
import android.net.Uri
import com.spotyflow.android.data.ArtistSummary
import com.spotyflow.android.data.CollectionSummary
import com.spotyflow.android.data.CustomPlaylist
import com.spotyflow.android.data.DailyStreak
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.UserProfile
import com.spotyflow.android.data.deterministicAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure SharedPreferences + org.json local persistence layer (zero KSP/Room overhead).
 * All user data (Profile, Likes, Followed Artists, Saved Collections, Playlists, Recent,
 * Top 50 play counts, Search History, and Daily Streak) survives app restarts and updates.
 */
class LocalLibraryStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("spotyflow_native_store_v2", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PROFILE = "user_profile"
        private const val KEY_LIKED = "liked_tracks"
        private const val KEY_FOLLOWED = "followed_artists"
        private const val KEY_SAVED_COLS = "saved_collections"
        private const val KEY_PLAYLISTS = "custom_playlists"
        private const val KEY_RECENT = "recent_tracks"
        private const val KEY_PLAY_COUNTS = "play_counts"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_STREAK = "daily_streak"
        private const val KEY_WATER_DROPS = "pref_water_drops"
        private const val KEY_UPLOADED = "uploaded_tracks"
    }

    fun getUserProfile(): UserProfile {
        val raw = prefs.getString(KEY_PROFILE, null) ?: return UserProfile()
        return try {
            val j = JSONObject(raw)
            UserProfile(
                displayName = j.optString("displayName", "Pendengar SpotyFlow"),
                handle = j.optString("handle", "@spotyflow"),
                bio = j.optString("bio", "Menikmati musik jernih 120 FPS di SpotyFlow Native"),
                avatarPath = j.optString("avatarPath", "")
            )
        } catch (_: Throwable) {
            UserProfile()
        }
    }

    fun saveUserProfile(profile: UserProfile) {
        val j = JSONObject().apply {
            put("displayName", profile.displayName)
            put("handle", profile.handle)
            put("bio", profile.bio)
            put("avatarPath", profile.avatarPath)
        }
        prefs.edit().putString(KEY_PROFILE, j.toString()).apply()
    }

    suspend fun saveAvatarFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val outFile = File(context.filesDir, "profile_avatar.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            val current = getUserProfile()
            val updatedPath = "${outFile.absolutePath}?t=${System.currentTimeMillis()}"
            saveUserProfile(current.copy(avatarPath = updatedPath))
            updatedPath
        } catch (_: Throwable) {
            null
        }
    }

    fun getLikedTracks(): List<TrackItem> = readTrackList(KEY_LIKED)

    fun toggleLike(track: TrackItem): List<TrackItem> {
        val list = getLikedTracks().toMutableList()
        val idx = list.indexOfFirst { it.videoId == track.videoId && track.videoId.isNotEmpty() || it.id == track.id }
        if (idx >= 0) list.removeAt(idx) else list.add(0, track)
        writeTrackList(KEY_LIKED, list)
        return list
    }

    fun getFollowedArtists(): List<ArtistSummary> {
        val raw = prefs.getString(KEY_FOLLOWED, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ArtistSummary(
                    browseId = o.optString("browseId"),
                    name = o.optString("name"),
                    subscribersText = o.optString("subscribersText"),
                    avatarUrl = o.optString("avatarUrl")
                )
            }.filter { it.name.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun toggleFollowArtist(artist: ArtistSummary): List<ArtistSummary> {
        val list = getFollowedArtists().toMutableList()
        val idx = list.indexOfFirst {
            (artist.browseId.isNotEmpty() && it.browseId == artist.browseId) ||
                it.name.equals(artist.name, ignoreCase = true)
        }
        if (idx >= 0) list.removeAt(idx) else list.add(0, artist)
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("browseId", a.browseId)
                put("name", a.name)
                put("subscribersText", a.subscribersText)
                put("avatarUrl", a.avatarUrl)
            })
        }
        prefs.edit().putString(KEY_FOLLOWED, arr.toString()).apply()
        return list
    }

    fun getSavedCollections(): List<CollectionSummary> {
        val raw = prefs.getString(KEY_SAVED_COLS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                CollectionSummary(
                    browseId = o.optString("browseId"),
                    title = o.optString("title"),
                    subtitle = o.optString("subtitle"),
                    year = o.optString("year"),
                    coverUrl = o.optString("coverUrl"),
                    isAlbum = o.optBoolean("isAlbum", true)
                )
            }.filter { it.title.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun toggleSaveCollection(col: CollectionSummary): List<CollectionSummary> {
        val list = getSavedCollections().toMutableList()
        val idx = list.indexOfFirst { it.browseId == col.browseId }
        if (idx >= 0) list.removeAt(idx) else list.add(0, col)
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("browseId", c.browseId)
                put("title", c.title)
                put("subtitle", c.subtitle)
                put("year", c.year)
                put("coverUrl", c.coverUrl)
                put("isAlbum", c.isAlbum)
            })
        }
        prefs.edit().putString(KEY_SAVED_COLS, arr.toString()).apply()
        return list
    }

    fun getCustomPlaylists(): List<CustomPlaylist> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                CustomPlaylist(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    description = o.optString("description"),
                    tracks = parseTrackArray(o.optJSONArray("tracks")),
                    createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis())
                )
            }.filter { it.title.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveCustomPlaylists(list: List<CustomPlaylist>) {
        val arr = JSONArray()
        list.forEach { pl ->
            arr.put(JSONObject().apply {
                put("id", pl.id)
                put("title", pl.title)
                put("description", pl.description)
                put("createdAtMs", pl.createdAtMs)
                put("tracks", encodeTrackArray(pl.tracks))
            })
        }
        prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    fun createPlaylist(title: String, initialTracks: List<TrackItem> = emptyList()): List<CustomPlaylist> {
        val list = getCustomPlaylists().toMutableList()
        val pl = CustomPlaylist(
            id = "pl_${System.currentTimeMillis()}",
            title = title.trim(),
            tracks = initialTracks
        )
        list.add(0, pl)
        saveCustomPlaylists(list)
        return list
    }

    fun addTrackToPlaylist(playlistId: String, track: TrackItem): List<CustomPlaylist> {
        val list = getCustomPlaylists().map { pl ->
            if (pl.id == playlistId && pl.tracks.none { it.videoId == track.videoId && track.videoId.isNotBlank() || it.id == track.id }) {
                pl.copy(tracks = pl.tracks + track)
            } else {
                pl
            }
        }
        saveCustomPlaylists(list)
        return list
    }

    fun renamePlaylist(playlistId: String, newTitle: String): List<CustomPlaylist> {
        val clean = newTitle.trim()
        if (clean.isBlank()) return getCustomPlaylists()
        val list = getCustomPlaylists().map { pl ->
            if (pl.id == playlistId) pl.copy(title = clean) else pl
        }
        saveCustomPlaylists(list)
        return list
    }

    fun deletePlaylist(playlistId: String): List<CustomPlaylist> {
        val list = getCustomPlaylists().filterNot { it.id == playlistId }
        saveCustomPlaylists(list)
        return list
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String): List<CustomPlaylist> {
        val list = getCustomPlaylists().map { pl ->
            if (pl.id == playlistId) pl.copy(tracks = pl.tracks.filterNot { it.id == trackId }) else pl
        }
        saveCustomPlaylists(list)
        return list
    }

    fun removeDuplicatesFromPlaylist(playlistId: String): List<CustomPlaylist> {
        val list = getCustomPlaylists().map { pl ->
            if (pl.id == playlistId) {
                val seen = HashSet<String>()
                val deduped = pl.tracks.filter { t ->
                    val k = t.videoId.ifBlank { "${t.title.lowercase()}|${t.artist.lowercase()}" }
                    seen.add(k)
                }
                pl.copy(tracks = deduped)
            } else {
                pl
            }
        }
        saveCustomPlaylists(list)
        return list
    }

    fun getUploadedTracks(): List<TrackItem> = readTrackList(KEY_UPLOADED)

    fun addUploadedTracks(newTracks: List<TrackItem>): List<TrackItem> {
        val existing = getUploadedTracks().toMutableList()
        newTracks.forEach { t ->
            if (existing.none { it.localUri == t.localUri && t.localUri.isNotBlank() }) {
                existing.add(0, t)
            }
        }
        writeTrackList(KEY_UPLOADED, existing)
        return existing
    }

    fun clearRecentHistory() {
        prefs.edit().remove(KEY_RECENT).apply()
    }

    fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    fun getRecentTracks(): List<TrackItem> = readTrackList(KEY_RECENT)

    fun recordTrackPlay(track: TrackItem, listenedMinutesDelta: Int = 1): Pair<List<TrackItem>, DailyStreak> {
        val recent = getRecentTracks().toMutableList()
        recent.removeAll { it.videoId == track.videoId && track.videoId.isNotEmpty() || it.id == track.id }
        recent.add(0, track)
        val trimmed = recent.take(50)
        writeTrackList(KEY_RECENT, trimmed)

        // Update play counts for Top 50
        val counts = getPlayCounts().toMutableMap()
        val key = track.videoId.ifEmpty { track.id }
        counts[key] = (counts[key] ?: 0) + 1
        val countJson = JSONObject()
        counts.forEach { (k, v) -> countJson.put(k, v) }
        prefs.edit().putString(KEY_PLAY_COUNTS, countJson.toString()).apply()

        val streak = bumpDailyStreak(listenedMinutesDelta)
        return Pair(trimmed, streak)
    }

    fun getPlayCounts(): Map<String, Int> {
        val raw = prefs.getString(KEY_PLAY_COUNTS, null) ?: return emptyMap()
        return try {
            val j = JSONObject(raw)
            val map = mutableMapOf<String, Int>()
            val it = j.keys()
            while (it.hasNext()) {
                val k = it.next()
                map[k] = j.optInt(k, 0)
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    fun getTop50Tracks(): List<TrackItem> {
        val counts = getPlayCounts()
        val combined = LinkedHashMap<String, TrackItem>()
        (getRecentTracks() + getLikedTracks()).forEach { t ->
            val k = t.videoId.ifEmpty { t.id }
            if (!combined.containsKey(k)) combined[k] = t
        }
        return combined.values
            .sortedByDescending { counts[it.videoId.ifEmpty { it.id }] ?: 0 }
            .take(50)
    }

    fun getSearchHistory(): List<String> {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, null)
            ?: return listOf("Tulus", "Mahalini", "Bernadya", "Hindia")
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun addSearchHistory(query: String): List<String> {
        val clean = query.trim()
        if (clean.isEmpty()) return getSearchHistory()
        val next = (listOf(clean) + getSearchHistory().filterNot { it.equals(clean, ignoreCase = true) }).take(10)
        val arr = JSONArray()
        next.forEach { arr.put(it) }
        prefs.edit().putString(KEY_SEARCH_HISTORY, arr.toString()).apply()
        return next
    }

    fun removeSearchHistory(query: String): List<String> {
        val next = getSearchHistory().filterNot { it == query }
        val arr = JSONArray()
        next.forEach { arr.put(it) }
        prefs.edit().putString(KEY_SEARCH_HISTORY, arr.toString()).apply()
        return next
    }

    fun getDailyStreak(): DailyStreak {
        val raw = prefs.getString(KEY_STREAK, null) ?: return DailyStreak()
        return try {
            val j = JSONObject(raw)
            DailyStreak(
                count = j.optInt("count", 1).coerceAtLeast(1),
                best = j.optInt("best", 1).coerceAtLeast(1),
                lastDate = j.optString("lastDate", ""),
                minutesToday = j.optInt("minutesToday", 0).coerceAtLeast(0)
            )
        } catch (_: Throwable) {
            DailyStreak()
        }
    }

    private fun bumpDailyStreak(minutesDelta: Int): DailyStreak {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = fmt.format(Date())
        val yesterday = fmt.format(Date(System.currentTimeMillis() - 86_400_000L))
        val cur = getDailyStreak()
        val next = when (cur.lastDate) {
            today -> cur.copy(minutesToday = cur.minutesToday + minutesDelta)
            yesterday -> {
                val c = cur.count + 1
                cur.copy(count = c, best = maxOf(cur.best, c), lastDate = today, minutesToday = minutesDelta)
            }
            else -> cur.copy(count = 1, best = maxOf(cur.best, 1), lastDate = today, minutesToday = minutesDelta)
        }
        val j = JSONObject().apply {
            put("count", next.count)
            put("best", next.best)
            put("lastDate", next.lastDate)
            put("minutesToday", next.minutesToday)
        }
        prefs.edit().putString(KEY_STREAK, j.toString()).apply()
        return next
    }

    fun isWaterDropsEnabled(): Boolean = prefs.getBoolean(KEY_WATER_DROPS, true)

    fun setWaterDropsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WATER_DROPS, enabled).apply()
    }

    private fun readTrackList(key: String): List<TrackItem> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            parseTrackArray(JSONArray(raw))
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun writeTrackList(key: String, tracks: List<TrackItem>) {
        prefs.edit().putString(key, encodeTrackArray(tracks).toString()).apply()
    }

    private fun encodeTrackArray(tracks: List<TrackItem>): JSONArray {
        val arr = JSONArray()
        tracks.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("videoId", t.videoId)
                put("title", t.title)
                put("artist", t.artist)
                put("artistBrowseId", t.artistBrowseId)
                put("album", t.album)
                put("albumBrowseId", t.albumBrowseId)
                put("durationSec", t.durationSec)
                put("mood", t.mood)
                put("coverUrl", t.coverUrl)
                put("localUri", t.localUri)
            })
        }
        return arr
    }

    private fun parseTrackArray(arr: JSONArray?): List<TrackItem> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val title = o.optString("title")
            TrackItem(
                id = o.optString("id"),
                videoId = o.optString("videoId"),
                title = title,
                artist = o.optString("artist", "Artis"),
                artistBrowseId = o.optString("artistBrowseId"),
                album = o.optString("album"),
                albumBrowseId = o.optString("albumBrowseId"),
                durationSec = o.optInt("durationSec", 0),
                mood = o.optString("mood", "Semua"),
                coverUrl = o.optString("coverUrl"),
                localUri = o.optString("localUri"),
                accentColor = deterministicAccent(title)
            )
        }.filter { it.title.isNotBlank() }
    }
}
