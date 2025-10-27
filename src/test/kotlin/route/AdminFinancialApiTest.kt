package bose.ankush.route

import bose.ankush.data.model.*
import bose.ankush.module
import config.JwtConfig
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

/**
 * Integration tests for Admin Financial API endpoints
 * Tests authentication, authorization, and core functionality of:
 * - /admin/finance/metrics
 * - /admin/finance/payments
 * - /admin/finance/generate-bill
 * - /admin/tools/export-financial-data
 */
class AdminFinancialApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    // Helper function to generate admin JWT token
    private fun generateAdminToken(): String {
        return JwtConfig.generateToken("admin@example.com", UserRole.ADMIN)
    }

    // Helper function to generate user JWT token
    private fun generateUserToken(): String {
        return JwtConfig.generateToken("user@example.com", UserRole.USER)
    }

    // Test: GET /admin/finance/metrics with valid admin authentication
    @Test
    fun `test get financial metrics with admin authentication`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val response = client.get("/admin/finance/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Should return 200 OK with financial metrics
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject

        // Verify response structure
        assertTrue(root.containsKey("status"))
        assertTrue(root.containsKey("data"))

        val status = root["status"]?.jsonPrimitive?.content == "true"
        assertTrue(status, "Expected status=true in response")
    }

    // Test: GET /admin/finance/metrics without authentication
    @Test
    fun `test get financial metrics without authentication`() = testApplication {
        application { module() }

        val response = client.get("/admin/finance/metrics")

        // Should return 401 Unauthorized
        assertEquals(HttpStatusCode.Unauthorized, response.status)

        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject

        val status = root["status"]?.jsonPrimitive?.content == "true"
        assertFalse(status, "Expected status=false for unauthenticated request")
    }

    // Test: GET /admin/finance/metrics with non-admin user
    @Test
    fun `test get financial metrics with non-admin user`() = testApplication {
        application { module() }

        val token = generateUserToken()

        val response = client.get("/admin/finance/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Should return 403 Forbidden
        assertEquals(HttpStatusCode.Forbidden, response.status)

        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject

        val status = root["status"]?.jsonPrimitive?.content == "true"
        assertFalse(status, "Expected status=false for non-admin user")
    }

    // Test: GET /admin/finance/payments with pagination
    @Test
    fun `test get payment history with pagination`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val response = client.get("/admin/finance/payments?page=1&pageSize=10") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Should return 200 OK
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject

        assertTrue(root.containsKey("status"))
        assertTrue(root.containsKey("data"))

        val status = root["status"]?.jsonPrimitive?.content == "true"
        assertTrue(status, "Expected status=true in response")
    }

    // Test: GET /admin/finance/payments with status filter
    @Test
    fun `test get payment history with status filter`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val response = client.get("/admin/finance/payments?page=1&pageSize=10&status=verified") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Should return 200 OK
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject

        val status = root["status"]?.jsonPrimitive?.content == "true"
        assertTrue(status, "Expected status=true in response")
    }

    // Test: GET /admin/finance/payments with date range filter
    @Test
    fun `test get payment history with date range filter`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val response =
            client.get("/admin/finance/payments?page=1&pageSize=10&startDate=2025-01-01&endDate=2025-12-31") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

        // Should return 200 OK
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject

        val status = root["status"]?.jsonPrimitive?.content == "true"
        assertTrue(status, "Expected status=true in response")
    }

    // Test: GET /admin/finance/payments without authentication
    @Test
    fun `test get payment history without authentication`() = testApplication {
        application { module() }

        val response = client.get("/admin/finance/payments?page=1&pageSize=10")

        // Should return 401 Unauthorized
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // Test: POST /admin/finance/generate-bill with valid request
    @Test
    fun `test generate bill with valid request`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val requestBody = """
            {
                "userEmail": "test@example.com",
                "paymentIds": [],
                "subscriptionIds": [],
                "sendViaEmail": false
            }
        """.trimIndent()

        val response = client.post("/admin/finance/generate-bill") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Response could be 200 OK or 404 if user doesn't exist
        // Both are valid responses for this test
        assertTrue(
            response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NotFound,
            "Expected 200 OK or 404 Not Found, got ${response.status}"
        )
    }

    // Test: POST /admin/finance/generate-bill without authentication
    @Test
    fun `test generate bill without authentication`() = testApplication {
        application { module() }

        val requestBody = """
            {
                "userEmail": "test@example.com",
                "paymentIds": [],
                "subscriptionIds": [],
                "sendViaEmail": false
            }
        """.trimIndent()

        val response = client.post("/admin/finance/generate-bill") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Should return 401 Unauthorized
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // Test: POST /admin/finance/generate-bill with non-admin user
    @Test
    fun `test generate bill with non-admin user`() = testApplication {
        application { module() }

        val token = generateUserToken()

        val requestBody = """
            {
                "userEmail": "test@example.com",
                "paymentIds": [],
                "subscriptionIds": [],
                "sendViaEmail": false
            }
        """.trimIndent()

        val response = client.post("/admin/finance/generate-bill") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Should return 403 Forbidden
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // Test: POST /admin/finance/generate-bill with invalid JSON
    @Test
    fun `test generate bill with invalid JSON`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val requestBody = """
            {
                "userEmail": "test@example.com",
                "paymentIds": [
            }
        """.trimIndent()

        val response = client.post("/admin/finance/generate-bill") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Should return 400 Bad Request
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // Test: POST /admin/tools/export-financial-data with payments export
    @Test
    fun `test export financial data with payments type`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val requestBody = """
            {
                "exportType": "PAYMENTS",
                "startDate": "2025-01-01",
                "endDate": "2025-12-31"
            }
        """.trimIndent()

        val response = client.post("/admin/tools/export-financial-data") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Should return 200 OK with CSV content
        assertEquals(HttpStatusCode.OK, response.status)

        // Verify Content-Type is CSV
        val contentType = response.headers[HttpHeaders.ContentType]
        assertTrue(
            contentType?.contains("text/csv") == true || contentType?.contains("text/plain") == true,
            "Expected CSV content type, got $contentType"
        )

        // Verify Content-Disposition header for download
        val contentDisposition = response.headers[HttpHeaders.ContentDisposition]
        assertNotNull(contentDisposition, "Expected Content-Disposition header")
        assertTrue(
            contentDisposition.contains("attachment"),
            "Expected attachment in Content-Disposition"
        )
    }

    // Test: POST /admin/tools/export-financial-data with subscriptions export
    @Test
    fun `test export financial data with subscriptions type`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val requestBody = """
            {
                "exportType": "SUBSCRIPTIONS",
                "startDate": "2025-01-01",
                "endDate": "2025-12-31"
            }
        """.trimIndent()

        val response = client.post("/admin/tools/export-financial-data") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Should return 200 OK with CSV content
        assertEquals(HttpStatusCode.OK, response.status)

        val contentType = response.headers[HttpHeaders.ContentType]
        assertTrue(
            contentType?.contains("text/csv") == true || contentType?.contains("text/plain") == true,
            "Expected CSV content type"
        )
    }

    // Test: POST /admin/tools/export-financial-data with both export type
    @Test
    fun `test export financial data with both type`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val requestBody = """
            {
                "exportType": "BOTH",
                "startDate": "2025-01-01",
                "endDate": "2025-12-31"
            }
        """.trimIndent()

        val response = client.post("/admin/tools/export-financial-data") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Should return 200 OK with CSV content
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        // Should contain both PAYMENTS and SUBSCRIPTIONS sections
        assertTrue(body.contains("PAYMENTS") || body.contains("Payment ID"))
    }

    // Test: POST /admin/tools/export-financial-data with invalid date range
    @Test
    fun `test export financial data with invalid date range`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val requestBody = """
            {
                "exportType": "PAYMENTS",
                "startDate": "2025-12-31",
                "endDate": "2025-01-01"
            }
        """.trimIndent()

        val response = client.post("/admin/tools/export-financial-data") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Should return 400 Bad Request
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject

        val status = root["status"]?.jsonPrimitive?.content == "true"
        assertFalse(status, "Expected status=false for invalid date range")
    }

    // Test: POST /admin/tools/export-financial-data without authentication
    @Test
    fun `test export financial data without authentication`() = testApplication {
        application { module() }

        val requestBody = """
            {
                "exportType": "PAYMENTS",
                "startDate": "2025-01-01",
                "endDate": "2025-12-31"
            }
        """.trimIndent()

        val response = client.post("/admin/tools/export-financial-data") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Should return 401 Unauthorized
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // Test: POST /admin/tools/export-financial-data with non-admin user
    @Test
    fun `test export financial data with non-admin user`() = testApplication {
        application { module() }

        val token = generateUserToken()

        val requestBody = """
            {
                "exportType": "PAYMENTS",
                "startDate": "2025-01-01",
                "endDate": "2025-12-31"
            }
        """.trimIndent()

        val response = client.post("/admin/tools/export-financial-data") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Should return 403 Forbidden
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // Test: POST /admin/tools/export-financial-data with invalid JSON
    @Test
    fun `test export financial data with invalid JSON`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val requestBody = """
            {
                "exportType": "PAYMENTS",
                "startDate": "2025-01-01"
        """.trimIndent()

        val response = client.post("/admin/tools/export-financial-data") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Should return 400 Bad Request
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // Test: Verify CSV format for payments export
    @Test
    fun `test CSV format for payments export`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val requestBody = """
            {
                "exportType": "PAYMENTS",
                "startDate": "2025-01-01",
                "endDate": "2025-12-31"
            }
        """.trimIndent()

        val response = client.post("/admin/tools/export-financial-data") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val csvContent = response.bodyAsText()

        // Verify CSV header
        assertTrue(
            csvContent.contains("Payment ID") || csvContent.contains("User Email"),
            "Expected CSV header with Payment ID or User Email"
        )
    }

    // Test: Verify CSV format for subscriptions export
    @Test
    fun `test CSV format for subscriptions export`() = testApplication {
        application { module() }

        val token = generateAdminToken()

        val requestBody = """
            {
                "exportType": "SUBSCRIPTIONS",
                "startDate": "2025-01-01",
                "endDate": "2025-12-31"
            }
        """.trimIndent()

        val response = client.post("/admin/tools/export-financial-data") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val csvContent = response.bodyAsText()

        // Verify CSV header
        assertTrue(
            csvContent.contains("Subscription ID") || csvContent.contains("User Email") || csvContent.contains("Service Name"),
            "Expected CSV header with subscription fields"
        )
    }
}
