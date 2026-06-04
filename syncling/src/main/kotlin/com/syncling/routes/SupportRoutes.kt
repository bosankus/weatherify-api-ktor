package com.syncling.routes

import com.syncling.repository.SupportTicketRepository
import com.syncling.repository.UserRepository
import com.syncling.services.NotificationService
import com.syncling.services.PipelineEventBus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.application
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
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
internal data class SendMessageRequest(
    val content: String,
)

@Serializable
internal data class SupportMessageResponse(
    val id: String,
    val senderType: String,
    val content: String,
    val sentAt: Long,
)

@Serializable
internal data class TicketSummary(
    val id: String,
    val category: String,
    val subject: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val userEmail: String?,
    val lastMessage: String?,
    val lastSenderType: String?,
)

@Serializable
internal data class TicketDetailResponse(
    val id: String,
    val category: String,
    val subject: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<SupportMessageResponse>,
)

@Serializable
internal data class TicketListPayload(
    val tickets: List<TicketSummary>,
    val isAdmin: Boolean,
)

fun Route.configureSupportRoutes(
    supportTicketRepository: SupportTicketRepository,
    userRepository: UserRepository,
    notificationService: NotificationService?,
    adminEmail: String,
    eventBus: PipelineEventBus? = null,
) {
    route("/api/support") {

        // ── Create new ticket ────────────────────────────────────────────────
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

            // Seed the first message in the chat thread
            runCatching { supportTicketRepository.addMessage(ticket.id, "user", userId, message) }
                .onFailure { log.warn("Failed to seed initial message for ticket {}: {}", ticket.id.take(8), it.message) }

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

            call.respond(HttpStatusCode.Created, mapOf(
                "id" to ticket.id,
                "category" to ticket.category,
                "subject" to ticket.subject,
                "status" to ticket.status,
                "createdAt" to ticket.createdAt,
            ))
        }

        // ── List tickets ─────────────────────────────────────────────────────
        get {
            val userId = call.userId() ?: run {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@get
            }
            val user = runCatching { userRepository.findById(userId) }.getOrElse { null }
            val isAdmin = user?.email?.equals(adminEmail, ignoreCase = true) == true

            val tickets = if (isAdmin) {
                supportTicketRepository.listAll()
            } else {
                supportTicketRepository.listForUser(userId)
            }

            val summaries = tickets.map { t ->
                // Provide a preview from the last stored message or fall back to the ticket message field
                TicketSummary(
                    id = t.id,
                    category = t.category,
                    subject = t.subject,
                    status = t.status,
                    createdAt = t.createdAt,
                    updatedAt = t.updatedAt ?: t.createdAt,
                    userEmail = if (isAdmin) t.userEmail else null,
                    lastMessage = t.adminReply ?: t.message,
                    lastSenderType = if (t.adminReply != null) "admin" else "user",
                )
            }
            call.respond(TicketListPayload(summaries, isAdmin))
        }

        // ── Ticket detail + messages ─────────────────────────────────────────
        route("{id}") {

            get {
                val userId = call.userId() ?: run {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                    return@get
                }
                val ticketId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing ticket id"))
                    return@get
                }
                val ticket = supportTicketRepository.findById(ticketId) ?: run {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Ticket not found"))
                    return@get
                }
                val user = runCatching { userRepository.findById(userId) }.getOrElse { null }
                val isAdmin = user?.email?.equals(adminEmail, ignoreCase = true) == true
                if (!isAdmin && ticket.userId != userId) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@get
                }

                val storedMessages = supportTicketRepository.getMessages(ticketId)
                // Backfill: if no stored messages (legacy ticket), synthesize from ticket fields
                val messages = if (storedMessages.isEmpty()) {
                    buildList {
                        add(SupportMessageResponse("legacy-0", "user", ticket.message, ticket.createdAt))
                        if (ticket.adminReply != null) {
                            add(SupportMessageResponse("legacy-1", "admin", ticket.adminReply, ticket.createdAt + 1))
                        }
                    }
                } else {
                    storedMessages.map { SupportMessageResponse(it.id, it.senderType, it.content, it.sentAt) }
                }

                call.respond(TicketDetailResponse(
                    id = ticket.id,
                    category = ticket.category,
                    subject = ticket.subject,
                    status = ticket.status,
                    createdAt = ticket.createdAt,
                    updatedAt = ticket.updatedAt ?: ticket.createdAt,
                    messages = messages,
                ))
            }

            // ── Send message ─────────────────────────────────────────────────
            post("messages") {
                val userId = call.userId() ?: run {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                    return@post
                }
                val ticketId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing ticket id"))
                    return@post
                }
                val ticket = supportTicketRepository.findById(ticketId) ?: run {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Ticket not found"))
                    return@post
                }
                val user = runCatching { userRepository.findById(userId) }.getOrElse { null }
                val isAdmin = user?.email?.equals(adminEmail, ignoreCase = true) == true
                val isOwner = ticket.userId == userId

                if (!isAdmin && !isOwner) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val body = call.receiveNullable<SendMessageRequest>() ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
                    return@post
                }
                val content = body.content.trim()
                if (content.isBlank() || content.length > 5000) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Message must be 1–5000 characters"))
                    return@post
                }

                val senderType = if (isAdmin) "admin" else "user"
                val msg = supportTicketRepository.addMessage(ticketId, senderType, userId, content) ?: run {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to send message"))
                    return@post
                }

                // Auto-acknowledge when admin first replies
                if (isAdmin && ticket.status == "open") {
                    runCatching { supportTicketRepository.updateStatus(ticketId, "acknowledged") }
                }

                // SSE: notify the other party in real-time
                if (eventBus != null) {
                    val newStatus = when {
                        isAdmin && ticket.status == "open" -> "acknowledged"
                        else -> ticket.status
                    }
                    if (isAdmin) {
                        // Push event to the ticket owner
                        eventBus.emitSupportMessage(ticket.userId, ticketId, "admin", newStatus)
                    } else {
                        // Push event to the admin
                        val adminUser = runCatching { userRepository.findByEmail(adminEmail) }.getOrNull()
                        if (adminUser != null) {
                            eventBus.emitSupportMessage(adminUser.id, ticketId, "user", newStatus)
                        }
                    }
                    // Always emit to the sender too (other tabs / optimistic refresh)
                    eventBus.emitSupportMessage(userId, ticketId, senderType, newStatus)
                }

                call.respond(HttpStatusCode.Created, SupportMessageResponse(
                    id = msg.id,
                    senderType = senderType,
                    content = msg.content,
                    sentAt = msg.sentAt,
                ))
            }

            // ── Resolve ticket ────────────────────────────────────────────────
            post("resolve") {
                val userId = call.userId() ?: run {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                    return@post
                }
                val ticketId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing ticket id"))
                    return@post
                }
                val ticket = supportTicketRepository.findById(ticketId) ?: run {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Ticket not found"))
                    return@post
                }
                val user = runCatching { userRepository.findById(userId) }.getOrElse { null }
                val isAdmin = user?.email?.equals(adminEmail, ignoreCase = true) == true
                val isOwner = ticket.userId == userId

                if (!isAdmin && !isOwner) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }
                if (ticket.status == "resolved") {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "resolved"))
                    return@post
                }

                supportTicketRepository.resolve(ticketId, userId)

                // SSE: notify the other party
                if (eventBus != null) {
                    if (isAdmin) {
                        eventBus.emitSupportMessage(ticket.userId, ticketId, "admin", "resolved")
                    } else {
                        val adminUser = runCatching { userRepository.findByEmail(adminEmail) }.getOrNull()
                        if (adminUser != null) {
                            eventBus.emitSupportMessage(adminUser.id, ticketId, "user", "resolved")
                        }
                    }
                    eventBus.emitSupportMessage(userId, ticketId, senderType = if (isAdmin) "admin" else "user", ticketStatus = "resolved")
                }

                call.respond(HttpStatusCode.OK, mapOf("status" to "resolved"))
            }
        }
    }
}
