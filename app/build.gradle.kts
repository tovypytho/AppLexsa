plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.tovypytho.applexsa"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.tovypytho.applexsa"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        getByName("debug") {
            System.getenv("LEXSA_KEYSTORE")?.let {
                storeFile = file(it)
                storePassword = System.getenv("LEXSA_STORE_PASSWORD")
                keyAlias = System.getenv("LEXSA_KEY_ALIAS")
                keyPassword = System.getenv("LEXSA_KEY_PASSWORD")
            }
        }
    }
    buildFeatures { viewBinding = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.datastore:datastore-preferences:1.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
