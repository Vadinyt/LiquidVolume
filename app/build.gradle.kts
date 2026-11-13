plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.liquidvolume.module"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.liquidvolume.module"
        minSdk = 31
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-dev"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
}
