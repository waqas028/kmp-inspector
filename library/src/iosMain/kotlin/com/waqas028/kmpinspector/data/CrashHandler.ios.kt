@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package com.waqas028.kmpinspector.data

import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException

/**
 * Kotlin/Native calls this hook for an unhandled Kotlin exception, before terminating.
 *
 * It does NOT cover everything a crash report would: Objective-C or Swift exceptions raised outside
 * Kotlin, and hard signals such as SIGSEGV or SIGABRT, are not Kotlin exceptions and never reach
 * here. Those need a signal handler or a native crash reporter, which is out of scope for a debug
 * overlay.
 */
internal actual fun installPlatformCrashHandler(appPackagePrefix: String?) {
    setUnhandledExceptionHook { throwable ->
        runCatching {
            captureFatal(
                type = throwable::class.simpleName ?: "Throwable",
                message = throwable.message.orEmpty(),
                threadName = "main",
                frameLines = throwable.getStackTrace().toList(),
                appPackagePrefix = appPackagePrefix,
            )
        }
        // The hook is observation only: without this the exception counts as handled and the app
        // would limp on in an unknown state instead of crashing as it should.
        terminateWithUnhandledException(throwable)
    }
}
