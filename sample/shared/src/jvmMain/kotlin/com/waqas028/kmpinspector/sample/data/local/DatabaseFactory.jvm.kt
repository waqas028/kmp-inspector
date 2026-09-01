package com.waqas028.kmpinspector.sample.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

internal actual fun createNewsDatabase(): NewsDatabase {
    val dir = File(System.getProperty("java.io.tmpdir"), "kmpinspector-sample").apply { mkdirs() }
    return Room.databaseBuilder<NewsDatabase>(name = File(dir, DB_FILE_NAME).absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
