package com.waqas028.kmpinspector

import android.app.Activity
import android.app.Application

/**
 * No-op twin of the real Android entry point. [attach] takes [Any] rather than RoomDatabase so
 * this artifact needs no Room on the classpath; call sites passing a RoomDatabase compile
 * unchanged.
 */
@Suppress("UNUSED_PARAMETER")
object KmpInspector {
    fun install(
        application: Application,
        enabled: Boolean = false,
        appPackagePrefix: String = application.packageName,
        captureLogcat: Boolean = true,
        captureWorkManager: Boolean = true,
        excludeActivity: (Activity) -> Boolean = { false },
    ) = Unit

    fun attach(database: Any, fileName: String? = null) = Unit
}
