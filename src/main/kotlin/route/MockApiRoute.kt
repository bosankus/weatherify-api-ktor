package bose.ankush.route

import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.request.userAgent
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.koin.ktor.ext.inject
import util.Analytics
import util.MockStore

fun Route.mockApiRoute() {
    val analytics: Analytics by application.inject()

    route("/mock") {
        // Create a new mock endpoint by posting raw JSON
        post("/create") {
            val raw = call.receiveText().trim()
            if (raw.isEmpty()) {
                return@post call.respondError<Unit>(
                    message = "Empty body. Provide JSON payload.",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
            }

            // Validate JSON
            val json = Json {
                ignoreUnknownKeys = true; isLenient = true; allowSpecialFloatingPointValues = true
            }
            val element: JsonElement = try {
                json.parseToJsonElement(raw)
            } catch (e: Exception) {
                return@post call.respondError<Unit>(
                    message = "Invalid JSON: ${e.message}",
                    data = Unit,
                    status = HttpStatusCode.BadRequest
                )
            }

            val id = MockStore.generateId()
            // Store minified JSON
            val minified = json.encodeToString(JsonElement.serializer(), element)
            MockStore.put(id, minified)

            val local = call.request.local
            val serverPort = local.serverPort
            val serverHost = local.serverHost
            val scheme = local.scheme
            val defaultPort =
                (scheme == "http" && serverPort == 80) || (scheme == "https" && serverPort == 443)
            val base =
                if (defaultPort) "$scheme://$serverHost" else "$scheme://$serverHost:$serverPort"
            val url = "$base/mock/$id"

            analytics.event(
                name = "mock_create",
                params = mapOf("id" to id),
                userAgent = call.request.userAgent()
            )

            return@post call.respondSuccess(
                message = "Mock created",
                data = mapOf("id" to id, "url" to url),
                status = HttpStatusCode.Created
            )
        }

        // Serve a stored mock as raw JSON
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respondText(
                text = "Missing id",
                contentType = ContentType.Text.Plain,
                status = HttpStatusCode.BadRequest
            )
            val json = MockStore.get(id)
            if (json == null) {
                analytics.event(
                    name = "mock_get_not_found",
                    params = mapOf("id" to id),
                    userAgent = call.request.userAgent()
                )
                return@get call.respondText(
                    text = "Not found",
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.NotFound
                )
            }

            analytics.event(
                name = "mock_get",
                params = mapOf("id" to id),
                userAgent = call.request.userAgent()
            )
            return@get call.respondText(
                text = json,
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        }

        // Delete a stored mock
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respondError<Unit>(
                message = "Missing id",
                data = Unit,
                status = HttpStatusCode.BadRequest
            )
            val removed = MockStore.remove(id)
            if (!removed) {
                return@delete call.respondError<Unit>(
                    message = "Mock not found",
                    data = Unit,
                    status = HttpStatusCode.NotFound
                )
            }

            analytics.event(
                name = "mock_delete",
                params = mapOf("id" to id),
                userAgent = call.request.userAgent()
            )

            return@delete call.respondSuccess(
                message = "Mock deleted",
                data = Unit,
                status = HttpStatusCode.OK
            )
        }
    }
}
