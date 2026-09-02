package com.waqas028.kmpinspector.data

import android.content.Context

private var appContext: Context? = null

/**
 * Gives the inspector a Context. Without it the library still runs, but crashes are not persisted
 * across process death and the share sheet is unavailable.
 */
fun initializeInspector(context: Context) {
    appContext = context.applicationContext
}

internal fun inspectorContext(): Context? = appContext
