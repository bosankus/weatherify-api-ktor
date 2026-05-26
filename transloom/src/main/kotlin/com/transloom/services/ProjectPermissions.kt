package com.transloom.services

import com.transloom.domain.Project
import com.transloom.domain.ProjectRole
import com.transloom.model.ApiError
import com.transloom.repository.ProjectMembershipRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Resolves the caller's effective role on [project] and enforces [minRole].
 *
 * On success returns the role. On denial (project missing, not a member, or role
 * below [minRole]) writes [denyStatus] + [denyMessage] to the response and returns
 * null — callers chain with `?: return@... `.
 *
 * The `ownerId == userId` short-circuit avoids a Mongo round-trip on the owner's
 * own requests (the common case) and stays safe for projects created before the
 * membership backfill runs.
 *
 * Status convention matching the existing routes: `/projects/{id}` endpoints use
 * 404 (don't leak existence to non-members); review/glossary endpoints that already
 * loaded a translation use 403 ("Access denied").
 */
@OptIn(ExperimentalContracts::class)
suspend fun ApplicationCall.requireProjectRole(
    project: Project?,
    userId: String,
    minRole: ProjectRole,
    memberships: ProjectMembershipRepository,
    denyStatus: HttpStatusCode = HttpStatusCode.NotFound,
    denyMessage: String = "Project not found"
): ProjectRole? {
    // Lets callers keep using `project` (non-null) after `?: return@xxx`, preserving
    // the smart-cast that the original `if (project == null) return` provided.
    contract { returnsNotNull() implies (project != null) }
    if (project == null) {
        respond(denyStatus, ApiError(denyMessage))
        return null
    }
    val role = effectiveRole(project, userId, memberships)
    if (role == null || !role.atLeast(minRole)) {
        respond(denyStatus, ApiError(denyMessage))
        return null
    }
    return role
}

/**
 * Returns the user's effective role on [project], or null if not a member.
 * Used by the bulk-approve site that scans many projects in one request — it
 * needs role resolution without writing a response.
 */
suspend fun effectiveRole(
    project: Project,
    userId: String,
    memberships: ProjectMembershipRepository
): ProjectRole? = if (project.ownerId == userId) {
    ProjectRole.OWNER
} else {
    memberships.roleFor(project.id, userId)
}
