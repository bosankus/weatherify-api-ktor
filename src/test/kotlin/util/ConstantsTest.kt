package util

import kotlin.test.Test
import kotlin.test.assertEquals

class ConstantsTest {

    @Test
    fun `test Database constants have expected values`() {
        // Test database constants
        assertEquals("weather", Constants.Database.DEFAULT_DB_NAME)
        assertEquals("feedback", Constants.Database.FEEDBACK_COLLECTION)
        assertEquals("weather", Constants.Database.WEATHER_COLLECTION)
        assertEquals("users", Constants.Database.USERS_COLLECTION)
        assertEquals("_id", Constants.Database.ID_FIELD)
        assertEquals("email", Constants.Database.EMAIL_FIELD)
    }

    @Test
    fun `test Auth constants have expected values`() {
        // Test auth constants
        assertEquals("jwt-realm", Constants.Auth.DEFAULT_JWT_REALM)
        assertEquals("jwt-issuer", Constants.Auth.DEFAULT_JWT_ISSUER)
        assertEquals("jwt-audience", Constants.Auth.DEFAULT_JWT_AUDIENCE)
        assertEquals("3600000", Constants.Auth.DEFAULT_JWT_EXPIRATION)
        assertEquals("email", Constants.Auth.JWT_CLAIM_EMAIL)
        assertEquals("jwt-secret", Constants.Auth.JWT_SECRET_NAME)
        assertEquals("db-connection-string", Constants.Auth.DB_CONNECTION_STRING_SECRET)
        assertEquals("Invalid email format", Constants.Auth.INVALID_EMAIL_FORMAT)
        assertEquals(
            "Password must be at least 8 characters long and contain uppercase, lowercase, digit, and at least one special character (!@#$%^&*()_-+=<>?/[]{}|)",
            Constants.Auth.INVALID_PASSWORD_STRENGTH
        )
    }

    @Test
    fun `test Api constants have expected values`() {
        // Test API constants
        assertEquals("/weather", Constants.Api.WEATHER_ENDPOINT)
        assertEquals("/air-pollution", Constants.Api.AIR_POLLUTION_ENDPOINT)
        assertEquals("/feedback", Constants.Api.FEEDBACK_ENDPOINT)
        assertEquals("/login", Constants.Api.LOGIN_ENDPOINT)
        assertEquals("/register", Constants.Api.REGISTER_ENDPOINT)
        assertEquals("/", Constants.Api.HOME_ENDPOINT)
        assertEquals(
            "https://api.openweathermap.org/data/3.0/onecall",
            Constants.Api.DEFAULT_WEATHER_URL
        )
        assertEquals(
            "https://api.openweathermap.org/data/2.5/air_pollution",
            Constants.Api.DEFAULT_AIR_POLLUTION_URL
        )
        assertEquals("lat", Constants.Api.PARAM_LAT)
        assertEquals("lon", Constants.Api.PARAM_LON)
        assertEquals("appid", Constants.Api.PARAM_APP_ID)
        assertEquals("exclude", Constants.Api.PARAM_EXCLUDE)
        assertEquals("id", Constants.Api.PARAM_ID)
        assertEquals("deviceId", Constants.Api.PARAM_DEVICE_ID)
        assertEquals("deviceOs", Constants.Api.PARAM_DEVICE_OS)
        assertEquals("feedbackTitle", Constants.Api.PARAM_FEEDBACK_TITLE)
        assertEquals("feedbackDescription", Constants.Api.PARAM_FEEDBACK_DESCRIPTION)
        assertEquals("minutely", Constants.Api.EXCLUDE_MINUTELY)
    }

    @Test
    fun `test Messages constants have expected values`() {
        // Test message constants
        assertEquals("User registered successfully", Constants.Messages.REGISTRATION_SUCCESS)
        assertEquals("Login successful", Constants.Messages.LOGIN_SUCCESS)
        assertEquals("Invalid credentials", Constants.Messages.INVALID_CREDENTIALS)
        assertEquals("User already exists", Constants.Messages.USER_ALREADY_EXISTS)
        assertEquals("Failed to register user", Constants.Messages.FAILED_REGISTER)
        assertEquals("User is not registered", Constants.Messages.USER_NOT_REGISTERED)
        assertEquals("Account is inactive", Constants.Messages.ACCOUNT_INACTIVE)
        assertEquals("Feedback submitted successfully", Constants.Messages.FEEDBACK_SUBMITTED)
        assertEquals("Feedback retrieved successfully", Constants.Messages.FEEDBACK_RETRIEVED)
        assertEquals("Feedback not found", Constants.Messages.FEEDBACK_NOT_FOUND)
        assertEquals("Feedback removed successfully", Constants.Messages.FEEDBACK_REMOVED)
        assertEquals(
            "Feedback not found or could not be removed",
            Constants.Messages.FEEDBACK_REMOVAL_FAILED
        )
        assertEquals("Failed to save feedback", Constants.Messages.FAILED_SAVE_FEEDBACK)
        assertEquals("Weather data retrieved successfully", Constants.Messages.WEATHER_RETRIEVED)
        assertEquals("Failed to save weather data", Constants.Messages.FAILED_SAVE_WEATHER)
        assertEquals("Failed to fetch weather data", Constants.Messages.FAILED_FETCH_WEATHER)
        assertEquals(
            "Air pollution data retrieved successfully",
            Constants.Messages.AIR_POLLUTION_RETRIEVED
        )
        assertEquals(
            "Failed to fetch air pollution data",
            Constants.Messages.FAILED_FETCH_AIR_POLLUTION
        )
        assertEquals(
            "Missing mandatory query parameters: lat and lon",
            Constants.Messages.MISSING_LOCATION_PARAMS
        )
        assertEquals("Internal server error", Constants.Messages.INTERNAL_SERVER_ERROR)
    }

    @Test
    fun `test Env constants have expected values`() {
        // Test environment variable constants
        assertEquals("JWT_EXPIRATION", Constants.Env.JWT_EXPIRATION)
        assertEquals("JWT_AUDIENCE", Constants.Env.JWT_AUDIENCE)
        assertEquals("JWT_ISSUER", Constants.Env.JWT_ISSUER)
        assertEquals("JWT_REALM", Constants.Env.JWT_REALM)
        assertEquals("DB_NAME", Constants.Env.DB_NAME)
        assertEquals("WEATHER_URL", Constants.Env.WEATHER_URL)
        assertEquals("AIR_POLLUTION_URL", Constants.Env.AIR_POLLUTION_URL)
    }
}