# Proguard rules for SMS Bridge Pro
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.json.* <fields>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
