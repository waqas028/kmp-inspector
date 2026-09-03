package com.waqas028.kmpinspector.data

import android.database.Cursor
import androidx.room.PooledConnection
import androidx.room.RoomDatabase
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.db.SupportSQLiteDatabase
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
import java.io.File

/**
 * Reads a Room database's tables into the Database panel.
 *
 * Two read paths, chosen at runtime:
 * - **Support path** (`openHelper.readableDatabase`): the classic Android setup, where Room sits on
 *   `SupportSQLiteOpenHelper`. The framework `SQLiteDatabase` has its own connection pool, so
 *   reading here never blocks the app's own queries.
 * - **Driver path** (`useReaderConnection`): Room 2.7+ with a `SQLiteDriver`, where `openHelper`
 *   throws. Only used when the support path is unavailable, because on the support setup Room's
 *   connection API runs in a compatibility mode that holds the single connection exclusively
 *   and stalls every other query in the app for as long as the block runs.
 *
 * Room is a compileOnly dependency; every `androidx.room` reference stays in this file.
 */
internal object RoomCollector {

    private const val ROW_LIMIT = 500
    private const val TABLES_SQL =
        "SELECT name FROM sqlite_master WHERE type = 'table' " +
            "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata') " +
            "ORDER BY name"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun attach(database: RoomDatabase, fileName: String?) {
        val controller = Controller(database, fileName)
        controller.refresh()
        Inspector.onOpen { controller.refresh() }
    }

    /**
     * Writes use the same two paths as reads. After a raw UPDATE, Room's invalidation tracker is
     * poked so the app's own Flows and LiveData see the change — the triggers log it, but Room
     * only reads that log when asked.
     */
    private class Controller(
        private val database: RoomDatabase,
        private val fileName: String?,
    ) : DatabaseController {

        override fun refresh() {
            scope.launch { runCatching { snapshot() }.onFailure { report("refresh", it) } }
        }

        override fun updateCell(table: String, rowId: Long, column: String, value: String?) {
            scope.launch {
                runCatching {
                    val sql = "UPDATE `$table` SET `$column` = ? WHERE rowid = ?"
                    val support = runCatching { database.openHelper.writableDatabase }.getOrNull()
                    if (support != null) {
                        support.execSQL(sql, arrayOf<Any?>(value, rowId))
                    } else {
                        database.useWriterConnection { connection ->
                            connection.usePrepared(sql) { stmt ->
                                if (value == null) stmt.bindNull(1) else stmt.bindText(1, value)
                                stmt.bindLong(2, rowId)
                                stmt.step()
                            }
                        }
                    }
                    runCatching { database.invalidationTracker.refreshAsync() }
                    InspectorLog.i("Inspector", "$table.$column updated for rowid $rowId")
                    snapshot()
                }.onFailure { report("update $table.$column", it) }
            }
        }

        private suspend fun snapshot() {
            val support = runCatching { database.openHelper.readableDatabase }.getOrNull()
            val tables = support?.readTables()
                ?: database.useReaderConnection { connection -> connection.readTables() }
            val file = support?.path?.let(::File)
                ?: fileName?.let { inspectorContext()?.getDatabasePath(it) }
            Inspector.setDatabase(
                info = DbInfo(
                    fileName = fileName ?: file?.name ?: "room",
                    engine = "SQLite · Room",
                    sizeLabel = file?.takeIf { it.exists() }?.sizeLabel()
                        ?: "${tables.sumOf { it.rowCount }} rows",
                ),
                tables = tables,
                controller = this,
            )
        }

        private fun report(what: String, error: Throwable) {
            InspectorLog.e("Inspector", "Database $what failed: $error")
        }
    }

    // ---- Support path -------------------------------------------------------------------------

    private fun SupportSQLiteDatabase.readTables(): List<DbTable> {
        val names = query(TABLES_SQL).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return names.map { name ->
            val columns = query("PRAGMA table_info(`$name`)").use { c ->
                buildList { while (c.moveToNext()) add(DbColumn(c.getString(1), c.getString(2))) }
            }
            // rowid first so edits can address the row. WITHOUT ROWID tables reject it; those are
            // read plainly and stay read-only.
            val withRowId = runCatching { query(rowIdSelect(name)).use { c -> c.readRows(skipFirst = true) } }
            val (rowIds, rows) = withRowId.getOrElse {
                query("SELECT * FROM `$name` LIMIT $ROW_LIMIT").use { c -> c.readRows(skipFirst = false) }
            }
            DbTable(name = name, columns = columns, rows = rows, rowIds = rowIds)
        }
    }

    private fun Cursor.readRows(skipFirst: Boolean): Pair<List<Long>?, List<List<DbValue>>> {
        val ids = if (skipFirst) mutableListOf<Long>() else null
        val rows = buildList {
            while (moveToNext()) {
                ids?.add(getLong(0))
                add(
                    ((if (skipFirst) 1 else 0) until columnCount).map { i ->
                        when (getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> DbValue.Null
                            Cursor.FIELD_TYPE_INTEGER, Cursor.FIELD_TYPE_FLOAT -> DbValue.Number(getString(i))
                            Cursor.FIELD_TYPE_BLOB -> DbValue.Blob(getBlob(i).size.toLong())
                            else -> DbValue.Text(getString(i))
                        }
                    },
                )
            }
        }
        return ids to rows
    }

    private fun rowIdSelect(name: String) = "SELECT rowid AS _kmp_rowid, * FROM `$name` LIMIT $ROW_LIMIT"

    // ---- Driver path --------------------------------------------------------------------------

    private suspend fun PooledConnection.readTables(): List<DbTable> {
        val names = usePrepared(TABLES_SQL) { stmt -> buildList { while (stmt.step()) add(stmt.getText(0)) } }
        return names.map { name ->
            val columns = usePrepared("PRAGMA table_info(`$name`)") { stmt ->
                buildList { while (stmt.step()) add(DbColumn(stmt.getText(1), stmt.getText(2))) }
            }
            val withRowId = runCatching {
                usePrepared(rowIdSelect(name)) { stmt ->
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

    private fun androidx.sqlite.SQLiteStatement.readRow(from: Int): List<DbValue> =
        (from until getColumnCount()).map { i ->
            when (getColumnType(i)) {
                SQLITE_DATA_NULL -> DbValue.Null
                SQLITE_DATA_INTEGER, SQLITE_DATA_FLOAT -> DbValue.Number(getText(i))
                SQLITE_DATA_BLOB -> DbValue.Blob(getBlob(i).size.toLong())
                else -> DbValue.Text(getText(i))
            }
        }

    private fun File.sizeLabel(): String {
        val kb = length() / 1024.0
        return if (kb < 1024) "%.0f KB".format(kb) else "%.1f MB".format(kb / 1024)
    }
}
