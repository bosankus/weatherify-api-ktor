package config

import util.Constants
import util.Constants.Api.DEFAULT_AIR_POLLUTION_URL
import util.Constants.Api.DEFAULT_WEATHER_URL
import util.Constants.Auth.DEFAULT_JWT_EXPIRATION
import util.Constants.Database.DEFAULT_DB_NAME

/**
 * Environment configuration with fallback values for local development
 */
object Environment {
    /**
     * Get the weather data URL from environment variables
     * Falls back to OpenWeatherMap API URL
     */
    fun getWeatherUrl(): String {
        return System.getenv(Constants.Env.WEATHER_URL) ?: DEFAULT_WEATHER_URL
    }

    /**
     * Get the air pollution data URL from environment variables
     * Falls back to OpenWeatherMap API URL
     */
    fun getAirPollutionUrl(): String {
        return System.getenv(Constants.Env.AIR_POLLUTION_URL) ?: DEFAULT_AIR_POLLUTION_URL
    }

    /**
     * Get the database name from environment variables
     * Falls back to default name
     */
    fun getDbName(): String {
        return System.getenv(Constants.Env.DB_NAME) ?: DEFAULT_DB_NAME
    }

    /**
     * Get the JWT expiration time in milliseconds
     * Falls back to 1 hour (3600000 milliseconds)
     */
    fun getJwtExpiration(): Long {
        return System.getenv(Constants.Env.JWT_EXPIRATION)?.toLongOrNull()
            ?: DEFAULT_JWT_EXPIRATION.toLong()
    }

    /** Get JWT audience with default fallback */
    fun getJwtAudience(): String {
        return System.getenv(Constants.Env.JWT_AUDIENCE) ?: Constants.Auth.DEFAULT_JWT_AUDIENCE
    }

    /** Get JWT issuer with default fallback */
    fun getJwtIssuer(): String {
        return System.getenv(Constants.Env.JWT_ISSUER) ?: Constants.Auth.DEFAULT_JWT_ISSUER
    }

    /** Get JWT realm with default fallback */
    fun getJwtRealm(): String {
        return System.getenv(Constants.Env.JWT_REALM) ?: Constants.Auth.DEFAULT_JWT_REALM
    }

    /** Notifications: Cloud Function URL if configured */
    fun getFcmFunctionUrl(): String? {
        return System.getenv(Constants.Env.FCM_FUNCTION_URL)?.takeIf { it.isNotBlank() }
    }

    /** Analytics (GA4): measurement ID or null if missing */
    fun getGaMeasurementId(): String? {
        return System.getenv(Constants.Env.GA_MEASUREMENT_ID)?.takeIf { it.isNotBlank() }
    }

    /** Analytics (GA4): API secret or null if missing */
    fun getGaApiSecret(): String? {
        return System.getenv(Constants.Env.GA_API_SECRET)?.takeIf { it.isNotBlank() }
    }

    /** Analytics (GA4): enabled flag, default false */
    fun isAnalyticsEnabled(): Boolean {
        return (System.getenv(Constants.Env.GA_ENABLED) ?: "false").equals(
            "true",
            ignoreCase = true
        )
    }

    /**
     * Check if refund feature is enabled
     * Falls back to true (enabled by default)
     */
    fun isRefundFeatureEnabled(): Boolean {
        return (System.getenv(Constants.Env.REFUND_FEATURE_ENABLED) ?: "true").equals(
            "true",
            ignoreCase = true
        )
    }

    /**
     * Check if instant refund is enabled
     * Falls back to true (enabled by default)
     */
    fun isInstantRefundEnabled(): Boolean {
        return (System.getenv(Constants.Env.INSTANT_REFUND_ENABLED) ?: "true").equals(
            "true",
            ignoreCase = true
        )
    }
}
