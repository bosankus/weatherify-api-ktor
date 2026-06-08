plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.syncling"
version = "0.0.1"

repositories {
    mavenCentral()
}

application {
    mainClass = "com.syncling.ApplicationKt"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.html)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.auth)
    implementation(libs.jedis)
    implementation(libs.lettuce.core)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.auth0.jwt)
    implementation(libs.logback.classic)
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.registry.prometheus)

    // Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // YAML
    implementation(libs.yamlkt)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)

    // DateTime (for routes that use kotlinx.datetime directly)
    implementation(libs.kotlinx.datetime)

    // PDF generation for branded invoices. Kept to kernel + layout only —
    // we hand-draw the layout, so font-asian / forms / sign / svg aren't needed.
    implementation("com.itextpdf:kernel:8.0.4")
    implementation("com.itextpdf:layout:8.0.4")

    // SMTP email delivery — Jakarta Mail 2.x implementation (Eclipse Angus).
    // No third-party SaaS required; works with any SMTP relay (Google Workspace,
    // self-hosted Postfix, AWS SES SMTP endpoint, etc.).
    implementation("org.eclipse.angus:angus-mail:2.0.4")
}

tasks.register<JavaExec>("previewLanding") {
    group = "application"
    description = "Boot a tiny preview server for the syncling landing page (no DB/Redis required)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.syncling.LandingPreviewKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("previewDashboard") {
    group = "application"
    description = "Boot a local preview server for the syncling dashboard (no DB/Redis/auth required). Visit http://localhost:8083/app."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.syncling.DashboardPreviewKt")
    standardInput = System.`in`
}

tasks.register<Exec>("stopPreviewDashboard") {
    group = "application"
    description = "Stop the local syncling dashboard preview server started by previewDashboard."
    commandLine("pkill", "-f", "com.syncling.DashboardPreviewKt")
    isIgnoreExitValue = true
    doLast {
        if (executionResult.get().exitValue == 0) logger.lifecycle("→ stopped dashboard preview server")
        else logger.lifecycle("→ no dashboard preview server was running")
    }
}

tasks.register<Exec>("stopPreviewLanding") {
    group = "application"
    description = "Stop the local syncling landing preview server started by previewLanding."
    commandLine("pkill", "-f", "com.syncling.LandingPreviewKt")
    isIgnoreExitValue = true
    doLast {
        if (executionResult.get().exitValue == 0) logger.lifecycle("→ stopped preview server")
        else logger.lifecycle("→ no preview server was running")
    }
}
