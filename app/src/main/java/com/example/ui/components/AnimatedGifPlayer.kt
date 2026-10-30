package com.example.ui.components

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.StudioTab
import com.example.ui.theme.ExpressiveSprings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hardware-accelerated Animated GIF Player.
 * Uses Android's native `ImageDecoder` + `AnimatedImageDrawable` (RenderThread accelerated) on API 28+
 * so high-resolution and high-FPS GIFs play at full native speed without frame delay or color shift.
 */
@Composable
fun AnimatedGifPlayer(
    gifFilePath: String,
    thumbnailPath: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true
) {
    val fallbackBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = thumbnailPath
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(thumbnailPath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else null
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center
    ) {
        val gifFile = File(gifFilePath)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && gifFile.exists()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        this.contentDescription = contentDescription
                    }
                },
                update = { imageView ->
                    val currentTag = imageView.tag as? String
                    if (currentTag != gifFilePath) {
                        imageView.tag = gifFilePath
                        runCatching {
                            val source = ImageDecoder.createSource(gifFile)
                            val drawable = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            }
                            imageView.setImageDrawable(drawable)
                            if (drawable is AnimatedImageDrawable && isPlaying) {
                                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                                drawable.start()
                            }
                        }
                    } else {
                        val drawable = imageView.drawable
                        if (drawable is AnimatedImageDrawable) {
                            if (isPlaying && !drawable.isRunning) {
                                drawable.start()
                            } else if (!isPlaying && drawable.isRunning) {
                                drawable.stop()
                            }
                        }
                    }
                }
            )
        } else if (fallbackBitmap != null) {
            Image(
                bitmap = fallbackBitmap!!,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = "GIF",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Material Design 3 Expressive (MD3E) Floating Pill Navigation Bar.
 * Features a detached floating capsule bar with bouncy expanding pill indicators.
 */
@Composable
fun ExpressiveFloatingNavBar(
    currentTab: StudioTab,
    queueCount: Int = 0,
    galleryCount: Int,
    onSelectTab: (StudioTab) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val isCompactNav = maxWidth < 380.dp

        Surface(
            modifier = Modifier.widthIn(max = 520.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveFloatingNavItem(
                    selected = currentTab == StudioTab.HOME,
                    activeIcon = Icons.Filled.Home,
                    inactiveIcon = Icons.Outlined.Home,
                    label = stringResource(R.string.nav_home),
                    badgeCount = 0,
                    isCompact = isCompactNav,
                    onClick = { onSelectTab(StudioTab.HOME) },
                    testTag = "nav_tab_home"
                )

                ExpressiveFloatingNavItem(
                    selected = currentTab == StudioTab.EDITOR,
                    activeIcon = Icons.Filled.Crop,
                    inactiveIcon = Icons.Outlined.Crop,
                    label = stringResource(R.string.nav_editor),
                    badgeCount = 0,
                    isCompact = isCompactNav,
                    onClick = { onSelectTab(StudioTab.EDITOR) },
                    testTag = "nav_tab_editor"
                )

                ExpressiveFloatingNavItem(
                    selected = currentTab == StudioTab.QUEUE,
                    activeIcon = Icons.Filled.QueuePlayNext,
                    inactiveIcon = Icons.Outlined.QueuePlayNext,
                    label = stringResource(R.string.nav_queue),
                    badgeCount = queueCount,
                    isCompact = isCompactNav,
                    onClick = { onSelectTab(StudioTab.QUEUE) },
                    testTag = "nav_tab_queue"
                )

                ExpressiveFloatingNavItem(
                    selected = currentTab == StudioTab.GALLERY,
                    activeIcon = Icons.Filled.AutoAwesomeMotion,
                    inactiveIcon = Icons.Outlined.AutoAwesomeMotion,
                    label = stringResource(R.string.nav_gallery),
                    badgeCount = galleryCount,
                    isCompact = isCompactNav,
                    onClick = { onSelectTab(StudioTab.GALLERY) },
                    testTag = "nav_tab_gallery"
                )
            }
        }
    }
}

/**
 * Canonical Material 3 Expressive Navigation Rail for Expanded / Tablet / Landscape screens.
 */
@Composable
fun ExpressiveNavigationRail(
    currentTab: StudioTab,
    queueCount: Int = 0,
    galleryCount: Int,
    onSelectTab: (StudioTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 12.dp, top = 16.dp, bottom = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExpressiveRailItem(
                selected = currentTab == StudioTab.HOME,
                activeIcon = Icons.Filled.Home,
                inactiveIcon = Icons.Outlined.Home,
                label = stringResource(R.string.nav_home),
                badgeCount = 0,
                onClick = { onSelectTab(StudioTab.HOME) },
                testTag = "nav_tab_home"
            )

            ExpressiveRailItem(
                selected = currentTab == StudioTab.EDITOR,
                activeIcon = Icons.Filled.Crop,
                inactiveIcon = Icons.Outlined.Crop,
                label = stringResource(R.string.nav_editor),
                badgeCount = 0,
                onClick = { onSelectTab(StudioTab.EDITOR) },
                testTag = "nav_tab_editor"
            )

            ExpressiveRailItem(
                selected = currentTab == StudioTab.QUEUE,
                activeIcon = Icons.Filled.QueuePlayNext,
                inactiveIcon = Icons.Outlined.QueuePlayNext,
                label = stringResource(R.string.nav_queue),
                badgeCount = queueCount,
                onClick = { onSelectTab(StudioTab.QUEUE) },
                testTag = "nav_tab_queue"
            )

            ExpressiveRailItem(
                selected = currentTab == StudioTab.GALLERY,
                activeIcon = Icons.Filled.AutoAwesomeMotion,
                inactiveIcon = Icons.Outlined.AutoAwesomeMotion,
                label = stringResource(R.string.nav_gallery),
                badgeCount = galleryCount,
                onClick = { onSelectTab(StudioTab.GALLERY) },
                testTag = "nav_tab_gallery"
            )
        }
    }
}

