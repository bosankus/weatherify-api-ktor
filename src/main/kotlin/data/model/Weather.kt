package bose.ankush.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class Weather(
    val id: Long?=null,
    val alerts: List<Alert?>? = listOf(),
    val current: Current? = null,
    val daily: List<Daily?>? = listOf(),
    val hourly: List<Hourly?>? = listOf(),
) {
    @Serializable
    @JsonIgnoreUnknownKeys
    data class Alert(
        val description: String?,
        val end: Int?,
        val event: String?,
        @SerialName("sender_name") val senderName: String?,
        val start: Int?,
    )

    @Serializable
    @JsonIgnoreUnknownKeys
    data class Current(
        val clouds: Int?,
        val dt: Long?,
        @SerialName("feels_like") val feelsLike: Double?,
        val humidity: Int?,
        val pressure: Int?,
        val sunrise: Int?,
        val sunset: Int?,
        val temp: Double?,
        val uvi: Double?,
        val weather: List<WeatherData?>? = listOf(),
        val wind_gust: Double?,
        val wind_speed: Double?
    )

    @Serializable
    @JsonIgnoreUnknownKeys
    data class Daily(
        val clouds: Int?,
        @SerialName("dew_point") val dewPoint: Double?,
        val dt: Long?,
        val humidity: Int?,
        val pressure: Int?,
        val rain: Double? = null,
        val summary: String?,
        val sunrise: Int?,
        val sunset: Int?,
        val temp: Temp?,
        val uvi: Double?,
        val weather: List<WeatherData?>? = listOf(),
        @SerialName("wind_gust") val windGust: Double?,
        @SerialName("wind_speed") val windSpeed: Double?
    ) {
        @Serializable
        data class Temp(
            val day: Double?,
            val eve: Double?,
            val max: Double?,
            val min: Double?,
            val morn: Double?,
            val night: Double?
        )
    }

    @Serializable
    @JsonIgnoreUnknownKeys
    data class Hourly(
        val clouds: Int?,
        val dt: Long?,
        @SerialName("feels_like") val feelsLike: Double?,
        val humidity: Int?,
        val temp: Double?,
        @SerialName("weather") val weather: List<WeatherData?>? = listOf(),
    )
}

@Serializable
data class WeatherData(
    val description: String, val icon: String, val id: Int?=null, val main: String
)

