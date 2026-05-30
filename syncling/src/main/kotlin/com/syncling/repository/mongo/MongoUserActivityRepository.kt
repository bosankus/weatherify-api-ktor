package com.syncling.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gte
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.lt
import com.mongodb.client.model.Filters.nin
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.syncling.domain.UserActivity
import com.syncling.domain.UserEvent
import com.syncling.repository.UserActivityRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bson.Document
import java.util.UUID

class MongoUserActivityRepository(db: MongoDatabase) : UserActivityRepository {

    private val collection = db.getCollection<Document>("user_events")

    override suspend fun record(
        userId: String,
        event: UserEvent,
        metadata: Map<String, String>
    ): UserActivity {
        val now = Clock.System.now()
        val id = UUID.randomUUID().toString()
        val doc = Document().apply {
            put("_id", id)
            put("userId", userId)
            put("event", event.name)
            put("occurredAt", now.toEpochMilliseconds())
            if (metadata.isNotEmpty()) put("metadata", Document(metadata.toMap()))
        }
        collection.insertOne(doc)
        return UserActivity(id, userId, event, now, metadata)
    }

    override suspend fun listForUser(userId: String, limit: Int): List<UserActivity> =
        collection.find(eq("userId", userId))
            .sort(Sorts.descending("occurredAt"))
            .limit(limit)
            .toList()
            .map { it.toUserActivity() }

    override suspend fun countForUser(userId: String): Int =
        collection.countDocuments(eq("userId", userId)).toInt()

    override suspend fun lastOccurrence(userId: String, events: Set<UserEvent>): UserActivity? =
        collection.find(and(eq("userId", userId), `in`("event", events.map { it.name })))
            .sort(Sorts.descending("occurredAt"))
            .limit(1)
            .firstOrNull()
            ?.toUserActivity()

    override suspend fun findUsersWithAbandonedPayments(olderThanMs: Long): List<String> {
        val cutoff = Clock.System.now().toEpochMilliseconds() - olderThanMs
        // Users whose most recent SUBSCRIPTION_INITIATED is older than cutoff, and who have
        // no PAYMENT_VERIFIED or PLAN_ACTIVATED event afterwards. Done with a two-step scan
        // rather than aggregation pipeline so the logic stays readable and easy to test.
        val initiated = collection
            .find(and(
                eq("event", UserEvent.SUBSCRIPTION_INITIATED.name),
                lt("occurredAt", cutoff)
            ))
            .sort(Sorts.descending("occurredAt"))
            .toList()

        val resolved = collection
            .find(and(
                `in`("event", listOf(
                    UserEvent.PAYMENT_VERIFIED.name,
                    UserEvent.PLAN_ACTIVATED.name,
                    UserEvent.SUBSCRIPTION_CHARGED.name
                )),
                gte("occurredAt", cutoff)
            ))
            .toList()
            .map { it.getString("userId") }
            .toSet()

        val seen = mutableSetOf<String>()
        return initiated.mapNotNull { doc ->
            val userId = doc.getString("userId") ?: return@mapNotNull null
            if (userId in resolved || userId in seen) null
            else { seen.add(userId); userId }
        }
    }

    private fun Document.toUserActivity(): UserActivity {
        val event = runCatching { UserEvent.valueOf(getString("event") ?: "") }
            .getOrElse { UserEvent.LOGGED_IN }
        val metaDoc = get("metadata") as? Document
        val metadata = metaDoc?.entries
            ?.associate { (k, v) -> k to v?.toString().orEmpty() }
            ?: emptyMap()
        return UserActivity(
            id = getString("_id"),
            userId = getString("userId"),
            event = event,
            occurredAt = Instant.fromEpochMilliseconds((get("occurredAt") as? Number)?.toLong() ?: 0L),
            metadata = metadata
        )
    }
}
