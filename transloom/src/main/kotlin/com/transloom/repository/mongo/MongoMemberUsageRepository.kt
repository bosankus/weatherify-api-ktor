package com.transloom.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.domain.MemberUsage
import com.transloom.repository.MemberUsageRepository
import kotlinx.coroutines.flow.toList
import org.bson.Document

class MongoMemberUsageRepository(db: MongoDatabase) : MemberUsageRepository {

    private val col = db.getCollection<Document>("member_usage_logs")

    override suspend fun record(
        projectId: String,
        memberUserId: String,
        ownerId: String,
        yearMonth: String,
        stringsTranslated: Int,
        perLocale: Map<String, Int>
    ) {
        val updates = mutableListOf(
            Updates.setOnInsert("projectId", projectId),
            Updates.setOnInsert("memberUserId", memberUserId),
            Updates.setOnInsert("ownerId", ownerId),
            Updates.setOnInsert("yearMonth", yearMonth),
            Updates.inc("stringsTranslated", stringsTranslated),
            Updates.inc("runsTriggered", 1)
        )
        // $inc on nested fields lets multiple concurrent runs accumulate without
        // losing locales the existing doc already tracks.
        for ((locale, n) in perLocale) {
            if (n > 0) updates += Updates.inc("perLocale.$locale", n)
        }
        col.findOneAndUpdate(
            and(
                eq("projectId", projectId),
                eq("memberUserId", memberUserId),
                eq("yearMonth", yearMonth)
            ),
            Updates.combine(updates),
            FindOneAndUpdateOptions().upsert(true)
        )
    }

    override suspend fun listForOwner(ownerId: String, yearMonth: String): List<MemberUsage> =
        col.find(and(eq("ownerId", ownerId), eq("yearMonth", yearMonth))).toList().map { it.toMemberUsage() }

    override suspend fun listForProject(projectId: String, yearMonth: String): List<MemberUsage> =
        col.find(and(eq("projectId", projectId), eq("yearMonth", yearMonth))).toList().map { it.toMemberUsage() }

    private fun Document.toMemberUsage(): MemberUsage {
        val perLocaleDoc = get("perLocale") as? Document
        val perLocale: Map<String, Int> = perLocaleDoc?.entries
            ?.associate { it.key to ((it.value as? Number)?.toInt() ?: 0) } ?: emptyMap()
        return MemberUsage(
            projectId = getString("projectId") ?: "",
            memberUserId = getString("memberUserId") ?: MemberUsage.EXTERNAL,
            ownerId = getString("ownerId") ?: "",
            yearMonth = getString("yearMonth") ?: "",
            stringsTranslated = (get("stringsTranslated") as? Number)?.toInt() ?: 0,
            runsTriggered = (get("runsTriggered") as? Number)?.toInt() ?: 0,
            perLocale = perLocale
        )
    }
}
