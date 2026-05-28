package com.androidplay.core.mongo

import com.mongodb.client.model.IndexOptions
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.slf4j.LoggerFactory

data class IndexSpec(
    val collection: String,
    val keys: Document,
    val options: IndexOptions = IndexOptions(),
)

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

        // Find any existing index with the same key pattern using JSON comparison,
        // which avoids BSON type mismatches between constructed Documents and decoded ones.
        val conflict = coll.listIndexes().toList()
            .filterNot { (it["name"] as? String) == "_id_" }
            .firstOrNull { (it["key"] as? Document)?.toJson() == keysJson }

        if (conflict != null) {
            val existingName = conflict.getString("name")
            val desiredName = spec.options.name ?: keysJson
            if (existingName == desiredName) {
                // Same name — MongoDB will no-op or update TTL in-place; just proceed.
                return
            }
            log.warn(
                "Dropping conflicting index '{}' on {}.{} to replace with '{}'",
                existingName, db.name, spec.collection, desiredName
            )
            coll.dropIndex(existingName)
        }

        try {
            coll.createIndex(spec.keys, spec.options)
        } catch (e: Exception) {
            log.error("Failed to create index on ${spec.collection}: $keysJson", e)
        }
    }
}
