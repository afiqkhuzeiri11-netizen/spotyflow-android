package com.spotyflow.android.data.remote

import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.deterministicAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class ImportedPlaylistResult(
    val title: String,
    val tracks: List<TrackItem>,
    val source: String
)

/**
 * Imports playlists directly from YouTube / YouTube Music URLs (list=...)
 * and Spotify public playlist URLs (open.spotify.com/playlist/{id} via embed JSON).
 */
object PlaylistImporter {

    suspend fun importFromUrl(rawUrl: String): ImportedPlaylistResult? = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (url.isBlank()) return@withContext null

        // 1. YouTube / YouTube Music playlist URL (?list=...)
        val ytListMatch = Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)
        if (ytListMatch != null) {
            val listId = ytListMatch.groupValues[1]
            val browseId = if (listId.startsWith("VL")) listId else "VL$listId"
            val col = InnerTubeMusicClient.fetchCollectionDetail(browseId, "Playlist YouTube")
            if (col != null && col.tracks.isNotEmpty()) {
                return@withContext ImportedPlaylistResult(
                    title = col.title.ifBlank { "Impor YouTube Music" },
                    tracks = col.tracks,
                    source = "YouTube Music"
                )
            }
        }

        // 2. Spotify public playlist URL (open.spotify.com/playlist/{id})
        val spMatch = Regex("spotify\\.com/(?:intl-[a-z]+/)?playlist/([A-Za-z0-9]+)").find(url)
        if (spMatch != null) {
            val playlistId = spMatch.groupValues[1]
            return@withContext importSpotifyEmbed(playlistId)
        }

        null
    }

    private fun importSpotifyEmbed(playlistId: String): ImportedPlaylistResult? {
        return try {
            val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
            val req = Request.Builder()
                .url(embedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
                )
                .get()
                .build()

            SpotyHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string() ?: return null
                val nextDataRegex = Regex(
                    """<script\s+id="__NEXT_DATA__"\s+type="application/json">(.+?)</script>""",
                    setOf(RegexOption.DOT_MATCHES_ALL)
                )
                val jsonStr = nextDataRegex.find(html)?.groupValues?.get(1) ?: return null
                val root = JSONObject(jsonStr)
                val entity = root.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("data")
                    ?.optJSONObject("entity") ?: return null

                val title = entity.optString("name", "Playlist Spotify")
                val coverUrl = entity.optJSONObject("coverArt")
                    ?.optJSONArray("sources")
                    ?.optJSONObject(0)
                    ?.optString("url")
                    .orEmpty()

                val trackList = entity.optJSONArray("trackList") ?: return null
                val tracks = mutableListOf<TrackItem>()
                for (i in 0 until trackList.length()) {
                    val item = trackList.optJSONObject(i) ?: continue
                    val tTitle = item.optString("title").trim()
                    if (tTitle.isBlank()) continue
                    val tArtist = item.optString("subtitle", "Artis").trim()
                    val durMs = item.optLong("duration", 0L)
                    tracks.add(
                        TrackItem(
                            id = "sp_${playlistId}_$i",
                            videoId = "", // Resolved on-demand via InnerTube when played!
                            title = tTitle,
                            artist = tArtist,
                            album = title,
                            durationSec = (durMs / 1000L).toInt().coerceAtLeast(0),
                            coverUrl = coverUrl,
                            accentColor = deterministicAccent(tTitle)
                        )
                    )
                }

                if (tracks.isEmpty()) return null
                ImportedPlaylistResult(title = title, tracks = tracks, source = "Spotify")
            }
        } catch (_: Throwable) {
            null
        }
    }
}
