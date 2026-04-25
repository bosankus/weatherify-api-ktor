package bose.ankush.util

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("GCPUtil")

/**
 * Retrieves a secret value from environment variables with local fallbacks.
 *
 * On Cloud Run, secrets are mounted as environment variables via the Cloud Run
 * service configuration (Secret Manager → Environment Variable). No SDK or
 * REST calls are needed — the platform injects them before the container starts.
 *
 * To configure in Cloud Run:
 *   gcloud run services update SERVICE_NAME \
 *     --update-secrets=DB_CONNECTION_STRING=db-connection-string:latest \
 *     --update-secrets=JWT_SECRET=jwt-secret:latest \
 *     ... (repeat for each secret)
 *
 * The Cloud Run service account must have roles/secretmanager.secretAccessor.
 */
internal fun getSecretValue(secretName: String): String {
    val envKey = secretNameToEnvKey(secretName)
    val envValue = System.getenv(envKey)
    if (!envValue.isNullOrBlank()) {
        logger.debug("Resolved secret '{}' from env var '{}'", secretName, envKey)
        return envValue
    }

    logger.warn(
        "Env var '{}' not set for secret '{}'. Using local fallback. " +
        "On Cloud Run, add: --update-secrets={}={}:latest",
        envKey, secretName, envKey, secretName
    )
    return getLocalFallback(secretName)
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

private fun getLocalFallback(secretName: String): String = when (secretName) {
    "db-connection-string"     -> "mongodb://localhost:27017"
    "jwt-secret"               -> "dev_jwt_secret_key_for_local_development_only"
    "weather-data-secret"      -> "dummy_weather_api_key"
    "razorpay-secret"          -> "dummy_razorpay_secret"
    "razorpay-key-id"          -> "dummy_razorpay_key_id"
    "razorpay-webhook-secret"  -> "dummy_razorpay_webhook_secret"
    "sendgrid-api-key"         -> ""
    "redis-url"                -> ""
    else                       -> "dummy_value_for_$secretName"
}
