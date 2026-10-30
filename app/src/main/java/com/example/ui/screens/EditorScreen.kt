package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.GifCreationEntity
import com.example.engine.CropAspectPreset
import com.example.engine.NormalizedCropRect
import com.example.engine.ResolutionPreset
import com.example.ui.EncodingProgressState
import com.example.ui.GifEditorUiState
import com.example.ui.components.AnimatedGifPlayer
import com.example.ui.components.ExpressiveMorphChip
import com.example.ui.components.formatDurationMs
import com.example.ui.components.formatFileSize
import com.example.ui.theme.ExpressiveSprings
import kotlin.math.abs
import kotlin.math.max

private enum class DragTarget {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER_PAN
}

@Composable
fun EditorScreen(
    uiState: GifEditorUiState,
    onPickVideo: () -> Unit,
    onSelectAspectPreset: (CropAspectPreset) -> Unit,
    onUpdateCropRect: (NormalizedCropRect) -> Unit,
    onUpdateTrimRange: (Long, Long) -> Unit,
    onTogglePreviewPlayback: () -> Unit,
    onUpdateFps: (Int) -> Unit,
    onUpdateResolution: (ResolutionPreset) -> Unit,
    onUpdateSpeed: (Float) -> Unit,
    onAddToQueue: () -> Unit = {},
    onExportGif: () -> Unit,
    onDismissEncodingDialog: () -> Unit,
    onShareCreation: (GifCreationEntity) -> Unit,
    onSaveCreationToGallery: (GifCreationEntity) -> Unit,
    onNavigateToGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.videoMetadata == null && !uiState.isLoadingVideo) {
        EmptyEditorView(
            onPickVideo = onPickVideo,
            modifier = modifier
        )
        return
    }

    val videoAspectRatio = remember(uiState.videoMetadata, uiState.currentPreviewBitmap) {
        val meta = uiState.videoMetadata
        val bmp = uiState.currentPreviewBitmap
        when {
            bmp != null && bmp.width > 0 && bmp.height > 0 ->
                (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.32f, 2.8f)
            meta != null -> meta.aspectRatio
            else -> 0.75f
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("editor_scroll_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Adaptive-Ratio Spatial Crop Canvas + Aspect Ratio Morphing Bar
            item {
                InteractiveCropCanvasCard(
                    previewBitmap = uiState.currentPreviewBitmap,
                    videoAspectRatio = videoAspectRatio,
                    cropRect = uiState.cropRect,
                    selectedPreset = uiState.aspectPreset,
                    outputWidth = uiState.outputWidth,
                    outputHeight = uiState.outputHeight,
                    currentTimeMs = uiState.currentPreviewTimeMs,
                    isPlaying = uiState.isPlayingPreview,
                    onTogglePlayback = onTogglePreviewPlayback,
                    onSelectPreset = onSelectAspectPreset,
                    onUpdateCrop = onUpdateCropRect
                )
            }

            // 2. Filmstrip Timeline & Start/End Trim Range
            item {
                val durationMs = uiState.videoMetadata?.durationMs ?: 4000L
                FilmstripTimelineCard(
                    thumbnails = uiState.timelineThumbnails,
                    totalDurationMs = durationMs,
                    trimStartMs = uiState.trimStartMs,
                    trimEndMs = uiState.trimEndMs,
                    playbackSpeed = uiState.playbackSpeed,
                    onUpdateTrimRange = onUpdateTrimRange
                )
            }

            // 3. Unified Parameter Console (FPS up to 60fps, Resolution, Playback Speed)
            item {
                ParameterConsoleCard(
                    targetFps = uiState.targetFps,
                    resolutionPreset = uiState.resolutionPreset,
                    playbackSpeed = uiState.playbackSpeed,
                    onUpdateFps = onUpdateFps,
                    onUpdateResolution = onUpdateResolution,
                    onUpdateSpeed = onUpdateSpeed
                )
            }

            // 4. Export Summary & Primary Action Card inside scroll flow above Floating Nav Bar
            item {
                ExportActionCard(
                    estimatedSizeBytes = uiState.estimatedSizeBytes,
                    estimatedFrameCount = uiState.estimatedFrameCount,
                    outputWidth = uiState.outputWidth,
                    outputHeight = uiState.outputHeight,
                    targetFps = uiState.targetFps,
                    playbackSpeed = uiState.playbackSpeed,
                    onAddToQueue = onAddToQueue,
                    onExportGif = onExportGif
                )
            }
        }

        // Encoding & Export Result Modal Dialog
        when (val encState = uiState.encodingState) {
            is EncodingProgressState.Encoding -> {
                EncodingProgressDialog(
                    currentFrame = encState.currentFrame,
                    totalFrames = encState.totalFrames
                )
            }
            is EncodingProgressState.Completed -> {
                ExportSuccessDialog(
                    creation = encState.creation,
                    onDismiss = onDismissEncodingDialog,
                    onShareSocial = { onShareCreation(encState.creation) },
                    onSaveGallery = { onSaveCreationToGallery(encState.creation) },
                    onViewInLibrary = {
                        onDismissEncodingDialog()
                        onNavigateToGallery()
                    }
                )
            }
            is EncodingProgressState.Error -> {
                Dialog(onDismissRequest = onDismissEncodingDialog) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "转码遇到问题",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = encState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = onDismissEncodingDialog) {
                                Text("我知道了")
                            }
                        }
                    }
                }
            }
            EncodingProgressState.Idle -> Unit
        }
    }
}

