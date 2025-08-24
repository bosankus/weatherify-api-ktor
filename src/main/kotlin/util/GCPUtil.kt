package bose.ankush.util

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient
import com.google.cloud.secretmanager.v1.SecretVersionName

internal fun getSecretValue(secretName: String): String {
    // Check if we're in a development environment (GCP_PROJECT_ID not set)
    val gcpProjectId = System.getenv("GCP_PROJECT_ID")
    if (gcpProjectId.isNullOrEmpty()) {
        // Provide fallback values for local development
        val fallbackValue = when (secretName) {
            "jwt-secret" -> {
                val envValue = System.getenv("JWT_SECRET")
                if (!envValue.isNullOrBlank()) {
                    println("Using JWT_SECRET environment variable for development")
                    envValue
                } else {
                    println("Using default JWT secret for development")
                    "dev_jwt_secret_key_for_local_development_only"
                }
            }

            "db-connection-string" -> {
                val envValue = System.getenv("DB_CONNECTION_STRING")
                if (!envValue.isNullOrBlank()) {
                    println("Using DB_CONNECTION_STRING environment variable for development")
                    envValue
                } else {
                    println("Using default MongoDB connection string for development")
                    "mongodb://localhost:27017"
                }
            }

            "weather-data-secret" -> {
                val envValue = System.getenv("WEATHER_API_KEY")
                if (!envValue.isNullOrBlank()) {
                    println("Using WEATHER_API_KEY environment variable for development")
                    envValue
                } else {
                    println("Using dummy weather API key for development")
                    "dummy_weather_api_key"
                }
            }

            "razorpay-secret" -> {
                val envValue = System.getenv("RAZORPAY_SECRET")
                if (!envValue.isNullOrBlank()) {
                    println("Using RAZORPAY_SECRET environment variable for development")
                    envValue
                } else {
                    println("Using dummy Razorpay secret for development")
                    "dummy_razorpay_secret"
                }
            }

            "razorpay-key-id" -> {
                val envValue = System.getenv("RAZORPAY_KEY_ID")
                if (!envValue.isNullOrBlank()) {
                    println("Using RAZORPAY_KEY_ID environment variable for development")
                    envValue
                } else {
                    println("Using dummy Razorpay key id for development")
                    "dummy_razorpay_key_id"
                }
            }

            else -> {
                println("Using dummy value for unknown secret: $secretName")
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
            "latest" // Use latest version instead of hardcoded "1"
        )
        val response = client.accessSecretVersion(secretVersionName)
        val secretValue = response.payload.data.toStringUtf8()

        // Validate the secret value
        if (secretValue.isBlank()) {
            throw IllegalStateException("Retrieved secret value is blank for $secretName")
        }

        return secretValue
    } catch (e: Exception) {
        // Log the error with more details
        System.err.println("Error accessing secret $secretName from Secret Manager: ${e.message}")
        e.printStackTrace()

        // Fallback to environment variables if Secret Manager fails
        val fallbackValue = when (secretName) {
            "jwt-secret" -> {
                val envValue = System.getenv("JWT_SECRET")
                if (!envValue.isNullOrBlank()) {
                    System.err.println("Using JWT_SECRET environment variable as fallback")
                    envValue
                } else {
                    System.err.println("WARNING: Using hardcoded JWT secret as fallback. This is not secure for production!")
                    // Generate a more secure fallback key
                    "fallback_jwt_secret_key_${System.currentTimeMillis()}"
                }
            }

            "db-connection-string" -> {
                val envValue = System.getenv("DB_CONNECTION_STRING")
                if (!envValue.isNullOrBlank()) {
                    System.err.println("Using DB_CONNECTION_STRING environment variable as fallback")
                    envValue
                } else {
                    System.err.println("WARNING: Using default MongoDB connection string as fallback")
                    "mongodb://localhost:27017"
                }
            }

            "weather-data-secret" -> {
                val envValue = System.getenv("WEATHER_API_KEY")
                if (!envValue.isNullOrBlank()) {
                    System.err.println("Using WEATHER_API_KEY environment variable as fallback")
                    envValue
                } else {
                    System.err.println("WARNING: Using dummy weather API key as fallback")
                    "dummy_weather_api_key"
                }
            }

            // Razorpay secrets explicit handling to avoid calling external API with bad credentials
            "razorpay-secret" -> {
                val envValue = System.getenv("RAZORPAY_SECRET")
                if (!envValue.isNullOrBlank()) {
                    System.err.println("Using RAZORPAY_SECRET environment variable as fallback")
                    envValue
                } else {
                    System.err.println("WARNING: Using dummy Razorpay secret as fallback")
                    "dummy_razorpay_secret"
                }
            }

            "razorpay-key-id" -> {
                val envValue = System.getenv("RAZORPAY_KEY_ID")
                if (!envValue.isNullOrBlank()) {
                    System.err.println("Using RAZORPAY_KEY_ID environment variable as fallback")
                    envValue
                } else {
                    System.err.println("WARNING: Using dummy Razorpay key id as fallback")
                    "dummy_razorpay_key_id"
                }
            }

            else -> {
                System.err.println("WARNING: Using dummy value for unknown secret: $secretName")
                "fallback_value_for_$secretName"
            }
        }

        return fallbackValue
    }
}
