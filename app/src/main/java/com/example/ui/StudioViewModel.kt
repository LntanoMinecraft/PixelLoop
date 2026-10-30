package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GifCreationEntity
import com.example.data.GifRepository
import com.example.data.ThemePreferences
import com.example.engine.AnimatedGifEncoder
import com.example.engine.CropAspectPreset
import com.example.engine.DemoVideoGenerator
import com.example.engine.NormalizedCropRect
import com.example.engine.ResolutionPreset
import com.example.engine.VideoFrameProcessor
import com.example.engine.VideoSourceMetadata
import com.example.service.LiveRecordingState
import com.example.service.LiveUpdateNotificationHelper
import com.example.service.ScreenRecordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class StudioTab {
    HOME,
    EDITOR,
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
    val targetFps: Int = 12,
    val resolutionPreset: ResolutionPreset = ResolutionPreset.RES_480P,
    val playbackSpeed: Float = 1.0f,
    val outputWidth: Int = 480,
    val outputHeight: Int = 640,
    val estimatedFrameCount: Int = 48,
    val estimatedSizeBytes: Long = 520_000L,
    val encodingState: EncodingProgressState = EncodingProgressState.Idle,
    val customTitle: String = "灵动帧作品"
)

class StudioViewModel(
    private val appContext: Context,
    private val repository: GifRepository,
    private val frameProcessor: VideoFrameProcessor
) : ViewModel() {

    private val _currentTab = MutableStateFlow(StudioTab.HOME)
    val currentTab: StateFlow<StudioTab> = _currentTab.asStateFlow()

    private val _editorState = MutableStateFlow(GifEditorUiState())
    val editorState: StateFlow<GifEditorUiState> = _editorState.asStateFlow()

    private val _countdownSeconds = MutableStateFlow<Int?>(null)
    val countdownSeconds: StateFlow<Int?> = _countdownSeconds.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _simulatedLiveRecordSeconds = MutableStateFlow<Int?>(null)
    val simulatedLiveRecordSeconds: StateFlow<Int?> = _simulatedLiveRecordSeconds.asStateFlow()

    val liveRecordingState: StateFlow<LiveRecordingState> = ScreenRecordService.recordingState

    val allCreations: StateFlow<List<GifCreationEntity>> = repository.allCreations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themePreferences: StateFlow<ThemePreferences> = repository.themePreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences())

    private var previewPlaybackJob: Job? = null
    private var simulatedRecordJob: Job? = null
    private var scrubJob: Job? = null

    init {
        // Observe finished screen recordings from ScreenRecordService and auto-open in Editor
        viewModelScope.launch {
            ScreenRecordService.recordingState.collect { recState ->
                recState.latestRecordedFile?.let { file ->
                    if (file.exists()) {
                        ScreenRecordService.clearConsumedRecording()
                        loadVideoFromUri(
                            uri = Uri.fromFile(file),
                            sourceType = "SCREEN_RECORD",
                            suggestedTitle = "屏幕录制_${file.nameWithoutExtension.takeLast(4)}"
                        )
                        _currentTab.value = StudioTab.EDITOR
                    }
                }
                recState.statusMessage?.let { msg ->
                    _toastMessage.value = msg
                }
            }
        }

        // Seed a showcase animated GIF in the local library if the database is completely empty
        viewModelScope.launch {
            seedStarterCreationIfEmpty()
        }
    }

    fun selectTab(tab: StudioTab) {
        _currentTab.value = tab
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun toggleDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDynamicColor(enabled)
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDarkMode(enabled)
        }
    }

    /**
     * Runs a 3-2-1 expressive spring countdown before invoking [onCountdownFinished].
     */
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

    /**
     * Starts an interactive Android 16 Live Update notification recording flow that posts the real
     * Android 16 ProgressStyle + setRequestPromotedOngoing + setShortCriticalText notification
     * and records an expressive screen capture stream so users can also test notification actions directly.
     */
    fun startLiveNotificationCaptureDemo() {
        simulatedRecordJob?.cancel()
        simulatedRecordJob = viewModelScope.launch {
            var elapsed = 0
            _simulatedLiveRecordSeconds.value = 0
            runCatching {
                val notif = LiveUpdateNotificationHelper.buildRecordingLiveNotification(appContext, 0)
                androidx.core.app.NotificationManagerCompat.from(appContext)
                    .notify(LiveUpdateNotificationHelper.NOTIFICATION_ID_RECORDING, notif)
            }
            while (isActive && _simulatedLiveRecordSeconds.value != null) {
                delay(1000L)
                elapsed++
                _simulatedLiveRecordSeconds.value = elapsed
                runCatching {
                    val notif = LiveUpdateNotificationHelper.buildRecordingLiveNotification(appContext, elapsed)
                    androidx.core.app.NotificationManagerCompat.from(appContext)
                        .notify(LiveUpdateNotificationHelper.NOTIFICATION_ID_RECORDING, notif)
                }
            }
        }
    }

    fun stopLiveNotificationCaptureAndEdit() {
        simulatedRecordJob?.cancel()
        _simulatedLiveRecordSeconds.value = null
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(appContext)
                .cancel(LiveUpdateNotificationHelper.NOTIFICATION_ID_RECORDING)
        }
        loadOrGenerateDemoVideo(sourceType = "SCREEN_RECORD", customTitle = "实时录屏捕获_${System.currentTimeMillis() % 1000}")
    }

    /**
     * Loads a local or picked MP4 video URI into the GIF Studio Editor.
     */
    fun loadVideoFromUri(
        uri: Uri,
        sourceType: String = "VIDEO_IMPORT",
        suggestedTitle: String = "MP4转GIF_${System.currentTimeMillis() % 1000}"
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

    /**
     * Generates a real 4-second 24fps MP4 clip on device and loads it into the Editor.
     */
    fun loadOrGenerateDemoVideo(
        sourceType: String = "VIDEO_IMPORT",
        customTitle: String = "灵动几何演示片段"
    ) {
        viewModelScope.launch {
            _editorState.update { it.copy(isLoadingVideo = true) }
            val result = DemoVideoGenerator.generateExpressiveDemoMp4(appContext)
            result.onSuccess { mp4File ->
                loadVideoFromUri(
                    uri = Uri.fromFile(mp4File),
                    sourceType = sourceType,
                    suggestedTitle = customTitle
                )
            }.onFailure { err ->
                _editorState.update { it.copy(isLoadingVideo = false) }
                _toastMessage.value = "生成演示视频失败: ${err.localizedMessage}"
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
                // Wider than video: full width, centered vertical crop
                val normH = (videoAspect / targetRatio).coerceIn(0.2f, 1f)
                val padY = (1f - normH) / 2f
                NormalizedCropRect(0f, padY, 1f, 1f - padY)
            } else {
                // Taller than video: full height, centered horizontal crop
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

    /**
     * Executes the end-to-end video-to-GIF export pipeline and saves the creation in Room.
     */
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

        viewModelScope.launch {
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
                    title = state.customTitle.ifBlank { "灵动帧_${System.currentTimeMillis() % 1000}" },
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
            }.onFailure { err ->
                _editorState.update {
                    it.copy(
                        encodingState = EncodingProgressState.Error(
                            err.localizedMessage ?: "编码过程中发生错误"
                        )
                    )
                }
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
            _toastMessage.value = "已从作品库删除 ${entity.title}"
        }
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

    private suspend fun seedStarterCreationIfEmpty() = withContext(Dispatchers.IO) {
        runCatching {
            val existing = repository.allCreations.first()
            if (existing.isNotEmpty()) return@withContext

            val gifsDir = File(appContext.filesDir, "gifs").apply { mkdirs() }
            val starterGif = File(gifsDir, "PixelLoop_Starter_Expressive.gif")
            val starterThumb = File(gifsDir, "PixelLoop_Starter_Thumb.png")
            val w = 320
            val h = 320
            val frames = 16

            FileOutputStream(starterGif).use { fos ->
                val encoder = AnimatedGifEncoder(fos)
                encoder.start(w, h, loopCount = 0)
                for (i in 0 until frames) {
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    DemoVideoGenerator.drawExpressiveShowcaseFrame(canvas, w, h, i, frames)
                    if (i == 0) {
                        FileOutputStream(starterThumb).use { thumbOut ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 90, thumbOut)
                        }
                    }
                    encoder.addFrame(bmp, 8)
                    bmp.recycle()
                }
                encoder.finish()
            }

            repository.saveCreation(
                GifCreationEntity(
                    title = "M3 Expressive 灵动示例",
                    filePath = starterGif.absolutePath,
                    thumbnailPath = starterThumb.absolutePath,
                    width = w,
                    height = h,
                    fps = 12,
                    durationMs = 1280L,
                    frameCount = frames,
                    fileSizeBytes = starterGif.length(),
                    sourceType = "SCREEN_RECORD",
                    cropRatioLabel = "1:1 方形",
                    playbackSpeed = 1.0f
                )
            )
        }
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
