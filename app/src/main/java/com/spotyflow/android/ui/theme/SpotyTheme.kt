package com.spotyflow.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SpotyColors {
    val PureWhiteCanvas = Color(0xFFFAFCFF)
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

object SpotySpacing {
    val ScreenHorizontal = 20.dp
    val SectionGap = 24.dp
    val CardGap = 12.dp
    val CardPadding = 16.dp
    val BottomScrollClearance = 208.dp
}

private val SpotyLightColorScheme = lightColorScheme(
    primary = SpotyColors.EmeraldCrystal,
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

private val SpotyTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
        color = SpotyColors.Slate900
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
        color = SpotyColors.Slate900
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
        color = SpotyColors.Slate900
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = SpotyColors.Slate900
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = SpotyColors.Slate500
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
        color = SpotyColors.Slate500
    )
)

@Composable
fun SpotyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpotyLightColorScheme,
        typography = SpotyTypography,
        content = content
    )
}
