plugins {
    id("com.android.application")
}

android {
    namespace = "gr.costas.tvfilemanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "gr.costas.tvfilemanager"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
}
