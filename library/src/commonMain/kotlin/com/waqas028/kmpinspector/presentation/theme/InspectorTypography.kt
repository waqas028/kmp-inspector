package com.waqas028.kmpinspector.presentation.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Every technical value is monospaced: paths, methods, status codes, headers, JSON, SQL, cell
 * values, log lines, stack frames, worker names, ids and timestamps.
 *
 * The handoff calls for bundled JetBrains Mono; this uses the platform monospace face instead
 * (Menlo/SF Mono on Apple, Roboto Mono on Android) so the library ships no binary font assets.
 * Swap [monoFamily] for a bundled family to match the design exactly.
 */
internal object InspectorType {

    val monoFamily: FontFamily = FontFamily.Monospace

    /**
     * Tabular figures, so columns of numbers line up. Durations, sizes, timestamps, row counts and
     * attempt counts all use this; prose deliberately does not.
     */
    private const val TABULAR = "tnum"

    fun mono(
        size: TextUnit = 12.sp,
        weight: FontWeight = FontWeight.Normal,
        color: androidx.compose.ui.graphics.Color = DebugPalette.text,
        lineHeight: TextUnit = TextUnit.Unspecified,
        tracking: TextUnit = TextUnit.Unspecified,
        tabular: Boolean = false,
    ) = TextStyle(
        fontFamily = monoFamily,
        fontSize = size,
        fontWeight = weight,
        color = color,
        lineHeight = lineHeight,
        letterSpacing = tracking,
        fontFeatureSettings = if (tabular) TABULAR else null,
    )

    /** Kicker / section label: mono 10sp, wide tracking, uppercase, `textFaint`. */
    val kicker = mono(10.sp, FontWeight.Medium, DebugPalette.textFaint, tracking = 0.1.em)

    /** Metadata and timestamps. */
    val meta = mono(10.5.sp, color = DebugPalette.textDim, tabular = true)

    /** Values in lists and grids. */
    val value = mono(12.5.sp, color = DebugPalette.text)

    /** JSON and stack frames — generous line height because they wrap. */
    val code = mono(12.sp, lineHeight = 20.4.sp)

    val overflowEllipsis = TextOverflow.Ellipsis
}
