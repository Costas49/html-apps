plugins {
    id("com.android.application")
}
android {
    namespace = "gr.maga.vpn"
    compileSdk = 36
    defaultConfig {
        applicationId = "gr.maga.vpn"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("com.wireguard.android:tunnel:1.0.20260102")
}
