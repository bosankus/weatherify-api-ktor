package com.syncling.routes

import kotlinx.html.*

/**
 * Per-project Members page. Server renders the shell + a project picker and
 * empty mount points; the client (members.js) hydrates by reading the project
 * id from the URL path and fetching from:
 *
 *   GET    /transloom/api/projects                       → resolves picker list + first project
 *   GET    /transloom/api/projects/{id}/members          → list members + pending invites
 *   POST   /transloom/api/projects/{id}/members          → invite { email, role }
 *   PATCH  /transloom/api/projects/{id}/members/{mId}    → change role
 *   DELETE /transloom/api/projects/{id}/members/{mId}    → revoke / self-leave
 *
 * Same thin-server / fat-client split used by ProjectsApp.kt — the markup is
 * deliberately minimal so the swap between view and client stays easy to scan.
 */
internal fun HTML.membersApp(projectId: String?) {
    portalShell(
        pageTitle = "Members",
        navKey = "members",
        staticStylesheets = listOf("/transloom/static/members.css"),
        staticScripts = listOf("/transloom/static/members.js"),
        mainClass = "mb-page",
    ) {
        // The picker lives in the header so admins can switch projects without
        // bouncing back through Projects. Hidden when only one project exists.
        header("mb-header") {
            div("mb-header-text") {
                h1("page-title") { +"Members" }
                p("page-sub") { +"Invite teammates and control who can review, translate, or just look around." }
            }
            div("mb-header-actions") {
                select {
                    id = "mb-project-select"
                    classes = setOf("mb-project-select")
                    attributes["aria-label"] = "Switch project"
                }
                button(classes = "bl-btn primary") {
                    id = "mb-invite-btn"
                    type = ButtonType.button
                    disabled = true
                    +"+ Invite"
                }
            }
        }

        // Read by the client to know which project to load on first paint.
        // Empty string means "pick first project after the list loads".
        div {
            id = "mb-bootstrap"
            attributes["data-project-id"] = projectId ?: ""
            attributes["hidden"] = "hidden"
        }

        // List host — skeleton placeholder until the first fetch completes.
        div {
            id = "mb-list"
            classes = setOf("mb-list")
            repeat(3) { div("mb-row mb-row-skeleton") }
        }

        // Modal mount-point — built lazily by JS on first invite click.
        div { id = "mb-modal-mount" }
    }
}
