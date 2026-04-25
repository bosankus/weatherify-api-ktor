package bose.ankush.util

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("GCPUtil")

/**
 * Retrieves a secret value from environment variables or Google Cloud Secret Manager REST API.
 *
 * Order of priority:
 * 1. Environment Variables (Best for Cloud Run and local dev)
 * 2. Google Cloud Secret Manager REST API (If GCP_PROJECT_ID is set)
 * 3. Local hardcoded fallbacks (For local development only)
 */
internal fun getSecretValue(secretName: String): String {
    // 1. Check Environment Variables first (Recommended for Cloud Run)
    val envKey = when (secretName) {
        "weather-data-secret" -> "WEATHER_API_KEY"
        "db-connection-string" -> "DB_CONNECTION_STRING"
        "jwt-secret" -> "JWT_SECRET"
        "razorpay-secret" -> "RAZORPAY_SECRET"
        "razorpay-key-id" -> "RAZORPAY_KEY_ID"
        "razorpay-webhook-secret" -> "RAZORPAY_WEBHOOK_SECRET"
        "sendgrid-api-key" -> "SENDGRID_API_KEY"
        "redis-url" -> "REDIS_URL"
        else -> secretName.uppercase().replace("-", "_")
    }

    val envValue = System.getenv(envKey)
    if (!envValue.isNullOrBlank()) {
        logger.debug("Using environment variable for: {}", secretName)
        return envValue
    }

    // 2. Try GCP Secret Manager via REST API (no gRPC SDK needed)
    val gcpProjectId = System.getenv("GCP_PROJECT_ID") ?: System.getenv("GOOGLE_CLOUD_PROJECT")
    if (!gcpProjectId.isNullOrEmpty()) {
        try {
            val value = fetchSecretFromRestApi(gcpProjectId, secretName)
            if (!value.isNullOrBlank()) {
                logger.info("Successfully retrieved secret {} from Secret Manager", secretName)
                return value
            }
        } catch (e: Exception) {
            logger.warn("Could not fetch secret {} from Secret Manager: {}. Falling back to defaults.", secretName, e.message)
        }
    }

    // 3. Fallback to hardcoded defaults for local development
    return getLocalFallback(secretName)
}

/**
 * Fetches a secret from GCP Secret Manager using the REST API and the Cloud Run metadata server
 * for authentication — no gRPC SDK required.
 */
private fun fetchSecretFromRestApi(projectId: String, secretName: String): String? {
    return try {
        runBlocking {
            // Wrap entire operation in timeout
            withTimeoutOrNull(10.seconds) {
                HttpClient(CIO) {
                    install(HttpTimeout) {
                        connectTimeoutMillis = 5000
                        requestTimeoutMillis = 10000
                        socketTimeoutMillis = 5000
                    }
                }.use { client ->
                    // Get access token from the Cloud Run metadata server
                    val tokenResponse = client.get("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token") {
                        header("Metadata-Flavor", "Google")
                    }
                    val tokenJson = tokenResponse.bodyAsText()
                    // Simple parse — token is in {"access_token":"...","expires_in":...,"token_type":"Bearer"}
                    val accessToken = tokenJson
                        .substringAfter("\"access_token\":\"")
                        .substringBefore("\"")

                    // Call Secret Manager REST API
                    val secretUrl = "https://secretmanager.googleapis.com/v1/projects/$projectId/secrets/$secretName/versions/latest:access"
                    val secretResponse = client.get(secretUrl) {
                        header("Authorization", "Bearer $accessToken")
                    }
                    val secretJson = secretResponse.bodyAsText()
                    // Payload data is base64-encoded in {"payload":{"data":"BASE64..."}}
                    val base64Data = secretJson
                        .substringAfter("\"data\":\"")
                        .substringBefore("\"")
                    if (base64Data.isNotBlank()) {
                        java.util.Base64.getDecoder().decode(base64Data).toString(Charsets.UTF_8)
                    } else null
                }
            } ?: run {
                logger.warn("Secret Manager request timed out for {}", secretName)
                null
            }
        }
    } catch (e: Exception) {
        logger.warn("Secret Manager request failed for {}: {}", secretName, e.message)
        null
    }
}

private fun getLocalFallback(secretName: String): String {
    logger.warn("No environment variable or Secret Manager value found for {}. Using local fallback.", secretName)
    return when (secretName) {
        "db-connection-string" -> "mongodb://localhost:27017"
        "jwt-secret" -> "dev_jwt_secret_key_for_local_development_only"
        "weather-data-secret" -> "dummy_weather_api_key"
        "razorpay-secret" -> "dummy_razorpay_secret"
        "razorpay-key-id" -> "dummy_razorpay_key_id"
        "razorpay-webhook-secret" -> "dummy_razorpay_webhook_secret"
        "sendgrid-api-key" -> ""
        "redis-url" -> "" // Disabled by default for local dev without Redis
        else -> "dummy_value_for_$secretName"
    }
}
