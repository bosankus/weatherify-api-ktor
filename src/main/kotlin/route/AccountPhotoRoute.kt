package bose.ankush.route

import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import com.androidplay.core.common.Result
import com.androidplay.weatherify.repository.UserRepository
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import util.Constants
import util.ProfilePhotoActions
import util.ProfilePhotoStorage

@Serializable
data class AccountPhotoResponse(
    val photoUrl: String? = null
)

private val ALLOWED_PHOTO_TYPES = setOf(
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
    "image/gif"
)

private const val MAX_PHOTO_BYTES = 5 * 1024 * 1024

/**
 * Authenticated profile photo routes.
 * - POST   /account/photo — multipart upload (field: file|photo|image); reuses object key
 * - GET    /account/photo — signed URL (15 minutes)
 * - DELETE /account/photo — delete GCS object and clear photoObject on the user
 *
 * User document stores photoObject (UUID). Responses expose photoUrl as a signed URL only.
 */
fun Route.accountPhotoRoute() {
    val userRepository: UserRepository by application.inject()
    val photoStorage: ProfilePhotoStorage by application.inject()
    val actions = ProfilePhotoActions(photoStorage, userRepository)

    authenticate("jwt-auth") {
        route("/account/photo") {
            post {
                val email = call.jwtEmailOrUnauthorized() ?: return@post

                val upload = try {
                    receivePhotoBytes(call)
                } catch (e: IllegalArgumentException) {
                    call.respondError(e.message ?: "Invalid photo upload", Unit, HttpStatusCode.BadRequest)
                    return@post
                } catch (e: Exception) {
                    call.respondError(
                        "Failed to read upload: ${e.message}",
                        Unit,
                        HttpStatusCode.BadRequest
                    )
                    return@post
                }

                when (
                    val result = withContext(Dispatchers.IO) {
                        actions.upload(email, upload.bytes, upload.contentType)
                    }
                ) {
                    is Result.Success -> call.respondSuccess(
                        "Profile photo updated",
                        AccountPhotoResponse(photoUrl = result.data.photoUrl)
                    )
                    is Result.Error -> {
                        val status = when {
                            result.message.contains("not found", ignoreCase = true) ->
                                HttpStatusCode.NotFound
                            result.message.contains("unavailable", ignoreCase = true) ||
                                result.message.contains("not configured", ignoreCase = true) ->
                                HttpStatusCode.ServiceUnavailable
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respondError(result.message, Unit, status)
                    }
                }
            }

            get {
                val email = call.jwtEmailOrUnauthorized() ?: return@get
                when (
                    val result = withContext(Dispatchers.IO) { actions.signedUrlFor(email) }
                ) {
                    is Result.Success -> call.respondSuccess(
                        if (result.data == null) "No profile photo" else "Profile photo",
                        AccountPhotoResponse(photoUrl = result.data)
                    )
                    is Result.Error -> {
                        val status = when {
                            result.message.contains("not found", ignoreCase = true) ->
                                HttpStatusCode.NotFound
                            result.message.contains("unavailable", ignoreCase = true) ||
                                result.message.contains("not configured", ignoreCase = true) ->
                                HttpStatusCode.ServiceUnavailable
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respondError(result.message, Unit, status)
                    }
                }
            }

            delete {
                val email = call.jwtEmailOrUnauthorized() ?: return@delete
                when (
                    val result = withContext(Dispatchers.IO) { actions.delete(email) }
                ) {
                    is Result.Success -> call.respondSuccess(
                        "Profile photo deleted",
                        AccountPhotoResponse(photoUrl = null)
                    )
                    is Result.Error -> {
                        val status = when {
                            result.message.contains("not found", ignoreCase = true) ->
                                HttpStatusCode.NotFound
                            result.message.contains("unavailable", ignoreCase = true) ||
                                result.message.contains("not configured", ignoreCase = true) ->
                                HttpStatusCode.ServiceUnavailable
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respondError(result.message, Unit, status)
                    }
                }
            }
        }
    }
}

private suspend fun RoutingCall.jwtEmailOrUnauthorized(): String? {
    val email = principal<JWTPrincipal>()
        ?.payload
        ?.getClaim(Constants.Auth.JWT_CLAIM_EMAIL)
        ?.asString()
    if (email.isNullOrBlank()) {
        respondError(
            "Authentication failed: missing account",
            Unit,
            HttpStatusCode.Unauthorized
        )
        return null
    }
    return email
}

private data class PhotoUpload(
    val bytes: ByteArray,
    val contentType: String
)

private suspend fun receivePhotoBytes(call: RoutingCall): PhotoUpload {
    var bytes: ByteArray? = null
    var contentType = "application/octet-stream"

    call.receiveMultipart().forEachPart { part ->
        try {
            when (part) {
                is PartData.FileItem -> {
                    val name = part.name?.lowercase()
                    if (name == null || name == "file" || name == "photo" || name == "image") {
                        contentType = part.contentType?.toString()?.substringBefore(";")?.trim()
                            ?: "application/octet-stream"
                        val data = part.provider().toByteArray()
                        if (data.size > MAX_PHOTO_BYTES) {
                            throw IllegalArgumentException(
                                "Photo exceeds ${MAX_PHOTO_BYTES / (1024 * 1024)}MB limit"
                            )
                        }
                        bytes = data
                    }
                }
                else -> Unit
            }
        } finally {
            part.dispose()
        }
    }

    val data = bytes
    if (data == null || data.isEmpty()) {
        throw IllegalArgumentException("Multipart file field 'file' or 'photo' is required")
    }
    val normalizedType = contentType.lowercase()
    if (normalizedType !in ALLOWED_PHOTO_TYPES && !normalizedType.startsWith("image/")) {
        throw IllegalArgumentException("Unsupported content type: $contentType")
    }
    return PhotoUpload(bytes = data, contentType = contentType)
}

/**
 * Resolve a signed photo URL for GET /account. Returns null when unset or storage is down
 * (never returns an object name or gs:// path).
 */
suspend fun resolveAccountPhotoUrl(
    photoObject: String?,
    photoStorage: ProfilePhotoStorage
): String? {
    val key = photoObject?.trim().orEmpty()
    if (key.isEmpty()) return null
    if (!photoStorage.isAvailable()) return null
    return try {
        withContext(Dispatchers.IO) { photoStorage.signedUrl(key) }
    } catch (_: Exception) {
        null
    }
}
