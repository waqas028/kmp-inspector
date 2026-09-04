package com.waqas028.kmpinspector

/** No-op twin of the real [InspectorLog]. */
@Suppress("UNUSED_PARAMETER")
object InspectorLog {
    fun v(tag: String, message: String) = Unit
    fun d(tag: String, message: String) = Unit
    fun i(tag: String, message: String) = Unit
    fun w(tag: String, message: String) = Unit
    fun e(tag: String, message: String) = Unit
}
