package com.spotyflow.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotyflow.android.data.local.AppThemeMode
import com.spotyflow.android.data.local.UiStyleMode

object SpotyColors {
    val PureWhiteCanvas = Color(0xFFFAFCFF)
    val DarkCanvas = Color(0xFF0D0E12)
    val AmoledCanvas = Color(0xFF000000)

    val Slate900 = Color(0xFF0F172A)
    val Slate700 = Color(0xFF334155)
    val Slate500 = Color(0xFF64748B)
    val Slate300 = Color(0xFFCBD5E1)
    val HairlineDivider = Color(0x160F172A)

    val EmeraldCrystal = Color(0xFF10B981)
    val DeepEmerald = Color(0xFF059669)
    val EmeraldTint = Color(0x1E10B981)

    val HeartRed = Color(0xFFEF4444)
    val FlameAmber = Color(0xFFF59E0B)
    val FlameOrange = Color(0xFFF97316)

    val DefaultMintMist = Color(0x40A7F3D0)
    val DefaultSkyCrystal = Color(0x45BAE6FD)
    val DefaultSoftLilac = Color(0x38E9D5FF)
}

data class SpotyAppearance(
    val isDark: Boolean = false,
    val isAmoled: Boolean = false,
    val canvasBackground: Color = SpotyColors.PureWhiteCanvas,
    val textPrimary: Color = SpotyColors.Slate900,
    val textSecondary: Color = SpotyColors.Slate500,
    val accent: Color = SpotyColors.EmeraldCrystal,
    val uiStyle: UiStyleMode = UiStyleMode.GLASS,
    val glassBlurEnabled: Boolean = true,
    val glassLensEnabled: Boolean = true
)

val LocalSpotyAppearance = compositionLocalOf { SpotyAppearance() }

object SpotySpacing {
    val ScreenHorizontal = 20.dp
    val SectionGap = 24.dp
    val CardGap = 12.dp
    val CardPadding = 16.dp
    val BottomScrollClearance = 208.dp
}

private fun spotyLightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = SpotyColors.DeepEmerald,
    onSecondary = Color.White,
    background = SpotyColors.PureWhiteCanvas,
    onBackground = SpotyColors.Slate900,
    surface = Color.White,
    onSurface = SpotyColors.Slate900,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = SpotyColors.Slate500,
    outline = Color(0x1F0F172A)
)

private fun spotyDarkScheme(accent: Color, amoled: Boolean) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = SpotyColors.EmeraldCrystal,
    onSecondary = Color.White,
    background = if (amoled) SpotyColors.AmoledCanvas else SpotyColors.DarkCanvas,
    onBackground = Color(0xFFF8FAFC),
    surface = if (amoled) Color(0xFF0A0B0F) else Color(0xFF161922),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E222E),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0x28FFFFFF)
)

private fun spotyTypography(ink: Color, sub: Color) = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
        color = ink
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
        color = ink
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
        color = ink
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = ink
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = sub
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
        color = sub
    )
)

@Composable
fun SpotyTheme(
    themeMode: AppThemeMode = AppThemeMode.PURE_WHITE,
    uiStyle: UiStyleMode = UiStyleMode.GLASS,
    glassBlurEnabled: Boolean = true,
    glassLensEnabled: Boolean = true,
    accentHex: Long = 0xFF10B981L,
    content: @Composable () -> Unit
) {
    val sysDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.PURE_WHITE -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
        AppThemeMode.SYSTEM -> sysDark
    }
    val isAmoled = themeMode == AppThemeMode.AMOLED
    val canvasBg = when {
        isAmoled -> SpotyColors.AmoledCanvas
        isDark -> SpotyColors.DarkCanvas
        else -> SpotyColors.PureWhiteCanvas
    }
    val accent = Color(accentHex)
    val ink = if (isDark) Color(0xFFF8FAFC) else SpotyColors.Slate900
    val sub = if (isDark) Color(0xFF94A3B8) else SpotyColors.Slate500

    val appearance = SpotyAppearance(
        isDark = isDark,
        isAmoled = isAmoled,
        canvasBackground = canvasBg,
        textPrimary = ink,
        textSecondary = sub,
        accent = accent,
        uiStyle = uiStyle,
        glassBlurEnabled = glassBlurEnabled,
        glassLensEnabled = glassLensEnabled
    )

    CompositionLocalProvider(LocalSpotyAppearance provides appearance) {
        MaterialTheme(
            colorScheme = if (isDark) spotyDarkScheme(accent, isAmoled) else spotyLightScheme(accent),
            typography = spotyTypography(ink, sub),
            content = content
        )
    }
}
