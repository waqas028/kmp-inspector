package com.waqas028.kmpinspector.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KmpInspector Sample",
    ) {
        App()
    }
}
