package domain.service.live

import bose.ankush.data.model.AirQuality
import bose.ankush.data.model.Weather
import domain.model.live.AirQualitySample
import domain.model.live.AlertInfo
import domain.model.live.Conditions
import domain.service.WeatherService
import org.slf4j.LoggerFactory
import kotlin.math.abs
import kotlin.random.Random

/**
 * Immutable point-in-time state of one location. The deriver diffs consecutive snapshots to
 * produce events; nothing downstream ever sees the raw upstream payload.
 */
data class LocationSnapshot(
    val conditions: Conditions,
    val aqi: AirQualitySample?,
    val alerts: List<AlertInfo>,
    /** First upcoming hourly bucket that reports precipitation, if any. */
    val nextPrecipitation: Precipitation?
) {
    data class Precipitation(val atEpochSeconds: Long, val description: String)
}

/**
 * Where a poller gets its data. Two implementations:
 *   - [UpstreamConditionsSource]  real OpenWeather, refreshes roughly every 10 minutes
 *   - [SimulatedConditionsSource] synthetic drift on top of one real seed sample
 *
 * The socket layer is written against this interface only, so the transport design stays
 * independent of how fast the data actually arrives. That independence is the point: it lets us
 * load-test fan-out with 10k connections without spending a single upstream API call.
 */
interface ConditionsSource {
    suspend fun fetch(lat: Double, lon: Double, includeAirQuality: Boolean): LocationSnapshot?
}

/** Reads the real vendor feed through the existing WeatherService. */
class UpstreamConditionsSource(
    private val weatherService: WeatherService
) : ConditionsSource {

    private val log = LoggerFactory.getLogger(UpstreamConditionsSource::class.java)

    override suspend fun fetch(
        lat: Double,
        lon: Double,
        includeAirQuality: Boolean
    ): LocationSnapshot? {
        val latStr = lat.toString()
        val lonStr = lon.toString()

        val weather = weatherService.getWeatherData(latStr, lonStr).getOrNull()
        if (weather == null) {
            log.warn("Upstream weather fetch failed for {},{}", lat, lon)
            return null
        }

        val air = if (includeAirQuality) {
            weatherService.getAirPollutionData(latStr, lonStr).getOrNull()
        } else null

        return LocationSnapshot(
            conditions = weather.toConditions(),
            aqi = air?.toSample(),
            alerts = weather.alerts.orEmpty().filterNotNull().map { it.toAlertInfo() },
            nextPrecipitation = weather.firstPrecipitation()
        )
    }
}

/**
 * Deterministic-ish synthetic source used in development and load tests.
 *
 * It seeds itself from one real upstream sample per location (so the numbers are plausible for
 * that place and season) and then random-walks that seed on every tick. It also injects a
 * synthetic severe-weather alert roughly once every [alertOddsPerTick] ticks, which is the only
 * practical way to exercise the alert path — real alerts fire a couple of times a month.
 */
class SimulatedConditionsSource(
    private val seedSource: ConditionsSource,
    private val alertOddsPerTick: Int = 40,
    private val random: Random = Random.Default
) : ConditionsSource {

    private val log = LoggerFactory.getLogger(SimulatedConditionsSource::class.java)

    /** Last emitted snapshot per "lat,lon", so the walk is continuous rather than jumping. */
    private val state = java.util.concurrent.ConcurrentHashMap<String, LocationSnapshot>()

    override suspend fun fetch(
        lat: Double,
        lon: Double,
        includeAirQuality: Boolean
    ): LocationSnapshot? {
        val key = "$lat,$lon"
        val previous = state[key] ?: seedSource.fetch(lat, lon, includeAirQuality)?.also {
            log.info("Seeded simulator for {} from real upstream sample", key)
        } ?: return null

        val next = previous.drift(includeAirQuality)
        state[key] = next
        return next
    }

    private fun LocationSnapshot.drift(includeAirQuality: Boolean): LocationSnapshot {
        val c = conditions
        val nowSeconds = System.currentTimeMillis() / 1000

        val newTemp = c.tempC?.plus(random.nextDouble(-0.4, 0.4))
        val newGust = c.windGustMs?.let { g ->
            // occasional spike so the gust-spike derivation actually fires
            if (random.nextInt(12) == 0) g + random.nextDouble(4.0, 9.0)
            else (g + random.nextDouble(-0.6, 0.6)).coerceAtLeast(0.0)
        }

        val drifted = c.copy(
            observedAt = nowSeconds,
            tempC = newTemp?.round1(),
            feelsLikeC = c.feelsLikeC?.plus(random.nextDouble(-0.4, 0.4))?.round1(),
            humidity = c.humidity?.plus(random.nextInt(-2, 3))?.coerceIn(0, 100),
            windSpeedMs = c.windSpeedMs?.plus(random.nextDouble(-0.5, 0.5))?.coerceAtLeast(0.0)?.round1(),
            windGustMs = newGust?.round1(),
            uvi = c.uvi?.plus(random.nextDouble(-0.3, 0.3))?.coerceAtLeast(0.0)?.round1()
        )

        val driftedAqi = if (!includeAirQuality) null else aqi?.let { s ->
            val bandShift = if (random.nextInt(15) == 0) (if (random.nextBoolean()) 1 else -1) else 0
            s.copy(
                band = (s.band + bandShift).coerceIn(1, 5),
                pm25 = s.pm25?.plus(random.nextDouble(-4.0, 6.0))?.coerceAtLeast(0.0)?.round1()
            )
        }

        val driftedAlerts = when {
            alerts.isNotEmpty() && random.nextInt(10) == 0 -> emptyList()
            alerts.isEmpty() && random.nextInt(alertOddsPerTick) == 0 -> listOf(
                AlertInfo(
                    id = "sim-${nowSeconds}",
                    event = SIM_ALERT_EVENTS.random(random),
                    senderName = "Simulated Weather Service",
                    description = "Synthetic alert generated by SimulatedConditionsSource for testing.",
                    startsAt = nowSeconds,
                    endsAt = nowSeconds + 3600
                )
            )
            else -> alerts
        }

        return copy(conditions = drifted, aqi = driftedAqi, alerts = driftedAlerts)
    }

    private companion object {
        val SIM_ALERT_EVENTS = listOf(
            "Thunderstorm Warning",
            "Heavy Rainfall Alert",
            "Heat Wave Advisory",
            "High Wind Warning"
        )
    }
}

