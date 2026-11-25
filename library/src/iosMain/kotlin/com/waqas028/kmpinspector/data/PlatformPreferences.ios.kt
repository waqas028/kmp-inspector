package com.waqas028.kmpinspector.data

import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.InspectorLog
import com.waqas028.kmpinspector.domain.model.PreferenceEntry
import com.waqas028.kmpinspector.domain.model.PreferenceFile
import com.waqas028.kmpinspector.domain.model.PreferenceType
import com.waqas028.kmpinspector.domain.model.PreferencesController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

internal actual fun startPlatformPreferences() = UserDefaultsCollector.start()

/**
 * Shows the app's own NSUserDefaults in the Preferences panel, with no setup.
 *
 * Only the app's persistent domain is read, i.e. what the app itself stored. The full
 * `dictionaryRepresentation()` also carries dozens of system keys (AppleLanguages and the like)
 * that would bury the app's few.
 */
internal object UserDefaultsCollector : PreferencesController {

    private const val SOURCE = "NSUserDefaults"

    private var started = false

    fun start() {
        if (started) return
        started = true
        // Read when the inspector opens, like the other snapshot sources. NSUserDefaults is an
        // in-memory cache, so this is cheap enough for the main thread.
        Inspector.onOpen { refresh() }
    }

    private val domain: String? get() = NSBundle.mainBundle.bundleIdentifier

    override fun refresh() {
        runCatching {
            val domain = domain ?: return
            val stored = NSUserDefaults.standardUserDefaults.persistentDomainForName(domain).orEmpty()
            val entries = stored.mapNotNull { (key, value) -> (key as? String)?.let { entryOf(it, value) } }
            Inspector.setPreferences(PreferenceFile(domain, SOURCE, entries), this)
        }.onFailure { InspectorLog.e("Inspector", "NSUserDefaults read failed: $it") }
    }

    override fun update(file: String, key: String, value: Any?) {
        val defaults = NSUserDefaults.standardUserDefaults
        when (value) {
            null -> defaults.removeObjectForKey(key)
            is Boolean -> defaults.setBool(value, key)
            is Long -> defaults.setInteger(value, key)
            is Int -> defaults.setInteger(value.toLong(), key)
            is Double -> defaults.setDouble(value, key)
            is Float -> defaults.setFloat(value, key)
            is String -> defaults.setObject(value, key)
            is Set<*> -> defaults.setObject(value.map { it.toString() }, key)
            else -> return
        }
        InspectorLog.i("Inspector", "$file.$key ${if (value == null) "removed" else "updated"}")
        refresh()
    }

    /**
     * Property-list values arrive as Foundation objects. Strings bridge to Kotlin on their own;
     * numbers stay NSNumber, and its type code is the only way to tell a BOOL from an integer.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun entryOf(key: String, value: Any?): PreferenceEntry = when (value) {
        is NSNumber -> when (value.objCType?.toKString()) {
            "c", "B" -> PreferenceEntry(key, value.boolValue.toString(), PreferenceType.Boolean)
            "f", "d" -> PreferenceEntry(key, value.doubleValue.toString(), PreferenceType.Double)
            else -> PreferenceEntry(key, value.longLongValue.toString(), PreferenceType.Long)
        }
        is NSData -> PreferenceEntry(key, "${value.length} bytes", PreferenceType.Other)
        // Arrays and dictionaries bridge to List and Map; shown as text, not edited.
        is List<*> -> PreferenceEntry(key, value.joinToString("\n"), PreferenceType.Other)
        else -> preferenceEntryOf(key, value)
    }
}
