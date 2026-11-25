package com.waqas028.kmpinspector.data

import android.content.Context
import android.content.SharedPreferences
import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.InspectorLog
import com.waqas028.kmpinspector.domain.model.PreferenceFile
import com.waqas028.kmpinspector.domain.model.PreferencesController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Shows every SharedPreferences file of the app in the Preferences panel, with no setup.
 *
 * Android keeps them all as `shared_prefs/<name>.xml` in the app's data directory, so the folder is
 * listed and each file opened through the normal `getSharedPreferences(name)`. That returns the
 * same instance the app holds, so reads see unsaved `apply()` writes and edits reach the app's
 * own listeners.
 *
 * Performance: nothing is read at startup, the first scan runs when the inspector is opened.
 * After that, writes the app makes while the inspector is closed cost nothing beyond a flag
 * check, because the next open rescans anyway; while it is open, bursts of writes to one file
 * are folded into a single republish.
 *
 * Each file is also labelled as the app's or a third-party SDK's, by name; see
 * [isLibraryPreferenceFile].
 */
internal object SharedPreferencesCollector : PreferencesController {

    private const val SOURCE = "SharedPreferences"

    /** Long enough to fold a burst of `putX()` calls into one republish, short enough to feel live. */
    private const val PUBLISH_DEBOUNCE_MS = 250L

    private val scope = CoroutineScope(SupervisorJob() + preferencesDispatcher)

    @Volatile
    private var context: Context? = null

    /** The application id, plus the source package from `install` when a flavor suffix differs. */
    @Volatile
    private var appPackages: List<String> = emptyList()

    /** Library-or-app per file name; a name never changes owner, so it is worked out once. */
    private val isLibrary = ConcurrentHashMap<String, Boolean>()

    /** Files with a republish already scheduled, so a burst of writes schedules only one. */
    private val pending: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Set by `install(capturePreferences = false)`, which must win over the composable's start. */
    @Volatile
    var optedOut = false

    /**
     * SharedPreferences keeps change listeners in a weak map, so a listener nobody else
     * references stops firing at the next GC. Held here for as long as the file exists.
     */
    private val listeners = ConcurrentHashMap<String, Pair<SharedPreferences, SharedPreferences.OnSharedPreferenceChangeListener>>()

    @Synchronized
    fun start(context: Context, appPackagePrefix: String? = null) {
        if (optedOut || this.context != null) return
        this.context = context.applicationContext
        appPackages = listOfNotNull(context.packageName, appPackagePrefix).distinct()
        Inspector.onOpen { refresh() }
    }

    override fun refresh() {
        scope.launch { runCatching { scan() }.onFailure { report("read", it) } }
    }

    override fun update(file: String, key: String, value: Any?) {
        scope.launch {
            runCatching {
                val prefs = prefs(file) ?: return@launch
                val editor = prefs.edit()
                when (value) {
                    null -> editor.remove(key)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
                    else -> error("unsupported type ${value::class.simpleName}")
                }
                // commit, not apply: this runs off the main thread already, and the republish
                // below has to read the written value back.
                editor.commit()
                InspectorLog.i("Inspector", "$file.$key ${if (value == null) "removed" else "updated"}")
                publish(file, prefs)
            }.onFailure { report("update $file.$key", it) }
        }
    }

    private fun scan() {
        val ctx = context ?: return
        val names = File(ctx.applicationInfo.dataDir, "shared_prefs")
            .listFiles { f -> f.isFile && f.name.endsWith(".xml") }
            .orEmpty()
            .map { it.name.removeSuffix(".xml") }
            .toSet()
        names.forEach { name -> prefs(name)?.let { publish(name, it) } }
        InspectorStore.retainPreferences(SOURCE, names)
        (listeners.keys - names).forEach { name ->
            listeners.remove(name)?.let { (prefs, listener) -> prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    private fun prefs(name: String): SharedPreferences? =
        context?.getSharedPreferences(name, Context.MODE_PRIVATE)?.also { watch(name, it) }

    /** Republishes a file whenever the app writes to it, so an open panel stays current. */
    private fun watch(name: String, prefs: SharedPreferences) {
        if (listeners.containsKey(name)) return
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            schedulePublish(name, prefs)
        }
        if (listeners.putIfAbsent(name, prefs to listener) == null) {
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }
    }

    /** Runs on the main thread, inside the app's own write, so it only checks and schedules. */
    private fun schedulePublish(name: String, prefs: SharedPreferences) {
        if (!InspectorStore.inspectorOpen) return
        if (!pending.add(name)) return
        scope.launch {
            delay(PUBLISH_DEBOUNCE_MS)
            // Cleared before reading, so a write that lands during the read schedules another.
            pending.remove(name)
            runCatching { publish(name, prefs) }
        }
    }

    private fun publish(name: String, prefs: SharedPreferences) {
        val entries = prefs.all.map { (key, value) -> preferenceEntryOf(key, value) }
        val library = isLibrary.getOrPut(name) { isLibraryPreferenceFile(name, appPackages) }
        Inspector.setPreferences(PreferenceFile(name, SOURCE, entries, isLibrary = library), this)
    }

    private fun report(what: String, error: Throwable) {
        InspectorLog.e("Inspector", "SharedPreferences $what failed: $error")
    }
}
