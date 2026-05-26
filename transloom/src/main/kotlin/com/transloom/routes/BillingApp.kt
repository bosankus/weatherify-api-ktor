package com.transloom.routes

import kotlinx.html.*

/**
 * Authenticated billing dashboard.
 *
 * Hands off to a small client app (`/transloom/static/billing.js`) that hydrates
 * the page from three endpoints:
 *
 *   GET  /transloom/api/billing/subscription   → SubscriptionResponse
 *   GET  /transloom/api/billing/usage          → UsageResponse
 *   GET  /transloom/api/billing/invoices       → { invoices: InvoiceResponse[] }
 *
 * Actions:
 *   POST /transloom/api/billing/subscribe        — upgrade to SOLO or TEAM (starts Razorpay)
 *   POST /transloom/api/billing/activate-now     — end trial early, begin first billing period
 *   POST /transloom/api/billing/cancel           — cancel at current period end
 *   POST /transloom/api/billing/dismiss-limit    — clear the trial-limit banner
 *   POST /transloom/api/billing/confirm-payment  — verify Razorpay handler signature
 *
 * The whole page is rendered server-side as skeletons and progressively filled in;
 * each section degrades to an "Unable to load" empty-state if its fetch fails so
 * a single endpoint outage cannot blank the entire screen.
 */
internal fun HTML.billingApp() {
    portalShell(
        pageTitle = "Billing",
        navKey = "billing",
        staticStylesheets = listOf("/transloom/static/billing.css"),
        staticScripts = listOf("/transloom/static/billing.js"),
        externalScripts = listOf("https://checkout.razorpay.com/v1/checkout.js"),
        onboardingPage = "billing",
        mainClass = "bl-page",
    ) {
        header("bl-header") {
            div("bl-header-text") {
                h1("page-title") { +"Billing" }
                p("page-sub") { +"Manage your subscription, track usage, and download invoices." }
            }
            // Slot the JS fills with a "Manage plan" button once subscription has loaded.
            div { id = "bl-header-cta"; classes = setOf("bl-header-cta") }
        }

        // Transient announcement region: payment success, cancellation pending,
        // trial-limit hit, errors. JS populates and toggles `.show`.
        div {
            id = "bl-banner"
            classes = setOf("bl-banner")
            attributes["role"] = "status"
            attributes["aria-live"] = "polite"
        }

        // ── Plan summary ──────────────────────────────────────────────────────
        section("bl-card bl-plan") {
            attributes["aria-labelledby"] = "bl-plan-title"
            div("bl-card-head") {
                h2 { id = "bl-plan-title"; +"Current plan" }
                span {
                    id = "bl-plan-status"
                    classes = setOf("bl-pill", "bl-pill-muted")
                    +"Loading…"
                }
            }
            div("bl-plan-grid") {
                div("bl-plan-name-block") {
                    div("bl-eyebrow") { +"Plan" }
                    div("bl-plan-name") { id = "bl-plan-name"; +"—" }
                    div("bl-plan-price") {
                        span { id = "bl-plan-price-amount"; +"—" }
                        span("bl-plan-price-period") { id = "bl-plan-price-period" }
                    }
                }
                blPlanStat("Renewal", "bl-plan-renewal")
                blPlanStat("Trial ends", "bl-plan-trial", emptyValue = "—")
                blPlanStat("Projects", "bl-plan-projects")
            }
            div {
                id = "bl-plan-actions"
                classes = setOf("bl-plan-actions")
                // Filled by JS: Upgrade / Activate now / Cancel / Resume.
            }
        }

        // ── Usage ─────────────────────────────────────────────────────────────
        section("bl-card bl-usage") {
            attributes["aria-labelledby"] = "bl-usage-title"
            div("bl-card-head") {
                h2 { id = "bl-usage-title"; +"Usage this period" }
                span("bl-eyebrow") { id = "bl-usage-period"; +"" }
            }
            div("bl-usage-grid") {
                blUsageMeter(
                    label = "Strings translated",
                    valueId = "bl-usage-strings",
                    barId = "bl-usage-strings-bar",
                    hintId = "bl-usage-strings-hint",
                )
                blUsageMeter(
                    label = "Projects",
                    valueId = "bl-usage-projects",
                    barId = "bl-usage-projects-bar",
                    hintId = "bl-usage-projects-hint",
                )
            }
            // 6-month sparkline filled by JS.
            div("bl-spark") {
                div("bl-spark-head") {
                    div {
                        div("bl-eyebrow") { +"Strings translated · Last 6 months" }
                        div("bl-spark-sub") { +"Monthly translation activity on your account" }
                    }
                    div("bl-spark-stats") { id = "bl-usage-spark-stats" }
                }
                div { id = "bl-usage-spark" }
            }
        }

        // ── Payment method ────────────────────────────────────────────────────
        section("bl-card bl-payment") {
            attributes["aria-labelledby"] = "bl-pay-title"
            div("bl-card-head") {
                h2 { id = "bl-pay-title"; +"Payment method" }
            }
            div("bl-payment-row") {
                div("bl-payment-icon") {
                    unsafe {
                        +"""<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="4" width="22" height="16" rx="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg>"""
                    }
                }
                div("bl-payment-body") {
                    div("bl-payment-title") { +"Managed by Razorpay" }
                    div("bl-payment-sub") {
                        +"Cards, UPI, and net-banking are stored securely with Razorpay. "
                        +"To change a payment method, contact "
                        a(href = "mailto:support@androidplay.in") { +"support@androidplay.in" }
                        +"."
                    }
                }
            }
        }

        // ── Invoice history ───────────────────────────────────────────────────
        section("bl-card bl-invoices") {
            attributes["aria-labelledby"] = "bl-inv-title"
            div("bl-card-head") {
                h2 { id = "bl-inv-title"; +"Invoice history" }
            }
            div("bl-invoice-head") {
                span { +"Date" }
                span { +"Reference" }
                span { +"Amount" }
                span { +"Status" }
                span { attributes["aria-hidden"] = "true" }
            }
            div {
                id = "bl-invoice-list"
                classes = setOf("bl-invoice-list")
                div("bl-invoice-empty") { +"Loading invoices…" }
            }
        }
    }
}

private fun FlowContent.blPlanStat(label: String, valueId: String, emptyValue: String = "—") {
    div("bl-plan-stat") {
        div("bl-eyebrow") { +label }
        div("bl-plan-stat-value") { id = valueId; +emptyValue }
    }
}

private fun FlowContent.blUsageMeter(label: String, valueId: String, barId: String, hintId: String) {
    div("bl-usage-meter") {
        div("bl-usage-meter-top") {
            div("bl-eyebrow") { +label }
            div("bl-usage-meter-value") { id = valueId; +"—" }
        }
        div("bl-usage-bar") {
            div("bl-usage-bar-fill") {
                id = barId
                attributes["style"] = "width:0%"
            }
        }
        div("bl-usage-meter-hint") { id = hintId; +"" }
    }
}
