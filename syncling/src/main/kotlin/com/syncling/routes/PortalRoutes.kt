package com.syncling.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private fun ApplicationCall.issueBootstrapCookie() {
    request.cookies[SESSION_COOKIE]?.ifBlank { null }?.let { tok ->
        response.cookies.append(Cookie(
            name = "tl_token_bootstrap", value = tok,
            path = "/", maxAge = 15,
            httpOnly = false, secure = true, extensions = mapOf("SameSite" to "Lax")
        ))
    }
}

private const val FAVICON_SVG = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">
  <defs>
    <linearGradient id="favG" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%"   stop-color="#8B7EFF"/>
      <stop offset="100%" stop-color="#a78bfa"/>
    </linearGradient>
  </defs>
  <rect width="32" height="32" rx="8" fill="#080c18"/>
  <g stroke="url(#favG)" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round" fill="none">
    <path d="M 9.5 12.25 A 7.5 7.5 0 0 1 22.5 12.25"/>
    <polyline points="20.5,10.25 22.5,12.25 24.5,10.25"/>
    <path d="M 22.5 19.75 A 7.5 7.5 0 0 1 9.5 19.75"/>
    <polyline points="7.5,21.75 9.5,19.75 11.5,21.75"/>
  </g>
  <circle cx="16" cy="16" r="1.8" fill="#8B7EFF" opacity=".9"/>
</svg>"""

private const val SITE_WEBMANIFEST = """{
  "name": "Syncling",
  "short_name": "Syncling",
  "icons": [
    {"src": "/android-chrome-192x192.png", "sizes": "192x192", "type": "image/png"},
    {"src": "/android-chrome-512x512.png", "sizes": "512x512", "type": "image/png"},
    {"src": "/favicon.svg",                "sizes": "any",       "type": "image/svg+xml"}
  ],
  "theme_color": "#080c18",
  "background_color": "#080c18",
  "display": "standalone",
  "start_url": "/"
}"""

private fun generateFaviconPng(size: Int): ByteArray {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING,       RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,  RenderingHints.VALUE_STROKE_PURE)
    val s = size / 32.0
    // Background
    g.color = Color(0x08, 0x0c, 0x18)
    g.fill(RoundRectangle2D.Double(0.0, 0.0, size.toDouble(), size.toDouble(), 8.0 * s, 8.0 * s))
    // Accent-purple gradient strokes
    g.paint  = GradientPaint(0f, 0f, Color(0x8B, 0x7E, 0xFF), size.toFloat(), size.toFloat(), Color(0xa7, 0x8b, 0xfa))
    g.stroke = BasicStroke((2.8 * s).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    val cx = 16.0 * s
    val r  =  7.5 * s
    // Top arc  — center (16, 18.18), angles 222.3°→95.4° sweep
    g.draw(Arc2D.Double(cx - r, 18.18 * s - r, 2.0 * r, 2.0 * r, 222.3, 95.4, Arc2D.OPEN))
    g.draw(Path2D.Double().also { p ->
        p.moveTo(20.5 * s, 10.25 * s); p.lineTo(22.5 * s, 12.25 * s); p.lineTo(24.5 * s, 10.25 * s)
    })
    // Bottom arc — center (16, 13.82), angles 42.3°→95.4° sweep
    g.draw(Arc2D.Double(cx - r, 13.82 * s - r, 2.0 * r, 2.0 * r,  42.3, 95.4, Arc2D.OPEN))
    g.draw(Path2D.Double().also { p ->
        p.moveTo(7.5 * s, 21.75 * s); p.lineTo(9.5 * s, 19.75 * s); p.lineTo(11.5 * s, 21.75 * s)
    })
    // Center node
    g.paint = Color(0x8B, 0x7E, 0xFF, 230)
    val dr = 1.8 * s
    g.fill(Ellipse2D.Double(cx - dr, 16.0 * s - dr, 2.0 * dr, 2.0 * dr))
    g.dispose()
    return ByteArrayOutputStream().also { ImageIO.write(img, "PNG", it) }.toByteArray()
}

// Lazily rendered at first request; JVM lifetime cache
private val FAVICON_PNG_32  by lazy { generateFaviconPng(32) }
private val FAVICON_PNG_180 by lazy { generateFaviconPng(180) }
private val FAVICON_PNG_192 by lazy { generateFaviconPng(192) }
private val FAVICON_PNG_512 by lazy { generateFaviconPng(512) }

fun Route.configurePortalRoutes(jwtSecret: String) {
    // Landing page — canonical URL stays at /syncling for SEO
    route("/syncling") {
        get {
            if (call.request.headers[HttpHeaders.Host]?.substringBefore(':') == "data.androidplay.in") {
                call.respondRedirect("https://syncling.space/syncling", permanent = false)
                return@get
            }
            if (call.sessionUserId(jwtSecret) != null) {
                call.respondRedirect("/app")
                return@get
            }
            call.respondHtml { landingPage() }
        }
        get("/docs") {
            if (call.request.headers[HttpHeaders.Host]?.substringBefore(':') == "data.androidplay.in") {
                call.respondRedirect("https://syncling.space/docs", permanent = false)
                return@get
            }
            call.respondHtml { docsPage() }
        }
        get("/favicon.svg") {
            call.respondText(FAVICON_SVG, ContentType("image", "svg+xml"))
        }
    }
    // Top-level docs and favicons for clean URLs on syncling.space
    get("/docs") {
        call.respondHtml { docsPage() }
    }
    get("/favicon.svg") {
        call.respondText(FAVICON_SVG, ContentType("image", "svg+xml"))
    }
    get("/favicon-32x32.png") {
        call.respondBytes(FAVICON_PNG_32, ContentType.Image.PNG)
    }
    get("/apple-touch-icon.png") {
        call.respondBytes(FAVICON_PNG_180, ContentType.Image.PNG)
    }
    get("/android-chrome-192x192.png") {
        call.respondBytes(FAVICON_PNG_192, ContentType.Image.PNG)
    }
    get("/android-chrome-512x512.png") {
        call.respondBytes(FAVICON_PNG_512, ContentType.Image.PNG)
    }
    get("/site.webmanifest") {
        call.respondText(SITE_WEBMANIFEST, ContentType.Application.Json)
    }
    // App pages — no /syncling/ prefix
    get("/app") {
        val sessionUserId = call.sessionUserId(jwtSecret)
        if (sessionUserId == null) {
            call.respondRedirect("/auth/github")
            return@get
        }

        val pendingPlan = call.request.cookies[PENDING_PLAN_COOKIE]
        if (!pendingPlan.isNullOrBlank()) {
            call.respondRedirect("/billing/checkout?plan=$pendingPlan")
            return@get
        }

        call.issueBootstrapCookie()
        call.respondHtml { dashboardApp() }
    }
    get("/welcome") {
        if (call.sessionUserId(jwtSecret) == null) {
            call.respondRedirect("/auth/github")
            return@get
        }
        call.respondHtml { welcomePage() }
    }
    get("/billing") {
        if (call.sessionUserId(jwtSecret) == null) {
            call.respondRedirect("/auth/github")
            return@get
        }
        call.issueBootstrapCookie()
        call.respondHtml { billingApp() }
    }
    get("/billing/analytics") {
        if (call.sessionUserId(jwtSecret) == null) {
            call.respondRedirect("/auth/github")
            return@get
        }
        call.issueBootstrapCookie()
        call.respondHtml { billingAnalyticsApp() }
    }
    get("/projects") {
        if (call.sessionUserId(jwtSecret) == null) {
            call.respondRedirect("/auth/github")
            return@get
        }
        call.issueBootstrapCookie()
        call.respondHtml { projectsApp() }
    }
    get("/review-portal") {
        if (call.sessionUserId(jwtSecret) == null) {
            call.respondRedirect("/auth/github")
            return@get
        }
        call.issueBootstrapCookie()
        call.respondHtml { reviewPortal() }
    }
    // Members landing — no projectId. Client will pick the first project after
    // it loads /api/projects; if there are none, the server-rendered empty state shows.
    get("/members") {
        if (call.sessionUserId(jwtSecret) == null) {
            call.respondRedirect("/auth/github")
            return@get
        }
        call.issueBootstrapCookie()
        call.respondHtml { membersApp(projectId = null) }
    }
    get("/members/{projectId}") {
        if (call.sessionUserId(jwtSecret) == null) {
            call.respondRedirect("/auth/github")
            return@get
        }
        call.issueBootstrapCookie()
        call.respondHtml { membersApp(projectId = call.parameters["projectId"]) }
    }
    // Public invite landing — no session required. The page itself decides whether
    // to offer Accept (logged in) or Continue with GitHub (logged out).
    get("/invite/{token}") {
        call.respondHtml { invitePage() }
    }
}

internal fun HEAD.favicon() {
    link { rel = "icon";             type = "image/svg+xml"; href = "/favicon.svg" }
    link { rel = "icon";             type = "image/png";     sizes = "32x32";   href = "/favicon-32x32.png" }
    link { rel = "apple-touch-icon"; sizes = "180x180";      href = "/apple-touch-icon.png" }
    link { rel = "manifest";         href = "/site.webmanifest" }
    meta(name = "theme-color",              content = "#080c18")
    meta(name = "msapplication-TileColor", content = "#080c18")
}

private const val LOGO_SVG = """
<svg class="brand-mark" viewBox="0 0 32 32" width="26" height="26" aria-hidden="true" focusable="false">
  <defs>
    <linearGradient id="slGrad" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%"   stop-color="#8B7EFF"/>
      <stop offset="100%" stop-color="#a78bfa"/>
    </linearGradient>
  </defs>
  <rect width="32" height="32" rx="8" fill="#1a2b3c"/>
  <g class="sync-arrows" stroke="url(#slGrad)" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round" fill="none">
    <path d="M 9.5 12.25 A 7.5 7.5 0 0 1 22.5 12.25"/>
    <polyline points="20.5,10.25 22.5,12.25 24.5,10.25"/>
    <path d="M 22.5 19.75 A 7.5 7.5 0 0 1 9.5 19.75"/>
    <polyline points="7.5,21.75 9.5,19.75 11.5,21.75"/>
  </g>
  <circle cx="16" cy="16" r="1.8" fill="#8B7EFF" opacity=".9"/>
