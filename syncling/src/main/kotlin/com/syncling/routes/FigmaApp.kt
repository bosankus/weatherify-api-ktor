package com.syncling.routes

import kotlinx.html.*

/**
 * Figma inbox page. Server renders the shell + empty mount points; the client
 * (figma.js) hydrates by reading the project id from the URL path and fetching:
 *
 *   GET   /api/projects                                → project picker
 *   GET   /api/figma/projects/{id}/candidates          → inbox rows (per status tab)
 *   GET   /api/figma/projects/{id}/preview?fileKey&frameId → frame thumbnails (blob-fetched)
 *   PATCH /api/figma/candidates/{cId}                  → rename key
 *   POST  /api/figma/projects/{id}/approve             → merge + open PR
 *   POST  /api/figma/projects/{id}/reject              → reject selection
 *
 * Same thin-server / fat-client split as MembersApp.kt.
 *
 * Figma sync is a paid feature. Free users get the plan gate (same formation as
 * the tokens/members pages) instead of the inbox — no toolbar, no list, and the
 * client script isn't loaded at all.
 *
 * [showOnboarding] opens the one-time "how this works" walkthrough on first paint.
 * State lives server-side on the user (figmaOnboardingSeenAt), not localStorage,
 * so it shows exactly once per user regardless of device — see PortalRoutes.
 */
internal fun HTML.figmaApp(projectId: String?, isPaid: Boolean, showOnboarding: Boolean = false) {
    portalShell(
        pageTitle = "Figma inbox",
        navKey = "figma",
        staticStylesheets = listOf("/transloom/static/figma.css"),
        staticScripts = if (isPaid) listOf("/transloom/static/figma.js") else emptyList(),
        mainClass = "fg-page",
    ) {
        header("fg-header") {
            div("fg-header-text") {
                h1("page-title") { +"Figma inbox" }
                p("page-sub") { +"Strings pushed from the Figma plugin. Approve to open a PR — merging it runs the whole translation pipeline." }
            }
            if (isPaid) {
                div("fg-header-actions") {
                    button(classes = "bl-btn") {
                        id = "fg-help-btn"
                        type = ButtonType.button
                        attributes["aria-label"] = "How Figma sync works"
                        +"How it works"
                    }
                    select {
                        id = "fg-project-select"
                        classes = setOf("fg-project-select")
                        attributes["aria-label"] = "Switch project"
                    }
                }
            }
        }

        // ── Paid-feature gate ────────────────────────────────────────────────
        // Mirrors the tokens page plan gate: free users only see what upgrading
        // unlocks — never the inbox itself.
        if (!isPaid) {
            div("fg-plan-gate") {
                h3 { +"Figma sync is a paid feature" }
                p {
                    +"You're on the "
                    b { +"Free" }
                    +" plan. Upgrade to push copy straight from Figma with the Syncling plugin, review incoming strings in this inbox, and approve them into a pull request — merging it runs your whole translation pipeline, with frame screenshots attached as context for higher-quality translations."
                }
                a("/billing") { classes = setOf("bl-btn", "primary"); +"Upgrade plan" }
            }
            return@portalShell
        }

        // Read by the client to know which project to load on first paint.
        div {
            id = "fg-bootstrap"
            attributes["data-project-id"] = projectId ?: ""
            attributes["data-show-onboarding"] = showOnboarding.toString()
            attributes["hidden"] = "hidden"
        }

        // Built lazily by figma.js only when data-show-onboarding is true.
        div { id = "fg-onboarding-mount" }

        div("fg-toolbar") {
            div("fg-tabs") {
                id = "fg-tabs"
                button(classes = "fg-tab active") { attributes["data-status"] = "PENDING"; type = ButtonType.button; +"Pending" }
                button(classes = "fg-tab") { attributes["data-status"] = "PR_OPEN"; type = ButtonType.button; +"PR opened" }
                button(classes = "fg-tab") { attributes["data-status"] = "REJECTED"; type = ButtonType.button; +"Rejected" }
                button(classes = "fg-tab") { attributes["data-status"] = "DRIFT"; type = ButtonType.button; +"Drift" }
            }
            // Bulk actions — enabled while at least one pending row is checked.
            div("fg-actions") {
                id = "fg-actions"
                label("fg-auto") {
                    attributes["title"] = "Skip the inbox: every push opens a PR immediately (admins only)"
                    input(InputType.checkBox) { id = "fg-auto-approve" }
                    +" Auto-approve → PR"
                }
                select { id = "fg-target-file"; classes = setOf("fg-target-file"); attributes["aria-label"] = "Target source file" }
                button(classes = "bl-btn primary") { id = "fg-approve-btn"; type = ButtonType.button; disabled = true; +"Approve → PR" }
                button(classes = "bl-btn") { id = "fg-reject-btn"; type = ButtonType.button; disabled = true; +"Reject" }
            }
        }

        // List host — skeleton placeholder until the first fetch completes.
        div {
            id = "fg-list"
            classes = setOf("fg-list")
            repeat(3) { div("fg-row fg-row-skeleton") }
        }
    }
}
