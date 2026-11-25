package com.waqas028.kmpinspector

import android.app.Activity
import android.app.Application

/**
 * No-op twin of the real Android entry point. [attach] and [attachDataStore] take [Any] rather
 * than RoomDatabase and DataStore so this artifact needs neither on the classpath; call sites
 * passing the real types compile unchanged.
 */
@Suppress("UNUSED_PARAMETER")
object KmpInspector {
    fun install(
        application: Application,
        enabled: Boolean = false,
        appPackagePrefix: String = application.packageName,
        captureLogcat: Boolean = true,
        captureWorkManager: Boolean = true,
        capturePreferences: Boolean = true,
        excludeActivity: (Activity) -> Boolean = { false },
    ) = Unit

    fun attach(database: Any, fileName: String? = null) = Unit

    fun attachDataStore(dataStore: Any, name: String) = Unit
}
