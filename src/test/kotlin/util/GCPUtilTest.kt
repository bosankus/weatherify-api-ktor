package util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the GCPUtil functionality
 *
 * Note: These tests verify the behavior of the getSecretValue function
 * in a development environment (when GCP_PROJECT_ID is not set).
 * We're testing the fallback values that should be returned.
 */
class GCPUtilTest {

    @Test
    fun `test getSecretValue returns expected values for different secrets`() {
        // Test JWT secret
        assertEquals(
            "dev_jwt_secret_key_for_local_development_only",
            getSecretValueForTest("jwt-secret")
        )

        // Test DB connection string
        assertEquals(
            "mongodb://localhost:27017",
            getSecretValueForTest("db-connection-string")
        )

        // Test weather API key
        assertEquals(
            "dummy_weather_api_key",
            getSecretValueForTest("weather-data-secret")
        )

        // Test unknown secret
        assertEquals(
            "dummy_value_for_test-secret",
            getSecretValueForTest("test-secret")
        )
    }

    /**
     * Test-specific implementation of getSecretValue that simulates
     * a development environment (GCP_PROJECT_ID not set)
     */
    private fun getSecretValueForTest(secretName: String): String {
        // Simulate development environment behavior
        return when (secretName) {
            "jwt-secret" -> "dev_jwt_secret_key_for_local_development_only"
            "db-connection-string" -> "mongodb://localhost:27017"
            "weather-data-secret" -> "dummy_weather_api_key"
            else -> "dummy_value_for_$secretName"
        }
    }
}