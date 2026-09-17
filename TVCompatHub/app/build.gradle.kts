plugins {
    id("com.android.application")
}

android {
    namespace = "gr.costas.tvcompathub"
    compileSdk = 36

    defaultConfig {
        applicationId = "gr.costas.tvcompathub"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.17.0")
}
