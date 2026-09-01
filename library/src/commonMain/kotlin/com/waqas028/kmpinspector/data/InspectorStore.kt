package com.waqas028.kmpinspector.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.waqas028.kmpinspector.domain.model.CrashRecord
import com.waqas028.kmpinspector.domain.model.DbInfo
import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.LogLine
import com.waqas028.kmpinspector.domain.model.NetworkRequest
import com.waqas028.kmpinspector.domain.model.WorkJob
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/**
 * The capture buffers. Sizes come from the handoff: 200 requests, a 2,000-line log ring.
 * Backed by snapshot state so the UI recomposes as data arrives.
 */
internal object InspectorStore {

    const val NETWORK_CAPACITY = 200
    const val LOG_CAPACITY = 2_000

    val requests = mutableStateListOf<NetworkRequest>()
    val logs = mutableStateListOf<LogLine>()
    val crashes = mutableStateListOf<CrashRecord>()
    val work = mutableStateListOf<WorkJob>()
    val tables = mutableStateListOf<DbTable>()

    var database by mutableStateOf<DbInfo?>(null)
    var appId by mutableStateOf("unknown")
    var variant by mutableStateOf("debug")

    /** Unread network activity since the inspector was last opened — drives the bubble badge. */
    var unreadCount by mutableStateOf(0)
        private set

    val sessionStartMillis: Long = InspectorPlatform.currentTimeMillis()

    private var nextId = 1L
    private fun nextId(): Long = nextId++

    fun addRequest(request: NetworkRequest) {
        requests.add(0, request)
        trim(requests, NETWORK_CAPACITY)
        unreadCount++
    }

    fun addLog(level: com.waqas028.kmpinspector.domain.model.LogLevel, tag: String, message: String) {
        logs.add(
            LogLine(
                id = nextId(),
                level = level,
                tag = tag,
                message = message,
                timestampMillis = InspectorPlatform.currentTimeMillis(),
            ),
        )
        // Ring buffer: oldest lines fall off the front, newest stay at the tail for tailing.
        while (logs.size > LOG_CAPACITY) logs.removeAt(0)
    }

    fun addCrash(record: CrashRecord) {
        crashes.add(0, record)
        unreadCount++
        if (record.fatal) unreadCrashes++
    }

    /** Cleared when the Crashes tab is opened, so the bubble returns to its resting look. */
    fun markCrashesRead() {
        unreadCrashes = 0
    }

    fun markRead() {
        unreadCount = 0
    }

    /**
     * Fatal crashes not yet looked at. The spec shows the tab dot "when unread crashes exist", so
     * the crash state is unread-scoped rather than permanent — otherwise one crash would pin the
     * bubble into its alarm state for the rest of the session.
     */
    var unreadCrashes by mutableStateOf(0)
        private set

    val hasCrash: Boolean get() = unreadCrashes > 0

    fun clear() {
        requests.clear(); logs.clear(); crashes.clear(); work.clear(); tables.clear()
        database = null
        unreadCount = 0
        unreadCrashes = 0
    }

    private fun <T> trim(list: MutableList<T>, capacity: Int) {
        while (list.size > capacity) list.removeAt(list.size - 1)
    }

    internal fun nextPublicId(): Long = nextId()
}
