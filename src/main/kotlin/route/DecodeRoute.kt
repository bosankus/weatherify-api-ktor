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
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.summary
import kotlinx.html.textArea
import kotlinx.html.title
import kotlinx.html.ul
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
                        content =
                            "width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover"
                    }
                    meta { name = "theme-color"; content = "#0f1117" }
                    meta { name = "apple-mobile-web-app-capable"; content = "yes" }
                    meta {
                        name = "apple-mobile-web-app-status-bar-style"; content =
                        "black-translucent"
                    }
                    meta { name = "mobile-web-app-capable"; content = "yes" }
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
                                :root { --safe-top: env(safe-area-inset-top); --safe-bottom: env(safe-area-inset-bottom); --safe-left: env(safe-area-inset-left); --safe-right: env(safe-area-inset-right); }
                                .container { padding: calc(0.75rem + var(--safe-top)) calc(1rem + var(--safe-right)) calc(1rem + var(--safe-bottom)) calc(1rem + var(--safe-left)); }
                                .header { margin-top: 0; display:flex; flex-wrap: wrap; gap: .5rem; }

                                .decode-container { max-width: 1100px; margin: 0 auto; padding: 24px; }
                                .decode-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
                                .panel { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 16px; box-shadow: 0 8px 30px var(--card-shadow); overflow: hidden; }
                                .panel-header { padding: 12px 16px; display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid var(--header-border); flex-wrap: wrap; gap: 8px; }
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
                                .toolbar { display:flex; gap:8px; flex-wrap: wrap; }
                                .btn { background: var(--endpoint-bg); border:1px solid var(--endpoint-border); color: var(--text-color); padding:10px 12px; border-radius:10px; cursor:pointer; min-height: 40px; }
                                .btn:hover { background: var(--card-hover-bg); border-color: var(--card-hover-border); }
                                .btn[disabled] { opacity: .6; cursor: not-allowed; }
                                .decode-container > p { margin-bottom: 20px; }
                                @media (max-width: 900px){
                                  .decode-grid{ grid-template-columns: 1fr; }
                                  .input-area{ min-height: 40vh; }
                                  .code-view{ max-height: 50vh; }
                                }
                                @media (max-width: 480px){
                                  .decode-container{ padding: 12px; }
                                  .panel-title{ margin-bottom: 4px; }
                                  .btn{ flex: 1 1 auto; }
                                }
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
                            }
                        }

                        // Main content
                        div {
                            classes = setOf("decode-container")
                            h1 {
                                attributes["style"] =
                                    "margin-top: 6px;"; +"Data Decoder (JSON, XML, Protobuf)"
                            }
                            p { +"Paste or type your data on the left. Choose a format. The formatted output appears on the right. Errors will be highlighted with line numbers when available." }
                            // Collapsible How to Use at top
                            details {
                                attributes["id"] = "howto-details"
                                attributes["style"] = "margin: 8px 0 12px;"
                                summary { +"How to Use" }
                                div {
                                    attributes["style"] =
                                        "padding: 12px 16px; border: 1px solid var(--header-border); border-radius: 12px; margin-top:8px;"
                                    p { +"Choose a format from the selector. Tools behave per format as described below." }
                                    p { +"Input tools:" }
                                    ul {
                                        li { +"Format: Pretty-print JSON/XML. For Protobuf, shows hex dump of Base64 input." }
                                        li { +"Auto Fix: JSON only. Fixes common JSON issues (quotes, commas, comments, Python True/False/None)." }
                                        li { +"Unescape: JSON only. Decodes escape sequences or string literals." }
                                        li { +"Clear: Clears the input area." }
                                        li { +"Sample: Inserts sample for the selected format." }
                                    }
                                    p { +"Output tools:" }
                                    ul {
                                        li { +"Copy: Copy the output." }
                                        li { +"Minify: JSON/XML minify; for Protobuf compacts whitespace in hex." }
                                        li { +"Sort Keys: JSON only. Recursively sorts object keys." }
                                        li { +"Download: Saves output with appropriate file type." }
                                        li { +"Create Mock API: JSON only. Uses your JSON to create a temporary mock endpoint." }
                                    }
                                    p { +"Notes:" }
                                    ul {
                                        li { +"JSON: Line/column errors highlighted where possible." }
                                        li { +"XML: Basic error reporting; pretty print is best-effort." }
                                        li { +"Protobuf: Schema (.proto) is required for full decode. Paste Base64 to view a hex dump or convert via protoc to JSON and use JSON mode." }
                                    }
                                }
                            }
                            div {
                                classes = setOf("decode-grid")
                                div("panel") {
                                    div("panel-header") {
                                        span("panel-title") { +"Input" }
                                        div("toolbar") {
                                            select(classes = "btn") {
                                                attributes["id"] = "format-select"
                                                attributes["title"] =
                                                    "Select data format (JSON, XML, Protobuf)"
                                                option { value = "json"; selected = true; +"JSON" }
                                                option { value = "xml"; +"XML" }
                                                option { value = "protobuf"; +"Protobuf" }
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-format"; attributes["title"] =
                                                "Format the input. Attempts auto-fix if needed."; +"Format"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-autofix"; attributes["title"] =
                                                "Auto-fix common JSON issues (quotes, commas, comments, booleans)"; +"Auto Fix"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-unescape"; attributes["title"] =
                                                "Decode escape sequences or quoted string into raw text (JSON)"; +"Unescape"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-clear"; attributes["title"] =
                                                "Clear the input area"; +"Clear"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-sample"; attributes["title"] =
                                                "Insert a sample for the selected format"; +"Sample"
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
                                                attributes["id"] = "btn-copy"; attributes["title"] =
                                                "Copy the output to clipboard"; +"Copy"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-minify"; attributes["title"] =
                                                "Minify the output"; +"Minify"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] = "btn-sort"; attributes["title"] =
                                                "Sort JSON object keys recursively"; +"Sort Keys"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-download"; attributes["title"] =
                                                "Download the output as a file"; +"Download"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-create-mock"; attributes["title"] =
                                                "Create a temporary mock API from JSON output"; +"Create Mock API"
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
                                                    attributes["id"] =
                                                        "btn-mock-copy"; attributes["title"] =
                                                    "Copy the mock API URL"; +"Copy URL"
                                                }
                                                button(classes = "btn") {
                                                    attributes["id"] =
                                                        "btn-mock-reset"; attributes["title"] =
                                                    "Delete the mock API and hide this box"; +"Reset"
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
