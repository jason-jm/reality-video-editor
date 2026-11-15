# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Release round R4: release builds keep warnings and errors (with their exceptions, for support) but drop info, debug and
# verbose logging, which carries clip names, file names and media URIs.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Release round R20: CompositionPlayerLeaks reads CompositionPlayer's internal players by this field's name to release the
# track selectors Media3 1.9.3 leaves registered with the Spatializer (the holder's player and the selector are found by type).
-keepclassmembernames class androidx.media3.transformer.CompositionPlayer {
    java.util.List playerHolders;
}
