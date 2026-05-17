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
        get { call.respondHtml { landingPage() } }
        get("/app") {
            // If the user is authenticated and has an incomplete checkout, route them back
            // to the payment page rather than silently landing them on the free-tier dashboard.
            val pendingPlan = call.request.cookies[PENDING_PLAN_COOKIE]
            val sessionUserId = call.sessionUserId(jwtSecret)
            if (!pendingPlan.isNullOrBlank() && sessionUserId != null) {
                call.respondRedirect("/transloom/billing/checkout?plan=$pendingPlan")
                return@get
            }
            call.respondHtml { dashboardApp() }
        }
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

private fun FlowContent.planTier(name: String, price: String, desc: String, planKey: String) {
    div("plan-tier") {
        div("plan-tier-info") {
            strong { +name }
            span("plan-price") { +price }
            span("plan-desc") { +desc }
        }
        button(classes = "btn btn-primary tier-btn") {
            attributes["onclick"] = "subscribe('$planKey')"
            +"Select"
        }
    }
}

private fun FlowContent.statCard(statId: String, label: String, value: String, yellow: Boolean = false) {
    div("stat-card card") {
        p("stat-label") { +label }
        p(if (yellow) "stat-value stat-yellow" else "stat-value") { id = statId; +value }
    }
}

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
                    +"Push to GitHub. Transloom detects new strings, translates them with Gemini AI,"; br {}
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

        section("stats-band") {
            div("stats-band-inner") {
                div("stat-tile fade-up") {
                    p("stat-num") { +"<60s" }
                    p("stat-cap") { +"from commit to pull request" }
                }
                div("stat-tile fade-up d1") {
                    p("stat-num") { +"0" }
                    p("stat-cap") { +"config files to write" }
                }
                div("stat-tile fade-up d2") {
                    p("stat-num") { +"100%" }
                    p("stat-cap") { +"placeholders preserved" }
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
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><path d="M15 2v2M9 2v2M15 20v2M9 20v2M2 15h2M2 9h2M20 15h2M20 9h2"/></svg>""","Context-aware AI","Gemini 2.5 Flash understands your app category and tone, producing natural-sounding translations.","fade-up")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><polyline points="9 12 11 14 15 10"/></svg>""","Placeholder guard","Automatic detection of %1\$s, %d, %@, &#8211; — bad translations are blocked before they ship.","fade-up d1")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>""","Glossary enforcement","Define brand terms once per language. Applied consistently across every string, every time.","fade-up d2")
                    featureCard("""<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>""","Review portal","Flag anomalous translations for human review before they hit your main branch.","fade-up d3")
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
                        span("trial-badge") { +"60-day free trial" }
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
                        a("/transloom/billing/start-subscription?plan=SOLO") { classes = setOf("pricing-cta", "accent"); +"Start 60-day free trial" }
                    }
                    div("pricing-card fade-up d2") {
                        span("trial-badge") { +"60-day free trial" }
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
                        a("/transloom/billing/start-subscription?plan=TEAM") { classes = setOf("pricing-cta", "outline"); +"Start 60-day free trial" }
                    }
                }
                p("pricing-note") { +"All paid plans include a 60-day free trial. No charge until the trial ends — cancel any time." }
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
.stats-band{padding:56px 24px;background:var(--surface2);border-top:1px solid var(--border);border-bottom:1px solid var(--border)}
.stats-band-inner{max-width:1100px;margin:0 auto;display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:24px;text-align:center}
.stat-tile{padding:8px 16px}
.stat-num{font-size:clamp(32px,4.5vw,42px);font-weight:800;background:linear-gradient(135deg,#00F5B0 0%,#00B894 100%);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;letter-spacing:-1.5px;line-height:1;margin-bottom:8px}
.stat-cap{font-size:13px;color:var(--text-muted);letter-spacing:.2px}
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
        script { src = "https://checkout.razorpay.com/v1/checkout.js" }
        style { unsafe { +"$SHARED_CSS$DASHBOARD_CSS" } }
    }
    body {
        div("app-layout") {
            aside("sidebar") {
                div("sidebar-logo brand") { unsafe { +LOGO_SVG }; span { +"Transloom" } }
                nav("sidebar-nav") {
                    a("/transloom/app") { classes = setOf("nav-item","active"); +"⬡ Dashboard" }
                    a("#") { attributes["onclick"] = "document.getElementById('projects').scrollIntoView({behavior:'smooth'});return false;"; classes = setOf("nav-item"); +"◻ Projects" }
                    a("/transloom/review-portal") {
                        classes = setOf("nav-item")
                        +"⚑ Review "
                        span("nav-badge review-badge") { id = "review-count"; +"0" }
                    }
                    a("#") { attributes["onclick"] = "document.getElementById('glossary').scrollIntoView({behavior:'smooth'});return false;"; classes = setOf("nav-item"); +"📖 Glossary" }
                    a("#") { attributes["onclick"] = "document.getElementById('billing').scrollIntoView({behavior:'smooth'});return false;"; classes = setOf("nav-item"); +"◈ Billing" }
                }
                div("sidebar-footer") {
                    div("user-chip") { id = "user-chip"; +"Loading..." }
                    button(classes = "btn btn-ghost logout-btn") {
                        attributes["onclick"] = "logout()"
                        +"Sign out"
                    }
                }
            }

            main("main-content") {
                div("stats-row") {
                    statCard("total-translated", "Strings translated", "—")
                    statCard("pending-review", "Pending review", "—", yellow = true)
                    statCard("active-langs", "Active languages", "—")
                    statCard("total-projects", "Projects", "—")
                }

                div("content-section") {
                    id = "projects"
                    div("section-header") {
                        h2 { +"Projects" }
                        button(classes = "btn btn-primary") {
                            attributes["onclick"] = "openNewProject()"
                            +"+ New project"
                        }
                    }
                    div("project-list") { id = "project-list"; div("empty-state") { +"Loading..." } }
                }

                div("content-section") {
                    id = "glossary"
                    div("section-header") {
                        h2 { +"Glossary" }
                    }
                    div("glossary-controls") {
                        id = "glossary-controls"
                        select {
                            id = "glossary-project-select"
                            attributes["onchange"] = "loadGlossary(this.value)"
                            option { value = ""; +"— Select a project —" }
                        }
                    }
                    div("glossary-add-row") {
                        id = "glossary-add-row"
                        attributes["style"] = "display:none"
                        input { type = InputType.text; id = "gl-lang"; placeholder = "Language code (e.g. es)" }
                        input { type = InputType.text; id = "gl-source"; placeholder = "Source term" }
                        input { type = InputType.text; id = "gl-target"; placeholder = "Translation" }
                        button(classes = "btn btn-primary") {
                            attributes["onclick"] = "addGlossaryEntry()"
                            +"Add"
                        }
                    }
                    div("glossary-list") { id = "glossary-list"; div("empty-state") { +"Select a project to view its glossary." } }
                }

                div("content-section") {
                    id = "billing"
                    div("section-header") { h2 { +"Billing" } }

                    // Plan card + usage
                    div("billing-grid") {
                        div("plan-card card") {
                            div("plan-header") {
                                div {
                                    p("plan-label") { +"Current Plan" }
                                    p("plan-name") { id = "plan-name"; +"—" }
                                }
                                button(classes = "btn btn-primary upgrade-btn") {
                                    id = "upgrade-btn"
                                    attributes["onclick"] = "upgradePlan()"
                                    +"Upgrade"
                                }
                            }
                            div("usage-section") {
                                div("usage-row") {
                                    span { +"Strings this month" }
                                    span { id = "usage-text"; +"—" }
                                }
                                div("usage-bar-track") { div("usage-bar-fill") { id = "usage-bar" } }
                            }
                            div("plan-actions") {
                                id = "plan-actions"
                                a("#") {
                                    id = "cancel-link"
                                    attributes["onclick"] = "cancelSubscription(); return false;"
                                    +"Cancel subscription →"
                                }
                            }
                            // F5: Historical Usage Container
                            div {
                                id = "historical-usage"
                                style = "margin-top: 24px; padding-top: 16px; border-top: 1px solid var(--border);"
                            }
                        }

                        div("plans-compare card") {
                            p("plan-label") { +"Plans" }
                            div("plan-tiers") {
                                planTier("Free","₹0/mo","500 strings · 1 project · 3 languages","FREE")
                                planTier("Solo","₹499/mo","5,000 strings · 3 projects · All languages","SOLO")
                                planTier("Team","₹1,999/mo","Unlimited strings · 10 projects · All languages","TEAM")
                            }
                        }
                    }

                    // Invoices
                    div("section-header invoice-header") {
                        h3("invoices-title") { +"Invoices" }
                    }
                    div("invoice-list") { id = "invoice-list" }
                }
            }
        }

        // New project modal
        div("modal-backdrop") {
            id = "modal-backdrop"
            div("modal card") {
                div("modal-header") {
                    h3 { +"New Project" }
                    button(classes = "modal-close") { attributes["onclick"] = "closeModal()"; +"✕" }
                }
                div("modal-body") {
                    p {
                        style = "font-size:13px;color:var(--text-muted);padding:10px 12px;background:var(--surface2);border-radius:6px;border:1px solid var(--border);line-height:1.5"
                        +"Transloom auto-installs a GitHub webhook on your repo. On every push, new strings are detected, translated by Gemini AI, and a pull request is opened automatically."
                    }
                    div("form-row") {
                        label { +"Project name" }
                        input { type = InputType.text; id = "proj-name"; placeholder = "My App" }
                    }
                    div("form-row") {
                        label { +"GitHub repo (owner/repo)" }
                        input { type = InputType.text; id = "proj-repo"; placeholder = "acme/my-app" }
                    }
                    // Fix 17: Platform selector drives source/target file path defaults
                    div("form-row") {
                        label { +"Platform" }
                        div("plat-toggle") {
                            label("plat-opt") {
                                input { type = InputType.radio; name = "platform"; id = "plat-android"; value = "android" }
                                span("plat-icon") {
                                    unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M5 16V8a7 7 0 0 1 14 0v8"/><line x1="2" y1="20" x2="22" y2="20"/><circle cx="9" cy="11" r=".8" fill="currentColor" stroke="none"/><circle cx="15" cy="11" r=".8" fill="currentColor" stroke="none"/><line x1="7" y1="5" x2="5.5" y2="3.5"/><line x1="17" y1="5" x2="18.5" y2="3.5"/></svg>""" }
                                }
                                +"Android"
                            }
                            label("plat-opt") {
                                input { type = InputType.radio; name = "platform"; id = "plat-ios"; value = "ios" }
                                span("plat-icon") {
                                    unsafe { +"""<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20.94c1.5 0 2.75 1.06 4 1.06 3 0 6-8 6-12.22A4.91 4.91 0 0 0 17 5c-2.22 0-4 1.44-5 2-1-.56-2.78-2-5-2a4.9 4.9 0 0 0-5 4.78C2 14 5 22 8 22c1.25 0 2.5-1.06 4-1.06Z"/><path d="M10 2c1 .5 2 2 2 5"/></svg>""" }
                                }
                                +"iOS"
                            }
                        }
                    }
                    div("form-row") {
                        label { +"Source strings file" }
                        input { type = InputType.text; id = "proj-source-path"; placeholder = "values/strings.xml" }
                        p("field-hint") { id = "source-hint"; +"Android: values/strings.xml · iOS: en.lproj/Localizable.strings" }
                    }
                    div("form-row two-col") {
                        div {
                            label { +"Watch branch" }
                            input { type = InputType.text; id = "proj-branch"; placeholder = "main"; value = "main" }
                        }
                        div {
                            label { +"Category" }
                            select { id = "proj-category"
                                option { value = "productivity"; +"Productivity" }
                                option { value = "gaming"; +"Gaming" }
                                option { value = "fintech"; +"Fintech" }
                                option { value = "social"; +"Social" }
                                option { value = "health"; +"Health" }
                                option { value = "ecommerce"; +"E-commerce" }
                            }
                        }
                    }
                    div("form-row") {
                        label { +"Tone" }
                        select { id = "proj-tone"
                            option { value = "professional"; +"Professional" }
                            option { value = "friendly"; +"Friendly" }
                            option { value = "casual"; +"Casual" }
                            option { value = "formal"; +"Formal" }
                        }
                    }
                    div("form-row") {
                        label { +"Target languages" }
                        div("lang-picker") {
                            mapOf("es" to "🇪🇸 Spanish","fr" to "🇫🇷 French","de" to "🇩🇪 German",
                                "ja" to "🇯🇵 Japanese","ko" to "🇰🇷 Korean","zh" to "🇨🇳 Chinese",
                                "pt" to "🇧🇷 Portuguese","it" to "🇮🇹 Italian","hi" to "🇮🇳 Hindi","ar" to "🇸🇦 Arabic"
                            ).forEach { (code, label) ->
                                label("lang-toggle") {
                                    input { type = InputType.checkBox; id = "lang-$code"; value = code }
                                    +label
                                }
                            }
                        }
                    }
                }
                div("modal-footer") {
                    button(classes = "btn btn-ghost") { attributes["onclick"] = "closeModal()"; +"Cancel" }
                    button(classes = "btn btn-primary") { attributes["onclick"] = "createProject()"; +"Create project" }
                }
            }
        }

        div("toast") { id = "toast" }
        script { unsafe { +DASHBOARD_JS } }
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
.stats-row{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:32px}
.stat-card{padding:20px}
.stat-label{font-size:12px;color:var(--text-muted);margin-bottom:6px}
.stat-value{font-size:30px;font-weight:700;color:var(--accent);transition:opacity 0.2s}
.stat-value.loading{opacity:0.5;animation:pulse 1.5s infinite}
@keyframes pulse{0%{opacity:0.3}50%{opacity:0.7}100%{opacity:0.3}}
.stat-yellow{color:var(--yellow)}
.content-section{margin-bottom:40px}
.section-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}
.section-header h2{font-size:18px;font-weight:600}
.project-list{display:flex;flex-direction:column;gap:12px}
.project-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:18px 20px;display:flex;align-items:center;justify-content:space-between;transition:border-color .15s}
.project-card:hover{border-color:rgba(0,229,160,.3)}
.project-info h3{font-size:15px;font-weight:600;margin-bottom:3px}
.project-meta{font-size:12px;color:var(--text-muted);display:flex;gap:12px}
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
"""

private val DASHBOARD_JS = """
const BASE='/transloom/api';
let token=localStorage.getItem('transloom_token');
const urlParams=new URLSearchParams(window.location.search);
const urlToken=urlParams.get('token');
const planFromUrl=urlParams.get('plan');
if(urlToken){localStorage.setItem('transloom_token',urlToken);token=urlToken;history.replaceState({},'','/transloom/app');}
if(!token){window.location.href='/transloom';}
if(planFromUrl&&planFromUrl!=='FREE'){setTimeout(()=>subscribe(planFromUrl),600);}

