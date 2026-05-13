plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.androidplay.weatherify"
version = "0.0.1"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":core"))
    implementation(libs.koin.core)
    implementation(libs.kotlin.mongo.driver)
    implementation(libs.kotlin.mongo.bson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.logback.classic)
    implementation(libs.bcrypt)
}