// ---------------------------------------------------------------------------
// Mapping from the existing upstream models into the trimmed live models
// ---------------------------------------------------------------------------

private fun Double.round1(): Double = kotlin.math.round(this * 10) / 10

/**
 * The upstream call never passes units=metric, so OpenWeather returns Kelvin. The REST weather
 * route has always relied on the Android/wear clients converting at display time (see
 * Compose-Weatherify's KELVIN_OFFSET), but this protocol's fields are named tempC/feelsLikeC and
 * are diffed server-side against Celsius-scale noise thresholds, so the conversion has to happen
 * here instead of trusting every consumer to know the quirk.
 */
private const val KELVIN_OFFSET = 273.15

private fun Double.kelvinToCelsius(): Double = this - KELVIN_OFFSET

private fun Weather.toConditions(): Conditions {
    val cur = current
    val w = cur?.weather?.firstOrNull()
    return Conditions(
        observedAt = cur?.dt ?: (System.currentTimeMillis() / 1000),
        tempC = cur?.temp?.kelvinToCelsius()?.round1(),
        feelsLikeC = cur?.feelsLike?.kelvinToCelsius()?.round1(),
        humidity = cur?.humidity,
        pressure = cur?.pressure,
        windSpeedMs = cur?.windSpeed,
        windGustMs = cur?.windGust,
        uvi = cur?.uvi,
        clouds = cur?.clouds,
        summary = w?.description,
        icon = w?.icon
    )
}

/**
 * Upstream alerts carry no identifier, but the client needs one to match an "issued" event with
 * the later "cleared" event. Deriving it from the fields that identify the alert in practice
 * (sender + event name + start time) gives a stable id without server-side storage.
 */
private fun Weather.Alert.toAlertInfo(): AlertInfo {
    val id = listOf(senderName.orEmpty(), event.orEmpty(), start?.toString().orEmpty())
        .joinToString("|")
        .hashCode()
        .let { abs(it).toString(36) }
    return AlertInfo(
        id = id,
        event = event ?: "Weather Alert",
        senderName = senderName,
        description = description?.take(500),
        startsAt = start?.toLong(),
        endsAt = end?.toLong()
    )
}

private fun Weather.firstPrecipitation(): LocationSnapshot.Precipitation? {
    val wet = setOf("Rain", "Drizzle", "Thunderstorm", "Snow")
    return hourly.orEmpty()
        .filterNotNull()
        .firstOrNull { h -> h.weather.orEmpty().filterNotNull().any { it.main in wet } }
        ?.let { h ->
            val desc = h.weather.orEmpty().filterNotNull().firstOrNull()?.description ?: "precipitation"
            LocationSnapshot.Precipitation(h.dt ?: 0L, desc)
        }
}

private fun AirQuality.toSample(): AirQualitySample? {
    val entry = list?.firstOrNull() ?: return null
    val c = entry.components
    return AirQualitySample(
        band = entry.main?.aqi ?: return null,
        pm25 = c?.pm25,
        pm10 = c?.pm10,
        o3 = c?.o3,
        no2 = c?.no2
    )
}
