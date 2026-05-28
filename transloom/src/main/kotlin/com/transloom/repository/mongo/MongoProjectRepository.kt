package com.transloom.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.exists
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.lt
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.domain.CreateProjectInput
import com.transloom.domain.Project
import com.transloom.domain.TargetConfig
import com.transloom.repository.ProjectRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.bson.Document
import java.util.UUID

class MongoProjectRepository(db: MongoDatabase) : ProjectRepository {

    private val projects = db.getCollection<Document>("projects")
    private val strings = db.getCollection<Document>("strings")
    private val translations = db.getCollection<Document>("translations")
    private val glossary = db.getCollection<Document>("glossary")

    override suspend fun create(ownerId: String, input: CreateProjectInput): Project {
        val id = UUID.randomUUID().toString()
        val now = Clock.System.now().toEpochMilliseconds()

        val targetsDocuments = input.targets.map { it.toDocument() }

        val doc = Document().apply {
            put("_id", id)
            put("ownerId", ownerId)
            put("name", input.name)
            put("githubRepo", input.githubRepo)
            put("watchBranch", input.watchBranch)
            put("sourceFilePaths", input.sourceFilePaths)
            put("category", input.category)
            put("tone", input.tone)
            put("targets", targetsDocuments)
            put("culturalSensitivityEnabled", input.culturalSensitivityEnabled)
            put("autoApproveEnabled", input.autoApproveEnabled)
            put("sharedMemoryOptIn", input.sharedMemoryOptIn)
            put("otaEnabled", input.otaEnabled)
            put("autoPromote", input.autoPromote)
            put("prBranchPattern", input.prBranchPattern)
            put("createdAt", now)
            put("updatedAt", now)
        }

        projects.insertOne(doc)

        return Project(
            id = id,
            ownerId = ownerId,
            name = input.name,
            githubRepo = input.githubRepo,
            watchBranch = input.watchBranch,
            sourceFilePaths = input.sourceFilePaths,
            category = input.category,
            tone = input.tone,
            targets = input.targets,
            culturalSensitivityEnabled = input.culturalSensitivityEnabled,
            autoApproveEnabled = input.autoApproveEnabled,
            sharedMemoryOptIn = input.sharedMemoryOptIn,
            otaEnabled = input.otaEnabled,
            autoPromote = input.autoPromote,
            prBranchPattern = input.prBranchPattern
        )
    }

    override suspend fun listForUser(ownerId: String): List<Project> {
        return projects
            .find(eq("ownerId", ownerId))
            .sort(Sorts.descending("createdAt"))
            .toList()
            .map { it.toProject() }
    }

    override suspend fun findById(projectId: String): Project? {
        return projects.find(eq("_id", projectId)).firstOrNull()?.toProject()
    }

    override suspend fun findByGithubRepo(githubRepo: String): Project? {
        return projects.find(eq("githubRepo", githubRepo)).firstOrNull()?.toProject()
    }

    override suspend fun countForUser(ownerId: String): Int {
        return projects.countDocuments(eq("ownerId", ownerId)).toInt()
    }

    override suspend fun update(
        projectId: String,
        name: String?,
        tone: String?,
        category: String?,
        watchBranch: String?,
        sourceFilePaths: List<String>?,
        targets: List<TargetConfig>?,
        culturalSensitivityEnabled: Boolean?,
        autoApproveEnabled: Boolean?,
        otaEnabled: Boolean?,
        autoPromote: Boolean?,
        sharedMemoryOptIn: Boolean?,
        prBranchPattern: String?,
        outboundWebhookUrl: String?,
        outboundWebhookSecret: String?,
        monthlyStringQuota: Int?,
        rolloutPercent: Int?
    ): Boolean {
        val updates = mutableListOf<org.bson.conversions.Bson>()

        name?.let { updates.add(Updates.set("name", it)) }
        tone?.let { updates.add(Updates.set("tone", it)) }
        category?.let { updates.add(Updates.set("category", it)) }
        watchBranch?.let { updates.add(Updates.set("watchBranch", it)) }
        sourceFilePaths?.let { updates.add(Updates.set("sourceFilePaths", it)) }
        targets?.let { updates.add(Updates.set("targets", it.map { t -> t.toDocument() })) }
        culturalSensitivityEnabled?.let { updates.add(Updates.set("culturalSensitivityEnabled", it)) }
        autoApproveEnabled?.let { updates.add(Updates.set("autoApproveEnabled", it)) }
        otaEnabled?.let { updates.add(Updates.set("otaEnabled", it)) }
        autoPromote?.let { updates.add(Updates.set("autoPromote", it)) }
        sharedMemoryOptIn?.let { updates.add(Updates.set("sharedMemoryOptIn", it)) }
        // "" means "clear the pattern" (caller uses null to mean "no change")
        prBranchPattern?.let {
            if (it.isBlank()) updates.add(Updates.unset("prBranchPattern"))
            else updates.add(Updates.set("prBranchPattern", it))
        }
        // "" clears the webhook URL/secret; non-blank sets it; null = no change
        outboundWebhookUrl?.let {
            if (it.isBlank()) updates.add(Updates.unset("outboundWebhookUrl"))
            else updates.add(Updates.set("outboundWebhookUrl", it))
        }
        outboundWebhookSecret?.let {
            if (it.isBlank()) updates.add(Updates.unset("outboundWebhookSecret"))
            else updates.add(Updates.set("outboundWebhookSecret", it))
        }
        // -1 clears the quota; positive value sets it; null = no change
        monthlyStringQuota?.let {
            if (it < 0) updates.add(Updates.unset("monthlyStringQuota"))
            else updates.add(Updates.set("monthlyStringQuota", it))
        }
        rolloutPercent?.let { updates.add(Updates.set("rolloutPercent", it.coerceIn(0, 100))) }

        if (updates.isEmpty()) return false

        updates.add(Updates.set("updatedAt", Clock.System.now().toEpochMilliseconds()))

        val result = projects.updateOne(eq("_id", projectId), Updates.combine(updates))

        // Fan out project name change to denormalized translations docs
        if (name != null) {
            translations.updateMany(eq("projectId", projectId), Updates.set("projectName", name))
        }
        return result.matchedCount > 0
    }

