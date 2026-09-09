package com.celmatech.myjournalplus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF7B6CFF)
val PrimaryDark = Color(0xFF5A4FD4)
val Accent = Color(0xFFA78BFA)
val BackgroundLight = Color(0xFFF5F3FF)
val SurfaceLight = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1F1B2E)
val TextMuted = Color(0xFF6B7280)
val PremiumGold = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)

data class ThemePalette(
    val id: String,
    val primary: Color,
    val primaryDark: Color,
    val screenBg: Color,
    val cardBg: Color,
    val fieldBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val isDark: Boolean = false
)

val ThemePalettes = mapOf(
    "default" to ThemePalette(
        id = "default",
        primary = Color(0xFF7B6CFF),
        primaryDark = Color(0xFF5A4FD4),
        screenBg = Color(0xFFF5F3FF),
        cardBg = Color(0xFFFFFFFF),
        fieldBg = Color(0xFFF8F7FC),
        textPrimary = Color(0xFF1A1523),
        textSecondary = Color(0xFF4B5563),
        textMuted = Color(0xFF6B7280),
        border = Color(0xFFD1CBE0)
    ),
    "midnight" to ThemePalette(
        id = "midnight",
        primary = Color(0xFF8B7CFF),
        primaryDark = Color(0xFF5A4FD4),
        screenBg = Color(0xFF0F0D1A),
        cardBg = Color(0xFF1A1628),
        fieldBg = Color(0xFF221C36),
        textPrimary = Color(0xFFF5F3FF),
        textSecondary = Color(0xFFB8B0D0),
        textMuted = Color(0xFF8A829E),
        border = Color(0xFF3A3358),
        isDark = true
    ),
    "ocean" to ThemePalette(
        id = "ocean",
        primary = Color(0xFF0EA5E9),
        primaryDark = Color(0xFF0284C7),
        screenBg = Color(0xFFECFEFF),
        cardBg = Color(0xFFFFFFFF),
        fieldBg = Color(0xFFF0F9FF),
        textPrimary = Color(0xFF0C4A6E),
        textSecondary = Color(0xFF0369A1),
        textMuted = Color(0xFF64748B),
        border = Color(0xFFBAE6FD)
    ),
    "forest" to ThemePalette(
        id = "forest",
        primary = Color(0xFF10B981),
        primaryDark = Color(0xFF059669),
        screenBg = Color(0xFFECFDF5),
        cardBg = Color(0xFFFFFFFF),
        fieldBg = Color(0xFFF0FDF4),
        textPrimary = Color(0xFF064E3B),
        textSecondary = Color(0xFF047857),
        textMuted = Color(0xFF6B7280),
        border = Color(0xFFA7F3D0)
    ),
    "rose" to ThemePalette(
        id = "rose",
        primary = Color(0xFFEC4899),
        primaryDark = Color(0xFFDB2777),
        screenBg = Color(0xFFFDF2F8),
        cardBg = Color(0xFFFFFFFF),
        fieldBg = Color(0xFFFCE7F3),
        textPrimary = Color(0xFF831843),
        textSecondary = Color(0xFF9D174D),
        textMuted = Color(0xFF9CA3AF),
        border = Color(0xFFFBCFE8)
    ),
    "sunset" to ThemePalette(
        id = "sunset",
        primary = Color(0xFFF97316),
        primaryDark = Color(0xFFEA580C),
        screenBg = Color(0xFFFFF7ED),
        cardBg = Color(0xFFFFFFFF),
        fieldBg = Color(0xFFFFF7ED),
        textPrimary = Color(0xFF7C2D12),
        textSecondary = Color(0xFFC2410C),
        textMuted = Color(0xFF9CA3AF),
        border = Color(0xFFFED7AA)
    ),
    "slate" to ThemePalette(
        id = "slate",
        primary = Color(0xFF64748B),
        primaryDark = Color(0xFF475569),
        screenBg = Color(0xFFF8FAFC),
        cardBg = Color(0xFFFFFFFF),
        fieldBg = Color(0xFFF1F5F9),
        textPrimary = Color(0xFF0F172A),
        textSecondary = Color(0xFF475569),
        textMuted = Color(0xFF94A3B8),
        border = Color(0xFFCBD5E1)
    )
)

val LocalThemePalette = staticCompositionLocalOf { ThemePalettes["default"]!! }

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    secondary = Accent,
    background = BackgroundLight,
    surface = SurfaceLight,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorRed
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B2F8A),
    secondary = Accent,
    background = Color(0xFF12101A),
    surface = Color(0xFF1E1A2E),
    onBackground = Color(0xFFF5F3FF),
    onSurface = Color(0xFFF5F3FF),
    error = ErrorRed
)

@Composable
fun MyJournalPlusTheme(
    themeId: String = "default",
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = ThemePalettes[themeId] ?: ThemePalettes["default"]!!
    val useDark = palette.isDark || (themeId == "default" && darkTheme)
    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primaryDark,
            secondary = palette.primary,
            background = palette.screenBg,
            surface = palette.cardBg,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            error = ErrorRed
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primary.copy(alpha = 0.15f),
            secondary = palette.primary,
            background = palette.screenBg,
            surface = palette.cardBg,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            error = ErrorRed
        )
    }
    CompositionLocalProvider(LocalThemePalette provides palette) {
        // Keep AppColors in sync for screens that read the object
        AppColors.applyPalette(palette)
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
