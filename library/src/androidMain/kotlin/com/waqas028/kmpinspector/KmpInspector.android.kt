package com.waqas028.kmpinspector

import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.data.DataStoreCollector
import com.waqas028.kmpinspector.data.LogcatCollector
import com.waqas028.kmpinspector.data.OverlayInjector
import com.waqas028.kmpinspector.data.RoomCollector
import com.waqas028.kmpinspector.data.SharedPreferencesCollector
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
 * - floats the bubble over **every** Activity, XML or Compose, without wrapping anything. Screens
 *   that show no content of their own, such as a library's browser hand-off, are left alone;
 * - captures fatal crashes and keeps them across restarts;
 * - streams this process's logcat into the Logs panel;
 * - mirrors WorkManager's job list into the Background Work panel when WorkManager is on the
 *   classpath;
 * - shows every SharedPreferences file in the Preferences panel, read when the inspector opens.
 *
 * Three things cannot be discovered from the outside and stay one line each: add
 * [com.waqas028.kmpinspector.okhttp.KmpInspectorInterceptor] to your OkHttp client, call
 * [attach] with your Room database, and call [attachDataStore] with each Preferences DataStore.
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
        capturePreferences: Boolean = true,
        /** Return true for Activities that should not get the bubble, e.g. a splash screen. */
        excludeActivity: (Activity) -> Boolean = { false },
    ) {
        if (!enabled) {
            // Records the decision globally, so a later attach() or interceptor stays quiet too.
            Inspector.enabled = false
            return
        }
        if (installed) return
        installed = true
        // Before anything composes: the overlay would otherwise start the reader on its own.
        SharedPreferencesCollector.optedOut = !capturePreferences

        initializeInspector(application)
        Inspector.configure(appId = application.packageName, variant = "debug")
        Inspector.installCrashHandler(appPackagePrefix)
        InspectorStore.restoreCrashes()

        OverlayInjector(excludeActivity).install(application)
        if (captureLogcat) LogcatCollector.start()
        if (captureWorkManager && WorkManagerCollector.isAvailable()) {
            WorkManagerCollector.start(application)
        }
        if (capturePreferences) SharedPreferencesCollector.start(application, appPackagePrefix)
    }

    /**
     * Shows [database]'s tables in the Database panel. A snapshot is taken now and again every
     * time the inspector is opened, so the rows are current as of the tap. Needs Room 2.7 or newer.
     *
     * [fileName] is only used for the header label; Room's KMP builder does not expose it.
     */
    fun attach(database: RoomDatabase, fileName: String? = null) {
        // Reading every table is real work on real data, so it is skipped outright when the
        // inspector is off rather than done and then discarded.
        if (!Inspector.enabled) return
        RoomCollector.attach(database, fileName)
    }

    /**
     * Shows [dataStore]'s keys in the Preferences panel, live, and writes edits made there back
     * through it. [name] labels it in the list; the file name you gave `preferencesDataStore` is
     * a good choice, since DataStore does not expose it.
     *
     * Passed in rather than found on disk: DataStore allows one instance per file in a process,
     * so the inspector has to reuse yours. Needs `androidx.datastore:datastore-preferences`.
     * Proto DataStore is not supported, because reading it needs your own schema.
     */
    fun attachDataStore(dataStore: DataStore<Preferences>, name: String) {
        if (!Inspector.enabled) return
        DataStoreCollector.attach(dataStore, name)
    }

    private fun Application.isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
