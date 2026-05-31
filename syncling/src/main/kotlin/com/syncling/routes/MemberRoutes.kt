package com.syncling.routes

import com.syncling.domain.MembershipStatus
import com.syncling.domain.ProjectMembership
import com.syncling.domain.ProjectRole
import com.syncling.model.ApiError
import com.syncling.repository.ProjectMembershipRepository
import com.syncling.repository.ProjectRepository
import com.syncling.repository.UserRepository
import com.syncling.services.BillingService
import com.syncling.services.InAppNotificationService
import com.syncling.services.NotificationService
import com.syncling.services.requireProjectRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private val memberLog = LoggerFactory.getLogger("MemberRoutes")
private val secureRandom = SecureRandom()
private val RESEND_COOLDOWN = 1.hours

@Serializable
data class MemberDto(
    val id: String,
    val userId: String?,
    val email: String,
    val role: String,
    val status: String,
    val invitedAt: Long,
    val acceptedAt: Long?,
    val displayName: String?,
    /** Epoch-millis when the pending invite link expires. Null for ACTIVE/REVOKED rows. */
    val expiresAt: Long?,
    /** Full accept URL — only present in invite-create and resend responses, never in list. */
    val inviteLink: String? = null
)

@Serializable
data class MemberListResponse(
    val members: List<MemberDto>,
    /** Project owner's plan, e.g. "FREE", "SOLO", "TEAM". */
    val ownerPlan: String,
    /** Max invitable seats (excluding OWNER) under the owner's plan. -1 means unlimited. */
    val seatLimit: Int,
    /** Active + pending invited rows (excluding OWNER) counted against the seat limit. */
    val seatsUsed: Int
)

@Serializable
data class InviteBody(val email: String, val role: String)

@Serializable
data class UpdateRoleBody(val role: String)

@Serializable
data class InvitePreview(
    val projectId: String,
    val projectName: String,
    val role: String,
    val email: String,
    val invitedBy: String?,
    val expired: Boolean
)

@Serializable
data class AcceptResponse(val projectId: String, val role: String)

