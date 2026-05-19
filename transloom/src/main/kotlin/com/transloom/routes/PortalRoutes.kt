package com.transloom.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*

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

            // Incomplete checkout — route back to the payment page.
            if (!pendingPlan.isNullOrBlank() && sessionUserId != null) {
                call.respondRedirect("/transloom/billing/checkout?plan=$pendingPlan")
                return@get
            }

            // If the dashboard JS has no token in localStorage it redirects to the
            // landing page. Recover any user who arrived without a ?token= param but
            // does have a valid tl_session cookie (e.g. came through the checkout flow
            // or is trying to re-open the dashboard after the token expired from storage).
            val urlToken = call.request.queryParameters["token"]
            if (urlToken.isNullOrBlank() && sessionUserId != null) {
                val sessionToken = call.request.cookies[SESSION_COOKIE]
                if (!sessionToken.isNullOrBlank()) {
                    call.respondRedirect(
                        "/transloom/app?token=" + java.net.URLEncoder.encode(sessionToken, "UTF-8")
                    )
                    return@get
                }
            }

            call.respondHtml { dashboardApp() }
        }
        get("/welcome") { call.respondHtml { welcomePage() } }
        get("/billing") { call.respondHtml { billingApp() } }
        get("/projects") { call.respondHtml { projectsApp() } }
        get("/review-portal") { call.respondHtml { reviewPortal() } }
        get("/favicon.svg") {
            call.respondText(FAVICON_SVG, ContentType("image", "svg+xml"))
        }
    }
}

private fun HEAD.favicon() {
    link {
        rel = "icon"
        type = "image/svg+xml"
        href = "/transloom/favicon.svg"
    }
}

// ─── Shared logo ──────────────────────────────────────────────────────────────

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

// ─── Shared app sidebar HTML (raw, injected via unsafe{}) ────────────────────

private fun appSidebar(active: String, reviewBadge: Boolean = false) = """
<aside class="sidebar">
  <div class="sidebar-logo brand">$LOGO_SVG<span>Transloom</span></div>
  <nav class="sidebar-nav">
    <a href="/transloom/app" class="nav-item${if (active=="dash") " active" else ""}">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
      Dashboard
    </a>
    <a href="/transloom/projects" class="nav-item${if (active=="projects") " active" else ""}">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
      Projects
    </a>
    <a href="/transloom/review-portal" class="nav-item${if (active=="review") " active" else ""}">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
      Review ${if (reviewBadge) """<span class="nav-badge review-badge" id="review-count"></span>""" else ""}
    </a>
    <a href="/transloom/billing" class="nav-item${if (active=="billing") " active" else ""}">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg>
      Billing
    </a>
  </nav>
  <div class="sidebar-footer">
    <div class="user-chip" id="user-chip">Loading…</div>
    <button class="btn btn-ghost logout-btn" onclick="logout()">Sign out</button>
  </div>
</aside>
"""

private val APP_SIDEBAR_DASH     get() = appSidebar("dash",     reviewBadge = true)
private val APP_SIDEBAR_PROJECTS get() = appSidebar("projects")
private val APP_SIDEBAR_BILLING  get() = appSidebar("billing")
private val APP_SIDEBAR_REVIEW   get() = appSidebar("review")

// ─── Shared CSS ───────────────────────────────────────────────────────────────

private const val SHARED_CSS = """
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
"""

// ─── Helpers ─────────────────────────────────────────────────────────────────

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

// ─── Welcome / Plan Selection Page (shown to brand-new users after OAuth) ─────

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

// ─── Landing Page ─────────────────────────────────────────────────────────────

private fun HTML.landingPage() {
    head {
        title { +"Transloom — Automated app localization for developers" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(name = "description", content = "Push a commit. Transloom translates your new strings into every target language and opens a pull request — automatically.")
        favicon()
        style { unsafe { +"$SHARED_CSS$LANDING_CSS" } }
    }
    body {
        nav {
            div("nav-inner") {
                div("brand") { unsafe { +LOGO_SVG }; span { +"Transloom" } }
                div("nav-links") {
                    a("#how") { +"How it works" }
                    a("/transloom#features") { +"Features" }
                    a("#pricing") { +"Pricing" }
                    a("/transloom/auth/github") { classes = setOf("btn", "btn-ghost", "nav-cta"); +"Login" }
                    a("#pricing") { classes = setOf("btn", "btn-primary", "nav-cta"); +"Get started" }
                }
            }
        }

        section("hero") {
            div("hero-glow") {}
            div("hero-inner") {
                span("badge fade-up") { +"Now in Beta · Free to start" }
                h1("hero-title fade-up d1") {
                    +"Developer adds "; span("accent") { +"one string" }; +". We handle"; br {}; +"the rest."
                }
                p("hero-sub fade-up d2") {
                    +"Push to GitHub. Transloom detects new strings, translates them,"; br {}
                    +"respects your glossary, and opens a pull request — automatically."
                }
                div("hero-actions fade-up d3") {
                    a("#pricing") { classes = setOf("btn", "btn-primary", "hero-btn"); +"Get started" }
                    a("/transloom#how") { classes = setOf("btn", "btn-ghost"); +"See how it works →" }
                }
                div("hero-lang-strip fade-up d4") {
                    listOf("🇪🇸 ES","🇫🇷 FR","🇩🇪 DE","🇯🇵 JA","🇰🇷 KO","🇨🇳 ZH","🇮🇳 HI","🇧🇷 PT","🇮🇹 IT","🇸🇦 AR")
                        .forEach { span("lang-chip") { +it } }
                }
            }
        }

        section("demo-section") {
            div("demo-inner") {
                p("section-label fade-up") { +"FROM COMMIT TO PR" }
                h2("demo-title fade-up d1") { +"See it work in seconds." }
                div("demo-grid fade-up d2") {
                    div("demo-card demo-before") {
                        div("demo-card-head") {
                            span("demo-dot dot-r") {}; span("demo-dot dot-y") {}; span("demo-dot dot-g") {}
                            span("demo-file") { +"values/strings.xml" }
                        }
                        div("demo-card-body") {
                            div("code-line ln-context") { span("ln") { +"12" }; span("code") { +"  <string name=\"greeting\">Hello</string>" } }
                            div("code-line ln-add") { span("ln") { +"13" }; span("code") { +"+ <string name=\"welcome\">Welcome back!</string>" } }
                            div("code-line ln-context") { span("ln") { +"14" }; span("code") { +"  <string name=\"settings\">Settings</string>" } }
                            div("demo-cta-row") {
                                span("git-prompt") { +"$ git push" }
                            }
                        }
                    }
                    div("demo-arrow") {
                        unsafe { +"""<svg viewBox="0 0 64 16" width="56" height="14" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M2 8h58M52 2l10 6-10 6"/></svg>""" }
                        span("demo-arrow-label") { +"~60s" }
                    }
                    div("demo-card demo-after") {
                        div("demo-card-head") {
                            span("pr-badge") { +"PR #128" }
                            span("demo-file") { +"auto-translated by Transloom" }
                        }
                        div("demo-card-body") {
                            listOf(
                                "🇪🇸" to "¡Bienvenido de nuevo!",
                                "🇫🇷" to "Bon retour !",
                                "🇩🇪" to "Willkommen zurück!",
                                "🇯🇵" to "おかえりなさい！",
                                "🇮🇳" to "वापसी पर स्वागत है!"
                            ).forEachIndexed { i, (flag, txt) ->
                                div("tr-row") {
                                    span("tr-flag") { +flag }
                                    span("tr-text") { +"\"$txt\"" }
                                    span("tr-check") { +"✓" }
                                }
                            }
                            div("demo-cta-row demo-cta-pass") {
                                span("check-pill") { +"✓ All checks passed" }
                            }
                        }
                    }
                }
            }
        }

        section("how-section") {
            id = "how"
            div("section-inner") {
                p("section-label fade-up") { +"HOW IT WORKS" }
                h2("fade-up d1") { +"Ship globally. Same team. Same week." }
                p("how-sub fade-up d2") { +"Your engineers push code the way they always have. We handle the rest — automatically, in under a minute." }

                // Compact 3-step horizontal flow with animated connector
                div("flow fade-up d3") {
                    div("flow-step") {
                        div("flow-icon-wrap") {
                            div("flow-icon") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>""" }
                            }
                            div("flow-ring") {}
                        }
                        p("flow-num") { +"01" }
                        h4("flow-title") { +"You push code" }
                        p("flow-desc") { +"Add a new string. Push to GitHub. That's it." }
                    }

                    div("flow-connector") { div("flow-dot") {} }

                    div("flow-step") {
                        div("flow-icon-wrap") {
                            div("flow-icon") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a10 10 0 1 0 10 10"/><path d="M22 2L12 12"/><circle cx="12" cy="12" r="3"/></svg>""" }
                            }
                            div("flow-ring") {}
                        }
                        p("flow-num") { +"02" }
                        h4("flow-title") { +"We translate" }
                        p("flow-desc") { +"AI handles every language. Context-aware. On-brand." }
                    }

                    div("flow-connector") { div("flow-dot") {} }

                    div("flow-step") {
                        div("flow-icon-wrap") {
                            div("flow-icon") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3v12"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="6" r="3"/><path d="M18 9v3a3 3 0 0 1-3 3H9"/></svg>""" }
                            }
                            div("flow-ring") {}
                        }
                        p("flow-num") { +"03" }
                        h4("flow-title") { +"PR ready to ship" }
                        p("flow-desc") { +"Auto-opened pull request. Review or auto-merge." }
                    }
                }

                // Team value section
                div("team-value") {
                    p("section-label tv-label fade-up") { +"BUILT FOR EVERY TEAM" }
                    div("team-grid") {
                        div("team-card fade-up") {
                            div("team-icon-bg") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>""" }
                            }
                            h4("team-name") { +"Engineering" }
                            p("team-desc") { +"Zero translation tickets. No context-switching. Just push and ship." }
                        }
                        div("team-card fade-up d1") {
                            div("team-icon-bg") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v18h18"/><path d="M7 14l4-4 4 4 6-6"/></svg>""" }
                            }
                            h4("team-name") { +"Product" }
                            p("team-desc") { +"Launch in new markets in days, not months. Test features globally from day one." }
                        }
                        div("team-card fade-up d2") {
                            div("team-icon-bg") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12h20"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/><circle cx="12" cy="12" r="10"/></svg>""" }
                            }
                            h4("team-name") { +"Localization" }
                            p("team-desc") { +"Glossary control and review portal. AI does the work, you keep the quality bar." }
                        }
                        div("team-card fade-up d3") {
                            div("team-icon-bg") {
                                unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>""" }
                            }
                            h4("team-name") { +"Leadership" }
                            p("team-desc") { +"Predictable monthly cost. Measurable time-to-market. No vendor lock-in." }
                        }
                    }
                }

                div("how-footer fade-up") {
                    div("time-pill") {
                        span("time-dot") {}
                        +"Average: "
                        strong { +"~45 seconds" }
                        span("time-sep") { +"·" }
                        +"From commit to merge-ready PR"
                    }
                }
            }
        }

        section("features-section") {
            id = "features"
            div("section-inner") {
                p("section-label fade-up") { +"FEATURES" }
                h2("fade-up d1") { +"Everything you need to ship globally. Nothing you don't." }
                div("features-grid") {
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><path d="M15 2v2M9 2v2M15 20v2M9 20v2M2 15h2M2 9h2M20 15h2M20 9h2"/></svg>""","Intelligent translations","Understands your app category and tone to produce natural-sounding translations.","fade-up")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>""","Placeholder guard","Automatic detection of %1\$s, %d, %@, &#8211; — bad translations are blocked before they ship.","fade-up d1")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>""","Glossary enforcement","Define brand terms once per language. Applied consistently across every string, every time.","fade-up d2")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>""","Review portal","Flag anomalous translations for human review before they hit your main branch.","fade-up d3")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3c-1 3-2 4-5 5 3 1 4 2 5 5 1-3 2-4 5-5-3-1-4-2-5-5z"/><path d="M5.5 10.5c-.5 1.5-1 2-2.5 2.5 1.5.5 2 1 2.5 2.5.5-1.5 1-2 2.5-2.5-1.5-.5-2-1-2.5-2.5z"/><path d="M18.5 5c-.3 1-.7 1.3-1.5 1.5.8.2 1.2.5 1.5 1.5.3-1 .7-1.3 1.5-1.5-.8-.2-1.2-.5-1.5-1.5z"/></svg>""","Smart change detection","AI classifies every English edit — surface rewrites skip retranslation; semantic changes trigger it. Fewer API calls, lower cost.","fade-up d4")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>""","Cultural sensitivity","Post-translation AI check flags formality mismatches, inappropriate idioms, and market-specific concerns before they ship.","fade-up d4")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>""","Translation memory","Identical strings reuse cached translations — faster throughput, lower API cost.","fade-up d4")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>""","Android + iOS","Native support for strings.xml and Localizable.strings file formats out of the box.","fade-up d4")
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
                        }
                        a("/transloom/billing/start-subscription?plan=TEAM") { classes = setOf("pricing-cta", "outline"); +"Start 7-day free trial" }
                    }
                }
                p("pricing-note") { +"All paid plans include a 7-day free trial. No charge until the trial ends — cancel any time." }
            }
        }

        section("cta-section") {
            div("cta-inner") {
                h2("fade-up") { +"Ready to ship in every language?" }
                p("fade-up d1") { +"Free tier includes 500 strings/month across 1 project." }
                a("/transloom/auth/github") { classes = setOf("btn","btn-primary","cta-btn","fade-up","d2"); +"Get started free" }
            }
        }

        footer {
            div("footer-inner") {
                div("brand") { unsafe { +LOGO_SVG }; span { +"Transloom" } }
                span("text-muted") { +"© 2026 · Built for developers who ship globally." }
            }
        }

        script { unsafe { +"""
            const params=new URLSearchParams(window.location.search);
            if(params.get('billing_error')==='link_failed'){
                const sub=params.get('sub')||'';
                const banner=document.createElement('div');
                banner.style.cssText='position:fixed;top:0;left:0;right:0;background:#3a1a1a;border-bottom:1px solid #ff4d4f;color:#ffb8b8;padding:14px 24px;text-align:center;font-size:14px;z-index:9999;line-height:1.5';
                banner.innerHTML='<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="display:inline;vertical-align:-3px;margin-right:6px"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>We received your payment but couldn'+String.fromCharCode(39)+'t link it to your account. Please email <a href="mailto:support@androidplay.in?subject=Subscription%20'+sub+'" style="color:#fff;text-decoration:underline">support@androidplay.in</a> with reference: <code style="background:rgba(255,255,255,.1);padding:2px 6px;border-radius:3px">'+sub+'</code>';
                document.body.prepend(banner);
                history.replaceState({},'','/transloom');
            } else {
                const t=localStorage.getItem('transloom_token');if(t){window.location.href='/transloom/app';}
            }
            const io=new IntersectionObserver((entries)=>{
                entries.forEach(e=>{if(e.isIntersecting){e.target.classList.add('in-view');io.unobserve(e.target);}});
            },{threshold:0.12,rootMargin:'0px 0px -40px 0px'});
            document.querySelectorAll('.fade-up').forEach(el=>io.observe(el));
        """ } }
    }
}

private const val LANDING_CSS = """
@keyframes heroDrift{0%,100%{transform:translate(-50%,0) scale(1)}50%{transform:translate(-50%,30px) scale(1.08)}}
@keyframes shimmer{0%{background-position:-200% 0}100%{background-position:200% 0}}
nav{position:sticky;top:0;z-index:100;background:rgba(8,8,8,.78);backdrop-filter:blur(14px);-webkit-backdrop-filter:blur(14px);border-bottom:1px solid var(--border)}
.nav-inner{max-width:1100px;margin:0 auto;padding:14px 24px;display:flex;align-items:center;justify-content:space-between}
.nav-links{display:flex;align-items:center;gap:24px}
.nav-links a:not(.btn){color:var(--text-muted);font-size:14px;transition:color .2s ease}
.nav-links a:not(.btn):hover{color:var(--text)}
.nav-cta{padding:8px 16px!important;font-size:13px!important}
.hero{position:relative;overflow:hidden;padding:96px 24px 60px;text-align:center}
.hero-glow{position:absolute;top:-200px;left:50%;transform:translateX(-50%);width:680px;height:680px;background:radial-gradient(circle,rgba(0,229,160,.14) 0%,transparent 70%);pointer-events:none;animation:heroDrift 12s ease-in-out infinite}
.hero-inner{max-width:760px;margin:0 auto;position:relative}
.hero-title{font-size:clamp(36px,6vw,62px);font-weight:800;line-height:1.1;letter-spacing:-1.5px;margin:20px 0}
.accent{color:var(--accent)}
.hero-sub{color:var(--text-muted);font-size:17px;line-height:1.7;margin-bottom:36px}
.hero-actions{display:flex;gap:12px;justify-content:center;flex-wrap:wrap;margin-bottom:48px}
.hero-btn{padding:13px 24px;font-size:15px}
.hero-lang-strip{display:flex;flex-wrap:wrap;gap:8px;justify-content:center}
.lang-chip{background:var(--surface);border:1px solid var(--border);border-radius:20px;padding:4px 12px;font-size:13px;color:var(--text-dim);transition:border-color .2s ease,color .2s ease,transform .2s ease}
.lang-chip:hover{border-color:var(--accent);color:var(--accent);transform:translateY(-1px)}
.demo-section{padding:72px 24px;background:linear-gradient(180deg,var(--bg) 0%,var(--surface2) 100%);border-top:1px solid var(--border)}
.demo-inner{max-width:1100px;margin:0 auto;text-align:center}
.demo-title{margin-bottom:36px!important}
.demo-grid{display:grid;grid-template-columns:1fr auto 1fr;gap:20px;align-items:center;text-align:left}
.demo-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden;box-shadow:0 12px 40px -16px rgba(0,0,0,.6);transition:transform .3s ease,border-color .3s ease}
.demo-card:hover{transform:translateY(-2px);border-color:rgba(0,229,160,.3)}
.demo-card-head{display:flex;align-items:center;gap:8px;padding:10px 14px;background:var(--surface2);border-bottom:1px solid var(--border)}
.demo-dot{width:9px;height:9px;border-radius:50%;display:inline-block}
.dot-r{background:#ff5f56}.dot-y{background:#ffbd2e}.dot-g{background:#27c93f}
.demo-file{font-family:ui-monospace,'SF Mono',Menlo,monospace;font-size:11px;color:var(--text-muted);margin-left:6px}
.pr-badge{background:var(--accent-dim);color:var(--accent);border:1px solid rgba(0,229,160,.3);font-size:11px;font-weight:600;padding:2px 8px;border-radius:6px}
.demo-card-body{padding:14px 14px 16px;font-family:ui-monospace,'SF Mono',Menlo,monospace;font-size:12.5px;line-height:1.7;min-height:200px;display:flex;flex-direction:column;gap:2px}
.code-line{display:flex;gap:10px;align-items:flex-start;border-radius:4px;padding:1px 4px}
.code-line .ln{color:var(--text-muted);opacity:.5;min-width:18px;text-align:right;user-select:none}
.code-line .code{color:var(--text-dim);white-space:pre-wrap;word-break:break-all}
.ln-add{background:rgba(0,229,160,.08)}
.ln-add .code{color:var(--accent)}
.ln-context .code{opacity:.55}
.demo-cta-row{margin-top:auto;padding-top:14px;border-top:1px dashed var(--border)}
.git-prompt{font-size:12px;color:var(--accent);font-weight:500}
.demo-cta-pass{display:flex;justify-content:flex-end;border-top:1px dashed rgba(0,229,160,.25)}
.check-pill{background:var(--accent-dim);color:var(--accent);border:1px solid rgba(0,229,160,.3);font-size:11px;font-weight:600;padding:3px 10px;border-radius:20px}
.tr-row{display:flex;align-items:center;gap:10px;padding:5px 4px;border-radius:4px}
.tr-flag{font-size:14px;flex-shrink:0}
.tr-text{flex:1;color:var(--text);font-size:12.5px}
.tr-check{color:var(--accent);font-weight:700;font-size:13px}
.demo-arrow{display:flex;flex-direction:column;align-items:center;gap:6px;color:var(--accent);opacity:.7}
.demo-arrow-label{font-family:ui-monospace,'SF Mono',Menlo,monospace;font-size:11px;color:var(--text-muted)}
section{padding:80px 24px}
.section-inner{max-width:1100px;margin:0 auto}
.section-label{font-size:11px;font-weight:700;letter-spacing:2px;color:var(--accent);margin-bottom:12px}
h2{font-size:clamp(26px,4vw,40px);font-weight:700;letter-spacing:-.5px;margin-bottom:48px}
.steps{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:24px}
.step{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:24px;display:flex;gap:16px;transition:border-color .25s ease,transform .25s ease,box-shadow .25s ease}
.step:hover{border-color:rgba(0,229,160,.3);transform:translateY(-2px);box-shadow:0 12px 32px -16px rgba(0,229,160,.18)}
.step-num{font-size:28px;font-weight:800;color:var(--accent);opacity:.3;line-height:1;min-width:36px;transition:opacity .25s ease}
.step:hover .step-num{opacity:.7}
.step-body h3{font-size:15px;font-weight:600;margin-bottom:6px}
.step-body p{font-size:13px;color:var(--text-muted);line-height:1.6}
/* ─── How it works — concise 3-step flow + team value grid ──────────────── */
.how-section{padding:88px 24px 100px}
.how-sub{color:var(--text-muted);font-size:16px;margin-top:-28px;margin-bottom:56px;max-width:580px;line-height:1.6}

/* Horizontal 3-step flow */
.flow{display:flex;align-items:flex-start;justify-content:center;gap:0;max-width:920px;margin:0 auto 80px;flex-wrap:wrap;position:relative}
.flow-step{flex:1;min-width:200px;display:flex;flex-direction:column;align-items:center;text-align:center;padding:0 12px;position:relative}
.flow-icon-wrap{position:relative;width:72px;height:72px;display:flex;align-items:center;justify-content:center;margin-bottom:18px}
.flow-icon{width:72px;height:72px;border-radius:50%;background:linear-gradient(135deg,rgba(0,229,160,.12) 0%,rgba(0,229,160,.04) 100%);border:1.5px solid rgba(0,229,160,.35);color:var(--accent);display:flex;align-items:center;justify-content:center;position:relative;z-index:2;transition:transform .4s ease}
.flow-ring{position:absolute;inset:-6px;border-radius:50%;border:1.5px solid rgba(0,229,160,.25);opacity:0}
.flow.in-view .flow-step:nth-child(1) .flow-ring{animation:flowRingPulse 2.6s ease-out infinite}
.flow.in-view .flow-step:nth-child(3) .flow-ring{animation:flowRingPulse 2.6s ease-out infinite .9s}
.flow.in-view .flow-step:nth-child(5) .flow-ring{animation:flowRingPulse 2.6s ease-out infinite 1.8s}
.flow-step:hover .flow-icon{transform:scale(1.06)}
@keyframes flowRingPulse{0%{opacity:.6;transform:scale(1)}100%{opacity:0;transform:scale(1.6)}}
.flow-num{font-size:11px;font-weight:700;color:var(--accent);letter-spacing:1.5px;margin-bottom:6px}
.flow-title{font-size:17px;font-weight:700;color:var(--text);margin-bottom:6px;letter-spacing:-.2px}
.flow-desc{font-size:13.5px;color:var(--text-muted);line-height:1.55;max-width:200px}

/* Animated connector between steps */
.flow-connector{flex:0 0 80px;height:2px;background:linear-gradient(90deg,rgba(0,229,160,.4) 0%,rgba(0,229,160,.15) 100%);margin-top:35px;position:relative;overflow:hidden;border-radius:1px}
.flow-dot{position:absolute;top:50%;left:0;transform:translateY(-50%);width:8px;height:8px;border-radius:50%;background:var(--accent);box-shadow:0 0 12px var(--accent);opacity:0}
.flow.in-view .flow-dot{animation:flowDotMove 2.4s ease-in-out infinite}
.flow.in-view .flow-connector:nth-child(4) .flow-dot{animation-delay:.9s}
@keyframes flowDotMove{0%{left:-8px;opacity:0}15%{opacity:1}85%{opacity:1}100%{left:calc(100% + 8px);opacity:0}}

/* Team value grid */
.team-value{max-width:1080px;margin:0 auto}
.tv-label{text-align:center;margin-bottom:36px}
.team-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:18px}
.team-card{padding:24px 22px;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);transition:transform .3s ease,border-color .3s ease,box-shadow .3s ease}
.team-card:hover{transform:translateY(-3px);border-color:rgba(0,229,160,.35);box-shadow:0 16px 40px -20px rgba(0,229,160,.25)}
.team-icon-bg{width:44px;height:44px;border-radius:10px;background:rgba(0,229,160,.1);color:var(--accent);display:flex;align-items:center;justify-content:center;margin-bottom:14px;transition:background .3s ease}
.team-card:hover .team-icon-bg{background:rgba(0,229,160,.18)}
.team-name{font-size:15px;font-weight:700;color:var(--text);margin-bottom:8px;letter-spacing:-.2px}
.team-desc{font-size:13.5px;color:var(--text-muted);line-height:1.6}

