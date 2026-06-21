package com.syncling

import com.syncling.routes.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

/**
 * Local-only dashboard preview. Boots a tiny Ktor server on PREVIEW_PORT
 * (default 8083) that mounts every authenticated portal page and backs each
 * client-side API call with realistic mock data — no DB, Redis, or auth token
 * required. All write endpoints (POST/PUT/DELETE) return a no-op 200 so
 * buttons and modals render without triggering side effects.
 *
 *   ./gradlew :syncling:previewDashboard
 *   open http://localhost:8083/app
 *
 * Pages available:
 *   /app                 — Dashboard (stats, pipeline runs, CDN widget)
 *   /projects            — Projects list + create/edit drawer
 *   /billing             — Billing plan, usage meters, invoices
 *   /billing/analytics   — Cost analytics, locale/run/member charts
 *   /review-portal       — Translation review queue
 *   /members             — Team members
 *   /tokens              — API tokens
 */
fun main() {
    val port = System.getenv("PREVIEW_PORT")?.toIntOrNull() ?: 8083

    // A fake JWT whose payload atob()-decodes to a recognisable preview user
    // so the sidebar user-chip shows something meaningful.
    val payloadJson = """{"userId":"preview","githubId":1,"username":"preview-user","email":"preview@syncling.space","aud":"syncling-app","iss":"syncling-backend"}"""
    val payloadB64 = java.util.Base64.getEncoder().encodeToString(payloadJson.toByteArray())
    val fakeJwt = "eyJhbGciOiJub25lIn0.$payloadB64.preview-sig"

    println("→ syncling dashboard preview at http://localhost:$port/app")

    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        install(ContentNegotiation) { json(Json { encodeDefaults = true; ignoreUnknownKeys = true }) }

        routing {

            // ── Static assets (CSS/JS bundles) ────────────────────────────────
            staticResources("/transloom/static", "static")

            // ── Favicons & manifest ───────────────────────────────────────────
            get("/favicon.svg") {
                call.respondRedirect("/transloom/static/favicon.svg", permanent = false)
            }

            // ── Auth shims (no real OAuth in preview) ─────────────────────────
            get("/auth/github") { call.respondRedirect("/app") }
            get("/auth/logout") { call.respondRedirect("/app") }
            get("/welcome") { call.respondRedirect("/app") }
            get("/billing/checkout") { call.respondRedirect("/billing") }
            get("/invite/{...}") { call.respondRedirect("/app") }

            // ── Portal pages — serve without any session check ────────────────
            fun ApplicationCall.setBootstrapCookie() =
                response.cookies.append(
                    Cookie("tl_token_bootstrap", fakeJwt, path = "/", maxAge = 3600, httpOnly = false, secure = false)
                )

            get("/app") {
                call.setBootstrapCookie()
                call.respondHtml { dashboardApp() }
            }
            get("/projects") {
                call.setBootstrapCookie()
                call.respondHtml { projectsApp() }
            }
            get("/billing") {
                call.setBootstrapCookie()
                call.respondHtml { billingApp() }
            }
            get("/billing/analytics") {
                call.setBootstrapCookie()
                call.respondHtml { billingAnalyticsApp() }
            }
            get("/review-portal") {
                call.setBootstrapCookie()
                call.respondHtml { reviewPortal() }
            }
            get("/members") {
                call.setBootstrapCookie()
                call.respondHtml { membersApp(projectId = null) }
            }
            get("/members/{projectId}") {
                call.setBootstrapCookie()
                call.respondHtml { membersApp(projectId = call.parameters["projectId"]) }
            }
            get("/tokens") {
                call.setBootstrapCookie()
                call.respondHtml { tokensApp(isPaid = true) }
            }

            // ── Mock API routes ───────────────────────────────────────────────

            // Dashboard stats
            get("/api/dashboard/stats") {
                call.respondText(
                    """{"totalStringsTranslated":12847,"pendingReview":3,"blockedCount":1,"activeLanguages":5,"totalProjects":2,"currentPlan":"SOLO","currentPlanDisplay":"PRO"}""",
                    ContentType.Application.Json
                )
            }

            // Pipeline insights tile
            get("/api/dashboard/insights") {
                call.respondText(
                    """{"windowDays":30,"runs":42,"memoryHitRate":0.63,"geminiSpendUsd":4.18,"costSavedUsd":7.12,"avgRunSeconds":48.5,"reviewerEdits":6}""",
                    ContentType.Application.Json
                )
            }

            // CDN status for dashboard widget
            get("/api/dashboard/cdn-status") {
                call.respondText(
                    """{"publishes":[{"projectId":"proj-001","projectName":"my-android-app","bundleVersion":"a1b2c3d4e5f6","publishedAt":${System.currentTimeMillis() - 3_600_000},"locales":["hi","es","fr","de","ja"],"status":"success"},{"projectId":"proj-002","projectName":"ios-translations","bundleVersion":"7f8e9d0c1a2b","publishedAt":${System.currentTimeMillis() - 86_400_000},"locales":["hi","es","fr"],"status":"success"}]}""",
                    ContentType.Application.Json
                )
            }

            // Pipeline runs (for dashboard activity feed)
            get("/api/pipeline/runs") {
                val now = System.currentTimeMillis()
                call.respondText(
                    """{"runs":[
                      {"runId":"run-003","repo":"myorg/my-android-app","branch":"main","commitShort":"9fa31c2","startedAt":${now - 1_800_000},"finishedAt":${now - 1_680_000},"prUrl":"https://github.com/myorg/my-android-app/pull/44","error":null,"projectId":"proj-001","surfaceSkipped":0,"triggeredByLabel":"ankush","steps":[{"id":"WEBHOOK_RECEIVED","label":"Push detected","status":"done","detail":null},{"id":"FETCHING_STRINGS","label":"Reading source file","status":"done","detail":"12 strings found"},{"id":"DETECTING_CHANGES","label":"Scanning for changes","status":"done","detail":"2 changed"},{"id":"BILLING_CHECK","label":"Checking plan limits","status":"done","detail":null},{"id":"TRANSLATING","label":"Translating strings","status":"done","detail":"2 strings × 5 languages"},{"id":"CREATING_PR","label":"Opening pull request","status":"done","detail":"PR #44"},{"id":"CDN_PUBLISH","label":"Publishing to CDN","status":"done","detail":"5 locales published"}],"locales":[{"code":"hi","name":"Hindi","status":"done","done":2,"total":2},{"code":"es","name":"Spanish","status":"done","done":2,"total":2},{"code":"fr","name":"French","status":"done","done":2,"total":2},{"code":"de","name":"German","status":"done","done":2,"total":2},{"code":"ja","name":"Japanese","status":"done","done":2,"total":2}],"progressDone":10,"progressTotal":10},
                      {"runId":"run-002","repo":"myorg/my-android-app","branch":"main","commitShort":"c7d4e1f","startedAt":${now - 86_400_000},"finishedAt":${now - 86_280_000},"prUrl":"https://github.com/myorg/my-android-app/pull/43","error":null,"projectId":"proj-001","surfaceSkipped":5,"triggeredByLabel":"ankush","steps":[{"id":"WEBHOOK_RECEIVED","label":"Push detected","status":"done","detail":null},{"id":"FETCHING_STRINGS","label":"Reading source file","status":"done","detail":"30 strings found"},{"id":"DETECTING_CHANGES","label":"Scanning for changes","status":"done","detail":"8 changed (5 surface rewrites skipped)"},{"id":"BILLING_CHECK","label":"Checking plan limits","status":"done","detail":null},{"id":"TRANSLATING","label":"Translating strings","status":"done","detail":"3 strings × 5 languages"},{"id":"CREATING_PR","label":"Opening pull request","status":"done","detail":"PR #43"},{"id":"CDN_PUBLISH","label":"Publishing to CDN","status":"done","detail":"5 locales published"}],"locales":[{"code":"hi","name":"Hindi","status":"done","done":3,"total":3},{"code":"es","name":"Spanish","status":"done","done":3,"total":3},{"code":"fr","name":"French","status":"done","done":3,"total":3},{"code":"de","name":"German","status":"done","done":3,"total":3},{"code":"ja","name":"Japanese","status":"done","done":3,"total":3}],"progressDone":15,"progressTotal":15},
                      {"runId":"run-001","repo":"myorg/ios-translations","branch":"release/2.4","commitShort":"b3e90a1","startedAt":${now - 172_800_000},"finishedAt":${now - 172_680_000},"prUrl":null,"error":"GitHub access lost — re-connect GitHub to resume automatic translations.","projectId":"proj-002","surfaceSkipped":0,"triggeredByLabel":"external","steps":[{"id":"WEBHOOK_RECEIVED","label":"Push detected","status":"done","detail":null},{"id":"FETCHING_STRINGS","label":"Reading source file","status":"error","detail":"GitHub access lost — re-connect GitHub to resume automatic translations."},{"id":"DETECTING_CHANGES","label":"Scanning for changes","status":"skipped","detail":null},{"id":"BILLING_CHECK","label":"Checking plan limits","status":"skipped","detail":null},{"id":"TRANSLATING","label":"Translating strings","status":"skipped","detail":null},{"id":"CREATING_PR","label":"Opening pull request","status":"skipped","detail":null},{"id":"CDN_PUBLISH","label":"Publishing to CDN","status":"skipped","detail":null}],"locales":[],"progressDone":0,"progressTotal":0}
                    ]}""".trimMargin(),
                    ContentType.Application.Json
                )
            }

            // SSE pipeline events — immediately close so the JS reconnect loop
            // stays quiet; it backs off to 30 s between attempts so console noise is minimal.
            get("/api/pipeline/events") {
                call.response.header("Cache-Control", "no-cache")
                call.respondTextWriter(ContentType.parse("text/event-stream")) {
                    write(": preview-noop\n\n")
                    flush()
                }
            }

            // Billing
            get("/api/billing/subscription") {
                val periodEnd = java.time.LocalDate.now().plusMonths(1)
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
                call.respondText(
                    """{"plan":"SOLO","displayName":"PRO","status":"active","currentPeriodEnd":"$periodEnd","cancelAtPeriodEnd":false,"maxProjects":5,"inTrial":false,"trialEndsAt":null}""",
                    ContentType.Application.Json
                )
            }
            get("/api/billing/usage") {
                val prevMonth = java.time.YearMonth.now().minusMonths(1)
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                val twoMonthsAgo = java.time.YearMonth.now().minusMonths(2)
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                call.respondText(
                    """{"stringsTranslated":3421,"stringLimit":15000,"projectsUsed":2,"history":[{"yearMonth":"$prevMonth","stringsTranslated":4892},{"yearMonth":"$twoMonthsAgo","stringsTranslated":3156}]}""",
                    ContentType.Application.Json
                )
            }
            get("/api/billing/invoices") {
                call.respondText(
                    """{"invoices":[{"id":"inv_may26","date":"May 1, 2026","amount":"₹499","status":"paid"},{"id":"inv_apr26","date":"Apr 1, 2026","amount":"₹499","status":"paid"},{"id":"inv_mar26","date":"Mar 1, 2026","amount":"₹499","status":"paid"}]}""",
                    ContentType.Application.Json
                )
            }

            // Projects
            get("/api/projects") {
                call.respondText(
                    """{"projects":[{"id":"proj-001","name":"my-android-app","githubRepo":"myorg/my-android-app","watchBranch":"main","sourceFilePaths":["app/src/main/res/values/strings.xml"],"category":"mobile","tone":"professional","targetCount":5},{"id":"proj-002","name":"ios-translations","githubRepo":"myorg/ios-translations","watchBranch":"release/2.4","sourceFilePaths":["Localizable.strings"],"category":"mobile","tone":"friendly","targetCount":3}]}""",
                    ContentType.Application.Json
                )
            }

            // Analytics
            get("/api/analytics/overview") {
                val now = System.currentTimeMillis()
                call.respondText(
                    """{"plan":"SOLO","planPriceInrPerMonth":499,"stringsTranslatedThisMonth":3421,"costPerStringInr":0.146,"lastMonthCostPerStringInr":0.102,"projectedEndOfMonthCostInr":0.138,"sparkline":[{"month":"2026-01","strings":0},{"month":"2026-02","strings":1204},{"month":"2026-03","strings":3156},{"month":"2026-04","strings":4892},{"month":"2026-05","strings":4601},{"month":"2026-06","strings":3421}],"trackingSinceMillis":${now - 10_800_000_000}}""",
                    ContentType.Application.Json
                )
            }
            get("/api/analytics/projects") {
                call.respondText(
                    """[{"projectId":"proj-001","name":"my-android-app","stringsTranslated":2843,"runs":18,"locales":5,"acceptanceRatePct":94,"lastRunMillis":${System.currentTimeMillis() - 1_800_000}},{"projectId":"proj-002","name":"ios-translations","stringsTranslated":578,"runs":6,"locales":3,"acceptanceRatePct":88,"lastRunMillis":${System.currentTimeMillis() - 172_800_000}}]""",
                    ContentType.Application.Json
                )
            }
            get("/api/analytics/locales") {
                call.respondText(
                    """[{"locale":"hi","stringsTranslated":886,"projectsCount":2},{"locale":"es","stringsTranslated":854,"projectsCount":2},{"locale":"fr","stringsTranslated":832,"projectsCount":2},{"locale":"de","stringsTranslated":821,"projectsCount":1},{"locale":"ja","stringsTranslated":450,"projectsCount":1}]""",
                    ContentType.Application.Json
                )
            }
            get("/api/analytics/runs") {
                val now = System.currentTimeMillis()
                call.respondText(
                    """[{"runId":"run-003","repo":"myorg/my-android-app","branch":"main","commitShort":"9fa31c2","startedAt":${now - 1_800_000},"finishedAt":${now - 1_680_000},"stringsTranslated":10,"localeCount":5,"triggeredByLabel":"ankush","hasPr":true,"hasError":false},{"runId":"run-002","repo":"myorg/my-android-app","branch":"main","commitShort":"c7d4e1f","startedAt":${now - 86_400_000},"finishedAt":${now - 86_280_000},"stringsTranslated":15,"localeCount":5,"triggeredByLabel":"ankush","hasPr":true,"hasError":false},{"runId":"run-001","repo":"myorg/ios-translations","branch":"release/2.4","commitShort":"b3e90a1","startedAt":${now - 172_800_000},"finishedAt":${now - 172_680_000},"stringsTranslated":0,"localeCount":0,"triggeredByLabel":"external","hasPr":false,"hasError":true}]""",
                    ContentType.Application.Json
                )
            }
            get("/api/analytics/members") {
                call.respondText(
                    """[{"userId":"preview","username":"preview-user","displayName":"preview-user","runsTriggered":2,"stringsTranslated":2843,"acceptanceRatePct":94}]""",
                    ContentType.Application.Json
                )
            }
            get("/api/analytics/cost-breakdown") {
                call.respondText(
                    """{"totalInr":499,"shares":[{"key":"proj-001","displayName":"my-android-app","strings":2843,"shareInr":412.3},{"key":"proj-002","displayName":"ios-translations","strings":578,"shareInr":86.7}]}""",
                    ContentType.Application.Json
                )
            }
            get("/api/analytics/quality") {
                call.respondText(
                    """[{"projectId":"proj-001","projectName":"my-android-app","locale":"hi","approved":841,"blocked":45,"pending":0,"acceptanceRatePct":95},{"projectId":"proj-001","projectName":"my-android-app","locale":"es","approved":812,"blocked":42,"pending":3,"acceptanceRatePct":95},{"projectId":"proj-002","projectName":"ios-translations","locale":"hi","accepted":312,"rejected":26,"pending":0,"acceptanceRatePct":92}]""",
                    ContentType.Application.Json
                )
            }

            // Review portal
            get("/api/review") {
                val now = System.currentTimeMillis()
                call.respondText(
                    """{"pending_reviews":[{"id":"rv-001","stringKey":"onboarding_title","sourceText":"Welcome to the app","targetLanguage":"hi","targetRegion":null,"translatedText":"ऐप में आपका स्वागत है","previousTranslatedText":null,"status":"review","blockReason":null,"projectId":"proj-001","projectName":"my-android-app","pipelineRunId":"run-003","commitShort":"9fa31c2","projectOtaEnabled":true},{"id":"rv-002","stringKey":"settings_header","sourceText":"Settings","targetLanguage":"hi","targetRegion":null,"translatedText":"सेटिंग्स","previousTranslatedText":null,"status":"review","blockReason":null,"projectId":"proj-001","projectName":"my-android-app","pipelineRunId":"run-003","commitShort":"9fa31c2","projectOtaEnabled":true},{"id":"rv-003","stringKey":"error_network","sourceText":"No internet connection. Please check your network and try again.","targetLanguage":"es","targetRegion":null,"translatedText":"Sin conexión a internet. Por favor verifique su red e inténtelo de nuevo.","previousTranslatedText":null,"status":"review","blockReason":null,"projectId":"proj-001","projectName":"my-android-app","pipelineRunId":"run-003","commitShort":"9fa31c2","projectOtaEnabled":true},{"id":"rv-004","stringKey":"payment_failed","sourceText":"Payment failed. Please try a different card.","targetLanguage":"fr","targetRegion":null,"translatedText":"Paiement échoué. Veuillez essayer une autre carte.","previousTranslatedText":null,"status":"blocked","blockReason":"Wrong tone — too formal for target audience","projectId":"proj-001","projectName":"my-android-app","pipelineRunId":"run-002","commitShort":"c7d4e1f","projectOtaEnabled":true}],"count":4}""",
                    ContentType.Application.Json
                )
            }
            // Review write actions — silently succeed without side effects
            post("/api/review/{id}/approve") { call.respond(HttpStatusCode.OK) }
            post("/api/review/{id}/reject") { call.respond(HttpStatusCode.OK) }
            post("/api/review/{id}/hotfix") {
                call.respondText(
                    """{"id":"rv-000","translatedText":"mock-edited","publish":null}""",
                    ContentType.Application.Json
                )
            }

            // Notifications
            get("/api/notifications") {
                val now = System.currentTimeMillis()
                call.respondText(
                    """{"notifications":[{"id":"notif-001","title":"Pipeline complete","message":"my-android-app — PR #44 opened with 2 translated strings.","level":"info","actionUrl":"/app","actionLabel":"View","createdAt":${now - 1_800_000},"readAt":null},{"id":"notif-002","title":"3 strings pending review","message":"Head to the review portal to approve or reject.","level":"warn","actionUrl":"/review-portal","actionLabel":"Review","createdAt":${now - 7_200_000},"readAt":${now - 3_600_000}}],"unreadCount":1}""",
                    ContentType.Application.Json
                )
            }
            post("/api/notifications/{id}/read") { call.respond(HttpStatusCode.OK) }
            post("/api/notifications/read-all") { call.respond(HttpStatusCode.OK) }

            // Onboarding — return fully completed so no modal pops up
            get("/api/onboarding/state") {
                call.respondText(
                    """{"step":"PIPELINE_COMPLETE","completed":true,"dismissed":false,"plan":"SOLO","inTrial":false,"hasProject":true}""",
                    ContentType.Application.Json
                )
            }
            post("/api/onboarding/skip") { call.respond(HttpStatusCode.OK) }
            post("/api/onboarding/resume") { call.respond(HttpStatusCode.OK) }

            // Members
            get("/api/members/{projectId}") {
                call.respondText(
                    """{"members":[{"userId":"preview","username":"preview-user","email":"preview@syncling.space","role":"OWNER","joinedAt":${System.currentTimeMillis() - 5_000_000_000}}],"invites":[]}""",
                    ContentType.Application.Json
                )
            }
            post("/api/members/{projectId}/invite") { call.respond(HttpStatusCode.OK) }
            delete("/api/members/{projectId}/{userId}") { call.respond(HttpStatusCode.OK) }
            put("/api/members/{projectId}/{userId}/role") { call.respond(HttpStatusCode.OK) }

            // API tokens
            get("/api/me/tokens") {
                call.respondText(
                    """{"tokens":[{"id":"tok-001","name":"CI / CD deploy token","platforms":["android","ios"],"createdAt":${System.currentTimeMillis() - 2_592_000_000},"lastUsedAt":${System.currentTimeMillis() - 86_400_000}}]}""",
                    ContentType.Application.Json
                )
            }
            post("/api/me/tokens") {
                call.respondText(
                    """{"id":"tok-new","name":"preview-token","platforms":["android"],"token":"sk_preview_00000000000000000000000000000000","createdAt":${System.currentTimeMillis()},"lastUsedAt":null}""",
                    ContentType.Application.Json
                )
            }
            delete("/api/me/tokens/{id}") { call.respond(HttpStatusCode.OK) }

            // Support
            get("/api/support") {
                call.respondText(
                    """{"tickets":[],"isAdmin":false}""",
                    ContentType.Application.Json
                )
            }
            post("/api/support") {
                call.respondText(
                    """{"id":"tkt-preview","category":"question","subject":"Preview ticket","status":"open","createdAt":${System.currentTimeMillis()}}""",
                    ContentType.Application.Json
                )
            }
            get("/api/support/{id}") {
                call.respondText(
                    """{"id":"tkt-preview","category":"question","subject":"Preview ticket","status":"open","messages":[],"createdAt":${System.currentTimeMillis()}}""",
                    ContentType.Application.Json
                )
            }

            // Project detail (drawer)
            get("/api/projects/{id}") {
                val id = call.parameters["id"] ?: "proj-001"
                call.respondText(
                    """{"id":"$id","name":"my-android-app","githubRepo":"myorg/my-android-app","watchBranch":"main","sourceFilePaths":["app/src/main/res/values/strings.xml"],"category":"mobile","tone":"professional","targets":[{"language":"hi","region":null},{"language":"es","region":null},{"language":"fr","region":null},{"language":"de","region":null},{"language":"ja","region":null}],"webhookInstalled":true,"autoPromote":true,"sharedMemoryOptIn":false,"prBranchPattern":null}""",
                    ContentType.Application.Json
                )
            }
            // Write actions for projects — silently succeed
            post("/api/projects") {
                call.respondText(
                    """{"id":"proj-new","name":"new-project","githubRepo":"myorg/new-project","watchBranch":"main","sourceFilePaths":["values/strings.xml"],"category":"mobile","tone":"professional","targetCount":0}""",
                    ContentType.Application.Json
                )
            }
            put("/api/projects/{id}") { call.respond(HttpStatusCode.OK) }
            delete("/api/projects/{id}") { call.respond(HttpStatusCode.OK) }
            post("/api/projects/{id}/sync") { call.respond(HttpStatusCode.OK) }
            post("/api/projects/{id}/install-webhook") { call.respond(HttpStatusCode.OK) }

            // Bundle endpoint (CDN widget locale chips link here)
            get("/api/projects/{id}/bundle/{locale}") {
                val locale = call.parameters["locale"] ?: "hi"
                call.respondText(
                    """{"locale":"$locale","strings":{"app_name":"Syncling","onboarding_title":"Preview translation","settings_header":"Settings"},"version":"a1b2c3d4","publishedAt":${System.currentTimeMillis() - 3_600_000}}""",
                    ContentType.Application.Json
                )
            }

            // Catch-all: redirect to /app
            get("/{...}") { call.respondRedirect("/app") }
        }
    }.start(wait = true)
}
