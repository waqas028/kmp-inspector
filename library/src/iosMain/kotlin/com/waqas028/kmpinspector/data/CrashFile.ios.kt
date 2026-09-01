@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.waqas028.kmpinspector.data

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

private val path: String? by lazy {
    val dirs = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory.toULong(),
        NSUserDomainMask.toULong(),
        true,
    )
    val base = dirs.firstOrNull() as? String ?: return@lazy null
    NSFileManager.defaultManager.createDirectoryAtPath(base, true, null, null)
    "$base/kmpinspector-crashes.txt"
}

internal actual object CrashFile {
    actual fun read(): String? {
        val p = path ?: return null
        return NSString.stringWithContentsOfFile(p, NSUTF8StringEncoding, null)
    }

    actual fun write(contents: String) {
        val p = path ?: return
        (contents as NSString).writeToFile(p, true, NSUTF8StringEncoding, null)
    }

    actual fun clear() {
        val p = path ?: return
        NSFileManager.defaultManager.removeItemAtPath(p, null)
    }
}
