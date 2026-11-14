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
        versionCode = 24
        versionName = "0.24.0-smoked-capsule"
    }
    signingConfigs {
        create("persistentDebug") {
            storeFile = rootProject.file("liquidvolume-signing.jks")
            storePassword = System.getenv("LV_KEYSTORE_PASSWORD") ?: ""
            keyAlias = "liquidvolume"
            keyPassword = System.getenv("LV_KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("persistentDebug")
        }
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

