package com.waqas028.kmpinspector.presentation.preferences

import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.data.isLibraryPreferenceFile
import com.waqas028.kmpinspector.data.parsePreferenceText
import com.waqas028.kmpinspector.data.preferenceEntryOf
import com.waqas028.kmpinspector.domain.model.PreferenceEntry
import com.waqas028.kmpinspector.domain.model.PreferenceFile
import com.waqas028.kmpinspector.domain.model.PreferenceType
import com.waqas028.kmpinspector.domain.model.PreferencesController
import com.waqas028.kmpinspector.presentation.InspectorState
import com.waqas028.kmpinspector.presentation.InspectorTab
import com.waqas028.kmpinspector.presentation.NameOrder
import com.waqas028.kmpinspector.presentation.PreferenceAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreferencesTest {

    /** Records writes instead of making them. */
    private class FakeController : PreferencesController {
        val writes = mutableListOf<Triple<String, String, Any?>>()
        var refreshes = 0
        override fun refresh() { refreshes++ }
        override fun update(file: String, key: String, value: Any?) { writes += Triple(file, key, value) }
    }

    private val session = PreferenceFile(
        name = "session",
        source = "SharedPreferences",
        entries = listOf(
            PreferenceEntry("token", "abc123", PreferenceType.String),
            PreferenceEntry("onboarded", "true", PreferenceType.Boolean),
            PreferenceEntry("launches", "7", PreferenceType.Int),
        ),
    )
    private val theme = PreferenceFile(
        name = "theme",
        source = "SharedPreferences",
        entries = listOf(PreferenceEntry("mode", "dark", PreferenceType.String)),
    )

    @BeforeTest
    fun setUp() {
        Inspector.enabled = true
        InspectorStore.clear()
    }

    @AfterTest
    fun tearDown() = InspectorStore.clear()

    // ---- Store ----------------------------------------------------------------------------

    @Test
    fun a_file_with_the_same_source_and_name_is_replaced_not_added() {
        Inspector.setPreferences(session)
        Inspector.setPreferences(theme)
        val updated = session.copy(entries = session.entries.take(1))
        Inspector.setPreferences(updated)

        assertEquals(listOf("session", "theme"), InspectorStore.preferences.map { it.file.name })
        assertEquals(updated, InspectorStore.preferences.first().file)
    }

    @Test
    fun the_same_name_from_another_source_is_a_separate_store() {
        Inspector.setPreferences(session)
        Inspector.setPreferences(session.copy(source = "DataStore"))

        assertEquals(2, InspectorStore.preferences.size)
    }

    @Test
    fun retain_drops_only_vanished_files_of_that_source() {
        Inspector.setPreferences(session)
        Inspector.setPreferences(theme)
        Inspector.setPreferences(theme.copy(source = "DataStore"))

        InspectorStore.retainPreferences("SharedPreferences", setOf("theme"))

        assertEquals(
            listOf("SharedPreferences/theme", "DataStore/theme"),
            InspectorStore.preferences.map { it.id },
        )
    }

    @Test
    fun a_repeated_key_is_kept_once_so_the_list_cannot_crash() {
        val twice = session.copy(entries = session.entries + PreferenceEntry("token", "other", PreferenceType.String))

        Inspector.setPreferences(twice)

        val stored = InspectorStore.preferences.single().file.entries
        assertEquals(listOf("token", "onboarded", "launches"), stored.map { it.key })
        assertEquals("abc123", stored.first().value)
    }

    @Test
    fun an_unchanged_snapshot_is_not_written_again() {
        Inspector.setPreferences(session)
        val first = InspectorStore.preferences.single()

        Inspector.setPreferences(session.copy())

        assertSame(first, InspectorStore.preferences.single())
    }

    @Test
    fun refresh_asks_each_live_store_once() {
        val shared = FakeController()
        Inspector.setPreferences(session, shared)
        Inspector.setPreferences(theme, shared)

        InspectorStore.refreshPreferences()

        assertEquals(1, shared.refreshes)
    }

    // ---- Values ---------------------------------------------------------------------------

    @Test
    fun stored_values_keep_their_type() {
        assertEquals(PreferenceType.Boolean, preferenceEntryOf("a", true).type)
        assertEquals(PreferenceType.Int, preferenceEntryOf("a", 1).type)
        assertEquals(PreferenceType.Long, preferenceEntryOf("a", 1L).type)
        assertEquals(PreferenceType.Float, preferenceEntryOf("a", 1f).type)
        assertEquals(PreferenceType.Double, preferenceEntryOf("a", 1.0).type)
        assertEquals(PreferenceType.String, preferenceEntryOf("a", "x").type)
        assertEquals(PreferenceType.Other, preferenceEntryOf("a", byteArrayOf(1, 2)).type)
        assertEquals("2 bytes", preferenceEntryOf("a", byteArrayOf(1, 2)).value)
    }

    @Test
    fun a_string_set_is_sorted_one_item_per_line() {
        val entry = preferenceEntryOf("tags", setOf("b", "a", "c"))

        assertEquals(PreferenceType.StringSet, entry.type)
        assertEquals("a\nb\nc", entry.value)
    }

    @Test
    fun editor_text_parses_as_the_entry_type_or_not_at_all() {
        assertEquals(true, parsePreferenceText(PreferenceType.Boolean, " True "))
        assertNull(parsePreferenceText(PreferenceType.Boolean, "yes"))
        assertEquals(42, parsePreferenceText(PreferenceType.Int, "42"))
        assertNull(parsePreferenceText(PreferenceType.Int, "4.2"))
        assertEquals(9_000_000_000L, parsePreferenceText(PreferenceType.Long, "9000000000"))
        assertEquals(1.5f, parsePreferenceText(PreferenceType.Float, "1.5"))
        assertEquals(" keep spaces ", parsePreferenceText(PreferenceType.String, " keep spaces "))
        assertEquals(setOf("a", "b"), parsePreferenceText(PreferenceType.StringSet, "a\n\nb"))
        assertNull(parsePreferenceText(PreferenceType.Other, "anything"))
    }

    // ---- Search ---------------------------------------------------------------------------

    @Test
    fun search_finds_the_file_that_holds_a_value() {
        Inspector.setPreferences(session)
        Inspector.setPreferences(theme)

        val found = filterStores(InspectorStore.preferences, "abc")

        assertEquals(listOf("session"), found.map { it.file.name })
        assertEquals(listOf("token"), visibleEntries(session, "abc", NameOrder.Ascending).map { it.key })
    }

    @Test
    fun a_file_matched_by_name_shows_all_its_keys_sorted() {
        val keys = visibleEntries(session, "sess", NameOrder.Descending).map { it.key }

        assertEquals(listOf("token", "onboarded", "launches"), keys)
    }

    // ---- App or library ------------------------------------------------------------------

    private val app = listOf("com.dil.ka.rishta.staging")

    @Test
    fun plain_names_and_the_apps_own_package_are_the_app() {
        assertFalse(isLibraryPreferenceFile("install_referrer", app))
        assertFalse(isLibraryPreferenceFile("Dil Ka Rishta", app))
        assertFalse(isLibraryPreferenceFile("user.settings", app))
        assertFalse(isLibraryPreferenceFile("com.dil.ka.rishta.staging_preferences", app))
    }

    @Test
    fun sdk_packages_and_brand_names_are_libraries() {
        assertTrue(isLibraryPreferenceFile("com.google.android.gms.measurement.prefs", app))
        assertTrue(isLibraryPreferenceFile("com.facebook.sdk.appEventPreferences", app))
        assertTrue(isLibraryPreferenceFile("androidx.work.util.preferences", app))
        assertTrue(isLibraryPreferenceFile("FirebaseHeartBeatW0RFRkFVTFRd+MTo1", app))
        assertTrue(isLibraryPreferenceFile("WebViewChromiumPrefs", app))
        assertTrue(isLibraryPreferenceFile("admob", app))
    }

    @Test
    fun the_app_group_comes_first_and_a_search_reaches_both() {
        val sdk = PreferenceFile(
            name = "com.google.android.gms.measurement.prefs",
            source = "SharedPreferences",
            entries = listOf(PreferenceEntry("app_instance_id", "abc999", PreferenceType.String)),
            isLibrary = true,
        )
        Inspector.setPreferences(sdk)
        Inspector.setPreferences(theme)
        Inspector.setPreferences(session)

        val all = groupStores(InspectorStore.preferences, "")
        assertEquals(listOf("session", "theme"), all.app.map { it.file.name })
        assertEquals(listOf(sdk.name), all.libraries.map { it.file.name })

        val searched = groupStores(InspectorStore.preferences, "abc")
        assertEquals(listOf("session"), searched.app.map { it.file.name })
        assertEquals(listOf(sdk.name), searched.libraries.map { it.file.name })
    }

    // ---- Editing --------------------------------------------------------------------------

    @Test
    fun a_valid_edit_is_written_with_its_type() {
        val controller = FakeController()
        Inspector.setPreferences(session, controller)
        val store = InspectorStore.preferences.single()
        val state = InspectorState()
        openPreferenceEditor(state, store, session.entries[2])

        commitPreference(state, store, session.entries[2], "8")

        assertEquals(listOf(Triple("session", "launches", 8 as Any?)), controller.writes)
        assertNull(state.editingPreference)
    }

    @Test
    fun an_invalid_edit_stays_open_with_an_error_and_writes_nothing() {
        val controller = FakeController()
        Inspector.setPreferences(session, controller)
        val store = InspectorStore.preferences.single()
        val state = InspectorState()
        openPreferenceEditor(state, store, session.entries[2])

        commitPreference(state, store, session.entries[2], "eight")

        assertTrue(controller.writes.isEmpty())
        assertEquals("Not a valid int", state.preferenceError)
        assertEquals(PreferenceAddress(store.id, "launches"), state.editingPreference)
    }

    @Test
    fun removing_a_key_writes_null() {
        val controller = FakeController()
        Inspector.setPreferences(session, controller)
        val store = InspectorStore.preferences.single()

        removePreference(InspectorState(), store, session.entries[0])

        assertEquals(listOf(Triple("session", "token", null as Any?)), controller.writes)
    }

    @Test
    fun back_closes_the_editor_before_the_file() {
        val state = InspectorState()
        state.selectTab(InspectorTab.Preferences)
        state.selectedPreferencesId = "SharedPreferences/session"
        state.editingPreference = PreferenceAddress("SharedPreferences/session", "token")

        assertTrue(state.handleBack())
        assertNull(state.editingPreference)
        assertEquals("SharedPreferences/session", state.selectedPreferencesId)
        assertTrue(state.handleBack())
        assertNull(state.selectedPreferencesId)
    }
}
