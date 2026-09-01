package com.waqas028.kmpinspector.sample.data.local

import androidx.room.Room
import com.waqas028.kmpinspector.sample.requireAppContext
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

internal actual fun createNewsDatabase(): NewsDatabase {
    val context = requireAppContext()
    return Room.databaseBuilder<NewsDatabase>(
        context = context,
        name = context.getDatabasePath(DB_FILE_NAME).absolutePath,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
