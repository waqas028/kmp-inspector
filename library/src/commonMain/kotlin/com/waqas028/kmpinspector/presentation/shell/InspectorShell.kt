package com.waqas028.kmpinspector.presentation.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waqas028.kmpinspector.data.InspectorPlatform
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.data.formatElapsed
import com.waqas028.kmpinspector.presentation.InspectorState
import com.waqas028.kmpinspector.presentation.InspectorTab
import com.waqas028.kmpinspector.presentation.PaneWidth
import com.waqas028.kmpinspector.presentation.common.Hairline
import com.waqas028.kmpinspector.presentation.common.HitTarget
import com.waqas028.kmpinspector.presentation.common.VerticalHairline
import com.waqas028.kmpinspector.presentation.crashes.CrashesSection
import com.waqas028.kmpinspector.presentation.database.DatabaseSection
import com.waqas028.kmpinspector.presentation.listPaneWidth
import com.waqas028.kmpinspector.presentation.logs.LogsSection
import com.waqas028.kmpinspector.presentation.network.NetworkSection
import com.waqas028.kmpinspector.presentation.paneWidthFor
import com.waqas028.kmpinspector.presentation.InspectorBackHandler
import com.waqas028.kmpinspector.presentation.theme.DebugPalette
import com.waqas028.kmpinspector.presentation.theme.Glyph
import com.waqas028.kmpinspector.presentation.theme.InspectorIcon
import com.waqas028.kmpinspector.presentation.theme.InspectorTheme
import com.waqas028.kmpinspector.presentation.theme.InspectorType
import com.waqas028.kmpinspector.presentation.work.WorkSection

/** Tabs available on this platform. Background Work is absent off Android, not disabled. */
internal fun visibleTabs(): List<InspectorTab> =
    InspectorTab.entries.filter { it != InspectorTab.Work || InspectorPlatform.isAndroid }

private fun InspectorTab.glyph(): Glyph = when (this) {
    InspectorTab.Network -> Glyph.SwapVert
    InspectorTab.Database -> Glyph.TableChart
    InspectorTab.Work -> Glyph.WorkHistory
    InspectorTab.Logs -> Glyph.Subject
    InspectorTab.Crashes -> Glyph.ErrorMark
}

private fun InspectorTab.searchPlaceholder(): String = when (this) {
    InspectorTab.Network -> "Search network…"
    InspectorTab.Database -> "Search tables…"
    InspectorTab.Work -> "Search work…"
    InspectorTab.Logs -> "Search logs…"
    InspectorTab.Crashes -> "Search crashes…"
}

/** Full-screen inspector over the host app. */
@Composable
internal fun InspectorShell(state: InspectorState, onClose: () -> Unit) = InspectorTheme {
    Surface(color = DebugPalette.bg, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val pane = paneWidthFor(maxWidth)
            // System back closes the inspector. At phone width the detail replaces the list, so
            // back first returns to the list, the way the in-app arrow does; wider panes show both
            // at once and go straight to closing.
            InspectorBackHandler(enabled = true) {
                if (pane != PaneWidth.Compact || !state.handleBack()) onClose()
            }

            Column(
                Modifier
                    .fillMaxSize()
                    // The inspector covers the whole window, so it owns the system bar insets.
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                InspectorHeader(onClose = onClose)
                Hairline()
                InspectorSearchField(state)
                InspectorTabs(state)
                Hairline()
                Box(Modifier.fillMaxSize()) {
                    when (state.tab) {
                        InspectorTab.Network -> NetworkSection(state, pane)
                        InspectorTab.Database -> DatabaseSection(state, pane)
                        InspectorTab.Work -> WorkSection(state, pane)
                        InspectorTab.Logs -> LogsSection(state)
                        InspectorTab.Crashes -> CrashesSection(state, pane)
                    }
                }
            }
        }
    }
}

/**
 * The header carries the session, not a logo: package, variant, OS and capture uptime are what a
 * developer checks a bug report against, and they make screenshots self-documenting.
 */
