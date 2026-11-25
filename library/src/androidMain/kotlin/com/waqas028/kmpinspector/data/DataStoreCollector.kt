package com.waqas028.kmpinspector.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.InspectorLog
import com.waqas028.kmpinspector.domain.model.PreferenceFile
import com.waqas028.kmpinspector.domain.model.PreferencesController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Mirrors a Preferences DataStore into the Preferences panel, live, and writes edits back through
 * it.
 *
 * The host hands over its own instance because DataStore allows only one per file in a process:
 * opening a second one on the same file throws, so the inspector never opens its own.
 *
 * DataStore is a compileOnly dependency; every `androidx.datastore` reference stays in this file,
 * and nothing here runs unless the host called `KmpInspector.attachDataStore`.
 */
internal object DataStoreCollector {

    private const val SOURCE = "DataStore"

    private val scope = CoroutineScope(SupervisorJob() + preferencesDispatcher)

    fun attach(dataStore: DataStore<Preferences>, name: String) {
        val controller = Controller(dataStore, name)
        dataStore.data
            // While the inspector is closed there is no one to show it to, and the next open
            // republishes through refresh() below anyway.
            .onEach { if (InspectorStore.inspectorOpen) controller.publish(it) }
            .catch { report("read $name", it) }
            .launchIn(scope)
        // The flow covers changes while it runs; this covers a cleared panel, or a flow that a
        // read error ended, the same way the database snapshots on open.
        Inspector.onOpen { controller.refresh() }
    }

    private class Controller(
        private val dataStore: DataStore<Preferences>,
        private val name: String,
    ) : PreferencesController {

        fun publish(prefs: Preferences) {
            val entries = prefs.asMap().map { (key, value) -> preferenceEntryOf(key.name, value) }
            Inspector.setPreferences(PreferenceFile(name, SOURCE, entries), this)
        }

        override fun refresh() {
            scope.launch {
                runCatching { publish(dataStore.data.first()) }
                    .onFailure {
                        InspectorStore.preferencesRefreshing = false
                        report("refresh $name", it)
                    }
            }
        }

        override fun update(file: String, key: String, value: Any?) {
            scope.launch {
                runCatching {
                    // Keys compare by name alone, so the typed key below also replaces or removes
                    // whatever the app stored under that name.
                    dataStore.edit { prefs ->
                        when (value) {
                            null -> prefs.remove(stringPreferencesKey(key))
                            is Boolean -> prefs[booleanPreferencesKey(key)] = value
                            is Int -> prefs[intPreferencesKey(key)] = value
                            is Long -> prefs[longPreferencesKey(key)] = value
                            is Float -> prefs[floatPreferencesKey(key)] = value
                            is Double -> prefs[doublePreferencesKey(key)] = value
                            is String -> prefs[stringPreferencesKey(key)] = value
                            is Set<*> -> prefs[stringSetPreferencesKey(key)] = value.map { it.toString() }.toSet()
                            else -> error("unsupported type ${value::class.simpleName}")
                        }
                    }
                    InspectorLog.i("Inspector", "$file.$key ${if (value == null) "removed" else "updated"}")
                }.onFailure { report("update $file.$key", it) }
            }
        }
    }

    private fun report(what: String, error: Throwable) {
        InspectorLog.e("Inspector", "DataStore $what failed: $error")
    }
}
