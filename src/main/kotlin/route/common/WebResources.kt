package bose.ankush.route.common

import kotlinx.html.HEAD
import kotlinx.html.script
import kotlinx.html.style
import kotlinx.html.unsafe

/**
 * Helper class for including shared web resources in HTML responses
 */
object WebResources {

    /**
     * Include Google tag (gtag.js) immediately after the <head> element
     */
    fun includeGoogleTag(head: HEAD) {
        // External gtag.js loader
        head.script {
            attributes["async"] = ""
            attributes["src"] = "https://www.googletagmanager.com/gtag/js?id=G-EBVRVNN6JF"
        }
        // Inline gtag config
        head.script {
            unsafe {
                raw(
                    """
                    window.dataLayer = window.dataLayer || [];
                    function gtag(){dataLayer.push(arguments);}
                    gtag('js', new Date());

                    gtag('config', 'G-EBVRVNN6JF');
                    """.trimIndent()
                )
            }
        }
    }

    // CSS file contents
    private val baseCss = readResourceFile("/web/css/base.css")
    private val componentsCss = readResourceFile("/web/css/components.css")
    private val themeToggleCss = readResourceFile("/web/css/theme-toggle.css")

    // JavaScript file contents
    private val themeJs = readResourceFile("/web/js/theme.js")
    private val utilsJs = readResourceFile("/web/js/utils.js")
    private val authJs = readResourceFile("/web/js/auth.js")
    private val adminJs = readResourceFile("/web/js/admin.js")
    private val decodeJs = readResourceFile("/web/js/decode.js")

    /**
     * Read a file from the resources directory
     * @param path The path to the file in the resources directory
     * @return The contents of the file as a string
     */
    private fun readResourceFile(path: String): String {
        return try {
            val resourceUrl = WebResources::class.java.getResource(path)
            if (resourceUrl != null) {
                resourceUrl.readText()
            } else {
                println("Resource not found: $path")
                ""
            }
        } catch (e: Exception) {
            println("Error reading resource: $path - ${e.message}")
            ""
        }
    }

    /**
     * Include shared CSS in the HTML head
     * @param head The HEAD element to add the CSS to
     */
    fun includeSharedCss(head: HEAD) {
        // Include all CSS directly in the HTML
        head.style {
            unsafe {
                raw(baseCss)
                raw(componentsCss)
                raw(themeToggleCss)
            }
        }
    }

    /**
     * Include shared JavaScript in the HTML head
     * @param head The HEAD element to add the JavaScript to
     */
    fun includeSharedJs(head: HEAD) {
        // Include all JavaScript directly in the HTML
        head.script {
            unsafe {
                raw(themeJs)
                raw(utilsJs)
                raw(authJs)

                // Initialize the app with a single DOMContentLoaded event listener
                raw(
                    """
                    // Single initialization point for the entire application
                    document.addEventListener('DOMContentLoaded', function() {
                        // Initialize all application components
                        initializeApp();
                    });
                """
                )
            }
        }
    }

    /**
     * Include minimal JavaScript for error pages (without auth.js)
     * @param head The HEAD element to add the JavaScript to
     */
    fun includeErrorPageJs(head: HEAD) {
        // Include only theme and utils JavaScript for error pages
        head.script {
            unsafe {
                raw(themeJs)
                raw(utilsJs)

                // Initialize the app with a single DOMContentLoaded event listener
                raw(
                    """
                    // Single initialization point for the error page
                    document.addEventListener('DOMContentLoaded', function() {
                        // Initialize only theme and basic functionality
                        try {
                            initializeTheme();
                            initializeClickFeedback();
                        } catch (error) {
                            console.error('Error during error page initialization:', error);
                        }
                    });
                """
                )
            }
        }
    }

    /**
     * Include admin JavaScript in the HTML head
     * @param head The HEAD element to add the JavaScript to
     */
    fun includeAdminJs(head: HEAD) {
        // Include Chart.js from CDN first so it's available to admin.js
        head.script {
            attributes["src"] = "https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"
            attributes["crossorigin"] = "anonymous"
        }
        // Include admin JavaScript directly in the HTML
        head.script {
            unsafe {
                raw(adminJs)
            }
        }
    }

    /**
     * Include decode page JavaScript in the HTML head
     */
    fun includeDecodeJs(head: HEAD) {
        // Include jsonlint library via CDN to get line/column error reporting
        head.script {
            attributes["src"] =
                "https://cdn.jsdelivr.net/npm/jsonlint-mod@1.7.6/dist/jsonlint.min.js"
            attributes["crossorigin"] = "anonymous"
        }
        head.script {
            unsafe {
                raw(decodeJs)
            }
        }
    }
}
