package bose.ankush.util

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

private val logger = LoggerFactory.getLogger("GCPUtil")

private val sharedClient by lazy {
    HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 2000
            requestTimeoutMillis = 5000
            socketTimeoutMillis = 2000
        }
    }
}

/**
 * Retrieves a secret value from environment variables or Google Cloud Secret Manager REST API.
 *
 * Order of priority:
 * 1. Environment Variables (Best for Cloud Run and local dev)
 * 2. Google Cloud Secret Manager REST API (If GCP_PROJECT_ID is set)
 * 3. Local hardcoded fallbacks (For local development only)
 */
internal fun getSecretValue(secretName: String): String {
    val envKey = secretNameToEnvKey(secretName)
    val envValue = System.getenv(envKey)
    if (!envValue.isNullOrBlank()) {
        logger.debug("Using environment variable for: {}", secretName)
        return envValue
    }

    // Try GCP Secret Manager via REST API (no gRPC SDK needed)
    var gcpProjectId = System.getenv("GCP_PROJECT_ID") ?: System.getenv("GOOGLE_CLOUD_PROJECT")

    // If project ID not in env vars, try fetching from Cloud Run metadata server
    if (gcpProjectId.isNullOrEmpty()) {
        gcpProjectId = getProjectIdFromMetadata()
    }

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
    } else {
        logger.warn("No GCP_PROJECT_ID found. Skipping Secret Manager fetch for {}.", secretName)
    }

    // Fallback to hardcoded defaults for local development
    return getLocalFallback(secretName)
}

/**
 * Fetches the GCP project ID from the Cloud Run metadata server.
 */
private fun getProjectIdFromMetadata(): String? {
    return try {
        runBlocking {
            withTimeoutOrNull(5.seconds) {
                val response = sharedClient.get("http://metadata.google.internal/computeMetadata/v1/project/project-id") {
                    header("Metadata-Flavor", "Google")
                }
                response.bodyAsText().trim().takeIf { it.isNotBlank() }
            }
        }
    } catch (e: Exception) {
        logger.debug("Could not fetch project ID from metadata server: {}", e.message)
        null
    }
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
                logger.debug("Fetching token from metadata server for secret: {}", secretName)
                // Get access token from the Cloud Run metadata server
                val tokenResponse = sharedClient.get("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token") {
                    header("Metadata-Flavor", "Google")
                }
                val tokenJson = tokenResponse.bodyAsText()
                val accessToken = tokenJson
                    .substringAfter("\"access_token\":\"")
                    .substringBefore("\"")

                if (accessToken.isBlank()) {
                    logger.error("Failed to extract access token from metadata server response: {}", tokenJson)
                    return@withTimeoutOrNull null
                }

                // Call Secret Manager REST API
                val secretUrl = "https://secretmanager.googleapis.com/v1/projects/$projectId/secrets/$secretName/versions/latest:access"
                val secretResponse = sharedClient.get(secretUrl) {
                    header("Authorization", "Bearer $accessToken")
                }

                val secretJson = secretResponse.bodyAsText()
                if (secretResponse.status.value != 200) return@withTimeoutOrNull null

                val jsonResponse = Json.parseToJsonElement(secretJson).jsonObject
                val base64Data = jsonResponse["payload"]?.jsonObject?.get("data")?.jsonPrimitive?.content
                
                base64Data?.let { java.util.Base64.getDecoder().decode(it).toString(Charsets.UTF_8) }
            } ?: run {
                logger.warn("Secret Manager request timed out for {}", secretName)
                null
            }
        }
    } catch (e: Exception) {
        logger.error("Secret Manager request failed for {}: {} - {}", secretName, e.message, e.javaClass.simpleName)
        e.printStackTrace()
        null
    }
}

private fun secretNameToEnvKey(secretName: String): String = when (secretName) {
    "weather-data-secret"      -> "WEATHER_API_KEY"
    "db-connection-string"     -> "DB_CONNECTION_STRING"
    "jwt-secret"               -> "JWT_SECRET"
    "razorpay-secret"          -> "RAZORPAY_SECRET"
    "razorpay-key-id"          -> "RAZORPAY_KEY_ID"
    "razorpay-webhook-secret"  -> "RAZORPAY_WEBHOOK_SECRET"
    "sendgrid-api-key"         -> "SENDGRID_API_KEY"
    "redis-url"                -> "REDIS_URL"
    else                       -> secretName.uppercase().replace("-", "_")
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
        "redis-url" -> ""
        else -> "dummy_value_for_$secretName"
    }
}
