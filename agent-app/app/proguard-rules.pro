# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in $ANDROID_SDK/tools/proguard/proguard-android.txt

# Keep Kotlin metadata for reflection-free coroutines/Compose.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.coroutines.**

# Keep data/domain models intended for (de)serialization on the wire.
-keep class com.securepay.agent.data.model.** { *; }
