package com.spotyflow.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.spotyflow.android.MainActivity
import com.spotyflow.android.R
import com.spotyflow.android.data.formatDurationMs
import com.spotyflow.android.data.formatRemainingMs
import com.spotyflow.android.data.local.LocalLibraryStore
import com.spotyflow.android.data.remote.SpotyHttpClient
import com.spotyflow.android.player.DynamicIslandCommand
import com.spotyflow.android.player.DynamicIslandStateBus
import com.spotyflow.android.player.SpotyFlowPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request

enum class WidgetSizeSpec(
    val layoutRes: Int,
    val bgW: Int,
    val bgH: Int,
    val coverPx: Int,
    val hasTitle: Boolean,
    val hasPrevNext: Boolean,
    val hasProgress: Boolean,
    val hasTime: Boolean,
    val hasLike: Boolean,
    val hasStreak: Boolean
) {
    SIZE_1x1(R.layout.widget_player_1x1, 160, 160, 128, false, false, false, false, false, false),
    SIZE_2x2(R.layout.widget_player_2x2, 260, 260, 112, true, true, false, false, false, false),
    SIZE_2x4(R.layout.widget_player_2x4, 260, 460, 140, true, true, true, false, true, false),
    SIZE_4x1(R.layout.widget_player_4x1, 420, 110, 96, true, true, false, false, false, false),
    SIZE_4x2(R.layout.widget_player_4x2, 420, 220, 118, true, true, true, true, true, true)
}