</svg>
"""

internal fun appSidebar(active: String, reviewBadge: Boolean = false) = """
<aside class="sidebar" id="app-sidebar">
  <div class="sidebar-head">
    <div class="sidebar-logo brand">$LOGO_SVG<span class="brand-text"><span class="brand-sync">Sync</span>ling</span></div>
    <button type="button" class="sidebar-toggle" id="sidebar-toggle" onclick="toggleSidebar()" aria-label="Collapse sidebar" title="Collapse sidebar">
      <svg class="sb-toggle-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
    </button>
  </div>
  <nav class="sidebar-nav">
    <a href="/app" class="nav-item${if (active=="dash") " active" else ""}" title="Dashboard">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
      <span class="nav-label">Dashboard</span>
    </a>
    <a href="/projects" class="nav-item${if (active=="projects") " active" else ""}" title="Projects">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
      <span class="nav-label">Projects</span>
    </a>
    <a href="/members" class="nav-item${if (active=="members") " active" else ""}" title="Members">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
      <span class="nav-label">Members</span>
    </a>
    <a href="/review-portal" class="nav-item${if (active=="review") " active" else ""}" title="Review">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
      <span class="nav-label">Review</span>${if (reviewBadge) """<span class="nav-badge review-badge" id="review-count"></span>""" else ""}
    </a>
    <a href="/billing/analytics" class="nav-item${if (active=="analytics") " active" else ""}" title="Analytics">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>
      <span class="nav-label">Analytics</span>
    </a>
    <a href="/billing" class="nav-item${if (active=="billing") " active" else ""}" title="Billing">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg>
      <span class="nav-label">Billing</span>
    </a>
    <a href="/tokens" class="nav-item${if (active=="tokens") " active" else ""}" title="API Tokens">
      <svg class="nav-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg>
      <span class="nav-label">API Tokens</span>
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
  --accent:#8B7EFF;--accent-dim:rgba(139,126,255,.12);--accent-dim2:rgba(139,126,255,.06);
  --text:#f0f0f0;--text-muted:#666;--text-dim:#999;
  --red:#ff4d4f;--yellow:#faad14;--radius:10px;--radius-sm:6px;
  --icon-bg:#1a2b3c;
  --lp-nav-bg:rgba(8,8,8,.88);--lp-nav-mobile:rgba(8,8,8,.97);
  --lp-text-soft:#555;--lp-text-muted2:#444;--lp-text-faint:#333;--lp-text-faintest:#252525;--lp-label-color:#3a3a3a;
  --lp-term-bg:#060606;--lp-term-header:#0a0a0a;--lp-term-title:#232323;--lp-term-text:#3a3a3a;--lp-term-border:#161616;--lp-term-cmd:#e2e8f0;--lp-term-dim:#222;--lp-term-final:#121212;
  --lp-code-bg:#040404;--lp-code-border:#181818;--lp-code-text:#3a3a3a;--lp-code-comment:#1e1e1e;
  --lp-chip-text:#444;--lp-chip-bg:rgba(255,255,255,.025);--lp-chip-border:rgba(255,255,255,.07);
  --lp-stats-bg:rgba(255,255,255,.015);--lp-stats-border:rgba(139,126,255,.12);
  --lp-card-border:rgba(255,255,255,.05);--lp-pricing-border:rgba(255,255,255,.06);
  --lp-cli-block:#0e0e0e;--lp-cli-out:#2a2a2a;
}
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
html{scroll-behavior:smooth}
body{background:var(--bg);color:var(--text);font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif;font-size:15px;line-height:1.6;-webkit-font-smoothing:antialiased}
a{color:var(--accent);text-decoration:none}
button,.btn{cursor:pointer;border:none;font-family:inherit;font-size:14px;font-weight:500;border-radius:var(--radius-sm);transition:background-color .2s ease,color .2s ease,transform .2s ease,box-shadow .2s ease,border-color .2s ease}
.btn-primary{background:var(--accent);color:#fff;padding:10px 20px;box-shadow:0 1px 0 rgba(0,0,0,.06),0 0 0 0 rgba(139,126,255,.0)}
.btn-primary:hover{background:#7A6DEE;color:#fff;transform:translateY(-1px);box-shadow:0 8px 24px -8px rgba(139,126,255,.45)}
.btn-ghost{background:transparent;color:var(--text-muted);padding:10px 20px;border:1px solid var(--border)}
.btn-ghost:hover{border-color:var(--accent);color:var(--accent);transform:translateY(-1px)}
.card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:20px;transition:border-color .25s ease,transform .25s ease,box-shadow .25s ease}.card:hover{border-color:rgba(139,126,255,.35);box-shadow:0 0 0 1px rgba(139,126,255,.08),0 8px 32px -8px rgba(139,126,255,.15)}
.brand{display:inline-flex;align-items:center;gap:10px;font-size:17px;font-weight:700;color:var(--text);letter-spacing:-.2px}.brand-text{font-family:'Jost',sans-serif;font-weight:700;letter-spacing:-.4px}.brand-sync{color:var(--accent)}
.brand-mark{flex-shrink:0;display:block;filter:drop-shadow(0 2px 8px rgba(139,126,255,.2));transition:filter .35s ease}
.brand-mark rect{fill:var(--icon-bg)}
.brand-mark .sync-arrows{animation:syncPulse 3s ease-in-out infinite}
.brand:hover .brand-mark,a:hover>.brand-mark{filter:drop-shadow(0 6px 18px rgba(139,126,255,.45))}
@keyframes syncPulse{0%,100%{opacity:.82}50%{opacity:1}}
@media(prefers-reduced-motion:reduce){.brand-mark .sync-arrows{animation:none}}
.fade-up{opacity:0;transform:translateY(18px);transition:opacity .65s cubic-bezier(.2,.7,.2,1),transform .65s cubic-bezier(.2,.7,.2,1)}
.fade-up.in-view{opacity:1;transform:translateY(0)}
.fade-up.d1{transition-delay:.08s}.fade-up.d2{transition-delay:.16s}.fade-up.d3{transition-delay:.24s}.fade-up.d4{transition-delay:.32s}
@media (prefers-reduced-motion:reduce){.fade-up{opacity:1;transform:none;transition:none}*,*::before,*::after{animation-duration:.001ms!important;transition-duration:.001ms!important}}
.badge{display:inline-block;background:var(--accent-dim);color:var(--accent);border:1px solid rgba(139,126,255,.3);border-radius:20px;padding:3px 12px;font-size:12px;font-weight:600;letter-spacing:.5px}
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
.notif-bell.has-unread{color:var(--accent);border-color:rgba(139,126,255,.4);background:var(--accent-dim2)}
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
.notif-item.unread{background:rgba(139,126,255,.04)}
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
html[data-theme="light"]{--bg:#f5f7fa;--surface:#ffffff;--surface2:#f0f4f8;--border:#e2e8f0;--text:#0f172a;--text-muted:#64748b;--text-dim:#94a3b8;--icon-bg:#1a2b3c;--lp-nav-bg:rgba(245,247,250,.92);--lp-nav-mobile:rgba(245,247,250,.98);--lp-text-soft:#64748b;--lp-text-muted2:#64748b;--lp-text-faint:#94a3b8;--lp-text-faintest:#94a3b8;--lp-label-color:#64748b;--lp-term-bg:#1e2a3a;--lp-term-header:#162030;--lp-term-title:#334155;--lp-term-text:#cbd5e1;--lp-term-border:#2d3f52;--lp-term-cmd:#f1f5f9;--lp-term-dim:#64748b;--lp-term-final:#2d3f52;--lp-code-bg:#1e2a3a;--lp-code-border:#2d3f52;--lp-code-text:#94a3b8;--lp-code-comment:#475569;--lp-chip-text:#64748b;--lp-chip-bg:rgba(15,23,42,.04);--lp-chip-border:rgba(15,23,42,.1);--lp-stats-bg:rgba(15,23,42,.03);--lp-stats-border:rgba(139,126,255,.2);--lp-card-border:rgba(15,23,42,.08);--lp-pricing-border:rgba(15,23,42,.1);--lp-cli-block:#e2e8f0;--lp-cli-out:#64748b}
@media(prefers-color-scheme:light){html:not([data-theme="dark"]){--bg:#f5f7fa;--surface:#ffffff;--surface2:#f0f4f8;--border:#e2e8f0;--text:#0f172a;--text-muted:#64748b;--text-dim:#94a3b8;--icon-bg:#1a2b3c;--lp-nav-bg:rgba(245,247,250,.92);--lp-nav-mobile:rgba(245,247,250,.98);--lp-text-soft:#64748b;--lp-text-muted2:#64748b;--lp-text-faint:#94a3b8;--lp-text-faintest:#94a3b8;--lp-label-color:#64748b;--lp-term-bg:#1e2a3a;--lp-term-header:#162030;--lp-term-title:#334155;--lp-term-text:#cbd5e1;--lp-term-border:#2d3f52;--lp-term-cmd:#f1f5f9;--lp-term-dim:#64748b;--lp-term-final:#2d3f52;--lp-code-bg:#1e2a3a;--lp-code-border:#2d3f52;--lp-code-text:#94a3b8;--lp-code-comment:#475569;--lp-chip-text:#64748b;--lp-chip-bg:rgba(15,23,42,.04);--lp-chip-border:rgba(15,23,42,.1);--lp-stats-bg:rgba(15,23,42,.03);--lp-stats-border:rgba(139,126,255,.2);--lp-card-border:rgba(15,23,42,.08);--lp-pricing-border:rgba(15,23,42,.1);--lp-cli-block:#e2e8f0;--lp-cli-out:#64748b}}
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
        title { +"Welcome to Syncling" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        favicon()
        link(rel = "preconnect", href = "https://fonts.googleapis.com")
        link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "" }
        link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Jost:wght@700&display=swap")
        style { unsafe { +"$SHARED_CSS$LANDING_CSS$WELCOME_CSS" } }
    }
    body {
        nav {
            div("nav-inner") {
                a("/syncling") { div("brand") { unsafe { +LOGO_SVG }; span { unsafe { +"<span class=\"brand-sync\">Sync</span>ling" } } } }
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
                        a("/app") { classes = setOf("pricing-cta", "outline"); +"Continue free →" }
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
                        a("/billing/start-subscription?plan=SOLO") { classes = setOf("pricing-cta", "accent"); +"Start 7-day free trial" }
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
                        a("/billing/start-subscription?plan=TEAM") { classes = setOf("pricing-cta", "outline"); +"Start 7-day free trial" }
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
<svg viewBox="0 0 1200 520" xmlns="http://www.w3.org/2000/svg" style="width:100%;max-width:1200px;display:block;margin:0 auto;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif" aria-label="Syncling System Architecture">
  <defs>
    <marker id="arr" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
      <path d="M0,1 L7,4 L0,7 Z" fill="rgba(139,126,255,.65)"/>
    </marker>
    <marker id="arrBypass" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
      <path d="M0,1 L7,4 L0,7 Z" fill="rgba(139,126,255,.3)"/>
    </marker>
    <marker id="arrFan" markerWidth="7" markerHeight="7" refX="6" refY="3.5" orient="auto">
      <path d="M0,1 L6,3.5 L0,6 Z" fill="rgba(139,126,255,.45)"/>
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
  <rect x="168" y="20" width="776" height="490" rx="12" fill="rgba(139,126,255,.01)" stroke="rgba(139,126,255,.16)" stroke-width="1"/>
  <rect x="952" y="20" width="240" height="490" rx="12" fill="rgba(100,130,255,.013)" stroke="rgba(100,130,255,.2)" stroke-width="1"/>

  <text x="84" y="13" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="2" fill="rgba(255,255,255,.2)">YOUR CODE</text>
  <text x="556" y="13" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="2.5" fill="rgba(139,126,255,.45)">SYNCLING PIPELINE</text>
  <text x="1072" y="13" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="2" fill="rgba(110,130,255,.45)">GLOBAL DELIVERY</text>

  <rect x="14" y="82" width="140" height="88" rx="10" fill="#111" stroke="#282828" stroke-width="1.2"/>
  <circle cx="46" cy="126" r="16" fill="#181818" stroke="#2e2e2e" stroke-width="1.2"/>
  <text x="46" y="131" text-anchor="middle" font-size="13" fill="#555">&lt;/&gt;</text>
  <text x="72" y="117" font-size="13" font-weight="600" fill="#c8c8c8">GitHub</text>
  <text x="72" y="133" font-size="11" fill="#454545">push · webhook</text>
  <text x="72" y="150" font-size="10" fill="#333">git push origin main</text>

  <line x1="154" y1="126" x2="188" y2="126" stroke="rgba(139,126,255,.5)" stroke-width="2" stroke-dasharray="5 3" marker-end="url(#arr)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.85s" repeatCount="indefinite"/>
  </line>

  <rect x="190" y="74" width="130" height="104" rx="11" fill="#061612" stroke="rgba(139,126,255,.75)" stroke-width="2" filter="url(#brandGlow)"/>
  <rect x="185" y="69" width="140" height="114" rx="14" fill="none" stroke="rgba(139,126,255,.08)" stroke-width="2.5">
    <animate attributeName="opacity" values="1;0.05;1" dur="3.2s" repeatCount="indefinite"/>
  </rect>
  <circle cx="222" cy="126" r="18" fill="rgba(139,126,255,.1)" stroke="rgba(139,126,255,.6)" stroke-width="1.5"/>
  <line x1="214" y1="119" x2="230" y2="119" stroke="#8B7EFF" stroke-width="2.5" stroke-linecap="round"/>
  <line x1="222" y1="119" x2="222" y2="133" stroke="#8B7EFF" stroke-width="2.5" stroke-linecap="round"/>
  <text x="249" y="114" font-size="14" font-weight="700" fill="#8B7EFF">Syncling</text>
  <text x="249" y="130" font-size="11" fill="rgba(139,126,255,.7)">Hub + Queue</text>
  <text x="249" y="146" font-size="10" fill="rgba(139,126,255,.38)">Redis pub-sub</text>
  <text x="249" y="161" font-size="10" fill="rgba(139,126,255,.25)">SSE live updates</text>

  <line x1="320" y1="126" x2="336" y2="126" stroke="rgba(139,126,255,.5)" stroke-width="2" stroke-dasharray="5 3" marker-end="url(#arr)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.85s" repeatCount="indefinite" begin="0.2s"/>
  </line>

  <rect x="338" y="82" width="118" height="88" rx="10" fill="#111" stroke="rgba(139,126,255,.28)" stroke-width="1.2"/>
  <circle cx="368" cy="126" r="16" fill="rgba(139,126,255,.07)" stroke="rgba(139,126,255,.38)" stroke-width="1.2"/>
  <text x="368" y="131" text-anchor="middle" font-size="15" fill="rgba(139,126,255,.8)">✦</text>
  <text x="394" y="117" font-size="13" font-weight="600" fill="#c8c8c8">Detect Δ</text>
  <text x="394" y="133" font-size="11" fill="#454545">semantic vs</text>
  <text x="394" y="148" font-size="11" fill="#454545">surface change</text>

  <line x1="456" y1="126" x2="472" y2="126" stroke="rgba(139,126,255,.5)" stroke-width="2" stroke-dasharray="5 3" marker-end="url(#arr)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.85s" repeatCount="indefinite" begin="0.4s"/>
  </line>

  <rect x="474" y="82" width="112" height="88" rx="10" fill="#111" stroke="rgba(139,126,255,.22)" stroke-width="1.2"/>
  <circle cx="502" cy="126" r="16" fill="rgba(139,126,255,.06)" stroke="rgba(139,126,255,.32)" stroke-width="1.2"/>
  <path d="M494 120 L510 120 L510 132 L494 132 Z M497 120 L497 117 M507 120 L507 117" fill="none" stroke="rgba(139,126,255,.65)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
  <text x="528" y="117" font-size="13" font-weight="600" fill="#c8c8c8">Billing ✓</text>
  <text x="528" y="133" font-size="11" fill="#454545">plan limits</text>
  <text x="528" y="148" font-size="11" fill="#454545">quota check</text>

  <line x1="586" y1="126" x2="602" y2="126" stroke="rgba(139,126,255,.5)" stroke-width="2" stroke-dasharray="5 3" marker-end="url(#arr)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.85s" repeatCount="indefinite" begin="0.6s"/>
  </line>

  <rect x="604" y="82" width="122" height="88" rx="10" fill="#111" stroke="rgba(139,126,255,.28)" stroke-width="1.2"/>
  <circle cx="634" cy="126" r="16" fill="rgba(139,126,255,.07)" stroke="rgba(139,126,255,.38)" stroke-width="1.2"/>
  <text x="634" y="131" text-anchor="middle" font-size="15" fill="rgba(139,126,255,.85)">◆</text>
  <text x="660" y="117" font-size="13" font-weight="600" fill="#c8c8c8">AI Translate</text>
  <text x="660" y="133" font-size="11" fill="#454545">Gemini Flash</text>
  <text x="660" y="148" font-size="11" fill="#454545">batch · 20+ langs</text>

  <line x1="726" y1="126" x2="742" y2="126" stroke="rgba(139,126,255,.5)" stroke-width="2" stroke-dasharray="5 3" marker-end="url(#arr)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.85s" repeatCount="indefinite" begin="0.8s"/>
  </line>

  <rect x="744" y="82" width="112" height="88" rx="10" fill="#111" stroke="rgba(139,126,255,.28)" stroke-width="1.2"/>
  <circle cx="773" cy="126" r="16" fill="rgba(139,126,255,.07)" stroke="rgba(139,126,255,.38)" stroke-width="1.2"/>
  <path d="M764 126 Q773 118 782 126 Q773 134 764 126 Z M773 126 m-2.5,0 a2.5,2.5 0 1,1 5,0 a2.5,2.5 0 1,1 -5,0" fill="none" stroke="rgba(139,126,255,.75)" stroke-width="1.6"/>
  <text x="799" y="117" font-size="13" font-weight="600" fill="#c8c8c8">Review</text>
  <text x="799" y="133" font-size="11" fill="#454545">cultural check</text>
  <text x="799" y="148" font-size="11" fill="#454545">approve · lock</text>

  <line x1="856" y1="126" x2="874" y2="126" stroke="rgba(139,126,255,.5)" stroke-width="2" stroke-dasharray="5 3" marker-end="url(#arr)">
    <animate attributeName="stroke-dashoffset" from="0" to="-16" dur="0.85s" repeatCount="indefinite" begin="1s"/>
  </line>

  <rect x="876" y="82" width="68" height="88" rx="10" fill="#0a1210" stroke="rgba(139,126,255,.55)" stroke-width="1.8"/>
  <text x="910" y="114" text-anchor="middle" font-size="17" fill="rgba(139,126,255,.9)">↑</text>
  <text x="910" y="134" text-anchor="middle" font-size="11" font-weight="700" fill="#c8c8c8">CDN</text>
  <text x="910" y="149" text-anchor="middle" font-size="10" fill="rgba(139,126,255,.6)">Publish</text>
  <text x="910" y="162" text-anchor="middle" font-size="9.5" fill="#333">~45s</text>

  <line x1="944" y1="126" x2="960" y2="126" stroke="rgba(139,126,255,.7)" stroke-width="2.2" stroke-dasharray="5 3" marker-end="url(#arrEdge)">
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

  <path d="M 1000,186 L 1000,210" fill="none" stroke="rgba(139,126,255,.45)" stroke-width="1.8" stroke-dasharray="4 3" marker-end="url(#arrFan)">
    <animate attributeName="stroke-dashoffset" from="0" to="-14" dur="0.9s" repeatCount="indefinite"/>
  </path>
  <path d="M 1120,186 L 1120,210" fill="none" stroke="rgba(139,126,255,.45)" stroke-width="1.8" stroke-dasharray="4 3" marker-end="url(#arrFan)">
    <animate attributeName="stroke-dashoffset" from="0" to="-14" dur="0.9s" repeatCount="indefinite" begin="0.3s"/>
  </path>
  <path d="M 1062,186 L 1062,350" fill="none" stroke="rgba(139,126,255,.3)" stroke-width="1.4" stroke-dasharray="4 3" marker-end="url(#arrFan)">
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

  <path d="M 397,170 L 397,246 L 900,246 L 900,170" fill="none" stroke="rgba(139,126,255,.2)" stroke-width="1.4" stroke-dasharray="7 5" marker-end="url(#arrBypass)">
    <animate attributeName="stroke-dashoffset" from="0" to="-24" dur="2.4s" repeatCount="indefinite"/>
  </path>
  <rect x="550" y="237" width="194" height="18" rx="4" fill="#090909" stroke="rgba(139,126,255,.1)" stroke-width="0.8"/>
  <text x="647" y="250" text-anchor="middle" font-size="10.5" fill="rgba(139,126,255,.35)">surface change only → skip translation</text>

  <path d="M 800,170 Q 820,220 800,278" fill="none" stroke="rgba(160,160,180,.2)" stroke-width="1.2" stroke-dasharray="5 4" marker-end="url(#arrSvc)"/>

  <text x="84"  y="506" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="1" fill="#2e2e2e">INGEST</text>
  <text x="256" y="506" text-anchor="middle" font-size="9" font-weight="700" letter-spacing="1" fill="rgba(139,126,255,.35)">PROCESS</text>
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
  var themeBtn=document.getElementById('theme-toggle');
  function getTheme(){
    var saved=localStorage.getItem('syncling-theme');
    if(saved)return saved;
    return window.matchMedia('(prefers-color-scheme:light)').matches?'light':'dark';
  }
  function applyTheme(t){
    document.documentElement.setAttribute('data-theme',t);
    localStorage.setItem('syncling-theme',t);
  }
  applyTheme(getTheme());
  if(themeBtn){
    themeBtn.addEventListener('click',function(){
      var cur=document.documentElement.getAttribute('data-theme')||'dark';
      applyTheme(cur==='dark'?'light':'dark');
    });
  }

  // smart nav CTA: console for authenticated, get started for unauthenticated
  var navCta=document.getElementById('nav-cta-btn');
  var authToken=localStorage.getItem('syncling_token');
  if(navCta&&authToken){navCta.textContent='Console';navCta.href='/app';}

  var params=new URLSearchParams(window.location.search);
  if(params.get('billing_error')==='link_failed'){
    var sub=params.get('sub')||'';
    var banner=document.createElement('div');
    banner.style.cssText='position:fixed;top:0;left:0;right:0;background:#3a1a1a;border-bottom:1px solid #ff4d4f;color:#ffb8b8;padding:14px 24px;text-align:center;font-size:14px;z-index:9999;line-height:1.5';
    banner.innerHTML='<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="display:inline;vertical-align:-3px;margin-right:6px"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>We received your payment but couldn'+String.fromCharCode(39)+'t link it to your account. Please email <a href="mailto:support@androidplay.in?subject=Subscription%20'+sub+'" style="color:#fff;text-decoration:underline">support@androidplay.in</a> with reference: <code style="background:rgba(255,255,255,.1);padding:2px 6px;border-radius:3px">'+sub+'</code>';
    document.body.prepend(banner);
    history.replaceState({},'','/syncling');
  } else {
    if(authToken){window.location.href='/app';}
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

private const val CANONICAL_BASE = "https://syncling.space"

private const val LANDING_JSON_LD = """{
  "@context":"https://schema.org",
  "@graph":[
    {
      "@type":"SoftwareApplication",
      "name":"Syncling",
      "applicationCategory":"DeveloperApplication",
      "operatingSystem":"Android, iOS, Web",
      "description":"AI-powered mobile app localization platform. Push a commit to GitHub and translations are automatically detected, translated into 10+ languages with Claude AI, and published to a global CDN in under 45 seconds — no app store release needed.",
      "url":"https://syncling.space/syncling",
      "offers":[
        {"@type":"Offer","name":"Free","price":"0","priceCurrency":"INR","description":"500 strings per month, 1 project, 3 target languages, GitHub webhook, CDN delivery"},
        {"@type":"Offer","name":"Solo","price":"499","priceCurrency":"INR","description":"5000 strings per month, 3 projects, all languages, glossary, translation memory, review portal"},
        {"@type":"Offer","name":"Team","price":"1999","priceCurrency":"INR","description":"Unlimited strings, 10 projects, team collaboration, per-project quotas, priority support"}
      ],
      "featureList":[
        "AI-powered translation using Claude and Gemini Flash",
        "Automatic semantic change detection — skips surface rewrites",
        "Global CDN delivery across 250+ Cloudflare PoPs",
        "Native Android SDK and iOS Swift SDK",
        "GitHub webhook integration — zero manual steps",
        "Translation memory for instant reuse of cached strings",
        "Glossary enforcement for brand-consistent terminology",
        "Human review portal with approve and rollback controls",
        "OTA translation updates without any app store release",
        "Placeholder guard for %1s, %d, %@ format strings"
      ]
    },
    {
      "@type":"Organization",
      "name":"Syncling",
      "url":"https://syncling.space/syncling",
      "description":"Automated mobile app localization platform for Android and iOS developers"
    },
    {
      "@type":"FAQPage",
      "mainEntity":[
        {"@type":"Question","name":"What is Syncling?","acceptedAnswer":{"@type":"Answer","text":"Syncling is an automated mobile app localization platform for Android and iOS developers. It connects to your GitHub repository via webhook, detects which strings actually changed semantically, translates them using Claude AI into 10+ languages, and publishes signed translation bundles to a global Cloudflare CDN — all within about 45 seconds of a git push."}},
        {"@type":"Question","name":"How does automated mobile app localization work with Syncling?","acceptedAnswer":{"@type":"Answer","text":"Syncling works in five steps: (1) A GitHub webhook fires on push. (2) An AI semantic diff classifier identifies only strings with real meaning changes. (3) Claude AI translates changed strings into every configured target language, enforcing your glossary and checking placeholders. (4) Translations are packaged into a signed bundle and published to Cloudflare R2. (5) Your Android or iOS SDK fetches from the nearest CDN node in under 20ms."}},
        {"@type":"Question","name":"Does Syncling support both Android and iOS localization?","acceptedAnswer":{"@type":"Answer","text":"Yes. Syncling provides a native Android SDK compatible with Kotlin and Java that works with your existing strings.xml setup, and a Swift-native iOS SDK compatible with both SwiftUI and UIKit. Both SDKs are production-ready and handle offline caching automatically."}},
        {"@type":"Question","name":"How quickly does Syncling publish translations after a commit?","acceptedAnswer":{"@type":"Answer","text":"End-to-end from git push to globally available CDN bundle takes approximately 45 seconds for a typical batch of changed strings — including webhook processing, semantic detection, AI translation, placeholder validation, bundle signing, and Cloudflare R2 replication."}},
        {"@type":"Question","name":"What languages does Syncling support?","acceptedAnswer":{"@type":"Answer","text":"Syncling supports Spanish, French, German, Japanese, Korean, Chinese Simplified, Hindi, Portuguese Brazilian, Italian, and Arabic. The Free tier includes 3 target languages; Solo and Team plans include all available languages."}},
        {"@type":"Question","name":"How is Syncling different from Phrase, Lokalise, or Crowdin?","acceptedAnswer":{"@type":"Answer","text":"Syncling is built for fully automated, git-native localization with no manual workflows. Key differences: semantic change detection avoids re-translating unchanged strings; CDN-first delivery enables OTA updates without app store releases; developer-friendly pricing starts free with no credit card required."}},
        {"@type":"Question","name":"Is there a free tier for Syncling?","acceptedAnswer":{"@type":"Answer","text":"Yes. The Free plan includes 500 strings per month, 1 project, 3 target languages, GitHub webhook, AI translation, and CDN delivery — no credit card required, free forever. Paid plans include a 7-day free trial with no charge until the trial ends."}},
        {"@type":"Question","name":"Can I update app translations without a new app store release?","acceptedAnswer":{"@type":"Answer","text":"Yes. Translations are served over-the-air (OTA) from Cloudflare's global CDN via the Syncling Android and iOS SDKs. Fix a typo or add a language at any time — it goes live in under 45 seconds with no App Store or Play Store submission required."}}
      ]
    }
  ]
}"""

internal fun HTML.landingPage() {
    head {
        title { +"Syncling — Automated App Localization & AI Translation for Android & iOS" }
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "description", content = "Syncling automates mobile app localization for Android and iOS. Push a commit — AI detects changes, translates into 10+ languages, and publishes to a global CDN in 45 seconds. OTA updates, no app release needed. Free to start.")
        meta(name = "keywords", content = "app localization, mobile app translation, Android localization, iOS localization, automated localization, AI translation, continuous localization, OTA translation updates, strings.xml translation, app translation tool, Syncling")
        meta(name = "robots", content = "index, follow")
        meta(name = "author", content = "Syncling")
        link(rel = "canonical", href = "$CANONICAL_BASE/syncling")
        meta { attributes["property"] = "og:type"; attributes["content"] = "website" }
        meta { attributes["property"] = "og:url"; attributes["content"] = "$CANONICAL_BASE/syncling" }
        meta { attributes["property"] = "og:title"; attributes["content"] = "Syncling — Automated App Localization & AI Translation for Android & iOS" }
        meta { attributes["property"] = "og:description"; attributes["content"] = "Push a commit. AI detects changes, translates into 10+ languages, and publishes to a global CDN in 45 seconds. Native Android and iOS SDKs. Free to start." }
        meta { attributes["property"] = "og:site_name"; attributes["content"] = "Syncling" }
        meta(name = "twitter:card", content = "summary_large_image")
        meta(name = "twitter:title", content = "Syncling — Automated App Localization & AI Translation")
        meta(name = "twitter:description", content = "Push a commit. AI translates your mobile app strings into 10+ languages and publishes to a global CDN in 45 seconds. No app store release needed.")
        favicon()
        link(rel = "preconnect", href = "https://fonts.googleapis.com")
        link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "" }
        link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=Jost:wght@700;800&family=JetBrains+Mono:wght@400;500;600&display=swap")
        script { unsafe { +"(function(){var t=localStorage.getItem('syncling-theme');if(t)document.documentElement.setAttribute('data-theme',t);})()" } }
        style { unsafe { +"$SHARED_CSS$LANDING_CSS" } }
        script {
            attributes["type"] = "application/ld+json"
            unsafe { +LANDING_JSON_LD }
        }
    }
    body {
        nav {
            div("nav-inner") {
                div("brand") { unsafe { +LOGO_SVG }; span { unsafe { +"<span class=\"brand-sync\">Sync</span>ling" } } }
                div("nav-links") {
                    id = "nav-menu"
                    a("#how") { +"How it works" }
                    a("#features") { +"Features" }
                    a("#pricing") { +"Pricing" }
                    a("#faq") { +"FAQ" }
                    a("/docs") { +"Docs" }
                    a("/auth/github") { id = "nav-cta-btn"; classes = setOf("btn", "btn-primary", "nav-cta"); +"Get started" }
                }
                div("nav-right") {
                    button {
                        id = "theme-toggle"
                        classes = setOf("nav-theme-toggle")
                        attributes["aria-label"] = "Toggle theme"
                        unsafe { +"""<svg class="theme-sun" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/></svg><svg class="theme-moon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>""" }
                    }
                    button {
                        id = "nav-toggle"
                        classes = setOf("nav-hamburger")
                        attributes["aria-label"] = "Open menu"
                        attributes["aria-expanded"] = "false"
                        unsafe { +"""<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>""" }
                    }
                }
            }
        }

        section("hero") {
            div("hero-grid") {}
            div("hero-glow") {}
            div("hero-glow2") {}
            div("hero-inner") {
                span("badge fade-up") { +"Android & iOS · 10+ Languages · Free to start" }
                h1("hero-title fade-up d1") {
                    +"Your app,"; br {}; +"every language. "; span("accent") { +"Automatically." }
                }
                p("hero-sub fade-up d2") {
                    +"Push a commit. Syncling detects changed strings, translates them into 10+ languages with AI, and ships to a global CDN in 45 seconds — no manual work, no app release needed."
                }
                div("hero-actions fade-up d3") {
                    a("/auth/github") { classes = setOf("btn", "btn-primary", "hero-btn"); +"Get started free" }
                    a("#how") { classes = setOf("btn", "btn-ghost"); +"See how it works →" }
                }
                div("hero-stats fade-up d4") {
                    div("hero-stat") { span("hero-stat-val") { +"<20ms" }; span("hero-stat-label") { +"Edge delivery" } }
                    div("hero-stat-div") {}
                    div("hero-stat") { span("hero-stat-val") { +"10+" }; span("hero-stat-label") { +"Languages" } }
                    div("hero-stat-div") {}
                    div("hero-stat") { span("hero-stat-val") { +"~45s" }; span("hero-stat-label") { +"Commit to CDN" } }
                    div("hero-stat-div") {}
                    div("hero-stat") { span("hero-stat-val") { +"250+" }; span("hero-stat-label") { +"CDN PoPs" } }
                }
                div("hero-lang-marquee fade-up") {
                    val langs = listOf("🇪🇸 ES","🇫🇷 FR","🇩🇪 DE","🇯🇵 JA","🇰🇷 KO","🇨🇳 ZH","🇮🇳 HI","🇧🇷 PT","🇮🇹 IT","🇸🇦 AR")
                    div("hero-lang-strip") {
                        (langs + langs).forEach { span("lang-chip") { +it } }
                    }
                }
            }
        }

        section("pipeline-section") {
            id = "how"
            div("section-inner") {
                p("section-label fade-up") { +"HOW IT WORKS" }
                h2("fade-up d1") { +"Commit. Translate. Ship." }
                p("pipeline-lead fade-up d1") { +"Every git push triggers a fully automated pipeline. No dashboards to babysit, no export/import, no manual review unless you want it." }
                div("pipeline-demo fade-up d2") {
                    div("term-window") {
                        div("term-header") {
                            div("term-dots") {
                                span("term-dot term-red") {}
                                span("term-dot term-yellow") {}
                                span("term-dot term-green") {}
                            }
                            span("term-title") { +"syncling — pipeline run" }
                        }
                        div("term-body") {
                            div("term-prompt") {
                                span("term-ps1") { +"$ " }
                                span("term-cmd") { +"git push origin main" }
                            }
                            div("term-line l1") {
                                span("term-check") { +"✓" }
                                span("term-text") { +" Webhook received "; span("term-dim") { +"(0.3s)" } }
                            }
                            div("term-line l2") {
                                span("term-check") { +"✓" }
                                span("term-text") { +" Semantic diff: "; span("term-accent") { +"3 changed" }; span("term-dim") { +", 12 skipped — no semantic change" } }
                            }
                            div("term-line l3") {
                                span("term-check") { +"✓" }
                                span("term-text") { +" Placeholder validation "; span("term-ok") { +"passed" }; span("term-dim") { +" · %1\$s %d %@ all present" } }
                            }
                            div("term-line l4") {
                                span("term-check") { +"✓" }
                                span("term-text") { +" Glossary enforced · Cultural flags "; span("term-ok") { +"clear" } }
                            }
                            div("term-line l5") {
                                span("term-check") { +"✓" }
                                span("term-text") { +" Translated → ES FR DE JA KO ZH HI PT IT AR "; span("term-dim") { +"(42s)" } }
                            }
                            div("term-line l6") {
                                span("term-check") { +"✓" }
                                span("term-text") { +" Bundle signed + published to Cloudflare R2 "; span("term-dim") { +"(0.8s)" } }
                            }
                            div("term-line l7 term-final") {
                                span("term-arrow") { +"→" }
                                span("term-text term-result") { +" Users served in "; span("term-accent") { +"<20ms" }; +" from the nearest PoP" }
                            }
                        }
                    }
                }
                div("pipeline-steps fade-up d3") {
                    data class PStep(val n: String, val t: String, val d: String)
                    listOf(
                        PStep("01", "Git push", "Syncling's webhook fires within seconds of any push to your configured branch."),
                        PStep("02", "Semantic detection", "AI classifier skips surface rewrites — only real semantic changes consume API quota."),
                        PStep("03", "Translate + validate", "Claude translates into all targets. Placeholders, glossary, and cultural flags are all checked."),
                        PStep("04", "Sign + publish", "Signed bundle written to Cloudflare R2 — replicated to 250+ PoPs globally in seconds."),
                        PStep("05", "SDK serves edge", "Android and iOS SDKs fetch from the nearest PoP, cached locally for instant subsequent loads.")
                    ).forEach { s ->
                        div("pstep") {
                            span("pstep-num") { +s.n }
                            h4("pstep-title") { +s.t }
                            p("pstep-desc") { +s.d }
                        }
                    }
                }
            }
        }

        section("sdk-section") {
            id = "sdk"
            div("section-inner") {
                p("section-label fade-up") { +"NATIVE SDKs" }
                h2("fade-up d1") { +"Drop in. Get live translations." }
                p("sdk-intro fade-up d1") { +"Native SDKs for Android and iOS fetch localized strings from the nearest Cloudflare edge node — offline-first, zero networking code, no caching logic to manage." }
                div("sdk-teaser-grid fade-up d2") {
                    div("sdk-teaser-card sdk-android-card") {
                        div("sdk-teaser-top") {
                            div("sdk-icon sdk-android") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>""" }
                            }
                            div {
                                p("sdk-name") { +"Android SDK" }
                                span("sdk-status") { +"In production" }
                            }
                        }
                        p("sdk-teaser-desc") { +"Kotlin & Java compatible. Works with your existing strings.xml. One-line init, smart cache invalidation, fully offline-first." }
                        a("/docs#android-sdk") { classes = setOf("sdk-teaser-link"); +"View Android docs →" }
                    }
                    div("sdk-teaser-card sdk-ios-card") {
                        div("sdk-teaser-top") {
                            div("sdk-icon sdk-ios-icon") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 3H5a2 2 0 0 0-2 2v4m6-6h10a2 2 0 0 1 2 2v4M9 3v18m0 0h10a2 2 0 0 0 2-2V9M9 21H5a2 2 0 0 1-2-2V9m0 0h18"/></svg>""" }
                            }
                            div {
                                p("sdk-name") { +"iOS SDK" }
                                span("sdk-status") { +"In production" }
                            }
                        }
                        p("sdk-teaser-desc") { +"Swift-native. SwiftUI and UIKit compatible. Zero dependencies, intelligent caching, serves strings from the nearest CDN node." }
                        a("/docs#ios-sdk") { classes = setOf("sdk-teaser-link"); +"View iOS docs →" }
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
                    data class Feature(val icon: String, val title: String, val desc: String, val metrics: List<Pair<String,String>> = emptyList(), val accent: Boolean = false)
                    listOf(
                        Feature("""<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>""",
                            "Global CDN delivery", "Translations published to Cloudflare R2 and served from 250+ PoPs worldwide. Strings reach users in under 20ms from the nearest edge node.", listOf("<20ms" to "P99 response", "250+" to "Global PoPs"), true),
                        Feature("""<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><polyline points="21 3 21 8 16 8"/></svg>""",
                            "Instant OTA updates", "Fix a typo or add a language without a new app release. Every CDN bundle is versioned — promote or roll back in one click from your dashboard."),
                        Feature("""<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3c-1 3-2 4-5 5 3 1 4 2 5 5 1-3 2-4 5-5-3-1-4-2-5-5z"/><path d="M5.5 10.5c-.5 1.5-1 2-2.5 2.5 1.5.5 2 1 2.5 2.5.5-1.5 1-2 2.5-2.5-1.5-.5-2-1-2.5-2.5z"/></svg>""",
                            "Semantic detection", "AI classifier skips retranslation for strings with no real meaning change. You only pay API quota for what actually matters."),
                        Feature("""<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>""",
                            "Placeholder guard", "Every format specifier — %1\$s, %d, %@ — validated after translation. Malformed strings are blocked before they reach CDN or your users."),
                        Feature("""<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>""",
                            "Glossary enforcement", "Brand terms defined once per language, applied to every string on every publish. Consistent tone, zero manual oversight."),
                        Feature("""<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>""",
                            "Translation memory", "Identical strings reuse cached results instantly — faster pipeline runs, lower API cost, same quality guarantee on every commit.")
                    ).forEachIndexed { i, f ->
                        div("feature-card fade-up${if (i > 0) " d${minOf(i, 4)}" else ""}") {
                            div("feature-card-icon${if (f.accent) " accent-icon" else ""}") {
                                unsafe { +f.icon }
                            }
                            h3("feature-title") { +f.title }
                            p("feature-desc") { +f.desc }
                            if (f.metrics.isNotEmpty()) {
                                div("feature-metrics") {
                                    f.metrics.forEach { (v, l) ->
                                        div {
                                            span("feature-metric-val") { +v }
                                            p("feature-metric-label") { +l }
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                        a("/auth/github") { classes = setOf("pricing-cta", "outline"); +"Get started free" }
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
                            li { +"Outbound webhooks" }
                            li { +"CDN + SDK access" }
                        }
                        a("/billing/start-subscription?plan=SOLO") { classes = setOf("pricing-cta", "accent"); +"Start 7-day free trial" }
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
                            li { +"Team members + per-project quotas" }
                            li { +"Priority support" }
                            li { +"Priority CDN SLA" }
                        }
                        a("/billing/start-subscription?plan=TEAM") { classes = setOf("pricing-cta", "outline"); +"Start 7-day free trial" }
                    }
                }
                p("pricing-note") { +"All paid plans include a 7-day free trial. No charge until the trial ends — cancel any time." }
            }
        }

        section("faq-section") {
            id = "faq"
            div("section-inner") {
                p("section-label fade-up") { +"FAQ" }
                h2("fade-up d1") { +"Frequently asked questions." }
                div("faq-grid") {
                    data class FaqItem(val q: String, val a: String)
                    listOf(
                        FaqItem(
                            "What exactly is Syncling?",
                            "Syncling is an automated mobile app localization platform for Android and iOS developers. It hooks into your GitHub repository, detects which strings semantically changed, translates them with Claude AI into 10+ languages, and publishes the result to a global Cloudflare CDN — all within ~45 seconds of a git push, no manual work required."
                        ),
                        FaqItem(
                            "How is this different from Phrase, Lokalise, or Crowdin?",
                            "Those tools manage translation workflows with human translators and dashboards. Syncling is fully automated and git-native — translations trigger from a commit, not a task. Semantic change detection skips unchanged strings so you only pay for real changes, and a CDN-first SDK means you can update copy in any language without a new app store release."
                        ),
                        FaqItem(
                            "Can I update translations without releasing a new app version?",
                            "Yes — this is a core feature. Translations are served over-the-air from Cloudflare's global CDN via the Syncling Android or iOS SDK. Fix a typo or add a language at any time and it goes live in under 45 seconds, with zero App Store or Google Play submission required."
                        ),
                        FaqItem(
                            "Does it work with my existing strings.xml or Localizable.strings?",
                            "Yes. The Android SDK integrates with your existing strings.xml workflow in Kotlin or Java. The iOS SDK works with SwiftUI and UIKit. Both SDKs are offline-first with smart cache invalidation and fetch from the nearest CDN node."
                        ),
                        FaqItem(
                            "What languages are supported?",
                            "Currently: Spanish, French, German, Japanese, Korean, Chinese Simplified, Hindi, Brazilian Portuguese, Italian, and Arabic. The Free plan includes 3 target languages; Solo and Team plans unlock all available languages."
                        ),
                        FaqItem(
                            "How accurate are the AI translations?",
                            "Translations use Claude (Anthropic) with your project's glossary applied so brand terms stay consistent. Post-translation, a cultural sensitivity check flags formality mismatches and regional idiom issues. You can also run translations through the built-in human review portal before they go live on the CDN."
                        ),
                        FaqItem(
                            "Is there a free plan? Do I need a credit card?",
                            "The Free plan includes 500 strings/month, 1 project, 3 target languages, GitHub webhook, AI translation, and CDN delivery — no credit card required, free forever. Paid plans (Solo ₹499/mo, Team ₹1,999/mo) each start with a 7-day free trial with no charge until it ends."
                        ),
                        FaqItem(
                            "What happens if a translation contains a broken placeholder like %1\$s?",
                            "Syncling's placeholder guard automatically validates all format specifiers (%1\$s, %d, %@, etc.) after translation. If a placeholder is missing or malformed, that string is blocked and flagged for review — it never reaches the CDN or your users."
                        )
                    ).forEach { item ->
                        div("faq-item fade-up") {
                            h3("faq-q") { +item.q }
                            p("faq-a") { +item.a }
                        }
                    }
                }
            }
        }

        section("cta-section") {
            div("cta-inner") {
                h2("fade-up") { +"Ready to go global?" }
                p("fade-up d1") { +"Free tier includes 500 strings/month, CDN delivery, and SDK access." }
                a("/auth/github") { classes = setOf("btn","btn-primary","cta-btn","fade-up","d2"); +"Get started free" }
            }
        }

        footer {
            div("footer-inner") {
                div("brand") { unsafe { +LOGO_SVG }; span { unsafe { +"<span class=\"brand-sync\">Sync</span>ling" } } }
                nav("footer-nav") {
                    a("#how") { +"How it works" }
                    a("#features") { +"Features" }
                    a("#pricing") { +"Pricing" }
                    a("#faq") { +"FAQ" }
                    a("/docs") { +"Docs" }
                }
                p("footer-copy") { +"© 2026 Syncling" }
            }
        }

        script { unsafe { +LANDING_JS } }
    }
}

private fun HTML.docsPage() {
    head {
        title { +"Syncling Docs — Setup, CLI Reference & FAQ" }
        meta(charset = "UTF-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "description", content = "Complete documentation for Syncling: quick start guide, CLI reference, Android & iOS SDK setup, GitHub webhook configuration, API tokens, and FAQ.")
        meta(name = "robots", content = "index, follow")
        link(rel = "canonical", href = "$CANONICAL_BASE/syncling/docs")
        favicon()
        link(rel = "preconnect", href = "https://fonts.googleapis.com")
        link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "" }
        link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Jost:wght@700&family=JetBrains+Mono:wght@400;500;600&display=swap")
        script { unsafe { +"(function(){var t=localStorage.getItem('syncling-theme');if(t)document.documentElement.setAttribute('data-theme',t);})()" } }
        style { unsafe { +"$SHARED_CSS$DOCS_CSS" } }
    }
    body {
        nav {
            div("nav-inner") {
                div("brand") {
                    a("/syncling") {
                        style = "display:flex;align-items:center;gap:8px;text-decoration:none;color:inherit"
                        unsafe { +LOGO_SVG }
                        span { unsafe { +"<span class=\"brand-sync\">Sync</span>ling" } }
                    }
                }
                div("docs-nav-right") {
                    a("/auth/github") { id = "docs-cta-btn"; classes = setOf("btn", "btn-primary", "nav-cta"); +"Get started" }
                    button {
                        id = "docs-theme-toggle"
                        classes = setOf("nav-theme-toggle")
                        attributes["aria-label"] = "Toggle theme"
                        unsafe { +"""<svg class="theme-sun" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/></svg><svg class="theme-moon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>""" }
                    }
                    button(classes = "docs-menu-btn") {
                        attributes["aria-label"] = "Toggle navigation"
                        unsafe { +"""<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>""" }
                    }
                }
            }
        }

        div("docs-layout") {
            nav("docs-sidebar") {
                div("docs-nav-group") {
                    span("docs-nav-label") { +"Getting Started" }
                    a(href = "#quickstart", classes = "docs-nav-link") { +"Quick Start" }
                    a(href = "#connect-repo", classes = "docs-nav-link") { +"Connect a Repository" }
                    a(href = "#add-sdk", classes = "docs-nav-link") { +"Add the SDK" }
                }
                div("docs-nav-group") {
                    span("docs-nav-label") { +"CLI" }
                    a(href = "#cli", classes = "docs-nav-link") { +"Installation" }
                    a(href = "#cli-auth", classes = "docs-nav-link") { +"Authentication" }
                    a(href = "#cli-commands", classes = "docs-nav-link") { +"Commands" }
                }
                div("docs-nav-group") {
                    span("docs-nav-label") { +"SDKs" }
                    a(href = "#android-sdk", classes = "docs-nav-link") { +"Android SDK" }
                    a(href = "#ios-sdk", classes = "docs-nav-link") { +"iOS SDK" }
                }
                div("docs-nav-group") {
                    span("docs-nav-label") { +"Platform" }
                    a(href = "#projects", classes = "docs-nav-link") { +"Projects" }
                    a(href = "#webhook", classes = "docs-nav-link") { +"GitHub Webhook" }
                    a(href = "#api-tokens", classes = "docs-nav-link") { +"API Tokens" }
                    a(href = "#glossary", classes = "docs-nav-link") { +"Glossary" }
                    a(href = "#cdn", classes = "docs-nav-link") { +"CDN & OTA" }
                    a(href = "#semantic", classes = "docs-nav-link") { +"Semantic Detection" }
                }
                div("docs-nav-group") {
                    span("docs-nav-label") { +"Billing & Help" }
                    a(href = "#pricing", classes = "docs-nav-link") { +"Pricing" }
                    a(href = "#faq", classes = "docs-nav-link") { +"FAQ" }
                    a(href = "#support", classes = "docs-nav-link") { +"Support" }
                }
            }
            main("docs-main") {

                // ── Quick Start ─────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "quickstart"
                    div("docs-section-header") {
                        span("docs-badge") { +"Getting Started" }
                        h1 { +"Quick Start" }
                        p("docs-lead") { +"Get from zero to globally localized in three steps. No credit card required." }
                    }
                    div("steps-list") {
                        div("step-card") {
                            div("step-number") { +"1" }
                            div("step-body") {
                                h3 { +"Sign up with GitHub" }
                                p { +"Go to "; a("/auth/github") { +"syncling.space/auth/github" }; +" and authenticate with your GitHub account. The Free plan activates instantly — no card needed." }
                            }
                        }
                        div("step-card") {
                            div("step-number") { +"2" }
                            div("step-body") {
                                h3 { +"Create a project" }
                                p { +"In the dashboard, click "; strong { +"New Project" }; +" and select your repository. Choose the branch Syncling should watch (e.g. "; code { +"main" }; +"), pick your source language and target languages, and save. Syncling automatically installs the webhook." }
                            }
                        }
                        div("step-card") {
                            div("step-number") { +"3" }
                            div("step-body") {
                                h3 { +"Add the SDK and push" }
                                p { +"Drop the Android or iOS SDK into your app (one-line init). Make any string change in your source file and push to the watched branch. Translations appear on the CDN in ~45 seconds." }
                            }
                        }
                    }
                    div("docs-callout docs-callout-tip") {
                        strong { +"Tip:" }
                        +" You can also trigger a sync manually any time using "; code { +"syncling push <project-id>" }; +" from the CLI — useful before a release."
                    }
                }

                // ── Connect Repo ────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "connect-repo"
                    h2 { +"Connect a Repository" }
                    p { +"Syncling uses GitHub webhooks to watch your repository for commits. When you create a project, Syncling registers the webhook automatically using the GitHub token you authorised at sign-up." }
                    h4 { +"What Syncling reads from your repo" }
                    ul("docs-list") {
                        li { strong { +"Android:" }; +" "; code { +"src/main/res/values/strings.xml" }; +" (configurable path)" }
                        li { strong { +"iOS:" }; +" "; code { +"*.lproj/Localizable.strings" }; +" (configurable path)" }
                        li { +"Any file matching the configured source path pattern" }
                    }
                    h4 { +"Branch configuration" }
                    p { +"During project setup you choose the "; strong { +"watch branch" }; +". Only pushes to that branch trigger the pipeline. You can also configure custom PR branch patterns from the project settings to run translation previews on pull requests." }
                    div("docs-callout docs-callout-info") {
                        strong { +"Note:" }
                        +" Syncling only reads your string resource files — it never clones the full repository. The webhook payload contains the file diff, not your source code."
                    }
                }

                // ── Add SDK ────────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "add-sdk"
                    h2 { +"Add the SDK to Your App" }
                    p { +"The SDK fetches the latest translations from the nearest Cloudflare edge node on first launch and caches them locally. If the CDN is unreachable (offline / airplane mode), the last cached strings are served automatically." }
                    p { +"For detailed setup instructions see the "; a("#android-sdk") { +"Android SDK" }; +" and "; a("#ios-sdk") { +"iOS SDK" }; +" sections below." }
                }

                // ── CLI Installation ────────────────────────────────────────────────────
                section("docs-section") {
                    id = "cli"
                    div("docs-section-header") {
                        span("docs-badge") { +"CLI" }
                        h2 { +"CLI Installation" }
                        p("docs-lead") { +"The Syncling CLI lets you manage your localization pipeline from any terminal. It's available via npm and works on macOS, Linux, and Windows." }
                    }
                    h4 { +"Requirements" }
                    ul("docs-list") {
                        li { +"Node.js 18 or later" }
                        li { +"npm 9 or later (bundled with Node.js)" }
                        li { +"A Syncling account and an API token ("; a("#api-tokens") { +"create one here" }; +")" }
                    }
                    h4 { +"Install globally" }
                    div("docs-code-block") {
                        pre("docs-code") { +"npm install -g syncling" }
                    }
                    h4 { +"Verify the installation" }
                    div("docs-code-block") {
                        pre("docs-code") { +"syncling --version\n# 0.1.0" }
                    }
                    h4 { +"Use without installing (npx)" }
                    div("docs-code-block") {
                        pre("docs-code") { +"npx syncling --help" }
                    }
                }

                // ── CLI Auth ────────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "cli-auth"
                    h2 { +"CLI Authentication" }
                    p { +"The CLI authenticates using an API token, not your GitHub session. Tokens start with "; code { +"sli_" }; +" and are scoped to your account." }
                    h4 { +"Step 1 — Generate a token" }
                    p { +"Go to "; a("/tokens") { +"syncling.space/tokens" }; +" in the dashboard and click "; strong { +"New Token" }; +". Give it a descriptive name (e.g. "; code { +"my-laptop" }; +"). Copy the token — it's shown only once." }
                    h4 { +"Step 2 — Login" }
                    div("docs-code-block") {
                        pre("docs-code") { +"syncling login\n\nCreate an API token at: https://syncling.space/tokens\nPaste your token (sli_…): sli_xxxxxxxxxxxxxxxx\n\nLogged in. Plan: Solo · 3 project(s)" }
                    }
                    p { +"Credentials are stored in "; code { +"~/.syncling/config.json" }; +" on your machine. To remove them:" }
                    div("docs-code-block") {
                        pre("docs-code") { +"syncling logout" }
                    }
                    h4 { +"CI/CD environments" }
                    p { +"For non-interactive environments, set the "; code { +"SYNCLING_TOKEN" }; +" environment variable. The CLI picks it up automatically without needing "; code { +"syncling login" }; +"." }
                    div("docs-code-block") {
                        pre("docs-code") { +"export SYNCLING_TOKEN=\"sli_xxxxxxxxxxxxxxxx\"\nsyncling push proj_abc123" }
                    }
                }

                // ── CLI Commands ────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "cli-commands"
                    h2 { +"CLI Command Reference" }
                    p { +"All commands accept "; code { +"--help" }; +" for inline usage." }

                    data class CmdDoc(val name: String, val usage: String, val desc: String, val example: String? = null, val flags: List<Pair<String,String>> = emptyList())
                    listOf(
                        CmdDoc("login", "syncling login [--api-base <url>]",
                            "Authenticate with an API token. Stores credentials in ~/.syncling/config.json.",
                            "syncling login",
                            listOf("--api-base <url>" to "Override the default API base URL (for self-hosted instances).")
                        ),
                        CmdDoc("logout", "syncling logout",
                            "Remove stored credentials from the machine.",
                            "syncling logout"
                        ),
                        CmdDoc("whoami", "syncling whoami",
                            "Display account info: plan, trial status, project count, strings translated.",
                            "syncling whoami"
                        ),
                        CmdDoc("projects", "syncling projects",
                            "List all projects linked to your account with IDs, repos, branches, and language counts.",
                            "syncling projects"
                        ),
                        CmdDoc("push", "syncling push <project-id>",
                            "Trigger a manual translation sync for a project. Useful in CI or before a release.",
                            "syncling push proj_abc123"
                        ),
                        CmdDoc("pull", "syncling pull <project-id> [options]",
                            "Download translated files for a project. By default pulls all configured languages.",
                            "syncling pull proj_abc123 -l es -l fr -o ./translations",
                            listOf(
                                "-l, --lang <code>" to "Language code to pull (repeatable). Omit to pull all configured languages.",
                                "-o, --out <dir>" to "Output directory (default: current directory).",
                                "-f, --format <fmt>" to "Output format: xml | json | strings. Default: auto-detect from project type."
                            )
                        ),
                        CmdDoc("status", "syncling status [options]",
                            "Show recent pipeline runs with status, duration, string count, and timestamps.",
                            "syncling status --project proj_abc123",
                            listOf("-p, --project <id>" to "Filter runs by project ID.")
                        ),
                        CmdDoc("tokens list", "syncling tokens list",
                            "List all API tokens on your account with creation date and last-used date.",
                            "syncling tokens list"
                        ),
                        CmdDoc("tokens create", "syncling tokens create <name>",
                            "Create a new API token. The token value is shown only once — copy it immediately.",
                            "syncling tokens create ci-pipeline"
                        ),
                        CmdDoc("tokens revoke", "syncling tokens revoke <id>",
                            "Immediately revoke an API token. Any system using that token loses access instantly.",
                            "syncling tokens revoke tok_xyz"
                        )
                    ).forEach { cmd ->
                        div("cmd-doc-block") {
                            h4("cmd-doc-name") { +cmd.name }
                            div("docs-code-block") {
                                pre("docs-code") { +cmd.usage }
                            }
                            p { +cmd.desc }
                            if (cmd.flags.isNotEmpty()) {
                                table("docs-table") {
                                    thead { tr { th { +"Flag" }; th { +"Description" } } }
                                    tbody {
                                        cmd.flags.forEach { (flag, desc) ->
                                            tr { td { code { +flag } }; td { +desc } }
                                        }
                                    }
                                }
                            }
                            cmd.example?.let {
                                div("docs-code-block") {
                                    pre("docs-code docs-code-example") { +it }
                                }
                            }
                        }
                    }
                }

                // ── Android SDK ─────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "android-sdk"
                    div("docs-section-header") {
                        span("docs-badge") { +"SDKs" }
                        h2 { +"Android SDK" }
                        p("docs-lead") { +"Drop-in localization for Android apps built with Kotlin or Java. Works with your existing strings.xml workflow — offline-first, zero extra networking code." }
                    }
                    h4 { +"1. Add the dependency" }
                    div("docs-code-block") {
                        pre("docs-code") { +"""// build.gradle.kts (app module)
dependencies {
    implementation("com.syncling:android-sdk:1.0.0")
}""" }
                    }
                    h4 { +"2. Initialize in Application" }
                    div("docs-code-block") {
                        pre("docs-code") { +"""// MyApplication.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Syncling.init(
            context = this,
            apiKey  = "YOUR_API_KEY",   // project API key from dashboard
        )
    }
}""" }
                    }
                    h4 { +"3. Fetch translated strings" }
                    div("docs-code-block") {
                        pre("docs-code") { +"""// Any Activity / Fragment
val title   = Syncling.string("onboarding_welcome_title")
val message = Syncling.string("onboarding_welcome_message")

// With format args
val greeting = Syncling.string("greeting_user", userName)""" }
                    }
                    h4 { +"Offline behaviour" }
                    p { +"On first launch the SDK fetches the latest bundle from the nearest Cloudflare PoP and caches it to local storage. All subsequent calls are served from the cache. The cache is invalidated when a new CDN publish is detected — controlled by an ETag check that runs in the background, not on the critical path." }
                    div("docs-callout docs-callout-info") {
                        strong { +"API Key:" }
                        +" Find your project API key under "; strong { +"Project Settings → SDK Keys" }; +" in the dashboard. Each project has a unique key."
                    }
                }

                // ── iOS SDK ─────────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "ios-sdk"
                    h2 { +"iOS SDK" }
                    p("docs-lead") { +"Swift-native localization with SwiftUI and UIKit support. Zero dependencies, offline-first, served from Cloudflare edge." }
                    h4 { +"1. Add via Swift Package Manager" }
                    div("docs-code-block") {
                        pre("docs-code") { +"""// Package.swift
dependencies: [
    .package(
        url: "https://github.com/syncling/ios-sdk",
        from: "1.0.0"
    )
]""" }
                    }
                    h4 { +"2. Configure in AppDelegate / @main" }
                    div("docs-code-block") {
                        pre("docs-code") { +"""// AppDelegate.swift
import Syncling

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions ...) -> Bool {
        Syncling.configure(apiKey: "YOUR_API_KEY")
        return true
    }
}""" }
                    }
                    h4 { +"3. Use in SwiftUI" }
                    div("docs-code-block") {
                        pre("docs-code") { +"""// ContentView.swift
import SwiftUI
import Syncling

struct ContentView: View {
    var body: some View {
        Text(Syncling.string("onboarding_welcome_title"))
    }
}""" }
                    }
                    h4 { +"4. UIKit" }
                    div("docs-code-block") {
                        pre("docs-code") { +"""label.text = Syncling.string("onboarding_welcome_title")""" }
                    }
                }

                // ── Projects ────────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "projects"
                    div("docs-section-header") {
                        span("docs-badge") { +"Platform" }
                        h2 { +"Projects" }
                    }
                    p { +"Each project maps to one GitHub repository. A project has exactly one source language and one or more target languages. The pipeline runs once per push to the configured watch branch." }
                    h4 { +"Creating a project" }
                    ol("docs-list") {
                        li { +"Open "; a("/projects") { +"Projects" }; +" in the dashboard and click "; strong { +"New Project" }; +"." }
                        li { +"Select the GitHub repository from the list (only repos you authorised are shown)." }
                        li { +"Set the "; strong { +"Watch Branch" }; +" (default: "; code { +"main" }; +")." }
                        li { +"Set the "; strong { +"Source File Path" }; +" — e.g. "; code { +"src/main/res/values/strings.xml" }; +" for Android." }
                        li { +"Choose the "; strong { +"Source Language" }; +" (the language your strings are written in)." }
                        li { +"Add one or more "; strong { +"Target Languages" }; +" from the supported list." }
                        li { +"Click "; strong { +"Create" }; +". The webhook is installed automatically." }
                    }
                    h4 { +"Per-project quota" }
                    p { +"Team plan users can set a "; strong { +"monthly string quota" }; +" per project. When a project's quota is reached, its pipeline pauses until the next billing cycle — other projects are unaffected." }
                    h4 { +"Project API key" }
                    p { +"Each project has a unique API key used by the SDK. Find it under "; strong { +"Project Settings → SDK Keys" }; +". Keys can be rotated at any time — update the key in your app and redeploy or wait for an OTA refresh." }
                }

                // ── Webhook ────────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "webhook"
                    h2 { +"GitHub Webhook" }
                    p { +"Syncling registers a webhook on your repository automatically when you create a project. No manual GitHub configuration is needed." }
                    h4 { +"What the webhook does" }
                    ul("docs-list") {
                        li { +"Fires on every "; code { +"push" }; +" event to the watched branch." }
                        li { +"Syncling reads the changed files from the push payload and checks if your source strings file was modified." }
                        li { +"If the strings file changed, the translation pipeline is queued." }
                        li { +"If the push doesn't touch the strings file, the webhook is acknowledged and nothing else runs — no wasted quota." }
                    }
                    h4 { +"Manual sync" }
                    p { +"You can trigger a sync at any time from the dashboard ("; strong { +"Project → Sync Now" }; +") or via the CLI:" }
                    div("docs-code-block") {
                        pre("docs-code") { +"syncling push <project-id>" }
                    }
                    h4 { +"Webhook security" }
                    p { +"All webhook payloads are verified using a HMAC-SHA256 signature computed from your GitHub webhook secret. Requests with invalid or missing signatures are rejected with "; code { +"403 Forbidden" }; +"." }
                }

                // ── API Tokens ──────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "api-tokens"
                    h2 { +"API Tokens" }
                    p { +"API tokens authenticate the CLI and REST API calls on behalf of your account. Tokens start with "; code { +"sli_" }; +" and carry the same permissions as the account that created them." }
                    h4 { +"Create a token" }
                    p { +"Go to "; a("/tokens") { +"syncling.space/tokens" }; +" → "; strong { +"New Token" }; +". Give it a descriptive name. The full token value is shown only once at creation — store it securely (e.g. as a CI secret)." }
                    h4 { +"Use in the CLI" }
                    div("docs-code-block") {
                        pre("docs-code") { +"syncling login   # interactive, stores token in ~/.syncling/config.json" }
                    }
                    h4 { +"Use in REST API calls" }
                    div("docs-code-block") {
                        pre("docs-code") { +"curl -H \"Authorization: Bearer sli_xxxxxxxxxxxxxxxx\" \\\n     https://syncling.space/api/projects" }
                    }
                    h4 { +"Revoking a token" }
                    p { +"Tokens can be revoked instantly from the dashboard or via the CLI:" }
                    div("docs-code-block") {
                        pre("docs-code") { +"syncling tokens revoke <token-id>" }
                    }
                    div("docs-callout docs-callout-warning") {
                        strong { +"Security:" }
                        +" Never commit API tokens to version control. Use environment variables or your CI secret store (GitHub Actions Secrets, Bitrise Secrets, etc.)."
                    }
                }

                // ── Glossary ────────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "glossary"
                    h2 { +"Glossary" }
                    p { +"The glossary lets you define brand terms, product names, and translation rules per language. Glossary terms are injected into every translation prompt — ensuring "; em { +"your" }; +" terminology is used consistently across all strings." }
                    h4 { +"Example use cases" }
                    ul("docs-list") {
                        li { +"Lock "; em { +"\"Syncling\"" }; +" to remain untranslated in all languages." }
                        li { +"Map "; em { +"\"free trial\"" }; +" → "; em { +"\"prueba gratuita\"" }; +" in Spanish." }
                        li { +"Enforce formal address ("; em { +"\"Sie\"" }; +" not "; em { +"\"du\"" }; +") in German." }
                    }
                    h4 { +"Managing glossary entries" }
                    p { +"Open "; strong { +"Project → Glossary" }; +" in the dashboard. Entries take effect on the next pipeline run." }
                }

                // ── CDN ─────────────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "cdn"
                    h2 { +"CDN & OTA Updates" }
                    p { +"Translations are compiled into signed bundles and published to Cloudflare R2, then replicated to 250+ edge PoPs globally. The SDK fetches bundles from the nearest PoP, giving P99 response times under 20ms." }
                    h4 { +"Bundle versioning" }
                    p { +"Every publish creates an immutable versioned bundle. The SDK checks for a new bundle version in the background (via an ETag header) and swaps to it silently — no app restart required." }
                    h4 { +"Rollback" }
                    p { +"If a bad translation reaches the CDN, you can roll back to any previous version from "; strong { +"Project → CDN Publishes" }; +" in the dashboard with a single click. Rollback takes effect globally within seconds." }
                    h4 { +"Bundle signing" }
                    p { +"Bundles are signed with an HMAC-SHA256 key derived from your project's signing secret. The SDK validates the signature before applying any bundle — tampered or corrupt bundles are rejected." }
                }

                // ── Semantic Change Detection ───────────────────────────────────────────
                section("docs-section") {
                    id = "semantic"
                    h2 { +"Semantic Change Detection" }
                    p { +"Not every string edit is a meaningful change. If a developer fixes whitespace, reorders parameters, or tweaks capitalisation, the translation stays valid — re-translating wastes API quota." }
                    p { +"Syncling runs an AI classifier on every changed string before calling the translation model. Strings classified as semantically identical to their current translation are skipped. Only real semantic changes consume quota." }
                    h4 { +"Impact" }
                    ul("docs-list") {
                        li { +"Typical savings of 40–70% of translatable strings per commit in mature projects." }
                        li { +"Quota is tracked per string change, not per pipeline run." }
                        li { +"Skipped strings still get their existing translations refreshed in the published bundle." }
                    }
                }

                // ── Pricing ─────────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "pricing"
                    div("docs-section-header") {
                        span("docs-badge") { +"Plans & Billing" }
                        h2 { +"Pricing" }
                        p("docs-lead") { +"All plans include a 7-day free trial. No charge until the trial ends — cancel any time." }
                    }
                    table("docs-table docs-pricing-table") {
                        thead {
                            tr {
                                th { +"Feature" }
                                th { +"Free" }
                                th { +"Solo  ₹499/mo" }
                                th { +"Team  ₹1,999/mo" }
                            }
                        }
                        tbody {
                            data class PRow(val feature: String, val free: String, val solo: String, val team: String)
                            listOf(
                                PRow("Monthly strings", "500", "5,000", "Unlimited"),
                                PRow("Projects", "1", "3", "10"),
                                PRow("Target languages", "3", "All (10+)", "All (10+)"),
                                PRow("GitHub webhook", "✓", "✓", "✓"),
                                PRow("AI translation", "✓", "✓", "✓"),
                                PRow("CDN delivery", "✓", "✓", "✓"),
                                PRow("Glossary enforcement", "—", "✓", "✓"),
                                PRow("Translation memory", "—", "✓", "✓"),
                                PRow("Human review portal", "—", "✓", "✓"),
                                PRow("Outbound webhooks", "—", "✓", "✓"),
                                PRow("Team members", "—", "—", "✓"),
                                PRow("Per-project quotas", "—", "—", "✓"),
                                PRow("Priority CDN SLA", "—", "—", "✓"),
                                PRow("Priority support", "—", "—", "✓"),
                                PRow("7-day free trial", "—", "✓", "✓")
                            ).forEach { r ->
                                tr {
                                    td { +r.feature }
                                    td { +r.free }
                                    td { +r.solo }
                                    td { +r.team }
                                }
                            }
                        }
                    }
                    div("docs-pricing-cta") {
                        a("/auth/github") { classes = setOf("btn", "btn-ghost"); +"Start free →" }
                        a("/billing/start-subscription?plan=SOLO") { classes = setOf("btn", "btn-primary"); +"Start Solo trial" }
                        a("/billing/start-subscription?plan=TEAM") { classes = setOf("btn", "btn-primary"); +"Start Team trial" }
                    }
                }

                // ── FAQ ─────────────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "faq"
                    div("docs-section-header") {
                        span("docs-badge") { +"Help" }
                        h2 { +"FAQ" }
                    }
                    data class DocFaq(val q: String, val a: String)
                    listOf(
                        DocFaq("What is Syncling?",
                            "Syncling is an automated mobile app localization platform. It hooks into your GitHub repository, detects semantic string changes, translates them into 10+ languages with Claude AI, and publishes the result to a global Cloudflare CDN within ~45 seconds of a git push."),
                        DocFaq("Do I need to set up the webhook manually?",
                            "No. Syncling registers the GitHub webhook automatically when you create a project. You just need to authorise the GitHub App during sign-up."),
                        DocFaq("Can I update translations without releasing a new app version?",
                            "Yes — this is a core feature. Translations are served over-the-air from Cloudflare's CDN via the SDK. Fix a typo or add a language at any time; changes go live in under 45 seconds with no App Store or Google Play submission required."),
                        DocFaq("Does it work with my existing strings.xml or Localizable.strings?",
                            "Yes. You point Syncling at your existing source file and the SDK resolves strings at runtime — no changes to your string keys, no migration."),
                        DocFaq("What languages are supported?",
                            "Spanish (ES), French (FR), German (DE), Japanese (JA), Korean (KO), Chinese Simplified (ZH), Hindi (HI), Brazilian Portuguese (PT), Italian (IT), and Arabic (AR). The Free plan includes 3; Solo and Team unlock all."),
                        DocFaq("How accurate are the AI translations?",
                            "Translations are generated by Claude (Anthropic) with your glossary applied for brand consistency. A post-translation pass checks for placeholder integrity and cultural sensitivity. You can route any translation through the human review portal before it reaches the CDN."),
                        DocFaq("What happens if a translation breaks a placeholder like %1\$s?",
                            "The placeholder guard validates all format specifiers after translation. If a specifier is missing or malformed, the string is blocked and flagged for review — it never reaches the CDN or your users."),
                        DocFaq("How does billing work?",
                            "You are billed per translated string (not per API call or character). The Free plan includes 500 strings/month forever. Paid plans include higher limits and start with a 7-day free trial — no charge until the trial ends."),
                        DocFaq("Can I cancel any time?",
                            "Yes. Cancel from the Billing page in the dashboard. Your plan stays active until the end of the current billing period. No cancellation fees."),
                        DocFaq("Is there a refund policy?",
                            "If you're not satisfied within 7 days of your first paid charge, contact support and we'll issue a full refund — no questions asked."),
                        DocFaq("How do I report a translation issue?",
                            "Open the Human Review Portal, find the string, and click Flag. You can add a note describing the issue. Flagged strings are removed from the live bundle until corrected."),
                        DocFaq("Can multiple team members use one account?",
                            "Team plan accounts support multiple members with role-based access. Invite members from Project → Members with Admin, Translator, or Reviewer roles. Each member gets their own login."),
                        DocFaq("How is the CLI different from the dashboard?",
                            "The CLI gives you the same core operations (push, pull, status, token management) in a form that integrates with terminal workflows, shell scripts, and CI/CD pipelines. The dashboard offers the full feature set including analytics, billing, human review, and CDN management.")
                    ).forEach { faq ->
                        div("docs-faq-item") {
                            h4("docs-faq-q") { +faq.q }
                            p("docs-faq-a") { +faq.a }
                        }
                    }
                }

                // ── Support ─────────────────────────────────────────────────────────────
                section("docs-section") {
                    id = "support"
                    h2 { +"Support" }
                    p { +"We're here to help. Reach out through any of these channels:" }
                    div("docs-support-grid") {
                        div("docs-support-card") {
                            div("docs-support-icon") {
                                unsafe { +"""<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>""" }
                            }
                            h4 { +"Email" }
                            p { +"Send us a message at "; a("mailto:support@androidplay.in") { +"support@androidplay.in" }; +". Include your project ID and a description of the issue." }
                        }
                    }
                }

            }
        }

        footer {
            div("footer-inner") {
                div("brand") {
                    a("/syncling") {
                        style = "display:flex;align-items:center;gap:8px;text-decoration:none;color:inherit"
                        unsafe { +LOGO_SVG }
                        span { unsafe { +"<span class=\"brand-sync\">Sync</span>ling" } }
                    }
                }
                nav("footer-nav") {
                    a("/syncling") { +"Home" }
                    a("/syncling#pricing") { +"Pricing" }
                    a("/syncling/docs") { +"Docs" }
                }
                p("footer-copy") { +"© 2026 Syncling · Automated app localization for Android & iOS developers." }
            }
        }

        script { unsafe { +"""
(function(){
  // theme
  var themeBtn = document.getElementById('docs-theme-toggle');
  function getTheme(){ var s=localStorage.getItem('syncling-theme'); return s||( window.matchMedia('(prefers-color-scheme:light)').matches?'light':'dark'); }
  function applyTheme(t){ document.documentElement.setAttribute('data-theme',t); localStorage.setItem('syncling-theme',t); }
  applyTheme(getTheme());
  if(themeBtn){ themeBtn.addEventListener('click',function(){ applyTheme(document.documentElement.getAttribute('data-theme')==='dark'?'light':'dark'); }); }

  // smart nav CTA: show Console for authenticated users
  var docsCta = document.getElementById('docs-cta-btn');
  if(docsCta && localStorage.getItem('syncling_token')){ docsCta.textContent = 'Console'; docsCta.href = '/app'; }

  // active nav link on scroll
  var links = document.querySelectorAll('a.docs-nav-link');
  var sections = Array.from(document.querySelectorAll('.docs-section[id]'));
  function activate(){
    var scrollY = window.scrollY + 90;
    var active = sections.reduce(function(a,s){ return s.offsetTop <= scrollY ? s : a; }, sections[0]);
    if(!active) return;
    links.forEach(function(l){ l.classList.toggle('active', l.getAttribute('href') === '#'+active.id); });
  }
  window.addEventListener('scroll', activate, {passive:true});
  activate();

  // mobile sidebar
  var menuBtn = document.querySelector('.docs-menu-btn');
  var sidebar = document.querySelector('.docs-sidebar');
  if(menuBtn && sidebar){
    menuBtn.addEventListener('click', function(e){
      e.stopPropagation();
      sidebar.classList.toggle('open');
    });
    document.addEventListener('click', function(e){
      if(sidebar.classList.contains('open') && !sidebar.contains(e.target) && !menuBtn.contains(e.target)){
        sidebar.classList.remove('open');
      }
    });
    sidebar.querySelectorAll('a.docs-nav-link').forEach(function(l){
      l.addEventListener('click', function(){ sidebar.classList.remove('open'); });
    });
  }

  // copy-to-clipboard on all code blocks
  document.querySelectorAll('.docs-code-block').forEach(function(block){
    var pre = block.querySelector('pre');
    if(!pre) return;
    var hdr = document.createElement('div');
    hdr.className = 'docs-code-header';
    var btn = document.createElement('button');
    btn.className = 'docs-copy-btn';
    btn.setAttribute('aria-label', 'Copy code');
    btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg><span>Copy</span>';
    btn.addEventListener('click', function(){
      var text = pre.textContent || '';
      navigator.clipboard.writeText(text).then(function(){
        btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg><span>Copied!</span>';
        btn.classList.add('copied');
        setTimeout(function(){
          btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg><span>Copy</span>';
          btn.classList.remove('copied');
        }, 2000);
      });
    });
    hdr.appendChild(btn);
    block.insertBefore(hdr, pre);
  });
})();
""" } }
    }
}

