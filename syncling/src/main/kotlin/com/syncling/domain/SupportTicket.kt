package com.syncling.domain

import kotlinx.serialization.Serializable

@Serializable
data class SupportTicket(
    val id: String,
    val userId: String,
    val userEmail: String? = null,
    val category: String,
    val subject: String,
    val message: String,
    val status: String = "open",
    val createdAt: Long,
    val updatedAt: Long? = null,
    val adminNote: String? = null,
    val adminReply: String? = null,
    val resolvedBy: String? = null,
    val resolvedAt: Long? = null,
)
