plugins {
    id("com.android.application")
}

android {
    namespace = "com.vaan.collectivemachine"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vaan.collectivemachine.v2"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
