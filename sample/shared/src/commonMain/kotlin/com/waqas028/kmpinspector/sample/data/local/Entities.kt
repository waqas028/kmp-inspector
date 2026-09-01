package com.waqas028.kmpinspector.sample.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val summary: String,
    val newsSite: String,
    val url: String,
    val imageUrl: String?,
    val publishedAtMillis: Long,
    /** When this row was written, so the UI can say how stale the cache is. */
    val cachedAtMillis: Long,
)

/** The signed-in user. One row in practice, which is what the inspector's grid will show. */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long,
    val displayName: String,
    val email: String,
    val lastLoginMillis: Long,
)
