package com.waqas028.kmpinspector.presentation.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waqas028.kmpinspector.data.AttachedPreferences
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.data.label
import com.waqas028.kmpinspector.data.parsePreferenceText
import com.waqas028.kmpinspector.domain.model.PreferenceEntry
import com.waqas028.kmpinspector.domain.model.PreferenceFile
import com.waqas028.kmpinspector.domain.model.PreferenceType
import com.waqas028.kmpinspector.presentation.InspectorState
import com.waqas028.kmpinspector.presentation.NameOrder
import com.waqas028.kmpinspector.presentation.PaneWidth
import com.waqas028.kmpinspector.presentation.PreferenceAddress
import com.waqas028.kmpinspector.presentation.common.ActionPill
import com.waqas028.kmpinspector.presentation.common.EmptyState
import com.waqas028.kmpinspector.presentation.common.Hairline
import com.waqas028.kmpinspector.presentation.common.Kicker
import com.waqas028.kmpinspector.presentation.common.NoResults
import com.waqas028.kmpinspector.presentation.common.OutlineChip
import com.waqas028.kmpinspector.presentation.common.RefreshPill
import com.waqas028.kmpinspector.presentation.common.ScrollToTop
import com.waqas028.kmpinspector.presentation.common.SortToggle
import com.waqas028.kmpinspector.presentation.common.StatusLine
import com.waqas028.kmpinspector.presentation.flip
import com.waqas028.kmpinspector.presentation.shell.MasterDetail
import com.waqas028.kmpinspector.presentation.theme.DebugPalette
import com.waqas028.kmpinspector.presentation.theme.Glyph
import com.waqas028.kmpinspector.presentation.theme.InspectorIcon
import com.waqas028.kmpinspector.presentation.theme.InspectorType

/** Long values (a cached JSON blob, say) are cut in the list; the sheet shows them in full. */
private const val VALUE_PREVIEW_CHARS = 600

@Composable
internal fun PreferencesSection(state: InspectorState, pane: PaneWidth) {
    val all = InspectorStore.preferences
    if (all.isEmpty()) {
        EmptyState(
            glyph = Glyph.Tune,
            title = "No preferences found",
            sentence = "On Android, SharedPreferences files are read when the inspector opens, and a DataStore needs one line. On iOS, the app's NSUserDefaults appear on their own.",
            snippet = "KmpInspector.attachDataStore(dataStore, name = \"settings\")",
            footnote = "Anything else: Inspector.setPreferences(file, controller)",
        )
        return
    }

    // Searching reads every value, so it reruns only when the files or the query change, not on
    // every redraw (a selection or an editor keystroke).
    val groups by remember(state) {
        derivedStateOf { groupStores(InspectorStore.preferences, state.query.trim()) }
    }
    val selected = all.firstOrNull { it.id == state.selectedPreferencesId }

    MasterDetail(
        pane = pane,
        hasSelection = selected != null,
        onBack = {
            state.selectedPreferencesId = null
            state.editingPreference = null
        },
        placeholder = "Select a preferences file",
        list = { StoreList(state, all, groups) },
        detail = { selected?.let { StoreDetail(it, state) } },
    )
}

/**
 * Stores whose name, or any key or value, contains [query], sorted by name. Searching a value
 * is the point: "which file holds this token?" is the usual question.
 */
internal fun filterStores(stores: List<AttachedPreferences>, query: String): List<AttachedPreferences> =
    stores
        .filter { s ->
            query.isEmpty() || s.file.name.contains(query, true) || s.file.entries.any { it.matches(query) }
        }
        .sortedBy { it.file.name.lowercase() }

/** The search result split into the app's own files and third-party SDK files, each sorted by name. */
internal class StoreGroups(val app: List<AttachedPreferences>, val libraries: List<AttachedPreferences>) {
    val isEmpty: Boolean get() = app.isEmpty() && libraries.isEmpty()
}

internal fun groupStores(stores: List<AttachedPreferences>, query: String): StoreGroups {
    val (libraries, app) = filterStores(stores, query).partition { it.file.isLibrary }
    return StoreGroups(app, libraries)
}

/** A store matched by its own name shows every key; otherwise only the keys that matched. */
internal fun visibleEntries(file: PreferenceFile, query: String, order: NameOrder): List<PreferenceEntry> {
    val matched = if (query.isEmpty() || file.name.contains(query, true)) {
        file.entries
    } else {
        file.entries.filter { it.matches(query) }
    }
    return if (order == NameOrder.Ascending) {
        matched.sortedBy { it.key.lowercase() }
    } else {
        matched.sortedByDescending { it.key.lowercase() }
    }
}

private fun PreferenceEntry.matches(query: String) =
    key.contains(query, true) || value.contains(query, true)

/**
 * The app's own files first, then the SDK files in a group that starts collapsed: collapsed rows
 * are never composed. A search shows matches in both groups, so nothing it finds is hidden.
 */
