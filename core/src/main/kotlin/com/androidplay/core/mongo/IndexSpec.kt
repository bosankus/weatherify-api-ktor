package com.androidplay.core.mongo

import com.mongodb.client.model.IndexOptions
import com.mongodb.kotlin.client.coroutine.MongoDatabase
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
            try {
                db.getCollection<Document>(spec.collection)
                    .createIndex(spec.keys, spec.options)
            } catch (e: Exception) {
                log.error("Failed to ensure index on ${spec.collection}: ${spec.keys.toJson()}", e)
            }
        }
        log.info("MongoDB indexes ensured for database '{}'", db.name)
    }
}
