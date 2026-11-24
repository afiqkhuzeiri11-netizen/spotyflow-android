package com.spotyflow.android.data.local

import android.content.Context
import android.net.Uri
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.deterministicAccent
import com.spotyflow.android.data.remote.FastStreamResolver
import com.spotyflow.android.data.remote.SpotyHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Full Offline M4A Download Engine:
 * - Downloads best audio stream via FastStreamResolver to filesDir/offline_audio/{safeId}.m4a
 * - Emits real-time 0..100% progress per track via StateFlow
 * - Supports single track and sequential batch playlist downloads (4G-safe)
 * - Provides 0ms Priority #0 offline file lookup for FastStreamResolver
 */
object OfflineDownloadManager {
    private const val PREF_NAME = "spotyflow_offline_meta_v3"
    private const val KEY_TRACKS = "offline_tracks"
    private const val DIR_NAME = "offline_audio"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()

    private val _progressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progressMap: StateFlow<Map<String, Int>> = _progressMap.asStateFlow()

    private val _downloadedTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val downloadedTracks: StateFlow<List<TrackItem>> = _downloadedTracks.asStateFlow()

    @Volatile
    private var appContextRef: Context? = null

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        _downloadedTracks.value = loadMetadataVerified(appCtx)
    }

    fun safeFileId(videoId: String): String {
        return videoId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "track" }
    }

    fun getOfflineFile(context: Context?, videoId: String): File? {
        val ctx = context?.applicationContext ?: appContextRef ?: return null
        if (videoId.isBlank()) return null
        val dir = File(ctx.filesDir, DIR_NAME)
        val f = File(dir, "${safeFileId(videoId)}.m4a")
        return if (f.exists() && f.length() > 16_384L) f else null
    }

    fun getOfflineFileUri(videoId: String): String? {
        val f = getOfflineFile(appContextRef, videoId) ?: return null
        return Uri.fromFile(f).toString()
    }

    fun isDownloaded(context: Context, videoId: String): Boolean {
        return getOfflineFile(context, videoId) != null
    }

    fun downloadTrack(context: Context, track: TrackItem, onDone: ((Boolean) -> Unit)? = null) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        val key = track.videoId.ifBlank { track.id }
        if (key.isBlank()) {
            onDone?.invoke(false)
            return
        }
        if (getOfflineFile(appCtx, key) != null) {
            onDone?.invoke(true)
            return
        }
        scope.launch {
            val ok = executeSingleDownload(appCtx, track)
            withContext(Dispatchers.Main) { onDone?.invoke(ok) }
        }
    }

    fun downloadBatch(context: Context, tracks: List<TrackItem>, onAllDone: ((Int) -> Unit)? = null) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        scope.launch {
            var successCount = 0
            for (t in tracks) {
                val key = t.videoId.ifBlank { t.id }
                if (key.isBlank()) continue
                if (getOfflineFile(appCtx, key) != null) {
                    successCount++
                    continue
                }
                if (executeSingleDownload(appCtx, t)) {
                    successCount++
                }
            }
            withContext(Dispatchers.Main) { onAllDone?.invoke(successCount) }
        }
    }

    private suspend fun executeSingleDownload(context: Context, track: TrackItem): Boolean =
        queueMutex.withLock {
            val vid = track.videoId
            if (vid.length != 11) return@withLock false
            _progressMap.update { it + (vid to 1) }

            val resolved = FastStreamResolver.resolve(vid, forceFallback = false, skipOfflineLookup = true)
                ?: FastStreamResolver.resolve(vid, forceFallback = true, skipOfflineLookup = true)
            if (resolved == null || resolved.audioUrl.isBlank()) {
                _progressMap.update { it - vid }
                return@withLock false
            }

            val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
            val tempFile = File(dir, "${safeFileId(vid)}.tmp")
            val finalFile = File(dir, "${safeFileId(vid)}.m4a")

            return@withLock try {
                val req = Request.Builder()
                    .url(resolved.audioUrl)
                    .header("User-Agent", "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L) gzip")
                    .build()

                SpotyHttpClient.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        tempFile.delete()
                        _progressMap.update { it - vid }
                        return@withLock false
                    }
                    val body = resp.body ?: run {
                        _progressMap.update { it - vid }
                        return@withLock false
                    }
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L
                    var lastEmitPct = 1

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(16_384)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                val pct = if (totalBytes > 0L) {
                                    ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(1, 99)
                                } else {
                                    (lastEmitPct + 2).coerceAtMost(95)
                                }
                                if (pct != lastEmitPct) {
                                    lastEmitPct = pct
                                    _progressMap.update { it + (vid to pct) }
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (tempFile.exists() && tempFile.length() > 16_384L) {
                    if (finalFile.exists()) finalFile.delete()
                    tempFile.renameTo(finalFile)
                    val savedTrack = track.copy(localUri = Uri.fromFile(finalFile).toString())
                    addOrUpdateMetadata(context, savedTrack)
                    _progressMap.update { it - vid }
                    true
                } else {
                    tempFile.delete()
                    _progressMap.update { it - vid }
                    false
                }
            } catch (_: Throwable) {
                tempFile.delete()
                _progressMap.update { it - vid }
                false
            }
        }

    fun deleteTrack(context: Context, videoId: String) {
        val appCtx = context.applicationContext
        val f = File(File(appCtx.filesDir, DIR_NAME), "${safeFileId(videoId)}.m4a")
        if (f.exists()) f.delete()
        val updated = _downloadedTracks.value.filterNot { it.videoId == videoId || it.id == videoId }
        saveMetadata(appCtx, updated)
        _downloadedTracks.value = updated
    }

    fun deleteAll(context: Context) {
        val appCtx = context.applicationContext
        val dir = File(appCtx.filesDir, DIR_NAME)
        dir.listFiles()?.forEach { it.delete() }
        saveMetadata(appCtx, emptyList())
        _downloadedTracks.value = emptyList()
    }

    fun totalOfflineBytes(context: Context): Long {
        val dir = File(context.applicationContext.filesDir, DIR_NAME)
        return dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }

    fun formatSizeMb(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }

    private fun addOrUpdateMetadata(context: Context, track: TrackItem) {
        val list = loadMetadataVerified(context).toMutableList()
        list.removeAll { it.videoId == track.videoId || it.id == track.id }
        list.add(0, track)
        saveMetadata(context, list)
        _downloadedTracks.value = list
    }

    private fun loadMetadataVerified(context: Context): List<TrackItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TRACKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<TrackItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val vid = o.optString("videoId")
                val file = getOfflineFile(context, vid) ?: continue
                val title = o.optString("title")
                result.add(
                    TrackItem(
                        id = o.optString("id", vid),
                        videoId = vid,
                        title = title,
                        artist = o.optString("artist", "Artis"),
                        artistBrowseId = o.optString("artistBrowseId"),
                        album = o.optString("album"),
                        albumBrowseId = o.optString("albumBrowseId"),
                        durationSec = o.optInt("durationSec", 0),
                        coverUrl = o.optString("coverUrl"),
                        localUri = Uri.fromFile(file).toString(),
                        accentColor = deterministicAccent(title)
                    )
                )
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun saveMetadata(context: Context, tracks: List<TrackItem>) {
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
                put("coverUrl", t.coverUrl)
            })
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRACKS, arr.toString())
            .apply()
    }
}
