package com.waqas028.kmpinspector

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Wraps [content] and floats a draggable inspector button over it. Callers get the button and the
 * inspector screen for free — they only have to wrap their root composable once:
 *
 * ```
 * setContent {
 *     KmpInspector {
 *         MyApp()
 *     }
 * }
 * ```
 *
 * Set [enabled] too false to compile the overlay out of the composition entirely — pass your own
 * debug flag so the inspector never reaches a release build.
 */
@Composable
fun KmpInspector(
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    var inspectorOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        content()

        // Hidden while the inspector is open, so it doesn't float over its own screen.
        if (!inspectorOpen) {
            DraggableInspectorFab(onClick = { inspectorOpen = true })
        }

        if (inspectorOpen) {
            InspectorScreen(onClose = { inspectorOpen = false })
        }
    }
}

@Composable
private fun DraggableInspectorFab(onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxX = with(density) { maxWidth.toPx() }
        val maxY = with(density) { maxHeight.toPx() }
        val margin = with(density) { 16.dp.toPx() }
        // Extra bottom inset so it doesn't start under the iOS home indicator.
        val bottomInset = with(density) { 48.dp.toPx() }

        var fabSize by remember { mutableStateOf(IntSize.Zero) }
        var dragged by remember { mutableStateOf<Offset?>(null) }

        // Until the user drags it, park it bottom-end. Recomputed if the window resizes,
        // which matters on desktop.
        val position = dragged ?: Offset(
            x = (maxX - fabSize.width - margin).coerceAtLeast(0f),
            y = (maxY - fabSize.height - bottomInset).coerceAtLeast(0f),
        )

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .onSizeChanged { fabSize = it }
                .semantics { contentDescription = "Open KMP Inspector" }
                .pointerInput(maxX, maxY, fabSize) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val next = (dragged ?: position) + dragAmount
                        // Clamp so it can never be dragged off-screen and become unreachable.
                        dragged = Offset(
                            x = next.x.coerceIn(0f, (maxX - fabSize.width).coerceAtLeast(0f)),
                            y = next.y.coerceIn(0f, (maxY - fabSize.height).coerceAtLeast(0f)),
                        )
                    }
                },
        ) {
            InspectorIcon()
        }
    }
}

/**
 * A magnifying glass, drawn rather than imported. Compose Multiplatform's material3 does not ship
 * the Material icon set, and pulling in `materialIconsExtended` for one glyph is a poor trade.
 */
@Composable
private fun InspectorIcon() {
    val tint = LocalContentColor.current
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val radius = size.minDimension * 0.27f
        val lensCenter = Offset(size.width * 0.42f, size.height * 0.42f)

        drawCircle(
            color = tint,
            radius = radius,
            center = lensCenter,
            style = Stroke(width = stroke),
        )

        // Handle, running from the lens edge out to the lower-right corner.
        val edge = radius * 0.72f
        drawLine(
            color = tint,
            start = Offset(lensCenter.x + edge, lensCenter.y + edge),
            end = Offset(size.width * 0.88f, size.height * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Deliberately empty for now — this is where inspector tools will go. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectorScreen(onClose: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("KMP Inspector") },
                    actions = {
                        TextButton(onClick = onClose) { Text("Close") }
                    },
                )
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding))
        }
    }
}
