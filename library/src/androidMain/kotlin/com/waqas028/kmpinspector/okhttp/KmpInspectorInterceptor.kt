package com.waqas028.kmpinspector.okhttp

import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.domain.model.HttpHeader
import com.waqas028.kmpinspector.domain.model.NetworkRequest
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Records every request that passes through an OkHttp (and therefore Retrofit) client:
 *
 * ```
 * OkHttpClient.Builder()
 *     .addInterceptor(KmpInspectorInterceptor())
 * ```
 *
 * Add it after any interceptor that sets headers, so the recorded request shows what was actually
 * sent. Bodies are captured up to [maxBodyBytes]; the response body is peeked, not consumed, so
 * the caller still receives it in full.
 */
class KmpInspectorInterceptor @JvmOverloads constructor(
    private val maxBodyBytes: Long = 256L * 1024,
) : Interceptor {

    // Ids must be unique across the whole session, including entries recorded by other paths.
    private val ids = AtomicLong(System.nanoTime())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAt = System.currentTimeMillis()

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            record(request, startedAt, response = null, error = e)
            throw e
        }
        record(request, startedAt, response, error = null)
        return response
    }

    private fun record(request: Request, startedAt: Long, response: Response?, error: IOException?) {
        runCatching {
            // Only text is worth copying. Peeking first would buffer every image and file download
            // that shares the client, which is exactly what an image loader does.
            val peeked = response?.takeIf { it.body?.contentType().isText() }?.peekBody(maxBodyBytes)
            Inspector.recordRequest(
                NetworkRequest(
                    id = ids.incrementAndGet(),
                    method = request.method,
                    url = request.url.toString(),
                    statusCode = response?.code,
                    reasonPhrase = response?.message.orEmpty(),
                    durationMillis = response
                        ?.let { it.receivedResponseAtMillis - it.sentRequestAtMillis }
                        ?: (System.currentTimeMillis() - startedAt),
                    requestBytes = request.body?.contentLength()?.coerceAtLeast(0) ?: 0,
                    responseBytes = response?.body?.contentLength()?.takeIf { it >= 0 }
                        ?: peeked?.contentLength()?.coerceAtLeast(0) ?: 0,
                    protocol = response?.protocol?.toString() ?: "http/1.1",
                    timestampMillis = startedAt,
                    requestHeaders = request.headers.toInspector(),
                    responseHeaders = response?.headers?.toInspector().orEmpty(),
                    requestBody = request.bodyText(),
                    responseBody = peeked?.string(),
                    contentType = response?.header("Content-Type"),
                    errorText = error?.toString(),
                ),
            )
        }
    }

    private fun Request.bodyText(): String? {
        val body = body ?: return null
        if (body.isOneShot() || body.isDuplex() || !body.contentType().isText()) return null
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8(minOf(buffer.size, maxBodyBytes))
    }

    private fun okhttp3.MediaType?.isText(): Boolean {
        val type = this ?: return true // unknown: assume text, the size cap bounds the damage
        return type.type == "text" ||
            type.subtype.contains("json") ||
            type.subtype.contains("xml") ||
            type.subtype.contains("form") ||
            type.subtype.contains("javascript")
    }

    private fun Headers.toInspector(): List<HttpHeader> = map { (name, value) -> HttpHeader(name, value) }
}
