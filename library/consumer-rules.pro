# Consumer rules, shipped inside the AAR. Room, WorkManager, DataStore and OkHttp are compileOnly: a consumer
# that lacks one of them still has library classes that mention it, and a minified build fails on
# a missing class unless told the reference is expected.
-dontwarn androidx.room.**
-dontwarn androidx.sqlite.**
-dontwarn androidx.work.**
-dontwarn androidx.datastore.**
-dontwarn okhttp3.**
-dontwarn okio.**
