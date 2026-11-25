package com.waqas028.kmpinspector.presentation.bubble

import androidx.compose.ui.geometry.Offset

/**
 * Bubble placement maths, shared by the on-screen bubble and the copy that floats above dialogs on
 * Android, so both land in the same place and snap the same way. Positions are the top-left of
 * the 56dp circle inside a `maxX` by `maxY` area, in pixels.
 */
internal fun clampBubble(position: Offset, bubblePx: Float, maxX: Float, maxY: Float): Offset = Offset(
    position.x.coerceIn(0f, (maxX - bubblePx).coerceAtLeast(0f)),
    position.y.coerceIn(0f, (maxY - bubblePx).coerceAtLeast(0f)),
)

/** Where the bubble starts before anyone has dragged it: right edge, a little below the middle. */
internal fun defaultBubblePosition(bubblePx: Float, maxX: Float, maxY: Float): Offset =
    Offset(maxX - bubblePx, maxY * 0.62f)

/**
 * Where a released bubble settles, and whether that is the right edge. Snaps horizontally only:
 * vertical stays where the finger left it, since top and bottom are where apps keep their bars.
 */
internal fun snapBubble(position: Offset, bubblePx: Float, maxX: Float, maxY: Float): Pair<Offset, Boolean> {
    val p = clampBubble(position, bubblePx, maxX, maxY)
    val toRight = p.x + bubblePx / 2f > maxX / 2f
    return Offset(if (toRight) maxX - bubblePx else 0f, p.y) to toRight
}
