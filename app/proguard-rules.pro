# GENDEV Shorts ProGuard rules
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# WebView JS Bridge
-keepclassmembers class com.gendev.shorts.ui.components.EmbedPlayerBridge {
    @android.webkit.JavascriptInterface <methods>;
}
