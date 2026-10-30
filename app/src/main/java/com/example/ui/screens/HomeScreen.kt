package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GifCreationEntity
import com.example.data.ThemePreferences
import com.example.ui.components.Android16LiveUpdateCard
import com.example.ui.components.AnimatedGifPlayer
import com.example.ui.components.formatFileSize
import com.example.ui.theme.ExpressiveSprings

@Composable
fun HomeScreen(
    themePreferences: ThemePreferences,
    isRecordingActive: Boolean,
    recordingElapsedSeconds: Int,
    countdownValue: Int?,
    recentCreations: List<GifCreationEntity>,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onStartScreenRecord: () -> Unit,
    onStopLiveRecordAndEdit: () -> Unit,
    onStartLiveNotifCaptureDemo: () -> Unit,
    onPickLocalVideo: () -> Unit,
    onGenerateDemoVideo: () -> Unit,
    onShareCreation: (GifCreationEntity) -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Clean Top Header + Theme Controls
            item {
                ExpressiveTopHeader(
                    themePreferences = themePreferences,
                    onToggleDynamicColor = onToggleDynamicColor,
                    onToggleDarkMode = onToggleDarkMode
                )
            }

            // 2. Unified Dual Creation Cards (MP4 to GIF & Screen Record to GIF)
            item {
                DualCreationHeroSection(
                    isRecordingActive = isRecordingActive,
                    onStartScreenRecord = onStartScreenRecord,
                    onStopLiveRecordAndEdit = onStopLiveRecordAndEdit,
                    onPickLocalVideo = onPickLocalVideo,
                    onGenerateDemoVideo = onGenerateDemoVideo
                )
            }

            // 3. Android 16 (API 36) Live Updates Interactive Status Card
            item {
                Android16LiveUpdateCard(
                    isRecording = isRecordingActive,
                    elapsedSeconds = recordingElapsedSeconds,
                    isPromotedSupported = true,
                    onStopAndEditClick = onStopLiveRecordAndEdit,
                    onTestLiveNotificationClick = onStartLiveNotifCaptureDemo
                )
            }

            // 4. Clean Specification Summary Grid
            item {
                UnifiedFeatureGrid()
            }

            // 5. Recent GIF Creations Carousel
            if (recentCreations.isNotEmpty()) {
                item {
                    RecentCreationsSection(
                        creations = recentCreations,
                        onShareCreation = onShareCreation,
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
private fun ExpressiveTopHeader(
    themePreferences: ThemePreferences,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable { onToggleDynamicColor(!themePreferences.useDynamicColor) }
                    .testTag("toggle_dynamic_color_button"),
                shape = CircleShape,
                color = if (themePreferences.useDynamicColor) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = stringResource(R.string.toggle_dynamic_color),
                        tint = if (themePreferences.useDynamicColor) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (themePreferences.useDynamicColor) "动态取色" else "标准配色",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (themePreferences.useDynamicColor) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            IconButton(
                onClick = { onToggleDarkMode(!themePreferences.useDarkMode) },
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape
                    )
                    .testTag("toggle_dark_mode_button")
            ) {
                Icon(
                    imageVector = if (themePreferences.useDarkMode) {
                        Icons.Filled.LightMode
                    } else {
                        Icons.Filled.DarkMode
                    },
                    contentDescription = stringResource(R.string.toggle_dark_mode),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
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
    onPickLocalVideo: () -> Unit,
    onGenerateDemoVideo: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Primary Card 1: MP4 Video to GIF
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("import_mp4_card"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.card_import_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.card_import_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPickLocalVideo,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pick_mp4_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_pick_video),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    FilledTonalButton(
                        onClick = onGenerateDemoVideo,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("generate_demo_video_button"),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MovieCreation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "载入示例视频",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Primary Card 2: Screen Recording to GIF
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("screen_record_card"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.ScreenShare,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.card_record_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.card_record_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = if (isRecordingActive) onStopLiveRecordAndEdit else onStartScreenRecord,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_screen_record_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecordingActive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (isRecordingActive) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRecordingActive) "结束录制并进入 GIF 剪辑" else stringResource(R.string.btn_start_record),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedFeatureGrid() {
    val specs = listOf(
        "帧率控制" to "5 – 60 FPS",
        "输出分辨率" to "240p – 720p",
        "画面裁剪" to "自由 / 多比例",
        "倍速调节" to "0.5x – 2.0x"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        specs.forEach { (title, value) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesomeMotion,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "最近作品",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

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
                        text = "查看全部 (${creations.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
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
                        .width(204.dp)
                        .testTag("recent_gif_card_${item.id}"),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(128.dp)
                                .clip(RoundedCornerShape(14.dp))
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
                                    style = MaterialTheme.typography.labelSmall,
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
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { onShareCreation(item) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    )
                                    .testTag("recent_share_button_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "一键分享",
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(148.dp)
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
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Text(
                text = "准备开始屏幕捕获…",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "录制开始后可通过 Android 16 实时通知栏随时停止并裁剪 GIF",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