/* Footer pill */
.how-footer{margin-top:56px;display:flex;justify-content:center}
.time-pill{display:inline-flex;align-items:center;gap:10px;background:var(--surface);border:1px solid rgba(0,229,160,.3);border-radius:30px;padding:10px 22px;font-size:13.5px;color:var(--text-dim);box-shadow:0 8px 24px -12px rgba(0,229,160,.2)}
.time-dot{width:8px;height:8px;border-radius:50%;background:var(--accent);box-shadow:0 0 10px var(--accent);animation:livePulse 1.4s ease-in-out infinite}
.time-pill strong{color:var(--accent);font-weight:700;margin:0 4px}
.time-sep{color:var(--text-muted);margin:0 4px}
@keyframes livePulse{0%,100%{opacity:1;transform:scale(1)}50%{opacity:.5;transform:scale(.85)}}

@media(max-width:760px){
  .flow{gap:24px}
  .flow-connector{flex:0 0 100%;height:2px;width:60%;max-width:200px;margin:0 auto;transform:rotate(90deg);transform-origin:center}
  .flow-step{min-width:240px}
}

.features-section{background:var(--surface2)}
.features-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:20px}
.feature-card{padding:24px}
.feature-card:hover{border-color:rgba(0,229,160,.3);transform:translateY(-2px);box-shadow:0 12px 32px -16px rgba(0,229,160,.18)}
.feature-icon{font-size:28px;margin-bottom:12px;color:var(--accent);line-height:0}
.feature-card h3{font-size:15px;font-weight:600;margin-bottom:8px}
.feature-card p{font-size:13px;color:var(--text-muted);line-height:1.6}
.cta-section{background:var(--accent-dim2);border-top:1px solid rgba(0,229,160,.15);border-bottom:1px solid rgba(0,229,160,.15);text-align:center}
.cta-inner{max-width:600px;margin:0 auto}
.cta-section h2{margin-bottom:12px}
.cta-section p{color:var(--text-muted);margin-bottom:32px}
.cta-btn{padding:14px 28px;font-size:16px}
footer{padding:28px 24px;border-top:1px solid var(--border)}
.footer-inner{max-width:1100px;margin:0 auto;display:flex;justify-content:space-between;align-items:center;font-size:13px;color:var(--text-muted);gap:16px;flex-wrap:wrap}
.text-muted{color:var(--text-muted)}
.pricing-section{background:var(--bg);border-top:1px solid var(--border)}
.pricing-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:20px;margin-top:20px;padding-top:4px}
.pricing-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:28px;display:flex;flex-direction:column;position:relative;transition:border-color .25s ease,transform .25s ease,box-shadow .25s ease}
.pricing-card:hover{transform:translateY(-3px);box-shadow:0 18px 40px -20px rgba(0,0,0,.7)}
.pricing-card.recommended{border-color:var(--accent);box-shadow:0 0 0 1px var(--accent),0 0 40px rgba(0,229,160,.06)}
.pricing-card.recommended:hover{box-shadow:0 0 0 1px var(--accent),0 18px 50px -16px rgba(0,229,160,.32)}
@media(max-width:760px){.demo-grid{grid-template-columns:1fr;gap:14px}.demo-arrow{transform:rotate(90deg);padding:6px 0}}
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
"""

// ─── Dashboard App ────────────────────────────────────────────────────────────

private fun HTML.dashboardApp() {
    head {
        title { +"Transloom — Dashboard" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        favicon()
        style { unsafe { +"$SHARED_CSS$DASHBOARD_CSS$ONBOARDING_CSS" } }
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
        script { unsafe { +DASHBOARD_JS } }
        script { unsafe { +ONBOARDING_JS } }
        script { unsafe { +"Onboarding.boot('dashboard');" } }
    }
}

private const val DASHBOARD_CSS = """
.app-layout{display:flex;height:100vh;overflow:hidden}
.sidebar{width:220px;flex-shrink:0;background:var(--surface);border-right:1px solid var(--border);display:flex;flex-direction:column;padding:20px 0}
.sidebar-logo{font-size:16px;font-weight:700;padding:2px 20px 18px;border-bottom:1px solid var(--border);margin-bottom:12px;color:var(--text);gap:10px!important}
.sidebar-logo span{color:var(--text)}
.sidebar-nav{flex:1;display:flex;flex-direction:column;gap:2px;padding:0 10px}
.nav-item{display:flex;align-items:center;justify-content:space-between;padding:9px 12px;border-radius:var(--radius-sm);color:var(--text-muted);font-size:13px;transition:all .12s}
.nav-item:hover{background:var(--surface2);color:var(--text)}
.nav-item.active{background:var(--accent-dim);color:var(--accent);font-weight:500}
.nav-badge{font-size:11px;font-weight:700;border-radius:10px;padding:1px 7px;min-width:20px;text-align:center}
.review-badge{background:var(--accent-dim);color:var(--accent);display:none}
.sidebar-footer{padding:16px 16px 0;border-top:1px solid var(--border);display:flex;flex-direction:column;gap:8px}
.user-chip{font-size:12px;color:var(--text-muted);padding:8px 12px;background:var(--surface2);border-radius:var(--radius-sm);border:1px solid var(--border);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.logout-btn{width:100%;font-size:12px;padding:7px 12px}
.main-content{flex:1;overflow-y:auto;padding:28px 32px}
/* ── Page header ─────────────────────────────────────────────────────────── */
.page-header{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:24px;gap:12px}
.page-title{font-size:22px;font-weight:700;letter-spacing:-.4px;margin-bottom:3px}
.page-sub{font-size:13px;color:var(--text-muted)}
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
.billing-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-bottom:24px}
.plan-card{padding:24px}
.plan-header{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:20px}
.plan-label{font-size:11px;font-weight:700;letter-spacing:1px;color:var(--text-muted);margin-bottom:4px}
.plan-name{font-size:22px;font-weight:700;color:var(--accent)}
.upgrade-btn{padding:8px 16px;font-size:13px}
.usage-section{margin-bottom:16px}
.usage-row{display:flex;justify-content:space-between;font-size:13px;color:var(--text-muted);margin-bottom:8px}
.usage-bar-track{height:6px;background:var(--border);border-radius:3px;overflow:hidden}
.usage-bar-fill{height:100%;background:var(--accent);border-radius:3px;transition:width .4s;width:0%}
.plan-actions a{font-size:13px;color:var(--text-muted)}
.plan-actions a:hover{color:var(--accent)}
.plans-compare{padding:24px}
.plan-tiers{display:flex;flex-direction:column;gap:12px;margin-top:12px}
.plan-tier{display:flex;align-items:center;justify-content:space-between;padding:12px;background:var(--surface2);border-radius:var(--radius-sm);border:1px solid var(--border)}
.plan-tier-info{display:flex;flex-direction:column;gap:2px}
.plan-tier-info strong{font-size:14px}
.plan-price{font-size:13px;color:var(--accent);font-weight:600}
.plan-desc{font-size:12px;color:var(--text-muted)}
.tier-btn{padding:6px 14px;font-size:12px}
.invoice-header{margin-top:8px;margin-bottom:12px}
.invoices-title{font-size:15px;font-weight:600}
.invoice-list{display:flex;flex-direction:column;gap:8px}
.invoice-row{display:grid;grid-template-columns:110px 1fr 90px 80px;gap:12px;align-items:center;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-sm);padding:10px 16px;font-size:13px}
.invoice-status-paid{color:var(--accent);font-size:12px;font-weight:600}
.invoice-status-open{color:var(--yellow);font-size:12px;font-weight:600}
.invoice-pdf{font-size:12px;color:var(--text-muted)}
.invoice-pdf:hover{color:var(--accent)}
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
.step-row{display:flex;align-items:center;gap:12px;padding:7px 0;position:relative}
.step-row:not(:last-child)::after{content:'';position:absolute;left:11px;top:28px;width:1px;height:calc(100% - 10px);background:var(--border);z-index:0}
.step-icon{width:24px;height:24px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:12px;flex-shrink:0;position:relative;z-index:1;transition:all .3s ease}
.step-icon.pending{background:var(--surface2);border:1.5px solid var(--border);color:var(--text-muted)}
.step-icon.running{background:rgba(0,229,160,.12);border:1.5px solid rgba(0,229,160,.4);color:var(--accent)}
.step-icon.done{background:rgba(0,229,160,.15);border:1.5px solid rgba(0,229,160,.5);color:var(--accent)}
.step-icon.error{background:rgba(255,77,79,.12);border:1.5px solid rgba(255,77,79,.4);color:var(--red)}
.step-icon.skipped{background:var(--surface2);border:1.5px solid var(--border);color:var(--text-muted);opacity:.5}
.step-spin{animation:spin .9s linear infinite}
@keyframes spin{from{transform:rotate(0deg)}to{transform:rotate(360deg)}}
.step-body{flex:1;display:flex;align-items:center;justify-content:space-between;min-width:0}
.step-label{font-size:13px;transition:color .2s}
.step-label.running{color:var(--text);font-weight:500}
.step-label.done{color:var(--text-dim)}
.step-label.error{color:var(--red)}
.step-label.skipped{color:var(--text-muted);opacity:.6}
.step-label.pending{color:var(--text-muted)}
.step-detail{font-size:12px;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:240px;text-align:right}
.step-detail.done{color:var(--text-dim)}
.step-detail.error{color:var(--red)}
.run-savings-chip{display:inline-flex;align-items:center;gap:4px;background:rgba(0,229,160,.1);color:var(--accent);border:1px solid rgba(0,229,160,.25);border-radius:20px;padding:2px 9px;font-size:11px;font-weight:600;flex-shrink:0}
.run-no-retranslation{font-size:12px;color:var(--accent);opacity:.8}
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
const urlParams=new URLSearchParams(window.location.search);
const urlToken=urlParams.get('token');
if(urlToken){localStorage.setItem('transloom_token',urlToken);token=urlToken;history.replaceState({},'','/transloom/app');}
if(!token){window.location.href='/transloom/app';throw new Error('no token');}
// Carry the JWT through portal-internal navigation so Safari ITP / private mode /
// fresh tabs that may lose localStorage still arrive authenticated.
document.addEventListener('click',function(e){
  var a=e.target.closest&&e.target.closest('a[href^="/transloom/"]');
  if(!a||a.target==='_blank'||a.hasAttribute('download'))return;
  if(a.getAttribute('href').startsWith('/transloom/auth/'))return;
  try{var u=new URL(a.href,location.origin);if(!u.searchParams.get('token')){u.searchParams.set('token',token);a.href=u.toString();}}catch(_){}
},true);

function authHeaders(){return{'Authorization':'Bearer '+token,'Content-Type':'application/json'};}
async function api(path,opts={}){
  const res=await fetch(BASE+path,{...opts,headers:{...authHeaders(),...(opts.headers||{})}});
  if(res.status===401){logout();return null;}return res;
}
function logout(){localStorage.removeItem('transloom_token');window.location.href='/transloom/auth/logout';}
function toast(msg,type='success'){const el=document.getElementById('toast');el.textContent=msg;el.className='toast show '+type;setTimeout(()=>el.className='toast',2800);}
function jwtPayload(t){try{return JSON.parse(atob(t.split('.')[1]));}catch{return{};}}
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
  const runs=Object.values(runState);
  const active=runs.filter(r=>!r.finishedAt).length;
  const failed=runs.filter(r=>r.finishedAt&&r.error).length;
  const done=runs.filter(r=>r.finishedAt&&!r.error&&r.prUrl).length;
  const badge=document.getElementById('w-run-badge');
  if(badge){
    if(active>0){badge.textContent=active+' running';badge.style.display='inline';}
    else if(failed>0){badge.textContent=failed+' failed';badge.style.background='rgba(255,77,79,.12)';badge.style.color='var(--red)';badge.style.display='inline';}
    else{badge.style.display='none';}
  }
  const list=document.getElementById('w-run-summary');if(!list)return;
  if(!runs.length){list.innerHTML='<div class="w-empty">No runs yet</div>';return;}
  const recent=runs.sort((a,b)=>(b.startedAt||0)-(a.startedAt||0)).slice(0,5);
  list.innerHTML=recent.map(r=>{
    const st=!r.finishedAt?'running':(r.error?'error':'done');
    return '<div class="w-run-row"><span class="mini-dot '+st+'"></span>'
      +'<span class="w-run-repo">'+esc(r.repo)+'</span>'
      +'<span class="w-run-branch">'+esc(r.branch)+'</span></div>';
  }).join('');
}

