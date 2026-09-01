package com.waqas028.kmpinspector.sample

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
