package com.spotyflow.android.data.remote

import com.spotyflow.android.data.ArtistProfileData
import com.spotyflow.android.data.ArtistSummary
import com.spotyflow.android.data.CollectionDetailData
import com.spotyflow.android.data.CollectionSummary
import com.spotyflow.android.data.SearchFilter
import com.spotyflow.android.data.SearchResults
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.deterministicAccent
import com.spotyflow.android.data.formatTotalDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 100% On-Device InnerTube YouTube Music (WEB_REMIX) & YouTube client.
 * Provides multi-type search, Full Artist Profile, Full Album/Playlist Detail,
 * Mood Feeds, Radio Related Tracks, and official YouTube Music Lyrics fallback.
 */
object InnerTubeMusicClient {
    private const val YTM_SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val YTM_BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
    private const val YTM_NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
    private const val YT_SEARCH_FALLBACK_URL = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val YTM_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private data class TimedCache<T>(val data: T, val createdAtMs: Long)

    private val artistCache = ConcurrentHashMap<String, TimedCache<ArtistProfileData>>()
    private val moodCache = ConcurrentHashMap<String, TimedCache<List<TrackItem>>>()

    private fun buildYtmContext(): JSONObject = JSONObject().apply {
        put("client", JSONObject().apply {
            put("clientName", "WEB_REMIX")
            put("clientVersion", "1.20250219.01.00")
            put("hl", "id")
            put("gl", "ID")
        })
    }

    private fun buildYtWebContext(): JSONObject = JSONObject().apply {
        put("client", JSONObject().apply {
            put("clientName", "WEB")
            put("clientVersion", "2.20250219.00.00")
            put("hl", "id")
            put("gl", "ID")
        })
    }

