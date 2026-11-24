package com.spotyflow.android.island

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.graphics.toArgb
import com.spotyflow.android.MainActivity
import com.spotyflow.android.data.remote.SpotyHttpClient
import com.spotyflow.android.player.DynamicIslandCommand
import com.spotyflow.android.player.DynamicIslandStateBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Custom Dynamic Island AccessibilityService using TYPE_ACCESSIBILITY_OVERLAY
 * (places the capsule cleanly across the camera cutout & status bar without SYSTEM_ALERT_WINDOW).
 */
class SpotyFlowDynamicIslandService : AccessibilityService() {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var islandView: SpotyFlowDynamicIslandView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentForegroundPkg: String = ""
    private var temporarilyDismissedTrackId: String = ""
    private var lastLoadedCoverUrl: String = ""
    private var cachedCoverBitmap: Bitmap? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        DynamicIslandConfigStore.load(this)
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
        attachIslandView()
        observeStateAndConfig()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString().orEmpty()
            if (pkg.isNotBlank()) {
                currentForegroundPkg = pkg
                updateVisibility()
            }
        }
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onUnbind(intent: Intent?): Boolean {
        detachIslandView()
        _isRunning.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        detachIslandView()
        scope.cancel()
        _isRunning.value = false
        super.onDestroy()
    }

    private fun attachIslandView() {
        if (islandView != null) return
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        val cfg = DynamicIslandConfigStore.config.value

        val view = SpotyFlowDynamicIslandView(
            context = this,
            onRequestLayoutSize = { wPx, hPx ->
                val lp = layoutParams ?: return@SpotyFlowDynamicIslandView
                if (lp.width != wPx || lp.height != hPx) {
                    lp.width = wPx
                    lp.height = hPx
                    try { wm.updateViewLayout(islandView, lp) } catch (_: Throwable) {}
                }
            },
            onPlayPause = { DynamicIslandStateBus.sendCommand(DynamicIslandCommand.PlayPause) },
            onNext = { DynamicIslandStateBus.sendCommand(DynamicIslandCommand.Next) },
            onPrev = { DynamicIslandStateBus.sendCommand(DynamicIslandCommand.Prev) },
            onToggleLike = { DynamicIslandStateBus.sendCommand(DynamicIslandCommand.ToggleLike) },
            onOpenApp = {
                try {
                    val launch = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(launch)
                } catch (_: Throwable) {}
            },
            onSwipeDismiss = {
                temporarilyDismissedTrackId = DynamicIslandStateBus.snapshot.value.currentTrack?.id.orEmpty()
                updateVisibility()
            }
        )
        view.applyConfig(cfg)
        view.visibility = View.GONE

        val params = WindowManager.LayoutParams(
            view.getCurrentWindowWidthPx(),
            view.getCurrentWindowHeightPx(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = (cfg.offsetXDp * density).toInt()
            y = (cfg.offsetYDp * density).toInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            wm.addView(view, params)
            islandView = view
            layoutParams = params
        } catch (_: Throwable) {
            // Safely handle devices disallowing overlay attach
        }
    }

    private fun detachIslandView() {
        val view = islandView ?: return
        view.cleanup()
        try {
            windowManager?.removeView(view)
        } catch (_: Throwable) {}
        islandView = null
        layoutParams = null
    }

    private fun observeStateAndConfig() {
        scope.launch {
            combine(
                DynamicIslandStateBus.snapshot,
                DynamicIslandConfigStore.config
            ) { snap, cfg -> Pair(snap, cfg) }.collect { (snap, cfg) ->
                val view = islandView ?: return@collect
                val lp = layoutParams
                val density = resources.displayMetrics.density

                view.applyConfig(cfg)
                if (lp != null) {
                    val nx = (cfg.offsetXDp * density).toInt()
                    val ny = (cfg.offsetYDp * density).toInt()
                    if (lp.x != nx || lp.y != ny) {
                        lp.x = nx
                        lp.y = ny
                        try { windowManager?.updateViewLayout(view, lp) } catch (_: Throwable) {}
                    }
                }

                val track = snap.currentTrack
                if (track != null && track.coverUrl.isNotBlank() && track.coverUrl != lastLoadedCoverUrl) {
                    lastLoadedCoverUrl = track.coverUrl
                    loadCoverAsync(track.coverUrl)
                }

                view.updateMedia(
                    title = track?.title.orEmpty(),
                    artist = track?.artist.orEmpty(),
                    lyric = snap.currentLyricLine,
                    playing = snap.isPlaying,
                    liked = snap.isLiked,
                    posMs = snap.positionMs,
                    durMs = snap.durationMs,
                    accentColorInt = snap.accentColor.toArgb(),
                    bitmap = cachedCoverBitmap
                )
                updateVisibility()
            }
        }
    }

    private fun updateVisibility() {
        val view = islandView ?: return
        val cfg = DynamicIslandConfigStore.config.value
        val snap = DynamicIslandStateBus.snapshot.value
        val track = snap.currentTrack
        val inOwnApp = currentForegroundPkg == applicationContext.packageName
        val dismissed = track != null && track.id == temporarilyDismissedTrackId

        val shouldShow = cfg.enabled &&
            track != null &&
            (snap.isPlaying || snap.isBuffering) &&
            !dismissed &&
            (cfg.showInsideSpotyFlow || !inOwnApp)

        view.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    private fun loadCoverAsync(url: String) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url(url).get().build()
                    SpotyHttpClient.client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val bytes = resp.body?.bytes() ?: return@use null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            if (bmp != null && url == lastLoadedCoverUrl) {
                cachedCoverBitmap = bmp
                val snap = DynamicIslandStateBus.snapshot.value
                val track = snap.currentTrack
                islandView?.updateMedia(
                    title = track?.title.orEmpty(),
                    artist = track?.artist.orEmpty(),
                    lyric = snap.currentLyricLine,
                    playing = snap.isPlaying,
                    liked = snap.isLiked,
                    posMs = snap.positionMs,
                    durMs = snap.durationMs,
                    accentColorInt = snap.accentColor.toArgb(),
                    bitmap = bmp
                )
            }
        }
    }
}
