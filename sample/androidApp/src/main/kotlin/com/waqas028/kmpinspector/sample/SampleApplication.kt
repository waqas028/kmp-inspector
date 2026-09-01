package com.waqas028.kmpinspector.sample

import android.app.Application
import com.waqas028.kmpinspector.data.initializeInspectorStorage

/**
 * Room and WorkManager both need a Context, and the worker runs with no Activity around, so the
 * handover happens once here rather than from the UI.
 */
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeSampleOnAndroid(this)
        // Lets the inspector persist crashes across process death.
        initializeInspectorStorage(this)
    }
}