const payload=jwtPayload(token);
const userEl=document.getElementById('user-chip');if(userEl)userEl.textContent=payload.username?'@'+payload.username:(payload.email||'You');

async function loadProjects(){
  const res=await api('/projects');
  if(!res||!res.ok){toast('Failed to load projects','error');return;}
  const data=await res.json();
  const list=document.getElementById('project-list');
  if(!data.projects||data.projects.length===0){
    list.innerHTML=`<div class="ob-guide">
      <div class="ob-intro">
        <h3>Welcome to Transloom</h3>
        <p>You are on the Free tier &mdash; 500 strings/month, 1 project, 3 languages. Get set up in minutes.</p>
      </div>
      <div class="ob-steps">
        <div class="ob-step ob-done">
          <div class="ob-num">&#10003;</div>
          <div class="ob-body"><strong>Connect GitHub</strong><span>Done &mdash; you are signed in with your GitHub account</span></div>
        </div>
        <div class="ob-step ob-active">
          <div class="ob-num">2</div>
          <div class="ob-body"><strong>Create your first project</strong><span>Point Transloom at your repo and strings file. The GitHub webhook is auto-installed.</span></div>
          <button class="btn btn-primary ob-cta" onclick="openNewProject()">+ Create project</button>
        </div>
        <div class="ob-step ob-pending">
          <div class="ob-num">3</div>
          <div class="ob-body"><strong>Push a new string</strong><span>Add a string to your file and push to GitHub. Transloom detects it and opens a PR with translations in under 60 seconds.</span></div>
        </div>
      </div>
    </div>`;
    return;
  }
  list.innerHTML=data.projects.map(p=>`
    <div class="project-card">
      <div class="project-info">
        <h3>${'$'}{esc(p.name)}</h3>
        <div class="project-meta">
          <span>${'$'}{esc(p.githubRepo)}</span><span>branch: ${'$'}{esc(p.watchBranch)}</span>
          <span>${'$'}{p.targetCount} language${'$'}{p.targetCount!==1?'s':''}</span><span>${'$'}{esc(p.tone)}</span>
        </div>
      </div>
      <button class="btn-delete-project" onclick="confirmDeleteProject('${'$'}{p.id}','${'$'}{esc(p.name)}')" title="Delete project">✕</button>
    </div>`).join('');
  // Populate glossary project selector
  const sel=document.getElementById('glossary-project-select');
  sel.innerHTML='<option value="">— Select a project —</option>'+data.projects.map(p=>`<option value="${'$'}{p.id}">${'$'}{esc(p.name)}</option>`).join('');
}

async function subscribe(plan){
  const res=await api('/billing/subscribe',{method:'POST',body:JSON.stringify({plan})});
  if(!res)return;
  if(!res.ok){const err=await res.json();toast(err.error||'Subscription failed','error');return;}
  const data=await res.json();
  if(!window.Razorpay){toast('Checkout failed to load — refresh and retry','error');return;}
  const rzp=new Razorpay({
    key:data.keyId,
    subscription_id:data.subscriptionId,
    name:'Transloom',
    description:data.plan+' plan · 7-day free trial',
    image:'/transloom/favicon.svg',
    theme:{color:'#00E5A0',backdrop_color:'#000000'},
    handler:function(resp){
      const p=new URLSearchParams({
        razorpay_payment_id:resp.razorpay_payment_id||'',
        razorpay_subscription_id:resp.razorpay_subscription_id||data.subscriptionId,
        razorpay_signature:resp.razorpay_signature||''
      });
      window.location.href='/transloom/billing/rp-callback?'+p.toString();
    },
    modal:{escape:true,backdropclose:false},
    notes:{plan:data.plan}
  });
  rzp.on('payment.failed',function(r){
    toast('Payment failed: '+(r.error&&r.error.description?r.error.description:'Please retry'),'error');
  });
  rzp.open();
}

function openNewProject(){document.getElementById('modal-backdrop').classList.add('open');}
function closeModal(){document.getElementById('modal-backdrop').classList.remove('open');}

// Fix 17: Platform-aware defaults for source/target file paths
document.querySelectorAll('[name="platform"]').forEach(r=>r.addEventListener('change',()=>{
  const plat=document.querySelector('[name="platform"]:checked')?.value||'android';
  const srcInput=document.getElementById('proj-source-path');
  if(plat==='ios'){srcInput.value='en.lproj/Localizable.strings';srcInput.placeholder='en.lproj/Localizable.strings';}
  else{srcInput.value='values/strings.xml';srcInput.placeholder='values/strings.xml';}
}));
// Set Android as default
const _platAndroid=document.getElementById('plat-android');if(_platAndroid)_platAndroid.checked=true;
const _projSource=document.getElementById('proj-source-path');if(_projSource)_projSource.value='values/strings.xml';

async function createProject(){
  const name=document.getElementById('proj-name').value.trim();
  const repo=document.getElementById('proj-repo').value.trim();
  const branch=document.getElementById('proj-branch').value.trim()||'main';
  const category=document.getElementById('proj-category').value;
  const tone=document.getElementById('proj-tone').value;
  const sourcePath=document.getElementById('proj-source-path').value.trim()||'values/strings.xml';
  const platform=document.querySelector('[name="platform"]:checked')?.value||'android';
  const langMap={es:'Spanish',fr:'French',de:'German',ja:'Japanese',ko:'Korean',zh:'Chinese',pt:'Portuguese',it:'Italian',hi:'Hindi',ar:'Arabic'};
  // Fix 17: Platform-specific target file paths
  const fileMap=platform==='ios'
    ?{es:'es.lproj',fr:'fr.lproj',de:'de.lproj',ja:'ja.lproj',ko:'ko.lproj',zh:'zh-Hans.lproj',pt:'pt-BR.lproj',it:'it.lproj',hi:'hi.lproj',ar:'ar.lproj'}
    :{es:'values-es',fr:'values-fr',de:'values-de',ja:'values-ja',ko:'values-ko',zh:'values-zh',pt:'values-pt',it:'values-it',hi:'values-hi',ar:'values-ar'};
  const fileExt=platform==='ios'?'/Localizable.strings':'/strings.xml';
  const selected=[...document.querySelectorAll('[id^="lang-"]:checked')].map(el=>el.value);
  if(!name||!repo){toast('Name and repo are required','error');return;}
  if(selected.length===0){toast('Select at least one target language','error');return;}
  const targets=selected.map(code=>({code,name:langMap[code],region:code.toUpperCase(),file:fileMap[code]+fileExt}));
  const res=await api('/projects',{method:'POST',body:JSON.stringify({name,githubRepo:repo,watchBranch:branch,sourceFilePath:sourcePath,category,tone,targets})});
  if(!res)return;
  if(res.ok){toast('Project created! Now push a new string to trigger your first translation.');closeModal();loadProjects();loadStats();window.Onboarding&&Onboarding.refresh();}
  else{
    const err=await res.json().catch(()=>({}));
    if(res.status===422&&err.code==='GITHUB_REAUTH_REQUIRED'){
      toast('Reconnecting GitHub…','error');
      setTimeout(()=>{window.location.href=err.reauthUrl||'/transloom/auth/github';},900);
      return;
    }
    toast(err.error||'Failed to create project','error');
  }
}

let projectToDelete = null;
function confirmDeleteProject(id, name) {
  projectToDelete = {id, name};
  const modal = document.createElement('div');
  modal.className = 'modal-backdrop open';
  modal.id = 'delete-modal';
  modal.innerHTML = `
    <div class="modal card">
      <div class="modal-header">
        <h3>Delete Project</h3>
        <button class="modal-close" onclick="closeDeleteModal()">✕</button>
      </div>
      <div class="modal-body">
        <p style="font-size:14px;color:var(--text-muted)">Type <strong>${'$'}{esc(name)}</strong> to confirm deletion. This will remove all strings, translations, and glossary entries.</p>
        <input type="text" id="delete-confirm-input" placeholder="${'$'}{esc(name)}" style="margin-top:12px">
      </div>
      <div class="modal-footer">
        <button class="btn btn-ghost" onclick="closeDeleteModal()">Cancel</button>
        <button class="btn btn-primary" style="background:var(--red);color:#fff" onclick="executeDeleteProject()">Delete</button>
      </div>
    </div>
  `;
  document.body.appendChild(modal);
}
function closeDeleteModal() {
  document.getElementById('delete-modal')?.remove();
  projectToDelete = null;
}
async function executeDeleteProject(){
  if (!projectToDelete) return;
  const input = document.getElementById('delete-confirm-input').value;
  if (input !== projectToDelete.name) {
    toast('Project name does not match', 'error');
    return;
  }
  const {id} = projectToDelete;
  closeDeleteModal();
  const res=await api('/projects/'+id,{method:'DELETE'});
  if(!res)return;
  if(res.ok){toast('Project deleted');loadProjects();loadStats();}
  else{const err=await res.json();toast(err.error||'Delete failed','error');}
}

let currentGlossaryProjectId=null;
let glossaryFetchId=0;
async function loadGlossary(projectId){
  const list=document.getElementById('glossary-list');
  const addRow=document.getElementById('glossary-add-row');
  if(!projectId){list.innerHTML='<div class="empty-state">Select a project to view its glossary.</div>';addRow.style.display='none';currentGlossaryProjectId=null;return;}
  currentGlossaryProjectId=projectId;
  const myFetchId=++glossaryFetchId;
  addRow.style.display='flex';
  const res=await api('/glossary/'+projectId);
  if(myFetchId!==glossaryFetchId)return; // U9: prevent race condition if user quickly switches projects
  if(!res||!res.ok){toast('Failed to load glossary','error');return;}
  const data=await res.json();
  if(!data.glossary||data.glossary.length===0){
    list.innerHTML='<div class="empty-state">No glossary entries yet. Add terms above.</div>';return;
  }
  list.innerHTML=data.glossary.map(e=>`
    <div class="glossary-row">
      <span class="gl-lang">${'$'}{esc(e.languageCode)}</span>
      <span class="gl-source">${'$'}{esc(e.sourceTerm)}</span>
      <span class="gl-arrow">→</span>
      <span class="gl-target">${'$'}{esc(e.targetTerm)}</span>
      <button class="btn-gl-delete" onclick="deleteGlossaryEntry('${'$'}{e.id}')" title="Remove">✕</button>
    </div>`).join('');
}
async function addGlossaryEntry(){
  const lang=document.getElementById('gl-lang').value.trim();
  const source=document.getElementById('gl-source').value.trim();
  const target=document.getElementById('gl-target').value.trim();
  if(!lang||!source||!target){toast('All glossary fields are required','error');return;}
  const res=await api('/glossary/'+currentGlossaryProjectId,{method:'POST',body:JSON.stringify({languageCode:lang,sourceTerm:source,targetTerm:target})});
  if(!res)return;
  if(res.ok){toast('Glossary updated');document.getElementById('gl-source').value='';document.getElementById('gl-target').value='';loadGlossary(currentGlossaryProjectId);}
  else{const err=await res.json();toast(err.error||'Failed','error');}
}
async function deleteGlossaryEntry(entryId){
  const res=await api('/glossary/'+currentGlossaryProjectId+'/'+entryId,{method:'DELETE'});
  if(!res)return;
  if(res.ok){toast('Entry removed');loadGlossary(currentGlossaryProjectId);}
  else toast('Failed to delete','error');
}

document.getElementById('user-chip').textContent=jwtPayload(token).username?'@'+jwtPayload(token).username:'Logged in';

// ─── Pipeline Activity ─────────────────────────────────────────────────────

const STEP_ORDER=['WEBHOOK_RECEIVED','FETCHING_STRINGS','DETECTING_CHANGES','BILLING_CHECK','TRANSLATING','CREATING_PR'];
const STEP_ICONS={
  pending:'<svg width="10" height="10" viewBox="0 0 10 10"><circle cx="5" cy="5" r="4" fill="none" stroke="currentColor" stroke-width="1.5"/></svg>',
  running:'<svg class="step-spin" width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M6 1v2M6 9v2M1 6h2M9 6h2M2.5 2.5l1.4 1.4M8.1 8.1l1.4 1.4M2.5 9.5l1.4-1.4M8.1 3.9l1.4-1.4"/></svg>',
  done:'<svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1.5 5 4 7.5 8.5 2.5"/></svg>',
  error:'<svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="1.5" y1="1.5" x2="8.5" y2="8.5"/><line x1="8.5" y1="1.5" x2="1.5" y2="8.5"/></svg>',
  skipped:'<svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="2" y1="5" x2="8" y2="5"/></svg>'
};

// runId -> { repo, branch, commitShort, startedAt, finishedAt, steps:{id->{status,detail}},
//            stepLabels, prUrl, error, projectId, retriedFromRunId, retryPending }
const runState={};

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

// Tick timestamps in all visible run cards every 20s without a full re-render
setInterval(function(){
  document.querySelectorAll('[data-started]').forEach(function(el){
    el.textContent=timeAgo(parseInt(el.dataset.started,10));
  });
},20000);

// ── Card builders ─────────────────────────────────────────────────────────────
function buildStepHtml(id,st,label){
  const s=st||{status:'pending',detail:null};
  const icon=STEP_ICONS[s.status]||STEP_ICONS.pending;
  const detail=s.detail?('<span class="step-detail '+esc(s.status)+'">'+esc(s.detail)+'</span>'):'';
  return '<div class="step-row"><div class="step-icon '+esc(s.status)+'">'+icon+'</div>'
    +'<div class="step-body"><span class="step-label '+esc(s.status)+'">'+esc(label)+'</span>'+detail+'</div></div>';
}

function buildRunHtml(runId){
  const run=runState[runId];if(!run)return '';
  const isActive=!run.finishedAt;
  const hasError=!!run.error;
  const isRetrying=!!run.retryPending;
  const steps=STEP_ORDER.map(id=>buildStepHtml(id,run.steps[id],run.stepLabels[id]||id)).join('');

  // Savings chip — shown whenever the semantic analyzer skipped retranslation
  const skipped=run.surfaceSkipped||0;
  const savingsChip=skipped>0
    ?'<span class="run-savings-chip"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3c-1 3-2 4-5 5 3 1 4 2 5 5 1-3 2-4 5-5-3-1-4-2-5-5z"/><path d="M5.5 10.5c-.5 1.5-1 2-2.5 2.5 1.5.5 2 1 2.5 2.5.5-1.5 1-2 2.5-2.5-1.5-.5-2-1-2.5-2.5z"/></svg>'
      +' '+skipped+' '+(skipped===1?'string':'strings')+' saved</span>'
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
    left=skipped>0
      ?'<span class="run-no-retranslation">Surface rewrites only — retranslation skipped</span>'
      :'<span class="run-duration">No changes found</span>';
  }
  if(run.finishedAt) right='<span class="run-duration">'+runDuration(run.startedAt,run.finishedAt)+'</span>';

  const footParts=[savingsChip,left,right].filter(Boolean);
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
    +footHtml+'</div>';
}

function renderRunCard(runId){
  const html=buildRunHtml(runId);if(!html)return;
  const existing=document.getElementById('rc-'+runId);
  if(existing){existing.outerHTML=html;}
  else{
    document.getElementById('activity-empty')?.remove();
    document.getElementById('run-list').insertAdjacentHTML('afterbegin',html);
  }
}

function applySnapshot(snapshot){
  const run=runState[snapshot.runId]||{steps:{},stepLabels:{}};
  Object.assign(run,{
    repo:snapshot.repo,branch:snapshot.branch,commitShort:snapshot.commitShort,
    startedAt:snapshot.startedAt,finishedAt:snapshot.finishedAt||null,
    prUrl:snapshot.prUrl||null,error:snapshot.error||null,
    projectId:snapshot.projectId||null,retriedFromRunId:snapshot.retriedFromRunId||null,
    surfaceSkipped:snapshot.surfaceSkipped||0
  });
  if(!run.steps)run.steps={};
  if(!run.stepLabels)run.stepLabels={};
  (snapshot.steps||[]).forEach(function(s){
    run.steps[s.id]={status:s.status,detail:s.detail||null};
    run.stepLabels[s.id]=s.label;
  });
  runState[snapshot.runId]=run;
}

function updateActivityBadge(){
  const active=Object.values(runState).filter(function(r){return !r.finishedAt;}).length;
  const badge=document.getElementById('activity-badge');
  if(badge){if(active>0){badge.textContent=active;badge.style.display='inline';}else{badge.style.display='none';}}
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
    // If this is a retry run, mark the parent as retried (clear retryPending)
    if(d.snapshot.retriedFromRunId){
      const parent=runState[d.snapshot.retriedFromRunId];
      if(parent){parent.retryPending=false;renderRunCard(d.snapshot.retriedFromRunId);}
    }
    renderRunCard(d.runId);updateActivityBadge();updateRunSummaryWidget();maybeScrollToActivity();
  }else if(d.type==='step'){
    const run=runState[d.runId];if(!run)return;
    if(!run.steps)run.steps={};
    run.steps[d.stepId]={status:d.status,detail:d.detail||null};
    renderRunCard(d.runId);updateActivityBadge();updateRunSummaryWidget();
  }else if(d.type==='finish'){
    const run=runState[d.runId];if(!run)return;
    run.finishedAt=d.finishedAt||Date.now();
    if(d.prUrl)run.prUrl=d.prUrl;if(d.error)run.error=d.error;
    if(d.surfaceSkipped)run.surfaceSkipped=d.surfaceSkipped;
    run.retryPending=false;
    renderRunCard(d.runId);updateActivityBadge();updateRunSummaryWidget();loadStats();
  }
}

