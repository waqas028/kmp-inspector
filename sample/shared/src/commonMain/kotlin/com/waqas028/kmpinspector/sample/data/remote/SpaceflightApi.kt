package com.waqas028.kmpinspector.sample.data.remote

import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.InspectorLog
import com.waqas028.kmpinspector.domain.model.HttpHeader
import com.waqas028.kmpinspector.domain.model.NetworkRequest
import com.waqas028.kmpinspector.sample.nowMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

private const val BASE_URL = "https://api.spaceflightnewsapi.net/v4/articles/"

@Serializable
internal data class ArticlesResponse(val count: Int, val results: List<ArticleDto>)

@Serializable
internal data class ArticleDto(
    val id: Long,
    val title: String,
    val url: String,
    val summary: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("news_site") val newsSite: String = "",
    @SerialName("published_at") val publishedAt: String = "",
)

/**
 * Talks to the keyless Spaceflight News API and reports every call to the inspector.
 *
 * The reporting is deliberately explicit rather than a Ktor plugin: in a sample, seeing
 * `Inspector.recordRequest(...)` at the call site says more about the library than a plugin would.
 */
internal class SpaceflightApi(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun latestArticles(limit: Int = 25): List<ArticleDto> {
        val url = "$BASE_URL?limit=$limit"
        val started = TimeSource.Monotonic.markNow()
        InspectorLog.d("News", "GET $url")

        return try {
            val response = client.get(url)
            val body = response.bodyAsText()
            val elapsed = started.elapsedNow().inWholeMilliseconds

            Inspector.recordRequest(
                NetworkRequest(
                    id = response.hashCode().toLong(),
                    method = "GET",
                    url = url,
                    statusCode = response.status.value,
                    reasonPhrase = response.status.description,
                    durationMillis = elapsed,
                    requestBytes = 0,
                    responseBytes = body.length.toLong(),
                    protocol = response.version.toString(),
                    timestampMillis = nowMillis(),
                    contentType = "application/json",
                    responseHeaders = response.headers.entries()
                        .take(8)
                        .map { HttpHeader(it.key, it.value.joinToString()) },
                    responseBody = body,
                ),
            )

            if (!response.status.isSuccess()) {
                InspectorLog.w("News", "Feed returned ${response.status.value}; keeping cached articles")
                return emptyList()
            }

            val parsed = json.decodeFromString<ArticlesResponse>(body)
            InspectorLog.i("News", "Fetched ${parsed.results.size} articles in ${elapsed}ms")
            parsed.results
        } catch (e: Exception) {
            val elapsed = started.elapsedNow().inWholeMilliseconds
            // A transport failure is still a request worth showing, with no status code.
            Inspector.recordRequest(
                NetworkRequest(
                    id = url.hashCode().toLong(),
                    method = "GET",
                    url = url,
                    statusCode = null,
                    durationMillis = elapsed,
                    requestBytes = 0,
                    responseBytes = 0,
                    timestampMillis = nowMillis(),
                    errorText = "${e::class.simpleName}: ${e.message}",
                ),
            )
            Inspector.recordNonFatal(
                exceptionType = e::class.simpleName ?: "Exception",
                message = e.message ?: "Network request failed",
                origin = "SpaceflightApi.kt",
            )
            InspectorLog.e("News", "Fetch failed: ${e.message}")
            emptyList()
        }
    }
}
