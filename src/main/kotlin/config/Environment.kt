package bose.ankush.config

/**
 * Environment configuration for the application
 * Provides access to environment variables with fallback values for local development
 */
object Environment {
    private const val DEFAULT_WEATHER_URL = "https://api.openweathermap.org/data/3.0/onecall"
    private const val DEFAULT_AIR_POLLUTION_URL =
        "https://api.openweathermap.org/data/2.5/air_pollution"
    private const val DEFAULT_DB_NAME = "weather"

    /**
     * Get the weather data URL from environment variables
     * Falls back to a dummy value for local development
     */
    fun getWeatherUrl(): String {
        return System.getenv("WEATHER_URL") ?: DEFAULT_WEATHER_URL
    }

    /**
     * Get the air pollution data URL from environment variables
     * Falls back to a dummy value for local development
     */
    fun getAirPollutionUrl(): String {
        return System.getenv("AIR_POLLUTION_URL") ?: DEFAULT_AIR_POLLUTION_URL
    }

    /**
     * Get the database name from environment variables
     * Falls back to a dummy value for local development
     */
    fun getDbName(): String {
        return System.getenv("DB_NAME") ?: DEFAULT_DB_NAME
    }
}