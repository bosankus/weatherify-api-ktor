package com.syncling.routes

import com.syncling.repository.SupportTicketRepository
import com.syncling.repository.UserRepository
import com.syncling.services.NotificationService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.application.application
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SupportRoutes")

private val ALLOWED_CATEGORIES = setOf("bug", "question", "feature", "billing")

@Serializable
internal data class SubmitTicketRequest(
    val category: String,
    val subject: String,
    val message: String,
)

@Serializable
internal data class TicketResponse(
    val id: String,
    val category: String,
    val subject: String,
    val message: String,
    val status: String,
    val createdAt: Long,
    val adminReply: String? = null,
)

fun Route.configureSupportRoutes(
    supportTicketRepository: SupportTicketRepository,
    userRepository: UserRepository,
    notificationService: NotificationService?,
    adminEmail: String,
) {
    route("/api/support") {
        post {
            val userId = call.userId() ?: run {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@post
            }
            val body = call.receiveNullable<SubmitTicketRequest>() ?: run {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
                return@post
            }

            val category = body.category.trim().lowercase()
            val subject = body.subject.trim()
            val message = body.message.trim()

            if (category !in ALLOWED_CATEGORIES) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid category"))
                return@post
            }
            if (subject.isBlank() || subject.length > 200) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Subject must be 1–200 characters"))
                return@post
            }
            if (message.isBlank() || message.length > 5000) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message must be 1–5000 characters"))
                return@post
            }

            val user = runCatching { userRepository.findById(userId) }.getOrElse {
                log.error("Failed to look up user {} for support ticket: {}", userId, it.message, it)
                null
            }
            val ticket = runCatching {
                supportTicketRepository.create(
                    userId = userId,
                    userEmail = user?.email,
                    category = category,
                    subject = subject,
                    message = message,
                )
            }.getOrElse {
                log.error("Failed to create support ticket for user {}: {}", userId, it.message, it)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create support ticket. Please try again."))
                return@post
            }
            log.info("Support ticket created id={} userId={} category={}", ticket.id.take(8), userId, category)

            if (notificationService != null) {
                call.application.launch {
                    runCatching {
                        if (adminEmail.isNotBlank()) {
                            notificationService.sendSupportTicketAlert(
                                adminEmail = adminEmail,
                                ticketId = ticket.id,
                                userEmail = user?.email,
                                category = category,
                                subject = subject,
                                message = message,
                            )
                        }
                        if (user?.email != null) {
                            notificationService.sendSupportTicketAck(
                                to = user.email,
                                ticketId = ticket.id,
                                subject = subject,
                                category = category,
                            )
                        }
                    }.onFailure { log.warn("Support ticket email failed ticketId={}: {}", ticket.id.take(8), it.message) }
                }
            }

            call.respond(HttpStatusCode.Created, TicketResponse(
                id = ticket.id,
                category = ticket.category,
                subject = ticket.subject,
                message = ticket.message,
                status = ticket.status,
                createdAt = ticket.createdAt,
                adminReply = null,
            ))
        }

        get {
            val userId = call.userId() ?: run {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@get
            }
            val tickets = supportTicketRepository.listForUser(userId)
            call.respond(mapOf("tickets" to tickets.map {
                TicketResponse(it.id, it.category, it.subject, it.message, it.status, it.createdAt, adminReply = it.adminReply)
            }))
        }
    }
}
