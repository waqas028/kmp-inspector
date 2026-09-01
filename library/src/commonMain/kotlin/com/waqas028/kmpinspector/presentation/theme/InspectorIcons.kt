package com.waqas028.kmpinspector.presentation.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Material Symbols named in the handoff, drawn rather than imported.
 *
 * Compose Multiplatform's material3 ships no icon set, and `materialIconsExtended` is thousands of
 * vectors for the dozen used here. The names match Material's so the spec stays readable.
 */
internal enum class Glyph {
    TravelExplore, Close, Search, SwapVert, TableChart, WorkHistory, Subject, ErrorMark,
    ChevronRight, ArrowBack, ContentCopy, Terminal, Pause, PlayArrow, Share, CheckCircle,
}

@Composable
internal fun InspectorIcon(
    glyph: Glyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = DebugPalette.text,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
    ) {
        drawGlyph(glyph, tint)
    }
}


/** Draws [glyph] scaled into the current canvas, from a nominal 24x24 grid. */
internal fun DrawScope.drawGlyph(glyph: Glyph, tint: Color) {
    val u = size.minDimension / 24f
    val sw = 1.9f * u
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(tint, p(x1, y1), p(x2, y2), sw, StrokeCap.Round)

    when (glyph) {
        Glyph.Search -> {
            drawCircle(tint, radius = 6.4f * u, center = p(10f, 10f), style = stroke)
            line(14.8f, 14.8f, 20.5f, 20.5f)
        }

        // The real Material symbol, so the bubble matches the design exactly.
        Glyph.TravelExplore -> {
            val s = size.minDimension / 960f
            withTransform({ scale(s, s, Offset.Zero) }) {
                drawPath(travelExplorePath, tint)
            }
        }

        Glyph.Close -> {
            line(5.5f, 5.5f, 18.5f, 18.5f)
            line(18.5f, 5.5f, 5.5f, 18.5f)
        }

        Glyph.SwapVert -> {
            line(8f, 4.5f, 8f, 18f); line(4.8f, 14.8f, 8f, 18f); line(11.2f, 14.8f, 8f, 18f)
            line(16f, 19.5f, 16f, 6f); line(12.8f, 9.2f, 16f, 6f); line(19.2f, 9.2f, 16f, 6f)
        }

        Glyph.TableChart -> {
            drawRect(tint, p(3.5f, 4.5f), Size(17f * u, 15f * u), style = stroke)
            line(3.5f, 9.5f, 20.5f, 9.5f)
            line(9.5f, 9.5f, 9.5f, 19.5f)
        }

        Glyph.WorkHistory -> {
            drawCircle(tint, radius = 7.5f * u, center = p(12f, 12.5f), style = stroke)
            line(12f, 8.5f, 12f, 12.5f); line(12f, 12.5f, 15f, 14.5f)
        }

        Glyph.Subject -> {
            line(4f, 6.5f, 20f, 6.5f); line(4f, 11f, 20f, 11f)
            line(4f, 15.5f, 16f, 15.5f); line(4f, 20f, 12f, 20f)
        }

        Glyph.ErrorMark -> {
            drawCircle(tint, radius = 8f * u, center = p(12f, 12f), style = stroke)
            line(12f, 7.2f, 12f, 13.2f)
            drawCircle(tint, radius = 1.05f * u, center = p(12f, 16.6f))
        }

        Glyph.ChevronRight -> { line(9.5f, 5.5f, 16f, 12f); line(16f, 12f, 9.5f, 18.5f) }
        Glyph.ArrowBack -> { line(19f, 12f, 5.5f, 12f); line(11f, 5.5f, 5.5f, 12f); line(11f, 18.5f, 5.5f, 12f) }

        Glyph.ContentCopy -> {
            drawRoundRectOutline(tint, p(8.5f, 3.5f), Size(12f * u, 14f * u), 2f * u, stroke)
            drawRoundRectOutline(tint, p(3.5f, 6.5f), Size(12f * u, 14f * u), 2f * u, stroke)
        }

        Glyph.Terminal -> {
            drawRoundRectOutline(tint, p(3f, 4.5f), Size(18f * u, 15f * u), 2f * u, stroke)
            line(7f, 9f, 10.5f, 12f); line(10.5f, 12f, 7f, 15f)
            line(12.5f, 15.5f, 17f, 15.5f)
        }

        Glyph.Pause -> { line(9.5f, 5.5f, 9.5f, 18.5f); line(14.5f, 5.5f, 14.5f, 18.5f) }

        Glyph.PlayArrow -> {
            val path = Path().apply {
                moveTo(8f * u, 5f * u); lineTo(18.5f * u, 12f * u); lineTo(8f * u, 19f * u); close()
            }
            drawPath(path, tint)
        }

        Glyph.Share -> {
            line(12f, 3.5f, 12f, 14.5f)
            line(8.5f, 7f, 12f, 3.5f); line(15.5f, 7f, 12f, 3.5f)
            drawPath(
                Path().apply {
                    moveTo(6.5f * u, 10.5f * u); lineTo(4.5f * u, 10.5f * u)
                    lineTo(4.5f * u, 20.5f * u); lineTo(19.5f * u, 20.5f * u)
                    lineTo(19.5f * u, 10.5f * u); lineTo(17.5f * u, 10.5f * u)
                },
                tint,
                style = stroke,
            )
        }

        Glyph.CheckCircle -> {
            drawCircle(tint, radius = 8f * u, center = p(12f, 12f), style = stroke)
            line(7.8f, 12.2f, 10.8f, 15.2f); line(10.8f, 15.2f, 16.2f, 9f)
        }
    }
}

private fun DrawScope.drawRoundRectOutline(
    color: Color,
    topLeft: Offset,
    size: Size,
    radius: Float,
    stroke: Stroke,
) = drawRoundRect(
    color = color,
    topLeft = topLeft,
    size = size,
    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
    style = stroke,
)

/**
 * `travel_explore` from Google's material-design-icons (Apache 2.0), embedded as path data rather
 * than pulled in as a font or an icon dependency.
 *
 * Material Symbols author in a `0 -960 960 960` viewBox — y runs from -960 to 0 — so the path is
 * shifted down once here and simply scaled at draw time.
 */
private const val TRAVEL_EXPLORE_PATH_DATA =
    "M480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q146 0 255.5 91.5T872-559h-82q-19-73-68.5-130.5T600-776v16q0 33-23.5 56.5T520-680h-80v80q0 17-11.5 28.5T400-560h-80v80h80v120h-40L168-552q-3 18-5.5 36t-2.5 36q0 131 92 225t228 95v80Zm364-20L716-228q-21 12-45 20t-51 8q-75 0-127.5-52.5T440-380q0-75 52.5-127.5T620-560q75 0 127.5 52.5T800-380q0 27-8 51t-20 45l128 128-56 56ZM620-280q42 0 71-29t29-71q0-42-29-71t-71-29q-42 0-71 29t-29 71q0 42 29 71t71 29Z"

private val travelExplorePath: Path by lazy {
    PathParser().parsePathString(TRAVEL_EXPLORE_PATH_DATA).toPath().apply {
        transform(Matrix().apply { translate(0f, 960f) })
    }
}
