package bose.ankush.route

import bose.ankush.data.model.Note
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import domain.model.Result
import domain.service.NoteService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import util.AuthHelper.getAuthenticatedAdminOrRespond
import util.Constants

private val noteLogger = LoggerFactory.getLogger("NoteRoute")

@Serializable
data class CreateNoteRequest(
    val content: String,
    val contentFormat: String = "richtext-json"
)

@Serializable
data class UpdateNoteRequest(
    val content: String,
    val contentFormat: String = "richtext-json"
)

@Serializable
data class NoteDTO(
    val id: String,
    val content: String,
    val contentFormat: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class NotesResponseDTO(
    val notes: List<NoteDTO>,
    val totalCount: Long,
    val page: Int,
    val totalPages: Int
)

private fun Note.toDTO() = NoteDTO(
    id = id.toHexString(),
    content = content,
    contentFormat = contentFormat,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Admin notes management routes
 * - Create note
 * - List notes with pagination and search
 * - Update note
 */
fun Route.noteRoute() {
    val noteService: NoteService by application.inject()

    // Wrap all admin note routes under /api prefix
    route("/api${Constants.Api.NOTES_ENDPOINT}") {
        // Create a new note
        post {
            val admin = call.getAuthenticatedAdminOrRespond() ?: return@post

            val request = try {
                call.receive<CreateNoteRequest>()
            } catch (e: Exception) {
                call.respondError(
                    "Invalid request body: ${e.message}",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            if (request.content.isBlank()) {
                call.respondError(
                    Constants.Messages.NOTE_EMPTY_ERROR,
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@post
            }

            when (val result = noteService.createNote(admin.email, request.content, request.contentFormat)) {
                is Result.Success -> {
                    call.respondSuccess(
                        Constants.Messages.NOTE_CREATED,
                        result.data.toDTO(),
                        HttpStatusCode.Created
                    )
                }

                is Result.Error -> {
                    call.respondError(result.message, Unit, HttpStatusCode.InternalServerError)
                }
            }
        }

        // List notes with pagination and search
        get {
            val admin = call.getAuthenticatedAdminOrRespond() ?: return@get

            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val search = call.request.queryParameters["search"]?.trim()

            val pageVal = if (page < 1) 1 else page
            val limitVal = when {
                limit < 1 -> 20
                limit > 100 -> 100
                else -> limit
            }

            when (val countResult = noteService.getNoteCount(admin.email, search)) {
                is Result.Success -> {
                    val totalCount = countResult.data
                    val totalPages = if (totalCount == 0L) 1 else ((totalCount + limitVal - 1) / limitVal).toInt()

                    when (val listResult = noteService.getNotes(admin.email, pageVal, limitVal, search)) {
                        is Result.Success -> {
                            val payload = NotesResponseDTO(
                                notes = listResult.data.map { it.toDTO() },
                                totalCount = totalCount,
                                page = pageVal,
                                totalPages = totalPages
                            )
                            call.respondSuccess(Constants.Messages.NOTES_RETRIEVED, payload)
                        }

                        is Result.Error -> {
                            noteLogger.error("getNotes failed for ${admin.email}: ${listResult.message}", listResult.exception)
                            call.respondError(
                                listResult.message,
                                Unit,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                }

                is Result.Error -> {
                    noteLogger.error("getNoteCount failed for ${admin.email}: ${countResult.message}", countResult.exception)
                    call.respondError(
                        countResult.message,
                        Unit,
                        HttpStatusCode.InternalServerError
                    )
                }
            }
        }

        // Update a note
        patch("/{id}") {
            val admin = call.getAuthenticatedAdminOrRespond() ?: return@patch

            val id = call.parameters["id"]?.trim()
            if (id.isNullOrEmpty()) {
                call.respondError(
                    "Note ID is required",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@patch
            }

            val request = try {
                call.receive<UpdateNoteRequest>()
            } catch (e: Exception) {
                call.respondError(
                    "Invalid request body: ${e.message}",
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@patch
            }

            if (request.content.isBlank()) {
                call.respondError(
                    Constants.Messages.NOTE_EMPTY_ERROR,
                    Unit,
                    HttpStatusCode.BadRequest
                )
                return@patch
            }

            when (val result = noteService.updateNote(id, admin.email, request.content, request.contentFormat)) {
                is Result.Success -> {
                    if (result.data != null) {
                        call.respondSuccess(Constants.Messages.NOTE_UPDATED, result.data.toDTO())
                    } else {
                        call.respondError(
                            Constants.Messages.NOTE_NOT_FOUND,
                            Unit,
                            HttpStatusCode.NotFound
                        )
                    }
                }

                is Result.Error -> {
                    if (result.message.contains("not found", ignoreCase = true)) {
                        call.respondError(result.message, Unit, HttpStatusCode.NotFound)
                    } else {
                        call.respondError(result.message, Unit, HttpStatusCode.InternalServerError)
                    }
                }
            }
        }
    }
}