@Composable
private fun StoreList(state: InspectorState, all: List<AttachedPreferences>, groups: StoreGroups) {
    val searching = state.query.isNotBlank()
    val showLibraries = state.librariesExpanded || searching
    // Headers only earn their space when there is something to tell apart.
    val grouped = groups.libraries.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        if (groups.isEmpty) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                NoResults(
                    message = "Nothing matches \"${state.query.trim()}\"",
                    actionLabel = "Clear search",
                    onAction = { state.query = "" },
                )
            }
        } else {
            val listState = rememberLazyListState()
            val select: (AttachedPreferences) -> Unit = {
                state.selectedPreferencesId = it.id
                state.editingPreference = null
            }
            Box(Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (grouped && groups.app.isNotEmpty()) {
                        item(key = "header-app") { GroupHeader("Your app", groups.app.size) }
                    }
                    items(groups.app, key = { it.id }) { store ->
                        StoreRow(store, selected = store.id == state.selectedPreferencesId) { select(store) }
                        Hairline(color = DebugPalette.lineFaint)
                    }
                    if (grouped) {
                        item(key = "header-libraries") {
                            GroupHeader(
                                title = "Libraries & SDKs",
                                count = groups.libraries.size,
                                expanded = showLibraries,
                                onToggle = if (searching) null else {
                                    { state.librariesExpanded = !state.librariesExpanded }
                                },
                            )
                        }
                    }
                    if (showLibraries) {
                        items(groups.libraries, key = { it.id }) { store ->
                            StoreRow(store, selected = store.id == state.selectedPreferencesId) { select(store) }
                            Hairline(color = DebugPalette.lineFaint)
                        }
                    }
                }
                ScrollToTop(listState)
            }
        }
        Hairline()
        StatusLine(text = "${all.size} files · ${all.sumOf { it.file.entries.size }} keys") {
            PreferencesRefresh()
        }
    }
}

/**
 * A group label. With [onToggle] the whole row folds the group, and the chevron says which way
 * it is; without one (the app's group, or while searching) it is a plain label.
 */
@Composable
private fun GroupHeader(
    title: String,
    count: Int,
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (onToggle != null) 48.dp else 36.dp)
            .background(DebugPalette.surface)
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Kicker("$title · $count", Modifier.weight(1f))
        if (onToggle != null) {
            Text(
                if (expanded) "Hide" else "Show",
                modifier = Modifier.padding(end = 6.dp),
                style = InspectorType.mono(11.sp, FontWeight.Medium, DebugPalette.accent),
            )
            InspectorIcon(
                Glyph.ChevronRight,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                modifier = Modifier.rotate(if (expanded) 90f else 0f),
                size = 14.dp,
                tint = DebugPalette.accent,
            )
        }
    }
    Hairline(color = DebugPalette.lineFaint)
}

@Composable
private fun StoreRow(store: AttachedPreferences, selected: Boolean, onClick: () -> Unit) {
    val count = store.file.entries.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (selected) DebugPalette.selectionFill else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InspectorIcon(Glyph.Tune, null, size = 15.dp, tint = DebugPalette.textDim)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                store.file.name,
                style = InspectorType.mono(13.sp, color = DebugPalette.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                store.file.source,
                modifier = Modifier.padding(top = 2.dp),
                style = InspectorType.mono(10.5.sp, color = DebugPalette.textFaint),
                maxLines = 1,
            )
        }
        Text(
            count.toString(),
            style = InspectorType.mono(
                11.5.sp,
                color = if (count == 0) DebugPalette.textFaint else DebugPalette.textDim,
                tabular = true,
            ),
        )
        InspectorIcon(
            Glyph.ChevronRight, null,
            modifier = Modifier.padding(start = 8.dp),
            size = 14.dp, tint = DebugPalette.textFaint,
        )
    }
}

@Composable
private fun StoreDetail(store: AttachedPreferences, state: InspectorState) {
    val q = state.query.trim()
    // Filtering and sorting every key is redone only when the file, query or order changes.
    val entries = remember(store.file, q, state.preferenceSort) { visibleEntries(store.file, q, state.preferenceSort) }
    val total = store.file.entries.size
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                store.file.name,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                style = InspectorType.mono(12.5.sp, FontWeight.Medium, DebugPalette.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OutlineChip(store.file.source)
        }
        Hairline()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                total == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NoResults(message = "This file has no keys")
                }

                entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NoResults(
                        message = "No keys match \"$q\"",
                        actionLabel = "Clear search",
                        onAction = { state.query = "" },
                    )
                }

                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(entries, key = { it.key }) { entry ->
                            val open = state.editingPreference == PreferenceAddress(store.id, entry.key)
                            EntryRow(entry, selected = open) { openPreferenceEditor(state, store, entry) }
                            Hairline(color = DebugPalette.lineFaint)
                        }
                    }
                    ScrollToTop(listState)
                }
            }
        }
        Hairline()
        StatusLine(text = if (entries.size == total) "$total keys" else "${entries.size} of $total keys") {
            SortToggle(state.preferenceSort, onToggle = { state.preferenceSort = state.preferenceSort.flip() })
            Spacer(Modifier.width(8.dp))
            PreferencesRefresh()
        }
        state.editingPreference
            ?.takeIf { it.storeId == store.id }
            ?.let { address -> store.file.entries.firstOrNull { it.key == address.key } }
            ?.let { PreferenceSheet(store, it, state) }
    }
}

