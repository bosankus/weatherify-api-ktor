package com.androidplay.core.secrets

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("SecretManager")

private val httpClient by lazy {
    HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 2_000
            requestTimeoutMillis = 5_000
            socketTimeoutMillis = 2_000
        }
    }
}

// Resolved once per process — project ID and token never change within a Cloud Run instance.
private val gcpProjectId: String? by lazy { resolveProjectId() }
private val gcpAccessToken: String? by lazy { gcpProjectId?.let { fetchAccessToken() } }

private fun resolveProjectId(): String? {
    val fromEnv = System.getenv("GCP_PROJECT_ID") ?: System.getenv("GOOGLE_CLOUD_PROJECT")
    if (!fromEnv.isNullOrEmpty()) return fromEnv
    return runBlocking {
        withTimeoutOrNull(5.seconds) {
            val res = httpClient.get("http://metadata.google.internal/computeMetadata/v1/project/project-id") {
                header("Metadata-Flavor", "Google")
            }
            res.bodyAsText().trim().takeIf { it.isNotBlank() }
        }
    }.also { if (it == null) log.warn("Could not resolve GCP project ID — Secret Manager unavailable") }
}

private fun fetchAccessToken(): String? {
    return runBlocking {
        withTimeoutOrNull(10.seconds) {
            val res = httpClient.get("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token") {
                header("Metadata-Flavor", "Google")
            }
            val json = res.bodyAsText()
            json.substringAfter("\"access_token\":\"").substringBefore("\"").takeIf { it.isNotBlank() }
        }
    }.also { if (it == null) log.warn("Could not fetch GCP access token from metadata server") }
}

private fun fetchFromSecretManager(secretName: String): String? {
    val projectId = gcpProjectId ?: return null
    val token = gcpAccessToken ?: return null
    return runBlocking {
        withTimeoutOrNull(10.seconds) {
            val url = "https://secretmanager.googleapis.com/v1/projects/$projectId/secrets/$secretName/versions/latest:access"
            val res = httpClient.get(url) { header("Authorization", "Bearer $token") }
            if (res.status.value != 200) return@withTimeoutOrNull null
            val base64 = Json.parseToJsonElement(res.bodyAsText())
                .jsonObject["payload"]?.jsonObject?.get("data")?.jsonPrimitive?.content
                ?: return@withTimeoutOrNull null
            java.util.Base64.getDecoder().decode(base64).toString(Charsets.UTF_8)
        }
    }
}

/**
 * Resolves a secret value with the following priority:
 * 1. Environment variable (instant — preferred on Cloud Run)
 * 2. GCP Secret Manager REST API (project ID and access token cached per process)
 * 3. Local development fallback
 */
fun getSecretValue(secretName: String): String {
    val envKey = toEnvKey(secretName)
    val envValue = System.getenv(envKey)
    if (!envValue.isNullOrBlank()) {
        log.debug("Secret '{}' resolved from env var {}", secretName, envKey)
        return envValue
    }

    if (gcpProjectId != null) {
        try {
            val value = fetchFromSecretManager(secretName)
            if (!value.isNullOrBlank()) {
                log.info("Secret '{}' resolved from Secret Manager", secretName)
                return value
            }
        } catch (e: Exception) {
            log.warn("Secret Manager fetch failed for '{}': {}", secretName, e.message)
        }
    }

    return localFallback(secretName).also {
        log.warn("Secret '{}' using local fallback — not suitable for production", secretName)
    }
}

/**
 * Converts a kebab-case secret name to its environment variable name.
 * Most names map directly via uppercasing and replacing hyphens with underscores.
 * The only non-standard mapping is weather-data-secret → WEATHER_API_KEY.
 */
private fun toEnvKey(secretName: String): String = when (secretName) {
    "weather-data-secret" -> "WEATHER_API_KEY"
    else -> secretName.uppercase().replace("-", "_")
}

private fun localFallback(secretName: String): String = when (secretName) {
    // Shared
    "jwt-secret"               -> "dev_jwt_secret_key_for_local_development_only"
    "redis-url"                -> ""
    "gemini-api-key"           -> ""
    // Weatherify / main app
    "db-connection-string"     -> "mongodb://localhost:27017"
    "weather-data-secret"      -> "dummy_weather_api_key"
    "razorpay-secret"          -> "dummy_razorpay_secret"
    "razorpay-key-id"          -> "dummy_razorpay_key_id"
    "razorpay-webhook-secret"  -> "dummy_razorpay_webhook_secret"
    "sendgrid-api-key"         -> ""
    // Transloom
    "mongo-uri"                -> "mongodb://localhost:27017"
    "token-encryption-key"     -> "dev_token_encryption_key_32chars!"
    "allowed-origin"           -> "http://localhost:5173"
    "auth-callback-uri"        -> "http://localhost:8081/transloom/auth/github/callback"
    "frontend-url"             -> "http://localhost:5173/?token="
    "secure-cookies"           -> "false"
    "github-webhook-secret"    -> ""
    "github-client-id"         -> "dummy_client_id"
    "github-client-secret"     -> "dummy_client_secret"
    "webhook-url"              -> "https://transloom.dev/transloom/webhook/github"
    "stripe-secret-key"        -> ""
    "stripe-webhook-secret"    -> ""
    "stripe-price-solo"        -> ""
    "stripe-price-team"        -> ""
    "app-url"                  -> "http://localhost:8081"
    else                       -> ""
}
