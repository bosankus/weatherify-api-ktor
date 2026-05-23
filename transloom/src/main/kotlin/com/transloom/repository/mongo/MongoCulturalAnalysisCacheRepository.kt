package com.transloom.repository.mongo

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.domain.CulturalAnalysis
import com.transloom.repository.CulturalAnalysisCacheRepository
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document

class MongoCulturalAnalysisCacheRepository(db: MongoDatabase) : CulturalAnalysisCacheRepository {

    private val col = db.getCollection<Document>("cultural_analysis_cache")

    override suspend fun get(hashKey: String): CulturalAnalysis? {
        val doc = col.find(eq("_id", hashKey)).firstOrNull() ?: return null
        val needsReview = doc.getBoolean("needsReview") ?: return null
        @Suppress("UNCHECKED_CAST")
        val issues = (doc["issues"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return CulturalAnalysis(issues, needsReview)
    }

    override suspend fun put(hashKey: String, analysis: CulturalAnalysis) {
        col.findOneAndUpdate(
            eq("_id", hashKey),
            Updates.combine(
                Updates.setOnInsert("_id", hashKey),
                Updates.set("needsReview", analysis.needsReview),
                Updates.set("issues", analysis.issues),
                Updates.set("createdAt", java.util.Date())
            ),
            FindOneAndUpdateOptions().upsert(true)
        )
    }
}
