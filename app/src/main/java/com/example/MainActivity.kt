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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.GifRepository
import com.example.data.ThemeMode
import com.example.engine.VideoFrameProcessor
import com.example.service.LiveUpdateNotificationHelper
import com.example.service.QuickRecordTileService
import com.example.service.ScreenRecordService
import com.example.ui.StudioTab
import com.example.ui.StudioViewModel
import com.example.ui.components.ExpressiveFloatingNavBar
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.QueueScreen
import com.example.ui.theme.PixelLoopTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class MainActivity : ComponentActivity() {

    private val latestIntentFlow = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LiveUpdateNotificationHelper.ensureChannel(this)
        latestIntentFlow.value = intent

        val database = AppDatabase.getInstance(applicationContext)
        val repository = GifRepository(applicationContext, database.gifDao())
        val frameProcessor = VideoFrameProcessor(applicationContext)
        val viewModelFactory = StudioViewModel.Factory(applicationContext, repository, frameProcessor)

        setContent {
            val studioViewModel: StudioViewModel = viewModel(factory = viewModelFactory)
            val themePrefs by studioViewModel.themePreferences.collectAsStateWithLifecycle()
            val incomingIntent by latestIntentFlow.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            val useDark = when (themePrefs.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            PixelLoopTheme(
                darkTheme = useDark,
                dynamicColor = themePrefs.useDynamicColor
            ) {
                PixelLoopAppRoot(
                    studioViewModel = studioViewModel,
                    incomingIntent = incomingIntent,
                    onConsumeIntent = { latestIntentFlow.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntentFlow.value = intent
    }
}

@Composable
fun PixelLoopAppRoot(
    studioViewModel: StudioViewModel,
    incomingIntent: Intent? = null,
    onConsumeIntent: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentTab by studioViewModel.currentTab.collectAsStateWithLifecycle()
    val editorState by studioViewModel.editorState.collectAsStateWithLifecycle()
    val queueState by studioViewModel.queueState.collectAsStateWithLifecycle()
    val allCreations by studioViewModel.allCreations.collectAsStateWithLifecycle()
    val themePrefs by studioViewModel.themePreferences.collectAsStateWithLifecycle()
    val countdownValue by studioViewModel.countdownSeconds.collectAsStateWithLifecycle()
    val liveRecordingState by studioViewModel.liveRecordingState.collectAsStateWithLifecycle()
    val toastMessage by studioViewModel.toastMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            studioViewModel.clearToast()
        }
    }

    BackHandler(enabled = currentTab != StudioTab.HOME) {
        studioViewModel.selectTab(StudioTab.HOME)
    }

    // Single video picker for Editor
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            studioViewModel.loadVideoFromUri(
                uri = uri,
                sourceType = "VIDEO_IMPORT",
                suggestedTitle = "GIF_${System.currentTimeMillis() % 10000}"
            )
        }
    }

    // Multi-video picker for Batch Queue
    val multiVideoQueuePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            studioViewModel.enqueueVideosToBatchQueue(uris)
        }
    }

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
            }
        }
    }

    // Handle intents from ScreenRecordService (finished file) or QuickRecordTileService (Control Center tile tap)
    LaunchedEffect(incomingIntent) {
        val intent = incomingIntent ?: return@LaunchedEffect
        val recordedPath = intent.getStringExtra(ScreenRecordService.EXTRA_RECORDED_FILE_PATH)
        val autoStartRecord = intent.getBooleanExtra(QuickRecordTileService.EXTRA_AUTO_START_SCREEN_RECORD, false)

        if (!recordedPath.isNullOrBlank()) {
            val file = File(recordedPath)
            if (file.exists()) {
                studioViewModel.loadVideoFromUri(
                    uri = Uri.fromFile(file),
                    sourceType = "SCREEN_RECORD",
                    suggestedTitle = "录屏_${file.nameWithoutExtension.takeLast(4)}"
                )
            }
            onConsumeIntent()
        } else if (autoStartRecord) {
            studioViewModel.selectTab(StudioTab.HOME)
            requestScreenRecord()
            onConsumeIntent()
        }
    }

    val stopActiveRecordingAndEdit: () -> Unit = {
        if (liveRecordingState.isRecording) {
            val stopIntent = Intent(context, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP_AND_EDIT
            }
            context.startService(stopIntent)
        }
    }

    val addTileToControlCenter: () -> Unit = {
        QuickRecordTileService.requestAddTileToControlCenter(context) { resultMsg ->
            studioViewModel.showToast(resultMsg)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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
                        isRecordingActive = liveRecordingState.isRecording,
                        recordingElapsedSeconds = liveRecordingState.elapsedSeconds,
                        countdownValue = countdownValue,
                        activeQueueCount = queueState.activeBadgeCount,
                        recentCreations = allCreations,
                        onToggleDynamicColor = studioViewModel::toggleDynamicColor,
                        onCycleThemeMode = studioViewModel::cycleThemeMode,
                        onStartScreenRecord = requestScreenRecord,
                        onStopLiveRecordAndEdit = stopActiveRecordingAndEdit,
                        onAddTileToControlCenter = addTileToControlCenter,
                        onPickLocalVideo = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onPickMultipleVideosForQueue = {
                            multiVideoQueuePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onOpenQueue = { studioViewModel.selectTab(StudioTab.QUEUE) },
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
                        onSelectAspectPreset = studioViewModel::selectAspectPreset,
                        onUpdateCropRect = studioViewModel::updateCropRect,
                        onUpdateTrimRange = studioViewModel::updateTrimRange,
                        onTogglePreviewPlayback = studioViewModel::togglePreviewPlayback,
                        onUpdateFps = studioViewModel::updateTargetFps,
                        onUpdateResolution = studioViewModel::updateResolutionPreset,
                        onUpdateSpeed = studioViewModel::updatePlaybackSpeed,
                        onAddToQueue = studioViewModel::enqueueCurrentEditorVideoToQueue,
                        onExportGif = studioViewModel::exportCurrentVideoToGif,
                        onDismissEncodingDialog = studioViewModel::dismissEncodingDialog,
                        onShareCreation = studioViewModel::shareGif,
                        onSaveCreationToGallery = studioViewModel::saveGifToSystemGallery,
                        onNavigateToGallery = { studioViewModel.selectTab(StudioTab.GALLERY) }
                    )
                }

                StudioTab.QUEUE -> {
                    QueueScreen(
                        queueState = queueState,
                        onAddMultipleVideos = {
                            multiVideoQueuePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onTogglePauseResume = studioViewModel::toggleQueuePauseResume,
                        onClearCompleted = studioViewModel::clearCompletedQueueTasks,
                        onMoveTaskUp = studioViewModel::moveQueueTaskUp,
                        onRemoveTask = studioViewModel::removeQueueTask,
                        onRetryTask = studioViewModel::retryQueueTask,
                        onUpdateBatchFps = studioViewModel::updateBatchQueueFps,
                        onUpdateBatchResolution = studioViewModel::updateBatchQueueResolution,
                        onUpdateBatchSpeed = studioViewModel::updateBatchQueueSpeed,
                        onShareCreation = studioViewModel::shareGif
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

            // MD3E (Material Design 3 Expressive) Floating Pill Navigation Bar
            ExpressiveFloatingNavBar(
                currentTab = currentTab,
                queueCount = queueState.activeBadgeCount,
                galleryCount = allCreations.size,
                onSelectTab = studioViewModel::selectTab,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
