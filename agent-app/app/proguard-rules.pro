-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.coroutines.**

-keep class com.touchbase.agent.data.model.** { *; }

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
    *** EmptySerializersModule;
}

-dontwarn com.google.errorprone.annotations.**
-keep class com.google.errorprone.annotations.** { *; }

-keep class com.google.common.util.concurrent.** { *; }
-keep class com.google.common.base.** { *; }