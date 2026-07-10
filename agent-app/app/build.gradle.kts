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
val releaseRequested = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
if (releaseRequested && (hmacSecret.isBlank() || signingCertHash.isBlank())) {
    throw GradleException("Release build requires TB_HMAC_SECRET and TB_SIGNING_CERT_HASH")
}

android {
    namespace = "com.touchbase.agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.touchbase.agent"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            val debugUrl = configured("TB_DEBUG_API_BASE_URL", "http://10.0.2.2:5173/api/")
            buildConfigField("String", "API_BASE_URL", buildConfigString(debugUrl))
            buildConfigField("String", "HMAC_SECRET", buildConfigString(hmacSecret))
            buildConfigField("String", "SIGNING_CERT_HASH", buildConfigString(signingCertHash))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "API_BASE_URL", buildConfigString(apiBaseUrl))
            buildConfigField("String", "HMAC_SECRET", buildConfigString(hmacSecret))
            buildConfigField("String", "SIGNING_CERT_HASH", buildConfigString(signingCertHash))
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            pickFirsts += "/META-INF/services/*"
            pickFirsts += "/META-INF/*.kotlin_module"
            pickFirsts += "/META-INF/DEPENDENCIES"
            pickFirsts += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.accompanist:accompanist-swiperefresh:0.34.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.google.zxing:core:3.5.3")

    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}