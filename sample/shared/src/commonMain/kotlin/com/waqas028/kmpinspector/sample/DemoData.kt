package com.waqas028.kmpinspector.sample

import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.InspectorLog
import com.waqas028.kmpinspector.domain.model.CrashRecord
import com.waqas028.kmpinspector.domain.model.DbColumn
import com.waqas028.kmpinspector.domain.model.DbInfo
import com.waqas028.kmpinspector.domain.model.DbTable
import com.waqas028.kmpinspector.domain.model.DbValue
import com.waqas028.kmpinspector.domain.model.HttpHeader
import com.waqas028.kmpinspector.domain.model.NetworkRequest
import com.waqas028.kmpinspector.domain.model.StackFrame
import com.waqas028.kmpinspector.domain.model.WorkJob
import com.waqas028.kmpinspector.domain.model.WorkState

/**
 * Seeds the inspector with the fixtures from the design handoff, so every section has something to
 * show. A real host app would feed [Inspector] from its own HTTP client, logger and scheduler.
 */
internal fun seedDemoData(nowMillis: Long) {
    Inspector.configure(appId = "com.example.shop", variant = "debug")

    fun at(secondsAgo: Long) = nowMillis - secondsAgo * 1000

    val json = """
        {"items":[{"id":8821,"title":"Selvedge denim jacket","price":12900,"currency":"GBP",
        "sizes":["S","M","L"],"inStock":true,"discount":null},{"id":8822,"title":"Raw denim jeans",
        "price":9900,"currency":"GBP","sizes":["30","32"],"inStock":false,"discount":15}],
        "page":1,"total":42}
    """.trimIndent().replace("\n", "")

    listOf(
        NetworkRequest(
            id = 1, method = "GET",
            url = "https://api.example.com/v2/products?category=denim&page=1",
            statusCode = 200, reasonPhrase = "OK", durationMillis = 142,
            requestBytes = 184, responseBytes = 18_400, protocol = "h2",
            timestampMillis = at(8), contentType = "application/json",
            requestHeaders = listOf(
                HttpHeader("accept", "application/json"),
                HttpHeader("authorization", "Bearer eyJhbGciOiJIUzI1NiJ9…"),
            ),
            responseHeaders = listOf(
                HttpHeader("content-type", "application/json; charset=utf-8"),
                HttpHeader("content-encoding", "gzip"),
                HttpHeader("cache-control", "max-age=60"),
            ),
            responseBody = json,
        ),
        NetworkRequest(
            id = 2, method = "POST", url = "https://api.example.com/v2/cart/items",
            statusCode = 201, reasonPhrase = "Created", durationMillis = 389,
            requestBytes = 96, responseBytes = 612, protocol = "h2", timestampMillis = at(35),
            contentType = "application/json",
            requestBody = """{"productId":8821,"qty":1}""",
            responseBody = """{"cartId":"c_4410","items":1,"subtotal":12900}""",
        ),
        NetworkRequest(
            id = 3, method = "GET", url = "https://api.example.com/v2/products/8821/reviews",
            statusCode = 304, reasonPhrase = "Not Modified", durationMillis = 41,
            requestBytes = 88, responseBytes = 0, protocol = "h2", timestampMillis = at(72),
        ),
        NetworkRequest(
            id = 4, method = "PATCH", url = "https://api.example.com/v2/cart/items/4410",
            statusCode = 409, reasonPhrase = "Conflict", durationMillis = 220,
            requestBytes = 64, responseBytes = 188, protocol = "h2", timestampMillis = at(96),
            contentType = "application/json",
            responseBody = """{"error":"quantity_unavailable","available":0}""",
        ),
        NetworkRequest(
            id = 5, method = "GET",
            url = "https://api.example.com/v2/recommendations?slot=home_hero",
            statusCode = 500, reasonPhrase = "Internal Server Error", durationMillis = 1_200,
            requestBytes = 72, responseBytes = 96, protocol = "h2", timestampMillis = at(128),
            contentType = "application/json",
            responseBody = """{"error":"upstream_timeout"}""",
        ),
        NetworkRequest(
            id = 6, method = "POST", url = "https://api.example.com/oauth/token",
            statusCode = 200, reasonPhrase = "OK", durationMillis = 96,
            requestBytes = 140, responseBytes = 740, protocol = "h2", timestampMillis = at(160),
        ),
        NetworkRequest(
            id = 7, method = "GET", url = "https://api.example.com/v2/orders?status=open",
            statusCode = null, durationMillis = 15_000,
            requestBytes = 80, responseBytes = 0, timestampMillis = at(200),
            errorText = "SocketTimeoutException: timeout after 15000ms",
        ),
        NetworkRequest(
            id = 8, method = "DELETE", url = "https://api.example.com/v2/cart/items/4408",
            statusCode = 204, reasonPhrase = "No Content", durationMillis = 110,
            requestBytes = 60, responseBytes = 0, protocol = "h2", timestampMillis = at(224),
        ),
    ).reversed().forEach(Inspector::recordRequest) // store prepends, so push oldest first

    InspectorLog.i("CartStore", "Cart restored from disk: 2 items, subtotal 22800")
    InspectorLog.d("HttpCache", "Hit for /v2/products?category=denim&page=1 (age 12s)")
    InspectorLog.v("Compose", "Recomposed ProductGrid (3 items changed)")
    InspectorLog.w("Checkout", "Price formatting fell back to locale default; expected en-GB")
    InspectorLog.e("Checkout", "Failed to parse price \"12,900\" — see CheckoutViewModel.kt:118")
    InspectorLog.i("Sync", "Enqueued periodic sync, next run in 6h")

    Inspector.recordCrash(
        CrashRecord(
            id = 101, fatal = true,
            exceptionType = "IllegalStateException",
            message = "Cannot proceed to payment: cart total is null after applying promotion PROMO_DENIM20.",
            origin = "CheckoutViewModel.kt:118",
            occurrences = 7,
            causedBy = "Caused by: NumberFormatException: For input string: \"12,900\"",
            timestampMillis = at(4),
            frames = listOf(
                StackFrame("com.example.shop.checkout.CheckoutViewModel.confirm(CheckoutViewModel.kt:118)", true),
                StackFrame("com.example.shop.checkout.CheckoutScreen\$onPay\$1.invoke(CheckoutScreen.kt:64)", true),
                StackFrame("androidx.compose.foundation.ClickableKt\$clickable\$4.invoke(Clickable.kt:154)", false),
                StackFrame("kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:104)", false),
                StackFrame("android.os.Looper.loop(Looper.java:294)", false),
            ),
        ),
    )
    Inspector.recordNonFatal(
        exceptionType = "JsonDecodingException",
        message = "Unexpected null for field 'discount' at offset 214; defaulted to 0.",
        origin = "ProductMapper.kt:41",
        frames = listOf(
            StackFrame("com.example.shop.data.ProductMapper.map(ProductMapper.kt:41)", true),
            StackFrame("kotlinx.serialization.json.internal.StreamingJsonDecoder.decode(StreamingJsonDecoder.kt:96)", false),
        ),
    )
    Inspector.recordNonFatal(
        exceptionType = "SocketTimeoutException",
        message = "timeout after 15000ms fetching /v2/orders?status=open",
        origin = "OrdersRepository.kt:73",
        frames = listOf(
            StackFrame("com.example.shop.data.OrdersRepository.load(OrdersRepository.kt:73)", true),
            StackFrame("io.ktor.client.engine.okhttp.OkHttpEngine.execute(OkHttpEngine.kt:112)", false),
        ),
    )

    Inspector.setWork(
        listOf(
            WorkJob(
                id = "9f2c-4d81", name = "SyncRecommendationsWorker", state = WorkState.Failed,
                tag = "sync", attempt = 3, lastRunMillis = at(300),
                nextRun = "12:34:26 (backoff, exponential 30s)",
                constraints = listOf("NETWORK: CONNECTED", "BATTERY_NOT_LOW"),
                inputData = listOf("slot" to "home_hero", "locale" to "en-GB"),
                failureReason = "HTTP 500 from /v2/recommendations after 3 attempts. " +
                    "Result.retry() returned; exponential backoff 30s → 60s → 120s.",
            ),
            WorkJob(
                id = "3a71-9c02", name = "CatalogRefreshWorker", state = WorkState.Succeeded,
                tag = "catalog", attempt = 1, lastRunMillis = at(1_800),
                nextRun = "18:00:00 (periodic, 6h)",
                constraints = listOf("NETWORK: CONNECTED", "IDLE"),
                inputData = listOf("since" to "2026-08-31T18:00:00Z"),
                outputData = listOf("updated" to "1284", "removed" to "17"),
            ),
            WorkJob(
                id = "b0d4-11ae", name = "UploadCartAnalyticsWorker", state = WorkState.Running,
                tag = "analytics", attempt = 1, lastRunMillis = at(20), nextRun = "— (running)",
                constraints = listOf("NETWORK: CONNECTED"),
            ),
            WorkJob(
                id = "77e1-52bb", name = "PrefetchImagesWorker", state = WorkState.Enqueued,
                tag = "media", attempt = 1, nextRun = "on constraints met",
                constraints = listOf("CHARGING", "STORAGE_NOT_LOW", "IDLE"),
            ),
            WorkJob(
                id = "12c9-7f40", name = "LegacyMigrationWorker", state = WorkState.Cancelled,
                tag = "migration", attempt = 2, lastRunMillis = at(5_400),
                failureReason = "Cancelled by CancelWorkById after the migration flag was disabled remotely.",
            ),
        ),
    )

    Inspector.setDatabase(
        info = DbInfo("app.db", "SQLDelight", "2.1 MB"),
        tables = listOf(
            DbTable(
                name = "order_items",
                columns = listOf(
                    DbColumn("id", "INTEGER PK"),
                    DbColumn("order_id", "INTEGER"),
                    DbColumn("sku", "TEXT"),
                    DbColumn("qty", "INTEGER"),
                    DbColumn("note", "TEXT NULL"),
                    DbColumn("thumb", "BLOB"),
                ),
                rows = listOf(
                    listOf(
                        DbValue.Number("1"), DbValue.Number("4410"), DbValue.Text("DNM-JKT-M"),
                        DbValue.Number("1"), DbValue.Text("gift wrap, no receipt"), DbValue.Blob(12_400),
                    ),
                    listOf(
                        DbValue.Number("2"), DbValue.Number("4410"), DbValue.Text("DNM-JNS-32"),
                        DbValue.Number("2"), DbValue.Null, DbValue.Blob(9_800),
                    ),
                    listOf(
                        DbValue.Number("3"), DbValue.Number("4408"), DbValue.Text("TEE-BLK-L"),
                        DbValue.Number("1"), DbValue.Text(""), DbValue.Blob(4_100),
                    ),
                ),
            ),
            DbTable(
                name = "products",
                columns = listOf(
                    DbColumn("id", "INTEGER PK"),
                    DbColumn("title", "TEXT"),
                    DbColumn("price", "INTEGER"),
                ),
                rows = listOf(
                    listOf(DbValue.Number("8821"), DbValue.Text("Selvedge denim jacket"), DbValue.Number("12900")),
                    listOf(DbValue.Number("8822"), DbValue.Text("Raw denim jeans"), DbValue.Number("9900")),
                ),
            ),
            DbTable(name = "sync_state", columns = listOf(DbColumn("key", "TEXT PK")), rows = emptyList()),
        ),
    )
}

/** Wall-clock now, so demo timestamps look live rather than frozen at build time. */
internal expect fun nowMillis(): Long
