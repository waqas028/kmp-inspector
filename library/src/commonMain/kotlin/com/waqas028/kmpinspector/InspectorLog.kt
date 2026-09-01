package com.waqas028.kmpinspector

import com.waqas028.kmpinspector.data.InspectorStore
import com.waqas028.kmpinspector.domain.model.LogLevel

/** Log entry point. Lines land in a 2,000-entry ring buffer and show up in the Logs tab. */
object InspectorLog {
    fun v(tag: String, message: String) = InspectorStore.addLog(LogLevel.Verbose, tag, message)
    fun d(tag: String, message: String) = InspectorStore.addLog(LogLevel.Debug, tag, message)
    fun i(tag: String, message: String) = InspectorStore.addLog(LogLevel.Info, tag, message)
    fun w(tag: String, message: String) = InspectorStore.addLog(LogLevel.Warn, tag, message)
    fun e(tag: String, message: String) = InspectorStore.addLog(LogLevel.Error, tag, message)
}
