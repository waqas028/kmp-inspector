package com.waqas028.kmpinspector.data

import android.content.Intent

internal actual object InspectorShare {
    actual val available: Boolean get() = inspectorContext() != null

    actual fun share(text: String, subject: String) {
        val context = inspectorContext() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        // Started from the application context, so the chooser needs its own task.
        val chooser = Intent.createChooser(send, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }
}