@Composable
private fun ExportActionCard(
    estimatedSizeBytes: Long,
    estimatedFrameCount: Int,
    outputWidth: Int,
    outputHeight: Int,
    targetFps: Int,
    playbackSpeed: Float,
    onAddToQueue: () -> Unit,
    onExportGif: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "预计 ${formatFileSize(estimatedSizeBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "共 $estimatedFrameCount 帧",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "$outputWidth × $outputHeight px · $targetFps FPS · ${playbackSpeed}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onAddToQueue,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_to_queue_button"),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddToQueue,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_add_to_queue),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = onExportGif,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("export_gif_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_export_gif),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyEditorView(
    onPickVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
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
                            imageVector = Icons.Filled.Crop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.editor_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = onPickVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("editor_pick_video_button"),
                    shape = CircleShape
                ) {
                    Icon(imageVector = Icons.Filled.VideoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_pick_video))
                }
            }
        }
    }
}

@Composable
private fun InteractiveCropCanvasCard(
    previewBitmap: Bitmap?,
    videoAspectRatio: Float,
    cropRect: NormalizedCropRect,
    selectedPreset: CropAspectPreset,
    outputWidth: Int,
    outputHeight: Int,
    currentTimeMs: Long,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onSelectPreset: (CropAspectPreset) -> Unit,
    onUpdateCrop: (NormalizedCropRect) -> Unit
) {
    val currentCrop by rememberUpdatedState(cropRect)
    var activeDragTarget by remember { mutableStateOf(DragTarget.NONE) }

    val handleScale by animateFloatAsState(
        targetValue = if (activeDragTarget != DragTarget.NONE) 1.25f else 1.0f,
        animationSpec = ExpressiveSprings.bouncySpatial(),
        label = "handleScale"
    )

    val cropFrameColor = MaterialTheme.colorScheme.primary
    val handleCenterColor = MaterialTheme.colorScheme.onPrimary

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Title + Output Resolution Badge + Reset Button
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
                        imageVector = Icons.Filled.Crop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "画面裁剪与预览",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${outputWidth}×${outputHeight} px",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = { onSelectPreset(CropAspectPreset.FREE) },
                        modifier = Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                            .testTag("reset_crop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.btn_reset_crop),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Adaptive Aspect-Ratio Stage: automatically matches the video's native aspect ratio
            // so portrait (9:16 / 9:20) and landscape (16:9) videos never stretch or distort!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 380.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(8.dp)
                    .testTag("interactive_crop_canvas"),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .aspectRatio(
                            ratio = videoAspectRatio,
                            matchHeightConstraintsFirst = videoAspectRatio < 1f
                        )
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val canvasW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val canvasH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                    if (previewBitmap != null && !previewBitmap.isRecycled) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "视频帧画面预览",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(videoAspectRatio) {
                                val touchSlopPx = 44.dp.toPx()
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val c = currentCrop
                                        val lPx = c.left * size.width
                                        val tPx = c.top * size.height
                                        val rPx = c.right * size.width
                                        val bPx = c.bottom * size.height

                                        activeDragTarget = when {
                                            abs(offset.x - lPx) < touchSlopPx && abs(offset.y - tPx) < touchSlopPx -> DragTarget.TOP_LEFT
                                            abs(offset.x - rPx) < touchSlopPx && abs(offset.y - tPx) < touchSlopPx -> DragTarget.TOP_RIGHT
                                            abs(offset.x - lPx) < touchSlopPx && abs(offset.y - bPx) < touchSlopPx -> DragTarget.BOTTOM_LEFT
                                            abs(offset.x - rPx) < touchSlopPx && abs(offset.y - bPx) < touchSlopPx -> DragTarget.BOTTOM_RIGHT
                                            offset.x in lPx..rPx && offset.y in tPx..bPx -> DragTarget.CENTER_PAN
                                            else -> DragTarget.NONE
                                        }
                                    },
                                    onDragEnd = { activeDragTarget = DragTarget.NONE },
                                    onDragCancel = { activeDragTarget = DragTarget.NONE },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val dx = dragAmount.x / canvasW
                                        val dy = dragAmount.y / canvasH
                                        val c = currentCrop

                                        val updated = when (activeDragTarget) {
                                            DragTarget.TOP_LEFT -> c.copy(
                                                left = (c.left + dx).coerceIn(0f, c.right - 0.18f),
                                                top = (c.top + dy).coerceIn(0f, c.bottom - 0.18f)
                                            )
                                            DragTarget.TOP_RIGHT -> c.copy(
                                                right = (c.right + dx).coerceIn(c.left + 0.18f, 1f),
                                                top = (c.top + dy).coerceIn(0f, c.bottom - 0.18f)
                                            )
                                            DragTarget.BOTTOM_LEFT -> c.copy(
                                                left = (c.left + dx).coerceIn(0f, c.right - 0.18f),
                                                bottom = (c.bottom + dy).coerceIn(c.top + 0.18f, 1f)
                                            )
                                            DragTarget.BOTTOM_RIGHT -> c.copy(
                                                right = (c.right + dx).coerceIn(c.left + 0.18f, 1f),
                                                bottom = (c.bottom + dy).coerceIn(c.top + 0.18f, 1f)
                                            )
                                            DragTarget.CENTER_PAN -> {
                                                val w = c.widthFraction
                                                val h = c.heightFraction
                                                val nl = (c.left + dx).coerceIn(0f, 1f - w)
                                                val nt = (c.top + dy).coerceIn(0f, 1f - h)
                                                NormalizedCropRect(nl, nt, nl + w, nt + h)
                                            }
                                            DragTarget.NONE -> c
                                        }
                                        onUpdateCrop(updated)
                                    }
                                )
                            }
                    ) {
                        val leftPx = cropRect.left * size.width
                        val topPx = cropRect.top * size.height
                        val rightPx = cropRect.right * size.width
                        val bottomPx = cropRect.bottom * size.height
                        val cropW = (rightPx - leftPx).coerceAtLeast(10f)
                        val cropH = (bottomPx - topPx).coerceAtLeast(10f)

                        val cutoutPath = Path().apply {
                            addRect(androidx.compose.ui.geometry.Rect(leftPx, topPx, rightPx, bottomPx))
                        }
                        clipPath(cutoutPath, clipOp = ClipOp.Difference) {
                            drawRect(color = Color(0xAA0A0C10))
                        }

                        val gridStroke = 1.dp.toPx()
                        val gridColor = Color.White.copy(alpha = 0.35f)
                        for (i in 1..2) {
                            val gx = leftPx + (cropW * i / 3f)
                            val gy = topPx + (cropH * i / 3f)
                            drawLine(gridColor, Offset(gx, topPx), Offset(gx, bottomPx), gridStroke)
                            drawLine(gridColor, Offset(leftPx, gy), Offset(rightPx, gy), gridStroke)
                        }

                        drawRect(
                            color = cropFrameColor,
                            topLeft = Offset(leftPx, topPx),
                            size = Size(cropW, cropH),
                            style = Stroke(width = 2.dp.toPx())
                        )

                        val handleRadius = 7.dp.toPx() * handleScale
                        val corners = listOf(
                            Offset(leftPx, topPx),
                            Offset(rightPx, topPx),
                            Offset(leftPx, bottomPx),
                            Offset(rightPx, bottomPx)
                        )
                        corners.forEach { pt ->
                            drawCircle(color = cropFrameColor, radius = handleRadius, center = pt)
                            drawCircle(color = handleCenterColor, radius = handleRadius * 0.4f, center = pt)
                        }
                    }
                }
            }

            // Dedicated Playback Control & Timecode Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onTogglePlayback)
                        .testTag("toggle_preview_play_button"),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) {
                                stringResource(R.string.btn_pause_preview)
                            } else {
                                stringResource(R.string.btn_preview_loop)
                            },
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = formatDurationMs(currentTimeMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(CropAspectPreset.entries) { preset ->
                    ExpressiveMorphChip(
                        label = preset.label,
                        selected = preset == selectedPreset,
                        onClick = { onSelectPreset(preset) },
                        testTag = "crop_aspect_chip_${preset.name}"
                    )
                }
            }
        }
    }
}

