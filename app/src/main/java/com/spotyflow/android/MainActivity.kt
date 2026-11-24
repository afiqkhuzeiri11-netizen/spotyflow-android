package com.spotyflow.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotyflow.android.data.DetailDestination
import com.spotyflow.android.data.PreviewSheetData
import com.spotyflow.android.data.SpotyTab
import com.spotyflow.android.data.SpotyViewModel
import com.spotyflow.android.data.TrackItem
import com.spotyflow.android.data.local.AppSettingsStore
import com.spotyflow.android.data.remote.SpotyHttpClient
import com.spotyflow.android.player.SpotyFlowPlaybackService
import com.spotyflow.android.poster.Poster3DStudioScreen
import com.spotyflow.android.ui.components.SpotyLiquidDock
import com.spotyflow.android.ui.components.SpotyMiniPlayer
import com.spotyflow.android.ui.components.SpotyNowPlayingSheet
import com.spotyflow.android.ui.components.TrackCreditsSheet
import com.spotyflow.android.ui.glass.LocalBgHazeState
import com.spotyflow.android.ui.glass.LocalContentHazeState
import com.spotyflow.android.ui.glass.PureWhiteAmbientCanvas
import com.spotyflow.android.ui.glass.SpotyArtwork
import com.spotyflow.android.ui.glass.SpotyFloatingGlass
import com.spotyflow.android.ui.glass.SpotyGlassCircleButton
import com.spotyflow.android.ui.screens.ArtistProfileScreen
import com.spotyflow.android.ui.screens.CollectionDetailScreen
import com.spotyflow.android.ui.screens.DynamicIslandSettingsScreen
import com.spotyflow.android.ui.screens.EqualizerScreen
import com.spotyflow.android.ui.screens.HomeScreen
import com.spotyflow.android.ui.screens.LibraryScreen
import com.spotyflow.android.ui.screens.ListenTogetherScreen
import com.spotyflow.android.ui.screens.ProfileScreen
import com.spotyflow.android.ui.screens.SearchScreen
import com.spotyflow.android.ui.screens.SettingsRootScreen
import com.spotyflow.android.ui.screens.SongRecognizeScreen
import com.spotyflow.android.ui.screens.StatsRecapScreen
import com.spotyflow.android.ui.theme.SpotyColors
import com.spotyflow.android.ui.theme.SpotySpacing
import com.spotyflow.android.ui.theme.SpotyTheme
import com.spotyflow.android.widget.WidgetSettingsScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions handled gracefully */ }

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

        SpotyFlowPlaybackService.ensureNotificationChannels(this)
        SpotyHttpClient.warmUpAsync()
        AppSettingsStore.load(this)
        requestRuntimePermissionsOnce()

        setContent {
            val appSettings by AppSettingsStore.settings.collectAsState()
            SpotyTheme(
                themeMode = appSettings.themeMode,
                uiStyle = appSettings.uiStyle,
                glassBlurEnabled = appSettings.glassBlurEnabled,
                glassLensEnabled = appSettings.glassLensEnabled,
                accentHex = appSettings.accentHex
            ) {
                SpotyFlowApp()
            }
        }
    }

    private fun requestRuntimePermissionsOnce() {
        val prefs = getSharedPreferences("spotyflow_runtime_perms", MODE_PRIVATE)
        if (prefs.getBoolean("requested_v2", false)) return
        prefs.edit().putBoolean("requested_v2", true).apply()

        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (needed.isNotEmpty()) {
            try {
                permissionLauncher.launch(needed.toTypedArray())
            } catch (_: Throwable) {
                // Non-fatal
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
    val context = LocalContext.current
    val pal = uiState.ambientPalette

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
            // LAYER 0 (Sibling): Adaptive Pure White Pastel Ambient Canvas
            PureWhiteAmbientCanvas(
                orbPrimaryTarget = pal.orbPrimary,
                orbSecondaryTarget = pal.orbSecondary,
                orbTertiaryTarget = pal.orbTertiary,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = bgHazeState)
            )

            // Scrollable Content (Main 4 Tabs OR Full Artist/Collection Profile Detail)
            CompositionLocalProvider(LocalBgHazeState provides bgHazeState) {
                val activeDetail = uiState.currentDetail
                if (activeDetail != null) {
                    when (activeDetail) {
                        is DetailDestination.Artist -> ArtistProfileScreen(
                            fallbackName = activeDetail.artistName,
                            profile = uiState.loadedArtistProfile,
                            isLoading = uiState.isArtistLoading,
                            isFollowed = vm.isArtistFollowed(
                                uiState.loadedArtistProfile?.browseId ?: activeDetail.browseId,
                                uiState.loadedArtistProfile?.name ?: activeDetail.artistName
                            ),
                            currentTrackId = uiState.currentTrack?.id,
                            isPlaying = uiState.isPlaying,
                            onBack = { vm.popDetail() },
                            onPlayAll = { tracks, shuf -> vm.playQueue(tracks, 0, shuf) },
                            onPlayTrack = vm::playTrack,
                            onToggleFollow = vm::toggleFollowArtist,
                            onOpenCollection = { col -> vm.openCollectionDetail(col.browseId, col.title) },
                            onOpenRelatedArtist = { rel -> vm.openArtistProfile(rel.browseId, rel.name) },
                            onOpenTrackMenu = vm::openTrackMenu
                        )

                        is DetailDestination.Collection -> CollectionDetailScreen(
                            fallbackTitle = activeDetail.fallbackTitle,
                            detail = uiState.loadedCollection,
                            isLoading = uiState.isCollectionLoading,
                            isSaved = vm.isCollectionSaved(activeDetail.browseId),
                            currentTrackId = uiState.currentTrack?.id,
                            isPlaying = uiState.isPlaying,
                            onBack = { vm.popDetail() },
                            onPlayAll = { tracks, shuf -> vm.playQueue(tracks, 0, shuf) },
                            onPlayTrack = vm::playTrack,
                            onToggleSave = vm::toggleSaveCollection,
                            onDownloadAll = vm::downloadTracksBatch,
                            onOpenArtist = { bId, name -> vm.openArtistProfile(bId, name) },
                            onOpenTrackMenu = vm::openTrackMenu
                        )

                        DetailDestination.Settings -> SettingsRootScreen(
                            onBack = { vm.popDetail() },
                            onOpenDynamicIsland = vm::openDynamicIslandSettings,
                            onOpenWidgetSettings = vm::openWidgetSettings,
                            onOpenEqualizer = vm::openEqualizer,
                            onImportPlaylistUrl = vm::importPlaylistFromUrl,
                            onClearHistory = vm::clearAllHistory,
                            onShowToast = vm::showToast
                        )

                        DetailDestination.DynamicIsland -> DynamicIslandSettingsScreen(
                            uiState = uiState,
                            onBack = { vm.popDetail() },
                            onShowToast = vm::showToast
                        )

                        DetailDestination.WidgetSettings -> WidgetSettingsScreen(
                            uiState = uiState,
                            onBack = { vm.popDetail() },
                            onShowToast = vm::showToast
                        )

                        DetailDestination.Equalizer -> EqualizerScreen(
                            onBack = { vm.popDetail() }
                        )

                        DetailDestination.SongRecognize -> SongRecognizeScreen(
                            onBack = { vm.popDetail() },
                            onPlayTrack = vm::playTrack,
                            onShowToast = vm::showToast
                        )

                        DetailDestination.ListenTogether -> ListenTogetherScreen(
                            uiState = uiState,
                            onBack = { vm.popDetail() },
                            onPlayTrack = vm::playTrack,
                            onShowToast = vm::showToast
                        )

                        DetailDestination.StatsRecap -> StatsRecapScreen(
                            uiState = uiState,
                            topTracks = vm.getTop50Tracks(),
                            onBack = { vm.popDetail() },
                            onPlayTrack = vm::playTrack,
                            onShowToast = vm::showToast
                        )

                        is DetailDestination.Poster3D -> Poster3DStudioScreen(
                            uiState = uiState,
                            initialTrack = activeDetail.initialTrack,
                            onBack = { vm.popDetail() },
                            onShowToast = vm::showToast
                        )
                    }
                } else {
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
                                onOpenTrackMenu = vm::openTrackMenu,
                                onOpenPreview = vm::openPreviewSheet
                            )

                            SpotyTab.Search -> SearchScreen(
                                uiState = uiState,
                                topHeaderClearance = topHeaderClearance,
                                onQueryChange = vm::updateSearchQuery,
                                onFillQuery = vm::fillSearchQueryWithoutSubmit,
                                onSubmitQuery = vm::submitSearchQuery,
                                onRemoveHistory = vm::removeSearchHistory,
                                onSelectFilter = vm::selectSearchFilter,
                                onLoadMore = vm::loadMoreSearchResults,
                                onPlayTrack = vm::playTrack,
                                onOpenArtist = { a -> vm.openArtistProfile(a.browseId, a.name) },
                                onOpenCollection = { c -> vm.openCollectionDetail(c.browseId, c.title) },
                                onOpenTrackMenu = vm::openTrackMenu
                            )

                            SpotyTab.Library -> LibraryScreen(
                                uiState = uiState,
                                topHeaderClearance = topHeaderClearance,
                                onOpenLocalCollection = vm::openLocalCollection,
                                onOpenCustomPlaylist = vm::openCustomPlaylist,
                                onOpenSavedCollection = { c -> vm.openCollectionDetail(c.browseId, c.title) },
                                onOpenArtist = { a -> vm.openArtistProfile(a.browseId, a.name) },
                                onPlayTrack = vm::playTrack,
                                onCreatePlaylist = { title -> vm.createPlaylist(title) },
                                onDeletePlaylist = vm::deletePlaylist,
                                onImportPlaylistUrl = vm::importPlaylistFromUrl,
                                onAddUploadedUris = vm::addUploadedAudioUris
                            )

                            SpotyTab.Profile -> ProfileScreen(
                                uiState = uiState,
                                topHeaderClearance = topHeaderClearance,
                                onUpdateProfile = vm::updateUserProfile,
                                onUpdateAvatar = vm::updateUserAvatar,
                                onOpenLocalCollection = vm::openLocalCollection,
                                onOpenArtist = { a -> vm.openArtistProfile(a.browseId, a.name) },
                                onOpenLibraryTab = { vm.selectTab(SpotyTab.Library) },
                                onOpenPoster3D = { vm.openPoster3D() },
                                onOpenStatsRecap = vm::openStatsRecap,
                                onOpenListenTogether = vm::openListenTogether,
                                onOpenSongRecognize = vm::openSongRecognize,
                                onOpenEqualizer = vm::openEqualizer,
                                onOpenDynamicIsland = vm::openDynamicIslandSettings,
                                onOpenWidgetSettings = vm::openWidgetSettings,
                                onOpenSettings = vm::openSettings
                            )
                        }
                    }
                }
            }
        }

        // =====================================================================
        // LAYER 2 (Sibling of Layer 1 -> Real 120 FPS Floating Liquid Glass!)
        // =====================================================================
        CompositionLocalProvider(LocalContentHazeState provides contentHazeState) {
            // Floating Top Header (only shown on main 4 tabs; detail screens have their own back header)
            if (uiState.currentDetail == null) {
                TopGlassBar(
                    activeTab = uiState.activeTab,
                    onRecognizeClick = vm::openSongRecognize,
                    onSettingsClick = vm::openSettings,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = SpotySpacing.ScreenHorizontal, vertical = 10.dp)
                )
            }

            // Floating Bottom Stack: Mini-Player + 10.dp Gap + Real Water Drops Liquid Glass Navbar
            // Hidden inside fullscreen Poster3DStudioScreen so user gets unobstructed canvas!
            if (uiState.currentDetail !is DetailDestination.Poster3D) {
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
                        onSelectTab = vm::selectTab,
                        waterDropsEnabled = uiState.appSettings.waterDropsEnabled,
                        waterDropDensity = uiState.appSettings.waterDropsDensity,
                        glassLensEnabled = uiState.appSettings.glassLensEnabled
                    )
                }
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
                    onSeekMs = vm::seekToMs,
                    onToggleLike = { vm.toggleLike() },
                    onToggleShuffle = vm::toggleShuffle,
                    onCycleRepeat = vm::cycleRepeat,
                    onSetPlayerMode = vm::setPlayerMode,
                    onSetVolume = vm::setVolume,
                    onToggleLyrics = vm::toggleLyricsView,
                    onOpenManualLyrics = { vm.setManualLyricsOpen(true) },
                    onOpenQueue = { vm.setQueueSheetOpen(true) },
                    onOpenArtist = { bId, name -> vm.openArtistProfile(bId, name) },
                    onOpenTrackMenu = vm::openTrackMenu,
                    onOpenPoster3D = { t -> vm.openPoster3D(t) },
                    onStartSleepTimer = vm::startSleepTimer,
                    onOpenPreview = vm::openPreviewSheet
                )
            }

            // Queue Bottom Sheet
            AnimatedVisibility(
                visible = uiState.isQueueSheetOpen,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                QueueGlassSheet(
                    queue = uiState.queue,
                    currentIdx = uiState.queueIndex,
                    onSelectIndex = { idx ->
                        uiState.queue.getOrNull(idx)?.let { vm.playTrack(it, uiState.queue) }
                        vm.setQueueSheetOpen(false)
                    },
                    onDismiss = { vm.setQueueSheetOpen(false) }
                )
            }

            // Track Context Menu Sheet (⋮)
            AnimatedVisibility(
                visible = uiState.menuTrack != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                uiState.menuTrack?.let { track ->
                    val isLiked = uiState.likedTracks.any { it.id == track.id }
                    TrackMenuGlassSheet(
                        track = track,
                        isLiked = isLiked,
                        onDismiss = vm::closeTrackMenu,
                        onPlayNext = {
                            vm.addTrackNext(track)
                            vm.closeTrackMenu()
                        },
                        onAddToQueue = {
                            vm.addTrackToQueue(track)
                            vm.closeTrackMenu()
                        },
                        onToggleLike = {
                            vm.toggleLike(track)
                            vm.closeTrackMenu()
                        },
                        onOpenArtist = {
                            vm.openArtistProfile(track.artistBrowseId, track.artist)
                        },
                        onOpenAlbum = {
                            vm.openAlbumForTrack(track)
                        },
                        onOpenCredits = {
                            vm.openTrackCredits(track)
                        },
                        onStartRadio = {
                            vm.closeTrackMenu()
                            vm.startRadioForTrack(track)
                        },
                        onDownloadOffline = {
                            vm.closeTrackMenu()
                            vm.downloadTrackOffline(track)
                        },
                        onOpenPoster3D = {
                            vm.openPoster3D(track)
                        },
                        onOpenWidgetSettings = {
                            vm.openWidgetSettings()
                        },
                        onShare = {
                            vm.closeTrackMenu()
                            try {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "${track.title} — ${track.artist}\nhttps://music.youtube.com/watch?v=${track.videoId}"
                                    )
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Bagikan Lagu"))
                            } catch (_: Throwable) {
                                // Ignore
                            }
                        }
                    )
                }
            }

            // Official Track Credits & Info Sheet
            AnimatedVisibility(
                visible = uiState.creditsTrack != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                uiState.creditsTrack?.let { cTrack ->
                    TrackCreditsSheet(
                        track = cTrack,
                        credits = uiState.loadedTrackCredits,
                        isLoading = uiState.isCreditsLoading,
                        onDismiss = vm::closeTrackCredits,
                        onOpenAlbum = { t -> vm.openAlbumForTrack(t) },
                        onOpenArtist = { bId, name ->
                            vm.closeTrackCredits()
                            vm.openArtistProfile(bId, name)
                        }
                    )
                }
            }

            // Preview Bottom Sheet
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

            // Floating Toast Pill
            AnimatedVisibility(
                visible = uiState.toastMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 166.dp)
            ) {
                uiState.toastMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(SpotyColors.Slate900)
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = msg,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Manual Lyrics Search Dialog
    if (uiState.isManualLyricsOpen) {
        val cur = uiState.currentTrack
        var qTitle by remember(cur?.id) { mutableStateOf(cur?.title.orEmpty()) }
        var qArtist by remember(cur?.id) { mutableStateOf(cur?.artist.orEmpty()) }
        AlertDialog(
            onDismissRequest = { vm.setManualLyricsOpen(false) },
            containerColor = Color.White,
            title = { Text("Cari Lirik Manual", fontWeight = FontWeight.ExtraBold, color = SpotyColors.Slate900) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = qTitle,
                        onValueChange = { qTitle = it },
                        label = { Text("Judul Lagu") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = qArtist,
                        onValueChange = { qArtist = it },
                        label = { Text("Nama Artis") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.searchLyricsManual(qTitle, qArtist) }) {
                    Text("Cari", fontWeight = FontWeight.Bold, color = SpotyColors.DeepEmerald)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.setManualLyricsOpen(false) }) {
                    Text("Batal", color = SpotyColors.Slate500)
                }
            }
        )
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
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) {
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
private fun QueueGlassSheet(
    queue: List<TrackItem>,
    currentIdx: Int,
    onSelectIndex: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotyColors.Slate900.copy(alpha = 0.28f))
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
                .padding(14.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(28.dp),
            tintAlpha = 0.90f,
            elevation = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Antrean (${queue.size} lagu)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SpotyColors.Slate900
                    )
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
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(queue) { idx, t ->
                        val active = idx == currentIdx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (active) SpotyColors.EmeraldTint else Color.Transparent)
                                .clickable { onSelectIndex(idx) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SpotyArtwork(
                                coverUrl = t.coverUrl,
                                accentColor = t.accentColor,
                                size = 42.dp,
                                cornerRadius = 10.dp
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = t.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) SpotyColors.DeepEmerald else SpotyColors.Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = t.artist,
                                    fontSize = 12.sp,
                                    color = SpotyColors.Slate500,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackMenuGlassSheet(
    track: TrackItem,
    isLiked: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenCredits: () -> Unit,
    onStartRadio: () -> Unit,
    onDownloadOffline: () -> Unit,
    onOpenPoster3D: () -> Unit,
    onOpenWidgetSettings: () -> Unit,
    onShare: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotyColors.Slate900.copy(alpha = 0.28f))
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
                .padding(14.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(28.dp),
            tintAlpha = 0.90f,
            elevation = 22.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpotyArtwork(
                        coverUrl = track.coverUrl,
                        accentColor = track.accentColor,
                        size = 48.dp,
                        cornerRadius = 12.dp
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SpotyColors.Slate900,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            fontSize = 12.5.sp,
                            color = SpotyColors.Slate500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                Spacer(modifier = Modifier.height(8.dp))
                MenuSheetActionRow(Icons.Rounded.QueuePlayNext, "Putar Berikutnya", onPlayNext)
                MenuSheetActionRow(Icons.Rounded.PlaylistAdd, "Tambah ke Antrean", onAddToQueue)
                MenuSheetActionRow(
                    if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (isLiked) "Hapus dari Disukai" else "Tambahkan ke Disukai",
                    onToggleLike
                )
                MenuSheetActionRow(Icons.Rounded.Person, "Lihat Profil Artis", onOpenArtist)
                MenuSheetActionRow(
                    Icons.Rounded.Album,
                    if (track.album.isNotBlank()) "Lihat Album (${track.album})" else "Lihat Album",
                    onOpenAlbum
                )
                MenuSheetActionRow(Icons.Rounded.Info, "Kredit & Info Lagu", onOpenCredits)
                MenuSheetActionRow(Icons.Rounded.Radio, "Mulai Radio Lagu Ini", onStartRadio)
                MenuSheetActionRow(Icons.Rounded.Download, "Unduh Offline (M4A)", onDownloadOffline)
                MenuSheetActionRow(Icons.Rounded.AutoAwesome, "Buat Poster Lagu 3D", onOpenPoster3D)
                MenuSheetActionRow(Icons.Rounded.Widgets, "Pengaturan Widget", onOpenWidgetSettings)
                MenuSheetActionRow(Icons.Rounded.Share, "Bagikan Lagu", onShare)
            }
        }
    }
}

@Composable
private fun MenuSheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = SpotyColors.Slate900,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = SpotyColors.Slate900
        )
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
            tintAlpha = 0.88f,
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
