plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "textforge.navigation"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }
}

dependencies {
    implementation(project(":ui"))

    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.compose.runtime:runtime")
}