fun Route.configureMemberRoutes(
    memberships: ProjectMembershipRepository,
    projects: ProjectRepository,
    users: UserRepository,
    billing: BillingService,
    notifications: NotificationService?,
    inApp: InAppNotificationService?
) {
    route("/syncling/api/projects/{id}/members") {
        get {
            val userId = call.userId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

            val project = projects.findById(projectId)
            call.requireProjectRole(project, userId, ProjectRole.VIEWER, memberships)
                ?: return@get

            val rows = memberships.list(projectId)
            val userIds = rows.mapNotNull { it.userId }.distinct()
            val nameById = users.findByIds(userIds).associate { it.id to it.githubUsername }
            val ownerPlan = billing.getPlan(project!!.ownerId)
            val seatsUsed = countSeats(rows)
            call.respond(
                HttpStatusCode.OK,
                MemberListResponse(
                    members = rows.map { it.toDto(nameById[it.userId]) },
                    ownerPlan = ownerPlan.name,
                    seatLimit = if (ownerPlan.maxMembers == Int.MAX_VALUE) -1 else ownerPlan.maxMembers,
                    seatsUsed = seatsUsed
                )
            )
        }

        post {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))

            val project = projects.findById(projectId)
            call.requireProjectRole(project, userId, ProjectRole.ADMIN, memberships)
                ?: return@post

            val body = runCatching { call.receive<InviteBody>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("email and role required"))
            }
            val email = body.email.trim().lowercase()
            if (!email.matches(EMAIL_REGEX)) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid email address"))
            }
            val role = parseRole(body.role)
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("role must be ADMIN, TRANSLATOR, or VIEWER"))
            if (role == ProjectRole.OWNER) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiError("Cannot invite as OWNER — use transfer-ownership instead"))
            }

            // Seat gate: enforce against the project OWNER's plan, since they pay.
            // FREE/SOLO have maxMembers=0 (no teammates); TEAM = 15; ENTERPRISE = unlimited.
            val ownerPlan = billing.getPlan(project!!.ownerId)
            if (ownerPlan.maxMembers <= 0) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    ApiError("Inviting teammates is a Team-plan feature. Ask the project owner to upgrade to invite members.")
                )
            }
            val existing = memberships.list(projectId)
            // Re-inviting an existing pending email doesn't consume a new seat — only block when net-new.
            val emailAlreadySeated = existing.any {
                it.email.equals(email, ignoreCase = true) &&
                    (it.status == MembershipStatus.ACTIVE || it.status == MembershipStatus.INVITED)
            }
            if (!emailAlreadySeated && countSeats(existing) >= ownerPlan.maxMembers) {
                return@post call.respond(
                    HttpStatusCode.Forbidden,
                    ApiError("Seat limit (${ownerPlan.maxMembers}) reached for the ${ownerPlan.displayName} plan.")
                )
            }

            val token = newInviteToken()
            val membership = memberships.upsertInvite(projectId, email, role, userId, token)

            // Best-effort email — invite is created even if SMTP is down so the link
            // can be shared manually from the dashboard.
            val acceptUrl = buildAcceptUrl(call, token)
            val inviter = users.findById(userId)
            if (notifications?.isConfigured == true) {
                runCatching {
                    notifications.sendInviteEmail(
                        to = email,
                        inviterName = inviter?.githubUsername ?: "A Syncling user",
                        projectName = project!!.name,
                        role = role.name,
                        acceptUrl = acceptUrl
                    )
                }.onFailure { memberLog.warn("Invite email failed: {}", it.message) }
            }

            memberLog.info("Invite created: project={} email={} role={} by={}", projectId, email, role, userId)
            call.respond(HttpStatusCode.Created, membership.toDto(null, acceptUrl))
        }

        patch("/{membershipId}") {
            val userId = call.userId()
                ?: return@patch call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@patch call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
            val membershipId = call.parameters["membershipId"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@patch call.respond(HttpStatusCode.BadRequest, ApiError("Invalid membership id"))

            val project = projects.findById(projectId)
            call.requireProjectRole(project, userId, ProjectRole.ADMIN, memberships)
                ?: return@patch

            val body = runCatching { call.receive<UpdateRoleBody>() }.getOrElse {
                return@patch call.respond(HttpStatusCode.BadRequest, ApiError("role required"))
            }
            val newRole = parseRole(body.role)
                ?: return@patch call.respond(HttpStatusCode.BadRequest, ApiError("role must be OWNER, ADMIN, TRANSLATOR, or VIEWER"))

            val target = memberships.findById(membershipId)
            if (target == null || target.projectId != projectId) {
                return@patch call.respond(HttpStatusCode.NotFound, ApiError("Member not found"))
            }
            // Demoting the sole OWNER would orphan the project — block it.
            if (target.role == ProjectRole.OWNER && newRole != ProjectRole.OWNER) {
                return@patch call.respond(HttpStatusCode.Conflict, ApiError("Transfer ownership before demoting the owner"))
            }
            val updated = memberships.updateRole(membershipId, newRole)
                ?: return@patch call.respond(HttpStatusCode.NotFound, ApiError("Member not found"))
            call.respond(HttpStatusCode.OK, updated.toDto(target.userId?.let { users.findById(it)?.githubUsername }))
        }

        delete("/{membershipId}") {
            val userId = call.userId()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
            val membershipId = call.parameters["membershipId"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid membership id"))

            val project = projects.findById(projectId)
                ?: return@delete call.respond(HttpStatusCode.NotFound, ApiError("Project not found"))
            val target = memberships.findById(membershipId)
            if (target == null || target.projectId != projectId) {
                return@delete call.respond(HttpStatusCode.NotFound, ApiError("Member not found"))
            }
            // Self-leave: a non-OWNER can revoke their own membership without ADMIN.
            // Otherwise require ADMIN+ to remove someone else.
            val isSelfLeave = target.userId == userId && target.role != ProjectRole.OWNER
            if (!isSelfLeave) {
                call.requireProjectRole(project, userId, ProjectRole.ADMIN, memberships)
                    ?: return@delete
            }
            if (target.role == ProjectRole.OWNER) {
                return@delete call.respond(HttpStatusCode.Conflict, ApiError("Transfer ownership before removing the owner"))
            }
            memberships.revoke(membershipId)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/{membershipId}/resend") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val projectId = call.parameters["id"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid project id"))
            val membershipId = call.parameters["membershipId"]
                ?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid membership id"))

            val project = projects.findById(projectId)
            call.requireProjectRole(project, userId, ProjectRole.ADMIN, memberships)
                ?: return@post

            val target = memberships.findById(membershipId)
            if (target == null || target.projectId != projectId) {
                return@post call.respond(HttpStatusCode.NotFound, ApiError("Member not found"))
            }
            if (target.status != MembershipStatus.INVITED) {
                return@post call.respond(HttpStatusCode.Conflict, ApiError("Can only resend to a pending invite"))
            }

            // Cooldown: invitedAt is updated on each send, so it tracks when the last invite went out.
            val cooldownExpiry = target.invitedAt + RESEND_COOLDOWN
            if (Clock.System.now() < cooldownExpiry) {
                val waitMins = ((cooldownExpiry - Clock.System.now()).inWholeSeconds / 60).coerceAtLeast(1)
                return@post call.respond(
                    HttpStatusCode.TooManyRequests,
                    ApiError("Invite was sent recently. Wait $waitMins minute(s) before resending.")
                )
            }

            val newToken = newInviteToken()
            val updated = memberships.resetInvite(membershipId, newToken)
                ?: return@post call.respond(HttpStatusCode.NotFound, ApiError("Member not found"))

            val acceptUrl = buildAcceptUrl(call, newToken)
            val inviter = users.findById(userId)
            if (notifications?.isConfigured == true) {
                runCatching {
                    notifications.sendInviteEmail(
                        to = target.email,
                        inviterName = inviter?.githubUsername ?: "A Syncling user",
                        projectName = project!!.name,
                        role = target.role.name,
                        acceptUrl = acceptUrl
                    )
                }.onFailure { memberLog.warn("Resend invite email failed: {}", it.message) }
            }

            memberLog.info("Invite resent: project={} email={} by={}", projectId, target.email, userId)
            call.respond(HttpStatusCode.OK, updated.toDto(null, acceptUrl))
        }
    }

    route("/syncling/api/invites/{token}") {
        // Unauthenticated preview so the invitee can see what they're accepting
        // before logging in. Returns 404 for any token mismatch or already-consumed invite.
        get {
            val token = call.parameters["token"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Missing token"))
            val membership = memberships.findByToken(token)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Invite not found or already used"))
            val project = projects.findById(membership.projectId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Project no longer exists"))
            val inviter = users.findById(membership.invitedBy)?.githubUsername
            val isExpired = membership.status != MembershipStatus.INVITED ||
                (membership.expiresAt != null && Clock.System.now() > membership.expiresAt)
            call.respond(
                HttpStatusCode.OK,
                InvitePreview(
                    projectId = project.id,
                    projectName = project.name,
                    role = membership.role.name,
                    email = membership.email,
                    invitedBy = inviter,
                    expired = isExpired
                )
            )
        }

        post("/accept") {
            val userId = call.userId()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid token"))
            val token = call.parameters["token"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Missing token"))

            val membership = memberships.findByToken(token)
                ?: return@post call.respond(HttpStatusCode.NotFound, ApiError("Invite not found or already used"))
            if (membership.status != MembershipStatus.INVITED) {
                return@post call.respond(HttpStatusCode.Conflict, ApiError("Invite already used"))
            }
            if (membership.expiresAt != null && Clock.System.now() > membership.expiresAt) {
                return@post call.respond(HttpStatusCode.Gone, ApiError("Invite link has expired. Ask an admin to send a new invite."))
            }
            val project = projects.findById(membership.projectId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ApiError("Project no longer exists"))

            // Block accept-as-different-user when the invitee already has an active row
            // on this project (e.g. they were re-invited after being added another way).
            val existing = memberships.findByProjectAndUser(membership.projectId, userId)
            if (existing != null && existing.status == MembershipStatus.ACTIVE) {
                return@post call.respond(HttpStatusCode.Conflict, ApiError("You are already a member of this project"))
            }

            val accepted = memberships.markAccepted(membership.id, userId)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, ApiError("Failed to accept invite"))

            // Notify the project owner so they see the join in the dashboard bell.
            inApp?.runCatching {
                val acceptingUser = users.findById(userId)
                notifyInviteAccepted(
                    ownerId = project.ownerId,
                    memberName = acceptingUser?.githubUsername ?: accepted.email,
                    projectName = project.name,
                    projectId = project.id
                )
            }?.onFailure { memberLog.warn("Invite-accepted notify failed: {}", it.message) }

            memberLog.info("Invite accepted: project={} role={} user={}", project.id, accepted.role, userId)
            call.respond(HttpStatusCode.OK, AcceptResponse(projectId = project.id, role = accepted.role.name))
        }
    }
}

private fun ProjectMembership.toDto(displayName: String?, inviteLink: String? = null): MemberDto = MemberDto(
    id = id,
    userId = userId,
    email = email,
    role = role.name,
    status = status.name,
    invitedAt = invitedAt.toEpochMilliseconds(),
    acceptedAt = acceptedAt?.toEpochMilliseconds(),
    displayName = displayName,
    expiresAt = expiresAt?.toEpochMilliseconds(),
    inviteLink = inviteLink
)

// Seats counted = active members + pending invites, excluding the OWNER (who is implicit, not a seat).
private fun countSeats(rows: List<ProjectMembership>): Int = rows.count {
    it.role != ProjectRole.OWNER &&
        (it.status == MembershipStatus.ACTIVE || it.status == MembershipStatus.INVITED)
}

private fun parseRole(raw: String): ProjectRole? =
    runCatching { ProjectRole.valueOf(raw.trim().uppercase()) }.getOrNull()

private fun newInviteToken(): String {
    val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun buildAcceptUrl(call: ApplicationCall, token: String): String {
    val origin = call.request.origin
    val scheme = if (origin.scheme.isNullOrBlank()) "https" else origin.scheme
    val host = origin.serverHost
    val port = origin.serverPort
    val portPart = if ((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) "" else ":$port"
    return "$scheme://$host$portPart/syncling/invite/$token"
}

// Pragmatic email regex — good enough for "did the user fat-finger this" and matches
// the level of validation used elsewhere in the codebase.
private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
