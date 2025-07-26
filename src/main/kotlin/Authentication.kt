package bose.ankush

import config.Environment
import config.JwtConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.header

/**
 * Configure authentication for the application
 */
fun Application.configureAuthentication() {
    install(Authentication) {
        jwt("jwt-auth") {
            realm = Environment.getJwtRealm()
            verifier(JwtConfig.verifier)

            validate { credential ->
                // Extract email from JWT token
                val email = credential.payload.getClaim("email").asString()
                if (email.isNotEmpty()) {
                    // Create a JWTPrincipal with the token's payload
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.response.status(HttpStatusCode.Unauthorized)
                call.response.header(HttpHeaders.WWWAuthenticate, "Bearer")
            }
        }
    }
}