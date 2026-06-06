# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in proguard-android-optimize.txt.

# Keep Kotlin metadata for reflection-friendly behavior.
-keepattributes *Annotation*, InnerClasses, Signature

# Keep the DeviceAdminReceiver so the platform can instantiate it by name.
-keep class com.securepay.customer.admin.SecurePayDeviceAdminReceiver { *; }

# Keep enum values referenced across the domain model.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
