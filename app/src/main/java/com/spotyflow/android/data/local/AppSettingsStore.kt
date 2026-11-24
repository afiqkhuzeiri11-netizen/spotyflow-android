package com.spotyflow.android.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class AppThemeMode(val label: String) {
    PURE_WHITE("Pure White"),
    DARK("Gelap"),
    AMOLED("Hitam Pekat AMOLED"),
    SYSTEM("Ikuti Sistem")
}

enum class UiStyleMode(val label: String) {
    GLASS("Kaca"),
    EDITORIAL("Editorial")
}

enum class WaterDropDensity(val label: String, val dropCount: Int, val alphaScale: Float) {
    SOFT("Halus", 6, 0.72f),
    MEDIUM("Sedang", 9, 0.90f),
    FULL("Penuh", 11, 1.0f)
}

enum class LyricAlignMode(val label: String) {
    LEFT("Kiri"),
    CENTER("Tengah"),
    RIGHT("Kanan")
}

enum class EqPreset(val label: String, val bandsMb: List<Int>) {
    FLAT("Datar", listOf(0, 0, 0, 0, 0)),
    BASS_BOOST("Bass Boost", listOf(550, 380, 50, 0, 120)),
    VOCAL("Vokal Jernih", listOf(-120, 120, 420, 360, 80)),
    POP("Pop Kristal", listOf(180, 260, 120, 240, 320)),
    ROCK("Rock & Live", listOf(420, 220, -80, 280, 440)),
    JAZZ("Jazz Hangat", listOf(260, 160, 180, 140, 220)),
    ELECTRONIC("EDM & Club", listOf(480, 320, 40, 260, 420)),
    ACOUSTIC("Akustik", listOf(180, 240, 280, 220, 160))
}

data class MusicAlarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "Bangun dengan SpotyFlow",
    val fadeInSec: Int = 12
)

