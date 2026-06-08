package com.syncling.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gte
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.syncling.domain.ReviewerFeedback
import com.syncling.repository.ReviewerFeedbackRepository
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.UUID

class MongoReviewerFeedbackRepository(db: MongoDatabase) : ReviewerFeedbackRepository {

    private val col = db.getCollection<Document>("reviewer_feedback")

    override suspend fun record(
        projectId: String,
        targetLanguage: String,
        sourceText: String,
        modelOutput: String,
        reviewerEdit: String,
        reason: String?
    ) {
        if (reviewerEdit.trim() == modelOutput.trim()) return
        val doc = Document().apply {
            put("_id", UUID.randomUUID().toString())
            put("projectId", projectId)
            put("targetLanguage", targetLanguage)
            put("sourceText", sourceText)
            put("modelOutput", modelOutput)
            put("reviewerEdit", reviewerEdit)
            if (reason != null) put("reason", reason)
            put("createdAt", System.currentTimeMillis())
        }
        col.insertOne(doc)
    }

    override suspend fun recentExamples(
        projectId: String,
        targetLanguage: String,
        limit: Int
    ): List<ReviewerFeedback> =
        col.find(and(eq("projectId", projectId), eq("targetLanguage", targetLanguage)))
            .sort(Sorts.descending("createdAt"))
            .limit(limit)
            .toList()
            .map { it.toReviewerFeedback() }

    override suspend fun countForProjectsSince(projectIds: Collection<String>, sinceMillis: Long): Int {
        if (projectIds.isEmpty()) return 0
        return col.countDocuments(
            and(`in`("projectId", projectIds), gte("createdAt", sinceMillis))
        ).toInt()
    }

    private fun Document.toReviewerFeedback() = ReviewerFeedback(
        id = getString("_id"),
        projectId = getString("projectId"),
        targetLanguage = getString("targetLanguage"),
        sourceText = getString("sourceText"),
        modelOutput = getString("modelOutput"),
        reviewerEdit = getString("reviewerEdit"),
        reason = getString("reason"),
        createdAt = getLong("createdAt")
    )
}
