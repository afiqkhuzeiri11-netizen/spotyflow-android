package com.spotyflow.android

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotyflow.android.data.PreviewSheetData
import com.spotyflow.android.data.SpotyTab
import com.spotyflow.android.data.SpotyViewModel
import com.spotyflow.android.ui.components.SpotyLiquidDock
import com.spotyflow.android.ui.components.SpotyMiniPlayer
import com.spotyflow.android.ui.components.SpotyNowPlayingSheet
import com.spotyflow.android.ui.glass.LocalBgHazeState
import com.spotyflow.android.ui.glass.LocalContentHazeState
import com.spotyflow.android.ui.glass.PureWhiteAmbientCanvas
import com.spotyflow.android.ui.glass.SpotyFloatingGlass
import com.spotyflow.android.ui.glass.SpotyGlassCircleButton
import com.spotyflow.android.ui.screens.HomeScreen
import com.spotyflow.android.ui.screens.LibraryScreen
import com.spotyflow.android.ui.screens.ProfileScreen
import com.spotyflow.android.ui.screens.SearchScreen
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing
import com.spotyflow.android.ui.theme.SpotyTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()

        setContent {
            SpotyTheme {
                SpotyFlowApp()
            }
        }
    }

    private fun requestHighestRefreshRate() {
        try {
            val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val bestMode = activeDisplay?.supportedModes?.maxByOrNull { it.refreshRate }
            if (bestMode != null) {
                val params = window.attributes
                params.preferredDisplayModeId = bestMode.modeId
                window.attributes = params
            }
        } catch (_: Throwable) {
            // Safely ignore on OEM devices that lock display mode switching
        }
    }
}

private fun dynamicGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 4..10 -> "Selamat pagi"
        in 11..14 -> "Selamat siang"
        in 15..18 -> "Selamat sore"
        else -> "Selamat malam"
    }
}

@Composable
fun SpotyFlowApp(vm: SpotyViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsState()
    val activeTrack = uiState.currentTrack

    // Two strictly separated sibling HazeStates (0% Haze 1.6.10 runtime crash!)
    val bgHazeState = remember { HazeState() }
    val contentHazeState = remember { HazeState() }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topHeaderClearance = statusBarTop + 88.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // =====================================================================
        // LAYER 1 (Captures background + scrolled content for Floating Chrome)
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = contentHazeState)
        ) {
            // LAYER 0 (Sibling): Pure White Pastel Ambient Canvas
            PureWhiteAmbientCanvas(
                orbPrimaryTarget = activeTrack.orbPrimary,
                orbSecondaryTarget = activeTrack.orbSecondary,
                orbTertiaryTarget = activeTrack.orbTertiary,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = bgHazeState)
            )

            // Scrollable Tab Content (Sibling of Layer 0 -> safely blurs bgHazeState!)
            CompositionLocalProvider(LocalBgHazeState provides bgHazeState) {
                AnimatedContent(
                    targetState = uiState.activeTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tabTransition",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        SpotyTab.Home -> HomeScreen(
                            uiState = uiState,
                            topHeaderClearance = topHeaderClearance,
                            onSelectMood = vm::selectMood,
                            onPlayTrack = vm::playTrack,
                            onTogglePlayPause = vm::togglePlayPause,
                            onOpenNowPlaying = { vm.openNowPlaying(false) },
                            onOpenPreview = vm::openPreviewSheet
                        )

                        SpotyTab.Search -> SearchScreen(
                            uiState = uiState,
                            topHeaderClearance = topHeaderClearance,
                            onQueryChange = vm::updateSearchQuery,
                            onSubmitQuery = vm::submitSearchQuery,
                            onRemoveHistory = vm::removeSearchHistory,
                            onSelectFilter = vm::selectSearchFilter,
                            onPlayTrack = vm::playTrack
                        )

                        SpotyTab.Library -> LibraryScreen(
                            uiState = uiState,
                            topHeaderClearance = topHeaderClearance,
                            onOpenPreview = vm::openPreviewSheet
                        )

                        SpotyTab.Profile -> ProfileScreen(
                            uiState = uiState,
                            topHeaderClearance = topHeaderClearance,
                            onOpenPreview = vm::openPreviewSheet
                        )
                    }
                }
            }
        }

        // =====================================================================
        // LAYER 2 (Sibling of Layer 1 -> Real 120 FPS Floating Liquid Glass!)
        // =====================================================================
        CompositionLocalProvider(LocalContentHazeState provides contentHazeState) {
            // Floating Minimalist Top Glass Header
            TopGlassBar(
                activeTab = uiState.activeTab,
                onRecognizeClick = {
                    vm.openPreviewSheet(
                        PreviewSheetData(
                            title = "Kenali Lagu",
                            subtitle = "Deteksi lagu di sekitarmu lewat mikrofon atau potongan lirik",
                            badge = "AUDIO RADAR",
                            bullets = listOf(
                                "Analisis suara cepat dengan gelombang radar real-time",
                                "Langsung putar & simpan ke Pustaka (Tahap 2)"
                            )
                        )
                    )
                },
                onSettingsClick = {
                    vm.selectTab(SpotyTab.Profile)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = SpotySpacing.ScreenHorizontal, vertical = 10.dp)
            )

            // Floating Bottom Stack: Mini-Player + 10.dp Gap + Interactive Draggable Lens Dock
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SpotyMiniPlayer(
                    uiState = uiState,
                    onOpenNowPlaying = { vm.openNowPlaying(false) },
                    onToggleLike = { vm.toggleLike() },
                    onTogglePlayPause = vm::togglePlayPause,
                    onNext = vm::nextTrack
                )

                SpotyLiquidDock(
                    activeTab = uiState.activeTab,
                    onSelectTab = vm::selectTab
                )
            }

            // Fullscreen Interactive Now Playing Sheet
            AnimatedVisibility(
                visible = uiState.isNowPlayingOpen,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeOut()
            ) {
                SpotyNowPlayingSheet(
                    uiState = uiState,
                    onDismiss = vm::closeNowPlaying,
                    onTogglePlayPause = vm::togglePlayPause,
                    onNext = vm::nextTrack,
                    onPrevious = vm::previousTrack,
                    onSeekFraction = vm::seekToFraction,
                    onSeekSecond = vm::seekToSecond,
                    onToggleLike = { vm.toggleLike() },
                    onToggleShuffle = vm::toggleShuffle,
                    onCycleRepeat = vm::cycleRepeat,
                    onSetPlayerMode = vm::setPlayerMode,
                    onSetVolume = vm::setVolume,
                    onToggleLyrics = vm::toggleLyricsView,
                    onOpenPreview = vm::openPreviewSheet
                )
            }

            // Glass Preview Bottom Sheet (ensures zero dead buttons across the app)
            AnimatedVisibility(
                visible = uiState.activePreviewSheet != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                uiState.activePreviewSheet?.let { sheetData ->
                    GlassPreviewSheetOverlay(
                        data = sheetData,
                        onDismiss = vm::closePreviewSheet
                    )
                }
            }
        }
    }
}

