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
import android.os.Process
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
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
import com.example.ui.SafFallbackMode
import com.example.ui.StudioTab
import com.example.ui.StudioViewModel
import com.example.ui.components.ExpressiveFloatingNavBar
import com.example.ui.components.ExpressiveNavigationRail
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
    val allRecordings by studioViewModel.allRecordings.collectAsStateWithLifecycle()
    val themePrefs by studioViewModel.themePreferences.collectAsStateWithLifecycle()
    val countdownValue by studioViewModel.countdownSeconds.collectAsStateWithLifecycle()
    val liveRecordingState by studioViewModel.liveRecordingState.collectAsStateWithLifecycle()
    val toastMessage by studioViewModel.toastMessage.collectAsStateWithLifecycle()
    val safFallbackRequest by studioViewModel.safFallbackRequest.collectAsStateWithLifecycle()
    val preferSafPicker by studioViewModel.preferSafDocumentPicker.collectAsStateWithLifecycle()

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

    // Single video picker for Editor (Android Photo Picker)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            logVideoPickerResultDiagnostics(
                context = context,
                pickerContract = "PickVisualMedia(Single)",
                uris = listOf(uri)
            )
            studioViewModel.loadVideoFromUri(
                uri = uri,
                sourceType = "VIDEO_IMPORT",
                suggestedTitle = "GIF_${System.currentTimeMillis() % 10000}"
            )
        } else {
            Log.d(
                VideoFrameProcessor.TAG,
                "[PICKER_RESULT] PickVisualMedia(Single) returned null URI (user cancelled or provider returned empty result)"
            )
        }
    }

    // Zero-permission SAF fallback for Single Video when OEM PhotoPicker provider is broken
    val safSingleVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            logVideoPickerResultDiagnostics(
                context = context,
                pickerContract = "SAF_OpenDocument(Single)",
                uris = listOf(uri)
            )
            studioViewModel.loadVideoFromUri(
                uri = uri,
                sourceType = "VIDEO_IMPORT",
                suggestedTitle = "GIF_${System.currentTimeMillis() % 10000}"
            )
        } else {
            Log.d(
                VideoFrameProcessor.TAG,
                "[PICKER_RESULT] SAF_OpenDocument(Single) returned null URI (user cancelled)"
            )
        }
    }

    // Multi-video picker for Batch Queue (Android Photo Picker)
    val multiVideoQueuePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            logVideoPickerResultDiagnostics(
                context = context,
                pickerContract = "PickMultipleVisualMedia(Batch, count=${uris.size})",
                uris = uris
            )
            studioViewModel.enqueueVideosToBatchQueue(uris)
        } else {
            Log.d(
                VideoFrameProcessor.TAG,
                "[PICKER_RESULT] PickMultipleVisualMedia(Batch) returned empty URI list (user cancelled or provider returned empty result)"
            )
        }
    }

    // Zero-permission SAF fallback for Multi-Video when OEM PhotoPicker provider is broken
    val safMultiVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            logVideoPickerResultDiagnostics(
                context = context,
                pickerContract = "SAF_OpenMultipleDocuments(Batch, count=${uris.size})",
                uris = uris
            )
            studioViewModel.enqueueVideosToBatchQueue(uris)
        } else {
            Log.d(
                VideoFrameProcessor.TAG,
                "[PICKER_RESULT] SAF_OpenMultipleDocuments(Batch) returned empty URI list (user cancelled)"
            )
        }
    }

    val launchSingleVideoPicker: () -> Unit = {
        if (preferSafPicker) {
            Log.d(VideoFrameProcessor.TAG, "[PICKER_LAUNCH] Launching SAF_OpenDocument(Single) (preferSafPicker=true)")
            runCatching { safSingleVideoLauncher.launch(arrayOf("video/*")) }
                .onFailure { err ->
                    Log.e(VideoFrameProcessor.TAG, "[PICKER_LAUNCH_ERROR] Failed launching SAF_OpenDocument(Single)", err)
                }
        } else {
            Log.d(VideoFrameProcessor.TAG, "[PICKER_LAUNCH] Launching PickVisualMedia(Single, VideoOnly)")
            runCatching {
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }.onFailure { err ->
                Log.w(
                    VideoFrameProcessor.TAG,
                    "[PICKER_LAUNCH_FALLBACK] PickVisualMedia(Single) launch failed (${err.javaClass.simpleName}: ${err.message}); falling back to SAF_OpenDocument",
                    err
                )
                runCatching { safSingleVideoLauncher.launch(arrayOf("video/*")) }
            }
        }
    }

    val launchMultiVideoPicker: () -> Unit = {
        if (preferSafPicker) {
            Log.d(VideoFrameProcessor.TAG, "[PICKER_LAUNCH] Launching SAF_OpenMultipleDocuments(Batch) (preferSafPicker=true)")
            runCatching { safMultiVideoLauncher.launch(arrayOf("video/*")) }
                .onFailure { err ->
                    Log.e(VideoFrameProcessor.TAG, "[PICKER_LAUNCH_ERROR] Failed launching SAF_OpenMultipleDocuments(Batch)", err)
                }
        } else {
            Log.d(VideoFrameProcessor.TAG, "[PICKER_LAUNCH] Launching PickMultipleVisualMedia(Batch, VideoOnly)")
            runCatching {
                multiVideoQueuePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }.onFailure { err ->
                Log.w(
                    VideoFrameProcessor.TAG,
                    "[PICKER_LAUNCH_FALLBACK] PickMultipleVisualMedia(Batch) launch failed (${err.javaClass.simpleName}: ${err.message}); falling back to SAF_OpenMultipleDocuments",
                    err
                )
                runCatching { safMultiVideoLauncher.launch(arrayOf("video/*")) }
            }
        }
    }

    LaunchedEffect(safFallbackRequest) {
        when (safFallbackRequest) {
            SafFallbackMode.SINGLE_VIDEO -> {
                Log.w(
                    VideoFrameProcessor.TAG,
                    "[PICKER_AUTO_FALLBACK] Triggering automatic fallback to SAF_OpenDocument(Single) due to PhotoPicker provider access denial"
                )
                studioViewModel.clearSafFallbackRequest()
                runCatching { safSingleVideoLauncher.launch(arrayOf("video/*")) }
            }
            SafFallbackMode.MULTI_VIDEO -> {
                Log.w(
                    VideoFrameProcessor.TAG,
                    "[PICKER_AUTO_FALLBACK] Triggering automatic fallback to SAF_OpenMultipleDocuments(Batch) due to PhotoPicker provider access denial"
                )
                studioViewModel.clearSafFallbackRequest()
                runCatching { safMultiVideoLauncher.launch(arrayOf("video/*")) }
            }
            null -> Unit
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

    val exportNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val ensureNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                exportNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

    // Handle intents from ScreenRecordService, QuickRecordTileService, or LiveUpdateNotificationHelper
    LaunchedEffect(incomingIntent) {
        val intent = incomingIntent ?: return@LaunchedEffect
        val recordedPath = intent.getStringExtra(ScreenRecordService.EXTRA_RECORDED_FILE_PATH)
        val autoStartRecord = intent.getBooleanExtra(QuickRecordTileService.EXTRA_AUTO_START_SCREEN_RECORD, false)
        val openTabExtra = intent.getStringExtra(LiveUpdateNotificationHelper.EXTRA_OPEN_TAB)

        if (!recordedPath.isNullOrBlank()) {
            val file = File(recordedPath)
            if (file.exists()) {
                ScreenRecordService.clearConsumedRecording()
                studioViewModel.loadAutoRecordedFileOnce(file)
            }
            onConsumeIntent()
        } else if (autoStartRecord) {
            studioViewModel.selectTab(StudioTab.HOME)
            requestScreenRecord()
            onConsumeIntent()
        } else if (!openTabExtra.isNullOrBlank()) {
            val targetTab = when (openTabExtra) {
                LiveUpdateNotificationHelper.TAB_EDITOR -> StudioTab.EDITOR
                LiveUpdateNotificationHelper.TAB_QUEUE -> StudioTab.QUEUE
                LiveUpdateNotificationHelper.TAB_GALLERY -> StudioTab.GALLERY
                else -> StudioTab.HOME
            }
            studioViewModel.selectTab(targetTab)
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val useNavigationRail = maxWidth >= 720.dp

            val mainScreenContent: @Composable (Modifier) -> Unit = { contentModifier ->
                Box(modifier = contentModifier) {
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
                                onPickLocalVideo = launchSingleVideoPicker,
                                onPickMultipleVideosForQueue = launchMultiVideoPicker,
                                onOpenQueue = { studioViewModel.selectTab(StudioTab.QUEUE) },
                                onShareCreation = studioViewModel::shareGif,
                                onOpenGallery = { studioViewModel.selectTab(StudioTab.GALLERY) }
                            )
                        }

                        StudioTab.EDITOR -> {
                            EditorScreen(
                                uiState = editorState,
                                onPickVideo = launchSingleVideoPicker,
                                onSelectAspectPreset = studioViewModel::selectAspectPreset,
                                onUpdateCropRect = studioViewModel::updateCropRect,
                                onUpdateTrimRange = studioViewModel::updateTrimRange,
                                onTogglePreviewPlayback = studioViewModel::togglePreviewPlayback,
                                onUpdateFps = studioViewModel::updateTargetFps,
                                onUpdateResolution = studioViewModel::updateResolutionPreset,
                                onUpdateSpeed = studioViewModel::updatePlaybackSpeed,
                                onAddToQueue = {
                                    ensureNotificationPermission()
                                    studioViewModel.enqueueCurrentEditorVideoToQueue()
                                },
                                onExportGif = {
                                    ensureNotificationPermission()
                                    studioViewModel.exportCurrentVideoToGif()
                                },
                                onDismissEncodingDialog = studioViewModel::dismissEncodingDialog,
                                onShareCreation = studioViewModel::shareGif,
                                onSaveCreationToGallery = studioViewModel::saveGifToSystemGallery,
                                onNavigateToGallery = { studioViewModel.selectTab(StudioTab.GALLERY) }
                            )
                        }

                        StudioTab.QUEUE -> {
                            QueueScreen(
                                queueState = queueState,
                                onAddMultipleVideos = launchMultiVideoPicker,
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
                                recordings = allRecordings,
                                onShareCreation = studioViewModel::shareGif,
                                onSaveToSystemGallery = studioViewModel::saveGifToSystemGallery,
                                onDeleteCreation = studioViewModel::deleteCreation,
                                onReloadRecordingToEditor = studioViewModel::reloadScreenRecordingToEditor,
                                onEnqueueRecordingToQueue = studioViewModel::enqueueScreenRecordingToQueue,
                                onDeleteRecording = studioViewModel::deleteScreenRecording,
                                onClearAllRecordings = studioViewModel::clearAllScreenRecordings,
                                onGoToHome = { studioViewModel.selectTab(StudioTab.HOME) }
                            )
                        }
                    }
                }
            }

            if (useNavigationRail) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ExpressiveNavigationRail(
                        currentTab = currentTab,
                        queueCount = queueState.activeBadgeCount,
                        galleryCount = allCreations.size + allRecordings.size,
                        onSelectTab = studioViewModel::selectTab
                    )
                    mainScreenContent(Modifier.weight(1f).fillMaxSize())
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    mainScreenContent(Modifier.fillMaxSize())

                    // MD3E (Material Design 3 Expressive) Floating Pill Navigation Bar
                    ExpressiveFloatingNavBar(
                        currentTab = currentTab,
                        queueCount = queueState.activeBadgeCount,
                        galleryCount = allCreations.size + allRecordings.size,
                        onSelectTab = studioViewModel::selectTab,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

/**
 * Logs structured diagnostics immediately when a video picker activity result returns,
 * capturing URI structure, OEM provider authority, caller read permission grant status,
 * and ContentResolver MIME/provider reachability to aid diagnosing file access denials.
 */
private fun logVideoPickerResultDiagnostics(
    context: Context,
    pickerContract: String,
    uris: List<Uri>
) {
    val pid = Process.myPid()
    val uid = Process.myUid()
    val deviceInfo = "${Build.MANUFACTURER}/${Build.BRAND}/${Build.MODEL} (SDK=${Build.VERSION.SDK_INT})"

    Log.d(
        VideoFrameProcessor.TAG,
        "[PICKER_RESULT] contract=$pickerContract | count=${uris.size} | device=$deviceInfo | callerPid=$pid callerUid=$uid"
    )

    uris.forEachIndexed { index, uri ->
        val readPermCheck = runCatching {
            context.checkUriPermission(uri, pid, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.getOrDefault(PackageManager.PERMISSION_DENIED)
        val permStatus = if (readPermCheck == PackageManager.PERMISSION_GRANTED) {
            "GRANTED"
        } else {
            "DENIED($readPermCheck)"
        }

        val providerInfo = runCatching {
            val auth = uri.authority
            if (!auth.isNullOrBlank()) {
                val pInfo = context.packageManager.resolveContentProvider(auth, 0)
                if (pInfo != null) {
                    "pkg=${pInfo.packageName},exported=${pInfo.exported},grantUriPerms=${pInfo.grantUriPermissions},readPerm=${pInfo.readPermission}"
                } else {
                    "UNRESOLVED_AUTHORITY($auth)"
                }
            } else {
                "NO_AUTHORITY"
            }
        }.getOrElse { "ERR(${it.javaClass.simpleName}:${it.message})" }

        val resolvedMime = runCatching {
            context.contentResolver.getType(uri) ?: "null"
        }.getOrElse { "ERR(${it.javaClass.simpleName}:${it.message})" }

        val segments = uri.pathSegments
        val delegatedCloudAuthority = if (segments.size >= 3 && (segments[0] == "picker" || segments[0] == "picker_get_content")) {
            val cloudAuth = segments[2]
            val cloudProviderPkg = runCatching {
                context.packageManager.resolveContentProvider(cloudAuth, 0)?.packageName ?: "UNRESOLVED($cloudAuth)"
            }.getOrDefault("ERR")
            "$cloudAuth (providerPkg=$cloudProviderPkg)"
        } else {
            "none"
        }

        Log.d(
            VideoFrameProcessor.TAG,
            "[PICKER_URI_DIAG] item[$index] uri=$uri | scheme=${uri.scheme} | authority=${uri.authority} | " +
                "segments=$segments | delegatedCloudProvider=$delegatedCloudAuthority | " +
                "readGrant=$permStatus | mime=$resolvedMime | provider=[$providerInfo]"
        )
    }
}

