package com.waqas028.kmpinspector.sample.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

internal actual fun createNewsDatabase(): NewsDatabase =
    Room.databaseBuilder<NewsDatabase>(name = documentsPath())
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun documentsPath(): String {
    val documents: NSURL = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    ) ?: error("No documents directory")
    return requireNotNull(documents.path) + "/" + DB_FILE_NAME
}
