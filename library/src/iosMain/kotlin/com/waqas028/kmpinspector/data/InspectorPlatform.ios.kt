package com.waqas028.kmpinspector.data

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMT
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIDevice

internal actual object InspectorPlatform {
    actual val name: String = "iOS ${UIDevice.currentDevice.systemVersion}"
    actual val isAndroid: Boolean = false
    actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
    actual fun utcOffsetMillis(): Long = NSTimeZone.localTimeZone.secondsFromGMT * 1000L
}
