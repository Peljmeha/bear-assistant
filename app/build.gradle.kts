plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.bear.assistant"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.bear.assistant"
        minSdk = 29
        targetSdk = 34
        versionCode = run
        versionName = "0.$run"
    }
    signingConfigs {
        create("bear") {
            val path = System.getenv("KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("KS_PASSWORD")
                keyAlias = "bear"
                keyPassword = System.getenv("KS_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("bear")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
