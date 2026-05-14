# Commute Checker ProGuard Rules

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.commutecheck.app.data.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Google Play Services
-keep class com.google.android.gms.** { *; }
