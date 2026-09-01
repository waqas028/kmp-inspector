package com.waqas028.kmpinspector.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Fixed colours that deliberately ignore dynamic color.
 *
 * A status hue derived from the user's wallpaper cannot be relied on, so status never depends on
 * colour alone: every state is a glyph, then literal text, then one of these tones. Dynamic color
 * is confined to chrome — app bar, tab indicator, ripples — which comes from `MaterialTheme`.
 */
internal object DebugPalette {
    val bg = Color(0xFF191817)
    val surface = Color(0xFF201F1E)
    val surfaceRaised = Color(0xFF232221)
    val surfaceSunken = Color(0xFF1E1D1C)

    private val hairline = Color(0xFFECE8E2)
    val line = hairline.copy(alpha = 0.13f)
    val lineStrong = hairline.copy(alpha = 0.20f)
    val lineFaint = hairline.copy(alpha = 0.07f)

    val text = Color(0xFFEDE9E4)
    val textDim = Color(0xFFA8A29B)
    val textFaint = Color(0xFF6E6A64)

    val accent = Color(0xFFE1AD66)
    val ok = Color(0xFF93AD8B)
    val warn = Color(0xFFE1AD66)
    val bad = Color(0xFFE08C7D)
    val neutralState = Color(0xFFA8A29B)
    val cancelled = Color(0xFF6E6A64)

    /** Bubble fill when a fatal exception has been captured. */
    val crashFill = Color(0xFF5A2A24)
    val badgeText = Color(0xFF1B1A19)

    val selectionFill = accent.copy(alpha = 0.09f)
    val activePillFill = accent.copy(alpha = 0.14f)
    val hoverFill = text.copy(alpha = 0.07f)
    val pressedFill = text.copy(alpha = 0.14f)
    val cellSelectedFill = accent.copy(alpha = 0.16f)
    val appFrameFill = accent.copy(alpha = 0.06f)
    val repeatChipFill = text.copy(alpha = 0.10f)
}
