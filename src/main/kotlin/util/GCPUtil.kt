package bose.ankush.util

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient
import com.google.cloud.secretmanager.v1.SecretVersionName
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("GCPUtil")

internal fun getSecretValue(secretName: String): String {
    // Check if we're in a development environment (GCP_PROJECT_ID not set)
    val gcpProjectId = System.getenv("GCP_PROJECT_ID")
    if (gcpProjectId.isNullOrEmpty()) {
        // Provide fallback values for local development
        val fallbackValue = when (secretName) {
            "jwt-secret" -> {
                val envValue = System.getenv("JWT_SECRET")
                if (!envValue.isNullOrBlank()) {
                    logger.debug("Using JWT_SECRET environment variable for development")
                    envValue
                } else {
                    logger.warn("Using default JWT secret for development")
                    "dev_jwt_secret_key_for_local_development_only"
                }
            }

            "db-connection-string" -> {
                val envValue = System.getenv("DB_CONNECTION_STRING")
                if (!envValue.isNullOrBlank()) {
                    logger.debug("Using DB_CONNECTION_STRING environment variable for development")
                    envValue
                } else {
                    logger.warn("Using default MongoDB connection string for development")
                    "mongodb://localhost:27017"
                }
            }

            "weather-data-secret" -> {
                val envValue = System.getenv("WEATHER_API_KEY")
                if (!envValue.isNullOrBlank()) {
                    logger.debug("Using WEATHER_API_KEY environment variable for development")
                    envValue
                } else {
                    logger.warn("Using dummy weather API key for development")
                    "dummy_weather_api_key"
                }
            }

            "razorpay-secret" -> {
                val envValue = System.getenv("RAZORPAY_SECRET")
                if (!envValue.isNullOrBlank()) {
                    logger.debug("Using RAZORPAY_SECRET environment variable for development")
                    envValue
                } else {
                    logger.warn("Using dummy Razorpay secret for development")
                    "dummy_razorpay_secret"
                }
            }

            "razorpay-key-id" -> {
                val envValue = System.getenv("RAZORPAY_KEY_ID")
                if (!envValue.isNullOrBlank()) {
                    logger.debug("Using RAZORPAY_KEY_ID environment variable for development")
                    envValue
                } else {
                    logger.warn("Using dummy Razorpay key id for development")
                    "dummy_razorpay_key_id"
                }
            }

            "razorpay-webhook-secret" -> {
                val envValue = System.getenv("RAZORPAY_WEBHOOK_SECRET")
                if (!envValue.isNullOrBlank()) {
                    logger.debug("Using RAZORPAY_WEBHOOK_SECRET environment variable for development")
                    envValue
                } else {
                    logger.warn("Using dummy Razorpay webhook secret for development")
                    "dummy_razorpay_webhook_secret"
                }
            }

            "sendgrid-api-key" -> {
                val envValue = System.getenv("SENDGRID_API_KEY")
                if (!envValue.isNullOrBlank()) {
                    logger.debug("Using SENDGRID_API_KEY environment variable for development")
                    envValue
                } else {
                    logger.warn("SendGrid API key not configured. Email functionality will be disabled.")
                    ""
                }
            }

            else -> {
                logger.warn("Using dummy value for unknown secret: {}", secretName)
                "dummy_value_for_$secretName"
            }
        }

        return fallbackValue
    }

    // In production, get the secret from Google Cloud Secret Manager
    try {
        val client = SecretManagerServiceClient.create()
        val secretVersionName = SecretVersionName.of(
            gcpProjectId,
            secretName,
            "latest"
        )
        val response = client.accessSecretVersion(secretVersionName)
        val secretValue = response.payload.data.toStringUtf8()

        if (secretValue.isBlank()) {
            throw IllegalStateException("Retrieved secret value is blank for $secretName")
        }

        return secretValue
    } catch (e: Exception) {
        logger.error("Error accessing secret {} from Secret Manager: {}", secretName, e.message, e)

        // Fallback to environment variables if Secret Manager fails
        val fallbackValue = when (secretName) {
            "jwt-secret" -> {
                val envValue = System.getenv("JWT_SECRET")
                if (!envValue.isNullOrBlank()) {
                    logger.warn("Using JWT_SECRET environment variable as fallback")
                    envValue
                } else {
                    logger.error("Using hardcoded JWT secret as fallback. This is not secure for production!")
                    "fallback_jwt_secret_key_${System.currentTimeMillis()}"
                }
            }

            "db-connection-string" -> {
                val envValue = System.getenv("DB_CONNECTION_STRING")
                if (!envValue.isNullOrBlank()) {
                    logger.warn("Using DB_CONNECTION_STRING environment variable as fallback")
                    envValue
                } else {
                    logger.error("Using default MongoDB connection string as fallback")
                    "mongodb://localhost:27017"
                }
            }

            "weather-data-secret" -> {
                val envValue = System.getenv("WEATHER_API_KEY")
                if (!envValue.isNullOrBlank()) {
                    logger.warn("Using WEATHER_API_KEY environment variable as fallback")
                    envValue
                } else {
                    logger.error("Using dummy weather API key as fallback")
                    "dummy_weather_api_key"
                }
            }

            "razorpay-secret" -> {
                val envValue = System.getenv("RAZORPAY_SECRET")
                if (!envValue.isNullOrBlank()) {
                    logger.warn("Using RAZORPAY_SECRET environment variable as fallback")
                    envValue
                } else {
                    logger.error("Using dummy Razorpay secret as fallback")
                    "dummy_razorpay_secret"
                }
            }

            "razorpay-key-id" -> {
                val envValue = System.getenv("RAZORPAY_KEY_ID")
                if (!envValue.isNullOrBlank()) {
                    logger.warn("Using RAZORPAY_KEY_ID environment variable as fallback")
                    envValue
                } else {
                    logger.error("Using dummy Razorpay key id as fallback")
                    "dummy_razorpay_key_id"
                }
            }

            "razorpay-webhook-secret" -> {
                val envValue = System.getenv("RAZORPAY_WEBHOOK_SECRET")
                if (!envValue.isNullOrBlank()) {
                    logger.warn("Using RAZORPAY_WEBHOOK_SECRET environment variable as fallback")
                    envValue
                } else {
                    logger.error("Using dummy Razorpay webhook secret as fallback")
                    "dummy_razorpay_webhook_secret"
                }
            }

            "sendgrid-api-key" -> {
                val envValue = System.getenv("SENDGRID_API_KEY")
                if (!envValue.isNullOrBlank()) {
                    logger.warn("Using SENDGRID_API_KEY environment variable as fallback")
                    envValue
                } else {
                    logger.error("SendGrid API key not configured. Email functionality will be disabled.")
                    ""
                }
            }

            else -> {
                logger.error("Using dummy value for unknown secret: {}", secretName)
                "fallback_value_for_$secretName"
            }
        }

        return fallbackValue
    }
}
