package com.spotyflow.android.data.remote

import com.spotyflow.android.data.LyricLine
import com.spotyflow.android.data.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class LyricsResult(
    val syncedLines: List<LyricLine> = emptyList(),
    val plainLines: List<LyricLine> = emptyList(),
    val source: String = "none"
) {
    val activeList: List<LyricLine>
        get() = if (syncedLines.isNotEmpty()) syncedLines else plainLines

    val isSynced: Boolean
        get() = syncedLines.isNotEmpty()
}

/**
 * Synced Lyrics Repository:
 * - Smart metadata sanitizer (strips Official Video, Lyrics, Topic, VEVO tags)
 * - Guards against mismatched lyrics on long DJ/Remix tracks (> 480s)
 * - Primary: LRCLIB (/api/get -> /api/search with title similarity >= 0.55 & duration delta <= 20s)
 * - Fallback: Official YouTube Music Lyrics (InnerTube next -> browse lyrics)
 */
object LyricsRepository {
    private val cache = ConcurrentHashMap<String, LyricsResult>()

    suspend fun fetchLyrics(track: TrackItem): LyricsResult {
        val key = track.videoId.ifBlank { track.id }
        cache[key]?.let { return it }

        // Guard: skip mismatched auto-lyrics for long DJ / non-stop remix sets (> 480s)
        val isLongDjMix = track.durationSec > 480 &&
            Regex("dj|remix|nonstop|full album|medley", RegexOption.IGNORE_CASE).containsMatchIn(track.title)
        if (isLongDjMix) {
            return LyricsResult(source = "skipped_long_mix")
        }

        val cleanTitle = sanitizeTitle(track.title)
        val cleanArtist = sanitizeArtist(track.artist)
        val result = fetchInternal(cleanTitle, cleanArtist, track.durationSec, track.videoId)
        if (result.activeList.isNotEmpty()) {
            cache[key] = result
        }
        return result
    }

    suspend fun searchManual(title: String, artist: String): LyricsResult {
        return fetchInternal(sanitizeTitle(title), sanitizeArtist(artist), 0, "")
    }

    private suspend fun fetchInternal(
        title: String,
        artist: String,
        durationSec: Int,
        videoId: String
    ): LyricsResult = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext LyricsResult()

        // 1. LRCLIB exact /api/get
        try {
            val getUrl = buildString {
                append("https://lrclib.net/api/get?track_name=")
                append(enc(title))
                if (artist.isNotBlank()) {
                    append("&artist_name=")
                    append(enc(artist))
                }
                if (durationSec > 20) {
                    append("&duration=")
                    append(durationSec)
                }
            }
            val exact = getJsonObj(getUrl)
            if (exact != null) {
                val parsed = parseLrcLibItem(exact)
                if (parsed.activeList.isNotEmpty()) return@withContext parsed
            }
        } catch (_: Throwable) {
            // Fall through to LRCLIB search
        }

        // 2. LRCLIB /api/search with strict title similarity >= 0.55 & duration delta <= 20s
        try {
            val q = "$artist $title".trim()
            val arr = getJsonArr("https://lrclib.net/api/search?q=${enc(q)}")
            if (arr != null) {
                val wantRemix = Regex("dj|remix|slowed|reverb|sped", RegexOption.IGNORE_CASE).containsMatchIn(title)
                var bestObj: JSONObject? = null
                var bestScore = -1.0
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val candTitle = item.optString("trackName").ifBlank { item.optString("name") }
                    if (!wantRemix && Regex("dj|remix|slowed|reverb", RegexOption.IGNORE_CASE).containsMatchIn(candTitle)) {
                        continue
                    }
                    val sim = titleSimilarity(title, candTitle)
                    if (sim < 0.55) continue
                    val candDur = item.optDouble("duration", 0.0).toInt()
                    if (durationSec > 30 && candDur > 30 && abs(candDur - durationSec) > 20) continue
                    val hasSynced = !item.optString("syncedLyrics").isNullOrBlank()
                    val score = sim * 100.0 + (if (hasSynced) 30.0 else 0.0) -
                        (if (durationSec > 30 && candDur > 30) abs(candDur - durationSec).toDouble() else 0.0)
                    if (score > bestScore) {
                        bestScore = score
                        bestObj = item
                    }
                }
                if (bestObj != null) {
                    val parsed = parseLrcLibItem(bestObj)
                    if (parsed.activeList.isNotEmpty()) return@withContext parsed
                }
            }
        } catch (_: Throwable) {
            // Fall through to YTM lyrics
        }

        // 3. Fallback: YouTube Music official lyrics
        if (videoId.length == 11) {
            val ytmText = InnerTubeMusicClient.fetchYouTubeMusicLyrics(videoId)
            if (!ytmText.isNullOrBlank()) {
                val plain = ytmText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { LyricLine(timeMs = -1L, text = it) }
                if (plain.isNotEmpty()) {
                    return@withContext LyricsResult(plainLines = plain, source = "ytm")
                }
            }
        }

        LyricsResult(source = "none")
    }

    private fun parseLrcLibItem(obj: JSONObject): LyricsResult {
        val syncedRaw = obj.optString("syncedLyrics", "").trim()
        val plainRaw = obj.optString("plainLyrics", "").trim()
        val synced = if (syncedRaw.isNotEmpty()) parseLrc(syncedRaw) else emptyList()
        val plain = if (plainRaw.isNotEmpty()) {
            plainRaw.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(-1L, it) }
        } else {
            emptyList()
        }
        return LyricsResult(syncedLines = synced, plainLines = plain, source = "lrclib")
    }

    fun parseLrc(raw: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val tagRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]")
        for (row in raw.lines()) {
            val matches = tagRegex.findAll(row).toList()
            if (matches.isEmpty()) continue
            val text = row.replace(tagRegex, "").trim()
            if (text.isEmpty()) continue
            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val fracRaw = m.groupValues[3].padEnd(3, '0').take(3)
                val ms = fracRaw.toLongOrNull() ?: 0L
                lines.add(LyricLine(timeMs = (min * 60_000L) + (sec * 1000L) + ms, text = text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun sanitizeTitle(raw: String): String {
        return raw
            .replace(Regex("\\((official|lyric|lyrics|audio|mv|music video|visualizer|hd|4k)[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[(official|lyric|lyrics|audio|mv|music video|visualizer|hd|4k)[^]]*]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun sanitizeArtist(raw: String): String {
        return raw
            .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("VEVO$", RegexOption.IGNORE_CASE), "")
            .split(",", "&", "feat.", "ft.")
            .firstOrNull()
            ?.trim()
            .orEmpty()
    }

    private fun titleSimilarity(a: String, b: String): Double {
        fun tokens(s: String): Set<String> = s.lowercase()
            .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .toSet()

        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val hits = ta.count { tb.contains(it) }
        return hits.toDouble() / maxOf(ta.size, tb.size).toDouble()
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun getJsonObj(url: String): JSONObject? {
        val req = Request.Builder().url(url).header("User-Agent", "SpotyFlow/1.1.0").get().build()
        SpotyHttpClient.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            return JSONObject(body)
        }
    }

    private fun getJsonArr(url: String): JSONArray? {
        val req = Request.Builder().url(url).header("User-Agent", "SpotyFlow/1.1.0").get().build()
        SpotyHttpClient.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            return JSONArray(body)
        }
    }
}
