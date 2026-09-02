package com.waqas028.kmpinspector.data

internal actual object InspectorShare {
    // Desktop has no share sheet. The UI hides the control rather than offering a dead button;
    // Copy trace already covers getting the text out.
    actual val available: Boolean get() = false

    actual fun share(text: String, subject: String) = Unit
}