abstract class SpotyFlowBaseWidgetProvider : AppWidgetProvider() {
    abstract val sizeSpec: WidgetSizeSpec

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgetsAsync(context, forceImmediate = true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_CMD) {
            when (intent.getStringExtra(EXTRA_CMD)) {
                CMD_PLAY_PAUSE -> {
                    val active = SpotyFlowPlaybackService.activeInstance
                    if (active != null) {
                        active.togglePlayPause()
                    } else {
                        val store = LocalLibraryStore(context.applicationContext)
                        val track = store.getRecentTracks().firstOrNull() ?: store.getLikedTracks().firstOrNull()
                        if (track != null) {
                            SpotyFlowPlaybackService.dispatch(context.applicationContext) { svc ->
                                svc.playQueue(listOf(track), 0)
                            }
                        } else {
                            openMainApp(context)
                        }
                    }
                }
                CMD_NEXT -> SpotyFlowPlaybackService.dispatch(context.applicationContext) { it.nextTrack(auto = false) }
                CMD_PREV -> SpotyFlowPlaybackService.dispatch(context.applicationContext) { it.previousTrack() }
                CMD_LIKE -> {
                    val cur = DynamicIslandStateBus.snapshot.value.currentTrack
                        ?: SpotyFlowPlaybackService.playbackState.value.currentTrack
                    if (cur != null) {
                        val store = LocalLibraryStore(context.applicationContext)
                        val updated = store.toggleLike(cur)
                        val liked = updated.any { it.id == cur.id || (cur.videoId.isNotEmpty() && it.videoId == cur.videoId) }
                        DynamicIslandStateBus.update { it.copy(isLiked = liked) }
                        DynamicIslandStateBus.sendCommand(DynamicIslandCommand.ToggleLike)
                    }
                }
            }
            updateAllWidgetsAsync(context, forceImmediate = true)
        }
    }

    companion object {
        const val ACTION_WIDGET_CMD = "com.spotyflow.android.ACTION_WIDGET_CMD"
        private const val EXTRA_CMD = "cmd"
        private const val CMD_PLAY_PAUSE = "play_pause"
        private const val CMD_NEXT = "next"
        private const val CMD_PREV = "prev"
        private const val CMD_LIKE = "like"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var pendingUpdateJob: Job? = null

        @Volatile
        private var lastUpdateMs: Long = 0L

        @Volatile
        private var cachedCoverUrl: String = ""

        @Volatile
        private var cachedCoverBitmap: Bitmap? = null

        private val ALL_PROVIDERS: List<Pair<Class<out SpotyFlowBaseWidgetProvider>, WidgetSizeSpec>> = listOf(
            SpotyFlowPlayer1x1Provider::class.java to WidgetSizeSpec.SIZE_1x1,
            SpotyFlowPlayer2x2Provider::class.java to WidgetSizeSpec.SIZE_2x2,
            SpotyFlowPlayer2x4Provider::class.java to WidgetSizeSpec.SIZE_2x4,
            SpotyFlowPlayer4x1Provider::class.java to WidgetSizeSpec.SIZE_4x1,
            SpotyFlowPlayer4x2Provider::class.java to WidgetSizeSpec.SIZE_4x2
        )

        fun updateAllWidgetsAsync(context: Context, forceImmediate: Boolean = false) {
            val appCtx = context.applicationContext
            pendingUpdateJob?.cancel()
            pendingUpdateJob = widgetScope.launch {
                val now = System.currentTimeMillis()
                val elapsed = now - lastUpdateMs
                if (!forceImmediate && elapsed < 380L) {
                    delay(380L - elapsed)
                }
                lastUpdateMs = System.currentTimeMillis()
                performWidgetRefresh(appCtx)
            }
        }

        private fun performWidgetRefresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val config = WidgetConfigStore.load(context)
            val snap = DynamicIslandStateBus.snapshot.value
            val store = LocalLibraryStore(context)
            val track = snap.currentTrack ?: store.getRecentTracks().firstOrNull() ?: store.getLikedTracks().firstOrNull()
            val streak = store.getDailyStreak()

            val accentArgb = if (config.accentMode == WidgetAccentMode.CUSTOM) {
                config.customAccentArgb
            } else {
                snap.accentColor.toArgb()
            }

            val rawCover = loadCoverBitmapCached(track?.coverUrl.orEmpty())
            val darkText = SpotyFlowWidgetRenderer.isDarkTextStyle(config.style)
            val titleColor = if (darkText) 0xFF0F172A.toInt() else 0xFFFFFFFF.toInt()
            val subColor = if (darkText) 0xFF475569.toInt() else 0xCCF1F5F9.toInt()

            val openAppPi = buildOpenAppIntent(context)

            for ((providerClass, spec) in ALL_PROVIDERS) {
                val comp = ComponentName(context, providerClass)
                val ids = try {
                    manager.getAppWidgetIds(comp)
                } catch (_: Throwable) {
                    IntArray(0)
                }
                if (ids.isEmpty()) continue

                val views = RemoteViews(context.packageName, spec.layoutRes)
                views.setOnClickPendingIntent(R.id.widget_root, openAppPi)

                val bgBmp = SpotyFlowWidgetRenderer.renderBackground(spec.bgW, spec.bgH, config, accentArgb)
                views.setImageViewBitmap(R.id.widget_bg, bgBmp)

                if (config.showCover || spec == WidgetSizeSpec.SIZE_1x1) {
                    views.setViewVisibility(R.id.widget_cover, View.VISIBLE)
                    val coverBmp = SpotyFlowWidgetRenderer.renderShapedCover(
                        source = rawCover,
                        sizePx = spec.coverPx,
                        shape = config.coverShape,
                        accentArgb = accentArgb
                    )
                    views.setImageViewBitmap(R.id.widget_cover, coverBmp)
                } else {
                    views.setViewVisibility(R.id.widget_cover, View.GONE)
                }

                if (spec.hasTitle) {
                    views.setTextViewText(R.id.widget_title, track?.title ?: "SpotyFlow")
                    views.setTextViewText(R.id.widget_artist, track?.artist ?: "Liquid Glass 120 FPS")
                    views.setTextColor(R.id.widget_title, titleColor)
                    views.setTextColor(R.id.widget_artist, subColor)
                    val baseTitleSp = if (spec == WidgetSizeSpec.SIZE_4x2) 14.5f else 13.5f
                    views.setTextViewTextSize(
                        R.id.widget_title,
                        TypedValue.COMPLEX_UNIT_SP,
                        baseTitleSp * config.textSize.scale
                    )
                }

                views.setImageViewResource(
                    R.id.widget_btn_play,
                    if (snap.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                )
                views.setOnClickPendingIntent(R.id.widget_btn_play, buildCmdIntent(context, providerClass, CMD_PLAY_PAUSE, 11))

                if (spec.hasPrevNext) {
                    val vis = if (config.showPrevNext) View.VISIBLE else View.GONE
                    views.setViewVisibility(R.id.widget_btn_prev, vis)
                    views.setViewVisibility(R.id.widget_btn_next, vis)
                    views.setOnClickPendingIntent(R.id.widget_btn_prev, buildCmdIntent(context, providerClass, CMD_PREV, 12))
                    views.setOnClickPendingIntent(R.id.widget_btn_next, buildCmdIntent(context, providerClass, CMD_NEXT, 13))
                }

                if (spec.hasLike) {
                    views.setViewVisibility(R.id.widget_btn_like, if (config.showLike) View.VISIBLE else View.GONE)
                    views.setImageViewResource(
                        R.id.widget_btn_like,
                        if (snap.isLiked) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_like, buildCmdIntent(context, providerClass, CMD_LIKE, 14))
                }

                if (spec.hasProgress) {
                    views.setViewVisibility(R.id.widget_progress, if (config.showProgress) View.VISIBLE else View.GONE)
                    val pct = if (snap.durationMs > 0L) {
                        ((snap.positionMs * 1000L) / snap.durationMs).toInt().coerceIn(0, 1000)
                    } else {
                        0
                    }
                    views.setProgressBar(R.id.widget_progress, 1000, pct, false)
                }

                if (spec.hasTime) {
                    views.setViewVisibility(R.id.widget_time_row, if (config.showTime) View.VISIBLE else View.GONE)
                    views.setTextViewText(R.id.widget_time_cur, formatDurationMs(snap.positionMs))
                    views.setTextViewText(R.id.widget_time_rem, formatRemainingMs(snap.positionMs, snap.durationMs))
                    views.setTextColor(R.id.widget_time_cur, subColor)
                    views.setTextColor(R.id.widget_time_rem, subColor)
                }

                if (spec.hasStreak) {
                    views.setViewVisibility(R.id.widget_streak_row, if (config.showStreak) View.VISIBLE else View.GONE)
                    views.setTextViewText(R.id.widget_streak_text, "${streak.count}")
                }

                try {
                    manager.updateAppWidget(ids, views)
                } catch (_: Throwable) {
                    // Protect against launcher binder hiccups
                }
            }
        }

        private fun loadCoverBitmapCached(url: String): Bitmap? {
            if (url.isBlank()) return null
            if (url == cachedCoverUrl && cachedCoverBitmap != null) return cachedCoverBitmap
            return try {
                val req = Request.Builder().url(url).get().build()
                SpotyHttpClient.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val bytes = resp.body?.bytes() ?: return null
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    if (bmp != null) {
                        cachedCoverUrl = url
                        cachedCoverBitmap = bmp
                    }
                    bmp
                }
            } catch (_: Throwable) {
                null
            }
        }

        private fun buildCmdIntent(
            context: Context,
            providerClass: Class<*>,
            cmd: String,
            reqCode: Int
        ): PendingIntent {
            val intent = Intent(context, providerClass).apply {
                action = ACTION_WIDGET_CMD
                putExtra(EXTRA_CMD, cmd)
            }
            return PendingIntent.getBroadcast(
                context,
                reqCode + providerClass.simpleName.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildOpenAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context,
                2026,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openMainApp(context: Context) {
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(intent)
            } catch (_: Throwable) {
                // Safe ignore
            }
        }
    }
}

class SpotyFlowPlayer1x1Provider : SpotyFlowBaseWidgetProvider() {
    override val sizeSpec: WidgetSizeSpec = WidgetSizeSpec.SIZE_1x1
}

class SpotyFlowPlayer2x2Provider : SpotyFlowBaseWidgetProvider() {
    override val sizeSpec: WidgetSizeSpec = WidgetSizeSpec.SIZE_2x2
}

class SpotyFlowPlayer2x4Provider : SpotyFlowBaseWidgetProvider() {
    override val sizeSpec: WidgetSizeSpec = WidgetSizeSpec.SIZE_2x4
}

class SpotyFlowPlayer4x1Provider : SpotyFlowBaseWidgetProvider() {
    override val sizeSpec: WidgetSizeSpec = WidgetSizeSpec.SIZE_4x1
}

class SpotyFlowPlayer4x2Provider : SpotyFlowBaseWidgetProvider() {
    override val sizeSpec: WidgetSizeSpec = WidgetSizeSpec.SIZE_4x2
}
