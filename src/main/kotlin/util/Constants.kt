package util

/**
 * This file contains all the constants used throughout the application.
 * Centralizing constants makes it easier to maintain and update them.
 */
object Constants {
    /**
     * Database related constants
     */
    object Database {
        // Database drivers and connection strings
        const val DEFAULT_DB_NAME = "weather"

        // Collection names
        const val FEEDBACK_COLLECTION = "feedback"
        const val WEATHER_COLLECTION = "weather"
        const val USERS_COLLECTION = "users"
        const val PAYMENTS_COLLECTION = "payments"

        // Field names
        const val ID_FIELD = "_id"
        const val EMAIL_FIELD = "email"
    }

    /**
     * Authentication related constants
     */
    object Auth {
        // JWT configuration
        const val DEFAULT_JWT_REALM = "jwt-realm"
        const val DEFAULT_JWT_ISSUER = "jwt-issuer"
        const val DEFAULT_JWT_AUDIENCE = "jwt-audience"
        const val DEFAULT_JWT_EXPIRATION = "3600000" // 1 hour in milliseconds

        // JWT claims
        const val JWT_CLAIM_EMAIL = "email"
        const val JWT_CLAIM_ROLE = "role"

        // Secret names
        const val JWT_SECRET_NAME = "jwt-secret"
        const val DB_CONNECTION_STRING_SECRET = "db-connection-string"
        const val RAZORPAY_SECRET = "razorpay-secret"
        const val RAZORPAY_KEY_ID = "razorpay-key-id"

        // Password validation messages
        const val INVALID_EMAIL_FORMAT = "Invalid email format"
        const val INVALID_PASSWORD_STRENGTH =
            "Password must be at least 8 characters long and contain uppercase, lowercase, digit, and at least one special character (!@#$%^&*()_-+=<>?/[]{}|)"
    }

    /**
     * API related constants
     */
    object Api {
        // API endpoints
        const val WEATHER_ENDPOINT = "/weather"
        const val AIR_POLLUTION_ENDPOINT = "/air-pollution"
        const val FEEDBACK_ENDPOINT = "/feedback"
        const val LOGIN_ENDPOINT = "/login"
        const val REGISTER_ENDPOINT = "/register"
        const val REFRESH_TOKEN_ENDPOINT = "/refresh-token"
        const val LOGOUT_ENDPOINT = "/logout"
        const val HOME_ENDPOINT = "/"
        const val CREATE_ORDER_ENDPOINT = "/create-order"
        const val STORE_PAYMENT_ENDPOINT = "/store-payment"

        // API URLs
        const val DEFAULT_WEATHER_URL = "https://api.openweathermap.org/data/3.0/onecall"
        const val DEFAULT_AIR_POLLUTION_URL =
            "https://api.openweathermap.org/data/2.5/air_pollution"

        // Query parameters
        const val PARAM_LAT = "lat"
        const val PARAM_LON = "lon"
        const val PARAM_APP_ID = "appid"
        const val PARAM_EXCLUDE = "exclude"
        const val PARAM_ID = "id"
        const val PARAM_DEVICE_ID = "deviceId"
        const val PARAM_DEVICE_OS = "deviceOs"
        const val PARAM_FEEDBACK_TITLE = "feedbackTitle"
        const val PARAM_FEEDBACK_DESCRIPTION = "feedbackDescription"

        // Parameter values
        const val EXCLUDE_MINUTELY = "minutely"
    }

    /**
     * Response message constants
     */
    object Messages {
        // Auth messages
        const val REGISTRATION_SUCCESS = "User registered successfully"
        const val LOGIN_SUCCESS = "Login successful"
        const val TOKEN_REFRESH_SUCCESS = "Token refreshed successfully"
        const val TOKEN_NOT_EXPIRED = "Token is still valid and does not need to be refreshed"
        const val TOKEN_INVALID = "Invalid token provided for refresh"
        const val INVALID_CREDENTIALS = "Invalid credentials"
        const val USER_ALREADY_EXISTS = "User already exists"
        const val FAILED_REGISTER = "Failed to register user"
        const val USER_NOT_REGISTERED = "User is not registered"
        const val ACCOUNT_INACTIVE = "Account is inactive"
        const val LOGOUT_SUCCESS = "Logged out successfully"

        // Feedback messages
        const val FEEDBACK_SUBMITTED = "Feedback submitted successfully"
        const val FEEDBACK_RETRIEVED = "Feedback retrieved successfully"
        const val FEEDBACK_NOT_FOUND = "Feedback not found"
        const val FEEDBACK_REMOVED = "Feedback removed successfully"
        const val FEEDBACK_REMOVAL_FAILED = "Feedback not found or could not be removed"
        const val FAILED_SAVE_FEEDBACK = "Failed to save feedback"

        // Weather messages
        const val WEATHER_RETRIEVED = "Weather data retrieved successfully"
        const val FAILED_SAVE_WEATHER = "Failed to save weather data"
        const val FAILED_FETCH_WEATHER = "Failed to fetch weather data"
        const val AIR_POLLUTION_RETRIEVED = "Air pollution data retrieved successfully"
        const val FAILED_FETCH_AIR_POLLUTION = "Failed to fetch air pollution data"
        const val MISSING_LOCATION_PARAMS = "Missing mandatory query parameters: lat and lon"

        // Error messages
        const val INTERNAL_SERVER_ERROR = "Internal server error"
        const val DATABASE_ERROR = "Database operation failed"
        const val AUTHENTICATION_ERROR = "Authentication process failed"
        const val VALIDATION_ERROR = "Validation process failed"
        const val NETWORK_ERROR = "Network operation failed"
        const val UNKNOWN_ERROR = "An unexpected error occurred"

        // Out of scope messages
        const val ENDPOINT_NOT_FOUND = "The requested endpoint does not exist"
    }

    /**
     * Environment variable names
     */
    object Env {
        // JWT environment variables
        const val JWT_EXPIRATION = "JWT_EXPIRATION"
        const val JWT_AUDIENCE = "JWT_AUDIENCE"
        const val JWT_ISSUER = "JWT_ISSUER"
        const val JWT_REALM = "JWT_REALM"

        // Database environment variables
        const val DB_NAME = "DB_NAME"

        // API environment variables
        const val WEATHER_URL = "WEATHER_URL"
        const val AIR_POLLUTION_URL = "AIR_POLLUTION_URL"

        // Notifications / FCM
        const val FCM_FUNCTION_URL = "FCM_FUNCTION_URL" // Optional: HTTPS Cloud Function endpoint
        // const val FCM_SERVER_KEY = "FCM_SERVER_KEY"     // Optional: Legacy FCM server key
    }
}
