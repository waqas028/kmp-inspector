package com.waqas028.kmpinspector.data

import java.io.File

private fun file(): File? = inspectorContext()?.let { File(it.filesDir, "kmpinspector-crashes.txt") }

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
