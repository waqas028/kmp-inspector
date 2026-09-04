package com.waqas028.kmpinspector.data

import android.os.Process
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Tails this process's own logcat output into the Logs panel, so `android.util.Log` calls made
 * anywhere in the app (and by its libraries) show up without a wrapper.
 *
 * An app may always read its own log lines; no `READ_LOGS` permission is involved.
 *
 * Two daemon threads: one reads the pipe and queues parsed lines, the other flushes the queue into
 * the store in batches at most about ten times a second. Chatty devices emit hundreds of lines a
 * second, and pushing each one into snapshot state individually was measurable as jank in the
 * host app.
 */
internal object LogcatCollector {

    private const val FLUSH_INTERVAL_MS = 100L

    @Volatile
    private var started = false

    private val queue = LinkedBlockingQueue<InspectorStore.LogEntry>()

    fun start() {
        if (started) return
        started = true
        Thread({ tail() }, "KmpInspector-logcat").apply { isDaemon = true }.start()
        Thread({ flush() }, "KmpInspector-logcat-flush").apply { isDaemon = true }.start()
    }

    private fun tail() {
        val process = runCatching {
            ProcessBuilder("logcat", "-v", "brief", "--pid=${Process.myPid()}", "*:V")
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            while (true) {
                val text = reader.readLine() ?: break
                val parsed = parseLogcatLine(text) ?: continue
                queue.offer(InspectorStore.LogEntry(parsed.level, parsed.tag, parsed.message))
            }
        }
    }

    private fun flush() {
        val batch = ArrayList<InspectorStore.LogEntry>()
        while (true) {
            val first = queue.poll(1, TimeUnit.SECONDS) ?: continue
            // Give the rest of the burst a moment to arrive, then take it all in one write.
            Thread.sleep(FLUSH_INTERVAL_MS)
            batch.add(first)
            queue.drainTo(batch)
            runCatching { InspectorStore.addLogs(batch.toList()) }
            batch.clear()
        }
    }
}
