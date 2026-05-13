package com.transloom.routes

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*

fun Route.configurePortalRoutes() {
    route("/transloom") {
        get { call.respondHtml { landingPage() } }
        get("/app") { call.respondHtml { dashboardApp() } }
        get("/review-portal") { call.respondHtml { reviewPortal() } }
    }
}

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
button,.btn{cursor:pointer;border:none;font-family:inherit;font-size:14px;font-weight:500;border-radius:var(--radius-sm);transition:all .15s}
.btn-primary{background:var(--accent);color:#000;padding:10px 20px}
.btn-primary:hover{background:#00c98d;transform:translateY(-1px)}
.btn-ghost{background:transparent;color:var(--text-muted);padding:10px 20px;border:1px solid var(--border)}
.btn-ghost:hover{border-color:var(--accent);color:var(--accent)}
.card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:20px}
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

private fun FlowContent.stepCard(num: String, title: String, desc: String) {
    div("step") {
        div("step-num") { +num }
        div("step-body") {
            h3 { +title }
            p { +desc }
        }
    }
}

private fun FlowContent.featureCard(icon: String, title: String, desc: String) {
    div("feature-card card") {
        div("feature-icon") { +icon }
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
            attributes["onclick"] = "checkout('$planKey')"
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
        title { +"Transloom — i18n on autopilot" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        style { unsafe { +"$SHARED_CSS$LANDING_CSS" } }
    }
    body {
        nav {
            div("nav-inner") {
                div("logo") { span { +"●" }; +" Transloom" }
                div("nav-links") {
                    a("/transloom") { +"How it works" }
                    a("/transloom#features") { +"Features" }
                    a("/transloom/auth/github") { classes = setOf("btn", "btn-primary", "nav-cta"); +"Connect GitHub" }
                }
            }
        }

        section("hero") {
            div("hero-glow") {}
            div("hero-inner") {
                span("badge") { +"Now in Beta · Free to start" }
                h1("hero-title") {
                    +"Developer adds "; span("accent") { +"one string" }; +". We handle"; br {}; +"the rest."
                }
                p("hero-sub") {
                    +"Push to GitHub. Transloom detects new strings, translates them with Gemini AI,"; br {}
                    +"respects your glossary, and opens a pull request — automatically."
                }
                div("hero-actions") {
                    a("/transloom/auth/github") { classes = setOf("btn", "btn-primary", "hero-btn"); +"Connect with GitHub" }
                    a("/transloom#how") { classes = setOf("btn", "btn-ghost"); +"See how it works →" }
                }
                div("hero-lang-strip") {
                    listOf("🇪🇸 ES","🇫🇷 FR","🇩🇪 DE","🇯🇵 JA","🇰🇷 KO","🇨🇳 ZH","🇮🇳 HI","🇧🇷 PT","🇮🇹 IT","🇸🇦 AR")
                        .forEach { span("lang-chip") { +it } }
                }
            }
        }

        section("how-section") {
            id = "how"
            div("section-inner") {
                p("section-label") { +"HOW IT WORKS" }
                h2 { +"Zero config. Zero friction." }
                div("steps") {
                    stepCard("01","Push a commit","Add a new string key to strings.xml or Localizable.strings and push.")
                    stepCard("02","Transloom detects it","Our webhook picks up the diff, extracts new keys, and validates placeholder integrity.")
                    stepCard("03","AI translates","Gemini 2.5 Flash translates with your app tone, category, and custom glossary applied.")
                    stepCard("04","PR lands in your repo","A ready-to-merge pull request appears with all target languages — auto-approved or queued for review.")
                }
            }
        }

        section("features-section") {
            id = "features"
            div("section-inner") {
                p("section-label") { +"FEATURES" }
                h2 { +"Everything i18n. Nothing extra." }
                div("features-grid") {
                    featureCard("🧠","Context-aware AI","Gemini 2.5 Flash understands your app category and tone, producing natural-sounding translations.")
                    featureCard("🛡️","Placeholder guard","Automatic detection of %1\$s, %d, %@, &#8211; — bad translations are blocked before they ship.")
                    featureCard("📖","Glossary enforcement","Define brand terms once per language. Applied consistently across every string, every time.")
                    featureCard("🔍","Review portal","Flag anomalous translations for human review before they hit your main branch.")
                    featureCard("⚡","Translation memory","Identical strings reuse cached translations — faster throughput, lower API cost.")
                    featureCard("🌐","Android + iOS","Native support for strings.xml and Localizable.strings file formats out of the box.")
                }
            }
        }

        section("cta-section") {
            div("cta-inner") {
                h2 { +"Ready to ship in every language?" }
                p { +"Free tier includes 500 strings/month across 1 project." }
                a("/transloom/auth/github") { classes = setOf("btn","btn-primary","cta-btn"); +"Get started free" }
            }
        }

        footer {
            div("footer-inner") {
                span { +"© 2026 Transloom" }
                span("text-muted") { +"Built for developers who ship globally." }
            }
        }

        script { unsafe { +"const t=localStorage.getItem('transloom_token');if(t)window.location.href='/transloom/app';" } }
    }
}

private const val LANDING_CSS = """
nav{position:sticky;top:0;z-index:100;background:rgba(8,8,8,.85);backdrop-filter:blur(12px);border-bottom:1px solid var(--border)}
.nav-inner{max-width:1100px;margin:0 auto;padding:16px 24px;display:flex;align-items:center;justify-content:space-between}
.logo{display:flex;align-items:center;gap:8px;font-size:18px;font-weight:700;color:var(--text)}
.logo span{color:var(--accent)}
.nav-links{display:flex;align-items:center;gap:24px}
.nav-links a:not(.btn){color:var(--text-muted);font-size:14px;transition:color .15s}
.nav-links a:not(.btn):hover{color:var(--text)}
.nav-cta{padding:8px 16px!important;font-size:13px!important}
.hero{position:relative;overflow:hidden;padding:100px 24px 80px;text-align:center}
.hero-glow{position:absolute;top:-200px;left:50%;transform:translateX(-50%);width:600px;height:600px;background:radial-gradient(circle,rgba(0,229,160,.12) 0%,transparent 70%);pointer-events:none}
.hero-inner{max-width:760px;margin:0 auto;position:relative}
.hero-title{font-size:clamp(36px,6vw,62px);font-weight:800;line-height:1.1;letter-spacing:-1.5px;margin:20px 0}
.accent{color:var(--accent)}
.hero-sub{color:var(--text-muted);font-size:17px;line-height:1.7;margin-bottom:36px}
.hero-actions{display:flex;gap:12px;justify-content:center;flex-wrap:wrap;margin-bottom:48px}
.hero-btn{padding:13px 24px;font-size:15px}
.hero-lang-strip{display:flex;flex-wrap:wrap;gap:8px;justify-content:center}
.lang-chip{background:var(--surface);border:1px solid var(--border);border-radius:20px;padding:4px 12px;font-size:13px;color:var(--text-dim)}
section{padding:80px 24px}
.section-inner{max-width:1100px;margin:0 auto}
.section-label{font-size:11px;font-weight:700;letter-spacing:2px;color:var(--accent);margin-bottom:12px}
h2{font-size:clamp(26px,4vw,40px);font-weight:700;letter-spacing:-.5px;margin-bottom:48px}
.steps{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:24px}
.step{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:24px;display:flex;gap:16px}
.step-num{font-size:28px;font-weight:800;color:var(--accent);opacity:.3;line-height:1;min-width:36px}
.step-body h3{font-size:15px;font-weight:600;margin-bottom:6px}
.step-body p{font-size:13px;color:var(--text-muted);line-height:1.6}
.features-section{background:var(--surface2)}
.features-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:20px}
.feature-card{padding:24px}
.feature-icon{font-size:28px;margin-bottom:12px}
.feature-card h3{font-size:15px;font-weight:600;margin-bottom:8px}
.feature-card p{font-size:13px;color:var(--text-muted);line-height:1.6}
.cta-section{background:var(--accent-dim2);border-top:1px solid rgba(0,229,160,.15);border-bottom:1px solid rgba(0,229,160,.15);text-align:center}
.cta-inner{max-width:600px;margin:0 auto}
.cta-section h2{margin-bottom:12px}
.cta-section p{color:var(--text-muted);margin-bottom:32px}
.cta-btn{padding:14px 28px;font-size:16px}
footer{padding:32px 24px;border-top:1px solid var(--border)}
.footer-inner{max-width:1100px;margin:0 auto;display:flex;justify-content:space-between;align-items:center;font-size:13px;color:var(--text-muted)}
.text-muted{color:var(--text-muted)}
"""

// ─── Dashboard App ────────────────────────────────────────────────────────────

private fun HTML.dashboardApp() {
    head {
        title { +"Transloom — Dashboard" }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        style { unsafe { +"$SHARED_CSS$DASHBOARD_CSS" } }
    }
    body {
        div("app-layout") {
            aside("sidebar") {
                div("sidebar-logo") { span { +"●" }; +" Transloom" }
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
                                    id = "portal-link"
                                    attributes["onclick"] = "openPortal(); return false;"
                                    +"Manage subscription →"
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
                                planTier("Free","$0/mo","500 strings · 1 project · 3 languages","FREE")
                                planTier("Solo","$4.99/mo","5,000 strings · 3 projects · All languages","SOLO")
                                planTier("Team","$19.99/mo","Unlimited strings · 10 projects · All languages","TEAM")
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
                                +"🤖 Android"
                            }
                            label("plat-opt") {
                                input { type = InputType.radio; name = "platform"; id = "plat-ios"; value = "ios" }
                                +"🍎 iOS"
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
.sidebar-logo{font-size:17px;font-weight:700;padding:0 20px 20px;border-bottom:1px solid var(--border);margin-bottom:12px;color:var(--text)}
.sidebar-logo span{color:var(--accent)}
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
.invoice-row{display:grid;grid-template-columns:120px 1fr 100px 80px auto;gap:12px;align-items:center;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-sm);padding:10px 16px;font-size:13px}
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
"""

private val DASHBOARD_JS = """
const BASE='/transloom/api';
let token=localStorage.getItem('transloom_token');
const urlParams=new URLSearchParams(window.location.search);
const urlToken=urlParams.get('token');
if(urlToken){localStorage.setItem('transloom_token',urlToken);token=urlToken;history.replaceState({},'','/transloom/app');}
if(!token){window.location.href='/transloom';}

// Show billing success/cancel toasts
if(urlParams.get('billing')==='success')setTimeout(()=>toast('Subscription activated! 🎉'),300);
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
    list.innerHTML='<div class="empty-state">No projects yet.<br><small>Create your first project to start translating automatically.</small></div>';return;
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
          <span>${'$'}{esc(inv.id.slice(0,24))}...</span>
          <span>${'$'}{esc(inv.amount)}</span>
          <span class="invoice-status-${'$'}{esc(inv.status.toLowerCase())}">${'$'}{esc(inv.status)}</span>
          ${'$'}{inv.pdfUrl?'<a class="invoice-pdf" href="'+inv.pdfUrl+'" target="_blank" rel="noopener">PDF ↗</a>':'<span></span>'}
        </div>`).join('');
    }
  }
}

async function checkout(plan){
  // Fix 18: plan in request body, not query param
  const res=await api('/billing/checkout',{method:'POST',body:JSON.stringify({plan})});
  if(!res)return;
  if(res.ok){const data=await res.json();window.location.href=data.checkoutUrl;}
  else{const err=await res.json();toast(err.error||'Checkout failed','error');}
}

async function upgradePlan(){
  // Open the plans card by scrolling to billing section
  document.getElementById('billing').scrollIntoView({behavior:'smooth'});
}

async function openPortal(){
  const res=await api('/billing/portal');
  if(!res)return;
  if(res.ok){const data=await res.json();window.open(data.portalUrl,'_blank');}
  else{const err=await res.json();toast(err.error||'Cannot open portal','error');}
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
  if(res.ok){toast('Project created! Webhook auto-installed.');closeModal();loadProjects();loadStats();}
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
  if(items.length===0){list.innerHTML='<div class="empty-state">No translations to review. 🎉</div>';return;}
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
          ${'$'}{item.blockReason?'<div class="block-reason">⚠ '+esc(item.blockReason)+'</div>':''}
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
