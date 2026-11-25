package com.waqas028.kmpinspector.data

/**
 * Starts reading the platform's own key-value store, if it has one the inspector can find without
 * help: SharedPreferences on Android (when a Context was handed over), NSUserDefaults on iOS.
 * Called once from the `KmpInspector { }` composable; safe to call again.
 */
internal expect fun startPlatformPreferences()
