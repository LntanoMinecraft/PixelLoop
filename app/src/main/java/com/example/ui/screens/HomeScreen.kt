package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GifCreationEntity
import com.example.ui.components.AnimatedGifPlayer
import com.example.ui.components.formatFileSize
import com.example.ui.theme.ExpressiveSprings

@Composable
fun HomeScreen(
    isRecordingActive: Boolean,
    recordingElapsedSeconds: Int,
    countdownValue: Int?,
    activeQueueCount: Int,
    recentCreations: List<GifCreationEntity>,
    recordingsCount: Int,
    onStartScreenRecord: () -> Unit,
    onStopLiveRecordAndEdit: () -> Unit,
    onAddTileToControlCenter: () -> Unit,
    onPickLocalVideo: () -> Unit,
    onPickMultipleVideosForQueue: () -> Unit,
    onOpenQueue: () -> Unit,
    onShareCreation: (GifCreationEntity) -> Unit,
    onOpenGallery: () -> Unit,
    bottomBarClearance: Dp = 112.dp,
    modifier: Modifier = Modifier
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp)
                .testTag("home_screen_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = bottomBarClearance + navBarBottom
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Clean Brand Top Header (PixelLoop only; theme controls moved to Settings)
            item {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // 2. Active Recording Banner (Shown only while recording is active)
            if (isRecordingActive) {
                item {
                    ActiveRecordingBanner(
                        elapsedSeconds = recordingElapsedSeconds,
                        onStopAndEditClick = onStopLiveRecordAndEdit
                    )
                }
            }

            // 2b. Active Background Queue Banner (Shown when queue has pending/processing items)
            if (activeQueueCount > 0) {
                item {
                    ActiveQueueBanner(
                        activeQueueCount = activeQueueCount,
                        onOpenQueueClick = onOpenQueue
                    )
                }
            }

            // 3. Adaptive Dual Action Cards: 2-Column on Medium/Expanded, 1-Column on Compact
            item {
                DualCreationHeroSection(
                    isRecordingActive = isRecordingActive,
                    onStartScreenRecord = onStartScreenRecord,
                    onStopLiveRecordAndEdit = onStopLiveRecordAndEdit,
                    onAddTileToControlCenter = onAddTileToControlCenter,
                    onPickLocalVideo = onPickLocalVideo,
                    onPickMultipleVideosForQueue = onPickMultipleVideosForQueue
                )
            }

            // 4. Recent Creations Carousel or Compact Quick Library Entry
            item {
                if (recentCreations.isNotEmpty()) {
                    RecentCreationsSection(
                        creations = recentCreations,
                        onShareCreation = onShareCreation,
                        onOpenGallery = onOpenGallery
                    )
                } else {
                    CompactLibraryShortcutCard(
                        recordingsCount = recordingsCount,
                        onOpenGallery = onOpenGallery
                    )
                }
            }
        }

        // Full-Screen 3-2-1 Expressive Spring Countdown Overlay
        AnimatedVisibility(
            visible = countdownValue != null,
            enter = fadeIn() + scaleIn(animationSpec = ExpressiveSprings.bouncySpatial()),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            CountdownOverlay(secondsLeft = countdownValue ?: 3)
        }
    }
}

