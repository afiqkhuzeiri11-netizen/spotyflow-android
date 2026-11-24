package com.spotyflow.android.data.remote

import com.spotyflow.android.data.ArtistProfileData
import com.spotyflow.android.data.ArtistSummary
import com.spotyflow.android.data.CollectionDetailData
import com.spotyflow.android.data.CollectionSummary
import com.spotyflow.android.data.QuickSearchEntity
import com.spotyflow.android.data.QuickSuggestionType
import com.spotyflow.android.data.SearchFilter
import com.spotyflow.android.data.SearchResults
import com.spotyflow.android.data.SearchSuggestionsResult
import com.spotyflow.android.data.TrackCreditsData
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.deterministicAccent
import com.spotyflow.android.data.formatDuration
import com.spotyflow.android.data.formatTotalDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * SpotyFlow v2.4.0 On-Device InnerTube YouTube Music (WEB_REMIX) + Multi-Source Metadata Engine.
 * - Dual-Engine Live Autocomplete (< 200ms): YTM get_search_suggestions + YouTube Suggest + Deezer Quick Rescue
 * - Super-Complete Search (20-100+ items): Hero Top Result, Songs, Videos, Albums, Artists, Playlists, Continuation & Deezer Rescue
 * - 100% HD Artist Banner (YTM Immersive/Visual/Standard headers + TheAudioDB Fanart/Banner + Deezer XL)
 * - Full Album Detail (MPREb_ + Deezer album metadata + Label, Release Date, Copyright, Numbered Tracks)
 * - Official Track Credits & Info (Performers, Composers, Lyricists, Producers, Label, Copyright, Genre, BPM)
 */
object InnerTubeMusicClient {
    private const val YTM_SUGGEST_URL = "https://music.youtube.com/youtubei/v1/music/get_search_suggestions?prettyPrint=false"
    private const val YT_FAST_SUGGEST_URL = "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&hl=id&gl=ID&q="
    private const val YTM_SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val YTM_BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
    private const val YTM_NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
    private const val YT_SEARCH_FALLBACK_URL = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val DEEZER_API = "https://api.deezer.com"
    private const val AUDIODB_API = "https://www.theaudiodb.com/api/v1/json/2"
    private const val ITUNES_API = "https://itunes.apple.com"
    private const val YTM_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val fastClient = SpotyHttpClient.client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()

    private data class TimedCache<T>(val data: T, val createdAtMs: Long)

    private val suggestionsCache = ConcurrentHashMap<String, TimedCache<SearchSuggestionsResult>>()
    private val artistCache = ConcurrentHashMap<String, TimedCache<ArtistProfileData>>()
    private val collectionCache = ConcurrentHashMap<String, TimedCache<CollectionDetailData>>()
    private val creditsCache = ConcurrentHashMap<String, TimedCache<TrackCreditsData>>()
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
            fastClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bodyStr = resp.body?.string() ?: return null
                JSONObject(bodyStr)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getString(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", YTM_UA)
                .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8")
                .get()
                .build()
            fastClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getJson(url: String): JSONObject? {
        val raw = getString(url) ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Throwable) {
            null
        }
    }

    private fun encodeQuery(q: String): String = try {
        URLEncoder.encode(q, "UTF-8")
    } catch (_: Throwable) {
        q.replace(" ", "%20")
    }

    // =========================================================================
    // 1. LIVE SEARCH SUGGESTIONS ALA YOUTUBE / YOUTUBE MUSIC (< 200ms)
    // =========================================================================
    suspend fun fetchSearchSuggestions(query: String): SearchSuggestionsResult = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext SearchSuggestionsResult()

