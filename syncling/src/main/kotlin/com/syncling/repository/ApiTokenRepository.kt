package com.syncling.repository

import com.syncling.domain.ApiToken

interface ApiTokenRepository {
    suspend fun create(userId: String, name: String, tokenHash: String): ApiToken
    suspend fun findByHash(tokenHash: String): ApiToken?
    suspend fun listForUser(userId: String): List<ApiToken>
    suspend fun delete(id: String, userId: String): Boolean
    suspend fun touch(id: String)
}
