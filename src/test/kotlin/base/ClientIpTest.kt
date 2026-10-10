package bose.ankush.base

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class ClientIpTest {
    @Test
    fun `falls back to the socket address without a forwarded header`() {
        assertEquals("10.0.0.1", resolveClientIp(null, "10.0.0.1", trustedHops = 1))
        assertEquals("10.0.0.1", resolveClientIp("  , ", "10.0.0.1", trustedHops = 1))
    }

    @Test
    fun `uses the rightmost entry for one trusted hop`() {
        assertEquals("203.0.113.7", resolveClientIp("6.6.6.6, 203.0.113.7", "10.0.0.1", trustedHops = 1))
    }

    @Test
    fun `client-injected leftmost entries are ignored`() {
        assertEquals("203.0.113.7", resolveClientIp("1.1.1.1, 2.2.2.2, 203.0.113.7", "10.0.0.1", trustedHops = 1))
    }

    @Test
    fun `counts from the right for multiple trusted hops`() {
        assertEquals("203.0.113.7", resolveClientIp("6.6.6.6, 203.0.113.7, 35.191.0.1", "10.0.0.1", trustedHops = 2))
    }

    @Test
    fun `shorter header than hop count uses the leftmost entry`() {
        assertEquals("203.0.113.7", resolveClientIp("203.0.113.7", "10.0.0.1", trustedHops = 3))
    }

    @Test
    fun `rotating a spoofed forwarded entry does not reset the rate limit`() = testApplication {
        application {
            install(RateLimit) {
                register(RateLimitName("t")) {
                    rateLimiter(limit = 2, refillPeriod = 1.minutes)
                    requestKey { call -> call.clientIp() }
                }
            }
            routing { rateLimit(RateLimitName("t")) { get("/x") { call.respondText("ok") } } }
        }

        val statuses = (1..4).map { i ->
            client.get("/x") { header("X-Forwarded-For", "9.9.9.$i, 203.0.113.7") }.status
        }
        assertEquals(
            listOf(HttpStatusCode.OK, HttpStatusCode.OK, HttpStatusCode.TooManyRequests, HttpStatusCode.TooManyRequests),
            statuses,
        )
    }

    @Test
    fun `different real clients get separate buckets`() = testApplication {
        application {
            install(RateLimit) {
                register(RateLimitName("t")) {
                    rateLimiter(limit = 1, refillPeriod = 1.minutes)
                    requestKey { call -> call.clientIp() }
                }
            }
            routing { rateLimit(RateLimitName("t")) { get("/x") { call.respondText("ok") } } }
        }

        val a = client.get("/x") { header("X-Forwarded-For", "203.0.113.1") }.status
        val b = client.get("/x") { header("X-Forwarded-For", "203.0.113.2") }.status
        assertEquals(HttpStatusCode.OK, a)
        assertEquals(HttpStatusCode.OK, b)
    }
}
