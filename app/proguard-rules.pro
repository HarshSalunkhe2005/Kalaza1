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
# kotlinx.serialization — required once isMinifyEnabled is turned on.
# Every @Serializable class here (the *Row DTOs in SupabaseDataRepositories.kt/
# SupabaseAuthRepository.kt, MedicationEvidenceRow, etc.) is (de)serialized by
# a generated $$serializer that R8 can't see through reflection alone; without
# these rules a release build would compile fine and then throw
# SerializationException at runtime the first time it hits the network —
# exactly the kind of "works in debug, breaks in release" gap that makes
# isMinifyEnabled risky to flip without testing. Not currently in effect
# (isMinifyEnabled is still false) — added ahead of time so enabling it later
# is a smaller, better-understood step. Standard rules per kotlinx.serialization's
# own docs: https://github.com/Kotlin/kotlinx.serialization/blob/master/rules/common.pro
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.kalazacare.app.**$$serializer { *; }
-keepclassmembers class com.kalazacare.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.kalazacare.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
