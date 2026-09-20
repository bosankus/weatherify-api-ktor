package live

import domain.model.live.AlertInfo
import domain.model.live.Conditions
import domain.model.live.LiveChannel
import domain.model.live.LiveEvent
import domain.model.live.ServerMessage
import domain.service.live.LiveConnection
import domain.service.live.LiveEntitlements
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveConnectionTest {

    private fun conditions() = Conditions(
        observedAt = 1_700_000_000, tempC = 28.0, feelsLikeC = 30.0, humidity = 70,
        pressure = 1010, windSpeedMs = 2.0, windGustMs = 3.0, uvi = 2.0,
        clouds = 10, summary = "clear sky", icon = "01d"
    )

    private fun conditionsEvent() = LiveEvent.ConditionsUpdate(conditions(), listOf("tempC"))
    private fun alertEvent() = LiveEvent.AlertIssued(AlertInfo(id = "a1", event = "Thunderstorm Warning"))

    private fun eventFrame(event: LiveEvent) =
        ServerMessage.Event(topic = "loc:tuvz0", seq = 1, ts = 0, event = event)

    private fun connection(
        entitlements: LiveEntitlements = LiveEntitlements.PREMIUM,
        capacity: Int = 4
    ) = LiveConnection(
        id = "c1", email = "a@b.com", entitlements = entitlements, queueCapacity = capacity
    )

    @Test
    fun `a full queue drops non-critical frames but keeps the connection alive`() {
        val conn = connection(capacity = 2)
        repeat(6) { assertTrue(conn.offer(eventFrame(conditionsEvent()))) }
        assertTrue(conn.droppedFrames.get() > 0)
        assertFalse(conn.closeRequested)
    }

    @Test
    fun `repeated undeliverable alerts close the connection as a slow consumer`() {
        // Dropping an alert silently is the worst outcome for this feature, so a client that
        // cannot keep up with critical traffic is disconnected instead. Reconnect + fresh
        // snapshot restores correctness; a swallowed alert never would.
        val conn = connection(capacity = 1)
        conn.offer(eventFrame(conditionsEvent())) // fills the queue

        var alive = true
        repeat(5) { if (alive) alive = conn.offer(eventFrame(alertEvent())) }

        assertFalse(alive)
        assertTrue(conn.closeRequested)
    }

    @Test
    fun `free tier is throttled on conditions but never on alerts`() {
        val conn = connection(LiveEntitlements.FREE)
        val topic = "loc:tuvz0"
        val t0 = 1_000_000L

        assertTrue(conn.shouldDeliver(topic, conditionsEvent(), t0))
        // 10s later — inside the 60s free cadence, so suppressed
        assertFalse(conn.shouldDeliver(topic, conditionsEvent(), t0 + 10_000))
        // alerts bypass the throttle entirely: cadence is a lever on convenience, not on safety
        assertTrue(conn.shouldDeliver(topic, alertEvent(), t0 + 10_000))
        // past the cadence window
        assertTrue(conn.shouldDeliver(topic, conditionsEvent(), t0 + 61_000))
    }

    @Test
    fun `premium tier is throttled at its own faster cadence`() {
        val conn = connection(LiveEntitlements.PREMIUM)
        val topic = "loc:tuvz0"
        val t0 = 1_000_000L

        assertTrue(conn.shouldDeliver(topic, conditionsEvent(), t0))
        assertFalse(conn.shouldDeliver(topic, conditionsEvent(), t0 + 5_000))
        assertTrue(conn.shouldDeliver(topic, conditionsEvent(), t0 + 16_000))
    }

    @Test
    fun `throttling is per topic, not per connection`() {
        val conn = connection(LiveEntitlements.FREE)
        val t0 = 1_000_000L
        assertTrue(conn.shouldDeliver("loc:aaaaa", conditionsEvent(), t0))
        // a different location must not be suppressed by the first one's timer
        assertTrue(conn.shouldDeliver("loc:bbbbb", conditionsEvent(), t0))
    }

    @Test
    fun `events outside the subscribed channel set are filtered out`() {
        val conn = connection(LiveEntitlements.PREMIUM)
        conn.channels = setOf(LiveChannel.ALERTS)
        assertFalse(conn.shouldDeliver("loc:tuvz0", conditionsEvent(), 1_000_000L))
        assertTrue(conn.shouldDeliver("loc:tuvz0", alertEvent(), 1_000_000L))
    }

    @Test
    fun `free tier is not entitled to the aqi channel`() {
        assertFalse(LiveEntitlements.FREE.allows(LiveChannel.AQI))
        assertTrue(LiveEntitlements.FREE.allows(LiveChannel.ALERTS))
        assertTrue(LiveEntitlements.PREMIUM.allows(LiveChannel.AQI))
    }

    @Test
    fun `snapshot bypasses the cadence throttle`() {
        // A freshly subscribed client must paint immediately rather than waiting a full cadence.
        val conn = connection(LiveEntitlements.FREE)
        val topic = "loc:tuvz0"
        val t0 = 1_000_000L
        assertTrue(conn.shouldDeliver(topic, conditionsEvent(), t0))
        val snapshot = LiveEvent.Snapshot(conditions(), null, emptyList())
        assertTrue(conn.shouldDeliver(topic, snapshot, t0 + 1_000))
    }

    @Test
    fun `forgetting a topic clears its throttle state`() {
        val conn = connection(LiveEntitlements.FREE)
        val topic = "loc:tuvz0"
        conn.topics.add(topic)
        val t0 = 1_000_000L
        assertTrue(conn.shouldDeliver(topic, conditionsEvent(), t0))
        conn.forgetTopic(topic)
        assertFalse(conn.topics.contains(topic))
        // resubscribing starts clean, so the first frame after resubscribe is not suppressed
        assertTrue(conn.shouldDeliver(topic, conditionsEvent(), t0 + 1_000))
    }

    @Test
    fun `dropped frame counter is exposed for monitoring`() {
        val conn = connection(capacity = 1)
        repeat(4) { conn.offer(eventFrame(conditionsEvent())) }
        assertEquals(3, conn.droppedFrames.get())
    }
}
