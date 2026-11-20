package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.StudioTab
import com.example.ui.theme.ExpressiveSprings

private data class NavDestinationSpec(
    val tab: StudioTab,
    val labelRes: Int,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
    val testTag: String
)

private val studioNavDestinations = listOf(
    NavDestinationSpec(
        tab = StudioTab.HOME,
        labelRes = R.string.nav_home,
        activeIcon = Icons.Filled.Home,
        inactiveIcon = Icons.Outlined.Home,
        testTag = "nav_tab_home"
    ),
    NavDestinationSpec(
        tab = StudioTab.EDITOR,
        labelRes = R.string.nav_editor,
        activeIcon = Icons.Filled.Crop,
        inactiveIcon = Icons.Outlined.Crop,
        testTag = "nav_tab_editor"
    ),
    NavDestinationSpec(
        tab = StudioTab.QUEUE,
        labelRes = R.string.nav_queue,
        activeIcon = Icons.Filled.QueuePlayNext,
        inactiveIcon = Icons.Outlined.QueuePlayNext,
        testTag = "nav_tab_queue"
    ),
    NavDestinationSpec(
        tab = StudioTab.GALLERY,
        labelRes = R.string.nav_gallery,
        activeIcon = Icons.Filled.AutoAwesomeMotion,
        inactiveIcon = Icons.Outlined.AutoAwesomeMotion,
        testTag = "nav_tab_gallery"
    ),
    NavDestinationSpec(
        tab = StudioTab.SETTINGS,
        labelRes = R.string.nav_settings,
        activeIcon = Icons.Filled.Settings,
        inactiveIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
)

/**
 * Material Design 3 Expressive Floating Capsule Navigation Bar.
 *
 * - Floats 12dp above the system gesture bar (`navigationBarsPadding()`).
 * - Unselected items display only their icon; selecting an item elastically expands it
 *   into a `secondaryContainer` pill indicator with "icon + label" using M3 Expressive
 *   spring physics (`dampingRatio = 0.62f, stiffness = 380f`).
 */
@Composable
fun ExpressiveFloatingNavBar(
    currentTab: StudioTab,
    queueCount: Int = 0,
    galleryCount: Int = 0,
    onSelectTab: (StudioTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .animateContentSize(animationSpec = ExpressiveSprings.navBarSpring())
                .testTag("floating_nav_bar"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 7.dp)
                    .animateContentSize(animationSpec = ExpressiveSprings.navBarSpring()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                studioNavDestinations.forEach { dest ->
                    val badgeCount = when (dest.tab) {
                        StudioTab.QUEUE -> queueCount
                        else -> 0
                    }
                    ExpressiveFloatingNavItem(
                        selected = currentTab == dest.tab,
                        activeIcon = dest.activeIcon,
                        inactiveIcon = dest.inactiveIcon,
                        label = stringResource(dest.labelRes),
                        badgeCount = badgeCount,
                        onClick = { onSelectTab(dest.tab) },
                        testTag = dest.testTag
                    )
                }
            }
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
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = ExpressiveSprings.navBarSpring(),
        label = "floatingNavItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveSprings.navBarSpring(),
        label = "floatingNavItemContent"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1.0f,
        animationSpec = ExpressiveSprings.navBarSpring(),
        label = "floatingNavIconScale"
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (selected) 16.dp else 12.dp,
        animationSpec = ExpressiveSprings.navBarSpring(),
        label = "floatingNavHorizontalPad"
    )

    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = CircleShape,
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .animateContentSize(animationSpec = ExpressiveSprings.navBarSpring()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = if (selected) activeIcon else inactiveIcon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
                if (!selected && badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = 4.dp, y = (-3).dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }

            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(
                    animationSpec = ExpressiveSprings.navBarSpring(),
                    expandFrom = Alignment.Start
                ) + fadeIn(animationSpec = ExpressiveSprings.navBarSpring()),
                exit = shrinkHorizontally(
                    animationSpec = ExpressiveSprings.navBarSpring(),
                    shrinkTowards = Alignment.Start
                ) + fadeOut(animationSpec = ExpressiveSprings.navBarSpring())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = contentColor
                    )
                    if (badgeCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = badgeCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
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
    galleryCount: Int = 0,
    onSelectTab: (StudioTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .navigationBarsPadding()
            .padding(start = 12.dp, top = 16.dp, bottom = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            studioNavDestinations.forEach { dest ->
                val badgeCount = when (dest.tab) {
                    StudioTab.QUEUE -> queueCount
                    StudioTab.GALLERY -> galleryCount
                    else -> 0
                }
                ExpressiveRailItem(
                    selected = currentTab == dest.tab,
                    activeIcon = dest.activeIcon,
                    inactiveIcon = dest.inactiveIcon,
                    label = stringResource(dest.labelRes),
                    badgeCount = badgeCount,
                    onClick = { onSelectTab(dest.tab) },
                    testTag = dest.testTag
                )
            }
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
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = ExpressiveSprings.navBarSpring(),
        label = "railItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = ExpressiveSprings.navBarSpring(),
        label = "railItemContent"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1.0f,
        animationSpec = ExpressiveSprings.navBarSpring(),
        label = "railIconScale"
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
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
                if (badgeCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor
            )
        }
    }
}

/**
 * Unified Material 3 Expressive Shape-Morphing Pill Chip (borderless tonal hierarchy).
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
        targetValue = if (selected) 24.dp else 14.dp,
        animationSpec = ExpressiveSprings.navBarSpring(),
        label = "chipCornerMorph"
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
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFeatureSettings = "tnum"
                ),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                color = contentColor
            )
        }
    }
}
