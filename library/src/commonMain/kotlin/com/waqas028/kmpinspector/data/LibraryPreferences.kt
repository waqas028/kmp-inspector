package com.waqas028.kmpinspector.data

/**
 * Best guess at whether a SharedPreferences file was written by a third-party SDK rather than the
 * app. Android does not record which code created a file, so this goes by name, which holds up
 * in practice: SDKs name their files after their own package (`com.google.android.gms…`) or a
 * fixed brand name (`FirebaseHeartBeat…`), while apps tend to use plain names (`session`).
 *
 * [appPackages] are the app's own package prefixes; a file under one of them is always the app's,
 * including Android's default `<package>_preferences`.
 */
internal fun isLibraryPreferenceFile(name: String, appPackages: Collection<String>): Boolean {
    if (appPackages.any { it.isNotEmpty() && name.startsWith(it) }) return false
    val lower = name.lowercase()
    if (LIBRARY_NAME_PREFIXES.any { lower.startsWith(it) }) return true
    // A reverse-domain name that is not the app's own: `androidx.work.util.preferences`,
    // `com.facebook.sdk.appEventPreferences`. Two segments at least, so `user.settings` stays the app's.
    val root = lower.substringBefore('.', missingDelimiterValue = "")
    return root in PACKAGE_ROOTS && lower.substringAfter('.').isNotEmpty()
}

/** First segments of package names; `app` and `me` are left out, since apps use them as words. */
private val PACKAGE_ROOTS = setOf(
    "com", "org", "net", "io", "co", "androidx", "android",
    "de", "fr", "jp", "cn", "ru", "br", "uk", "kr",
)

/** Fixed file names that SDKs use without a package, matched by prefix, lowercase. */
private val LIBRARY_NAME_PREFIXES = listOf(
    "firebase", "webviewchromium", "webviewprofile", "admob", "google_", "gtm",
    "appsflyer", "branch_referral", "clevertap", "onesignal", "multidex", "leakcanary",
    "crashlytics", "bugsnag", "mixpanel", "amplitude", "sentry", "datadog", "applovin",
    "ironsource", "chartboost", "vungle", "mopub", "inmobi", "tapjoy", "appcenter",
    "zendesk", "intercom", "braze", "appboy", "kochava", "moengage", "webengage",
    "freshchat", "helpshift",
)
