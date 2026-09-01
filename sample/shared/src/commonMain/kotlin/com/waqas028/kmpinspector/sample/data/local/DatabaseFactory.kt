package com.waqas028.kmpinspector.sample.data.local

/** Each platform knows where its database file lives; Room builds the same schema on all of them. */
internal expect fun createNewsDatabase(): NewsDatabase
