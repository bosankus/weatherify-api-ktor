package bose.ankush.route

import bose.ankush.route.common.WebResources
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.textArea
import kotlinx.html.title
import kotlinx.html.unsafe
import org.koin.ktor.ext.inject

fun Route.decodeRoute() {
    val pageName = "Decode | Androidplay"
    val analytics: util.Analytics by application.inject()
    route("/decode") {
        get {
            analytics.event(
                name = "page_view",
                params = mapOf(
                    "page_location" to "decode",
                    "page_title" to pageName
                ),
                userAgent = call.request.headers["User-Agent"]
            )
            call.respondHtml(HttpStatusCode.OK) {
                attributes["lang"] = "en"
                head {
                    WebResources.includeGoogleTag(this)
                    title { +pageName }
                    meta { charset = "UTF-8" }
                    meta {
                        name = "viewport"
                        content = "width=device-width, initial-scale=1.0"
                    }
                    // fonts and icons similar to home
                    link { rel = "preconnect"; href = "https://fonts.googleapis.com" }
                    link {
                        rel = "preconnect"
                        href = "https://fonts.gstatic.com"
                        attributes["crossorigin"] = ""
                    }
                    link {
                        rel = "stylesheet"
                        href =
                            "https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap"
                    }
                    link {
                        rel = "stylesheet"; href =
                        "https://fonts.googleapis.com/icon?family=Material+Icons"
                    }

                    WebResources.includeSharedCss(this)

                    // Page specific styles to give a unique look for decoder while aligning with home
                    style {
                        unsafe {
                            raw(
                                """
                                .container { padding: 1rem 2rem 2rem; }
                                .header { margin-top: 0; }

                                .decode-container { max-width: 1100px; margin: 0 auto; padding: 24px; }
                                .decode-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
                                .panel { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 16px; box-shadow: 0 8px 30px var(--card-shadow); overflow: hidden; }
                                .panel-header { padding: 12px 16px; display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid var(--header-border); }
                                .panel-title { font-weight:600; color: var(--card-title); }
                                .panel-body { padding: 0; }
                                .input-area { width:100%; min-height:420px; resize:vertical; padding:16px; background: transparent; color: var(--text-color); font-family: 'JetBrains Mono', monospace; border: none; outline:none; }
                                .code-view { display:flex; font-family:'JetBrains Mono', monospace; font-size: 13px; }
                                .gutter { user-select:none; background: var(--endpoint-bg); color: var(--text-secondary); padding: 12px 8px; text-align:right; min-width: 48px; border-right:1px solid var(--endpoint-border); }
                                .gutter div.error { color: #ff6b6b; font-weight: 700; }
                                .code { padding:12px 16px; overflow:auto; white-space: pre; flex:1; }
                                .token-key { color:#8ab4f8; }
                                .token-string { color:#98FB98; }
                                .token-number { color:#fbd38d; }
                                .token-boolean { color:#f28b82; }
                                .token-null { color:#c792ea; }
                                .error-line { background: rgba(255,107,107,0.1); }
                                .toolbar { display:flex; gap:8px; }
                                .btn { background: var(--endpoint-bg); border:1px solid var(--endpoint-border); color: var(--text-color); padding:6px 10px; border-radius:10px; cursor:pointer; }
                                .btn:hover { background: var(--card-hover-bg); border-color: var(--card-hover-border); }
                                .decode-container > p { margin-bottom: 20px; }
                                @media (max-width: 900px){ .decode-grid{ grid-template-columns: 1fr; } }
                                """
                            )
                        }
                    }

                    // include shared JS and our decode script
                    WebResources.includeSharedJs(this)
                    WebResources.includeDecodeJs(this)
                }
                body {
                    div {
                        classes = setOf("container")
                        div {
                            classes = setOf("header")
                            div {
                                classes = setOf("brand-text")
                                h1 {
                                    classes = setOf("logo")
                                    +"Androidplay"
                                }
                                span {
                                    classes = setOf("subtitle")
                                    +"Decode"
                                }
                            }
                            div { style = "flex-grow: 1;" }
                            div {
                                style = "display: flex; align-items: center; gap: 1rem;"
                                label {
                                    classes = setOf("toggle")
                                    style =
                                        "position: relative; cursor: pointer; margin-right: 0.5rem;"
                                    input {
                                        type = InputType.checkBox
                                        id = "theme-toggle"
                                    }
                                    div { }
                                }
                                span {
                                    classes = setOf("material-icons", "nav-icon", "github-link")
                                    id = "github-link"
                                    attributes["data-url"] =
                                        "https://github.com/bosankus/weatherify-api-ktor"
                                    +"code"
                                }
                            }
                        }

                        // Main content
                        div {
                            classes = setOf("decode-container")
                            h1 { +"JSON Decoder" }
                            p { +"Paste or type unformatted JSON on the left. The formatted, colorized output appears on the right. Errors will be highlighted with the line number." }
                            div {
                                classes = setOf("decode-grid")
                                div("panel") {
                                    div("panel-header") {
                                        span("panel-title") { +"Input" }
                                        div("toolbar") {
                                            button(classes = "btn") {
                                                attributes["id"] = "btn-format"; +"Format"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] = "btn-autofix"; +"Auto Fix"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] = "btn-unescape"; +"Unescape"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] = "btn-clear"; +"Clear"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] = "btn-sample"; +"Sample"
                                            }
                                        }
                                    }
                                    div("panel-body") {
                                        textArea(rows = "20", cols = "40") {
                                            attributes["id"] = "json-input"
                                            classes = setOf("input-area")
                                            placeholder =
                                                "{\n  \"hello\":\"world\",\n  \"n\":123,\n  \"ok\":true\n}"
                                        }
                                    }
                                }
                                div("panel") {
                                    div("panel-header") {
                                        span("panel-title") { +"Output" }
                                        div("toolbar") {
                                            button(classes = "btn") {
                                                attributes["id"] = "btn-copy"; +"Copy"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] = "btn-minify"; +"Minify"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] = "btn-sort"; +"Sort Keys"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] = "btn-download"; +"Download"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-create-mock"; +"Create Mock API"
                                            }
                                        }
                                    }
                                    div("panel-body") {
                                        div("code-view") {
                                            div("gutter") { id = "line-gutter" }
                                            pre { id = "code-output"; classes = setOf("code") }
                                        }
                                        div {
                                            id = "error-box"; classes =
                                            setOf("error-message"); style =
                                            "padding:10px 16px;color:#ff6b6b;display:none;"
                                        }
                                        div {
                                            id = "mock-api-box"
                                            attributes["data-mock-id"] = ""
                                            style =
                                                "display:none;padding:10px 16px;border-top:1px solid var(--header-border);"
                                            div {
                                                id = "mock-url"
                                                +""
                                            }
                                            div("toolbar") {
                                                button(classes = "btn") {
                                                    attributes["id"] = "btn-mock-copy"; +"Copy URL"
                                                }
                                                button(classes = "btn") {
                                                    attributes["id"] = "btn-mock-reset"; +"Reset"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Footer
                        footer {
                            classes = setOf("footer")
                            div {
                                classes = setOf("footer-content")
                                p { +"© ${java.time.Year.now().value} Androidplay. All rights reserved." }
                            }
                        }
                    }
                }
            }
        }
    }
}
