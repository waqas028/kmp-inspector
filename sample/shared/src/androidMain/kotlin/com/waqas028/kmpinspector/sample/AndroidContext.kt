package com.waqas028.kmpinspector.sample

import android.content.Context

private var appContext: Context? = null

/**
 * Room and WorkManager both need a Context, and the worker runs outside any composition, so the
 * app hands one over once from Application.onCreate.
 */
fun initializeSampleOnAndroid(context: Context) {
    appContext = context.applicationContext
}

internal fun requireAppContext(): Context = requireNotNull(appContext) {
    "Call initializeSampleOnAndroid(context) from Application.onCreate() first"
}
