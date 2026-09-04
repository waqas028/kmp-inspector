package com.waqas028.kmpinspector.data

import com.waqas028.kmpinspector.domain.model.LogLevel

internal class ParsedLogLine(val level: LogLevel, val tag: String, val message: String)

// logcat "brief" format: "D/Tag     (12345): message". The tag may contain spaces and
// parentheses of its own, so it is matched lazily up to the "(pid):" marker.
private val BRIEF_LINE = Regex("""^([VDIWEF])/(.*?)\s*\(\s*\d+\):\s?(.*)$""")

/** Parses one line of `logcat -v brief`; null for lines that are not log entries. */
internal fun parseLogcatLine(text: String): ParsedLogLine? {
    val match = BRIEF_LINE.find(text) ?: return null
    val (level, tag, message) = match.destructured
    return ParsedLogLine(
        level = when (level) {
            "V" -> LogLevel.Verbose
            "D" -> LogLevel.Debug
            "I" -> LogLevel.Info
            "W" -> LogLevel.Warn
            else -> LogLevel.Error
        },
        tag = tag,
        message = message,
    )
}
