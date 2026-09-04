package com.waqas028.kmpinspector

import com.waqas028.kmpinspector.domain.model.CrashRecord
import com.waqas028.kmpinspector.domain.model.DatabaseController
import com.waqas028.kmpinspector.domain.model.DbInfo
import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.NetworkRequest
import com.waqas028.kmpinspector.domain.model.StackFrame
import com.waqas028.kmpinspector.domain.model.WorkJob

/** No-op twin of the real [Inspector]: every call is accepted and discarded. */
@Suppress("UNUSED_PARAMETER")
object Inspector {
    fun configure(appId: String, variant: String = "debug") = Unit
    fun installCrashHandler(appPackagePrefix: String? = null) = Unit
    fun clearCrashes() = Unit
    fun recordRequest(request: NetworkRequest) = Unit
    fun recordRequest(
        method: String,
        url: String,
        statusCode: Int?,
        durationMillis: Long,
        requestBytes: Long = 0,
        responseBytes: Long = 0,
    ) = Unit
    fun recordNonFatal(
        exceptionType: String,
        message: String,
        origin: String,
        frames: List<StackFrame> = emptyList(),
    ) = Unit
    fun recordCrash(record: CrashRecord) = Unit
    fun setWork(jobs: List<WorkJob>, engineLabel: String? = null) = Unit
    fun setDatabase(info: DbInfo, tables: List<DbTable>, controller: DatabaseController? = null) = Unit
    fun redactHeaders(names: Set<String>) = Unit
    fun onOpen(listener: () -> Unit) = Unit
    fun clear() = Unit
}
