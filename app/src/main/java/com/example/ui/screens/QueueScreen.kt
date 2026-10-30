package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.GifCreationEntity
import com.example.engine.BatchQueueConfig
import com.example.engine.BatchQueueUiState
import com.example.engine.QueueTaskItem
import com.example.engine.QueueTaskStatus
import com.example.engine.ResolutionPreset
import com.example.ui.components.AnimatedGifPlayer
import com.example.ui.components.ExpressiveMorphChip
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatFileSize
import kotlin.math.abs
import kotlin.math.max

@Composable
fun QueueScreen(
    queueState: BatchQueueUiState,
    onAddMultipleVideos: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onClearCompleted: () -> Unit,
    onMoveTaskUp: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    onRetryTask: (String) -> Unit,
    onUpdateBatchFps: (Int) -> Unit,
    onUpdateBatchResolution: (ResolutionPreset) -> Unit,
    onUpdateBatchSpeed: (Float) -> Unit,
    onShareCreation: (GifCreationEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("queue_screen_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Bar: Title + Live Status Badge + Add Videos Button
        item {
            QueueHeaderSection(
                queueState = queueState,
                onAddMultipleVideos = onAddMultipleVideos,
                onTogglePauseResume = onTogglePauseResume,
                onClearCompleted = onClearCompleted
            )
        }

        // 2. Batch Preset Parameters Card
        item {
            BatchPresetConfigCard(
                config = queueState.config,
                onUpdateFps = onUpdateBatchFps,
                onUpdateResolution = onUpdateBatchResolution,
                onUpdateSpeed = onUpdateBatchSpeed
            )
        }

        // 3. Queue Task Items or Empty State
        if (queueState.items.isEmpty()) {
            item {
                EmptyQueueStateCard(onAddMultipleVideos = onAddMultipleVideos)
            }
        } else {
            itemsIndexed(
                items = queueState.items,
                key = { _, item -> item.id }
            ) { index, task ->
                QueueTaskCard(
                    index = index + 1,
                    task = task,
                    canMoveUp = index > 0 &&
                        task.status == QueueTaskStatus.PENDING &&
                        queueState.items[index - 1].status == QueueTaskStatus.PENDING,
                    onMoveUp = { onMoveTaskUp(task.id) },
                    onRemove = { onRemoveTask(task.id) },
                    onRetry = { onRetryTask(task.id) },
                    onShareCreation = onShareCreation
                )
            }
        }
    }
}

@Composable
private fun QueueHeaderSection(
    queueState: BatchQueueUiState,
    onAddMultipleVideos: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onClearCompleted: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "任务队列",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                val statusText = when {
                    queueState.isProcessing -> "处理中 (${queueState.completedCount}/${queueState.items.size})"
                    queueState.isPaused && queueState.pendingCount > 0 -> "已暂停 (${queueState.pendingCount})"
                    queueState.items.isNotEmpty() -> "共 ${queueState.items.size} 项"
                    else -> "空闲"
                }

                Surface(
                    shape = CircleShape,
                    color = if (queueState.isProcessing) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (queueState.isProcessing) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Button(
                onClick = onAddMultipleVideos,
                shape = CircleShape,
                modifier = Modifier
                    .height(42.dp)
                    .testTag("queue_add_videos_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.AddToQueue,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("添加视频", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (queueState.items.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (queueState.pendingCount > 0 || queueState.isProcessing) {
                    FilledTonalButton(
                        onClick = onTogglePauseResume,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("queue_pause_resume_button")
                    ) {
                        Icon(
                            imageVector = if (queueState.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (queueState.isPaused) "继续队列" else "暂停队列",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                if (queueState.completedCount > 0) {
                    FilledTonalButton(
                        onClick = onClearCompleted,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("queue_clear_completed_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "清理已完成 (${queueState.completedCount})",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchPresetConfigCard(
    config: BatchQueueConfig,
    onUpdateFps: (Int) -> Unit,
    onUpdateResolution: (ResolutionPreset) -> Unit,
    onUpdateSpeed: (Float) -> Unit
) {
    val batchFpsOptions = listOf(10, 15, 24, 30, 45, 60)
    val batchSpeedOptions = listOf(0.5f, 1.0f, 1.5f, 2.0f)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "队列输出预设",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "${config.defaultResolution.label} · ${config.defaultFps} FPS · ${config.defaultSpeed}x",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(batchFpsOptions) { fps ->
                    ExpressiveMorphChip(
                        label = "$fps FPS",
                        selected = config.defaultFps == fps,
                        onClick = { onUpdateFps(fps) },
                        testTag = "batch_fps_chip_$fps"
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ResolutionPreset.entries) { preset ->
                    ExpressiveMorphChip(
                        label = preset.label,
                        selected = config.defaultResolution == preset,
                        onClick = { onUpdateResolution(preset) },
                        testTag = "batch_res_chip_${preset.name}"
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(batchSpeedOptions) { speed ->
                    ExpressiveMorphChip(
                        label = "${speed}x",
                        selected = abs(config.defaultSpeed - speed) < 0.01f,
                        onClick = { onUpdateSpeed(speed) },
                        testTag = "batch_speed_chip_$speed"
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueTaskCard(
    index: Int,
    task: QueueTaskItem,
    canMoveUp: Boolean,
    onMoveUp: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onShareCreation: (GifCreationEntity) -> Unit
) {
    val progress = if (task.totalFrames > 0) {
        (task.currentFrame.toFloat() / max(1, task.totalFrames)).coerceIn(0f, 1f)
    } else 0f
    val percent = (progress * 100).toInt()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("queue_task_card_${task.id}"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = when (task.status) {
                QueueTaskStatus.PROCESSING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                QueueTaskStatus.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail or finished GIF preview
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center
                ) {
                    val creation = task.resultCreation
                    if (task.status == QueueTaskStatus.COMPLETED && creation != null) {
                        AnimatedGifPlayer(
                            gifFilePath = creation.filePath,
                            thumbnailPath = creation.thumbnailPath,
                            contentDescription = task.metadata.displayName,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (task.thumbnail != null && !task.thumbnail.isRecycled) {
                        Image(
                            bitmap = task.thumbnail.asImageBitmap(),
                            contentDescription = task.metadata.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = "#$index",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Task info & status
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = task.metadata.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        QueueStatusPill(status = task.status, percent = percent)
                    }

                    val clipSec = ((task.trimEndMs - task.trimStartMs) / 1000f).coerceAtLeast(0.3f)
                    val metaSummary = buildString {
                        append("${task.resolutionPreset.label} · ${task.targetFps} FPS · ${task.playbackSpeed}x")
                        append(" · ${String.format("%.1fs", clipSec)}")
                        task.resultCreation?.let {
                            append(" · ${formatFileSize(it.fileSizeBytes)}")
                        }
                    }

                    Text(
                        text = metaSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (task.status == QueueTaskStatus.FAILED && !task.errorMessage.isNullOrBlank()) {
                        Text(
                            text = task.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.status == QueueTaskStatus.PENDING && canMoveUp) {
                        IconButton(
                            onClick = onMoveUp,
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                                .testTag("queue_move_up_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = "上移",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (task.status == QueueTaskStatus.FAILED) {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .testTag("queue_retry_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "重试",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    val finishedCreation = task.resultCreation
                    if (task.status == QueueTaskStatus.COMPLETED && finishedCreation != null) {
                        IconButton(
                            onClick = { onShareCreation(finishedCreation) },
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .testTag("queue_share_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "分享",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                            .testTag("queue_remove_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "移除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (task.status == QueueTaskStatus.PROCESSING) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = "正在编码第 ${task.currentFrame} / ${max(1, task.totalFrames)} 帧 ($percent%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueStatusPill(
    status: QueueTaskStatus,
    percent: Int
) {
    val (bgColor, contentColor, icon, text) = when (status) {
        QueueTaskStatus.PENDING -> Quad(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Filled.Schedule,
            "排队中"
        )
        QueueTaskStatus.PROCESSING -> Quad(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Filled.QueuePlayNext,
            "转码 $percent%"
        )
        QueueTaskStatus.COMPLETED -> Quad(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Filled.CheckCircle,
            "已完成"
        )
        QueueTaskStatus.FAILED -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.ErrorOutline,
            "失败"
        )
    }

    Surface(
        shape = CircleShape,
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
private fun EmptyQueueStateCard(
    onAddMultipleVideos: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.QueuePlayNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = "队列暂无待处理视频",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onAddMultipleVideos,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("empty_queue_add_videos_button"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(imageVector = Icons.Filled.AddToQueue, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("批量选择视频加入队列")
            }
        }
    }
}
