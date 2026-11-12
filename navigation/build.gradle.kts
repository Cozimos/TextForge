plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "textforge.navigation"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }
    compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":ui"))

    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.navigation:navigation-compose:2.9.5")
}
