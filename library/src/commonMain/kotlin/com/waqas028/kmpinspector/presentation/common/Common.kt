package com.waqas028.kmpinspector.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.shadow
import com.waqas028.kmpinspector.presentation.NameOrder
import com.waqas028.kmpinspector.presentation.SortOrder
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.waqas028.kmpinspector.presentation.theme.DebugPalette
import com.waqas028.kmpinspector.presentation.theme.Glyph
import com.waqas028.kmpinspector.presentation.theme.InspectorIcon
import com.waqas028.kmpinspector.presentation.theme.InspectorType

/**
 * The hit-slop rule: every interactive element gets a 48dp minimum target, but chips, level letters
 * and toggles draw at 32dp inside it. A toolbar of 48dp-tall pills reads as a wall of buttons; the
 * visual weight stays quiet while the target stays legal.
 */
@Composable
internal fun HitTarget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minSize: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    // No ripple: the target is a 48dp square around a 32dp pill, so a ripple draws a box that
    // matches nothing visible. The pill's own selected state is the feedback.
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minSize, minHeight = minSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Children draw at their own (smaller) size; the box above carries the target.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) { content() }
    }
}

/** 32dp pill. Active: accent @14% fill, 1dp accent border, accent text. */
@Composable
internal fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HitTarget(onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .background(
                    if (selected) DebugPalette.activePillFill else Color.Transparent,
                    RoundedCornerShape(16.dp),
                )
                .border(
                    1.dp,
                    if (selected) DebugPalette.accent else DebugPalette.lineStrong,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = InspectorType.mono(
                    size = 11.5.sp,
                    weight = FontWeight.Medium,
                    color = if (selected) DebugPalette.accent else DebugPalette.textDim,
                    tabular = true,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * The one-line footer under a list: a count on the left, and room on the right for a control
 * that must never be clipped, such as the sort toggle. Unlike the filter rows it does not scroll.
 */
@Composable
internal fun StatusLine(text: String, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f).padding(end = 8.dp), style = InspectorType.meta, maxLines = 2)
        trailing()
    }
}

/** A quiet bordered pill for one-off actions on a status line: Clear, Share, CSV. */
@Composable
internal fun ActionPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false) {
    HitTarget(onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .border(1.dp, if (accent) DebugPalette.accent else DebugPalette.lineStrong, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = InspectorType.mono(11.5.sp, FontWeight.Medium, if (accent) DebugPalette.accent else DebugPalette.textDim),
                maxLines = 1,
            )
        }
    }
}

/**
 * The sort control. A word plus an arrow, not an icon alone: "↓ Newest" says both what the order is
 * and which way it goes, where a bare swap-vert icon says neither.
 */
@Composable
internal fun SortToggle(
    label: String,
    descending: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HitTarget(onClick = onToggle, modifier = modifier) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .border(1.dp, DebugPalette.lineStrong, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (if (descending) "↓ " else "↑ ") + label,
                style = InspectorType.mono(11.5.sp, FontWeight.Medium, DebugPalette.textDim),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SortToggle(order: SortOrder, onToggle: () -> Unit, modifier: Modifier = Modifier) =
    SortToggle(
        label = if (order == SortOrder.NewestFirst) "Newest" else "Oldest",
        descending = order == SortOrder.NewestFirst,
        onToggle = onToggle,
        modifier = modifier,
    )

@Composable
internal fun SortToggle(order: NameOrder, onToggle: () -> Unit, modifier: Modifier = Modifier) =
    SortToggle(
        label = if (order == NameOrder.Ascending) "A–Z" else "Z–A",
        descending = order == NameOrder.Descending,
        onToggle = onToggle,
        modifier = modifier,
    )

/**
 * A "back to top" pill that floats over a list once the reader has scrolled a few items down.
 * Place it inside the same Box as the LazyColumn; it aligns itself to the bottom centre.
 *
 * It appears after [threshold] items rather than only at the very end, because the moment you
 * want it is when the top is out of reach, not when you have read everything.
 */
@Composable
internal fun BoxScope.ScrollToTop(listState: LazyListState, threshold: Int = 4) {
    val scope = rememberCoroutineScope()
    val visible by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex >= threshold }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        HitTarget(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .shadow(6.dp, RoundedCornerShape(16.dp))
                    .background(DebugPalette.surfaceRaised, RoundedCornerShape(16.dp))
                    .border(1.dp, DebugPalette.accent, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "↑ Top",
                    style = InspectorType.mono(11.5.sp, FontWeight.Medium, DebugPalette.accent),
                    maxLines = 1,
                )
            }
        }
    }
}

