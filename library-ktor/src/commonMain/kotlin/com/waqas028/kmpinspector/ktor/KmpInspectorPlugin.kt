package com.waqas028.kmpinspector.ktor

import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.domain.model.HttpHeader
import com.waqas028.kmpinspector.domain.model.NetworkRequest
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.util.date.getTimeMillis
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

class KmpInspectorPluginConfig {
    /** Bodies are captured up to this many bytes; larger ones are cut, never skipped. */
    var maxBodyBytes: Int = 256 * 1024
}

/**
 * Records every call into the Network panel:
 *
 * ```
 * HttpClient {
 *     install(KmpInspectorPlugin)
 * }
 * ```
 *
 * Text responses are saved in memory so the panel can show them and the app still reads them;
 * binary responses are passed through untouched with only their size recorded. Failures before a
 * response arrives are recorded as transport errors and rethrown.
 */
@OptIn(ExperimentalAtomicApi::class)
val KmpInspectorPlugin = createClientPlugin("KmpInspector", ::KmpInspectorPluginConfig) {
    val maxBodyBytes = pluginConfig.maxBodyBytes
    val ids = AtomicLong(getTimeMillis())

    on(Send) { request ->
        val startedAt = getTimeMillis()
        val requestBody = request.body as? OutgoingContent
        val call = try {
            proceed(request)
        } catch (e: Throwable) {
            Inspector.recordRequest(
                NetworkRequest(
                    id = ids.incrementAndFetch(),
                    method = request.method.value,
                    url = request.url.buildString(),
                    statusCode = null,
                    durationMillis = getTimeMillis() - startedAt,
                    requestBytes = requestBody?.contentLength ?: 0,
                    responseBytes = 0,
                    timestampMillis = startedAt,
                    requestHeaders = request.headers.build().toInspector(),
                    requestBody = requestBody?.text(maxBodyBytes),
                    errorText = e.toString(),
                ),
            )
            throw e
        }

        // Saving reads the body once into memory; the app then reads the copy. Only worth it for
        // text, and only when the declared size is within the cap.
        val declared = call.response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val isText = call.response.contentType().isText()
        val saved: HttpClientCall = if (isText && (declared == null || declared <= maxBodyBytes)) {
            runCatching { call.save() }.getOrElse { call }
        } else call
        val responseText = if (saved !== call) runCatching { saved.response.bodyAsText().take(maxBodyBytes) }.getOrNull() else null

        Inspector.recordRequest(
            NetworkRequest(
                id = ids.incrementAndFetch(),
                method = saved.request.method.value,
                url = saved.request.url.toString(),
                statusCode = saved.response.status.value,
                reasonPhrase = saved.response.status.description,
                durationMillis = saved.response.responseTime.timestamp - saved.response.requestTime.timestamp,
                requestBytes = requestBody?.contentLength ?: 0,
                responseBytes = declared ?: responseText?.length?.toLong() ?: 0,
                protocol = saved.response.version.toString(),
                timestampMillis = startedAt,
                requestHeaders = saved.request.headers.toInspector(),
                responseHeaders = saved.response.headers.toInspector(),
                requestBody = requestBody?.text(maxBodyBytes),
                responseBody = responseText,
                contentType = saved.response.headers[HttpHeaders.ContentType],
            ),
        )
        saved
    }
}

private fun Headers.toInspector(): List<HttpHeader> =
    entries().flatMap { (name, values) -> values.map { HttpHeader(name, it) } }

private fun OutgoingContent.text(max: Int): String? = when (this) {
    is TextContent -> text.take(max)
    is ByteArrayContent -> if (contentType.isText()) bytes().decodeToString().take(max) else null
    else -> null
}

private fun ContentType?.isText(): Boolean {
    val type = this ?: return true // unknown: assume text, the size cap bounds the damage
    return type.contentType == "text" ||
        type.contentSubtype.contains("json") ||
        type.contentSubtype.contains("xml") ||
        type.contentSubtype.contains("form") ||
        type.contentSubtype.contains("javascript")
}