data class AppSettings(
    // === 1. SUARA & PEMUTARAN ===
    val skipSilence: Boolean = false,
    val normalizeAudio: Boolean = false,
    val loudnessGainMb: Int = 0, // 0..1000 mB
    val audioOffload: Boolean = false,
    val varispeedEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f, // 0.5..2.0
    val playbackPitch: Float = 1.0f, // 0.5..2.0
    val crossfadeSec: Int = 0, // 0..12
    val progressiveSeek: Boolean = true,
    val eqEnabled: Boolean = false,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val eqBandsMb: List<Int> = listOf(0, 0, 0, 0, 0),
    val keepPlayingInGames: Boolean = true,
    val stopOnAppClose: Boolean = false,
    val pauseOnMute: Boolean = true,
    val resumeOnBluetooth: Boolean = true,
    val sleepFadeOut: Boolean = true,
    val sleepStopAtTrackEnd: Boolean = false,
    val alarms: List<MusicAlarm> = emptyList(),
    // 13 Sakelar Antrean
    val queuePersist: Boolean = true,
    val queueAutoRadio: Boolean = true,
    val queuePreventDuplicates: Boolean = true,
    val queueShowNextUp: Boolean = true,
    val queueSwipeToRemove: Boolean = true,
    val queueResetOnNewPlay: Boolean = false,
    val queueCrossfadeManualSkip: Boolean = false,
    val queueSmartShuffle: Boolean = true,
    val queueKeepHistory: Boolean = true,
    val queuePrioritizeOffline: Boolean = true,
    val queueAutoPrefetchNext: Boolean = true,
    val queueHapticReorder: Boolean = true,
    val queueClearOnSleep: Boolean = false,
    // 10 Sakelar Lainnya (Pemutaran)
    val playGapless: Boolean = true,
    val playHighBitrateWifi: Boolean = true,
    val playDataSaverCellular: Boolean = false,
    val playHeadsetAutoResume: Boolean = true,
    val playVolumeKeySkip: Boolean = false,
    val playDoubleTapSeek: Boolean = true,
    val playPreferAudioOnly: Boolean = true,
    val playShowAudioCodecBadge: Boolean = true,
    val playVibrationFeedback: Boolean = true,
    val playAutoRetryFallback: Boolean = true,

    // === 2. TAMPILAN ===
    val uiStyle: UiStyleMode = UiStyleMode.GLASS,
    val themeMode: AppThemeMode = AppThemeMode.PURE_WHITE,
    val glassBlurEnabled: Boolean = true,
    val glassLensEnabled: Boolean = true,
    val waterDropsEnabled: Boolean = true,
    val waterDropsDensity: WaterDropDensity = WaterDropDensity.FULL,
    val collapseNavOnScroll: Boolean = false,
    val iosSpringAnimations: Boolean = true,
    val showStreakOnHome: Boolean = true,
    val highRefreshRate120Hz: Boolean = true,
    val accentHex: Long = 0xFF10B981L,
    val miniPlayerShowProgress: Boolean = true,
    val miniPlayerSwipeSkip: Boolean = true,
    val showMiniLyricsInPlayer: Boolean = true,
    val showLockscreenLyrics: Boolean = false,
    // 6 Sakelar Playlist Otomatis di Pustaka
    val showColLiked: Boolean = true,
    val showColDownloaded: Boolean = true,
    val showColCached: Boolean = true,
    val showColDevice: Boolean = true,
    val showColTop50: Boolean = true,
    val showColUploaded: Boolean = true,

    // === 3. BAHASA & KONTEN ===
    val appLanguage: String = "Indonesia",
    val contentRegion: String = "ID",
    val filterExplicit: Boolean = false,
    val preferredAudioQuality: String = "Tinggi (Opus/M4A)",

    // === 4. LIRIK & TERJEMAHAN ===
    val lyricsAutoTranslate: Boolean = false,
    val lyricsTranslateTarget: String = "id",
    val lyricsAutoRomanize: Boolean = false,
    val lyricsOffsetMs: Long = 0L, // -2000..+2000
    val lyricsAlign: LyricAlignMode = LyricAlignMode.CENTER,
    val lyricsBlurInactive: Boolean = true,
    val lyricsSyncIsland: Boolean = true,

    // === 5. PRIVASI & RIWAYAT ===
    val pauseListenHistory: Boolean = false,
    val pauseSearchHistory: Boolean = false,
    val incognitoSession: Boolean = false,

    // === 8. ANDROID AUTO ===
    val androidAutoShowOfflineFirst: Boolean = true,
    val androidAutoVoiceSearch: Boolean = true,
    val androidAutoShowLyricsCard: Boolean = false
)

object AppSettingsStore {
    private const val PREF_NAME = "spotyflow_settings_v3"
    private const val KEY_JSON = "settings_json"

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Volatile
    private var loaded = false