// ── Retrigger a failed run ────────────────────────────────────────────────────
async function retriggerRun(runId){
  const run=runState[runId];
  if(!run||run.retryPending)return;
  run.retryPending=true;
  renderRunCard(runId); // show "Retrying…" button state immediately

  const res=await api('/pipeline/runs/'+encodeURIComponent(runId)+'/retry',{method:'POST'});
  if(!res||!res.ok){
    run.retryPending=false;
    renderRunCard(runId);
    toast('Failed to queue retry — please try again','error');
    return;
  }
  toast('Retry queued — watching for new run…');
  // The SSE "start" event for the new run will arrive shortly and clear retryPending via handlePipelineEvent
}

// ── SSE connection via fetch (Bearer auth — no token in URL) ──────────────────
let sseBackoff=2000;
let sseInstance=null;  // holds {abort: fn} to cancel the current stream
let sseRetries=0;
const SSE_MAX_RETRIES=5;

function setSseStatus(state,text){
  const el=document.getElementById('sse-status');
  const txt=document.getElementById('sse-status-text');
  if(!el)return;
  el.className='sse-status '+state;
  if(txt)txt.textContent=text;
}

function scheduleReconnect(){
  sseInstance=null;
  sseRetries++;
  if(sseRetries>=SSE_MAX_RETRIES){setSseStatus('disconnected','Live updates unavailable');return;}
  setSseStatus('reconnecting','Reconnecting…');
  setTimeout(connectPipelineSSE,sseBackoff);
  sseBackoff=Math.min(sseBackoff*2,30000);
}

function connectPipelineSSE(){
  if(!token)return;
  if(sseInstance){sseInstance.abort();sseInstance=null;}
  const controller=new AbortController();
  sseInstance={abort:function(){controller.abort();}};
  fetch(BASE+'/pipeline/events',{headers:{'Authorization':'Bearer '+token},signal:controller.signal})
    .then(function(res){
      if(!res.ok){throw new Error('HTTP '+res.status);}
      sseBackoff=2000;sseRetries=0;setSseStatus('idle','');
      const reader=res.body.getReader();
      const dec=new TextDecoder();
      let buf='';
      function read(){
        reader.read().then(function(r){
          if(r.done){scheduleReconnect();return;}
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

async function loadPipelineRuns(){
  const res=await api('/pipeline/runs');if(!res||!res.ok)return;
  const data=await res.json();
  const runs=(data.runs||[]).slice(0,10);if(!runs.length)return;
  document.getElementById('activity-empty')?.remove();
  runs.forEach(function(s){applySnapshot(s);renderRunCard(s.runId);});
  updateActivityBadge();updateRunSummaryWidget();
}

loadStats();loadPlanWidget();loadPipelineRuns();connectPipelineSSE();
""".trimIndent()

// ─── Projects Page ────────────────────────────────────────────────────────────

private const val PROJECTS_CSS = """
.projects-page{padding:28px 32px;flex:1;overflow-y:auto}
.proj-page-header{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:8px;gap:16px;flex-wrap:wrap}
.proj-page-header h1{font-size:22px;font-weight:700;letter-spacing:-.4px;margin-bottom:3px}
.proj-page-header p{font-size:13px;color:var(--text-muted)}
.limit-indicator{display:inline-flex;align-items:center;gap:8px;background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);padding:6px 12px;font-size:12px;color:var(--text-muted);margin-bottom:20px}
.limit-indicator .li-used{font-weight:700;color:var(--text)}
.limit-indicator .li-sep{color:var(--border)}
.limit-banner{display:none;padding:12px 16px;border-radius:var(--radius);border:1px solid rgba(250,173,20,.3);background:rgba(250,173,20,.07);color:var(--yellow);font-size:13px;line-height:1.5;margin-bottom:20px}
.limit-banner.visible{display:block}
.limit-banner a{color:var(--yellow);text-decoration:underline;font-weight:600}
.proj-toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;gap:12px}
/* ── Project cards ──────────────────────────────────────────────────────────── */
.project-grid{display:flex;flex-direction:column;gap:14px;margin-bottom:40px}
.pc-pro{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden;transition:border-color .2s,box-shadow .2s}
.pc-pro:hover{border-color:rgba(0,229,160,.25);box-shadow:0 4px 24px -8px rgba(0,0,0,.4)}
.pc-header{display:flex;align-items:flex-start;justify-content:space-between;padding:18px 20px 14px;gap:12px}
.pc-info{flex:1;min-width:0}
.pc-name{font-size:15px;font-weight:700;color:var(--text);margin-bottom:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.pc-repo{font-size:12px;color:var(--text-muted);font-family:ui-monospace,monospace}
.pc-actions{display:flex;gap:8px;flex-shrink:0}
.pc-btn{background:var(--surface2);border:1px solid var(--border);color:var(--text-dim);font-size:12px;font-weight:500;padding:5px 12px;border-radius:var(--radius-sm);cursor:pointer;transition:all .15s}
.pc-btn:hover{border-color:var(--accent);color:var(--accent)}
.pc-btn.del:hover{border-color:var(--red);color:var(--red);background:rgba(255,77,79,.07)}
.pc-divider{height:1px;background:var(--border);margin:0 20px}
.pc-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;padding:12px 20px}
.pc-tag{display:inline-flex;align-items:center;gap:4px;font-size:11px;font-weight:600;letter-spacing:.3px;padding:3px 9px;border-radius:20px;background:var(--surface2);border:1px solid var(--border);color:var(--text-muted)}
.pc-tag.branch{background:var(--accent-dim);border-color:rgba(0,229,160,.25);color:var(--accent)}
.pc-tag.plat{color:var(--text-dim)}
.pc-tag.langs{color:var(--text-dim)}
.pc-source{padding:0 20px 14px;font-size:11px;font-family:ui-monospace,monospace;color:var(--text-muted)}
/* ── Edit modal ─────────────────────────────────────────────────────────────── */
.edit-modal-title{font-size:16px;font-weight:700;letter-spacing:-.2px}
.edit-modal-repo{font-size:12px;color:var(--text-muted);font-family:monospace;margin-top:2px}
.feature-toggle-row{border:1px solid var(--border);border-radius:var(--radius-sm);padding:14px;background:var(--surface2)}
.feature-toggle-header{display:flex;align-items:flex-start;gap:12px}
.feature-toggle-label{position:relative;display:inline-flex;align-items:center;flex-shrink:0;cursor:pointer;margin-top:2px}
.feature-toggle-label input{opacity:0;width:0;height:0;position:absolute}
.feature-toggle-track{display:inline-block;width:36px;height:20px;background:var(--border);border-radius:10px;transition:background .2s;flex-shrink:0}
.feature-toggle-track::after{content:'';position:absolute;top:3px;left:3px;width:14px;height:14px;background:#fff;border-radius:50%;transition:transform .2s}
.feature-toggle-label input:checked+.feature-toggle-track{background:var(--accent)}
.feature-toggle-label input:checked+.feature-toggle-track::after{transform:translateX(16px)}
.feature-toggle-name{font-size:13px;font-weight:600;margin-bottom:3px}
.feature-toggle-hint{font-size:12px;color:var(--text-muted);line-height:1.5}
/* ── Glossary section ───────────────────────────────────────────────────────── */
.glossary-section{margin-top:8px}
.glossary-controls{margin-bottom:12px}
.glossary-controls select{max-width:300px}
.glossary-add-row{display:flex;gap:8px;margin-bottom:16px;align-items:flex-end}
.glossary-add-row input{max-width:180px}
.glossary-list{display:flex;flex-direction:column;gap:8px}
.glossary-row{display:flex;align-items:center;gap:12px;padding:10px 16px;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-sm);font-size:13px}
.gl-lang{font-weight:700;color:var(--accent);min-width:32px;text-transform:uppercase;font-size:12px}
.gl-source{color:var(--text)}
.gl-arrow{color:var(--text-muted);font-size:11px}
.gl-target{color:var(--accent);flex:1}
.btn-gl-delete{background:none;border:none;color:var(--text-muted);font-size:13px;cursor:pointer;padding:4px 8px;border-radius:var(--radius-sm);transition:all .15s;margin-left:auto}
.btn-gl-delete:hover{color:var(--red);background:rgba(255,77,79,.1)}
"""

private val PROJECTS_JS = """
const BASE='/transloom/api';
let token=localStorage.getItem('transloom_token');
const urlParams=new URLSearchParams(location.search);
const urlToken=urlParams.get('token');
if(urlToken){localStorage.setItem('transloom_token',urlToken);token=urlToken;urlParams.delete('token');const qs=urlParams.toString();history.replaceState({},'','/transloom/projects'+(qs?'?'+qs:'')+location.hash);}
if(!token){window.location.href='/transloom/app';throw new Error('no token');}
document.addEventListener('click',function(e){
  var a=e.target.closest&&e.target.closest('a[href^="/transloom/"]');
  if(!a||a.target==='_blank'||a.hasAttribute('download'))return;
  if(a.getAttribute('href').startsWith('/transloom/auth/'))return;
  try{var u=new URL(a.href,location.origin);if(!u.searchParams.get('token')){u.searchParams.set('token',token);a.href=u.toString();}}catch(_){}
},true);
function authHeaders(){return{'Authorization':'Bearer '+token,'Content-Type':'application/json'};}
async function api(path,opts={}){
  const res=await fetch(BASE+path,{...opts,headers:{...authHeaders(),...(opts.headers||{})}});
  if(res.status===401){logout();return null;}return res;
}
function logout(){localStorage.removeItem('transloom_token');window.location.href='/transloom/auth/logout';}
function toast(msg,type='success'){const el=document.getElementById('toast');el.textContent=msg;el.className='toast show '+type;setTimeout(()=>el.className='toast',2800);}
function esc(s){if(!s)return '';const d=document.createElement('div');d.textContent=String(s);return d.innerHTML;}
function jwtPayload(t){try{return JSON.parse(atob(t.split('.')[1]));}catch{return{};}}

const LANG_MAP={es:'Spanish',fr:'French',de:'German',ja:'Japanese',ko:'Korean',zh:'Chinese',pt:'Portuguese',it:'Italian',hi:'Hindi',ar:'Arabic'};
const FILE_MAP_ANDROID={es:'values-es',fr:'values-fr',de:'values-de',ja:'values-ja',ko:'values-ko',zh:'values-zh',pt:'values-pt',it:'values-it',hi:'values-hi',ar:'values-ar'};
const FILE_MAP_IOS={es:'es.lproj',fr:'fr.lproj',de:'de.lproj',ja:'ja.lproj',ko:'ko.lproj',zh:'zh-Hans.lproj',pt:'pt-BR.lproj',it:'it.lproj',hi:'hi.lproj',ar:'ar.lproj'};

let currentSub=null;
let currentProjects=[];
let currentGlossaryProjectId=null;
let glossaryFetchId=0;

async function init(){
  const [projRes,subRes]=await Promise.all([api('/projects'),api('/billing/subscription')]);
  if(!projRes||!subRes)return;
  const projData=await projRes.json();
  currentSub=await subRes.json();
  currentProjects=projData.projects||[];
  renderPlanLimitUI();
  renderProjects();
  populateGlossarySelector();
  const payload=jwtPayload(token);
  const chip=document.getElementById('user-chip');
  if(chip)chip.textContent=payload.username?'@'+payload.username:(payload.email||'You');
}

function renderPlanLimitUI(){
  const used=currentProjects.length;
  const max=currentSub?.maxProjects>0?currentSub.maxProjects:null;
  const plan=currentSub?.displayName||currentSub?.plan||'Free';
  const liUsed=document.getElementById('li-used');
  const liMax=document.getElementById('li-max');
  const liPlan=document.getElementById('li-plan');
  if(liUsed)liUsed.textContent=used;
  if(liMax)liMax.textContent=max?(' / '+max):'';
  if(liPlan)liPlan.textContent=plan+' plan';
  const btn=document.getElementById('new-proj-btn');
  const banner=document.getElementById('limit-banner');
  const bannerText=document.getElementById('limit-banner-msg');
  const atLimit=max&&used>=max;
  if(btn){btn.disabled=!!atLimit;btn.title=atLimit?'Project limit reached — upgrade your plan to add more':'';}
  if(banner)banner.className='limit-banner'+(atLimit?' visible':'');
  if(bannerText&&atLimit){
    if(currentSub?.plan==='FREE')bannerText.innerHTML='Free plan allows 1 project. <a href="/transloom/billing">Upgrade to Solo (3 projects) or Team (10 projects)</a>.';
    else if(currentSub?.plan==='SOLO')bannerText.innerHTML='Solo plan allows 3 projects. <a href="/transloom/billing">Upgrade to Team for up to 10 projects</a>.';
    else bannerText.innerHTML='Project limit reached. <a href="/transloom/billing">View your plan</a>.';
  }
}

function renderProjects(){
  const list=document.getElementById('project-list');if(!list)return;
  if(!currentProjects.length){
    list.innerHTML=`<div class="ob-guide">
      <div class="ob-intro"><h3>No projects yet</h3><p>Create your first project to start automating translations.</p></div>
      <div class="ob-steps">
        <div class="ob-step ob-done"><div class="ob-num">&#10003;</div><div class="ob-body"><strong>Connect GitHub</strong><span>Done — signed in with GitHub</span></div></div>
        <div class="ob-step ob-active"><div class="ob-num">2</div><div class="ob-body"><strong>Create your first project</strong><span>Point Transloom at your repo and strings file. Webhook is auto-installed.</span></div>
          <button class="btn btn-primary ob-cta" onclick="openNewProject()">+ Create project</button></div>
        <div class="ob-step ob-pending"><div class="ob-num">3</div><div class="ob-body"><strong>Push a new string</strong><span>Transloom detects it and opens a PR in under 60 seconds.</span></div></div>
      </div></div>`;
    return;
  }
  list.innerHTML=currentProjects.map(p=>buildProjectCard(p)).join('');
}

function buildProjectCard(p){
  const isIos=p.sourceFilePath&&p.sourceFilePath.includes('.strings');
  const platform=isIos?'iOS':'Android';
  return `<div class="pc-pro" id="pc-${'$'}{p.id}">
    <div class="pc-header">
      <div class="pc-info">
        <div class="pc-name">${'$'}{esc(p.name)}</div>
        <div class="pc-repo">${'$'}{esc(p.githubRepo)}</div>
      </div>
      <div class="pc-actions">
        <button class="pc-btn" onclick="openEditModal('${'$'}{p.id}')">&#9998; Edit</button>
        <button class="pc-btn del" onclick="confirmDeleteProject('${'$'}{p.id}','${'$'}{esc(p.name)}')">&#10005; Delete</button>
      </div>
    </div>
    <div class="pc-divider"></div>
    <div class="pc-meta">
      <span class="pc-tag branch">&#x2387; ${'$'}{esc(p.watchBranch)}</span>
      <span class="pc-tag plat">${'$'}{platform}</span>
      <span class="pc-tag">${'$'}{esc(p.category)}</span>
      <span class="pc-tag">${'$'}{esc(p.tone)}</span>
      <span class="pc-tag langs">${'$'}{p.targetCount} language${'$'}{p.targetCount!==1?'s':''}</span>
    </div>
    <div class="pc-source">${'$'}{esc(p.sourceFilePath)}</div>
  </div>`;
}

// ── New project modal ──────────────────────────────────────────────────────────
function openNewProject(){
  const max=currentSub?.maxProjects>0?currentSub.maxProjects:null;
  if(max&&currentProjects.length>=max){
    toast('Project limit reached — upgrade your plan','error');return;
  }
  document.getElementById('new-modal').classList.add('open');
}
function closeNewModal(){document.getElementById('new-modal').classList.remove('open');}

document.querySelectorAll('[name="platform"]').forEach(r=>r.addEventListener('change',()=>{
  const plat=document.querySelector('[name="platform"]:checked')?.value||'android';
  const src=document.getElementById('proj-source-path');
  if(plat==='ios'){src.value='en.lproj/Localizable.strings';}else{src.value='values/strings.xml';}
}));
const _pa=document.getElementById('plat-android');if(_pa)_pa.checked=true;
const _ps=document.getElementById('proj-source-path');if(_ps)_ps.value='values/strings.xml';

async function createProject(){
  const name=document.getElementById('proj-name').value.trim();
  const repo=document.getElementById('proj-repo').value.trim();
  const branch=document.getElementById('proj-branch').value.trim()||'main';
  const category=document.getElementById('proj-category').value;
  const tone=document.getElementById('proj-tone').value;
  const sourcePath=document.getElementById('proj-source-path').value.trim()||'values/strings.xml';
  const platform=document.querySelector('[name="platform"]:checked')?.value||'android';
  const fileMap=platform==='ios'?FILE_MAP_IOS:FILE_MAP_ANDROID;
  const fileExt=platform==='ios'?'/Localizable.strings':'/strings.xml';
  const selected=[...document.querySelectorAll('[id^="lang-"]:checked')].map(el=>el.value);
  if(!name||!repo){toast('Name and GitHub repo are required','error');return;}
  if(selected.length===0){toast('Select at least one target language','error');return;}
  const targets=selected.map(code=>({code,name:LANG_MAP[code],region:code.toUpperCase(),file:fileMap[code]+fileExt}));
  const res=await api('/projects',{method:'POST',body:JSON.stringify({name,githubRepo:repo,watchBranch:branch,sourceFilePath:sourcePath,category,tone,targets})});
  if(!res)return;
  if(res.ok){toast('Project created! Webhook auto-installed.');closeNewModal();await init();window.Onboarding&&Onboarding.refresh();}
  else{
    const err=await res.json().catch(()=>({}));
    if(res.status===422&&err.code==='GITHUB_REAUTH_REQUIRED'){
      toast('Reconnecting GitHub…','error');
      setTimeout(()=>{window.location.href=err.reauthUrl||'/transloom/auth/github';},900);
      return;
    }
    toast(err.error||'Failed to create project','error');
  }
}

// ── Edit project modal ─────────────────────────────────────────────────────────
async function openEditModal(projectId){
  const res=await api('/projects/'+projectId);
  if(!res||!res.ok){toast('Failed to load project','error');return;}
  const p=await res.json();
  document.getElementById('edit-proj-id').value=p.id;
  document.getElementById('edit-proj-name').value=p.name;
  document.getElementById('edit-proj-branch').value=p.watchBranch;
  document.getElementById('edit-proj-source').value=p.sourceFilePath;
  document.getElementById('edit-proj-category').value=p.category;
  document.getElementById('edit-proj-tone').value=p.tone;
  document.getElementById('edit-modal-repo').textContent=p.githubRepo;
  document.getElementById('edit-cultural-enabled').checked=!!p.culturalSensitivityEnabled;
  const isIos=p.sourceFilePath&&p.sourceFilePath.includes('.strings');
  const platRadio=document.getElementById(isIos?'edit-plat-ios':'edit-plat-android');
  if(platRadio)platRadio.checked=true;
  const selectedLangs=new Set((p.targets||[]).map(t=>t.code));
  document.querySelectorAll('[id^="edit-lang-"]').forEach(cb=>{cb.checked=selectedLangs.has(cb.value);});
  document.getElementById('edit-modal').classList.add('open');
}
function closeEditModal(){document.getElementById('edit-modal').classList.remove('open');}

document.querySelectorAll('[name="edit-platform"]').forEach(r=>r.addEventListener('change',()=>{
  const plat=document.querySelector('[name="edit-platform"]:checked')?.value||'android';
  const src=document.getElementById('edit-proj-source');
  if(plat==='ios'&&src.value==='values/strings.xml')src.value='en.lproj/Localizable.strings';
  else if(plat==='android'&&src.value==='en.lproj/Localizable.strings')src.value='values/strings.xml';
}));

async function saveEdit(){
  const projectId=document.getElementById('edit-proj-id').value;
  const name=document.getElementById('edit-proj-name').value.trim();
  const watchBranch=document.getElementById('edit-proj-branch').value.trim()||'main';
  const sourceFilePath=document.getElementById('edit-proj-source').value.trim();
  const category=document.getElementById('edit-proj-category').value;
  const tone=document.getElementById('edit-proj-tone').value;
  const platform=document.querySelector('[name="edit-platform"]:checked')?.value||'android';
  const fileMap=platform==='ios'?FILE_MAP_IOS:FILE_MAP_ANDROID;
  const fileExt=platform==='ios'?'/Localizable.strings':'/strings.xml';
  const selected=[...document.querySelectorAll('[id^="edit-lang-"]:checked')].map(el=>el.value);
  if(!name){toast('Name is required','error');return;}
  if(!selected.length){toast('Select at least one language','error');return;}
  const targets=selected.map(code=>({code,name:LANG_MAP[code],region:code.toUpperCase(),file:fileMap[code]+fileExt}));
  const culturalSensitivityEnabled=document.getElementById('edit-cultural-enabled')?.checked||false;
  const res=await api('/projects/'+projectId,{method:'PUT',body:JSON.stringify({name,watchBranch,sourceFilePath,category,tone,targets,culturalSensitivityEnabled})});
  if(!res)return;
  if(res.ok){toast('Project updated');closeEditModal();await init();}
  else{const err=await res.json();toast(err.error||'Update failed','error');}
}

// ── Delete project ─────────────────────────────────────────────────────────────
let projectToDelete=null;
function confirmDeleteProject(id,name){
  projectToDelete={id,name};
  const modal=document.createElement('div');
  modal.className='modal-backdrop open';modal.id='delete-modal';
  modal.innerHTML=`<div class="modal card">
    <div class="modal-header"><h3>Delete Project</h3><button class="modal-close" onclick="closeDeleteModal()">&#10005;</button></div>
    <div class="modal-body">
      <p style="font-size:14px;color:var(--text-muted)">Type <strong>${'$'}{esc(name)}</strong> to confirm. All strings, translations, and glossary entries will be removed permanently.</p>
      <input type="text" id="delete-confirm-input" placeholder="${'$'}{esc(name)}" style="margin-top:12px">
    </div>
    <div class="modal-footer">
      <button class="btn btn-ghost" onclick="closeDeleteModal()">Cancel</button>
      <button class="btn btn-primary" style="background:var(--red);color:#fff" onclick="executeDeleteProject()">Delete</button>
    </div>
  </div>`;
  document.body.appendChild(modal);
}
function closeDeleteModal(){document.getElementById('delete-modal')?.remove();projectToDelete=null;}
async function executeDeleteProject(){
  if(!projectToDelete)return;
  if(document.getElementById('delete-confirm-input').value!==projectToDelete.name){toast('Name does not match','error');return;}
  const {id}=projectToDelete;
  closeDeleteModal();
  const res=await api('/projects/'+id,{method:'DELETE'});
  if(!res)return;
  if(res.ok){toast('Project deleted');await init();}
  else{const err=await res.json();toast(err.error||'Delete failed','error');}
}

// ── Glossary ───────────────────────────────────────────────────────────────────
function populateGlossarySelector(){
  const sel=document.getElementById('glossary-project-select');if(!sel)return;
  sel.innerHTML='<option value="">— Select a project —</option>'+
    currentProjects.map(p=>`<option value="${'$'}{p.id}">${'$'}{esc(p.name)}</option>`).join('');
  if(currentGlossaryProjectId)sel.value=currentGlossaryProjectId;
}
async function loadGlossary(projectId){
  const list=document.getElementById('glossary-list');
  const addRow=document.getElementById('glossary-add-row');
  if(!projectId){if(list)list.innerHTML='<div class="empty-state">Select a project to view its glossary.</div>';if(addRow)addRow.style.display='none';currentGlossaryProjectId=null;return;}
  currentGlossaryProjectId=projectId;
  const myId=++glossaryFetchId;
  if(addRow)addRow.style.display='flex';
  const res=await api('/glossary/'+projectId);
  if(myId!==glossaryFetchId)return;
  if(!res||!res.ok){toast('Failed to load glossary','error');return;}
  const data=await res.json();
  if(!list)return;
  if(!data.glossary||!data.glossary.length){list.innerHTML='<div class="empty-state">No glossary entries yet.</div>';return;}
  list.innerHTML=data.glossary.map(e=>`
    <div class="glossary-row">
      <span class="gl-lang">${'$'}{esc(e.languageCode)}</span>
      <span class="gl-source">${'$'}{esc(e.sourceTerm)}</span>
      <span class="gl-arrow">&#8594;</span>
      <span class="gl-target">${'$'}{esc(e.targetTerm)}</span>
      <button class="btn-gl-delete" onclick="deleteGlossaryEntry('${'$'}{e.id}')" title="Remove">&#10005;</button>
    </div>`).join('');
}
async function addGlossaryEntry(){
  const lang=document.getElementById('gl-lang').value.trim();
  const source=document.getElementById('gl-source').value.trim();
  const target=document.getElementById('gl-target').value.trim();
  if(!lang||!source||!target){toast('All glossary fields are required','error');return;}
  const res=await api('/glossary/'+currentGlossaryProjectId,{method:'POST',body:JSON.stringify({languageCode:lang,sourceTerm:source,targetTerm:target})});
  if(!res)return;
  if(res.ok){toast('Glossary updated');document.getElementById('gl-source').value='';document.getElementById('gl-target').value='';loadGlossary(currentGlossaryProjectId);}
  else{const err=await res.json();toast(err.error||'Failed','error');}
}
async function deleteGlossaryEntry(entryId){
  const res=await api('/glossary/'+currentGlossaryProjectId+'/'+entryId,{method:'DELETE'});
  if(!res)return;
  if(res.ok){toast('Entry removed');loadGlossary(currentGlossaryProjectId);}
  else toast('Failed to delete','error');
}

// Handle #glossary anchor from external links
if(window.location.hash==='#glossary'){
  setTimeout(()=>document.getElementById('glossary-section')?.scrollIntoView({behavior:'smooth'}),400);
}

init();
""".trimIndent()

private fun HTML.projectsApp() {
    head {
        title { +"Transloom — Projects" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        favicon()
        style { unsafe { +"$SHARED_CSS$DASHBOARD_CSS$PROJECTS_CSS$ONBOARDING_CSS" } }
    }
    body {
        div("app-layout") {
            unsafe { +APP_SIDEBAR_PROJECTS }
            main("projects-page") {
                div("proj-page-header") {
                    div {
                        h1 { +"Projects" }
                        p { +"Manage your repositories, branches, and translation settings." }
                    }
                    button(classes = "btn btn-primary") {
                        id = "new-proj-btn"
                        attributes["onclick"] = "openNewProject()"
                        +"+ New project"
                    }
                }
                div("limit-indicator") {
                    span("li-used") { id = "li-used"; +"—" }
                    span("li-max") { id = "li-max" }
                    span("li-sep") { +" · " }
                    span { id = "li-plan"; +"Loading…" }
                }
                div("limit-banner") {
                    id = "limit-banner"
                    span { id = "limit-banner-msg" }
                }
                div("project-grid") { id = "project-list"; div("empty-state") { +"Loading…" } }

                div("content-section glossary-section") {
                    id = "glossary-section"
                    div("section-header") {
                        h2 { +"Glossary" }
                    }
                    div("glossary-controls") {
                        select {
                            id = "glossary-project-select"
                            attributes["onchange"] = "loadGlossary(this.value)"
                            option { value = ""; +"— Select a project —" }
                        }
                    }
                    div("glossary-add-row") {
                        id = "glossary-add-row"
                        style = "display:none"
                        input { type = InputType.text; id = "gl-lang"; placeholder = "Language code (e.g. es)" }
                        input { type = InputType.text; id = "gl-source"; placeholder = "Source term" }
                        input { type = InputType.text; id = "gl-target"; placeholder = "Translation" }
                        button(classes = "btn btn-primary") { attributes["onclick"] = "addGlossaryEntry()"; +"Add" }
                    }
                    div("glossary-list") { id = "glossary-list"; div("empty-state") { +"Select a project to view its glossary." } }
                }
            }
        }

        // ── New project modal ─────────────────────────────────────────────────
        div("modal-backdrop") {
            id = "new-modal"
            div("modal card") {
                div("modal-header") {
                    h3 { +"New Project" }
                    button(classes = "modal-close") { attributes["onclick"] = "closeNewModal()"; +"✕" }
                }
                div("modal-body") {
                    p { style = "font-size:13px;color:var(--text-muted);padding:10px 12px;background:var(--surface2);border-radius:6px;border:1px solid var(--border);line-height:1.5"
                        +"Transloom installs a GitHub webhook automatically. On every push, new strings are detected, translated, and a PR is opened."
                    }
                    div("form-row") { label { +"Project name" }; input { type = InputType.text; id = "proj-name"; placeholder = "My App" } }
                    div("form-row") { label { +"GitHub repo (owner/repo)" }; input { type = InputType.text; id = "proj-repo"; placeholder = "acme/my-app" } }
                    div("form-row") {
                        label { +"Platform" }
                        div("plat-toggle") {
                            label("plat-opt") { input { type = InputType.radio; name = "platform"; id = "plat-android"; value = "android" }; span("plat-icon") { unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M5 16V8a7 7 0 0 1 14 0v8"/><line x1="2" y1="20" x2="22" y2="20"/></svg>""" } }; +"Android" }
                            label("plat-opt") { input { type = InputType.radio; name = "platform"; id = "plat-ios"; value = "ios" }; span("plat-icon") { unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20.94c1.5 0 2.75 1.06 4 1.06 3 0 6-8 6-12.22A4.91 4.91 0 0 0 17 5c-2.22 0-4 1.44-5 2-1-.56-2.78-2-5-2a4.9 4.9 0 0 0-5 4.78C2 14 5 22 8 22c1.25 0 2.5-1.06 4-1.06Z"/></svg>""" } }; +"iOS" }
                        }
                    }
                    div("form-row") { label { +"Source strings file" }; input { type = InputType.text; id = "proj-source-path"; placeholder = "values/strings.xml" }; p("field-hint") { +"Android: values/strings.xml · iOS: en.lproj/Localizable.strings" } }
                    div("form-row two-col") {
                        div { label { +"Watch branch" }; input { type = InputType.text; id = "proj-branch"; placeholder = "main"; value = "main" } }
                        div { label { +"Category" }; select { id = "proj-category"; option { value = "productivity"; +"Productivity" }; option { value = "gaming"; +"Gaming" }; option { value = "fintech"; +"Fintech" }; option { value = "social"; +"Social" }; option { value = "health"; +"Health" }; option { value = "ecommerce"; +"E-commerce" } } }
                    }
                    div("form-row") { label { +"Tone" }; select { id = "proj-tone"; option { value = "professional"; +"Professional" }; option { value = "friendly"; +"Friendly" }; option { value = "casual"; +"Casual" }; option { value = "formal"; +"Formal" } } }
                    div("form-row") {
                        label { +"Target languages" }
                        div("lang-picker") {
                            mapOf("es" to "🇪🇸 Spanish","fr" to "🇫🇷 French","de" to "🇩🇪 German","ja" to "🇯🇵 Japanese","ko" to "🇰🇷 Korean","zh" to "🇨🇳 Chinese","pt" to "🇧🇷 Portuguese","it" to "🇮🇹 Italian","hi" to "🇮🇳 Hindi","ar" to "🇸🇦 Arabic").forEach { (code, lbl) ->
                                label("lang-toggle") { input { type = InputType.checkBox; id = "lang-$code"; value = code }; +lbl }
                            }
                        }
                    }
                }
                div("modal-footer") {
                    button(classes = "btn btn-ghost") { attributes["onclick"] = "closeNewModal()"; +"Cancel" }
                    button(classes = "btn btn-primary") { attributes["onclick"] = "createProject()"; +"Create project" }
                }
            }
        }

        // ── Edit project modal ────────────────────────────────────────────────
        div("modal-backdrop") {
            id = "edit-modal"
            div("modal card") {
                div("modal-header") {
                    div {
                        p("edit-modal-title") { +"Edit Project" }
                        p("edit-modal-repo") { id = "edit-modal-repo" }
                    }
                    button(classes = "modal-close") { attributes["onclick"] = "closeEditModal()"; +"✕" }
                }
                div("modal-body") {
                    input { type = InputType.hidden; id = "edit-proj-id" }
                    div("form-row") { label { +"Project name" }; input { type = InputType.text; id = "edit-proj-name" } }
                    div("form-row") {
                        label { +"Platform" }
                        div("plat-toggle") {
                            label("plat-opt") { input { type = InputType.radio; name = "edit-platform"; id = "edit-plat-android"; value = "android" }; span("plat-icon") { unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M5 16V8a7 7 0 0 1 14 0v8"/><line x1="2" y1="20" x2="22" y2="20"/></svg>""" } }; +"Android" }
                            label("plat-opt") { input { type = InputType.radio; name = "edit-platform"; id = "edit-plat-ios"; value = "ios" }; span("plat-icon") { unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20.94c1.5 0 2.75 1.06 4 1.06 3 0 6-8 6-12.22A4.91 4.91 0 0 0 17 5c-2.22 0-4 1.44-5 2-1-.56-2.78-2-5-2a4.9 4.9 0 0 0-5 4.78C2 14 5 22 8 22c1.25 0 2.5-1.06 4-1.06Z"/></svg>""" } }; +"iOS" }
                        }
                    }
                    div("form-row") { label { +"Source strings file" }; input { type = InputType.text; id = "edit-proj-source" }; p("field-hint") { +"Android: values/strings.xml · iOS: en.lproj/Localizable.strings" } }
                    div("form-row two-col") {
                        div { label { +"Watch branch" }; input { type = InputType.text; id = "edit-proj-branch" } }
                        div { label { +"Category" }; select { id = "edit-proj-category"; option { value = "productivity"; +"Productivity" }; option { value = "gaming"; +"Gaming" }; option { value = "fintech"; +"Fintech" }; option { value = "social"; +"Social" }; option { value = "health"; +"Health" }; option { value = "ecommerce"; +"E-commerce" } } }
                    }
                    div("form-row") { label { +"Tone" }; select { id = "edit-proj-tone"; option { value = "professional"; +"Professional" }; option { value = "friendly"; +"Friendly" }; option { value = "casual"; +"Casual" }; option { value = "formal"; +"Formal" } } }
                    div("form-row feature-toggle-row") {
                        div("feature-toggle-header") {
                            label("feature-toggle-label") {
                                input { type = InputType.checkBox; id = "edit-cultural-enabled" }
                                span("feature-toggle-track") {}
                            }
                            div {
                                p("feature-toggle-name") { +"Cultural sensitivity check" }
                                p("feature-toggle-hint") { +"Post-translation AI pass flags formality mismatches, market-inappropriate idioms, and communication style issues. Off by default." }
                            }
                        }
                    }
                    div("form-row") {
                        label { +"Target languages" }
                        div("lang-picker") {
                            mapOf("es" to "🇪🇸 Spanish","fr" to "🇫🇷 French","de" to "🇩🇪 German","ja" to "🇯🇵 Japanese","ko" to "🇰🇷 Korean","zh" to "🇨🇳 Chinese","pt" to "🇧🇷 Portuguese","it" to "🇮🇹 Italian","hi" to "🇮🇳 Hindi","ar" to "🇸🇦 Arabic").forEach { (code, lbl) ->
                                label("lang-toggle") { input { type = InputType.checkBox; id = "edit-lang-$code"; value = code }; +lbl }
                            }
                        }
                    }
                }
                div("modal-footer") {
                    button(classes = "btn btn-ghost") { attributes["onclick"] = "closeEditModal()"; +"Cancel" }
                    button(classes = "btn btn-primary") { attributes["onclick"] = "saveEdit()"; +"Save changes" }
                }
            }
        }

        div("toast") { id = "toast" }
        div { id = "ob-host" }
        script { unsafe { +PROJECTS_JS } }
        script { unsafe { +ONBOARDING_JS } }
        script { unsafe { +"Onboarding.boot('projects');" } }
    }
}

