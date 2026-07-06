plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.touchbase.user"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.touchbase.securepay.client"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.66:5173/api/\"")
            buildConfigField("String", "HMAC_SECRET", "\"dev-hmac-secret-change-in-release\"")
            buildConfigField("String", "SIGNING_CERT_HASH", "\"\"")
            buildConfigField("String", "FCM_PROJECT_ID", "\"spay-fintech\"")
            buildConfigField("String", "FCM_API_KEY", "\"AIzaSyARxW2ltAOVQGbjL-qx6TC0HODLoNrtF2w\"")
            buildConfigField("String", "FCM_SENDER_ID", "\"1055815727331\"")
            buildConfigField("String", "FCM_APPLICATION_ID", "\"\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "API_BASE_URL", "\"https://securepay-dashboard.pages.dev/api/\"")
            buildConfigField("String", "HMAC_SECRET", "\"${System.getenv("HMAC_SECRET") ?: ""}\"")
            buildConfigField("String", "SIGNING_CERT_HASH", "\"${System.getenv("SIGNING_CERT_HASH") ?: ""}\"")
            buildConfigField("String", "FCM_PROJECT_ID", "\"${System.getenv("FCM_PROJECT_ID") ?: ""}\"")
            buildConfigField("String", "FCM_API_KEY", "\"${System.getenv("FCM_API_KEY") ?: ""}\"")
            buildConfigField("String", "FCM_SENDER_ID", "\"${System.getenv("FCM_SENDER_ID") ?: ""}\"")
            buildConfigField("String", "FCM_APPLICATION_ID", "\"${System.getenv("FCM_APPLICATION_ID") ?: ""}\"")
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
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
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
