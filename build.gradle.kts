import com.google.cloud.tools.gradle.appengine.appyaml.AppEngineAppYamlExtension

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.gcloud.appengine)
    jacoco
}

group = "bose.ankush"
version = "0.0.1"

application {
    mainClass = "bose.ankush.ApplicationKt"

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=$isDevelopment",
        "-Xms512m",
        "-Xmx1g",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=200",
        "-XX:+UseStringDeduplication"
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
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
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.bcrypt)
    implementation(libs.ktor.server.html)
    implementation(libs.gcp.secret.manager)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Koin for dependency injection
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Firebase Admin SDK for push notifications
    implementation(libs.firebase.admin)

    // PDF Generation
    implementation("com.itextpdf:itext7-core:8.0.4")

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

jacoco {
    toolVersion = "0.8.8"
}

tasks.test {
    // Force tests to always run and not be skipped as up-to-date
    outputs.upToDateWhen { false }

    // Enable JaCoCo agent for test coverage
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco"))
    }

    // Ensure the report is always generated
    outputs.upToDateWhen { false }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/data/model/**",
                    "**/Application*"
                )
            }
        })
    )
}
