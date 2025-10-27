package bose.ankush.route

import bose.ankush.module
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MockApiRouteTest {
    @Test
    fun createMock_success() = testApplication {
        application { module() }
        val resp = client.post("/mock/create") {
            contentType(ContentType.Application.Json)
            setBody("""{"foo":"bar","n":1}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        val body = resp.bodyAsText()
        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
        val status = root["status"]!!.jsonPrimitive.content == "true"
        assertTrue(status, "Expected status=true in response: $body")
        val data = root["data"]?.jsonObject
        assertNotNull(data, "Expected data object in response: $body")
        assertNotNull(data["id"], "Expected id in data: $body")
        assertNotNull(data["url"], "Expected url in data: $body")
    }

    @Test
    fun createMock_invalidJson() = testApplication {
        application { module() }
        val resp = client.post("/mock/create") {
            contentType(ContentType.Application.Json)
            setBody("""{"foo": }""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        val body = resp.bodyAsText()
        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
        val status = root["status"]!!.jsonPrimitive.content == "true"
        assertTrue(!status, "Expected status=false for invalid JSON: $body")
    }
}