private const val DOCS_CSS = """
.brand span{font-family:'Jost',sans-serif;font-weight:700;letter-spacing:-.3px}
nav{position:sticky;top:0;z-index:200;background:rgba(8,8,8,.95);backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);border-bottom:1px solid var(--border)}
html[data-theme="light"] nav{background:rgba(245,247,250,.95)}
@media(prefers-color-scheme:light){html:not([data-theme="dark"]) nav{background:rgba(245,247,250,.95)}}
.nav-inner{max-width:100%;margin:0 auto;padding:13px 28px;display:flex;align-items:center;justify-content:space-between;gap:16px}
.nav-links{display:flex;align-items:center;gap:12px}
.nav-links a:not(.btn){color:var(--text-muted);font-size:13.5px;transition:color .18s}
.nav-links a:not(.btn):hover{color:var(--text)}
.nav-cta{padding:7px 16px!important;font-size:13px!important;font-weight:600!important}
.btn-ghost{background:transparent;border:1px solid var(--border);color:var(--text-muted)}
.btn-ghost:hover{border-color:var(--accent);color:var(--accent)}
.btn-primary{background:var(--accent);color:#fff;border:1px solid var(--accent)}
.btn-primary:hover{background:#7d6ff5;border-color:#7d6ff5;color:#fff;transform:translateY(-1px);box-shadow:0 4px 16px -4px rgba(139,126,255,.45)}
.docs-nav-right{display:flex;align-items:center;gap:8px;margin-left:auto}
.nav-theme-toggle{display:flex;align-items:center;justify-content:center;background:transparent;border:1px solid var(--border);border-radius:6px;color:var(--text-muted);width:34px;height:34px;padding:0;flex-shrink:0;transition:border-color .2s,color .2s,background .2s;cursor:pointer}
.nav-theme-toggle:hover{border-color:var(--accent);color:var(--accent);background:var(--accent-dim2)}
.docs-menu-btn{display:none;align-items:center;justify-content:center;background:transparent;border:1px solid var(--border);border-radius:6px;color:var(--text-muted);width:34px;height:34px;padding:0;cursor:pointer;flex-shrink:0;transition:border-color .15s,color .15s}
.docs-menu-btn:hover{border-color:rgba(139,126,255,.4);color:var(--accent)}
.theme-sun{display:none}.theme-moon{display:block}
html[data-theme="light"] .theme-sun{display:block}html[data-theme="light"] .theme-moon{display:none}
@media(prefers-color-scheme:light){html:not([data-theme="dark"]) .theme-sun{display:block}html:not([data-theme="dark"]) .theme-moon{display:none}}
.docs-layout{display:flex;min-height:calc(100vh - 57px)}
.docs-sidebar{width:240px;flex-shrink:0;position:sticky;top:57px;height:calc(100vh - 57px);overflow-y:auto;padding:28px 0 32px;border-right:1px solid var(--border);scrollbar-width:none}
.docs-sidebar::-webkit-scrollbar{display:none}
.docs-nav-group{margin-bottom:22px}
.docs-nav-label{display:block;font-size:10px;font-weight:700;letter-spacing:1.4px;text-transform:uppercase;color:var(--text-muted);padding:0 20px 8px;opacity:.5}
a.docs-nav-link{display:block;font-size:13px;color:var(--text-muted);padding:6px 20px;border-left:2px solid transparent;transition:color .14s,border-color .14s,background .14s;line-height:1.45}
a.docs-nav-link:hover{color:var(--text);background:rgba(139,126,255,.04)}
a.docs-nav-link.active{color:var(--accent);border-left-color:var(--accent);background:rgba(139,126,255,.06);font-weight:600}
.docs-main{flex:1;min-width:0;padding:56px 72px 96px}
.docs-main>*{max-width:720px}
.docs-section{margin-bottom:80px;padding-bottom:80px;border-bottom:1px solid var(--border);max-width:720px}
.docs-section:last-child{border-bottom:none;margin-bottom:0}
.docs-section-header{margin-bottom:32px}
.docs-badge{display:inline-block;font-size:10px;font-weight:700;letter-spacing:1.4px;text-transform:uppercase;color:var(--accent);background:rgba(139,126,255,.1);border:1px solid rgba(139,126,255,.22);border-radius:20px;padding:3px 11px;margin-bottom:14px}
.docs-main h1{font-size:28px;font-weight:800;letter-spacing:-.5px;margin-bottom:8px;line-height:1.2;color:var(--text)}
.docs-main h2{font-size:22px;font-weight:700;letter-spacing:-.35px;margin-bottom:8px;line-height:1.25;color:var(--text)}
.docs-main h3{font-size:17px;font-weight:600;margin-bottom:10px;color:var(--text)}
.docs-main h4{font-size:11px;font-weight:700;color:var(--accent);margin:30px 0 12px;text-transform:uppercase;letter-spacing:1.2px;opacity:.8}
.docs-lead{font-size:16px;color:var(--text-muted);line-height:1.78;margin-top:8px}
.docs-main p{font-size:15px;color:var(--text-muted);line-height:1.82;margin-bottom:16px}
.docs-main a{color:var(--accent);text-decoration:none;font-weight:500}
.docs-main a:hover{text-decoration:underline}
.docs-main strong{color:var(--text);font-weight:600}
.docs-main em{color:var(--text);font-style:italic}
.docs-main code{background:rgba(139,126,255,.1);border:1px solid rgba(139,126,255,.2);border-radius:5px;padding:2px 7px;font-size:13px;font-family:'JetBrains Mono',ui-monospace,'SF Mono',Menlo,Consolas,monospace;color:var(--accent)}
html[data-theme="light"] .docs-main code{background:rgba(139,126,255,.08);border-color:rgba(139,126,255,.25)}
@media(prefers-color-scheme:light){html:not([data-theme="dark"]) .docs-main code{background:rgba(139,126,255,.08);border-color:rgba(139,126,255,.25)}}
.docs-list{padding-left:22px;display:flex;flex-direction:column;gap:9px;margin:0 0 18px}
.docs-list li{font-size:15px;color:var(--text-muted);line-height:1.78}
.steps-list{display:flex;flex-direction:column;gap:12px;margin-bottom:28px}
.step-card{display:flex;gap:18px;border:1px solid var(--border);border-radius:10px;padding:20px 22px;align-items:flex-start;background:var(--surface);transition:border-color .2s,box-shadow .2s}
.step-card:hover{border-color:rgba(139,126,255,.3);box-shadow:0 4px 20px -8px rgba(139,126,255,.15)}
.step-number{width:28px;height:28px;border-radius:50%;background:var(--accent);color:#0d0720;font-size:13px;font-weight:800;display:flex;align-items:center;justify-content:center;flex-shrink:0;margin-top:2px}
.step-body h3{font-size:15px;font-weight:700;color:var(--text);margin-bottom:6px}
.step-body p{font-size:14px;color:var(--text-muted);line-height:1.72;margin:0}
.docs-callout{border-radius:8px;padding:14px 18px;font-size:14px;line-height:1.7;margin:22px 0;border:1px solid;display:flex;gap:10px;align-items:flex-start}
.docs-callout-tip{background:rgba(61,220,132,.05);border-color:rgba(61,220,132,.2);color:#86c89e}
html[data-theme="light"] .docs-callout-tip{color:#2d7a4f}
.docs-callout-info{background:rgba(139,126,255,.05);border-color:rgba(139,126,255,.2);color:var(--text-muted)}
.docs-callout-warning{background:rgba(254,188,46,.05);border-color:rgba(254,188,46,.2);color:#b89a50}
html[data-theme="light"] .docs-callout-warning{color:#7a5a1a}
.docs-callout strong{color:inherit;font-weight:700}
.docs-code-block{margin:14px 0 22px;border-radius:10px;overflow:hidden;border:1px solid rgba(139,126,255,.18);box-shadow:0 2px 12px rgba(0,0,0,.25)}
html[data-theme="light"] .docs-code-block{border-color:rgba(139,126,255,.2);box-shadow:0 2px 8px rgba(0,0,0,.08)}
@media(prefers-color-scheme:light){html:not([data-theme="dark"]) .docs-code-block{border-color:rgba(139,126,255,.2);box-shadow:0 2px 8px rgba(0,0,0,.08)}}
.docs-code-header{display:flex;align-items:center;justify-content:flex-end;background:#111;padding:8px 12px;border-bottom:1px solid rgba(255,255,255,.06)}
html[data-theme="light"] .docs-code-header{background:#1a2035;border-color:rgba(255,255,255,.08)}
@media(prefers-color-scheme:light){html:not([data-theme="dark"]) .docs-code-header{background:#1a2035;border-color:rgba(255,255,255,.08)}}
.docs-copy-btn{display:inline-flex;align-items:center;gap:5px;background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.12);border-radius:5px;color:rgba(255,255,255,.5);cursor:pointer;font-size:11.5px;font-weight:500;padding:4px 10px;transition:background .15s,color .15s,border-color .15s;font-family:inherit;line-height:1}
.docs-copy-btn:hover{background:rgba(139,126,255,.18);border-color:rgba(139,126,255,.4);color:#a89fff}
.docs-copy-btn.copied{background:rgba(61,220,132,.12);border-color:rgba(61,220,132,.3);color:#4ade80}
.docs-code{background:#0d0f1a;color:#c9d1e0;font-family:'JetBrains Mono',ui-monospace,'SF Mono',Menlo,Consolas,monospace;font-size:13px;line-height:1.8;padding:18px 22px;margin:0;white-space:pre;overflow-x:auto;display:block}
.docs-code-example{border-top:1px solid rgba(139,126,255,.12)}
.cmd-doc-block{margin-bottom:40px;padding-bottom:40px;border-bottom:1px solid var(--border)}
.cmd-doc-block:last-child{border-bottom:none;margin-bottom:0;padding-bottom:0}
.cmd-doc-name{font-size:15px;font-weight:700;color:var(--text);font-family:'JetBrains Mono',ui-monospace,'SF Mono',Menlo,monospace;margin-bottom:12px;display:block;padding:6px 0}
.docs-table{width:100%;border-collapse:collapse;font-size:14px;margin:14px 0 22px;overflow-x:auto;display:block}
.docs-table th{text-align:left;padding:10px 14px;background:var(--surface2);color:var(--text);font-size:11px;font-weight:700;letter-spacing:.8px;text-transform:uppercase;border-bottom:2px solid var(--border)}
.docs-table td{padding:11px 14px;color:var(--text-muted);border-bottom:1px solid var(--border);vertical-align:top;line-height:1.65}
.docs-table tr:last-child td{border-bottom:none}
.docs-table tr:hover td{background:rgba(139,126,255,.03)}
.docs-pricing-table td:nth-child(n+2){text-align:center}
.docs-pricing-table th:nth-child(n+2){text-align:center}
.docs-pricing-cta{display:flex;gap:12px;margin-top:28px;flex-wrap:wrap}
.docs-pricing-cta .btn{padding:10px 22px;font-size:14px;font-weight:600;border-radius:8px}
.docs-faq-item{margin-bottom:28px;padding-bottom:28px;border-bottom:1px solid var(--border)}
.docs-faq-item:last-child{border-bottom:none;margin-bottom:0;padding-bottom:0}
.docs-faq-q{font-size:15.5px;font-weight:700;color:var(--text);margin-bottom:10px;line-height:1.4}
.docs-faq-a{font-size:14px;color:var(--text-muted);line-height:1.82;margin:0}
.docs-support-grid{display:grid;grid-template-columns:minmax(0,480px);gap:16px;margin-top:18px}
.docs-support-card{background:var(--surface);border:1px solid var(--border);border-radius:10px;padding:22px;display:flex;flex-direction:column;gap:10px;transition:border-color .2s,box-shadow .2s}
.docs-support-card:hover{border-color:rgba(139,126,255,.3);box-shadow:0 4px 20px -8px rgba(139,126,255,.15)}
.docs-support-icon{color:var(--accent);line-height:0}
.docs-support-card h4{font-size:14.5px;font-weight:700;color:var(--text);margin:0}
.docs-support-card p{font-size:13.5px;color:var(--text-muted);line-height:1.68;margin:0}
footer{padding:28px 32px;border-top:1px solid var(--border)}
.footer-inner{max-width:100%;display:flex;justify-content:space-between;align-items:center;font-size:13px;color:var(--text-muted);gap:16px;flex-wrap:wrap}
.footer-nav{display:flex;gap:22px;flex-wrap:wrap}
.footer-nav a{color:var(--text-muted);font-size:13px;font-weight:500;transition:color .15s}
.footer-nav a:hover{color:var(--accent)}
.footer-copy{font-size:12px;color:var(--text-muted)}
@media(max-width:1024px){.docs-main{padding:48px 40px 80px}}
@media(max-width:860px){
  .docs-menu-btn{display:flex}
  .docs-sidebar{display:none;position:fixed;top:57px;left:0;height:calc(100vh - 57px);background:var(--bg);z-index:150;width:252px;padding:24px 0;border-right:1px solid var(--border)}
  .docs-sidebar.open{display:block}
  .docs-main{padding:36px 24px 64px}
  footer{padding:22px 20px}
}
@media(max-width:480px){
  .docs-main{padding:28px 16px 48px}
  .step-card{flex-direction:column;gap:12px}
  .docs-pricing-cta{flex-direction:column}
  .docs-pricing-cta .btn{width:100%;text-align:center}
  .docs-support-grid{grid-template-columns:1fr}
}
"""