    fun load(context: Context): AppSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JSON, null)
        if (raw == null) {
            loaded = true
            return _settings.value
        }
        val parsed = decodeSettings(raw)
        _settings.value = parsed
        loaded = true
        return parsed
    }

    fun ensureLoaded(context: Context): AppSettings {
        return if (loaded) _settings.value else load(context)
    }

    fun update(context: Context, transform: (AppSettings) -> AppSettings): AppSettings {
        val next = transform(_settings.value)
        _settings.value = next
        persist(context, next)
        return next
    }

    fun exportFullBackupJson(context: Context): String {
        val settingsJson = encodeSettings(_settings.value)
        val libPrefs = context.applicationContext.getSharedPreferences("spotyflow_native_store_v2", Context.MODE_PRIVATE)
        val libObj = JSONObject()
        libPrefs.all.forEach { (k, v) ->
            if (v != null) libObj.put(k, v)
        }
        return JSONObject().apply {
            put("app", "SpotyFlow Native")
            put("version", "2.5.0-native")
            put("exportedAt", System.currentTimeMillis())
            put("settings", settingsJson)
            put("library", libObj)
        }.toString(2)
    }

    fun importFullBackupJson(context: Context, rawJson: String): Boolean {
        return try {
            val root = JSONObject(rawJson)
            val sObj = root.optJSONObject("settings")
            if (sObj != null) {
                val decoded = decodeSettings(sObj.toString())
                _settings.value = decoded
                persist(context, decoded)
            }
            val libObj = root.optJSONObject("library")
            if (libObj != null) {
                val ed = context.applicationContext
                    .getSharedPreferences("spotyflow_native_store_v2", Context.MODE_PRIVATE)
                    .edit()
                val keys = libObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = libObj.opt(k)
                    when (v) {
                        is Boolean -> ed.putBoolean(k, v)
                        is Int -> ed.putInt(k, v)
                        is Long -> ed.putLong(k, v)
                        is String -> ed.putString(k, v)
                    }
                }
                ed.apply()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Safe Cache Cleaner: wipes Coil image cache & Media3 temp cache WITHOUT touching offline_audio or SharedPreferences.
     */
    fun clearSafeCache(context: Context): Long {
        var freedBytes = 0L
        try {
            val cacheRoot = context.applicationContext.cacheDir
            cacheRoot.listFiles()?.forEach { f ->
                if (f.name != "sf_media3_cache") {
                    freedBytes += deleteRecursiveSize(f)
                }
            }
        } catch (_: Throwable) {
            // Safe ignore
        }
        return freedBytes
    }

    fun calculateCacheSizeBytes(context: Context): Long {
        return try {
            dirSizeBytes(context.applicationContext.cacheDir)
        } catch (_: Throwable) {
            0L
        }
    }

    private fun dirSizeBytes(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { dirSizeBytes(it) } ?: 0L
    }

    private fun deleteRecursiveSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) {
            val len = file.length()
            file.delete()
            return len
        }
        var total = 0L
        file.listFiles()?.forEach { total += deleteRecursiveSize(it) }
        file.delete()
        return total
    }

    private fun persist(context: Context, s: AppSettings) {
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, encodeSettings(s).toString())
            .apply()
    }

    private fun encodeSettings(s: AppSettings): JSONObject = JSONObject().apply {
        put("skipSilence", s.skipSilence)
        put("normalizeAudio", s.normalizeAudio)
        put("loudnessGainMb", s.loudnessGainMb)
        put("audioOffload", s.audioOffload)
        put("varispeedEnabled", s.varispeedEnabled)
        put("playbackSpeed", s.playbackSpeed.toDouble())
        put("playbackPitch", s.playbackPitch.toDouble())
        put("crossfadeSec", s.crossfadeSec)
        put("progressiveSeek", s.progressiveSeek)
        put("eqEnabled", s.eqEnabled)
        put("eqPreset", s.eqPreset.name)
        put("eqBandsMb", JSONArray(s.eqBandsMb))
        put("keepPlayingInGames", s.keepPlayingInGames)
        put("stopOnAppClose", s.stopOnAppClose)
        put("pauseOnMute", s.pauseOnMute)
        put("resumeOnBluetooth", s.resumeOnBluetooth)
        put("sleepFadeOut", s.sleepFadeOut)
        put("sleepStopAtTrackEnd", s.sleepStopAtTrackEnd)
        val alarmArr = JSONArray()
        s.alarms.forEach { a ->
            alarmArr.put(JSONObject().apply {
                put("id", a.id)
                put("hour", a.hour)
                put("minute", a.minute)
                put("enabled", a.enabled)
                put("label", a.label)
                put("fadeInSec", a.fadeInSec)
            })
        }
        put("alarms", alarmArr)
        put("queuePersist", s.queuePersist)
        put("queueAutoRadio", s.queueAutoRadio)
        put("queuePreventDuplicates", s.queuePreventDuplicates)
        put("queueShowNextUp", s.queueShowNextUp)
        put("queueSwipeToRemove", s.queueSwipeToRemove)
        put("queueResetOnNewPlay", s.queueResetOnNewPlay)
        put("queueCrossfadeManualSkip", s.queueCrossfadeManualSkip)
        put("queueSmartShuffle", s.queueSmartShuffle)
        put("queueKeepHistory", s.queueKeepHistory)
        put("queuePrioritizeOffline", s.queuePrioritizeOffline)
        put("queueAutoPrefetchNext", s.queueAutoPrefetchNext)
        put("queueHapticReorder", s.queueHapticReorder)
        put("queueClearOnSleep", s.queueClearOnSleep)
        put("playGapless", s.playGapless)
        put("playHighBitrateWifi", s.playHighBitrateWifi)
        put("playDataSaverCellular", s.playDataSaverCellular)
        put("playHeadsetAutoResume", s.playHeadsetAutoResume)
        put("playVolumeKeySkip", s.playVolumeKeySkip)
        put("playDoubleTapSeek", s.playDoubleTapSeek)
        put("playPreferAudioOnly", s.playPreferAudioOnly)
        put("playShowAudioCodecBadge", s.playShowAudioCodecBadge)
        put("playVibrationFeedback", s.playVibrationFeedback)
        put("playAutoRetryFallback", s.playAutoRetryFallback)

        put("uiStyle", s.uiStyle.name)
        put("themeMode", s.themeMode.name)
        put("glassBlurEnabled", s.glassBlurEnabled)
        put("glassLensEnabled", s.glassLensEnabled)
        put("waterDropsEnabled", s.waterDropsEnabled)
        put("waterDropsDensity", s.waterDropsDensity.name)
        put("collapseNavOnScroll", s.collapseNavOnScroll)
        put("iosSpringAnimations", s.iosSpringAnimations)
        put("showStreakOnHome", s.showStreakOnHome)
        put("highRefreshRate120Hz", s.highRefreshRate120Hz)
        put("accentHex", s.accentHex)
        put("miniPlayerShowProgress", s.miniPlayerShowProgress)
        put("miniPlayerSwipeSkip", s.miniPlayerSwipeSkip)
        put("showMiniLyricsInPlayer", s.showMiniLyricsInPlayer)
        put("showLockscreenLyrics", s.showLockscreenLyrics)
        put("showColLiked", s.showColLiked)
        put("showColDownloaded", s.showColDownloaded)
        put("showColCached", s.showColCached)
        put("showColDevice", s.showColDevice)
        put("showColTop50", s.showColTop50)
        put("showColUploaded", s.showColUploaded)

        put("appLanguage", s.appLanguage)
        put("contentRegion", s.contentRegion)
        put("filterExplicit", s.filterExplicit)
        put("preferredAudioQuality", s.preferredAudioQuality)

        put("lyricsAutoTranslate", s.lyricsAutoTranslate)
        put("lyricsTranslateTarget", s.lyricsTranslateTarget)
        put("lyricsAutoRomanize", s.lyricsAutoRomanize)
        put("lyricsOffsetMs", s.lyricsOffsetMs)
        put("lyricsAlign", s.lyricsAlign.name)
        put("lyricsBlurInactive", s.lyricsBlurInactive)
        put("lyricsSyncIsland", s.lyricsSyncIsland)

        put("pauseListenHistory", s.pauseListenHistory)
        put("pauseSearchHistory", s.pauseSearchHistory)
        put("incognitoSession", s.incognitoSession)

        put("androidAutoShowOfflineFirst", s.androidAutoShowOfflineFirst)
        put("androidAutoVoiceSearch", s.androidAutoVoiceSearch)
        put("androidAutoShowLyricsCard", s.androidAutoShowLyricsCard)
    }

    private fun decodeSettings(raw: String): AppSettings {
        return try {
            val j = JSONObject(raw)
            val bandsArr = j.optJSONArray("eqBandsMb")
            val bands = if (bandsArr != null && bandsArr.length() == 5) {
                List(5) { bandsArr.optInt(it, 0).coerceIn(-1200, 1200) }
            } else {
                listOf(0, 0, 0, 0, 0)
            }
            val alarmsArr = j.optJSONArray("alarms")
            val alarms = if (alarmsArr != null) {
                List(alarmsArr.length()) { i ->
                    val o = alarmsArr.getJSONObject(i)
                    MusicAlarm(
                        id = o.optInt("id", i + 1),
                        hour = o.optInt("hour", 7).coerceIn(0, 23),
                        minute = o.optInt("minute", 0).coerceIn(0, 59),
                        enabled = o.optBoolean("enabled", true),
                        label = o.optString("label", "Bangun dengan SpotyFlow"),
                        fadeInSec = o.optInt("fadeInSec", 12).coerceIn(3, 30)
                    )
                }
            } else {
                emptyList()
            }
            AppSettings(
                skipSilence = j.optBoolean("skipSilence", false),
                normalizeAudio = j.optBoolean("normalizeAudio", false),
                loudnessGainMb = j.optInt("loudnessGainMb", 0).coerceIn(0, 1000),
                audioOffload = j.optBoolean("audioOffload", false),
                varispeedEnabled = j.optBoolean("varispeedEnabled", false),
                playbackSpeed = j.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(0.5f, 2.0f),
                playbackPitch = j.optDouble("playbackPitch", 1.0).toFloat().coerceIn(0.5f, 2.0f),
                crossfadeSec = j.optInt("crossfadeSec", 0).coerceIn(0, 12),
                progressiveSeek = j.optBoolean("progressiveSeek", true),
                eqEnabled = j.optBoolean("eqEnabled", false),
                eqPreset = runCatching { EqPreset.valueOf(j.optString("eqPreset", "FLAT")) }.getOrDefault(EqPreset.FLAT),
                eqBandsMb = bands,
                keepPlayingInGames = j.optBoolean("keepPlayingInGames", true),
                stopOnAppClose = j.optBoolean("stopOnAppClose", false),
                pauseOnMute = j.optBoolean("pauseOnMute", true),
                resumeOnBluetooth = j.optBoolean("resumeOnBluetooth", true),
                sleepFadeOut = j.optBoolean("sleepFadeOut", true),
                sleepStopAtTrackEnd = j.optBoolean("sleepStopAtTrackEnd", false),
                alarms = alarms,
                queuePersist = j.optBoolean("queuePersist", true),
                queueAutoRadio = j.optBoolean("queueAutoRadio", true),
                queuePreventDuplicates = j.optBoolean("queuePreventDuplicates", true),
                queueShowNextUp = j.optBoolean("queueShowNextUp", true),
                queueSwipeToRemove = j.optBoolean("queueSwipeToRemove", true),
                queueResetOnNewPlay = j.optBoolean("queueResetOnNewPlay", false),
                queueCrossfadeManualSkip = j.optBoolean("queueCrossfadeManualSkip", false),
                queueSmartShuffle = j.optBoolean("queueSmartShuffle", true),
                queueKeepHistory = j.optBoolean("queueKeepHistory", true),
                queuePrioritizeOffline = j.optBoolean("queuePrioritizeOffline", true),
                queueAutoPrefetchNext = j.optBoolean("queueAutoPrefetchNext", true),
                queueHapticReorder = j.optBoolean("queueHapticReorder", true),
                queueClearOnSleep = j.optBoolean("queueClearOnSleep", false),
                playGapless = j.optBoolean("playGapless", true),
                playHighBitrateWifi = j.optBoolean("playHighBitrateWifi", true),
                playDataSaverCellular = j.optBoolean("playDataSaverCellular", false),
                playHeadsetAutoResume = j.optBoolean("playHeadsetAutoResume", true),
                playVolumeKeySkip = j.optBoolean("playVolumeKeySkip", false),
                playDoubleTapSeek = j.optBoolean("playDoubleTapSeek", true),
                playPreferAudioOnly = j.optBoolean("playPreferAudioOnly", true),
                playShowAudioCodecBadge = j.optBoolean("playShowAudioCodecBadge", true),
                playVibrationFeedback = j.optBoolean("playVibrationFeedback", true),
                playAutoRetryFallback = j.optBoolean("playAutoRetryFallback", true),
                uiStyle = runCatching { UiStyleMode.valueOf(j.optString("uiStyle", "GLASS")) }.getOrDefault(UiStyleMode.GLASS),
                themeMode = runCatching { AppThemeMode.valueOf(j.optString("themeMode", "PURE_WHITE")) }.getOrDefault(AppThemeMode.PURE_WHITE),
                glassBlurEnabled = j.optBoolean("glassBlurEnabled", true),
                glassLensEnabled = j.optBoolean("glassLensEnabled", true),
                waterDropsEnabled = j.optBoolean("waterDropsEnabled", true),
                waterDropsDensity = runCatching { WaterDropDensity.valueOf(j.optString("waterDropsDensity", "FULL")) }.getOrDefault(WaterDropDensity.FULL),
                collapseNavOnScroll = j.optBoolean("collapseNavOnScroll", false),
                iosSpringAnimations = j.optBoolean("iosSpringAnimations", true),
                showStreakOnHome = j.optBoolean("showStreakOnHome", true),
                highRefreshRate120Hz = j.optBoolean("highRefreshRate120Hz", true),
                accentHex = j.optLong("accentHex", 0xFF10B981L),
                miniPlayerShowProgress = j.optBoolean("miniPlayerShowProgress", true),
                miniPlayerSwipeSkip = j.optBoolean("miniPlayerSwipeSkip", true),
                showMiniLyricsInPlayer = j.optBoolean("showMiniLyricsInPlayer", true),
                showLockscreenLyrics = j.optBoolean("showLockscreenLyrics", false),
                showColLiked = j.optBoolean("showColLiked", true),
                showColDownloaded = j.optBoolean("showColDownloaded", true),
                showColCached = j.optBoolean("showColCached", true),
                showColDevice = j.optBoolean("showColDevice", true),
                showColTop50 = j.optBoolean("showColTop50", true),
                showColUploaded = j.optBoolean("showColUploaded", true),
                appLanguage = j.optString("appLanguage", "Indonesia"),
                contentRegion = j.optString("contentRegion", "ID"),
                filterExplicit = j.optBoolean("filterExplicit", false),
                preferredAudioQuality = j.optString("preferredAudioQuality", "Tinggi (Opus/M4A)"),
                lyricsAutoTranslate = j.optBoolean("lyricsAutoTranslate", false),
                lyricsTranslateTarget = j.optString("lyricsTranslateTarget", "id"),
                lyricsAutoRomanize = j.optBoolean("lyricsAutoRomanize", false),
                lyricsOffsetMs = j.optLong("lyricsOffsetMs", 0L).coerceIn(-2000L, 2000L),
                lyricsAlign = runCatching { LyricAlignMode.valueOf(j.optString("lyricsAlign", "CENTER")) }.getOrDefault(LyricAlignMode.CENTER),
                lyricsBlurInactive = j.optBoolean("lyricsBlurInactive", true),
                lyricsSyncIsland = j.optBoolean("lyricsSyncIsland", true),
                pauseListenHistory = j.optBoolean("pauseListenHistory", false),
                pauseSearchHistory = j.optBoolean("pauseSearchHistory", false),
                incognitoSession = j.optBoolean("incognitoSession", false),
                androidAutoShowOfflineFirst = j.optBoolean("androidAutoShowOfflineFirst", true),
                androidAutoVoiceSearch = j.optBoolean("androidAutoVoiceSearch", true),
                androidAutoShowLyricsCard = j.optBoolean("androidAutoShowLyricsCard", false)
            )
        } catch (_: Throwable) {
            AppSettings()
        }
    }
}
