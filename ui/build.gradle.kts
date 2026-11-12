plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":reader"))
    implementation(project(":navigation"))
    implementation(project(":processing"))
    implementation(project(":integrations"))
}

kotlin {
    jvmToolchain(21)
}
