package com.transloom.routes

import com.transloom.model.ApiError
import com.transloom.repository.NotificationRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.configureNotificationRoutes(notificationRepository: NotificationRepository) {

    route("/transloom/api/notifications") {

        // List — most recent 20, unread first within each time bucket
        get {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
            val notifications = notificationRepository.listForUser(userId, limit)
            val unread = notifications.count { !it.isRead }
            call.respond(HttpStatusCode.OK, mapOf("notifications" to notifications, "unreadCount" to unread))
        }

        // Mark a single notification as read
        post("/{id}/read") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val notifId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid notification id"))
            notificationRepository.markRead(notifId, userId)
            call.respond(HttpStatusCode.OK, mapOf("status" to "read"))
        }

        // Mark all notifications as read
        post("/read-all") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val count = notificationRepository.markAllRead(userId)
            call.respond(HttpStatusCode.OK, mapOf("marked" to count))
        }

        // Unread count only — cheap poll for badge sync on non-dashboard pages
        get("/unread-count") {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val count = notificationRepository.unreadCount(userId)
            call.respond(HttpStatusCode.OK, mapOf("unreadCount" to count))
        }
    }
}
