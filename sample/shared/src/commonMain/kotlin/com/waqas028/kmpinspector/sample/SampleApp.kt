package com.waqas028.kmpinspector.sample

import com.waqas028.kmpinspector.sample.data.NewsRepository
import com.waqas028.kmpinspector.sample.data.local.NewsDatabase
import com.waqas028.kmpinspector.sample.data.local.createNewsDatabase
import com.waqas028.kmpinspector.sample.data.remote.SpaceflightApi
import io.ktor.client.HttpClient

/** Hand-rolled container. A real app would use a DI framework; a sample should stay readable. */
internal object SampleApp {
    val database: NewsDatabase by lazy { createNewsDatabase() }

    // Exactly one Ktor engine is on each platform's classpath, so it resolves itself.
    private val client: HttpClient by lazy { HttpClient() }

    val repository: NewsRepository by lazy { NewsRepository(database, SpaceflightApi(client)) }
}
