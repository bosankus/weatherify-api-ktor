package com.syncling.domain

import kotlinx.serialization.Serializable

@Serializable
data class SupportMessage(
    val id: String,
    val ticketId: String,
    val senderType: String, // "user" | "admin"
    val senderId: String,
    val content: String,
    val sentAt: Long,
)