/** A bordered chip that is not a pill — 4dp radius. Used for constraints, BLOBs, repeat counts. */
@Composable
internal fun OutlineChip(
    label: String,
    modifier: Modifier = Modifier,
    tone: Color = DebugPalette.textDim,
    fill: Color = Color.Transparent,
    borderColor: Color = DebugPalette.line,
    size: androidx.compose.ui.unit.TextUnit = 10.sp,
) {
    Box(
        modifier = modifier
            .heightIn(min = 24.dp)
            .background(fill, RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = InspectorType.mono(size, FontWeight.Medium, tone, tabular = true),
            maxLines = 1,
        )
    }
}

/**
 * Glyph, then literal text, then colour — in that order of priority, so status survives greyscale
 * and colour-blindness. This draws the glyph half.
 */
@Composable
internal fun StatusMark(glyph: String, tone: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Box(
        modifier = modifier
            .size(size)
            .border(1.dp, tone, RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = InspectorType.mono(10.sp, FontWeight.Medium, tone), maxLines = 1)
    }
}

/** State badge: glyph + word, so the state reads without colour. */
@Composable
internal fun StateBadge(glyph: String, word: String, tone: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 20.dp)
            .border(1.dp, tone, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$glyph $word",
            style = InspectorType.mono(9.5.sp, FontWeight.Medium, tone, tracking = 0.06.em),
            maxLines = 1,
        )
    }
}

/** Uppercase mono section label above a block in a detail pane. */
@Composable
internal fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, style = InspectorType.kicker, maxLines = 1)
}

@Composable
internal fun Hairline(modifier: Modifier = Modifier, color: Color = DebugPalette.line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
internal fun VerticalHairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight().width(1.dp).background(DebugPalette.line))
}

/**
 * First-run state. Every one contains the fix: an empty list that only says "nothing here" wastes
 * the one moment the developer is looking for setup instructions.
 */
@Composable
internal fun EmptyState(
    glyph: Glyph,
    title: String,
    sentence: String,
    snippet: String,
    modifier: Modifier = Modifier,
    glyphTint: Color = DebugPalette.textFaint,
    footnote: String? = null,
) {
    val clipboard = LocalClipboardManager.current
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            InspectorIcon(glyph, contentDescription = null, size = 40.dp, tint = glyphTint)
            Text(title, style = InspectorType.title.copy(fontSize = 20.sp))
            Text(
                text = sentence,
                style = InspectorType.prose(),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(DebugPalette.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, DebugPalette.line, RoundedCornerShape(4.dp))
                    .padding(12.dp),
            ) {
                Text(snippet, style = InspectorType.mono(11.5.sp, color = DebugPalette.text))
            }
            HitTarget(onClick = { clipboard.setText(AnnotatedString(snippet)) }) {
                Text(
                    "Copy snippet",
                    style = InspectorType.mono(12.sp, FontWeight.Medium, DebugPalette.accent),
                )
            }
            if (footnote != null) {
                Text(
                    footnote,
                    style = InspectorType.mono(11.sp, color = DebugPalette.textFaint, lineHeight = 17.sp),
                )
            }
        }
    }
}

/**
 * Shown when a filter or search matches nothing.
 *
 * Distinct from [EmptyState], which is the first-run case and carries setup instructions. Here the
 * data exists and the filter is hiding it, so the message names the filter and offers a way out —
 * a blank pane leaves the developer wondering whether the capture broke.
 */
@Composable
internal fun NoResults(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message,
                style = InspectorType.mono(12.sp, color = DebugPalette.textDim),
            )
            if (actionLabel != null && onAction != null) {
                HitTarget(onClick = onAction) {
                    Text(
                        actionLabel,
                        style = InspectorType.mono(12.sp, FontWeight.Medium, DebugPalette.accent),
                    )
                }
            }
        }
    }
}

/** Two-column key/value table used by Headers, Input/Output data. */
@Composable
internal fun KeyValueRow(key: String, value: String, keyWeight: Float = 0.34f) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(
            key,
            modifier = Modifier.weight(keyWeight),
            style = InspectorType.mono(11.5.sp, color = DebugPalette.textDim),
        )
        Text(
            value,
            modifier = Modifier.weight(1f - keyWeight),
            style = InspectorType.mono(11.5.sp, color = DebugPalette.text, lineHeight = 18.sp),
        )
    }
}

internal val NullValueStyle
    @Composable get() = InspectorType.mono(12.sp, color = DebugPalette.textFaint)
        .copy(fontStyle = FontStyle.Italic)
