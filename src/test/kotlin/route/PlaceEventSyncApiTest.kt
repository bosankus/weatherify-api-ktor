package bose.ankush.route

import bose.ankush.base.API_RATE_LIMIT
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import domain.service.InMemoryPlaceEventRepository
import domain.service.PlaceEventService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import util.Constants
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Offline-first sync routes for place events, run against an in-memory repository so they need
 * no MongoDB. Auth mirrors the "jwt-auth" provider with a test-only signing key.
 */
class PlaceEventSyncApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val algorithm = Algorithm.HMAC256("place-event-sync-test-secret")

    private fun tokenFor(email: String): String =
        JWT.create().withClaim(Constants.Auth.JWT_CLAIM_EMAIL, email).sign(algorithm)

    private val alice = tokenFor("alice@example.com")
    private val bob = tokenFor("bob@example.com")

    private fun ApplicationTestBuilder.placeEventsApp(
        repo: InMemoryPlaceEventRepository = InMemoryPlaceEventRepository()
    ): InMemoryPlaceEventRepository {
        application {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
            }
            install(Authentication) {
                jwt("jwt-auth") {
                    verifier(JWT.require(algorithm).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            install(RateLimit) {
                register(API_RATE_LIMIT) { rateLimiter(limit = 1_000, refillPeriod = 1.minutes) }
            }
            routing {
                rateLimit(API_RATE_LIMIT) { placeEventRoute(PlaceEventService(repo)) }
            }
        }
        return repo
    }

    private fun eventBody(
        id: String? = null,
        title: String = "Dinner",
        lat: Double = 12.9716,
        lon: Double = 77.5946,
        extra: String = ""
    ): String {
        val idField = id?.let { "\"id\": \"$it\"," } ?: ""
        return """
            {
                $idField
                "placeName": "Bengaluru",
                "lat": $lat,
                "lon": $lon,
                "title": "$title",
                "startsAt": "2026-10-25T14:00:00Z"
                $extra
            }
        """.trimIndent()
    }

    private suspend fun ApplicationTestBuilder.post(token: String, body: String): HttpResponse =
        client.post("/place-events") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.put(
        token: String,
        id: String,
        body: String,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse =
        client.put("/place-events/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
            headers.forEach { (k, v) -> header(k, v) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.delete(token: String, id: String): HttpResponse =
        client.delete("/place-events/$id") { header(HttpHeaders.Authorization, "Bearer $token") }

    private suspend fun ApplicationTestBuilder.sync(token: String, since: String? = null): HttpResponse =
        client.get("/place-events/sync") {
            header(HttpHeaders.Authorization, "Bearer $token")
            since?.let { parameter("since", it) }
        }

    private suspend fun HttpResponse.root(): JsonObject = json.parseToJsonElement(bodyAsText()).jsonObject
    private suspend fun HttpResponse.data(): JsonObject = root()["data"]!!.jsonObject

    @Test
    fun `create with client id twice returns 201 then 200 with the same event`() = testApplication {
        val repo = placeEventsApp()
        val id = UUID.randomUUID().toString()

        val first = post(alice, eventBody(id = id))
        assertEquals(HttpStatusCode.Created, first.status)
        val firstData = first.data()
        assertEquals(id, firstData["id"]!!.jsonPrimitive.content)
        assertNotNull(firstData["updatedAt"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, firstData["deletedAt"])

        val retry = post(alice, eventBody(id = id.uppercase(), title = "Dinner (retry)"))
        assertEquals(HttpStatusCode.OK, retry.status)
        assertEquals(firstData, retry.data())
        assertEquals(1, repo.created.size)
    }

    @Test
    fun `create without id keeps server id and 201`() = testApplication {
        placeEventsApp()
        val response = post(alice, eventBody())
        assertEquals(HttpStatusCode.Created, response.status)
        val id = response.data()["id"]!!.jsonPrimitive.content
        assertEquals(24, id.length)
    }

    @Test
    fun `create with malformed id is a 400 validation error`() = testApplication {
        placeEventsApp()
        val response = post(alice, eventBody(id = "not-a-uuid"))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val root = response.root()
        assertEquals("false", root["status"]!!.jsonPrimitive.content)
        assertEquals("id must be a UUID string", root["message"]!!.jsonPrimitive.content)
    }

    @Test
    fun `put with stale base returns 409 with the current event`() = testApplication {
        placeEventsApp()
        val id = UUID.randomUUID().toString()
        val created = post(alice, eventBody(id = id)).data()
        val base = created["updatedAt"]!!.jsonPrimitive.content

        val firstEdit = put(alice, id, eventBody(title = "Dinner at 8", extra = ""","baseUpdatedAt": "$base""""))
        assertEquals(HttpStatusCode.OK, firstEdit.status)
        val current = firstEdit.data()
        assertEquals("Dinner at 8", current["title"]!!.jsonPrimitive.content)
        assertNotEquals(base, current["updatedAt"]!!.jsonPrimitive.content)

        // Second device still holds the original version.
        val stale = put(alice, id, eventBody(title = "Dinner at 9", extra = ""","baseUpdatedAt": "$base""""))
        assertEquals(HttpStatusCode.Conflict, stale.status)
        assertEquals(current, stale.data())

        val staleHeader = put(alice, id, eventBody(title = "Dinner at 9"), mapOf(HttpHeaders.IfUnmodifiedSince to base))
        assertEquals(HttpStatusCode.Conflict, staleHeader.status)

        // No base: last write wins.
        val lww = put(alice, id, eventBody(title = "Dinner at 9"))
        assertEquals(HttpStatusCode.OK, lww.status)
        assertEquals("Dinner at 9", lww.data()["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `put validates like create`() = testApplication {
        placeEventsApp()
        val id = UUID.randomUUID().toString()
        post(alice, eventBody(id = id))
        val response = put(alice, id, eventBody(lat = 95.0))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("lat must be between -90 and 90", response.root()["message"]!!.jsonPrimitive.content)
    }

    @Test
    fun `delete then sync shows tombstone and place list hides it`() = testApplication {
        placeEventsApp()
        val kept = UUID.randomUUID().toString()
        val removed = UUID.randomUUID().toString()
        post(alice, eventBody(id = kept, title = "Kept"))
        post(alice, eventBody(id = removed, title = "Removed"))

        val initial = sync(alice).data()
        assertEquals(2, initial["items"]!!.jsonArray.size)
        val checkpoint = initial["checkpoint"]!!.jsonPrimitive.content
        assertEquals(JsonNull, initial["nextSince"])

        assertEquals(HttpStatusCode.NoContent, delete(alice, removed).status)
        // Idempotent.
        assertEquals(HttpStatusCode.NoContent, delete(alice, removed).status)

        val delta = sync(alice, since = checkpoint)
        assertEquals(HttpStatusCode.OK, delta.status)
        val items = delta.data()["items"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf(removed), items.map { it["id"]!!.jsonPrimitive.content })
        assertTrue(items.single()["deletedAt"] !is JsonNull)

        // Deleted rows cannot be edited and leave the place list.
        assertEquals(HttpStatusCode.NotFound, put(alice, removed, eventBody()).status)
        val list = client.get("/place-events?lat=12.9716&lon=77.5946") {
            header(HttpHeaders.Authorization, "Bearer $alice")
        }
        assertEquals(HttpStatusCode.OK, list.status)
        val titles = json.parseToJsonElement(list.bodyAsText()).jsonObject["data"]!!.jsonArray
            .map { it.jsonObject["title"]!!.jsonPrimitive.content }
        assertEquals(listOf("Kept"), titles)
    }

    @Test
    fun `another account's id is 404 on post put and delete`() = testApplication {
        val repo = placeEventsApp()
        val id = UUID.randomUUID().toString()
        assertEquals(HttpStatusCode.Created, post(alice, eventBody(id = id)).status)

        assertEquals(HttpStatusCode.NotFound, post(bob, eventBody(id = id)).status)
        assertEquals(HttpStatusCode.NotFound, put(bob, id, eventBody(title = "Hijack")).status)
        assertEquals(HttpStatusCode.NotFound, delete(bob, id).status)

        // Unknown ids answer exactly like another account's id.
        assertEquals(HttpStatusCode.NotFound, delete(bob, UUID.randomUUID().toString()).status)

        // Alice's event is untouched and Bob's sync never sees it.
        val stored = repo.created.single()
        assertEquals("Dinner", stored.title)
        assertEquals(null, stored.deletedAt)
        assertEquals(0, sync(bob).data()["items"]!!.jsonArray.size)
    }

    @Test
    fun `sync rejects a malformed since`() = testApplication {
        placeEventsApp()
        assertEquals(HttpStatusCode.BadRequest, sync(alice, since = "yesterday").status)
        assertEquals(HttpStatusCode.OK, sync(alice, since = "2026-10-01T00:00:00Z").status)
    }

    @Test
    fun `routes require authentication`() = testApplication {
        placeEventsApp()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/place-events/sync").status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/place-events/${UUID.randomUUID()}").status)
    }
}
