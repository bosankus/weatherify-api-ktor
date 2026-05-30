package com.syncling.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.syncling.domain.MembershipStatus
import com.syncling.domain.ProjectMembership
import com.syncling.domain.ProjectRole
import com.syncling.repository.ProjectMembershipRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import org.bson.Document
import java.util.UUID

private val INVITE_TTL = 7.days

class MongoProjectMembershipRepository(
    db: MongoDatabase
) : ProjectMembershipRepository {

    private val collection = db.getCollection<Document>("project_members")

    override suspend fun list(projectId: String): List<ProjectMembership> =
        collection.find(eq("projectId", projectId))
            .sort(Sorts.descending("invitedAt"))
            .toList()
            .map { it.toMembership() }

    override suspend fun findById(membershipId: String): ProjectMembership? =
        collection.find(eq("_id", membershipId)).firstOrNull()?.toMembership()

    override suspend fun findByToken(inviteToken: String): ProjectMembership? =
        collection.find(eq("inviteToken", inviteToken)).firstOrNull()?.toMembership()

    override suspend fun findByProjectAndUser(projectId: String, userId: String): ProjectMembership? =
        collection.find(and(eq("projectId", projectId), eq("userId", userId)))
            .firstOrNull()?.toMembership()

    override suspend fun findActiveByProjectAndEmail(projectId: String, email: String): ProjectMembership? {
        val normalized = email.trim().lowercase()
        if (normalized.isEmpty()) return null
        return collection.find(
            and(
                eq("projectId", projectId),
                eq("email", normalized),
                eq("status", MembershipStatus.ACTIVE.name)
            )
        ).firstOrNull()?.toMembership()
    }

    override suspend fun roleFor(projectId: String, userId: String): ProjectRole? {
        val doc = collection.find(
            and(
                eq("projectId", projectId),
                eq("userId", userId),
                eq("status", MembershipStatus.ACTIVE.name)
            )
        ).firstOrNull() ?: return null
        return runCatching { ProjectRole.valueOf(doc.getString("role") ?: "") }.getOrNull()
    }

    override suspend fun upsertInvite(
        projectId: String,
        email: String,
        role: ProjectRole,
        invitedBy: String,
        inviteToken: String
    ): ProjectMembership {
        val now = Clock.System.now()
        val nowMs = now.toEpochMilliseconds()
        val expiresAtMs = (now + INVITE_TTL).toEpochMilliseconds()
        val normalizedEmail = email.trim().lowercase()

        val update = Updates.combine(
            Updates.set("projectId", projectId),
            Updates.set("email", normalizedEmail),
            Updates.set("role", role.name),
            Updates.set("status", MembershipStatus.INVITED.name),
            Updates.set("inviteToken", inviteToken),
            Updates.set("invitedBy", invitedBy),
            Updates.set("invitedAt", nowMs),
            Updates.set("expiresAt", expiresAtMs),
            Updates.unset("acceptedAt"),
            Updates.unset("revokedAt"),
            Updates.setOnInsert("_id", UUID.randomUUID().toString())
        )

        val options = FindOneAndUpdateOptions()
            .upsert(true)
            .returnDocument(ReturnDocument.AFTER)

        val doc = collection.findOneAndUpdate(
            and(eq("projectId", projectId), eq("email", normalizedEmail)),
            update,
            options
        ) ?: error("upsertInvite returned null for project=$projectId email=$normalizedEmail")

        return doc.toMembership()
    }

    override suspend fun ensureOwner(
        projectId: String,
        userId: String,
        email: String
    ): ProjectMembership {
        val now = Clock.System.now().toEpochMilliseconds()
        val normalizedEmail = email.trim().lowercase()

        // Idempotent: if a row already exists for (projectId, userId), leave it alone.
        // Used by startup backfill across existing projects.
        val update = Updates.combine(
            Updates.setOnInsert("_id", UUID.randomUUID().toString()),
            Updates.setOnInsert("projectId", projectId),
            Updates.setOnInsert("userId", userId),
            Updates.setOnInsert("email", normalizedEmail),
            Updates.setOnInsert("role", ProjectRole.OWNER.name),
            Updates.setOnInsert("status", MembershipStatus.ACTIVE.name),
            Updates.setOnInsert("invitedBy", userId),
            Updates.setOnInsert("invitedAt", now),
            Updates.setOnInsert("acceptedAt", now)
        )

        val options = FindOneAndUpdateOptions()
            .upsert(true)
            .returnDocument(ReturnDocument.AFTER)

        val doc = collection.findOneAndUpdate(
            and(eq("projectId", projectId), eq("userId", userId)),
            update,
            options
        ) ?: error("ensureOwner returned null for project=$projectId user=$userId")

        return doc.toMembership()
    }

    override suspend fun markAccepted(membershipId: String, userId: String): ProjectMembership? {
        val now = Clock.System.now().toEpochMilliseconds()
        val update = Updates.combine(
            Updates.set("userId", userId),
            Updates.set("status", MembershipStatus.ACTIVE.name),
            Updates.set("acceptedAt", now),
            Updates.unset("inviteToken")  // single-use
        )
        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return collection.findOneAndUpdate(eq("_id", membershipId), update, options)
            ?.toMembership()
    }

    override suspend fun updateRole(membershipId: String, role: ProjectRole): ProjectMembership? {
        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return collection.findOneAndUpdate(
            eq("_id", membershipId),
            Updates.set("role", role.name),
            options
        )?.toMembership()
    }

    override suspend fun resetInvite(membershipId: String, inviteToken: String): ProjectMembership? {
        val now = Clock.System.now()
        val update = Updates.combine(
            Updates.set("inviteToken", inviteToken),
            Updates.set("invitedAt", now.toEpochMilliseconds()),
            Updates.set("expiresAt", (now + INVITE_TTL).toEpochMilliseconds()),
            Updates.set("status", MembershipStatus.INVITED.name)
        )
        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return collection.findOneAndUpdate(eq("_id", membershipId), update, options)?.toMembership()
    }

    override suspend fun listProjectIdsByMember(userId: String): List<String> =
        collection.find(and(eq("userId", userId), eq("status", MembershipStatus.ACTIVE.name)))
            .projection(Document("projectId", 1))
            .toList()
            .mapNotNull { it.getString("projectId") }
            .distinct()

    override suspend fun revoke(membershipId: String): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        val result = collection.updateOne(
            eq("_id", membershipId),
            Updates.combine(
                Updates.set("status", MembershipStatus.REVOKED.name),
                Updates.set("revokedAt", now),
                Updates.unset("inviteToken")
            )
        )
        return result.modifiedCount > 0
    }

    private fun Document.toMembership(): ProjectMembership {
        val role = runCatching { ProjectRole.valueOf(getString("role") ?: "") }
            .getOrDefault(ProjectRole.VIEWER)
        val status = runCatching { MembershipStatus.valueOf(getString("status") ?: "") }
            .getOrDefault(MembershipStatus.INVITED)
        val invitedAt = (get("invitedAt") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
            ?: Instant.fromEpochMilliseconds(0)
        val acceptedAt = (get("acceptedAt") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
        val revokedAt = (get("revokedAt") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
        val expiresAt = (get("expiresAt") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
        return ProjectMembership(
            id = getString("_id"),
            projectId = getString("projectId") ?: "",
            userId = getString("userId"),
            email = getString("email") ?: "",
            role = role,
            status = status,
            inviteToken = getString("inviteToken"),
            invitedBy = getString("invitedBy") ?: "",
            invitedAt = invitedAt,
            acceptedAt = acceptedAt,
            revokedAt = revokedAt,
            expiresAt = expiresAt
        )
    }
}
