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

// ── Master secret ─────────────────────────────────────────────────────────────
//
// All application secrets are stored as a single flat JSON object in one GCP
// Secret Manager secret (one active version → minimum billing).
//
// Secret name: "app-secrets"  (override via APP_SECRET_NAME env var)
//
// In Cloud Run the secret is mounted as the APP_SECRETS env var:
//   --update-secrets=APP_SECRETS=app-secrets:latest
// That is the fast path (no HTTP call at runtime).  If APP_SECRETS is not set
// the code falls back to fetching the secret via the Secret Manager HTTP API.
//
// Required keys — every string ever passed to getSecretValue() must be present:
//
//   "jwt-secret"                     shared JWT signing key
//   "mongo-uri"                      MongoDB URI (Syncling module)
//   "db-connection-string"           MongoDB URI (Weatherify module)
//   "token-encryption-key"           GitHub token AES encryption key
//   "redis-url"                      Upstash Redis URL
//   "allowed-origin"                 CORS allowed origin
//   "frontend-url"                   Post-auth redirect URL
//   "gemini-api-key"                 Google Gemini API key
//   "weather-data-secret"            OpenWeatherMap API key
//   "sendgrid-api-key"               SendGrid API key
//   "smtp-host"                      SMTP server hostname
//   "smtp-port"                      SMTP server port (string, e.g. "587")
//   "smtp-user"                      SMTP username / from address
//   "smtp-password"                  SMTP password / app password
//   "razorpay-key-id"                Razorpay publishable key
//   "razorpay-secret"                Razorpay secret key
//   "razorpay-webhook-secret"        Razorpay webhook HMAC secret
//   "razorpay-plan-id-solo"          Razorpay Solo subscription plan ID
//   "razorpay-plan-id-team"          Razorpay Team subscription plan ID
//   "github-client-id"               GitHub OAuth app client ID
//   "github-client-secret"           GitHub OAuth app client secret
//   "github-webhook-secret"          GitHub webhook HMAC secret
//   "cloudflare-account-id"          Cloudflare account ID
//   "cloudflare-r2-bucket-name"      R2 bucket name
//   "cloudflare-r2-access-key-id"    R2 access key ID
//   "cloudflare-r2-secret-access-key" R2 secret access key
//   "bundle-signing-key"             CDN bundle HMAC signing key
//   "firebase-service-account-key"   Firebase service-account JSON (stringified)
//
// Individual env vars (e.g. JWT_SECRET) still override any key — useful for
// local dev and CI where the JSON secret is not available.
//
private val masterSecretName: String
    get() = System.getenv("APP_SECRET_NAME")?.takeIf { it.isNotBlank() } ?: "app-secrets"

// Parsed once per process.  Null means neither APP_SECRETS env var nor the
// Secret Manager API had a valid value — all lookups will fall through to
// localFallback().
private val masterSecretCache: Map<String, String>? by lazy { loadMasterSecret() }

private fun loadMasterSecret(): Map<String, String>? {
    // Fast path: Cloud Run mounts the JSON secret as APP_SECRETS env var.
    val fromEnv = System.getenv("APP_SECRETS")
    if (!fromEnv.isNullOrBlank()) {
        return parseSecretJson(fromEnv, "APP_SECRETS env var")
    }
    // Fallback: fetch directly from the Secret Manager API.
    if (gcpProjectId == null) return null
    val raw = fetchRawSecret(masterSecretName) ?: run {
        log.warn("Master secret '{}' not found in Secret Manager", masterSecretName)
        return null
    }
    return parseSecretJson(raw, masterSecretName)
}

private fun parseSecretJson(raw: String, source: String): Map<String, String>? =
    try {
        Json.parseToJsonElement(raw).jsonObject
            .entries.associate { (k, v) -> k to v.jsonPrimitive.content }
            .also { log.info("Secrets loaded from {} ({} keys)", source, it.size) }
    } catch (e: Exception) {
        log.error("Secret source '{}' is not valid JSON — cannot load secrets: {}", source, e.message)
        null
    }

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

private fun fetchRawSecret(secretName: String): String? {
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

fun getSecretValue(secretName: String): String {
    // 1. Individual env var wins — allows local dev and CI to override any key.
    val envKey = toEnvKey(secretName)
    val envValue = System.getenv(envKey)
    if (!envValue.isNullOrBlank()) {
        log.debug("Secret '{}' resolved from env var {}", secretName, envKey)
        return envValue
    }

    // 2. Master JSON secret — single Secret Manager fetch shared by all keys.
    val masterValue = masterSecretCache?.get(secretName)
    if (!masterValue.isNullOrBlank()) {
        log.debug("Secret '{}' resolved from master secret '{}'", secretName, masterSecretName)
        return masterValue
    }

    // 3. Local fallback — dev defaults, never used in production.
    return localFallback(secretName).also {
        log.warn("Secret '{}' using local fallback — not suitable for production", secretName)
    }
}

// Non-standard: weather-data-secret → WEATHER_API_KEY (all others follow UPPER_SNAKE convention).
private fun toEnvKey(secretName: String): String = when (secretName) {
    "weather-data-secret" -> "WEATHER_API_KEY"
    else -> secretName.uppercase().replace("-", "_")
}

private fun localFallback(secretName: String): String = when (secretName) {
    // Core
    "jwt-secret"                      -> "dev_jwt_secret_key_for_local_development_only"
    "mongo-uri"                       -> "mongodb://localhost:27017"
    "db-connection-string"            -> "mongodb://localhost:27017"
    "token-encryption-key"            -> "dev_token_encryption_key_32chars!"
    "redis-url"                       -> ""
    "allowed-origin"                  -> "http://localhost:5173"
    "frontend-url"                    -> "http://localhost:5173/syncling/app"
    // Weather / AI
    "weather-data-secret"             -> "dummy_weather_api_key"
    "gemini-api-key"                  -> ""
    "sendgrid-api-key"                -> ""
    // SMTP
    "smtp-host"                       -> "smtp.gmail.com"
    "smtp-port"                       -> "587"
    "smtp-user"                       -> ""
    "smtp-password"                   -> ""
    // Razorpay
    "razorpay-key-id"                 -> "dummy_razorpay_key_id"
    "razorpay-secret"                 -> "dummy_razorpay_secret"
    "razorpay-webhook-secret"         -> "dummy_razorpay_webhook_secret"
    "razorpay-plan-id-solo"           -> ""
    "razorpay-plan-id-team"           -> ""
    // GitHub
    "github-client-id"                -> "dummy_client_id"
    "github-client-secret"            -> "dummy_client_secret"
    "github-webhook-secret"           -> ""
    // Cloudflare R2 + CDN
    "cloudflare-account-id"           -> ""
    "cloudflare-r2-bucket-name"       -> ""
    "cloudflare-r2-access-key-id"     -> ""
    "cloudflare-r2-secret-access-key" -> ""
    "bundle-signing-key"              -> ""
    // Firebase (prod-only; value is a stringified service-account JSON)
    "firebase-service-account-key"    -> ""
    else                              -> ""
}
