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

# Google LiteRT & TensorFlow Lite JNI
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class com.google.ai.edge.litert.** {
    native <methods>;
    *;
}
-dontwarn com.google.ai.edge.litert.**
-dontwarn org.tensorflow.lite.**

# ML Kit Bundled Text Recognition & Optional Play Services / GenAI
-keep class com.google.mlkit.** { *; }
-keep class com.google.mlkit.genai.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.mlkit.genai.**
-dontwarn com.google.android.gms.**
-dontwarn javax.annotation.**

# Jetpack Glance AppWidgets & ActionCallbacks
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.action.ActionCallback { *; }
-keep class io.github.ultrapg.smartnote.integration.widgets.** { *; }
