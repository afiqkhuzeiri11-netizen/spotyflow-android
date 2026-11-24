package com.spotyflow.android.data.remote

import com.spotyflow.android.data.local.OfflineDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class ResolvedStream(
    val videoId: String,
    val audioUrl: String,
    val videoUrl: String = "",
    val durationSec: Int = 0,
    val resolvedAtMs: Long = System.currentTimeMillis()
)

/**
 * 2-Layer Anti-403 Fast Stream Resolver (< 0.7s on-device):
 * - RAM Cache (2.5h TTL, max 80 entries)
 * - Layer #1: Direct InnerTube ANDROID_VR (1.61.48) extracting best audio + muxed MP4 video
 * - Layer #2: Automatic fallback to NewPipeExtractor v0.26.0 using strictly stream.content
 */
object FastStreamResolver {
    private const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private const val VR_UA = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    private const val CACHE_TTL_MS = 150L * 60_000L // 2.5 hours
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val cache = ConcurrentHashMap<String, ResolvedStream>()
    private val newPipeReady = AtomicBoolean(false)
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getCached(videoId: String): ResolvedStream? {
        val hit = cache[videoId] ?: return null
        if (System.currentTimeMillis() - hit.resolvedAtMs > CACHE_TTL_MS) {
            cache.remove(videoId)
            return null
        }
        return hit
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    fun prefetchAsync(videoId: String) {
        if (videoId.length != 11 || getCached(videoId) != null) return
        prefetchScope.launch {
            resolve(videoId, forceFallback = false)
        }
    }

    suspend fun resolve(
        videoId: String,
        forceFallback: Boolean = false,
        skipOfflineLookup: Boolean = false
    ): ResolvedStream? =
        withContext(Dispatchers.IO) {
            if (videoId.length != 11) return@withContext null

            // Priority #0 (0ms): Local Offline M4A File — instant playback without internet!
            if (!skipOfflineLookup) {
                val offlineUri = OfflineDownloadManager.getOfflineFileUri(videoId)
                if (!offlineUri.isNullOrBlank()) {
                    return@withContext ResolvedStream(
                        videoId = videoId,
                        audioUrl = offlineUri,
                        videoUrl = ""
                    )
                }
            }

            if (!forceFallback) {
                getCached(videoId)?.let { return@withContext it }
            }

            // Layer #1: Direct InnerTube ANDROID_VR (~300-550ms)
            if (!forceFallback) {
                val fast = resolveViaAndroidVr(videoId)
                if (fast != null) {
                    putCache(fast)
                    return@withContext fast
                }
            }

            // Layer #2: Automatic NewPipeExtractor v0.26.0 Fallback (strictly stream.content!)
            val fallback = resolveViaNewPipe(videoId)
            if (fallback != null) {
                putCache(fallback)
                return@withContext fallback
            }

            null
        }

    private fun putCache(stream: ResolvedStream) {
        if (cache.size >= 80) {
            val oldest = cache.entries.minByOrNull { it.value.resolvedAtMs }?.key
            if (oldest != null) cache.remove(oldest)
        }
        cache[stream.videoId] = stream
    }

    private fun resolveViaAndroidVr(videoId: String): ResolvedStream? {
        return try {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.61.48")
                        put("deviceMake", "Oculus")
                        put("deviceModel", "Quest 3")
                        put("osName", "Android")
                        put("osVersion", "12L")
                        put("androidSdkVersion", 32)
                        put("hl", "id")
                        put("gl", "ID")
                    })
                })
            }

            val req = Request.Builder()
                .url(PLAYER_URL)
                .header("User-Agent", VR_UA)
                .header("X-YouTube-Client-Name", "28")
                .header("X-YouTube-Client-Version", "1.61.48")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            SpotyHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val raw = resp.body?.string() ?: return null
                val root = JSONObject(raw)
                val streamingData = root.optJSONObject("streamingData") ?: return null
                val durationSec = root.optJSONObject("videoDetails")
                    ?.optString("lengthSeconds")?.toIntOrNull() ?: 0

                // Pick best audio from adaptiveFormats (prefer itag 251 Opus / 140 M4A / highest bitrate)
                var bestAudioUrl = ""
                var bestAudioScore = -1
                val adaptive = streamingData.optJSONArray("adaptiveFormats")
                if (adaptive != null) {
                    for (i in 0 until adaptive.length()) {
                        val fmt = adaptive.optJSONObject(i) ?: continue
                        val mime = fmt.optString("mimeType")
                        val url = fmt.optString("url")
                        if (!mime.startsWith("audio/") || url.isBlank()) continue
                        val itag = fmt.optInt("itag", 0)
                        val bitrate = fmt.optInt("bitrate", 0)
                        val score = when (itag) {
                            251 -> 1_000_000 + bitrate
                            140 -> 900_000 + bitrate
                            250 -> 700_000 + bitrate
                            else -> bitrate
                        }
                        if (score > bestAudioScore) {
                            bestAudioScore = score
                            bestAudioUrl = url
                        }
                    }
                }

                // Pick best muxed MP4 video (itag 22 HD / itag 18 SD) for Audio | Video switch
                var bestVideoUrl = ""
                val formats = streamingData.optJSONArray("formats")
                if (formats != null) {
                    for (i in 0 until formats.length()) {
                        val fmt = formats.optJSONObject(i) ?: continue
                        val url = fmt.optString("url")
                        val itag = fmt.optInt("itag", 0)
                        if (url.isBlank()) continue
                        if (itag == 22) {
                            bestVideoUrl = url
                            break
                        } else if (itag == 18 || bestVideoUrl.isBlank()) {
                            bestVideoUrl = url
                        }
                    }
                }

                if (bestAudioUrl.isBlank()) bestAudioUrl = bestVideoUrl
                if (bestAudioUrl.isBlank()) return null

                ResolvedStream(
                    videoId = videoId,
                    audioUrl = bestAudioUrl,
                    videoUrl = bestVideoUrl,
                    durationSec = durationSec
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureNewPipeInitialized() {
        if (newPipeReady.compareAndSet(false, true)) {
            try {
                NewPipe.init(OkHttpNewPipeDownloader())
            } catch (_: Throwable) {
                // Already initialized
            }
        }
    }

    private fun resolveViaNewPipe(videoId: String): ResolvedStream? {
        return try {
            ensureNewPipeInitialized()
            val url = "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)

            val bestAudio = info.audioStreams
                .filter { !it.content.isNullOrBlank() }
                .maxByOrNull { it.averageBitrate }
            val audioUrl = bestAudio?.content.orEmpty()

            val bestVideo = info.videoStreams
                .filter { !it.content.isNullOrBlank() }
                .maxByOrNull { it.height }
            val videoUrl = bestVideo?.content.orEmpty()

            val finalAudio = audioUrl.ifBlank { videoUrl }
            if (finalAudio.isBlank()) return null

            ResolvedStream(
                videoId = videoId,
                audioUrl = finalAudio,
                videoUrl = videoUrl,
                durationSec = info.duration.toInt().coerceAtLeast(0)
            )
        } catch (_: Throwable) {
            null
        }
    }

    private class OkHttpNewPipeDownloader : Downloader() {
        override fun execute(request: NpRequest): NpResponse {
            val method = request.httpMethod()
            val url = request.url()
            val data = request.dataToSend()
            val builder = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
                )

            request.headers().forEach { (key, values) ->
                values.forEach { v -> builder.addHeader(key, v) }
            }

            when (method) {
                "POST" -> builder.post((data ?: ByteArray(0)).toRequestBody(null))
                "HEAD" -> builder.head()
                else -> builder.get()
            }

            SpotyHttpClient.client.newCall(builder.build()).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                return NpResponse(
                    resp.code,
                    resp.message,
                    resp.headers.toMultimap(),
                    bodyStr,
                    resp.request.url.toString()
                )
            }
        }
    }
}
