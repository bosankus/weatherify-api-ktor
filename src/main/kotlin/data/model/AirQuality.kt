package bose.ankush.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AirQuality(@SerialName("list") val list: List<QualityDetails?>?) {
    @Serializable
    data class QualityDetails(
        @SerialName("components") val components: Components?,
        @SerialName("dt") val dt: Int?,
        @SerialName("main") val main: Main?
    ) {
        @Serializable
        data class Components(
            @SerialName("co") val co: Double?,
            @SerialName("nh3") val nh3: Double?,
            @SerialName("no") val no: Double?,
            @SerialName("no2") val no2: Double?,
            @SerialName("o3") val o3: Double?,
            @SerialName("pm10") val pm10: Double?,
            @SerialName("pm2_5") val pm25: Double?,
            @SerialName("so2") val so2: Double?
        )

        @Serializable
        data class Main(@SerialName("aqi") val aqi: Int?)
    }
}
