package com.waqas028.kmpinspector.room3

import androidx.room3.RoomDatabase
import com.waqas028.kmpinspector.Inspector

/**
 * Shows a Room 3 [database] in the Database panel on any platform:
 *
 * ```
 * RoomInspector.attach(database, fileName = "app.db")
 * ```
 *
 * Same call as the Room 2 artifact (`com.waqas028.kmpinspector.room.RoomInspector`), only the
 * import differs. Tables are read now, again each time the inspector opens, and on Refresh; cell
 * edits are written back and SQL queries run against the live database.
 */
object RoomInspector {
    fun attach(database: RoomDatabase, fileName: String? = null) {
        // No tables are read and no handle is kept when the inspector is switched off.
        if (!Inspector.enabled) return
        DriverRoomCollector(database, fileName).attach()
    }
}
