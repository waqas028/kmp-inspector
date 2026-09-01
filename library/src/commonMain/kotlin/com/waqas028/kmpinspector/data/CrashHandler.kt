package com.waqas028.kmpinspector.data

import com.waqas028.kmpinspector.domain.model.CrashRecord
import com.waqas028.kmpinspector.domain.model.StackFrame

/**
 * Installs a platform uncaught-exception handler. Called once, and only from
 * [com.waqas028.kmpinspector.Inspector.installCrashHandler].
 */
internal expect fun installPlatformCrashHandler(appPackagePrefix: String?)

/**
 * Shared by every platform's handler: turn a throwable into a record, store it, and flush to disk
 * before the process goes away.
 */
internal fun captureFatal(
    type: String,
    message: String,
    threadName: String,
    frameLines: List<String>,
    appPackagePrefix: String?,
) {
    val frames = frameLines.map { line ->
        // "App" frames are the ones you can act on; everything else is framework. Matching on the
        // host's package prefix is the only signal available without a symbol table.
        //
        // `contains`, not `startsWith`: JVM frames begin with the package, but Kotlin/Native frames
        // read "0  iosApp.debug.dylib  0x...  kfun:com.example.Foo#bar", so anchoring at the start
        // would classify every iOS frame as framework.
        StackFrame(line, isAppFrame = appPackagePrefix != null && line.contains(appPackagePrefix))
    }
    InspectorStore.addCrash(
        CrashRecord(
            id = InspectorPlatform.currentTimeMillis(),
            fatal = true,
            exceptionType = type,
            message = message,
            origin = frames.firstOrNull { it.isAppFrame }?.text ?: frames.firstOrNull()?.text.orEmpty(),
            threadName = threadName,
            frames = frames,
            timestampMillis = InspectorPlatform.currentTimeMillis(),
        ),
    )
}
