package com.waqas028.kmpinspector.presentation.bubble

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.waqas028.kmpinspector.data.InspectorStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waqas028.kmpinspector.presentation.theme.DebugPalette
import com.waqas028.kmpinspector.presentation.theme.Glyph
import com.waqas028.kmpinspector.presentation.theme.InspectorIcon
import com.waqas028.kmpinspector.presentation.theme.InspectorType
import kotlin.math.roundToInt
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

private enum class SnapEdge { Left, Right }

private val BUBBLE_SIZE = 56.dp

/** How far the resting bubble hangs off the snapped edge. */
private val RECESS = 16.dp

/**
 * The always-available entry point, which must never break the host app.
 *
 * Resting, part of the circle sits outside the viewport at 40% opacity, so it covers less of the
 * host app. Unread activity or a crash makes it opaque — contrast rules apply to the states that
 * carry information — but it stays recessed either way, so the bubble never moves on its own.
 */
@Composable
internal fun InspectorBubble(
    unreadCount: Int,
    hasCrash: Boolean,
    onClick: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxX = with(density) { maxWidth.toPx() }
        val maxY = with(density) { maxHeight.toPx() }

        // Geometry is derived from the fixed 56dp circle, never from the measured box. The badge
        // and the crash halo live inside that box, so measuring it would change the bubble's
        // position whenever a badge appeared or a crash arrived — it would drift sideways.
        val bubblePx = with(density) { BUBBLE_SIZE.toPx() }
        val recessPx = with(density) { RECESS.toPx() }

        var dragging by remember { mutableStateOf(false) }
        var pressed by remember { mutableStateOf(false) }
        // Seeded from the shared store and written back on every change, so the bubble stays
        // where it was dragged across Activities and rotations. Clamped, because the last screen
        // may have been a different size.
        var edge by remember {
            mutableStateOf(if (InspectorStore.bubbleOnRight) SnapEdge.Right else SnapEdge.Left)
        }
        var position by remember {
            mutableStateOf(
                InspectorStore.bubblePosition?.let {
                    Offset(
                        it.x.coerceIn(0f, (maxX - bubblePx).coerceAtLeast(0f)),
                        it.y.coerceIn(0f, (maxY - bubblePx).coerceAtLeast(0f)),
                    )
                },
            )
        }
        LaunchedEffect(position, edge) {
            InspectorStore.bubblePosition = position
            InspectorStore.bubbleOnRight = edge == SnapEdge.Right
        }

        val current = position ?: Offset(maxX - bubblePx, maxY * 0.62f)

        val active = dragging || pressed
        val informational = unreadCount > 0 || hasCrash
        val targetAlpha = when {
            active -> 1f
            informational -> 0.92f
            else -> 0.40f
        }
        val alpha by animateFloatAsState(targetAlpha, tween(160), label = "bubbleAlpha")
        val scale by animateFloatAsState(if (active) 1.06f else 1f, tween(120), label = "bubbleScale")

        // Touch-down brings it fully back in before the tap resolves, so the whole circle is hittable.
        val recessTarget = if (active) 0f else recessPx
        val recess by animateFloatAsState(recessTarget, tween(160), label = "bubbleRecess")
        val recessX = if (edge == SnapEdge.Right) recess else -recess

        Box(
            modifier = Modifier
                .offset { IntOffset((current.x + recessX).roundToInt(), current.y.roundToInt()) }
                .size(BUBBLE_SIZE),
        ) {
            if (hasCrash) CrashHalo()

            Box(
                modifier = Modifier
                    .size(BUBBLE_SIZE)
                    .scale(scale)
                    .alpha(alpha)
                    .shadow(4.dp, CircleShape)
                    .background(
                        if (hasCrash) DebugPalette.crashFill else DebugPalette.surfaceRaised,
                        CircleShape,
                    )
                    .border(1.dp, if (hasCrash) DebugPalette.bad else DebugPalette.accent, CircleShape)
                    .semantics {
                        contentDescription = buildString {
                            append("Open KmpInspector")
                            if (unreadCount > 0) append(", $unreadCount new")
                            if (hasCrash) append(", crash captured")
                        }
                    }
                    .pointerInput(maxX, maxY) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                // Snap horizontally only. Vertical stays where the finger left it:
                                // top and bottom are where host apps keep their own bars.
                                val p = position ?: current
                                val toRight = p.x + bubblePx / 2f > maxX / 2f
                                edge = if (toRight) SnapEdge.Right else SnapEdge.Left
                                position = Offset(if (toRight) maxX - bubblePx else 0f, p.y)
                            },
                            onDragCancel = { dragging = false },
                        ) { change, drag ->
                            change.consume()
                            val next = (position ?: current) + drag
                            position = Offset(
                                next.x.coerceIn(0f, (maxX - bubblePx).coerceAtLeast(0f)),
                                next.y.coerceIn(0f, (maxY - bubblePx).coerceAtLeast(0f)),
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { onClick() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // One glyph in every state. The crash state is carried by the fill, the border,
                // the pulsing halo and the badge instead of by a different symbol.
                InspectorIcon(
                    glyph = Glyph.TravelExplore,
                    contentDescription = null,
                    size = 24.dp,
                    tint = if (hasCrash) DebugPalette.bad else DebugPalette.accent,
                )
            }

            // The badge is the exception to the transparency: a translucent number is not a number.
            // It sits on the inward side so it stays visible while the bubble is recessed.
            if (unreadCount > 0 || hasCrash) {
                val count = if (hasCrash && unreadCount == 0) 1 else unreadCount
                Box(
                    modifier = Modifier
                        .align(if (edge == SnapEdge.Right) Alignment.TopStart else Alignment.TopEnd)
                        .offset(x = if (edge == SnapEdge.Right) (-10).dp else 10.dp, y = (-6).dp)
                        .widthIn(min = 20.dp)
                        .background(DebugPalette.accent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = count.toString(),
                        style = InspectorType.mono(
                            size = 11.sp,
                            weight = FontWeight.Bold,
                            color = DebugPalette.badgeText,
                            tabular = true,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Third signal on a crash, so the state survives greyscale and red-blindness. */
@Composable
private fun CrashHalo() {
    val transition = rememberInfiniteTransition(label = "halo")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400)),
        label = "haloProgress",
    )
    // Drawn inside the fixed-size box and centred, so it cannot affect layout.
    Box(Modifier.size(BUBBLE_SIZE), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(BUBBLE_SIZE + (9.dp * progress))
                .alpha((1f - progress) * 0.45f)
                .border(2.dp, DebugPalette.bad, CircleShape),
        )
    }
}
