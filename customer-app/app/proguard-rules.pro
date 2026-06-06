# SecurePay Customer app ProGuard rules.
# Keep the DeviceAdminReceiver referenced from AndroidManifest + device_admin.xml.
-keep class com.securepay.customer.admin.SecurePayDeviceAdminReceiver { *; }

# Keep enums used for reactive state mapping.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.Json {
    kotlinx.serialization.json.JsonConfiguration getConfiguration();
}
-keepclassmembers class kotlinx.serialization.json.JsonConfiguration {
    boolean getPrettyPrint();
    boolean getIsLenient();
    boolean getIgnoreUnknownKeys();
    boolean getSerializeSpecialFloatingPointValues();
    kotlinx.serialization.json.JsonConfiguration$Stability getStability();
}
-keepclassmembers class **$Serializer { *; }
-keep,class * implements kotlinx.serialization.KSerializer

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowshrinking,allowobfuscation class kotlin.Unit
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation interface retrofit2.Call
-keepclassmembers,allowshrinking interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Google Error Prone (Tink dependency via security-crypto)
-dontwarn com.google.errorprone.annotations.**
-keep class com.google.errorprone.annotations.** { *; }
