package bose.ankush.route

import bose.ankush.route.common.WebResources
import bose.ankush.route.common.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.acceptItems
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import org.slf4j.LoggerFactory
import util.Constants

// Logger for NotFoundRoute
private val logger = LoggerFactory.getLogger("NotFoundRoute")

/**
 * Helper function to determine if the request prefers HTML
 */
private fun isHtmlPreferred(acceptItems: List<io.ktor.http.HeaderValue>): Boolean {
    if (acceptItems.isEmpty()) return false

    // Check if any Accept header explicitly prefers HTML
    for (item in acceptItems) {
        val contentType = item.value
        if (contentType.contains("text/html", ignoreCase = true)) {
            return true
        }
        if (contentType.contains("*/*", ignoreCase = true)) {
            // Wildcard, but check if there's a more specific JSON preference
            if (!acceptItems.any { it.value.contains("application/json", ignoreCase = true) }) {
                return true
            }
        }
    }

    return false
}

/**
 * API-friendly 404 handler for any endpoint
 * This function can be called from the catch-all route in Routing.kt
 */
suspend fun io.ktor.server.application.ApplicationCall.handleNotFound() {
    val path = this.request.path()
    val method = this.request.httpMethod.value
    logger.info("404 Not Found: $method $path")

    // Check if the client prefers HTML (browser) or JSON (API client)
    val acceptItems = this.request.acceptItems()

    if (isHtmlPreferred(acceptItems)) {
        // Browser request - redirect to HTML 404 page
        logger.debug("Client prefers HTML, redirecting to 404 page")
        this.respondRedirect("/not-found")
    } else {
        // API request - return JSON error response
        logger.debug("Client prefers JSON, returning API error response")
        this.respondError(
            Constants.Messages.ENDPOINT_NOT_FOUND,
            mapOf(
                "path" to path,
                "method" to method,
                "errorType" to "NOT_FOUND"
            ),
            HttpStatusCode.NotFound
        )
    }
}

/**
 * Route for 404 Not Found error page
 */
fun Route.notFoundRoute() {
    val pageName = "Weatherify - Page Not Found"

    route("/not-found") {
        get {
            call.respondHtml(HttpStatusCode.NotFound) {
                attributes["lang"] = "en"
                head {
                    title { +pageName }
                    meta {
                        charset = "UTF-8"
                    }
                    meta {
                        name = "viewport"
                        content = "width=device-width, initial-scale=1.0"
                    }
                    link {
                        rel = "preconnect"
                        href = "https://fonts.googleapis.com"
                    }
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
                        rel = "stylesheet"
                        href = "https://fonts.googleapis.com/icon?family=Material+Icons"
                    }

                    // Include shared CSS
                    WebResources.includeSharedCss(this)

                    // Include page-specific CSS
                    style {
                        unsafe {
                            raw(
                                """
                                /* 404 page specific styles */
                                .error-content {
                                    max-width: 800px;
                                    margin: 2rem auto;
                                    padding: 2rem;
                                    text-align: center;
                                }
                                
                                .error-code {
                                    font-size: 8rem;
                                    font-weight: 700;
                                    margin: 0;
                                    line-height: 1;
                                    background: linear-gradient(135deg, #3b4f7d, #2d3748);
                                    -webkit-background-clip: text;
                                    -webkit-text-fill-color: transparent;
                                    margin-bottom: 1rem;
                                }
                                
                                .error-title {
                                    font-size: 2rem;
                                    margin-bottom: 1.5rem;
                                    color: var(--text-primary);
                                }
                                
                                .error-message {
                                    font-size: 1.2rem;
                                    margin-bottom: 2rem;
                                    color: var(--text-secondary);
                                    line-height: 1.6;
                                }
                                
                                .error-icon {
                                    font-size: 5rem;
                                    color: var(--text-secondary);
                                    margin-bottom: 1rem;
                                    opacity: 0.7;
                                }
                                
                                .action-buttons {
                                    display: flex;
                                    justify-content: center;
                                    gap: 1rem;
                                    margin-top: 2rem;
                                }
                                
                                .action-button {
                                    padding: 0.75rem 1.5rem;
                                    border-radius: 8px;
                                    font-weight: 600;
                                    font-size: 1rem;
                                    cursor: pointer;
                                    transition: transform 0.1s, box-shadow 0.2s;
                                    text-decoration: none;
                                    display: inline-flex;
                                    align-items: center;
                                    gap: 0.5rem;
                                }
                                
                                .primary-button {
                                    background: linear-gradient(135deg, #3b4f7d, #2d3748);
                                    color: white;
                                    border: none;
                                }
                                
                                .secondary-button {
                                    background: transparent;
                                    color: var(--text-primary);
                                    border: 1px solid var(--border-color);
                                }
                                
                                .action-button:hover {
                                    transform: translateY(-2px);
                                    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
                                }
                                
                                .action-button:active {
                                    transform: translateY(0);
                                }
                                
                                .material-icons {
                                    font-size: 1.2rem;
                                }
                                """
                            )
                        }
                    }

                    // Include minimal JavaScript for error page (without auth.js)
                    WebResources.includeErrorPageJs(this)

                    // Include page-specific JavaScript
                    script {
                        unsafe {
                            raw(
                                """
                                // 404 page specific JavaScript
                                document.addEventListener('DOMContentLoaded', function() {
                                    // Add click handler for the "Go Back" button
                                    document.getElementById('go-back-button').addEventListener('click', function() {
                                        window.history.back();
                                    });
                                });
                                """
                            )
                        }
                    }
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
                                    +"Error"
                                }
                            }
                            div {
                                style = "flex-grow: 1;"
                            }
                            div {
                                style = "display: flex; align-items: center; gap: 1rem;"

                                // Theme toggle
                                label {
                                    classes = setOf("toggle")
                                    style =
                                        "position: relative; cursor: pointer; margin-right: 0.5rem;"

                                    input {
                                        type = InputType.checkBox
                                        id = "theme-toggle"
                                    }

                                    div {
                                        // This div becomes the toggle button
                                    }
                                }
                            }
                        }


                        // 404 Error content
                        div {
                            classes = setOf("error-content")

                            // Error code
                            h1 {
                                classes = setOf("error-code")
                                +"404"
                            }

                            // Error title
                            h2 {
                                classes = setOf("error-title")
                                +"Page Not Found"
                            }

                            // Action buttons
                            div {
                                classes = setOf("action-buttons")


                                // Go back button
                                button {
                                    id = "go-back-button"
                                    classes = setOf("action-button", "secondary-button")
                                    span {
                                        classes = setOf("material-icons")
                                        +"arrow_back"
                                    }
                                    +"Go Back"
                                }
                            }
                        }

                        // Footer
                        footer {
                            classes = setOf("footer")
                            div {
                                classes = setOf("footer-content")
                                p {
                                    +"© ${java.time.Year.now().value} Androidplay. All rights reserved."
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}