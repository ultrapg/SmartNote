# Room & BundledSQLiteDriver
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-keep class androidx.sqlite.driver.bundled.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.ultrapg.smartnote.**$$serializer { *; }
-keepclassmembers class io.github.ultrapg.smartnote.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.ultrapg.smartnote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Android AppFunctions (KSP generated service, inventory, schemas)
-keep class io.github.ultrapg.smartnote.gemini.** { *; }
-keep class androidx.appfunctions.** { *; }
-keep @androidx.appfunctions.AppFunctionSerializable class * { *; }
-keepclassmembers class * {
    @androidx.appfunctions.service.AppFunction <methods>;
}

# Google LiteRT & TensorFlow Lite JNI
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class com.google.ai.edge.litert.** {
    native <methods>;
    *;
}
-dontwarn com.google.ai.edge.litert.**
-dontwarn org.tensorflow.lite.**

# ML Kit Bundled Text Recognition
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
-dontwarn javax.annotation.**
