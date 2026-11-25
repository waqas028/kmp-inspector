package com.waqas028.kmpinspector.data

import kotlinx.coroutines.Dispatchers

/**
 * One thread for every preferences read and write, so a SharedPreferences scan and a DataStore
 * update never publish into the store at the same moment.
 */
internal val preferencesDispatcher = Dispatchers.IO.limitedParallelism(1)

/**
 * Compose Multiplatform apps reach Android through the `KmpInspector { }` composable and
 * `initializeInspector(context)` rather than `install()`; this gives them SharedPreferences too.
 */
internal actual fun startPlatformPreferences() {
    inspectorContext()?.let(SharedPreferencesCollector::start)
}
