# Consumer rules, shipped inside the AAR. Room, WorkManager and OkHttp are compileOnly: a consumer
# that lacks one of them still has library classes that mention it, and a minified build fails on
# a missing class unless told the reference is expected.
-dontwarn androidx.room.**
-dontwarn androidx.sqlite.**
-dontwarn androidx.work.**
-dontwarn okhttp3.**
-dontwarn okio.**
