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
import util.ProfilePhotoBytes
import util.ProfilePhotoStorage
import util.GcsProfilePhotoStorage

@Serializable
data class AccountPhotoResponse(
    val photoUrl: String? = null
)

private const val MAX_PHOTO_BYTES = 5 * 1024 * 1024

internal fun profilePhotoErrorStatus(message: String): HttpStatusCode = when {
    message.contains("not found", ignoreCase = true) -> HttpStatusCode.NotFound
    message.contains(ProfilePhotoBytes.HEIC_REJECTED, ignoreCase = false) ||
        message.contains("Unsupported content type", ignoreCase = true) ||
        message.contains("Unsupported photo", ignoreCase = true) ->
        HttpStatusCode.BadRequest
    message.contains("unavailable", ignoreCase = true) ||
        message.contains("not configured", ignoreCase = true) ||
        message.contains("Failed to sign", ignoreCase = true) ->
        HttpStatusCode.ServiceUnavailable
    else -> HttpStatusCode.InternalServerError
}

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
                        actions.upload(email, upload.bytes, upload.contentType, upload.filename)
                    }
                ) {
                    is Result.Success -> call.respondSuccess(
                        "Profile photo updated",
                        AccountPhotoResponse(photoUrl = result.data.photoUrl)
                    )
                    is Result.Error -> call.respondError(
                        result.message,
                        Unit,
                        profilePhotoErrorStatus(result.message)
                    )
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
                    is Result.Error -> call.respondError(
                        result.message,
                        Unit,
                        profilePhotoErrorStatus(result.message)
                    )
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
                    is Result.Error -> call.respondError(
                        result.message,
                        Unit,
                        profilePhotoErrorStatus(result.message)
                    )
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
    val contentType: String,
    val filename: String?
)

private suspend fun receivePhotoBytes(call: RoutingCall): PhotoUpload {
    var bytes: ByteArray? = null
    var contentType = "application/octet-stream"
    var filename: String? = null

    call.receiveMultipart().forEachPart { part ->
        try {
            when (part) {
                is PartData.FileItem -> {
                    val name = part.name?.lowercase()
                    if (name == null || name == "file" || name == "photo" || name == "image") {
                        contentType = part.contentType?.toString()?.substringBefore(";")?.trim()
                            ?: "application/octet-stream"
                        filename = part.originalFileName
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
    if (ProfilePhotoBytes.isHeic(data, contentType, filename)) {
        throw IllegalArgumentException(ProfilePhotoBytes.HEIC_REJECTED)
    }
    if (ProfilePhotoBytes.isSvg(data, contentType, filename) || !ProfilePhotoBytes.isAllowed(contentType)) {
        throw IllegalArgumentException("Unsupported content type: $contentType")
    }
    return PhotoUpload(bytes = data, contentType = contentType, filename = filename)
}

/**
 * Resolve a signed photo URL for GET /account.
 * Null only when the user has no photo. Signing or storage failure is an error
 * (the route maps that to 503), never a silent null and never an object name.
 */
suspend fun resolveAccountPhotoUrl(
    photoObject: String?,
    photoStorage: ProfilePhotoStorage
): Result<String?> {
    val key = photoObject?.trim().orEmpty()
    if (key.isEmpty()) return Result.success(null)
    if (!photoStorage.isAvailable()) {
        return Result.error(GcsProfilePhotoStorage.UNAVAILABLE)
    }
    return try {
        Result.success(withContext(Dispatchers.IO) { photoStorage.signedUrl(key) })
    } catch (e: Exception) {
        Result.error("Failed to sign profile photo URL: ${e.message}", e)
    }
}
