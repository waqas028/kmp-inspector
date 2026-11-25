package com.waqas028.kmpinspector.data

import com.waqas028.kmpinspector.domain.model.PreferenceEntry
import com.waqas028.kmpinspector.domain.model.PreferenceType

/**
 * Turns a raw stored value into an entry. Shared by SharedPreferences and DataStore, which hand
 * back the same plain Kotlin types.
 */
internal fun preferenceEntryOf(key: String, value: Any?): PreferenceEntry = when (value) {
    is Boolean -> PreferenceEntry(key, value.toString(), PreferenceType.Boolean)
    is Int -> PreferenceEntry(key, value.toString(), PreferenceType.Int)
    is Long -> PreferenceEntry(key, value.toString(), PreferenceType.Long)
    is Float -> PreferenceEntry(key, value.toString(), PreferenceType.Float)
    is Double -> PreferenceEntry(key, value.toString(), PreferenceType.Double)
    is String -> PreferenceEntry(key, value, PreferenceType.String)
    // Sorted, because a set has no order of its own and an unsorted one reshuffles on every read.
    is Set<*> -> PreferenceEntry(
        key,
        value.map { it.toString() }.sorted().joinToString("\n"),
        PreferenceType.StringSet,
    )
    is ByteArray -> PreferenceEntry(key, "${value.size} bytes", PreferenceType.Other)
    null -> PreferenceEntry(key, "null", PreferenceType.Other)
    else -> PreferenceEntry(key, value.toString(), PreferenceType.Other)
}

/**
 * Reads the editor's text as [type], or null when it does not parse. The result is what
 * [com.waqas028.kmpinspector.domain.model.PreferencesController.update] expects.
 */
internal fun parsePreferenceText(type: PreferenceType, text: String): Any? = when (type) {
    PreferenceType.String -> text
    PreferenceType.Boolean -> text.trim().lowercase().toBooleanStrictOrNull()
    PreferenceType.Int -> text.trim().toIntOrNull()
    PreferenceType.Long -> text.trim().toLongOrNull()
    PreferenceType.Float -> text.trim().toFloatOrNull()
    PreferenceType.Double -> text.trim().toDoubleOrNull()
    PreferenceType.StringSet -> text.split('\n').filter { it.isNotEmpty() }.toSet()
    PreferenceType.Other -> null
}

/** Lowercase type name for the UI, e.g. `int`, `string set`. */
internal val PreferenceType.label: String
    get() = when (this) {
        PreferenceType.StringSet -> "string set"
        else -> name.lowercase()
    }
