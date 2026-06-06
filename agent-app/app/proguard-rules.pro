-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.coroutines.**

-keep class com.securepay.agent.data.model.** { *; }

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
    *** EmptySerializersModule;
}