@Composable
private fun TopGlassBar(
    activeTab: SpotyTab,
    onRecognizeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heading = remember(activeTab) {
        when (activeTab) {
            SpotyTab.Home -> dynamicGreeting()
            SpotyTab.Search -> "Cari Musik"
            SpotyTab.Library -> "Pustaka"
            SpotyTab.Profile -> "Profil Kamu"
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpotyFloatingGlass(
            shape = RoundedCornerShape(22.dp),
            tintAlpha = 0.56f,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "SPOTYFLOW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp,
                    color = SpotyColors.EmeraldCrystal
                )
                Text(
                    text = heading,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    color = SpotyColors.Slate900,
                    maxLines = 1
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpotyGlassCircleButton(onClick = onRecognizeClick, size = 42.dp) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Kenali Lagu",
                    tint = SpotyColors.Slate900,
                    modifier = Modifier.size(20.dp)
                )
            }
            SpotyGlassCircleButton(onClick = onSettingsClick, size = 42.dp) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Pengaturan",
                    tint = SpotyColors.Slate900,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun GlassPreviewSheetOverlay(
    data: PreviewSheetData,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotyColors.Slate900.copy(alpha = 0.24f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        SpotyFloatingGlass(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(28.dp),
            tintAlpha = 0.86f,
            elevation = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(SpotyColors.EmeraldTint)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = data.badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.1.sp,
                            color = SpotyColors.DeepEmerald
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SpotyColors.Slate900.copy(alpha = 0.08f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup",
                            tint = SpotyColors.Slate900,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = data.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SpotyColors.Slate900
                    )
                    Text(
                        text = data.subtitle,
                        fontSize = 13.5.sp,
                        color = SpotyColors.Slate500
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.bullets.forEach { bullet ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = SpotyColors.EmeraldCrystal,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = bullet,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = SpotyColors.Slate700
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(SpotyColors.Slate900)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Mengerti",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
