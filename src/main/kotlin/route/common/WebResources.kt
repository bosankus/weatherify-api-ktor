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

    /**
     * Include Firebase Analytics for web tracking
     * This initializes Firebase with the project configuration and enables analytics
     */
    fun includeFirebaseAnalytics(head: HEAD) {
        head.script {
            attributes["type"] = "module"
            unsafe {
                raw(
                    """
                    // Import the functions you need from the SDKs you need
                    import { initializeApp } from "https://www.gstatic.com/firebasejs/12.4.0/firebase-app.js";
                    import { getAnalytics } from "https://www.gstatic.com/firebasejs/12.4.0/firebase-analytics.js";
                    
                    // Your web app's Firebase configuration
                    const firebaseConfig = {
                      apiKey: "AIzaSyDwt4UFPfOPAFJL876I7nkAIFU9jM4r5ho",
                      authDomain: "weatherify-mvvm.firebaseapp.com",
                      projectId: "weatherify-mvvm",
                      storageBucket: "weatherify-mvvm.firebasestorage.app",
                      messagingSenderId: "1017382896100",
                      appId: "1:1017382896100:web:f3742e383a820e7c7447f1",
                      measurementId: "G-RVYWKNSDS7"
                    };
                    
                    // Initialize Firebase
                    try {
                      const app = initializeApp(firebaseConfig);
                      const analytics = getAnalytics(app);
                      console.log('Firebase Analytics initialized successfully');
                    } catch (error) {
                      console.error('Error initializing Firebase Analytics:', error);
                    }
                    """.trimIndent()
                )
            }
        }
    }

    // CSS file contents
    private val baseCss = readResourceFile("/web/css/base.css")
    private val componentsCss = readResourceFile("/web/css/components.css")
    private val themeToggleCss = readResourceFile("/web/css/theme-toggle.css")
    private val adminHeaderCss = readResourceFile("/web/css/admin-header.css")
    private val adminUsersCss = readResourceFile("/web/css/admin-users.css")
    private val headerCss = readResourceFile("/web/css/header.css")

    // JavaScript file contents
    private val themeJs = readResourceFile("/web/js/theme.js")
    private val utilsJs = readResourceFile("/web/js/utils.js")
    private val authJs = readResourceFile("/web/js/auth.js")
    private val adminDataCacheJs = readResourceFile("/web/js/admin-data-cache.js")
    private val adminTabManagerJs = readResourceFile("/web/js/admin-tab-manager.js")
    private val adminUsersJs = readResourceFile("/web/js/admin-users.js")
    private val adminJs = readResourceFile("/web/js/admin.js")
    private val financeAdminJs = readResourceFile("/web/js/finance-admin.js")
    private val refundAdminJs = readResourceFile("/web/js/refund-admin.js")
    private val serviceCatalogAdminJs = readResourceFile("/web/js/service-catalog-admin.js")
    private val reportsChartsJs = readResourceFile("/web/js/reports-charts.js")
    private val adminHeaderJs = readResourceFile("/web/js/admin-header.js")
    private val headerJs = readResourceFile("/web/js/header.js")
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
                raw(headerCss)
                raw(adminUsersCss)
            }
        }
    }

    /**
     * Include admin header CSS in the HTML head
     * @param head The HEAD element to add the CSS to
     */
    fun includeAdminHeaderCss(head: HEAD) {
        head.style {
            unsafe {
                raw(adminHeaderCss)
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
                raw(headerJs)

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
        // Include performance optimization scripts FIRST (before other admin scripts)
        head.script {
            unsafe {
                raw(adminDataCacheJs)
            }
        }
        head.script {
            unsafe {
                raw(adminTabManagerJs)
            }
        }
        // Include admin users module (before main admin.js)
        head.script {
            unsafe {
                raw(adminUsersJs)
            }
        }
        // Include admin JavaScript directly in the HTML
        head.script {
            unsafe {
                raw(adminJs)
            }
        }
        // Include finance admin JavaScript
        head.script {
            unsafe {
                raw(financeAdminJs)
            }
        }
        // Include refund admin JavaScript
        head.script {
            unsafe {
                raw(refundAdminJs)
            }
        }
        // Include service catalog admin JavaScript
        head.script {
            unsafe {
                raw(serviceCatalogAdminJs)
            }
        }
        // Include reports charts JavaScript
        head.script {
            unsafe {
                raw(reportsChartsJs)
            }
        }
        // Ensure admin dashboard initializes after DOM is ready
        head.script {
            unsafe {
                raw(
                    """
                    document.addEventListener('DOMContentLoaded', function() {
                        try {
                            if (typeof initializeAdmin === 'function') {
                                initializeAdmin();
                            }
                        } catch (e) {
                            console.error('Error initializing admin dashboard:', e);
                        }
                    });
                    """
                )
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
