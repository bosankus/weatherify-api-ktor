package com.transloom.routes

import kotlinx.html.*

/**
 * Analytics page at /transloom/billing/analytics.
 *
 * Hands off to `/transloom/static/billing-analytics.js` which hydrates seven sections
 * from /transloom/api/analytics/{overview, projects, locales, runs, members,
 * cost-breakdown, quality}. Server-renders skeletons + a "tracking since" banner
 * (filled in once the overview endpoint returns); each section degrades to an
 * "Unable to load" empty-state if its fetch fails.
 *
 * FREE users hit a 403 from the endpoints; the JS catches that and shows a soft
 * upgrade prompt without leaving the page. Team-only sections (Members, Cost
 * breakdown) are hidden on the client when /overview reports a non-TEAM plan.
 */
internal fun HTML.billingAnalyticsApp() {
    portalShell(
        pageTitle = "Analytics",
        navKey = "analytics",
        staticStylesheets = listOf("/transloom/static/billing.css", "/transloom/static/billing-analytics.css"),
        staticScripts = listOf("/transloom/static/billing-analytics.js"),
        mainClass = "bl-page",
    ) {
        header("bl-header") {
            div("bl-header-text") {
                h1("page-title") { +"Analytics" }
                p("page-sub") { +"Translation activity, costs, and team performance — sourced from your own pipeline runs." }
            }
            div("bla-range-picker") {
                id = "bla-range-picker"
                attributes["role"] = "tablist"
                attributes["aria-label"] = "Date range"
                button(classes = "bla-range-btn active") {
                    attributes["data-range"] = "30d"
                    attributes["role"] = "tab"
                    +"30 days"
                }
                button(classes = "bla-range-btn") {
                    attributes["data-range"] = "90d"
                    attributes["role"] = "tab"
                    +"90 days"
                }
                button(classes = "bla-range-btn") {
                    attributes["data-range"] = "month"
                    attributes["role"] = "tab"
                    +"This month"
                }
            }
        }

        // Tracking-since banner — JS fills text + show/hide based on overview.trackingSinceMillis.
        // Default visible "loading" state so the page doesn't appear empty if the request is slow.
        div {
            id = "bla-tracking-banner"
            classes = setOf("bla-tracking-banner")
            attributes["aria-live"] = "polite"
            +"Loading…"
        }

        // Plan-gate banner. JS shows it for FREE users and hides the metric sections.
        div {
            id = "bla-plan-gate"
            classes = setOf("bla-plan-gate")
            attributes["aria-live"] = "polite"
            style = "display:none"
            div("bla-plan-gate-text") {
                strong { +"Analytics is part of Solo and Team plans." }
                +" Upgrade to see cost-per-string trends, per-project breakdowns, and team activity."
            }
            a("/transloom/billing") { classes = setOf("bla-plan-gate-cta"); +"Compare plans →" }
        }

        // ── Overview ─────────────────────────────────────────────────────────────
        section("bl-card") {
            attributes["aria-labelledby"] = "bla-overview-title"
            div("bl-card-head") {
                h2 { id = "bla-overview-title"; +"Overview" }
                span("bl-eyebrow") { id = "bla-overview-period"; +"This month" }
            }
            div("bla-overview-grid") {
                blaMetric("Cost / string", "bla-cps", "—")
                blaMetric("This month", "bla-cps-month-strings", "—", subId = "bla-cps-month-sub")
                blaMetric("vs last month", "bla-cps-delta", "—", subId = "bla-cps-delta-sub")
                blaMetric("Projected EoM", "bla-cps-projected", "—", subId = "bla-cps-projected-sub")
            }
            div("bla-spark") {
                id = "bla-spark"
                // Six bars rendered client-side using the existing .bl-spark-grid pattern.
            }
        }

        // ── Projects ─────────────────────────────────────────────────────────────
        section("bl-card") {
            attributes["aria-labelledby"] = "bla-projects-title"
            div("bl-card-head") {
                h2 { id = "bla-projects-title"; +"Projects" }
                span("bl-eyebrow") { id = "bla-projects-count" }
            }
            div("bla-table-wrap") { id = "bla-projects-body"; +"" }
        }

        // ── Locales ──────────────────────────────────────────────────────────────
        section("bl-card") {
            attributes["aria-labelledby"] = "bla-locales-title"
            div("bl-card-head") { h2 { id = "bla-locales-title"; +"Locales" } }
            div("bla-table-wrap") { id = "bla-locales-body" }
        }

        // ── Quality (acceptance rate) ────────────────────────────────────────────
        section("bl-card") {
            attributes["aria-labelledby"] = "bla-quality-title"
            div("bl-card-head") {
                h2 { id = "bla-quality-title"; +"Quality" }
                span("bl-eyebrow") { +"Auto-approval acceptance rate per project" }
            }
            div("bla-table-wrap") { id = "bla-quality-body" }
        }

        // ── Members (Team only — JS hides for Solo) ──────────────────────────────
        section("bl-card") {
            id = "bla-members-section"
            attributes["aria-labelledby"] = "bla-members-title"
            attributes["data-team-only"] = "true"
            div("bl-card-head") {
                h2 { id = "bla-members-title"; +"Members" }
                span("bl-eyebrow") { +"Team plan · who translated what" }
            }
            div("bla-table-wrap") { id = "bla-members-body" }
        }

        // ── Cost breakdown (Team only) ───────────────────────────────────────────
        section("bl-card") {
            id = "bla-cost-section"
            attributes["aria-labelledby"] = "bla-cost-title"
            attributes["data-team-only"] = "true"
            div("bl-card-head") {
                h2 { id = "bla-cost-title"; +"Cost breakdown" }
                span("bl-eyebrow") { +"Plan price prorated by translation share" }
            }
            div { id = "bla-cost-body" }
        }

        // ── Pipeline runs table ──────────────────────────────────────────────────
        section("bl-card") {
            attributes["aria-labelledby"] = "bla-runs-title"
            div("bl-card-head") {
                h2 { id = "bla-runs-title"; +"Pipeline runs" }
                span("bl-eyebrow") { id = "bla-runs-count" }
            }
            div("bla-runs-toolbar") { id = "bla-runs-toolbar" }
            div("bla-table-wrap") { id = "bla-runs-body" }
            div("bla-runs-pagination") { id = "bla-runs-pagination" }
        }
    }
}

private fun FlowContent.blaMetric(label: String, valueId: String, fallback: String, subId: String? = null) {
    div("bla-metric") {
        div("bla-metric-label") { +label }
        div("bla-metric-value") { id = valueId; +fallback }
        if (subId != null) div("bla-metric-sub") { id = subId; +"" }
    }
}
