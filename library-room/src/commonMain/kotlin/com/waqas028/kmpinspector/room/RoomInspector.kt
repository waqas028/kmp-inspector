package com.waqas028.kmpinspector.room

import androidx.room.RoomDatabase
import com.waqas028.kmpinspector.Inspector

/**
 * Shows [database] in the Database panel on any platform:
 *
 * ```
 * RoomInspector.attach(database, fileName = "app.db")
 * ```
 *
 * Tables are read now, again each time the inspector opens, and on Refresh; cell edits are written
 * back and SQL queries run against the live database. On Android this is the same collector as
 * `KmpInspector.attach`; on desktop and iOS it uses Room's connection API.
 */
object RoomInspector {
    fun attach(database: RoomDatabase, fileName: String? = null) {
        // No tables are read and no handle is kept when the inspector is switched off.
        if (!Inspector.enabled) return
        attachRoom(database, fileName)
    }
}

internal expect fun attachRoom(database: RoomDatabase, fileName: String?)
