package bose.ankush.util

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient
import com.google.cloud.secretmanager.v1.SecretVersionName

internal fun getSecretValue(secretName: String): String {
    // Check if we're in a development environment (GCP_PROJECT_ID not set)
    val gcpProjectId = System.getenv("GCP_PROJECT_ID")
    if (gcpProjectId.isNullOrEmpty()) {
        // Provide fallback values for local development
        return when (secretName) {
            "jwt-secret" -> "dev_jwt_secret_key_for_local_development_only"
            "db-connection-string" -> System.getenv("DB_CONNECTION_STRING")
                ?: "mongodb://localhost:27017"

            "weather-data-secret" -> System.getenv("WEATHER_API_KEY") ?: "dummy_weather_api_key"
            else -> "dummy_value_for_$secretName"
        }
    }

    // In production, get the secret from Google Cloud Secret Manager
    try {
        val client = SecretManagerServiceClient.create()
        val secretVersionName = SecretVersionName.of(
            gcpProjectId,
            secretName,
            "1"
        )
        val response = client.accessSecretVersion(secretVersionName)
        return response.payload.data.toStringUtf8()
    } catch (e: Exception) {
        println("Error accessing secret $secretName: ${e.message}")
        // Fallback to environment variables if Secret Manager fails
        return when (secretName) {
            "jwt-secret" -> "fallback_jwt_secret_key"
            "db-connection-string" -> System.getenv("DB_CONNECTION_STRING")
                ?: "mongodb://localhost:27017"

            "weather-data-secret" -> System.getenv("WEATHER_API_KEY") ?: "dummy_weather_api_key"
            else -> "fallback_value_for_$secretName"
        }
    }
}
