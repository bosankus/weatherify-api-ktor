package com.syncling.repository

import com.syncling.domain.UserActivity
import com.syncling.domain.UserEvent
import kotlinx.datetime.Instant

interface UserActivityRepository {

    suspend fun record(
        userId: String,
        event: UserEvent,
        metadata: Map<String, String> = emptyMap()
    ): UserActivity

    suspend fun listForUser(userId: String, limit: Int = 50): List<UserActivity>

    suspend fun countForUser(userId: String): Int

    /**
     * Returns the most recent occurrence of any event in [events] for the user,
     * or null if no such event has been recorded. Useful for "did they ever
     * create a project after signup?" style queries.
     */
    suspend fun lastOccurrence(userId: String, events: Set<UserEvent>): UserActivity?

    /**
     * Returns user IDs that have a [SUBSCRIPTION_INITIATED] event older than
     * [olderThanMs] millis but never followed by a [PAYMENT_VERIFIED] or
     * [PLAN_ACTIVATED] event — i.e. payment was started but abandoned.
     */
    suspend fun findUsersWithAbandonedPayments(olderThanMs: Long): List<String>
}
