plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.linkpilot.browser"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.linkpilot.browser"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
