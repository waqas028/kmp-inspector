package com.waqas028.kmpinspector.domain.model



/** Status families the network list encodes as glyph + code + colour. */
enum class HttpOutcome { Success, Redirect, ClientError, ServerError, TransportError }

data class HttpHeader(val name: String, val value: String)

data class NetworkRequest(
    val id: Long,
    val method: String,
    val url: String,
    val statusCode: Int?,
    val reasonPhrase: String = "",
    val durationMillis: Long,
    val requestBytes: Long,
    val responseBytes: Long,
    val protocol: String = "http/1.1",
    val timestampMillis: Long,
    val requestHeaders: List<HttpHeader> = emptyList(),
    val responseHeaders: List<HttpHeader> = emptyList(),
    val requestBody: String? = null,
    val responseBody: String? = null,
    val contentType: String? = null,
    val errorText: String? = null,
) {
    val outcome: HttpOutcome
        get() = when (statusCode) {
            null -> HttpOutcome.TransportError
            in 200..299 -> HttpOutcome.Success
            in 300..399 -> HttpOutcome.Redirect
            in 400..499 -> HttpOutcome.ClientError
            else -> HttpOutcome.ServerError
        }

    /** The part shown in the list: path plus query, head-truncated by the UI. */
    val pathAndQuery: String
        get() = url.substringAfter("://").substringAfter('/').let { if (it.isEmpty()) "/" else "/$it" }

    val isWrite: Boolean get() = method.uppercase() in setOf("POST", "PUT", "PATCH", "DELETE")
}

enum class LogLevel(val letter: Char) { Verbose('V'), Debug('D'), Info('I'), Warn('W'), Error('E') }

data class LogLine(
    val id: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val timestampMillis: Long,
)

data class StackFrame(
    val text: String,
    /** App frames are pulled left and marked; framework frames are indented and dimmed. */
    val isAppFrame: Boolean,
)

data class CrashRecord(
    val id: Long,
    val fatal: Boolean,
    val exceptionType: String,
    val message: String,
    val origin: String,
    val threadName: String = "main",
    val occurrences: Int = 1,
    val causedBy: String? = null,
    val frames: List<StackFrame> = emptyList(),
    val timestampMillis: Long,
)

enum class WorkState { Enqueued, Running, Succeeded, Failed, Cancelled }

data class WorkJob(
    val id: String,
    val name: String,
    val state: WorkState,
    val tag: String? = null,
    val attempt: Int = 1,
    val lastRunMillis: Long? = null,
    val nextRun: String? = null,
    val constraints: List<String> = emptyList(),
    val inputData: List<Pair<String, String>> = emptyList(),
    val outputData: List<Pair<String, String>> = emptyList(),
    val failureReason: String? = null,
)

/** A cell value, kept as a type so NULL, BLOB and empty string stay distinguishable. */
sealed interface DbValue {
    data class Text(val value: String) : DbValue
    data class Number(val value: String) : DbValue
    data class Blob(val bytes: Long) : DbValue
    data object Null : DbValue
}

data class DbColumn(val name: String, val type: String)

data class DbTable(
    val name: String,
    val columns: List<DbColumn>,
    val rows: List<List<DbValue>>,
    /**
     * SQLite rowid per row, parallel to [rows]. Needed for cell edits to reach a live database;
     * null for tables handed over as plain snapshots (edits then change the snapshot only).
     */
    val rowIds: List<Long>? = null,
) {
    val rowCount: Int get() = rows.size
}

/**
 * A live database behind the Database panel. Optional: without one the panel browses the
 * snapshot it was given and edits stay in memory.
 *
 * Both calls may run their work on a background thread; they should end by handing a fresh
 * snapshot to `Inspector.setDatabase` so the panel shows the result.
 */
interface DatabaseController {
    /** Re-read every table and republish. Bound to the Refresh button. */
    fun refresh()

    /**
     * Writes one cell. [rowId] comes from [DbTable.rowIds]; a null [value] writes SQL NULL, and any
     * other string is bound as text, which SQLite converts by the column's affinity.
     */
    fun updateCell(table: String, rowId: Long, column: String, value: String?)
}

data class DbInfo(val fileName: String, val engine: String, val sizeLabel: String)
