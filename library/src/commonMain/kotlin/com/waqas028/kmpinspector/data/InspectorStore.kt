package com.waqas028.kmpinspector.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.waqas028.kmpinspector.domain.model.CrashRecord
import com.waqas028.kmpinspector.domain.model.DatabaseController
import com.waqas028.kmpinspector.domain.model.DbInfo
import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.LogLine
import com.waqas028.kmpinspector.domain.model.NetworkRequest
import com.waqas028.kmpinspector.domain.model.WorkJob
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

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
    /** Live handle behind [database], when the host has one. */
    var databaseController by mutableStateOf<DatabaseController?>(null)

    /** True from a Refresh tap until the next snapshot lands; drives the button's spinner. */
    var databaseRefreshing by mutableStateOf(false)
    var appId by mutableStateOf("unknown")
    /** Host-supplied label for the work engine, e.g. "WorkManager 2.11". */
    var workLabel by mutableStateOf<String?>(null)
    var variant by mutableStateOf("debug")

    /** Unread network activity since the inspector was last opened — drives the bubble badge. */
    var unreadCount by mutableStateOf(0)
        private set

    val sessionStartMillis: Long = InspectorPlatform.currentTimeMillis()

    // Ids are handed out from OkHttp threads, the logcat thread and the UI at once.
    @OptIn(ExperimentalAtomicApi::class)
    private val nextId = AtomicLong(0L)

    @OptIn(ExperimentalAtomicApi::class)
    private fun nextId(): Long = nextId.incrementAndFetch()

    fun addRequest(request: NetworkRequest) {
        requests.add(0, request)
        trim(requests, NETWORK_CAPACITY)
        unreadCount++
    }

    fun addLog(level: com.waqas028.kmpinspector.domain.model.LogLevel, tag: String, message: String) {
        addLogs(listOf(LogEntry(level, tag, message)))
    }

    /** A line waiting to enter the ring; the timestamp is when it was captured, not when flushed. */
    class LogEntry(
        val level: com.waqas028.kmpinspector.domain.model.LogLevel,
        val tag: String,
        val message: String,
        val timestampMillis: Long = InspectorPlatform.currentTimeMillis(),
    )

    /**
     * One snapshot write for a whole batch. High-volume feeds (logcat) must use this: a write per
     * line means a global-snapshot apply, a recomposition check and, once the ring is full, an
     * O(n) removal for every single line, which is enough to make the host app stutter.
     */
    fun addLogs(entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        logs.addAll(
            entries.map {
                LogLine(
                    id = nextId(),
                    level = it.level,
                    tag = it.tag,
                    message = it.message,
                    timestampMillis = it.timestampMillis,
                )
            },
        )
        // Ring buffer: oldest lines fall off the front in one removal, not one per line.
        val excess = logs.size - LOG_CAPACITY
        if (excess > 0) logs.removeRange(0, excess)
    }

    fun addCrash(record: CrashRecord) {
        crashes.add(0, record)
        unreadCount++
        if (record.fatal) unreadCrashes++
        persistCrashes()
    }

    /**
     * Crashes are the one buffer that has to outlive the process: the app is gone by the time you
     * could look at it, so the record is only useful on the next launch.
     */
    fun persistCrashes() {
        runCatching { CrashFile.write(CrashCodec.encode(crashes)) }
    }

    /** Called once at startup, before any UI reads the list. */
    fun restoreCrashes() {
        if (restored) return
        restored = true
        val stored = runCatching { CrashFile.read()?.let(CrashCodec::decode) }.getOrNull().orEmpty()
        if (stored.isEmpty()) return
        crashes.addAll(stored)
        // Restored crashes are unread by definition - nobody has seen them yet.
        unreadCrashes += stored.count { it.fatal }
    }

    private var restored = false

    fun clearCrashes() {
        crashes.clear()
        unreadCrashes = 0
        runCatching { CrashFile.clear() }
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
        databaseController = null
        unreadCount = 0
        unreadCrashes = 0
        runCatching { CrashFile.clear() }
    }

    private fun <T> trim(list: MutableList<T>, capacity: Int) {
        while (list.size > capacity) list.removeAt(list.size - 1)
    }

    internal fun nextPublicId(): Long = nextId()

    /**
     * Called every time the inspector is opened. Collectors that hold a snapshot rather than a
     * stream (the database) use this to refresh, so what you see is current as of the tap.
     */
    private val openListeners = mutableListOf<() -> Unit>()

    fun addOpenListener(listener: () -> Unit) {
        openListeners += listener
    }

    fun notifyOpened() {
        openListeners.toList().forEach { runCatching(it) }
    }

    /** Refresh button: ask the live database if there is one, else replay the open hooks. */
    fun refreshDatabase() {
        databaseRefreshing = true
        databaseController?.refresh() ?: notifyOpened()
    }
}
