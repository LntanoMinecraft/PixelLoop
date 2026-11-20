package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.example.data.ThemePalette

// ---------------------------------------------------------------------------
// 1. Cobalt (Google Blue — Default M3 Expressive Tonal Palette)
// ---------------------------------------------------------------------------
val CobaltDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF1B4485),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3A4459),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFD0BCFF),
    onTertiary = Color(0xFF33205D),
    tertiaryContainer = Color(0xFF4A3775),
    onTertiaryContainer = Color(0xFFEADDFF),
    background = Color(0xFF101218),
    onBackground = Color(0xFFE3E5EE),
    surface = Color(0xFF101218),
    onSurface = Color(0xFFE3E5EE),
    surfaceVariant = Color(0xFF434753),
    onSurfaceVariant = Color(0xFFC2C6D4),
    outline = Color(0xFF8C909F),
    outlineVariant = Color(0xFF434753),
    surfaceContainerLowest = Color(0xFF0B0D12),
    surfaceContainerLow = Color(0xFF171A23),
    surfaceContainer = Color(0xFF1C202B),
    surfaceContainerHigh = Color(0xFF252A37),
    surfaceContainerHighest = Color(0xFF303645)
)

val CobaltLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1A56B8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B47),
    secondary = Color(0xFF555E72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E2F9),
    onSecondaryContainer = Color(0xFF131B2C),
    tertiary = Color(0xFF634B93),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEADDFF),
    onTertiaryContainer = Color(0xFF200A4B),
    background = Color(0xFFF9FAFF),
    onBackground = Color(0xFF181C24),
    surface = Color(0xFFF9FAFF),
    onSurface = Color(0xFF181C24),
    surfaceVariant = Color(0xFFE0E3EE),
    onSurfaceVariant = Color(0xFF444854),
    outline = Color(0xFF747885),
    outlineVariant = Color(0xFFC4C7D4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4FC),
    surfaceContainer = Color(0xFFEAEFF8),
    surfaceContainerHigh = Color(0xFFE3E8F2),
    surfaceContainerHighest = Color(0xFFDCE1EC)
)

// ---------------------------------------------------------------------------
// 2. Teal (Airy Cyan-Teal)
// ---------------------------------------------------------------------------
val TealDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF7CD7CB),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFA0F2E6),
    secondary = Color(0xFFB1CCC7),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF324B47),
    onSecondaryContainer = Color(0xFFCDE8E3),
    tertiary = Color(0xFFB0C9E6),
    onTertiary = Color(0xFF19324A),
    tertiaryContainer = Color(0xFF304962),
    onTertiaryContainer = Color(0xFFCFE5FF),
    background = Color(0xFF0F1413),
    onBackground = Color(0xFFDFE4E2),
    surface = Color(0xFF0F1413),
    onSurface = Color(0xFFDFE4E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF161C1B),
    surfaceContainer = Color(0xFF1B2221),
    surfaceContainerHigh = Color(0xFF252D2C),
    surfaceContainerHighest = Color(0xFF2F3836)
)

val TealLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC0F2EA),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF45617C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCBE6FF),
    onTertiaryContainer = Color(0xFF001E34),
    background = Color(0xFFF5FBF9),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF5FBF9),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E2),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F3),
    surfaceContainer = Color(0xFFE9F0ED),
    surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDDE4E1)
)

// ---------------------------------------------------------------------------
// 3. Violet (Twilight Lavender)
// ---------------------------------------------------------------------------
val VioletDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFCBB9FF),
    onPrimary = Color(0xFF331B75),
    primaryContainer = Color(0xFF4A338D),
    onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Color(0xFFCAC3DC),
    onSecondary = Color(0xFF322E41),
    secondaryContainer = Color(0xFF484459),
    onSecondaryContainer = Color(0xFFE6DFF9),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF4A2532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF131218),
    onBackground = Color(0xFFE5E1EC),
    surface = Color(0xFF131218),
    onSurface = Color(0xFFE5E1EC),
    surfaceVariant = Color(0xFF48454F),
    onSurfaceVariant = Color(0xFFC9C4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF48454F),
    surfaceContainerLowest = Color(0xFF0E0D13),
    surfaceContainerLow = Color(0xFF1B1921),
    surfaceContainer = Color(0xFF201E27),
    surfaceContainerHigh = Color(0xFF2B2832),
    surfaceContainerHighest = Color(0xFF36333E)
)

val VioletLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF6145B5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF1D0060),
    secondary = Color(0xFF605B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6DFF9),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFDF8FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFDF8FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE6E0EC),
    onSurfaceVariant = Color(0xFF48454F),
    outline = Color(0xFF797580),
    outlineVariant = Color(0xFFC9C4D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F1FB),
    surfaceContainer = Color(0xFFF0EBF5),
    surfaceContainerHigh = Color(0xFFEAE5F0),
    surfaceContainerHighest = Color(0xFFE4DFEA)
)

// ---------------------------------------------------------------------------
// 4. Coral (Warm Terracotta Coral)
// ---------------------------------------------------------------------------
val CoralDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB3AC),
    onPrimary = Color(0xFF601312),
    primaryContainer = Color(0xFF7E2A27),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB8),
    onSecondary = Color(0xFF442927),
    secondaryContainer = Color(0xFF5D3F3C),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFE0C38C),
    onTertiary = Color(0xFF3F2E04),
    tertiaryContainer = Color(0xFF584419),
    onTertiaryContainer = Color(0xFFFDDFA6),
    background = Color(0xFF171212),
    onBackground = Color(0xFFECE0DF),
    surface = Color(0xFF171212),
    onSurface = Color(0xFFECE0DF),
    surfaceVariant = Color(0xFF534342),
    onSurfaceVariant = Color(0xFFD8C2BF),
    outline = Color(0xFFA08C8A),
    outlineVariant = Color(0xFF534342),
    surfaceContainerLowest = Color(0xFF120D0D),
    surfaceContainerLow = Color(0xFF201A19),
    surfaceContainer = Color(0xFF251E1E),
    surfaceContainerHigh = Color(0xFF302827),
    surfaceContainerHighest = Color(0xFF3B3332)
)

val CoralLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFB43B37),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775653),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF2C1513),
    tertiary = Color(0xFF725B2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFDDFA6),
    onTertiaryContainer = Color(0xFF261900),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF221918),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF221918),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534342),
    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFD8C2BF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0EE),
    surfaceContainer = Color(0xFFFBEAE8),
    surfaceContainerHigh = Color(0xFFF5E4E2),
    surfaceContainerHighest = Color(0xFFEFDEDC)
)

// ---------------------------------------------------------------------------
// 5. Amber (Warm Gold Amber)
// ---------------------------------------------------------------------------
val AmberDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFF5BE6B),
    onPrimary = Color(0xFF452B00),
    primaryContainer = Color(0xFF633F00),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFDCC3A1),
    onSecondary = Color(0xFF3D2E16),
    secondaryContainer = Color(0xFF55442A),
    onSecondaryContainer = Color(0xFFF9DFBB),
    tertiary = Color(0xFFB6CE9E),
    onTertiary = Color(0xFF233513),
    tertiaryContainer = Color(0xFF394C27),
    onTertiaryContainer = Color(0xFFD2EAB9),
    background = Color(0xFF16130E),
    onBackground = Color(0xFFEAE1D6),
    surface = Color(0xFF16130E),
    onSurface = Color(0xFFEAE1D6),
    surfaceVariant = Color(0xFF4E4539),
    onSurfaceVariant = Color(0xFFD2C5B4),
    outline = Color(0xFF9B8F80),
    outlineVariant = Color(0xFF4E4539),
    surfaceContainerLowest = Color(0xFF110E0A),
    surfaceContainerLow = Color(0xFF1F1B15),
    surfaceContainer = Color(0xFF241F19),
    surfaceContainerHigh = Color(0xFF2F2A23),
    surfaceContainerHighest = Color(0xFF3A342D)
)

val AmberLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF8A5500),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB0),
    onPrimaryContainer = Color(0xFF2B1700),
    secondary = Color(0xFF6F5B40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF9DFBB),
    onSecondaryContainer = Color(0xFF261904),
    tertiary = Color(0xFF50643D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD2EAB9),
    onTertiaryContainer = Color(0xFF0F2002),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF201B14),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF201B14),
    surfaceVariant = Color(0xFFEEE0CF),
    onSurfaceVariant = Color(0xFF4E4539),
    outline = Color(0xFF807567),
    outlineVariant = Color(0xFFD2C5B4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF2E8),
    surfaceContainer = Color(0xFFF5ECE1),
    surfaceContainerHigh = Color(0xFFEFE6DC),
    surfaceContainerHighest = Color(0xFFE9E0D6)
)

// ---------------------------------------------------------------------------
// 6. Emerald (Forest Sage Green)
// ---------------------------------------------------------------------------
val EmeraldDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF89D6AE),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF085235),
    onPrimaryContainer = Color(0xFFA5F2C9),
    secondary = Color(0xFFB5CCBC),
    onSecondary = Color(0xFF213529),
    secondaryContainer = Color(0xFF374B3F),
    onSecondaryContainer = Color(0xFFD1E8D8),
    tertiary = Color(0xFFA5CCE0),
    onTertiary = Color(0xFF083545),
    tertiaryContainer = Color(0xFF244C5C),
    onTertiaryContainer = Color(0xFFC1E8FC),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDFE4DF),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDFE4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B221D),
    surfaceContainerHigh = Color(0xFF262D28),
    surfaceContainerHighest = Color(0xFF303832)
)

val EmeraldLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1E6B4B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4F0D0),
    onPrimaryContainer = Color(0xFF002113),
    secondary = Color(0xFF4D6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D8),
    onSecondaryContainer = Color(0xFF0B1F15),
    tertiary = Color(0xFF3D6374),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E8FC),
    onTertiaryContainer = Color(0xFF001F2A),
    background = Color(0xFFF6FBF6),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FBF6),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F0),
    surfaceContainer = Color(0xFFEAEFEA),
    surfaceContainerHigh = Color(0xFFE4EAE5),
    surfaceContainerHighest = Color(0xFFDFE4DF)
)

// ---------------------------------------------------------------------------
// 7. Rose (Blossom Berry)
// ---------------------------------------------------------------------------
val RoseDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB0C9),
    onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7B294A),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFE2BDC6),
    onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5A3F47),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFEFBD94),
    onTertiary = Color(0xFF48290C),
    tertiaryContainer = Color(0xFF623F20),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF171114),
    onBackground = Color(0xFFECDFE2),
    surface = Color(0xFF171114),
    onSurface = Color(0xFFECDFE2),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    outline = Color(0xFF9E8C90),
    outlineVariant = Color(0xFF514347),
    surfaceContainerLowest = Color(0xFF120C0F),
    surfaceContainerLow = Color(0xFF20191C),
    surfaceContainer = Color(0xFF251E20),
    surfaceContainerHigh = Color(0xFF30282B),
    surfaceContainerHighest = Color(0xFF3B3235)
)

val RoseLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF9D3D62),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E3),
    onPrimaryContainer = Color(0xFF3E001E),
    secondary = Color(0xFF74565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF7D5635),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF2DDE2),
    onSurfaceVariant = Color(0xFF514347),
    outline = Color(0xFF837377),
    outlineVariant = Color(0xFFD5C2C6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF0F2),
    surfaceContainer = Color(0xFFF6EAEC),
    surfaceContainerHigh = Color(0xFFF1E4E7),
    surfaceContainerHighest = Color(0xFFEBDDE0)
)

fun colorSchemeForPalette(palette: ThemePalette, darkTheme: Boolean): ColorScheme {
    return when (palette) {
        ThemePalette.COBALT -> if (darkTheme) CobaltDarkScheme else CobaltLightScheme
        ThemePalette.TEAL -> if (darkTheme) TealDarkScheme else TealLightScheme
        ThemePalette.VIOLET -> if (darkTheme) VioletDarkScheme else VioletLightScheme
        ThemePalette.CORAL -> if (darkTheme) CoralDarkScheme else CoralLightScheme
        ThemePalette.AMBER -> if (darkTheme) AmberDarkScheme else AmberLightScheme
        ThemePalette.EMERALD -> if (darkTheme) EmeraldDarkScheme else EmeraldLightScheme
        ThemePalette.ROSE -> if (darkTheme) RoseDarkScheme else RoseLightScheme
    }
}
