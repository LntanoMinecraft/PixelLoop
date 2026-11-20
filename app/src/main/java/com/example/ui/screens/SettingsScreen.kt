package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.ThemeMode
import com.example.data.ThemePalette
import com.example.data.ThemePreferences
import com.example.ui.theme.ExpressiveSprings

private const val GITHUB_REPO_URL = "https://github.com/LntanoMinecraft/PixelLoop"
private const val COOLAPK_PROFILE_URL = "https://www.coolapk.com/u/34054336"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    themePreferences: ThemePreferences,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectThemePalette: (ThemePalette) -> Unit,
    onShowMessage: (String) -> Unit,
    bottomBarClearance: Dp = 112.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("settings_screen_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = bottomBarClearance + navBarBottom
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Page Header
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Section 1: 外观与偏好
            item {
                SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // 1. Dynamic Color (Material You)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = dynamicSupported) {
                                    onToggleDynamicColor(!themePreferences.useDynamicColor)
                                },
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
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Palette,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = stringResource(R.string.toggle_dynamic_color),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (dynamicSupported) {
                                            stringResource(R.string.toggle_dynamic_color_desc)
                                        } else {
                                            "需 Android 12 及以上系统支持"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Switch(
                                checked = themePreferences.useDynamicColor && dynamicSupported,
                                onCheckedChange = onToggleDynamicColor,
                                enabled = dynamicSupported,
                                modifier = Modifier.testTag("settings_dynamic_color_switch")
                            )
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )

                        // 2. Dark / Light Mode Segmented Selector
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.toggle_dark_mode),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            ThemeModeSegmentedSelector(
                                selectedMode = themePreferences.themeMode,
                                onSelectMode = onSelectThemeMode
                            )
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )

                        // 3. 7-Color Theme Palette Swatch Selector
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_theme_palette),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
                                        text = if (themePreferences.useDynamicColor && dynamicSupported) {
                                            "跟随壁纸"
                                        } else {
                                            themePreferences.themePalette.label
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThemePalette.entries.forEach { palette ->
                                    val isSelected = (!themePreferences.useDynamicColor || !dynamicSupported) &&
                                        themePreferences.themePalette == palette
                                    ThemePaletteSwatch(
                                        palette = palette,
                                        selected = isSelected,
                                        onClick = { onSelectThemePalette(palette) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: 关于 PixelLoop
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsSectionHeader(title = stringResource(R.string.settings_section_about))
            }

            // Brand Header Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("about_brand_card"),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = Color.White,
                            shadowElevation = 3.dp
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_brand_logo),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.about_tagline),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Open-Source & Developer Links Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AboutLinkRow(
                            icon = Icons.Filled.Code,
                            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.about_github_title),
                            subtitle = stringResource(R.string.about_github_subtitle),
                            onClick = {
                                openExternalUrl(
                                    context = context,
                                    url = GITHUB_REPO_URL,
                                    onError = { onShowMessage("无法打开链接，请检查浏览器设置") }
                                )
                            },
                            testTag = "about_github_link"
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )

                        AboutLinkRow(
                            icon = Icons.Filled.Person,
                            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.about_coolapk_title),
                            subtitle = stringResource(R.string.about_coolapk_subtitle),
                            onClick = {
                                openExternalUrl(
                                    context = context,
                                    url = COOLAPK_PROFILE_URL,
                                    onError = { onShowMessage("无法打开酷安主页，请检查网络或浏览器") }
                                )
                            },
                            testTag = "about_coolapk_link"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun ThemeModeSegmentedSelector(
    selectedMode: ThemeMode,
    onSelectMode: (ThemeMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemeMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                val bgColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = ExpressiveSprings.navBarSpring(),
                    label = "themeModeBg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = ExpressiveSprings.navBarSpring(),
                    label = "themeModeContent"
                )
                val icon = when (mode) {
                    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                    ThemeMode.DARK -> Icons.Filled.DarkMode
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .height(44.dp)
                        .clip(CircleShape)
                        .clickable { onSelectMode(mode) }
                        .testTag("settings_theme_mode_${mode.name.lowercase()}"),
                    shape = CircleShape,
                    color = bgColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = mode.label,
                            tint = contentColor,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePaletteSwatch(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit
) {
    val swatchColor = Color(palette.seedColorHex)
    val ringColor by animateColorAsState(
        targetValue = if (selected) swatchColor else Color.Transparent,
        animationSpec = ExpressiveSprings.navBarSpring(),
        label = "swatchRingColor"
    )
    val swatchScale by animateFloatAsState(
        targetValue = if (selected) 0.92f else 1.0f,
        animationSpec = ExpressiveSprings.navBarSpring(),
        label = "swatchInnerScale"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(width = 2.5.dp, color = ringColor, shape = CircleShape)
            .padding(5.dp)
            .testTag("palette_swatch_${palette.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = swatchScale
                    scaleY = swatchScale
                }
                .clip(CircleShape)
                .background(swatchColor),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(animationSpec = ExpressiveSprings.navBarSpring()) + fadeIn(),
                exit = scaleOut(animationSpec = ExpressiveSprings.navBarSpring()) + fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "${palette.label} 已选中",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = iconContainerColor,
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Opens an external URL via system default handler (`Intent.ACTION_VIEW`), which automatically
 * invokes installed App Links handlers (e.g. CoolApk App) and falls back to the system browser.
 */
private fun openExternalUrl(
    context: Context,
    url: String,
    onError: () -> Unit
) {
    val uri = Uri.parse(url)
    val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(viewIntent)
    }.onFailure {
        runCatching {
            val chooser = Intent.createChooser(viewIntent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }.onFailure {
            onError()
        }
    }
}
