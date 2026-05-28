package com.transloom.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*

private fun ApplicationCall.issueBootstrapCookie() {
    request.cookies[SESSION_COOKIE]?.ifBlank { null }?.let { tok ->
        response.cookies.append(Cookie(
            name = "tl_token_bootstrap", value = tok,
            path = "/transloom", maxAge = 15,
            httpOnly = false, secure = true, extensions = mapOf("SameSite" to "Lax")
        ))
    }
}

private const val FAVICON_SVG = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">
  <defs>
    <linearGradient id="favG" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#00F5B0"/>
      <stop offset="100%" stop-color="#00A87A"/>
    </linearGradient>
  </defs>
  <rect width="32" height="32" rx="7" fill="url(#favG)"/>
  <path d="M8 11 H24 M16 11 V24" stroke="#0a0a0a" stroke-width="3.5" stroke-linecap="round" fill="none"/>
  <path d="M10 18.5 Q13 16.5 16 18.5 T22 18.5" stroke="#0a0a0a" stroke-width="2" stroke-linecap="round" fill="none" opacity="0.5"/>
</svg>"""

fun Route.configurePortalRoutes(jwtSecret: String) {
    route("/transloom") {
        get {
            if (call.sessionUserId(jwtSecret) != null) {
                call.respondRedirect("/transloom/app")
                return@get
            }
            call.respondHtml { landingPage() }
        }
        get("/app") {
            val pendingPlan = call.request.cookies[PENDING_PLAN_COOKIE]
            val sessionUserId = call.sessionUserId(jwtSecret)

            if (!pendingPlan.isNullOrBlank() && sessionUserId != null) {
                call.respondRedirect("/transloom/billing/checkout?plan=$pendingPlan")
                return@get
            }

            call.issueBootstrapCookie()
            call.respondHtml { dashboardApp() }
        }
        get("/welcome") { call.respondHtml { welcomePage() } }
        get("/billing") {
            call.issueBootstrapCookie()
            call.respondHtml { billingApp() }
        }
        get("/billing/analytics") {
            call.issueBootstrapCookie()
            call.respondHtml { billingAnalyticsApp() }
        }
        get("/projects") {
            call.issueBootstrapCookie()
            call.respondHtml { projectsApp() }
        }
        get("/review-portal") {
            call.issueBootstrapCookie()
            call.respondHtml { reviewPortal() }
        }
        // Members landing — no projectId. Client will pick the first project after
        // it loads /api/projects; if there are none, the server-rendered empty state shows.
        get("/members") {
            call.issueBootstrapCookie()
            call.respondHtml { membersApp(projectId = null) }
        }
        get("/members/{projectId}") {
            call.issueBootstrapCookie()
            call.respondHtml { membersApp(projectId = call.parameters["projectId"]) }
        }
        // Public invite landing — no session required. The page itself decides whether
        // to offer Accept (logged in) or Continue with GitHub (logged out).
        get("/invite/{token}") {
            call.respondHtml { invitePage() }
        }
        get("/favicon.svg") {
            call.respondText(FAVICON_SVG, ContentType("image", "svg+xml"))
        }
    }
}

internal fun HEAD.favicon() {
    link {
        rel = "icon"
        type = "image/svg+xml"
        href = "/transloom/favicon.svg"
    }
}

private const val LOGO_SVG = """
<svg class="brand-mark" viewBox="0 0 32 32" width="26" height="26" aria-hidden="true" focusable="false">
  <defs>
    <linearGradient id="tlmGrad" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
      <stop offset="0%" stop-color="#00F5B0"/>
      <stop offset="100%" stop-color="#00A87A"/>
    </linearGradient>
  </defs>
  <rect x="0" y="0" width="32" height="32" rx="8" fill="url(#tlmGrad)"/>
  <path d="M8.5 10.5 H23.5" stroke="#0a0a0a" stroke-width="2.8" stroke-linecap="round"/>
  <path d="M16 10.5 V23" stroke="#0a0a0a" stroke-width="2.8" stroke-linecap="round"/>
  <path class="weft" d="M10 18.5 Q13 16.5 16 18.5 T22 18.5" stroke="#0a0a0a" stroke-width="2" stroke-linecap="round" fill="none" opacity="0.55"/>
</svg>
"""

internal fun appSidebar(active: String, reviewBadge: Boolean = false) = """
<aside class="sidebar" id="app-sidebar">
  <div class="sidebar-head">
    <div class="sidebar-logo brand">$LOGO_SVG<span class="brand-text">Transloom</span></div>
    <button type="button" class="sidebar-toggle" id="sidebar-toggle" onclick="toggleSidebar()" aria-label="Collapse sidebar" title="Collapse sidebar">
      <svg class="sb-toggle-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
    </button>
  </div>
  <nav class="sidebar-nav">
    <a href="/transloom/app" class="nav-item${if (active=="dash") " active" else ""}" title="Dashboard">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
      <span class="nav-label">Dashboard</span>
    </a>
    <a href="/transloom/projects" class="nav-item${if (active=="projects") " active" else ""}" title="Projects">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
      <span class="nav-label">Projects</span>
    </a>
    <a href="/transloom/members" class="nav-item${if (active=="members") " active" else ""}" title="Members">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
      <span class="nav-label">Members</span>
    </a>
    <a href="/transloom/review-portal" class="nav-item${if (active=="review") " active" else ""}" title="Review">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
      <span class="nav-label">Review</span>${if (reviewBadge) """<span class="nav-badge review-badge" id="review-count"></span>""" else ""}
    </a>
    <a href="/transloom/billing/analytics" class="nav-item${if (active=="analytics") " active" else ""}" title="Analytics">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>
      <span class="nav-label">Analytics</span>
    </a>
    <a href="/transloom/billing" class="nav-item${if (active=="billing") " active" else ""}" title="Billing">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg>
      <span class="nav-label">Billing</span>
    </a>
  </nav>
  <div class="sidebar-footer">
    <div id="sb-quota" class="sb-quota"></div>
    <div class="sidebar-footer-row">
      <div class="user-chip" id="user-chip">
        <div class="user-avatar" id="user-avatar">•</div>
        <div class="user-name" id="user-name">…</div>
      </div>
      <button class="notif-bell" id="notif-bell" onclick="toggleNotifPanel()" aria-label="Notifications" title="Notifications">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
        <span class="notif-badge" id="notif-badge" style="display:none">0</span>
      </button>
    </div>
    <button class="btn btn-ghost logout-btn" onclick="logout()">Sign out</button>
  </div>
</aside>

<div class="notif-overlay" id="notif-overlay" onclick="closeNotifPanel()"></div>
<div class="notif-panel" id="notif-panel">
  <div class="notif-panel-header">
    <div class="notif-panel-header-left">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color:var(--accent)"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
      <span class="notif-panel-title">Notifications</span>
    </div>
    <div class="notif-panel-header-right">
      <button class="notif-mark-all" onclick="markAllNotifsRead()">Mark all read</button>
      <button class="notif-close-btn" onclick="closeNotifPanel()" aria-label="Close">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
      </button>
    </div>
  </div>
  <div class="notif-list" id="notif-list">
    <div class="notif-empty">No notifications yet</div>
  </div>
