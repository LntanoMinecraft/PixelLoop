package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GifCreationEntity
import com.example.data.GifRepository
import com.example.data.ScreenRecordingEntity
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import com.example.engine.BatchQueueManager
import com.example.engine.BatchQueueUiState
import com.example.engine.CropAspectPreset
import com.example.engine.NormalizedCropRect
import com.example.engine.ResolutionPreset
import com.example.engine.VideoFrameProcessor
import com.example.engine.VideoSourceMetadata
import com.example.service.GifProcessingService
import com.example.service.LiveRecordingState
import com.example.service.LiveUpdateNotificationHelper
import com.example.service.ScreenRecordService
import com.example.ui.components.formatFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class StudioTab {
    HOME,
    EDITOR,
    QUEUE,
    GALLERY
}

sealed interface EncodingProgressState {
    data object Idle : EncodingProgressState
    data class Encoding(val currentFrame: Int, val totalFrames: Int) : EncodingProgressState
    data class Completed(val creation: GifCreationEntity) : EncodingProgressState
    data class Error(val message: String) : EncodingProgressState
}

data class GifEditorUiState(
    val videoMetadata: VideoSourceMetadata? = null,
    val isLoadingVideo: Boolean = false,
    val timelineThumbnails: List<Bitmap> = emptyList(),
    val currentPreviewBitmap: Bitmap? = null,
    val currentPreviewTimeMs: Long = 0L,
    val isPlayingPreview: Boolean = false,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 4000L,
    val cropRect: NormalizedCropRect = NormalizedCropRect(),
    val aspectPreset: CropAspectPreset = CropAspectPreset.FREE,
    val targetFps: Int = 15,
    val resolutionPreset: ResolutionPreset = ResolutionPreset.RES_480P,
    val playbackSpeed: Float = 1.0f,
    val outputWidth: Int = 480,
    val outputHeight: Int = 640,
    val estimatedFrameCount: Int = 48,
    val estimatedSizeBytes: Long = 520_000L,
    val encodingState: EncodingProgressState = EncodingProgressState.Idle,
    val customTitle: String = "GIF"
)

