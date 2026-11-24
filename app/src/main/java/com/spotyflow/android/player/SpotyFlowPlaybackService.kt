package com.spotyflow.android.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.remote.FastStreamResolver
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
    val errorMessage: String? = null
)

@OptIn(UnstableApi::class)
class SpotyFlowPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var resolveJob: Job? = null
    private var prefetchJob: Job? = null
    private var tickerJob: Job? = null
    private var retriedWithFallbackForId: String = ""

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
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        ensureNotificationChannels(this)

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

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
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
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        resolveJob?.cancel()
        prefetchJob?.cancel()
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

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncPlayerSnapshot()
            if (isPlaying) scheduleNextPrefetch()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncPlayerSnapshot()
            if (playbackState == Player.STATE_ENDED) {
                handleTrackEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val current = _playbackState.value.currentTrack ?: return
            if (current.videoId.length == 11 && retriedWithFallbackForId != current.videoId) {
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
            while (isActive) {
                val p = exoPlayer
                if (p != null && (p.isPlaying || p.playbackState == Player.STATE_BUFFERING)) {
                    syncPlayerSnapshot()
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

    private fun syncPlayerSnapshot() {
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
    }

    // =========================================================================
    // PUBLIC PLAYBACK COMMANDS
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
        loadAndPlayCurrent(startPosMs = 0L, forceFallback = false)
    }

    fun addNext(track: TrackItem) {
        _playbackState.update { st ->
            val q = st.queue.toMutableList()
            val insertAt = (st.queueIndex + 1).coerceIn(0, q.size)
            q.add(insertAt, track)
            st.copy(queue = q)
        }
    }

    fun addToQueueEnd(track: TrackItem) {
        _playbackState.update { st ->
            st.copy(queue = st.queue + track)
        }
    }

    fun togglePlayPause() {
        val p = exoPlayer ?: return
        if (p.mediaItemCount == 0 && _playbackState.value.currentTrack != null) {
            loadAndPlayCurrent(_playbackState.value.positionMs, forceFallback = false)
            return
        }
        if (p.isPlaying) p.pause() else p.play()
        syncPlayerSnapshot()
    }

    fun seekTo(positionMs: Long) {
        val p = exoPlayer ?: return
        val clamped = positionMs.coerceAtLeast(0L)
        p.seekTo(clamped)
        _playbackState.update { it.copy(positionMs = clamped) }
        DynamicIslandStateBus.update { it.copy(positionMs = clamped) }
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
            val streamUri = if (track.localUri.isNotBlank()) {
                track.localUri
            } else {
                val resolved = FastStreamResolver.resolve(track.videoId, forceFallback = forceFallback)
                if (resolved == null) {
                    _playbackState.update { it.copy(isBuffering = false, isPlaying = false, errorMessage = "Stream tidak tersedia") }
                    return@launch
                }
                if (mode == PlayerMode.Video && resolved.videoUrl.isNotBlank()) {
                    resolved.videoUrl
                } else {
                    resolved.audioUrl
                }
            }

            // Guard against stale async completion if user skipped quickly
            if (_playbackState.value.currentTrack?.id != track.id) return@launch

            val p = exoPlayer ?: return@launch
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
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

            p.setMediaItem(mediaItem, startPosMs.coerceAtLeast(0L))
            p.prepare()
            p.playWhenReady = true
            syncPlayerSnapshot()
        }
    }

    private fun scheduleNextPrefetch() {
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
