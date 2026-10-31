package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.GifCreationEntity
import com.example.data.GifRepository
import com.example.service.GifProcessingService
import com.example.service.LiveUpdateNotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.min

enum class QueueTaskStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class QueueTaskItem(
    val id: String = UUID.randomUUID().toString(),
    val metadata: VideoSourceMetadata,
    val thumbnail: Bitmap? = null,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = min(metadata.durationMs, 10_000L),
    val cropRect: NormalizedCropRect = NormalizedCropRect(),
    val aspectPreset: CropAspectPreset = CropAspectPreset.FREE,
    val targetFps: Int = 15,
    val resolutionPreset: ResolutionPreset = ResolutionPreset.RES_480P,
    val playbackSpeed: Float = 1.0f,
    val status: QueueTaskStatus = QueueTaskStatus.PENDING,
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val resultCreation: GifCreationEntity? = null,
    val errorMessage: String? = null
)

data class BatchQueueConfig(
    val defaultFps: Int = 15,
    val defaultResolution: ResolutionPreset = ResolutionPreset.RES_480P,
    val defaultSpeed: Float = 1.0f
)

data class BatchQueueUiState(
    val items: List<QueueTaskItem> = emptyList(),
    val config: BatchQueueConfig = BatchQueueConfig(),
    val isProcessing: Boolean = false,
    val isPaused: Boolean = false
) {
    val pendingCount: Int
        get() = items.count { it.status == QueueTaskStatus.PENDING }

    val processingCount: Int
        get() = items.count { it.status == QueueTaskStatus.PROCESSING }

    val completedCount: Int
        get() = items.count { it.status == QueueTaskStatus.COMPLETED }

    val activeBadgeCount: Int
        get() = pendingCount + processingCount
}

/**
 * Sequential background queue manager that accepts multiple video files and converts them
 * one by one into GIFs in the background, persisting each finished GIF to Room and updating
 * Android 16 Live Update notifications.
 */
