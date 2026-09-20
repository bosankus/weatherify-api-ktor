package live

import util.Geohash
import util.LiveTopic
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GeohashTest {

    @Test
    fun `encodes known reference coordinates`() {
        // Well-known geohash fixtures.
        assertEquals("ezs42", Geohash.encode(42.6, -5.6))
        assertEquals("u4pru", Geohash.encode(57.64911, 10.40744))
    }

    @Test
    fun `nearby coordinates collapse to the same topic`() {
        // Two users a few hundred metres apart in central Kolkata must share one poller,
        // otherwise the per-location fan-out degrades back into per-user polling.
        val a = LiveTopic.forCoordinates(22.5726, 88.3639)
        val b = LiveTopic.forCoordinates(22.5751, 88.3662)
        assertEquals(a, b)
    }

    @Test
    fun `distant coordinates produce different topics`() {
        val kolkata = LiveTopic.forCoordinates(22.5726, 88.3639)
        val bengaluru = LiveTopic.forCoordinates(12.9716, 77.5946)
        assertNotEquals(kolkata, bengaluru)
    }

    @Test
    fun `cell centre round-trips back into the same cell`() {
        val topic = LiveTopic.forCoordinates(22.5726, 88.3639)
        val (lat, lon) = LiveTopic.centerOf(topic)
        assertEquals(topic, LiveTopic.forCoordinates(lat, lon))
        // precision 5 is ~4.9km, so the centre is always within ~3.5km of the input
        assertTrue(abs(lat - 22.5726) < 0.05)
        assertTrue(abs(lon - 88.3639) < 0.05)
    }

    @Test
    fun `topic validation accepts generated topics and rejects junk`() {
        assertTrue(LiveTopic.isValid(LiveTopic.forCoordinates(51.5072, -0.1276)))
        assertFalse(LiveTopic.isValid("loc:"))
        assertFalse(LiveTopic.isValid("room:abcde"))
        assertFalse(LiveTopic.isValid("loc:abcdefgh"))
    }

    @Test
    fun `coordinate bounds are enforced`() {
        assertTrue(LiveTopic.validCoordinates(0.0, 0.0))
        assertTrue(LiveTopic.validCoordinates(-90.0, 180.0))
        assertFalse(LiveTopic.validCoordinates(91.0, 0.0))
        assertFalse(LiveTopic.validCoordinates(0.0, 181.0))
        assertFalse(LiveTopic.validCoordinates(Double.NaN, 0.0))
    }
}
