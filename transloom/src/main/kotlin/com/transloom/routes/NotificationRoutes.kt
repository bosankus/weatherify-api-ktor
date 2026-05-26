package com.transloom.routes

import com.transloom.domain.Notification
import com.transloom.model.ApiError
import com.transloom.repository.NotificationRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
private data class NotificationListResponse(
    val notifications: List<Notification>,
    val unreadCount: Int
)

@Serializable
private data class StatusResponse(val status: String)

@Serializable
private data class MarkedResponse(val marked: Int)

@Serializable
private data class UnreadCountResponse(val unreadCount: Int)

fun Route.configureNotificationRoutes(notificationRepository: NotificationRepository) {

    route("/transloom/api/notifications") {

        // List — most recent 20, unread first within each time bucket
        get {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
            val notifications = notificationRepository.listForUser(userId, limit)
            val unread = notifications.count { !it.isRead }
            call.respond(HttpStatusCode.OK, NotificationListResponse(notifications, unread))
        }

        // Mark a single notification as read
        post("/{id}/read") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val notifId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid notification id"))
            notificationRepository.markRead(notifId, userId)
            call.respond(HttpStatusCode.OK, StatusResponse("read"))
        }

        // Mark all notifications as read
        post("/read-all") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val count = notificationRepository.markAllRead(userId)
            call.respond(HttpStatusCode.OK, MarkedResponse(count))
        }

        // Unread count only — cheap poll for badge sync on non-dashboard pages
        get("/unread-count") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val count = notificationRepository.unreadCount(userId)
            call.respond(HttpStatusCode.OK, UnreadCountResponse(count))
        }
    }
}
