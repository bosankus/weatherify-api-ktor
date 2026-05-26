package com.transloom.routes

import kotlinx.html.*

/**
 * Authenticated translation review portal.
 *
 * Server-renders the hero + toolbar shell; the list, cards, and reject/edit
 * affordances are all hydrated by `review.js`.
 *
 * Endpoints consumed:
 *   GET    /transloom/api/review?status&language&limit&offset
 *   POST   /transloom/api/review/{id}/approve         body: { editedText? }
 *   POST   /transloom/api/review/batch-approve        body: { ids: [...] }
 *   POST   /transloom/api/review/{id}/reject          body: { reason }
 *   POST   /transloom/api/review/{id}/hotfix          body: { newText }
 *   POST   /transloom/api/review/{id}/lock
 *   POST   /transloom/api/review/{id}/unlock
 *
 * Class names use the `rv-` prefix to match the existing static/review.css.
 * That stylesheet is the source of truth for visuals — keep markup classes
 * synced with it when changing layout.
 */
internal fun HTML.reviewPortal() {
    portalShell(
        pageTitle = "Review",
        navKey = "review",
        reviewBadge = true,
        staticStylesheets = listOf("/transloom/static/review.css"),
        staticScripts = listOf("/transloom/static/review.js"),
        mainClass = "rv-page",
    ) {
        div("rv-inner") {
            // ── Hero ─────────────────────────────────────────────────────────
            header("rv-hero") {
                div("rv-hero-top") {
                    div {
                        h1("rv-page-title") { +"Pending translations" }
                        p("rv-page-sub") {
                            +"Approve, edit, or reject AI translations before they ship. "
                            +"Hotfixes publish to the CDN immediately for OTA-enabled projects."
                        }
                    }
                    div("rv-hero-controls") {
                        button(classes = "rv-btn-refresh", type = ButtonType.button) {
                            id = "rv-refresh-btn"
                            unsafe { +"""<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>"""
                            }
                            +" Refresh"
                        }
                    }
                }
                // Hydrated by JS. Skeleton chips keep layout stable on first paint.
                div("rv-stat-row") {
                    id = "rv-stats"
                    repeat(4) {
                        span("rv-stat-chip rv-chip-pending") { +"…" }
                    }
                }
            }

            // ── Toolbar (filters + search) ───────────────────────────────────
            div("rv-toolbar") {
                div("rv-filters") {
                    id = "rv-filters"
                    attributes["role"] = "tablist"
                    rvFilter("all", "All", active = true)
                    rvFilter("review", "Needs review")
                    rvFilter("blocked", "Blocked")
                    rvFilter("cultural", "Cultural")
                }
                div("rv-search-wrap") {
                    span("rv-search-icon") {
                        unsafe { +"""<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>""" }
                    }
                    input(type = InputType.search, classes = "rv-search-input") {
                        id = "rv-search"
                        placeholder = "Search keys, projects, languages…"
                        attributes["autocomplete"] = "off"
                    }
                }
            }

            // ── List ─────────────────────────────────────────────────────────
            div("rv-list") {
                id = "rv-list"
                // First-paint skeleton.
                repeat(2) {
                    div("rv-skeleton") {
                        div("rv-skel-head") {}
                        div("rv-skel-body") {
                            div("rv-skel-col") {
                                div("rv-skel-line w80") {}
                                div("rv-skel-line w60") {}
                                div("rv-skel-line w40") {}
                            }
                            div("rv-skel-col") {
                                div("rv-skel-line w80") {}
                                div("rv-skel-line w60") {}
                                div("rv-skel-line w40") {}
                            }
                        }
                        div("rv-skel-foot") {}
                    }
                }
            }
        }
    }
}

private fun FlowContent.rvFilter(id: String, label: String, active: Boolean = false) {
    button(classes = if (active) "rv-filter active" else "rv-filter", type = ButtonType.button) {
        attributes["data-filter"] = id
        attributes["role"] = "tab"
        attributes["aria-selected"] = active.toString()
        +label
        span("rv-filter-count") {
            attributes["data-filter-count"] = id
            +"0"
        }
    }
}
