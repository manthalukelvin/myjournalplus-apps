package com.celmatech.myjournalplus.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Shared high-contrast colors. Values update when the user picks an Appearance theme.
 */
object AppColors {
    var Primary by mutableStateOf(Color(0xFF7B6CFF))
        private set
    var PrimaryDark by mutableStateOf(Color(0xFF5A4FD4))
        private set
    var ScreenBg by mutableStateOf(Color(0xFFF5F3FF))
        private set
    var CardBg by mutableStateOf(Color(0xFFFFFFFF))
        private set
    var FieldBg by mutableStateOf(Color(0xFFF8F7FC))
        private set
    var TextPrimary by mutableStateOf(Color(0xFF1A1523))
        private set
    var TextSecondary by mutableStateOf(Color(0xFF4B5563))
        private set
    var TextMuted by mutableStateOf(Color(0xFF6B7280))
        private set
    var TextOnPrimary by mutableStateOf(Color(0xFFFFFFFF))
        private set
    var TextOnPrimaryMuted by mutableStateOf(Color(0xE6FFFFFF))
        private set
    var Border by mutableStateOf(Color(0xFFD1CBE0))
        private set
    val Success = Color(0xFF059669)
    val Error = Color(0xFFEF4444)
    val PremiumGold = Color(0xFFF59E0B)

    fun applyPalette(p: ThemePalette) {
        Primary = p.primary
        PrimaryDark = p.primaryDark
        ScreenBg = p.screenBg
        CardBg = p.cardBg
        FieldBg = p.fieldBg
        TextPrimary = p.textPrimary
        TextSecondary = p.textSecondary
        TextMuted = p.textMuted
        Border = p.border
        TextOnPrimary = Color.White
        TextOnPrimaryMuted = Color.White.copy(alpha = 0.9f)
    }
}
