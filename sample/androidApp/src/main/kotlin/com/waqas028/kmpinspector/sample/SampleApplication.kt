package com.waqas028.kmpinspector.sample

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.waqas028.kmpinspector.KmpInspector
import com.waqas028.kmpinspector.data.initializeInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The app's one Preferences DataStore. DataStore allows a single instance per file. */
private val Context.settings by preferencesDataStore(name = "settings")

/**
 * Room and WorkManager both need a Context, and the worker runs with no Activity around, so the
 * handover happens once here rather than from the UI.
 */
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeSampleOnAndroid(this)
        // Lets the inspector persist crashes across process death, and enables sharing. It also
        // gives the inspector what it needs to read SharedPreferences on its own.
        initializeInspector(this)
        seedPreferences()
        KmpInspector.attachDataStore(settings, name = "settings")
    }

    /**
     * A few values so the Preferences panel has something to show. Written once only, so edits
     * made in the inspector survive a restart.
     */
    private fun seedPreferences() {
        val session = getSharedPreferences("session", MODE_PRIVATE)
        if (!session.contains("user_name")) {
            session.edit()
                .putString("user_name", "Momo")
                .putBoolean("onboarded", true)
                .putInt("launch_count", 1)
                .putStringSet("topics", setOf("launches", "rockets"))
                .apply()
        }
        CoroutineScope(Dispatchers.IO).launch {
            settings.edit { prefs ->
                if (stringPreferencesKey("theme") !in prefs) {
                    prefs[stringPreferencesKey("theme")] = "dark"
                    prefs[intPreferencesKey("articles_per_page")] = 20
                }
            }
        }
    }
}
