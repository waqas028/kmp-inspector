package com.waqas028.kmpinspector.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The inspector supplies its own dark scheme rather than inheriting the host app's.
 *
 * Without this, every Material component drawn inside the inspector — dropdown menus, ripples,
 * text-selection handles — takes its colours from whatever theme the host app happens to use. A
 * light-themed host renders a white menu surface behind the inspector's near-white text, which is
 * how the tag dropdown ended up unreadable.
 *
 * This covers chrome only. Status tones stay in [DebugPalette], deliberately outside the scheme, so
 * they survive dynamic color.
 */
@Composable
internal fun InspectorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DebugPalette.accent,
            onPrimary = DebugPalette.badgeText,
            background = DebugPalette.bg,
            onBackground = DebugPalette.text,
            surface = DebugPalette.surfaceRaised,
            onSurface = DebugPalette.text,
            surfaceVariant = DebugPalette.surface,
            onSurfaceVariant = DebugPalette.textDim,
            surfaceContainer = DebugPalette.surfaceRaised,
            surfaceContainerHigh = DebugPalette.surfaceRaised,
            outline = DebugPalette.lineStrong,
            error = DebugPalette.bad,
        ),
        content = content,
    )
}
