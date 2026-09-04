package com.waqas028.kmpinspector.room

import androidx.room.PooledConnection
import androidx.room.RoomDatabase
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteStatement
import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.InspectorLog
import com.waqas028.kmpinspector.domain.model.DatabaseController
import com.waqas028.kmpinspector.domain.model.DbColumn
import com.waqas028.kmpinspector.domain.model.DbInfo
import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.DbValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ROW_LIMIT = 500
private const val TABLES_SQL =
    "SELECT name FROM sqlite_master WHERE type = 'table' " +
        "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata') " +
        "ORDER BY name"

/**
 * Reads and writes through Room's pooled connections. This is the only path on desktop and iOS,
 * where there is no SupportSQLiteDatabase underneath.
 */
internal class DriverRoomCollector(
    private val database: RoomDatabase,
    private val fileName: String?,
) : DatabaseController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun attach() {
        refresh()
        Inspector.onOpen { refresh() }
    }

    override fun refresh() {
        scope.launch { runCatching { snapshot() }.onFailure { report("refresh", it) } }
    }

    override fun updateCell(table: String, rowId: Long, column: String, value: String?) {
        scope.launch {
            runCatching {
                database.useWriterConnection { connection ->
                    connection.usePrepared("UPDATE `$table` SET `$column` = ? WHERE rowid = ?") { stmt ->
                        if (value == null) stmt.bindNull(1) else stmt.bindText(1, value)
                        stmt.bindLong(2, rowId)
                        stmt.step()
                    }
                }
                runCatching { database.invalidationTracker.refreshAsync() }
                InspectorLog.i("Inspector", "$table.$column updated for rowid $rowId")
                snapshot()
            }.onFailure { report("update $table.$column", it) }
        }
    }

    override fun query(sql: String): DbTable = runBlocking {
        database.useReaderConnection { connection ->
            connection.usePrepared(sql) { stmt ->
                val columns = (0 until stmt.getColumnCount()).map { DbColumn(stmt.getColumnName(it), "") }
                val rows = buildList {
                    while (size < ROW_LIMIT && stmt.step()) add(stmt.readRow(from = 0))
                }
                DbTable(name = "query", columns = columns, rows = rows)
            }
        }
    }

    private suspend fun snapshot() {
        val tables = database.useReaderConnection { connection -> connection.readTables() }
        Inspector.setDatabase(
            info = DbInfo(
                fileName = fileName ?: "room",
                engine = "SQLite · Room",
                sizeLabel = "${tables.sumOf { it.rowCount }} rows",
            ),
            tables = tables,
            controller = this,
        )
    }

    private suspend fun PooledConnection.readTables(): List<DbTable> {
        val names = usePrepared(TABLES_SQL) { stmt -> buildList { while (stmt.step()) add(stmt.getText(0)) } }
        return names.map { name ->
            val columns = usePrepared("PRAGMA table_info(`$name`)") { stmt ->
                buildList { while (stmt.step()) add(DbColumn(stmt.getText(1), stmt.getText(2))) }
            }
            // rowid first so edits can address the row; WITHOUT ROWID tables fall back to read-only.
            val withRowId = runCatching {
                usePrepared("SELECT rowid AS _kmp_rowid, * FROM `$name` LIMIT $ROW_LIMIT") { stmt ->
                    val ids = mutableListOf<Long>()
                    val rows = buildList {
                        while (stmt.step()) {
                            ids += stmt.getLong(0)
                            add(stmt.readRow(from = 1))
                        }
                    }
                    ids.toList() to rows
                }
            }
            val (rowIds, rows) = withRowId.getOrElse {
                usePrepared("SELECT * FROM `$name` LIMIT $ROW_LIMIT") { stmt ->
                    null to buildList { while (stmt.step()) add(stmt.readRow(from = 0)) }
                }
            }
            DbTable(name = name, columns = columns, rows = rows, rowIds = rowIds)
        }
    }

    private fun SQLiteStatement.readRow(from: Int): List<DbValue> =
        (from until getColumnCount()).map { i ->
            when (getColumnType(i)) {
                SQLITE_DATA_NULL -> DbValue.Null
                SQLITE_DATA_INTEGER, SQLITE_DATA_FLOAT -> DbValue.Number(getText(i))
                SQLITE_DATA_BLOB -> DbValue.Blob(getBlob(i).size.toLong())
                else -> DbValue.Text(getText(i))
            }
        }

    private fun report(what: String, error: Throwable) {
        InspectorLog.e("Inspector", "Database $what failed: $error")
    }
}