</div>
"""

private val APP_SIDEBAR_DASH     get() = appSidebar("dash",     reviewBadge = true)
private val APP_SIDEBAR_PROJECTS get() = appSidebar("projects")
private val APP_SIDEBAR_BILLING  get() = appSidebar("billing")
private val APP_SIDEBAR_REVIEW   get() = appSidebar("review")

internal const val SHARED_CSS = """
:root {
  --bg:#080808;--surface:#111;--surface2:#161616;--border:#1f1f1f;
  --accent:#00E5A0;--accent-dim:rgba(0,229,160,.12);--accent-dim2:rgba(0,229,160,.06);
  --text:#f0f0f0;--text-muted:#666;--text-dim:#999;
  --red:#ff4d4f;--yellow:#faad14;--radius:10px;--radius-sm:6px;
}
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
html{scroll-behavior:smooth}
body{background:var(--bg);color:var(--text);font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif;font-size:15px;line-height:1.6;-webkit-font-smoothing:antialiased}
a{color:var(--accent);text-decoration:none}
button,.btn{cursor:pointer;border:none;font-family:inherit;font-size:14px;font-weight:500;border-radius:var(--radius-sm);transition:background-color .2s ease,color .2s ease,transform .2s ease,box-shadow .2s ease,border-color .2s ease}
.btn-primary{background:var(--accent);color:#000;padding:10px 20px;box-shadow:0 1px 0 rgba(0,0,0,.06),0 0 0 0 rgba(0,229,160,.0)}
.btn-primary:hover{background:#00c98d;transform:translateY(-1px);box-shadow:0 8px 24px -8px rgba(0,229,160,.45)}
.btn-ghost{background:transparent;color:var(--text-muted);padding:10px 20px;border:1px solid var(--border)}
.btn-ghost:hover{border-color:var(--accent);color:var(--accent);transform:translateY(-1px)}
.card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:20px;transition:border-color .25s ease,transform .25s ease,box-shadow .25s ease}
.brand{display:inline-flex;align-items:center;gap:10px;font-size:17px;font-weight:700;color:var(--text);letter-spacing:-.2px}
.brand-mark{flex-shrink:0;display:block;filter:drop-shadow(0 2px 8px rgba(0,229,160,.18));transform-origin:center;animation:brandBreath 4.5s ease-in-out infinite;transition:transform .35s cubic-bezier(.2,.8,.2,1),filter .35s ease}
.brand-mark .weft{stroke-dasharray:18;stroke-dashoffset:0;animation:weftFlow 3.6s ease-in-out infinite}
.brand:hover .brand-mark,a:hover>.brand-mark{animation-play-state:paused;transform:scale(1.12) rotate(-8deg);filter:drop-shadow(0 6px 18px rgba(0,229,160,.5))}
@keyframes brandBreath{0%,100%{transform:scale(1)}50%{transform:scale(1.05)}}
@keyframes weftFlow{0%{stroke-dashoffset:18;opacity:.25}50%{stroke-dashoffset:0;opacity:.75}100%{stroke-dashoffset:-18;opacity:.25}}
@media(prefers-reduced-motion:reduce){.brand-mark,.brand-mark .weft{animation:none}}
.fade-up{opacity:0;transform:translateY(18px);transition:opacity .65s cubic-bezier(.2,.7,.2,1),transform .65s cubic-bezier(.2,.7,.2,1)}
.fade-up.in-view{opacity:1;transform:translateY(0)}
.fade-up.d1{transition-delay:.08s}.fade-up.d2{transition-delay:.16s}.fade-up.d3{transition-delay:.24s}.fade-up.d4{transition-delay:.32s}
@media (prefers-reduced-motion:reduce){.fade-up{opacity:1;transform:none;transition:none}*,*::before,*::after{animation-duration:.001ms!important;transition-duration:.001ms!important}}
.badge{display:inline-block;background:var(--accent-dim);color:var(--accent);border:1px solid rgba(0,229,160,.3);border-radius:20px;padding:3px 12px;font-size:12px;font-weight:600;letter-spacing:.5px}
input,select,textarea{background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);color:var(--text);font-family:inherit;font-size:14px;padding:9px 12px;width:100%;outline:none;transition:border-color .15s}
input:focus,select:focus,textarea:focus{border-color:var(--accent)}
label{display:block;font-size:13px;color:var(--text-dim);margin-bottom:5px}
.toast{position:fixed;bottom:24px;right:24px;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:12px 20px;font-size:14px;box-shadow:0 8px 32px rgba(0,0,0,.4);z-index:9999;opacity:0;transform:translateY(8px);transition:all .2s;pointer-events:none}
.toast.show{opacity:1;transform:translateY(0)}
.toast.success{border-color:var(--accent);color:var(--accent)}
.toast.error{border-color:var(--red);color:var(--red)}
.sidebar-footer-row{display:flex;align-items:center;gap:8px}
.sidebar-footer-row .user-chip{flex:1;min-width:0}
.notif-bell{position:relative;display:flex;align-items:center;justify-content:center;width:32px;height:32px;flex-shrink:0;background:transparent;border:1px solid var(--border);border-radius:var(--radius-sm);color:var(--text-muted);cursor:pointer;transition:border-color .18s,color .18s,background .18s}
.notif-bell:hover{border-color:var(--accent);color:var(--accent);background:var(--accent-dim2)}
.notif-bell.has-unread{color:var(--accent);border-color:rgba(0,229,160,.4);background:var(--accent-dim2)}
@keyframes bellRing{0%,100%{transform:rotate(0)}15%{transform:rotate(14deg)}30%{transform:rotate(-11deg)}45%{transform:rotate(8deg)}60%{transform:rotate(-5deg)}75%{transform:rotate(3deg)}}
.notif-bell.ringing svg{animation:bellRing .6s ease forwards}
.notif-badge{position:absolute;top:-6px;right:-6px;min-width:17px;height:17px;background:var(--red);color:#fff;border-radius:9px;font-size:10px;font-weight:700;line-height:17px;text-align:center;padding:0 4px;border:2px solid var(--surface)}
.notif-overlay{position:fixed;inset:0;z-index:1000;background:rgba(0,0,0,.45);backdrop-filter:blur(2px);display:none;opacity:0;transition:opacity .25s}
.notif-overlay.open{display:block;opacity:1}
.notif-panel{position:fixed;top:0;right:0;width:360px;height:100vh;background:var(--surface);border-left:1px solid var(--border);box-shadow:-8px 0 48px rgba(0,0,0,.6);z-index:1001;display:flex;flex-direction:column;transform:translateX(100%);transition:transform .28s cubic-bezier(.2,.8,.2,1)}
.notif-panel.open{transform:translateX(0)}
.notif-panel-header{display:flex;align-items:center;justify-content:space-between;padding:16px 18px;border-bottom:1px solid var(--border);flex-shrink:0}
.notif-panel-header-left{display:flex;align-items:center;gap:8px}
.notif-panel-header-right{display:flex;align-items:center;gap:4px}
.notif-panel-title{font-size:14px;font-weight:700;color:var(--text)}
.notif-mark-all{background:transparent;border:none;color:var(--text-muted);font-size:12px;cursor:pointer;padding:4px 8px;border-radius:4px;transition:color .15s}
.notif-mark-all:hover{color:var(--accent)}
.notif-close-btn{background:transparent;border:none;color:var(--text-muted);cursor:pointer;padding:5px;border-radius:4px;display:flex;align-items:center;justify-content:center;transition:color .15s,background .15s}
.notif-close-btn:hover{color:var(--text);background:var(--surface2)}
.notif-list{overflow-y:auto;flex:1}
.notif-empty{padding:48px 24px;text-align:center;color:var(--text-muted);font-size:13px;line-height:1.6}
.notif-item{padding:14px 18px;border-bottom:1px solid var(--border);cursor:pointer;transition:background .15s;display:flex;gap:12px;align-items:flex-start}
.notif-item:last-child{border-bottom:none}
.notif-item:hover{background:var(--surface2)}
.notif-item.unread{background:rgba(0,229,160,.04)}
.notif-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0;margin-top:4px}
.notif-dot.success{background:var(--accent)}
.notif-dot.warning{background:var(--yellow)}
.notif-dot.error{background:var(--red)}
.notif-dot.info{background:#60a5fa}
.notif-dot.read{background:transparent;border:1.5px solid #333}
.notif-body{flex:1;min-width:0}
.notif-title{font-size:13px;font-weight:600;color:var(--text);line-height:1.4;margin-bottom:3px}
.notif-msg{font-size:12px;color:var(--text-muted);line-height:1.45;margin-bottom:5px;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}
.notif-meta{display:flex;align-items:center;gap:8px}
.notif-time{font-size:11px;color:var(--text-muted)}
.notif-action-link{font-size:11px;color:var(--accent);font-weight:600;white-space:nowrap}
@media(max-width:768px){.notif-panel{width:100%;border-left:none;border-top:1px solid var(--border)}}
"""

private fun FlowContent.stepCard(num: String, title: String, desc: String, extra: String = "") {
    div("step ${extra}".trim()) {
        div("step-num") { +num }
        div("step-body") {
            h3 { +title }
            p { +desc }
        }
    }
}

private fun FlowContent.featureCard(icon: String, title: String, desc: String, extra: String = "") {
    div("feature-card card ${extra}".trim()) {
        div("feature-icon") { unsafe { +icon } }
        h3 { +title }
        p { +desc }
    }
}

private fun FlowContent.statCard(statId: String, label: String, value: String, yellow: Boolean = false) {
    div("stat-card card") {
        p("stat-label") { +label }
        p(if (yellow) "stat-value stat-yellow" else "stat-value") { id = statId; +value }
    }
}

private fun HTML.welcomePage() {
    head {
        title { +"Welcome to Transloom" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        favicon()
        style { unsafe { +"$SHARED_CSS$LANDING_CSS$WELCOME_CSS" } }
    }
    body {
        nav {
            div("nav-inner") {
                a("/transloom") { div("brand") { unsafe { +LOGO_SVG }; span { +"Transloom" } } }
            }
        }

        section("welcome-hero") {
            div("welcome-hero-inner") {
                span("badge") { +"Account created" }
                h1("welcome-title") { +"You're in. Pick your plan." }
                p("welcome-sub") {
                    +"Start free and upgrade whenever you're ready — no card required on the free tier."
                }
            }
        }

        section("pricing-section welcome-pricing") {
            div("section-inner") {
                div("pricing-grid") {
                    div("pricing-card") {
                        p("pricing-name") { +"Free" }
                        p("pricing-price") { +"₹0"; span("price-mo") { +"/mo" } }
                        p("pricing-period") { +"Forever · No credit card needed" }
                        ul("pricing-features") {
                            li { +"500 strings / month" }
                            li { +"1 project" }
                            li { +"3 target languages" }
                            li { +"GitHub webhook" }
                            li { +"AI translation" }
                        }
                        a("/transloom/app") { classes = setOf("pricing-cta", "outline"); +"Continue free →" }
                    }
                    div("pricing-card recommended") {
                        span("rec-badge") { +"Best for Solo Developers" }
                        span("trial-badge") { +"7-day free trial" }
                        p("pricing-name") { +"Solo" }
                        p("pricing-price") { +"₹499"; span("price-mo") { +"/mo" } }
                        p("pricing-period") { +"after trial · Cancel anytime" }
                        ul("pricing-features") {
                            li { +"5,000 strings / month" }
                            li { +"3 projects" }
                            li { +"All target languages" }
                            li { +"Glossary enforcement" }
                            li { +"Translation memory" }
                            li { +"Review portal" }
                        }
                        a("/transloom/billing/start-subscription?plan=SOLO") { classes = setOf("pricing-cta", "accent"); +"Start 7-day free trial" }
                    }
                    div("pricing-card") {
                        span("trial-badge") { +"7-day free trial" }
                        p("pricing-name") { +"Team" }
                        p("pricing-price") { +"₹1,999"; span("price-mo") { +"/mo" } }
                        p("pricing-period") { +"after trial · Cancel anytime" }
                        ul("pricing-features") {
                            li { +"Unlimited strings" }
                            li { +"10 projects" }
                            li { +"All target languages" }
                            li { +"Everything in Solo" }
                            li { +"Priority support" }
                        }
                        a("/transloom/billing/start-subscription?plan=TEAM") { classes = setOf("pricing-cta", "outline"); +"Start 7-day free trial" }
                    }
                }
                p("pricing-note") { +"All paid plans include a 7-day free trial. No charge until the trial ends — cancel any time." }
            }
        }

        script { unsafe { +"""
            const io=new IntersectionObserver((entries)=>{
                entries.forEach(e=>{if(e.isIntersecting){e.target.classList.add('in-view');io.unobserve(e.target);}});
            },{threshold:0.12,rootMargin:'0px 0px -40px 0px'});
            document.querySelectorAll('.fade-up').forEach(el=>io.observe(el));
        """ } }
    }
}

private const val WELCOME_CSS = """
.welcome-hero{padding:64px 24px 16px;text-align:center}
.welcome-hero-inner{max-width:600px;margin:0 auto}
.welcome-title{font-size:clamp(28px,5vw,48px);font-weight:800;line-height:1.15;letter-spacing:-1px;margin:16px 0 12px}
.welcome-sub{color:var(--text-muted);font-size:16px;line-height:1.7}
.welcome-pricing{padding-top:0}
"""

private const val CDN_ARCH_SVG = """
<svg viewBox="0 0 1200 520" xmlns="http://www.w3.org/2000/svg" style="width:100%;max-width:1200px;display:block;margin:0 auto;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif" aria-label="Transloom System Architecture">
  <defs>
    <marker id="arr" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
      <path d="M0,1 L7,4 L0,7 Z" fill="rgba(0,229,160,.65)"/>
    </marker>
    <marker id="arrBypass" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
      <path d="M0,1 L7,4 L0,7 Z" fill="rgba(0,229,160,.3)"/>
    </marker>
    <marker id="arrFan" markerWidth="7" markerHeight="7" refX="6" refY="3.5" orient="auto">
      <path d="M0,1 L6,3.5 L0,6 Z" fill="rgba(0,229,160,.45)"/>
    </marker>
    <marker id="arrEdge" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
      <path d="M0,1 L7,4 L0,7 Z" fill="rgba(110,130,255,.7)"/>
    </marker>
    <marker id="arrSvc" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
      <path d="M0,1 L5,3 L0,5 Z" fill="rgba(255,255,255,.2)"/>
    </marker>
    <filter id="brandGlow" x="-40%" y="-40%" width="180%" height="180%">
      <feGaussianBlur stdDeviation="6" result="blur"/>
      <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
    <filter id="edgeGlow" x="-35%" y="-35%" width="170%" height="170%">
      <feGaussianBlur stdDeviation="5" result="blur"/>
      <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
  </defs>

  <rect x="8" y="20" width="152" height="490" rx="12" fill="rgba(255,255,255,.013)" stroke="#1e1e1e" stroke-width="1"/>
  <rect x="168" y="20" width="776" height="490" rx="12" fill="rgba(0,229,160,.01)" stroke="rgba(0,229,160,.16)" stroke-width="1"/>
  <rect x="952" y="20" width="240" height="490" rx="12" fill="rgba(100,130,255,.013)" stroke="rgba(100,130,255,.2)" stroke-width="1"/>

  <text x="84" y="13" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="2" fill="rgba(255,255,255,.2)">YOUR CODE</text>
  <text x="556" y="13" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="2.5" fill="rgba(0,229,160,.45)">TRANSLOOM PIPELINE</text>
  <text x="1072" y="13" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="2" fill="rgba(110,130,255,.45)">GLOBAL DELIVERY</text>

  <rect x="14" y="82" width="140" height="88" rx="10" fill="#111" stroke="#282828" stroke-width="1.2"/>
  <circle cx="46" cy="126" r="16" fill="#181818" stroke="#2e2e2e" stroke-width="1.2"/>
  <text x="46" y="131" text-anchor="middle" font-size="13" fill="#555">&lt;/&gt;</text>
  <text x="72" y="117" font-size="13" font-weight="600" fill="#c8c8c8">GitHub</text>
  <text x="72" y="133" font-size="11" fill="#454545">push · webhook</text>
  <text x="72" y="150" font-size="10" fill="#333">git push origin main</text>

  <line x1="154" y1="126" x2="188" y2="126" stroke="rgba(0,229,160,.5)" stroke-width="2" stroke-dasharray="5 3" marker-end="url(#arr)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.85s" repeatCount="indefinite"/>
  </line>

  <rect x="190" y="74" width="130" height="104" rx="11" fill="#061612" stroke="rgba(0,229,160,.75)" stroke-width="2" filter="url(#brandGlow)"/>
  <rect x="185" y="69" width="140" height="114" rx="14" fill="none" stroke="rgba(0,229,160,.08)" stroke-width="2.5">
    <animate attributeName="opacity" values="1;0.05;1" dur="3.2s" repeatCount="indefinite"/>
  </rect>
  <circle cx="222" cy="126" r="18" fill="rgba(0,229,160,.1)" stroke="rgba(0,229,160,.6)" stroke-width="1.5"/>
  <line x1="214" y1="119" x2="230" y2="119" stroke="#00E5A0" stroke-width="2.5" stroke-linecap="round"/>
  <line x1="222" y1="119" x2="222" y2="133" stroke="#00E5A0" stroke-width="2.5" stroke-linecap="round"/>
  <text x="249" y="114" font-size="14" font-weight="700" fill="#00E5A0">Transloom</text>
  <text x="249" y="130" font-size="11" fill="rgba(0,229,160,.7)">Hub + Queue</text>
  <text x="249" y="146" font-size="10" fill="rgba(0,229,160,.38)">Redis pub-sub</text>
  <text x="249" y="161" font-size="10" fill="rgba(0,229,160,.25)">SSE live updates</text>

  <line x1="320" y1="126" x2="336" y2="126" stroke="rgba(0,229,160,.5)" stroke-width="2" stroke-dasharray="5 3" marker-end="url(#arr)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.85s" repeatCount="indefinite" begin="0.2s"/>
  </line>

  <rect x="338" y="82" width="118" height="88" rx="10" fill="#111" stroke="rgba(0,229,160,.28)" stroke-width="1.2"/>
  <circle cx="368" cy="126" r="16" fill="rgba(0,229,160,.07)" stroke="rgba(0,229,160,.38)" stroke-width="1.2"/>
  <text x="368" y="131" text-anchor="middle" font-size="15" fill="rgba(0,229,160,.8)">✦</text>
  <text x="394" y="117" font-size="13" font-weight="600" fill="#c8c8c8">Detect Δ</text>
  <text x="394" y="133" font-size="11" fill="#454545">semantic vs</text>
  <text x="394" y="148" font-size="11" fill="#454545">surface change</text>

  <line x1="456" y1="126" x2="472" y2="126" stroke="rgba(0,229,160,.5)" stroke-width="2" stroke-dasharray="5 3" marker-end="url(#arr)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.85s" repeatCount="indefinite" begin="0.4s"/>
  </line>

  <rect x="474" y="82" width="112" height="88" rx="10" fill="#111" stroke="rgba(0,229,160,.22)" stroke-width="1.2"/>
  <circle cx="502" cy="126" r="16" fill="rgba(0,229,160,.06)" stroke="rgba(0,229,160,.32)" stroke-width="1.2"/>
  <path d="M494 120 L510 120 L510 132 L494 132 Z M497 120 L497 117 M507 120 L507 117" fill="none" stroke="rgba(0,229,160,.65)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
  <text x="528" y="117" font-size="13" font-weight="600" fill="#c8c8c8">Billing ✓</text>
  <text x="528" y="133" font-size="11" fill="#454545">plan limits</text>
  <text x="528" y="148" font-size="11" fill="#454545">quota check</text>

  <line x1="586" y1="126" x2="602" y2="126" stroke="rgba(0,229,160,.5)" stroke-width="2" stroke-dasharray="5 3" marker-end="url(#arr)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.85s" repeatCount="indefinite" begin="0.6s"/>
  </line>

  <rect x="604" y="82" width="122" height="88" rx="10" fill="#111" stroke="rgba(0,229,160,.28)" stroke-width="1.2"/>
  <circle cx="634" cy="126" r="16" fill="rgba(0,229,160,.07)" stroke="rgba(0,229,160,.38)" stroke-width="1.2"/>
  <text x="634" y="131" text-anchor="middle" font-size="15" fill="rgba(0,229,160,.85)">◆</text>
  <text x="660" y="117" font-size="13" font-weight="600" fill="#c8c8c8">AI Translate</text>
  <text x="660" y="133" font-size="11" fill="#454545">Gemini Flash</text>
  <text x="660" y="148" font-size="11" fill="#454545">batch · 20+ langs</text>

  <line x1="726" y1="126" x2="742" y2="126" stroke="rgba(0,229,160,.5)" stroke-width="2" stroke-dasharray="5 3" marker-end="url(#arr)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.85s" repeatCount="indefinite" begin="0.8s"/>
  </line>

  <rect x="744" y="82" width="112" height="88" rx="10" fill="#111" stroke="rgba(0,229,160,.28)" stroke-width="1.2"/>
  <circle cx="773" cy="126" r="16" fill="rgba(0,229,160,.07)" stroke="rgba(0,229,160,.38)" stroke-width="1.2"/>
  <path d="M764 126 Q773 118 782 126 Q773 134 764 126 Z M773 126 m-2.5,0 a2.5,2.5 0 1,1 5,0 a2.5,2.5 0 1,1 -5,0" fill="none" stroke="rgba(0,229,160,.75)" stroke-width="1.6"/>
  <text x="799" y="117" font-size="13" font-weight="600" fill="#c8c8c8">Review</text>
  <text x="799" y="133" font-size="11" fill="#454545">cultural check</text>
  <text x="799" y="148" font-size="11" fill="#454545">approve · lock</text>

  <line x1="856" y1="126" x2="874" y2="126" stroke="rgba(0,229,160,.5)" stroke-width="2" stroke-dasharray="5 3" marker-end="url(#arr)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.85s" repeatCount="indefinite" begin="1s"/>
  </line>

  <rect x="876" y="82" width="68" height="88" rx="10" fill="#0a1210" stroke="rgba(0,229,160,.55)" stroke-width="1.8"/>
  <text x="910" y="114" text-anchor="middle" font-size="17" fill="rgba(0,229,160,.9)">↑</text>
  <text x="910" y="134" text-anchor="middle" font-size="11" font-weight="700" fill="#c8c8c8">CDN</text>
  <text x="910" y="149" text-anchor="middle" font-size="10" fill="rgba(0,229,160,.6)">Publish</text>
  <text x="910" y="162" text-anchor="middle" font-size="9.5" fill="#333">~45s</text>

  <line x1="944" y1="126" x2="960" y2="126" stroke="rgba(0,229,160,.7)" stroke-width="2.2" stroke-dasharray="5 3" marker-end="url(#arrEdge)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.75s" repeatCount="indefinite" begin="1.15s"/>
  </line>

  <rect x="192" y="278" width="120" height="72" rx="9" fill="#0e1008" stroke="rgba(87,180,90,.35)" stroke-width="1.2"/>
  <circle cx="218" cy="314" r="13" fill="rgba(87,180,90,.08)" stroke="rgba(87,180,90,.4)" stroke-width="1.2"/>
  <ellipse cx="218" cy="308" rx="8" ry="4" fill="none" stroke="rgba(87,180,90,.6)" stroke-width="1.2"/>
  <rect x="210" y="308" width="16" height="8" fill="none" stroke="rgba(87,180,90,.4)" stroke-width="1"/>
  <ellipse cx="218" cy="316" rx="8" ry="4" fill="none" stroke="rgba(87,180,90,.4)" stroke-width="1"/>
  <text x="242" y="307" font-size="12" font-weight="600" fill="#aaa">MongoDB</text>
  <text x="242" y="321" font-size="10.5" fill="#444">translations</text>
  <text x="242" y="334" font-size="10.5" fill="#444">projects · users</text>

  <rect x="330" y="278" width="112" height="72" rx="9" fill="#0e0a08" stroke="rgba(255,100,80,.3)" stroke-width="1.2"/>
  <circle cx="356" cy="314" r="13" fill="rgba(255,80,60,.06)" stroke="rgba(255,80,60,.4)" stroke-width="1.2"/>
  <path d="M350 318 L356 308 L362 318 Z M356 308 L356 320" fill="none" stroke="rgba(255,80,60,.7)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
  <text x="380" y="307" font-size="12" font-weight="600" fill="#aaa">Redis</text>
  <text x="380" y="321" font-size="10.5" fill="#444">job queue</text>
  <text x="380" y="334" font-size="10.5" fill="#444">pub-sub SSE</text>

  <rect x="604" y="278" width="122" height="72" rx="9" fill="#0a0c16" stroke="rgba(130,120,255,.32)" stroke-width="1.2"/>
  <circle cx="632" cy="314" r="13" fill="rgba(120,110,255,.07)" stroke="rgba(130,120,255,.45)" stroke-width="1.2"/>
  <text x="632" y="318" text-anchor="middle" font-size="14" fill="rgba(130,120,255,.85)">✦</text>
  <text x="656" y="307" font-size="12" font-weight="600" fill="#aaa">Gemini API</text>
  <text x="656" y="321" font-size="10.5" fill="#444">translation</text>
  <text x="656" y="334" font-size="10.5" fill="#444">semantic detect</text>

  <rect x="744" y="278" width="112" height="72" rx="9" fill="#0c0c0e" stroke="rgba(160,160,180,.22)" stroke-width="1.2"/>
  <circle cx="770" cy="314" r="13" fill="rgba(160,160,180,.05)" stroke="rgba(160,160,180,.3)" stroke-width="1.2"/>
  <text x="770" y="319" text-anchor="middle" font-size="11" fill="rgba(160,160,180,.65)">&lt;/&gt;</text>
  <text x="794" y="307" font-size="12" font-weight="600" fill="#aaa">GitHub API</text>
  <text x="794" y="321" font-size="10.5" fill="#444">create PR</text>
  <text x="794" y="334" font-size="10.5" fill="#444">file commits</text>

  <line x1="255" y1="178" x2="252" y2="278" stroke="rgba(87,180,90,.28)" stroke-width="1.2" stroke-dasharray="5 4" marker-end="url(#arrSvc)"/>
  <line x1="290" y1="178" x2="380" y2="278" stroke="rgba(255,80,60,.22)" stroke-width="1.2" stroke-dasharray="5 4" marker-end="url(#arrSvc)"/>
  <line x1="644" y1="170" x2="644" y2="278" stroke="rgba(130,120,255,.45)" stroke-width="1.6" stroke-dasharray="5 3" marker-end="url(#arrSvc)"/>
  <line x1="778" y1="170" x2="778" y2="278" stroke="rgba(160,160,180,.28)" stroke-width="1.2" stroke-dasharray="5 4" marker-end="url(#arrSvc)"/>

  <rect x="960" y="70" width="216" height="116" rx="12" fill="#080818" stroke="rgba(110,130,255,.55)" stroke-width="2" filter="url(#edgeGlow)"/>
  <rect x="955" y="65" width="226" height="126" rx="15" fill="none" stroke="rgba(110,130,255,.07)" stroke-width="2.5">
    <animate attributeName="opacity" values="1;0.05;1" dur="3s" repeatCount="indefinite"/>
  </rect>
  <circle cx="994" cy="128" r="20" fill="none" stroke="rgba(110,130,255,.55)" stroke-width="1.5"/>
  <ellipse cx="994" cy="128" rx="9" ry="20" fill="none" stroke="rgba(110,130,255,.3)" stroke-width="1.2"/>
  <line x1="974" y1="128" x2="1014" y2="128" stroke="rgba(110,130,255,.35)" stroke-width="1.2"/>
  <line x1="975" y1="116" x2="1013" y2="116" stroke="rgba(110,130,255,.2)" stroke-width="1"/>
  <line x1="975" y1="140" x2="1013" y2="140" stroke="rgba(110,130,255,.2)" stroke-width="1"/>
  <text x="1025" y="112" font-size="14" font-weight="700" fill="#9aa8f0">Cloudflare</text>
  <text x="1025" y="130" font-size="14" font-weight="700" fill="#9aa8f0">Global KV</text>
  <text x="1025" y="147" font-size="11" fill="rgba(110,130,255,.6)">250+ edge PoPs</text>
  <text x="1025" y="162" font-size="11" fill="rgba(110,130,255,.38)">worldwide &lt;20ms</text>

  <path d="M 1000,186 L 1000,210" fill="none" stroke="rgba(0,229,160,.45)" stroke-width="1.8" stroke-dasharray="4 3" marker-end="url(#arrFan)">
    <animate attributeName="stroke-dashoffset" from="0" to="-14" dur="0.9s" repeatCount="indefinite"/>
  </path>
  <path d="M 1120,186 L 1120,210" fill="none" stroke="rgba(0,229,160,.45)" stroke-width="1.8" stroke-dasharray="4 3" marker-end="url(#arrFan)">
    <animate attributeName="stroke-dashoffset" from="0" to="-14" dur="0.9s" repeatCount="indefinite" begin="0.3s"/>
  </path>
  <path d="M 1062,186 L 1062,350" fill="none" stroke="rgba(0,229,160,.3)" stroke-width="1.4" stroke-dasharray="4 3" marker-end="url(#arrFan)">
    <animate attributeName="stroke-dashoffset" from="0" to="-14" dur="1.1s" repeatCount="indefinite" begin="0.15s"/>
  </path>

  <rect x="960" y="210" width="108" height="64" rx="9" fill="#091209" stroke="rgba(61,220,132,.35)" stroke-width="1.5"/>
  <circle cx="984" cy="242" r="14" fill="rgba(61,220,132,.07)" stroke="rgba(61,220,132,.4)" stroke-width="1.2"/>
  <path d="M979 238 L979 247 M989 238 L989 247 M976 238 Q984 230 992 238" fill="none" stroke="rgba(61,220,132,.75)" stroke-width="1.6" stroke-linecap="round"/>
  <text x="1009" y="236" font-size="12" font-weight="700" fill="#3DDC84">Android</text>
  <text x="1009" y="251" font-size="10.5" fill="rgba(61,220,132,.6)">SDK · KMP</text>
  <text x="1009" y="265" font-size="10" fill="#333">&lt;20 ms</text>

  <rect x="1082" y="210" width="104" height="64" rx="9" fill="#0b0b18" stroke="rgba(160,160,220,.3)" stroke-width="1.5"/>
  <circle cx="1104" cy="242" r="14" fill="rgba(160,160,220,.06)" stroke="rgba(160,160,220,.38)" stroke-width="1.2"/>
  <path d="M1098 245 Q1100 235 1108 236 Q1113 241 1109 248 L1098 245 Z M1104 234 Q1105 230 1109 231" fill="none" stroke="rgba(160,160,220,.7)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
  <text x="1128" y="236" font-size="12" font-weight="700" fill="#a8b0e0">iOS</text>
  <text x="1128" y="251" font-size="10.5" fill="rgba(160,160,220,.55)">Swift SDK</text>
  <text x="1128" y="265" font-size="10" fill="#333">&lt;20 ms</text>

  <rect x="1016" y="350" width="100" height="56" rx="9" fill="#0c0c0c" stroke="rgba(255,200,80,.28)" stroke-width="1.3"/>
  <circle cx="1038" cy="378" r="12" fill="rgba(255,200,80,.06)" stroke="rgba(255,200,80,.38)" stroke-width="1.2"/>
  <text x="1038" y="383" text-anchor="middle" font-size="12" fill="rgba(255,200,80,.75)">JS</text>
  <text x="1060" y="371" font-size="12" font-weight="700" fill="#c8c072">Web/JS</text>
  <text x="1060" y="385" font-size="10.5" fill="rgba(255,200,80,.5)">REST fetch</text>
  <text x="1060" y="398" font-size="10" fill="#333">&lt;20 ms</text>

  <path d="M 397,170 L 397,246 L 900,246 L 900,170" fill="none" stroke="rgba(0,229,160,.2)" stroke-width="1.4" stroke-dasharray="7 5" marker-end="url(#arrBypass)">
    <animate attributeName="stroke-dashoffset" from="0" to="-24" dur="2.4s" repeatCount="indefinite"/>
  </path>
  <rect x="550" y="237" width="194" height="18" rx="4" fill="#090909" stroke="rgba(0,229,160,.1)" stroke-width="0.8"/>
  <text x="647" y="250" text-anchor="middle" font-size="10.5" fill="rgba(0,229,160,.35)">surface change only → skip translation</text>

  <path d="M 800,170 Q 820,220 800,278" fill="none" stroke="rgba(160,160,180,.2)" stroke-width="1.2" stroke-dasharray="5 4" marker-end="url(#arrSvc)"/>

  <text x="84"  y="506" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="1" fill="#2e2e2e">INGEST</text>
  <text x="256" y="506" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="1" fill="rgba(0,229,160,.35)">PROCESS</text>
  <text x="398" y="506" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="1" fill="#2e2e2e">DETECT</text>
  <text x="530" y="506" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="1" fill="#2e2e2e">BILLING</text>
  <text x="666" y="506" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="1" fill="#2e2e2e">TRANSLATE</text>
  <text x="800" y="506" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="1" fill="#2e2e2e">REVIEW</text>
  <text x="910" y="506" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="1" fill="#2e2e2e">PUBLISH</text>
  <text x="1072" y="506" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="1" fill="rgba(110,130,255,.38)">DELIVER</text>
</svg>
"""

private const val LANDING_JS = """
(function(){
  var params=new URLSearchParams(window.location.search);
  if(params.get('billing_error')==='link_failed'){
    var sub=params.get('sub')||'';
    var banner=document.createElement('div');
    banner.style.cssText='position:fixed;top:0;left:0;right:0;background:#3a1a1a;border-bottom:1px solid #ff4d4f;color:#ffb8b8;padding:14px 24px;text-align:center;font-size:14px;z-index:9999;line-height:1.5';
    banner.innerHTML='<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="display:inline;vertical-align:-3px;margin-right:6px"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>We received your payment but couldn'+String.fromCharCode(39)+'t link it to your account. Please email <a href="mailto:support@androidplay.in?subject=Subscription%20'+sub+'" style="color:#fff;text-decoration:underline">support@androidplay.in</a> with reference: <code style="background:rgba(255,255,255,.1);padding:2px 6px;border-radius:3px">'+sub+'</code>';
    document.body.prepend(banner);
    history.replaceState({},'','/transloom');
  } else {
    var t=localStorage.getItem('transloom_token');if(t){window.location.href='/transloom/app';}
  }

  var io=new IntersectionObserver(function(entries){
    entries.forEach(function(e){if(e.isIntersecting){e.target.classList.add('in-view');io.unobserve(e.target);}});
  },{threshold:0.1,rootMargin:'0px 0px -40px 0px'});
  document.querySelectorAll('.fade-up').forEach(function(el){io.observe(el);});

  var navToggle=document.getElementById('nav-toggle');
  var navMenu=document.getElementById('nav-menu');
  if(navToggle&&navMenu){
    navToggle.addEventListener('click',function(){
      var open=navMenu.classList.toggle('open');
      navToggle.setAttribute('aria-expanded',String(open));
      navToggle.innerHTML=open
        ?'<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>'
        :'<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>';
    });
    navMenu.querySelectorAll('a').forEach(function(a){
      a.addEventListener('click',function(){
        navMenu.classList.remove('open');
        navToggle.setAttribute('aria-expanded','false');
        navToggle.innerHTML='<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>';
      });
    });
  }
})();
"""

private fun HTML.landingPage() {
    head {
        title { +"Transloom — Build once. Deliver in every language, from the edge." }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "description", content = "Push a commit. Transloom translates your strings, publishes them to a global CDN, and serves them to your users in under 20ms — via native Android and iOS SDKs.")
        favicon()
        style { unsafe { +"$SHARED_CSS$LANDING_CSS" } }
    }
    body {
        nav {
            div("nav-inner") {
                div("brand") { unsafe { +LOGO_SVG }; span { +"Transloom" } }
                button {
                    id = "nav-toggle"
                    classes = setOf("nav-hamburger")
                    attributes["aria-label"] = "Open menu"
                    attributes["aria-expanded"] = "false"
                    unsafe { +"""<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>""" }
                }
                div("nav-links") {
                    id = "nav-menu"
                    a("#architecture") { +"Architecture" }
                    a("#features") { +"Features" }
                    a("#pricing") { +"Pricing" }
                    a("/transloom/auth/github") { classes = setOf("btn", "btn-ghost", "nav-cta"); +"Login" }
                    a("#pricing") { classes = setOf("btn", "btn-primary", "nav-cta"); +"Get started" }
                }
            }
        }

        section("hero") {
            div("hero-glow") {}
            div("hero-glow2") {}
            div("hero-inner") {
                span("badge fade-up") { +"Production CDN · SDK Beta · Free to start" }
                h1("hero-title fade-up d1") {
                    +"Build once. Deliver in "; span("accent") { +"every language" }; +", from the edge."
                }
                p("hero-sub fade-up d2") {
                    +"Push a commit. Transloom translates your strings, publishes them to a global CDN, and serves them to your users in under 20ms — via native Android and iOS SDKs."
                }
                div("hero-actions fade-up d3") {
                    a("#pricing") { classes = setOf("btn", "btn-primary", "hero-btn"); +"Get started free" }
                    a("#architecture") { classes = setOf("btn", "btn-ghost"); +"See the architecture →" }
                }
                div("hero-stats fade-up d4") {
                    div("hero-stat") { span("hero-stat-val") { +"<20ms" }; span("hero-stat-label") { +"Edge delivery" } }
                    div("hero-stat-div") {}
                    div("hero-stat") { span("hero-stat-val") { +"10+" }; span("hero-stat-label") { +"Languages" } }
                    div("hero-stat-div") {}
                    div("hero-stat") { span("hero-stat-val") { +"2" }; span("hero-stat-label") { +"Native SDKs" } }
                    div("hero-stat-div") {}
                    div("hero-stat") { span("hero-stat-val") { +"~45s" }; span("hero-stat-label") { +"Commit to CDN" } }
                }
                div("hero-lang-strip fade-up") {
                    listOf("🇪🇸 ES","🇫🇷 FR","🇩🇪 DE","🇯🇵 JA","🇰🇷 KO","🇨🇳 ZH","🇮🇳 HI","🇧🇷 PT","🇮🇹 IT","🇸🇦 AR")
                        .forEach { span("lang-chip") { +it } }
                }
            }
        }

        section("arch-section") {
            id = "architecture"
            div("section-inner") {
                p("section-label fade-up") { +"TRANSLOOM ARCHITECTURE" }
                h2("fade-up d1") { +"How Transloom takes you from commit to global delivery." }
                p("arch-sub fade-up d2") { +"Transloom listens to your GitHub pushes, runs AI-powered semantic detection, translates with Claude, validates every string, and publishes to Cloudflare's global KV — so your app serves any language in under 20ms." }
                div("arch-diagram fade-up d3") { unsafe { +CDN_ARCH_SVG } }
            }
        }

        section("sdk-section") {
            id = "sdk"
            div("section-inner") {
                p("section-label fade-up") { +"NATIVE SDKs" }
                h2("fade-up d1") { +"Your app. Our translations."; br {}; +"Zero networking code." }
                p("arch-sub fade-up d2") { +"Drop in our SDK and get live translations served from the nearest edge node. No REST calls to write. No caching logic to manage." }
                div("sdk-grid") {
                    div("sdk-card fade-up d2") {
                        div("sdk-card-top") {
                            div("sdk-icon sdk-android") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>""" }
                            }
                            div {
                                p("sdk-name") { +"Android SDK" }
                                span("sdk-status sdk-status-prod") { +"In production" }
                            }
                        }
                        p("sdk-desc") { +"One-line init. Automatic string resolution. Works with your existing strings.xml. Offline-first with smart cache invalidation." }
                        div("sdk-code-block") {
                            unsafe { +"""<pre class="sdk-code"><span class="sdk-comment">// build.gradle.kts</span>
implementation(<span class="sdk-str">"com.transloom:android-sdk:1.0.0"</span>)

<span class="sdk-comment">// Application.kt</span>
Transloom.init(this, apiKey = <span class="sdk-str">"YOUR_API_KEY"</span>)

<span class="sdk-comment">// Usage</span>
val title = Transloom.string(<span class="sdk-str">"onboarding_welcome_title"</span>)</pre>""" }
                        }
                        div("sdk-footer") {
                            span("sdk-release-badge") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>""" }
                                +"Releasing publicly soon"
                            }
                        }
                    }
                    div("sdk-card sdk-ios fade-up d3") {
                        div("sdk-card-top") {
                            div("sdk-icon sdk-ios-icon") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-13h2v6h-2zm0 8h2v2h-2z"/></svg>""" }
                            }
                            div {
                                p("sdk-name") { +"iOS SDK" }
                                span("sdk-status sdk-status-prod") { +"In production" }
                            }
                        }
                        p("sdk-desc") { +"Swift-native. SwiftUI and UIKit compatible. Fetches from Cloudflare edge on first launch, caches intelligently. Zero dependencies." }
                        div("sdk-code-block") {
                            unsafe { +"""<pre class="sdk-code"><span class="sdk-comment">// Package.swift</span>
.package(url: <span class="sdk-str">"https://github.com/transloom/ios-sdk"</span>, from: <span class="sdk-str">"1.0.0"</span>)

<span class="sdk-comment">// AppDelegate.swift</span>
Transloom.configure(apiKey: <span class="sdk-str">"YOUR_API_KEY"</span>)

<span class="sdk-comment">// SwiftUI</span>
Text(Transloom.string(<span class="sdk-str">"onboarding_welcome_title"</span>))</pre>""" }
                        }
                        div("sdk-footer") {
                            span("sdk-release-badge") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>""" }
                                +"Releasing publicly soon"
                            }
                        }
                    }
                }
                div("sdk-bottom") {
                    div("sdk-compat") {
                        unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>""" }
                        +"Both SDKs are live in internal production — public release coming imminently"
                    }
                }
            }
        }

        section("how-section") {
            id = "how"
            div("section-inner") {
                p("section-label fade-up") { +"HOW IT WORKS" }
                h2("fade-up d1") { +"Five steps. Fully automatic." }
                p("how-sub fade-up d2") { +"From your git push to a globally-served, edge-cached translation — nothing to configure." }

                div("how-steps fade-up d3") {
                    data class Step(val num: String, val icon: String, val title: String, val desc: String, val tag: String)
                    listOf(
                        Step("01", """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>""", "Push to GitHub", "Add a new string to your strings.xml or .strings file and push. Transloom's webhook fires within seconds.", "Webhook"),
                        Step("02", """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3c-1 3-2 4-5 5 3 1 4 2 5 5 1-3 2-4 5-5-3-1-4-2-5-5z"/><path d="M5 10.5c-.5 1.5-1 2-2.5 2.5 1.5.5 2 1 2.5 2.5.5-1.5 1-2 2.5-2.5-1.5-.5-2-1-2.5-2.5z"/></svg>""", "AI detects changes", "Our semantic classifier decides if strings need retranslation or just a surface update. Only real changes bill API quota.", "Smart diff"),
                        Step("03", """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>""", "Translate + validate", "Claude translates into every target language. Placeholders are guarded, glossary terms enforced, cultural flags checked.", "AI pipeline"),
                        Step("04", """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>""", "Publish to CDN", "Translations are compiled into a versioned bundle and written to Cloudflare KV — replicated to 250+ PoPs instantly.", "~45 sec total"),
                        Step("05", """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>""", "SDK serves from edge", "Your Android or iOS SDK fetches from the nearest PoP. First hit is cached locally — subsequent calls are instant.", "<20ms")
                    ).forEach { s ->
                        div("how-step-card") {
                            div("hsc-left") {
                                span("hsc-num") { +s.num }
                                div("hsc-line") {}
                            }
                            div("hsc-body") {
                                div("hsc-head") {
                                    div("hsc-icon") { unsafe { +s.icon } }
                                    h4("hsc-title") { +s.title }
                                    span("hsc-tag") { +s.tag }
                                }
                                p("hsc-desc") { +s.desc }
                            }
                        }
                    }
                }

                div("how-footer fade-up") {
                    div("time-pill") {
                        span("time-dot") {}
                        +"Commit to CDN: "
                        strong { +"~45 seconds" }
                        span("time-sep") { +"·" }
                        +"Edge response: "
                        strong { +"<20ms" }
                    }
                }
            }
        }

        section("features-section") {
            id = "features"
            div("section-inner") {
                p("section-label fade-up") { +"FEATURES" }
                h2("fade-up d1") { +"Everything you need to ship globally." }
                div("features-grid") {
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>""","Global CDN delivery","Translations compiled and pushed to Cloudflare's global KV. Served from the nearest PoP to your user.","fade-up")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><polyline points="21 3 21 8 16 8"/></svg>""","Instant OTA updates","Fix a typo in any language without a new app release. Every publish is versioned — promote or roll back in one click.","fade-up d1")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>""","Android + iOS SDKs","Native SDKs in production. Drop-in init, edge delivery, offline cache — no networking code to write.","fade-up d1")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3c-1 3-2 4-5 5 3 1 4 2 5 5 1-3 2-4 5-5-3-1-4-2-5-5z"/><path d="M5.5 10.5c-.5 1.5-1 2-2.5 2.5 1.5.5 2 1 2.5 2.5.5-1.5 1-2 2.5-2.5-1.5-.5-2-1-2.5-2.5z"/><path d="M18.5 5c-.3 1-.7 1.3-1.5 1.5.8.2 1.2.5 1.5 1.5.3-1 .7-1.3 1.5-1.5-.8-.2-1.2-.5-1.5-1.5z"/></svg>""","Smart change detection","AI classifier skips retranslation for surface rewrites. Only semantic changes trigger the pipeline.","fade-up d2")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>""","Placeholder guard","Automatic detection of %1\$s, %d, %@ — bad translations are blocked before they reach the CDN.","fade-up d3")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>""","Glossary enforcement","Brand terms defined once per language, applied consistently across every string, every CDN publish.","fade-up d4")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>""","Review portal","Flag anomalous translations for human review before they hit the CDN and go live globally.","fade-up d4")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>""","Translation memory","Identical strings reuse cached translations — faster throughput, lower API cost, instant CDN updates.","fade-up d4")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>""","Cultural sensitivity","Post-translation AI check flags formality mismatches and idiom issues before strings publish to CDN.","fade-up d4")
                }
            }
        }

        section("pricing-section") {
            id = "pricing"
            div("section-inner") {
                p("section-label fade-up") { +"PRICING" }
                h2("fade-up d1") { +"Simple, transparent pricing." }
                div("pricing-grid") {
                    div("pricing-card fade-up") {
                        p("pricing-name") { +"Free" }
                        p("pricing-price") { +"₹0"; span("price-mo") { +"/mo" } }
                        p("pricing-period") { +"Forever · No credit card needed" }
                        ul("pricing-features") {
                            li { +"500 strings / month" }
                            li { +"1 project" }
                            li { +"3 target languages" }
                            li { +"GitHub webhook" }
                            li { +"AI translation" }
                            li { +"CDN delivery" }
                        }
                        a("/transloom/auth/github") { classes = setOf("pricing-cta", "outline"); +"Get started free" }
                    }
                    div("pricing-card recommended fade-up d1") {
                        span("rec-badge") { +"Best for Solo Developers" }
                        span("trial-badge") { +"7-day free trial" }
                        p("pricing-name") { +"Solo" }
                        p("pricing-price") { +"₹499"; span("price-mo") { +"/mo" } }
                        p("pricing-period") { +"after trial · Cancel anytime" }
                        ul("pricing-features") {
                            li { +"5,000 strings / month" }
                            li { +"3 projects" }
                            li { +"All target languages" }
                            li { +"Glossary enforcement" }
                            li { +"Translation memory" }
                            li { +"Review portal" }
                            li { +"CDN + SDK access" }
                        }
                        a("/transloom/billing/start-subscription?plan=SOLO") { classes = setOf("pricing-cta", "accent"); +"Start 7-day free trial" }
                    }
                    div("pricing-card fade-up d2") {
                        span("trial-badge") { +"7-day free trial" }
                        p("pricing-name") { +"Team" }
                        p("pricing-price") { +"₹1,999"; span("price-mo") { +"/mo" } }
                        p("pricing-period") { +"after trial · Cancel anytime" }
                        ul("pricing-features") {
                            li { +"Unlimited strings" }
                            li { +"10 projects" }
                            li { +"All target languages" }
                            li { +"Everything in Solo" }
                            li { +"Priority support" }
                            li { +"Priority CDN SLA" }
                        }
                        a("/transloom/billing/start-subscription?plan=TEAM") { classes = setOf("pricing-cta", "outline"); +"Start 7-day free trial" }
                    }
                }
                p("pricing-note") { +"All paid plans include a 7-day free trial. No charge until the trial ends — cancel any time." }
            }
        }

        section("cta-section") {
            div("cta-inner") {
                h2("fade-up") { +"Ready to go global?" }
                p("fade-up d1") { +"Free tier includes 500 strings/month, CDN delivery, and SDK access." }
                a("/transloom/auth/github") { classes = setOf("btn","btn-primary","cta-btn","fade-up","d2"); +"Get started free" }
            }
        }

        footer {
            div("footer-inner") {
                div("brand") { unsafe { +LOGO_SVG }; span { +"Transloom" } }
                span("text-muted") { +"© 2026 · Built for developers who ship globally." }
            }
        }

        script { unsafe { +LANDING_JS } }
    }
}

private const val LANDING_CSS = """
@keyframes heroDrift{0%,100%{transform:translate(-50%,0) scale(1)}50%{transform:translate(-50%,30px) scale(1.08)}}
@keyframes livePulse{0%,100%{opacity:1;transform:scale(1)}50%{opacity:.5;transform:scale(.85)}}
nav{position:sticky;top:0;z-index:100;background:rgba(8,8,8,.85);backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);border-bottom:1px solid var(--border)}
.nav-inner{max-width:1140px;margin:0 auto;padding:14px 24px;display:flex;align-items:center;justify-content:space-between;gap:16px}
.nav-links{display:flex;align-items:center;gap:24px}
.nav-links a:not(.btn){color:var(--text-muted);font-size:14px;transition:color .2s ease}
.nav-links a:not(.btn):hover{color:var(--text)}
.nav-cta{padding:8px 16px!important;font-size:13px!important}
.nav-hamburger{display:none;align-items:center;justify-content:center;background:transparent;border:1px solid var(--border);border-radius:6px;color:var(--text-muted);width:36px;height:36px;padding:0;flex-shrink:0;transition:border-color .2s,color .2s;cursor:pointer}
.nav-hamburger:hover{border-color:var(--accent);color:var(--accent)}
.hero{position:relative;overflow:hidden;padding:96px 24px 72px;text-align:center}
.hero-glow{position:absolute;top:-200px;left:50%;transform:translateX(-50%);width:680px;height:680px;background:radial-gradient(circle,rgba(0,229,160,.14) 0%,transparent 70%);pointer-events:none;animation:heroDrift 12s ease-in-out infinite}
.hero-glow2{position:absolute;top:100px;right:-200px;width:500px;height:500px;background:radial-gradient(circle,rgba(0,229,160,.06) 0%,transparent 70%);pointer-events:none}
.hero-inner{max-width:780px;margin:0 auto;position:relative}
.hero-title{font-size:clamp(34px,6vw,62px);font-weight:800;line-height:1.1;letter-spacing:-1.5px;margin:20px 0}
.accent{color:var(--accent)}
.hero-sub{color:var(--text-muted);font-size:clamp(15px,2vw,17px);line-height:1.7;margin-bottom:36px}
.hero-actions{display:flex;gap:12px;justify-content:center;flex-wrap:wrap;margin-bottom:40px}
.hero-btn{padding:13px 24px;font-size:15px}
.hero-stats{display:flex;align-items:center;justify-content:center;gap:0;flex-wrap:wrap;margin-bottom:40px;background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:16px 24px;max-width:640px;margin-left:auto;margin-right:auto}
.hero-stat{display:flex;flex-direction:column;align-items:center;padding:0 20px}
.hero-stat-val{font-size:22px;font-weight:800;color:var(--accent);line-height:1.2}
.hero-stat-label{font-size:12px;color:var(--text-muted);margin-top:2px}
.hero-stat-div{width:1px;height:36px;background:var(--border);flex-shrink:0}
.hero-lang-strip{display:flex;flex-wrap:wrap;gap:8px;justify-content:center}
.lang-chip{background:var(--surface);border:1px solid var(--border);border-radius:20px;padding:4px 12px;font-size:13px;color:var(--text-dim);transition:border-color .2s ease,color .2s ease,transform .2s ease}
.lang-chip:hover{border-color:var(--accent);color:var(--accent);transform:translateY(-1px)}
section{padding:80px 24px}
.section-inner{max-width:1140px;margin:0 auto}
.section-label{font-size:11px;font-weight:700;letter-spacing:2px;color:var(--accent);margin-bottom:12px}
h2{font-size:clamp(24px,4vw,40px);font-weight:700;letter-spacing:-.5px;margin-bottom:48px}
.arch-section{padding:80px 24px;background:var(--bg);border-top:1px solid var(--border)}
.arch-diagram{max-width:1200px;margin:0 auto;overflow-x:auto;background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:36px 28px}
.arch-sub{color:var(--text-muted);font-size:16px;margin-top:-28px;margin-bottom:48px;max-width:640px;line-height:1.6}
.how-section{padding:88px 24px 100px}
.how-sub{color:var(--text-muted);font-size:16px;margin-top:-28px;margin-bottom:48px;max-width:580px;line-height:1.6}
.how-steps{display:flex;flex-direction:column;gap:0;max-width:760px;margin:0 auto 64px;border:1px solid var(--border);border-radius:14px;overflow:hidden;background:var(--surface)}
.how-step-card{display:flex;gap:0;border-bottom:1px solid var(--border);transition:background .2s}
.how-step-card:last-child{border-bottom:none}
.how-step-card:hover{background:var(--surface2)}
.hsc-left{display:flex;flex-direction:column;align-items:center;padding:24px 20px;min-width:64px;border-right:1px solid var(--border)}
.hsc-num{font-size:13px;font-weight:800;color:var(--accent);letter-spacing:.5px;line-height:1}
.hsc-line{flex:1;width:1px;background:var(--border);margin-top:10px}
.how-step-card:last-child .hsc-line{display:none}
.hsc-body{padding:20px 24px;display:flex;flex-direction:column;gap:8px;flex:1}
.hsc-head{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.hsc-icon{color:var(--accent);display:flex;align-items:center;flex-shrink:0}
.hsc-title{font-size:15px;font-weight:700;color:var(--text);letter-spacing:-.2px}
.hsc-tag{background:var(--accent-dim);color:var(--accent);border:1px solid rgba(0,229,160,.2);border-radius:20px;padding:1px 9px;font-size:11px;font-weight:600;margin-left:auto}
.hsc-desc{font-size:13.5px;color:var(--text-muted);line-height:1.65;max-width:580px}
.how-footer{display:flex;justify-content:center}
.time-pill{display:inline-flex;align-items:center;gap:10px;background:var(--surface);border:1px solid rgba(0,229,160,.3);border-radius:30px;padding:10px 22px;font-size:13.5px;color:var(--text-dim);box-shadow:0 8px 24px -12px rgba(0,229,160,.2)}
.time-dot{width:8px;height:8px;border-radius:50%;background:var(--accent);box-shadow:0 0 10px var(--accent);animation:livePulse 1.4s ease-in-out infinite}
.time-pill strong{color:var(--accent);font-weight:700;margin:0 2px}
.time-sep{color:var(--text-muted);margin:0 4px}
.sdk-section{padding:88px 24px;background:var(--bg);border-top:1px solid var(--border)}
.sdk-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:24px;margin-top:48px}
.sdk-card{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:28px;display:flex;flex-direction:column;gap:20px;transition:border-color .3s,transform .3s,box-shadow .3s}
.sdk-card:hover{border-color:rgba(0,229,160,.35);transform:translateY(-2px);box-shadow:0 16px 48px -20px rgba(0,229,160,.2)}
.sdk-card-top{display:flex;align-items:center;gap:14px}
.sdk-icon{width:52px;height:52px;border-radius:12px;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.sdk-android{background:rgba(61,220,132,.12);color:#3DDC84}
.sdk-ios-icon{background:rgba(150,150,160,.12);color:#a0a0b0}
.sdk-name{font-size:18px;font-weight:700;color:var(--text);margin-bottom:4px}
.sdk-status{display:inline-block;padding:2px 10px;border-radius:20px;font-size:11px;font-weight:600}
.sdk-status-prod{background:rgba(0,229,160,.12);color:var(--accent);border:1px solid rgba(0,229,160,.25)}
.sdk-desc{font-size:14px;color:var(--text-muted);line-height:1.65}
.sdk-code-block{background:var(--surface2);border:1px solid var(--border);border-radius:8px;overflow:hidden}
.sdk-code{padding:16px;font-family:ui-monospace,'SF Mono',Menlo,monospace;font-size:12px;line-height:1.7;color:var(--text-dim);white-space:pre;overflow-x:auto;margin:0}
.sdk-comment{color:var(--text-muted);opacity:.7}
.sdk-str{color:#a8d8a8}
.sdk-footer{margin-top:auto}
.sdk-release-badge{display:inline-flex;align-items:center;gap:6px;background:rgba(250,173,20,.1);color:#faad14;border:1px solid rgba(250,173,20,.25);border-radius:20px;padding:4px 12px;font-size:12px;font-weight:600}
.sdk-bottom{margin-top:32px;display:flex;justify-content:center}
.sdk-compat{display:inline-flex;align-items:center;gap:10px;font-size:14px;color:var(--text-dim);background:var(--surface);border:1px solid var(--border);border-radius:8px;padding:12px 20px}
.sdk-compat svg{color:var(--accent);flex-shrink:0}
.features-section{background:var(--surface2);border-top:1px solid var(--border)}
.features-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:20px}
.feature-card{padding:24px}
.feature-card:hover{border-color:rgba(0,229,160,.3);transform:translateY(-2px);box-shadow:0 12px 32px -16px rgba(0,229,160,.18)}
.feature-icon{font-size:28px;margin-bottom:12px;color:var(--accent);line-height:0}
.feature-card h3{font-size:15px;font-weight:600;margin-bottom:8px}
.feature-card p{font-size:13px;color:var(--text-muted);line-height:1.6}
.pricing-section{background:var(--bg);border-top:1px solid var(--border)}
.pricing-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:20px;margin-top:20px;padding-top:4px}
.pricing-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:28px;display:flex;flex-direction:column;position:relative;transition:border-color .25s ease,transform .25s ease,box-shadow .25s ease}
.pricing-card:hover{transform:translateY(-3px);box-shadow:0 18px 40px -20px rgba(0,0,0,.7)}
.pricing-card.recommended{border-color:var(--accent);box-shadow:0 0 0 1px var(--accent),0 0 40px rgba(0,229,160,.06)}
.pricing-card.recommended:hover{box-shadow:0 0 0 1px var(--accent),0 18px 50px -16px rgba(0,229,160,.32)}
.rec-badge{position:absolute;top:-13px;left:50%;transform:translateX(-50%);background:var(--accent);color:#000;font-size:10px;font-weight:700;letter-spacing:.8px;padding:3px 14px;border-radius:20px;white-space:nowrap;text-transform:uppercase}
.trial-badge{display:inline-block;background:var(--accent-dim);color:var(--accent);border:1px solid rgba(0,229,160,.25);border-radius:20px;padding:2px 10px;font-size:11px;font-weight:600;margin-bottom:14px}
.pricing-name{font-size:13px;font-weight:700;letter-spacing:.5px;color:var(--text-muted);text-transform:uppercase;margin-bottom:10px}
.pricing-price{font-size:42px;font-weight:800;letter-spacing:-2px;line-height:1;color:var(--text);margin-bottom:4px}
.price-mo{font-size:16px;font-weight:400;letter-spacing:0;color:var(--text-muted)}
.pricing-period{font-size:12px;color:var(--text-muted);margin-bottom:24px;min-height:16px}
.pricing-features{list-style:none;display:flex;flex-direction:column;gap:9px;flex:1;margin-bottom:24px}
.pricing-features li{font-size:13px;color:var(--text-dim);display:flex;align-items:center;gap:8px}
.pricing-features li::before{content:"✓";color:var(--accent);font-weight:700;font-size:12px;flex-shrink:0}
.pricing-cta{display:block;text-align:center;padding:11px 20px;border-radius:var(--radius-sm);font-size:14px;font-weight:600;transition:all .15s;cursor:pointer;text-decoration:none}
.pricing-cta.accent{background:var(--accent);color:#000}
.pricing-cta.accent:hover{background:#00c98d;transform:translateY(-1px)}
.pricing-cta.outline{background:transparent;color:var(--text);border:1px solid var(--border)}
.pricing-cta.outline:hover{border-color:var(--accent);color:var(--accent)}
.pricing-note{text-align:center;margin-top:28px;font-size:12px;color:var(--text-muted)}
.cta-section{background:var(--accent-dim2);border-top:1px solid rgba(0,229,160,.15);border-bottom:1px solid rgba(0,229,160,.15);text-align:center}
.cta-inner{max-width:600px;margin:0 auto}
.cta-section h2{margin-bottom:12px}
.cta-section p{color:var(--text-muted);margin-bottom:32px}
.cta-btn{padding:14px 28px;font-size:16px}
footer{padding:28px 24px;border-top:1px solid var(--border)}
.footer-inner{max-width:1140px;margin:0 auto;display:flex;justify-content:space-between;align-items:center;font-size:13px;color:var(--text-muted);gap:16px;flex-wrap:wrap}
.text-muted{color:var(--text-muted)}
@media(max-width:1024px){
  .sdk-grid{grid-template-columns:repeat(2,1fr)}
  .features-grid{grid-template-columns:repeat(2,1fr)}
  .arch-diagram svg{min-width:760px}
}
@media(max-width:768px){
  .nav-hamburger{display:flex}
  .nav-links{position:absolute;top:calc(100% + 1px);left:0;right:0;flex-direction:column;align-items:stretch;background:rgba(8,8,8,.97);border-bottom:1px solid var(--border);padding:8px 0 16px;backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);display:none;gap:0;z-index:200}
  .nav-links.open{display:flex}
  .nav-links a:not(.btn){padding:13px 24px;font-size:15px;width:100%;box-sizing:border-box;border-bottom:1px solid rgba(255,255,255,.04)}
  .nav-links .nav-cta{margin:8px 16px 0;width:calc(100% - 32px)!important;text-align:center;display:block;padding:12px 16px!important;font-size:14px!important;box-sizing:border-box}
  .nav-inner{position:relative;flex-wrap:nowrap}
  .hero{padding:80px 20px 56px}
  section{padding:64px 20px}
  h2{margin-bottom:32px}
  .arch-sub{font-size:15px}
  .how-section{padding:64px 20px 80px}
  .sdk-section{padding:64px 20px}
}
@media(max-width:640px){
  .hero{padding:72px 16px 48px}
  .hero-inner{padding:0}
  .hero-stats{flex-wrap:wrap;padding:12px 12px;gap:2px}
  .hero-stat{padding:6px 12px;min-width:calc(50% - 24px)}
  .hero-stat-div{display:none}
  .hero-stat-val{font-size:20px}
  .hero-actions{flex-direction:column;align-items:stretch;padding:0 8px}
  .hero-actions .btn{text-align:center;width:100%}
  .hero-lang-strip{gap:6px}
  .lang-chip{font-size:12px;padding:4px 10px}
  .arch-diagram{padding:16px 12px;border-radius:10px}
  .how-steps{margin-left:0;margin-right:0;border-radius:10px}
  .hsc-tag{display:none}
  .hsc-body{padding:14px 16px}
  .hsc-left{min-width:52px;padding:18px 14px}
  .sdk-grid{grid-template-columns:1fr}
  .features-grid{grid-template-columns:1fr}
  .pricing-grid{grid-template-columns:1fr}
  .sdk-code{font-size:11px}
  section{padding:52px 16px}
  .sdk-section,.how-section{padding:52px 16px 64px}
}
@media(max-width:400px){
  .hero-title{font-size:28px;letter-spacing:-1px}
  .hero-sub{font-size:14px}
  .hero-btn{font-size:14px;padding:12px 20px}
  .hsc-head{flex-direction:column;align-items:flex-start;gap:6px}
  .hsc-title{font-size:14px}
  .pricing-price{font-size:36px}
  .time-pill{font-size:12px;padding:8px 16px;gap:8px}
  .nav-inner{padding:12px 16px}
}
"""

private fun HTML.dashboardApp() {
    head {
        title { +"Transloom — Dashboard" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        favicon()
        style { unsafe { +"$SHARED_CSS$SHELL_LAYOUT_CSS$DASHBOARD_CSS$SIDEBAR_QUOTA_CSS$CONVERSION_CSS$ONBOARDING_CSS" } }
    }
    body {
        div("app-layout") {
            unsafe { +APP_SIDEBAR_DASH }
            main("main-content") {
                div("page-header") {
                    div {
                        h1("page-title") { +"Dashboard" }
                        p("page-sub") { +"Live overview of your translation pipeline." }
                    }
                    span {
                        id = "sse-status"; classes = setOf("sse-status", "idle")
                        div { classes = setOf("sse-status-dot") }
                        span { id = "sse-status-text"; +"" }
                    }
                }

                div("dash-alert") { id = "dash-alert" }

                div("stats-grid") {
                    div("stat-card card") {
                        div("stat-card-top") {
                            p("stat-label") { +"Strings Translated" }
                            unsafe { +"<svg class='stat-icon' width='15' height='15' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><polyline points='17 1 21 5 17 9'/><path d='M3 11V9a4 4 0 0 1 4-4h14'/><polyline points='7 23 3 19 7 15'/><path d='M21 13v2a4 4 0 0 1-4 4H3'/></svg>" }
                        }
                        p("stat-value loading") { id = "total-translated"; +"—" }
                        p("stat-sub") { +"translated all time" }
                    }
                    div("stat-card card") {
                        div("stat-card-top") {
                            p("stat-label") { +"Pending Review" }
                            unsafe { +"<svg class='stat-icon' width='15' height='15' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'/><polyline points='12 6 12 12 16 14'/></svg>" }
                        }
                        p("stat-value loading") { id = "pending-review"; +"—" }
                        p("stat-sub") { +"awaiting your approval" }
                    }
                    div("stat-card card") {
                        div("stat-card-top") {
                            p("stat-label") { +"Blocked" }
                            unsafe { +"<svg class='stat-icon' width='15' height='15' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'/><line x1='4.93' y1='4.93' x2='19.07' y2='19.07'/></svg>" }
                        }
                        p("stat-value loading") { id = "blocked-count"; +"—" }
                        p("stat-sub") { +"rejected translations" }
                    }
                    div("stat-card card") {
                        div("stat-card-top") {
                            p("stat-label") { +"Languages" }
                            unsafe { +"<svg class='stat-icon' width='15' height='15' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'/><line x1='2' y1='12' x2='22' y2='12'/><path d='M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z'/></svg>" }
                        }
                        p("stat-value loading") { id = "active-langs"; +"—" }
                        p("stat-sub") { +"active target languages" }
                    }
                    div("stat-card card") {
                        div("stat-card-top") {
                            p("stat-label") { +"Projects" }
                            unsafe { +"<svg class='stat-icon' width='15' height='15' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><path d='M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z'/></svg>" }
                        }
                        p("stat-value loading") { id = "total-projects"; +"—" }
                        p("stat-sub") { +"connected repos" }
                    }
                }

                div("dash-body") {
                    div("dash-col-main") {
                        div("content-section") {
                            id = "activity"
                            div("section-header") {
                                h2 { +"Pipeline Activity" }
                            }
                            div("run-list") {
                                id = "run-list"
                                div("activity-empty") {
                                    id = "activity-empty"
                                    div("activity-empty-icon") {
                                        unsafe { +"""<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>""" }
                                    }
                                    p { +"No pipeline runs yet. Push a commit to GitHub to trigger your first translation." }
                                }
                            }
                        }
                    }

                    div("dash-col-side") {
                        div("widget-card") {
                            id = "plan-widget"
                            div("widget-header") {
                                span("widget-title") { +"Plan & Usage" }
                                a("/transloom/billing") { classes = setOf("widget-link"); +"Manage →" }
                            }
                            div("widget-body") {
                                div("plan-row-sm") {
                                    span("plan-name-sm") { id = "w-plan-name"; +"—" }
                                    span("status-badge status-free") { id = "w-plan-badge"; +"Free" }
                                }
                                div("usage-item-sm") {
                                    div("usage-row-sm") { span { +"Strings this month" }; span("usage-val") { id = "w-strings"; +"—" } }
                                    div("usage-track") { div("usage-fill") { id = "w-strings-bar" } }
                                }
                                div("usage-item-sm") {
                                    div("usage-row-sm") { span { +"Projects" }; span("usage-val") { id = "w-projects"; +"—" } }
                                    div("usage-track") { div("usage-fill") { id = "w-projects-bar" } }
                                }
                                p("widget-hint") { id = "w-trial-info" }
                                div { id = "w-upgrade-cta" }
                            }
                        }

                        div("widget-card") {
                            div("widget-header") {
                                span("widget-title") { +"Review Queue" }
                                a("/transloom/review-portal") { classes = setOf("widget-link"); +"Open →" }
                            }
                            div("widget-body") {
                                div { id = "w-review-queue" }
                            }
                        }

                        div("widget-card") {
                            id = "cdn-widget"
                            div("widget-header") {
                                span("widget-title") { +"CDN Status" }
                                span("cdn-widget-badge") { id = "cdn-widget-badge" }
                            }
                            div("widget-body") {
                                div {
                                    id = "cdn-widget-body"
                                    div("cdnw-empty") { +"Loading…" }
                                }
                            }
                        }

                        div("widget-card") {
                            div("widget-header") { span("widget-title") { +"Quick Actions" } }
                            div("widget-body qa-body") {
                                a("/transloom/projects") {
                                    id = "qa-new-project"; classes = setOf("qa-btn")
                                    unsafe { +"<svg width='13' height='13' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z'/><line x1='12' y1='11' x2='12' y2='17'/><line x1='9' y1='14' x2='15' y2='14'/></svg> New project" }
                                }
                                a("/transloom/review-portal") {
                                    classes = setOf("qa-btn")
                                    unsafe { +"<svg width='13' height='13' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z'/><circle cx='12' cy='12' r='3'/></svg> Review translations" }
                                }
                                a("/transloom/projects") {
                                    classes = setOf("qa-btn")
                                    unsafe { +"<svg width='13' height='13' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M4 19.5A2.5 2.5 0 0 1 6.5 17H20'/><path d='M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'/></svg> Edit glossary" }
                                }
                                a("/transloom/billing") {
                                    classes = setOf("qa-btn")
                                    unsafe { +"<svg width='13' height='13' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><rect x='1' y='4' width='22' height='16' rx='2' ry='2'/><line x1='1' y1='10' x2='23' y2='10'/></svg> Manage plan" }
                                }
                            }
                        }
                    }
                }
            }
        }
        div("toast") { id = "toast" }
        div { id = "ob-host" }
        script { unsafe { +SHELL_RUNTIME_JS } }
        script { unsafe { +BILLING_CACHE_JS } }
        script { unsafe { +SIDEBAR_QUOTA_JS } }
        script { unsafe { +DASHBOARD_JS } }
        script { unsafe { +NOTIFICATIONS_JS } }
        script { unsafe { +ONBOARDING_JS } }
        script { unsafe { +"Onboarding.boot('dashboard');" } }
    }
}

private const val DASHBOARD_CSS = """
/* ── Stats grid ──────────────────────────────────────────────────────────── */
.stats-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:14px;margin-bottom:20px}
@media(max-width:1100px){.stats-grid{grid-template-columns:repeat(3,1fr)}}
@media(max-width:700px){.stats-grid{grid-template-columns:repeat(2,1fr)}}
.stat-card{padding:18px 20px}
.stat-label{font-size:11px;font-weight:600;letter-spacing:.5px;color:var(--text-muted);text-transform:uppercase;margin-bottom:8px}
.stat-value{font-size:28px;font-weight:700;color:var(--accent);transition:opacity .2s;letter-spacing:-.5px}
.stat-value.loading{opacity:.4;animation:pulse 1.4s infinite}
@keyframes pulse{0%{opacity:.2}50%{opacity:.7}100%{opacity:.2}}
.stat-yellow{color:var(--yellow)!important}
.stat-plan{font-size:18px;font-weight:700;color:var(--accent)}
/* ── Two-column dashboard body ───────────────────────────────────────────── */
.dash-body{display:grid;grid-template-columns:1fr 300px;gap:20px;align-items:start}
@media(max-width:1100px){.dash-body{grid-template-columns:1fr}}
.dash-col-main{min-width:0}
.dash-col-side{display:flex;flex-direction:column;gap:16px}
/* ── Widget cards (right column) ─────────────────────────────────────────── */
.widget-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden}
.widget-header{display:flex;align-items:center;justify-content:space-between;padding:14px 16px;border-bottom:1px solid var(--border)}
.widget-title{font-size:12px;font-weight:700;letter-spacing:.5px;color:var(--text-dim);text-transform:uppercase}
.widget-link{font-size:12px;color:var(--text-muted);transition:color .15s}.widget-link:hover{color:var(--accent)}
.widget-badge{font-size:11px;font-weight:700;color:var(--accent);background:var(--accent-dim);border:1px solid rgba(0,229,160,.25);border-radius:20px;padding:2px 8px}
.widget-body{padding:16px}
.widget-hint{font-size:11px;color:var(--text-muted);margin-top:8px}
.plan-row-sm{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}
.plan-name-sm{font-size:15px;font-weight:700;color:var(--text)}
.usage-item-sm{margin-bottom:12px}
.usage-row-sm{display:flex;justify-content:space-between;font-size:12px;color:var(--text-muted);margin-bottom:5px}
.usage-val{font-weight:600;color:var(--text-dim)}
.usage-track{height:5px;background:var(--border);border-radius:3px;overflow:hidden}
.usage-fill{height:100%;background:var(--accent);border-radius:3px;transition:width .5s ease;width:0%}
.w-empty{font-size:12px;color:var(--text-muted);text-align:center;padding:16px 0}
.w-run-row{display:flex;align-items:center;gap:8px;padding:7px 0;border-bottom:1px solid var(--border);font-size:12px}
.w-run-row:last-child{border-bottom:none}
.mini-dot{width:7px;height:7px;border-radius:50%;flex-shrink:0}
.mini-dot.running{background:var(--accent);animation:pulse 1.4s infinite}
.mini-dot.done{background:var(--accent)}
.mini-dot.error{background:var(--red)}
.w-run-repo{flex:1;color:var(--text-dim);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.w-run-branch{color:var(--text-muted);font-family:monospace;font-size:11px}
.qa-body{display:flex;flex-direction:column;gap:8px}
.qa-btn{display:block;padding:9px 14px;font-size:13px;font-weight:500;color:var(--text-muted);background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);transition:all .15s}
.qa-btn:hover{border-color:var(--accent);color:var(--accent);background:var(--accent-dim2)}
/* ── Sections ─────────────────────────────────────────────────────────────── */
.content-section{margin-bottom:32px}
.section-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}
.section-header h2{font-size:17px;font-weight:600}
.empty-state{text-align:center;padding:48px 24px;color:var(--text-muted);font-size:14px;background:var(--surface);border:1px dashed var(--border);border-radius:var(--radius)}
.glossary-list{display:flex;flex-direction:column;gap:8px}
.modal-backdrop{position:fixed;inset:0;background:rgba(0,0,0,.7);display:flex;align-items:center;justify-content:center;z-index:1000;opacity:0;pointer-events:none;transition:opacity .15s}
.modal-backdrop.open{opacity:1;pointer-events:all}
.modal{width:520px;max-width:calc(100vw - 32px);max-height:85vh;overflow-y:auto}
.modal-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px}
.modal-header h3{font-size:16px;font-weight:600}
.modal-close{background:none;border:none;color:var(--text-muted);font-size:18px;cursor:pointer}
.modal-body{display:flex;flex-direction:column;gap:16px}
.form-row{display:flex;flex-direction:column;gap:6px}
.two-col{display:grid;grid-template-columns:1fr 1fr;gap:12px;flex-direction:unset}
.plat-toggle{display:flex;gap:8px}
.plat-opt{display:flex;align-items:center;gap:6px;background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);padding:8px 14px;font-size:13px;cursor:pointer;transition:all .12s}
.plat-opt:has(input:checked){background:var(--accent-dim);border-color:var(--accent);color:var(--accent);font-weight:500}
.plat-opt input{width:0;height:0;opacity:0;position:absolute}
.plat-icon{display:inline-flex;align-items:center;color:var(--text-muted);transition:color .12s}
.plat-opt:has(input:checked) .plat-icon{color:var(--accent)}
.field-hint{font-size:11px;color:var(--text-muted);margin-top:4px}
.lang-picker{display:flex;flex-wrap:wrap;gap:8px}
.lang-toggle{display:flex;align-items:center;gap:6px;background:var(--surface2);border:1px solid var(--border);border-radius:20px;padding:5px 12px;font-size:13px;cursor:pointer;transition:all .12s}
.lang-toggle:has(input:checked){background:var(--accent-dim);border-color:var(--accent);color:var(--accent)}
.lang-toggle input{width:0;height:0;opacity:0;position:absolute}
.modal-footer{display:flex;justify-content:flex-end;gap:10px;margin-top:24px}
.btn-delete-project{background:none;border:none;color:var(--text-muted);font-size:14px;cursor:pointer;padding:6px 10px;border-radius:var(--radius-sm);transition:all .15s}
.btn-delete-project:hover{color:var(--red);background:rgba(255,77,79,.1)}
.glossary-controls{margin-bottom:12px}
.glossary-controls select{max-width:280px}
.glossary-add-row{display:flex;gap:8px;margin-bottom:16px;align-items:flex-end}
.glossary-add-row input{max-width:180px}
.glossary-row{display:flex;align-items:center;gap:12px;padding:10px 16px;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-sm);font-size:13px}
.gl-lang{font-weight:600;color:var(--accent);min-width:32px;text-transform:uppercase}
.gl-source{color:var(--text)}
.gl-arrow{color:var(--text-muted);font-size:11px}
.gl-target{color:var(--accent);flex:1}
.btn-gl-delete{background:none;border:none;color:var(--text-muted);font-size:13px;cursor:pointer;padding:4px 8px;border-radius:var(--radius-sm);transition:all .15s;margin-left:auto}
.btn-gl-delete:hover{color:var(--red);background:rgba(255,77,79,.1)}
/* ─── Pipeline Activity ───────────────────────────────────────────────────── */
@keyframes livePulse{0%{box-shadow:0 0 0 0 rgba(0,229,160,.5)}70%{box-shadow:0 0 0 8px rgba(0,229,160,0)}100%{box-shadow:0 0 0 0 rgba(0,229,160,0)}}
.activity-badge{background:rgba(0,229,160,.15);color:var(--accent);display:none}
.sse-status{display:inline-flex;align-items:center;gap:5px;font-size:11px;font-weight:600;letter-spacing:.5px;padding:3px 10px;border-radius:20px;transition:all .3s}
.sse-status.idle,.sse-status.connected{display:none}
.sse-status.reconnecting{display:inline-flex;background:rgba(250,173,20,.1);color:var(--yellow);border:1px solid rgba(250,173,20,.2);animation:ssePulse 1.5s ease-in-out infinite}
.sse-status.disconnected{display:inline-flex;background:rgba(255,77,79,.08);color:var(--red);border:1px solid rgba(255,77,79,.2)}
.sse-status-dot{width:6px;height:6px;border-radius:50%;background:currentColor;flex-shrink:0}
.sse-status.reconnecting .sse-status-dot{animation:sseDot 1.2s ease-in-out infinite}
@keyframes ssePulse{0%,100%{opacity:1}50%{opacity:.65}}
@keyframes sseDot{0%,100%{opacity:.3}50%{opacity:1}}
.run-list{display:flex;flex-direction:column;gap:12px}
.activity-empty{display:flex;flex-direction:column;align-items:center;gap:10px;padding:40px 24px;background:var(--surface);border:1px dashed var(--border);border-radius:var(--radius);color:var(--text-muted);font-size:13px;text-align:center}
.activity-empty-icon{color:var(--text-muted);opacity:.5}
.run-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden;transition:border-color .25s}
.run-card.run-active{border-color:rgba(0,229,160,.3);box-shadow:0 0 0 1px rgba(0,229,160,.1) inset}
.run-card.run-error{border-color:rgba(255,77,79,.25)}
.run-card.run-retrying{border-color:rgba(250,173,20,.3);box-shadow:0 0 0 1px rgba(250,173,20,.08) inset}
.run-header{display:flex;align-items:center;justify-content:space-between;padding:14px 18px;border-bottom:1px solid var(--border);gap:12px}
.run-repo{font-size:13px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.run-meta{display:flex;align-items:center;gap:10px;font-size:12px;color:var(--text-muted);flex-shrink:0}
.run-commit{font-family:monospace;background:var(--surface2);border:1px solid var(--border);border-radius:4px;padding:1px 6px;color:var(--text-dim)}
.run-branch{color:var(--text-muted)}
.run-ago{color:var(--text-muted);font-variant-numeric:tabular-nums}
.run-status-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0}
.run-status-dot.active{background:var(--accent);animation:livePulse 1.8s infinite}
.run-status-dot.done{background:var(--accent)}
.run-status-dot.error{background:var(--red)}
.run-status-dot.retrying{background:var(--yellow);animation:livePulse 1.8s infinite}
.run-retried-badge{font-size:10px;font-weight:700;letter-spacing:.5px;padding:1px 7px;border-radius:10px;background:rgba(250,173,20,.12);color:var(--yellow);border:1px solid rgba(250,173,20,.25);white-space:nowrap}
.run-steps{padding:12px 18px;display:flex;flex-direction:column;gap:0}
.step-row{display:flex;align-items:flex-start;gap:12px;padding:7px 0;position:relative}
.step-row:not(:last-child)::after{content:'';position:absolute;left:11px;top:28px;width:1px;height:calc(100% - 10px);background:var(--border);z-index:0}
.step-icon{width:24px;height:24px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:12px;flex-shrink:0;position:relative;z-index:1;transition:all .3s ease;margin-top:1px}
.step-icon.pending{background:var(--surface2);border:1.5px solid var(--border);color:var(--text-muted)}
.step-icon.running{background:rgba(0,229,160,.12);border:1.5px solid rgba(0,229,160,.4);color:var(--accent)}
.step-icon.done{background:rgba(0,229,160,.15);border:1.5px solid rgba(0,229,160,.5);color:var(--accent)}
.step-icon.error{background:rgba(255,77,79,.12);border:1.5px solid rgba(255,77,79,.4);color:var(--red)}
.step-icon.skipped{background:var(--surface2);border:1.5px solid var(--border);color:var(--text-muted);opacity:.5}
.step-spin{animation:spin .9s linear infinite}
@keyframes spin{from{transform:rotate(0deg)}to{transform:rotate(360deg)}}
.step-body{flex:1;display:flex;flex-direction:column;align-items:flex-start;gap:2px;min-width:0}
.step-label{font-size:13px;transition:color .2s}
.step-label.running{color:var(--text);font-weight:500}
.step-label.done{color:var(--text-dim)}
.step-label.error{color:var(--red)}
.step-label.skipped{color:var(--text-muted);opacity:.6}
.step-label.pending{color:var(--text-muted)}
.step-detail{font-size:11.5px;color:var(--text-muted);word-break:break-word;overflow-wrap:anywhere;line-height:1.4}
.step-detail.done{color:var(--text-dim)}
.step-detail.error{color:var(--red);opacity:.9}
.run-savings-chip{display:inline-flex;align-items:center;gap:4px;background:rgba(0,229,160,.1);color:var(--accent);border:1px solid rgba(0,229,160,.25);border-radius:20px;padding:2px 9px;font-size:11px;font-weight:600;flex-shrink:0}
.run-cdn-chip{display:inline-flex;align-items:center;gap:4px;background:rgba(0,229,160,.08);color:var(--accent);border:1px solid rgba(0,229,160,.22);border-radius:20px;padding:2px 9px;font-size:11px;font-weight:600;flex-shrink:0}
/* ── CDN Status Widget ───────────────────────────────────────────────────── */
.cdn-widget-badge{font-size:11px;font-weight:700;border-radius:20px;padding:2px 8px;transition:all .3s}
.cdn-widget-badge.live{color:var(--accent);background:var(--accent-dim);border:1px solid rgba(0,229,160,.25)}
.cdnw-empty{font-size:12px;color:var(--text-muted);padding:4px 0}
.cdnw-proj-name{font-size:11px;font-weight:600;color:var(--text-muted);margin-bottom:8px;letter-spacing:.2px}
.cdnw-stat-row{display:flex;gap:0;margin-bottom:12px;background:var(--surface2);border:1px solid var(--border);border-radius:8px;overflow:hidden}
.cdnw-stat{flex:1;display:flex;flex-direction:column;align-items:center;padding:10px 8px;border-right:1px solid var(--border)}
.cdnw-stat:last-child{border-right:none}
.cdnw-stat-val{font-size:15px;font-weight:700;color:var(--accent);line-height:1.1}
.cdnw-stat-lbl{font-size:10px;color:var(--text-muted);margin-top:3px;letter-spacing:.3px}
.cdnw-mono{font-family:ui-monospace,'SF Mono',Menlo,monospace;font-size:11px!important}
.cdnw-locales{display:flex;flex-wrap:wrap;gap:4px;margin-bottom:12px}
.cdnw-locale-chip{font-size:10px;font-weight:600;padding:2px 7px;background:var(--surface2);border:1px solid var(--border);border-radius:10px;color:var(--text-muted)}
.cdnw-locale-more{color:var(--accent);background:var(--accent-dim);border-color:rgba(0,229,160,.2)}
.cdnw-kv-note{font-size:10px;color:var(--text-muted);margin-bottom:10px;padding:6px 8px;background:var(--surface2);border:1px solid var(--border);border-radius:6px;letter-spacing:.2px}
.cdnw-proj-list{display:flex;flex-direction:column;gap:0;margin-bottom:12px;background:var(--surface2);border:1px solid var(--border);border-radius:8px;overflow:hidden}
.cdnw-proj-row{display:flex;align-items:center;gap:8px;padding:8px 10px;border-bottom:1px solid var(--border);font-size:11px}
.cdnw-proj-row:last-child{border-bottom:none}
.cdnw-status-dot{width:6px;height:6px;border-radius:50%;background:var(--border);flex-shrink:0}
.cdnw-status-dot.live{background:var(--accent);box-shadow:0 0 5px var(--accent)}
.cdnw-proj-label{flex:1;font-weight:600;color:var(--text-dim);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.cdnw-proj-meta{color:var(--text-muted);white-space:nowrap}
.cdnw-proj-time{color:var(--text-muted);white-space:nowrap;margin-left:4px}
.cdnw-sdk-note{display:flex;align-items:flex-start;gap:6px;font-size:11px;color:var(--text-muted);line-height:1.55;padding-top:10px;border-top:1px solid var(--border)}
.cdnw-sdk-note svg{flex-shrink:0;margin-top:1px;opacity:.6}
.run-no-retranslation{font-size:12px;color:var(--accent);opacity:.8}
.force-translate-btn{display:inline-flex;align-items:center;gap:5px;font-size:11.5px;font-weight:500;color:var(--text-muted);background:none;border:1px solid var(--border);border-radius:var(--radius-sm);padding:3px 9px;cursor:pointer;transition:border-color .15s,color .15s;margin-left:8px}
.force-translate-btn:hover{border-color:var(--accent);color:var(--accent)}
.force-translate-btn:disabled{opacity:.4;cursor:not-allowed}
/* ── Per-locale lanes + ETA ─────────────────────────────────────────────── */
.run-locales{padding:6px 18px 14px;display:flex;flex-direction:column;gap:6px;border-top:1px dashed var(--border)}
.run-locales-head{display:flex;align-items:center;justify-content:space-between;font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;font-weight:600;padding-bottom:2px}
.run-locales-eta{color:var(--text-dim);text-transform:none;letter-spacing:0;font-weight:500;font-variant-numeric:tabular-nums}
.lane-row{display:flex;align-items:center;gap:10px;font-size:12px}
.lane-code{flex-shrink:0;min-width:54px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;color:var(--text-dim);font-size:11px}
.lane-name{flex:1;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:11.5px}
.lane-track{flex:1.4;height:4px;background:var(--surface2);border:1px solid var(--border);border-radius:3px;overflow:hidden;min-width:60px}
.lane-fill{height:100%;background:var(--text-muted);border-radius:3px;transition:width .4s ease,background .2s}
.lane-row.translating .lane-fill{background:var(--accent);animation:pulse 1.6s infinite}
.lane-row.done .lane-fill{background:var(--accent);opacity:.7}
.lane-row.error .lane-fill{background:var(--red)}
.lane-row.queued .lane-fill{background:var(--text-muted);opacity:.4}
.lane-count{flex-shrink:0;color:var(--text-muted);font-variant-numeric:tabular-nums;font-size:11px;min-width:46px;text-align:right}
/* ── Resume-on-reconnect pill ───────────────────────────────────────────── */
.sse-resume-pill{position:fixed;left:50%;top:18px;transform:translateX(-50%) translateY(-12px);z-index:2200;display:none;align-items:center;gap:8px;padding:8px 14px;background:var(--surface);border:1px solid var(--accent);border-radius:20px;font-size:12px;font-weight:600;color:var(--text);box-shadow:0 8px 24px -8px rgba(0,0,0,.5);opacity:0;transition:opacity .2s,transform .2s}
.sse-resume-pill.visible{display:inline-flex;opacity:1;transform:translateX(-50%) translateY(0)}
.sse-resume-pill .sse-resume-dot{width:7px;height:7px;border-radius:50%;background:var(--accent);box-shadow:0 0 0 4px rgba(0,229,160,.15)}
.run-footer{padding:10px 18px;border-top:1px solid var(--border);display:flex;align-items:center;justify-content:space-between;gap:10px}
.pr-link{display:inline-flex;align-items:center;gap:6px;font-size:12px;color:var(--accent);font-weight:500}
.pr-link:hover{text-decoration:underline}
.run-error-msg{font-size:12px;color:var(--red);flex:1}
.run-duration{font-size:12px;color:var(--text-muted)}
.retry-btn{display:inline-flex;align-items:center;gap:5px;font-size:12px;padding:5px 12px;border-radius:var(--radius-sm);background:rgba(255,77,79,.08);color:var(--red);border:1px solid rgba(255,77,79,.25);cursor:pointer;transition:all .15s;flex-shrink:0}
.retry-btn:hover{background:rgba(255,77,79,.14);border-color:rgba(255,77,79,.4)}
.retry-btn:disabled{opacity:.5;cursor:not-allowed}
.retry-btn.retrying{background:rgba(250,173,20,.1);color:var(--yellow);border-color:rgba(250,173,20,.3)}
.ob-guide{background:var(--surface);border:1px solid rgba(0,229,160,.2);border-radius:var(--radius);padding:32px}
.ob-intro{margin-bottom:28px}
.ob-intro h3{font-size:18px;font-weight:700;margin-bottom:6px}
.ob-intro p{font-size:14px;color:var(--text-muted)}
.ob-steps{display:flex;flex-direction:column}
.ob-step{display:flex;align-items:center;gap:16px;padding:16px 0;border-bottom:1px solid var(--border)}
.ob-step:last-child{border-bottom:none;padding-bottom:0}
.ob-step:first-child{padding-top:0}
.ob-num{width:32px;height:32px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:700;flex-shrink:0}
.ob-done .ob-num{background:var(--accent-dim);color:var(--accent);border:1.5px solid rgba(0,229,160,.4)}
.ob-active .ob-num{background:var(--accent);color:#0a0a0a}
.ob-pending .ob-num{background:var(--surface2);color:var(--text-muted);border:1.5px solid var(--border)}
.ob-body{flex:1;display:flex;flex-direction:column;gap:3px}
.ob-body strong{font-size:14px;font-weight:600}
.ob-done .ob-body strong{color:var(--text-dim)}
.ob-pending .ob-body strong{color:var(--text-muted)}
.ob-body span{font-size:13px;color:var(--text-muted)}
.ob-cta{padding:8px 16px;font-size:13px;white-space:nowrap}
/* ── Stat card improvements ──────────────────────────────────────────────── */
.stat-card-top{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:6px}
.stat-icon{color:var(--text-muted);opacity:.45;flex-shrink:0;margin-top:2px}
.stat-sub{font-size:11px;color:var(--text-muted);margin-top:5px;font-weight:400;letter-spacing:.1px}
/* ── Review alert banner ─────────────────────────────────────────────────── */
.dash-alert{display:none;align-items:center;justify-content:space-between;gap:14px;padding:11px 16px;border-radius:var(--radius);border:1px solid rgba(250,173,20,.3);background:rgba(250,173,20,.06);margin-bottom:20px;font-size:13px;line-height:1.5}
.dash-alert.visible{display:flex}
.dash-alert.critical{border-color:rgba(255,77,79,.3);background:rgba(255,77,79,.05)}
.dash-alert-msg{color:var(--text-muted);flex:1}
.dash-alert-msg strong{color:var(--yellow)}
.dash-alert.critical .dash-alert-msg strong{color:var(--red)}
.dash-alert-action{font-size:13px;font-weight:600;color:var(--yellow);white-space:nowrap;padding:5px 12px;border-radius:var(--radius-sm);border:1px solid rgba(250,173,20,.3);transition:all .15s}
.dash-alert.critical .dash-alert-action{color:var(--red);border-color:rgba(255,77,79,.3)}
.dash-alert-action:hover{opacity:.8}
/* ── Review Queue widget ─────────────────────────────────────────────────── */
.rq-rows{display:flex;flex-direction:column;gap:8px;margin-bottom:12px}
.rq-row{display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:var(--radius-sm);background:var(--surface2);border:1px solid var(--border)}
.rq-pending{border-color:rgba(250,173,20,.25);background:rgba(250,173,20,.05)}
.rq-blocked{border-color:rgba(255,77,79,.25);background:rgba(255,77,79,.05)}
.rq-num{font-size:24px;font-weight:700;line-height:1;min-width:28px;letter-spacing:-.5px}
.rq-pending .rq-num{color:var(--yellow)}
.rq-blocked .rq-num{color:var(--red)}
.rq-info{display:flex;flex-direction:column;gap:2px}
.rq-label{font-size:12px;font-weight:600;color:var(--text-dim)}
.rq-sublabel{font-size:11px;color:var(--text-muted)}
.rq-cta{display:block;text-align:center;padding:8px 14px;font-size:13px;font-weight:600;border-radius:var(--radius-sm)}
.rq-all-clear{display:flex;align-items:center;gap:8px;font-size:12px;color:var(--text-muted);padding:4px 0}
/* ── Plan widget additions ───────────────────────────────────────────────── */
.w-upgrade-link{display:block;font-size:12px;color:var(--accent);margin-top:10px;text-align:center;padding:7px 12px;border:1px solid rgba(0,229,160,.25);border-radius:var(--radius-sm);background:var(--accent-dim2);transition:all .15s;font-weight:500}
.w-upgrade-link:hover{background:var(--accent-dim);border-color:rgba(0,229,160,.4)}
/* ── Quick action icons ──────────────────────────────────────────────────── */
.qa-btn{display:flex;align-items:center;gap:8px}
.qa-btn svg{flex-shrink:0;opacity:.65}
/* ── Status badges (dashboard plan widget) ───────────────────────────────── */
.status-badge{display:inline-flex;align-items:center;gap:5px;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:700;letter-spacing:.5px}
.status-active{background:rgba(0,229,160,.12);color:var(--accent);border:1px solid rgba(0,229,160,.25)}
.status-cancelling{background:rgba(255,77,79,.1);color:var(--red);border:1px solid rgba(255,77,79,.2)}
.status-free{background:var(--surface2);color:var(--text-muted);border:1px solid var(--border)}
"""

private val DASHBOARD_JS = """
const BASE='/transloom/api';
let token=localStorage.getItem('transloom_token');
if(!token){var _bc=(document.cookie.match(/(?:^|;\s*)tl_token_bootstrap=([^;]*)/))||[];if(_bc[1]){token=decodeURIComponent(_bc[1]);localStorage.setItem('transloom_token',token);}}
document.cookie='tl_token_bootstrap=;path=/transloom;max-age=0;secure;samesite=lax';
if(!token){window.location.href='/transloom';throw new Error('no token');}

function authHeaders(){return{'Authorization':'Bearer '+token,'Content-Type':'application/json'};}
async function api(path,opts={}){
  const res=await fetch(BASE+path,{...opts,headers:{...authHeaders(),...(opts.headers||{})}});
  if(res.status===401){logout();return null;}return res;
}
function logout(){localStorage.removeItem('transloom_token');window.location.href='/transloom/auth/logout';}
function toast(msg,type='success'){const el=document.getElementById('toast');el.textContent=msg;el.className='toast show '+type;setTimeout(()=>el.className='toast',2800);}
function esc(s){if(!s)return '';const d=document.createElement('div');d.textContent=String(s);return d.innerHTML;}

async function loadStats(){
  document.querySelectorAll('.stat-value').forEach(el=>el.classList.add('loading'));
  const res=await api('/dashboard/stats');
  if(!res||!res.ok){document.querySelectorAll('.stat-value').forEach(el=>el.classList.remove('loading'));return;}
  const s=await res.json();
  const set=(id,v)=>{const el=document.getElementById(id);if(el){el.textContent=v??0;el.classList.remove('loading');}};
  set('total-translated',s.totalStringsTranslated);
  set('pending-review',s.pendingReview);
  set('blocked-count',s.blockedCount);
  set('active-langs',s.activeLanguages);
  set('total-projects',s.totalProjects);
  // Dynamic color coding — pending = yellow when non-zero, blocked = red when non-zero
  const pendingEl=document.getElementById('pending-review');
  if(pendingEl)pendingEl.style.color=(s.pendingReview??0)>0?'var(--yellow)':'';
  const blockedEl=document.getElementById('blocked-count');
  if(blockedEl)blockedEl.style.color=(s.blockedCount??0)>0?'var(--red)':'';
  // Sidebar review badge
  const badge=document.getElementById('review-count');
  if(badge){if((s.pendingReview??0)>0){badge.textContent=s.pendingReview;badge.style.display='inline';}else{badge.style.display='none';}}
  // Alert banner and review queue widget
  updateDashAlert(s.pendingReview||0,s.blockedCount||0);
  updateReviewQueueWidget(s.pendingReview||0,s.blockedCount||0);
}

function updateDashAlert(pending,blocked){
  const el=document.getElementById('dash-alert');if(!el)return;
  if(pending===0&&blocked===0){el.className='dash-alert';return;}
  const parts=[];
  if(pending>0)parts.push('<strong>'+pending+'</strong> pending review');
  if(blocked>0)parts.push('<strong>'+blocked+'</strong> blocked');
  el.innerHTML='<span class="dash-alert-msg">'+parts.join(' &middot; ')+'</span><a class="dash-alert-action" href="/transloom/review-portal">Review now &rarr;</a>';
  el.className='dash-alert visible'+(blocked>0?' critical':'');
}

function updateReviewQueueWidget(pending,blocked){
  const el=document.getElementById('w-review-queue');if(!el)return;
  if(pending===0&&blocked===0){
    el.innerHTML='<div class="rq-all-clear"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>Queue is empty &mdash; all clear!</div>';
    return;
  }
  el.innerHTML='<div class="rq-rows">'
    +(pending>0?'<div class="rq-row rq-pending"><span class="rq-num">'+pending+'</span><div class="rq-info"><span class="rq-label">pending review</span><span class="rq-sublabel">awaiting your approval</span></div></div>':'')
    +(blocked>0?'<div class="rq-row rq-blocked"><span class="rq-num">'+blocked+'</span><div class="rq-info"><span class="rq-label">blocked</span><span class="rq-sublabel">rejected &mdash; needs rework</span></div></div>':'')
    +'</div><a href="/transloom/review-portal" class="rq-cta btn btn-primary">Open review portal &rarr;</a>';
}

async function loadPlanWidget(){
  const [subRes,usageRes]=await Promise.all([api('/billing/subscription'),api('/billing/usage')]);
  if(!subRes?.ok||!usageRes?.ok)return;
  const sub=await subRes.json();
  const usage=await usageRes.json();
  const setTxt=(id,v)=>{const e=document.getElementById(id);if(e)e.textContent=v;};
  setTxt('w-plan-name',sub.displayName||sub.plan);
  const badge=document.getElementById('w-plan-badge');
  if(badge){
    if(sub.cancelAtPeriodEnd){badge.className='status-badge status-cancelling';badge.textContent='Cancelling';}
    else if(sub.plan==='FREE'){badge.className='status-badge status-free';badge.textContent='Free';}
    else{badge.className='status-badge status-active';badge.textContent='Active';}
  }
  const setBar=(barId,valId,used,max)=>{
    const bar=document.getElementById(barId);const val=document.getElementById(valId);if(!bar||!val)return;
    if(max&&max>0){const pct=Math.min(100,Math.round(used/max*100));bar.style.width=pct+'%';if(pct>85)bar.style.background='var(--yellow)';val.textContent=used+' / '+max;}
    else{bar.style.width='100%';bar.style.background='rgba(0,229,160,.25)';val.textContent=used+' (unlimited)';}
  };
  setBar('w-strings-bar','w-strings',usage.stringsTranslated,usage.stringLimit);
  const pmax=sub.maxProjects>0?sub.maxProjects:null;
  setBar('w-projects-bar','w-projects',usage.projectsUsed,pmax);
  const hint=document.getElementById('w-trial-info');
  if(hint){
    if(sub.plan==='FREE'&&usage.stringLimit>0){const rem=Math.max(0,usage.stringLimit-(usage.stringsTranslated||0));hint.textContent=rem+' strings remaining this month';}
    else if(sub.currentPeriodEnd&&sub.plan!=='FREE'){hint.textContent=(sub.cancelAtPeriodEnd?'Cancels ':'Renews ')+sub.currentPeriodEnd;}
  }
  const cta=document.getElementById('w-upgrade-cta');
  if(cta&&sub.plan==='FREE')cta.innerHTML='<a href="/transloom/billing" class="w-upgrade-link">Upgrade for more capacity &rarr;</a>';
}

function updateRunSummaryWidget(){
  // Single pass: tally active/failed and collect rows for the top-5 sort.
  let active=0,failed=0;
  const all=[];
  runState.forEach(function(r){
    if(!r.finishedAt)active++;
    else if(r.error)failed++;
    all.push(r);
  });
  const badge=document.getElementById('w-run-badge');
  if(badge){
    if(active>0){badge.textContent=active+' running';badge.style.background='';badge.style.color='';badge.style.display='inline';}
    else if(failed>0){badge.textContent=failed+' failed';badge.style.background='rgba(255,77,79,.12)';badge.style.color='var(--red)';badge.style.display='inline';}
    else{badge.style.display='none';}
  }
  const list=document.getElementById('w-run-summary');if(!list)return;
  if(!all.length){list.innerHTML='<div class="w-empty">No runs yet</div>';return;}
  all.sort(function(a,b){return(b.startedAt||0)-(a.startedAt||0);});
  list.innerHTML=all.slice(0,5).map(function(r){
    const st=!r.finishedAt?'running':(r.error?'error':'done');
    return '<div class="w-run-row"><span class="mini-dot '+st+'"></span>'
      +'<span class="w-run-repo">'+esc(r.repo)+'</span>'
      +'<span class="w-run-branch">'+esc(r.branch)+'</span></div>';
  }).join('');
}


// ─── Pipeline Activity ─────────────────────────────────────────────────────

const STEP_ORDER=['WEBHOOK_RECEIVED','FETCHING_STRINGS','DETECTING_CHANGES','BILLING_CHECK','TRANSLATING','CREATING_PR','CDN_PUBLISH'];
const STEP_ICONS={
  pending:'<svg width="10" height="10" viewBox="0 0 10 10"><circle cx="5" cy="5" r="4" fill="none" stroke="currentColor" stroke-width="1.5"/></svg>',
  running:'<svg class="step-spin" width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M6 1v2M6 9v2M1 6h2M9 6h2M2.5 2.5l1.4 1.4M8.1 8.1l1.4 1.4M2.5 9.5l1.4-1.4M8.1 3.9l1.4-1.4"/></svg>',
  done:'<svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1.5 5 4 7.5 8.5 2.5"/></svg>',
  error:'<svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="1.5" y1="1.5" x2="8.5" y2="8.5"/><line x1="8.5" y1="1.5" x2="1.5" y2="8.5"/></svg>',
  skipped:'<svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="2" y1="5" x2="8" y2="5"/></svg>'
};

// runId -> { repo, branch, commitShort, startedAt, finishedAt, steps:{id->{status,detail}},
//            stepLabels, prUrl, error, projectId, retriedFromRunId, retryPending }
// Map (not plain object) so we can evict the oldest entry when capacity is
// exceeded over a long-lived dashboard session.
const runState=new Map();
const RUN_CAP=20; // matches PipelineEventBus.MAX_RUNS on the server

// ── Render scheduler ────────────────────────────────────────────────────────
// SSE bursts (a run emits webhook+fetch+detect+billing+translate+pr+cdn within
// seconds) used to trigger one full-card outerHTML rewrite per event. Coalesce
// every render into a single requestAnimationFrame so multiple events landing
// in the same tick produce exactly one DOM write per touched run.
const _dirtyRuns=new Set();
let _widgetDirty=false;
let _rafScheduled=false;
function _scheduleFlush(){if(!_rafScheduled){_rafScheduled=true;requestAnimationFrame(_flushRenders);}}
function scheduleRender(runId){_dirtyRuns.add(runId);_scheduleFlush();}
function scheduleWidgets(){_widgetDirty=true;_scheduleFlush();}
function _flushRenders(){
  _rafScheduled=false;
  if(_dirtyRuns.size){
    const list=document.getElementById('run-list');
    _dirtyRuns.forEach(function(runId){
      const html=buildRunHtml(runId);if(!html)return;
      const existing=document.getElementById('rc-'+runId);
      if(existing){existing.outerHTML=html;}
      else if(list){document.getElementById('activity-empty')?.remove();list.insertAdjacentHTML('afterbegin',html);}
    });
    _dirtyRuns.clear();
  }
  if(_widgetDirty){_widgetDirty=false;updateRunSummaryWidget();}
}

// ── Timestamps ────────────────────────────────────────────────────────────────
function timeAgo(ms){
  const d=(Date.now()-ms)/1e3;
  if(d<5)return 'just now';
  if(d<60)return Math.floor(d)+'s ago';
  if(d<3600)return Math.floor(d/60)+'m ago';
  if(d<86400)return Math.floor(d/3600)+'h ago';
  return Math.floor(d/86400)+'d ago';
}
function runDuration(s,e){const sec=Math.round((e-s)/1e3);if(sec<60)return sec+'s';return Math.floor(sec/60)+'m '+sec%60+'s';}

// Tick visible-card timestamps every 20s. Paused when the tab is hidden so
// backgrounded dashboards don't burn CPU on querySelectorAll loops.
let _tickTimer=null;
function startTimestampTicker(){
  if(_tickTimer)return;
  _tickTimer=setInterval(function(){
    document.querySelectorAll('[data-started]').forEach(function(el){
      el.textContent=timeAgo(parseInt(el.dataset.started,10));
    });
    // Re-render any active run so the ETA decreases between SSE bursts.
    runState.forEach(function(r,id){if(!r.finishedAt&&(r.localeOrder||[]).length)scheduleRender(id);});
  },20000);
}
function stopTimestampTicker(){if(_tickTimer){clearInterval(_tickTimer);_tickTimer=null;}}
startTimestampTicker();

// ── Card builders ─────────────────────────────────────────────────────────────
function buildStepHtml(id,st,label){
  const s=st||{status:'pending',detail:null};
  const icon=STEP_ICONS[s.status]||STEP_ICONS.pending;
  const detail=s.detail?('<span class="step-detail '+esc(s.status)+'">'+esc(s.detail)+'</span>'):'';
  return '<div class="step-row"><div class="step-icon '+esc(s.status)+'">'+icon+'</div>'
    +'<div class="step-body"><span class="step-label '+esc(s.status)+'">'+esc(label)+'</span>'+detail+'</div></div>';
}

function buildLaneHtml(L){
  const pct=L.total>0?Math.min(100,Math.round((L.done/L.total)*100)):0;
  const status=esc(L.status||'queued');
  return '<div class="lane-row '+status+'">'
    +'<span class="lane-code">'+esc(L.code)+'</span>'
    +'<span class="lane-name">'+esc(L.name||'')+'</span>'
    +'<div class="lane-track"><div class="lane-fill" style="width:'+pct+'%"></div></div>'
    +'<span class="lane-count">'+(L.done||0)+' / '+(L.total||0)+'</span>'
    +'</div>';
}

function buildEta(run){
  const done=run.progressDone||0,total=run.progressTotal||0;
  if(!total||done>=total||run.finishedAt)return '';
  const elapsed=(Date.now()-(run.startedAt||Date.now()))/1000;
  if(elapsed<3||done===0)return '';
  const rate=done/elapsed;
  const remaining=Math.round((total-done)/rate);
  if(!isFinite(remaining)||remaining<=0)return '';
  const label=remaining<60?(remaining+'s'):(Math.floor(remaining/60)+'m '+(remaining%60)+'s');
  return done+' / '+total+' strings · ~'+label+' left';
}

function buildRunHtml(runId){
  const run=runState.get(runId);if(!run)return '';
  const isActive=!run.finishedAt;
  const hasError=!!run.error;
  const isRetrying=!!run.retryPending;
  const steps=STEP_ORDER.map(id=>buildStepHtml(id,run.steps[id],run.stepLabels[id]||id)).join('');

  // Per-locale lanes (Point 1) — visible while the run is active or for the
  // brief window where finished runs still have lane data in memory. Skipped
  // entirely when no lanes have been emitted yet (e.g. early steps).
  let lanesHtml='';
  const order=run.localeOrder||[];
  if(order.length){
    const lanes=order.map(function(c){return buildLaneHtml(run.locales[c]);}).join('');
    const eta=isActive?buildEta(run):'';
    lanesHtml='<div class="run-locales">'
      +'<div class="run-locales-head"><span>Languages</span>'
      +(eta?'<span class="run-locales-eta">'+esc(eta)+'</span>':'')
      +'</div>'+lanes+'</div>';
  }

  // Savings chip — shown whenever the semantic analyzer skipped retranslation
  const skipped=run.surfaceSkipped||0;
  const savingsChip=skipped>0
    ?'<span class="run-savings-chip"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3c-1 3-2 4-5 5 3 1 4 2 5 5 1-3 2-4 5-5-3-1-4-2-5-5z"/><path d="M5.5 10.5c-.5 1.5-1 2-2.5 2.5 1.5.5 2 1 2.5 2.5.5-1.5 1-2 2.5-2.5-1.5-.5-2-1-2.5-2.5z"/></svg>'
      +' '+skipped+' '+(skipped===1?'string':'strings')+' saved</span>'
    :'';

  // CDN chip — shown when CDN publish completed for this run
  const cdnStep=run.steps&&run.steps['CDN_PUBLISH'];
  const cdnDone=cdnStep&&cdnStep.status==='done';
  const cdnChip=cdnDone
    ?'<span class="run-cdn-chip"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>'
      +' '+(cdnStep.detail||'CDN live')+'</span>'
    :'';

  // Footer: error + retry button, or PR link, or no-changes message
  let left='',right='';
  if(run.prUrl){
    left='<a class="pr-link" href="'+esc(run.prUrl)+'" target="_blank" rel="noopener">'
      +'<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="18" cy="18" r="3"/><circle cx="6" cy="6" r="3"/><path d="M13 6h3a2 2 0 0 1 2 2v7"/><line x1="6" y1="9" x2="6" y2="21"/></svg>'
      +' View pull request</a>';
  } else if(hasError&&!isRetrying){
    left='<span class="run-error-msg">&#9888; '+esc(run.error)+'</span>';
    left+='<button class="retry-btn" onclick="retriggerRun(\''+runId+'\')">'
      +'<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 .49-4.5"/></svg>'
      +' Retry</button>';
  } else if(hasError&&isRetrying){
    left='<span class="run-error-msg">&#9888; '+esc(run.error)+'</span>';
    left+='<button class="retry-btn retrying" disabled>'
      +'<svg class="step-spin" width="11" height="11" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M6 1v2M6 9v2M1 6h2M9 6h2M2.5 2.5l1.4 1.4M8.1 8.1l1.4 1.4M2.5 9.5l1.4-1.4M8.1 3.9l1.4-1.4"/></svg>'
      +' Retrying…</button>';
  } else if(!isActive){
    if(skipped>0){
      left='<span class="run-no-retranslation">Surface rewrites only — retranslation skipped</span>'
        +'<button class="force-translate-btn" onclick="forceTranslateRun(\''+runId+'\')" title="Override classifier and retranslate these strings">'
        +'<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 .49-4.5"/></svg>'
        +' Retranslate anyway</button>';
    } else {
      left='<span class="run-duration">No changes found</span>';
    }
  }
  if(run.finishedAt) right='<span class="run-duration">'+runDuration(run.startedAt,run.finishedAt)+'</span>';

  const footParts=[savingsChip,cdnChip,left,right].filter(Boolean);
  const footHtml=footParts.length?('<div class="run-footer">'+footParts.join('')+'</div>'):'';

  const dot=isActive?'active':(isRetrying?'retrying':(hasError?'error':'done'));
  let cardCls='run-card';
  if(isActive)cardCls+=' run-active';
  else if(isRetrying)cardCls+=' run-retrying';
  else if(hasError)cardCls+=' run-error';

  const retriedBadge=run.retriedFromRunId
    ?'<span class="run-retried-badge">↺ Retry</span>' : '';

  return '<div class="'+cardCls+'" id="rc-'+runId+'">'
    +'<div class="run-header"><div class="run-repo">'+esc(run.repo)+' '+retriedBadge+'</div>'
    +'<div class="run-meta">'
    +'<span class="run-branch">'+esc(run.branch)+'</span>'
    +'<span class="run-commit">'+esc(run.commitShort)+'</span>'
    +'<span class="run-ago" data-started="'+run.startedAt+'">'+timeAgo(run.startedAt)+'</span>'
    +'<div class="run-status-dot '+dot+'"></div>'
    +'</div></div>'
    +'<div class="run-steps">'+steps+'</div>'
    +lanesHtml
    +footHtml+'</div>';
}

function applySnapshot(snapshot){
  const existing=runState.get(snapshot.runId);
  const run=existing||{steps:{},stepLabels:{},locales:{},localeOrder:[]};
  run.repo=snapshot.repo;run.branch=snapshot.branch;run.commitShort=snapshot.commitShort;
  run.startedAt=snapshot.startedAt;run.finishedAt=snapshot.finishedAt||null;
  run.prUrl=snapshot.prUrl||null;run.error=snapshot.error||null;
  run.projectId=snapshot.projectId||null;run.retriedFromRunId=snapshot.retriedFromRunId||null;
  run.surfaceSkipped=snapshot.surfaceSkipped||0;
  (snapshot.steps||[]).forEach(function(s){
    run.steps[s.id]={status:s.status,detail:s.detail||null};
    run.stepLabels[s.id]=s.label;
  });
  // Per-locale lanes (Point 1) — server replays the full set on REST snapshot,
  // and dribbles individual updates over SSE via "locale" events.
  if(Array.isArray(snapshot.locales)&&snapshot.locales.length){
    if(!run.locales)run.locales={};
    if(!run.localeOrder)run.localeOrder=[];
    snapshot.locales.forEach(function(l){
      if(!run.locales[l.code])run.localeOrder.push(l.code);
      run.locales[l.code]=l;
    });
  }
  run.progressDone=snapshot.progressDone||run.progressDone||0;
  run.progressTotal=snapshot.progressTotal||run.progressTotal||0;
  runState.set(snapshot.runId,run);
  if(runState.size>RUN_CAP)_evictOldestRun();
}

function _evictOldestRun(){
  // Prefer evicting the oldest *finished* run so an unbounded burst of new
  // starts never bumps a live run out from under the user.
  let oldestKey=null,oldestTs=Infinity;
  let oldestFinishedKey=null,oldestFinishedTs=Infinity;
  runState.forEach(function(r,k){
    const t=r.startedAt||0;
    if(t<oldestTs){oldestTs=t;oldestKey=k;}
    if(r.finishedAt&&t<oldestFinishedTs){oldestFinishedTs=t;oldestFinishedKey=k;}
  });
  const evict=oldestFinishedKey||oldestKey;
  if(!evict)return;
  runState.delete(evict);
  document.getElementById('rc-'+evict)?.remove();
}

// ── Smart scroll: only auto-scroll when user hasn't scrolled past first card ──
let userViewedHistory=false;
function maybeScrollToActivity(){
  const list=document.getElementById('run-list');
  if(!list)return;
  const firstCard=list.querySelector('.run-card');
  if(!firstCard)return;
  const rect=firstCard.getBoundingClientRect();
  // Consider user "inspecting history" if the first card is already fully visible
  // or if they have scrolled the activity section out of view upward
  if(rect.top>0&&rect.top<window.innerHeight*0.6){return;} // first card visible — no scroll
  if(!userViewedHistory){
    document.getElementById('activity').scrollIntoView({behavior:'smooth',block:'nearest'});
  }
}
document.addEventListener('scroll',function(){userViewedHistory=true;},{once:true,passive:true});

// ── Handle incoming SSE events ────────────────────────────────────────────────
function handlePipelineEvent(evt){
  // Ignore heartbeat comments
  if(!evt.data||evt.data.trim()==='')return;
  let d;try{d=JSON.parse(evt.data);}catch{return;}

  if(d.type==='start'&&d.snapshot){
    applySnapshot(d.snapshot);
    // For a retry run, replace the parent's slot in-place so the activity feed
    // shows one card whose state advances, not two stacked entries. Re-key the
    // existing DOM node to the new runId; the upcoming scheduleRender will then
    // outerHTML-replace it in place rather than prepending at top.
    if(d.snapshot.retriedFromRunId){
      const parentId=d.snapshot.retriedFromRunId;
      const parentEl=document.getElementById('rc-'+parentId);
      if(parentEl) parentEl.id='rc-'+d.runId;
      runState.delete(parentId);
      _dirtyRuns.delete(parentId);
    }
    scheduleRender(d.runId);scheduleWidgets();maybeScrollToActivity();
  }else if(d.type==='locale'&&d.locale){
    const run=runState.get(d.runId);if(!run)return;
    if(!run.locales)run.locales={};
    if(!run.localeOrder)run.localeOrder=[];
    if(!run.locales[d.locale.code])run.localeOrder.push(d.locale.code);
    run.locales[d.locale.code]=d.locale;
    // Refresh aggregate counters so ETA stays accurate as batches stream in.
    let done=0,total=0;
    run.localeOrder.forEach(function(c){const L=run.locales[c];done+=L.done||0;total+=L.total||0;});
    run.progressDone=done;run.progressTotal=total;
    scheduleRender(d.runId);
  }else if(d.type==='locales'&&Array.isArray(d.locales)){
    const run=runState.get(d.runId);if(!run)return;
    if(!run.locales)run.locales={};
    if(!run.localeOrder)run.localeOrder=[];
    d.locales.forEach(function(l){
      if(!run.locales[l.code])run.localeOrder.push(l.code);
      run.locales[l.code]=l;
    });
    run.progressDone=d.progressDone||0;run.progressTotal=d.progressTotal||0;
    scheduleRender(d.runId);
  }else if(d.type==='step'){
    const run=runState.get(d.runId);if(!run)return;
    if(!run.steps)run.steps={};
    run.steps[d.stepId]={status:d.status,detail:d.detail||null};
    scheduleRender(d.runId);scheduleWidgets();
  }else if(d.type==='finish'){
    const run=runState.get(d.runId);if(!run)return;
    run.finishedAt=d.finishedAt||Date.now();
    if(d.prUrl)run.prUrl=d.prUrl;if(d.error)run.error=d.error;
    if(d.surfaceSkipped)run.surfaceSkipped=d.surfaceSkipped;
    run.retryPending=false;
    scheduleRender(d.runId);scheduleWidgets();loadStats();
    if(!run.error)maybeShowConversionToast(run);
  }else if(d.type==='cdn_ready'){
    const run=runState.get(d.runId);
    if(run){scheduleRender(d.runId);}
    if(d.cdnBundleVersion){loadCdnStatus();}
    toast('Translations live on edge — SDK consumers will refresh on next launch');
  }else if(d.type==='notification'){
    if(typeof window.pushInAppNotification==='function')window.pushInAppNotification(d);
  }
}

// ── Retrigger a failed run ────────────────────────────────────────────────────
async function retriggerRun(runId){
  const run=runState.get(runId);
  if(!run||run.retryPending)return;
  run.retryPending=true;
  scheduleRender(runId); // show "Retrying…" button state immediately

  const res=await api('/pipeline/runs/'+encodeURIComponent(runId)+'/retry',{method:'POST'});
  if(!res||!res.ok){
    run.retryPending=false;
    scheduleRender(runId);
    toast('Failed to queue retry — please try again','error');
    return;
  }
  toast('Retry queued — watching for new run…');
  // The SSE "start" event for the new run will arrive shortly and clear retryPending via handlePipelineEvent
}

async function forceTranslateRun(runId){
  const run=runState.get(runId);
  if(!run||run.retryPending)return;
  run.retryPending=true;
  scheduleRender(runId);

  const res=await api('/pipeline/runs/'+encodeURIComponent(runId)+'/retry',{
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body:JSON.stringify({forceTranslate:true})
  });
  if(!res||!res.ok){
    run.retryPending=false;
    scheduleRender(runId);
    toast('Failed to queue retranslation — please try again','error');
    return;
  }
  toast('Retranslation queued — classifier overridden…');
}

// ── SSE connection via fetch (Bearer auth — no token in URL) ──────────────────
// Liveness contract:
//   • Server sends ": ping\n\n" every 25s (see ApiRoutes.kt /pipeline/events).
//   • Client watchdog reconnects if no bytes (data OR heartbeat) arrive for SSE_STALE_MS.
//   • Backgrounded tabs (document.hidden) close the stream so a hidden dashboard
//     costs nothing on Cloud Run; visibilitychange reopens it and refreshes the
//     run list via REST so events emitted during the gap aren't missed.
let sseBackoff=2000;
let sseInstance=null;  // holds {abort: fn} to cancel the current stream
let sseRetries=0;
let sseLastFrameAt=0;
let sseWatchdog=null;
let _sseHadGap=false;  // set true when an active SSE drops; consumed by loadPipelineRuns()

function showResumePill(n){
  let el=document.getElementById('sse-resume-pill');
  if(!el){
    el=document.createElement('div');
    el.id='sse-resume-pill';el.className='sse-resume-pill';
    el.innerHTML='<span class="sse-resume-dot"></span><span id="sse-resume-text"></span>';
    document.body.appendChild(el);
  }
  const txt=document.getElementById('sse-resume-text');
  if(txt)txt.textContent='Resumed — caught up on '+n+' update'+(n===1?'':'s');
  requestAnimationFrame(function(){el.classList.add('visible');});
  clearTimeout(el._t);
  el._t=setTimeout(function(){el.classList.remove('visible');},3500);
}
const SSE_MAX_RETRIES=5;
const SSE_STALE_MS=45000;  // server pings every 25s; allow ~2x before giving up

function setSseStatus(state,text){
  const el=document.getElementById('sse-status');
  const txt=document.getElementById('sse-status-text');
  if(!el)return;
  el.className='sse-status '+state;
  if(txt)txt.textContent=text;
}

function stopSseWatchdog(){
  if(sseWatchdog){clearInterval(sseWatchdog);sseWatchdog=null;}
}

function startSseWatchdog(){
  stopSseWatchdog();
  sseLastFrameAt=Date.now();
  sseWatchdog=setInterval(function(){
    if(Date.now()-sseLastFrameAt>SSE_STALE_MS){
      // No data or heartbeat for too long — assume the socket is half-open.
      if(sseInstance){sseInstance.abort();sseInstance=null;}
      stopSseWatchdog();
      scheduleReconnect();
    }
  },5000);
}

function scheduleReconnect(){
  sseInstance=null;
  stopSseWatchdog();
  _sseHadGap=true;
  sseRetries++;
  if(sseRetries>=SSE_MAX_RETRIES){setSseStatus('disconnected','Live updates unavailable');return;}
  setSseStatus('reconnecting','Reconnecting…');
  setTimeout(connectPipelineSSE,sseBackoff);
  sseBackoff=Math.min(sseBackoff*2,30000);
}

function connectPipelineSSE(){
  if(!token)return;
  if(document.hidden)return;  // hidden tabs hold no stream — visibilitychange will reopen
  if(sseInstance){sseInstance.abort();sseInstance=null;}
  const controller=new AbortController();
  sseInstance={abort:function(){controller.abort();}};
  fetch(BASE+'/pipeline/events',{headers:{'Authorization':'Bearer '+token},signal:controller.signal})
    .then(function(res){
      if(!res.ok){throw new Error('HTTP '+res.status);}
      sseBackoff=2000;sseRetries=0;setSseStatus('idle','');
      startSseWatchdog();
      // Refresh REST snapshot so any events emitted during the gap (backgrounded
      // tab, dropped connection) are still reflected in the UI.
      loadPipelineRuns();
      const reader=res.body.getReader();
      const dec=new TextDecoder();
      let buf='';
      function read(){
        reader.read().then(function(r){
          if(r.done){scheduleReconnect();return;}
          sseLastFrameAt=Date.now();  // any bytes (data or heartbeat) prove the link is alive
          buf+=dec.decode(r.value,{stream:true});
          const lines=buf.split('\n');
          buf=lines.pop();
          let data='';
          for(let i=0;i<lines.length;i++){
            const l=lines[i];
            if(l.startsWith('data: ')){data=l.slice(6);}
            else if(l===''&&data){handlePipelineEvent({data:data});data='';}
          }
          read();
        }).catch(function(e){if(e.name!=='AbortError')scheduleReconnect();});
      }
      read();
    })
    .catch(function(e){if(e.name!=='AbortError')scheduleReconnect();});
}

document.addEventListener('visibilitychange',function(){
  if(document.hidden){
    if(sseInstance){sseInstance.abort();sseInstance=null;}
    stopSseWatchdog();
    stopTimestampTicker();
    setSseStatus('idle','');
    _sseHadGap=true;
  }else{
    sseRetries=0;sseBackoff=2000;
    startTimestampTicker();
    connectPipelineSSE();
  }
});

async function loadPipelineRuns(){
  const res=await api('/pipeline/runs');if(!res||!res.ok)return;
  const data=await res.json();
  // Server returns newest-first (capped at MAX_RUNS server-side).
  const runs=(data.runs||[]).slice(0,RUN_CAP);if(!runs.length)return;
  // Resume-aware diff: count snapshot changes vs current in-memory state so we
  // can show the user a visible "caught up" signal after an SSE gap. Captures
  // brand-new runs and progress that advanced while disconnected.
  let caughtUp=0;
  runs.forEach(function(s){
    const prev=runState.get(s.runId);
    if(!prev){caughtUp++;return;}
    if((s.progressDone||0)>(prev.progressDone||0))caughtUp++;
    else if(s.finishedAt&&!prev.finishedAt)caughtUp++;
  });
  if(_sseHadGap&&caughtUp>0){
    showResumePill(caughtUp);
  }
  _sseHadGap=false;
  runs.forEach(applySnapshot);
  // Collapse retry chains: a retry run supersedes its parent in the feed, so
  // drop parents that have a retry present. Matches the live SSE behavior in
  // handlePipelineEvent where a 'start' with retriedFromRunId re-keys the
  // parent's card to the new runId.
  const retriedParents=new Set();
  runs.forEach(function(s){if(s.retriedFromRunId)retriedParents.add(s.retriedFromRunId);});
  retriedParents.forEach(function(id){runState.delete(id);});
  const visibleRuns=runs.filter(function(s){return !retriedParents.has(s.runId);});
  const list=document.getElementById('run-list');
  if(list){
    document.getElementById('activity-empty')?.remove();
    // Build the full list in newest-first order in one DOM write — the prior
    // per-card insertAdjacentHTML('afterbegin') loop reversed the order.
    list.innerHTML=visibleRuns.map(function(s){return buildRunHtml(s.runId);}).join('');
  }
  updateRunSummaryWidget();
}

async function loadCdnStatus(){
  const res=await api('/dashboard/cdn-status');
  if(!res||!res.ok)return;
  const data=await res.json();
  updateCdnWidget(data.publishes||[]);
}

function updateCdnWidget(publishes){
  const body=document.getElementById('cdn-widget-body');
  const badge=document.getElementById('cdn-widget-badge');
  if(!body)return;
  if(!publishes||!publishes.length){
    body.innerHTML='<div class="cdnw-empty">No CDN publish yet &mdash; push a commit to start.</div>';
    if(badge){badge.textContent='';badge.className='cdn-widget-badge';}
    return;
  }
  const sorted=publishes.slice().sort(function(a,b){return(b.publishedAt||0)-(a.publishedAt||0);});
  const liveCount=sorted.filter(function(p){return p.status==='success';}).length;
  if(badge){
    if(liveCount===0){badge.textContent='';badge.className='cdn-widget-badge';}
    else if(liveCount===sorted.length){badge.textContent='● Live';badge.className='cdn-widget-badge live';}
    else{badge.textContent='● '+liveCount+' live';badge.className='cdn-widget-badge live';}
  }
  const sdkNote='<div class="cdnw-sdk-note">'
    +'<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>'
    +' SDK consumers receive updated translations on next app launch or background refresh'
    +'</div>';
  const kvNote='<div class="cdnw-kv-note">Cloudflare KV &mdash; globally replicated</div>';
  if(sorted.length===1){
    const p=sorted[0];
    const ver=esc((p.bundleVersion||'').substring(0,12));
    const ago=timeAgo(p.publishedAt||Date.now());
    const locales=p.locales||[];
    const localeChips=locales.slice(0,8).map(function(l){return '<span class="cdnw-locale-chip">'+esc(l)+'</span>';}).join('');
    const moreLocales=locales.length>8?'<span class="cdnw-locale-chip cdnw-locale-more">+'+(locales.length-8)+'</span>':'';
    body.innerHTML=''
      +(p.projectName?'<div class="cdnw-proj-name">'+esc(p.projectName)+'</div>':'')
      +'<div class="cdnw-stat-row">'
      +'<div class="cdnw-stat"><span class="cdnw-stat-val">'+locales.length+'</span><span class="cdnw-stat-lbl">locales</span></div>'
      +'<div class="cdnw-stat"><span class="cdnw-stat-val cdnw-mono">'+ver+'</span><span class="cdnw-stat-lbl">bundle</span></div>'
      +'<div class="cdnw-stat"><span class="cdnw-stat-val">'+esc(ago)+'</span><span class="cdnw-stat-lbl">published</span></div>'
      +'</div>'
      +(localeChips?'<div class="cdnw-locales">'+localeChips+moreLocales+'</div>':'')
      +kvNote+sdkNote;
  }else{
    const rows=sorted.map(function(p){
      const isLive=p.status==='success';
      const localeCount=(p.locales||[]).length;
      const ago=timeAgo(p.publishedAt||Date.now());
      return '<div class="cdnw-proj-row">'
        +'<span class="cdnw-status-dot'+(isLive?' live':'')+'"></span>'
        +'<span class="cdnw-proj-label">'+esc(p.projectName||p.projectId)+'</span>'
        +'<span class="cdnw-proj-meta">'+localeCount+' locales</span>'
        +'<span class="cdnw-proj-time">'+esc(ago)+'</span>'
        +'</div>';
    }).join('');
    body.innerHTML='<div class="cdnw-proj-list">'+rows+'</div>'+kvNote+sdkNote;
  }
}

// ── Free-plan post-run conversion nudge ─────────────────────────────────────
// Triggered once per session when a free user sees a successful run finish.
// The point is timing: we surface "Solo is faster" at the moment the user has
// just experienced value, not on page load. sessionStorage scopes the cap to
// the current tab — a new session is a new opportunity.
function maybeShowConversionToast(run){
  try{
    if(sessionStorage.getItem('tl_conv_toast_shown'))return;
    if(!window.tlSubscription)return;
    window.tlSubscription().then(function(sub){
      if(!sub||sub.plan!=='FREE')return;
      sessionStorage.setItem('tl_conv_toast_shown','1');
      var durTxt='';
      if(run.finishedAt&&run.startedAt){
        var sec=Math.round((run.finishedAt-run.startedAt)/1000);
        durTxt='This run took <strong>'+(sec<60?sec+'s':Math.floor(sec/60)+'m '+(sec%60)+'s')+'</strong> on Free. ';
      }
      showConvBanner(durTxt+'Solo translates every locale in parallel — typically <strong>3–5× faster</strong>.');
    });
  }catch(_){}
}
function showConvBanner(msgHtml){
  var existing=document.querySelector('.conv-toast');if(existing)existing.remove();
  var el=document.createElement('div');el.className='conv-toast';
  el.innerHTML='<div class="conv-msg">'+msgHtml+'</div>'
    +'<a href="/transloom/billing" class="conv-cta">Upgrade →</a>'
    +'<button type="button" class="conv-close" aria-label="Dismiss">×</button>';
  document.body.appendChild(el);
  requestAnimationFrame(function(){el.classList.add('visible');});
  el.querySelector('.conv-close').addEventListener('click',function(){el.classList.remove('visible');setTimeout(function(){el.remove();},250);});
  setTimeout(function(){if(el.parentNode){el.classList.remove('visible');setTimeout(function(){el.remove();},250);}},14000);
}

loadStats();loadPlanWidget();loadPipelineRuns();loadCdnStatus();connectPipelineSSE();
"""

internal val NOTIFICATIONS_JS = """(function(){const NOTIF_BASE='/transloom/api/notifications';let notifState=[];let panelOpen=!1;function notifApi(path,opts){return fetch(NOTIF_BASE+path,{...opts,headers:{...authHeaders(),...(opts?.headers||{})}})}async function loadNotifications(){try{const res=await notifApi('');if(!res.ok)return;const d=await res.json();notifState=d.notifications||[];renderNotifBadge(d.unreadCount||0);renderNotifList()}catch{}}function renderNotifBadge(count){const bell=document.getElementById('notif-bell'),badge=document.getElementById('notif-badge');if(!bell||!badge)return;if(count>0){badge.textContent=count>9?'9+':String(count);badge.style.display='block';bell.classList.add('has-unread')}else{badge.style.display='none';bell.classList.remove('has-unread')}}function renderNotifList(){const list=document.getElementById('notif-list');if(!list)return;if(!notifState.length){list.innerHTML='<div class="notif-empty">No notifications yet</div>';return}list.innerHTML=notifState.map(n=>{const isUnread=!n.readAt,dotClass=isUnread?n.level||'info':'read',timeAgo=formatTimeAgo(n.createdAt),action=n.actionUrl?`<a class="notif-action-link" href="${'$'}{esc(n.actionUrl)}" onclick="event.stopPropagation()">${'$'}{esc(n.actionLabel||'View')}</a>`:'';return `<div class="notif-item${'$'}{isUnread?' unread':''}" onclick="onNotifClick('${'$'}{n.id}','${'$'}{esc(n.actionUrl||'')}')"><div class="notif-dot ${'$'}{dotClass}"></div><div class="notif-body"><div class="notif-title">${'$'}{esc(n.title)}</div><div class="notif-msg">${'$'}{esc(n.message)}</div><div class="notif-meta"><span class="notif-time">${'$'}{timeAgo}</span>${'$'}{action}</div></div></div>`}).join('')}function formatTimeAgo(ms){const diff=Date.now()-ms;if(diff<60000)return'just now';if(diff<3600000)return Math.floor(diff/60000)+'m ago';if(diff<86400000)return Math.floor(diff/3600000)+'h ago';return Math.floor(diff/86400000)+'d ago'}async function onNotifClick(id,actionUrl){const n=notifState.find(x=>x.id===id);if(n&&!n.readAt){n.readAt=Date.now();const unread=notifState.filter(x=>!x.readAt).length;renderNotifBadge(unread);renderNotifList();notifApi('/'+id+'/read',{method:'POST'}).catch(()=>{})}if(actionUrl){window.location.href=actionUrl}}async function markAllNotifsRead(){notifState.forEach(n=>{if(!n.readAt)n.readAt=Date.now()});renderNotifBadge(0);renderNotifList();try{await notifApi('/read-all',{method:'POST'})}catch{}}function toggleNotifPanel(){panelOpen?closeNotifPanel():openNotifPanel()}function openNotifPanel(){panelOpen=!0;document.getElementById('notif-panel')?.classList.add('open');document.getElementById('notif-overlay')?.classList.add('open')}function closeNotifPanel(){panelOpen=!1;document.getElementById('notif-panel')?.classList.remove('open');document.getElementById('notif-overlay')?.classList.remove('open')}window.pushInAppNotification=function(evt){const existing=notifState.find(n=>n.id===evt.notificationId);if(existing)return;const n={id:evt.notificationId,title:evt.notificationTitle,message:evt.notificationMessage,level:evt.notificationLevel||'info',actionUrl:evt.notificationActionUrl,actionLabel:evt.notificationActionLabel,createdAt:Date.now(),readAt:null};notifState.unshift(n);const unread=notifState.filter(x=>!x.readAt).length;renderNotifBadge(unread);renderNotifList();const bell=document.getElementById('notif-bell');if(bell){bell.classList.add('ringing');setTimeout(()=>bell.classList.remove('ringing'),600)}};window.toggleNotifPanel=toggleNotifPanel;window.closeNotifPanel=closeNotifPanel;window.markAllNotifsRead=markAllNotifsRead;loadNotifications()})();"""

internal const val ONBOARDING_CSS = """
.ob-overlay{position:fixed;inset:0;z-index:9000;pointer-events:none}
.ob-overlay.active{pointer-events:auto}
.ob-shade{position:fixed;background:rgba(8,10,14,.62);transition:opacity .18s;opacity:0}
.ob-overlay.active .ob-shade{opacity:1}
.ob-spot{position:fixed;border-radius:10px;box-shadow:0 0 0 3px rgba(0,229,160,.55),0 0 0 99999px rgba(8,10,14,.62);pointer-events:none;transition:all .22s cubic-bezier(.2,.7,.2,1);opacity:0}
.ob-overlay.active .ob-spot.visible{opacity:1}
.ob-pop{position:fixed;background:var(--surface,#15181d);border:1px solid var(--border,#262a31);border-radius:12px;padding:18px 18px 14px;width:340px;max-width:calc(100vw - 32px);box-shadow:0 16px 48px -12px rgba(0,0,0,.6);color:var(--text,#e6e7eb);z-index:9001;opacity:0;transform:translateY(4px);transition:opacity .18s,transform .18s}
.ob-overlay.active .ob-pop.visible{opacity:1;transform:translateY(0)}
.ob-pop-eyebrow{font-size:11px;font-weight:700;letter-spacing:.6px;text-transform:uppercase;color:var(--accent,#00e5a0);margin-bottom:6px}
.ob-pop-title{font-size:15px;font-weight:700;letter-spacing:-.2px;margin-bottom:6px;color:var(--text,#e6e7eb)}
.ob-pop-body{font-size:13px;line-height:1.5;color:var(--text-muted,#9aa0aa);margin-bottom:14px}
.ob-pop-body strong{color:var(--text,#e6e7eb);font-weight:600}
.ob-pop-meta{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:14px}
.ob-chip{display:inline-flex;align-items:center;gap:6px;font-size:11px;font-weight:600;padding:4px 10px;border-radius:20px;background:var(--surface2,#1c1f25);border:1px solid var(--border,#262a31);color:var(--text-dim,#c5c8cf)}
.ob-chip.warn{color:var(--yellow,#faad14);border-color:rgba(250,173,20,.3)}
.ob-pop-actions{display:flex;align-items:center;justify-content:space-between;gap:8px}
.ob-pop-progress{font-size:11px;color:var(--text-muted,#9aa0aa);letter-spacing:.4px}
.ob-pop-buttons{display:flex;gap:8px}
.ob-pop-btn{font-size:12px;font-weight:600;padding:7px 14px;border-radius:6px;cursor:pointer;border:1px solid transparent;background:transparent;color:var(--text-muted,#9aa0aa);transition:all .12s}
.ob-pop-btn:hover{color:var(--text,#e6e7eb)}
.ob-pop-btn.primary{background:var(--accent,#00e5a0);color:#06281d;border-color:var(--accent,#00e5a0)}
.ob-pop-btn.primary:hover{filter:brightness(1.08);color:#06281d}
.ob-pop-btn:focus-visible{outline:2px solid var(--accent,#00e5a0);outline-offset:2px}
.ob-resume-pill{position:fixed;bottom:24px;right:24px;z-index:8500;display:none;align-items:center;gap:10px;padding:10px 16px;background:var(--surface,#15181d);border:1px solid var(--accent,#00e5a0);border-radius:24px;font-size:13px;font-weight:600;color:var(--text,#e6e7eb);box-shadow:0 8px 28px -8px rgba(0,0,0,.5);cursor:pointer;transition:transform .15s}
.ob-resume-pill:hover{transform:translateY(-1px)}
.ob-resume-pill.visible{display:inline-flex}
.ob-resume-pill .ob-resume-dot{width:8px;height:8px;border-radius:50%;background:var(--accent,#00e5a0);box-shadow:0 0 0 4px rgba(0,229,160,.15)}
@media (prefers-reduced-motion:reduce){.ob-spot,.ob-pop{transition:none}}
"""

internal val ONBOARDING_JS = """(function(){if(window.Onboarding)return;var BASE='/transloom/api/onboarding',state=null,page='dashboard',steps=[],idx=0,prevFocus=null;function authHeaders(){var t=localStorage.getItem('transloom_token');return t?{'Authorization':'Bearer '+t,'Content-Type':'application/json'}:{'Content-Type':'application/json'}}function fetchState(){return fetch(BASE+'/state',{headers:authHeaders()}).then(function(r){return r.ok?r.json():null}).catch(function(){return null})}function postSkip(){return fetch(BASE+'/skip',{method:'POST',headers:authHeaders()}).catch(function(){})}function postResume(){return fetch(BASE+'/resume',{method:'POST',headers:authHeaders()}).catch(function(){})}function host(){var h=document.getElementById('ob-host');if(!h){h=document.createElement('div');h.id='ob-host';document.body.appendChild(h)}return h}function esc(s){var d=document.createElement('div');d.textContent=String(s==null?'':s);return d.innerHTML}function planLabel(p){return p==='SOLO'?'Solo':p==='TEAM'?'Team':p==='ENTERPRISE'?'Enterprise':'Free'}function buildSteps(){var planChip='<span class="ob-chip">Plan: <strong style="margin-left:4px">'+esc(planLabel(state.plan))+'</strong></span>',trialChip=state.inTrial?'<span class="ob-chip warn">Trial</span>':'',meta=planChip+trialChip;if(page==='dashboard'){return[{eyebrow:'Welcome',title:'Welcome to Transloom',body:'You\'re all set on the <strong>'+esc(planLabel(state.plan))+'</strong> plan. In the next two steps we\'ll connect your GitHub repo and watch your first translation run.',meta:meta,anchor:null,primary:{label:'Get started',action:'next'},secondary:{label:'Skip',action:'skip'}},{eyebrow:'Step 1 of 2',title:'Connect your first repository',body:'Click <strong>+ New project</strong> to point Transloom at your GitHub repo. We\'ll auto-install the webhook so every push triggers translation.',anchor:'#qa-new-project',primary:{label:'Open projects',action:'navigate',href:'/transloom/projects?ob=connect'},secondary:{label:'Skip',action:'skip'}}]}if(page==='projects'){return[{eyebrow:'Step 2 of 2',title:'Create your project',body:'Fill in your GitHub repo URL (e.g. <strong>owner/repo</strong>), pick a branch and target languages. We\'ll handle the webhook.',anchor:'#new-proj-btn',primary:{label:'Open form',action:'click-anchor'},secondary:{label:'Skip',action:'skip'}}]}return[]}function getAnchorRect(sel){if(!sel)return null;var el=document.querySelector(sel);if(!el)return null;el.scrollIntoView({block:'center',behavior:'smooth'});return el.getBoundingClientRect()}function positionPop(pop,rect){var pad=12,vw=window.innerWidth,vh=window.innerHeight,pw=pop.offsetWidth,ph=pop.offsetHeight;if(!rect){pop.style.left=Math.max(16,(vw-pw)/2)+'px';pop.style.top=Math.max(16,(vh-ph)/2)+'px';return}var preferBelow=(rect.bottom+pad+ph)<=vh-8,top=preferBelow?(rect.bottom+pad):Math.max(16,rect.top-pad-ph),left=Math.min(Math.max(16,rect.left+(rect.width-pw)/2),vw-pw-16);pop.style.left=left+'px';pop.style.top=top+'px'}function render(){cleanup();var step=steps[idx];if(!step)return;var overlay=document.createElement('div');overlay.className='ob-overlay active';overlay.setAttribute('role','dialog');overlay.setAttribute('aria-modal','true');overlay.setAttribute('aria-live','polite');var shade=document.createElement('div');shade.className='ob-shade';shade.style.inset='0';overlay.appendChild(shade);var spot=document.createElement('div');spot.className='ob-spot';overlay.appendChild(spot);var pop=document.createElement('div');pop.className='ob-pop';var meta=step.meta?'<div class="ob-pop-meta">'+step.meta+'</div>':'',progress=steps.length>1?(idx+1)+' / '+steps.length:'';pop.innerHTML='<div class="ob-pop-eyebrow">'+esc(step.eyebrow||'')+'</div>'+'<div class="ob-pop-title">'+esc(step.title)+'</div>'+'<div class="ob-pop-body">'+step.body+'</div>'+meta+'<div class="ob-pop-actions">'+'<span class="ob-pop-progress">'+esc(progress)+'</span>'+'<div class="ob-pop-buttons">'+(step.secondary?'<button type="button" class="ob-pop-btn" data-act="'+esc(step.secondary.action)+'">'+esc(step.secondary.label)+'</button>':'')+(step.primary?'<button type="button" class="ob-pop-btn primary" data-act="'+esc(step.primary.action)+'">'+esc(step.primary.label)+'</button>':'')+'</div></div>';overlay.appendChild(pop);host().appendChild(overlay);var rect=getAnchorRect(step.anchor);if(rect){var pad=8;spot.style.left=(rect.left-pad)+'px';spot.style.top=(rect.top-pad)+'px';spot.style.width=(rect.width+pad*2)+'px';spot.style.height=(rect.height+pad*2)+'px';spot.classList.add('visible')}requestAnimationFrame(function(){positionPop(pop,rect);pop.classList.add('visible')});var btns=pop.querySelectorAll('button[data-act]');btns.forEach(function(b){b.addEventListener('click',function(){handleAction(b.getAttribute('data-act'),step)})});var first=pop.querySelector('button.primary')||btns[0];if(first){prevFocus=document.activeElement;first.focus()}overlay._onKey=function(e){if(e.key==='Escape'){e.preventDefault();skip()}else if(e.key==='Tab'){var nodes=Array.prototype.slice.call(btns);if(!nodes.length)return;var i=nodes.indexOf(document.activeElement);if(e.shiftKey){if(i<=0){e.preventDefault();nodes[nodes.length-1].focus()}}else{if(i===nodes.length-1){e.preventDefault();nodes[0].focus()}}}};document.addEventListener('keydown',overlay._onKey);overlay._onResize=function(){positionPop(pop,getAnchorRect(step.anchor))};window.addEventListener('resize',overlay._onResize);window.addEventListener('scroll',overlay._onResize,!0)}function cleanup(){var h=document.getElementById('ob-host');if(!h)return;Array.prototype.slice.call(h.querySelectorAll('.ob-overlay')).forEach(function(o){if(o._onKey)document.removeEventListener('keydown',o._onKey);if(o._onResize){window.removeEventListener('resize',o._onResize);window.removeEventListener('scroll',o._onResize,!0)}o.remove()});if(prevFocus&&prevFocus.focus){try{prevFocus.focus()}catch(_){}prevFocus=null}}function handleAction(act,step){if(act==='next'){idx++;if(idx>=steps.length){finish()}else{render()}}else if(act==='skip'){skip()}else if(act==='navigate'){postSkip();window.location.href=step.primary.href}else if(act==='click-anchor'){var el=step.anchor&&document.querySelector(step.anchor);cleanup();if(el)el.click()}}function finish(){cleanup();renderResumePill(!1)}function skip(){postSkip();cleanup();renderResumePill(!0)}function renderResumePill(show){var existing=document.getElementById('ob-resume-pill');if(!show){if(existing)existing.classList.remove('visible');return}if(existing){existing.classList.add('visible');return}var pill=document.createElement('div');pill.id='ob-resume-pill';pill.className='ob-resume-pill';pill.setAttribute('role','button');pill.setAttribute('tabindex','0');pill.setAttribute('aria-label','Resume setup');pill.innerHTML='<span class="ob-resume-dot"></span><span>Resume setup</span>';function resume(){postResume().then(function(){pill.classList.remove('visible');state.dismissed=!1;steps=buildSteps();idx=0;render()})}pill.addEventListener('click',resume);pill.addEventListener('keydown',function(e){if(e.key==='Enter'||e.key===' '){e.preventDefault();resume()}});document.body.appendChild(pill);requestAnimationFrame(function(){pill.classList.add('visible')})}function shouldRun(){if(!state)return!1;if(state.completed)return!1;if(page==='dashboard')return state.step==='SIGNED_UP';if(page==='projects'){var fromDash=new URLSearchParams(window.location.search).get('ob')==='connect';return(state.step==='SIGNED_UP'&&!state.hasProject)||fromDash}return!1}var Onboarding={boot:function(pageName){page=pageName||'dashboard';fetchState().then(function(s){if(!s)return;state=s;if(state.dismissed&&!state.completed){renderResumePill(!0);return}if(!shouldRun())return;steps=buildSteps();idx=0;render()})},refresh:function(){fetchState().then(function(s){if(!s)return;state=s;if(state.completed){cleanup();renderResumePill(!1);return}if(state.step!=='SIGNED_UP'){cleanup()}})},cleanup:cleanup};window.Onboarding=Onboarding})();"""
