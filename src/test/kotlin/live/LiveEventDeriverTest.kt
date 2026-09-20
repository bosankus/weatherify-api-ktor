package live

import domain.model.live.AirQualitySample
import domain.model.live.AlertInfo
import domain.model.live.Conditions
import domain.model.live.LiveEvent
import domain.service.live.LiveEventDeriver
import domain.service.live.LocationSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LiveEventDeriverTest {

    private fun conditions(
        temp: Double = 28.0,
        feelsLike: Double = 30.0,
        gust: Double? = 3.0,
        uvi: Double = 2.0,
        humidity: Int = 70,
        icon: String = "01d",
        observedAt: Long = 1_700_000_000
    ) = Conditions(
        observedAt = observedAt,
        tempC = temp,
        feelsLikeC = feelsLike,
        humidity = humidity,
        pressure = 1010,
        windSpeedMs = 2.0,
        windGustMs = gust,
        uvi = uvi,
        clouds = 20,
        summary = "clear sky",
        icon = icon
    )

    private fun snapshot(
        c: Conditions = conditions(),
        aqi: AirQualitySample? = AirQualitySample(band = 2, pm25 = 20.0),
        alerts: List<AlertInfo> = emptyList(),
        rain: LocationSnapshot.Precipitation? = null
    ) = LocationSnapshot(c, aqi, alerts, rain)

    @Test
    fun `first observation produces a full snapshot`() {
        val events = LiveEventDeriver.derive(null, snapshot())
        assertEquals(1, events.size)
        assertIs<LiveEvent.Snapshot>(events.single())
    }

    @Test
    fun `sub-threshold jitter produces no events at all`() {
        // This is the noise filter that stops the server waking every radio on every poll.
        val previous = snapshot(conditions(temp = 28.0, feelsLike = 30.0))
        val current = snapshot(conditions(temp = 28.05, feelsLike = 30.1))
        assertTrue(LiveEventDeriver.derive(previous, current).isEmpty())
    }

    @Test
    fun `meaningful temperature move emits a conditions update naming the field`() {
        val previous = snapshot(conditions(temp = 28.0))
        val current = snapshot(conditions(temp = 28.6))
        val update = LiveEventDeriver.derive(previous, current)
            .filterIsInstance<LiveEvent.ConditionsUpdate>()
            .single()
        assertEquals(listOf("tempC"), update.changed)
    }

    @Test
    fun `new alert is emitted and ordered before ordinary conditions drift`() {
        val alert = AlertInfo(id = "a1", event = "Thunderstorm Warning")
        val previous = snapshot(conditions(temp = 28.0))
        val current = snapshot(conditions(temp = 31.0), alerts = listOf(alert))

        val events = LiveEventDeriver.derive(previous, current)
        assertIs<LiveEvent.AlertIssued>(events.first())
        // Ordering matters: under backpressure the tail is dropped first, so the frame that
        // matters most has to be produced first.
        assertTrue(events.indexOfFirst { it is LiveEvent.AlertIssued } <
            events.indexOfFirst { it is LiveEvent.ConditionsUpdate })
    }

    @Test
    fun `disappearing alert is emitted as cleared`() {
        val alert = AlertInfo(id = "a1", event = "Heat Wave Advisory")
        val previous = snapshot(alerts = listOf(alert))
        val current = snapshot(alerts = emptyList())

        val cleared = LiveEventDeriver.derive(previous, current)
            .filterIsInstance<LiveEvent.AlertCleared>()
            .single()
        assertEquals("a1", cleared.alertId)
    }

    @Test
    fun `unchanged alert is not re-emitted`() {
        val alert = AlertInfo(id = "a1", event = "High Wind Warning")
        val events = LiveEventDeriver.derive(snapshot(alerts = listOf(alert)), snapshot(alerts = listOf(alert)))
        assertTrue(events.none { it is LiveEvent.AlertIssued })
    }

    @Test
    fun `gust spike emits its own event`() {
        val events = LiveEventDeriver.derive(
            snapshot(conditions(gust = 3.0)),
            snapshot(conditions(gust = 9.0))
        )
        val spike = events.filterIsInstance<LiveEvent.WindGustSpike>().single()
        assertEquals(6.0, spike.deltaMs)
    }

    @Test
    fun `uv high fires on the crossing only, not on every poll above the band`() {
        val below = snapshot(conditions(uvi = 5.0))
        val above = snapshot(conditions(uvi = 7.0))
        val higher = snapshot(conditions(uvi = 8.0))

        assertEquals(1, LiveEventDeriver.derive(below, above).count { it is LiveEvent.UvHigh })
        // already high -> must not repeat, or the user gets nagged all afternoon
        assertEquals(0, LiveEventDeriver.derive(above, higher).count { it is LiveEvent.UvHigh })
    }

    @Test
    fun `aqi band change is reported with direction`() {
        val events = LiveEventDeriver.derive(
            snapshot(aqi = AirQualitySample(band = 2)),
            snapshot(aqi = AirQualitySample(band = 4))
        )
        val crossed = events.filterIsInstance<LiveEvent.AqiThresholdCrossed>().single()
        assertEquals(2, crossed.previousBand)
        assertEquals(4, crossed.band)
        assertTrue(crossed.worsening)
    }

    @Test
    fun `rain beyond the horizon is not announced`() {
        val observedAt = 1_700_000_000L
        val soon = LocationSnapshot.Precipitation(observedAt + 30 * 60, "light rain")
        val distant = LocationSnapshot.Precipitation(observedAt + 6 * 3600, "light rain")

        val nearEvents = LiveEventDeriver.derive(
            snapshot(conditions(observedAt = observedAt)),
            snapshot(conditions(observedAt = observedAt), rain = soon)
        )
        assertEquals(1, nearEvents.count { it is LiveEvent.RainStartingSoon })

        val farEvents = LiveEventDeriver.derive(
            snapshot(conditions(observedAt = observedAt)),
            snapshot(conditions(observedAt = observedAt), rain = distant)
        )
        assertEquals(0, farEvents.count { it is LiveEvent.RainStartingSoon })
    }
}