class BatchQueueManager(
    private val appContext: Context,
    private val repository: GifRepository,
    private val frameProcessor: VideoFrameProcessor
) {
    private val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workerMutex = Mutex()
    private var activeTaskJob: Job? = null

    private val _queueState = MutableStateFlow(BatchQueueUiState())
    val queueState: StateFlow<BatchQueueUiState> = _queueState.asStateFlow()

    /**
     * Adds multiple video URIs to the queue using the current batch default parameters
     * and starts sequential background processing if not paused.
     */
    fun enqueueMultipleVideos(
        uris: List<Uri>,
        onEnqueued: (Int) -> Unit = {}
    ) {
        if (uris.isEmpty()) return
        queueScope.launch(Dispatchers.IO) {
            val currentConfig = _queueState.value.config
            val addedItems = mutableListOf<QueueTaskItem>()

            uris.forEachIndexed { idx, uri ->
                val title = "批量GIF_${(System.currentTimeMillis() + idx) % 10000}"
                val metaResult = frameProcessor.inspectVideo(
                    uri = uri,
                    sourceType = "BATCH_QUEUE",
                    fallbackTitle = title
                )
                metaResult.onSuccess { meta ->
                    val thumb = frameProcessor.extractPreviewFrame(uri, 0L, maxDimension = 320)
                    val defaultEnd = min(meta.durationMs, 10_000L)
                    addedItems.add(
                        QueueTaskItem(
                            metadata = meta,
                            thumbnail = thumb,
                            trimStartMs = 0L,
                            trimEndMs = defaultEnd,
                            cropRect = NormalizedCropRect(),
                            aspectPreset = CropAspectPreset.FREE,
                            targetFps = currentConfig.defaultFps,
                            resolutionPreset = currentConfig.defaultResolution,
                            playbackSpeed = currentConfig.defaultSpeed,
                            status = QueueTaskStatus.PENDING
                        )
                    )
                }
            }

            if (addedItems.isNotEmpty()) {
                _queueState.update { state ->
                    state.copy(items = state.items + addedItems)
                }
                onEnqueued(addedItems.size)
                triggerQueueWorkerIfNeeded()
            } else {
                onEnqueued(0)
            }
        }
    }

    /**
     * Enqueues a single pre-configured video task (e.g. with custom trim/crop from the Editor).
     */
    fun enqueueConfiguredTask(
        metadata: VideoSourceMetadata,
        thumbnail: Bitmap?,
        trimStartMs: Long,
        trimEndMs: Long,
        cropRect: NormalizedCropRect,
        aspectPreset: CropAspectPreset,
        targetFps: Int,
        resolutionPreset: ResolutionPreset,
        playbackSpeed: Float
    ) {
        val item = QueueTaskItem(
            metadata = metadata,
            thumbnail = thumbnail,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            cropRect = cropRect,
            aspectPreset = aspectPreset,
            targetFps = targetFps,
            resolutionPreset = resolutionPreset,
            playbackSpeed = playbackSpeed,
            status = QueueTaskStatus.PENDING
        )
        _queueState.update { state ->
            state.copy(items = state.items + item)
        }
        triggerQueueWorkerIfNeeded()
    }

    /**
     * Updates default batch conversion parameters and applies them to any PENDING tasks.
     */
    fun updateBatchConfig(
        fps: Int = _queueState.value.config.defaultFps,
        resolution: ResolutionPreset = _queueState.value.config.defaultResolution,
        speed: Float = _queueState.value.config.defaultSpeed
    ) {
        val newConfig = BatchQueueConfig(
            defaultFps = fps.coerceIn(5, 60),
            defaultResolution = resolution,
            defaultSpeed = speed
        )
        _queueState.update { state ->
            val updatedItems = state.items.map { item ->
                if (item.status == QueueTaskStatus.PENDING) {
                    item.copy(
                        targetFps = newConfig.defaultFps,
                        resolutionPreset = newConfig.defaultResolution,
                        playbackSpeed = newConfig.defaultSpeed
                    )
                } else {
                    item
                }
            }
            state.copy(config = newConfig, items = updatedItems)
        }
    }

    fun togglePauseResume() {
        val currentlyPaused = _queueState.value.isPaused
        if (currentlyPaused) {
            _queueState.update { it.copy(isPaused = false) }
            triggerQueueWorkerIfNeeded()
        } else {
            _queueState.update { it.copy(isPaused = true) }
        }
    }

    fun moveTaskUp(taskId: String) {
        _queueState.update { state ->
            val list = state.items.toMutableList()
            val index = list.indexOfFirst { it.id == taskId }
            if (index > 0 && list[index].status == QueueTaskStatus.PENDING) {
                val prevIndex = index - 1
                if (list[prevIndex].status == QueueTaskStatus.PENDING) {
                    val temp = list[prevIndex]
                    list[prevIndex] = list[index]
                    list[index] = temp
                }
            }
            state.copy(items = list)
        }
    }

    fun removeTask(taskId: String) {
        val target = _queueState.value.items.find { it.id == taskId } ?: return
        if (target.status == QueueTaskStatus.PROCESSING) {
            activeTaskJob?.cancel()
        }
        _queueState.update { state ->
            state.copy(items = state.items.filterNot { it.id == taskId })
        }
    }

    fun retryTask(taskId: String) {
        _queueState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.id == taskId && item.status == QueueTaskStatus.FAILED) {
                        item.copy(
                            status = QueueTaskStatus.PENDING,
                            currentFrame = 0,
                            errorMessage = null
                        )
                    } else {
                        item
                    }
                },
                isPaused = false
            )
        }
        triggerQueueWorkerIfNeeded()
    }

    fun clearCompletedTasks() {
        _queueState.update { state ->
            state.copy(items = state.items.filterNot { it.status == QueueTaskStatus.COMPLETED })
        }
    }

    private fun triggerQueueWorkerIfNeeded() {
        queueScope.launch {
            if (workerMutex.isLocked) return@launch
            workerMutex.withLock {
                processSequentialLoop()
            }
        }
    }

    private suspend fun processSequentialLoop() {
        var processedInThisRun = 0
        try {
            while (true) {
                val snapshot = _queueState.value
                if (snapshot.isPaused) {
                    _queueState.update { it.copy(isProcessing = false) }
                    LiveUpdateNotificationHelper.notifyBatchQueueProgress(
                        context = appContext,
                        currentItemIndex = 0,
                        totalItems = 0,
                        currentTaskTitle = "",
                        currentFrame = 0,
                        totalFrames = 0,
                        isComplete = true
                    )
                    GifProcessingService.stopEncodingForeground(appContext)
                    break
                }

                val nextTask = snapshot.items.firstOrNull { it.status == QueueTaskStatus.PENDING }
                if (nextTask == null) {
                    _queueState.update { it.copy(isProcessing = false) }
                    LiveUpdateNotificationHelper.notifyBatchQueueProgress(
                        context = appContext,
                        currentItemIndex = 0,
                        totalItems = 0,
                        currentTaskTitle = "",
                        currentFrame = 0,
                        totalFrames = 0,
                        isComplete = true
                    )
                    GifProcessingService.stopEncodingForeground(appContext)
                    if (processedInThisRun > 0) {
                        LiveUpdateNotificationHelper.notifyProcessingCompleted(
                            context = appContext,
                            title = "批量 GIF 队列已完成 ($processedInThisRun 项)",
                            subtitle = "点击查看已导出的 GIF 作品",
                            targetTab = LiveUpdateNotificationHelper.TAB_QUEUE
                        )
                    }
                    break
                }

                val totalQueueItems = snapshot.items.size
                val currentItemNumber = (snapshot.items.indexOfFirst { it.id == nextTask.id } + 1)
                    .coerceAtLeast(1)

                LiveUpdateNotificationHelper.beginQueueItemSession(
                    context = appContext,
                    currentItemIndex = currentItemNumber,
                    totalItems = totalQueueItems,
                    currentTaskTitle = nextTask.metadata.displayName,
                    totalFrames = 100
                )

                // Promote process to Foreground Service + WakeLock so backgrounding never freezes GIF encoding
                GifProcessingService.startQueueForeground(
                    context = appContext,
                    currentItemIndex = currentItemNumber,
                    totalItems = totalQueueItems,
                    currentTaskTitle = nextTask.metadata.displayName,
                    currentFrame = 0,
                    totalFrames = 100
                )

                _queueState.update { state ->
                    state.copy(
                        isProcessing = true,
                        items = state.items.map { item ->
                            if (item.id == nextTask.id) {
                                item.copy(status = QueueTaskStatus.PROCESSING, currentFrame = 0)
                            } else {
                                item
                            }
                        }
                    )
                }

                val job = queueScope.launch {
                    val exportResult = frameProcessor.exportVideoToGif(
                        metadata = nextTask.metadata,
                        trimStartMs = nextTask.trimStartMs,
                        trimEndMs = nextTask.trimEndMs,
                        cropRect = nextTask.cropRect,
                        targetFps = nextTask.targetFps,
                        resolutionPreset = nextTask.resolutionPreset,
                        playbackSpeed = nextTask.playbackSpeed,
                        onProgress = { current, total ->
                            _queueState.update { state ->
                                state.copy(
                                    items = state.items.map { item ->
                                        if (item.id == nextTask.id) {
                                            item.copy(currentFrame = current, totalFrames = total)
                                        } else {
                                            item
                                        }
                                    }
                                )
                            }
                            LiveUpdateNotificationHelper.notifyBatchQueueProgress(
                                context = appContext,
                                currentItemIndex = currentItemNumber,
                                totalItems = totalQueueItems,
                                currentTaskTitle = nextTask.metadata.displayName,
                                currentFrame = current,
                                totalFrames = total,
                                isComplete = false
                            )
                        }
                    )

                    exportResult.onSuccess { res ->
                        processedInThisRun++
                        val entity = GifCreationEntity(
                            title = nextTask.metadata.displayName,
                            filePath = res.gifFile.absolutePath,
                            thumbnailPath = res.thumbnailFile.absolutePath,
                            width = res.outputWidth,
                            height = res.outputHeight,
                            fps = nextTask.targetFps,
                            durationMs = res.durationMs,
                            frameCount = res.frameCount,
                            fileSizeBytes = res.fileSizeBytes,
                            sourceType = nextTask.metadata.sourceType,
                            cropRatioLabel = nextTask.aspectPreset.label,
                            playbackSpeed = nextTask.playbackSpeed
                        )
                        val savedId = repository.saveCreation(entity)
                        val savedEntity = entity.copy(id = savedId)

                        _queueState.update { state ->
                            state.copy(
                                items = state.items.map { item ->
                                    if (item.id == nextTask.id) {
                                        item.copy(
                                            status = QueueTaskStatus.COMPLETED,
                                            currentFrame = res.frameCount,
                                            totalFrames = res.frameCount,
                                            resultCreation = savedEntity
                                        )
                                    } else {
                                        item
                                    }
                                }
                            )
                        }
                    }.onFailure { err ->
                        if (err is CancellationException) return@onFailure
                        _queueState.update { state ->
                            state.copy(
                                items = state.items.map { item ->
                                    if (item.id == nextTask.id) {
                                        item.copy(
                                            status = QueueTaskStatus.FAILED,
                                            errorMessage = err.localizedMessage ?: "转码失败"
                                        )
                                    } else {
                                        item
                                    }
                                }
                            )
                        }
                    }
                }

                activeTaskJob = job
                job.join()
                activeTaskJob = null
            }
        } finally {
            GifProcessingService.stopEncodingForeground(appContext)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: BatchQueueManager? = null

        fun getInstance(
            appContext: Context,
            repository: GifRepository,
            frameProcessor: VideoFrameProcessor
        ): BatchQueueManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BatchQueueManager(
                    appContext = appContext.applicationContext,
                    repository = repository,
                    frameProcessor = frameProcessor
                ).also { INSTANCE = it }
            }
        }
    }
}