        val cacheKey = clean.lowercase()
        val cached = suggestionsCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.createdAtMs < 4 * 60_000L) {
            return@withContext cached.data
        }

        coroutineScope {
            val ytmDeferred = async { fetchYtmRawSuggestions(clean) }
            val fastFallbackDeferred = async { fetchYoutubeFastSuggestKeywords(clean) }
            val deezerQuickDeferred = async { fetchDeezerQuickEntities(clean) }

            val (ytmKeywords, ytmEntities) = ytmDeferred.await()
            val fallbackKeywords = fastFallbackDeferred.await()

            val mergedKeywords = LinkedHashSet<String>()
            ytmKeywords.forEach { if (it.isNotBlank()) mergedKeywords.add(it.trim()) }
            fallbackKeywords.forEach { if (it.isNotBlank() && mergedKeywords.size < 8) mergedKeywords.add(it.trim()) }

            val finalEntities = mutableListOf<QuickSearchEntity>()
            finalEntities.addAll(ytmEntities.take(6))
            if (finalEntities.size < 3) {
                val rescued = deezerQuickDeferred.await()
                val existingKeys = finalEntities.map { "${it.type}:${it.title.lowercase()}" }.toMutableSet()
                for (e in rescued) {
                    if (finalEntities.size >= 6) break
                    if (existingKeys.add("${e.type}:${e.title.lowercase()}")) {
                        finalEntities.add(e)
                    }
                }
            }

            val result = SearchSuggestionsResult(
                query = clean,
                textSuggestions = mergedKeywords.take(8),
                quickEntities = finalEntities.take(6)
            )
            if (result.textSuggestions.isNotEmpty() || result.quickEntities.isNotEmpty()) {
                suggestionsCache[cacheKey] = TimedCache(result, System.currentTimeMillis())
            }
            result
        }
    }

    private fun fetchYtmRawSuggestions(query: String): Pair<List<String>, List<QuickSearchEntity>> {
        val payload = JSONObject().apply {
            put("context", buildYtmContext())
            put("input", query)
        }
        val root = postJson(YTM_SUGGEST_URL, payload, isYtm = true) ?: return Pair(emptyList(), emptyList())

        val keywords = mutableListOf<String>()
        val entities = mutableListOf<QuickSearchEntity>()
        val seenKw = HashSet<String>()
        val seenEnt = HashSet<String>()

        // 1. Text suggestions (searchSuggestionRenderer)
        for (renderer in collectObjectsByKey(root, "searchSuggestionRenderer")) {
            val text = joinRuns(renderer.optJSONObject("suggestion")?.optJSONArray("runs")).trim()
            if (text.isNotBlank() && seenKw.add(text.lowercase())) {
                keywords.add(text)
            }
        }

        // 2. Instant Rich Music/Album/Artist entities (musicResponsiveListItemRenderer)
        for (item in collectObjectsByKey(root, "musicResponsiveListItemRenderer")) {
            val entity = parseQuickSuggestionEntity(item) ?: continue
            if (seenEnt.add("${entity.type}:${entity.id}")) {
                entities.add(entity)
            }
        }

        return Pair(keywords.take(8), entities.take(6))
    }

    private fun parseQuickSuggestionEntity(item: JSONObject): QuickSearchEntity? {
        val flexCols = item.optJSONArray("flexColumns") ?: return null
        val firstRuns = flexCols.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
        val secondRuns = flexCols.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")

        val title = joinRuns(firstRuns).trim()
        if (title.isBlank()) return null

        val cover = upgradeCoverUrl(extractBestThumbnail(item.optJSONObject("thumbnail")), 240)
        val rawSub = joinRuns(secondRuns).trim()
        val videoId = extractVideoId(item)
        val navBrowseId = item.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId").orEmpty()
        val pageType = item.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType").orEmpty()

        val allRuns = collectFlexRuns(item)
        val artistName = extractPrimaryArtist(secondRuns).ifBlank { extractPrimaryArtist(allRuns) }
        val artistId = extractArtistBrowseId(allRuns).ifBlank { extractMenuBrowseId(item, "UC") }
        val (albName, albId) = extractAlbumInfo(allRuns).let { pair ->
            if (pair.second.isNotBlank()) pair else Pair(pair.first, extractMenuBrowseId(item, "MPREb_"))
        }

        if (videoId.length == 11) {
            val dur = parseDurationFromRuns(secondRuns).takeIf { it > 0 } ?: parseDurationFromRuns(allRuns)
            val isVid = rawSub.contains("video", ignoreCase = true)
            val track = TrackItem(
                id = videoId,
                videoId = videoId,
                title = title,
                artist = artistName.ifBlank { "Artis" },
                artistBrowseId = artistId,
                album = albName,
                albumBrowseId = albId,
                durationSec = dur,
                coverUrl = upgradeCoverUrl(cover, 544),
                accentColor = deterministicAccent(title)
            )
            val cleanSub = rawSub.ifBlank {
                buildString {
                    append(if (isVid) "Video" else "Lagu")
                    if (track.artist.isNotBlank()) append(" • ${track.artist}")
                    if (dur > 0) append(" • ${formatDuration(dur)}")
                }
            }
            return QuickSearchEntity(
                type = if (isVid) QuickSuggestionType.VIDEO else QuickSuggestionType.SONG,
                id = videoId,
                title = title,
                subtitle = cleanSub,
                coverUrl = cover,
                trackItem = track,
                artistBrowseId = artistId.takeIf { it.isNotBlank() },
                albumBrowseId = albId.takeIf { it.isNotBlank() }
            )
        }

        if (navBrowseId.startsWith("UC") || pageType.contains("ARTIST", ignoreCase = true)) {
            if (navBrowseId.isBlank()) return null
            val cleanSub = rawSub.ifBlank { "Artis" }.let {
                if (it.contains("artis", ignoreCase = true) || it.contains("artist", ignoreCase = true)) it else "Artis • $it"
            }
            return QuickSearchEntity(
                type = QuickSuggestionType.ARTIST,
                id = navBrowseId,
                title = title,
                subtitle = cleanSub,
                coverUrl = cover,
                artistBrowseId = navBrowseId
            )
        }

        if (navBrowseId.startsWith("MPREb_") || pageType.contains("ALBUM", ignoreCase = true)) {
            if (navBrowseId.isBlank()) return null
            val cleanSub = rawSub.ifBlank { "Album" }.let {
                if (it.contains("album", ignoreCase = true) || it.contains("single", ignoreCase = true) || it.contains("ep", ignoreCase = true)) it else "Album • $it"
            }
            return QuickSearchEntity(
                type = QuickSuggestionType.ALBUM,
                id = navBrowseId,
                title = title,
                subtitle = cleanSub,
                coverUrl = cover,
                artistBrowseId = artistId.takeIf { it.isNotBlank() },
                albumBrowseId = navBrowseId
            )
        }

        if (navBrowseId.startsWith("VL") || navBrowseId.startsWith("PL") || pageType.contains("PLAYLIST", ignoreCase = true)) {
            if (navBrowseId.isBlank()) return null
            return QuickSearchEntity(
                type = QuickSuggestionType.PLAYLIST,
                id = navBrowseId,
                title = title,
                subtitle = rawSub.ifBlank { "Playlist" },
                coverUrl = cover
            )
        }

        return null
    }

    private fun fetchYoutubeFastSuggestKeywords(query: String): List<String> {
        val raw = getString("$YT_FAST_SUGGEST_URL${encodeQuery(query)}") ?: return emptyList()
        return try {
            val start = raw.indexOf('[')
            val end = raw.lastIndexOf(']')
            if (start < 0 || end <= start) return emptyList()
            val arr = JSONArray(raw.substring(start, end + 1))
            val listArr = arr.optJSONArray(1) ?: return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until listArr.length()) {
                val entry = listArr.opt(i)
                val kw = when (entry) {
                    is JSONArray -> entry.optString(0).trim()
                    is String -> entry.trim()
                    else -> ""
                }
                if (kw.isNotBlank()) out.add(kw)
                if (out.size >= 8) break
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun fetchDeezerQuickEntities(query: String): List<QuickSearchEntity> {
        val out = mutableListOf<QuickSearchEntity>()
        val encoded = encodeQuery(query)
        try {
            val artJson = getJson("$DEEZER_API/search/artist?q=$encoded&limit=2")
            val artArr = artJson?.optJSONArray("data")
            if (artArr != null && artArr.length() > 0) {
                val a = artArr.optJSONObject(0)
                val name = a?.optString("name").orEmpty().trim()
                val fans = a?.optLong("nb_fan", 0L) ?: 0L
                val pic = a?.optString("picture_medium").orEmpty()
                if (name.isNotBlank()) {
                    val sub = if (fans > 0) "Artis • ${formatFollowerCount(fans)}" else "Artis"
                    out.add(
                        QuickSearchEntity(
                            type = QuickSuggestionType.ARTIST,
                            id = "deezer_artist:$name",
                            title = name,
                            subtitle = sub,
                            coverUrl = pic
                        )
                    )
                }
            }

            val trkJson = getJson("$DEEZER_API/search/track?q=$encoded&limit=3")
            val trkArr = trkJson?.optJSONArray("data")
            if (trkArr != null) {
                for (i in 0 until minOf(3, trkArr.length())) {
                    val t = trkArr.optJSONObject(i) ?: continue
                    val title = t.optString("title").trim()
                    val artist = t.optJSONObject("artist")?.optString("name").orEmpty().trim()
                    val albObj = t.optJSONObject("album")
                    val albTitle = albObj?.optString("title").orEmpty().trim()
                    val albId = albObj?.optLong("id", 0L) ?: 0L
                    val cover = albObj?.optString("cover_big").orEmpty()
                        .ifBlank { albObj?.optString("cover_medium").orEmpty() }
                    val dur = t.optInt("duration", 0)
                    if (title.isBlank()) continue
                    val syntheticId = "dz_${t.optLong("id", title.hashCode().toLong())}"
                    val trackItem = TrackItem(
                        id = syntheticId,
                        videoId = "",
                        title = title,
                        artist = artist.ifBlank { "Artis" },
                        album = albTitle,
                        albumBrowseId = if (albId > 0L) "deezer_album:$albId" else "",
                        durationSec = dur,
                        coverUrl = cover,
                        accentColor = deterministicAccent(title)
                    )
                    val sub = buildString {
                        append("Lagu • ${trackItem.artist}")
                        if (dur > 0) append(" • ${formatDuration(dur)}")
                    }
                    out.add(
                        QuickSearchEntity(
                            type = QuickSuggestionType.SONG,
                            id = syntheticId,
                            title = title,
                            subtitle = sub,
                            coverUrl = cover,
                            trackItem = trackItem,
                            albumBrowseId = trackItem.albumBrowseId.takeIf { it.isNotBlank() }
                        )
                    )
                }
            }

            val albJson = getJson("$DEEZER_API/search/album?q=$encoded&limit=2")
            val albArr = albJson?.optJSONArray("data")
            if (albArr != null && albArr.length() > 0) {
                val al = albArr.optJSONObject(0)
                val title = al?.optString("title").orEmpty().trim()
                val alId = al?.optLong("id", 0L) ?: 0L
                val artist = al?.optJSONObject("artist")?.optString("name").orEmpty().trim()
                val cover = al?.optString("cover_big").orEmpty()
                if (title.isNotBlank() && alId > 0L) {
                    out.add(
                        QuickSearchEntity(
                            type = QuickSuggestionType.ALBUM,
                            id = "deezer_album:$alId",
                            title = title,
                            subtitle = if (artist.isNotBlank()) "Album • $artist" else "Album",
                            coverUrl = cover,
                            albumBrowseId = "deezer_album:$alId"
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            // Non-fatal quick rescue
        }
        return out
    }

    // =========================================================================
    // 2. SUPER-COMPLETE MULTI-TYPE SEARCH (20-100+ Results + Continuation + Rescue)
    // =========================================================================
    suspend fun searchAll(query: String, filter: SearchFilter = SearchFilter.ALL): SearchResults =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isEmpty()) return@withContext SearchResults()

            coroutineScope {
                val primaryDeferred = async {
                    val payload = JSONObject().apply {
                        put("context", buildYtmContext())
                        put("query", clean)
                        if (filter.ytmParams != null) put("params", filter.ytmParams)
                    }
                    val root = postJson(YTM_SEARCH_URL, payload, isYtm = true)
                    if (root != null) parseYtmSearch(root, filter) else SearchResults()
                }

                // When searching ALL, fetch a dedicated SONGS pass in parallel so we always have 20+ songs!
                val songsBoostDeferred = if (filter == SearchFilter.ALL) {
                    async {
                        val p = JSONObject().apply {
                            put("context", buildYtmContext())
                            put("query", clean)
                            put("params", SearchFilter.SONGS.ytmParams)
                        }
                        val r = postJson(YTM_SEARCH_URL, p, isYtm = true)
                        if (r != null) parseYtmSearch(r, SearchFilter.SONGS) else SearchResults()
                    }
                } else null

                val primary = primaryDeferred.await()
                val songsBoost = songsBoostDeferred?.await()

                val mergedTracks = mutableListOf<TrackItem>()
                val seenTrackIds = HashSet<String>()
                fun addUniqueTracks(list: List<TrackItem>) {
                    for (t in list) {
                        val key = if (t.videoId.length == 11) t.videoId else "${t.title.lowercase()}::${t.artist.lowercase()}"
                        if (seenTrackIds.add(key)) mergedTracks.add(t)
                    }
                }
                addUniqueTracks(primary.tracks)
                if (songsBoost != null) addUniqueTracks(songsBoost.tracks)

                val mergedVideos = primary.videos.toMutableList()
                val mergedArtists = primary.artists.toMutableList()
                val mergedAlbums = primary.albums.toMutableList()
                val mergedPlaylists = primary.playlists.toMutableList()

                // Multi-Source Parallel Rescue (YouTube Web + Deezer Public API) if results are sparse
                val needRescue = (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) && mergedTracks.size < 6
                if (needRescue) {
                    val ytDef = async { searchYouTubeFallback(clean) }
                    val dzDef = async { searchDeezerRescue(clean) }
                    addUniqueTracks(ytDef.await())
                    val (dzTracks, dzAlbums, dzArtists) = dzDef.await()
                    addUniqueTracks(dzTracks)
                    if (mergedAlbums.isEmpty()) mergedAlbums.addAll(dzAlbums)
                    if (mergedArtists.isEmpty()) mergedArtists.addAll(dzArtists)
                }

                val bestContinuation = primary.continuationToken ?: songsBoost?.continuationToken

                SearchResults(
                    topResult = primary.topResult ?: mergedTracks.firstOrNull(),
                    topArtist = primary.topArtist ?: mergedArtists.firstOrNull(),
                    topAlbum = primary.topAlbum ?: mergedAlbums.firstOrNull(),
                    tracks = mergedTracks,
                    videos = mergedVideos,
                    artists = mergedArtists,
                    albums = mergedAlbums,
                    playlists = mergedPlaylists,
                    continuationToken = bestContinuation
                )
            }
        }

    suspend fun searchContinuation(continuationToken: String, filter: SearchFilter = SearchFilter.ALL): SearchResults =
        withContext(Dispatchers.IO) {
            val token = continuationToken.trim()
            if (token.isEmpty()) return@withContext SearchResults()
            val payload = JSONObject().apply {
                put("context", buildYtmContext())
                put("continuation", token)
            }
            val url = "$YTM_SEARCH_URL&ctoken=${encodeQuery(token)}&continuation=${encodeQuery(token)}"
            val root = postJson(url, payload, isYtm = true) ?: return@withContext SearchResults()
            parseYtmSearch(root, filter)
        }

    private fun parseYtmSearch(root: JSONObject, filter: SearchFilter): SearchResults {
        val tracks = mutableListOf<TrackItem>()
        val videos = mutableListOf<TrackItem>()
        val artists = mutableListOf<ArtistSummary>()
        val albums = mutableListOf<CollectionSummary>()
        val playlists = mutableListOf<CollectionSummary>()
        val seenTrackIds = HashSet<String>()
        val seenVideoIds = HashSet<String>()
        val seenArtistIds = HashSet<String>()
        val seenColIds = HashSet<String>()

        var topTrack: TrackItem? = null
        var topArtist: ArtistSummary? = null
        var topAlbum: CollectionSummary? = null

        val cards = collectObjectsByKey(root, "musicCardShelfRenderer")
        for (card in cards) {
            val titleRuns = card.optJSONObject("title")?.optJSONArray("runs")
            val title = joinRuns(titleRuns).trim()
            val nav = titleRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                ?: card.optJSONObject("onTap")
            val videoId = nav?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty()
                .ifBlank { extractVideoId(card) }
            val browseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            val subRuns = card.optJSONObject("subtitle")?.optJSONArray("runs")
            val subtitle = joinRuns(subRuns).trim()
            val cover = extractBestThumbnail(card.optJSONObject("thumbnail"))

            if (videoId.length == 11 && title.isNotBlank()) {
                val (albName, albId) = extractAlbumInfo(subRuns).let { p ->
                    if (p.second.isNotBlank()) p else Pair(p.first, extractMenuBrowseId(card, "MPREb_"))
                }
                val t = TrackItem(
                    id = videoId,
                    videoId = videoId,
                    title = title,
                    artist = extractPrimaryArtist(subRuns).ifBlank { subtitle },
                    artistBrowseId = extractArtistBrowseId(subRuns).ifBlank { extractMenuBrowseId(card, "UC") },
                    album = albName,
                    albumBrowseId = albId,
                    durationSec = parseDurationFromRuns(subRuns),
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
            } else if (browseId.startsWith("MPREb_") && title.isNotBlank()) {
                val col = CollectionSummary(
                    browseId = browseId,
                    title = title,
                    subtitle = subtitle,
                    year = extractYear(subtitle),
                    coverUrl = upgradeCoverUrl(cover, 544),
                    isAlbum = true
                )
                topAlbum = col
                if (seenColIds.add(browseId)) albums.add(col)
            }

            // Card shelf may also hold nested contents (e.g., top songs under top artist!)
            val cardContents = card.optJSONArray("contents")
            if (cardContents != null) {
                for (i in 0 until cardContents.length()) {
                    val item = cardContents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    parseResponsiveItem(
                        item = item,
                        tracks = tracks,
                        videos = videos,
                        artists = artists,
                        albums = albums,
                        playlists = playlists,
                        seenTrackIds = seenTrackIds,
                        seenVideoIds = seenVideoIds,
                        seenArtistIds = seenArtistIds,
                        seenColIds = seenColIds
                    )
                }
            }
        }

        // Standard search shelves + continuation shelves
        val shelves = collectObjectsByKey(root, "musicShelfRenderer") +
            collectObjectsByKey(root, "musicShelfContinuation")
        var continuationToken: String? = null

        for (shelf in shelves) {
            val shelfTitle = joinRuns(shelf.optJSONObject("title")?.optJSONArray("runs")).lowercase()
            val isVideoShelf = filter == SearchFilter.VIDEOS || shelfTitle.contains("video")
            val contents = shelf.optJSONArray("contents")
            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val item = contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    parseResponsiveItem(
                        item = item,
                        tracks = tracks,
                        videos = videos,
                        artists = artists,
                        albums = albums,
                        playlists = playlists,
                        seenTrackIds = seenTrackIds,
                        seenVideoIds = seenVideoIds,
                        seenArtistIds = seenArtistIds,
                        seenColIds = seenColIds,
                        preferVideo = isVideoShelf
                    )
                }
            }

            if (continuationToken == null) {
                val conts = shelf.optJSONArray("continuations")
                val token = conts?.optJSONObject(0)
                    ?.optJSONObject("nextContinuationData")
                    ?.optString("continuation").orEmpty()
                if (token.isNotBlank()) continuationToken = token
            }
        }

        // Fallback scan for continuation token anywhere in response
        if (continuationToken == null) {
            val nextConts = collectObjectsByKey(root, "nextContinuationData")
            continuationToken = nextConts.firstOrNull()?.optString("continuation")?.takeIf { it.isNotBlank() }
        }

        return SearchResults(
            topResult = topTrack ?: tracks.firstOrNull(),
            topArtist = topArtist ?: artists.firstOrNull(),
            topAlbum = topAlbum ?: albums.firstOrNull(),
            tracks = tracks,
            videos = videos,
            artists = artists,
            albums = albums,
            playlists = playlists,
            continuationToken = continuationToken
        )
    }

    private fun parseResponsiveItem(
        item: JSONObject,
        tracks: MutableList<TrackItem>,
        videos: MutableList<TrackItem> = mutableListOf(),
        artists: MutableList<ArtistSummary>,
        albums: MutableList<CollectionSummary>,
        playlists: MutableList<CollectionSummary>,
        seenTrackIds: MutableSet<String>,
        seenVideoIds: MutableSet<String> = HashSet(),
        seenArtistIds: MutableSet<String>,
        seenColIds: MutableSet<String>,
        preferVideo: Boolean = false,
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
        val allFlexRuns = collectFlexRuns(item)

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
            val subText = joinRuns(secondRuns)
            val isVideoItem = preferVideo || subText.contains("ditonton", ignoreCase = true) || subText.contains("views", ignoreCase = true)
            val artistName = extractPrimaryArtist(secondRuns)
                .ifBlank { extractPrimaryArtist(allFlexRuns) }
                .ifBlank { fallbackArtist.ifBlank { "Artis" } }
            val artistId = extractArtistBrowseId(allFlexRuns)
                .ifBlank { extractMenuBrowseId(item, "UC") }
                .ifBlank { fallbackArtistId }
            val (foundAlbName, foundAlbId) = extractAlbumInfo(allFlexRuns)
            val albumId = foundAlbId
                .ifBlank { extractMenuBrowseId(item, "MPREb_") }
                .ifBlank { fallbackAlbumId }
            val albumName = foundAlbName.ifBlank { fallbackAlbum }

            val fixedCols = item.optJSONArray("fixedColumns")
            val fixedRuns = fixedCols?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            val dur = parseDurationFromRuns(fixedRuns).takeIf { it > 0 }
                ?: parseDurationFromRuns(secondRuns).takeIf { it > 0 }
                ?: parseDurationFromRuns(allFlexRuns)

            val track = TrackItem(
                id = videoId,
                videoId = videoId,
                title = title,
                artist = artistName,
                artistBrowseId = artistId,
                album = albumName,
                albumBrowseId = albumId,
                durationSec = dur,
                coverUrl = cover,
                accentColor = deterministicAccent(title)
            )
            if (isVideoItem) {
                if (seenVideoIds.add(videoId)) videos.add(track)
                // Also keep playable in tracks list if tracks is empty
                if (preferVideo && seenTrackIds.add(videoId)) tracks.add(track)
            } else {
                if (seenTrackIds.add(videoId)) tracks.add(track)
            }
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

    private fun searchDeezerRescue(query: String): Triple<List<TrackItem>, List<CollectionSummary>, List<ArtistSummary>> {
        val tracks = mutableListOf<TrackItem>()
        val albums = mutableListOf<CollectionSummary>()
        val artists = mutableListOf<ArtistSummary>()
        val seenAlb = HashSet<Long>()
        val seenArt = HashSet<String>()
        val json = getJson("$DEEZER_API/search?q=${encodeQuery(query)}&limit=25") ?: return Triple(tracks, albums, artists)
        val arr = json.optJSONArray("data") ?: return Triple(tracks, albums, artists)
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            if (title.isBlank()) continue
            val artObj = item.optJSONObject("artist")
            val artName = artObj?.optString("name").orEmpty().trim().ifBlank { "Artis" }
            val artPic = artObj?.optString("picture_big").orEmpty().ifBlank { artObj?.optString("picture_medium").orEmpty() }
            val albObj = item.optJSONObject("album")
            val albId = albObj?.optLong("id", 0L) ?: 0L
            val albTitle = albObj?.optString("title").orEmpty().trim()
            val cover = albObj?.optString("cover_xl").orEmpty()
                .ifBlank { albObj?.optString("cover_big").orEmpty() }
                .ifBlank { albObj?.optString("cover_medium").orEmpty() }
            val dur = item.optInt("duration", 0)
            val dzId = item.optLong("id", i.toLong())

            // Full track metadata — videoId resolved to full YouTube ExoPlayer stream on tap (ZERO 30s preview!)
            tracks.add(
                TrackItem(
                    id = "dz_$dzId",
                    videoId = "",
                    title = title,
                    artist = artName,
                    album = albTitle,
                    albumBrowseId = if (albId > 0L) "deezer_album:$albId" else "",
                    durationSec = dur,
                    coverUrl = cover,
                    accentColor = deterministicAccent(title)
                )
            )
            if (albId > 0L && albTitle.isNotBlank() && seenAlb.add(albId)) {
                albums.add(
                    CollectionSummary(
                        browseId = "deezer_album:$albId",
                        title = albTitle,
                        subtitle = artName,
                        coverUrl = cover,
                        isAlbum = true
                    )
                )
            }
            if (artName.isNotBlank() && seenArt.add(artName.lowercase())) {
                artists.add(
                    ArtistSummary(
                        browseId = "",
                        name = artName,
                        subscribersText = "Artis",
                        avatarUrl = artPic
                    )
                )
            }
        }
        return Triple(tracks, albums.take(12), artists.take(10))
    }

    /**
     * Resolves any rescued track (without 11-char YouTube videoId) into a full YouTube track
     * so playback ALWAYS uses full YouTube/ExoPlayer audio (NEVER 30-second previews).
     */
    suspend fun resolveFullTrack(track: TrackItem): TrackItem = withContext(Dispatchers.IO) {
        if (track.videoId.length == 11 || track.localUri.isNotBlank()) return@withContext track
        val q = "${track.title} ${track.artist}".trim()
        val ytmRes = searchAll(q, SearchFilter.SONGS)
        val best = ytmRes.tracks.firstOrNull { it.videoId.length == 11 }
            ?: searchYouTubeFallback(q).firstOrNull()
        if (best != null && best.videoId.length == 11) {
            track.copy(
                id = best.videoId,
                videoId = best.videoId,
                artistBrowseId = track.artistBrowseId.ifBlank { best.artistBrowseId },
                album = track.album.ifBlank { best.album },
                albumBrowseId = track.albumBrowseId.ifBlank { best.albumBrowseId },
                coverUrl = track.coverUrl.ifBlank { best.coverUrl },
                durationSec = if (track.durationSec > 0) track.durationSec else best.durationSec
            )
        } else {
            track
        }
    }

    /**
     * Locates an album browseId (`MPREb_...` or `deezer_album:...`) for tracks missing albumBrowseId.
     */
    suspend fun findAlbumBrowseIdForTrack(track: TrackItem): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (track.albumBrowseId.isNotBlank()) {
            return@withContext Pair(track.albumBrowseId, track.album.ifBlank { track.title })
        }
        val query = if (track.album.isNotBlank()) "${track.album} ${track.artist}" else "${track.title} ${track.artist}"
        val ytmAlbums = searchAll(query, SearchFilter.ALBUMS).albums
        val bestYtm = ytmAlbums.firstOrNull { it.browseId.startsWith("MPREb_") }
        if (bestYtm != null) return@withContext Pair(bestYtm.browseId, bestYtm.title)

        val dz = getJson("$DEEZER_API/search/track?q=${encodeQuery("${track.title} ${track.artist}")}&limit=1")
        val first = dz?.optJSONArray("data")?.optJSONObject(0)
        val albObj = first?.optJSONObject("album")
        val albId = albObj?.optLong("id", 0L) ?: 0L
        if (albId > 0L) {
            return@withContext Pair("deezer_album:$albId", albObj?.optString("title").orEmpty().ifBlank { track.title })
        }
        null
    }

    // =========================================================================
    // 3. FULL ARTIST PROFILE WITH 100% GUARANTEED HD BANNER (YTM + TheAudioDB + Deezer)
    // =========================================================================
    private data class ExternalArtistMeta(
        val fanartBannerUrl: String = "",
        val thumbUrl: String = "",
        val country: String = "",
        val debutYear: String = "",
        val genre: String = "",
        val bio: String = "",
        val followersText: String = ""
    )

    suspend fun fetchArtistProfile(browseId: String?, artistName: String): ArtistProfileData? =
        withContext(Dispatchers.IO) {
            var resolvedId = browseId?.trim().orEmpty()
            if (!resolvedId.startsWith("UC") && artistName.isNotBlank()) {
                val found = searchAll(artistName, SearchFilter.ARTISTS)
                resolvedId = found.artists.firstOrNull { it.browseId.startsWith("UC") }?.browseId.orEmpty()
            }

            val cacheKey = resolvedId.ifBlank { "name:${artistName.trim().lowercase()}" }
            val cached = artistCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.createdAtMs < 20 * 60_000L) {
                return@withContext cached.data
            }

            coroutineScope {
                val externalDeferred = async { fetchExternalArtistMeta(artistName) }

                if (resolvedId.isBlank()) {
                    val fallback = searchAll(artistName, SearchFilter.SONGS)
                    val ext = externalDeferred.await()
                    if (fallback.tracks.isEmpty() && ext.thumbUrl.isBlank()) return@coroutineScope null
                    val firstCover = fallback.tracks.firstOrNull()?.coverUrl.orEmpty()
                    val bestBanner = ext.fanartBannerUrl.ifBlank { ext.thumbUrl }.ifBlank { firstCover }
                    val bestAvatar = ext.thumbUrl.ifBlank { firstCover }
                    val prof = ArtistProfileData(
                        browseId = "",
                        name = artistName,
                        subscribersText = ext.followersText.ifBlank { "Profil Artis SpotyFlow" },
                        country = ext.country,
                        debutYear = ext.debutYear,
                        genre = ext.genre,
                        bannerUrl = bestBanner,
                        avatarUrl = bestAvatar,
                        description = ext.bio,
                        topSongs = fallback.tracks.take(20),
                        albums = fallback.albums
                    )
                    artistCache[cacheKey] = TimedCache(prof, System.currentTimeMillis())
                    return@coroutineScope prof
                }

                val payload = JSONObject().apply {
                    put("context", buildYtmContext())
                    put("browseId", resolvedId)
                }
                val root = postJson(YTM_BROWSE_URL, payload, isYtm = true)
                val ext = externalDeferred.await()
                if (root == null) return@coroutineScope null

                // Extract from ALL 3 YouTube Music artist header renderer types
                val headerObj = root.optJSONObject("header")
                val header = headerObj?.optJSONObject("musicImmersiveHeaderRenderer")
                    ?: headerObj?.optJSONObject("musicVisualHeaderRenderer")
                    ?: headerObj?.optJSONObject("musicHeaderRenderer")

                val name = joinRuns(header?.optJSONObject("title")?.optJSONArray("runs"))
                    .ifBlank { artistName }
                val ytmDescription = joinRuns(header?.optJSONObject("description")?.optJSONArray("runs")).trim()
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

                val rawBannerImg = extractBestThumbnail(header?.optJSONObject("thumbnail"))
                val rawForegroundImg = extractBestThumbnail(header?.optJSONObject("foregroundThumbnail"))
                val ytmBannerHd = upgradeCoverUrl(rawBannerImg, 1440, 600)
                val ytmAvatarHd = upgradeCoverUrl(rawForegroundImg.ifBlank { rawBannerImg }, 600, 600)

                val topSongs = mutableListOf<TrackItem>()
                val albums = mutableListOf<CollectionSummary>()
                val singles = mutableListOf<CollectionSummary>()
                val related = mutableListOf<ArtistSummary>()
                val seenTracks = HashSet<String>()
                val seenCols = HashSet<String>()
                val seenArtists = HashSet<String>()

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

                if (topSongs.isEmpty() && name.isNotBlank()) {
                    val extra = searchAll(name, SearchFilter.SONGS)
                    topSongs.addAll(extra.tracks.take(20))
                }

                // 100% Guaranteed HD Banner: YTM wide -> TheAudioDB Fanart/Banner -> Deezer XL -> Top song cover
                val guaranteedBanner = listOf(
                    ytmBannerHd,
                    ext.fanartBannerUrl,
                    ext.thumbUrl,
                    topSongs.firstOrNull()?.coverUrl.orEmpty()
                ).firstOrNull { it.isNotBlank() }.orEmpty()

                val guaranteedAvatar = listOf(
                    ytmAvatarHd,
                    ext.thumbUrl,
                    guaranteedBanner
                ).firstOrNull { it.isNotBlank() }.orEmpty()

                val profile = ArtistProfileData(
                    browseId = resolvedId,
                    name = name,
                    subscribersText = subsText.ifBlank { monthlyListeners }.ifBlank { ext.followersText },
                    monthlyListenersText = monthlyListeners,
                    country = ext.country,
                    debutYear = ext.debutYear,
                    genre = ext.genre,
                    bannerUrl = guaranteedBanner,
                    avatarUrl = guaranteedAvatar,
                    description = ytmDescription.ifBlank { ext.bio },
                    topSongs = topSongs,
                    albums = albums,
                    singles = singles,
                    relatedArtists = related
                )
                artistCache[cacheKey] = TimedCache(profile, System.currentTimeMillis())
                profile
            }
        }

    private suspend fun fetchExternalArtistMeta(artistName: String): ExternalArtistMeta = coroutineScope {
        val clean = artistName.trim()
        if (clean.isEmpty()) return@coroutineScope ExternalArtistMeta()
        val encoded = encodeQuery(clean)

        val audioDbDef = async {
            val json = getJson("$AUDIODB_API/search.php?s=$encoded")
            json?.optJSONArray("artists")?.optJSONObject(0)
        }
        val deezerDef = async {
            val json = getJson("$DEEZER_API/search/artist?q=$encoded&limit=1")
            json?.optJSONArray("data")?.optJSONObject(0)
        }

        val adb = audioDbDef.await()
        val dz = deezerDef.await()

        val fanart = listOf(
            adb?.optString("strArtistFanart").orEmpty(),
            adb?.optString("strArtistFanart2").orEmpty(),
            adb?.optString("strArtistBanner").orEmpty(),
            dz?.optString("picture_xl").orEmpty()
        ).firstOrNull { it.startsWith("http") }.orEmpty()

        val thumb = listOf(
            dz?.optString("picture_xl").orEmpty(),
            adb?.optString("strArtistThumb").orEmpty(),
            dz?.optString("picture_big").orEmpty()
        ).firstOrNull { it.startsWith("http") }.orEmpty()

        val country = adb?.optString("strCountry").orEmpty().takeIf { it != "null" && it.isNotBlank() }.orEmpty()
        val debutYear = adb?.optString("intFormedYear").orEmpty()
            .takeIf { it.matches(Regex("^(19|20)\\d{2}$")) }.orEmpty()
        val genre = adb?.optString("strGenre").orEmpty()
            .takeIf { it != "null" && it.isNotBlank() }.orEmpty()
        val bio = adb?.optString("strBiographyID").orEmpty()
            .takeIf { it != "null" && it.length > 20 }
            ?: adb?.optString("strBiographyEN").orEmpty().takeIf { it != "null" && it.length > 20 }.orEmpty()
        val fans = dz?.optLong("nb_fan", 0L) ?: 0L
        val followersText = if (fans > 0L) formatFollowerCount(fans) else ""

        ExternalArtistMeta(
            fanartBannerUrl = fanart,
            thumbUrl = thumb,
            country = country,
            debutYear = debutYear,
            genre = genre,
            bio = bio,
            followersText = followersText
        )
    }

    // =========================================================================
    // 4. FULL ALBUM & PLAYLIST DETAIL (MPREb_..., VL..., PL..., deezer_album:...)
    // =========================================================================
    suspend fun fetchCollectionDetail(browseId: String, fallbackTitle: String = ""): CollectionDetailData? =
        withContext(Dispatchers.IO) {
            val cleanId = browseId.trim()
            if (cleanId.isEmpty()) return@withContext null

            val cached = collectionCache[cleanId]
            if (cached != null && System.currentTimeMillis() - cached.createdAtMs < 20 * 60_000L) {
                return@withContext cached.data
            }

            // Support Deezer-discovered albums with full numbered tracklist
            if (cleanId.startsWith("deezer_album:")) {
                val dzAlbumId = cleanId.removePrefix("deezer_album:")
                val dzDetail = fetchDeezerAlbumDetail(dzAlbumId, fallbackTitle)
                if (dzDetail != null) collectionCache[cleanId] = TimedCache(dzDetail, System.currentTimeMillis())
                return@withContext dzDetail
            }

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
            val secondSubRuns = detailHeader?.optJSONObject("secondSubtitle")?.optJSONArray("runs")
            val strapRuns = detailHeader?.optJSONObject("straplineTextOne")?.optJSONArray("runs")
            val subtitle = joinRuns(subRuns).trim()
            val secondSubtitle = joinRuns(secondSubRuns).trim()
            val artistName = extractPrimaryArtist(strapRuns).ifBlank { extractPrimaryArtist(subRuns) }
            val artistId = extractArtistBrowseId(strapRuns).ifBlank { extractArtistBrowseId(subRuns) }
            val description = joinRuns(detailHeader?.optJSONObject("description")?.optJSONArray("runs")).trim()
            val coverUrl = upgradeCoverUrl(
                extractBestThumbnail(detailHeader?.optJSONObject("thumbnail")),
                800
            )
            val isAlbum = normalizedId.startsWith("MPREb_")

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

            // Enrich album metadata (record label, release date, genre, copyright) asynchronously for albums
            var recordLabel = ""
            var releaseDate = ""
            var copyright = ""
            var genre = ""
            if (isAlbum && title.isNotBlank()) {
                val meta = fetchAlbumExtraMeta(title, artistName)
                recordLabel = meta.first
                releaseDate = meta.second
                genre = meta.third
                if (recordLabel.isNotBlank()) {
                    val yr = extractYear(releaseDate).ifBlank { extractYear(subtitle) }
                    copyright = if (yr.isNotBlank()) "℗ $yr $recordLabel" else "℗ $recordLabel"
                }
            }

            val detail = CollectionDetailData(
                browseId = normalizedId,
                title = title,
                subtitle = listOf(subtitle, secondSubtitle).filter { it.isNotBlank() }.joinToString(" • "),
                artistName = artistName,
                artistBrowseId = artistId,
                year = extractYear(releaseDate).ifBlank { extractYear(subtitle) },
                releaseDate = releaseDate,
                recordLabel = recordLabel,
                copyright = copyright,
                genre = genre,
                description = description,
                coverUrl = coverUrl.ifBlank { tracks.firstOrNull()?.coverUrl.orEmpty() },
                totalDurationText = formatTotalDuration(tracks),
                isAlbum = isAlbum,
                tracks = tracks
            )
            collectionCache[cleanId] = TimedCache(detail, System.currentTimeMillis())
            detail
        }

    private fun fetchDeezerAlbumDetail(albumId: String, fallbackTitle: String): CollectionDetailData? {
        val json = getJson("$DEEZER_API/album/$albumId") ?: return null
        val title = json.optString("title").ifBlank { fallbackTitle.ifBlank { "Album" } }
        val artistName = json.optJSONObject("artist")?.optString("name").orEmpty()
        val cover = json.optString("cover_xl").ifBlank { json.optString("cover_big") }
        val releaseDate = json.optString("release_date")
        val label = json.optString("label")
        val genre = json.optJSONObject("genres")?.optJSONArray("data")?.optJSONObject(0)?.optString("name").orEmpty()
        val yr = extractYear(releaseDate)

        val tracks = mutableListOf<TrackItem>()
        val trkArr = json.optJSONObject("tracks")?.optJSONArray("data")
        if (trkArr != null) {
            for (i in 0 until trkArr.length()) {
                val t = trkArr.optJSONObject(i) ?: continue
                val tTitle = t.optString("title").trim()
                if (tTitle.isBlank()) continue
                val tArtist = t.optJSONObject("artist")?.optString("name").orEmpty().ifBlank { artistName }
                val dur = t.optInt("duration", 0)
                val dzId = t.optLong("id", i.toLong())
                tracks.add(
                    TrackItem(
                        id = "dz_$dzId",
                        videoId = "",
                        title = tTitle,
                        artist = tArtist,
                        album = title,
                        albumBrowseId = "deezer_album:$albumId",
                        durationSec = dur,
                        coverUrl = cover,
                        accentColor = deterministicAccent(tTitle)
                    )
                )
            }
        }
        return CollectionDetailData(
            browseId = "deezer_album:$albumId",
            title = title,
            subtitle = if (yr.isNotBlank()) "Album • $yr" else "Album",
            artistName = artistName,
            year = yr,
            releaseDate = releaseDate,
            recordLabel = label,
            copyright = if (label.isNotBlank()) "℗ $yr $label".trim() else "",
            genre = genre,
            coverUrl = cover,
            totalDurationText = formatTotalDuration(tracks),
            isAlbum = true,
            tracks = tracks
        )
    }

    private fun fetchAlbumExtraMeta(albumTitle: String, artistName: String): Triple<String, String, String> {
        return try {
            val q = encodeQuery("$albumTitle $artistName".trim())
            val search = getJson("$DEEZER_API/search/album?q=$q&limit=1")
            val id = search?.optJSONArray("data")?.optJSONObject(0)?.optLong("id", 0L) ?: 0L
            if (id <= 0L) return Triple("", "", "")
            val full = getJson("$DEEZER_API/album/$id") ?: return Triple("", "", "")
            val label = full.optString("label").orEmpty()
            val rel = full.optString("release_date").orEmpty()
            val genre = full.optJSONObject("genres")?.optJSONArray("data")?.optJSONObject(0)?.optString("name").orEmpty()
            Triple(label, rel, genre)
        } catch (_: Throwable) {
            Triple("", "", "")
        }
    }

    // =========================================================================
    // 5. OFFICIAL TRACK CREDITS & INFO (YTM Next Description + Deezer + iTunes)
    // =========================================================================
    suspend fun fetchTrackCredits(track: TrackItem, activeStreamQuality: String): TrackCreditsData =
        withContext(Dispatchers.IO) {
            val key = "${track.videoId.ifBlank { track.id }}::${track.title.lowercase()}"
            val cached = creditsCache[key]
            if (cached != null && System.currentTimeMillis() - cached.createdAtMs < 30 * 60_000L) {
                return@withContext cached.data.copy(streamQualityLabel = activeStreamQuality)
            }

            coroutineScope {
                val ytDescDeferred = async {
                    if (track.videoId.length == 11) fetchYoutubeDescriptionCredits(track.videoId) else emptyMap()
                }
                val deezerDeferred = async { fetchDeezerTrackMeta(track.title, track.artist) }
                val itunesDeferred = async { fetchItunesTrackMeta(track.title, track.artist) }

                val ytMap = ytDescDeferred.await()
                val dz = deezerDeferred.await()
                val itunes = itunesDeferred.await()

                val performers = LinkedHashSet<String>()
                performers.add(track.artist)
                ytMap["performers"]?.split(",")?.forEach { if (it.isNotBlank()) performers.add(it.trim()) }
                dz["performers"]?.split(",")?.forEach { if (it.isNotBlank()) performers.add(it.trim()) }

                val composers = ytMap["composers"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
                val lyricists = ytMap["lyricists"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
                val producers = ytMap["producers"]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()

                val label = ytMap["label"].orEmpty()
                    .ifBlank { dz["label"].orEmpty() }
                val releaseDate = ytMap["releaseDate"].orEmpty()
                    .ifBlank { dz["releaseDate"].orEmpty() }
                    .ifBlank { itunes["releaseDate"].orEmpty() }
                val copyright = ytMap["copyright"].orEmpty()
                    .ifBlank { itunes["copyright"].orEmpty() }
                    .ifBlank {
                        if (label.isNotBlank()) {
                            val yr = extractYear(releaseDate)
                            if (yr.isNotBlank()) "℗ $yr $label" else "℗ $label"
                        } else ""
                    }
                val genre = dz["genre"].orEmpty().ifBlank { itunes["genre"].orEmpty() }
                val bpm = dz["bpm"]?.toIntOrNull() ?: 0
                val resolvedAlbum = track.album.ifBlank { dz["album"].orEmpty() }.ifBlank { itunes["album"].orEmpty() }
                val resolvedAlbumId = track.albumBrowseId.ifBlank { dz["albumBrowseId"].orEmpty() }

                val credits = TrackCreditsData(
                    videoId = track.videoId,
                    title = track.title,
                    artist = track.artist,
                    artistBrowseId = track.artistBrowseId,
                    album = resolvedAlbum,
                    albumBrowseId = resolvedAlbumId,
                    coverUrl = track.coverUrl,
                    performers = performers.toList(),
                    composers = composers,
                    lyricists = lyricists,
                    producers = producers,
                    recordLabel = label,
                    copyright = copyright,
                    releaseDate = releaseDate,
                    genre = genre,
                    bpm = bpm,
                    durationSec = track.durationSec,
                    streamQualityLabel = activeStreamQuality,
                    sourceName = if (ytMap.isNotEmpty()) "YouTube Music & Deezer" else "SpotyFlow Metadata"
                )
                creditsCache[key] = TimedCache(credits, System.currentTimeMillis())
                credits
            }
        }

    private fun fetchYoutubeDescriptionCredits(videoId: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val payload = JSONObject().apply {
            put("context", buildYtWebContext())
            put("videoId", videoId)
        }
        val root = postJson("https://www.youtube.com/youtubei/v1/next?prettyPrint=false", payload, isYtm = false)
            ?: return out

        // Extract description text from videoSecondaryInfoRenderer or engagementPanels
        val rawDesc = buildString {
            for (sec in collectObjectsByKey(root, "attributedDescription")) {
                val c = sec.optString("content")
                if (c.isNotBlank()) appendLine(c)
            }
            for (sec in collectObjectsByKey(root, "description")) {
                val r = joinRuns(sec.optJSONArray("runs"))
                if (r.isNotBlank()) appendLine(r)
            }
        }
        if (rawDesc.isBlank()) return out

        val composers = mutableListOf<String>()
        val lyricists = mutableListOf<String>()
        val producers = mutableListOf<String>()
        val performers = mutableListOf<String>()

        rawDesc.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Provided to YouTube by ", ignoreCase = true) -> {
                    out["label"] = trimmed.removePrefix("Provided to YouTube by ").trim()
                }
                trimmed.startsWith("℗") || trimmed.startsWith("©") -> {
                    if (!out.containsKey("copyright")) out["copyright"] = trimmed
                }
                trimmed.startsWith("Released on:", ignoreCase = true) -> {
                    out["releaseDate"] = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("Composer:", ignoreCase = true) ||
                    trimmed.startsWith("Music:", ignoreCase = true) ||
                    trimmed.startsWith("Songwriter:", ignoreCase = true) -> {
                    composers.add(trimmed.substringAfter(":").trim())
                }
                trimmed.startsWith("Lyricist:", ignoreCase = true) ||
                    trimmed.startsWith("Author:", ignoreCase = true) -> {
                    lyricists.add(trimmed.substringAfter(":").trim())
                }
                trimmed.startsWith("Producer:", ignoreCase = true) ||
                    trimmed.startsWith("Music Publisher:", ignoreCase = true) -> {
                    producers.add(trimmed.substringAfter(":").trim())
                }
                trimmed.startsWith("Main Artist:", ignoreCase = true) ||
                    trimmed.startsWith("Vocals:", ignoreCase = true) ||
                    trimmed.startsWith("Featured Artist:", ignoreCase = true) -> {
                    performers.add(trimmed.substringAfter(":").trim())
                }
            }
        }

        if (composers.isNotEmpty()) out["composers"] = composers.distinct().joinToString(", ")
        if (lyricists.isNotEmpty()) out["lyricists"] = lyricists.distinct().joinToString(", ")
        if (producers.isNotEmpty()) out["producers"] = producers.distinct().joinToString(", ")
        if (performers.isNotEmpty()) out["performers"] = performers.distinct().joinToString(", ")
        return out
    }

    private fun fetchDeezerTrackMeta(title: String, artist: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        try {
            val search = getJson("$DEEZER_API/search/track?q=${encodeQuery("$title $artist")}&limit=1") ?: return out
            val first = search.optJSONArray("data")?.optJSONObject(0) ?: return out
            val trackId = first.optLong("id", 0L)
            val albId = first.optJSONObject("album")?.optLong("id", 0L) ?: 0L
            if (albId > 0L) {
                out["album"] = first.optJSONObject("album")?.optString("title").orEmpty()
                out["albumBrowseId"] = "deezer_album:$albId"
            }

            if (trackId > 0L) {
                val fullTrack = getJson("$DEEZER_API/track/$trackId")
                val bpm = fullTrack?.optDouble("bpm", 0.0)?.toInt() ?: 0
                if (bpm > 0) out["bpm"] = bpm.toString()
                val rel = fullTrack?.optString("release_date").orEmpty()
                if (rel.isNotBlank()) out["releaseDate"] = rel
                val contribs = fullTrack?.optJSONArray("contributors")
                if (contribs != null && contribs.length() > 0) {
                    val names = (0 until contribs.length()).mapNotNull {
                        contribs.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() }
                    }
                    if (names.isNotEmpty()) out["performers"] = names.distinct().joinToString(", ")
                }
            }

            if (albId > 0L) {
                val fullAlb = getJson("$DEEZER_API/album/$albId")
                val label = fullAlb?.optString("label").orEmpty()
                if (label.isNotBlank()) out["label"] = label
                val genre = fullAlb?.optJSONObject("genres")?.optJSONArray("data")?.optJSONObject(0)?.optString("name").orEmpty()
                if (genre.isNotBlank()) out["genre"] = genre
                if (!out.containsKey("releaseDate")) {
                    val rel = fullAlb?.optString("release_date").orEmpty()
                    if (rel.isNotBlank()) out["releaseDate"] = rel
                }
            }
        } catch (_: Throwable) {
            // Non-fatal
        }
        return out
    }

    private fun fetchItunesTrackMeta(title: String, artist: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        try {
            val json = getJson("$ITUNES_API/search?term=${encodeQuery("$title $artist")}&entity=song&limit=1") ?: return out
            val first = json.optJSONArray("results")?.optJSONObject(0) ?: return out
            val genre = first.optString("primaryGenreName").orEmpty()
            if (genre.isNotBlank()) out["genre"] = genre
            val rel = first.optString("releaseDate").orEmpty().take(10)
            if (rel.isNotBlank()) out["releaseDate"] = rel
            val album = first.optString("collectionName").orEmpty()
            if (album.isNotBlank()) out["album"] = album
            val copy = first.optString("copyright").orEmpty()
            if (copy.isNotBlank()) out["copyright"] = copy
        } catch (_: Throwable) {
            // Non-fatal
        }
        return out
    }

    // =========================================================================
    // 6. HOME MOOD FEEDS & RADIO RELATED TRACKS
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
    // 7. YOUTUBE MUSIC OFFICIAL LYRICS FALLBACK (next -> browse lyrics)
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

    private fun collectFlexRuns(item: JSONObject): JSONArray {
        val merged = JSONArray()
        val flexCols = item.optJSONArray("flexColumns") ?: return merged
        for (c in 1 until flexCols.length()) {
            val runs = flexCols.optJSONObject(c)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs") ?: continue
            for (i in 0 until runs.length()) {
                runs.opt(i)?.let { merged.put(it) }
            }
        }
        return merged
    }

    private fun extractMenuBrowseId(item: JSONObject, prefix: String): String {
        val menuItems = collectObjectsByKey(item.optJSONObject("menu"), "menuNavigationItemRenderer")
        for (mi in menuItems) {
            val bId = mi.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId").orEmpty()
            if (bId.startsWith(prefix)) return bId
        }
        return ""
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
        val nested = collectObjectsByKey(container, "thumbnail")
        for (n in nested) {
            val arr = n.optJSONArray("thumbnails")
            if (arr != null && arr.length() > 0) {
                val u = arr.optJSONObject(arr.length() - 1)?.optString("url").orEmpty()
                if (u.isNotBlank()) return u
            }
        }
        return ""
    }

    private fun upgradeCoverUrl(url: String, w: Int, h: Int = w): String {
        if (url.isBlank()) return ""
        return if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
            if (url.contains("=w")) {
                url.replace(Regex("=w\\d+-h\\d+[^\\s]*"), "=w$w-h$h-l90-rj")
            } else if (url.contains("=s")) {
                url.replace(Regex("=s\\d+[^\\s]*"), "=w$w-h$h-l90-rj")
            } else {
                "$url=w$w-h$h-l90-rj"
            }
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

    private fun formatFollowerCount(count: Long): String {
        return when {
            count >= 1_000_000L -> String.format(java.util.Locale.US, "%.1f jt pengikut", count / 1_000_000.0).replace('.', ',')
            count >= 1_000L -> String.format(java.util.Locale.US, "%.1f rb pengikut", count / 1_000.0).replace('.', ',')
            else -> "$count pengikut"
        }
    }
}
