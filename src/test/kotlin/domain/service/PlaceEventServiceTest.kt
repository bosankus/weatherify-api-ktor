package domain.service

import com.androidplay.core.common.Result
import com.androidplay.weatherify.domain.PlaceEvent
import com.androidplay.weatherify.domain.PlaceProximity
import com.androidplay.weatherify.repository.PlaceEventRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceEventServiceTest {

    private class MockPlaceEventRepository : PlaceEventRepository {
        val created = mutableListOf<PlaceEvent>()

        override suspend fun create(event: PlaceEvent): Result<PlaceEvent> {
            created += event
            return Result.success(event)
        }

        override suspend fun listByUserAndPlace(
            email: String,
            lat: Double,
            lon: Double
        ): Result<List<PlaceEvent>> =
            Result.success(
                created.filter {
                    it.userEmail == email &&
                        PlaceProximity.withinMatchRadius(lat, lon, it.lat, it.lon)
                }.sortedBy { it.startsAt }
            )
    }

    private val repo = MockPlaceEventRepository()
    private val service = PlaceEventService(repo)

    @Test
    fun `validateCreate rejects missing coordinates`() {
        val error = service.validateCreate(
            PlaceEventService.CreateInput(
                placeName = "Home",
                lat = null,
                lon = null,
                title = "Leave for office",
                startsAt = "2026-10-04T09:00:00Z"
            )
        )
        assertNotNull(error)
        assertTrue(error!!.contains("coordinates", ignoreCase = true))
    }

    @Test
    fun `validateCreate rejects blank title`() {
        val error = service.validateCreate(
            PlaceEventService.CreateInput(
                placeName = "Home",
                lat = 12.97,
                lon = 77.59,
                title = "  ",
                startsAt = "2026-10-04T09:00:00Z"
            )
        )
        assertEquals("title is required", error)
    }

    @Test
    fun `validateCreate rejects invalid startsAt`() {
        val error = service.validateCreate(
            PlaceEventService.CreateInput(
                placeName = "Home",
                lat = 12.97,
                lon = 77.59,
                title = "Dinner",
                startsAt = "not-an-instant"
            )
        )
        assertNotNull(error)
        assertTrue(error!!.contains("ISO-8601"))
    }

    @Test
    fun `validateListCoords rejects missing lat lon`() {
        assertNotNull(service.validateListCoords(null, 77.0))
        assertNotNull(service.validateListCoords(12.0, null))
        assertNull(service.validateListCoords(12.97, 77.59))
    }

    @Test
    fun `create stores account from caller and returns event`() = runBlocking {
        val result = service.create(
            "user@example.com",
            PlaceEventService.CreateInput(
                placeName = "Bengaluru",
                lat = 12.9716,
                lon = 77.5946,
                title = "Team standup",
                startsAt = "2026-10-05T04:30:00Z",
                note = "Bring laptop"
            )
        )
        assertTrue(result is Result.Success)
        val event = (result as Result.Success).data
        assertEquals("user@example.com", event.userEmail)
        assertEquals("Bengaluru", event.placeName)
        assertEquals("Team standup", event.title)
        assertEquals(1, repo.created.size)
    }

    @Test
    fun `list returns soonest first for the given place only`() = runBlocking {
        service.create(
            "user@example.com",
            PlaceEventService.CreateInput("A", 1.0, 2.0, "Later", "2026-10-06T10:00:00Z")
        )
        service.create(
            "user@example.com",
            PlaceEventService.CreateInput("A", 1.0, 2.0, "Sooner", "2026-10-05T10:00:00Z")
        )
        service.create(
            "user@example.com",
            PlaceEventService.CreateInput("Other", 9.0, 9.0, "Elsewhere", "2026-10-04T10:00:00Z")
        )

        val listed = (service.list("user@example.com", 1.0, 2.0) as Result.Success).data
        assertEquals(listOf("Sooner", "Later"), listed.map { it.title })
    }

    @Test
    fun `list keeps events within about 1 km and drops farther ones`() = runBlocking {
        val hereLat = 12.9716
        val hereLon = 77.5946
        val nearbyLat = hereLat + (500.0 / 111_320.0)
        val farLat = hereLat + (3_000.0 / 111_320.0)
        service.create(
            "user@example.com",
            PlaceEventService.CreateInput("Home", nearbyLat, hereLon, "Nearby", "2026-10-05T10:00:00Z")
        )
        service.create(
            "user@example.com",
            PlaceEventService.CreateInput("Home", farLat, hereLon, "Far", "2026-10-04T10:00:00Z")
        )

        val listed = (service.list("user@example.com", hereLat, hereLon) as Result.Success).data
        assertEquals(listOf("Nearby"), listed.map { it.title })
    }
}
