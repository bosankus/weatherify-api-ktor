package com.syncling.repository

import com.syncling.domain.SupportTicket

interface SupportTicketRepository {
    suspend fun create(
        userId: String,
        userEmail: String?,
        category: String,
        subject: String,
        message: String,
    ): SupportTicket

    suspend fun listForUser(userId: String, limit: Int = 20): List<SupportTicket>

    /** Admin: list all tickets across all users, optionally filtered by status. */
    suspend fun listAll(status: String? = null, limit: Int = 200): List<SupportTicket>

    /** Admin: update a ticket's status. Returns false if ticket not found. */
    suspend fun updateStatus(id: String, status: String): Boolean

    /** Admin: save an internal note on a ticket. Returns false if ticket not found. */
    suspend fun updateNote(id: String, note: String): Boolean

    /** Admin: send a visible reply to the user. Returns false if ticket not found. */
    suspend fun updateReply(id: String, reply: String): Boolean
}
