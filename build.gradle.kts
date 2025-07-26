import com.google.cloud.tools.gradle.appengine.appyaml.AppEngineAppYamlExtension

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.gcloud.appengine)
}

group = "bose.ankush"
version = "0.0.1"

application {
    mainClass = "bose.ankush.ApplicationKt"

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

kotlin {
    jvmToolchain(16)
}

repositories {
    mavenCentral()
}

configure<AppEngineAppYamlExtension> {
    stage {
        setArtifact("build/libs/${project.name}-all.jar")
    }
    deploy {
        version = "GCLOUD_CONFIG"
        projectId = "GCLOUD_CONFIG"
    }
}

dependencies {
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.mongo.driver)
    implementation(libs.kotlin.mongo.bson)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.bcrypt)
    implementation(libs.ktor.server.html)
    implementation(libs.gcp.secret.manager)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}