private const val LANDING_CSS = """
/* ── typography ──────────────────────────────────────────────── */
body{font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif;font-feature-settings:'cv02','cv03','cv04','cv11';-webkit-font-smoothing:antialiased}
.brand,.brand-text,.brand span{font-family:'Jost',sans-serif!important}
code,pre,.term-body,.term-cmd,.term-ps1,.term-title{font-family:'JetBrains Mono','SF Mono',Menlo,Consolas,monospace}
/* ── keyframes ───────────────────────────────────────────────── */
@keyframes heroDrift{0%,100%{transform:translateX(-50%) scale(1)}50%{transform:translateX(-50%) translateY(32px) scale(1.08)}}
@keyframes termIn{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:translateY(0)}}
@keyframes marquee{0%{transform:translateX(0)}100%{transform:translateX(-50%)}}
@keyframes gridReveal{from{opacity:0}to{opacity:1}}
@keyframes badgePulse{0%,100%{box-shadow:0 0 0 0 rgba(139,126,255,.45)}50%{box-shadow:0 0 0 6px rgba(139,126,255,0)}}
/* ── nav ─────────────────────────────────────────────────────── */
nav{position:sticky;top:0;z-index:100;background:var(--lp-nav-bg);backdrop-filter:blur(24px);-webkit-backdrop-filter:blur(24px);border-bottom:1px solid var(--border)}
.nav-inner{max-width:1200px;margin:0 auto;padding:13px 32px;display:flex;align-items:center;justify-content:space-between;gap:16px}
.nav-right{display:flex;align-items:center;gap:8px}
.nav-links{display:flex;align-items:center;gap:28px}
.nav-links a:not(.btn){color:var(--lp-text-soft);font-size:13.5px;font-weight:500;letter-spacing:.01em;transition:color .15s ease;font-family:'Inter',-apple-system,sans-serif}
.nav-links a:not(.btn):hover{color:var(--text)}
.nav-cta{padding:7px 18px!important;font-size:13px!important;font-weight:600!important;letter-spacing:.01em!important}
.nav-hamburger{display:none;align-items:center;justify-content:center;background:transparent;border:1px solid var(--border);border-radius:6px;color:var(--text-muted);width:34px;height:34px;padding:0;flex-shrink:0;transition:border-color .2s,color .2s;cursor:pointer}
.nav-hamburger:hover{border-color:var(--accent);color:var(--accent)}
.nav-theme-toggle{display:flex;align-items:center;justify-content:center;background:transparent;border:1px solid var(--border);border-radius:6px;color:var(--text-muted);width:34px;height:34px;padding:0;flex-shrink:0;transition:border-color .2s,color .2s,background .2s;cursor:pointer}
.nav-theme-toggle:hover{border-color:var(--accent);color:var(--accent);background:var(--accent-dim2)}
.theme-sun{display:none}.theme-moon{display:block}
html[data-theme="light"] .theme-sun{display:block}html[data-theme="light"] .theme-moon{display:none}
@media(prefers-color-scheme:light){html:not([data-theme="dark"]) .theme-sun{display:block}html:not([data-theme="dark"]) .theme-moon{display:none}}
/* ── hero ────────────────────────────────────────────────────── */
.hero{position:relative;overflow:hidden;padding:144px 24px 120px;text-align:center}
.hero-grid{position:absolute;inset:0;background-image:linear-gradient(rgba(139,126,255,.045) 1px,transparent 1px),linear-gradient(90deg,rgba(139,126,255,.045) 1px,transparent 1px);background-size:64px 64px;-webkit-mask-image:radial-gradient(ellipse 88% 62% at 50% 0%,#000 28%,transparent 100%);mask-image:radial-gradient(ellipse 88% 62% at 50% 0%,#000 28%,transparent 100%);pointer-events:none;animation:gridReveal 2s ease forwards}
.hero-glow{position:absolute;top:-420px;left:50%;transform:translateX(-50%);width:1100px;height:1100px;background:radial-gradient(circle,rgba(139,126,255,.07) 0%,transparent 56%);pointer-events:none;animation:heroDrift 16s ease-in-out infinite}
.hero-glow2{position:absolute;top:80px;right:-320px;width:680px;height:680px;background:radial-gradient(circle,rgba(34,211,238,.032) 0%,transparent 65%);pointer-events:none}
.hero-inner{max-width:880px;margin:0 auto;position:relative}
.badge{display:inline-flex;align-items:center;gap:8px;background:rgba(139,126,255,.06);border:1px solid rgba(139,126,255,.16);border-radius:100px;padding:5px 18px 5px 8px;font-size:11px;font-weight:600;color:var(--lp-text-soft);letter-spacing:.06em;margin-bottom:36px;text-transform:uppercase;font-family:'Inter',-apple-system,sans-serif}
.badge::before{content:'';width:6px;height:6px;border-radius:50%;background:var(--accent);box-shadow:0 0 10px var(--accent);flex-shrink:0;animation:badgePulse 2.4s ease-in-out infinite}
.hero-title{font-size:clamp(42px,7.8vw,92px);font-weight:800;line-height:.97;letter-spacing:-4.5px;margin:0 0 28px;color:var(--text);font-family:'Inter',-apple-system,sans-serif}
.accent{background:linear-gradient(120deg,#c4b5fd 0%,#8B7EFF 50%,#22d3ee 100%);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;display:inline-block}
.hero-sub{color:var(--lp-text-soft);font-size:clamp(15px,2vw,17px);line-height:1.82;margin-bottom:44px;max-width:560px;margin-left:auto;margin-right:auto;font-weight:400;font-family:'Inter',-apple-system,sans-serif}
.hero-actions{display:flex;gap:12px;justify-content:center;flex-wrap:wrap;margin-bottom:56px}
.hero-btn{padding:14px 32px;font-size:15px;font-weight:600;letter-spacing:-.02em;font-family:'Inter',-apple-system,sans-serif}
.btn-primary{background:var(--accent);color:#fff;border-color:var(--accent);box-shadow:0 0 44px -12px rgba(139,126,255,.45)}
.btn-primary:hover{background:#7d6ff5;color:#fff;border-color:#7d6ff5;box-shadow:0 0 56px -8px rgba(139,126,255,.65);transform:translateY(-1px)}
.hero-stats{display:flex;align-items:center;justify-content:center;gap:0;flex-wrap:wrap;margin:0 auto 52px;background:rgba(255,255,255,.018);border:1px solid rgba(139,126,255,.1);border-radius:16px;padding:22px 40px;max-width:680px;backdrop-filter:blur(12px)}
.hero-stat{display:flex;flex-direction:column;align-items:center;padding:0 28px}
.hero-stat-val{font-size:26px;font-weight:800;color:var(--accent);line-height:1;letter-spacing:-1.5px;font-variant-numeric:tabular-nums;font-family:'Inter',-apple-system,sans-serif}
.hero-stat-label{font-size:10px;font-weight:600;color:var(--lp-label-color);margin-top:6px;letter-spacing:.1em;text-transform:uppercase;font-family:'Inter',-apple-system,sans-serif}
.hero-stat-div{width:1px;height:36px;background:rgba(139,126,255,.1);flex-shrink:0}
.hero-lang-marquee{overflow:hidden;-webkit-mask-image:linear-gradient(90deg,transparent 0%,#000 14%,#000 86%,transparent 100%);mask-image:linear-gradient(90deg,transparent 0%,#000 14%,#000 86%,transparent 100%)}
.hero-lang-strip{display:flex;flex-wrap:nowrap;gap:8px;width:max-content;animation:marquee 32s linear infinite}
.hero-lang-strip:hover{animation-play-state:paused}
.lang-chip{background:rgba(255,255,255,.028);border:1px solid rgba(255,255,255,.07);border-radius:100px;padding:5px 14px;font-size:12.5px;color:var(--lp-chip-text);white-space:nowrap;font-weight:500;transition:border-color .2s,color .2s,background .2s;cursor:default;letter-spacing:.01em;font-family:'Inter',-apple-system,sans-serif}
.lang-chip:hover{border-color:rgba(139,126,255,.3);color:var(--accent);background:rgba(139,126,255,.05)}
/* ── sections shared ─────────────────────────────────────────── */
section{padding:120px 24px}
.section-inner{max-width:1200px;margin:0 auto}
.section-label{font-size:10.5px;font-weight:700;letter-spacing:.18em;color:var(--accent);margin-bottom:20px;display:flex;align-items:center;gap:12px;text-transform:uppercase;font-family:'Inter',-apple-system,sans-serif}
.section-label::before{content:'';width:20px;height:1px;background:currentColor;opacity:.5;flex-shrink:0}
h2{font-size:clamp(26px,4.2vw,52px);font-weight:800;letter-spacing:-2.5px;margin-bottom:64px;line-height:1.04;font-family:'Inter',-apple-system,sans-serif}
/* ── pipeline ────────────────────────────────────────────────── */
.pipeline-section{padding:120px 24px 136px;background:var(--bg);border-top:1px solid var(--border);position:relative;overflow:hidden}
.pipeline-section::before{content:'';position:absolute;inset:0;background:radial-gradient(ellipse 52% 38% at 50% 0%,rgba(139,126,255,.035),transparent);pointer-events:none}
.pipeline-lead{color:var(--lp-text-soft);font-size:16.5px;line-height:1.78;max-width:520px;margin-top:-48px;margin-bottom:68px;font-weight:400;font-family:'Inter',-apple-system,sans-serif}
.term-window{max-width:780px;margin:0 auto 72px;background:var(--lp-term-bg);border:1px solid var(--lp-term-border);border-radius:16px;overflow:hidden;box-shadow:0 0 0 1px rgba(139,126,255,.04),0 64px 128px -44px rgba(0,0,0,.9),0 0 80px -40px rgba(139,126,255,.07)}
.term-header{display:flex;align-items:center;gap:12px;padding:12px 18px;background:var(--lp-term-header);border-bottom:1px solid var(--lp-term-border)}
.term-dots{display:flex;gap:7px}
.term-dot{width:12px;height:12px;border-radius:50%}
.term-red{background:#ff5f57}.term-yellow{background:#febc2e}.term-green{background:#28c840}
.term-title{flex:1;text-align:center;font-size:11px;color:var(--lp-term-title);letter-spacing:.08em}
.term-body{padding:24px 30px 30px;font-size:13px;line-height:2.05;display:flex;flex-direction:column;color:var(--lp-term-text)}
.term-prompt{display:flex;gap:0;margin-bottom:14px}
.term-ps1{color:#6366f1;font-weight:600}
.term-cmd{color:var(--lp-term-cmd)}
.term-line{opacity:0;display:flex;gap:10px;align-items:baseline}
.term-check{color:#34d399;font-weight:700;flex-shrink:0;width:16px}
.term-arrow{color:var(--accent);flex-shrink:0;width:16px}
.term-text{color:var(--lp-term-text)}
.term-result{color:var(--lp-term-cmd);font-weight:600}
.term-accent{color:var(--accent);font-weight:600}
.term-ok{color:#4ade80;font-weight:600}
.term-dim{color:var(--lp-term-dim)}
.term-final{margin-top:16px;padding-top:16px;border-top:1px solid var(--lp-term-final)}
.pipeline-demo.in-view .term-line{animation:termIn .35s cubic-bezier(.2,.7,.2,1) forwards}
.pipeline-demo.in-view .l1{animation-delay:.6s}
.pipeline-demo.in-view .l2{animation-delay:1.4s}
.pipeline-demo.in-view .l3{animation-delay:2.2s}
.pipeline-demo.in-view .l4{animation-delay:2.9s}
.pipeline-demo.in-view .l5{animation-delay:3.6s}
.pipeline-demo.in-view .l6{animation-delay:4.8s}
.pipeline-demo.in-view .l7{animation-delay:5.6s}
.pipeline-steps{display:grid;grid-template-columns:repeat(5,1fr);position:relative;padding-top:8px;gap:0}
.pipeline-steps::before{content:'';position:absolute;top:29px;left:calc(10% + 24px);right:calc(10% + 24px);height:1px;background:linear-gradient(90deg,transparent,rgba(139,126,255,.25) 15%,rgba(139,126,255,.25) 85%,transparent);pointer-events:none}
.pstep{padding:0 12px;display:flex;flex-direction:column;align-items:center;text-align:center;position:relative}
.pstep-num{display:flex;align-items:center;justify-content:center;width:56px;height:56px;border-radius:50%;background:var(--bg);border:1px solid rgba(139,126,255,.15);font-size:11.5px;font-weight:700;color:var(--accent);letter-spacing:.04em;margin:0 auto 24px;position:relative;z-index:1;transition:border-color .25s,background .25s,box-shadow .25s;flex-shrink:0;font-family:'JetBrains Mono',monospace}
.pstep:hover .pstep-num{border-color:rgba(139,126,255,.5);background:rgba(139,126,255,.05);box-shadow:0 0 32px -8px rgba(139,126,255,.35)}
.pstep-title{font-size:13px;font-weight:700;color:var(--text);margin-bottom:10px;letter-spacing:-.01em;font-family:'Inter',-apple-system,sans-serif}
.pstep-desc{font-size:12px;color:var(--lp-text-muted2);line-height:1.68;font-family:'Inter',-apple-system,sans-serif}
/* ── features ────────────────────────────────────────────────── */
.features-section{background:var(--surface2);border-top:1px solid var(--border);padding:120px 24px}
.features-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:1px;background:var(--border);border:1px solid var(--border);border-radius:20px;overflow:hidden}
.feature-card{background:var(--surface);border:none;border-radius:0;padding:36px;display:flex;flex-direction:column;gap:0;transition:background .22s;position:relative;overflow:hidden}
.feature-card::after{content:'';position:absolute;inset:0;background:radial-gradient(circle at 25% 25%,rgba(139,126,255,.045),transparent 68%);opacity:0;transition:opacity .28s;pointer-events:none}
.feature-card:hover{background:rgba(139,126,255,.018)}
.feature-card:hover::after{opacity:1}
.feature-card-icon{color:var(--text-muted);line-height:0;flex-shrink:0;width:44px;height:44px;border-radius:10px;background:rgba(255,255,255,.03);border:1px solid rgba(255,255,255,.06);display:flex;align-items:center;justify-content:center;margin-bottom:24px;transition:background .2s,border-color .2s,color .2s}
.feature-card:hover .feature-card-icon{background:rgba(139,126,255,.07);border-color:rgba(139,126,255,.16);color:var(--accent)}
.feature-card-icon.accent-icon{color:var(--accent);background:rgba(139,126,255,.07);border-color:rgba(139,126,255,.16)}
.feature-title{font-size:14.5px;font-weight:700;color:var(--text);letter-spacing:-.02em;line-height:1.35;margin-bottom:10px;font-family:'Inter',-apple-system,sans-serif}
.feature-desc{font-size:13px;color:var(--lp-text-muted2);line-height:1.76;flex:1;font-family:'Inter',-apple-system,sans-serif}
.feature-metrics{display:flex;gap:28px;margin-top:28px;padding-top:22px;border-top:1px solid rgba(255,255,255,.05)}
.feature-metric-val{font-size:22px;font-weight:800;color:var(--accent);font-variant-numeric:tabular-nums;line-height:1;letter-spacing:-1px;font-family:'Inter',-apple-system,sans-serif}
.feature-metric-label{font-size:10.5px;color:var(--lp-label-color);font-weight:600;letter-spacing:.08em;text-transform:uppercase;margin-top:6px;font-family:'Inter',-apple-system,sans-serif}
/* ── sdk ─────────────────────────────────────────────────────── */
.sdk-section{padding:120px 24px;background:var(--bg);border-top:1px solid var(--border);position:relative}
.sdk-section::before{content:'';position:absolute;top:0;left:0;right:0;height:1px;background:linear-gradient(90deg,transparent,rgba(139,126,255,.15) 35%,rgba(34,211,238,.1) 65%,transparent)}
.sdk-intro{color:var(--lp-text-soft);font-size:16.5px;line-height:1.78;max-width:520px;margin-top:-48px;margin-bottom:64px;font-weight:400;font-family:'Inter',-apple-system,sans-serif}
.sdk-teaser-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:1px;background:var(--border);border:1px solid var(--border);border-radius:20px;overflow:hidden;max-width:840px}
.sdk-teaser-card{background:var(--surface);border:none;border-radius:0;padding:40px;display:flex;flex-direction:column;gap:24px;transition:background .22s;position:relative}
.sdk-teaser-card:hover{background:rgba(139,126,255,.018)}
.sdk-teaser-top{display:flex;align-items:center;gap:16px}
.sdk-icon{width:52px;height:52px;border-radius:14px;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.sdk-android{background:rgba(61,220,132,.07);color:#3DDC84;border:1px solid rgba(61,220,132,.15)}
.sdk-ios-icon{background:rgba(96,165,250,.07);color:#60a5fa;border:1px solid rgba(96,165,250,.15)}
.sdk-name{font-size:19px;font-weight:800;color:var(--text);margin-bottom:6px;letter-spacing:-.03em;font-family:'Inter',-apple-system,sans-serif}
.sdk-status{display:inline-block;padding:2px 10px;border-radius:100px;font-size:10px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;background:rgba(74,222,128,.06);color:#4ade80;border:1px solid rgba(74,222,128,.15);font-family:'Inter',-apple-system,sans-serif}
.sdk-teaser-desc{font-size:14px;color:var(--lp-text-muted2);line-height:1.76;flex:1;font-family:'Inter',-apple-system,sans-serif}
.sdk-teaser-link{display:inline-flex;align-items:center;gap:6px;font-size:13px;font-weight:600;color:var(--accent);transition:gap .18s;letter-spacing:.01em;font-family:'Inter',-apple-system,sans-serif}
.sdk-teaser-link:hover{gap:10px}
/* ── pricing ─────────────────────────────────────────────────── */
.pricing-section{background:var(--surface2);border-top:1px solid var(--border);position:relative;overflow:hidden;padding:120px 24px}
.pricing-section::before{content:'';position:absolute;bottom:-200px;left:50%;transform:translateX(-50%);width:800px;height:400px;background:radial-gradient(circle,rgba(139,126,255,.038),transparent 68%);pointer-events:none}
.pricing-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px;margin-top:20px;padding-top:4px}
.pricing-card{background:var(--surface);border:1px solid var(--lp-pricing-border);border-radius:20px;padding:36px;display:flex;flex-direction:column;position:relative;transition:border-color .25s,transform .28s,box-shadow .28s}
.pricing-card:hover{transform:translateY(-4px);border-color:rgba(255,255,255,.1);box-shadow:0 32px 64px -28px rgba(0,0,0,.8)}
.pricing-card.recommended{border-color:rgba(139,126,255,.3);background:linear-gradient(160deg,rgba(14,10,32,.98) 0%,rgba(11,11,20,.98) 60%);box-shadow:0 0 0 1px rgba(139,126,255,.1),0 0 100px -40px rgba(139,126,255,.2)}
.pricing-card.recommended:hover{border-color:rgba(139,126,255,.5);box-shadow:0 0 0 1px rgba(139,126,255,.22),0 40px 72px -20px rgba(139,126,255,.25)}
.rec-badge{position:absolute;top:-14px;left:50%;transform:translateX(-50%);background:var(--accent);color:#fff;font-size:9.5px;font-weight:800;letter-spacing:.12em;padding:4px 18px;border-radius:100px;white-space:nowrap;text-transform:uppercase;font-family:'Inter',-apple-system,sans-serif}
.trial-badge{display:inline-block;background:rgba(34,211,238,.05);color:#22d3ee;border:1px solid rgba(34,211,238,.15);border-radius:100px;padding:3px 12px;font-size:10.5px;font-weight:600;margin-bottom:16px;letter-spacing:.04em;font-family:'Inter',-apple-system,sans-serif}
.pricing-name{font-size:10.5px;font-weight:700;letter-spacing:.14em;color:var(--lp-text-faint);text-transform:uppercase;margin-bottom:12px;font-family:'Inter',-apple-system,sans-serif}
.pricing-price{font-size:52px;font-weight:800;letter-spacing:-3.5px;line-height:1;color:var(--text);margin-bottom:6px;font-variant-numeric:tabular-nums;font-family:'Inter',-apple-system,sans-serif}
.price-mo{font-size:15px;font-weight:400;letter-spacing:0;color:var(--lp-text-faint)}
.pricing-period{font-size:12px;color:var(--lp-text-faint);margin-bottom:28px;min-height:16px;font-weight:400;font-family:'Inter',-apple-system,sans-serif}
.pricing-features{list-style:none;display:flex;flex-direction:column;gap:11px;flex:1;margin-bottom:32px}
.pricing-features li{font-size:13px;color:var(--lp-text-soft);display:flex;align-items:center;gap:10px;font-weight:400;font-family:'Inter',-apple-system,sans-serif}
.pricing-features li::before{content:'';width:15px;height:15px;background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%238B7EFF' stroke-width='2.5' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpolyline points='20 6 9 17 4 12'/%3E%3C/svg%3E");background-size:contain;background-repeat:no-repeat;background-position:center;flex-shrink:0;opacity:.65}
.pricing-card.recommended .pricing-features li{color:rgba(232,228,255,.7)}
/* ── pricing CTAs — fixed contrast ───────────────────────────── */
.pricing-cta{display:block;text-align:center;padding:13px 20px;border-radius:10px;font-size:13.5px;font-weight:600;transition:all .18s;cursor:pointer;text-decoration:none;letter-spacing:.01em;font-family:'Inter',-apple-system,sans-serif}
.pricing-cta.accent{background:var(--accent);color:#fff;box-shadow:0 4px 24px -6px rgba(139,126,255,.45)}
.pricing-cta.accent:hover{background:#7d6ff5;color:#fff;transform:translateY(-1px);box-shadow:0 10px 32px -4px rgba(139,126,255,.6)}
.pricing-cta.outline{background:rgba(255,255,255,.055);color:var(--text);border:1px solid rgba(255,255,255,.12)}
.pricing-cta.outline:hover{border-color:rgba(139,126,255,.45);color:var(--accent);background:rgba(139,126,255,.06);transform:translateY(-1px)}
html[data-theme="light"] .pricing-cta.outline{background:rgba(0,0,0,.04);color:var(--text);border:1px solid rgba(0,0,0,.12)}
html[data-theme="light"] .pricing-cta.outline:hover{border-color:var(--accent);color:var(--accent);background:var(--accent-dim)}
@media(prefers-color-scheme:light){html:not([data-theme="dark"]) .pricing-cta.outline{background:rgba(0,0,0,.04);color:var(--text);border:1px solid rgba(0,0,0,.12)}}
.pricing-note{text-align:center;margin-top:36px;font-size:12px;color:var(--lp-text-faint);font-weight:400;font-family:'Inter',-apple-system,sans-serif}
/* ── cta section ─────────────────────────────────────────────── */
.cta-section{background:var(--bg);border-top:1px solid rgba(139,126,255,.08);border-bottom:1px solid rgba(139,126,255,.08);text-align:center;position:relative;overflow:hidden;padding:120px 24px}
.cta-section::before{content:'';position:absolute;inset:0;background:radial-gradient(ellipse 58% 78% at 50% 50%,rgba(139,126,255,.055),transparent 68%);pointer-events:none}
.cta-section::after{content:'';position:absolute;inset:0;background-image:linear-gradient(rgba(139,126,255,.03) 1px,transparent 1px),linear-gradient(90deg,rgba(139,126,255,.03) 1px,transparent 1px);background-size:48px 48px;-webkit-mask-image:radial-gradient(ellipse 75% 75% at 50% 50%,#000,transparent);mask-image:radial-gradient(ellipse 75% 75% at 50% 50%,#000,transparent);pointer-events:none}
.cta-inner{max-width:520px;margin:0 auto;position:relative;z-index:1}
.cta-section h2{margin-bottom:18px;font-size:clamp(28px,4.5vw,56px);letter-spacing:-3px}
.cta-section p{color:var(--lp-text-muted2);margin-bottom:44px;font-size:16px;font-weight:400;line-height:1.68;font-family:'Inter',-apple-system,sans-serif}
.cta-btn{padding:15px 36px;font-size:15.5px;font-weight:700;letter-spacing:-.02em}
/* ── footer ──────────────────────────────────────────────────── */
footer{padding:40px 32px;border-top:1px solid var(--border)}
.footer-inner{max-width:1200px;margin:0 auto;display:flex;justify-content:space-between;align-items:center;font-size:13px;color:var(--text-muted);gap:16px;flex-wrap:wrap}
.footer-nav{display:flex;gap:28px;flex-wrap:wrap}
.footer-nav a{color:var(--lp-text-faint);font-size:13px;font-weight:500;letter-spacing:.01em;transition:color .15s;font-family:'Inter',-apple-system,sans-serif}.footer-nav a:hover{color:var(--text)}
.footer-copy{font-size:12px;color:var(--lp-text-faintest);font-weight:400;font-family:'Inter',-apple-system,sans-serif}
.text-muted{color:var(--text-muted)}
/* ── faq ─────────────────────────────────────────────────────── */
.faq-section{background:var(--bg);border-top:1px solid var(--border);padding:120px 24px}
.faq-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:1px;background:var(--border);border:1px solid var(--border);border-radius:20px;overflow:hidden}
.faq-item{background:var(--surface);border:none;padding:32px;transition:background .2s}
.faq-item:hover{background:rgba(139,126,255,.018)}
.faq-q{font-size:14px;font-weight:700;color:var(--text);margin-bottom:14px;line-height:1.5;letter-spacing:-.015em;font-family:'Inter',-apple-system,sans-serif}
.faq-a{font-size:13px;color:var(--lp-text-muted2);line-height:1.82;font-weight:400;font-family:'Inter',-apple-system,sans-serif}
/* ── responsive ──────────────────────────────────────────────── */
@media(max-width:640px){.footer-inner{flex-direction:column;text-align:center}.footer-nav{justify-content:center}}
@media(max-width:1024px){
  .features-grid{grid-template-columns:repeat(2,1fr)}
  .pipeline-steps{grid-template-columns:repeat(3,1fr)}
  .pipeline-steps::before{left:calc(16.66% + 24px);right:calc(16.66% + 24px)}
}
@media(max-width:768px){
  .nav-hamburger{display:flex}
  .nav-links{position:absolute;top:calc(100% + 1px);left:0;right:0;flex-direction:column;align-items:stretch;background:var(--lp-nav-mobile);border-bottom:1px solid var(--border);padding:8px 0 16px;backdrop-filter:blur(24px);-webkit-backdrop-filter:blur(24px);display:none;gap:0;z-index:200}
  .nav-links.open{display:flex}
  .nav-links a:not(.btn){padding:13px 24px;font-size:14.5px;width:100%;box-sizing:border-box;border-bottom:1px solid rgba(255,255,255,.04)}
  .nav-links .nav-cta{margin:8px 16px 0;width:calc(100% - 32px)!important;text-align:center;display:block;padding:11px 16px!important;font-size:14px!important;box-sizing:border-box}
  .nav-inner{position:relative;flex-wrap:nowrap}
  .hero{padding:108px 20px 80px}
  section{padding:88px 20px}
  h2{margin-bottom:48px}
  .pipeline-section{padding:88px 20px 104px}
  .sdk-teaser-grid{grid-template-columns:1fr;max-width:100%;background:transparent;border:none;gap:8px}
  .sdk-teaser-card{border:1px solid var(--lp-card-border);border-radius:16px}
  .pipeline-steps{grid-template-columns:repeat(2,1fr)}
  .pipeline-steps::before{display:none}
  .faq-grid{grid-template-columns:1fr;background:transparent;border:none;gap:8px}
  .faq-item{border:1px solid var(--lp-card-border);border-radius:16px}
  .features-grid{background:transparent;border:none;gap:8px}
  .feature-card{border:1px solid var(--lp-card-border);border-radius:16px}
}
@media(max-width:640px){
  .hero{padding:84px 16px 64px}
  .hero-inner{padding:0}
  .hero-stats{flex-wrap:wrap;padding:14px 16px;gap:2px}
  .hero-stat{padding:10px 18px;min-width:calc(50% - 32px)}
  .hero-stat-div{display:none}
  .hero-stat-val{font-size:22px}
  .hero-actions{flex-direction:column;align-items:stretch;padding:0 8px}
  .hero-actions .btn{text-align:center;width:100%}
  .features-grid{grid-template-columns:1fr}
  .pipeline-steps{grid-template-columns:1fr}
  .term-body{padding:18px 20px 20px;font-size:12px}
  section{padding:72px 16px}
  .pipeline-section,.sdk-section{padding:72px 16px 88px}
  .pricing-section,.faq-section,.cta-section{padding:72px 16px}
}
@media(max-width:400px){
  .hero-title{font-size:36px;letter-spacing:-2.5px}
  .hero-sub{font-size:14.5px}
  .hero-btn{font-size:14px;padding:12px 24px}
  .pricing-price{font-size:44px}
  .nav-inner{padding:11px 16px}
}
"""

