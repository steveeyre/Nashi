package com.nashi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 苔绿 + 暖纸: 与启动图标同一色感, 低饱和底色把注意力留给物品照片与库存/临期状态。
private val LightColors = lightColorScheme(
    primary = Color(0xFF3F6152),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E7DD),
    onPrimaryContainer = Color(0xFF10231A),
    secondary = Color(0xFF7C6A4E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE3D2),
    onSecondaryContainer = Color(0xFF2A2113),
    tertiary = Color(0xFFA2583C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF7DCCF),
    onTertiaryContainer = Color(0xFF3A1409),
    background = Color(0xFFFAF7F1),
    onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF1B1C19),
    surfaceVariant = Color(0xFFECE6DA),
    onSurfaceVariant = Color(0xFF4A4A42),
    surfaceTint = Color(0xFF3F6152),
    inverseSurface = Color(0xFF30312C),
    inverseOnSurface = Color(0xFFF2F1EA),
    inversePrimary = Color(0xFFA7CDB9),
    outline = Color(0xFF7B7A70),
    outlineVariant = Color(0xFFD5CFC2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7CDB9),
    onPrimary = Color(0xFF113326),
    primaryContainer = Color(0xFF263F33),
    onPrimaryContainer = Color(0xFFC3E9D4),
    secondary = Color(0xFFDCC7A6),
    onSecondary = Color(0xFF2B2416),
    secondaryContainer = Color(0xFF453B29),
    onSecondaryContainer = Color(0xFFEFE0C8),
    tertiary = Color(0xFFE6B49C),
    onTertiary = Color(0xFF40220F),
    tertiaryContainer = Color(0xFF5C3524),
    onTertiaryContainer = Color(0xFFF7DCCF),
    background = Color(0xFF121512),
    onBackground = Color(0xFFE3E4DE),
    surface = Color(0xFF161B17),
    onSurface = Color(0xFFE3E4DE),
    surfaceVariant = Color(0xFF3F4841),
    onSurfaceVariant = Color(0xFFBFC9C1),
    surfaceTint = Color(0xFFA7CDB9),
    inverseSurface = Color(0xFFE3E4DE),
    inverseOnSurface = Color(0xFF30312C),
    inversePrimary = Color(0xFF3F6152),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF3F4841),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val NashiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val NashiTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
)

/** 物品首字/统计色块共用的低饱和点缀色。 */
data class Accent(val container: Color, val content: Color)

private val Accents = listOf(
    Accent(Color(0xFFD6E7DD), Color(0xFF1D3B2E)),
    Accent(Color(0xFFF7DCCF), Color(0xFF5A2413)),
    Accent(Color(0xFFEFE3D2), Color(0xFF4A3A1D)),
    Accent(Color(0xFFDCE6F0), Color(0xFF23384D)),
    Accent(Color(0xFFEBDDE7), Color(0xFF4A2B40)),
)

fun accentAt(seed: Int): Accent = Accents[Math.floorMod(seed, Accents.size)]

@Composable
fun NashiTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = NashiShapes,
        typography = NashiTypography,
        content = content,
    )
}
