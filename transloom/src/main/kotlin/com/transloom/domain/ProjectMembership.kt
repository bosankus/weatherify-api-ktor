package com.transloom.domain

import kotlinx.datetime.Instant

/**
 * Per-project collaborator record. The owner is materialized as a row with
 * role = OWNER so every access check follows a single uniform code path.
 *
 * userId is null while the invite is pending — it binds when the invitee logs
 * in and accepts. email is the invitation address (kept after acceptance for
 * display and re-invite flows).
 */
data class ProjectMembership(
    val id: String,
    val projectId: String,
    val userId: String?,
    val email: String,
    val role: ProjectRole,
    val status: MembershipStatus,
    val inviteToken: String?,
    val invitedBy: String,
    val invitedAt: Instant,
    val acceptedAt: Instant?,
    val revokedAt: Instant?,
    /** Null for OWNER rows and legacy rows created before expiry was introduced. */
    val expiresAt: Instant?
)

enum class ProjectRole {
    /** Project creator. Full control including delete + billing. Exactly one per project. */
    OWNER,
    /** Manages members and all project operations except delete/billing. */
    ADMIN,
    /** Read project; approve/reject/hotfix in review queue; trigger sync. */
    TRANSLATOR,
    /** Read-only access to project, translations, review queue. */
    VIEWER;

    /** OWNER >= ADMIN >= TRANSLATOR >= VIEWER. Higher ordinal = lower privilege. */
    fun atLeast(min: ProjectRole): Boolean = this.ordinal <= min.ordinal
}

enum class MembershipStatus {
    /** Email sent; invitee has not yet accepted. userId is null. */
    INVITED,
    /** Invite accepted; userId is bound. */
    ACTIVE,
    /** Removed by admin or self-leave. Kept for audit; no access. */
    REVOKED
}