@Composable
private fun InspectorHeader(onClose: () -> Unit) {
    val elapsed = remember { InspectorStore.sessionStartMillis }
    val session = buildString {
        append(InspectorStore.appId)
        append(" · ").append(InspectorStore.variant)
        append(" · ").append(InspectorPlatform.name)
        append(" · ").append(formatElapsed(InspectorPlatform.currentTimeMillis() - elapsed))
        append(" captured")
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("KmpInspector", style = InspectorType.title)
            Text(
                session,
                style = InspectorType.mono(11.sp, color = DebugPalette.textDim, tabular = true),
                maxLines = 2,
            )
        }
        HitTarget(onClick = onClose) {
            InspectorIcon(Glyph.Close, "Close inspector", size = 20.dp, tint = DebugPalette.textDim)
        }
    }
}

/** Scoped to the active tab, and the placeholder says so, so the field never lies about its reach. */
@Composable
private fun InspectorSearchField(state: InspectorState) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(DebugPalette.surface, RoundedCornerShape(4.dp))
                .border(1.dp, DebugPalette.lineStrong, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InspectorIcon(Glyph.Search, null, size = 16.dp, tint = DebugPalette.textFaint)
            Box(Modifier.weight(1f).padding(start = 10.dp)) {
                if (state.query.isEmpty()) {
                    Text(
                        state.tab.searchPlaceholder(),
                        style = InspectorType.mono(13.sp, color = DebugPalette.textFaint),
                    )
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = { state.query = it },
                    singleLine = true,
                    textStyle = InspectorType.mono(13.sp, color = DebugPalette.text),
                    cursorBrush = SolidColor(DebugPalette.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Scrollable single row. Six sections never fit 380dp, and a "more" menu hides exactly the section
 * you need.
 */
@Composable
private fun InspectorTabs(state: InspectorState) {
    val tabs = visibleTabs()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab == state.tab
            val hasUnreadCrash = tab == InspectorTab.Crashes && InspectorStore.hasCrash
            Column(
                modifier = Modifier
                    .height(48.dp)
                    .clickable { state.selectTab(tab) }
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InspectorIcon(
                        tab.glyph(),
                        null,
                        size = 16.dp,
                        tint = if (selected) DebugPalette.accent else DebugPalette.textDim,
                    )
                    Text(
                        tab.label,
                        modifier = Modifier.padding(start = 6.dp),
                        style = InspectorType.tabLabel.copy(
                            color = if (selected) DebugPalette.accent else DebugPalette.textDim,
                        ),
                        maxLines = 1,
                    )
                    if (hasUnreadCrash) {
                        Box(
                            Modifier
                                .padding(start = 5.dp)
                                .size(5.dp)
                                .background(DebugPalette.bad, RoundedCornerShape(50)),
                        )
                    }
                }
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .height(2.dp)
                        .width(if (selected) 44.dp else 0.dp)
                        .background(if (selected) DebugPalette.accent else Color.Transparent),
                )
            }
        }
    }
}

/**
 * One body, three arrangements. At >= 768dp the back arrow disappears and selecting a row updates
 * the detail pane in place.
 */
@Composable
internal fun MasterDetail(
    pane: PaneWidth,
    hasSelection: Boolean,
    onBack: () -> Unit,
    placeholder: String,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    if (pane == PaneWidth.Compact) {
        if (hasSelection) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HitTarget(onClick = onBack) {
                        InspectorIcon(Glyph.ArrowBack, "Back to list", size = 18.dp, tint = DebugPalette.textDim)
                    }
                }
                Box(Modifier.fillMaxSize()) { detail() }
            }
        } else {
            list()
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(pane.listPaneWidth).fillMaxHeight()) { list() }
            VerticalHairline()
            Box(Modifier.fillMaxSize()) {
                if (hasSelection) {
                    detail()
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            placeholder,
                            style = InspectorType.mono(12.sp, color = DebugPalette.textFaint),
                        )
                    }
                }
            }
        }
    }
}