/** Key and type on one line, the value under it: keys are long and values longer. */
@Composable
private fun EntryRow(entry: PreferenceEntry, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) DebugPalette.selectionFill else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.key,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                style = InspectorType.mono(11.5.sp, color = DebugPalette.textDim),
            )
            Text(
                entry.type.label,
                style = InspectorType.mono(10.sp, color = DebugPalette.textFaint),
                maxLines = 1,
            )
        }
        if (entry.value.isEmpty()) {
            Text(
                "(empty)",
                modifier = Modifier.padding(top = 4.dp),
                style = InspectorType.mono(12.sp, color = DebugPalette.textFaint),
            )
        } else {
            Text(
                entry.value.take(VALUE_PREVIEW_CHARS),
                modifier = Modifier.padding(top = 4.dp),
                style = InspectorType.mono(12.sp, color = DebugPalette.text, lineHeight = 18.sp),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Opens for every entry, editable or not, because it is also where a long value is read in
 * full and copied. A sheet pinned to the bottom rather than an inline field, for the same reason
 * as the Database cell editor: a phone keyboard would cover the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferenceSheet(store: AttachedPreferences, entry: PreferenceEntry, state: InspectorState) {
    val clipboard = LocalClipboardManager.current
    val live = store.controller != null
    val editable = live && entry.type != PreferenceType.Other
    Column(
        Modifier
            .fillMaxWidth()
            .background(DebugPalette.surfaceRaised)
            .padding(16.dp),
    ) {
        Text(
            "${store.file.name} · ${entry.key} · ${entry.type.label}",
            style = InspectorType.mono(10.5.sp, color = DebugPalette.textFaint),
        )
        // A boolean has two possible values; two buttons beat typing "true" on a phone keyboard.
        if (!(editable && entry.type == PreferenceType.Boolean)) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(DebugPalette.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, DebugPalette.lineStrong, RoundedCornerShape(4.dp))
                    .padding(10.dp),
            ) {
                BasicTextField(
                    value = state.preferenceDraft,
                    onValueChange = {
                        state.preferenceDraft = it
                        state.preferenceError = null
                    },
                    readOnly = !editable,
                    textStyle = InspectorType.mono(12.sp, color = DebugPalette.text, lineHeight = 19.sp),
                    cursorBrush = SolidColor(DebugPalette.accent),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                )
            }
        }
        state.preferenceError?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 6.dp),
                style = InspectorType.mono(10.5.sp, color = DebugPalette.bad),
            )
        }
        FlowRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (editable && entry.type == PreferenceType.Boolean) {
                ActionPill("Set true", onClick = { commitPreference(state, store, entry, "true") }, accent = true)
                ActionPill("Set false", onClick = { commitPreference(state, store, entry, "false") }, accent = true)
            } else if (editable) {
                ActionPill("Save", onClick = { commitPreference(state, store, entry, state.preferenceDraft) }, accent = true)
            }
            if (live) ActionPill("Remove key", onClick = { removePreference(state, store, entry) })
            ActionPill("Copy", onClick = { clipboard.setText(AnnotatedString(entry.value)) })
            ActionPill("Close", onClick = { state.editingPreference = null })
        }
        Text(
            when {
                editable -> "Writes go straight to the app's ${store.file.source}."
                live -> "This type can't be edited here; it can be removed."
                else -> "Read-only: no live store attached."
            },
            modifier = Modifier.padding(top = 8.dp),
            style = InspectorType.mono(10.5.sp, color = if (live) DebugPalette.warn else DebugPalette.textFaint),
        )
    }
}

@Composable
private fun PreferencesRefresh() = RefreshPill(
    inFlight = { InspectorStore.preferencesRefreshing },
    settle = { InspectorStore.preferencesRefreshing = false },
    onRefresh = { InspectorStore.refreshPreferences() },
)

internal fun openPreferenceEditor(state: InspectorState, store: AttachedPreferences, entry: PreferenceEntry) {
    state.editingPreference = PreferenceAddress(store.id, entry.key)
    state.preferenceDraft = entry.value
    state.preferenceError = null
}

/**
 * Parses [text] as the entry's own type and writes it through the store's controller. Text that
 * does not parse stays in the sheet with an error, so a typo never changes a key's type.
 */
internal fun commitPreference(state: InspectorState, store: AttachedPreferences, entry: PreferenceEntry, text: String) {
    val controller = store.controller ?: return
    val value = parsePreferenceText(entry.type, text)
    if (value == null) {
        state.preferenceError = "Not a valid ${entry.type.label}"
        return
    }
    state.editingPreference = null
    state.preferenceError = null
    controller.update(store.file.name, entry.key, value)
}

internal fun removePreference(state: InspectorState, store: AttachedPreferences, entry: PreferenceEntry) {
    val controller = store.controller ?: return
    state.editingPreference = null
    controller.update(store.file.name, entry.key, null)
}
