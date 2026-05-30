package com.syncling.domain

data class SupportTicket(
    val id: String,
    val userId: String,
    val userEmail: String?,
    val category: String,
    val subject: String,
    val message: String,
    val status: String = "open",
    val createdAt: Long,
)
