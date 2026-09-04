package com.waqas028.kmpinspector.data

import com.waqas028.kmpinspector.domain.model.DbColumn
import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.DbValue
import com.waqas028.kmpinspector.domain.model.LogLevel
import com.waqas028.kmpinspector.domain.model.LogLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportTest {
    @Test
    fun csv_quotes_only_what_needs_quoting_and_keeps_null_empty() {
        val table = DbTable(
            name = "t",
            columns = listOf(DbColumn("id", "INTEGER"), DbColumn("name", "TEXT"), DbColumn("note", "TEXT"), DbColumn("pic", "BLOB")),
            rows = listOf(
                listOf(DbValue.Number("1"), DbValue.Text("plain"), DbValue.Null, DbValue.Blob(2048)),
                listOf(DbValue.Number("2"), DbValue.Text("has, comma"), DbValue.Text("say \"hi\""), DbValue.Null),
            ),
        )
        val lines = tableToCsv(table).trimEnd().lines()
        assertEquals("id,name,note,pic", lines[0])
        assertEquals("1,plain,,BLOB 2 kB", lines[1])
        assertEquals("2,\"has, comma\",\"say \"\"hi\"\"\",", lines[2])
    }

    @Test
    fun logs_export_one_line_per_entry_with_level_and_tag() {
        val text = logsToText(
            listOf(
                LogLine(1, LogLevel.Info, "Cart", "restored", timestampMillis = 0),
                LogLine(2, LogLevel.Error, "Checkout", "failed", timestampMillis = 1_000),
            ),
        )
        val lines = text.trimEnd().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith(" I/Cart: restored"), lines[0])
        assertTrue(lines[1].endsWith(" E/Checkout: failed"), lines[1])
    }
}
