package com.waqas028.kmpinspector.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.waqas028.kmpinspector.resources.Res
import com.waqas028.kmpinspector.resources.jetbrainsmono_bold
import com.waqas028.kmpinspector.resources.jetbrainsmono_medium
import com.waqas028.kmpinspector.resources.jetbrainsmono_regular
import org.jetbrains.compose.resources.Font

/**
 * Every technical value is monospaced: paths, methods, status codes, headers, JSON, SQL, cell
 * values, log lines, stack frames, worker names, ids and timestamps.
 *
 * JetBrains Mono is bundled rather than relying on the platform's monospace face. That face is not
 * guaranteed to exist: on ROMs that ship none, `FontFamily.Monospace` silently falls back to the
 * proportional default and every column of numbers stops lining up.
 */
internal object InspectorType {

    /**
     * Assigned once by [ProvideInspectorFonts] before any inspector UI composes.
     *
     * A CompositionLocal would be tidier, but the roles below are read from ordinary properties
     * rather than composables, so the family has to be resolvable without a composition. The
     * fallback keeps text readable if the resource ever fails to load.
     */
    var monoFamily: FontFamily = FontFamily.Monospace

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

    /**
     * The proportional roles. The prototype drew these in Cormorant Garamond and Lora; the handoff
     * says to substitute the M3 type scale's default family and not to ship a serif, so they use
     * the platform default. Mono is for technical values only — prose reads faster proportional.
     */
    val title get() = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 25.sp,
        fontWeight = FontWeight.Normal,
        color = DebugPalette.text,
    )

    val tabLabel get() = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
    )

    /** Crash messages and empty-state copy: 13sp at a 1.6 line-height. */
    fun prose(
        color: androidx.compose.ui.graphics.Color = DebugPalette.textDim,
        size: TextUnit = 13.sp,
    ) = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size,
        lineHeight = size * 1.6f,
        color = color,
    )

    /** Kicker / section label: mono 10sp, wide tracking, uppercase, `textFaint`. */
    val kicker get() = mono(10.sp, FontWeight.Medium, DebugPalette.textFaint, tracking = 0.1.em)

    /** Metadata and timestamps. */
    val meta get() = mono(10.5.sp, color = DebugPalette.textDim, tabular = true)

    /** Values in lists and grids. */
    val value get() = mono(12.5.sp, color = DebugPalette.text)

    /** JSON and stack frames — generous line height because they wrap. */
    val code get() = mono(12.sp, lineHeight = 20.4.sp)

    val overflowEllipsis = TextOverflow.Ellipsis
}

/**
 * Loads the bundled family and installs it on [InspectorType]. Call this above any inspector UI —
 * the bubble as well as the shell — so both render in the same face.
 */
@Composable
internal fun ProvideInspectorFonts() {
    val bundled = FontFamily(
        Font(Res.font.jetbrainsmono_regular, FontWeight.Normal),
        Font(Res.font.jetbrainsmono_medium, FontWeight.Medium),
        Font(Res.font.jetbrainsmono_bold, FontWeight.Bold),
    )
    // Written during composition, before children compose, so they read the bundled family.
    InspectorType.monoFamily = bundled
}