// ─── Billing Page ─────────────────────────────────────────────────────────────

private const val BILLING_CSS = """
.billing-page{padding:28px 32px;flex:1;overflow-y:auto}
.billing-page-header{margin-bottom:28px}
.billing-page-header h1{font-size:22px;font-weight:700;margin-bottom:4px}
.billing-page-header p{font-size:13px;color:var(--text-muted)}
.billing-section{margin-bottom:32px}
.billing-section-title{font-size:12px;font-weight:700;letter-spacing:1px;color:var(--text-muted);text-transform:uppercase;margin-bottom:12px}
.sub-summary{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:16px}
.sub-detail-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:20px;transition:border-color .25s}
.sub-detail-label{font-size:11px;font-weight:700;letter-spacing:1px;color:var(--text-muted);text-transform:uppercase;margin-bottom:6px}
.sub-detail-value{font-size:17px;font-weight:600;color:var(--text)}
.sub-detail-value.accent{color:var(--accent)}
.plan-status-row{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:16px}
.status-badge{display:inline-flex;align-items:center;gap:5px;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:700;letter-spacing:.5px}
.status-active{background:rgba(0,229,160,.12);color:var(--accent);border:1px solid rgba(0,229,160,.25)}
.status-trial{background:rgba(250,173,20,.1);color:var(--yellow);border:1px solid rgba(250,173,20,.25)}
.status-cancelling{background:rgba(255,77,79,.1);color:var(--red);border:1px solid rgba(255,77,79,.2)}
.status-free{background:var(--surface2);color:var(--text-muted);border:1px solid var(--border)}
.plan-actions-row{display:flex;gap:10px;flex-wrap:wrap;margin-top:16px;align-items:center}
.plan-upgrade-group{background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius);padding:16px;margin-top:16px}
.plan-upgrade-group-title{font-size:11px;font-weight:700;letter-spacing:1px;color:var(--text-muted);text-transform:uppercase;margin-bottom:10px}
.upgrade-option{display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border-radius:var(--radius-sm);background:var(--surface);border:1px solid var(--border);margin-bottom:8px;transition:border-color .15s}
.upgrade-option:last-child{margin-bottom:0}
.upgrade-option:hover{border-color:rgba(0,229,160,.3)}
.upgrade-option-info strong{font-size:13px;font-weight:600}
.upgrade-option-info span{font-size:12px;color:var(--text-muted)}
.upgrade-option-price{font-size:13px;font-weight:700;color:var(--accent);margin-right:12px}
.sync-state{display:none;align-items:center;gap:12px;padding:14px 16px;background:rgba(0,229,160,.06);border:1px solid rgba(0,229,160,.2);border-radius:var(--radius);margin-top:16px}
.sync-state.visible{display:flex}
.sync-spinner{width:18px;height:18px;border:2px solid rgba(0,229,160,.3);border-top-color:var(--accent);border-radius:50%;animation:spin .8s linear infinite;flex-shrink:0}
@keyframes spin{to{transform:rotate(360deg)}}
.sync-text{font-size:13px;color:var(--accent);font-weight:500}
.inline-banner{display:none;padding:14px 18px;border-radius:var(--radius);margin-top:0;margin-bottom:20px;font-size:13px;line-height:1.5}
.inline-banner.visible{display:block}
.inline-banner.success{background:rgba(0,229,160,.08);border:1px solid rgba(0,229,160,.25);color:var(--accent)}
.inline-banner.warning{background:rgba(250,173,20,.08);border:1px solid rgba(250,173,20,.25);color:var(--yellow)}
.inline-banner.error{background:rgba(255,77,79,.07);border:1px solid rgba(255,77,79,.2);color:var(--red)}
.inline-banner strong{display:block;font-size:14px;margin-bottom:3px}
.payment-method-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:20px;display:flex;align-items:center;gap:16px}
.payment-method-icon{width:44px;height:44px;background:var(--surface2);border:1px solid var(--border);border-radius:var(--radius-sm);display:flex;align-items:center;justify-content:center;flex-shrink:0}
.payment-method-info{flex:1}
.payment-method-title{font-size:14px;font-weight:600;margin-bottom:2px}
.payment-method-sub{font-size:12px;color:var(--text-muted)}
.invoice-table-header{display:grid;grid-template-columns:110px 1fr 100px 90px 80px;gap:12px;padding:8px 16px;font-size:11px;font-weight:700;letter-spacing:.5px;color:var(--text-muted);text-transform:uppercase;border-bottom:1px solid var(--border)}
.invoice-row-full{display:grid;grid-template-columns:110px 1fr 100px 90px 80px;gap:12px;align-items:center;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-sm);padding:12px 16px;font-size:13px;transition:border-color .15s}
.invoice-row-full:hover{border-color:rgba(0,229,160,.2)}
.invoice-id{font-family:monospace;font-size:11px;color:var(--text-dim);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.invoice-amount{font-weight:600}
.invoice-download{font-size:12px;color:var(--text-muted);display:flex;align-items:center;gap:4px;transition:color .15s}
.invoice-download:hover{color:var(--accent)}
.empty-invoices{text-align:center;padding:40px 24px;color:var(--text-muted);font-size:14px;background:var(--surface);border:1px dashed var(--border);border-radius:var(--radius)}
.usage-overview{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:20px}
.usage-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px}
.usage-item-label{font-size:12px;color:var(--text-muted);margin-bottom:6px}
.usage-item-value{font-size:15px;font-weight:600;margin-bottom:8px}
@media(max-width:768px){.sub-summary{grid-template-columns:1fr}.usage-grid{grid-template-columns:1fr}.invoice-table-header{display:none}.invoice-row-full{grid-template-columns:1fr 1fr;grid-template-rows:auto auto}}
"""

