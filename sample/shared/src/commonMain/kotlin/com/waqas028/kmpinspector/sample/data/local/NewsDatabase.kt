package com.waqas028.kmpinspector.sample.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [ArticleEntity::class, UserEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(NewsDatabaseConstructor::class)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun articles(): ArticleDao
    abstract fun users(): UserDao
}

/**
 * Room generates the `actual` per platform; this only has to be declared.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object NewsDatabaseConstructor : RoomDatabaseConstructor<NewsDatabase> {
    override fun initialize(): NewsDatabase
}

internal const val DB_FILE_NAME = "news.db"
