package bose.ankush.route

import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import domain.service.PushTokenStore
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import util.Constants

@Serializable
data class PushTokenRequest(val token: String = "")

/** FCM tokens are 100–200 chars; anything far longer is not one. */
private const val MAX_TOKEN_LENGTH = 4096

/**
 * Authenticated device push-token routes. Every signed-in device registers its own token, so
 * account changes can reach all of them.
 * - POST   /account/push-token — register this device's token (after login and on rotation)
 * - DELETE /account/push-token — forget it (before logout)
 */
fun Route.accountPushTokenRoute() {
    val tokens: PushTokenStore by application.inject()

    authenticate("jwt-auth") {
        route("/account/push-token") {
            post {
                val (email, token) = call.emailAndToken() ?: return@post
                tokens.add(email, token)
                call.respondSuccess("Push token registered", Unit)
            }

            delete {
                val (email, token) = call.emailAndToken() ?: return@delete
                tokens.remove(email, token)
                call.respondSuccess("Push token removed", Unit)
            }
        }
    }
}

private suspend fun RoutingCall.emailAndToken(): Pair<String, String>? {
    val email = principal<JWTPrincipal>()
        ?.payload
        ?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)
        ?.asString()
    if (email.isNullOrBlank()) {
        respondError("Authentication failed: missing account", Unit, HttpStatusCode.Unauthorized)
        return null
    }
    val token = runCatching { receive<PushTokenRequest>().token.trim() }.getOrDefault("")
    if (token.isEmpty() || token.length > MAX_TOKEN_LENGTH) {
        respondError("Validation process failed: token is required", Unit, HttpStatusCode.BadRequest)
        return null
    }
    return email to token
}
