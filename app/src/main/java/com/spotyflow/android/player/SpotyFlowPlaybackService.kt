package com.spotyflow.android.player

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.spotyflow.android.MainActivity
import com.spotyflow.android.R
import com.spotyflow.android.data.PlayerMode
import com.spotyflow.android.data.RepeatMode
import com.spotyflow.android.data.SearchFilter
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.local.AppSettings
import com.spotyflow.android.data.local.AppSettingsStore
import com.spotyflow.android.data.local.MusicAlarm
import com.spotyflow.android.data.local.OfflineDownloadManager
import com.spotyflow.android.data.remote.FastStreamResolver
import com.spotyflow.android.data.remote.InnerTubeMusicClient
import com.spotyflow.android.widget.SpotyFlowBaseWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

data class ServicePlaybackState(
    val queue: List<TrackItem> = emptyList(),
    val queueIndex: Int = -1,
    val currentTrack: TrackItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val playerMode: PlayerMode = PlayerMode.Audio,
    val sleepTimerRemainingSec: Int = 0,
    val sleepStopAtEndOfTrack: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(UnstableApi::class)
class SpotyFlowPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var hardwareEqualizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var boundAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    private var resolveJob: Job? = null
    private var prefetchJob: Job? = null
    private var tickerJob: Job? = null
    private var sleepJob: Job? = null
    private var fadeJob: Job? = null
    private var retriedWithFallbackForId: String = ""
    private var receiverRegistered: Boolean = false
    private var lastProgressiveSeekMs: Long = 0L
    private var progressiveSeekStep: Int = 0

    // Keep system media notification clean while live lyrics show in Custom Dynamic Island
    private val islandLyricsInSystemNotification: Boolean = false

    private val hardwareEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val settings = AppSettingsStore.ensureLoaded(context)
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (settings.resumeOnBluetooth) {
                        val p = exoPlayer
                        if (p != null && p.mediaItemCount > 0 && !p.isPlaying) {
                            p.play()
                            syncPlayerSnapshot(triggerWidget = true)
                        }
                    }
                }
                "android.media.VOLUME_CHANGED_ACTION" -> {
                    if (settings.pauseOnMute) {
                        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        val vol = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 1
                        val p = exoPlayer
                        if (vol == 0 && p != null && p.isPlaying) {
                            p.pause()
                            syncPlayerSnapshot(triggerWidget = true)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val CHANNEL_GENERAL = "spotyflow_general"
        const val CHANNEL_PLAYBACK = "spotyflow_playback"
        const val CHANNEL_MUSIC = "spotyflow_music"
        const val CHANNEL_DOWNLOADS = "spotyflow_downloads"

        @Volatile
        private var sharedCache: SimpleCache? = null

        @Volatile
        var activeInstance: SpotyFlowPlaybackService? = null
            private set

        private val _playbackState = MutableStateFlow(ServicePlaybackState())
        val playbackState: StateFlow<ServicePlaybackState> = _playbackState.asStateFlow()

        private var pendingAction: ((SpotyFlowPlaybackService) -> Unit)? = null

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val channels = listOf(
                NotificationChannel(CHANNEL_GENERAL, "Umum SpotyFlow", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_PLAYBACK, "Pemutaran Musik", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_MUSIC, "Rekomendasi & Rilisan", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_DOWNLOADS, "Unduhan Offline", NotificationManager.IMPORTANCE_LOW)
            )
            channels.forEach { nm.createNotificationChannel(it) }
        }

        private fun getOrCreateCache(context: Context): SimpleCache {
            sharedCache?.let { return it }
            synchronized(this) {
                sharedCache?.let { return it }
                val cacheDir = File(context.applicationContext.cacheDir, "sf_media3_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(180L * 1024L * 1024L)
                val db = StandaloneDatabaseProvider(context.applicationContext)
                return SimpleCache(cacheDir, evictor, db).also { sharedCache = it }
            }
        }

        fun dispatch(context: Context, action: (SpotyFlowPlaybackService) -> Unit) {
            val inst = activeInstance
            if (inst != null) {
                action(inst)
            } else {
                pendingAction = action
                try {
                    val intent = Intent(context.applicationContext, SpotyFlowPlaybackService::class.java)
                    context.applicationContext.startService(intent)
                } catch (_: Throwable) {
                    // Fallback handled when MediaController binds
                }
            }
        }

        fun getActiveExoPlayer(): ExoPlayer? = activeInstance?.exoPlayer

        fun scheduleMusicAlarm(context: Context, alarm: MusicAlarm) {
            val appCtx = context.applicationContext
            val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(appCtx, SpotyFlowAlarmReceiver::class.java).apply {
                putExtra("alarm_id", alarm.id)
                putExtra("fade_in_sec", alarm.fadeInSec)
            }
            val pi = PendingIntent.getBroadcast(
                appCtx,
                5000 + alarm.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (!alarm.enabled) {
                try { am.cancel(pi) } catch (_: Throwable) {}
                return
            }
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                }
            } catch (_: Throwable) {
                try {
                    am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } catch (_: Throwable) {
                    // Safe fallback
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        ensureNotificationChannels(this)
        AppSettingsStore.ensureLoaded(this)
        OfflineDownloadManager.init(this)

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(12_000)
            .setUserAgent("com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L) gzip")

        val upstreamFactory = DefaultDataSource.Factory(this, httpFactory)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(getOrCreateCache(this))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(25_000, 60_000, 850, 1_700)
            .build()

        val settings = AppSettingsStore.settings.value
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, !settings.keepPlayingInGames)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(playerListener)
        exoPlayer = player

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPi = PendingIntent.getActivity(
            this,
            100,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_PLAYBACK)
            .setChannelName(R.string.notif_channel_playback)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_stat_music)
            }
        setMediaNotificationProvider(notificationProvider)

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPi)
            .build()

        registerHardwareReceiverSafely()
        applyAudioSettings(settings)
        observeSettingsChanges()
        startPositionTicker()
        observeDynamicIslandCommands()

        pendingAction?.let { act ->
            pendingAction = null
            act(this)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = exoPlayer
        val stopOnClose = AppSettingsStore.ensureLoaded(this).stopOnAppClose
        if (stopOnClose || player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            player?.pause()
            stopSelf()
        }
    }

    override fun onDestroy() {
        unregisterHardwareReceiverSafely()
        releaseAudioFx()
        tickerJob?.cancel()
        resolveJob?.cancel()
        prefetchJob?.cancel()
        sleepJob?.cancel()
        fadeJob?.cancel()
        serviceScope.cancel()
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    private fun registerHardwareReceiverSafely() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction("android.media.VOLUME_CHANGED_ACTION")
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(hardwareEventReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(hardwareEventReceiver, filter)
            }
            receiverRegistered = true
        } catch (_: Throwable) {
            receiverRegistered = false
        }
    }

    private fun unregisterHardwareReceiverSafely() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(hardwareEventReceiver)
        } catch (_: Throwable) {
            // Safe ignore
        } finally {
            receiverRegistered = false
        }
    }

    private fun observeSettingsChanges() {
        serviceScope.launch {
            AppSettingsStore.settings.collect { s ->
                applyAudioSettings(s)
            }
        }
    }

    fun applyAudioSettings(s: AppSettings) {
        val p = exoPlayer ?: return
        try {
            p.skipSilenceEnabled = s.skipSilence
            val speed = if (s.varispeedEnabled) s.playbackSpeed.coerceIn(0.5f, 2.0f) else 1.0f
            val pitch = if (s.varispeedEnabled) s.playbackPitch.coerceIn(0.5f, 2.0f) else 1.0f
            p.playbackParameters = PlaybackParameters(speed, pitch)

            val offloadMode = if (s.audioOffload && !s.eqEnabled && !s.varispeedEnabled) {
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
            } else {
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            }
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(offloadMode)
                        .build()
                )
                .build()
        } catch (_: Throwable) {
            // Ignore device-specific ExoPlayer capability gaps
        }

        ensureAndApplyHardwareAudioFx(p.audioSessionId, s)
    }

    private fun ensureAndApplyHardwareAudioFx(sessionId: Int, s: AppSettings) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return

        if (boundAudioSessionId != sessionId) {
            releaseAudioFx()
            boundAudioSessionId = sessionId
            try {
                hardwareEqualizer = Equalizer(0, sessionId)
            } catch (_: Throwable) {
                hardwareEqualizer = null
            }
            try {
                loudnessEnhancer = LoudnessEnhancer(sessionId)
            } catch (_: Throwable) {
                loudnessEnhancer = null
            }
        }

        // Apply 5-Band Hardware Equalizer safely
        try {
            hardwareEqualizer?.let { eq ->
                eq.enabled = s.eqEnabled
                if (s.eqEnabled) {
                    val bandsCount = eq.numberOfBands.toInt().coerceAtLeast(0)
                    val range = eq.bandLevelRange
                    val minMb = range.getOrNull(0)?.toInt() ?: -1500
                    val maxMb = range.getOrNull(1)?.toInt() ?: 1500
                    for (b in 0 until minOf(bandsCount, s.eqBandsMb.size)) {
                        val lvl = s.eqBandsMb[b].coerceIn(minMb, maxMb).toShort()
                        eq.setBandLevel(b.toShort(), lvl)
                    }
                }
            }
        } catch (_: Throwable) {
            // Protect against OEM DSP exceptions
        }

        // Apply Hardware Loudness Enhancer safely
        try {
            loudnessEnhancer?.let { le ->
                val activeGain = when {
                    s.normalizeAudio && s.loudnessGainMb > 0 -> s.loudnessGainMb
                    s.normalizeAudio -> 320
                    s.loudnessGainMb > 0 -> s.loudnessGainMb
                    else -> 0
                }
                le.enabled = activeGain > 0
                if (activeGain > 0) {
                    le.setTargetGain(activeGain.coerceIn(0, 1000))
                }
            }
        } catch (_: Throwable) {
            // Protect against OEM DSP exceptions
        }
    }

    private fun releaseAudioFx() {
        try {
            hardwareEqualizer?.release()
        } catch (_: Throwable) {
        } finally {
            hardwareEqualizer = null
        }
        try {
            loudnessEnhancer?.release()
        } catch (_: Throwable) {
        } finally {
            loudnessEnhancer = null
        }
        boundAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            ensureAndApplyHardwareAudioFx(audioSessionId, AppSettingsStore.settings.value)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncPlayerSnapshot(triggerWidget = true)
            if (isPlaying) scheduleNextPrefetch()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncPlayerSnapshot(triggerWidget = playbackState == Player.STATE_READY)
            if (playbackState == Player.STATE_ENDED) {
                handleTrackEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val current = _playbackState.value.currentTrack ?: return
            val autoRetry = AppSettingsStore.settings.value.playAutoRetryFallback
            if (autoRetry && current.videoId.length == 11 && retriedWithFallbackForId != current.videoId) {
                retriedWithFallbackForId = current.videoId
                FastStreamResolver.invalidate(current.videoId)
                loadAndPlayCurrent(startPosMs = _playbackState.value.positionMs, forceFallback = true)
            } else {
                _playbackState.update { it.copy(isBuffering = false, errorMessage = "Gagal memutar stream") }
                nextTrack(auto = true)
            }
        }
    }

    private fun startPositionTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var tickCounter = 0
            while (isActive) {
                val p = exoPlayer
                if (p != null && (p.isPlaying || p.playbackState == Player.STATE_BUFFERING)) {
                    tickCounter++
                    syncPlayerSnapshot(triggerWidget = (tickCounter % 12 == 0))
                }
                delay(250L)
            }
        }
    }

    private fun observeDynamicIslandCommands() {
        serviceScope.launch {
            DynamicIslandStateBus.commands.collect { cmd ->
                when (cmd) {
                    DynamicIslandCommand.PlayPause -> togglePlayPause()
                    DynamicIslandCommand.Next -> nextTrack(auto = false)
                    DynamicIslandCommand.Prev -> previousTrack()
                    else -> Unit
                }
            }
        }
    }

    private fun syncPlayerSnapshot(triggerWidget: Boolean = false) {
        val p = exoPlayer ?: return
        val pos = p.currentPosition.coerceAtLeast(0L)
        val dur = p.duration.takeIf { it > 0L }
            ?: ((_playbackState.value.currentTrack?.durationSec ?: 0) * 1000L)
        val buffering = p.playbackState == Player.STATE_BUFFERING
        val playing = p.isPlaying
        _playbackState.update {
            it.copy(
                isPlaying = playing,
                isBuffering = buffering,
                positionMs = pos,
                durationMs = dur
            )
        }
        DynamicIslandStateBus.update { bus ->
            bus.copy(
                currentTrack = _playbackState.value.currentTrack,
                isPlaying = playing,
                isBuffering = buffering,
                positionMs = pos,
                durationMs = dur
            )
        }
        if (triggerWidget) {
            SpotyFlowBaseWidgetProvider.updateAllWidgetsAsync(this)
        }
    }

    // =========================================================================
    // PUBLIC PLAYBACK COMMANDS + SLEEP TIMER + ALARM FADE-IN
    // =========================================================================
    fun playQueue(tracks: List<TrackItem>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val idx = startIndex.coerceIn(0, tracks.lastIndex)
        val target = tracks[idx]
        retriedWithFallbackForId = ""
        _playbackState.update {
            it.copy(
                queue = tracks,
                queueIndex = idx,
                currentTrack = target,
                isPlaying = true,
                isBuffering = true,
                positionMs = 0L,
                durationMs = target.durationSec * 1000L,
                errorMessage = null
            )
        }
        DynamicIslandStateBus.update {
            it.copy(
                currentTrack = target,
                isPlaying = true,
                isBuffering = true,
                positionMs = 0L,
                durationMs = target.durationSec * 1000L,
                accentColor = target.accentColor
            )
        }
        SpotyFlowBaseWidgetProvider.updateAllWidgetsAsync(this, forceImmediate = true)
        loadAndPlayCurrent(startPosMs = 0L, forceFallback = false)
    }

    fun playAlarmWithFadeIn(tracks: List<TrackItem>, fadeInSec: Int) {
        if (tracks.isEmpty()) return
        playQueue(tracks, 0)
        fadeJob?.cancel()
        fadeJob = serviceScope.launch {
            val steps = (fadeInSec * 4).coerceIn(8, 120)
            exoPlayer?.volume = 0.08f
            for (i in 1..steps) {
                delay(250L)
                exoPlayer?.volume = (0.08f + 0.92f * (i.toFloat() / steps)).coerceIn(0.08f, 1.0f)
            }
            exoPlayer?.volume = 1.0f
        }
    }

    fun startSleepTimer(minutes: Int, stopAtTrackEnd: Boolean = false) {
        sleepJob?.cancel()
        if (minutes <= 0 && !stopAtTrackEnd) {
            _playbackState.update { it.copy(sleepTimerRemainingSec = 0, sleepStopAtEndOfTrack = false) }
            exoPlayer?.volume = 1.0f
            return
        }
        if (stopAtTrackEnd) {
            _playbackState.update { it.copy(sleepTimerRemainingSec = 0, sleepStopAtEndOfTrack = true) }
            return
        }
        val totalSec = (minutes * 60).coerceAtLeast(15)
        _playbackState.update { it.copy(sleepTimerRemainingSec = totalSec, sleepStopAtEndOfTrack = false) }
        sleepJob = serviceScope.launch {
            var remaining = totalSec
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _playbackState.update { it.copy(sleepTimerRemainingSec = remaining) }
                val fadeEnabled = AppSettingsStore.settings.value.sleepFadeOut
                if (fadeEnabled && remaining in 1..8) {
                    exoPlayer?.volume = (remaining / 8f).coerceIn(0.05f, 1f)
                }
            }
            if (isActive) {
                exoPlayer?.pause()
                exoPlayer?.volume = 1.0f
                _playbackState.update { it.copy(sleepTimerRemainingSec = 0, sleepStopAtEndOfTrack = false) }
                syncPlayerSnapshot(triggerWidget = true)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        exoPlayer?.volume = 1.0f
        _playbackState.update { it.copy(sleepTimerRemainingSec = 0, sleepStopAtEndOfTrack = false) }
    }

    fun addNext(track: TrackItem) {
        _playbackState.update { st ->
            val q = st.queue.toMutableList()
            if (AppSettingsStore.settings.value.queuePreventDuplicates) {
                q.removeAll { it.id == track.id && q.indexOf(it) > st.queueIndex }
            }
            val insertAt = (st.queueIndex + 1).coerceIn(0, q.size)
            q.add(insertAt, track)
            st.copy(queue = q)
        }
    }

    fun addToQueueEnd(track: TrackItem) {
        _playbackState.update { st ->
            val q = st.queue.toMutableList()
            if (!AppSettingsStore.settings.value.queuePreventDuplicates || q.none { it.id == track.id }) {
                q.add(track)
            }
            st.copy(queue = q)
        }
    }

    fun removeFromQueue(index: Int) {
        _playbackState.update { st ->
            if (index !in st.queue.indices || index == st.queueIndex) return@update st
            val q = st.queue.toMutableList()
            q.removeAt(index)
            val newIdx = if (index < st.queueIndex) st.queueIndex - 1 else st.queueIndex
            st.copy(queue = q, queueIndex = newIdx)
        }
    }

    fun togglePlayPause() {
        val p = exoPlayer ?: return
        if (p.mediaItemCount == 0 && _playbackState.value.currentTrack != null) {
            loadAndPlayCurrent(_playbackState.value.positionMs, forceFallback = false)
            return
        }
        if (p.isPlaying) p.pause() else p.play()
        syncPlayerSnapshot(triggerWidget = true)
    }

    fun seekTo(positionMs: Long) {
        val p = exoPlayer ?: return
        val clamped = positionMs.coerceAtLeast(0L)
        p.seekTo(clamped)
        _playbackState.update { it.copy(positionMs = clamped) }
        DynamicIslandStateBus.update { it.copy(positionMs = clamped) }
    }

    fun seekRelative(forward: Boolean) {
        val p = exoPlayer ?: return
        val now = System.currentTimeMillis()
        val progressive = AppSettingsStore.settings.value.progressiveSeek
        if (progressive && now - lastProgressiveSeekMs < 1400L) {
            progressiveSeekStep = (progressiveSeekStep + 1).coerceAtMost(6)
        } else {
            progressiveSeekStep = 1
        }
        lastProgressiveSeekMs = now
        val deltaMs = (5_000L * progressiveSeekStep) * if (forward) 1L else -1L
        val dur = p.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        seekTo((p.currentPosition + deltaMs).coerceIn(0L, dur))
    }

    fun setPlayerMode(mode: PlayerMode) {
        if (_playbackState.value.playerMode == mode) return
        val resumePos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: _playbackState.value.positionMs
        _playbackState.update { it.copy(playerMode = mode) }
        if (_playbackState.value.currentTrack != null) {
            loadAndPlayCurrent(startPosMs = resumePos, forceFallback = false)
        }
    }

    fun toggleShuffle() {
        _playbackState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun cycleRepeat() {
        _playbackState.update { st ->
            val next = when (st.repeatMode) {
                RepeatMode.Off -> RepeatMode.All
                RepeatMode.All -> RepeatMode.One
                RepeatMode.One -> RepeatMode.Off
            }
            st.copy(repeatMode = next)
        }
    }

    fun nextTrack(auto: Boolean) {
        val st = _playbackState.value
        val q = st.queue
        if (q.isEmpty()) return
        val nextIdx = when {
            st.isShuffle && q.size > 1 -> {
                val choices = q.indices.filter { it != st.queueIndex }
                choices.random()
            }
            st.queueIndex + 1 < q.size -> st.queueIndex + 1
            st.repeatMode == RepeatMode.All || !auto -> 0
            else -> return
        }
        playQueue(q, nextIdx)
    }

    fun previousTrack() {
        val p = exoPlayer
        if (p != null && p.currentPosition > 4_000L) {
            seekTo(0L)
            return
        }
        val st = _playbackState.value
        val q = st.queue
        if (q.isEmpty()) return
        val prevIdx = if (st.queueIndex <= 0) q.lastIndex else st.queueIndex - 1
        playQueue(q, prevIdx)
    }

    private fun handleTrackEnded() {
        val st = _playbackState.value
        if (st.sleepStopAtEndOfTrack) {
            exoPlayer?.pause()
            _playbackState.update { it.copy(sleepStopAtEndOfTrack = false, isPlaying = false) }
            syncPlayerSnapshot(triggerWidget = true)
            return
        }
        if (st.repeatMode == RepeatMode.One) {
            seekTo(0L)
            exoPlayer?.play()
        } else {
            nextTrack(auto = true)
        }
    }

    private fun loadAndPlayCurrent(startPosMs: Long, forceFallback: Boolean) {
        resolveJob?.cancel()
        prefetchJob?.cancel()
        val track = _playbackState.value.currentTrack ?: return
        val mode = _playbackState.value.playerMode

        resolveJob = serviceScope.launch {
            val streamUri = when {
                track.localUri.isNotBlank() -> track.localUri
                track.videoId.length == 11 -> {
                    val resolved = FastStreamResolver.resolve(track.videoId, forceFallback = forceFallback)
                    if (resolved == null) {
                        _playbackState.update {
                            it.copy(isBuffering = false, isPlaying = false, errorMessage = "Stream tidak tersedia")
                        }
                        return@launch
                    }
                    if (mode == PlayerMode.Video && resolved.videoUrl.isNotBlank()) {
                        resolved.videoUrl
                    } else {
                        resolved.audioUrl
                    }
                }
                else -> {
                    // On-demand InnerTube match for Spotify-imported tracks (no videoId yet)
                    val query = "${track.title} ${track.artist}".trim()
                    val matched = InnerTubeMusicClient.searchAll(query, SearchFilter.SONGS).tracks.firstOrNull()
                    if (matched == null || matched.videoId.length != 11) {
                        _playbackState.update {
                            it.copy(isBuffering = false, isPlaying = false, errorMessage = "Lagu tidak ditemukan")
                        }
                        return@launch
                    }
                    val resolved = FastStreamResolver.resolve(matched.videoId, forceFallback = forceFallback)
                    resolved?.audioUrl ?: return@launch
                }
            }

            if (_playbackState.value.currentTrack?.id != track.id) return@launch

            val p = exoPlayer ?: return@launch
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(if (islandLyricsInSystemNotification) track.artist else track.artist)
                .setAlbumTitle(track.album)
                .apply {
                    if (track.coverUrl.isNotBlank()) {
                        setArtworkUri(Uri.parse(track.coverUrl))
                    }
                }
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(streamUri)
                .setMediaMetadata(metadata)
                .build()

            val crossfadeSec = AppSettingsStore.settings.value.crossfadeSec
            p.setMediaItem(mediaItem, startPosMs.coerceAtLeast(0L))
            p.prepare()
            if (crossfadeSec > 0 && startPosMs == 0L) {
                applyCrossfadeIn(crossfadeSec)
            } else {
                p.volume = 1.0f
            }
            p.playWhenReady = true
            syncPlayerSnapshot(triggerWidget = true)
        }
    }

    private fun applyCrossfadeIn(seconds: Int) {
        fadeJob?.cancel()
        val durationMs = (seconds * 1000L).coerceIn(800L, 6000L)
        val steps = 16
        val stepDelay = durationMs / steps
        fadeJob = serviceScope.launch {
            exoPlayer?.volume = 0.12f
            for (i in 1..steps) {
                delay(stepDelay)
                exoPlayer?.volume = (0.12f + 0.88f * (i.toFloat() / steps)).coerceIn(0.12f, 1.0f)
            }
            exoPlayer?.volume = 1.0f
        }
    }

    private fun scheduleNextPrefetch() {
        if (!AppSettingsStore.settings.value.queueAutoPrefetchNext) return
        prefetchJob?.cancel()
        prefetchJob = serviceScope.launch {
            delay(4_000L)
            val st = _playbackState.value
            if (!st.isPlaying || st.queue.size < 2) return@launch
            val nextIdx = (st.queueIndex + 1) % st.queue.size
            val nextTrack = st.queue.getOrNull(nextIdx) ?: return@launch
            if (nextTrack.videoId.length == 11) {
                FastStreamResolver.prefetchAsync(nextTrack.videoId)
            }
        }
    }
}
