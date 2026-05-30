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
}
