plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":parser"))
    implementation(project(":storage"))
    implementation(project(":integrations"))
    implementation(project(":navigation"))
}

kotlin {
    jvmToolchain(21)
}
