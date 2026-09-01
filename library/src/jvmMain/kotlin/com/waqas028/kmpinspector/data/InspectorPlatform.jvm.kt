package com.waqas028.kmpinspector.data

import java.util.TimeZone

internal actual object InspectorPlatform {
    actual val name: String = "Desktop (${System.getProperty("os.name")})"
    actual val isAndroid: Boolean = false
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
    actual fun utcOffsetMillis(): Long =
        TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
}
