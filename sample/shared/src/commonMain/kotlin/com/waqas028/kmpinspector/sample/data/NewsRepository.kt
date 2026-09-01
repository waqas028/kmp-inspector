package com.waqas028.kmpinspector.sample.data

import com.waqas028.kmpinspector.InspectorLog
import com.waqas028.kmpinspector.sample.data.local.ArticleEntity
import com.waqas028.kmpinspector.sample.data.local.NewsDatabase
import com.waqas028.kmpinspector.sample.data.local.UserEntity
import com.waqas028.kmpinspector.sample.data.remote.SpaceflightApi
import com.waqas028.kmpinspector.sample.nowMillis

/** What the news screen renders. */
data class NewsUiState(
    val articles: List<ArticleEntity> = emptyList(),
    val user: UserEntity? = null,
    val loading: Boolean = false,
    val usingDemoData: Boolean = false,
    val message: String? = null,
)

/**
 * Fetches articles, stores them in Room, and serves whatever is on disk.
 *
 * The cache is the source of truth for the UI: a failed refresh leaves the last good articles on
 * screen rather than emptying it. Only when the network fails *and* the table is empty does the
 * sample fall back to fixture data, so the inspector always has something to show.
 */
internal class NewsRepository(
    private val database: NewsDatabase,
    private val api: SpaceflightApi,
) {

    suspend fun ensureUser(): UserEntity {
        database.users().current()?.let { return it }
        val user = UserEntity(
            id = 1,
            displayName = "Waqas",
            email = "waqaswaseem679@gmail.com",
            lastLoginMillis = nowMillis(),
        )
        database.users().upsert(user)
        InspectorLog.i("Auth", "Seeded local user ${user.email}")
        return user
    }

    suspend fun cached(): List<ArticleEntity> = database.articles().all()

    /** Returns true when the network produced articles and the cache was replaced. */
    suspend fun refresh(): Boolean {
        val dtos = api.latestArticles()
        if (dtos.isEmpty()) {
            InspectorLog.w("News", "Refresh produced nothing; cache left untouched")
            return false
        }
        val now = nowMillis()
        val entities = dtos.map { dto ->
            ArticleEntity(
                id = dto.id,
                title = dto.title,
                summary = dto.summary,
                newsSite = dto.newsSite,
                url = dto.url,
                imageUrl = dto.imageUrl,
                publishedAtMillis = parseIsoToMillis(dto.publishedAt) ?: now,
                cachedAtMillis = now,
            )
        }
        database.articles().clear()
        database.articles().upsertAll(entities)
        InspectorLog.i("News", "Cached ${entities.size} articles to Room")
        return true
    }
}

/**
 * `2026-09-01T12:04:31Z` to epoch millis without pulling in a date library. Good enough to sort a
 * news list; not a general-purpose parser.
 */
internal fun parseIsoToMillis(iso: String): Long? {
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})""").find(iso) ?: return null
    val (y, mo, d, h, mi, s) = m.destructured
    val year = y.toInt(); val month = mo.toInt(); val day = d.toInt()
    var days = 0L
    for (yy in 1970 until year) days += if (isLeap(yy)) 366 else 365
    val lengths = intArrayOf(31, if (isLeap(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    for (mm in 0 until month - 1) days += lengths[mm]
    days += (day - 1)
    return ((days * 24 + h.toInt()) * 60 + mi.toInt()) * 60_000L + s.toInt() * 1000L
}

private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
