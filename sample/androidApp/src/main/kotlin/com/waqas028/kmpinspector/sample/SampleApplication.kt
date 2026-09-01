package com.waqas028.kmpinspector.sample

import android.app.Application

/**
 * Room and WorkManager both need a Context, and the worker runs with no Activity around, so the
 * handover happens once here rather than from the UI.
 */
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeSampleOnAndroid(this)
    }
}
