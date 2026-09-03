package com.waqas028.kmpinspector.data

import android.database.Cursor
import androidx.room.PooledConnection
import androidx.room.RoomDatabase
import androidx.room.useReaderConnection
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.db.SupportSQLiteDatabase
import com.waqas028.kmpinspector.Inspector
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
        snapshot(database, fileName)
        Inspector.onOpen { snapshot(database, fileName) }
    }

    private fun snapshot(database: RoomDatabase, fileName: String?) {
        scope.launch {
            runCatching {
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
                )
            }
        }
    }

    // ---- Support path -------------------------------------------------------------------------

    private fun SupportSQLiteDatabase.readTables(): List<DbTable> {
        val names = query(TABLES_SQL).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return names.map { name ->
            val columns = query("PRAGMA table_info(`$name`)").use { c ->
                buildList { while (c.moveToNext()) add(DbColumn(c.getString(1), c.getString(2))) }
            }
            val rows = query("SELECT * FROM `$name` LIMIT $ROW_LIMIT").use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            (0 until c.columnCount).map { i ->
                                when (c.getType(i)) {
                                    Cursor.FIELD_TYPE_NULL -> DbValue.Null
                                    Cursor.FIELD_TYPE_INTEGER, Cursor.FIELD_TYPE_FLOAT -> DbValue.Number(c.getString(i))
                                    Cursor.FIELD_TYPE_BLOB -> DbValue.Blob(c.getBlob(i).size.toLong())
                                    else -> DbValue.Text(c.getString(i))
                                }
                            },
                        )
                    }
                }
            }
            DbTable(name = name, columns = columns, rows = rows)
        }
    }

    // ---- Driver path --------------------------------------------------------------------------

    private suspend fun PooledConnection.readTables(): List<DbTable> {
        val names = usePrepared(TABLES_SQL) { stmt -> buildList { while (stmt.step()) add(stmt.getText(0)) } }
        return names.map { name ->
            val columns = usePrepared("PRAGMA table_info(`$name`)") { stmt ->
                buildList { while (stmt.step()) add(DbColumn(stmt.getText(1), stmt.getText(2))) }
            }
            val rows = usePrepared("SELECT * FROM `$name` LIMIT $ROW_LIMIT") { stmt ->
                buildList {
                    while (stmt.step()) {
                        add(
                            (0 until stmt.getColumnCount()).map { i ->
                                when (stmt.getColumnType(i)) {
                                    SQLITE_DATA_NULL -> DbValue.Null
                                    SQLITE_DATA_INTEGER, SQLITE_DATA_FLOAT -> DbValue.Number(stmt.getText(i))
                                    SQLITE_DATA_BLOB -> DbValue.Blob(stmt.getBlob(i).size.toLong())
                                    else -> DbValue.Text(stmt.getText(i))
                                }
                            },
                        )
                    }
                }
            }
            DbTable(name = name, columns = columns, rows = rows)
        }
    }

    private fun File.sizeLabel(): String {
        val kb = length() / 1024.0
        return if (kb < 1024) "%.0f KB".format(kb) else "%.1f MB".format(kb / 1024)
    }
}
