# SecurePay Customer app ProGuard rules.
#
# CRITICAL: Android's device-provisioning system instantiates several classes
# by reflection (launched from the manifest / by component name). If R8 renames
# or strips them, provisioning fails with "something went wrong" — only in
# RELEASE builds (debug never minifies). These keep rules are mandatory.

# --- Manifest-referenced classes instantiated by the Android framework ---
-keep class com.touchbase.user.SecurePayApplication { *; }
-keep class com.touchbase.user.MainActivity { *; }
-keep class com.touchbase.user.admin.GetProvisioningModeActivity { *; }
-keep class com.touchbase.user.admin.PolicyComplianceActivity { *; }
-keep class com.touchbase.user.admin.ProvisioningActivity { *; }
-keep class com.touchbase.user.admin.ProvisioningFinalizer { *; }
-keep class com.touchbase.user.admin.SecurePayDeviceAdminReceiver { *; }
-keep class com.touchbase.user.worker.BootReceiver { *; }
-keep class com.touchbase.user.worker.FcmService { *; }
-keep class com.touchbase.user.ui.lock.LockTaskActivity { *; }

# Keep ALL Activities, Receivers, Services, Application classes referenced in
# the manifest — R8 can't always prove they're reachable from code.
-keep public class * extends androidx.activity.ComponentActivity
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.admin.DeviceAdminReceiver
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Service

# --- Enums used in reactive state mapping ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- kotlinx-serialization ---
# Keep the @Serializable classes themselves (R8 can otherwise rename the
# class so the generated Serializer can't find it on decode).
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.touchbase.user.data.model.** { *; }
-keepclassmembers class com.touchbase.user.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.touchbase.user.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
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

# --- Retrofit ---
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

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# --- Jetpack Security (EncryptedSharedPreferences / Tink) ---
# Tink has optional code paths for REMOTE key fetching (via Google HTTP + Joda)
# that reference classes not present in this app's dependency tree. We only use
# Tink for LOCAL key storage, so these references are never exercised. Without
# -dontwarn, R8 fails the release build with "Missing class" errors.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.client.json.**
-dontwarn com.google.api.client.googleapis.**
-dontwarn com.google.api.client.util.**
-dontwarn org.joda.time.**
-keep class com.google.errorprone.annotations.** { *; }
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class com.google.crypto.tink.** { *; }

# --- Firebase / FCM (standalone without google-services plugin) ---
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# --- Jetpack Compose runtime (defensive: lambda/class merging can break it) ---
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
