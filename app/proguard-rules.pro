# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ── Stack traces ─────────────────────────────────────────────────────
# Preserve line numbers for readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Strip verbose/debug logging in release builds ────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ── TensorFlow Lite / LiteRT ────────────────────────────────────────
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**

# ── OkHttp ───────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class okhttp3.Callback { *; }
-keep class okhttp3.Call { *; }

# ── ML Kit Face Detection ───────────────────────────────────────────
-keep class com.google.mlkit.vision.face.** { *; }
-dontwarn com.google.mlkit.**

# ── JCodec ───────────────────────────────────────────────────────────
-keep class org.jcodec.api.android.** { *; }
-dontwarn org.jcodec.**
