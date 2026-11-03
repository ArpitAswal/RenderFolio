package com.example.constant

import androidx.compose.ui.graphics.Color

object AppConstants {

    const val DISABLE_NATIVE_PDF_EXTRACTION = false // false = native first with ML Kit fallback
    const val MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L // 10MB
    const val TIER_1_CHAR_THRESHOLD = 150 // Threshold below which ML Kit OCR activates

    // Cold-Start Screen UI & Adaptive Tokens
    const val MAX_COLD_START_CONTENT_WIDTH_DP = 380
    const val TABLET_COLD_START_CONTENT_WIDTH_DP = 440
    const val COLD_START_CARD_CORNER_RADIUS_DP = 20
    const val COLD_START_BUTTON_CORNER_RADIUS_DP = 14
    const val COLD_START_ENTRANCE_ANIM_DURATION_MS = 280L

    // PDF Extraction Screen Tokens
    const val EXTRACTION_CONTENT_MAX_WIDTH_DP = 380
    const val EXTRACTION_WHEEL_HEIGHT_DP = 220
    const val EXTRACTION_SLOT_HEIGHT_DP = 68
    const val EXTRACTION_SCROLLING_SPACING_DP = 20

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
