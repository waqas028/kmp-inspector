package com.waqas028.kmpinspector

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.room.RoomDatabase
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.data.LogcatCollector
import com.waqas028.kmpinspector.data.OverlayInjector
import com.waqas028.kmpinspector.data.RoomCollector
import com.waqas028.kmpinspector.data.WorkManagerCollector
import com.waqas028.kmpinspector.data.initializeInspector

/**
 * One-call Android setup. Everything the inspector can collect on its own is switched on here, so a
 * host app does not need to wrap any UI or write any plumbing:
 *
 * ```
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         KmpInspector.install(this)
 *     }
 * }
 * ```
 *
 * What it does:
 * - floats the bubble over **every** Activity, XML or Compose, without wrapping anything;
 * - captures fatal crashes and keeps them across restarts;
 * - streams this process's logcat into the Logs panel;
 * - mirrors WorkManager's job list into the Background Work panel when WorkManager is on the
 *   classpath.
 *
 * Two things cannot be discovered from the outside and stay one line each: add
 * [com.waqas028.kmpinspector.okhttp.KmpInspectorInterceptor] to your OkHttp client, and call
 * [attach] with your Room database.
 *
 * [enabled] defaults to the manifest's `debuggable` flag, so release builds get nothing installed.
 * Do not also wrap your Compose content in the `KmpInspector { }` composable on Android when using
 * this, or you will see two bubbles.
 */
object KmpInspector {

    @Volatile
    private var installed = false

    fun install(
        application: Application,
        enabled: Boolean = application.isDebuggable(),
        appPackagePrefix: String = application.packageName,
        captureLogcat: Boolean = true,
        captureWorkManager: Boolean = true,
        /** Return true for Activities that should not get the bubble, e.g. a splash screen. */
        excludeActivity: (Activity) -> Boolean = { false },
    ) {
        if (!enabled || installed) return
        installed = true

        initializeInspector(application)
        Inspector.configure(appId = application.packageName, variant = "debug")
        Inspector.installCrashHandler(appPackagePrefix)
        InspectorStore.restoreCrashes()

        OverlayInjector(excludeActivity).install(application)
        if (captureLogcat) LogcatCollector.start()
        if (captureWorkManager && WorkManagerCollector.isAvailable()) {
            WorkManagerCollector.start(application)
        }
    }

    /**
     * Shows [database]'s tables in the Database panel. A snapshot is taken now and again every
     * time the inspector is opened, so the rows are current as of the tap. Needs Room 2.7 or newer.
     *
     * [fileName] is only used for the header label; Room's KMP builder does not expose it.
     */
    fun attach(database: RoomDatabase, fileName: String? = null) {
        RoomCollector.attach(database, fileName)
    }

    private fun Application.isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