@Composable
private fun FilmstripTimelineCard(
    thumbnails: List<Bitmap>,
    totalDurationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    playbackSpeed: Float,
    onUpdateTrimRange: (Long, Long) -> Unit
) {
    val safeTotalMs = max(1000L, totalDurationMs)
    val startFraction = (trimStartMs.toFloat() / safeTotalMs).coerceIn(0f, 0.95f)
    val endFraction = (trimEndMs.toFloat() / safeTotalMs).coerceIn(startFraction + 0.05f, 1f)
    val clippedDurationSec = ((trimEndMs - trimStartMs) / 1000f / playbackSpeed).coerceAtLeast(0.2f)
    val activeFrameColor = MaterialTheme.colorScheme.primary

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
                Text(
                    text = stringResource(R.string.section_timeline_trim),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = String.format(
                            "%s - %s (%.1fs)",
                            formatDurationMs(trimStartMs),
                            formatDurationMs(trimEndMs),
                            clippedDurationSec
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    thumbnails.forEach { bmp ->
                        if (!bmp.isRecycled) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val leftX = startFraction * size.width
                    val rightX = endFraction * size.width
                    val activeW = (rightX - leftX).coerceAtLeast(8f)

                    if (leftX > 0f) {
                        drawRect(
                            color = Color(0xAA0A0C10),
                            topLeft = Offset(0f, 0f),
                            size = Size(leftX, size.height)
                        )
                    }
                    if (rightX < size.width) {
                        drawRect(
                            color = Color(0xAA0A0C10),
                            topLeft = Offset(rightX, 0f),
                            size = Size(size.width - rightX, size.height)
                        )
                    }

                    drawRoundRect(
                        color = activeFrameColor,
                        topLeft = Offset(leftX, 0f),
                        size = Size(activeW, size.height),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }

            RangeSlider(
                value = startFraction..endFraction,
                onValueChange = { range ->
                    val newStart = (range.start * safeTotalMs).toLong()
                    val newEnd = (range.endInclusive * safeTotalMs).toLong()
                    onUpdateTrimRange(newStart, newEnd)
                },
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("timeline_range_slider")
            )
        }
    }
}

@Composable
private fun ParameterConsoleCard(
    targetFps: Int,
    resolutionPreset: ResolutionPreset,
    playbackSpeed: Float,
    onUpdateFps: (Int) -> Unit,
    onUpdateResolution: (ResolutionPreset) -> Unit,
    onUpdateSpeed: (Float) -> Unit
) {
    val fpsOptions = listOf(5, 8, 10, 12, 15, 20, 24, 30, 45, 60)
    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.section_parameters),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.label_fps),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$targetFps FPS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = targetFps.toFloat(),
                    onValueChange = { onUpdateFps(it.toInt()) },
                    valueRange = 5f..60f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fps_slider")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fpsOptions) { fps ->
                        ExpressiveMorphChip(
                            label = "$fps FPS",
                            selected = targetFps == fps,
                            onClick = { onUpdateFps(fps) },
                            testTag = "fps_chip_$fps"
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.label_resolution),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ResolutionPreset.entries) { preset ->
                        ExpressiveMorphChip(
                            label = preset.label,
                            selected = resolutionPreset == preset,
                            onClick = { onUpdateResolution(preset) },
                            testTag = "res_chip_${preset.name}"
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.label_speed),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(speedOptions) { speed ->
                        ExpressiveMorphChip(
                            label = "${speed}x",
                            selected = abs(playbackSpeed - speed) < 0.01f,
                            onClick = { onUpdateSpeed(speed) },
                            testTag = "speed_chip_$speed"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EncodingProgressDialog(
    currentFrame: Int,
    totalFrames: Int
) {
    val progress = (currentFrame.toFloat() / max(1, totalFrames)).coerceIn(0f, 1f)
    val percent = (progress * 100).toInt()

    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(80.dp),
                        strokeWidth = 7.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.exporting_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "$currentFrame / $totalFrames 帧",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ExportSuccessDialog(
    creation: GifCreationEntity,
    onDismiss: () -> Unit,
    onShareSocial: () -> Unit,
    onSaveGallery: () -> Unit,
    onViewInLibrary: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.export_complete_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(18.dp)
                        )
                ) {
                    AnimatedGifPlayer(
                        gifFilePath = creation.filePath,
                        thumbnailPath = creation.thumbnailPath,
                        contentDescription = creation.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = formatFileSize(creation.fileSizeBytes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = "${creation.width}×${creation.height} · ${creation.fps} FPS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Button(
                    onClick = onShareSocial,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_share_social_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_share_social))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSaveGallery,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("dialog_save_gallery_button"),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("存至相册")
                    }

                    FilledTonalButton(
                        onClick = onViewInLibrary,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("dialog_view_library_button"),
                        shape = CircleShape
                    ) {
                        Text("查看作品库")
                    }
                }
            }
        }
    }
}