// Show billing success/cancel toasts
if(urlParams.get('billing')==='success')setTimeout(()=>toast('Subscription activated'),300);
if(urlParams.get('billing')==='cancelled')setTimeout(()=>toast('Checkout cancelled','error'),300);

function authHeaders(){return{'Authorization':'Bearer '+token,'Content-Type':'application/json'};}
async function api(path,opts={}){
  const res=await fetch(BASE+path,{...opts,headers:{...authHeaders(),...(opts.headers||{})}});
  if(res.status===401){logout();return null;}return res;
}
function logout(){localStorage.removeItem('transloom_token');window.location.href='/transloom';}
function toast(msg,type='success'){const el=document.getElementById('toast');el.textContent=msg;el.className='toast show '+type;setTimeout(()=>el.className='toast',2800);}
function jwtPayload(t){try{return JSON.parse(atob(t.split('.')[1]));}catch{return{};}}
function esc(s){if(!s)return '';const d=document.createElement('div');d.textContent=String(s);return d.innerHTML;}

async function loadStats(){
  document.querySelectorAll('.stat-value').forEach(el=>el.classList.add('loading'));
  const res=await api('/dashboard/stats');
  if(!res||!res.ok){toast('Failed to load stats','error');document.querySelectorAll('.stat-value').forEach(el=>el.classList.remove('loading'));return;}
  const s=await res.json();
  const elTotal=document.getElementById('total-translated');elTotal.textContent=s.totalStringsTranslated??0;elTotal.classList.remove('loading');
  const elReview=document.getElementById('pending-review');elReview.textContent=s.pendingReview??0;elReview.classList.remove('loading');
  const elLangs=document.getElementById('active-langs');elLangs.textContent=s.activeLanguages??0;elLangs.classList.remove('loading');
  const elProj=document.getElementById('total-projects');elProj.textContent=s.totalProjects??0;elProj.classList.remove('loading');
  const badge=document.getElementById('review-count');
  if(s.pendingReview>0){badge.textContent=s.pendingReview;badge.style.display='inline';}
}

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

