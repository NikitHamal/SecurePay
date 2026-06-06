# SecurePay Customer app ProGuard rules.
# Keep the DeviceAdminReceiver referenced from AndroidManifest + device_admin.xml.
-keep class com.securepay.customer.admin.SecurePayDeviceAdminReceiver { *; }

# Keep enums used for reactive state mapping.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
