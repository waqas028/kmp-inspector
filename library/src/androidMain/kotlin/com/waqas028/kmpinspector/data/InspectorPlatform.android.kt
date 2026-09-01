package com.waqas028.kmpinspector.data

import android.os.Build
import java.util.TimeZone

internal actual object InspectorPlatform {
    actual val name: String = "Android ${Build.VERSION.RELEASE}"
    actual val isAndroid: Boolean = true
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
    actual fun utcOffsetMillis(): Long =
        TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
}
