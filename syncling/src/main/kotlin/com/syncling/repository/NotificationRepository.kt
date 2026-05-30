package com.syncling.repository

import com.syncling.domain.Notification

interface NotificationRepository {
    suspend fun create(
        userId: String,
        type: String,
        title: String,
        message: String,
        level: String = "info",
        actionUrl: String? = null,
        actionLabel: String? = null
    ): Notification

    suspend fun listForUser(userId: String, limit: Int = 20): List<Notification>

    suspend fun unreadCount(userId: String): Int

    suspend fun markRead(notificationId: String, userId: String): Boolean

    suspend fun markAllRead(userId: String): Int

    /** Prevents duplicate notifications of the same type within [withinMs] ms. */
    suspend fun existsRecent(userId: String, type: String, withinMs: Long): Boolean
}