    private fun postJson(url: String, payload: JSONObject, isYtm: Boolean = true): JSONObject? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", YTM_UA)
                .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8")
                .header("Origin", if (isYtm) "https://music.youtube.com" else "https://www.youtube.com")
                .header("Referer", if (isYtm) "https://music.youtube.com/" else "https://www.youtube.com/")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            SpotyHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bodyStr = resp.body?.string() ?: return null
                JSONObject(bodyStr)
            }
        } catch (_: Throwable) {
            null
        }
    }

    // =========================================================================
    // 1. MULTI-TYPE SEARCH (Songs, Videos, Artists, Albums, Playlists)
    // =========================================================================
    suspend fun searchAll(query: String, filter: SearchFilter = SearchFilter.ALL): SearchResults =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isEmpty()) return@withContext SearchResults()

            val payload = JSONObject().apply {
                put("context", buildYtmContext())
                put("query", clean)
                if (filter.ytmParams != null) put("params", filter.ytmParams)
            }

            val root = postJson(YTM_SEARCH_URL, payload, isYtm = true)
            val parsed = if (root != null) parseYtmSearch(root) else SearchResults()

            if (!parsed.isEmpty) {
                return@withContext parsed
            }

            // Automatic YouTube Web fallback if YTM returned empty
            val ytFallback = searchYouTubeFallback(clean)
            SearchResults(
                topResult = ytFallback.firstOrNull(),
                tracks = ytFallback
            )
        }

    private fun parseYtmSearch(root: JSONObject): SearchResults {
        val tracks = mutableListOf<TrackItem>()
        val artists = mutableListOf<ArtistSummary>()
        val albums = mutableListOf<CollectionSummary>()
        val playlists = mutableListOf<CollectionSummary>()
        val seenTrackIds = HashSet<String>()
        val seenArtistIds = HashSet<String>()
        val seenColIds = HashSet<String>()

        var topTrack: TrackItem? = null
        var topArtist: ArtistSummary? = null

        val shelves = collectObjectsByKey(root, "musicShelfRenderer")
        val cards = collectObjectsByKey(root, "musicCardShelfRenderer")

        // Top result card shelf
        for (card in cards) {
            val titleRuns = card.optJSONObject("title")?.optJSONArray("runs")
            val title = joinRuns(titleRuns)
            val nav = titleRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
            val videoId = nav?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty()
            val browseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            val subtitle = joinRuns(card.optJSONObject("subtitle")?.optJSONArray("runs"))
            val cover = extractBestThumbnail(card.optJSONObject("thumbnail"))

            if (videoId.length == 11 && title.isNotBlank()) {
                val t = TrackItem(
                    id = videoId,
                    videoId = videoId,
                    title = title,
                    artist = extractPrimaryArtist(card.optJSONObject("subtitle")?.optJSONArray("runs")).ifBlank { subtitle },
                    artistBrowseId = extractArtistBrowseId(card.optJSONObject("subtitle")?.optJSONArray("runs")),
                    durationSec = parseDurationFromRuns(card.optJSONObject("subtitle")?.optJSONArray("runs")),
                    coverUrl = upgradeCoverUrl(cover, 544),
                    accentColor = deterministicAccent(title)
                )
                topTrack = t
                if (seenTrackIds.add(videoId)) tracks.add(t)
            } else if (browseId.startsWith("UC") && title.isNotBlank()) {
                val a = ArtistSummary(
                    browseId = browseId,
                    name = title,
                    subscribersText = subtitle,
                    avatarUrl = upgradeCoverUrl(cover, 544)
                )
                topArtist = a
                if (seenArtistIds.add(browseId)) artists.add(a)
            }
        }

        // Standard search shelves
        for (shelf in shelves) {
            val contents = shelf.optJSONArray("contents") ?: continue
            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                parseResponsiveItem(
                    item = item,
                    tracks = tracks,
                    artists = artists,
                    albums = albums,
                    playlists = playlists,
                    seenTrackIds = seenTrackIds,
                    seenArtistIds = seenArtistIds,
                    seenColIds = seenColIds
                )
            }
        }

        return SearchResults(
            topResult = topTrack ?: tracks.firstOrNull(),
            topArtist = topArtist ?: artists.firstOrNull(),
            tracks = tracks,
            artists = artists,
            albums = albums,
            playlists = playlists
        )
    }

    private fun parseResponsiveItem(
        item: JSONObject,
        tracks: MutableList<TrackItem>,
        artists: MutableList<ArtistSummary>,
        albums: MutableList<CollectionSummary>,
        playlists: MutableList<CollectionSummary>,
        seenTrackIds: MutableSet<String>,
        seenArtistIds: MutableSet<String>,
        seenColIds: MutableSet<String>,
        fallbackCover: String = "",
        fallbackArtist: String = "",
        fallbackArtistId: String = "",
        fallbackAlbum: String = "",
        fallbackAlbumId: String = ""
    ) {
        val flexCols = item.optJSONArray("flexColumns") ?: return
        val firstRuns = flexCols.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
        val secondRuns = flexCols.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")

        val title = joinRuns(firstRuns).trim()
        if (title.isBlank()) return

        val rawCover = extractBestThumbnail(item.optJSONObject("thumbnail")).ifBlank { fallbackCover }
        val cover = upgradeCoverUrl(rawCover, 544)

        val videoId = extractVideoId(item)
        val navBrowseId = item.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId").orEmpty()
        val pageType = item.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType").orEmpty()

        if (videoId.length == 11) {
            if (!seenTrackIds.add(videoId)) return
            val artistName = extractPrimaryArtist(secondRuns).ifBlank { fallbackArtist.ifBlank { "Artis" } }
            val artistId = extractArtistBrowseId(secondRuns).ifBlank { fallbackArtistId }
            val (albName, albId) = extractAlbumInfo(secondRuns)
            val fixedCols = item.optJSONArray("fixedColumns")
            val fixedRuns = fixedCols?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            val dur = parseDurationFromRuns(fixedRuns).takeIf { it > 0 }
                ?: parseDurationFromRuns(secondRuns)

            tracks.add(
                TrackItem(
                    id = videoId,
                    videoId = videoId,
                    title = title,
                    artist = artistName,
                    artistBrowseId = artistId,
                    album = albName.ifBlank { fallbackAlbum },
                    albumBrowseId = albId.ifBlank { fallbackAlbumId },
                    durationSec = dur,
                    coverUrl = cover,
                    accentColor = deterministicAccent(title)
                )
            )
            return
        }

        if (navBrowseId.startsWith("UC") || pageType.contains("ARTIST", ignoreCase = true)) {
            if (navBrowseId.isNotBlank() && seenArtistIds.add(navBrowseId)) {
                artists.add(
                    ArtistSummary(
                        browseId = navBrowseId,
                        name = title,
                        subscribersText = joinRuns(secondRuns),
                        avatarUrl = cover
                    )
                )
            }
            return
        }

        if (navBrowseId.startsWith("MPREb_") || pageType.contains("ALBUM", ignoreCase = true)) {
            if (navBrowseId.isNotBlank() && seenColIds.add(navBrowseId)) {
                val sub = joinRuns(secondRuns)
                albums.add(
                    CollectionSummary(
                        browseId = navBrowseId,
                        title = title,
                        subtitle = sub,
                        year = extractYear(sub),
                        coverUrl = cover,
                        isAlbum = true
                    )
                )
            }
            return
        }

        if (navBrowseId.startsWith("VL") || navBrowseId.startsWith("PL") || pageType.contains("PLAYLIST", ignoreCase = true)) {
            if (navBrowseId.isNotBlank() && seenColIds.add(navBrowseId)) {
                playlists.add(
                    CollectionSummary(
                        browseId = navBrowseId,
                        title = title,
                        subtitle = joinRuns(secondRuns),
                        coverUrl = cover,
                        isAlbum = false
                    )
                )
            }
        }
    }

    private fun searchYouTubeFallback(query: String): List<TrackItem> {
        val payload = JSONObject().apply {
            put("context", buildYtWebContext())
            put("query", "$query official audio")
        }
        val root = postJson(YT_SEARCH_FALLBACK_URL, payload, isYtm = false) ?: return emptyList()
        val videoRenderers = collectObjectsByKey(root, "videoRenderer")
        val results = mutableListOf<TrackItem>()
        val seen = HashSet<String>()
        for (v in videoRenderers) {
            val vid = v.optString("videoId")
            if (vid.length != 11 || !seen.add(vid)) continue
            val title = joinRuns(v.optJSONObject("title")?.optJSONArray("runs")).trim()
            if (title.isBlank()) continue
            val artist = joinRuns(v.optJSONObject("ownerText")?.optJSONArray("runs")).ifBlank { "YouTube" }
            val durStr = v.optJSONObject("lengthText")?.optString("simpleText").orEmpty()
            val cover = "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
            results.add(
                TrackItem(
                    id = vid,
                    videoId = vid,
                    title = title,
                    artist = artist,
                    durationSec = parseClockSeconds(durStr),
                    coverUrl = cover,
                    accentColor = deterministicAccent(title)
                )
            )
            if (results.size >= 25) break
        }
        return results
    }

    // =========================================================================
    // 2. FULL ARTIST PROFILE (Banner, Avatar, Bio, Top Songs, Albums, Singles, Related)
    // =========================================================================
    suspend fun fetchArtistProfile(browseId: String?, artistName: String): ArtistProfileData? =
        withContext(Dispatchers.IO) {
            var resolvedId = browseId?.trim().orEmpty()
            if (!resolvedId.startsWith("UC") && artistName.isNotBlank()) {
                val found = searchAll(artistName, SearchFilter.ARTISTS)
                resolvedId = found.artists.firstOrNull()?.browseId.orEmpty()
            }

            if (resolvedId.isBlank()) {
                // Graceful synthesized artist profile from track search
                val fallback = searchAll(artistName, SearchFilter.SONGS)
                if (fallback.tracks.isEmpty()) return@withContext null
                val firstCover = fallback.tracks.first().coverUrl
                return@withContext ArtistProfileData(
                    browseId = "",
                    name = artistName,
                    subscribersText = "Profil Artis SpotyFlow",
                    bannerUrl = firstCover,
                    avatarUrl = firstCover,
                    topSongs = fallback.tracks.take(15)
                )
            }

            val cached = artistCache[resolvedId]
            if (cached != null && System.currentTimeMillis() - cached.createdAtMs < 20 * 60_000L) {
                return@withContext cached.data
            }

            val payload = JSONObject().apply {
                put("context", buildYtmContext())
                put("browseId", resolvedId)
            }
            val root = postJson(YTM_BROWSE_URL, payload, isYtm = true) ?: return@withContext null

            val header = root.optJSONObject("header")
                ?.optJSONObject("musicImmersiveHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicVisualHeaderRenderer")

            val name = joinRuns(header?.optJSONObject("title")?.optJSONArray("runs"))
                .ifBlank { artistName }
            val description = joinRuns(header?.optJSONObject("description")?.optJSONArray("runs"))
            val subsText = joinRuns(
                header?.optJSONObject("subscriptionButton")
                    ?.optJSONObject("subscribeButtonRenderer")
                    ?.optJSONObject("subscriberCountText")
                    ?.optJSONArray("runs")
            ).ifBlank {
                joinRuns(
                    header?.optJSONObject("subscriptionButton")
                        ?.optJSONObject("subscribeButtonRenderer")
                        ?.optJSONObject("longSubscriberCountText")
                        ?.optJSONArray("runs")
                )
            }
            val monthlyListeners = joinRuns(header?.optJSONObject("monthlyListenerCount")?.optJSONArray("runs"))
            val rawHeaderImg = extractBestThumbnail(header?.optJSONObject("thumbnail"))
            val bannerUrl = upgradeCoverUrl(rawHeaderImg, 1200, 600)
            val avatarUrl = upgradeCoverUrl(rawHeaderImg, 544, 544)

            val topSongs = mutableListOf<TrackItem>()
            val albums = mutableListOf<CollectionSummary>()
            val singles = mutableListOf<CollectionSummary>()
            val related = mutableListOf<ArtistSummary>()
            val seenTracks = HashSet<String>()
            val seenCols = HashSet<String>()
            val seenArtists = HashSet<String>()

            // Top songs from musicShelfRenderer
            for (shelf in collectObjectsByKey(root, "musicShelfRenderer")) {
                val contents = shelf.optJSONArray("contents") ?: continue
                for (i in 0 until contents.length()) {
                    val item = contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    parseResponsiveItem(
                        item = item,
                        tracks = topSongs,
                        artists = related,
                        albums = albums,
                        playlists = mutableListOf(),
                        seenTrackIds = seenTracks,
                        seenArtistIds = seenArtists,
                        seenColIds = seenCols,
                        fallbackArtist = name,
                        fallbackArtistId = resolvedId
                    )
                }
            }

            // Albums, Singles & Related Artists from musicCarouselShelfRenderer
            for (carousel in collectObjectsByKey(root, "musicCarouselShelfRenderer")) {
                val shelfTitle = joinRuns(
                    carousel.optJSONObject("header")
                        ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                        ?.optJSONObject("title")
                        ?.optJSONArray("runs")
                ).lowercase()
                val contents = carousel.optJSONArray("contents") ?: continue
                for (i in 0 until contents.length()) {
                    val twoRow = contents.optJSONObject(i)?.optJSONObject("musicTwoRowItemRenderer") ?: continue
                    val itemTitle = joinRuns(twoRow.optJSONObject("title")?.optJSONArray("runs")).trim()
                    val itemSub = joinRuns(twoRow.optJSONObject("subtitle")?.optJSONArray("runs")).trim()
                    val bId = twoRow.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId").orEmpty()
                    val img = upgradeCoverUrl(extractBestThumbnail(twoRow.optJSONObject("thumbnailRenderer")), 544)

                    if (bId.startsWith("UC")) {
                        if (seenArtists.add(bId) && itemTitle.isNotBlank()) {
                            related.add(
                                ArtistSummary(
                                    browseId = bId,
                                    name = itemTitle,
                                    subscribersText = itemSub,
                                    avatarUrl = img
                                )
                            )
                        }
                    } else if (bId.startsWith("MPREb_") && itemTitle.isNotBlank() && seenCols.add(bId)) {
                        val col = CollectionSummary(
                            browseId = bId,
                            title = itemTitle,
                            subtitle = itemSub,
                            year = extractYear(itemSub),
                            coverUrl = img,
                            isAlbum = true
                        )
                        if (shelfTitle.contains("single") || shelfTitle.contains("ep")) {
                            singles.add(col)
                        } else {
                            albums.add(col)
                        }
                    }
                }
            }

            // Ensure topSongs is always populated even for smaller artists
            if (topSongs.isEmpty() && name.isNotBlank()) {
                val extra = searchAll(name, SearchFilter.SONGS)
                topSongs.addAll(extra.tracks.take(15))
            }

            val profile = ArtistProfileData(
                browseId = resolvedId,
                name = name,
                subscribersText = subsText.ifBlank { monthlyListeners },
                monthlyListenersText = monthlyListeners,
                bannerUrl = bannerUrl.ifBlank { topSongs.firstOrNull()?.coverUrl.orEmpty() },
                avatarUrl = avatarUrl.ifBlank { topSongs.firstOrNull()?.coverUrl.orEmpty() },
                description = description,
                topSongs = topSongs,
                albums = albums,
                singles = singles,
                relatedArtists = related
            )
            artistCache[resolvedId] = TimedCache(profile, System.currentTimeMillis())
            profile
        }

    // =========================================================================
    // 3. FULL ALBUM & PLAYLIST DETAIL (browseId MPREb_... or VL... / PL...)
    // =========================================================================
    suspend fun fetchCollectionDetail(browseId: String, fallbackTitle: String = ""): CollectionDetailData? =
        withContext(Dispatchers.IO) {
            val cleanId = browseId.trim()
            if (cleanId.isEmpty()) return@withContext null
            val normalizedId = if (cleanId.startsWith("PL") && !cleanId.startsWith("VL")) "VL$cleanId" else cleanId

            val payload = JSONObject().apply {
                put("context", buildYtmContext())
                put("browseId", normalizedId)
            }
            val root = postJson(YTM_BROWSE_URL, payload, isYtm = true) ?: return@withContext null

            val detailHeader = collectObjectsByKey(root, "musicResponsiveHeaderRenderer").firstOrNull()
                ?: root.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")

            val title = joinRuns(detailHeader?.optJSONObject("title")?.optJSONArray("runs"))
                .ifBlank { fallbackTitle.ifBlank { "Koleksi" } }
            val subRuns = detailHeader?.optJSONObject("subtitle")?.optJSONArray("runs")
            val strapRuns = detailHeader?.optJSONObject("straplineTextOne")?.optJSONArray("runs")
            val subtitle = joinRuns(subRuns)
            val artistName = extractPrimaryArtist(strapRuns).ifBlank { extractPrimaryArtist(subRuns) }
            val artistId = extractArtistBrowseId(strapRuns).ifBlank { extractArtistBrowseId(subRuns) }
            val description = joinRuns(detailHeader?.optJSONObject("description")?.optJSONArray("runs"))
            val coverUrl = upgradeCoverUrl(
                extractBestThumbnail(detailHeader?.optJSONObject("thumbnail")),
                640
            )

            val tracks = mutableListOf<TrackItem>()
            val seen = HashSet<String>()
            val listItems = collectObjectsByKey(root, "musicResponsiveListItemRenderer")
            for (item in listItems) {
                parseResponsiveItem(
                    item = item,
                    tracks = tracks,
                    artists = mutableListOf(),
                    albums = mutableListOf(),
                    playlists = mutableListOf(),
                    seenTrackIds = seen,
                    seenArtistIds = HashSet(),
                    seenColIds = HashSet(),
                    fallbackCover = coverUrl,
                    fallbackArtist = artistName,
                    fallbackArtistId = artistId,
                    fallbackAlbum = title,
                    fallbackAlbumId = normalizedId
                )
            }

            CollectionDetailData(
                browseId = normalizedId,
                title = title,
                subtitle = subtitle,
                artistName = artistName,
                artistBrowseId = artistId,
                year = extractYear(subtitle),
                description = description,
                coverUrl = coverUrl.ifBlank { tracks.firstOrNull()?.coverUrl.orEmpty() },
                totalDurationText = formatTotalDuration(tracks),
                isAlbum = normalizedId.startsWith("MPREb_"),
                tracks = tracks
            )
        }

    // =========================================================================
    // 4. HOME MOOD FEEDS & RADIO RELATED TRACKS
    // =========================================================================
    suspend fun fetchMoodTracks(mood: String): List<TrackItem> = withContext(Dispatchers.IO) {
        val key = mood.ifBlank { "Semua" }
        val cached = moodCache[key]
        if (cached != null && System.currentTimeMillis() - cached.createdAtMs < 15 * 60_000L) {
            return@withContext cached.data
        }
        val query = when (key) {
            "Santai" -> "lagu akustik indie santai Indonesia"
            "Fokus" -> "lofi instrumental study focus chill beats"
            "Semangat" -> "top pop upbeat workout hits Indonesia"
            "Perjalanan" -> "lagu pop perjalanan road trip Indonesia"
            "Malam" -> "lagu malam galau slow acoustic Indonesia"
            else -> "top hits pop Indonesia terbaru"
        }
        val list = searchAll(query, SearchFilter.SONGS).tracks.take(24)
        if (list.isNotEmpty()) {
            moodCache[key] = TimedCache(list, System.currentTimeMillis())
        }
        list
    }

    suspend fun fetchRelatedTracks(track: TrackItem): List<TrackItem> = withContext(Dispatchers.IO) {
        val q = "${track.artist} ${track.title} mix"
        searchAll(q, SearchFilter.SONGS)
            .tracks
            .filter { it.videoId != track.videoId }
            .take(18)
    }

    // =========================================================================
    // 5. YOUTUBE MUSIC OFFICIAL LYRICS FALLBACK (next -> browse lyrics)
    // =========================================================================
    suspend fun fetchYouTubeMusicLyrics(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.length != 11) return@withContext null
        val nextPayload = JSONObject().apply {
            put("context", buildYtmContext())
            put("videoId", videoId)
        }
        val nextRoot = postJson(YTM_NEXT_URL, nextPayload, isYtm = true) ?: return@withContext null
        val tabs = collectObjectsByKey(nextRoot, "tabRenderer")
        var lyricsBrowseId = ""
        for (tab in tabs) {
            val bId = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            if (bId.startsWith("MPLYt")) {
                lyricsBrowseId = bId
                break
            }
        }
        if (lyricsBrowseId.isBlank()) return@withContext null

        val lyrPayload = JSONObject().apply {
            put("context", buildYtmContext())
            put("browseId", lyricsBrowseId)
        }
        val lyrRoot = postJson(YTM_BROWSE_URL, lyrPayload, isYtm = true) ?: return@withContext null
        val descShelf = collectObjectsByKey(lyrRoot, "musicDescriptionShelfRenderer").firstOrNull()
        val text = joinRuns(descShelf?.optJSONObject("description")?.optJSONArray("runs")).trim()
        text.takeIf { it.length > 20 }
    }

    // =========================================================================
    // DEFENSIVE JSON EXTRACTION HELPERS
    // =========================================================================
    private fun collectObjectsByKey(node: Any?, targetKey: String, out: MutableList<JSONObject> = mutableListOf()): List<JSONObject> {
        when (node) {
            is JSONObject -> {
                val it = node.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val v = node.opt(k)
                    if (k == targetKey && v is JSONObject) {
                        out.add(v)
                    }
                    collectObjectsByKey(v, targetKey, out)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectObjectsByKey(node.opt(i), targetKey, out)
                }
            }
        }
        return out
    }

    private fun joinRuns(runs: JSONArray?): String {
        if (runs == null) return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString()
    }

    private fun extractVideoId(item: JSONObject): String {
        val playlistId = item.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
        if (playlistId.length == 11) return playlistId
        val overlay = item.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId").orEmpty()
        if (overlay.length == 11) return overlay
        val watchList = collectObjectsByKey(item, "watchEndpoint")
        return watchList.firstOrNull()?.optString("videoId").orEmpty()
    }

    private fun extractPrimaryArtist(runs: JSONArray?): String {
        if (runs == null) return ""
        for (i in 0 until runs.length()) {
            val r = runs.optJSONObject(i) ?: continue
            val bId = r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            if (bId.startsWith("UC")) return r.optString("text").trim()
        }
        // Fallback: first meaningful non-bullet, non-type token
        val skip = setOf("lagu", "song", "video", "album", "single", "ep", "playlist", "artis", "artist", "•", " • ")
        for (i in 0 until runs.length()) {
            val txt = runs.optJSONObject(i)?.optString("text")?.trim().orEmpty()
            if (txt.isNotEmpty() && !skip.contains(txt.lowercase()) && !txt.contains(":") && !txt.all { it.isDigit() }) {
                return txt
            }
        }
        return ""
    }

    private fun extractArtistBrowseId(runs: JSONArray?): String {
        if (runs == null) return ""
        for (i in 0 until runs.length()) {
            val bId = runs.optJSONObject(i)
                ?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId").orEmpty()
            if (bId.startsWith("UC")) return bId
        }
        return ""
    }

    private fun extractAlbumInfo(runs: JSONArray?): Pair<String, String> {
        if (runs == null) return Pair("", "")
        for (i in 0 until runs.length()) {
            val r = runs.optJSONObject(i) ?: continue
            val bId = r.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId").orEmpty()
            if (bId.startsWith("MPREb_")) {
                return Pair(r.optString("text").trim(), bId)
            }
        }
        return Pair("", "")
    }

    private fun extractBestThumbnail(container: JSONObject?): String {
        if (container == null) return ""
        val thumbs = collectObjectsByKey(container, "thumbnails")
        // Also direct array lookup
        val directArr = container.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: container.optJSONObject("croppedSquareThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            ?: container.optJSONArray("thumbnails")
        if (directArr != null && directArr.length() > 0) {
            return directArr.optJSONObject(directArr.length() - 1)?.optString("url").orEmpty()
        }
        if (thumbs.isNotEmpty()) {
            return thumbs.last().optString("url").orEmpty()
        }
        return ""
    }

    private fun upgradeCoverUrl(url: String, w: Int, h: Int = w): String {
        if (url.isBlank()) return ""
        return if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
            url.replace(Regex("=w\\d+-h\\d+[^\\s]*"), "=w$w-h$h-l90-rj")
        } else {
            url
        }
    }

    private fun parseDurationFromRuns(runs: JSONArray?): Int {
        if (runs == null) return 0
        for (i in runs.length() - 1 downTo 0) {
            val t = runs.optJSONObject(i)?.optString("text")?.trim().orEmpty()
            val sec = parseClockSeconds(t)
            if (sec > 0) return sec
        }
        return 0
    }

    private fun parseClockSeconds(text: String): Int {
        val m = Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$").find(text.trim()) ?: return 0
        val p1 = m.groupValues[1].toIntOrNull() ?: return 0
        val p2 = m.groupValues[2].toIntOrNull() ?: return 0
        val p3 = m.groupValues[3].toIntOrNull()
        return if (p3 != null) p1 * 3600 + p2 * 60 + p3 else p1 * 60 + p2
    }

    private fun extractYear(text: String): String {
        return Regex("\\b(19|20)\\d{2}\\b").find(text)?.value.orEmpty()
    }
}
