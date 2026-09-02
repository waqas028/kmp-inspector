package com.waqas028.kmpinspector.data

/**
 * Hands text to the platform's share sheet.
 *
 * [available] is false where there is no such thing — desktop has no share sheet — so the UI can
 * hide the control rather than offer a button that does nothing.
 */
internal expect object InspectorShare {
    val available: Boolean
    fun share(text: String, subject: String)
}
