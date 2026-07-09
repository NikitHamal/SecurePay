-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.coroutines.**

-keep class com.touchbase.agent.data.model.** { *; }

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
    *** EmptySerializersModule;
}

-dontwarn com.google.errorprone.annotations.**
-keep class com.google.errorprone.annotations.** { *; }

-dontwarn autovalue.shaded.**
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**