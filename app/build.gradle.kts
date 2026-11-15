plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.messagevault"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.messagevault"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
    }

    // A fixed signing key. Without it every GitHub build is signed with a new random key and Android
    // refuses to install the new APK over the old one (you would have to uninstall and lose all saved
    // messages). Keep the repository PRIVATE because this key file is in it.
    val vaultKeystore = file("debug.keystore")
    if (vaultKeystore.exists()) {
        signingConfigs.getByName("debug") {
            storeFile = vaultKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // collectAsStateWithLifecycle() lives in this artifact (it was missing before)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Reads WhatsApp's folder (statuses, documents) after the user grants access to it
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