@Composable
private fun ExpressiveRailItem(
    selected: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    label: String,
    badgeCount: Int,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = ExpressiveSprings.snappyMorph(),
        label = "railItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = ExpressiveSprings.snappyMorph(),
        label = "railItemContent"
    )

    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier
                .width(72.dp)
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = if (selected) activeIcon else inactiveIcon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
                if (badgeCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor
            )
        }
    }
}

@Composable
private fun ExpressiveFloatingNavItem(
    selected: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    label: String,
    badgeCount: Int,
    isCompact: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val horizontalPad by animateDpAsState(
        targetValue = when {
            selected && isCompact -> 12.dp
            selected -> 15.dp
            isCompact -> 9.dp
            else -> 12.dp
        },
        animationSpec = ExpressiveSprings.cornerMorphSpec,
        label = "navItemPad"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1.0f,
        animationSpec = ExpressiveSprings.bouncySpatial(),
        label = "navItemScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = ExpressiveSprings.snappyMorph(),
        label = "navItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveSprings.snappyMorph(),
        label = "navItemContent"
    )

    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = CircleShape,
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPad, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (selected) activeIcon else inactiveIcon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                color = contentColor
            )
            if (badgeCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Unified Material 3 Expressive Shape-Morphing Pill Chip.
 */
@Composable
fun ExpressiveMorphChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onAccentColor: Color = MaterialTheme.colorScheme.onPrimary,
    testTag: String? = null
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 24.dp else 12.dp,
        animationSpec = ExpressiveSprings.cornerMorphSpec,
        label = "chipCornerMorph"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1.0f,
        animationSpec = ExpressiveSprings.bouncySpatial(),
        label = "chipScaleMorph"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = ExpressiveSprings.snappyMorph(),
        label = "chipBgColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) onAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = ExpressiveSprings.snappyMorph(),
        label = "chipTextColor"
    )

    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Surface(
        modifier = taggedModifier
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

/**
 * Clean, Tonal Android 16 (API 36) Live Updates Status Card.
 */
@Composable
fun Android16LiveUpdateCard(
    isRecording: Boolean,
    elapsedSeconds: Int,
    isPromotedSupported: Boolean,
    onStopAndEditClick: () -> Unit,
    onTestLiveNotificationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "liveRecPulse")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    val mins = elapsedSeconds / 60
    val secs = elapsedSeconds % 60
    val formattedTimer = String.format("%02d:%02d", mins, secs)
    val statusChipText = if (isRecording) "REC $formattedTimer" else "LIVE READY"

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("android16_live_update_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (isRecording) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = "Android 16 实时通知",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Android 16 实时通知",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "状态栏胶囊计时与 ProgressStyle 进度",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (isRecording) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .graphicsLayer {
                                    if (isRecording) {
                                        scaleX = dotScale
                                        scaleY = dotScale
                                    }
                                }
                                .background(
                                    color = if (isRecording) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = statusChipText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isRecording) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            val progressFraction = (elapsedSeconds.coerceIn(0, 30) / 30f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
            ) {
                val gap = 6.dp.toPx()
                val segWidth = (size.width - gap * 2f) / 3f
                val h = size.height

                for (i in 0..2) {
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset((segWidth + gap) * i, 0f),
                        size = Size(segWidth, h),
                        cornerRadius = CornerRadius(h / 2f, h / 2f)
                    )
                }

                val activeWidth = if (isRecording) {
                    (size.width * progressFraction).coerceAtLeast(16.dp.toPx())
                } else {
                    segWidth * 0.5f
                }
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(activeWidth.coerceAtMost(size.width), h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRecording) {
                        "录制进行中 · 可在通知栏或此处停止并裁剪"
                    } else {
                        "录制时自动在系统状态栏胶囊展示实时秒数"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                if (isRecording) {
                    Button(
                        onClick = onStopAndEditClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("stop_live_record_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.StopCircle,
                            contentDescription = "停止录制并编辑",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("停止并转 GIF", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onTestLiveNotificationClick,
                        shape = CircleShape,
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("preview_live_notif_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FiberManualRecord,
                            contentDescription = "体验实时通知录屏",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("模拟实时通知", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    return if (bytes >= 1024 * 1024) {
        String.format("%.2f MB", bytes / (1024f * 1024f))
    } else {
        String.format("%d KB", (bytes / 1024L).coerceAtLeast(1L))
    }
}

fun formatDurationMs(ms: Long): String {
    val totalTenths = (ms / 100L).coerceAtLeast(0L)
    val secs = totalTenths / 10
    val tenths = totalTenths % 10
    val mins = secs / 60
    val remSecs = secs % 60
    return String.format("%02d:%02d.%d", mins, remSecs, tenths)
}
