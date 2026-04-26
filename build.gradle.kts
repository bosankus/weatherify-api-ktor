plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
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
    implementation(libs.ktor.server.compression)
    implementation(libs.bcrypt)
    implementation(libs.ktor.server.html)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Koin for dependency injection
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Redis client (Lettuce — async, coroutine-friendly)
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    // Firebase Admin SDK for push notifications
    implementation(libs.firebase.admin) {
        // Exclude GCP storage and Firestore — not used, just push notifications
        exclude(group = "com.google.cloud", module = "google-cloud-storage")
        exclude(group = "com.google.cloud", module = "google-cloud-firestore")
        // Exclude duplicate Guava (pulled by multiple GCP libs — Ktor's HttpClient handles HTTP)
        exclude(group = "com.google.guava", module = "listenablefuture")
    }
    // firebase-admin's ApiClientUtils.getDefaultJsonFactory() calls JacksonFactory directly.
    // Previously pulled in transitively via google-cloud-storage / google-cloud-firestore (now excluded),
    // so it must be declared explicitly or FirebaseOptions.build() throws NoClassDefFoundError at startup.
    implementation("com.google.http-client:google-http-client-jackson2:1.43.3")

    // PDF Generation — only the modules actually used (kernel, layout, barcodes).
    // Avoids pulling in itext7-core which brings font-asian (2.7M), hyph (964K),
    // styled-xml-parser, svg, pdfa, pdfua, sign, forms — none of which are used.
    implementation("com.itextpdf:kernel:8.0.4")
    implementation("com.itextpdf:layout:8.0.4")
    implementation("com.itextpdf:barcodes:8.0.4")

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
