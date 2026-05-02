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

# --- Crashlytics: keep line numbers, hide original filenames ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Firestore: data classes are mapped by reflection on field names ---
-keep class com.cycling.workitout.data.firestore.** { *; }

# --- kotlinx-serialization ---
# The plugin generates a `$$serializer` companion for every @Serializable class.
# Kotlin reflection looks it up by name, so R8 must not rename/remove it.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep `INSTANCE` for object serializers + the generated $$serializer for any class we mark @Serializable
-keep,includedescriptorclasses class com.cycling.workitout.**$$serializer { *; }
-keepclassmembers class com.cycling.workitout.** {
    *** Companion;
}
-keepclasseswithmembers class com.cycling.workitout.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit DTOs (belt-and-suspenders; modern Retrofit usually handles this) ---
-keep class com.cycling.workitout.data.network.strava.dto.** { *; }
-keep class com.cycling.workitout.data.network.anthropic.dto.** { *; }
-keep class com.cycling.workitout.data.ai.AiWorkoutDtos$** { *; }