class StudioViewModel(
    private val appContext: Context,
    private val repository: GifRepository,
    private val frameProcessor: VideoFrameProcessor
) : ViewModel() {

    private val batchQueueManager = BatchQueueManager.getInstance(
        appContext = appContext,
        repository = repository,
        frameProcessor = frameProcessor
    )

    private val backgroundExportScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentTab = MutableStateFlow(StudioTab.HOME)
    val currentTab: StateFlow<StudioTab> = _currentTab.asStateFlow()

    private val _editorState = MutableStateFlow(GifEditorUiState())
    val editorState: StateFlow<GifEditorUiState> = _editorState.asStateFlow()

    val queueState: StateFlow<BatchQueueUiState> = batchQueueManager.queueState

    private val _countdownSeconds = MutableStateFlow<Int?>(null)
    val countdownSeconds: StateFlow<Int?> = _countdownSeconds.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val liveRecordingState: StateFlow<LiveRecordingState> = ScreenRecordService.recordingState

    val allCreations: StateFlow<List<GifCreationEntity>> = repository.allCreations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecordings: StateFlow<List<ScreenRecordingEntity>> = repository.allRecordings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themePreferences: StateFlow<ThemePreferences> = repository.themePreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences())

    private var previewPlaybackJob: Job? = null
    private var scrubJob: Job? = null

    init {
        // Observe finished screen recordings from ScreenRecordService, register in Works Library, and auto-open in Editor
        viewModelScope.launch {
            ScreenRecordService.recordingState.collect { recState ->
                recState.latestRecordedFile?.let { file ->
                    if (file.exists()) {
                        ScreenRecordService.clearConsumedRecording()
                        val recTitle = "录屏_${file.nameWithoutExtension.takeLast(4)}"
                        repository.registerScreenRecordingFile(
                            videoFile = file,
                            frameProcessor = frameProcessor,
                            suggestedTitle = recTitle
                        )
                        loadVideoFromUri(
                            uri = Uri.fromFile(file),
                            sourceType = "SCREEN_RECORD",
                            suggestedTitle = recTitle
                        )
                        _currentTab.value = StudioTab.EDITOR
                    }
                }
                recState.statusMessage?.let { msg ->
                    _toastMessage.value = msg
                }
            }
        }

        // Sync existing screen recordings on disk into the Works Library & clean legacy sample GIF
        viewModelScope.launch {
            runCatching {
                val items = repository.allCreations.first()
                items.filter { it.filePath.endsWith("PixelLoop_Starter_Expressive.gif") }
                    .forEach { repository.deleteCreation(it) }
            }
            repository.syncRecordingsDirectory(frameProcessor)
        }
    }

    fun selectTab(tab: StudioTab) {
        _currentTab.value = tab
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun toggleDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDynamicColor(enabled)
        }
    }

    fun cycleThemeMode() {
        val next = when (themePreferences.value.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        viewModelScope.launch {
            repository.setThemeMode(next)
        }
    }

    fun startRecordCountdown(onCountdownFinished: () -> Unit) {
        if (_countdownSeconds.value != null) return
        viewModelScope.launch {
            for (sec in 3 downTo 1) {
                _countdownSeconds.value = sec
                delay(850L)
            }
            _countdownSeconds.value = null
            onCountdownFinished()
        }
    }

    fun loadVideoFromUri(
        uri: Uri,
        sourceType: String = "VIDEO_IMPORT",
        suggestedTitle: String = "GIF_${System.currentTimeMillis() % 10000}"
    ) {
        previewPlaybackJob?.cancel()
        viewModelScope.launch {
            _editorState.update {
                it.copy(
                    isLoadingVideo = true,
                    isPlayingPreview = false,
                    encodingState = EncodingProgressState.Idle
                )
            }

            // If loading an in-app screen recording file, ensure it is indexed in Works Library
            if (sourceType == "SCREEN_RECORD" && uri.scheme == "file") {
                uri.path?.let { path ->
                    val recFile = File(path)
                    if (recFile.exists()) {
                        repository.registerScreenRecordingFile(
                            videoFile = recFile,
                            frameProcessor = frameProcessor,
                            suggestedTitle = suggestedTitle
                        )
                    }
                }
            }

            val metaResult = frameProcessor.inspectVideo(uri, sourceType, suggestedTitle)
            metaResult.onSuccess { meta ->
                val defaultEndMs = min(meta.durationMs, 10_000L)
                val initialCrop = NormalizedCropRect(0f, 0f, 1f, 1f)
                val (outW, outH) = frameProcessor.calculateOutputDimensions(
                    meta.width,
                    meta.height,
                    initialCrop,
                    _editorState.value.resolutionPreset
                )
                val firstFrame = frameProcessor.extractPreviewFrame(uri, 0L)
                val thumbs = frameProcessor.extractTimelineThumbnails(uri, meta.durationMs, count = 8)

                _editorState.update { state ->
                    val newState = state.copy(
                        videoMetadata = meta,
                        isLoadingVideo = false,
                        timelineThumbnails = thumbs,
                        currentPreviewBitmap = firstFrame,
                        currentPreviewTimeMs = 0L,
                        trimStartMs = 0L,
                        trimEndMs = defaultEndMs,
                        cropRect = initialCrop,
                        aspectPreset = CropAspectPreset.FREE,
                        outputWidth = outW,
                        outputHeight = outH,
                        customTitle = suggestedTitle
                    )
                    recalculateEstimates(newState)
                }
                _currentTab.value = StudioTab.EDITOR
            }.onFailure { err ->
                _editorState.update { it.copy(isLoadingVideo = false) }
                _toastMessage.value = "读取视频失败: ${err.localizedMessage ?: "格式不支持"}"
            }
        }
    }

    fun updateTrimRange(newStartMs: Long, newEndMs: Long) {
        val meta = _editorState.value.videoMetadata ?: return
        val clampedStart = newStartMs.coerceIn(0L, max(0L, meta.durationMs - 300L))
        val clampedEnd = newEndMs.coerceIn(clampedStart + 300L, meta.durationMs)
        val scrubTargetMs = if (clampedStart != _editorState.value.trimStartMs) clampedStart else clampedEnd

        _editorState.update { state ->
            recalculateEstimates(
                state.copy(
                    trimStartMs = clampedStart,
                    trimEndMs = clampedEnd,
                    currentPreviewTimeMs = scrubTargetMs
                )
            )
        }
        scrubPreviewFrame(scrubTargetMs)
    }

    fun updateCropRect(newCrop: NormalizedCropRect) {
        val left = newCrop.left.coerceIn(0f, 0.82f)
        val top = newCrop.top.coerceIn(0f, 0.82f)
        val right = newCrop.right.coerceIn(left + 0.18f, 1f)
        val bottom = newCrop.bottom.coerceIn(top + 0.18f, 1f)
        val normalized = NormalizedCropRect(left, top, right, bottom)

        _editorState.update { state ->
            recalculateEstimates(state.copy(cropRect = normalized))
        }
    }

    fun selectAspectPreset(preset: CropAspectPreset) {
        val meta = _editorState.value.videoMetadata
        val srcW = (meta?.width ?: 480).toFloat()
        val srcH = (meta?.height ?: 640).toFloat()

        val targetRatio = preset.aspectRatio
        val newCrop = if (targetRatio == null) {
            NormalizedCropRect(0f, 0f, 1f, 1f)
        } else {
            val videoAspect = srcW / srcH
            if (targetRatio > videoAspect) {
                val normH = (videoAspect / targetRatio).coerceIn(0.2f, 1f)
                val padY = (1f - normH) / 2f
                NormalizedCropRect(0f, padY, 1f, 1f - padY)
            } else {
                val normW = (targetRatio / videoAspect).coerceIn(0.2f, 1f)
                val padX = (1f - normW) / 2f
                NormalizedCropRect(padX, 0f, 1f - padX, 1f)
            }
        }

        _editorState.update { state ->
            recalculateEstimates(
                state.copy(
                    aspectPreset = preset,
                    cropRect = newCrop
                )
            )
        }
    }

    fun updateTargetFps(fps: Int) {
        val clamped = fps.coerceIn(5, 60)
        _editorState.update { state ->
            recalculateEstimates(state.copy(targetFps = clamped))
        }
    }

    fun updateResolutionPreset(preset: ResolutionPreset) {
        _editorState.update { state ->
            recalculateEstimates(state.copy(resolutionPreset = preset))
        }
    }

    fun updatePlaybackSpeed(speed: Float) {
        _editorState.update { state ->
            recalculateEstimates(state.copy(playbackSpeed = speed))
        }
    }

    fun togglePreviewPlayback() {
        val currentlyPlaying = _editorState.value.isPlayingPreview
        if (currentlyPlaying) {
            previewPlaybackJob?.cancel()
            _editorState.update { it.copy(isPlayingPreview = false) }
        } else {
            startPreviewLoop()
        }
    }

    private fun startPreviewLoop() {
        val meta = _editorState.value.videoMetadata ?: return
        previewPlaybackJob?.cancel()
        _editorState.update { it.copy(isPlayingPreview = true) }

        previewPlaybackJob = viewModelScope.launch {
            var currentMs = _editorState.value.trimStartMs
            while (isActive && _editorState.value.isPlayingPreview) {
                val state = _editorState.value
                val stepDelayMs = (1000L / state.targetFps.coerceIn(5, 30)).coerceAtLeast(33L)
                val advanceMs = (stepDelayMs * state.playbackSpeed).toLong()

                val frame = frameProcessor.extractPreviewFrame(meta.uri, currentMs, maxDimension = 540)
                if (frame != null) {
                    _editorState.update {
                        it.copy(
                            currentPreviewBitmap = frame,
                            currentPreviewTimeMs = currentMs
                        )
                    }
                }
                delay(stepDelayMs)
                currentMs += advanceMs
                if (currentMs > state.trimEndMs) {
                    currentMs = state.trimStartMs
                }
            }
        }
    }

    private fun scrubPreviewFrame(timeMs: Long) {
        val meta = _editorState.value.videoMetadata ?: return
        scrubJob?.cancel()
        scrubJob = viewModelScope.launch {
            delay(30L)
            val bmp = frameProcessor.extractPreviewFrame(meta.uri, timeMs, maxDimension = 640)
            if (bmp != null) {
                _editorState.update { it.copy(currentPreviewBitmap = bmp) }
            }
        }
    }

    fun exportCurrentVideoToGif() {
        val state = _editorState.value
        val meta = state.videoMetadata ?: return
        if (state.encodingState is EncodingProgressState.Encoding) return

        previewPlaybackJob?.cancel()
        _editorState.update {
            it.copy(
                isPlayingPreview = false,
                encodingState = EncodingProgressState.Encoding(0, state.estimatedFrameCount)
            )
        }

        GifProcessingService.startEncodingForeground(
            context = appContext,
            currentFrame = 0,
            totalFrames = state.estimatedFrameCount
        )

        backgroundExportScope.launch {
            try {
                val exportResult = frameProcessor.exportVideoToGif(
                    metadata = meta,
                    trimStartMs = state.trimStartMs,
                    trimEndMs = state.trimEndMs,
                    cropRect = state.cropRect,
                    targetFps = state.targetFps,
                    resolutionPreset = state.resolutionPreset,
                    playbackSpeed = state.playbackSpeed,
                    onProgress = { current, total ->
                        _editorState.update {
                            it.copy(encodingState = EncodingProgressState.Encoding(current, total))
                        }
                        LiveUpdateNotificationHelper.notifyEncodingProgress(
                            context = appContext,
                            currentFrame = current,
                            totalFrames = total,
                            isComplete = false
                        )
                    }
                )

                LiveUpdateNotificationHelper.notifyEncodingProgress(
                    context = appContext,
                    currentFrame = 100,
                    totalFrames = 100,
                    isComplete = true
                )

                exportResult.onSuccess { res ->
                    val entity = GifCreationEntity(
                        title = state.customTitle.ifBlank { "GIF_${System.currentTimeMillis() % 1000}" },
                        filePath = res.gifFile.absolutePath,
                        thumbnailPath = res.thumbnailFile.absolutePath,
                        width = res.outputWidth,
                        height = res.outputHeight,
                        fps = state.targetFps,
                        durationMs = res.durationMs,
                        frameCount = res.frameCount,
                        fileSizeBytes = res.fileSizeBytes,
                        sourceType = meta.sourceType,
                        cropRatioLabel = state.aspectPreset.label,
                        playbackSpeed = state.playbackSpeed
                    )
                    val id = repository.saveCreation(entity)
                    val savedEntity = entity.copy(id = id)

                    _editorState.update {
                        it.copy(encodingState = EncodingProgressState.Completed(savedEntity))
                    }
                    LiveUpdateNotificationHelper.notifyProcessingCompleted(
                        context = appContext,
                        title = "动态 GIF 导出完成 · ${savedEntity.title}",
                        subtitle = "${savedEntity.width}×${savedEntity.height} · ${savedEntity.fps} FPS · 点击查看或分享",
                        targetTab = LiveUpdateNotificationHelper.TAB_EDITOR
                    )
                }.onFailure { err ->
                    _editorState.update {
                        it.copy(
                            encodingState = EncodingProgressState.Error(
                                err.localizedMessage ?: "导出失败"
                            )
                        )
                    }
                }
            } finally {
                GifProcessingService.stopEncodingForeground(appContext)
            }
        }
    }

    fun dismissEncodingDialog() {
        _editorState.update { it.copy(encodingState = EncodingProgressState.Idle) }
    }

    fun shareGif(entity: GifCreationEntity) {
        repository.shareGifToSocial(entity).onFailure { err ->
            _toastMessage.value = "拉起分享失败: ${err.localizedMessage}"
        }
    }

    fun saveGifToSystemGallery(entity: GifCreationEntity) {
        viewModelScope.launch {
            repository.exportGifToSystemGallery(entity)
                .onSuccess { path ->
                    _toastMessage.value = "已保存至系统相册: $path"
                }
                .onFailure { err ->
                    _toastMessage.value = "保存失败: ${err.localizedMessage}"
                }
        }
    }

    fun deleteCreation(entity: GifCreationEntity) {
        viewModelScope.launch {
            repository.deleteCreation(entity)
            _toastMessage.value = "已删除 ${entity.title}"
        }
    }

    fun reloadScreenRecordingToEditor(recording: ScreenRecordingEntity) {
        val file = File(recording.videoFilePath)
        if (!file.exists()) {
            viewModelScope.launch {
                repository.deleteScreenRecording(recording)
                _toastMessage.value = "录屏原片文件已不存在，已清理记录"
            }
            return
        }
        loadVideoFromUri(
            uri = Uri.fromFile(file),
            sourceType = "SCREEN_RECORD",
            suggestedTitle = "${recording.title}_新剪辑"
        )
        _toastMessage.value = "已载入录屏原片「${recording.title}」，可重新裁剪导出"
    }

    fun enqueueScreenRecordingToQueue(recording: ScreenRecordingEntity) {
        val file = File(recording.videoFilePath)
        if (!file.exists()) {
            viewModelScope.launch {
                repository.deleteScreenRecording(recording)
                _toastMessage.value = "录屏原片文件已不存在，已清理记录"
            }
            return
        }
        enqueueVideosToBatchQueue(listOf(Uri.fromFile(file)))
    }

    fun deleteScreenRecording(recording: ScreenRecordingEntity) {
        viewModelScope.launch {
            repository.deleteScreenRecording(recording)
            _toastMessage.value = "已删除录屏原片「${recording.title}」，释放 ${formatFileSize(recording.fileSizeBytes)}"
        }
    }

    fun clearAllScreenRecordings() {
        viewModelScope.launch {
            val freed = repository.deleteAllScreenRecordings()
            _toastMessage.value = "已清空全部录屏原片，释放 ${formatFileSize(freed)} 存储空间"
        }
    }

    fun enqueueVideosToBatchQueue(uris: List<Uri>) {
        if (uris.isEmpty()) return
        batchQueueManager.enqueueMultipleVideos(uris) { addedCount ->
            if (addedCount > 0) {
                _toastMessage.value = "已将 $addedCount 个视频加入后台队列"
                _currentTab.value = StudioTab.QUEUE
            } else {
                _toastMessage.value = "未能读取所选视频文件"
            }
        }
    }

    fun enqueueCurrentEditorVideoToQueue() {
        val state = _editorState.value
        val meta = state.videoMetadata ?: return
        previewPlaybackJob?.cancel()
        _editorState.update { it.copy(isPlayingPreview = false) }

        batchQueueManager.enqueueConfiguredTask(
            metadata = meta.copy(displayName = state.customTitle.ifBlank { meta.displayName }),
            thumbnail = state.currentPreviewBitmap,
            trimStartMs = state.trimStartMs,
            trimEndMs = state.trimEndMs,
            cropRect = state.cropRect,
            aspectPreset = state.aspectPreset,
            targetFps = state.targetFps,
            resolutionPreset = state.resolutionPreset,
            playbackSpeed = state.playbackSpeed
        )
        _toastMessage.value = "已加入后台转码队列"
        _currentTab.value = StudioTab.QUEUE
    }

    fun toggleQueuePauseResume() {
        batchQueueManager.togglePauseResume()
    }

    fun moveQueueTaskUp(taskId: String) {
        batchQueueManager.moveTaskUp(taskId)
    }

    fun removeQueueTask(taskId: String) {
        batchQueueManager.removeTask(taskId)
    }

    fun retryQueueTask(taskId: String) {
        batchQueueManager.retryTask(taskId)
    }

    fun clearCompletedQueueTasks() {
        batchQueueManager.clearCompletedTasks()
    }

    fun updateBatchQueueFps(fps: Int) {
        batchQueueManager.updateBatchConfig(fps = fps)
    }

    fun updateBatchQueueResolution(preset: ResolutionPreset) {
        batchQueueManager.updateBatchConfig(resolution = preset)
    }

    fun updateBatchQueueSpeed(speed: Float) {
        batchQueueManager.updateBatchConfig(speed = speed)
    }

    private fun recalculateEstimates(state: GifEditorUiState): GifEditorUiState {
        val meta = state.videoMetadata ?: return state
        val (outW, outH) = frameProcessor.calculateOutputDimensions(
            sourceWidth = meta.width,
            sourceHeight = meta.height,
            cropRect = state.cropRect,
            resolutionPreset = state.resolutionPreset
        )
        val rawClipMs = (state.trimEndMs - state.trimStartMs).coerceAtLeast(300L)
        val effectiveDurationMs = (rawClipMs / state.playbackSpeed).roundToInt().coerceAtLeast(250)
        val effectiveGifFps = min(50, state.targetFps.coerceIn(5, 60))
        val frameCount = ((effectiveDurationMs / 1000f) * effectiveGifFps).roundToInt().coerceIn(4, 240)

        val bytesPerFrame = (outW * outH * 0.28f).toLong()
        val estimatedBytes = (bytesPerFrame * frameCount + 1200L).coerceAtLeast(12_000L)

        return state.copy(
            outputWidth = outW,
            outputHeight = outH,
            estimatedFrameCount = frameCount,
            estimatedSizeBytes = estimatedBytes
        )
    }

    class Factory(
        private val appContext: Context,
        private val repository: GifRepository,
        private val frameProcessor: VideoFrameProcessor
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudioViewModel(appContext, repository, frameProcessor) as T
        }
    }
}