private val BILLING_JS = """
const BASE='/transloom/api';
let token=localStorage.getItem('transloom_token');
const urlParams=new URLSearchParams(location.search);
const urlToken=urlParams.get('token');
if(urlToken){localStorage.setItem('transloom_token',urlToken);token=urlToken;urlParams.delete('token');const qs=urlParams.toString();history.replaceState({},'','/transloom/billing'+(qs?'?'+qs:''));}
if(!token){window.location.href='/transloom/app';throw new Error('no token');}
document.addEventListener('click',function(e){
  var a=e.target.closest&&e.target.closest('a[href^="/transloom/"]');
  if(!a||a.target==='_blank'||a.hasAttribute('download'))return;
  if(a.getAttribute('href').startsWith('/transloom/auth/'))return;
  try{var u=new URL(a.href,location.origin);if(!u.searchParams.get('token')){u.searchParams.set('token',token);a.href=u.toString();}}catch(_){}
},true);
function authHeaders(){return{'Authorization':'Bearer '+token,'Content-Type':'application/json'};}
async function api(path,opts={}){
  const res=await fetch(BASE+path,{...opts,headers:{...authHeaders(),...(opts.headers||{})}});
  if(res.status===401){logout();return null;}return res;
}
function logout(){localStorage.removeItem('transloom_token');window.location.href='/transloom/auth/logout';}
function toast(msg,type='success'){const el=document.getElementById('toast');el.textContent=msg;el.className='toast show '+type;setTimeout(()=>el.className='toast',2800);}
function esc(s){if(!s)return '';const d=document.createElement('div');d.textContent=String(s);return d.innerHTML;}
function jwtPayload(t){try{return JSON.parse(atob(t.split('.')[1]));}catch{return{};}}

// ── Payment flow lock — prevents duplicate checkout attempts ──────────────────
let paymentPending=false;

// ── In-page sync state elements ───────────────────────────────────────────────
function showSyncState(visible,msg='Updating your plan…'){
  const el=document.getElementById('sync-state');
  if(!el)return;
  el.classList.toggle('visible',visible);
  const txt=el.querySelector('.sync-text');
  if(txt)txt.textContent=msg;
}

function showInlineBanner(type,title,body){
  const el=document.getElementById('payment-banner');
  if(!el)return;
  el.className='inline-banner visible '+type;
  el.innerHTML=(title?`<strong>${'$'}{esc(title)}</strong>`:'')+(body?esc(body):'');
}

function hideInlineBanner(){
  const el=document.getElementById('payment-banner');
  if(el)el.className='inline-banner';
}

// ── Main billing page loader ───────────────────────────────────────────────────
async function loadBillingPage(){
  const [subRes,usageRes,invRes]=await Promise.all([
    api('/billing/subscription'),api('/billing/usage'),api('/billing/invoices')
  ]);
  if(!subRes||!subRes.ok||!usageRes||!usageRes.ok){toast('Failed to load billing info','error');return;}
  const sub=await subRes.json();
  const usage=await usageRes.json();

  // Plan name
  document.getElementById('plan-name').textContent=sub.displayName||sub.plan;

  // Status badge
  const statusEl=document.getElementById('plan-status');
  if(sub.cancelAtPeriodEnd){
    statusEl.className='status-badge status-cancelling';statusEl.textContent='Cancelling';
  } else if(sub.plan==='FREE'){
    statusEl.className='status-badge status-free';statusEl.textContent='Free';
  } else if(sub.trialLimitHit){
    statusEl.className='status-badge status-trial';statusEl.textContent='Trial limit hit';
  } else {
    statusEl.className='status-badge status-active';statusEl.textContent='Active';
  }

  // Renewal / period
  const expiryLabel=document.getElementById('plan-expiry-label');
  if(sub.currentPeriodEnd){
    document.getElementById('renewal-date').textContent=sub.currentPeriodEnd;
    document.getElementById('billing-period').textContent='Until '+sub.currentPeriodEnd;
    if(expiryLabel)expiryLabel.textContent=sub.cancelAtPeriodEnd?'CANCELS ON':'RENEWAL DATE';
  } else {
    document.getElementById('renewal-date').textContent='—';
    document.getElementById('billing-period').textContent='No active billing period';
    if(expiryLabel)expiryLabel.textContent='RENEWAL DATE';
  }

  // Price
  if(sub.monthlyPricePaise){
    document.getElementById('plan-price').textContent='₹'+(sub.monthlyPricePaise/100)+'/mo';
  } else {
    document.getElementById('plan-price').textContent=sub.plan==='FREE'?'Free':'—';
  }

  // Usage bars
  const barEl=document.getElementById('usage-bar-billing');
  if(usage.stringLimit){
    const pct=Math.min(100,Math.round((usage.stringsTranslated/usage.stringLimit)*100));
    if(barEl){barEl.style.width=pct+'%';if(pct>80)barEl.style.background='var(--yellow)';}
    document.getElementById('usage-strings').textContent=usage.stringsTranslated+' / '+usage.stringLimit;
  } else {
    if(barEl){barEl.style.width='100%';barEl.style.background='var(--accent-dim)';}
    document.getElementById('usage-strings').textContent=usage.stringsTranslated+' (unlimited)';
  }
  document.getElementById('usage-projects').textContent=
    usage.projectLimit?usage.projectsUsed+' / '+usage.projectLimit:usage.projectsUsed+' (unlimited)';

  // Plan actions — only rendered when no payment is in progress
  if(!paymentPending){
    renderPlanActions(sub);
  }

  // User chip
  const chip=document.getElementById('user-chip');
  if(chip){const p=jwtPayload(token);chip.textContent=p.username?'@'+p.username:(p.email||'You');}

  // Invoices
  if(invRes){
    const invData=await invRes.json();
    const list=document.getElementById('invoice-list-billing');
    if(!invData.invoices||invData.invoices.length===0){
      list.innerHTML='<div class="empty-invoices">No invoices yet. Your first invoice will appear here after your trial ends.</div>';
    } else {
      list.innerHTML=invData.invoices.map(inv=>`
        <div class="invoice-row-full">
          <span>${'$'}{esc(inv.date)}</span>
          <span class="invoice-id">${'$'}{esc(inv.id)}</span>
          <span class="invoice-amount">${'$'}{esc(inv.amount)}</span>
          <span class="invoice-status-${'$'}{esc(inv.status.toLowerCase())}">${'$'}{esc(inv.status)}</span>
          <a class="invoice-download" href="/transloom/api/billing/invoices/${'$'}{encodeURIComponent(inv.id)}/receipt" target="_blank">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            Download
          </a>
        </div>`).join('');
    }
  }
}

// ── Render plan action buttons based on current plan ──────────────────────────
function renderPlanActions(sub){
  const el=document.getElementById('plan-actions-billing');
  if(!el)return;
  let html='';

  if(sub.trialLimitHit){
    html+='<button class="btn btn-primary" onclick="activateNow()">Activate paid plan now</button>';
  }

  if(sub.plan==='FREE'){
    // Show both upgrade options explicitly so users know what they're buying
    html+=`
      <div class="plan-upgrade-group">
        <div class="plan-upgrade-group-title">Available plans</div>
        <div class="upgrade-option">
          <div class="upgrade-option-info">
            <strong>Solo</strong>
            <span> &mdash; 5,000 strings &middot; 3 projects &middot; All languages</span>
          </div>
          <div style="display:flex;align-items:center">
            <span class="upgrade-option-price">₹499/mo</span>
            <button class="btn btn-primary" style="font-size:12px;padding:6px 14px" onclick="startUpgrade('SOLO')">Select</button>
          </div>
        </div>
        <div class="upgrade-option">
          <div class="upgrade-option-info">
            <strong>Team</strong>
            <span> &mdash; Unlimited strings &middot; 10 projects &middot; All languages</span>
          </div>
          <div style="display:flex;align-items:center">
            <span class="upgrade-option-price">₹1,999/mo</span>
            <button class="btn btn-primary" style="font-size:12px;padding:6px 14px;background:var(--surface2);color:var(--text);border:1px solid var(--border)" onclick="startUpgrade('TEAM')">Select</button>
          </div>
        </div>
      </div>`;
  } else if(sub.plan==='SOLO'){
    html+='<button class="btn btn-primary" style="font-size:13px" onclick="startUpgrade(\'TEAM\')">Upgrade to Team</button>';
  }

  if((sub.plan==='SOLO'||sub.plan==='TEAM')&&!sub.cancelAtPeriodEnd){
    html+='<a href="#" style="font-size:13px;color:var(--text-muted);align-self:center" onclick="cancelSubscription();return false">Cancel subscription →</a>';
  }
  if(sub.cancelAtPeriodEnd&&(sub.plan==='SOLO'||sub.plan==='TEAM')){
    html+='<span style="font-size:12px;color:var(--yellow)">Access continues until '+esc(sub.currentPeriodEnd||'end of period')+'</span>';
  }

  el.innerHTML=html;
}

// ── Open Razorpay checkout for a given plan ────────────────────────────────────
async function startUpgrade(plan){
  if(paymentPending){toast('A payment is already in progress','error');return;}
  if(!window.Razorpay){toast('Checkout not loaded — please refresh','error');return;}

  paymentPending=true;
  // Optimistically disable action buttons so user can't click again
  const actionsEl=document.getElementById('plan-actions-billing');
  if(actionsEl)actionsEl.innerHTML='<span style="font-size:13px;color:var(--text-muted)">Opening checkout…</span>';

  const res=await api('/billing/subscribe',{method:'POST',body:JSON.stringify({plan})});
  if(!res||!res.ok){
    const err=res?await res.json():{};
    toast(err.error||'Could not initiate checkout','error');
    paymentPending=false;
    loadBillingPage();
    return;
  }
  const data=await res.json();

  const rzp=new Razorpay({
    key:data.keyId,
    subscription_id:data.subscriptionId,
    name:'Transloom',
    description:data.plan+' plan · 7-day free trial',
    image:'/transloom/favicon.svg',
    theme:{color:'#00E5A0',backdrop_color:'#000000'},
    modal:{escape:true,backdropclose:false,ondismiss:function(){
      // User closed checkout without paying — restore buttons
      paymentPending=false;
      loadBillingPage();
    }},
    handler:function(resp){
      // Payment captured by Razorpay — now verify server-side and sync subscription
      verifyAndSync(
        resp.razorpay_payment_id||'',
        resp.razorpay_subscription_id||data.subscriptionId,
        resp.razorpay_signature||''
      );
    }
  });
  rzp.on('payment.failed',function(r){
    paymentPending=false;
    const desc=r.error&&r.error.description?r.error.description:'Please try again.';
    showInlineBanner('error','Payment failed',desc);
    loadBillingPage();
  });
  rzp.open();
}

// ── Post-payment: verify + immediately activate plan (no webhook polling) ─────
async function verifyAndSync(paymentId,subscriptionId,signature){
  showSyncState(true,'Verifying payment…');
  const actionsEl=document.getElementById('plan-actions-billing');
  if(actionsEl)actionsEl.innerHTML='<span style="font-size:13px;color:var(--text-muted)">Activating…</span>';

  const vRes=await api('/billing/confirm-payment',{
    method:'POST',
    body:JSON.stringify({paymentId,subscriptionId,signature})
  });

  showSyncState(false);
  paymentPending=false;

  if(!vRes||!vRes.ok){
    showInlineBanner('warning',
      'Payment received — verifying your subscription.',
      'This can take a moment. If your plan has not updated after refreshing, contact support@androidplay.in with reference: '+esc(paymentId)
    );
    loadBillingPage();
    return;
  }

  const data=await vRes.json();
  const planName=data.displayName||data.plan||'paid plan';
  showInlineBanner('success',
    'You are now on the '+esc(planName)+' plan.',
    'Your subscription is active and all features are now unlocked.'
  );
  loadBillingPage();
}

async function downgradePlan(){
  if(!confirm('Downgrade to Free plan? Your current plan stays active until the period ends.'))return;
  await cancelSubscription();
}

async function activateNow(){
  const res=await api('/billing/activate-now',{method:'POST'});
  if(!res)return;
  if(res.ok){toast('Paid plan activated');hideInlineBanner();loadBillingPage();}
  else{const err=await res.json();toast(err.error||'Activation failed','error');}
}

async function cancelSubscription(){
  if(!confirm('Cancel subscription? You will keep access until the end of the billing period.'))return;
  const res=await api('/billing/cancel',{method:'POST'});
  if(!res)return;
  if(res.ok){toast('Subscription will cancel at end of period');loadBillingPage();}
  else{const err=await res.json();toast(err.error||'Cancel failed','error');}
}

loadBillingPage();
""".trimIndent()

