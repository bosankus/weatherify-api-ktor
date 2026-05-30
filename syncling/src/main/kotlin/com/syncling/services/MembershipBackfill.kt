package com.syncling.services

import com.syncling.repository.ProjectMembershipRepository
import com.syncling.repository.ProjectRepository
import com.syncling.repository.UserRepository
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("MembershipBackfill")

/**
 * Materializes an OWNER membership row for every existing project so the new
 * permission helper has a single code path with no "is legacy owner?" fallback.
 *
 * Idempotent: `ensureOwner` upserts on (projectId, userId), so repeated runs
 * are no-ops and the (projectId, userId) unique index never trips.
 *
 * Called from app startup in a background coroutine — startup latency is not
 * affected and the owner short-circuit in `requireProjectRole` keeps reads
 * correct even before the backfill finishes.
 */
suspend fun backfillProjectMemberships(
    projects: ProjectRepository,
    users: UserRepository,
    memberships: ProjectMembershipRepository
) {
    val all = projects.listAll()
    var created = 0
    var skipped = 0
    for (project in all) {
        val existing = memberships.findByProjectAndUser(project.id, project.ownerId)
        if (existing != null) { skipped++; continue }
        val email = users.findById(project.ownerId)?.email ?: ""
        runCatching { memberships.ensureOwner(project.id, project.ownerId, email) }
            .onSuccess { created++ }
            .onFailure { log.warn("backfill: ensureOwner failed for project={} owner={}: {}", project.id, project.ownerId, it.message) }
    }
    log.info("Membership backfill complete: projects={} owner-rows-created={} already-present={}", all.size, created, skipped)
}
