package com.waqas028.kmpinspector.room3

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.waqas028.kmpinspector.domain.model.DbValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Entity
class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String?,
    val score: Double,
    val icon: ByteArray?,
)

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: Note): Long

    @Query("SELECT title FROM Note ORDER BY id")
    fun titles(): Flow<List<String>>
}

@Database(entities = [Note::class], version = 1, exportSchema = false)
abstract class TestDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}

/** Runs the Room 3 reader against a real in-memory Room 3 database on the desktop. */
class DriverRoomCollectorTest {

    private lateinit var database: TestDatabase
    private lateinit var collector: DriverRoomCollector

    @BeforeTest
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder<TestDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        database.noteDao().insert(Note(title = "first", body = "hello", score = 1.5, icon = byteArrayOf(1, 2, 3)))
        database.noteDao().insert(Note(title = "second", body = null, score = 2.0, icon = null))
        collector = DriverRoomCollector(database, fileName = "test.db")
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun tables_lists_the_app_tables_with_columns_rows_and_row_ids() = runBlocking {
        val tables = collector.tables()

        // Room's own room_master_table stays hidden.
        assertEquals(listOf("Note"), tables.map { it.name })
        val note = tables.single()
        assertEquals(listOf("id", "title", "body", "score", "icon"), note.columns.map { it.name })
        assertEquals(listOf(1L, 2L), note.rowIds)
        assertEquals(2, note.rows.size)
        val first = note.rows[0]
        assertEquals(DbValue.Text("first"), first[1])
        assertEquals(DbValue.Text("hello"), first[2])
        assertEquals(DbValue.Number("1.5"), first[3])
        assertEquals(DbValue.Blob(3), first[4])
        assertEquals(DbValue.Null, note.rows[1][2])
    }

    @Test
    fun query_runs_sql_against_the_live_database() {
        val result = collector.query("SELECT title, score FROM Note WHERE score > 1.8")

        assertEquals(listOf("title", "score"), result.columns.map { it.name })
        assertEquals(listOf(listOf(DbValue.Text("second"), DbValue.Number("2.0"))), result.rows)
    }

    @Test
    fun a_cell_edit_is_written_and_the_apps_own_flow_sees_it() = runBlocking {
        val rowId = assertNotNull(collector.tables().single().rowIds).first()

        // Start watching before the edit, the way an app screen would, so the test proves a Flow
        // that is already running gets the change, not just that a fresh query reads it.
        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        val watcher = launch { database.noteDao().titles().collect { emissions.send(it) } }
        assertEquals(listOf("first", "second"), withTimeout(5_000) { emissions.receive() })

        collector.updateCell(table = "Note", rowId = rowId, column = "title", value = "edited")

        val titles = withTimeout(5_000) {
            var latest = emissions.receive()
            while ("edited" !in latest) latest = emissions.receive()
            latest
        }
        watcher.cancel()
        assertEquals(listOf("edited", "second"), titles)
        assertTrue(collector.query("SELECT title FROM Note WHERE id = 1").rows.single().contains(DbValue.Text("edited")))
    }
}
