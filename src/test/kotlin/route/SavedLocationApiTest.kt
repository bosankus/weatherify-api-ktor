package bose.ankush.route

import bose.ankush.data.model.UserRole
import bose.ankush.module
import config.JwtConfig
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedLocationApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun generateUserToken(): String {
        return JwtConfig.generateToken("user@example.com", UserRole.USER)
    }

    @Test
    fun `test save location with authentication`() = testApplication {
        application { module() }

        val token = generateUserToken()
        val requestBody = """
            {
                "name": "London",
                "lat": 51.5074,
                "lon": -0.1278
            }
        """.trimIndent()

        val response = client.post("/locations") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        assertTrue(root["status"]?.jsonPrimitive?.content == "true")
    }

    @Test
    fun `test get locations with authentication`() = testApplication {
        application { module() }

        val token = generateUserToken()

        val response = client.get("/locations") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        assertTrue(root["status"]?.jsonPrimitive?.content == "true")
    }

    @Test
    fun `test save location without authentication`() = testApplication {
        application { module() }

        val requestBody = """
            {
                "name": "London",
                "lat": 51.5074,
                "lon": -0.1278
            }
        """.trimIndent()

        val response = client.post("/locations") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
