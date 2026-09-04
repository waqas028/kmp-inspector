package com.waqas028.kmpinspector.data

import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.DbValue
import com.waqas028.kmpinspector.domain.model.LogLine

/** Log lines as plain text, one per line, in the order given. */
internal fun logsToText(lines: List<LogLine>): String = buildString {
    lines.forEach { line ->
        append(formatClock(line.timestampMillis)).append(' ')
            .append(line.level.letter).append('/').append(line.tag).append(": ")
            .append(line.message).append('\n')
    }
}

/**
 * RFC 4180 CSV: a header row, then one row per record. NULL is an empty field, a BLOB is
 * described rather than dumped, and anything with a comma, quote or line break is quoted.
 */
internal fun tableToCsv(table: DbTable): String = buildString {
    appendLine(table.columns.joinToString(",") { csvField(it.name) })
    table.rows.forEach { row ->
        appendLine(
            row.joinToString(",") { value ->
                when (value) {
                    DbValue.Null -> ""
                    is DbValue.Text -> csvField(value.value)
                    is DbValue.Number -> value.value
                    is DbValue.Blob -> csvField("BLOB ${formatBytes(value.bytes)}")
                }
            },
        )
    }
}

private fun csvField(text: String): String =
    if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\""
    else text
