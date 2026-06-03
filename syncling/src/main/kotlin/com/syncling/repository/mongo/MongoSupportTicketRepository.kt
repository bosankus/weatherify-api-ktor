package com.syncling.repository.mongo

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.syncling.domain.SupportMessage
import com.syncling.domain.SupportTicket
import com.syncling.repository.SupportTicketRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.UUID

class MongoSupportTicketRepository(db: MongoDatabase) : SupportTicketRepository {

    private val col = db.getCollection<Document>("support_tickets")
    private val msgCol = db.getCollection<Document>("support_messages")

    override suspend fun create(
        userId: String,
        userEmail: String?,
        category: String,
        subject: String,
        message: String,
    ): SupportTicket {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val doc = Document().apply {
            put("_id", id)
            put("userId", userId)
            if (userEmail != null) put("userEmail", userEmail)
            put("category", category)
            put("subject", subject)
            put("message", message)
            put("status", "open")
            put("createdAt", now)
            put("updatedAt", now)
        }
        col.insertOne(doc)
        return SupportTicket(id, userId, userEmail, category, subject, message, "open", now, now)
    }

    override suspend fun findById(id: String): SupportTicket? =
        col.find(eq("_id", id)).firstOrNull()?.toTicket()

    override suspend fun listForUser(userId: String, limit: Int): List<SupportTicket> =
        col.find(eq("userId", userId))
            .sort(Sorts.descending("updatedAt", "createdAt"))
            .limit(limit)
            .toList()
            .map { it.toTicket() }

    override suspend fun listAll(status: String?, limit: Int): List<SupportTicket> {
        val filter = if (status != null) eq("status", status) else Document()
        return col.find(filter)
            .sort(Sorts.descending("updatedAt", "createdAt"))
            .limit(limit)
            .toList()
            .map { it.toTicket() }
    }

    override suspend fun updateStatus(id: String, status: String): Boolean {
        val result = col.updateOne(eq("_id", id), Updates.combine(
            Updates.set("status", status),
            Updates.set("updatedAt", System.currentTimeMillis()),
        ))
        return result.matchedCount > 0
    }

    override suspend fun updateNote(id: String, note: String): Boolean {
        val update = if (note.isBlank()) Updates.unset("adminNote") else Updates.set("adminNote", note)
        val result = col.updateOne(eq("_id", id), update)
        return result.matchedCount > 0
    }

    override suspend fun updateReply(id: String, reply: String): Boolean {
        val update = if (reply.isBlank()) Updates.unset("adminReply") else Updates.set("adminReply", reply)
        val result = col.updateOne(eq("_id", id), update)
        return result.matchedCount > 0
    }

    override suspend fun addMessage(
        ticketId: String,
        senderType: String,
        senderId: String,
        content: String,
    ): SupportMessage? {
        val ticket = findById(ticketId) ?: return null
        val now = System.currentTimeMillis()
        val msgId = UUID.randomUUID().toString()
        val doc = Document().apply {
            put("_id", msgId)
            put("ticketId", ticketId)
            put("senderType", senderType)
            put("senderId", senderId)
            put("content", content)
            put("sentAt", now)
        }
        msgCol.insertOne(doc)
        col.updateOne(eq("_id", ticketId), Updates.set("updatedAt", now))
        return SupportMessage(msgId, ticketId, senderType, senderId, content, now)
    }

    override suspend fun getMessages(ticketId: String): List<SupportMessage> =
        msgCol.find(eq("ticketId", ticketId))
            .sort(Sorts.ascending("sentAt"))
            .toList()
            .map { it.toMessage() }

    override suspend fun resolve(id: String, closedBy: String): Boolean {
        val now = System.currentTimeMillis()
        val result = col.updateOne(eq("_id", id), Updates.combine(
            Updates.set("status", "resolved"),
            Updates.set("resolvedBy", closedBy),
            Updates.set("resolvedAt", now),
            Updates.set("updatedAt", now),
        ))
        return result.matchedCount > 0
    }

    private fun Document.toTicket() = SupportTicket(
        id = getString("_id"),
        userId = getString("userId") ?: "",
        userEmail = getString("userEmail"),
        category = getString("category") ?: "",
        subject = getString("subject") ?: "",
        message = getString("message") ?: "",
        status = getString("status") ?: "open",
        createdAt = getLong("createdAt") ?: 0L,
        updatedAt = getLong("updatedAt"),
        adminNote = getString("adminNote"),
        adminReply = getString("adminReply"),
        resolvedBy = getString("resolvedBy"),
        resolvedAt = getLong("resolvedAt"),
    )

    private fun Document.toMessage() = SupportMessage(
        id = getString("_id"),
        ticketId = getString("ticketId") ?: "",
        senderType = getString("senderType") ?: "user",
        senderId = getString("senderId") ?: "",
        content = getString("content") ?: "",
        sentAt = getLong("sentAt") ?: 0L,
    )
}
