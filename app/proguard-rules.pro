# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
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

# ─────────────────────────────────────────────────────────────────────────────
# Release build rules (R8 is ON: isMinifyEnabled + isShrinkResources in build.gradle.kts).
# "Works in debug, breaks in release" shows up as a crash the first time a code path runs, so every
# release build is exercised end to end (all four roles) before it ships — see context/.

# kotlinx.serialization — every @Serializable class (the *Row DTOs, payloads) is (de)serialized by a
# generated $$serializer that R8 can't see through reflection alone; without these rules a release build
# compiles fine and then throws SerializationException on the first network call.
# https://github.com/Kotlin/kotlinx.serialization/blob/master/rules/common.pro
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.kalazacare.app.**$$serializer { *; }
-keepclassmembers class com.kalazacare.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.kalazacare.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep readable stack traces in the in-app crash screen.
-keepattributes SourceFile,LineNumberTable

# Drop chatty debug logging from the release build (errors/warnings are kept).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Ktor/Supabase reference optional JVM-only classes that don't exist on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
