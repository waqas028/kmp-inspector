package com.waqas028.kmpinspector.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.res.painterResource

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KmpInspector Sample",
        icon = painterResource("app-icon.png"),
    ) {
        App()
    }
}
