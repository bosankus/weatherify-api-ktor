package bose.ankush.base

import bose.ankush.data.model.ApiResponse
import config.Environment
import config.JwtConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import util.Constants

/**
 * Configure authentication for the application
 */
fun Application.configureAuthentication() {
    install(Authentication) {
        jwt("jwt-auth") {
            realm = Environment.getJwtRealm()
            verifier(JwtConfig.verifier)

            validate { credential ->
                try {
                    // Extract email from JWT token
                    val email = credential.payload.getClaim("email").asString()
                    if (email.isNotEmpty()) {
                        // Create a JWTPrincipal with the token's payload
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    // Log the exception if needed
                    null
                }
            }

            challenge { defaultScheme, realm ->
                // Set the response status to 401 Unauthorized
                call.response.status(HttpStatusCode.Unauthorized)
                call.response.header(HttpHeaders.WWWAuthenticate, "$defaultScheme realm=\"$realm\"")

                // Create an ApiResponse with an error message that includes refresh token info
                val apiResponse = ApiResponse(
                    status = false,
                    message = "Authentication failed: Invalid or expired token. If your token is expired, you can get a new one at the /refresh-token endpoint.",
                    data = mapOf(
                        "refreshEndpoint" to Constants.Api.REFRESH_TOKEN_ENDPOINT,
                        "method" to "POST",
                        "requestFormat" to "{ \"token\": \"your-expired-token\" }"
                    )
                )

                // Respond with the JSON-encoded ApiResponse
                call.respondText(
                    Json.encodeToString(apiResponse),
                    ContentType.Application.Json
                )
            }
        }
    }
}