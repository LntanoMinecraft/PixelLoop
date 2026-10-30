package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.GifRepository
import com.example.engine.VideoFrameProcessor
import com.example.service.LiveUpdateNotificationHelper
import com.example.service.ScreenRecordService
import com.example.ui.StudioTab
import com.example.ui.StudioViewModel
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.PixelLoopTheme
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LiveUpdateNotificationHelper.ensureChannel(this)

        val database = AppDatabase.getInstance(applicationContext)
        val repository = GifRepository(applicationContext, database.gifDao())
        val frameProcessor = VideoFrameProcessor(applicationContext)
        val viewModelFactory = StudioViewModel.Factory(applicationContext, repository, frameProcessor)

        setContent {
            val studioViewModel: StudioViewModel = viewModel(factory = viewModelFactory)
            val themePrefs by studioViewModel.themePreferences.collectAsStateWithLifecycle()

            // Check if launched from the Android 16 Live Update notification "停止并转 GIF" action
            LaunchedEffect(intent) {
                val recordedPath = intent?.getStringExtra(ScreenRecordService.EXTRA_RECORDED_FILE_PATH)
                if (!recordedPath.isNullOrBlank()) {
                    val file = File(recordedPath)
                    if (file.exists()) {
                        studioViewModel.loadVideoFromUri(
                            uri = Uri.fromFile(file),
                            sourceType = "SCREEN_RECORD",
                            suggestedTitle = "录屏捕获_${file.nameWithoutExtension.takeLast(4)}"
                        )
                    }
                }
            }

            PixelLoopTheme(
                darkTheme = themePrefs.useDarkMode,
                dynamicColor = themePrefs.useDynamicColor
            ) {
                PixelLoopAppRoot(studioViewModel = studioViewModel)
            }
        }
    }
}

