package com.waqas028.kmpinspector.data

internal actual fun installPlatformCrashHandler(appPackagePrefix: String?) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            captureFatal(
                type = throwable::class.simpleName ?: "Throwable",
                message = throwable.message.orEmpty(),
                threadName = thread.name,
                frameLines = throwable.stackTrace.map { it.toString() },
                appPackagePrefix = appPackagePrefix,
            )
        }
        previous?.uncaughtException(thread, throwable)
    }
}
