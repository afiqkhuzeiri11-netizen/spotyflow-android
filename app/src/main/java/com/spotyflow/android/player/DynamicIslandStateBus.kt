package com.spotyflow.android.player

import androidx.compose.ui.graphics.Color
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.ui.theme.SpotyColors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DynamicIslandSnapshot(
    val currentTrack: TrackItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentLyricLine: String = "",
    val accentColor: Color = SpotyColors.EmeraldCrystal,
    val isLiked: Boolean = false
)

enum class DynamicIslandCommand {
    PlayPause,
    Next,
    Prev,
    ToggleLike,
    OpenApp
}

/**
 * Shared StateFlow & Command bus connecting SpotyFlowPlaybackService + LyricsRepository
 * directly to Stage 3 Custom Accessibility Dynamic Island without coupling services.
 */
object DynamicIslandStateBus {
    private val _snapshot = MutableStateFlow(DynamicIslandSnapshot())
    val snapshot: StateFlow<DynamicIslandSnapshot> = _snapshot.asStateFlow()

    private val _commands = MutableSharedFlow<DynamicIslandCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<DynamicIslandCommand> = _commands.asSharedFlow()

    fun update(transform: (DynamicIslandSnapshot) -> DynamicIslandSnapshot) {
        _snapshot.update(transform)
    }

    fun sendCommand(command: DynamicIslandCommand) {
        _commands.tryEmit(command)
    }
}
