package com.syncling.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.syncling.domain.ApiToken
import com.syncling.repository.ApiTokenRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bson.Document
import java.util.UUID

class MongoApiTokenRepository(db: MongoDatabase) : ApiTokenRepository {

    private val col = db.getCollection<Document>("api_tokens")

    override suspend fun create(userId: String, name: String, tokenHash: String): ApiToken {
        val now = Clock.System.now()
        val id = UUID.randomUUID().toString()
        col.insertOne(
            Document("_id", id)
                .append("userId", userId)
                .append("name", name)
                .append("tokenHash", tokenHash)
                .append("createdAt", now.toEpochMilliseconds())
        )
        return ApiToken(id = id, userId = userId, name = name, tokenHash = tokenHash, createdAt = now)
    }

    override suspend fun findByHash(tokenHash: String): ApiToken? =
        col.find(eq("tokenHash", tokenHash)).firstOrNull()?.toApiToken()

    override suspend fun listForUser(userId: String): List<ApiToken> =
        col.find(eq("userId", userId)).toList().map { it.toApiToken() }

    override suspend fun delete(id: String, userId: String): Boolean =
        col.deleteOne(and(eq("_id", id), eq("userId", userId))).deletedCount > 0

    override suspend fun touch(id: String) {
        col.updateOne(
            eq("_id", id),
            Updates.set("lastUsedAt", Clock.System.now().toEpochMilliseconds())
        )
    }

    private fun Document.toApiToken() = ApiToken(
        id = getString("_id"),
        userId = getString("userId"),
        name = getString("name"),
        tokenHash = getString("tokenHash"),
        createdAt = Instant.fromEpochMilliseconds(getLong("createdAt")),
        lastUsedAt = getLong("lastUsedAt")?.let { Instant.fromEpochMilliseconds(it) }
    )
}
