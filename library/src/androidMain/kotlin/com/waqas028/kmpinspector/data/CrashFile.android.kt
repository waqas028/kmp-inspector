package com.waqas028.kmpinspector.data

import android.content.Context
import java.io.File

private var appContext: Context? = null

/**
 * Gives the inspector somewhere to persist crashes. Without it the library still works, but the
 * crash buffer is in-memory only and dies with the process.
 */
fun initializeInspectorStorage(context: Context) {
    appContext = context.applicationContext
}

private fun file(): File? = appContext?.let { File(it.filesDir, "kmpinspector-crashes.txt") }

internal actual object CrashFile {
    actual fun read(): String? = file()?.takeIf { it.exists() }?.readText()

    actual fun write(contents: String) {
        val target = file() ?: return
        // Write to a temp file and rename: a crash during the write leaves the previous file intact
        // rather than a half-written one.
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(contents)
        tmp.renameTo(target)
    }

    actual fun clear() {
        file()?.delete()
    }
}
