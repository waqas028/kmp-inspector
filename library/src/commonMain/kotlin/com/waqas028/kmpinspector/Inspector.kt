package com.waqas028.kmpinspector

import com.waqas028.kmpinspector.data.InspectorPlatform
import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.domain.model.CrashRecord
import com.waqas028.kmpinspector.domain.model.DbInfo
import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.NetworkRequest
import com.waqas028.kmpinspector.domain.model.StackFrame
import com.waqas028.kmpinspector.domain.model.WorkJob

/**
 * How data gets into the inspector. Everything the UI shows arrives through these calls, so a host
 * app can feed it from whichever HTTP client, database or scheduler it actually uses.
 */
object Inspector {

    /**
     * Identifies the session in the inspector header, e.g. `com.example.shop · debug`. Call once at
     * startup; without it the header reads `unknown`.
     */
    fun configure(appId: String, variant: String = "debug") {
        InspectorStore.appId = appId
        InspectorStore.variant = variant
    }

    fun recordRequest(request: NetworkRequest) = InspectorStore.addRequest(request)

    /** Convenience for callers that do not want to build [NetworkRequest] by hand. */
    fun recordRequest(
        method: String,
        url: String,
        statusCode: Int?,
        durationMillis: Long,
        requestBytes: Long = 0,
        responseBytes: Long = 0,
    ) = InspectorStore.addRequest(
        NetworkRequest(
            id = InspectorStore.nextPublicId(),
            method = method,
            url = url,
            statusCode = statusCode,
            durationMillis = durationMillis,
            requestBytes = requestBytes,
            responseBytes = responseBytes,
            timestampMillis = InspectorPlatform.currentTimeMillis(),
        ),
    )

    fun recordNonFatal(
        exceptionType: String,
        message: String,
        origin: String,
        frames: List<StackFrame> = emptyList(),
    ) = InspectorStore.addCrash(
        CrashRecord(
            id = InspectorStore.nextPublicId(),
            fatal = false,
            exceptionType = exceptionType,
            message = message,
            origin = origin,
            frames = frames,
            timestampMillis = InspectorPlatform.currentTimeMillis(),
        ),
    )

    fun recordCrash(record: CrashRecord) = InspectorStore.addCrash(record)

    /** Replaces the Background Work list. Android-only in the UI; a no-op tab elsewhere. */
    fun setWork(jobs: List<WorkJob>) {
        InspectorStore.work.clear()
        InspectorStore.work.addAll(jobs)
    }

    fun setDatabase(info: DbInfo, tables: List<DbTable>) {
        InspectorStore.database = info
        InspectorStore.tables.clear()
        InspectorStore.tables.addAll(tables)
    }

    fun clear() = InspectorStore.clear()
}
