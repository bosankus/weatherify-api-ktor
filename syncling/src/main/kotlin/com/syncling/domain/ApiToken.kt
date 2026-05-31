package com.syncling.domain

import kotlinx.datetime.Instant

data class ApiToken(
    val id: String,
    val userId: String,
    val name: String,
    val tokenHash: String,
    val createdAt: Instant,
    val lastUsedAt: Instant? = null,
    val type: String = "CLI",
)
