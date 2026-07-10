plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun configured(name: String, fallback: String = ""): String =
    providers.gradleProperty(name)
        .orElse(providers.environmentVariable(name))
        .orElse(fallback)
        .get()

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val apiBaseUrl = configured("TB_API_BASE_URL", "https://securepay-dashboard.pages.dev/api/")
val hmacSecret = configured("TB_HMAC_SECRET")
val signingCertHash = configured("TB_SIGNING_CERT_HASH")
val fcmProjectId = configured("TB_FCM_PROJECT_ID")
val fcmApiKey = configured("TB_FCM_API_KEY")
val fcmSenderId = configured("TB_FCM_SENDER_ID")
val fcmApplicationId = configured("TB_FCM_APPLICATION_ID")
val releaseRequested = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
if (releaseRequested && (hmacSecret.isBlank() || signingCertHash.isBlank())) {
    throw GradleException("Release build requires TB_HMAC_SECRET and TB_SIGNING_CERT_HASH via environment or Gradle properties")
}

android {
    namespace = "com.touchbase.user"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.touchbase.securepay.client"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "API_BASE_URL", buildConfigString(apiBaseUrl))
        buildConfigField("String", "HMAC_SECRET", buildConfigString(hmacSecret))
        buildConfigField("String", "SIGNING_CERT_HASH", buildConfigString(signingCertHash))
        buildConfigField("String", "FCM_PROJECT_ID", buildConfigString(fcmProjectId))
        buildConfigField("String", "FCM_API_KEY", buildConfigString(fcmApiKey))
        buildConfigField("String", "FCM_SENDER_ID", buildConfigString(fcmSenderId))
        buildConfigField("String", "FCM_APPLICATION_ID", buildConfigString(fcmApplicationId))
    }

    buildTypes {
        debug {
            val debugUrl = configured("TB_DEBUG_API_BASE_URL", "http://10.0.2.2:5173/api/")
            buildConfigField("String", "API_BASE_URL", buildConfigString(debugUrl))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.firebase:firebase-messaging:24.0.0")
    implementation("com.google.firebase:firebase-common:21.0.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
