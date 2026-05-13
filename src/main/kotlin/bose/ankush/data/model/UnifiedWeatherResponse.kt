package bose.ankush.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UnifiedWeatherResponse(
    val current: Weather.Current?,
    val hourly: List<Weather.Hourly?>?,
    val daily: List<Weather.Daily?>?,
    val alerts: List<Weather.Alert?>?,
    val airQuality: AirQuality?,
    val entitlements: ResponseEntitlements
)

@Serializable
data class ResponseEntitlements(
    val hourlyIncluded: Boolean,
    val dailyIncluded: Boolean,
    val alertsIncluded: Boolean,
    val airQualityIncluded: Boolean,
    val upgradeRequired: Boolean
)