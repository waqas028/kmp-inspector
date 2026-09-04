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
import androidx.compose.ui.geometry.Offset
import kotlin.concurrent.atomics.AtomicInt
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

    /**
     * Total request + response body text kept across the network buffer. 200 entries at up to
     * 256 KB each way could pin 100 MB; past this budget the oldest entries keep their metadata
     * and lose their bodies.
     */
    var bodyBudgetChars: Int = 16 * 1024 * 1024
        internal set

    /**
     * Header names whose values are masked in anything that leaves the device through Share.
     * The in-app Headers tab and the clipboard still show them in full.
     */
    var redactedHeaders: Set<String> = setOf("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key")

    /**
     * Whether the inspector is open, and where the bubble sits. Shared rather than remembered per
     * composition, so the overlay on the next Activity, or after a rotation, picks up where the
     * last one left off instead of resetting.
     */
    var inspectorOpen by mutableStateOf(false)
    var bubblePosition by mutableStateOf<Offset?>(null)
    var bubbleOnRight by mutableStateOf(true)

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

    // Requests arrive on HTTP client threads; a plain ++ on snapshot state can lose increments.
    @OptIn(ExperimentalAtomicApi::class)
    private val unread = AtomicInt(0)

    @OptIn(ExperimentalAtomicApi::class)
    fun addRequest(request: NetworkRequest) {
        requests.add(0, request)
        trim(requests, NETWORK_CAPACITY)
        enforceBodyBudget()
        unreadCount = unread.incrementAndFetch()
    }

    /** Walks newest to oldest; once the running total passes the budget, older bodies are dropped. */
    private fun enforceBodyBudget() {
        var total = 0L
        for (i in requests.indices) {
            val r = requests[i]
            val size = (r.requestBody?.length ?: 0) + (r.responseBody?.length ?: 0)
            if (size == 0) continue
            total += size
            if (total > bodyBudgetChars) {
                requests[i] = r.copy(requestBody = null, responseBody = null, bodiesEvicted = true)
            }
        }
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

    @OptIn(ExperimentalAtomicApi::class)
    fun markRead() {
        unread.store(0)
        unreadCount = 0
    }

    fun clearRequests() {
        requests.clear()
        markRead()
    }

    fun clearLogs() = logs.clear()

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
        markRead()
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
