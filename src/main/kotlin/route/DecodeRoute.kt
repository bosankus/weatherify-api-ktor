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
                    WebResources.includeFirebaseAnalytics(this)
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
                                /* Safe area insets for mobile devices */
                                /* Smooth theme transitions for all elements */
                                * {
                                  transition-property: background-color, border-color, color, fill, stroke;
                                  transition-duration: 0.3s;
                                  transition-timing-function: ease;
                                }

                                /* Override for elements with specific transitions */
                                *:where(.btn, .panel, .input-area, .code-view) {
                                  transition-property: none;
                                }

                                :root {
                                  --safe-top: env(safe-area-inset-top);
                                  --safe-bottom: env(safe-area-inset-bottom);
                                  --safe-left: env(safe-area-inset-left);
                                  --safe-right: env(safe-area-inset-right);

                                  /* Decode-specific CSS variables */
                                  --decode-panel-bg: var(--card-bg);
                                  --decode-panel-border: var(--card-border);
                                  --decode-panel-shadow: 0 4px 20px var(--card-shadow);
                                  --decode-panel-radius: 16px;
                                  --decode-panel-hover-shadow: 0 8px 30px var(--card-shadow);

                                  --decode-btn-bg: var(--endpoint-bg, var(--card-bg));
                                  --decode-btn-border: var(--endpoint-border, var(--card-border));
                                  --decode-btn-hover-bg: var(--card-hover-bg);
                                  --decode-btn-hover-border: var(--card-hover-border);
                                  --decode-btn-radius: 10px;
                                  --decode-btn-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
                                  --decode-btn-hover-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);

                                  --decode-code-font: 'JetBrains Mono', monospace;
                                  --decode-code-font-size: 13px;
                                  --decode-code-line-height: 1.6;
                                  --decode-code-padding: 12px 16px;

                                  --decode-gutter-bg: var(--endpoint-bg);
                                  --decode-gutter-border: var(--endpoint-border);
                                  --decode-gutter-width: 56px;

                                  --decode-toolbar-gap: 12px;
                                  --decode-input-min-height: 420px;
                                }

                                .container {
                                  padding: calc(0.75rem + var(--safe-top)) calc(1rem + var(--safe-right)) calc(1rem + var(--safe-bottom)) calc(1rem + var(--safe-left));
                                }

                                /* Main container */
                                .decode-container {
                                  max-width: 1100px;
                                  margin: 0 auto;
                                  padding: 24px;
                                  transition: padding 0.2s ease;
                                }

                                .decode-container > h1 {
                                  margin-top: 6px;
                                  margin-bottom: 12px;
                                  transition: color 0.3s ease;
                                }

                                .decode-container > p {
                                  margin-bottom: 20px;
                                  line-height: 1.6;
                                  transition: color 0.3s ease;
                                }

                                /* Grid layout */
                                .decode-grid {
                                  display: grid;
                                  grid-template-columns: 1fr 1fr;
                                  gap: 16px;
                                  transition: grid-template-columns 0.3s ease;
                                }

                                /* Modern panel styling with cards and shadows */
                                .panel {
                                  background: var(--decode-panel-bg);
                                  border: 1px solid var(--decode-panel-border);
                                  border-radius: var(--decode-panel-radius);
                                  box-shadow: var(--decode-panel-shadow);
                                  overflow: hidden;
                                  transition: box-shadow 0.3s ease, transform 0.3s ease, background 0.3s ease, border-color 0.3s ease;
                                  backdrop-filter: blur(10px);
                                  will-change: transform;
                                }

                                .panel:hover {
                                  box-shadow: var(--decode-panel-hover-shadow);
                                  transform: translateY(-2px);
                                }

                                .panel-header {
                                  padding: 16px 20px;
                                  display: flex;
                                  justify-content: space-between;
                                  align-items: center;
                                  border-bottom: 1px solid var(--header-border);
                                  flex-wrap: wrap;
                                  gap: 12px;
                                  background: rgba(var(--card-bg-rgb, 0, 0, 0), 0.5);
                                  transition: background 0.3s ease, border-color 0.3s ease;
                                }

                                .panel-title {
                                  font-weight: 600;
                                  font-size: 1.1rem;
                                  color: var(--card-title);
                                  display: flex;
                                  align-items: center;
                                  gap: 8px;
                                  transition: color 0.3s ease;
                                }

                                .panel-title .material-icons {
                                  font-size: 1.3rem;
                                  opacity: 0.8;
                                  transition: opacity 0.2s ease;
                                }

                                .panel-body {
                                  padding: 0;
                                  transition: background 0.3s ease;
                                }

                                /* Input area */
                                .input-area {
                                  width: 100%;
                                  min-height: var(--decode-input-min-height);
                                  resize: vertical;
                                  padding: 16px;
                                  background: transparent;
                                  color: var(--text-color);
                                  font-family: var(--decode-code-font);
                                  font-size: var(--decode-code-font-size);
                                  line-height: var(--decode-code-line-height);
                                  border: none;
                                  outline: none;
                                  transition: background 0.2s ease, color 0.3s ease;
                                }

                                .input-area:focus {
                                  background: rgba(var(--card-bg-rgb, 0, 0, 0), 0.3);
                                }

                                .input-area::placeholder {
                                  transition: color 0.3s ease;
                                }

                                /* Code display */
                                .code-view {
                                  display: flex;
                                  font-family: var(--decode-code-font);
                                  font-size: var(--decode-code-font-size);
                                  line-height: var(--decode-code-line-height);
                                  transition: opacity 0.2s ease;
                                }

                                .gutter {
                                  user-select: none;
                                  background: var(--decode-gutter-bg);
                                  color: var(--text-secondary);
                                  padding: var(--decode-code-padding);
                                  text-align: right;
                                  min-width: var(--decode-gutter-width);
                                  border-right: 1px solid var(--decode-gutter-border);
                                  font-variant-numeric: tabular-nums;
                                  transition: background 0.3s ease, color 0.3s ease, border-color 0.3s ease;
                                }

                                .gutter div {
                                  transition: color 0.2s ease;
                                }

                                .gutter div.error {
                                  color: #ff6b6b;
                                  font-weight: 700;
                                }

                                .code {
                                  padding: var(--decode-code-padding);
                                  overflow: auto;
                                  white-space: pre;
                                  flex: 1;
                                  tab-size: 2;
                                  transition: color 0.3s ease;
                                }

                                /* Syntax highlighting colors for both themes */
                                .token-key {
                                  color: #8ab4f8;
                                  font-weight: 500;
                                  transition: color 0.3s ease;
                                }

                                .token-string {
                                  color: #98FB98;
                                  transition: color 0.3s ease;
                                }

                                .token-number {
                                  color: #fbd38d;
                                  transition: color 0.3s ease;
                                }

                                .token-boolean {
                                  color: #f28b82;
                                  font-weight: 500;
                                  transition: color 0.3s ease;
                                }

                                .token-null {
                                  color: #c792ea;
                                  font-style: italic;
                                  transition: color 0.3s ease;
                                }

                                /* Light theme syntax colors */
                                [data-theme="light"] .token-key {
                                  color: #0066cc;
                                }

                                [data-theme="light"] .token-string {
                                  color: #22863a;
                                }

                                [data-theme="light"] .token-number {
                                  color: #e36209;
                                }

                                [data-theme="light"] .token-boolean {
                                  color: #d73a49;
                                }

                                [data-theme="light"] .token-null {
                                  color: #6f42c1;
                                }

                                .error-line {
                                  background: rgba(255, 107, 107, 0.1);
                                  border-left: 3px solid #ff6b6b;
                                  margin-left: -3px;
                                  padding-left: 3px;
                                  transition: background 0.2s ease, border-color 0.2s ease;
                                }

                                /* Toolbar */
                                .toolbar {
                                  display: flex;
                                  gap: var(--decode-toolbar-gap);
                                  flex-wrap: wrap;
                                  transition: gap 0.2s ease;
                                }

                                /* Select dropdown styling */
                                select.btn {
                                  appearance: none;
                                  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23888' d='M6 9L1 4h10z'/%3E%3C/svg%3E");
                                  background-repeat: no-repeat;
                                  background-position: right 12px center;
                                  padding-right: 36px;
                                }

                                /* Modern button styles with icons and hover effects */
                                .btn {
                                  background: var(--decode-btn-bg);
                                  border: 1px solid var(--decode-btn-border);
                                  color: var(--text-color);
                                  padding: 10px 16px;
                                  border-radius: var(--decode-btn-radius);
                                  cursor: pointer;
                                  min-height: 40px;
                                  font-size: 0.875rem;
                                  font-weight: 500;
                                  transition: all 0.2s ease;
                                  display: inline-flex;
                                  align-items: center;
                                  gap: 6px;
                                  box-shadow: var(--decode-btn-shadow);
                                  will-change: transform;
                                }

                                .btn .material-icons {
                                  font-size: 1.1rem;
                                  transition: transform 0.2s ease;
                                }

                                .btn:hover:not(:disabled) {
                                  background: var(--decode-btn-hover-bg);
                                  border-color: var(--decode-btn-hover-border);
                                  transform: translateY(-1px);
                                  box-shadow: var(--decode-btn-hover-shadow);
                                }

                                .btn:hover:not(:disabled) .material-icons {
                                  transform: scale(1.1);
                                }

                                .btn:active:not(:disabled) {
                                  transform: translateY(0);
                                  box-shadow: var(--decode-btn-shadow);
                                  transition: all 0.1s ease;
                                }

                                .btn:disabled {
                                  opacity: 0.6;
                                  cursor: not-allowed;
                                  transform: none;
                                }

                                /* Error display with modern design */
                                .error-message {
                                  display: none;
                                  padding: 16px;
                                  margin: 16px;
                                  background: rgba(239, 68, 68, 0.08);
                                  border-left: 4px solid #ef4444;
                                  border-radius: 8px;
                                  color: #dc2626;
                                  font-weight: 500;
                                  line-height: 1.6;
                                  transition: background 0.3s ease, color 0.3s ease, border-color 0.3s ease;
                                  animation: slideIn 0.3s ease;
                                }

                                [data-theme="light"] .error-message {
                                  background: rgba(239, 68, 68, 0.1);
                                  color: #b91c1c;
                                }

                                .error-message::before {
                                  content: "⚠️ ";
                                  font-size: 1.2rem;
                                  margin-right: 8px;
                                }

                                @keyframes slideIn {
                                  from {
                                    opacity: 0;
                                    transform: translateY(-10px);
                                  }
                                  to {
                                    opacity: 1;
                                    transform: translateY(0);
                                  }
                                }

                                /* Mock API box with success styling */
                                #mock-api-box {
                                  display: none;
                                  padding: 16px;
                                  margin: 16px;
                                  background: rgba(16, 185, 129, 0.08);
                                  border: 1px solid rgba(16, 185, 129, 0.3);
                                  border-radius: 12px;
                                  color: var(--text-color);
                                  transition: background 0.3s ease, border-color 0.3s ease, color 0.3s ease;
                                  animation: slideIn 0.3s ease;
                                }

                                [data-theme="light"] #mock-api-box {
                                  background: rgba(16, 185, 129, 0.1);
                                }

                                #mock-api-box::before {
                                  content: "✓ ";
                                  color: #10b981;
                                  font-size: 1.3rem;
                                  font-weight: 700;
                                  margin-right: 8px;
                                }

                                #mock-url {
                                  font-family: var(--decode-code-font);
                                  font-size: 0.9rem;
                                  background: rgba(0, 0, 0, 0.1);
                                  padding: 8px 12px;
                                  border-radius: 6px;
                                  margin: 8px 0;
                                  word-break: break-all;
                                  color: var(--text-color);
                                  transition: background 0.3s ease, color 0.3s ease;
                                }

                                [data-theme="light"] #mock-url {
                                  background: rgba(0, 0, 0, 0.05);
                                }

                                /* Collapsible How-to-Use section */
                                #howto-details {
                                  margin: 16px 0;
                                  border: 1px solid var(--card-border);
                                  border-radius: 12px;
                                  background: var(--card-bg);
                                  box-shadow: 0 2px 8px var(--card-shadow);
                                  overflow: hidden;
                                  transition: background 0.3s ease, border-color 0.3s ease, box-shadow 0.3s ease;
                                }

                                #howto-details summary {
                                  padding: 12px 16px;
                                  cursor: pointer;
                                  display: flex;
                                  align-items: center;
                                  gap: 8px;
                                  font-weight: 600;
                                  font-size: 1rem;
                                  user-select: none;
                                  transition: background 0.2s ease, color 0.3s ease;
                                  list-style: none;
                                }

                                #howto-details summary .material-icons {
                                  font-size: 1.2rem;
                                  opacity: 0.8;
                                  transition: opacity 0.2s ease, transform 0.2s ease;
                                }

                                #howto-details summary:hover .material-icons {
                                  opacity: 1;
                                  transform: scale(1.1);
                                }

                                #howto-details summary::-webkit-details-marker {
                                  display: none;
                                }

                                #howto-details summary::before {
                                  content: "▶";
                                  font-size: 0.8rem;
                                  transition: transform 0.3s ease;
                                  display: inline-block;
                                }

                                #howto-details[open] summary::before {
                                  transform: rotate(90deg);
                                }

                                #howto-details summary:hover {
                                  background: var(--card-hover-bg);
                                }

                                #howto-details > div {
                                  padding: 16px;
                                  border-top: 1px solid var(--header-border);
                                  line-height: 1.6;
                                  animation: slideDown 0.3s ease;
                                  transition: border-color 0.3s ease;
                                }

                                @keyframes slideDown {
                                  from {
                                    opacity: 0;
                                    transform: translateY(-10px);
                                  }
                                  to {
                                    opacity: 1;
                                    transform: translateY(0);
                                  }
                                }

                                #howto-details ul {
                                  margin: 8px 0;
                                  padding-left: 24px;
                                  transition: color 0.3s ease;
                                }

                                #howto-details li {
                                  margin: 4px 0;
                                  transition: color 0.3s ease;
                                }

                                #howto-details p {
                                  transition: color 0.3s ease;
                                }

                                /* Toast notification animations */
                                @keyframes fadeInSlide {
                                  from {
                                    opacity: 0;
                                    transform: translateY(-20px);
                                  }
                                  to {
                                    opacity: 1;
                                    transform: translateY(0);
                                  }
                                }

                                @keyframes fadeOut {
                                  from {
                                    opacity: 1;
                                  }
                                  to {
                                    opacity: 0;
                                  }
                                }

                                /* Performance optimization: reduce animations on mobile */
                                @media (prefers-reduced-motion: reduce) {
                                  *,
                                  *::before,
                                  *::after {
                                    animation-duration: 0.01ms !important;
                                    animation-iteration-count: 1 !important;
                                    transition-duration: 0.01ms !important;
                                  }
                                }

                                /* Responsive layout with breakpoints */
                                @media (max-width: 900px) {
                                  .decode-grid {
                                    grid-template-columns: 1fr;
                                  }

                                  .input-area {
                                    min-height: 40vh;
                                  }

                                  .code-view {
                                    max-height: 50vh;
                                  }

                                  .panel:hover {
                                    transform: none;
                                  }
                                }

                                @media (max-width: 480px) {
                                  .decode-container {
                                    padding: 12px;
                                  }

                                  .panel-header {
                                    padding: 12px 16px;
                                  }

                                  .panel-title {
                                    font-size: 1rem;
                                  }

                                  .panel-title .material-icons {
                                    font-size: 1.1rem;
                                  }

                                  .btn {
                                    flex: 1 1 auto;
                                    padding: 10px 12px;
                                    font-size: 0.8rem;
                                  }

                                  .btn .material-icons {
                                    font-size: 1rem;
                                  }

                                  .toolbar {
                                    gap: 8px;
                                  }

                                  .gutter {
                                    min-width: 40px;
                                    padding: 12px 6px;
                                  }

                                  .code {
                                    font-size: 12px;
                                  }

                                  #howto-details summary .material-icons {
                                    font-size: 1rem;
                                  }
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
                    // Standardized Header Component
                    unsafe {
                        raw(
                            """
                            <header class="app-header" role="banner">
                              <div class="app-header__container">
                                <div class="app-header__brand">
                                  <a href="/weather" id="header-logo-link" class="app-header__logo-link" aria-label="Androidplay Home">
                                    <h1 class="app-header__logo">Androidplay</h1>
                                  </a>
                                  <span id="header-subtitle" class="app-header__subtitle" data-default-subtitle="Data Decoder">
                                    Data Decoder
                                  </span>
                                </div>
                                <div class="app-header__spacer"></div>
                                <div id="header-actions" class="app-header__actions" role="navigation" aria-label="Header actions">
                                </div>
                              </div>
                            </header>
                            """
                        )
                    }

                    // Initialize header with decode page configuration
                    unsafe {
                        raw(
                            """
                            <script>
                              initializeHeader({
                                homeUrl: '/weather',
                                subtitle: 'Data Decoder',
                                actions: [
                                  { type: 'theme-toggle' },
                                  { type: 'user-info' },
                                  { type: 'logout' }
                                ]
                              });
                            </script>
                            """
                        )
                    }

                    div {
                        classes = setOf("container")

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
                                summary {
                                    span("material-icons") { +"help_outline" }
                                    +"How to Use"
                                }
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
                                        span("panel-title") {
                                            span("material-icons") { +"input" }
                                            +"Input"
                                        }
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
                                                "Format the input. Attempts auto-fix if needed."
                                                span("material-icons") { +"format_align_left" }
                                                +"Format"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-autofix"; attributes["title"] =
                                                "Auto-fix common JSON issues (quotes, commas, comments, booleans)"
                                                span("material-icons") { +"build" }
                                                +"Auto Fix"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-unescape"; attributes["title"] =
                                                "Decode escape sequences or quoted string into raw text (JSON)"
                                                span("material-icons") { +"code_off" }
                                                +"Unescape"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-clear"; attributes["title"] =
                                                "Clear the input area"
                                                span("material-icons") { +"clear" }
                                                +"Clear"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-sample"; attributes["title"] =
                                                "Insert a sample for the selected format"
                                                span("material-icons") { +"insert_drive_file" }
                                                +"Sample"
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
                                        span("panel-title") {
                                            span("material-icons") { +"output" }
                                            +"Output"
                                        }
                                        div("toolbar") {
                                            button(classes = "btn") {
                                                attributes["id"] = "btn-copy"; attributes["title"] =
                                                "Copy the output to clipboard"
                                                span("material-icons") { +"content_copy" }
                                                +"Copy"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-minify"; attributes["title"] =
                                                "Minify the output"
                                                span("material-icons") { +"compress" }
                                                +"Minify"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] = "btn-sort"; attributes["title"] =
                                                "Sort JSON object keys recursively"
                                                span("material-icons") { +"sort_by_alpha" }
                                                +"Sort Keys"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-download"; attributes["title"] =
                                                "Download the output as a file"
                                                span("material-icons") { +"download" }
                                                +"Download"
                                            }
                                            button(classes = "btn") {
                                                attributes["id"] =
                                                    "btn-create-mock"; attributes["title"] =
                                                "Create a temporary mock API from JSON output"
                                                span("material-icons") { +"cloud_upload" }
                                                +"Create Mock API"
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
                                                    "Copy the mock API URL"
                                                    span("material-icons") { +"content_copy" }
                                                    +"Copy URL"
                                                }
                                                button(classes = "btn") {
                                                    attributes["id"] =
                                                        "btn-mock-reset"; attributes["title"] =
                                                    "Delete the mock API and hide this box"
                                                    span("material-icons") { +"refresh" }
                                                    +"Reset"
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
