package com.androidplay.core.mongo

import com.mongodb.client.model.IndexOptions
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

data class IndexSpec(
    val collection: String,
    val keys: Document,
    val options: IndexOptions = IndexOptions(),
)

/**
 * What to do when an index with the same keys already exists.
 * Same keys are always kept. A name or unique/sparse difference is not a reason to drop.
 */
data class IndexPlan(
    val create: Boolean,
    val warning: String? = null,
)

/**
 * Pure decision used by [MongoIndexer]. Same key pattern → keep the existing index.
 * [existingIndexes] entries are Mongo listIndexes documents (`name`, `key`, `unique`, `sparse`).
 */
fun planIndex(
    existingIndexes: List<Document>,
    keysJson: String,
    desiredUnique: Boolean,
    desiredSparse: Boolean,
): IndexPlan {
    val match = existingIndexes.firstOrNull { existing ->
        val name = existing.getString("name")
        if (name == "_id_") return@firstOrNull false
        (existing["key"] as? Document)?.toJson() == keysJson
    } ?: return IndexPlan(create = true)

    val existingUnique = match["unique"] as? Boolean ?: false
    val existingSparse = match["sparse"] as? Boolean ?: false
    val warning = if (existingUnique != desiredUnique || existingSparse != desiredSparse) {
        "Keeping index '${match.getString("name")}' for keys $keysJson; " +
            "unique/sparse differs (existing unique=$existingUnique sparse=$existingSparse, " +
            "desired unique=$desiredUnique sparse=$desiredSparse). Not dropping."
    } else {
        null
    }
    return IndexPlan(create = false, warning = warning)
}

/** Mongo's default index name: `email_1`, `status_1_createdAt_-1`, `content_text`. */
fun mongoDefaultIndexName(keys: Document): String =
    keys.entries.joinToString("_") { (field, direction) -> "${field}_$direction" }

/**
 * Gives a spec an explicit name for indexes that do not exist yet.
 * Does not mutate a shared [IndexOptions] instance (several specs often share one).
 * Specs that already name themselves are left alone, so a TTL or partial name is preserved.
 */
fun IndexSpec.withExplicitName(): IndexSpec {
    if (!options.name.isNullOrBlank()) return this
    val named = IndexOptions()
        .unique(options.isUnique)
        .sparse(options.isSparse)
        .background(options.isBackground)
        .hidden(options.isHidden)
        .name(mongoDefaultIndexName(keys))
    options.getExpireAfter(TimeUnit.SECONDS)?.let { seconds ->
        named.expireAfter(seconds, TimeUnit.SECONDS)
    }
    options.partialFilterExpression?.let { named.partialFilterExpression(it) }
    options.weights?.let { named.weights(it) }
    options.collation?.let { named.collation(it) }
    return copy(options = named)
}

object MongoIndexer {
    private val log = LoggerFactory.getLogger(MongoIndexer::class.java)

    suspend fun ensure(db: MongoDatabase, specs: List<IndexSpec>) {
        specs.forEach { spec ->
            ensureIndex(db, spec)
        }
        log.info("MongoDB indexes ensured for database '{}'", db.name)
    }

    private suspend fun ensureIndex(db: MongoDatabase, spec: IndexSpec) {
        val coll = db.getCollection<Document>(spec.collection)
        val keysJson = spec.keys.toJson()
        val existing = coll.listIndexes().toList()
        val plan = planIndex(
            existingIndexes = existing,
            keysJson = keysJson,
            desiredUnique = spec.options.isUnique,
            desiredSparse = spec.options.isSparse,
        )
        if (!plan.create) {
            plan.warning?.let { message ->
                log.warn(
                    "{} (database '{}', collection '{}')",
                    message, db.name, spec.collection
                )
            }
            return
        }

        try {
            coll.createIndex(spec.keys, spec.options)
        } catch (e: Exception) {
            log.error("Failed to create index on ${spec.collection}: $keysJson", e)
        }
    }
}