@Composable
private fun ActiveQueueBanner(
    activeQueueCount: Int,
    onOpenQueueClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onOpenQueueClick)
            .testTag("active_queue_banner"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.QueuePlayNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "后台队列处理中 ($activeQueueCount 项)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "查看队列",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ActiveRecordingBanner(
    elapsedSeconds: Int,
    onStopAndEditClick: () -> Unit
) {
    val mins = elapsedSeconds / 60
    val secs = elapsedSeconds % 60
    val formattedTimer = String.format("%02d:%02d", mins, secs)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("android16_live_update_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Text(
                    text = "录制中 $formattedTimer",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Button(
                onClick = onStopAndEditClick,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("stop_live_record_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.StopCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "停止并编辑",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DualCreationHeroSection(
    isRecordingActive: Boolean,
    onStartScreenRecord: () -> Unit,
    onStopLiveRecordAndEdit: () -> Unit,
    onAddTileToControlCenter: () -> Unit,
    onPickLocalVideo: () -> Unit,
    onPickMultipleVideosForQueue: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isTwoColumn = maxWidth >= 600.dp

        if (isTwoColumn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                VideoImportHeroCard(
                    onPickLocalVideo = onPickLocalVideo,
                    onPickMultipleVideosForQueue = onPickMultipleVideosForQueue,
                    modifier = Modifier.weight(1f)
                )
                ScreenRecordHeroCard(
                    isRecordingActive = isRecordingActive,
                    onStartScreenRecord = onStartScreenRecord,
                    onStopLiveRecordAndEdit = onStopLiveRecordAndEdit,
                    onAddTileToControlCenter = onAddTileToControlCenter,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                VideoImportHeroCard(
                    onPickLocalVideo = onPickLocalVideo,
                    onPickMultipleVideosForQueue = onPickMultipleVideosForQueue,
                    modifier = Modifier.fillMaxWidth()
                )
                ScreenRecordHeroCard(
                    isRecordingActive = isRecordingActive,
                    onStartScreenRecord = onStartScreenRecord,
                    onStopLiveRecordAndEdit = onStopLiveRecordAndEdit,
                    onAddTileToControlCenter = onAddTileToControlCenter,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun VideoImportHeroCard(
    onPickLocalVideo: () -> Unit,
    onPickMultipleVideosForQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("import_mp4_card"),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.card_import_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "画面裁剪、时间截取与批量转码",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AdaptiveDualActionButtons(
                primaryLabel = stringResource(R.string.btn_pick_video),
                primaryIcon = Icons.Filled.VideoLibrary,
                primaryContainerColor = MaterialTheme.colorScheme.primary,
                primaryContentColor = MaterialTheme.colorScheme.onPrimary,
                primaryTestTag = "pick_mp4_button",
                onPrimaryClick = onPickLocalVideo,
                secondaryLabel = stringResource(R.string.btn_batch_queue),
                secondaryIcon = Icons.Filled.AddToQueue,
                secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                secondaryContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                secondaryTestTag = "batch_queue_mp4_button",
                onSecondaryClick = onPickMultipleVideosForQueue
            )
        }
    }
}

@Composable
private fun ScreenRecordHeroCard(
    isRecordingActive: Boolean,
    onStartScreenRecord: () -> Unit,
    onStopLiveRecordAndEdit: () -> Unit,
    onAddTileToControlCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("screen_record_card"),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.ScreenShare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.card_record_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "录制屏幕片段并自动载入剪辑",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AdaptiveDualActionButtons(
                primaryLabel = if (isRecordingActive) {
                    stringResource(R.string.btn_stop_record_now)
                } else {
                    stringResource(R.string.btn_start_record)
                },
                primaryIcon = if (isRecordingActive) Icons.Filled.StopCircle else Icons.Filled.FiberManualRecord,
                primaryContainerColor = if (isRecordingActive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                primaryContentColor = if (isRecordingActive) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                primaryTestTag = "start_screen_record_button",
                onPrimaryClick = if (isRecordingActive) onStopLiveRecordAndEdit else onStartScreenRecord,
                secondaryLabel = stringResource(R.string.btn_add_control_center),
                secondaryIcon = Icons.Filled.DashboardCustomize,
                secondaryContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                secondaryTestTag = "add_control_center_tile_button",
                onSecondaryClick = onAddTileToControlCenter
            )
        }
    }
}

/**
 * Container-aware adaptive dual button layout with generous touch height and strong contrast.
 */
@Composable
private fun AdaptiveDualActionButtons(
    primaryLabel: String,
    primaryIcon: ImageVector,
    primaryContainerColor: Color,
    primaryContentColor: Color,
    primaryTestTag: String,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String,
    secondaryIcon: ImageVector,
    secondaryContainerColor: Color,
    secondaryContentColor: Color,
    secondaryTestTag: String,
    onSecondaryClick: () -> Unit
) {
    val compactPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackVertically = maxWidth < 300.dp

        if (stackVertically) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPrimaryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag(primaryTestTag),
                    shape = CircleShape,
                    contentPadding = compactPadding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryContainerColor,
                        contentColor = primaryContentColor
                    )
                ) {
                    Icon(
                        imageVector = primaryIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = primaryLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }

                FilledTonalButton(
                    onClick = onSecondaryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag(secondaryTestTag),
                    shape = CircleShape,
                    contentPadding = compactPadding,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = secondaryContainerColor,
                        contentColor = secondaryContentColor
                    )
                ) {
                    Icon(
                        imageVector = secondaryIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = secondaryLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPrimaryClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag(primaryTestTag),
                    shape = CircleShape,
                    contentPadding = compactPadding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryContainerColor,
                        contentColor = primaryContentColor
                    )
                ) {
                    Icon(
                        imageVector = primaryIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = primaryLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }

                FilledTonalButton(
                    onClick = onSecondaryClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag(secondaryTestTag),
                    shape = CircleShape,
                    contentPadding = compactPadding,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = secondaryContainerColor,
                        contentColor = secondaryContentColor
                    )
                ) {
                    Icon(
                        imageVector = secondaryIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = secondaryLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactLibraryShortcutCard(
    recordingsCount: Int,
    onOpenGallery: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onOpenGallery)
            .testTag("home_open_gallery_button"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesomeMotion,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "最近作品",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (recordingsCount > 0) {
                            "作品库已有 $recordingsCount 段录屏原片"
                        } else {
                            "导出的 GIF 与录屏原片会保存在作品库"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "进入作品库",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RecentCreationsSection(
    creations: List<GifCreationEntity>,
    onShareCreation: (GifCreationEntity) -> Unit,
    onOpenGallery: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "最近作品",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(onClick = onOpenGallery)
                    .testTag("home_open_gallery_button"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "全部 (${creations.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(creations.take(6), key = { it.id }) { item ->
                Surface(
                    modifier = Modifier
                        .width(196.dp)
                        .testTag("recent_gif_card_${item.id}"),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(124.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            AnimatedGifPlayer(
                                gifFilePath = item.filePath,
                                thumbnailPath = item.thumbnailPath,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize()
                            )

                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = formatFileSize(item.fileSizeBytes),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${item.width}×${item.height} · ${item.fps} FPS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { onShareCreation(item) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    )
                                    .testTag("recent_share_button_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "分享",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
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
private fun CountdownOverlay(secondsLeft: Int) {
    val scale by animateFloatAsState(
        targetValue = 1.06f,
        animationSpec = ExpressiveSprings.bouncySpatial(),
        label = "countdownScale"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(144.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = secondsLeft.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