private fun HTML.billingApp() {
    head {
        title { +"Transloom — Billing" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        favicon()
        script { src = "https://checkout.razorpay.com/v1/checkout.js" }
        style { unsafe { +"$SHARED_CSS$DASHBOARD_CSS$BILLING_CSS$ONBOARDING_CSS" } }
    }
    body {
        div("app-layout") {
            unsafe { +APP_SIDEBAR_BILLING }
            main("billing-page") {
                div("billing-page-header") {
                    h1 { +"Billing" }
                    p { +"Manage your plan, payment method, and download invoices." }
                }

                // ── Inline payment status banner (hidden until needed) ─────────
                div {
                    id = "payment-banner"
                    classes = setOf("inline-banner")
                }

                // ── Subscription summary ──────────────────────────────────────
                div("billing-section") {
                    div("billing-section-title") { +"Subscription" }
                    div("sub-summary") {
                        div("sub-detail-card") {
                            div("plan-status-row") {
                                div {
                                    div("sub-detail-label") { +"Current Plan" }
                                    p("plan-name sub-detail-value accent") { id = "plan-name"; +"—" }
                                }
                                span { id = "plan-status"; classes = setOf("status-badge", "status-active"); +"Loading" }
                            }
                            div {
                                div("sub-detail-label") { +"Monthly price" }
                                div("sub-detail-value") { id = "plan-price"; +"—" }
                            }
                            // Sync state — visible during post-payment reconciliation
                            div {
                                id = "sync-state"
                                classes = setOf("sync-state")
                                div { classes = setOf("sync-spinner") }
                                span("sync-text") { +"Updating your plan…" }
                            }
                            div {
                                id = "plan-actions-billing"
                                classes = setOf("plan-actions-row")
                            }
                        }
                        div("sub-detail-card") {
                            div {
                                div("sub-detail-label") { id = "plan-expiry-label"; +"Renewal Date" }
                                div("sub-detail-value") { id = "renewal-date"; +"—" }
                            }
                            div {
                                style = "margin-top:16px"
                                div("sub-detail-label") { +"Billing Period" }
                                div("sub-detail-value") { id = "billing-period"; style = "font-size:14px"; +"—" }
                            }
                        }
                    }
                }

                // ── Usage overview ────────────────────────────────────────────
                div("billing-section") {
                    div("billing-section-title") { +"Usage This Period" }
                    div("usage-overview") {
                        div("usage-grid") {
                            div {
                                div("usage-item-label") { +"Strings translated" }
                                div("usage-item-value") { id = "usage-strings"; +"—" }
                                div("usage-bar-track") { div("usage-bar-fill") { id = "usage-bar-billing" } }
                            }
                            div {
                                div("usage-item-label") { +"Projects" }
                                div("usage-item-value") { id = "usage-projects"; +"—" }
                            }
                        }
                    }
                }

                // ── Payment method ────────────────────────────────────────────
                div("billing-section") {
                    div("billing-section-title") { +"Payment Method" }
                    div("payment-method-card") {
                        div("payment-method-icon") {
                            unsafe { +"<svg width='20' height='20' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><rect x='1' y='4' width='22' height='16' rx='2' ry='2'/><line x1='1' y1='10' x2='23' y2='10'/></svg>" }
                        }
                        div("payment-method-info") {
                            div("payment-method-title") { +"Managed by Razorpay" }
                            div("payment-method-sub") { +"Payment details are securely stored with Razorpay. To update your card or UPI, contact support." }
                        }
                    }
                }

                // ── Invoice history ───────────────────────────────────────────
                div("billing-section") {
                    div("billing-section-title") { +"Invoice History" }
                    div("invoice-table-header") {
                        span { +"Date" }
                        span { +"Reference" }
                        span { +"Amount" }
                        span { +"Status" }
                        span { +"" }
                    }
                    div {
                        id = "invoice-list-billing"
                        classes = setOf("invoice-list")
                        div("empty-invoices") { +"Loading invoices..." }
                    }
                }
            }
        }

        div {
            id = "toast"
            classes = setOf("toast")
        }
        div { id = "ob-host" }

        script { unsafe { raw(BILLING_JS) } }
        script { unsafe { +ONBOARDING_JS } }
        script { unsafe { +"Onboarding.boot('billing');" } }
    }
}

// ─── Review Portal ────────────────────────────────────────────────────────────

private fun HTML.reviewPortal() {
    head {
        title { +"Transloom — Review Portal" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        favicon()
        style { unsafe { +"$SHARED_CSS$DASHBOARD_CSS$REVIEW_CSS" } }
    }
    body {
        div("app-layout") {
            unsafe { +APP_SIDEBAR_REVIEW }
            main("rv-page") {
                div("page-header") {
                    div {
                        div("page-title") { +"Translation Review" }
                        div("page-sub") { +"Review flagged translations before they merge into your repos. Edit inline, then approve or reject." }
                    }
                    div("rv-stat-chips") { id = "rv-stat-chips" }
                }
                div("rv-toolbar") {
                    div("rv-filters") {
                        button(classes = "rv-filter active") {
                            attributes["onclick"] = "filterBy('all',this)"
                            +"All "; span("rv-filter-count") { id = "cnt-all" }
                        }
                        button(classes = "rv-filter") {
                            attributes["onclick"] = "filterBy('review',this)"
                            +"Pending "; span("rv-filter-count") { id = "cnt-review" }
                        }
                        button(classes = "rv-filter") {
                            id = "tab-cultural"
                            attributes["onclick"] = "filterBy('cultural',this)"
                            attributes["style"] = "display:none"
                            +"Cultural "; span("rv-filter-count") { id = "cnt-cultural" }
                        }
                        button(classes = "rv-filter") {
                            attributes["onclick"] = "filterBy('blocked',this)"
                            +"Blocked "; span("rv-filter-count") { id = "cnt-blocked" }
                        }
                    }
                    div("rv-search-wrap") {
                        unsafe { +"<svg class='rv-search-icon' width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><circle cx='11' cy='11' r='8'/><line x1='21' y1='21' x2='16.65' y2='16.65'/></svg>" }
                        input {
                            type = InputType.text
                            classes = setOf("rv-search-input")
                            id = "rv-search"
                            placeholder = "Search by key, project, or text…"
                            attributes["oninput"] = "applySearch(this.value)"
                        }
                    }
                }
                div("rv-list") { id = "review-list" }
            }
        }
        div("toast") { id = "toast" }
        script { unsafe { +REVIEW_JS } }
    }
}

private const val REVIEW_CSS = """
.rv-page{flex:1;overflow-y:auto;padding:28px 32px}
.rv-stat-chips{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.rv-stat-chip{display:inline-flex;align-items:center;gap:5px;padding:4px 11px;border-radius:20px;font-size:12px;font-weight:600;border:1px solid}
.rv-chip-pending{background:rgba(250,173,20,.1);color:var(--yellow);border-color:rgba(250,173,20,.25)}
.rv-chip-blocked{background:rgba(255,77,79,.1);color:var(--red);border-color:rgba(255,77,79,.25)}
.rv-chip-projects{background:var(--accent-dim);color:var(--accent);border-color:rgba(0,229,160,.25)}
.rv-toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;gap:12px;flex-wrap:wrap}
.rv-filters{display:flex;gap:4px}
.rv-filter{padding:7px 14px;border-radius:20px;background:var(--surface);border:1px solid var(--border);color:var(--text-muted);font-size:13px;cursor:pointer;transition:all .12s;display:inline-flex;align-items:center;gap:6px}
.rv-filter.active{background:var(--accent-dim);border-color:var(--accent);color:var(--accent);font-weight:500}
.rv-filter-count{font-size:11px;font-weight:700;background:rgba(0,0,0,.15);border-radius:10px;padding:1px 6px;min-width:18px;text-align:center;line-height:1.6}
.rv-filter.active .rv-filter-count{background:rgba(0,229,160,.18)}
.rv-search-wrap{position:relative;flex:1;max-width:300px}
.rv-search-icon{position:absolute;left:10px;top:50%;transform:translateY(-50%);color:var(--text-muted);pointer-events:none}
.rv-search-input{padding-left:32px!important}
.rv-list{display:flex;flex-direction:column;gap:12px}
.rv-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden;transition:box-shadow .15s}
.rv-card:hover{box-shadow:0 2px 16px rgba(0,0,0,.22)}
.rv-card.status-review{border-left:3px solid var(--yellow)}
.rv-card.status-blocked{border-left:3px solid var(--red)}
.rv-card-header{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;background:var(--surface2);border-bottom:1px solid var(--border);gap:12px}
.rv-card-header-left{display:flex;align-items:center;gap:10px;min-width:0;flex:1}
.rv-key{font-size:12px;font-family:'SFMono-Regular',Consolas,monospace;color:var(--accent);background:var(--accent-dim2);padding:3px 8px;border-radius:4px;border:1px solid rgba(0,229,160,.15);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:260px;display:inline-block;vertical-align:middle}
.rv-badges{display:flex;align-items:center;gap:6px;flex-shrink:0;flex-wrap:wrap}
.rv-badge{font-size:11px;font-weight:500;padding:2px 8px;border-radius:20px;white-space:nowrap;border:1px solid}
.rv-badge-project{background:var(--surface);border-color:var(--border);color:var(--text-muted)}
.rv-badge-lang{background:var(--accent-dim2);border-color:rgba(0,229,160,.2);color:var(--accent);font-family:monospace;font-size:10px;letter-spacing:.6px;font-weight:700}
.rv-status-pill{font-size:11px;font-weight:700;padding:3px 10px;border-radius:20px;letter-spacing:.2px;flex-shrink:0;border:1px solid}
.rv-pill-review{background:rgba(250,173,20,.1);color:var(--yellow);border-color:rgba(250,173,20,.3)}
.rv-pill-blocked{background:rgba(255,77,79,.1);color:var(--red);border-color:rgba(255,77,79,.3)}
.rv-pill-cultural{background:rgba(138,43,226,.12);color:#b57bee;border-color:rgba(138,43,226,.3)}
.rv-block-banner{display:flex;align-items:flex-start;gap:8px;padding:10px 16px;background:rgba(255,77,79,.05);border-bottom:1px solid rgba(255,77,79,.15);font-size:12px;color:var(--red);line-height:1.5}
.rv-cultural-banner{padding:12px 16px;background:rgba(138,43,226,.06);border-bottom:1px solid rgba(138,43,226,.18);font-size:12px}
.rv-cultural-banner-title{display:flex;align-items:center;gap:6px;color:#b57bee;font-weight:600;margin-bottom:6px}
.rv-cultural-issues{margin:0;padding:0 0 0 18px;color:var(--text-muted);line-height:1.7}
.rv-chip-cultural{background:rgba(138,43,226,.1);color:#b57bee;border-color:rgba(138,43,226,.25)}
.rv-body{display:grid;grid-template-columns:1fr 1fr}
.rv-source{padding:16px;border-right:1px solid var(--border)}
.rv-target{padding:16px}
.rv-pane-label{font-size:10px;font-weight:700;letter-spacing:1px;color:var(--text-muted);text-transform:uppercase;margin-bottom:10px;display:flex;align-items:center;gap:5px}
.rv-editable-hint{font-weight:400;letter-spacing:0;text-transform:none;font-size:11px;color:var(--text-muted);opacity:.65;margin-left:2px}
.rv-source-text{font-size:14px;color:var(--text-dim);line-height:1.75}
.rv-textarea{font-size:14px;line-height:1.75;resize:vertical;min-height:80px;background:var(--surface2)!important;border-color:var(--border)!important;transition:border-color .15s!important}
.rv-textarea:focus{border-color:var(--accent)!important}
.rv-actions{padding:10px 16px;border-top:1px solid var(--border);display:flex;align-items:center;justify-content:space-between;gap:12px}
.rv-char-hint{font-size:11px;color:var(--text-muted);font-variant-numeric:tabular-nums;white-space:nowrap}
.rv-action-btns{display:flex;gap:8px;flex-shrink:0}
.rv-btn-reject{display:inline-flex;align-items:center;gap:5px;background:transparent;color:var(--text-muted);border:1px solid var(--border);padding:6px 14px;font-size:13px;border-radius:var(--radius-sm);cursor:pointer;transition:all .15s;font-family:inherit}
.rv-btn-reject:hover:not(:disabled){border-color:var(--red);color:var(--red);background:rgba(255,77,79,.06)}
.rv-btn-approve{display:inline-flex;align-items:center;gap:5px;background:var(--accent);color:#000;padding:6px 16px;font-size:13px;font-weight:600;border-radius:var(--radius-sm);border:none;cursor:pointer;transition:all .15s;font-family:inherit}
.rv-btn-approve:hover:not(:disabled){background:#00c98d;transform:translateY(-1px);box-shadow:0 4px 14px -4px rgba(0,229,160,.4)}
.rv-btn-approve:disabled,.rv-btn-reject:disabled{opacity:.38;cursor:not-allowed!important;transform:none!important;box-shadow:none!important}
.rv-reject-panel{display:none;padding:14px 16px;border-top:1px solid rgba(255,77,79,.18);background:rgba(255,77,79,.03)}
.rv-reject-panel.open{display:block}
.rv-reject-textarea{min-height:60px;resize:vertical;font-size:13px;margin-top:8px;margin-bottom:10px;border-color:rgba(255,77,79,.35)!important}
.rv-reject-footer{display:flex;gap:8px;justify-content:flex-end}
.rv-btn-confirm-reject{background:var(--red);color:#fff;border:none;padding:6px 14px;font-size:13px;border-radius:var(--radius-sm);cursor:pointer;font-family:inherit;transition:background .15s}
.rv-btn-confirm-reject:hover:not(:disabled){background:#e03e40}
.rv-btn-confirm-reject:disabled{opacity:.4;cursor:not-allowed}
.rv-btn-cancel{background:transparent;color:var(--text-muted);border:1px solid var(--border);padding:6px 14px;font-size:13px;border-radius:var(--radius-sm);cursor:pointer;font-family:inherit;transition:all .15s}
.rv-btn-cancel:hover{color:var(--text);border-color:var(--text-muted)}
@media(max-width:700px){.rv-body{grid-template-columns:1fr}.rv-source{border-right:none;border-bottom:1px solid var(--border)}.rv-toolbar{flex-direction:column;align-items:stretch}.rv-search-wrap{max-width:100%}}
"""

private val REVIEW_JS = """
const BASE='/transloom/api';
let token=localStorage.getItem('transloom_token');
const urlParams=new URLSearchParams(location.search);
const urlToken=urlParams.get('token');
if(urlToken){localStorage.setItem('transloom_token',urlToken);token=urlToken;history.replaceState({},'','/transloom/review-portal');}
if(!token){window.location.href='/transloom/app';throw new Error('no token');}
document.addEventListener('click',function(e){
  var a=e.target.closest&&e.target.closest('a[href^="/transloom/"]');
  if(!a||a.target==='_blank'||a.hasAttribute('download'))return;
  if(a.getAttribute('href').startsWith('/transloom/auth/'))return;
  try{var u=new URL(a.href,location.origin);if(!u.searchParams.get('token')){u.searchParams.set('token',token);a.href=u.toString();}}catch(_){}
},true);
function authHeaders(){return{'Authorization':'Bearer '+token,'Content-Type':'application/json'};}
function logout(){localStorage.removeItem('transloom_token');window.location.href='/transloom/auth/logout';}
function toast(msg,type='success'){const el=document.getElementById('toast');el.textContent=msg;el.className='toast show '+type;setTimeout(()=>el.className='toast',2800);}
function esc(s){if(!s)return '';const d=document.createElement('div');d.textContent=String(s);return d.innerHTML;}
function jwtPayload(t){try{return JSON.parse(atob(t.split('.')[1]));}catch(e){return{};}}
const chip=document.getElementById('user-chip');if(chip){const p=jwtPayload(token);chip.textContent=p.username?'@'+p.username:(p.email||'You');}

const LANG_NAMES={af:'Afrikaans',ar:'Arabic',bg:'Bulgarian',bn:'Bengali',ca:'Catalan',cs:'Czech',da:'Danish',de:'German',el:'Greek',en:'English',es:'Spanish',et:'Estonian',fa:'Persian',fi:'Finnish',fr:'French',gu:'Gujarati',he:'Hebrew',hi:'Hindi',hr:'Croatian',hu:'Hungarian',hy:'Armenian',id:'Indonesian',it:'Italian',ja:'Japanese',ka:'Georgian',kn:'Kannada',ko:'Korean',lt:'Lithuanian',lv:'Latvian',mk:'Macedonian',ml:'Malayalam',mr:'Marathi',ms:'Malay',nl:'Dutch',no:'Norwegian',pa:'Punjabi',pl:'Polish',pt:'Portuguese',ro:'Romanian',ru:'Russian',sk:'Slovak',sl:'Slovenian',sq:'Albanian',sr:'Serbian',sv:'Swedish',sw:'Swahili',ta:'Tamil',te:'Telugu',th:'Thai',tl:'Filipino',tr:'Turkish',uk:'Ukrainian',ur:'Urdu',vi:'Vietnamese',zh:'Chinese'};
function langName(code){return LANG_NAMES[code]||(code?code.toUpperCase():'?');}

let allItems=[];let currentFilter='all';let searchQuery='';

async function loadReviews(){
  document.getElementById('review-list').innerHTML='<div class="empty-state">Loading…</div>';
  const res=await fetch(BASE+'/review',{headers:authHeaders()});
  if(!res.ok){document.getElementById('review-list').innerHTML='<div class="empty-state">Failed to load reviews. Please try refreshing.</div>';return;}
  const data=await res.json();
  allItems=data.pending_reviews||[];
  updateStatChips();
  render();
}

function isCulturalItem(item){return item.status==='review'&&item.blockReason&&item.blockReason.startsWith('Cultural:');}
function parseCulturalIssues(blockReason){return blockReason.replace(/^Cultural:\s*/,'').split(/;\s*/).filter(Boolean);}

function updateStatChips(){
  const pending=allItems.filter(i=>i.status==='review').length;
  const blocked=allItems.filter(i=>i.status==='blocked').length;
  const cultural=allItems.filter(isCulturalItem).length;
  const projects=[...new Set(allItems.map(i=>i.projectName))].length;
  const el=document.getElementById('rv-stat-chips');if(!el)return;
  const clockSvg='<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>';
  const blockSvg='<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/></svg>';
  const folderSvg='<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>';
  const culturalSvg='<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3c-1 3-2 4-5 5 3 1 4 2 5 5 1-3 2-4 5-5-3-1-4-2-5-5z"/><path d="M5.5 10.5c-.5 1.5-1 2-2.5 2.5 1.5.5 2 1 2.5 2.5.5-1.5 1-2 2.5-2.5-1.5-.5-2-1-2.5-2.5z"/></svg>';
  el.innerHTML=(pending>0?'<span class="rv-stat-chip rv-chip-pending">'+clockSvg+' '+pending+' pending</span>':'')+(cultural>0?'<span class="rv-stat-chip rv-chip-cultural">'+culturalSvg+' '+cultural+' cultural</span>':'')+(blocked>0?'<span class="rv-stat-chip rv-chip-blocked">'+blockSvg+' '+blocked+' blocked</span>':'')+(projects>1?'<span class="rv-stat-chip rv-chip-projects">'+folderSvg+' '+projects+' projects</span>':'')+(pending===0&&blocked===0?'<span class="rv-stat-chip rv-chip-projects"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg> All clear</span>':'');
}

function updateCounts(){
  const all=allItems.length;
  const pending=allItems.filter(i=>i.status==='review').length;
  const blocked=allItems.filter(i=>i.status==='blocked').length;
  const cultural=allItems.filter(isCulturalItem).length;
  const s=(id,n)=>{const el=document.getElementById(id);if(el)el.textContent=n;};
  s('cnt-all',all);s('cnt-review',pending);s('cnt-blocked',blocked);s('cnt-cultural',cultural);
  // Show/hide cultural tab based on whether any exist
  const culturalTab=document.getElementById('tab-cultural');
  if(culturalTab)culturalTab.style.display=cultural>0?'':'none';
}

function filterBy(status,btn){
  currentFilter=status;
  document.querySelectorAll('.rv-filter').forEach(b=>b.classList.remove('active'));
  btn.classList.add('active');render();
}

function applySearch(q){searchQuery=q.toLowerCase().trim();render();}

function getVisible(){
  let items;
  if(currentFilter==='cultural') items=allItems.filter(isCulturalItem);
  else if(currentFilter==='all') items=allItems;
  else items=allItems.filter(i=>i.status===currentFilter);
  if(searchQuery)items=items.filter(i=>(i.stringKey||'').toLowerCase().includes(searchQuery)||(i.projectName||'').toLowerCase().includes(searchQuery)||(i.sourceText||'').toLowerCase().includes(searchQuery)||(i.translatedText||'').toLowerCase().includes(searchQuery));
  return items;
}

function render(){
  updateCounts();
  const items=getVisible();
  const list=document.getElementById('review-list');
  if(items.length===0){
    const okSvg='<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="display:inline;vertical-align:-4px;margin-right:8px;color:var(--accent)"><polyline points="20 6 9 17 4 12"/></svg>';
    const msg=searchQuery?'No results for “'+esc(searchQuery)+'”.'
      :currentFilter==='blocked'?'No blocked translations.'
      :currentFilter==='review'?okSvg+'All pending translations reviewed!'
      :okSvg+'All caught up — no translations to review.';
    list.innerHTML='<div class="empty-state">'+msg+'</div>';return;
  }

  const warnSvg='<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;margin-top:1px"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>';
  const globeSvg='<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>';
  const xSvg='<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>';
  const checkSvg='<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>';
  const arrowSvg='<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>';

  list.innerHTML=items.map(function(item){
    var id=esc(item.id);
    var isBlocked=item.status==='blocked';
    var lang=(item.targetLanguage||'')+(item.targetRegion?'-'+item.targetRegion:'');
    var lname=langName(item.targetLanguage)+(item.targetRegion?' ('+esc(item.targetRegion)+')':'');
    var charCount=item.translatedText?item.translatedText.length:0;
    var srcLen=item.sourceText?item.sourceText.length:0;
    var pillCls=isBlocked?'rv-pill-blocked':(isCulturalItem(item)?'rv-pill-cultural':'rv-pill-review');
    var pillTxt=isBlocked?'Blocked':(isCulturalItem(item)?'Cultural':'Pending');
    var blockBanner=isBlocked&&item.blockReason?'<div class="rv-block-banner">'+warnSvg+'<span><strong>Rejection reason:</strong> '+esc(item.blockReason)+'</span></div>':'';
    var isCultural=isCulturalItem(item);
    var culturalBanner='';
    if(isCultural){
      var cIssues=parseCulturalIssues(item.blockReason||'');
      var issueHtml=cIssues.map(function(s){return '<li>'+esc(s)+'</li>';}).join('');
      culturalBanner='<div class="rv-cultural-banner"><div class="rv-cultural-banner-title"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3c-1 3-2 4-5 5 3 1 4 2 5 5 1-3 2-4 5-5-3-1-4-2-5-5z"/><path d="M5.5 10.5c-.5 1.5-1 2-2.5 2.5 1.5.5 2 1 2.5 2.5.5-1.5 1-2 2.5-2.5-1.5-.5-2-1-2.5-2.5z"/></svg><strong>Cultural sensitivity flag</strong></div>'+(issueHtml?'<ul class="rv-cultural-issues">'+issueHtml+'</ul>':'')+'</div>';
    }
    return '<div class="rv-card status-'+esc(item.status)+'" id="card-'+id+'">'+
      '<div class="rv-card-header">'+
        '<div class="rv-card-header-left">'+
          '<code class="rv-key" title="'+esc(item.stringKey)+'">'+esc(item.stringKey)+'</code>'+
          '<div class="rv-badges">'+
            '<span class="rv-badge rv-badge-project">'+esc(item.projectName)+'</span>'+
            '<span class="rv-badge rv-badge-lang" title="'+esc(lname)+'">'+esc(lang.toUpperCase())+'</span>'+
          '</div>'+
        '</div>'+
        '<span class="rv-status-pill '+pillCls+'">'+pillTxt+'</span>'+
      '</div>'+
      blockBanner+culturalBanner+
      '<div class="rv-body">'+
        '<div class="rv-source">'+
          '<div class="rv-pane-label">'+globeSvg+' Source &middot; English</div>'+
          '<div class="rv-source-text">'+esc(item.sourceText)+'</div>'+
        '</div>'+
        '<div class="rv-target">'+
          '<div class="rv-pane-label">'+arrowSvg+' Translation &middot; '+esc(lname)+'<span class="rv-editable-hint">&ensp;editable</span></div>'+
          '<textarea class="rv-textarea" id="trans-'+id+'" oninput="updateCharCount(\''+id+'\',this.value,'+srcLen+')">'+esc(item.translatedText)+'</textarea>'+
        '</div>'+
      '</div>'+
      '<div class="rv-actions">'+
        '<span class="rv-char-hint" id="chars-'+id+'">'+charCount+' chars</span>'+
        '<div class="rv-action-btns">'+
          '<button class="rv-btn-reject" id="btn-reject-'+id+'" onclick="showRejectPanel(\''+id+'\')">'+xSvg+' Reject</button>'+
          '<button class="rv-btn-approve" id="btn-approve-'+id+'" onclick="approve(\''+id+'\')">'+checkSvg+' Approve &amp; merge</button>'+
        '</div>'+
      '</div>'+
      '<div class="rv-reject-panel" id="reject-panel-'+id+'">'+
        '<div class="rv-pane-label" style="margin-bottom:6px">'+warnSvg+' Rejection Reason</div>'+
        '<textarea id="reject-reason-'+id+'" class="rv-reject-textarea" placeholder="Describe why this translation needs to be redone…"></textarea>'+
        '<div class="rv-reject-footer">'+
          '<button class="rv-btn-cancel" onclick="hideRejectPanel(\''+id+'\')">Cancel</button>'+
          '<button class="rv-btn-confirm-reject" onclick="confirmReject(\''+id+'\')">Confirm Rejection</button>'+
        '</div>'+
      '</div>'+
    '</div>';
  }).join('');
}

function updateCharCount(id,val,srcLen){
  const el=document.getElementById('chars-'+id);if(!el)return;
  const n=val.length;
  el.textContent=n+' chars';
  const ratio=srcLen>0?n/srcLen:1;
  el.style.color=(srcLen>0&&(ratio<0.35||ratio>2.8))?'var(--yellow)':'var(--text-muted)';
}

async function approve(id){
  const ab=document.getElementById('btn-approve-'+id),rb=document.getElementById('btn-reject-'+id);
  if(ab)ab.disabled=true;if(rb)rb.disabled=true;
  const editedText=(document.getElementById('trans-'+id)?.value||'').trim();
  const res=await fetch(BASE+'/review/'+id+'/approve',{method:'POST',headers:authHeaders(),body:JSON.stringify({editedText:editedText||null})});
  if(res.ok){
    allItems=allItems.filter(i=>i.id!==id);
    document.getElementById('card-'+id)?.remove();
    updateStatChips();updateCounts();
    toast('Approved — PR will be updated shortly.');
    if(getVisible().length===0)render();
  }else{
    if(ab)ab.disabled=false;if(rb)rb.disabled=false;
    const err=await res.json().catch(()=>({}));
    toast(err.error||'Approval failed — please try again.','error');
  }
}

function showRejectPanel(id){
  document.getElementById('reject-panel-'+id)?.classList.add('open');
  document.getElementById('btn-approve-'+id).disabled=true;
  document.getElementById('btn-reject-'+id).disabled=true;
  document.getElementById('reject-reason-'+id)?.focus();
}
function hideRejectPanel(id){
  document.getElementById('reject-panel-'+id)?.classList.remove('open');
  document.getElementById('btn-approve-'+id).disabled=false;
  document.getElementById('btn-reject-'+id).disabled=false;
}
async function confirmReject(id){
  const reason=(document.getElementById('reject-reason-'+id)?.value||'').trim();
  if(!reason){toast('Please enter a rejection reason.','error');return;}
  const confirmBtn=document.querySelector('#reject-panel-'+id+' .rv-btn-confirm-reject');
  if(confirmBtn)confirmBtn.disabled=true;
  const res=await fetch(BASE+'/review/'+id+'/reject',{method:'POST',headers:authHeaders(),body:JSON.stringify({reason})});
  if(res.ok){
    const item=allItems.find(i=>i.id===id);
    if(item){item.status='blocked';item.blockReason=reason;}
    updateStatChips();
    toast('Translation blocked.');
    render();
  }else{
    if(confirmBtn)confirmBtn.disabled=false;
    const err=await res.json().catch(()=>({}));
    toast(err.error||'Rejection failed — please try again.','error');
  }
}
loadReviews();
""".trimIndent()

// ─── Onboarding tour (shared across dashboard/projects/billing) ───────────────

private const val ONBOARDING_CSS = """
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

private val ONBOARDING_JS = """
(function(){
  if(window.Onboarding)return;
  var BASE='/transloom/api/onboarding';
  var state=null,page='dashboard',steps=[],idx=0,prevFocus=null;

  function authHeaders(){var t=localStorage.getItem('transloom_token');return t?{'Authorization':'Bearer '+t,'Content-Type':'application/json'}:{'Content-Type':'application/json'};}
  function fetchState(){return fetch(BASE+'/state',{headers:authHeaders()}).then(function(r){return r.ok?r.json():null;}).catch(function(){return null;});}
  function postSkip(){return fetch(BASE+'/skip',{method:'POST',headers:authHeaders()}).catch(function(){});}
  function postResume(){return fetch(BASE+'/resume',{method:'POST',headers:authHeaders()}).catch(function(){});}

  function host(){var h=document.getElementById('ob-host');if(!h){h=document.createElement('div');h.id='ob-host';document.body.appendChild(h);}return h;}
  function esc(s){var d=document.createElement('div');d.textContent=String(s==null?'':s);return d.innerHTML;}
  function planLabel(p){return p==='SOLO'?'Solo':p==='TEAM'?'Team':p==='ENTERPRISE'?'Enterprise':'Free';}

  function buildSteps(){
    var planChip='<span class="ob-chip">Plan: <strong style="margin-left:4px">'+esc(planLabel(state.plan))+'</strong></span>';
    var trialChip=state.inTrial?'<span class="ob-chip warn">Trial</span>':'';
    var meta=planChip+trialChip;
    if(page==='dashboard'){
      return [
        {
          eyebrow:'Welcome',
          title:'Welcome to Transloom',
          body:'You\'re all set on the <strong>'+esc(planLabel(state.plan))+'</strong> plan. In the next two steps we\'ll connect your GitHub repo and watch your first translation run.',
          meta:meta,
          anchor:null,
          primary:{label:'Get started',action:'next'},
          secondary:{label:'Skip',action:'skip'}
        },
        {
          eyebrow:'Step 1 of 2',
          title:'Connect your first repository',
          body:'Click <strong>+ New project</strong> to point Transloom at your GitHub repo. We\'ll auto-install the webhook so every push triggers translation.',
          anchor:'#qa-new-project',
          primary:{label:'Open projects',action:'navigate',href:'/transloom/projects?ob=connect'},
          secondary:{label:'Skip',action:'skip'}
        }
      ];
    }
    if(page==='projects'){
      // Resumes here when dashboard sent the user with ?ob=connect, or when they navigate directly while still SIGNED_UP.
      return [
        {
          eyebrow:'Step 2 of 2',
          title:'Create your project',
          body:'Fill in your GitHub repo URL (e.g. <strong>owner/repo</strong>), pick a branch and target languages. We\'ll handle the webhook.',
          anchor:'#new-proj-btn',
          primary:{label:'Open form',action:'click-anchor'},
          secondary:{label:'Skip',action:'skip'}
        }
      ];
    }
    return [];
  }

  function getAnchorRect(sel){
    if(!sel)return null;
    var el=document.querySelector(sel);
    if(!el)return null;
    el.scrollIntoView({block:'center',behavior:'smooth'});
    return el.getBoundingClientRect();
  }

  function positionPop(pop,rect){
    var pad=12,vw=window.innerWidth,vh=window.innerHeight,pw=pop.offsetWidth,ph=pop.offsetHeight;
    if(!rect){
      pop.style.left=Math.max(16,(vw-pw)/2)+'px';
      pop.style.top=Math.max(16,(vh-ph)/2)+'px';
      return;
    }
    var preferBelow=(rect.bottom+pad+ph)<=vh-8;
    var top=preferBelow?(rect.bottom+pad):Math.max(16,rect.top-pad-ph);
    var left=Math.min(Math.max(16,rect.left+(rect.width-pw)/2),vw-pw-16);
    pop.style.left=left+'px';pop.style.top=top+'px';
  }

  function render(){
    cleanup();
    var step=steps[idx];if(!step)return;
    var overlay=document.createElement('div');overlay.className='ob-overlay active';overlay.setAttribute('role','dialog');overlay.setAttribute('aria-modal','true');overlay.setAttribute('aria-live','polite');
    var shade=document.createElement('div');shade.className='ob-shade';shade.style.inset='0';overlay.appendChild(shade);
    var spot=document.createElement('div');spot.className='ob-spot';overlay.appendChild(spot);
    var pop=document.createElement('div');pop.className='ob-pop';
    var meta=step.meta?'<div class="ob-pop-meta">'+step.meta+'</div>':'';
    var progress=steps.length>1?(idx+1)+' / '+steps.length:'';
    pop.innerHTML=
      '<div class="ob-pop-eyebrow">'+esc(step.eyebrow||'')+'</div>'+
      '<div class="ob-pop-title">'+esc(step.title)+'</div>'+
      '<div class="ob-pop-body">'+step.body+'</div>'+
      meta+
      '<div class="ob-pop-actions">'+
        '<span class="ob-pop-progress">'+esc(progress)+'</span>'+
        '<div class="ob-pop-buttons">'+
          (step.secondary?'<button type="button" class="ob-pop-btn" data-act="'+esc(step.secondary.action)+'">'+esc(step.secondary.label)+'</button>':'')+
          (step.primary?'<button type="button" class="ob-pop-btn primary" data-act="'+esc(step.primary.action)+'">'+esc(step.primary.label)+'</button>':'')+
        '</div>'+
      '</div>';
    overlay.appendChild(pop);
    host().appendChild(overlay);

    var rect=getAnchorRect(step.anchor);
    if(rect){
      var pad=8;
      spot.style.left=(rect.left-pad)+'px';spot.style.top=(rect.top-pad)+'px';
      spot.style.width=(rect.width+pad*2)+'px';spot.style.height=(rect.height+pad*2)+'px';
      spot.classList.add('visible');
    }
    requestAnimationFrame(function(){positionPop(pop,rect);pop.classList.add('visible');});

    var btns=pop.querySelectorAll('button[data-act]');
    btns.forEach(function(b){b.addEventListener('click',function(){handleAction(b.getAttribute('data-act'),step);});});
    var first=pop.querySelector('button.primary')||btns[0];if(first){prevFocus=document.activeElement;first.focus();}

    overlay._onKey=function(e){
      if(e.key==='Escape'){e.preventDefault();skip();}
      else if(e.key==='Tab'){
        var nodes=Array.prototype.slice.call(btns);if(!nodes.length)return;
        var i=nodes.indexOf(document.activeElement);
        if(e.shiftKey){if(i<=0){e.preventDefault();nodes[nodes.length-1].focus();}}
        else{if(i===nodes.length-1){e.preventDefault();nodes[0].focus();}}
      }
    };
    document.addEventListener('keydown',overlay._onKey);
    overlay._onResize=function(){positionPop(pop,getAnchorRect(step.anchor));};
    window.addEventListener('resize',overlay._onResize);window.addEventListener('scroll',overlay._onResize,true);
  }

  function cleanup(){
    var h=document.getElementById('ob-host');if(!h)return;
    Array.prototype.slice.call(h.querySelectorAll('.ob-overlay')).forEach(function(o){
      if(o._onKey)document.removeEventListener('keydown',o._onKey);
      if(o._onResize){window.removeEventListener('resize',o._onResize);window.removeEventListener('scroll',o._onResize,true);}
      o.remove();
    });
    if(prevFocus&&prevFocus.focus){try{prevFocus.focus();}catch(_){}prevFocus=null;}
  }

  function handleAction(act,step){
    if(act==='next'){idx++;if(idx>=steps.length){finish();}else{render();}}
    else if(act==='skip'){skip();}
    else if(act==='navigate'){postSkip();window.location.href=step.primary.href;}
    else if(act==='click-anchor'){
      var el=step.anchor&&document.querySelector(step.anchor);
      cleanup();if(el)el.click();
    }
  }

  function finish(){cleanup();renderResumePill(false);}

  function skip(){postSkip();cleanup();renderResumePill(true);}

  function renderResumePill(show){
    var existing=document.getElementById('ob-resume-pill');
    if(!show){if(existing)existing.classList.remove('visible');return;}
    if(existing){existing.classList.add('visible');return;}
    var pill=document.createElement('div');pill.id='ob-resume-pill';pill.className='ob-resume-pill';
    pill.setAttribute('role','button');pill.setAttribute('tabindex','0');pill.setAttribute('aria-label','Resume setup');
    pill.innerHTML='<span class="ob-resume-dot"></span><span>Resume setup</span>';
    function resume(){postResume().then(function(){pill.classList.remove('visible');state.dismissed=false;steps=buildSteps();idx=0;render();});}
    pill.addEventListener('click',resume);
    pill.addEventListener('keydown',function(e){if(e.key==='Enter'||e.key===' '){e.preventDefault();resume();}});
    document.body.appendChild(pill);requestAnimationFrame(function(){pill.classList.add('visible');});
  }

  function shouldRun(){
    if(!state)return false;
    if(state.completed)return false;
    if(page==='dashboard')return state.step==='SIGNED_UP';
    if(page==='projects'){
      var fromDash=new URLSearchParams(window.location.search).get('ob')==='connect';
      return (state.step==='SIGNED_UP'&&!state.hasProject)||fromDash;
    }
    return false;
  }

  var Onboarding={
    boot:function(pageName){
      page=pageName||'dashboard';
      fetchState().then(function(s){
        if(!s)return;state=s;
        if(state.dismissed&&!state.completed){renderResumePill(true);return;}
        if(!shouldRun())return;
        steps=buildSteps();idx=0;render();
      });
    },
    refresh:function(){
      fetchState().then(function(s){if(!s)return;state=s;
        if(state.completed){cleanup();renderResumePill(false);return;}
        if(state.step!=='SIGNED_UP'){cleanup();}
      });
    },
    cleanup:cleanup
  };
  window.Onboarding=Onboarding;
})();
""".trimIndent()
