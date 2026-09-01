package com.waqas028.kmpinspector.data

import com.waqas028.kmpinspector.domain.model.CrashRecord
import com.waqas028.kmpinspector.domain.model.StackFrame

/**
 * Reads and writes the crash file. Each platform decides where it lives; returning null simply
 * means "no crashes stored", so a platform without a writable location degrades to in-memory.
 */
internal expect object CrashFile {
    fun read(): String?
    fun write(contents: String)
    fun clear()
}

/** Crashes kept on disk. Small enough to read synchronously at startup. */
internal const val PERSISTED_CRASH_LIMIT = 20

/**
 * A deliberately boring line format instead of a serialization library.
 *
 * `:library` depends on Compose and nothing else, and adding kotlinx-serialization to a debug
 * overlay to store at most twenty records is a poor trade. The format is `key=value` per line with
 * `--` between records, so a half-written file can be read back up to the last complete record
 * rather than being lost entirely.
 */
internal object CrashCodec {

    private const val RECORD_SEPARATOR = "--"

    fun encode(crashes: List<CrashRecord>): String = buildString {
        crashes.take(PERSISTED_CRASH_LIMIT).forEach { crash ->
            appendLine("id=${crash.id}")
            appendLine("fatal=${crash.fatal}")
            appendLine("type=${esc(crash.exceptionType)}")
            appendLine("message=${esc(crash.message)}")
            appendLine("origin=${esc(crash.origin)}")
            appendLine("thread=${esc(crash.threadName)}")
            appendLine("occurrences=${crash.occurrences}")
            crash.causedBy?.let { appendLine("causedBy=${esc(it)}") }
            appendLine("time=${crash.timestampMillis}")
            crash.frames.forEach { f ->
                appendLine("frame=${if (f.isAppFrame) "1" else "0"}|${esc(f.text)}")
            }
            appendLine(RECORD_SEPARATOR)
        }
    }

    fun decode(text: String): List<CrashRecord> {
        val out = mutableListOf<CrashRecord>()
        var fields = mutableMapOf<String, String>()
        var frames = mutableListOf<StackFrame>()

        fun flush() {
            // A record without these is a torn write; drop it rather than inventing values.
            val id = fields["id"]?.toLongOrNull()
            val type = fields["type"]
            val time = fields["time"]?.toLongOrNull()
            if (id != null && type != null && time != null) {
                out += CrashRecord(
                    id = id,
                    fatal = fields["fatal"] == "true",
                    exceptionType = type,
                    message = fields["message"].orEmpty(),
                    origin = fields["origin"].orEmpty(),
                    threadName = fields["thread"] ?: "main",
                    occurrences = fields["occurrences"]?.toIntOrNull() ?: 1,
                    causedBy = fields["causedBy"],
                    frames = frames.toList(),
                    timestampMillis = time,
                )
            }
            fields = mutableMapOf()
            frames = mutableListOf()
        }

        text.lineSequence().forEach { line ->
            when {
                line == RECORD_SEPARATOR -> flush()
                line.startsWith("frame=") -> {
                    val raw = line.removePrefix("frame=")
                    val isApp = raw.startsWith("1|")
                    frames += StackFrame(unesc(raw.substringAfter('|')), isApp)
                }
                line.contains('=') -> fields[line.substringBefore('=')] = unesc(line.substringAfter('='))
            }
        }
        // A file truncated mid-record simply loses that record; everything before it survives.
        return out
    }

    /** Newlines would break the line format, and backslash has to be escaped to make that reversible. */
    private fun esc(s: String) = s
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun unesc(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
