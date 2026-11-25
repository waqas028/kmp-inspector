package com.waqas028.kmpinspector.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

@Suppress("unused", "FunctionName") // called from Swift in iosApp/ContentView.swift
fun MainViewController(): UIViewController {
    seedUserDefaults()
    return ComposeUIViewController { App() }
}

/**
 * A few values so the Preferences panel has something to show; the inspector reads the app's
 * NSUserDefaults on its own. Written once only, so edits made in the inspector survive a restart.
 */
private fun seedUserDefaults() {
    val defaults = NSUserDefaults.standardUserDefaults
    if (defaults.objectForKey("user_name") != null) return
    defaults.setObject("Momo", "user_name")
    defaults.setBool(true, "onboarded")
    defaults.setInteger(1, "launch_count")
    defaults.setDouble(1.25, "font_scale")
}
