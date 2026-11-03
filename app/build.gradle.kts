plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.divya.ai"
    compileSdk = 34
    defaultConfig { applicationId = "com.divya.ai"; minSdk = 26; targetSdk = 34; versionCode = 30; versionName = "2.12.0" }
    signingConfigs {
        getByName("debug") {
            storeFile = file("divya.keystore")
            storePassword = "divya123"
            keyAlias = "divya"
            keyPassword = "divya123"
        }
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Camera scan (Divya ki "aankhein")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // OCR (text padhna): Latin/English + Devanagari/Hindi, phone par hi (offline)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
}
