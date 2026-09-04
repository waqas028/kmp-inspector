package com.waqas028.kmpinspector.okhttp

import okhttp3.Interceptor
import okhttp3.Response

/** No-op twin of the real interceptor: passes every call straight through. */
class KmpInspectorInterceptor @JvmOverloads constructor(
    @Suppress("UNUSED_PARAMETER") maxBodyBytes: Long = 256L * 1024,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
