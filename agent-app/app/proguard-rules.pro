# Add project specific ProGuard rules here.

# Keep Compose runtime metadata
-keep class androidx.compose.** { *; }

# Keep Kotlin metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# CameraX
-keep class androidx.camera.** { *; }

# Keep enrollment data models
-keep class com.securepay.agent.data.model.** { *; }
