package domain.service

import com.androidplay.core.common.Result
import com.androidplay.weatherify.domain.PlaceEvent
import com.androidplay.weatherify.domain.PlaceEventSync
import org.bson.types.ObjectId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceEventServiceTest {

    private val repo = InMemoryPlaceEventRepository()
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

    private fun row(title: String, updatedAt: String?, createdAt: String = "2026-10-01T00:00:00Z", email: String = "user@example.com") =
        PlaceEvent(
            id = ObjectId(),
            userEmail = email,
            lat = 1.0,
            lon = 2.0,
            title = title,
            startsAt = "2026-10-05T10:00:00Z",
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    @Test
    fun `sync pages through rows sharing one timestamp without skipping`() = runBlocking {
        val same = "2026-10-22T10:00:00.000Z"
        repeat(5) { repo.created += row("same-$it", same) }
        repo.created += row("later", "2026-10-22T10:00:00.001Z")

        val seen = mutableListOf<String>()
        var cursor: PlaceEventSync.Cursor? = null
        var pages = 0
        do {
            val page = (service.sync("user@example.com", cursor, limit = 2) as Result.Success).data
            seen += page.items.map { it.title }
            cursor = page.nextSince?.let { PlaceEventSync.parseCursor(it)!! }
            pages++
        } while (cursor != null)

        assertEquals(3, pages)
        assertEquals(6, seen.size)
        assertEquals(6, seen.toSet().size)
        assertEquals("later", seen.last())
    }

    @Test
    fun `sync treats legacy rows without updatedAt as updated at createdAt`() = runBlocking {
        repo.created += row("legacy", updatedAt = null, createdAt = "2026-09-01T08:00:00.123456Z")
        repo.created += row("new", updatedAt = "2026-10-22T10:00:00.000Z")
        repo.created += row("other user", updatedAt = "2026-10-22T10:00:00.000Z", email = "other@example.com")

        val all = (service.sync("user@example.com", null) as Result.Success).data
        assertEquals(listOf("legacy", "new"), all.items.map { it.title })
        assertNull(all.nextSince)

        val since = PlaceEventSync.parseCursor("2026-09-01T00:00:00Z")
        val delta = (service.sync("user@example.com", since) as Result.Success).data
        assertEquals(listOf("legacy", "new"), delta.items.map { it.title })

        val after = PlaceEventSync.parseCursor("2026-09-02T00:00:00Z")
        assertEquals(listOf("new"), (service.sync("user@example.com", after) as Result.Success).data.items.map { it.title })
    }

    @Test
    fun `sync caps a page at 500`() = runBlocking {
        repeat(501) { repo.created += row("e$it", PlaceEventSync.format(java.time.Instant.parse("2026-10-22T10:00:00Z").plusMillis(it.toLong()))) }
        val page = (service.sync("user@example.com", null, limit = 10_000) as Result.Success).data
        assertEquals(500, page.items.size)
        assertNotNull(page.nextSince)
        val rest = (service.sync("user@example.com", PlaceEventSync.parseCursor(page.nextSince!!)) as Result.Success).data
        assertEquals(listOf("e500"), rest.items.map { it.title })
        assertNull(rest.nextSince)
    }

    @Test
    fun `update and delete work on legacy server-id rows`() = runBlocking {
        val legacy = row("legacy", updatedAt = null)
        repo.created += legacy
        val input = PlaceEventService.CreateInput("Home", 1.0, 2.0, "Edited", "2026-10-06T10:00:00Z")
        val updated = service.update("user@example.com", legacy.id.toHexString(), input, null)
        assertTrue((updated as Result.Success).data is PlaceEventService.UpdateOutcome.Updated)
        assertEquals("Edited", repo.created.single().title)
        assertNotNull(repo.created.single().updatedAt)

        val deleted = service.delete("user@example.com", legacy.id.toHexString())
        assertEquals(PlaceEventService.DeleteOutcome.Deleted, (deleted as Result.Success).data)
        assertNotNull(repo.created.single().deletedAt)
        assertTrue((service.list("user@example.com", 1.0, 2.0) as Result.Success).data.isEmpty())
    }

    @Test
    fun `http-date base compares at second precision`() = runBlocking {
        val r = row("x", updatedAt = "2026-10-22T10:00:00.750Z")
        repo.created += r
        val input = PlaceEventService.CreateInput("Home", 1.0, 2.0, "Edited", "2026-10-06T10:00:00Z")
        val sameSecond = PlaceEventService.UpdateBase(java.time.Instant.parse("2026-10-22T10:00:00Z"), secondPrecision = true)
        val ok = service.update("user@example.com", r.id.toHexString(), input, sameSecond)
        assertTrue((ok as Result.Success).data is PlaceEventService.UpdateOutcome.Updated)

        val stale = PlaceEventService.UpdateBase(java.time.Instant.parse("2026-10-22T10:00:00.750Z"))
        val conflict = service.update("user@example.com", r.id.toHexString(), input, stale)
        assertTrue((conflict as Result.Success).data is PlaceEventService.UpdateOutcome.Conflict)
    }
}
