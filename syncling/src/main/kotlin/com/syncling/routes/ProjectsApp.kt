package com.syncling.routes

import kotlinx.html.*

/**
 * Authenticated projects page.
 *
 * Server-renders the shell + an empty container; the client fetches and hydrates:
 *
 *   GET    /transloom/api/projects                 → { projects: ProjectResponse[] }
 *   POST   /transloom/api/projects                 → create (CreateProjectBody)
 *   GET    /transloom/api/projects/{id}            → ProjectDetailResponse
 *   PUT    /transloom/api/projects/{id}            → update (UpdateProjectBody)
 *   DELETE /transloom/api/projects/{id}            → delete
 *   POST   /transloom/api/projects/{id}/sync       → trigger manual sync
 *   POST   /transloom/api/projects/{id}/install-webhook → reinstall webhook
 *
 * All UI state (modal, drawer, list, language picker) is owned by projects.js.
 * The Kotlin side intentionally stays thin so the markup is easy to scan when
 * jumping between view and client.
 */
internal fun HTML.projectsApp() {
    portalShell(
        pageTitle = "Projects",
        navKey = "projects",
        staticStylesheets = listOf("/transloom/static/projects.css"),
        staticScripts = listOf("/transloom/static/projects.js"),
        onboardingPage = "projects",
        mainClass = "pr-page",
    ) {
        header("pr-header") {
            div("pr-header-text") {
                h1("page-title") { +"Projects" }
                p("page-sub") { +"Connect GitHub repositories and watch translations happen on every push." }
            }
            button(classes = "bl-btn primary") {
                id = "pr-new-btn"
                type = ButtonType.button
                +"+ New project"
            }
        }

        // Loading / empty / error states are owned by JS and swapped into #pr-list.
        // First paint shows a skeleton so the layout doesn't jump.
        div {
            id = "pr-list"
            classes = setOf("pr-grid")
            repeat(3) { div("pr-card pr-card-skeleton") }
        }

        // Detail drawer mount-point. Built lazily by JS the first time a card opens.
        div { id = "pr-drawer-mount" }

        // Create modal mount-point. Built lazily by JS the first time + New project is clicked.
        div { id = "pr-modal-mount" }
    }
}
