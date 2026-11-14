package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.MainDestination
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.LocalLibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.QueueBottomSheet
import com.example.ui.screens.YouTubeMusicScreen
import com.example.ui.theme.SynthWaveTheme
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.NowPlayingViewModel
import com.example.ui.viewmodel.SynthWaveViewModelFactory
import com.example.ui.viewmodel.YouTubeViewModel
import com.example.ui.visualizer.AmbientRgbBackground
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SynthWaveApplication
        val factory = SynthWaveViewModelFactory(app)

        setContent {
            SynthWaveTheme {
                SynthWaveRoot(app = app, factory = factory)
            }
        }
    }
}

@Composable
private fun SynthWaveRoot(
    app: SynthWaveApplication,
    factory: SynthWaveViewModelFactory
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val libraryViewModel: LibraryViewModel = viewModel(factory = factory)
    val youTubeViewModel: YouTubeViewModel = viewModel(factory = factory)
    val nowPlayingViewModel: NowPlayingViewModel = viewModel(factory = factory)

    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val youtubeState by youTubeViewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by nowPlayingViewModel.playbackState.collectAsStateWithLifecycle()
    val spectrumFrame by nowPlayingViewModel.spectrumFrame.collectAsStateWithLifecycle()
    val lyricsState by nowPlayingViewModel.lyricsUiState.collectAsStateWithLifecycle()
    val favoriteIds by nowPlayingViewModel.favoriteIds.collectAsStateWithLifecycle()

    var currentDestination by remember { mutableStateOf(MainDestination.LOCAL_LIBRARY) }
    var showQueueSheet by remember { mutableStateOf(false) }

    val storagePermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun syncPermissionsState() {
        val hasStorage = ContextCompat.checkSelfPermission(
            context,
            storagePermission
        ) == PackageManager.PERMISSION_GRANTED
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        libraryViewModel.updatePermissions(storageGranted = hasStorage, recordAudioGranted = hasAudio)
        app.playbackCoordinator.updateRecordAudioPermission(hasAudio)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        syncPermissionsState()
    }

    LaunchedEffect(Unit) {
        syncPermissionsState()
    }

    val allNeededPermissions = remember {
        buildList {
            add(storagePermission)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val storageOnlyPermissions = remember {
        buildList {
            add(storagePermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    var hasStartedYouTube by remember { mutableStateOf(false) }
    LaunchedEffect(playbackState.isYouTubeActive) {
        if (playbackState.isYouTubeActive) {
            hasStartedYouTube = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Global Ambient RGB Mesh Backdrop
        AmbientRgbBackground(
            spectrumFrame = spectrumFrame,
            isPlaying = playbackState.isPlaying
        )

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
                        .navigationBarsPadding()
                ) {
                    val activeTrack = playbackState.currentTrack
                    if (activeTrack != null) {
                        MiniPlayerBar(
                            track = activeTrack,
                            isPlaying = playbackState.isPlaying,
                            isFavorite = activeTrack.id in favoriteIds,
                            positionMs = playbackState.positionMs,
                            durationMs = playbackState.durationMs,
                            loadEmbeddedArt = { libraryViewModel.loadEmbeddedArt(it) },
                            onExpand = { nowPlayingViewModel.setNowPlayingExpanded(true) },
                            onTogglePlayPause = { nowPlayingViewModel.togglePlayPause() },
                            onSkipNext = { nowPlayingViewModel.skipNext() },
                            onToggleFavorite = { nowPlayingViewModel.toggleFavoriteCurrent() }
                        )
                    }

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = currentDestination == MainDestination.LOCAL_LIBRARY,
                            onClick = { currentDestination = MainDestination.LOCAL_LIBRARY },
                            icon = {
                                Icon(
                                    imageVector = if (currentDestination == MainDestination.LOCAL_LIBRARY) {
                                        Icons.Rounded.LibraryMusic
                                    } else {
                                        Icons.Outlined.LibraryMusic
                                    },
                                    contentDescription = stringResource(R.string.nav_local_library)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.nav_local_library),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_local_library")
                        )

                        NavigationBarItem(
                            selected = currentDestination == MainDestination.YOUTUBE_MUSIC,
                            onClick = { currentDestination = MainDestination.YOUTUBE_MUSIC },
                            icon = {
                                Icon(
                                    imageVector = if (currentDestination == MainDestination.YOUTUBE_MUSIC) {
                                        Icons.Rounded.PlayCircle
                                    } else {
                                        Icons.Outlined.PlayCircle
                                    },
                                    contentDescription = stringResource(R.string.nav_youtube_music)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.nav_youtube_music),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.secondary,
                                selectedTextColor = MaterialTheme.colorScheme.secondary,
                                indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_youtube_music")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .displayCutoutPadding()
            ) {
                when (currentDestination) {
                    MainDestination.LOCAL_LIBRARY -> {
                        LocalLibraryScreen(
                            uiState = libraryState,
                            currentTrackId = playbackState.currentTrack?.id,
                            onSearchQueryChange = libraryViewModel::onSearchQueryChange,
                            onSelectTab = libraryViewModel::selectTab,
                            onToggleFavoritesFilter = libraryViewModel::toggleFavoritesFilter,
                            onToggleRecentsFilter = libraryViewModel::toggleRecentsFilter,
                            onRequestAllPermissions = {
                                permissionsLauncher.launch(allNeededPermissions)
                            },
                            onRequestStorageOnly = {
                                permissionsLauncher.launch(storageOnlyPermissions)
                            },
                            onRescanLibrary = libraryViewModel::scanLibrary,
                            onPlayTrack = { track, queue ->
                                app.playbackCoordinator.playTrack(track, queue)
                            },
                            onPlayNext = { track -> app.playbackCoordinator.playNext(track) },
                            onAddToQueue = { track -> app.playbackCoordinator.addToQueue(track) },
                            onToggleFavorite = libraryViewModel::toggleFavorite,
                            onCreatePlaylist = libraryViewModel::createPlaylist,
                            onDeletePlaylist = libraryViewModel::deletePlaylist,
                            onAddTrackToPlaylist = libraryViewModel::addTrackToPlaylist,
                            onRemoveTrackFromPlaylist = libraryViewModel::removeTrackFromPlaylist,
                            getPlaylistTracksFlow = libraryViewModel::getTracksForPlaylist,
                            loadEmbeddedArt = { libraryViewModel.loadEmbeddedArt(it) },
                            onOpenQueue = { showQueueSheet = true },
                            bottomPadding = innerPadding.calculateBottomPadding()
                        )
                    }

                    MainDestination.YOUTUBE_MUSIC -> {
                        YouTubeMusicScreen(
                            uiState = youtubeState,
                            currentTrackId = playbackState.currentTrack?.id,
                            favoriteIds = libraryState.favoriteIds,
                            playlists = libraryState.playlists,
                            onQueryChange = youTubeViewModel::onQueryChange,
                            onSearchSubmit = youTubeViewModel::performSearch,
                            onSaveApiKey = youTubeViewModel::saveApiKey,
                            onResetToDemo = youTubeViewModel::resetToDemoStreams,
                            onPlayTrack = { track, queue ->
                                app.playbackCoordinator.playTrack(track, queue)
                            },
                            onPlayNext = { track -> app.playbackCoordinator.playNext(track) },
                            onAddToQueue = { track -> app.playbackCoordinator.addToQueue(track) },
                            onToggleFavorite = libraryViewModel::toggleFavorite,
                            onAddTrackToPlaylist = libraryViewModel::addTrackToPlaylist,
                            onCreatePlaylist = libraryViewModel::createPlaylist,
                            loadEmbeddedArt = { libraryViewModel.loadEmbeddedArt(it) },
                            bottomPadding = innerPadding.calculateBottomPadding()
                        )
                    }
                }
            }
        }

        // Full-Screen Expanding Now Playing Visualizer Sheet
        AnimatedVisibility(
            visible = playbackState.isNowPlayingExpanded && playbackState.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.zIndex(10f)
        ) {
            NowPlayingScreen(
                playbackState = playbackState,
                spectrumFrame = spectrumFrame,
                lyricsUiState = lyricsState,
                isFavorite = playbackState.currentTrack?.id in favoriteIds,
                loadEmbeddedArt = { libraryViewModel.loadEmbeddedArt(it) },
                onCollapse = { nowPlayingViewModel.setNowPlayingExpanded(false) },
                onSelectVisualizerMode = nowPlayingViewModel::setVisualizerMode,
                onToggleVideoPreview = nowPlayingViewModel::toggleVideoPreviewExpanded,
                onTogglePlayPause = nowPlayingViewModel::togglePlayPause,
                onSkipNext = nowPlayingViewModel::skipNext,
                onSkipPrevious = nowPlayingViewModel::skipPrevious,
                onSeekTo = nowPlayingViewModel::seekTo,
                onToggleShuffle = nowPlayingViewModel::toggleShuffle,
                onCycleRepeat = nowPlayingViewModel::cycleRepeatMode,
                onToggleFavorite = nowPlayingViewModel::toggleFavoriteCurrent,
                onOpenQueue = { showQueueSheet = true },
                onSaveCustomLyrics = nowPlayingViewModel::saveCustomLyrics,
                onImportLyricsUri = nowPlayingViewModel::importLyricsFromUri,
                onClearCustomLyrics = nowPlayingViewModel::clearCustomLyrics
            )
        }

        // Persistent YouTubePlayerView: lazily initialized on first YouTube play, stays alive in audio-focused mode and expands smoothly when requested
        if (hasStartedYouTube) {
            val youTubePlayerView = remember {
                YouTubePlayerView(context).apply {
                    enableAutomaticInitialization = true
                    app.playbackCoordinator.youTubeBridge.attachPlayerView(this)
                }
            }

            DisposableEffect(lifecycleOwner, youTubePlayerView) {
                lifecycleOwner.lifecycle.addObserver(youTubePlayerView)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(youTubePlayerView)
                    youTubePlayerView.release()
                }
            }

            val showExpandedVideo = playbackState.isNowPlayingExpanded &&
                playbackState.isYouTubeActive &&
                playbackState.isVideoPreviewExpanded

            Box(
                modifier = if (showExpandedVideo) {
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 120.dp)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                        .zIndex(20f)
                } else {
                    Modifier
                        .align(Alignment.TopStart)
                        .size(1.dp)
                        .alpha(0.01f)
                        .zIndex(-1f)
                }
            ) {
                AndroidView(
                    factory = { youTubePlayerView },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (showQueueSheet) {
            QueueBottomSheet(
                queue = playbackState.queue,
                currentIndex = playbackState.currentIndex,
                loadEmbeddedArt = { libraryViewModel.loadEmbeddedArt(it) },
                onDismiss = { showQueueSheet = false },
                onJumpToIndex = { idx ->
                    nowPlayingViewModel.jumpToQueueIndex(idx)
                },
                onRemoveIndex = { idx ->
                    nowPlayingViewModel.removeFromQueue(idx)
                }
            )
        }
    }
}
