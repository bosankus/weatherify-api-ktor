package bose.ankush.util

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient
import com.google.cloud.secretmanager.v1.SecretVersionName
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("GCPUtil")

/**
 * Retrieves a secret value from environment variables or Google Cloud Secret Manager.
 *
 * Order of priority:
 * 1. Environment Variables (Best for Cloud Run and local dev)
 * 2. Google Cloud Secret Manager (If GCP_PROJECT_ID is set)
 * 3. Local hardcoded fallbacks (For local development only)
 */
internal fun getSecretValue(secretName: String): String {
    // 1. Check Environment Variables first (Recommended for Cloud Run)
    // Map secret-name to SECRET_NAME or specific overrides
    val envKey = when (secretName) {
        "weather-data-secret" -> "WEATHER_API_KEY"
        "db-connection-string" -> "DB_CONNECTION_STRING"
        "jwt-secret" -> "JWT_SECRET"
        "razorpay-secret" -> "RAZORPAY_SECRET"
        "razorpay-key-id" -> "RAZORPAY_KEY_ID"
        "razorpay-webhook-secret" -> "RAZORPAY_WEBHOOK_SECRET"
        "sendgrid-api-key" -> "SENDGRID_API_KEY"
        else -> secretName.uppercase().replace("-", "_")
    }

    val envValue = System.getenv(envKey)
    if (!envValue.isNullOrBlank()) {
        logger.debug("Using environment variable for: {}", secretName)
        return envValue
    }

    // 2. Try GCP Secret Manager if we are in a GCP environment
    val gcpProjectId = System.getenv("GCP_PROJECT_ID") ?: System.getenv("GOOGLE_CLOUD_PROJECT")
    if (!gcpProjectId.isNullOrEmpty()) {
        try {
            SecretManagerServiceClient.create().use { client ->
                val secretVersionName = SecretVersionName.of(gcpProjectId, secretName, "latest")
                val response = client.accessSecretVersion(secretVersionName)
                val secretValue = response.payload.data.toStringUtf8()
                if (secretValue.isNotBlank()) {
                    logger.info("Successfully retrieved secret {} from Secret Manager", secretName)
                    return secretValue
                }
            }
        } catch (e: Exception) {
            logger.warn("Could not fetch secret {} from Secret Manager: {}. Falling back to defaults.", secretName, e.message)
        }
    }

    // 3. Fallback to hardcoded defaults for local development
    return getLocalFallback(secretName)
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
        else -> "dummy_value_for_$secretName"
    }
}
