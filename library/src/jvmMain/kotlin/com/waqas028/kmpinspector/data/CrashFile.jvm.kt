package com.waqas028.kmpinspector.data

import java.io.File

private val file: File by lazy {
    // Under the user's home rather than the temp dir, so the file survives a reboot.
    val dir = File(System.getProperty("user.home"), ".kmpinspector").apply { mkdirs() }
    File(dir, "crashes.txt")
}

internal actual object CrashFile {
    actual fun read(): String? = file.takeIf { it.exists() }?.readText()

    actual fun write(contents: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(contents)
        tmp.renameTo(file)
    }

    actual fun clear() {
        file.delete()
    }
}
