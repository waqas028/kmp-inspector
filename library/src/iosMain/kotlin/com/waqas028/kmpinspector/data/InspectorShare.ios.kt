package com.waqas028.kmpinspector.data

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

internal actual object InspectorShare {
    actual val available: Boolean get() = true

    actual fun share(text: String, subject: String) {
        val root = rootViewController() ?: return
        val controller = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null,
        )
        // On iPad a share sheet without a source anchor raises an exception, so give it the
        // presenting view as its anchor.
        controller.popoverPresentationController?.sourceView = root.view
        root.presentViewController(controller, animated = true, completion = null)
    }

    private fun rootViewController(): UIViewController? {
        val application = UIApplication.sharedApplication
        val windows = application.windows.filterIsInstance<platform.UIKit.UIWindow>()
        return windows.firstOrNull { it.isKeyWindow() }?.rootViewController
            ?: windows.firstOrNull()?.rootViewController
    }
}
