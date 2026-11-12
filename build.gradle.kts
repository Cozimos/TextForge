dependencies {
    implementation(project(":navigation"))
}plugins {
    kotlin("jvm") version "2.2.20" apply false
    application apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}
