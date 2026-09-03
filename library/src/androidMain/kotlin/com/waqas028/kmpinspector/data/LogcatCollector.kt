package com.waqas028.kmpinspector.data

import android.os.Process
import com.waqas028.kmpinspector.InspectorLog
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Tails this process's own logcat output into the Logs panel, so `android.util.Log` calls made
 * anywhere in the app (and by its libraries) show up without a wrapper.
 *
 * An app may always read its own log lines; no `READ_LOGS` permission is involved. The reader
 * lives on a daemon thread and dies with the process.
 */
internal object LogcatCollector {

    @Volatile
    private var started = false

    // brief format: "D/Tag     (12345): message"
    private val line = Regex("""^([VDIWEF])/(.*?)\s*\(\s*\d+\):\s?(.*)$""")

    fun start() {
        if (started) return
        started = true
        Thread({ tail() }, "KmpInspector-logcat").apply { isDaemon = true }.start()
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
                val match = line.find(text) ?: continue
                val (level, tag, message) = match.destructured
                when (level) {
                    "V" -> InspectorLog.v(tag, message)
                    "D" -> InspectorLog.d(tag, message)
                    "I" -> InspectorLog.i(tag, message)
                    "W" -> InspectorLog.w(tag, message)
                    else -> InspectorLog.e(tag, message)
                }
            }
        }
    }
}
