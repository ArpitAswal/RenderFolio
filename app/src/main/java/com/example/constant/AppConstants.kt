package com.example.constant

import androidx.compose.ui.graphics.Color

object AppConstants {
    const val MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L
    const val TIER_1_CHAR_THRESHOLD = 120

    val DarkBackground = Color(0xFF121418)
    val DarkSurface = Color(0xFF1C2026)
    val DarkPrimary = Color(0xFF3B82F6)
    val DarkTextPrimary = Color(0xFFF3F4F6)
    val DarkTextMuted = Color(0xFF9CA3AF)
    val DarkBorder = Color(0x14FFFFFF)

    val LightBackground = Color(0xFFF4F5F7)
    val LightSurface = Color(0xFFFFFFFF)
    val LightPrimary = Color(0xFF2563EB)
    val LightTextPrimary = Color(0xFF111827)
    val LightTextMuted = Color(0xFF6B7280)
    val LightBorder = Color(0x14000000)

    const val TAG_PROGRESS_INDICATOR = "ingestion_progress_indicator"
    const val TAG_SUCCESS_SHOWCASE = "resume_success_showcase"
    const val TAG_EDIT_BUTTON = "edit_resume_button"
    const val TAG_SAVE_EDIT_BUTTON = "save_edit_button"
    const val TAG_CANCEL_EDIT_BUTTON = "cancel_edit_button"
}
