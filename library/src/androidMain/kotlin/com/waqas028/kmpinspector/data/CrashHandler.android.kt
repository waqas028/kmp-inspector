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
        // Always hand back to whoever was there before. Swallowing this would stop the app crashing
        // normally and would silence Crashlytics or any other reporter already installed.
        previous?.uncaughtException(thread, throwable)
    }
}