private fun HTML.dashboardApp() {
    head {
        title { +"Syncling — Dashboard" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        favicon()
        link(rel = "preconnect", href = "https://fonts.googleapis.com")
        link(rel = "preconnect", href = "https://fonts.gstatic.com")
        link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=DM+Sans:opsz,wght@9..40,400;9..40,500;9..40,600;9..40,700&family=JetBrains+Mono:wght@600;700&display=swap")
        style { unsafe { +"$SHARED_CSS$SHELL_LAYOUT_CSS$DASHBOARD_CSS$SIDEBAR_QUOTA_CSS$CONVERSION_CSS$ONBOARDING_CSS$SUPPORT_CHAT_CSS" } }
    }
    body {
        div("app-layout") {
            unsafe { +APP_SIDEBAR_DASH }
            main("main-content dash-main") {
                div("page-header dash-page-header") {
                    div("dash-title-block") {
                        div("dash-title-row") {
                            h1("page-title") { +"Dashboard" }
                            span {
                                id = "sse-status"; classes = setOf("sse-status", "idle")
                                div { classes = setOf("sse-status-dot") }
                                span { id = "sse-status-text"; +"" }
                            }
                        }
                        p("page-sub") { +"Live overview of your translation pipeline." }
                    }
                }

                div("dash-alert") { id = "dash-alert" }
                div { id = "wh-reject-banner" }

                div("stats-grid") {
                    div("stat-card card stat-card-green") {
                        div("stat-card-top") {
                            p("stat-label") { +"Strings Translated" }
                            div("stat-icon-wrap stat-icon-green") {
                                unsafe { +"<svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><polyline points='17 1 21 5 17 9'/><path d='M3 11V9a4 4 0 0 1 4-4h14'/><polyline points='7 23 3 19 7 15'/><path d='M21 13v2a4 4 0 0 1-4 4H3'/></svg>" }
                            }
                        }
                        p("stat-value stat-value-green loading") { id = "total-translated"; +"—" }
                        p("stat-sub") { +"all time" }
                    }
                    div("stat-card card stat-card-amber") {
                        div("stat-card-top") {
                            p("stat-label") { +"Pending Review" }
                            div("stat-icon-wrap stat-icon-amber") {
                                unsafe { +"<svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'/><polyline points='12 6 12 12 16 14'/></svg>" }
                            }
                        }
                        p("stat-value stat-value-amber loading") { id = "pending-review"; +"—" }
                        p("stat-sub") { +"awaiting approval" }
                    }
                    div("stat-card card stat-card-red") {
                        div("stat-card-top") {
                            p("stat-label") { +"Blocked" }
                            div("stat-icon-wrap stat-icon-red") {
                                unsafe { +"<svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'/><line x1='4.93' y1='4.93' x2='19.07' y2='19.07'/></svg>" }
                            }
                        }
                        p("stat-value stat-value-red loading") { id = "blocked-count"; +"—" }
                        p("stat-sub") { +"rejected translations" }
                    }
                    div("stat-card card stat-card-blue") {
                        div("stat-card-top") {
                            p("stat-label") { +"Languages" }
                            div("stat-icon-wrap stat-icon-blue") {
                                unsafe { +"<svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'/><line x1='2' y1='12' x2='22' y2='12'/><path d='M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z'/></svg>" }
                            }
                        }
                        p("stat-value stat-value-blue loading") { id = "active-langs"; +"—" }
                        p("stat-sub") { +"active targets" }
                    }
                    div("stat-card card stat-card-purple") {
                        div("stat-card-top") {
                            p("stat-label") { +"Projects" }
                            div("stat-icon-wrap stat-icon-purple") {
                                unsafe { +"<svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z'/></svg>" }
                            }
                        }
                        p("stat-value stat-value-purple loading") { id = "total-projects"; +"—" }
                        p("stat-sub") { +"connected repos" }
                    }
                }

                div("dash-body") {
                    div("dash-col-main") {
                        div("content-section") {
                            id = "activity"
                            div("section-header dash-section-header") {
                                div("section-header-left") {
                                    div("section-header-mark") {}
                                    h2 { +"Pipeline Activity" }
                                }
                            }
                            div("run-list") {
                                id = "run-list"
                                div("activity-empty") {
                                    id = "activity-empty"
                                    div("activity-empty-icon") {
                                        unsafe { +"""<svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>""" }
                                    }
                                    p("activity-empty-title") { +"No pipeline runs yet" }
                                    p("activity-empty-hint") { +"Push a commit to GitHub to trigger your first translation." }
                                }
                            }
                        }
                    }

                    div("dash-col-side") {
                        div("widget-card") {
                            id = "plan-widget"
                            div("widget-header") {
                                div("widget-header-left") {
                                    div("widget-icon widget-icon-purple") {
                                        unsafe { +"<svg width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'><rect x='1' y='4' width='22' height='16' rx='2'/><line x1='1' y1='10' x2='23' y2='10'/></svg>" }
                                    }
                                    span("widget-title") { +"Plan & Usage" }
                                }
                                a("/billing") { classes = setOf("widget-link"); +"Manage →" }
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
                                div("widget-header-left") {
                                    div("widget-icon widget-icon-amber") {
                                        unsafe { +"<svg width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'><path d='M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'/><polyline points='14 2 14 8 20 8'/><line x1='16' y1='13' x2='8' y2='13'/><line x1='16' y1='17' x2='8' y2='17'/></svg>" }
                                    }
                                    span("widget-title") { +"Review Queue" }
                                }
                                a("/review-portal") { classes = setOf("widget-link"); +"Open →" }
                            }
                            div("widget-body") {
                                div { id = "w-review-queue" }
                            }
                        }

                        div("widget-card") {
                            id = "cdn-widget"
                            div("widget-header") {
                                div("widget-header-left") {
                                    div("widget-icon widget-icon-blue") {
                                        unsafe { +"<svg width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='10'/><line x1='2' y1='12' x2='22' y2='12'/><path d='M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z'/></svg>" }
                                    }
                                    span("widget-title") { +"CDN Status" }
                                }
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
                            div("widget-header") {
                                div("widget-header-left") {
                                    div("widget-icon widget-icon-green") {
                                        unsafe { +"<svg width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'><polyline points='13 2 3 14 12 14 11 22 21 10 12 10 13 2'/></svg>" }
                                    }
                                    span("widget-title") { +"Quick Actions" }
                                }
                            }
                            div("widget-body qa-body") {
                                a("/projects") {
                                    id = "qa-new-project"; classes = setOf("qa-btn")
                                    unsafe { +"<svg width='13' height='13' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z'/><line x1='12' y1='11' x2='12' y2='17'/><line x1='9' y1='14' x2='15' y2='14'/></svg> New project" }
                                }
                                a("/review-portal") {
                                    classes = setOf("qa-btn")
                                    unsafe { +"<svg width='13' height='13' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z'/><circle cx='12' cy='12' r='3'/></svg> Review translations" }
                                }
                                a("/projects") {
                                    classes = setOf("qa-btn")
                                    unsafe { +"<svg width='13' height='13' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M4 19.5A2.5 2.5 0 0 1 6.5 17H20'/><path d='M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'/></svg> Edit glossary" }
                                }
                                a("/billing") {
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
        script { unsafe { +SUPPORT_CHAT_JS } }
        script { unsafe { +"Onboarding.boot('dashboard');" } }
    }
}

private const val DASHBOARD_CSS = """
body{font-family:'DM Sans',system-ui,sans-serif}
/* ── Page layout ─────────────────────────────────────────────────────────── */
.dash-main{padding:24px 28px}
.dash-page-header{margin-bottom:24px;padding-bottom:0}
.dash-title-block{}
.dash-title-row{display:flex;align-items:center;gap:12px;margin-bottom:3px}
.dash-title-row .page-title{font-size:24px;font-weight:700;letter-spacing:-.5px}
/* ── Stats grid ──────────────────────────────────────────────────────────── */
.stats-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin-bottom:20px}
@media(max-width:1180px){.stats-grid{grid-template-columns:repeat(3,1fr)}}
@media(max-width:700px){.stats-grid{grid-template-columns:repeat(2,1fr)}}
.stat-card{padding:18px 20px;position:relative;overflow:hidden;transition:border-color .2s,box-shadow .2s}
.stat-card-green{border-top:2px solid rgba(74,222,128,.35);background:linear-gradient(160deg,rgba(74,222,128,.04) 0%,var(--surface) 60%)}
.stat-card-amber{border-top:2px solid rgba(251,191,36,.35);background:linear-gradient(160deg,rgba(251,191,36,.04) 0%,var(--surface) 60%)}
.stat-card-red{border-top:2px solid rgba(248,113,113,.35);background:linear-gradient(160deg,rgba(248,113,113,.04) 0%,var(--surface) 60%)}
.stat-card-blue{border-top:2px solid rgba(96,165,250,.35);background:linear-gradient(160deg,rgba(96,165,250,.04) 0%,var(--surface) 60%)}
.stat-card-purple{border-top:2px solid rgba(139,126,255,.45);background:linear-gradient(160deg,rgba(139,126,255,.05) 0%,var(--surface) 60%)}
.stat-card-green:hover{border-color:rgba(74,222,128,.5);box-shadow:0 4px 24px -8px rgba(74,222,128,.12)}
.stat-card-amber:hover{border-color:rgba(251,191,36,.5);box-shadow:0 4px 24px -8px rgba(251,191,36,.12)}
.stat-card-red:hover{border-color:rgba(248,113,113,.5);box-shadow:0 4px 24px -8px rgba(248,113,113,.12)}
.stat-card-blue:hover{border-color:rgba(96,165,250,.5);box-shadow:0 4px 24px -8px rgba(96,165,250,.12)}
.stat-card-purple:hover{border-color:rgba(139,126,255,.6);box-shadow:0 4px 24px -8px rgba(139,126,255,.15)}
.stat-card-top{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:10px}
.stat-label{font-size:11px;font-weight:600;letter-spacing:.4px;color:var(--text-muted);text-transform:uppercase}
.stat-icon-wrap{width:30px;height:30px;border-radius:8px;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.stat-icon-green{background:rgba(74,222,128,.12);color:#4ade80;border:1px solid rgba(74,222,128,.2)}
.stat-icon-amber{background:rgba(251,191,36,.12);color:#fbbf24;border:1px solid rgba(251,191,36,.2)}
.stat-icon-red{background:rgba(248,113,113,.12);color:#f87171;border:1px solid rgba(248,113,113,.2)}
.stat-icon-blue{background:rgba(96,165,250,.12);color:#60a5fa;border:1px solid rgba(96,165,250,.2)}
.stat-icon-purple{background:rgba(139,126,255,.12);color:var(--accent);border:1px solid rgba(139,126,255,.2)}
.stat-value{font-family:'JetBrains Mono',ui-monospace,monospace;font-size:30px;font-weight:700;line-height:1;letter-spacing:-1px;transition:opacity .2s}
.stat-value.loading{opacity:.3;animation:pulse 1.4s infinite}
.stat-value-green{color:#4ade80}
.stat-value-amber{color:#fbbf24}
.stat-value-red{color:#f87171}
.stat-value-blue{color:#60a5fa}
.stat-value-purple{color:var(--accent)}
@keyframes pulse{0%{opacity:.15}50%{opacity:.5}100%{opacity:.15}}
.stat-yellow{color:#fbbf24!important}
.stat-plan{font-size:18px;font-weight:700;color:var(--accent)}
.stat-sub{font-size:11px;color:var(--text-muted);margin-top:6px;letter-spacing:.1px}
/* ── Two-column dashboard body ───────────────────────────────────────────── */
.dash-body{display:grid;grid-template-columns:1fr 296px;gap:20px;align-items:start}
@media(max-width:1100px){.dash-body{grid-template-columns:1fr}}
.dash-col-main{min-width:0}
.dash-col-side{display:flex;flex-direction:column;gap:14px}
/* ── Section header ──────────────────────────────────────────────────────── */
.content-section{margin-bottom:28px}
.section-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}
.dash-section-header{}
.section-header-left{display:flex;align-items:center;gap:10px}
.section-header-mark{width:3px;height:18px;border-radius:2px;background:linear-gradient(180deg,var(--accent),rgba(139,126,255,.3));flex-shrink:0}
.section-header h2{font-size:15px;font-weight:700;letter-spacing:-.2px;color:var(--text)}
/* ── Widget cards (right column) ─────────────────────────────────────────── */
.widget-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden;transition:border-color .2s}
.widget-card:hover{border-color:rgba(139,126,255,.2)}
.widget-header{display:flex;align-items:center;justify-content:space-between;padding:12px 14px;border-bottom:1px solid var(--border);background:rgba(255,255,255,.012)}
.widget-header-left{display:flex;align-items:center;gap:8px}
.widget-icon{width:24px;height:24px;border-radius:6px;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.widget-icon-purple{background:rgba(139,126,255,.1);color:var(--accent);border:1px solid rgba(139,126,255,.18)}
.widget-icon-amber{background:rgba(251,191,36,.1);color:#fbbf24;border:1px solid rgba(251,191,36,.18)}
.widget-icon-blue{background:rgba(96,165,250,.1);color:#60a5fa;border:1px solid rgba(96,165,250,.18)}
.widget-icon-green{background:rgba(74,222,128,.1);color:#4ade80;border:1px solid rgba(74,222,128,.18)}
.widget-title{font-size:11px;font-weight:700;letter-spacing:.4px;color:var(--text-dim);text-transform:uppercase}
.widget-link{font-size:11px;font-weight:600;color:var(--text-muted);transition:color .15s;padding:2px 6px;border-radius:4px}.widget-link:hover{color:var(--accent);background:var(--accent-dim2)}
.widget-badge{font-size:11px;font-weight:700;color:var(--accent);background:var(--accent-dim);border:1px solid rgba(139,126,255,.25);border-radius:20px;padding:2px 8px}
.widget-body{padding:14px}
.widget-hint{font-size:11px;color:var(--text-muted);margin-top:8px}
.plan-row-sm{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}
.plan-name-sm{font-size:14px;font-weight:700;color:var(--text)}
.usage-item-sm{margin-bottom:11px}
.usage-row-sm{display:flex;justify-content:space-between;font-size:11.5px;color:var(--text-muted);margin-bottom:5px}
.usage-val{font-weight:600;color:var(--text-dim);font-family:'JetBrains Mono',ui-monospace,monospace;font-size:11px}
.usage-track{height:4px;background:var(--surface2);border:1px solid var(--border);border-radius:3px;overflow:hidden}
.usage-fill{height:100%;background:linear-gradient(90deg,var(--accent),rgba(139,126,255,.7));border-radius:3px;transition:width .6s cubic-bezier(.4,0,.2,1);width:0%}
.w-empty{font-size:12px;color:var(--text-muted);text-align:center;padding:14px 0}
.w-run-row{display:flex;align-items:center;gap:8px;padding:6px 0;border-bottom:1px solid var(--border);font-size:12px}
.w-run-row:last-child{border-bottom:none}
.mini-dot{width:7px;height:7px;border-radius:50%;flex-shrink:0}
.mini-dot.running{background:var(--accent);animation:pulse 1.4s infinite}
.mini-dot.done{background:#4ade80}
.mini-dot.error{background:var(--red)}
.w-run-repo{flex:1;color:var(--text-dim);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.w-run-branch{color:var(--text-muted);font-family:monospace;font-size:11px}
.qa-body{display:flex;flex-direction:column;gap:7px}
.qa-btn{display:flex;align-items:center;gap:9px;padding:9px 12px;font-size:12.5px;font-weight:500;color:var(--text-muted);background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);transition:all .15s}
.qa-btn:hover{border-color:rgba(139,126,255,.35);color:var(--text);background:rgba(139,126,255,.06)}
.qa-btn svg{flex-shrink:0;opacity:.55}
/* ── Misc ─────────────────────────────────────────────────────────────────── */
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
@keyframes livePulse{0%{box-shadow:0 0 0 0 rgba(139,126,255,.5)}70%{box-shadow:0 0 0 8px rgba(139,126,255,0)}100%{box-shadow:0 0 0 0 rgba(139,126,255,0)}}
.activity-badge{background:rgba(139,126,255,.15);color:var(--accent);display:none}
.sse-status{display:inline-flex;align-items:center;gap:5px;font-size:11px;font-weight:600;letter-spacing:.5px;padding:3px 10px;border-radius:20px;transition:all .3s}
.sse-status.idle,.sse-status.connected{display:none}
.sse-status.reconnecting{display:inline-flex;background:rgba(250,173,20,.1);color:var(--yellow);border:1px solid rgba(250,173,20,.2);animation:ssePulse 1.5s ease-in-out infinite}
.sse-status.disconnected{display:inline-flex;background:rgba(255,77,79,.08);color:var(--red);border:1px solid rgba(255,77,79,.2)}
.sse-status-dot{width:6px;height:6px;border-radius:50%;background:currentColor;flex-shrink:0}
.sse-status.reconnecting .sse-status-dot{animation:sseDot 1.2s ease-in-out infinite}
@keyframes ssePulse{0%,100%{opacity:1}50%{opacity:.65}}
@keyframes sseDot{0%,100%{opacity:.3}50%{opacity:1}}
.run-list{display:flex;flex-direction:column;gap:10px}
.activity-empty{display:flex;flex-direction:column;align-items:center;gap:8px;padding:48px 24px;background:var(--surface);border:1px dashed rgba(139,126,255,.15);border-radius:var(--radius);text-align:center}
.activity-empty-icon{color:rgba(139,126,255,.35);margin-bottom:4px}
.activity-empty-title{font-size:14px;font-weight:600;color:var(--text-dim)}
.activity-empty-hint{font-size:12px;color:var(--text-muted);max-width:320px;line-height:1.55}
.run-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden;transition:border-color .2s,box-shadow .2s}
.run-card:hover{border-color:rgba(139,126,255,.2)}
.run-card.run-active{border-color:rgba(139,126,255,.3);box-shadow:0 0 0 1px rgba(139,126,255,.08) inset,0 4px 20px -8px rgba(139,126,255,.12)}
.run-card.run-error{border-color:rgba(255,77,79,.22);box-shadow:0 4px 20px -8px rgba(255,77,79,.08)}
.run-card.run-retrying{border-color:rgba(250,173,20,.28);box-shadow:0 0 0 1px rgba(250,173,20,.06) inset}
.run-header{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid var(--border);gap:12px;background:rgba(255,255,255,.012)}
.run-repo{font-size:13px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;color:var(--text)}
.run-meta{display:flex;align-items:center;gap:8px;font-size:12px;color:var(--text-muted);flex-shrink:0}
.run-commit{font-family:'JetBrains Mono',ui-monospace,monospace;font-size:11px;background:var(--surface2);border:1px solid var(--border);border-radius:4px;padding:2px 7px;color:var(--text-dim)}
.run-branch{color:var(--text-muted);font-family:'JetBrains Mono',ui-monospace,monospace;font-size:11px;background:var(--surface2);border:1px solid var(--border);border-radius:4px;padding:2px 7px}
.run-ago{color:var(--text-muted);font-variant-numeric:tabular-nums;font-size:11px}
.run-status-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0}
.run-status-dot.active{background:var(--accent);animation:livePulse 1.8s infinite}
.run-status-dot.done{background:#4ade80}
.run-status-dot.error{background:var(--red)}
.run-status-dot.retrying{background:var(--yellow);animation:livePulse 1.8s infinite}
.run-retried-badge{font-size:10px;font-weight:700;letter-spacing:.5px;padding:2px 7px;border-radius:10px;background:rgba(250,173,20,.12);color:var(--yellow);border:1px solid rgba(250,173,20,.25);white-space:nowrap}
.run-steps{padding:10px 16px;display:flex;flex-direction:column;gap:0}
.step-row{display:flex;align-items:flex-start;gap:12px;padding:6px 0;position:relative}
.step-row:not(:last-child)::after{content:'';position:absolute;left:11px;top:26px;width:1px;height:calc(100% - 8px);background:var(--border);z-index:0}
.step-row.step-row-done:not(:last-child)::after{background:rgba(74,222,128,.2)}
.step-icon{width:24px;height:24px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:12px;flex-shrink:0;position:relative;z-index:1;transition:all .25s ease;margin-top:1px}
.step-icon.pending{background:var(--surface2);border:1.5px solid var(--border);color:var(--text-muted)}
.step-icon.running{background:rgba(139,126,255,.12);border:1.5px solid rgba(139,126,255,.5);color:var(--accent);box-shadow:0 0 0 3px rgba(139,126,255,.08)}
.step-icon.done{background:rgba(74,222,128,.1);border:1.5px solid rgba(74,222,128,.35);color:#4ade80}
.step-icon.error{background:rgba(255,77,79,.1);border:1.5px solid rgba(255,77,79,.4);color:var(--red)}
.step-icon.skipped{background:var(--surface2);border:1.5px solid var(--border);color:var(--text-muted);opacity:.45}
.step-spin{animation:spin .9s linear infinite}
@keyframes spin{from{transform:rotate(0deg)}to{transform:rotate(360deg)}}
.step-body{flex:1;display:flex;flex-direction:column;align-items:flex-start;gap:2px;min-width:0}
.step-label{font-size:12.5px;transition:color .2s}
.step-label.running{color:var(--text);font-weight:600}
.step-label.done{color:var(--text-dim)}
.step-label.error{color:var(--red)}
.step-label.skipped{color:var(--text-muted);opacity:.55}
.step-label.pending{color:var(--text-muted)}
.step-detail{font-size:11px;color:var(--text-muted);word-break:break-word;overflow-wrap:anywhere;line-height:1.4}
.step-detail.done{color:var(--text-dim)}
.step-detail.error{color:var(--red);opacity:.9}
.run-savings-chip{display:inline-flex;align-items:center;gap:4px;background:rgba(139,126,255,.1);color:var(--accent);border:1px solid rgba(139,126,255,.22);border-radius:20px;padding:2px 9px;font-size:11px;font-weight:600;flex-shrink:0}
.run-cdn-chip{display:inline-flex;align-items:center;gap:4px;background:rgba(74,222,128,.08);color:#4ade80;border:1px solid rgba(74,222,128,.2);border-radius:20px;padding:2px 9px;font-size:11px;font-weight:600;flex-shrink:0}
/* ── CDN Status Widget ───────────────────────────────────────────────────── */
.cdn-widget-badge{font-size:11px;font-weight:700;border-radius:20px;padding:2px 8px;transition:all .3s}
.cdn-widget-badge.live{color:#4ade80;background:rgba(74,222,128,.1);border:1px solid rgba(74,222,128,.25)}
.cdnw-empty{font-size:12px;color:var(--text-muted);padding:4px 0}
.cdnw-proj-name{font-size:11px;font-weight:600;color:var(--text-muted);margin-bottom:8px;letter-spacing:.2px}
.cdnw-stat-row{display:flex;gap:0;margin-bottom:12px;background:var(--surface2);border:1px solid var(--border);border-radius:8px;overflow:hidden}
.cdnw-stat{flex:1;display:flex;flex-direction:column;align-items:center;padding:10px 8px;border-right:1px solid var(--border)}
.cdnw-stat:last-child{border-right:none}
.cdnw-stat-val{font-size:14px;font-weight:700;color:var(--accent);line-height:1.1;font-family:'JetBrains Mono',ui-monospace,monospace}
.cdnw-stat-lbl{font-size:10px;color:var(--text-muted);margin-top:3px;letter-spacing:.3px}
.cdnw-mono{font-family:'JetBrains Mono',ui-monospace,monospace;font-size:11px!important}
.cdnw-locales{display:flex;flex-wrap:wrap;gap:4px;margin-bottom:12px}
.cdnw-locale-chip{font-size:10px;font-weight:600;padding:2px 7px;background:var(--surface2);border:1px solid var(--border);border-radius:10px;color:var(--text-muted)}
.cdnw-locale-more{color:var(--accent);background:var(--accent-dim);border-color:rgba(139,126,255,.2)}
.cdnw-kv-note{font-size:10px;color:var(--text-muted);margin-bottom:10px;padding:6px 8px;background:var(--surface2);border:1px solid var(--border);border-radius:6px;letter-spacing:.2px}
.cdnw-proj-list{display:flex;flex-direction:column;gap:0;margin-bottom:12px;background:var(--surface2);border:1px solid var(--border);border-radius:8px;overflow:hidden}
.cdnw-proj-row{display:flex;align-items:center;gap:8px;padding:8px 10px;border-bottom:1px solid var(--border);font-size:11px}
.cdnw-proj-row:last-child{border-bottom:none}
.cdnw-status-dot{width:6px;height:6px;border-radius:50%;background:var(--border);flex-shrink:0}
.cdnw-status-dot.live{background:#4ade80;box-shadow:0 0 6px rgba(74,222,128,.4)}
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
.run-locales{padding:6px 16px 14px;display:flex;flex-direction:column;gap:6px;border-top:1px solid var(--border)}
.run-locales-head{display:flex;align-items:center;justify-content:space-between;font-size:10.5px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.5px;font-weight:700;padding-bottom:4px}
.run-locales-eta{color:var(--text-dim);text-transform:none;letter-spacing:0;font-weight:500;font-variant-numeric:tabular-nums;font-size:11px}
.lane-row{display:flex;align-items:center;gap:10px;font-size:12px}
.lane-code{flex-shrink:0;min-width:54px;font-family:'JetBrains Mono',ui-monospace,monospace;color:var(--text-dim);font-size:10.5px}
.lane-name{flex:1;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:11.5px}
.lane-track{flex:1.4;height:3px;background:var(--surface2);border:1px solid var(--border);border-radius:2px;overflow:hidden;min-width:60px}
.lane-fill{height:100%;background:var(--text-muted);border-radius:2px;transition:width .4s ease,background .2s}
.lane-row.translating .lane-fill{background:var(--accent);animation:pulse 1.6s infinite}
.lane-row.done .lane-fill{background:#4ade80;opacity:.8}
.lane-row.error .lane-fill{background:var(--red)}
.lane-row.queued .lane-fill{background:var(--text-muted);opacity:.35}
.lane-count{flex-shrink:0;color:var(--text-muted);font-variant-numeric:tabular-nums;font-size:10.5px;min-width:46px;text-align:right;font-family:'JetBrains Mono',ui-monospace,monospace}
/* ── Resume-on-reconnect pill ───────────────────────────────────────────── */
.sse-resume-pill{position:fixed;left:50%;top:18px;transform:translateX(-50%) translateY(-12px);z-index:2200;display:none;align-items:center;gap:8px;padding:8px 14px;background:var(--surface);border:1px solid var(--accent);border-radius:20px;font-size:12px;font-weight:600;color:var(--text);box-shadow:0 8px 24px -8px rgba(0,0,0,.5);opacity:0;transition:opacity .2s,transform .2s}
.sse-resume-pill.visible{display:inline-flex;opacity:1;transform:translateX(-50%) translateY(0)}
.sse-resume-pill .sse-resume-dot{width:7px;height:7px;border-radius:50%;background:var(--accent);box-shadow:0 0 0 4px rgba(139,126,255,.15)}
.run-footer{padding:9px 16px;border-top:1px solid var(--border);display:flex;align-items:center;justify-content:space-between;gap:10px;background:rgba(255,255,255,.008)}
.pr-link{display:inline-flex;align-items:center;gap:6px;font-size:12px;color:var(--accent);font-weight:500}
.pr-link:hover{text-decoration:underline}
.run-error-msg{font-size:12px;color:var(--red);flex:1}
.run-duration{font-size:11.5px;color:var(--text-muted);font-family:'JetBrains Mono',ui-monospace,monospace}
.retry-btn{display:inline-flex;align-items:center;gap:5px;font-size:12px;padding:5px 12px;border-radius:var(--radius-sm);background:rgba(255,77,79,.07);color:var(--red);border:1px solid rgba(255,77,79,.22);cursor:pointer;transition:all .15s;flex-shrink:0}
.retry-btn:hover{background:rgba(255,77,79,.13);border-color:rgba(255,77,79,.38)}
.retry-btn:disabled{opacity:.5;cursor:not-allowed}
.retry-btn.retrying{background:rgba(250,173,20,.09);color:var(--yellow);border-color:rgba(250,173,20,.28)}
.ob-guide{background:var(--surface);border:1px solid rgba(139,126,255,.2);border-radius:var(--radius);padding:32px}
.ob-intro{margin-bottom:28px}
.ob-intro h3{font-size:18px;font-weight:700;margin-bottom:6px}
.ob-intro p{font-size:14px;color:var(--text-muted)}
.ob-steps{display:flex;flex-direction:column}
.ob-step{display:flex;align-items:center;gap:16px;padding:16px 0;border-bottom:1px solid var(--border)}
.ob-step:last-child{border-bottom:none;padding-bottom:0}
.ob-step:first-child{padding-top:0}
.ob-num{width:32px;height:32px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:700;flex-shrink:0}
.ob-done .ob-num{background:var(--accent-dim);color:var(--accent);border:1.5px solid rgba(139,126,255,.4)}
.ob-active .ob-num{background:var(--accent);color:#0a0a0a}
.ob-pending .ob-num{background:var(--surface2);color:var(--text-muted);border:1.5px solid var(--border)}
.ob-body{flex:1;display:flex;flex-direction:column;gap:3px}
.ob-body strong{font-size:14px;font-weight:600}
.ob-done .ob-body strong{color:var(--text-dim)}
.ob-pending .ob-body strong{color:var(--text-muted)}
.ob-body span{font-size:13px;color:var(--text-muted)}
.ob-cta{padding:8px 16px;font-size:13px;white-space:nowrap}
/* ── Review alert banner ─────────────────────────────────────────────────── */
.dash-alert{display:none;align-items:center;justify-content:space-between;gap:14px;padding:11px 16px;border-radius:var(--radius);border:1px solid rgba(250,173,20,.28);background:rgba(250,173,20,.05);margin-bottom:20px;font-size:13px;line-height:1.5}
.dash-alert.visible{display:flex}
.dash-alert.critical{border-color:rgba(255,77,79,.25);background:rgba(255,77,79,.04)}
.dash-alert-msg{color:var(--text-muted);flex:1}
.dash-alert-msg strong{color:var(--yellow)}
.dash-alert.critical .dash-alert-msg strong{color:var(--red)}
.dash-alert-action{font-size:13px;font-weight:600;color:var(--yellow);white-space:nowrap;padding:5px 12px;border-radius:var(--radius-sm);border:1px solid rgba(250,173,20,.28);transition:all .15s}
.dash-alert.critical .dash-alert-action{color:var(--red);border-color:rgba(255,77,79,.28)}
.dash-alert-action:hover{opacity:.8}
.wh-reject-banner{display:none;flex-direction:column;gap:6px;padding:14px 16px;border-radius:var(--radius);border:1px solid rgba(250,173,20,.22);background:rgba(250,173,20,.04);margin-bottom:20px;font-size:13px;position:relative;animation:fadeSlideIn .22s ease}
.wh-reject-banner.visible{display:flex}
.wh-reject-banner-row{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}
.wh-reject-icon{flex-shrink:0;width:16px;height:16px;margin-top:1px;color:#fbbf24;opacity:.9}
.wh-reject-body{flex:1;min-width:0}
.wh-reject-title{font-size:12px;font-weight:700;color:#fbbf24;letter-spacing:.04em;text-transform:uppercase;margin-bottom:3px}
.wh-reject-title.reason-branch_mismatch{color:#f97316}
.wh-reject-title.reason-usage_limit{color:var(--red)}
.wh-reject-detail{font-size:12.5px;color:var(--text-muted);line-height:1.6}
.wh-reject-meta{font-size:11px;color:var(--text-dim);margin-top:4px;font-family:'JetBrains Mono',monospace}
.wh-reject-dismiss{flex-shrink:0;background:transparent;border:none;color:var(--text-muted);cursor:pointer;padding:2px 4px;border-radius:4px;font-size:16px;line-height:1;transition:color .15s}
.wh-reject-dismiss:hover{color:var(--text)}
.wh-reject-action{display:inline-flex;align-items:center;gap:5px;margin-top:8px;font-size:12px;font-weight:600;color:#fbbf24;border:1px solid rgba(251,191,36,.25);border-radius:5px;padding:3px 10px;transition:opacity .15s}
.wh-reject-action:hover{opacity:.75}
.wh-reject-action.action-billing{color:var(--red);border-color:rgba(255,77,79,.25)}
@keyframes fadeSlideIn{from{opacity:0;transform:translateY(-6px)}to{opacity:1;transform:translateY(0)}}
/* ── Review Queue widget ─────────────────────────────────────────────────── */
.rq-rows{display:flex;flex-direction:column;gap:7px;margin-bottom:10px}
.rq-row{display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:var(--radius-sm);background:var(--surface2);border:1px solid var(--border)}
.rq-pending{border-color:rgba(251,191,36,.22);background:rgba(251,191,36,.04)}
.rq-blocked{border-color:rgba(248,113,113,.22);background:rgba(248,113,113,.04)}
.rq-num{font-size:22px;font-weight:700;line-height:1;min-width:28px;letter-spacing:-1px;font-family:'JetBrains Mono',ui-monospace,monospace}
.rq-pending .rq-num{color:#fbbf24}
.rq-blocked .rq-num{color:#f87171}
.rq-info{display:flex;flex-direction:column;gap:2px}
.rq-label{font-size:12px;font-weight:600;color:var(--text-dim)}
.rq-sublabel{font-size:11px;color:var(--text-muted)}
.rq-cta{display:block;text-align:center;padding:8px 14px;font-size:13px;font-weight:600;border-radius:var(--radius-sm)}
.rq-all-clear{display:flex;align-items:center;gap:8px;font-size:12px;color:var(--text-muted);padding:4px 0}
/* ── Plan widget additions ───────────────────────────────────────────────── */
.w-upgrade-link{display:block;font-size:12px;color:var(--accent);margin-top:10px;text-align:center;padding:7px 12px;border:1px solid rgba(139,126,255,.22);border-radius:var(--radius-sm);background:var(--accent-dim2);transition:all .15s;font-weight:500}
.w-upgrade-link:hover{background:var(--accent-dim);border-color:rgba(139,126,255,.4)}
/* ── Status badges (dashboard plan widget) ───────────────────────────────── */
.status-badge{display:inline-flex;align-items:center;gap:5px;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:700;letter-spacing:.4px}
.status-active{background:rgba(74,222,128,.1);color:#4ade80;border:1px solid rgba(74,222,128,.22)}
.status-cancelling{background:rgba(255,77,79,.1);color:var(--red);border:1px solid rgba(255,77,79,.2)}
.status-free{background:var(--surface2);color:var(--text-muted);border:1px solid var(--border)}
"""

private val DASHBOARD_JS = """
const BASE='/api';
let token=localStorage.getItem('syncling_token');
if(!token){var _bc=(document.cookie.match(/(?:^|;\s*)tl_token_bootstrap=([^;]*)/))||[];if(_bc[1]){token=decodeURIComponent(_bc[1]);localStorage.setItem('syncling_token',token);}}
document.cookie='tl_token_bootstrap=;path=/;max-age=0;secure;samesite=lax';
if(!token){window.location.href='/syncling';throw new Error('no token');}


function authHeaders(){return{'Authorization':'Bearer '+token,'Content-Type':'application/json'};}
async function api(path,opts={}){
  const res=await fetch(BASE+path,{...opts,headers:{...authHeaders(),...(opts.headers||{})}});
  if(res.status===401){logout();return null;}return res;
}
function logout(){localStorage.removeItem('syncling_token');window.location.href='/auth/logout';}
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
  el.innerHTML='<span class="dash-alert-msg">'+parts.join(' &middot; ')+'</span><a class="dash-alert-action" href="/review-portal">Review now &rarr;</a>';
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
    +'</div><a href="/review-portal" class="rq-cta btn btn-primary">Open review portal &rarr;</a>';
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
    else{bar.style.width='100%';bar.style.background='rgba(139,126,255,.25)';val.textContent=used+' (unlimited)';}
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
  if(cta&&sub.plan==='FREE')cta.innerHTML='<a href="/billing" class="w-upgrade-link">Upgrade for more capacity &rarr;</a>';
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
    dismissWebhookRejected();
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
  }else if(d.type==='webhook_rejected'){
    showWebhookRejected(d);
  }
}

// ── Webhook rejected banner ───────────────────────────────────────────────────
var _whRejectDismissTimer=null;
function showWebhookRejected(d){
  var el=document.getElementById('wh-reject-banner');
  if(!el)return;
  var reason=d.rejectedReason||'unknown';
  var detail=d.rejectedDetail||'The push was received but the pipeline did not start.';
  var repo=d.rejectedRepo||'';
  var branch=d.rejectedBranch||'';

  var titles={
    branch_mismatch:'Wrong branch',
    source_not_modified:'No string changes',
    usage_limit:'Quota reached',
    rate_limited:'Already queued'
  };
  var icons={
    branch_mismatch:'<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="6" y1="3" x2="6" y2="15"/><circle cx="18" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M18 9a9 9 0 0 1-9 9"/></svg>',
    source_not_modified:'<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>',
    usage_limit:'<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
    rate_limited:'<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>'
  };
  var actionHtml='';
  if(reason==='branch_mismatch'){
    actionHtml='<a class="wh-reject-action" href="/projects">Fix in project settings &rarr;</a>';
  }else if(reason==='usage_limit'){
    actionHtml='<a class="wh-reject-action action-billing" href="/billing">Upgrade plan &rarr;</a>';
  }
  var metaHtml=repo?('<span class="wh-reject-meta">'+esc(repo)+(branch?' &rarr; '+esc(branch):'')+'</span>'):'';
  el.innerHTML=
    '<div class="wh-reject-banner-row">'+
      '<span class="wh-reject-icon">'+(icons[reason]||icons['source_not_modified'])+'</span>'+
      '<div class="wh-reject-body">'+
        '<div class="wh-reject-title reason-'+esc(reason)+'">'+(titles[reason]||'Push ignored')+'</div>'+
        '<div class="wh-reject-detail">'+esc(detail)+'</div>'+
        metaHtml+
        actionHtml+
      '</div>'+
      '<button class="wh-reject-dismiss" aria-label="Dismiss" onclick="dismissWebhookRejected()">&times;</button>'+
    '</div>';
  el.className='wh-reject-banner visible';
  // Auto-dismiss rate_limited (it is informational only) after 8s; others persist until dismissed
  clearTimeout(_whRejectDismissTimer);
  if(reason==='rate_limited'){
    _whRejectDismissTimer=setTimeout(dismissWebhookRejected,8000);
  }
}
function dismissWebhookRejected(){
  var el=document.getElementById('wh-reject-banner');
  if(el)el.className='wh-reject-banner';
  clearTimeout(_whRejectDismissTimer);
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
  if(sseRetries>=SSE_MAX_RETRIES){setSseStatus('disconnected','Live updates unavailable');loadPipelineRuns();return;}
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
    +'<a href="/billing" class="conv-cta">Upgrade →</a>'
    +'<button type="button" class="conv-close" aria-label="Dismiss">×</button>';
  document.body.appendChild(el);
  requestAnimationFrame(function(){el.classList.add('visible');});
  el.querySelector('.conv-close').addEventListener('click',function(){el.classList.remove('visible');setTimeout(function(){el.remove();},250);});
  setTimeout(function(){if(el.parentNode){el.classList.remove('visible');setTimeout(function(){el.remove();},250);}},14000);
}

if(window._tlOnWake)window._tlOnWake(function(){loadStats();loadPlanWidget();loadCdnStatus();});
loadStats();loadPlanWidget();loadPipelineRuns();loadCdnStatus();connectPipelineSSE();
"""

internal val NOTIFICATIONS_JS = """(function(){const NOTIF_BASE='/api/notifications';let notifState=[];let panelOpen=!1;function notifApi(path,opts){return fetch(NOTIF_BASE+path,{...opts,headers:{...authHeaders(),...(opts?.headers||{})}})}async function loadNotifications(){try{const res=await notifApi('');if(!res.ok)return;const d=await res.json();notifState=d.notifications||[];renderNotifBadge(d.unreadCount||0);renderNotifList()}catch{}}function renderNotifBadge(count){const bell=document.getElementById('notif-bell'),badge=document.getElementById('notif-badge');if(!bell||!badge)return;if(count>0){badge.textContent=count>9?'9+':String(count);badge.style.display='block';bell.classList.add('has-unread')}else{badge.style.display='none';bell.classList.remove('has-unread')}}function renderNotifList(){const list=document.getElementById('notif-list');if(!list)return;if(!notifState.length){list.innerHTML='<div class="notif-empty">No notifications yet</div>';return}list.innerHTML=notifState.map(n=>{const isUnread=!n.readAt,dotClass=isUnread?n.level||'info':'read',timeAgo=formatTimeAgo(n.createdAt),action=n.actionUrl?`<a class="notif-action-link" href="${'$'}{esc(n.actionUrl)}" onclick="event.stopPropagation()">${'$'}{esc(n.actionLabel||'View')}</a>`:'';return `<div class="notif-item${'$'}{isUnread?' unread':''}" onclick="onNotifClick('${'$'}{n.id}','${'$'}{esc(n.actionUrl||'')}')"><div class="notif-dot ${'$'}{dotClass}"></div><div class="notif-body"><div class="notif-title">${'$'}{esc(n.title)}</div><div class="notif-msg">${'$'}{esc(n.message)}</div><div class="notif-meta"><span class="notif-time">${'$'}{timeAgo}</span>${'$'}{action}</div></div></div>`}).join('')}function formatTimeAgo(ms){const diff=Date.now()-ms;if(diff<60000)return'just now';if(diff<3600000)return Math.floor(diff/60000)+'m ago';if(diff<86400000)return Math.floor(diff/3600000)+'h ago';return Math.floor(diff/86400000)+'d ago'}async function onNotifClick(id,actionUrl){const n=notifState.find(x=>x.id===id);if(n&&!n.readAt){n.readAt=Date.now();const unread=notifState.filter(x=>!x.readAt).length;renderNotifBadge(unread);renderNotifList();notifApi('/'+id+'/read',{method:'POST'}).catch(()=>{})}if(actionUrl){window.location.href=actionUrl}}async function markAllNotifsRead(){notifState.forEach(n=>{if(!n.readAt)n.readAt=Date.now()});renderNotifBadge(0);renderNotifList();try{await notifApi('/read-all',{method:'POST'})}catch{}}function toggleNotifPanel(){panelOpen?closeNotifPanel():openNotifPanel()}function openNotifPanel(){panelOpen=!0;document.getElementById('notif-panel')?.classList.add('open');document.getElementById('notif-overlay')?.classList.add('open')}function closeNotifPanel(){panelOpen=!1;document.getElementById('notif-panel')?.classList.remove('open');document.getElementById('notif-overlay')?.classList.remove('open')}window.pushInAppNotification=function(evt){const existing=notifState.find(n=>n.id===evt.notificationId);if(existing)return;const n={id:evt.notificationId,title:evt.notificationTitle,message:evt.notificationMessage,level:evt.notificationLevel||'info',actionUrl:evt.notificationActionUrl,actionLabel:evt.notificationActionLabel,createdAt:Date.now(),readAt:null};notifState.unshift(n);const unread=notifState.filter(x=>!x.readAt).length;renderNotifBadge(unread);renderNotifList();const bell=document.getElementById('notif-bell');if(bell){bell.classList.add('ringing');setTimeout(()=>bell.classList.remove('ringing'),600)}};window.toggleNotifPanel=toggleNotifPanel;window.closeNotifPanel=closeNotifPanel;window.markAllNotifsRead=markAllNotifsRead;if(window._tlOnWake)window._tlOnWake(loadNotifications);loadNotifications()})();"""

internal const val ONBOARDING_CSS = """
.ob-overlay{position:fixed;inset:0;z-index:9000;pointer-events:none}
.ob-overlay.active{pointer-events:auto}
.ob-shade{position:fixed;background:rgba(8,10,14,.62);transition:opacity .18s;opacity:0}
.ob-overlay.active .ob-shade{opacity:1}
.ob-spot{position:fixed;border-radius:10px;box-shadow:0 0 0 3px rgba(139,126,255,.55),0 0 0 99999px rgba(8,10,14,.62);pointer-events:none;transition:all .22s cubic-bezier(.2,.7,.2,1);opacity:0}
.ob-overlay.active .ob-spot.visible{opacity:1}
.ob-pop{position:fixed;background:var(--surface,#15181d);border:1px solid var(--border,#262a31);border-radius:12px;padding:18px 18px 14px;width:340px;max-width:calc(100vw - 32px);box-shadow:0 16px 48px -12px rgba(0,0,0,.6);color:var(--text,#e6e7eb);z-index:9001;opacity:0;transform:translateY(4px);transition:opacity .18s,transform .18s}
.ob-overlay.active .ob-pop.visible{opacity:1;transform:translateY(0)}
.ob-pop-eyebrow{font-size:11px;font-weight:700;letter-spacing:.6px;text-transform:uppercase;color:var(--accent,#8b7eff);margin-bottom:6px}
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
.ob-pop-btn.primary{background:var(--accent,#8b7eff);color:#06281d;border-color:var(--accent,#8b7eff)}
.ob-pop-btn.primary:hover{filter:brightness(1.08);color:#06281d}
.ob-pop-btn:focus-visible{outline:2px solid var(--accent,#8b7eff);outline-offset:2px}
.ob-resume-pill{position:fixed;bottom:24px;right:24px;z-index:8500;display:none;align-items:center;gap:10px;padding:10px 16px;background:var(--surface,#15181d);border:1px solid var(--accent,#8b7eff);border-radius:24px;font-size:13px;font-weight:600;color:var(--text,#e6e7eb);box-shadow:0 8px 28px -8px rgba(0,0,0,.5);cursor:pointer;transition:transform .15s}
.ob-resume-pill:hover{transform:translateY(-1px)}
.ob-resume-pill.visible{display:inline-flex}
.ob-resume-pill .ob-resume-dot{width:8px;height:8px;border-radius:50%;background:var(--accent,#8b7eff);box-shadow:0 0 0 4px rgba(139,126,255,.15)}
@media (prefers-reduced-motion:reduce){.ob-spot,.ob-pop{transition:none}}
"""

internal val ONBOARDING_JS = """(function(){if(window.Onboarding)return;var BASE='/api/onboarding',state=null,page='dashboard',steps=[],idx=0,prevFocus=null;function authHeaders(){var t=localStorage.getItem('syncling_token');return t?{'Authorization':'Bearer '+t,'Content-Type':'application/json'}:{'Content-Type':'application/json'}}function fetchState(){return fetch(BASE+'/state',{headers:authHeaders()}).then(function(r){return r.ok?r.json():null}).catch(function(){return null})}function postSkip(){return fetch(BASE+'/skip',{method:'POST',headers:authHeaders()}).catch(function(){})}function postResume(){return fetch(BASE+'/resume',{method:'POST',headers:authHeaders()}).catch(function(){})}function host(){var h=document.getElementById('ob-host');if(!h){h=document.createElement('div');h.id='ob-host';document.body.appendChild(h)}return h}function esc(s){var d=document.createElement('div');d.textContent=String(s==null?'':s);return d.innerHTML}function planLabel(p){return p==='SOLO'?'Solo':p==='TEAM'?'Team':p==='ENTERPRISE'?'Enterprise':'Free'}function buildSteps(){var planChip='<span class="ob-chip">Plan: <strong style="margin-left:4px">'+esc(planLabel(state.plan))+'</strong></span>',trialChip=state.inTrial?'<span class="ob-chip warn">Trial</span>':'',meta=planChip+trialChip;if(page==='dashboard'){return[{eyebrow:'Welcome',title:'Welcome to Syncling',body:'You\'re all set on the <strong>'+esc(planLabel(state.plan))+'</strong> plan. In the next two steps we\'ll connect your GitHub repo and watch your first translation run.',meta:meta,anchor:null,primary:{label:'Get started',action:'next'},secondary:{label:'Skip',action:'skip'}},{eyebrow:'Step 1 of 2',title:'Connect your first repository',body:'Click <strong>+ New project</strong> to point Syncling at your GitHub repo. We\'ll auto-install the webhook so every push triggers translation.',anchor:'#qa-new-project',primary:{label:'Open projects',action:'navigate',href:'/projects?ob=connect'},secondary:{label:'Skip',action:'skip'}}]}if(page==='projects'){return[{eyebrow:'Step 2 of 2',title:'Create your project',body:'Fill in your GitHub repo URL (e.g. <strong>owner/repo</strong>), pick a branch and target languages. We\'ll handle the webhook.',anchor:'#new-proj-btn',primary:{label:'Open form',action:'click-anchor'},secondary:{label:'Skip',action:'skip'}}]}return[]}function getAnchorRect(sel){if(!sel)return null;var el=document.querySelector(sel);if(!el)return null;el.scrollIntoView({block:'center',behavior:'smooth'});return el.getBoundingClientRect()}function positionPop(pop,rect){var pad=12,vw=window.innerWidth,vh=window.innerHeight,pw=pop.offsetWidth,ph=pop.offsetHeight;if(!rect){pop.style.left=Math.max(16,(vw-pw)/2)+'px';pop.style.top=Math.max(16,(vh-ph)/2)+'px';return}var preferBelow=(rect.bottom+pad+ph)<=vh-8,top=preferBelow?(rect.bottom+pad):Math.max(16,rect.top-pad-ph),left=Math.min(Math.max(16,rect.left+(rect.width-pw)/2),vw-pw-16);pop.style.left=left+'px';pop.style.top=top+'px'}function render(){cleanup();var step=steps[idx];if(!step)return;var overlay=document.createElement('div');overlay.className='ob-overlay active';overlay.setAttribute('role','dialog');overlay.setAttribute('aria-modal','true');overlay.setAttribute('aria-live','polite');var shade=document.createElement('div');shade.className='ob-shade';shade.style.inset='0';overlay.appendChild(shade);var spot=document.createElement('div');spot.className='ob-spot';overlay.appendChild(spot);var pop=document.createElement('div');pop.className='ob-pop';var meta=step.meta?'<div class="ob-pop-meta">'+step.meta+'</div>':'',progress=steps.length>1?(idx+1)+' / '+steps.length:'';pop.innerHTML='<div class="ob-pop-eyebrow">'+esc(step.eyebrow||'')+'</div>'+'<div class="ob-pop-title">'+esc(step.title)+'</div>'+'<div class="ob-pop-body">'+step.body+'</div>'+meta+'<div class="ob-pop-actions">'+'<span class="ob-pop-progress">'+esc(progress)+'</span>'+'<div class="ob-pop-buttons">'+(step.secondary?'<button type="button" class="ob-pop-btn" data-act="'+esc(step.secondary.action)+'">'+esc(step.secondary.label)+'</button>':'')+(step.primary?'<button type="button" class="ob-pop-btn primary" data-act="'+esc(step.primary.action)+'">'+esc(step.primary.label)+'</button>':'')+'</div></div>';overlay.appendChild(pop);host().appendChild(overlay);var rect=getAnchorRect(step.anchor);if(rect){var pad=8;spot.style.left=(rect.left-pad)+'px';spot.style.top=(rect.top-pad)+'px';spot.style.width=(rect.width+pad*2)+'px';spot.style.height=(rect.height+pad*2)+'px';spot.classList.add('visible')}requestAnimationFrame(function(){positionPop(pop,rect);pop.classList.add('visible')});var btns=pop.querySelectorAll('button[data-act]');btns.forEach(function(b){b.addEventListener('click',function(){handleAction(b.getAttribute('data-act'),step)})});var first=pop.querySelector('button.primary')||btns[0];if(first){prevFocus=document.activeElement;first.focus()}overlay._onKey=function(e){if(e.key==='Escape'){e.preventDefault();skip()}else if(e.key==='Tab'){var nodes=Array.prototype.slice.call(btns);if(!nodes.length)return;var i=nodes.indexOf(document.activeElement);if(e.shiftKey){if(i<=0){e.preventDefault();nodes[nodes.length-1].focus()}}else{if(i===nodes.length-1){e.preventDefault();nodes[0].focus()}}}};document.addEventListener('keydown',overlay._onKey);overlay._onResize=function(){positionPop(pop,getAnchorRect(step.anchor))};window.addEventListener('resize',overlay._onResize);window.addEventListener('scroll',overlay._onResize,!0)}function cleanup(){var h=document.getElementById('ob-host');if(!h)return;Array.prototype.slice.call(h.querySelectorAll('.ob-overlay')).forEach(function(o){if(o._onKey)document.removeEventListener('keydown',o._onKey);if(o._onResize){window.removeEventListener('resize',o._onResize);window.removeEventListener('scroll',o._onResize,!0)}o.remove()});if(prevFocus&&prevFocus.focus){try{prevFocus.focus()}catch(_){}prevFocus=null}}function handleAction(act,step){if(act==='next'){idx++;if(idx>=steps.length){finish()}else{render()}}else if(act==='skip'){skip()}else if(act==='navigate'){postSkip();window.location.href=step.primary.href}else if(act==='click-anchor'){var el=step.anchor&&document.querySelector(step.anchor);cleanup();if(el)el.click()}}function finish(){cleanup();renderResumePill(!1)}function skip(){postSkip();cleanup();renderResumePill(!0)}function renderResumePill(show){var existing=document.getElementById('ob-resume-pill');if(!show){if(existing)existing.classList.remove('visible');return}if(existing){existing.classList.add('visible');return}var pill=document.createElement('div');pill.id='ob-resume-pill';pill.className='ob-resume-pill';pill.setAttribute('role','button');pill.setAttribute('tabindex','0');pill.setAttribute('aria-label','Resume setup');pill.innerHTML='<span class="ob-resume-dot"></span><span>Resume setup</span>';function resume(){postResume().then(function(){pill.classList.remove('visible');state.dismissed=!1;steps=buildSteps();idx=0;render()})}pill.addEventListener('click',resume);pill.addEventListener('keydown',function(e){if(e.key==='Enter'||e.key===' '){e.preventDefault();resume()}});document.body.appendChild(pill);requestAnimationFrame(function(){pill.classList.add('visible')})}function shouldRun(){if(!state)return!1;if(state.completed)return!1;if(page==='dashboard')return state.step==='SIGNED_UP';if(page==='projects'){var fromDash=new URLSearchParams(window.location.search).get('ob')==='connect';return(state.step==='SIGNED_UP'&&!state.hasProject)||fromDash}return!1}var Onboarding={boot:function(pageName){page=pageName||'dashboard';fetchState().then(function(s){if(!s)return;state=s;if(state.dismissed&&!state.completed){renderResumePill(!0);return}if(!shouldRun())return;steps=buildSteps();idx=0;render()})},refresh:function(){fetchState().then(function(s){if(!s)return;state=s;if(state.completed){cleanup();renderResumePill(!1);return}if(state.step!=='SIGNED_UP'){cleanup()}})},cleanup:cleanup};window.Onboarding=Onboarding})();"""