@Composable
fun PixelLoopAppRoot(studioViewModel: StudioViewModel) {
    val context = LocalContext.current
    val currentTab by studioViewModel.currentTab.collectAsStateWithLifecycle()
    val editorState by studioViewModel.editorState.collectAsStateWithLifecycle()
    val allCreations by studioViewModel.allCreations.collectAsStateWithLifecycle()
    val themePrefs by studioViewModel.themePreferences.collectAsStateWithLifecycle()
    val countdownValue by studioViewModel.countdownSeconds.collectAsStateWithLifecycle()
    val liveRecordingState by studioViewModel.liveRecordingState.collectAsStateWithLifecycle()
    val simulatedLiveSeconds by studioViewModel.simulatedLiveRecordSeconds.collectAsStateWithLifecycle()
    val toastMessage by studioViewModel.toastMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            studioViewModel.clearToast()
        }
    }

    // Back navigation handler for secondary tabs
    BackHandler(enabled = currentTab != StudioTab.HOME) {
        studioViewModel.selectTab(StudioTab.HOME)
    }

    // Zero-Permission Android Photo Picker for MP4 Video selection
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            studioViewModel.loadVideoFromUri(
                uri = uri,
                sourceType = "VIDEO_IMPORT",
                suggestedTitle = "导入视频_${System.currentTimeMillis() % 1000}"
            )
        }
    }

    // MediaProjection Screen Capture Launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val metrics = context.resources.displayMetrics
            studioViewModel.startRecordCountdown {
                val serviceIntent = Intent(context, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_START_RECORDING
                    putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenRecordService.EXTRA_RESULT_DATA, result.data)
                    putExtra(ScreenRecordService.EXTRA_SCREEN_WIDTH, metrics.widthPixels)
                    putExtra(ScreenRecordService.EXTRA_SCREEN_HEIGHT, metrics.heightPixels)
                    putExtra(ScreenRecordService.EXTRA_SCREEN_DENSITY, metrics.densityDpi)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }

    // Android 13+ / Android 16 Notification Permission Launcher for Live Updates
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    val requestScreenRecord: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            runCatching {
                mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }.onFailure {
                // Fallback in headless/virtual environments: run countdown & live notification capture flow
                studioViewModel.startRecordCountdown {
                    studioViewModel.startLiveNotificationCaptureDemo()
                }
            }
        }
    }

    val isAnyRecordingActive = liveRecordingState.isRecording || simulatedLiveSeconds != null
    val activeElapsedSeconds = if (liveRecordingState.isRecording) {
        liveRecordingState.elapsedSeconds
    } else {
        simulatedLiveSeconds ?: 0
    }

    val stopActiveRecordingAndEdit: () -> Unit = {
        if (liveRecordingState.isRecording) {
            val stopIntent = Intent(context, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP_AND_EDIT
            }
            context.startService(stopIntent)
        } else {
            studioViewModel.stopLiveNotificationCaptureAndEdit()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                NavigationBarItem(
                    selected = currentTab == StudioTab.HOME,
                    onClick = { studioViewModel.selectTab(StudioTab.HOME) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == StudioTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = stringResource(R.string.nav_home)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_home)) },
                    modifier = Modifier.testTag("nav_tab_home")
                )

                NavigationBarItem(
                    selected = currentTab == StudioTab.EDITOR,
                    onClick = { studioViewModel.selectTab(StudioTab.EDITOR) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == StudioTab.EDITOR) Icons.Filled.Crop else Icons.Outlined.Crop,
                            contentDescription = stringResource(R.string.nav_editor)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_editor)) },
                    modifier = Modifier.testTag("nav_tab_editor")
                )

                NavigationBarItem(
                    selected = currentTab == StudioTab.GALLERY,
                    onClick = { studioViewModel.selectTab(StudioTab.GALLERY) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (allCreations.isNotEmpty()) {
                                    Badge { Text(allCreations.size.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (currentTab == StudioTab.GALLERY) {
                                    Icons.Filled.AutoAwesomeMotion
                                } else {
                                    Icons.Outlined.AutoAwesomeMotion
                                },
                                contentDescription = stringResource(R.string.nav_gallery)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.nav_gallery)) },
                    modifier = Modifier.testTag("nav_tab_gallery")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                StudioTab.HOME -> {
                    HomeScreen(
                        themePreferences = themePrefs,
                        isRecordingActive = isAnyRecordingActive,
                        recordingElapsedSeconds = activeElapsedSeconds,
                        countdownValue = countdownValue,
                        recentCreations = allCreations,
                        onToggleDynamicColor = studioViewModel::toggleDynamicColor,
                        onToggleDarkMode = studioViewModel::toggleDarkMode,
                        onStartScreenRecord = requestScreenRecord,
                        onStopLiveRecordAndEdit = stopActiveRecordingAndEdit,
                        onStartLiveNotifCaptureDemo = {
                            studioViewModel.startRecordCountdown {
                                studioViewModel.startLiveNotificationCaptureDemo()
                            }
                        },
                        onPickLocalVideo = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onGenerateDemoVideo = {
                            studioViewModel.loadOrGenerateDemoVideo()
                        },
                        onShareCreation = studioViewModel::shareGif,
                        onOpenGallery = { studioViewModel.selectTab(StudioTab.GALLERY) }
                    )
                }

                StudioTab.EDITOR -> {
                    EditorScreen(
                        uiState = editorState,
                        onPickVideo = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onGenerateDemoVideo = {
                            studioViewModel.loadOrGenerateDemoVideo()
                        },
                        onSelectAspectPreset = studioViewModel::selectAspectPreset,
                        onUpdateCropRect = studioViewModel::updateCropRect,
                        onUpdateTrimRange = studioViewModel::updateTrimRange,
                        onTogglePreviewPlayback = studioViewModel::togglePreviewPlayback,
                        onUpdateFps = studioViewModel::updateTargetFps,
                        onUpdateResolution = studioViewModel::updateResolutionPreset,
                        onUpdateSpeed = studioViewModel::updatePlaybackSpeed,
                        onExportGif = studioViewModel::exportCurrentVideoToGif,
                        onDismissEncodingDialog = studioViewModel::dismissEncodingDialog,
                        onShareCreation = studioViewModel::shareGif,
                        onSaveCreationToGallery = studioViewModel::saveGifToSystemGallery,
                        onNavigateToGallery = { studioViewModel.selectTab(StudioTab.GALLERY) }
                    )
                }

                StudioTab.GALLERY -> {
                    GalleryScreen(
                        creations = allCreations,
                        onShareCreation = studioViewModel::shareGif,
                        onSaveToSystemGallery = studioViewModel::saveGifToSystemGallery,
                        onDeleteCreation = studioViewModel::deleteCreation,
                        onGoToHome = { studioViewModel.selectTab(StudioTab.HOME) }
                    )
                }
            }
        }
    }
}
