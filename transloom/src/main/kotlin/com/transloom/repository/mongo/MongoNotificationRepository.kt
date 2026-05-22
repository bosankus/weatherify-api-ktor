package com.transloom.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.domain.Notification
import com.transloom.repository.NotificationRepository
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.UUID

class MongoNotificationRepository(db: MongoDatabase) : NotificationRepository {

    private val col = db.getCollection<Document>("notifications")

    override suspend fun create(
        userId: String,
        type: String,
        title: String,
        message: String,
        level: String,
        actionUrl: String?,
        actionLabel: String?
    ): Notification {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val doc = Document().apply {
            put("_id", id)
            put("userId", userId)
            put("type", type)
            put("title", title)
            put("message", message)
            put("level", level)
            if (actionUrl != null) put("actionUrl", actionUrl)
            if (actionLabel != null) put("actionLabel", actionLabel)
            put("createdAt", now)
        }
        col.insertOne(doc)
        return Notification(id, userId, type, title, message, level, actionUrl, actionLabel, now)
    }

    override suspend fun listForUser(userId: String, limit: Int): List<Notification> =
        col.find(eq("userId", userId))
            .sort(Sorts.descending("createdAt"))
            .limit(limit)
            .toList()
            .map { it.toNotification() }

    override suspend fun unreadCount(userId: String): Int =
        col.countDocuments(and(eq("userId", userId), eq("readAt", null))).toInt()

    override suspend fun markRead(notificationId: String, userId: String): Boolean {
        val result = col.updateOne(
            and(eq("_id", notificationId), eq("userId", userId), eq("readAt", null)),
            Updates.set("readAt", System.currentTimeMillis())
        )
        return result.modifiedCount > 0
    }

    override suspend fun markAllRead(userId: String): Int {
        val result = col.updateMany(
            and(eq("userId", userId), eq("readAt", null)),
            Updates.set("readAt", System.currentTimeMillis())
        )
        return result.modifiedCount.toInt()
    }

    override suspend fun existsRecent(userId: String, type: String, withinMs: Long): Boolean {
        val since = System.currentTimeMillis() - withinMs
        return col.countDocuments(
            and(eq("userId", userId), eq("type", type), gt("createdAt", since))
        ) > 0
    }

    private fun Document.toNotification() = Notification(
        id = getString("_id"),
        userId = getString("userId") ?: "",
        type = getString("type") ?: "",
        title = getString("title") ?: "",
        message = getString("message") ?: "",
        level = getString("level") ?: "info",
        actionUrl = getString("actionUrl"),
        actionLabel = getString("actionLabel"),
        createdAt = (get("createdAt") as? Number)?.toLong() ?: 0L,
        readAt = (get("readAt") as? Number)?.toLong()
    )
}