async function loadBilling(){
  const [subRes,usageRes,invRes]=await Promise.all([
    api('/billing/subscription'),api('/billing/usage'),api('/billing/invoices')
  ]);
  if(!subRes||!subRes.ok||!usageRes||!usageRes.ok){toast('Failed to load billing info','error');return;}

  const sub=await subRes.json();
  const usage=await usageRes.json();

  // Fix 23: use displayName from billing/subscription; stats currentPlan returns enum key
  document.getElementById('plan-name').textContent=sub.displayName||sub.plan;

  // Show/hide upgrade button
  const upgradeBtn=document.getElementById('upgrade-btn');
  if(sub.plan==='TEAM'||sub.plan==='ENTERPRISE'){upgradeBtn.style.display='none';}

  // Usage bar
  if(usage.stringLimit){
    const pct=Math.min(100,Math.round((usage.stringsTranslated/usage.stringLimit)*100));
    document.getElementById('usage-bar').style.width=pct+'%';
    document.getElementById('usage-text').textContent=usage.stringsTranslated+' / '+usage.stringLimit;
    if(pct>80)document.getElementById('usage-bar').style.background='var(--yellow)';
  } else {
    document.getElementById('usage-text').textContent=usage.stringsTranslated+' (unlimited)';
    document.getElementById('usage-bar').style.width='100%';
    document.getElementById('usage-bar').style.background='var(--accent-dim)';
  }

  // Cancel notice
  if(sub.cancelAtPeriodEnd&&sub.currentPeriodEnd){
    document.getElementById('plan-actions').innerHTML='<span style="color:var(--yellow);font-size:12px">Cancels on ${'$'}{sub.currentPeriodEnd}</span>';
  }

  // F5: Historical usage UI
  const uh=document.getElementById('historical-usage');
  if(uh) {
      if(usage.history && usage.history.length>0) {
          uh.innerHTML = '<div style="font-size:11px;color:var(--text-muted);margin-bottom:12px;font-weight:600;letter-spacing:1px">HISTORICAL USAGE</div>' + 
            usage.history.map(h=>`<div style="display:flex;justify-content:space-between;padding:4px 0;font-size:13px">
              <span>${'$'}{esc(h.month)}</span><span style="font-family:monospace">${'$'}{h.count} strings</span>
            </div>`).join('');
      } else {
          uh.innerHTML = '';
      }
  }

  // Invoices
  if(invRes){
    const invData=await invRes.json();
    const list=document.getElementById('invoice-list');
    if(!invData.invoices||invData.invoices.length===0){
      list.innerHTML='<div style="font-size:13px;color:var(--text-muted);padding:12px 0">No invoices yet.</div>';
    } else {
      list.innerHTML=invData.invoices.map(inv=>`
        <div class="invoice-row">
          <span>${'$'}{esc(inv.date)}</span>
          <span style="font-family:monospace;font-size:11px">${'$'}{esc(inv.id)}</span>
          <span>${'$'}{esc(inv.amount)}</span>
          <span class="invoice-status-${'$'}{esc(inv.status.toLowerCase())}">${'$'}{esc(inv.status)}</span>
        </div>`).join('');
    }
  }
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
    description:data.plan+' plan · 60-day free trial',
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

async function upgradePlan(){
  // Open the plans card by scrolling to billing section
  document.getElementById('billing').scrollIntoView({behavior:'smooth'});
}

async function cancelSubscription(){
  if(!confirm('Cancel your subscription? It will remain active until the end of the current billing period.'))return;
  const res=await api('/billing/cancel',{method:'POST'});
  if(!res)return;
  if(res.ok){toast('Subscription will cancel at end of period');loadBilling();}
  else{const err=await res.json();toast(err.error||'Cancel failed','error');}
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
document.getElementById('plat-android').checked=true;
document.getElementById('proj-source-path').value='values/strings.xml';

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
  if(res.ok){toast('Project created! Now push a new string to trigger your first translation.');closeModal();loadProjects();loadStats();}
  else{const err=await res.json();toast(err.error||'Failed to create project','error');}
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

const payload=jwtPayload(token);
document.getElementById('user-chip').textContent=payload.username?'@'+payload.username:'Logged in';
loadStats();loadProjects();loadBilling();
""".trimIndent()

// ─── Review Portal ────────────────────────────────────────────────────────────

private fun HTML.reviewPortal() {
    head {
        title { +"Transloom — Review Portal" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        favicon()
        style { unsafe { +"$SHARED_CSS$REVIEW_CSS" } }
    }
    body {
        div("review-layout") {
            div("review-header") {
                a("/transloom/app") { classes = setOf("back-link"); +"← Dashboard" }
                h1 { +"Translation Review" }
                p("header-sub") { +"Approve or reject flagged translations before they merge." }
            }
            div("filters-bar") {
                button(classes = "filter-btn active") { attributes["onclick"] = "filterStatus('all',this)"; +"All" }
                button(classes = "filter-btn") { attributes["onclick"] = "filterStatus('review',this)"; +"Pending" }
                button(classes = "filter-btn") { attributes["onclick"] = "filterStatus('blocked',this)"; +"Blocked" }
                span("filter-count") { id = "item-count" }
            }
            div("review-list") { id = "review-list"; div("empty-state") { +"Loading..." } }
        }
        div("toast") { id = "toast" }
        script { unsafe { +REVIEW_JS } }
    }
}

private const val REVIEW_CSS = """
.review-layout{max-width:900px;margin:0 auto;padding:32px 24px}
.review-header{margin-bottom:28px}
.back-link{font-size:13px;color:var(--text-muted);margin-bottom:12px;display:inline-block}
.back-link:hover{color:var(--accent)}
.review-header h1{font-size:26px;font-weight:700;margin-bottom:6px}
.header-sub{color:var(--text-muted);font-size:14px}
.filters-bar{display:flex;align-items:center;gap:8px;margin-bottom:20px}
.filter-btn{padding:6px 14px;border-radius:20px;background:var(--surface);border:1px solid var(--border);color:var(--text-muted);font-size:13px;cursor:pointer;transition:all .12s}
.filter-btn.active{background:var(--accent-dim);border-color:var(--accent);color:var(--accent)}
.filter-count{font-size:12px;color:var(--text-muted);margin-left:auto}
.review-list{display:flex;flex-direction:column;gap:16px}
.review-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);overflow:hidden}
.review-card.status-review{border-left:3px solid var(--yellow)}
.review-card.status-blocked{border-left:3px solid var(--red)}
.review-card-header{padding:14px 18px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--border);background:var(--surface2)}
.review-key{font-size:12px;font-family:monospace;color:var(--accent)}
.review-meta{font-size:12px;color:var(--text-muted);display:flex;gap:12px;align-items:center}
.status-pill{font-size:11px;font-weight:600;border-radius:20px;padding:2px 10px}
.pill-review{background:rgba(250,173,20,.12);color:var(--yellow);border:1px solid rgba(250,173,20,.3)}
.pill-blocked{background:rgba(255,77,79,.12);color:var(--red);border:1px solid rgba(255,77,79,.3)}
.review-body{display:grid;grid-template-columns:1fr 1fr}
.source-pane,.target-pane{padding:16px 18px}
.source-pane{border-right:1px solid var(--border)}
.pane-label{font-size:11px;font-weight:600;letter-spacing:1px;color:var(--text-muted);margin-bottom:8px}
.source-text{font-size:14px;color:var(--text-dim);line-height:1.6}
.translation-input{font-size:14px;line-height:1.6;resize:vertical;min-height:60px}
.block-reason{font-size:12px;color:var(--red);background:rgba(255,77,79,.08);border:1px solid rgba(255,77,79,.2);border-radius:var(--radius-sm);padding:8px 12px;margin-top:8px}
.review-actions{padding:12px 18px;border-top:1px solid var(--border);display:flex;justify-content:flex-end;gap:8px;align-items:center}
.btn-approve{background:var(--accent);color:#000;padding:7px 16px;font-size:13px;border-radius:var(--radius-sm);border:none;cursor:pointer;transition:all .15s}
.btn-reject{background:rgba(255,77,79,.12);color:var(--red);border:1px solid rgba(255,77,79,.3);padding:7px 16px;font-size:13px;border-radius:var(--radius-sm);cursor:pointer;transition:all .15s}
.btn-approve:hover:not(:disabled){background:#00c98d}
.btn-reject:hover:not(:disabled){background:rgba(255,77,79,.2)}
.btn-approve:disabled,.btn-reject:disabled{opacity:.45;cursor:not-allowed}
.reject-panel{display:none;padding:12px 18px;border-top:1px solid var(--border);background:rgba(255,77,79,.04)}
.reject-panel.open{display:block}
.reject-panel textarea{min-height:52px;resize:vertical;font-size:13px;margin-bottom:8px;border-color:rgba(255,77,79,.4)}
.reject-panel-actions{display:flex;gap:8px;justify-content:flex-end}
.btn-confirm-reject{background:var(--red);color:#fff;border:none;padding:6px 14px;font-size:13px;border-radius:var(--radius-sm);cursor:pointer}
.btn-confirm-reject:hover{background:#e03e40}
.btn-cancel-reject{background:transparent;color:var(--text-muted);border:1px solid var(--border);padding:6px 14px;font-size:13px;border-radius:var(--radius-sm);cursor:pointer}
.btn-cancel-reject:hover{color:var(--text);border-color:var(--text-muted)}
"""

private val REVIEW_JS = """
const BASE='/transloom/api';
let token=localStorage.getItem('transloom_token');
if(!token){window.location.href='/transloom';}
function authHeaders(){return{'Authorization':'Bearer '+token,'Content-Type':'application/json'};}
function toast(msg,type='success'){const el=document.getElementById('toast');el.textContent=msg;el.className='toast show '+type;setTimeout(()=>el.className='toast',2800);}
function esc(s){if(!s)return '';const d=document.createElement('div');d.textContent=String(s);return d.innerHTML;}

let allItems=[];let currentFilter='all';

async function loadReviews(){
  const res=await fetch(BASE+'/review',{headers:authHeaders()});
  if(!res.ok){document.getElementById('review-list').innerHTML='<div class="empty-state">Failed to load reviews.</div>';return;}
  const data=await res.json();allItems=data.pending_reviews||[];render();
}

function filterStatus(status,btn){
  currentFilter=status;
  document.querySelectorAll('.filter-btn').forEach(b=>b.classList.remove('active'));
  btn.classList.add('active');render();
}

function render(){
  const items=currentFilter==='all'?allItems:allItems.filter(i=>i.status===currentFilter);
  document.getElementById('item-count').textContent=items.length+' item'+(items.length!==1?'s':'');
  const list=document.getElementById('review-list');
  if(items.length===0){list.innerHTML='<div class="empty-state"><svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="display:inline;vertical-align:-4px;margin-right:8px;color:var(--accent)"><polyline points="20 6 9 17 4 12"/></svg>All caught up — no translations to review.</div>';return;}
  const pillClass=s=>s==='review'?'pill-review':'pill-blocked';
  const pillLabel=s=>s==='review'?'Pending':'Blocked';
  list.innerHTML=items.map(item=>`
    <div class="review-card status-${'$'}{esc(item.status)}" id="card-${'$'}{esc(item.id)}">
      <div class="review-card-header">
        <span class="review-key">${'$'}{esc(item.stringKey)}</span>
        <div class="review-meta">
          <span>${'$'}{esc(item.projectName)}</span>
          <span>${'$'}{esc(item.targetLanguage)}${'$'}{item.targetRegion?' ('+esc(item.targetRegion)+')':''}</span>
          <span class="status-pill ${'$'}{pillClass(item.status)}">${'$'}{pillLabel(item.status)}</span>
        </div>
      </div>
      <div class="review-body">
        <div class="source-pane"><div class="pane-label">SOURCE (EN)</div><div class="source-text">${'$'}{esc(item.sourceText)}</div></div>
        <div class="target-pane">
          <div class="pane-label">TRANSLATION <span style="color:var(--text-muted);font-weight:400;font-size:10px;letter-spacing:0">(editable — your changes will be committed)</span></div>
          <textarea class="translation-input" id="trans-${'$'}{esc(item.id)}">${'$'}{esc(item.translatedText)}</textarea>
          ${'$'}{item.blockReason?'<div class="block-reason"><svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" style="display:inline;vertical-align:-2px;margin-right:5px"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>'+esc(item.blockReason)+'</div>':''}
        </div>
      </div>
      <div class="review-actions">
        <button class="btn-reject" id="btn-reject-${'$'}{esc(item.id)}" onclick="showRejectPanel('${'$'}{esc(item.id)}')">Reject</button>
        <button class="btn-approve" id="btn-approve-${'$'}{esc(item.id)}" onclick="approve('${'$'}{esc(item.id)}')">✓ Approve</button>
      </div>
      <div class="reject-panel" id="reject-panel-${'$'}{esc(item.id)}">
        <div class="pane-label" style="margin-bottom:8px">REJECTION REASON</div>
        <textarea id="reject-reason-${'$'}{esc(item.id)}" placeholder="Describe why this translation needs to be redone…"></textarea>
        <div class="reject-panel-actions">
          <button class="btn-cancel-reject" onclick="hideRejectPanel('${'$'}{esc(item.id)}')">Cancel</button>
          <button class="btn-confirm-reject" onclick="confirmReject('${'$'}{esc(item.id)}')">Confirm Rejection</button>
        </div>
      </div>
    </div>`).join('');
}

// Fix U8: send the reviewer's (possibly edited) textarea value as editedText.
// Fix U1: disable buttons while the request is in flight to prevent double-submit / duplicate PRs.
async function approve(id){
  const approveBtn=document.getElementById('btn-approve-'+id);
  const rejectBtn=document.getElementById('btn-reject-'+id);
  if(approveBtn)approveBtn.disabled=true;
  if(rejectBtn)rejectBtn.disabled=true;
  const editedText=(document.getElementById('trans-'+id)?.value||'').trim();
  const res=await fetch(BASE+'/review/'+id+'/approve',{
    method:'POST',headers:authHeaders(),
    body:JSON.stringify({editedText:editedText||null})
  });
  if(res.ok){
    allItems=allItems.filter(i=>i.id!==id);
    document.getElementById('card-'+id)?.remove();
    toast('Approved!');
    if(allItems.length===0)render();
  } else {
    if(approveBtn)approveBtn.disabled=false;
    if(rejectBtn)rejectBtn.disabled=false;
    const err=await res.json().catch(()=>({}));
    toast(err.error||'Approval failed — please try again','error');
  }
}

// Fix U2: replace window.prompt() with an inline rejection panel.
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
  if(!reason){toast('Please enter a rejection reason','error');return;}
  const confirmBtn=document.querySelector('#reject-panel-'+id+' .btn-confirm-reject');
  if(confirmBtn)confirmBtn.disabled=true;
  const res=await fetch(BASE+'/review/'+id+'/reject',{method:'POST',headers:authHeaders(),body:JSON.stringify({reason})});
  if(res.ok){
    const item=allItems.find(i=>i.id===id);
    if(item){item.status='blocked';item.blockReason=reason;}
    toast('Translation blocked.');
    render();
  } else {
    if(confirmBtn)confirmBtn.disabled=false;
    const err=await res.json().catch(()=>({}));
    toast(err.error||'Rejection failed — please try again','error');
  }
}
loadReviews();
""".trimIndent()
