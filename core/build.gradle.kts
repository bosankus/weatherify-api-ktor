plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.androidplay.core"
version = "0.0.1"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlin.mongo.driver)
    api(libs.kotlin.mongo.bson)
    implementation(libs.koin.core)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    // Used by SecretManager to reach GCP metadata server and Secret Manager REST API
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
}
