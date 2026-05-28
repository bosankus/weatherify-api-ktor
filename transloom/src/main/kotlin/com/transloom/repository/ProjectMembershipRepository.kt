package com.transloom.repository

import com.transloom.domain.ProjectMembership
import com.transloom.domain.ProjectRole

interface ProjectMembershipRepository {
    /** All memberships for a project, including INVITED and REVOKED, newest first. */
    suspend fun list(projectId: String): List<ProjectMembership>

    suspend fun findById(membershipId: String): ProjectMembership?

    suspend fun findByToken(inviteToken: String): ProjectMembership?

    suspend fun findByProjectAndUser(projectId: String, userId: String): ProjectMembership?

    /**
     * Looks up an ACTIVE membership for the given (projectId, lowercased email).
     * Returns null if no ACTIVE row matches — used by the pipeline to attribute
     * webhook-triggered runs to a known member when the GitHub commit author's
     * email maps to one. Pending INVITED / REVOKED rows are deliberately ignored
     * so a stale invite never grants attribution credit.
     */
    suspend fun findActiveByProjectAndEmail(projectId: String, email: String): ProjectMembership?

    /** Resolves to the user's effective role on a project, or null if not ACTIVE. */
    suspend fun roleFor(projectId: String, userId: String): ProjectRole?

    /**
     * Creates an INVITED row keyed on (projectId, lowercased email). If a row already
     * exists for that pair the role is updated and a fresh inviteToken is issued — this
     * lets an admin re-invite a stale email without a duplicate-key crash.
     */
    suspend fun upsertInvite(
        projectId: String,
        email: String,
        role: ProjectRole,
        invitedBy: String,
        inviteToken: String
    ): ProjectMembership

    /**
     * Materializes the owner as an ACTIVE membership. Idempotent — used by startup
     * backfill and by the project-create flow. No-op if a row already exists.
     */
    suspend fun ensureOwner(projectId: String, userId: String, email: String): ProjectMembership

    /** Binds [userId] to the invite and flips status to ACTIVE. Clears the token (single-use). */
    suspend fun markAccepted(membershipId: String, userId: String): ProjectMembership?

    suspend fun updateRole(membershipId: String, role: ProjectRole): ProjectMembership?

    /**
     * Resets an INVITED row with a fresh token and a new 7-day expiry window.
     * Updates invitedAt to now so the resend cooldown starts from this moment.
     * Returns null if the row no longer exists.
     */
    suspend fun resetInvite(membershipId: String, inviteToken: String): ProjectMembership?

    /** Soft-delete: status -> REVOKED, stamps revokedAt. Keeps the row for audit. */
    suspend fun revoke(membershipId: String): Boolean

    /** Returns IDs of all projects where [userId] has an ACTIVE membership (any role). */
    suspend fun listProjectIdsByMember(userId: String): List<String>
}
