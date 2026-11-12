plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":ui"))
}

kotlin {
    jvmToolchain(21)
}
