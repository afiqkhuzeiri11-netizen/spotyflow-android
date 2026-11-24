package com.spotyflow.android.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.spotyflow.android.data.local.LocalLibraryStore

/**
 * Triggered by AlarmManager for scheduled SpotyFlow Music Alarms.
 * Starts soft fade-in playback of the user's liked/recent track.
 */
class SpotyFlowAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fadeInSec = intent.getIntExtra("fade_in_sec", 12).coerceIn(3, 30)
        val appCtx = context.applicationContext
        val store = LocalLibraryStore(appCtx)
        val tracks = store.getLikedTracks().ifEmpty { store.getRecentTracks() }
        if (tracks.isEmpty()) return

        SpotyFlowPlaybackService.dispatch(appCtx) { svc ->
            svc.playAlarmWithFadeIn(tracks, fadeInSec)
        }
    }
}
