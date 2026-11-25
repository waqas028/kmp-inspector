package com.waqas028.kmpinspector.domain.model

/**
 * What a preference holds. Kept with the value so an edit writes the same type back: a
 * SharedPreferences `getInt` on a key that was rewritten as a string throws in the host app.
 */
enum class PreferenceType { String, Boolean, Int, Long, Float, Double, StringSet, Other }

data class PreferenceEntry(
    val key: String,
    /** The value as text. A string set is one item per line. */
    val value: String,
    val type: PreferenceType,
)

/**
 * One key-value store in the Preferences panel: a SharedPreferences file, a DataStore, or the
 * app's NSUserDefaults.
 */
data class PreferenceFile(
    /** e.g. `app_session` for SharedPreferences, `settings` for a DataStore. */
    val name: String,
    /** Where it lives, shown beside the name: "SharedPreferences", "DataStore", "NSUserDefaults". */
    val source: String,
    val entries: List<PreferenceEntry>,
    /**
     * True for a file written by a third-party SDK (Firebase, Facebook, WebView…) rather than the
     * app. The panel lists the app's own files first and folds these into a collapsed group.
     */
    val isLibrary: Boolean = false,
)

/**
 * The live store behind a [PreferenceFile]. Optional: without one the panel is read-only.
 *
 * Both calls may do their work on a background thread; they should end by handing a fresh
 * snapshot to `Inspector.setPreferences` so the panel shows the result.
 */
interface PreferencesController {
    /** Re-read and republish. Bound to the Refresh button. */
    fun refresh()

    /**
     * Writes [value] under [key] in the file named [file]. [value] is a Boolean, Int, Long,
     * Float, Double, String or Set<String>, matching the entry's [PreferenceType]; null removes
     * the key.
     */
    fun update(file: String, key: String, value: Any?)
}