    override suspend fun delete(projectId: String) {
        // 1. Collect all string IDs for this project
        val stringIds = strings
            .find(eq("projectId", projectId))
            .toList()
            .map { it.getString("_id") }

        // 2. Delete translations for those strings (only if there are any)
        if (stringIds.isNotEmpty()) {
            translations.deleteMany(`in`("stringId", stringIds))
        }

        // 3. Delete all strings for this project
        strings.deleteMany(eq("projectId", projectId))

        // 4. Delete all glossary entries for this project
        glossary.deleteMany(eq("projectId", projectId))

        // 5. Delete the project itself
        projects.deleteOne(eq("_id", projectId))
    }

    override suspend fun listAll(): List<Project> {
        return projects.find().sort(Sorts.descending("createdAt")).toList().map { it.toProject() }
    }

    override suspend fun listProjectsNeedingWebhookHeal(staleBefore: Instant): List<Project> {
        val threshold = staleBefore.toEpochMilliseconds()
        val filter = or(
            exists("webhookVerifiedAt", false),
            eq("webhookVerifiedAt", null),
            lt("webhookVerifiedAt", threshold)
        )
        return projects.find(filter).sort(Sorts.ascending("webhookVerifiedAt")).toList().map { it.toProject() }
    }

    override suspend fun markWebhookVerified(projectId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        projects.updateOne(
            eq("_id", projectId),
            Updates.combine(
                Updates.set("webhookVerifiedAt", now),
                Updates.set("updatedAt", now)
            )
        )
    }

    override suspend fun updateSourceFileHash(projectId: String, hash: String) {
        projects.updateOne(
            eq("_id", projectId),
            Updates.combine(
                Updates.set("lastSourceFileHash", hash),
                Updates.set("updatedAt", Clock.System.now().toEpochMilliseconds())
            )
        )
    }

    override suspend fun getGlossary(projectId: String): Map<String, Map<String, String>> {
        val entries = glossary
            .find(and(eq("projectId", projectId), eq("isActive", true)))
            .toList()

        return entries
            .groupBy { it.getString("languageCode") }
            .mapValues { (_, docs) ->
                docs.associate { doc ->
                    doc.getString("sourceTerm") to doc.getString("targetTerm")
                }
            }
    }

    // --- Mapping helpers ---

    private fun TargetConfig.toDocument(): Document = Document().apply {
        put("code", code)
        put("name", name)
        put("region", region)
        put("file", file)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Document.toProject(): Project {
        val rawTargets = get("targets") as? List<*> ?: emptyList<Any>()
        val targets = rawTargets.mapNotNull { it as? Document }.map { t ->
            TargetConfig(
                code = t.getString("code") ?: "",
                name = t.getString("name") ?: "",
                region = t.getString("region"),
                file = t.getString("file") ?: ""
            )
        }
        val webhookVerifiedAt = (get("webhookVerifiedAt") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
        // Backward compat: old documents store a single string in "sourceFilePath";
        // new documents store a list in "sourceFilePaths".
        val sourceFilePaths = (get("sourceFilePaths") as? List<*>)
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: getString("sourceFilePath")?.let { listOf(it) }
            ?: listOf("values/strings.xml")
        return Project(
            id = getString("_id"),
            ownerId = getString("ownerId") ?: "",
            name = getString("name") ?: "",
            githubRepo = getString("githubRepo") ?: "",
            watchBranch = getString("watchBranch") ?: "",
            sourceFilePaths = sourceFilePaths,
            category = getString("category") ?: "",
            tone = getString("tone") ?: "",
            targets = targets,
            culturalSensitivityEnabled = getBoolean("culturalSensitivityEnabled") ?: false,
            autoApproveEnabled = getBoolean("autoApproveEnabled") ?: false,
            sharedMemoryOptIn = getBoolean("sharedMemoryOptIn") ?: false,
            webhookVerifiedAt = webhookVerifiedAt,
            lastSourceFileHash = getString("lastSourceFileHash"),
            otaEnabled = getBoolean("otaEnabled") ?: false,
            autoPromote = getBoolean("autoPromote") ?: true,
            prBranchPattern = getString("prBranchPattern"),
            outboundWebhookUrl = getString("outboundWebhookUrl"),
            outboundWebhookSecret = getString("outboundWebhookSecret"),
            monthlyStringQuota = (get("monthlyStringQuota") as? Number)?.toInt(),
            rolloutPercent = (get("rolloutPercent") as? Number)?.toInt() ?: 100
        )
    }
}
