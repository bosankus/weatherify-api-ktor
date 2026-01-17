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
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.link
import kotlinx.html.main
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
                    WebResources.includeGoogleTag(this)
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
                                .notfound-page {
                                    min-height: 100vh;
                                    display: flex;
                                    flex-direction: column;
                                    background: var(--content-bg);
                                }

                                .notfound-main {
                                    flex: 1;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    padding: 2.5rem 1.5rem 3rem;
                                }

                                .notfound-shell {
                                    width: min(1040px, 100%);
                                    display: grid;
                                    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
                                    gap: 2rem;
                                    align-items: center;
                                }

                                .notfound-card {
                                    background: var(--card-bg);
                                    border: 1px solid var(--card-border);
                                    border-radius: 18px;
                                    padding: 2rem;
                                    box-shadow: 0 18px 40px rgba(0, 0, 0, 0.12);
                                }

                                .notfound-hero {
                                    display: flex;
                                    flex-direction: column;
                                    gap: 1rem;
                                }

                                .notfound-code {
                                    font-size: clamp(2.8rem, 7vw, 4.5rem);
                                    font-weight: 700;
                                    margin: 0;
                                    line-height: 1;
                                    letter-spacing: -0.04em;
                                    background: linear-gradient(135deg, #6366f1, #4f46e5);
                                    -webkit-background-clip: text;
                                    -webkit-text-fill-color: transparent;
                                    background-clip: text;
                                }

                                .notfound-title {
                                    font-size: clamp(1.6rem, 4vw, 2.3rem);
                                    font-weight: 700;
                                    margin: 0;
                                    color: var(--heading-color);
                                }

                                .notfound-message {
                                    font-size: 1rem;
                                    color: var(--text-secondary);
                                    line-height: 1.6;
                                    margin: 0;
                                }

                                .notfound-actions {
                                    display: flex;
                                    flex-wrap: wrap;
                                    gap: 0.75rem;
                                    margin-top: 1rem;
                                }

                                .notfound-btn {
                                    display: inline-flex;
                                    align-items: center;
                                    gap: 0.5rem;
                                    padding: 0.65rem 1.1rem;
                                    border-radius: 10px;
                                    font-weight: 600;
                                    font-size: 0.95rem;
                                    cursor: pointer;
                                    border: 1px solid transparent;
                                    text-decoration: none;
                                    transition: transform 0.2s ease, box-shadow 0.2s ease;
                                }

                                .notfound-btn-primary {
                                    background: linear-gradient(135deg, #6366f1, #4f46e5);
                                    color: #ffffff;
                                }

                                .notfound-btn-secondary {
                                    background: var(--card-bg);
                                    color: var(--text-color);
                                    border-color: var(--card-border);
                                }

                                .notfound-btn:hover {
                                    transform: translateY(-1px);
                                    box-shadow: 0 8px 18px rgba(0, 0, 0, 0.12);
                                }

                                .notfound-info {
                                    display: grid;
                                    gap: 1rem;
                                }

                                .notfound-tip {
                                    padding: 1rem 1.2rem;
                                    border-radius: 14px;
                                    background: var(--card-bg);
                                    border: 1px solid var(--card-border);
                                }

                                .notfound-tip-title {
                                    margin: 0 0 0.35rem;
                                    font-weight: 600;
                                    color: var(--heading-color);
                                }

                                .notfound-tip-text {
                                    margin: 0;
                                    color: var(--text-secondary);
                                    font-size: 0.92rem;
                                    line-height: 1.5;
                                }

                                @media (max-width: 720px) {
                                    .notfound-main {
                                        padding: 2rem 1rem 2.5rem;
                                    }

                                    .notfound-card {
                                        padding: 1.6rem;
                                    }

                                    .notfound-actions {
                                        flex-direction: column;
                                    }

                                    .notfound-btn {
                                        width: 100%;
                                        justify-content: center;
                                    }
                                }
                                """
                            )
                        }
                    }

                    // Include minimal JavaScript for error page (without auth.js)
                    WebResources.includeErrorPageJs(this)
                    WebResources.includeHeaderJs(this)

                    // Include page-specific JavaScript
                    script {
                        unsafe {
                            raw(
                                """
                                // 404 page specific JavaScript
                                document.addEventListener('DOMContentLoaded', function() {
                                    if (typeof initializeHeader === 'function') {
                                        initializeHeader({
                                            homeUrl: '/',
                                            subtitle: 'NOT FOUND',
                                            actions: [ { type: 'theme-toggle' } ]
                                        });
                                    }
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
                        classes = setOf("notfound-page")
                        div {
                            createHeader(this)
                        }
                        main {
                            classes = setOf("notfound-main")
                            div {
                                classes = setOf("notfound-shell")
                                div {
                                    classes = setOf("notfound-card", "notfound-hero")
                                    h1 {
                                        classes = setOf("notfound-code")
                                        +"404"
                                    }
                                    h2 {
                                        classes = setOf("notfound-title")
                                        +"We can’t find that page"
                                    }
                                    p {
                                        classes = setOf("notfound-message")
                                        +"The page you’re looking for doesn’t exist or was moved. Try one of the options below."
                                    }
                                    div {
                                        classes = setOf("notfound-actions")
                                        a {
                                            href = "/"
                                            classes = setOf("notfound-btn", "notfound-btn-primary")
                                            span {
                                                classes = setOf("material-icons")
                                                +"home"
                                            }
                                            +"Go to Home"
                                        }
                                        button {
                                            id = "go-back-button"
                                            classes = setOf("notfound-btn", "notfound-btn-secondary")
                                            span {
                                                classes = setOf("material-icons")
                                                +"arrow_back"
                                            }
                                            +"Go Back"
                                        }
                                    }
                                }
                                div {
                                    classes = setOf("notfound-info")
                                    div {
                                        classes = setOf("notfound-tip")
                                        h2 {
                                            classes = setOf("notfound-tip-title")
                                            +"Check the URL"
                                        }
                                        p {
                                            classes = setOf("notfound-tip-text")
                                            +"Look for any typos or missing sections in the address."
                                        }
                                    }
                                    div {
                                        classes = setOf("notfound-tip")
                                        h2 {
                                            classes = setOf("notfound-tip-title")
                                            +"Need help?"
                                        }
                                        p {
                                            classes = setOf("notfound-tip-text")
                                            +"Return to the main dashboard or contact support if the issue persists."
                                        }
                                    }
                                }
                            }
                        }
                        div {
                            createFooter(this)
                        }
                    }
                }
            }
        }
        
        // Handle other HTTP methods for /not-found (POST, PUT, DELETE)
        // Return JSON error response for API clients
        post {
            call.respondError(
                Constants.Messages.ENDPOINT_NOT_FOUND,
                mapOf(
                    "path" to "/not-found",
                    "method" to "POST",
                    "errorType" to "NOT_FOUND"
                ),
                HttpStatusCode.NotFound
            )
        }
        
        put {
            call.respondError(
                Constants.Messages.ENDPOINT_NOT_FOUND,
                mapOf(
                    "path" to "/not-found",
                    "method" to "PUT",
                    "errorType" to "NOT_FOUND"
                ),
                HttpStatusCode.NotFound
            )
        }
        
        delete {
            call.respondError(
                Constants.Messages.ENDPOINT_NOT_FOUND,
                mapOf(
                    "path" to "/not-found",
                    "method" to "DELETE",
                    "errorType" to "NOT_FOUND"
                ),
                HttpStatusCode.NotFound
            )
        }
    }
}
