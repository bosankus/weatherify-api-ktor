package bose.ankush.route

import bose.ankush.data.model.SubscriptionStatus
import bose.ankush.data.model.UserRole
import bose.ankush.route.common.WebResources
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import config.Environment
import config.JwtConfig
import domain.model.Result
import domain.service.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import util.AuthHelper
import util.AuthHelper.authenticateAdmin
import util.AuthHelper.getAuthenticatedAdminOrRespond
import util.AuthHelper.isAdminToken
import util.AuthHelper.isTokenValid
import util.Constants
import java.time.Year

@Serializable
data class AdminLoginRequest(val email: String, val password: String)

@Serializable
data class AdminCancelSubscriptionRequest(val userEmail: String)

/**
 * Helper function to set up common HTML head elements
 */
fun HEAD.setupHead(title: String, includeAdminJs: Boolean = false) {
    WebResources.includeGoogleTag(this)
    WebResources.includeFirebaseAnalytics(this)
    title { +title }
    meta { charset = "UTF-8" }
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

    // Include shared JavaScript
    WebResources.includeSharedJs(this)


    // Include admin JavaScript if needed
    if (includeAdminJs) {
        WebResources.includeAdminJs(this)
    }
}

/**
 * Helper function to create the admin header with logo and theme toggle
 * Uses the new admin-header component structure
 */
fun createHeader(container: DIV) {
    container.header {
        classes = setOf("app-header")
        attributes["role"] = "banner"

        div {
            classes = setOf("app-header__container")

            // Brand section with logo and subtitle
            div {
                classes = setOf("app-header__brand")
                a {
                    href = "/weather"
                    id = "header-logo-link"
                    classes = setOf("app-header__logo-link")
                    attributes["aria-label"] = "Androidplay Home"
                    h1 {
                        classes = setOf("app-header__logo")
                        +"Androidplay"
                    }
                }
                span {
                    id = "header-subtitle"
                    classes = setOf("app-header__subtitle")
                    attributes["data-default-subtitle"] = "ADMIN PORTAL"
                    +"ADMIN PORTAL"
                }
            }

            // Spacer for flex layout
            div {
                classes = setOf("app-header__spacer")
            }

            // Actions container - populated dynamically by header.js
            div {
                id = "header-actions"
                classes = setOf("app-header__actions")
                attributes["role"] = "navigation"
                attributes["aria-label"] = "Header actions"
            }
        }
    }
}

/**
 * Helper function to create the footer
 */
fun createFooter(container: DIV) {
    container.footer {
        classes = setOf("footer")
        div {
            classes = setOf("footer-content")
            div {
                classes = setOf("footer-copyright")
                +"© ${Year.now().value} Androidplay. All rights reserved."
            }
        }
    }
}

/**
 * Helper function to serve the admin login page
 */
private suspend fun serveLoginPage(
    call: ApplicationCall,
    errorMessage: String? = null
) {
    call.respondHtml(HttpStatusCode.OK) {
        attributes["lang"] = "en"
        head {
            setupHead("Admin Login - Androidplay Weather API")

            // Include page-specific JavaScript if needed
            script {
                unsafe {
                    raw(
                        """
                        // Admin login page specific JavaScript
                        function initializeApp() {
                            // Initialize shared header component
                            if (typeof initializeHeader === 'function') {
                                initializeHeader({
                                    homeUrl: '/admin',
                                    subtitle: 'ADMIN PORTAL',
                                    actions: [ { type: 'theme-toggle' } ]
                                });
                            }

                            // Initialize theme toggle functionality
                            initializeTheme();

                            // Initialize banner notification system
                            ensureBannerStyles();

                            // Initialize custom checkbox styles
                            ensureCheckboxStyles();

                            // Initialize login form
                            initializeLoginForm();
                        }

                        // Ensure custom checkbox styles are available
                        function ensureCheckboxStyles() {
                            if (document.getElementById('custom-checkbox-styles')) return;
                            const style = document.createElement('style');
                            style.id = 'custom-checkbox-styles';
                            style.textContent = `
                                input[type="checkbox"] {
                                    appearance: none;
                                    -webkit-appearance: none;
                                    -moz-appearance: none;
                                    width: 20px;
                                    height: 20px;
                                    border: 2px solid var(--card-border, #d1d5db);
                                    border-radius: 6px;
                                    background: var(--card-bg, #ffffff);
                                    cursor: pointer;
                                    position: relative;
                                    transition: all 0.2s ease;
                                    flex-shrink: 0;
                                }
                                input[type="checkbox"]:hover {
                                    border-color: #6366f1;
                                    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
                                }
                                input[type="checkbox"]:checked {
                                    background: #6366f1;
                                    border-color: #6366f1;
                                }
                                input[type="checkbox"]:checked::after {
                                    content: '';
                                    position: absolute;
                                    left: 6px;
                                    top: 2px;
                                    width: 5px;
                                    height: 10px;
                                    border: solid white;
                                    border-width: 0 2px 2px 0;
                                    transform: rotate(45deg);
                                }
                                input[type="checkbox"]:focus {
                                    outline: none;
                                    border-color: #6366f1;
                                    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
                                }
                                input[type="checkbox"]:disabled {
                                    opacity: 0.5;
                                    cursor: not-allowed;
                                }
                            `;
                            document.head.appendChild(style);
                        }

                        // Ensure banner styles are available
                        function ensureBannerStyles() {
                            if (document.getElementById('banner-styles')) return;
                            const style = document.createElement('style');
                            style.id = 'banner-styles';
                            style.textContent = `
                                .banner-container {
                                    position: fixed;
                                    top: 20px;
                                    right: 20px;
                                    z-index: 10000;
                                    display: flex;
                                    flex-direction: column;
                                    gap: 12px;
                                    pointer-events: none;
                                    max-width: 420px;
                                }
                                .banner {
                                    display: flex;
                                    align-items: center;
                                    gap: 12px;
                                    padding: 16px 18px;
                                    border-radius: 12px;
                                    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12), 0 2px 8px rgba(0, 0, 0, 0.08);
                                    backdrop-filter: blur(20px) saturate(180%);
                                    -webkit-backdrop-filter: blur(20px) saturate(180%);
                                    border: 1px solid rgba(255, 255, 255, 0.18);
                                    opacity: 0;
                                    transform: translateX(450px);
                                    transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
                                    pointer-events: auto;
                                    cursor: pointer;
                                    min-width: 320px;
                                }
                                .banner-visible { opacity: 1; transform: translateX(0); }
                                .banner-hide { opacity: 0; transform: translateX(450px); }
                                .banner-icon-wrapper {
                                    flex-shrink: 0;
                                    width: 32px;
                                    height: 32px;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    border-radius: 50%;
                                    font-size: 16px;
                                    font-weight: 700;
                                    background: rgba(255, 255, 255, 0.25);
                                    backdrop-filter: blur(8px);
                                    -webkit-backdrop-filter: blur(8px);
                                }
                                .banner-content {
                                    flex: 1;
                                    min-width: 0;
                                    display: flex;
                                    flex-direction: column;
                                    gap: 3px;
                                }
                                .banner-title { font-weight: 600; font-size: 14px; line-height: 1.4; }
                                .banner-message { font-size: 13px; line-height: 1.5; opacity: 0.95; word-wrap: break-word; }
                                .banner-close {
                                    flex-shrink: 0;
                                    width: 28px;
                                    height: 28px;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    border-radius: 50%;
                                    font-size: 18px;
                                    opacity: 0.7;
                                    transition: all 0.2s ease;
                                    cursor: pointer;
                                    margin-left: 4px;
                                    background: rgba(255, 255, 255, 0.1);
                                }
                                .banner-close:hover {
                                    opacity: 1;
                                    background: rgba(255, 255, 255, 0.2);
                                    transform: scale(1.1);
                                }
                                .banner-success {
                                    background: rgba(16, 185, 129, 0.15);
                                    border-color: rgba(16, 185, 129, 0.3);
                                    color: #10b981;
                                }
                                .banner-error {
                                    background: rgba(239, 68, 68, 0.15);
                                    border-color: rgba(239, 68, 68, 0.3);
                                    color: #ef4444;
                                }
                                .banner-info {
                                    background: rgba(59, 130, 246, 0.15);
                                    border-color: rgba(59, 130, 246, 0.3);
                                    color: #3b82f6;
                                }
                                .banner-warning {
                                    background: rgba(245, 158, 11, 0.15);
                                    border-color: rgba(245, 158, 11, 0.3);
                                    color: #f59e0b;
                                }
                                @media (max-width: 768px) {
                                    .banner-container {
                                        top: 10px;
                                        right: 10px;
                                        left: 10px;
                                        max-width: none;
                                    }
                                    .banner {
                                        min-width: auto;
                                        padding: 14px 16px;
                                    }
                                }
                            `;
                            document.head.appendChild(style);
                        }

                        // Show banner notification
                        function showBanner(type, message, timeout) {
                            const duration = typeof timeout === 'number' ? timeout : 5000;
                            let container = document.getElementById('banner-container');
                            if (!container) {
                                container = document.createElement('div');
                                container.id = 'banner-container';
                                container.className = 'banner-container';
                                document.body.appendChild(container);
                            }

                            const banner = document.createElement('div');
                            const t = ['success', 'error', 'info', 'warning'].includes(type) ? type : 'info';
                            banner.className = 'banner banner-' + t;

                            const iconWrapper = document.createElement('div');
                            iconWrapper.className = 'banner-icon-wrapper';
                            const iconMap = { success: '✓', error: '✕', info: 'ℹ', warning: '⚠' };
                            iconWrapper.textContent = iconMap[t] || 'ℹ';

                            const content = document.createElement('div');
                            content.className = 'banner-content';

                            const text = document.createElement('div');
                            text.className = 'banner-message';
                            text.textContent = message || '';
                            content.appendChild(text);

                            const closeBtn = document.createElement('div');
                            closeBtn.className = 'banner-close';
                            closeBtn.textContent = '×';

                            banner.appendChild(iconWrapper);
                            banner.appendChild(content);
                            banner.appendChild(closeBtn);
                            container.appendChild(banner);

                            requestAnimationFrame(() => {
                                requestAnimationFrame(() => {
                                    banner.classList.add('banner-visible');
                                });
                            });

                            const hide = () => {
                                banner.classList.remove('banner-visible');
                                banner.classList.add('banner-hide');
                                setTimeout(() => {
                                    if (banner.parentNode) banner.parentNode.removeChild(banner);
                                }, 400);
                            };

                            const timer = setTimeout(hide, duration);
                            banner.addEventListener('click', () => { clearTimeout(timer); hide(); });
                            closeBtn.addEventListener('click', (e) => { e.stopPropagation(); clearTimeout(timer); hide(); });
                        }

                        function initializeLoginForm() {
                            const loginForm = document.getElementById('login-form');
                            const loginButton = document.getElementById('login-button');

                            if (!loginForm || !loginButton) return;

                            // Check URL parameters for error messages
                            const urlParams = new URLSearchParams(window.location.search);
                            const errorParam = urlParams.get('error');

                            if (errorParam) {
                                if (errorParam === 'auth_required') {
                                    showBanner('error', 'Please login to access the admin dashboard');
                                } else if (errorParam === 'session_expired') {
                                    showBanner('warning', 'Your session has expired. Please login again.');
                                } else if (errorParam === 'access_denied') {
                                    showBanner('error', 'Access denied. You do not have administrator privileges.');
                                }
                            }

                            // Check if already logged in with admin role
                            const token = localStorage.getItem('jwt_token');
                            if (token) {
                                fetch('/admin/dashboard', {
                                    headers: { 'Authorization': 'Bearer ' + token }
                                })
                                .then(response => {
                                    if (response.ok) {
                                        window.location.href = '/admin/dashboard';
                                    }
                                })
                                .catch(error => {
                                    console.error('Error checking authentication:', error);
                                    localStorage.removeItem('jwt_token');
                                });
                            }

                            // Handle form submission
                            loginForm.addEventListener('submit', function(e) {
                                e.preventDefault();

                                const email = document.getElementById('email').value;
                                const password = document.getElementById('password').value;

                                if (!email || !password) {
                                    showBanner('error', 'Please enter both email and password');
                                    return;
                                }

                                loginButton.classList.add('loading');
                                loginButton.disabled = true;

                                fetch('/admin/login', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: JSON.stringify({ email: email, password: password })
                                })
                                .then(response => response.json())
                                .then(data => {
                                    if (data.status === true) {
                                        showBanner('success', 'Login successful! Redirecting to dashboard...');

                                        localStorage.setItem('jwt_token', data.data.token);

                                        try {
                                            const token = data.data.token;
                                            const tokenParts = token.split('.');
                                            if (tokenParts.length === 3) {
                                                const payload = JSON.parse(atob(tokenParts[1]));
                                                const userInfo = {
                                                    email: payload.email || email,
                                                    role: payload.role || 'USER',
                                                    isActive: payload.isActive === 'false' ? false : Boolean(payload.isActive)
                                                };
                                                localStorage.setItem('user_info', JSON.stringify(userInfo));
                                            }
                                        } catch (e) {
                                            console.error('Error parsing JWT token:', e);
                                        }

                                        setTimeout(() => {
                                            const intendedDestination = sessionStorage.getItem('intendedDestination');
                                            if (typeof navigateToAdminPage === 'function') {
                                                if (intendedDestination && intendedDestination.startsWith('/admin')) {
                                                    sessionStorage.removeItem('intendedDestination');
                                                    navigateToAdminPage(intendedDestination);
                                                } else {
                                                    navigateToAdminPage('/admin/dashboard');
                                                }
                                            } else {
                                                window.location.href = '/admin/login?error=auth_required';
                                            }
                                        }, 1000);
                                    } else {
                                        loginButton.classList.remove('loading');
                                        loginButton.disabled = false;
                                        showBanner('error', data.message || 'Invalid email or password');
                                    }
                                })
                                .catch(error => {
                                    loginButton.classList.remove('loading');
                                    loginButton.disabled = false;
                                    showBanner('error', 'An error occurred. Please try again.');
                                    console.error('Login error:', error);
                                });
                            });
                        }
                        """
                    )
                }
            }
        }
        body {
            div {
                classes = setOf("container", "main-container")
                style =
                    "max-width: 100%; width: 100%; margin: 0; padding: 1rem 2rem; box-sizing: border-box;"

                // Header with logo and theme toggle
                createHeader(this)

                // Content area with login form
                div {
                    classes = setOf("content-area")
                    style = "margin-top: 2rem;"
                    h2 {
                        classes = setOf("login-heading")
                        +"Admin Login"
                    }
                    span {
                        classes = setOf("login-subtitle")
                        +"Sign in to access the admin features and manage your weather API."
                    }
                    // Login form
                    form {
                        id = "login-form"
                        classes = setOf("login-form")
                        div {
                            classes = setOf("form-group")
                            label {
                                attributes["for"] = "email"
                                +"Email"
                            }
                            input {
                                type = InputType.email
                                id = "email"
                                name = "email"
                                attributes["required"] = ""
                                attributes["autocomplete"] = "email"
                            }
                        }

                        div {
                            classes = setOf("form-group")
                            label {
                                attributes["for"] = "password"
                                +"Password"
                            }
                            input {
                                type = InputType.password
                                id = "password"
                                name = "password"
                                attributes["required"] = ""
                                attributes["autocomplete"] = "current-password"
                            }
                        }

                        button {
                            attributes["type"] = "submit"
                            id = "login-button"
                            classes = setOf("login-button")
                            style =
                                "display: flex; align-items: center; justify-content: center; gap: 0.5em; width: 100%;"
                            span {
                                id = "login-loading"
                                classes = setOf("loading-spinner")
                                style = "display: none; margin-right: 0.5em;"
                            }
                            span {
                                style = "text-align: center; width: 100%;"
                                +"Login"
                            }
                        }
                    }
                }

                // Footer
                createFooter(this)
            }
        }
    }
}

/**
 * Admin authentication route
 * Provides login UI for admin users and redirects to dashboard when authenticated
 */
fun Route.adminAuthRoute() {

    val authService: AuthService by application.inject()
    val logger = LoggerFactory.getLogger("AdminAuthRoute")
    val pageName = "Admin Authentication - Androidplay Weather API"

    // Public route for login page - no authentication required
    route("/admin/login") {
        get {
            // Check if user is already authenticated as admin using unified helper
            val authHeader = call.request.headers["Authorization"]
            val token = if (authHeader != null && authHeader.startsWith("Bearer ")) {
                authHeader.substring(7)
            } else {
                call.request.cookies["jwt_token"]
            }

            if (token != null && isTokenValid(token) && isAdminToken(token)) {
                logger.info("Admin user already authenticated, redirecting to dashboard")

                // Set jwt_token cookie if it came from header
                if (authHeader != null) {
                    try {
                        val maxAgeSeconds = (Environment.getJwtExpiration() / 1000).toInt()
                        val isHttps = (
                            call.request.headers["X-Forwarded-Proto"]?.equals("https", true) == true ||
                                call.request.headers["X-Forwarded-Protocol"]?.equals("https", true) == true ||
                                call.request.headers["X-Forwarded-Ssl"]?.equals("on", true) == true
                            )
                        call.response.cookies.append(
                            Cookie(
                                name = "jwt_token",
                                value = token,
                                path = "/",
                                httpOnly = true,
                                secure = isHttps,
                                maxAge = maxAgeSeconds
                            )
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to set auth cookie on /admin/login redirect: ${e.message}")
                    }
                }

                call.respondRedirect("/admin/dashboard", permanent = false)
                return@get
            }

            // Get error message from query parameter if it exists
            val errorParam = call.request.queryParameters["error"]
            val errorMessage = when (errorParam) {
                "auth_required" -> "Please login to access the admin dashboard"
                "session_expired" -> "Your session has expired. Please login again"
                "access_denied" -> "Access denied. You need administrator privileges"
                "account_inactive" -> "Your account is inactive. Please contact support"
                "admin_required" -> "Admin access required. You don't have sufficient privileges"
                else -> null
            }

            logger.info("Serving admin login page, error: $errorParam")
            serveLoginPage(call, errorMessage)
        }

        post {
            try {
                val loginRequest = call.receive<AdminLoginRequest>()
                logger.info("Processing login request for email: ${loginRequest.email}")

                when (val result =
                    authService.loginUser(loginRequest.email, loginRequest.password)) {
                    is Result.Success<String> -> {
                        val token = result.data

                        // Verify JWT token with error handling
                        val decodedJWT = try {
                            JwtConfig.verifier.verify(token)
                        } catch (e: Exception) {
                            logger.error("Failed to verify JWT token: ${e.message}")
                            call.respondError(
                                "Authentication failed: Invalid token",
                                null,
                                HttpStatusCode.Unauthorized
                            )
                            return@post
                        }

                        // Extract claims with null safety
                        val email =
                            decodedJWT.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString() ?: ""
                        val role =
                            decodedJWT.getClaim(Constants.Auth.JWT_CLAIM_ROLE).asString() ?: ""
                        val isActive = decodedJWT.getClaim("isActive")?.asBoolean()

                        isActive?.let {
                            if (!it) {
                                logger.warn("Inactive account attempted login: $email")
                                call.respondError(
                                    "Account inactive",
                                    null,
                                    HttpStatusCode.Forbidden
                                )
                                return@post
                            }
                        }

                        // Create a simplified response data structure with only string values
                        // to avoid serialization issues
                        val responseData = mapOf(
                            "token" to token,
                            "email" to email,
                            "role" to role,
                            "isActive" to (isActive?.toString() ?: "true")
                        )

                        // Log the response data for debugging
                        logger.info(
                            "Response data: token=${
                                token.substring(
                                    0,
                                    10
                                )
                            }..., email=$email, role=$role, isActive=$isActive"
                        )

                        // Debug logging to identify comparison issues
                        logger.info(
                            "Role comparison - User role: '$role', ADMIN role: '${UserRole.ADMIN.name}', Equal: ${
                                role.equals(UserRole.ADMIN.name, ignoreCase = true)
                            }"
                        )
                        logger.info("isActive value: $isActive, Type: ${isActive?.javaClass?.name}")

                        // Fix: More robust role comparison and handle null roles and isActive
                        val isRoleAdmin = role.trim().equals(UserRole.ADMIN.name, ignoreCase = true)

                        if (isRoleAdmin) {
                            logger.info("Admin login successful for: $email, role: $role")

                            // Set jwt_token cookie so browser navigations to /admin pages remain authenticated
                            try {
                                val maxAgeSeconds =
                                    (Environment.getJwtExpiration() / 1000).toInt()
                                val isHttps = (
                                    call.request.headers["X-Forwarded-Proto"]?.equals(
                                        "https",
                                        true
                                    ) == true ||
                                        call.request.headers["X-Forwarded-Protocol"]?.equals(
                                            "https",
                                            true
                                        ) == true ||
                                        call.request.headers["X-Forwarded-Ssl"]?.equals(
                                            "on",
                                            true
                                        ) == true
                                    )
                                call.response.cookies.append(
                                    Cookie(
                                        name = "jwt_token",
                                        value = token,
                                        path = "/",
                                        httpOnly = true,
                                        secure = isHttps,
                                        maxAge = maxAgeSeconds
                                    )
                                )
                            } catch (e: Exception) {
                                logger.warn("Failed to set auth cookie on admin login: ${e.message}")
                            }

                            call.respondSuccess("Login successful", responseData)
                            return@post
                        }

                        // Log the specific reason for denial
                        logger.warn("Non-admin user attempted to login to admin area: $email, role: $role")

                        call.respondError(
                            "Access denied: You are not an admin",
                            null,
                            HttpStatusCode.Forbidden
                        )
                    }

                    is Result.Error -> {
                        logger.warn("Admin login failed for email: ${loginRequest.email}, reason: ${result.message}")
                        call.respondError(result.message, null, HttpStatusCode.Unauthorized)
                    }
                }
            } catch (e: Exception) {
                // Log detailed error information
                logger.error("Error processing admin login: ${e.message}")
                logger.error("Exception type: ${e.javaClass.name}")
                logger.error("Stack trace: ${e.stackTraceToString()}")

                // Respond with a more specific error message
                call.respondError("Login failed: ${e.message}", null, HttpStatusCode.BadRequest)
            }
        }
    }

    route("/admin") {
        // Root admin route - redirect to dashboard if authenticated admin, or to login page if not
        get {
            // Only redirect to dashboard if a valid admin JWT is present
            val jwtToken = call.request.cookies["jwt_token"]
            var isAdmin = false
            var userEmail: String? = null

            if (jwtToken != null) {
                try {
                    val decodedJWT = JwtConfig.verifier.verify(jwtToken)
                    userEmail = decodedJWT.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
                    isAdmin = JwtConfig.isAdmin(decodedJWT)
                } catch (e: Exception) {
                    // Invalid or expired token, clear cookie to break redirect loop
                    val isHttps = (
                        call.request.headers["X-Forwarded-Proto"]?.equals("https", true) == true ||
                            call.request.headers["X-Forwarded-Protocol"]?.equals(
                                "https",
                                true
                            ) == true ||
                            call.request.headers["X-Forwarded-Ssl"]?.equals("on", true) == true
                        )
                    call.response.cookies.append(
                        Cookie(
                            name = "jwt_token",
                            value = "",
                            path = "/",
                            httpOnly = true,
                            secure = isHttps,
                            maxAge = 0
                        )
                    )
                    // Optionally log the error for debugging
                    logger.warn("Failed to verify JWT in /admin: ${e.message}")
                }
            }

            if (isAdmin) {
                // User is authenticated and has ADMIN role, redirect to dashboard
                logger.info("Admin user authenticated: $userEmail, redirecting to dashboard")
                call.respondRedirect("/admin/dashboard", permanent = false)
                return@get
            } else if (jwtToken != null) {
                // Token exists but not admin, force login with error
                logger.warn("Non-admin or invalid JWT tried to access /admin: $userEmail")
                call.respondRedirect("/admin/login?error=admin_required", permanent = false)
                return@get
            }

            // User is not authenticated, redirect to login page
            logger.info("Unauthenticated user, redirecting to login page")
            call.respondRedirect("/admin/login", permanent = false)
        }

        // Admin subscription management endpoints
        route("/subscriptions") {
            // GET /admin/subscriptions - Get all subscriptions with pagination and filtering
            get {
                val admin = call.getAuthenticatedAdminOrRespond()
                if (admin == null) {
                    return@get
                }

                try {
                    // Parse and validate query parameters
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 10
                    val statusFilter = call.request.queryParameters["status"]?.let { statusStr ->
                        try {
                            SubscriptionStatus.valueOf(statusStr.uppercase())
                        } catch (e: IllegalArgumentException) {
                            call.respondError(
                                "Invalid status filter. Valid values: ACTIVE, EXPIRED, CANCELLED, GRACE_PERIOD",
                                null,
                                HttpStatusCode.BadRequest
                            )
                            return@get
                        }
                    }

                    // Validate pagination parameters
                    if (page < 1) {
                        call.respondError(
                            "Invalid page parameter. Must be >= 1",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@get
                    }

                    if (pageSize < 1 || pageSize > 100) {
                        call.respondError(
                            "Invalid pageSize parameter. Must be between 1 and 100",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@get
                    }

                    logger.info("Admin ${admin.email} requesting subscriptions: page=$page, pageSize=$pageSize, status=$statusFilter")

                    val subscriptionService: domain.service.SubscriptionService by application.inject()
                    when (val result = subscriptionService.getAllSubscriptions(page, pageSize, statusFilter)) {
                        is Result.Success -> {
                            call.respondSuccess(
                                "Subscriptions retrieved successfully",
                                result.data,
                                HttpStatusCode.OK
                            )
                        }

                        is Result.Error -> {
                            logger.error("Failed to get subscriptions: ${result.message}")
                            call.respondError(
                                result.message,
                                null,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing admin subscriptions request", e)
                    call.respondError(
                        "Failed to retrieve subscriptions: ${e.message}",
                        null,
                        HttpStatusCode.InternalServerError
                    )
                }
            }

            // GET /admin/subscriptions/analytics - Get subscription analytics
            get("/analytics") {
                val admin = call.getAuthenticatedAdminOrRespond()
                if (admin == null) {
                    return@get
                }

                try {
                    logger.info("Admin ${admin.email} requesting subscription analytics")

                    val subscriptionService: domain.service.SubscriptionService by application.inject()
                    when (val result = subscriptionService.getSubscriptionAnalytics()) {
                        is Result.Success -> {
                            call.respondSuccess(
                                "Analytics retrieved successfully",
                                result.data,
                                HttpStatusCode.OK
                            )
                        }

                        is Result.Error -> {
                            logger.error("Failed to get subscription analytics: ${result.message}")
                            call.respondError(
                                result.message,
                                null,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing subscription analytics request", e)
                    call.respondError(
                        "Failed to retrieve analytics: ${e.message}",
                        null,
                        HttpStatusCode.InternalServerError
                    )
                }
            }

            // POST /admin/subscriptions/cancel - Admin cancels a user's subscription
            post("/cancel") {
                val admin = call.getAuthenticatedAdminOrRespond()
                if (admin == null) {
                    logger.warn("Unauthorized cancel subscription attempt")
                    return@post
                }

                try {
                    logger.info("Received cancel subscription request from admin: ${admin.email}")
                    val requestBody = call.receiveText()
                    logger.info("Request body: $requestBody")

                    val request = try {
                        kotlinx.serialization.json.Json.decodeFromString<AdminCancelSubscriptionRequest>(requestBody)
                    } catch (e: Exception) {
                        logger.error("Failed to parse request body: $requestBody", e)
                        call.respondError(
                            "Invalid request format: ${e.message}",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }

                    // Validate request
                    if (request.userEmail.isBlank()) {
                        logger.warn("Empty user email in cancel request")
                        call.respondError(
                            "User email is required",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }

                    logger.info("Admin ${admin.email} cancelling subscription for user: ${request.userEmail}")

                    val subscriptionService: domain.service.SubscriptionService by application.inject()
                    when (val result = subscriptionService.cancelUserSubscription(admin.email, request.userEmail)) {
                        is Result.Success -> {
                            logger.info("Successfully cancelled subscription for ${request.userEmail}")
                            call.respondSuccess(
                                "Subscription cancelled successfully",
                                result.data,
                                HttpStatusCode.OK
                            )
                        }

                        is Result.Error -> {
                            logger.error("Failed to cancel subscription for ${request.userEmail}: ${result.message}")
                            call.respondError(
                                result.message,
                                null,
                                HttpStatusCode.BadRequest
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing admin subscription cancellation", e)
                    call.respondError(
                        "Failed to cancel subscription: ${e.message}",
                        null,
                        HttpStatusCode.InternalServerError
                    )
                }
            }
        }

        // Admin finance management endpoints
        route("/finance") {
            // GET /admin/finance/metrics - Get financial metrics
            get("/metrics") {
                val admin = call.getAuthenticatedAdminOrRespond()
                if (admin == null) {
                    return@get
                }

                try {
                    // Audit log: Financial metrics request
                    val timestamp = java.time.Instant.now().toString()
                    logger.info("[AUDIT] [$timestamp] Financial Metrics Request - Admin: ${admin.email}, Action: VIEW_FINANCIAL_METRICS")

                    val financialService: domain.service.FinancialService by application.inject()
                    when (val result = financialService.getFinancialMetrics()) {
                        is Result.Success -> {
                            logger.info("[AUDIT] [$timestamp] Financial Metrics Request - Admin: ${admin.email}, Status: SUCCESS")
                            call.respondSuccess(
                                "Financial metrics retrieved successfully",
                                result.data,
                                HttpStatusCode.OK
                            )
                        }

                        is Result.Error -> {
                            logger.error("[AUDIT] [$timestamp] Financial Metrics Request - Admin: ${admin.email}, Status: FAILED, Error: ${result.message}")
                            call.respondError(
                                result.message,
                                null,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                } catch (e: Exception) {
                    val timestamp = java.time.Instant.now().toString()
                    logger.error(
                        "[AUDIT] [$timestamp] Financial Metrics Request - Admin: ${admin.email}, Status: ERROR, Exception: ${e.message}",
                        e
                    )
                    call.respondError(
                        "Failed to retrieve financial metrics: ${e.message}",
                        null,
                        HttpStatusCode.InternalServerError
                    )
                }
            }

            // GET /admin/finance/payments - Get payment history with pagination and filtering
            get("/payments") {
                val admin = call.getAuthenticatedAdminOrRespond()
                if (admin == null) {
                    return@get
                }

                try {
                    // Parse and validate query parameters
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 50
                    val status = call.request.queryParameters["status"]
                    val startDate = call.request.queryParameters["startDate"]
                    val endDate = call.request.queryParameters["endDate"]

                    // Validate pagination parameters
                    if (page < 1) {
                        call.respondError(
                            "Invalid page parameter. Must be >= 1",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@get
                    }

                    if (pageSize < 1 || pageSize > 100) {
                        call.respondError(
                            "Invalid pageSize parameter. Must be between 1 and 100",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@get
                    }

                    // Validate date parameters if provided
                    if (startDate != null && startDate.isNotBlank()) {
                        try {
                            java.time.LocalDate.parse(startDate)
                        } catch (e: Exception) {
                            call.respondError(
                                "Invalid start date format. Please use ISO format (YYYY-MM-DD)",
                                null,
                                HttpStatusCode.BadRequest
                            )
                            return@get
                        }
                    }

                    if (endDate != null && endDate.isNotBlank()) {
                        try {
                            java.time.LocalDate.parse(endDate)
                        } catch (e: Exception) {
                            call.respondError(
                                "Invalid end date format. Please use ISO format (YYYY-MM-DD)",
                                null,
                                HttpStatusCode.BadRequest
                            )
                            return@get
                        }
                    }

                    // Validate date range if both dates are provided
                    if (startDate != null && endDate != null && startDate.isNotBlank() && endDate.isNotBlank()) {
                        try {
                            val start = java.time.LocalDate.parse(startDate)
                            val end = java.time.LocalDate.parse(endDate)
                            if (start.isAfter(end)) {
                                call.respondError(
                                    "Start date must be before or equal to end date",
                                    null,
                                    HttpStatusCode.BadRequest
                                )
                                return@get
                            }
                        } catch (e: Exception) {
                            // Already handled above
                        }
                    }

                    // Audit log: Payment history request
                    val timestamp = java.time.Instant.now().toString()
                    logger.info("[AUDIT] [$timestamp] Payment History Request - Admin: ${admin.email}, Action: VIEW_PAYMENT_HISTORY, Page: $page, PageSize: $pageSize, Status: $status, StartDate: $startDate, EndDate: $endDate")

                    val financialService: domain.service.FinancialService by application.inject()
                    when (val result = financialService.getPaymentHistory(page, pageSize, status, startDate, endDate)) {
                        is Result.Success -> {
                            logger.info("[AUDIT] [$timestamp] Payment History Request - Admin: ${admin.email}, Status: SUCCESS, RecordsReturned: ${result.data.payments.size}")
                            call.respondSuccess(
                                "Payment history retrieved successfully",
                                result.data,
                                HttpStatusCode.OK
                            )
                        }

                        is Result.Error -> {
                            logger.error("[AUDIT] [$timestamp] Payment History Request - Admin: ${admin.email}, Status: FAILED, Error: ${result.message}")
                            call.respondError(
                                result.message,
                                null,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                } catch (e: Exception) {
                    val timestamp = java.time.Instant.now().toString()
                    logger.error(
                        "[AUDIT] [$timestamp] Payment History Request - Admin: ${admin.email}, Status: ERROR, Exception: ${e.message}",
                        e
                    )
                    call.respondError(
                        "Failed to retrieve payment history: ${e.message}",
                        null,
                        HttpStatusCode.InternalServerError
                    )
                }
            }

            // GET /admin/finance/user-transactions - Get user transactions for bill generation
            get("/user-transactions") {
                val admin = call.getAuthenticatedAdminOrRespond()
                if (admin == null) {
                    return@get
                }

                try {
                    val userEmail = call.request.queryParameters["userEmail"]

                    // Validate userEmail parameter
                    if (userEmail.isNullOrBlank()) {
                        call.respondError(
                            "User email is required",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@get
                    }

                    // Validate email format
                    if (!util.ValidationUtils.isValidEmail(userEmail)) {
                        call.respondError(
                            "Invalid email format",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@get
                    }

                    logger.info("Admin ${admin.email} requesting transactions for user: $userEmail")

                    val financialService: domain.service.FinancialService by application.inject()
                    when (val result = financialService.getUserTransactions(userEmail)) {
                        is Result.Success -> {
                            call.respondSuccess(
                                "User transactions retrieved successfully",
                                result.data,
                                HttpStatusCode.OK
                            )
                        }

                        is Result.Error -> {
                            logger.error("Failed to get user transactions: ${result.message}")
                            call.respondError(
                                result.message,
                                null,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing user transactions request", e)
                    call.respondError(
                        "Failed to retrieve user transactions: ${e.message}",
                        null,
                        HttpStatusCode.InternalServerError
                    )
                }
            }

            // POST /admin/finance/generate-bill - Generate and optionally send PDF bill
            post("/generate-bill") {
                val admin = call.getAuthenticatedAdminOrRespond()
                if (admin == null) {
                    return@post
                }

                var targetUserEmail = "unknown"
                try {
                    val request = call.receive<bose.ankush.data.model.BillGenerationRequest>()
                    targetUserEmail = request.userEmail

                    // Validate request
                    if (request.userEmail.isBlank()) {
                        call.respondError(
                            "User email is required",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }

                    // Validate email format using ValidationUtils
                    if (!util.ValidationUtils.isValidEmail(request.userEmail)) {
                        call.respondError(
                            "Invalid email format. Please provide a valid email address",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }

                    if (request.paymentIds.isEmpty() && request.subscriptionIds.isEmpty()) {
                        call.respondError(
                            "At least one payment or subscription ID is required",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }

                    // Audit log: Bill generation request
                    val timestamp = java.time.Instant.now().toString()
                    logger.info(
                        "[AUDIT] [$timestamp] Bill Generation Request - Admin: ${admin.email}, Action: GENERATE_BILL, TargetUser: ${request.userEmail}, PaymentIDs: ${
                            request.paymentIds.joinToString(
                                ","
                            )
                        }, SubscriptionIDs: ${request.subscriptionIds.joinToString(",")}, SendViaEmail: ${request.sendViaEmail}"
                    )

                    val billService: domain.service.BillService by application.inject()

                    if (request.sendViaEmail) {
                        // Generate and send bill via email
                        when (val result = billService.generateAndSendBill(
                            admin.email,
                            request.userEmail,
                            request.paymentIds,
                            request.subscriptionIds
                        )) {
                            is Result.Success -> {
                                logger.info("[AUDIT] [$timestamp] Bill Generation Request - Admin: ${admin.email}, Status: SUCCESS, TargetUser: ${request.userEmail}, DeliveryMethod: EMAIL")
                                call.respondSuccess(
                                    result.data.message,
                                    result.data,
                                    HttpStatusCode.OK
                                )
                            }

                            is Result.Error -> {
                                logger.error("[AUDIT] [$timestamp] Bill Generation Request - Admin: ${admin.email}, Status: FAILED, TargetUser: ${request.userEmail}, Error: ${result.message}")
                                call.respondError(
                                    result.message,
                                    null,
                                    HttpStatusCode.InternalServerError
                                )
                            }
                        }
                    } else {
                        // Generate bill and return PDF for download
                        when (val result = billService.generateBill(
                            request.userEmail,
                            request.paymentIds,
                            request.subscriptionIds
                        )) {
                            is Result.Success -> {
                                val pdfBytes = result.data
                                val timestampMillis = System.currentTimeMillis()
                                val filename = "invoice-${request.userEmail.replace("@", "-")}-$timestampMillis.pdf"

                                logger.info("[AUDIT] [$timestamp] Bill Generation Request - Admin: ${admin.email}, Status: SUCCESS, TargetUser: ${request.userEmail}, DeliveryMethod: DOWNLOAD, FileSize: ${pdfBytes.size} bytes")

                                call.response.header(
                                    HttpHeaders.ContentDisposition,
                                    "attachment; filename=\"$filename\""
                                )
                                call.respondBytes(
                                    pdfBytes,
                                    ContentType.Application.Pdf,
                                    HttpStatusCode.OK
                                )
                            }

                            is Result.Error -> {
                                logger.error("[AUDIT] [$timestamp] Bill Generation Request - Admin: ${admin.email}, Status: FAILED, TargetUser: ${request.userEmail}, Error: ${result.message}")
                                call.respondError(
                                    result.message,
                                    null,
                                    HttpStatusCode.InternalServerError
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    val timestamp = java.time.Instant.now().toString()
                    logger.error(
                        "[AUDIT] [$timestamp] Bill Generation Request - Admin: ${admin.email}, Status: ERROR, TargetUser: $targetUserEmail, Exception: ${e.message}",
                        e
                    )
                    call.respondError(
                        "Failed to generate bill: ${e.message}",
                        null,
                        HttpStatusCode.InternalServerError
                    )
                }
            }
        }

        // Admin tools endpoints
        route("/tools") {
            // POST /admin/tools/export-financial-data - Export financial data to CSV
            post("/export-financial-data") {
                val admin = call.getAuthenticatedAdminOrRespond()
                if (admin == null) {
                    return@post
                }

                var exportType = "unknown"
                try {
                    val request = call.receive<bose.ankush.data.model.FinancialExportRequest>()
                    exportType = request.exportType.name

                    // Validate date range
                    if (request.startDate.isBlank() || request.endDate.isBlank()) {
                        call.respondError(
                            "Start date and end date are required",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }

                    // Parse dates to validate format and order
                    val start: java.time.LocalDate
                    val end: java.time.LocalDate

                    try {
                        start = java.time.LocalDate.parse(request.startDate)
                        end = java.time.LocalDate.parse(request.endDate)
                    } catch (e: java.time.format.DateTimeParseException) {
                        call.respondError(
                            "Invalid date format. Please use ISO format (YYYY-MM-DD)",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    } catch (e: Exception) {
                        call.respondError(
                            "Invalid date format. Please use ISO format (YYYY-MM-DD)",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }

                    // Validate date order
                    if (start.isAfter(end)) {
                        call.respondError(
                            "Start date must be before or equal to end date",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }

                    // Validate date range is not too large (max 2 years)
                    val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(start, end)
                    if (daysBetween > 730) {
                        call.respondError(
                            "Date range is too large. Maximum allowed range is 2 years (730 days)",
                            null,
                            HttpStatusCode.BadRequest
                        )
                        return@post
                    }

                    // Audit log: Financial export request
                    val timestamp = java.time.Instant.now().toString()
                    logger.info("[AUDIT] [$timestamp] Financial Export Request - Admin: ${admin.email}, Action: EXPORT_FINANCIAL_DATA, ExportType: ${request.exportType}, StartDate: ${request.startDate}, EndDate: ${request.endDate}")

                    val financialService: domain.service.FinancialService by application.inject()

                    // Generate CSV based on export type
                    val csvResult = when (request.exportType) {
                        bose.ankush.data.model.ExportType.PAYMENTS ->
                            financialService.exportPayments(request.startDate, request.endDate)

                        bose.ankush.data.model.ExportType.SUBSCRIPTIONS ->
                            financialService.exportSubscriptions(request.startDate, request.endDate)

                        bose.ankush.data.model.ExportType.BOTH ->
                            financialService.exportBoth(request.startDate, request.endDate)
                    }

                    when (csvResult) {
                        is Result.Success -> {
                            val csvData = csvResult.data
                            val timestampMillis = System.currentTimeMillis()
                            val filename =
                                "financial-export-${request.exportType.name.lowercase()}-$timestampMillis.csv"

                            logger.info("[AUDIT] [$timestamp] Financial Export Request - Admin: ${admin.email}, Status: SUCCESS, ExportType: ${request.exportType}, FileSize: ${csvData.length} bytes, RecordCount: ${csvData.lines().size - 1}")

                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                "attachment; filename=\"$filename\""
                            )
                            call.respondText(
                                csvData,
                                ContentType.Text.CSV,
                                HttpStatusCode.OK
                            )
                        }

                        is Result.Error -> {
                            logger.error("[AUDIT] [$timestamp] Financial Export Request - Admin: ${admin.email}, Status: FAILED, ExportType: ${request.exportType}, Error: ${csvResult.message}")
                            call.respondError(
                                csvResult.message,
                                null,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                } catch (e: Exception) {
                    val timestamp = java.time.Instant.now().toString()
                    logger.error(
                        "[AUDIT] [$timestamp] Financial Export Request - Admin: ${admin.email}, Status: ERROR, ExportType: $exportType, Exception: ${e.message}",
                        e
                    )
                    call.respondError(
                        "Failed to export financial data: ${e.message}",
                        null,
                        HttpStatusCode.InternalServerError
                    )
                }
            }
        }

        get("/dashboard") {
            // Check authentication without responding (to allow redirect)
            val authResult = call.authenticateAdmin()

            when (authResult) {
                is AuthHelper.AuthResult.Failure -> {
                    // Authentication failed, redirect to login with appropriate error
                    logger.info("Unauthenticated access to dashboard, redirecting to login")
                    call.respondRedirect("/admin/login?error=auth_required", permanent = false)
                    return@get
                }

                is AuthHelper.AuthResult.Success -> {
                    val admin = authResult.user
                    val userEmail = admin.email
                    logger.info("Serving admin dashboard to admin user: $userEmail")

            // Respond with the admin dashboard HTML
            call.respondHtml(HttpStatusCode.OK) {
                attributes["lang"] = "en"
                head {
                    setupHead(pageName, includeAdminJs = true)

                    // Include page-specific CSS if needed
                    style {
                        unsafe {
                            raw(
                                """
                                /* Admin dashboard specific styles */
                                .admin-container {
    /* Full width with padding on sides */
    position: relative;
    width: 100%;
    max-width: none;
    margin: 0 auto 2rem auto;
    padding: 1.5rem 2rem;
    background: var(--content-bg);
    border: 1px solid var(--content-border);
    border-radius: 12px;
    box-shadow: 0 4px 12px var(--card-shadow);
    backdrop-filter: blur(10px);
    -webkit-backdrop-filter: blur(10px);
    min-height: 200px;
}

                                .admin-header {
                                    display: flex;
                                    justify-content: space-between;
                                    align-items: center;
                                    margin-bottom: 2rem;
                                    padding-bottom: 1rem;
                                    border-bottom: 1px solid var(--card-border);
                                }

                                .admin-title {
                                    margin: 0;
                                    font-size: 1.8rem;
                                    font-weight: 600;
                                    color: var(--card-title);
                                }

                                .admin-user-info {
                                    display: flex;
                                    align-items: center;
                                    gap: 0.5rem;
                                    font-size: 0.9rem;
                                    color: var(--text-secondary);
                                }

                                .admin-user-email {
                                    font-weight: 600;
                                    color: var(--text-color);
                                }

                                .admin-logout {
                                    cursor: pointer;
                                    color: #ef4444;
                                    text-decoration: underline;
                                    transition: color 0.2s ease;
                                }

                                .admin-logout:hover {
                                    color: #dc2626;
                                }

                                .dashboard-content {
                                    display: grid;
                                    gap: 2rem;
                                }

                                .dashboard-section {
                                    position: relative;
                                    min-width: 0;
                                    width: 100%;
                                    box-sizing: border-box;
                                    background: var(--card-bg);
                                    border: 1px solid var(--card-border);
                                    border-radius: 8px;
                                    padding: 1.5rem;
                                    transition: all 0.3s ease;
                                    min-height: 160px;
                                }

                                /* Ensure all card UI components and tab contents have consistent sizing behavior */
                                .dashboard-card, .tab-content, .tab-panel {
                                    position: relative;
                                    min-width: 0;
                                    width: 100%;
                                    box-sizing: border-box;
                                }

                                .dashboard-section:hover {
                                    border-color: var(--card-hover-border);
                                    box-shadow: 0 4px 12px var(--card-shadow);
                                }

                                .dashboard-section-title {
                                    margin: 0 0 1rem 0;
                                    font-size: 1.4rem;
                                    font-weight: 600;
                                    color: var(--card-title);
                                }

                                .dashboard-card {
                                    background: var(--endpoint-bg);
                                    border: 1px solid var(--endpoint-border);
                                    border-radius: 10px;
                                    padding: 0.5rem;
                                    margin-bottom: 1rem;
                                    min-height: 120px;
                                }

                                .dashboard-card-title {
                                    font-weight: 600;
                                    color: var(--card-title);
                                    margin-bottom: 0.75rem;
                                }

                                .dashboard-card-content {
                                    color: var(--text-secondary);
                                    line-height: 1.6;
                                }

                                /* Users table breathing space and styling */
                                .dashboard-card-content {
                                    overflow-x: auto;
                                }
                                .dashboard-card-content table {
                                    width: 100%;
                                    table-layout: auto;
                                    border-collapse: separate !important;
                                    border-spacing: 0 10px;
                                    min-width: 800px;
                                }
                                .dashboard-card-content table.users-table th,
                                .dashboard-card-content table.users-table td {
                                    overflow: visible;
                                    text-overflow: clip;
                                    white-space: normal;
                                    word-break: break-word;
                                }
                                .dashboard-card-content table.users-table th:nth-child(1),
                                .dashboard-card-content table.users-table td:nth-child(1) { min-width: 200px; }
                                .dashboard-card-content table.users-table th:nth-child(2),
                                .dashboard-card-content table.users-table td:nth-child(2) { min-width: 140px; }
                                .dashboard-card-content table.users-table th:nth-child(3),
                                .dashboard-card-content table.users-table td:nth-child(3) { min-width: 120px; }
                                .dashboard-card-content table.users-table th:nth-child(4),
                                .dashboard-card-content table.users-table td:nth-child(4) { min-width: 100px; }
                                .dashboard-card-content table.users-table th:nth-child(5),
                                .dashboard-card-content table.users-table td:nth-child(5) { min-width: 100px; }
                                .dashboard-card-content thead th {
                                    text-align: left;
                                    padding: 12px 14px;
                                    border-bottom: 1px solid var(--card-border);
                                    color: var(--text-secondary);
                                    font-weight: 600;
                                    background: transparent;
                                    vertical-align: middle;
                                }
                                .dashboard-card-content tbody tr {
                                    background: var(--card-bg);
                                    border: 1px solid var(--card-border);
                                    transition: background 0.2s ease, border-color 0.2s ease;
                                }
                                .dashboard-card-content tbody tr:hover {
                                    background: var(--card-hover-bg);
                                    border-color: var(--card-hover-border);
                                }
                                .dashboard-card-content tbody td {
                                    padding: 12px 14px;
                                    border: none;
                                    vertical-align: middle;
                                }
                                .dashboard-card-content tbody tr td:first-child {
                                    border-top-left-radius: 8px;
                                    border-bottom-left-radius: 8px;
                                }
                                .dashboard-card-content tbody tr td:last-child {
                                    border-top-right-radius: 8px;
                                    border-bottom-right-radius: 8px;
                                }

                                /* Role dropdown styling */
                                .role-select {
                                    appearance: none;
                                    -webkit-appearance: none;
                                    background: var(--endpoint-bg);
                                    color: var(--text-color);
                                    border: 1px solid var(--endpoint-border);
                                    border-radius: 8px;
                                    padding: 8px 32px 8px 12px;
                                    font-size: 0.95rem;
                                    cursor: pointer;
                                    transition: border-color 0.2s ease, box-shadow 0.2s ease;
                                    background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='20' height='20' viewBox='0 0 24 24' fill='none' stroke='%236366f1' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><polyline points='6 9 12 15 18 9'/></svg>");
                                    background-repeat: no-repeat;
                                    background-position: right 10px center;
                                    background-size: 16px 16px;
                                    vertical-align: middle;
                                }
                                .role-select:hover { border-color: var(--card-hover-border); }
                                .role-select:focus {
                                    outline: none;
                                    border-color: var(--card-hover-border);
                                    box-shadow: 0 0 0 3px rgba(99,102,241,0.15);
                                }

                                /* Actions cell overflow + menu + ripple */
                                .dashboard-card-content table.users-table td.actions-cell, .dashboard-card-content table.users-table th.actions-col { overflow: visible; }
                                .actions-wrapper { overflow: visible; }
                                .icon-button { position: relative; overflow: hidden; }
                                .icon-button .ripple { position: absolute; border-radius: 50%; transform: scale(0); animation: ripple 600ms linear; background-color: var(--icon-color); opacity: 0.25; pointer-events: none; }
                                @keyframes ripple { to { transform: scale(4); opacity: 0; } }

                                /* Defensively hide any accidental icon button inside Role column */
                                .dashboard-card-content table.users-table td:nth-child(3) .icon-button { display: none !important; }

                                /* Ensure menus are not clipped by table rows */
                                .dashboard-card-content tbody, .dashboard-card-content tbody tr { overflow: visible; }

                                /* Status toggle switch */
                                .status-toggle { position: relative; display: inline-block; width: 44px; height: 24px; vertical-align: middle; }
                                .status-toggle input { opacity: 0; width: 0; height: 0; }
                                .status-slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #ef4444; transition: .2s; border-radius: 9999px; }
                                .status-slider:before { position: absolute; content: ""; height: 18px; width: 18px; left: 3px; top: 3px; background-color: white; transition: .2s; border-radius: 50%; }
                                .status-toggle input:checked + .status-slider { background-color: #10b981; }
                                .status-toggle input:checked + .status-slider:before { transform: translateX(20px); }

                                /* Pagination buttons */
                                #pagination { gap: 0.5rem; }
                                .pagination-button { padding: 6px 10px; border: 1px solid var(--endpoint-border); background: var(--endpoint-bg); color: var(--text-color); border-radius: 6px; cursor: pointer; transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease; }
                                .pagination-button.active { background: var(--card-hover-bg); border-color: var(--card-hover-border); }
                                .pagination-button.disabled { opacity: 0.5; cursor: default; }
                                .pagination-button:hover:not(.disabled):not(.active) { border-color: var(--card-hover-border); }

                                /* Tabs */
                                .tabs {
                                    display: flex;
                                    align-items: flex-end;
                                    gap: 0.75rem;
                                    border-bottom: 1px solid var(--card-border);
                                    margin-bottom: 1rem;
                                    flex-wrap: wrap;
                                }
                                .tab {
                                    position: relative;
                                    padding: 0.6rem 1rem;
                                    cursor: pointer;
                                    border: 1px solid var(--card-border);
                                    border-bottom: none;
                                    border-top-left-radius: 8px;
                                    border-top-right-radius: 8px;
                                    background: var(--endpoint-bg);
                                    color: var(--text-secondary);
                                    transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
                                    display: inline-flex;
                                    align-items: center;
                                    gap: 0.35rem;
                                }
                                .tab:hover {
                                    color: var(--text-color);
                                    border-color: var(--card-hover-border);
                                }
                                .tab.active {
                                    background: var(--card-bg);
                                    color: var(--text-color);
                                    border-color: var(--card-hover-border);
                                }
                                .tab.active::after {
                                    content: "";
                                    position: absolute;
                                    left: 0;
                                    right: 0;
                                    bottom: -1px;
                                    height: 3px;
                                    background: #6366f1;
                                    border-bottom-left-radius: 3px;
                                    border-bottom-right-radius: 3px;
                                }
                                .tab-content { position: relative; }
                                .tab-panel { display: none; opacity: 0; transform: translateY(6px); transition: opacity 0.2s ease, transform 0.2s ease; min-height: 180px; }
                                .tab-panel.active { display: block; opacity: 1; transform: translateY(0); }

                                /* Messages */
                                .message { padding: 0.75rem 1rem; border-radius: 6px; margin-bottom: 0.75rem; }
                                .success-message { background: rgba(16, 185, 129, 0.1); color: #10b981; }
                                .error-message { background: rgba(239, 68, 68, 0.1); color: #ef4444; }
                                .info-message { background: rgba(59, 130, 246, 0.1); color: #3b82f6; }
                                .hidden { display: none; }

                                /* Banner notifications */
                                .banner-container { position: fixed; top: 0; left: 0; right: 0; z-index: 10000; display: flex; flex-direction: column; gap: 0; pointer-events: none; }
                                .banner { display: flex; align-items: center; gap: 12px; padding: 14px 20px; box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15); backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); border-bottom: 1px solid; opacity: 0; transform: translateY(-100%); transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1); pointer-events: auto; cursor: pointer; }
                                .banner-visible { opacity: 1; transform: translateY(0); }
                                .banner-hide { opacity: 0; transform: translateY(-100%); }
                                .banner-icon-wrapper { flex-shrink: 0; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; border-radius: 50%; font-size: 14px; font-weight: 700; }
                                .banner-content { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
                                .banner-title { font-weight: 600; font-size: 14px; line-height: 1.4; }
                                .banner-message { font-size: 13px; line-height: 1.5; opacity: 0.9; word-wrap: break-word; }
                                .banner-close { flex-shrink: 0; width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; border-radius: 50%; font-size: 20px; opacity: 0.6; transition: opacity 0.2s ease, background 0.2s ease; cursor: pointer; margin-left: 8px; }
                                .banner-close:hover { opacity: 1; background: rgba(0, 0, 0, 0.1); }
                                .banner-success { background: rgba(16, 185, 129, 0.95); border-color: rgba(16, 185, 129, 0.3); color: #ffffff; }
                                .banner-success .banner-icon-wrapper { background: rgba(255, 255, 255, 0.2); color: #ffffff; }
                                .banner-error { background: rgba(239, 68, 68, 0.95); border-color: rgba(239, 68, 68, 0.3); color: #ffffff; }
                                .banner-error .banner-icon-wrapper { background: rgba(255, 255, 255, 0.2); color: #ffffff; }
                                .banner-info { background: rgba(59, 130, 246, 0.95); border-color: rgba(59, 130, 246, 0.3); color: #ffffff; }
                                .banner-info .banner-icon-wrapper { background: rgba(255, 255, 255, 0.2); color: #ffffff; }
                                .banner-warning { background: rgba(245, 158, 11, 0.95); border-color: rgba(245, 158, 11, 0.3); color: #ffffff; }
                                .banner-warning .banner-icon-wrapper { background: rgba(255, 255, 255, 0.2); color: #ffffff; }

                                /* Loader skeleton */
                                .skeleton { background: linear-gradient(90deg, rgba(255,255,255,0), rgba(255,255,255,0.2), rgba(255,255,255,0)); background-size: 200% 100%; animation: shimmer 1.2s infinite; border-radius: 6px; }
                                @keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

                                /* Badges */
                                .badge { display: inline-block; padding: 0.15rem 0.5rem; border-radius: 9999px; font-size: 0.75rem; margin-left: 0.5rem; }
                                .badge-admin { background: rgba(245, 158, 11, 0.15); color: #f59e0b; border: 1px solid rgba(245, 158, 11, 0.3); }
                                .badge-premium { background: rgba(99, 102, 241, 0.15); color: #6366f1; border: 1px solid rgba(99, 102, 241, 0.3); }

                                /* Table transitions */
                                .fade-in { animation: fadeIn 0.25s ease; }
                                @keyframes fadeIn { from { opacity: 0 } to { opacity: 1 } }

                                /* Custom scrollbar for tables */
                                ::-webkit-scrollbar {
                                    width: 8px;
                                    height: 8px;
                                }
                                ::-webkit-scrollbar-track {
                                    background: var(--card-bg);
                                    border-radius: 10px;
                                }
                                ::-webkit-scrollbar-thumb {
                                    background: var(--card-border);
                                    border-radius: 10px;
                                }
                                ::-webkit-scrollbar-thumb:hover {
                                    background: var(--card-hover-border);
                                }

                                /* Ensure body and html take full width */
                                html, body {
                                    width: 100%;
                                    margin: 0;
                                    padding: 0;
                                    overflow-x: hidden;
                                }

                                /* Main container full width */
                                .container, .main-container {
                                    max-width: none !important;
                                    width: 100% !important;
                                }

                                /* Finance Tab Styles */
                                .finance-summary {
                                    display: grid;
                                    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
                                    gap: 1rem;
                                    margin-bottom: 2rem;
                                }

                                .metric-card {
                                    background: var(--endpoint-bg);
                                    border: 1px solid var(--endpoint-border);
                                    border-radius: 10px;
                                    padding: 1.5rem;
                                    transition: all 0.3s ease;
                                }

                                .metric-card:hover {
                                    border-color: var(--card-hover-border);
                                    box-shadow: 0 4px 12px var(--card-shadow);
                                    transform: translateY(-2px);
                                }

                                .metric-label {
                                    font-size: 0.875rem;
                                    color: var(--text-secondary);
                                    margin-bottom: 0.5rem;
                                    font-weight: 500;
                                }

                                .metric-value {
                                    font-size: 1.75rem;
                                    font-weight: 700;
                                    color: var(--card-title);
                                    font-family: 'JetBrains Mono', monospace;
                                }

                                .finance-chart-container {
                                    background: var(--endpoint-bg);
                                    border: 1px solid var(--endpoint-border);
                                    border-radius: 10px;
                                    padding: 1.5rem;
                                    margin-bottom: 2rem;
                                    min-height: 300px;
                                }

                                .finance-actions {
                                    display: flex;
                                    flex-wrap: wrap;
                                    gap: 1rem;
                                    align-items: center;
                                    margin-bottom: 1.5rem;
                                    padding: 1rem;
                                    background: var(--endpoint-bg);
                                    border: 1px solid var(--endpoint-border);
                                    border-radius: 10px;
                                }

                                .btn {
                                    padding: 0.6rem 1.2rem;
                                    border-radius: 8px;
                                    font-weight: 500;
                                    cursor: pointer;
                                    transition: all 0.2s ease;
                                    border: none;
                                    display: inline-flex;
                                    align-items: center;
                                    gap: 0.5rem;
                                }

                                .btn-primary {
                                    background: #6366f1;
                                    color: white;
                                }

                                .btn-primary:hover {
                                    background: #4f46e5;
                                    box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
                                }

                                .btn-secondary {
                                    background: var(--endpoint-bg);
                                    color: var(--text-color);
                                    border: 1px solid var(--endpoint-border);
                                }

                                .btn-secondary:hover {
                                    border-color: var(--card-hover-border);
                                    background: var(--card-hover-bg);
                                }

                                .date-input {
                                    padding: 0.5rem 0.75rem;
                                    border: 1px solid var(--endpoint-border);
                                    border-radius: 6px;
                                    background: var(--card-bg);
                                    color: var(--text-color);
                                    font-size: 0.875rem;
                                    transition: all 0.2s ease;
                                }

                                .date-input:focus {
                                    outline: none;
                                    border-color: var(--card-hover-border);
                                    box-shadow: 0 0 0 3px rgba(99,102,241,0.15);
                                }

                                .payments-table {
                                    width: 100%;
                                    border-collapse: collapse;
                                    background: var(--card-bg);
                                }

                                .payments-table thead {
                                    background: var(--endpoint-bg);
                                    border-bottom: 2px solid var(--endpoint-border);
                                }

                                .payments-table th {
                                    padding: 0.75rem 1rem;
                                    text-align: left;
                                    font-weight: 600;
                                    color: var(--card-title);
                                    font-size: 0.875rem;
                                    white-space: nowrap;
                                    vertical-align: middle;
                                }

                                .payments-table td {
                                    padding: 0.75rem 1rem;
                                    border-bottom: 1px solid var(--endpoint-border);
                                    color: var(--text-color);
                                    font-size: 0.875rem;
                                    vertical-align: middle;
                                }

                                .payments-table th:nth-child(5),
                                .payments-table td:nth-child(5) {
                                    text-align: center;
                                }

                                .payments-table th:nth-child(8),
                                .payments-table td:nth-child(8) {
                                    text-align: center;
                                }

                                /* Refunds table specific alignment */
                                .refunds-table th:nth-child(4),
                                .refunds-table td:nth-child(4) {
                                    text-align: center;
                                }

                                .refunds-table th:nth-child(8),
                                .refunds-table td:nth-child(8) {
                                    text-align: center;
                                }

                                /* Simple status badges */
                                .status-badge {
                                    display: inline-block;
                                    padding: 0.25rem 0.75rem;
                                    border-radius: 4px;
                                    font-size: 0.75rem;
                                    font-weight: 600;
                                    text-transform: uppercase;
                                    letter-spacing: 0.025em;
                                }

                                .status-processed {
                                    background: rgba(16, 185, 129, 0.15);
                                    color: #059669;
                                    border: 1px solid rgba(16, 185, 129, 0.3);
                                }

                                .status-pending {
                                    background: rgba(245, 158, 11, 0.15);
                                    color: #d97706;
                                    border: 1px solid rgba(245, 158, 11, 0.3);
                                }

                                .status-failed {
                                    background: rgba(239, 68, 68, 0.15);
                                    color: #dc2626;
                                    border: 1px solid rgba(239, 68, 68, 0.3);
                                }

                                [data-theme="dark"] .status-processed {
                                    background: rgba(16, 185, 129, 0.2);
                                    color: #86efac;
                                }

                                [data-theme="dark"] .status-pending {
                                    background: rgba(245, 158, 11, 0.2);
                                    color: #fde047;
                                }

                                [data-theme="dark"] .status-failed {
                                    background: rgba(239, 68, 68, 0.2);
                                    color: #fca5a5;
                                }

                                .payments-table tbody tr {
                                    transition: background 0.2s ease;
                                }

                                .payments-table tbody tr:hover {
                                    background: var(--endpoint-bg);
                                }

                                .payment-status {
                                    display: inline-block;
                                    padding: 0.25rem 0.75rem;
                                    border-radius: 9999px;
                                    font-size: 0.75rem;
                                    font-weight: 600;
                                    text-transform: uppercase;
                                }

                                .payment-status-success {
                                    background: rgba(16, 185, 129, 0.15);
                                    color: #10b981;
                                    border: 1px solid rgba(16, 185, 129, 0.3);
                                }

                                .payment-status-failed {
                                    background: rgba(239, 68, 68, 0.15);
                                    color: #ef4444;
                                    border: 1px solid rgba(239, 68, 68, 0.3);
                                }

                                .payment-status-pending {
                                    background: rgba(245, 158, 11, 0.15);
                                    color: #f59e0b;
                                    border: 1px solid rgba(245, 158, 11, 0.3);
                                }

                                .payment-status-refunded {
                                    background: rgba(59, 130, 246, 0.15);
                                    color: #3b82f6;
                                    border: 1px solid rgba(59, 130, 246, 0.3);
                                }

                                /* KPI Card Styles */
                                .kpi-card {
                                    cursor: default;
                                }

                                .kpi-card:hover {
                                    transform: translateY(-4px);
                                    box-shadow: 0 8px 24px var(--card-shadow);
                                    border-color: var(--card-hover-border);
                                }

                                /* Responsive adjustments */
                                @media (max-width: 1024px) {
                                    .admin-container {
                                        padding: 1rem;
                                    }
                                    .container, .main-container {
                                        padding: 0.5rem 1rem !important;
                                    }
                                    .finance-summary {
                                        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
                                    }
                                    .finance-actions {
                                        flex-direction: column;
                                        align-items: stretch;
                                    }
                                    .finance-actions > * {
                                        width: 100%;
                                    }
                                }

                                @media (max-width: 768px) {
                                    .finance-summary {
                                        grid-template-columns: 1fr;
                                    }
                                    .metric-value {
                                        font-size: 1.5rem;
                                    }
                                    .payments-table {
                                        font-size: 0.75rem;
                                    }
                                    .payments-table th,
                                    .payments-table td {
                                        padding: 0.5rem;
                                    }
                                    #reports-kpis {
                                        grid-template-columns: 1fr;
                                    }
                                    .kpi-card {
                                        padding: 1rem;
                                    }
                                    #reports-header {
                                        flex-direction: column;
                                        align-items: flex-start;
                                    }
                                    #reports-refresh-btn {
                                        width: 100%;
                                        justify-content: center;
                                    }
                                }
                                """
                            )
                        }
                    }

                    // Tabs initialization script
                    script {
                        unsafe {
                            raw(
                                """
                                (function(){
                                    function initDashboardTabs(){
                                        try {
                                            const tabs = document.querySelectorAll('.tab');
                                            const panels = document.querySelectorAll('.tab-panel');
                                            let iamLoaded = false;
                                            let financeLoaded = false;
                                            let serviceCatalogLoaded = false;
                                            function activateTab(name){
                                                tabs.forEach(t => t.classList.toggle('active', t.dataset.tab === name));
                                                panels.forEach(p => p.classList.toggle('active', p.id === name));
                                                if(name === 'iam' && !iamLoaded){
                                                    iamLoaded = true;
                                                    if (typeof loadUsers === 'function') {
                                                        loadUsers(1, 10);
                                                    }
                                                }
                                                if(name === 'finance' && !financeLoaded){
                                                    financeLoaded = true;
                                                    if (typeof initializeFinanceTab === 'function') {
                                                        initializeFinanceTab();
                                                    }
                                                }
                                                if(name === 'service-catalog' && !serviceCatalogLoaded){
                                                    serviceCatalogLoaded = true;
                                                    if (typeof initializeServiceCatalog === 'function') {
                                                        initializeServiceCatalog();
                                                    }
                                                }
                                            }
                                            tabs.forEach(tab => tab.addEventListener('click', function(){
                                                activateTab(this.dataset.tab);
                                            }));
                                            // Default activate IAM
                                            activateTab('iam');
                                        } catch(e){ console.error('Failed to init tabs', e); }
                                    }
                                    document.addEventListener('DOMContentLoaded', initDashboardTabs);
                                })();
                                """
                            )
                        }
                    }
                }
                body {
                    // Use a single, consistent container for all dashboard content
                    div {
                        classes = setOf("container", "main-container")
                        style =
                            "max-width: 100%; width: 100%; margin: 0; padding: 1rem 2rem; box-sizing: border-box;" // <-- Full width with padding

                        createHeader(this)

                        // Admin dashboard content
                        div {
                            classes = setOf("admin-container")
                            style =
                                "max-width: 100%; width: 100%; box-sizing: border-box;" // <-- Ensure admin-container fills parent

                            div {
                                classes = setOf("admin-header")
                                h2 {
                                    classes = setOf("admin-title")
                                    +"Dashboard"
                                }
                            }

                            // Dashboard content
                            div {
                                classes = setOf("dashboard-content")
                                style =
                                    "display: grid; gap: 2rem; width: 100%; box-sizing: border-box;" // <-- Always full width

                                // Tabs section
                                div {
                                    classes = setOf("dashboard-section")
                                    style =
                                        "position: relative; min-width: 0; width: 100%; box-sizing: border-box;" // <-- Always full width

                                    // Tabs navigation
                                    div {
                                        classes = setOf("tabs")
                                        span {
                                            classes = setOf("tab", "active")
                                            attributes["data-tab"] = "iam"
                                            unsafe {
                                                raw("<span class='material-icons' style='font-size:18px; vertical-align:middle; margin-right:6px;'>group</span> Users")
                                            }
                                        }
                                        span {
                                            classes = setOf("tab")
                                            attributes["data-tab"] = "finance"
                                            unsafe {
                                                raw("<span class='material-icons' style='font-size:18px; vertical-align:middle; margin-right:6px;'>payments</span> Finance")
                                            }
                                        }
                                        span {
                                            classes = setOf("tab")
                                            attributes["data-tab"] = "reports"
                                            unsafe {
                                                raw("<span class='material-icons' style='font-size:18px; vertical-align:middle; margin-right:6px;'>analytics</span> Reports")
                                            }
                                        }
                                        span {
                                            classes = setOf("tab")
                                            attributes["data-tab"] = "tools"
                                            unsafe {
                                                raw("<span class='material-icons' style='font-size:18px; vertical-align:middle; margin-right:6px;'>build_circle</span> Tools")
                                            }
                                        }
                                        span {
                                            classes = setOf("tab")
                                            attributes["data-tab"] = "subscriptions"
                                            unsafe {
                                                raw("<span class='material-icons' style='font-size:18px; vertical-align:middle; margin-right:6px;'>card_membership</span> Subscriptions")
                                            }
                                        }
                                        span {
                                            classes = setOf("tab")
                                            attributes["data-tab"] = "service-catalog"
                                            unsafe {
                                                raw("<span class='material-icons' style='font-size:18px; vertical-align:middle; margin-right:6px;'>inventory_2</span> Service Catalog")
                                            }
                                        }
                                    }

                                    // Tabs content panels
                                    div {
                                        classes = setOf("tab-content")
                                        style =
                                            "width: 100%; box-sizing: border-box;" // <-- Always full width

                                        // IAM Panel
                                        div {
                                            classes = setOf("tab-panel", "active")
                                            id = "iam"
                                            style =
                                                "width: 100%; box-sizing: border-box;" // <-- Always full width
                                            div {
                                                classes = setOf("dashboard-card")
                                                div {
                                                    classes = setOf("dashboard-card-content")
                                                    unsafe {
                                                        raw(
                                                            """
                                                            <div id="success-message" class="message success-message hidden"></div>
                                                            <div id="error-message" class="message error-message hidden"></div>
                                                            <div id="info-message" class="message info-message hidden"></div>

                                                            <div id="iam-loader" class="skeleton" style="height:8px;width:100%;display:none;"></div>

                                                            <table class="users-table">
                                                                <colgroup>
                                                                    <col style="min-width:200px">
                                                                    <col style="min-width:140px">
                                                                    <col style="min-width:120px">
                                                                    <col style="min-width:100px">
                                                                    <col style="min-width:100px">
                                                                    <col style="min-width:80px">
                                                                </colgroup>
                                                                <thead>
                                                                    <tr>
                                                                        <th>Email</th>
                                                                        <th>Created At</th>
                                                                        <th>Role</th>
                                                                        <th>Status</th>
                                                                        <th>Premium</th>
                                                                        <th class="actions-col" aria-label="Actions"></th>
                                                                    </tr>
                                                                </thead>
                                                                <tbody id="users-table-body"></tbody>
                                                            </table>

                                                            <div id="pagination" style="display:flex;gap:0.5rem;margin-top:1rem;"></div>
                                                            """
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Finance Panel
                                        div {
                                            classes = setOf("tab-panel")
                                            id = "finance"
                                            style =
                                                "width: 100%; box-sizing: border-box;" // <-- Always full width

                                            // Financial Summary Cards
                                            div {
                                                classes = setOf("finance-summary")

                                                // Total Revenue Card
                                                div {
                                                    classes = setOf("metric-card")
                                                    div {
                                                        classes = setOf("metric-label")
                                                        +"Total Revenue"
                                                    }
                                                    div {
                                                        classes = setOf("metric-value")
                                                        id = "total-revenue"
                                                        +"$0.00"
                                                    }
                                                }

                                                // Monthly Revenue Card
                                                div {
                                                    classes = setOf("metric-card")
                                                    div {
                                                        classes = setOf("metric-label")
                                                        +"Monthly Revenue"
                                                    }
                                                    div {
                                                        classes = setOf("metric-value")
                                                        id = "monthly-revenue"
                                                        +"$0.00"
                                                    }
                                                }

                                                // Active Subscriptions Revenue Card
                                                div {
                                                    classes = setOf("metric-card")
                                                    div {
                                                        classes = setOf("metric-label")
                                                        +"Active Subscriptions Revenue"
                                                    }
                                                    div {
                                                        classes = setOf("metric-value")
                                                        id = "active-sub-revenue"
                                                        +"$0.00"
                                                    }
                                                }

                                                // Total Payments Card
                                                div {
                                                    classes = setOf("metric-card")
                                                    div {
                                                        classes = setOf("metric-label")
                                                        +"Total Payments"
                                                    }
                                                    div {
                                                        classes = setOf("metric-value")
                                                        id = "total-payments"
                                                        +"0"
                                                    }
                                                }
                                            }

                                            // Revenue Chart Container
                                            div {
                                                classes = setOf("finance-chart-container")
                                                canvas {
                                                    id = "revenue-chart"
                                                }
                                            }

                                            // Payment History Section
                                            div {
                                                classes = setOf("finance-actions")

                                                button {
                                                    id = "generate-bill-btn"
                                                    classes = setOf("btn", "btn-primary")
                                                    unsafe {
                                                        raw("<span class='material-icons' style='font-size:18px; vertical-align:middle; margin-right:6px;'>receipt</span> Generate Bill")
                                                    }
                                                }

                                                select {
                                                    id = "payment-status-filter"
                                                    classes = setOf("role-select")
                                                    option {
                                                        value = ""
                                                        +"All Statuses"
                                                    }
                                                    option {
                                                        value = "SUCCESS"
                                                        +"Success"
                                                    }
                                                    option {
                                                        value = "FAILED"
                                                        +"Failed"
                                                    }
                                                    option {
                                                        value = "PENDING"
                                                        +"Pending"
                                                    }
                                                    option {
                                                        value = "REFUNDED"
                                                        +"Refunded"
                                                    }
                                                }

                                                input {
                                                    type = InputType.date
                                                    id = "payment-date-from"
                                                    classes = setOf("date-input")
                                                    placeholder = "Start Date"
                                                }

                                                input {
                                                    type = InputType.date
                                                    id = "payment-date-to"
                                                    classes = setOf("date-input")
                                                    placeholder = "End Date"
                                                }
                                            }

                                            // Payment History Table
                                            div {
                                                classes = setOf("dashboard-card-content")
                                                style = "overflow-x: auto;"

                                                table {
                                                    classes = setOf("payments-table")
                                                    thead {
                                                        tr {
                                                            th { +"User Email" }
                                                            th { +"Amount" }
                                                            th { +"Currency" }
                                                            th { +"Payment Method" }
                                                            th {
                                                                style = "text-align: center;"
                                                                +"Status"
                                                            }
                                                            th { +"Transaction ID" }
                                                            th { +"Date" }
                                                            th {
                                                                style = "text-align: center;"
                                                                +"Actions"
                                                            }
                                                        }
                                                    }
                                                    tbody {
                                                        id = "payments-table-body"
                                                        tr {
                                                            td {
                                                                colSpan = "8"
                                                                style =
                                                                    "text-align: center; padding: 2rem; color: var(--text-secondary);"
                                                                +"Loading payment history..."
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Pagination Controls
                                            div {
                                                id = "payments-pagination"
                                                style =
                                                    "display: flex; justify-content: center; align-items: center; gap: 0.5rem; margin-top: 1rem;"
                                            }

                                            // Refund Analytics Section
                                            div {
                                                style =
                                                    "margin-top: 3rem; padding-top: 2rem; border-top: 2px solid var(--card-border);"

                                                // Section Header
                                                h2 {
                                                    style =
                                                        "color: var(--card-title); font-size: 1.5rem; font-weight: 600; margin-bottom: 1.5rem; display: flex; align-items: center; gap: 0.5rem;"
                                                    unsafe {
                                                        raw("<span class='material-icons' style='font-size:24px;'>money_off</span> Refund Analytics")
                                                    }
                                                }

                                                // Refund Metrics Cards
                                                div {
                                                    classes = setOf("finance-summary")
                                                    style = "margin-bottom: 1.5rem;"

                                                    // Total Refunds Card
                                                    div {
                                                        classes = setOf("metric-card")
                                                        div {
                                                            classes = setOf("metric-label")
                                                            +"Total Refunds"
                                                        }
                                                        div {
                                                            classes = setOf("metric-value")
                                                            id = "total-refunds"
                                                            +"$0.00"
                                                        }
                                                    }

                                                    // Monthly Refunds Card
                                                    div {
                                                        classes = setOf("metric-card")
                                                        div {
                                                            classes = setOf("metric-label")
                                                            +"Monthly Refunds"
                                                        }
                                                        div {
                                                            classes = setOf("metric-value")
                                                            id = "monthly-refunds"
                                                            +"$0.00"
                                                        }
                                                    }

                                                    // Refund Rate Card
                                                    div {
                                                        classes = setOf("metric-card")
                                                        div {
                                                            classes = setOf("metric-label")
                                                            +"Refund Rate"
                                                        }
                                                        div {
                                                            classes = setOf("metric-value")
                                                            id = "refund-rate"
                                                            +"0.00%"
                                                        }
                                                    }

                                                    // Instant Refunds Card
                                                    div {
                                                        classes = setOf("metric-card")
                                                        div {
                                                            classes = setOf("metric-label")
                                                            +"Instant Refunds"
                                                        }
                                                        div {
                                                            classes = setOf("metric-value")
                                                            id = "instant-refunds"
                                                            +"0"
                                                        }
                                                    }

                                                    // Normal Refunds Card
                                                    div {
                                                        classes = setOf("metric-card")
                                                        div {
                                                            classes = setOf("metric-label")
                                                            +"Normal Refunds"
                                                        }
                                                        div {
                                                            classes = setOf("metric-value")
                                                            id = "normal-refunds"
                                                            +"0"
                                                        }
                                                    }

                                                    // Average Processing Time Card
                                                    div {
                                                        classes = setOf("metric-card")
                                                        div {
                                                            classes = setOf("metric-label")
                                                            +"Avg Processing Time"
                                                        }
                                                        div {
                                                            classes = setOf("metric-value")
                                                            id = "avg-processing-time"
                                                            +"0.0h"
                                                        }
                                                    }
                                                }

                                                // Refund Chart Container
                                                div {
                                                    classes = setOf("finance-chart-container")
                                                    style = "margin-bottom: 1.5rem;"
                                                    canvas {
                                                        id = "refund-chart"
                                                    }
                                                }

                                                // Refund History Section
                                                div {
                                                    classes = setOf("finance-actions")

                                                    button {
                                                        id = "export-refunds-btn"
                                                        classes = setOf("btn", "btn-secondary")
                                                        unsafe {
                                                            raw("<span class='material-icons' style='font-size:18px; vertical-align:middle; margin-right:6px;'>download</span> Export Refunds")
                                                        }
                                                    }

                                                    select {
                                                        id = "refund-status-filter"
                                                        classes = setOf("role-select")
                                                        option {
                                                            value = ""
                                                            +"All Statuses"
                                                        }
                                                        option {
                                                            value = "PENDING"
                                                            +"Pending"
                                                        }
                                                        option {
                                                            value = "PROCESSED"
                                                            +"Processed"
                                                        }
                                                        option {
                                                            value = "FAILED"
                                                            +"Failed"
                                                        }
                                                    }

                                                    input {
                                                        type = InputType.date
                                                        id = "refund-date-from"
                                                        classes = setOf("date-input")
                                                        placeholder = "Start Date"
                                                    }

                                                    input {
                                                        type = InputType.date
                                                        id = "refund-date-to"
                                                        classes = setOf("date-input")
                                                        placeholder = "End Date"
                                                    }
                                                }

                                                // Refund History Table
                                                div {
                                                    classes = setOf("dashboard-card-content")
                                                    style = "overflow-x: auto;"

                                                    // Loader
                                                    div {
                                                        id = "refunds-loader"
                                                        classes = setOf("loading-spinner")
                                                        style = "display: none; margin: 2rem auto;"
                                                    }

                                                    table {
                                                        classes = setOf("payments-table", "refunds-table")
                                                        thead {
                                                            tr {
                                                                th { +"Refund ID" }
                                                                th { +"User Email" }
                                                                th { +"Amount" }
                                                                th {
                                                                    style = "text-align: center;"
                                                                    +"Status"
                                                                }
                                                                th { +"Type" }
                                                                th { +"Processed By" }
                                                                th { +"Date" }
                                                                th {
                                                                    style = "text-align: center;"
                                                                    +"Actions"
                                                                }
                                                            }
                                                        }
                                                        tbody {
                                                            id = "refunds-table-body"
                                                            tr {
                                                                td {
                                                                    colSpan = "8"
                                                                    style =
                                                                        "text-align: center; padding: 2rem; color: var(--text-secondary);"
                                                                    +"Loading refund history..."
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                // Refund Pagination Controls
                                                div {
                                                    id = "refunds-pagination"
                                                    style =
                                                        "display: flex; justify-content: center; align-items: center; gap: 0.5rem; margin-top: 1rem;"
                                                }
                                            }
                                        }

                                        // Reports Panel
                                        div {
                                            classes = setOf("tab-panel")
                                            id = "reports"
                                            style =
                                                "width: 100%; box-sizing: border-box;" // <-- Always full width
                                            div {
                                                classes = setOf("dashboard-card")
                                                div {
                                                    classes = setOf("dashboard-card-content")
                                                    unsafe {
                                                        raw(
                                                            """
                                                        <!-- Enhanced Header Section -->
                                                        <div id="reports-header" style="display:flex;align-items:center;justify-content:space-between;margin-bottom:1.5rem;flex-wrap:wrap;gap:1rem;">
                                                            <div style="display:flex;align-items:center;gap:0.75rem;">
                                                                <span class="material-icons" style="font-size:28px;color:#6366f1;">analytics</span>
                                                                <div>
                                                                    <h3 style="margin:0;font-size:1.5rem;font-weight:600;color:var(--card-title);">Analytics & Reports</h3>
                                                                    <div id="reports-last-updated" style="font-size:0.85rem;color:var(--text-secondary);margin-top:0.25rem;">
                                                                        Last updated: Never
                                                                    </div>
                                                                </div>
                                                            </div>
                                                            <div style="display:flex;gap:0.75rem;flex-wrap:wrap;">
                                                                <button id="reports-export-btn" class="btn btn-secondary" style="display:flex;align-items:center;gap:0.5rem;" aria-label="Export report data to CSV">
                                                                    <span class="material-icons" style="font-size:18px;">download</span>
                                                                    <span>Export CSV</span>
                                                                </button>
                                                                <button id="reports-refresh-btn" class="btn btn-secondary" style="display:flex;align-items:center;gap:0.5rem;" aria-label="Refresh reports data">
                                                                    <span class="material-icons" style="font-size:18px;">refresh</span>
                                                                    <span>Refresh</span>
                                                                    <span id="reports-refresh-spinner" class="loading-spinner" style="display:none;margin-left:0.25rem;"></span>
                                                                </button>
                                                            </div>
                                                        </div>

                                                        <!-- Time Range Selector -->
                                                        <div id="reports-controls" style="background:var(--endpoint-bg);border:1px solid var(--endpoint-border);border-radius:8px;padding:1rem;margin-bottom:1.5rem;">
                                                            <div style="display:flex;gap:1rem;align-items:flex-start;flex-wrap:wrap;">
                                                                <div style="flex:1;min-width:200px;">
                                                                    <label for="reports-range" style="display:block;color:var(--text-secondary);font-size:.9rem;margin-bottom:0.5rem;font-weight:500;">Time Range</label>
                                                                    <select id="reports-range" class="role-select" style="width:100%;">
                                                                        <option value="7">Last 7 days</option>
                                                                        <option value="30" selected>Last 30 days</option>
                                                                        <option value="90">Last 90 days</option>
                                                                        <option value="180">Last 6 months</option>
                                                                        <option value="365">Last year</option>
                                                                        <option value="custom">Custom range</option>
                                                                    </select>
                                                                </div>
                                                                <div id="custom-date-range" style="display:none;flex:2;min-width:300px;">
                                                                    <div style="display:flex;gap:1rem;align-items:flex-end;flex-wrap:wrap;">
                                                                        <div style="flex:1;min-width:140px;">
                                                                            <label for="reports-start-date" style="display:block;color:var(--text-secondary);font-size:.9rem;margin-bottom:0.5rem;font-weight:500;">Start Date</label>
                                                                            <input type="date" id="reports-start-date" class="role-select" style="width:100%;padding:0.5rem;border:1px solid var(--card-border);border-radius:6px;background:var(--card-bg);color:var(--text-color);" />
                                                                        </div>
                                                                        <div style="flex:1;min-width:140px;">
                                                                            <label for="reports-end-date" style="display:block;color:var(--text-secondary);font-size:.9rem;margin-bottom:0.5rem;font-weight:500;">End Date</label>
                                                                            <input type="date" id="reports-end-date" class="role-select" style="width:100%;padding:0.5rem;border:1px solid var(--card-border);border-radius:6px;background:var(--card-bg);color:var(--text-color);" />
                                                                        </div>
                                                                        <button id="apply-custom-range" class="btn btn-secondary" style="padding:0.5rem 1rem;">Apply</button>
                                                                    </div>
                                                                    <div id="date-range-error" style="display:none;color:#ef4444;font-size:0.85rem;margin-top:0.5rem;"></div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                        <!-- Enhanced KPI Cards Grid -->
                                                        <div id="reports-kpis" style="display:grid;grid-template-columns:repeat(auto-fit, minmax(200px, 1fr));gap:1rem;margin-bottom:2rem;">
                                                            <!-- Total Users Card -->
                                                            <div class="kpi-card" style="background:var(--endpoint-bg);border:1px solid var(--endpoint-border);border-radius:12px;padding:1.5rem;transition:all 0.3s ease;position:relative;overflow:hidden;">
                                                                <div style="position:absolute;top:0;left:0;right:0;height:4px;background:linear-gradient(90deg, #6366f1, #8b5cf6);"></div>
                                                                <div style="display:flex;align-items:center;gap:0.75rem;margin-bottom:0.75rem;">
                                                                    <div style="width:40px;height:40px;border-radius:10px;background:rgba(99, 102, 241, 0.1);display:flex;align-items:center;justify-content:center;">
                                                                        <span class="material-icons" style="font-size:24px;color:#6366f1;">group</span>
                                                                    </div>
                                                                    <div style="font-size:0.85rem;color:var(--text-secondary);font-weight:500;text-transform:uppercase;letter-spacing:0.5px;">Total Users</div>
                                                                </div>
                                                                <div id="kpi-total-users" style="font-size:2rem;font-weight:700;color:var(--card-title);line-height:1;">—</div>
                                                            </div>

                                                            <!-- New Users Card -->
                                                            <div class="kpi-card" style="background:var(--endpoint-bg);border:1px solid var(--endpoint-border);border-radius:12px;padding:1.5rem;transition:all 0.3s ease;position:relative;overflow:hidden;">
                                                                <div style="position:absolute;top:0;left:0;right:0;height:4px;background:linear-gradient(90deg, #10b981, #059669);"></div>
                                                                <div style="display:flex;align-items:center;gap:0.75rem;margin-bottom:0.75rem;">
                                                                    <div style="width:40px;height:40px;border-radius:10px;background:rgba(16, 185, 129, 0.1);display:flex;align-items:center;justify-content:center;">
                                                                        <span class="material-icons" style="font-size:24px;color:#10b981;">person_add</span>
                                                                    </div>
                                                                    <div style="font-size:0.85rem;color:var(--text-secondary);font-weight:500;text-transform:uppercase;letter-spacing:0.5px;">New Users</div>
                                                                </div>
                                                                <div id="kpi-new-users" style="font-size:2rem;font-weight:700;color:var(--card-title);line-height:1;">—</div>
                                                                <div id="kpi-new-users-subtitle" style="font-size:0.75rem;color:var(--text-secondary);margin-top:0.5rem;">in selected period</div>
                                                            </div>

                                                            <!-- Active Rate Card -->
                                                            <div class="kpi-card" style="background:var(--endpoint-bg);border:1px solid var(--endpoint-border);border-radius:12px;padding:1.5rem;transition:all 0.3s ease;position:relative;overflow:hidden;">
                                                                <div style="position:absolute;top:0;left:0;right:0;height:4px;background:linear-gradient(90deg, #3b82f6, #2563eb);"></div>
                                                                <div style="display:flex;align-items:center;gap:0.75rem;margin-bottom:0.75rem;">
                                                                    <div style="width:40px;height:40px;border-radius:10px;background:rgba(59, 130, 246, 0.1);display:flex;align-items:center;justify-content:center;">
                                                                        <span class="material-icons" style="font-size:24px;color:#3b82f6;">check_circle</span>
                                                                    </div>
                                                                    <div style="font-size:0.85rem;color:var(--text-secondary);font-weight:500;text-transform:uppercase;letter-spacing:0.5px;">Active Rate</div>
                                                                </div>
                                                                <div id="kpi-active-rate" style="font-size:2rem;font-weight:700;color:var(--card-title);line-height:1;">—</div>
                                                            </div>

                                                            <!-- Premium Users Card -->
                                                            <div class="kpi-card" style="background:var(--endpoint-bg);border:1px solid var(--endpoint-border);border-radius:12px;padding:1.5rem;transition:all 0.3s ease;position:relative;overflow:hidden;">
                                                                <div style="position:absolute;top:0;left:0;right:0;height:4px;background:linear-gradient(90deg, #f59e0b, #d97706);"></div>
                                                                <div style="display:flex;align-items:center;gap:0.75rem;margin-bottom:0.75rem;">
                                                                    <div style="width:40px;height:40px;border-radius:10px;background:rgba(245, 158, 11, 0.1);display:flex;align-items:center;justify-content:center;">
                                                                        <span class="material-icons" style="font-size:24px;color:#f59e0b;">star</span>
                                                                    </div>
                                                                    <div style="font-size:0.85rem;color:var(--text-secondary);font-weight:500;text-transform:uppercase;letter-spacing:0.5px;">Premium Users</div>
                                                                </div>
                                                                <div id="kpi-premium" style="font-size:2rem;font-weight:700;color:var(--card-title);line-height:1;">—</div>
                                                            </div>

                                                            <!-- Growth Rate Card -->
                                                            <div class="kpi-card" style="background:var(--endpoint-bg);border:1px solid var(--endpoint-border);border-radius:12px;padding:1.5rem;transition:all 0.3s ease;position:relative;overflow:hidden;">
                                                                <div style="position:absolute;top:0;left:0;right:0;height:4px;background:linear-gradient(90deg, #8b5cf6, #7c3aed);"></div>
                                                                <div style="display:flex;align-items:center;gap:0.75rem;margin-bottom:0.75rem;">
                                                                    <div style="width:40px;height:40px;border-radius:10px;background:rgba(139, 92, 246, 0.1);display:flex;align-items:center;justify-content:center;">
                                                                        <span class="material-icons" style="font-size:24px;color:#8b5cf6;">trending_up</span>
                                                                    </div>
                                                                    <div style="font-size:0.85rem;color:var(--text-secondary);font-weight:500;text-transform:uppercase;letter-spacing:0.5px;">Growth Rate</div>
                                                                </div>
                                                                <div id="kpi-growth-rate" style="font-size:2rem;font-weight:700;color:var(--card-title);line-height:1;">—</div>
                                                            </div>

                                                            <!-- Retention Rate Card -->
                                                            <div class="kpi-card" style="background:var(--endpoint-bg);border:1px solid var(--endpoint-border);border-radius:12px;padding:1.5rem;transition:all 0.3s ease;position:relative;overflow:hidden;">
                                                                <div style="position:absolute;top:0;left:0;right:0;height:4px;background:linear-gradient(90deg, #ec4899, #db2777);"></div>
                                                                <div style="display:flex;align-items:center;gap:0.75rem;margin-bottom:0.75rem;">
                                                                    <div style="width:40px;height:40px;border-radius:10px;background:rgba(236, 72, 153, 0.1);display:flex;align-items:center;justify-content:center;">
                                                                        <span class="material-icons" style="font-size:24px;color:#ec4899;">autorenew</span>
                                                                    </div>
                                                                    <div style="font-size:0.85rem;color:var(--text-secondary);font-weight:500;text-transform:uppercase;letter-spacing:0.5px;">Retention Rate</div>
                                                                </div>
                                                                <div id="kpi-retention-rate" style="font-size:2rem;font-weight:700;color:var(--card-title);line-height:1;">—</div>
                                                            </div>
                                                        </div>
                                                        <div style="position:relative;min-height:260px">
                                                            <canvas id="reports-chart" height="260" aria-label="User registrations chart" role="img"></canvas>
                                                            <div id="reports-empty" class="message info-message hidden" style="position:absolute;left:0;right:0;top:0;">Not enough data to render chart</div>
                                                        </div>
                                                        """
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Tools Panel
                                        div {
                                            classes = setOf("tab-panel")
                                            id = "tools"
                                            style =
                                                "width: 100%; box-sizing: border-box;"
                                            div {
                                                classes = setOf("dashboard-card")
                                                div {
                                                    classes =
                                                        setOf("dashboard-card-title"); +"Tools"
                                                }
                                                div {
                                                    classes = setOf("dashboard-card-content")
                                                    unsafe {
                                                        raw(
                                                            """
                                                            <div class="tool-list" role="list">
                                                                <div class="tool-item" role="listitem">
                                                                    <span class="material-icons tool-icon" aria-hidden="true">cached</span>
                                                                    <div class="tool-content">
                                                                        <div class="tool-title">Clear Weather Cache</div>
                                                                        <div class="tool-desc">Purge cached weather and air quality responses from memory to fetch fresh data on next requests.</div>
                                                                        <div class="tool-actions">
                                                                            <button id="clear-cache-btn" class="btn btn-secondary" aria-label="Clear weather cache">Clear Cache</button>
                                                                            <span id="clear-cache-spinner" class="loading-spinner" style="display:none;"></span>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                                <div class="tool-item" role="listitem">
                                                                    <span class="material-icons tool-icon" aria-hidden="true">monitor_heart</span>
                                                                    <div class="tool-content">
                                                                        <div class="tool-title">Run Health Check</div>
                                                                        <div class="tool-desc">Validate upstream APIs and response latency across endpoints. Results will be shown in a popup.</div>
                                                                        <div class="tool-actions">
                                                                            <button id="run-health-btn" class="btn btn-secondary" aria-label="Run health check">Run</button>
                                                                            <span id="run-health-spinner" class="loading-spinner" style="display:none;"></span>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                                <div class="tool-item" role="listitem">
                                                                    <span class="material-icons tool-icon" aria-hidden="true">bolt</span>
                                                                    <div class="tool-content">
                                                                        <div class="tool-title">Warmup Endpoints</div>
                                                                        <div class="tool-desc">Preload critical endpoints to prime caches and reduce cold-start latency. Results will be shown in a popup.</div>
                                                                        <div class="tool-actions">
                                                                            <button id="warmup-btn" class="btn btn-secondary" aria-label="Warm up endpoints">Warm up</button>
                                                                            <span id="warmup-spinner" class="loading-spinner" style="display:none;"></span>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                                <div class="tool-item" role="listitem">
                                                                    <span class="material-icons tool-icon" aria-hidden="true">vpn_key</span>
                                                                    <div class="tool-content">
                                                                        <div class="tool-title">JWT Token Inspector</div>
                                                                        <div class="tool-desc">Paste a JWT to decode its header and payload, view claims and expiration, and check time validity.</div>
                                                                        <div class="tool-actions">
                                                                            <button id="jwt-inspector-btn" class="btn btn-secondary" aria-label="Open JWT Token Inspector">Open</button>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                                <div class="tool-item" role="listitem">
                                                                    <span class="material-icons tool-icon" aria-hidden="true">file_download</span>
                                                                    <div class="tool-content">
                                                                        <div class="tool-title">Export Financial Data</div>
                                                                        <div class="tool-desc">Export payment and subscription records to CSV format for financial reconciliation and reporting.</div>
                                                                        <div class="tool-actions">
                                                                            <button id="export-financial-btn" class="btn btn-secondary" aria-label="Export financial data">Export</button>
                                                                            <span id="export-financial-spinner" class="loading-spinner" style="display:none;"></span>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                            """
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Subscriptions Panel
                                        div {
                                            classes = setOf("tab-panel")
                                            id = "subscriptions"
                                            style = "width: 100%; box-sizing: border-box;"
                                            div {
                                                classes = setOf("dashboard-card")
                                                div {
                                                    classes = setOf("dashboard-card-content")
                                                    unsafe {
                                                        raw(
                                                            """
                                                            <div id="subscriptions-success-message" class="message success-message hidden"></div>
                                                            <div id="subscriptions-error-message" class="message error-message hidden"></div>

                                                            <!-- Analytics Summary Cards -->
                                                            <div class="subscription-stats" style="display:grid;grid-template-columns:repeat(4, minmax(0,1fr));gap:.75rem;margin-bottom:1.5rem">
                                                                <div class="dashboard-card" style="margin:0;padding:.75rem;min-height:auto">
                                                                    <div style="font-size:.8rem;color:var(--text-secondary)">Active Subscriptions</div>
                                                                    <div id="stat-active" style="font-size:1.4rem;font-weight:700;color:var(--card-title)">—</div>
                                                                </div>
                                                                <div class="dashboard-card" style="margin:0;padding:.75rem;min-height:auto">
                                                                    <div style="font-size:.8rem;color:var(--text-secondary)">Total Revenue</div>
                                                                    <div id="stat-revenue" style="font-size:1.4rem;font-weight:700;color:var(--card-title)">—</div>
                                                                </div>
                                                                <div class="dashboard-card" style="margin:0;padding:.75rem;min-height:auto">
                                                                    <div style="font-size:.8rem;color:var(--text-secondary)">Expired</div>
                                                                    <div id="stat-expired" style="font-size:1.4rem;font-weight:700;color:var(--card-title)">—</div>
                                                                </div>
                                                                <div class="dashboard-card" style="margin:0;padding:.75rem;min-height:auto">
                                                                    <div style="font-size:.8rem;color:var(--text-secondary)">Cancelled</div>
                                                                    <div id="stat-cancelled" style="font-size:1.4rem;font-weight:700;color:var(--card-title)">—</div>
                                                                </div>
                                                            </div>

                                                            <!-- Status Filter -->
                                                            <div class="subscription-filters" style="margin-bottom:1rem">
                                                                <select id="status-filter" class="role-select">
                                                                    <option value="">All Statuses</option>
                                                                    <option value="ACTIVE">Active</option>
                                                                    <option value="EXPIRED">Expired</option>
                                                                    <option value="CANCELLED">Cancelled</option>
                                                                    <option value="GRACE_PERIOD">Grace Period</option>
                                                                </select>
                                                            </div>

                                                            <div id="subscriptions-loader" class="skeleton" style="height:8px;width:100%;display:none;"></div>

                                                            <!-- Subscriptions Table -->
                                                            <table class="users-table subscriptions-table">
                                                                <colgroup>
                                                                    <col style="min-width:180px">
                                                                    <col style="min-width:100px">
                                                                    <col style="min-width:120px">
                                                                    <col style="min-width:120px">
                                                                    <col style="min-width:100px">
                                                                    <col style="min-width:90px">
                                                                    <col style="min-width:90px">
                                                                    <col style="min-width:80px">
                                                                </colgroup>
                                                                <thead>
                                                                    <tr>
                                                                        <th>User Email</th>
                                                                        <th>Service</th>
                                                                        <th>Start Date</th>
                                                                        <th>End Date</th>
                                                                        <th>Status</th>
                                                                        <th>Days Left</th>
                                                                        <th>Amount</th>
                                                                        <th class="actions-col" aria-label="Actions"></th>
                                                                    </tr>
                                                                </thead>
                                                                <tbody id="subscriptions-table-body"></tbody>
                                                            </table>

                                                            <!-- Pagination -->
                                                            <div id="subscriptions-pagination" style="display:flex;gap:0.5rem;margin-top:1rem;"></div>
                                                            """
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Service Catalog Panel
                                        div {
                                            classes = setOf("tab-panel")
                                            id = "service-catalog"
                                            style = "width: 100%; box-sizing: border-box;"
                                            div {
                                                classes = setOf("dashboard-card")
                                                div {
                                                    classes = setOf("dashboard-card-content")
                                                    unsafe {
                                                        raw(
                                                            """
                                                            <!-- Service Catalog Controls -->
                                                            <div class="service-catalog-controls" style="display: flex; gap: 1rem; margin-bottom: 1.5rem; flex-wrap: wrap; align-items: center;">
                                                                <input
                                                                    type="text"
                                                                    id="service-search-input"
                                                                    placeholder="Search services..."
                                                                    class="form-control"
                                                                    style="flex: 1; min-width: 200px; padding: 0.5rem 0.75rem; border: 1px solid var(--endpoint-border); border-radius: 6px; background: var(--card-bg); color: var(--text-color);"
                                                                />
                                                                <select id="service-status-filter" class="role-select" style="min-width: 150px;">
                                                                    <option value="ALL">All Statuses</option>
                                                                    <option value="ACTIVE">Active</option>
                                                                    <option value="INACTIVE">Inactive</option>
                                                                    <option value="ARCHIVED">Archived</option>
                                                                </select>
                                                                <button id="create-service-btn" class="btn btn-primary">
                                                                    <span class='material-icons' style='font-size:18px; vertical-align:middle; margin-right:6px;'>add</span>
                                                                    Create Service
                                                                </button>
                                                            </div>

                                                            <div id="service-catalog-loader" class="skeleton" style="height:8px;width:100%;display:none;"></div>

                                                            <!-- Services Table -->
                                                            <table class="users-table services-table">
                                                                <colgroup>
                                                                    <col style="min-width:200px">
                                                                    <col style="min-width:100px">
                                                                    <col style="min-width:120px">
                                                                    <col style="min-width:120px">
                                                                    <col style="min-width:140px">
                                                                    <col style="min-width:200px">
                                                                </colgroup>
                                                                <thead>
                                                                    <tr>
                                                                        <th>Service Code / Name</th>
                                                                        <th style="text-align: center;">Status</th>
                                                                        <th style="text-align: center;">Active Subs</th>
                                                                        <th style="text-align: right;">Starting Price</th>
                                                                        <th>Created At</th>
                                                                        <th style="text-align: center;">Actions</th>
                                                                    </tr>
                                                                </thead>
                                                                <tbody id="services-table-body">
                                                                    <tr>
                                                                        <td colspan="6" style="text-align: center; padding: 2rem; color: var(--text-secondary);">
                                                                            Loading services...
                                                                        </td>
                                                                    </tr>
                                                                </tbody>
                                                            </table>

                                                            <!-- Pagination -->
                                                            <div id="service-pagination" style="display:flex;gap:0.5rem;margin-top:1rem;justify-content:center;"></div>
                                                            """
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Announcements Panel
                                        div {
                                            classes = setOf("tab-panel")
                                            id = "announcements"
                                            style =
                                                "width: 100%; box-sizing: border-box;" // <-- Always full width
                                            div {
                                                classes = setOf("dashboard-card")
                                                div {
                                                    classes = setOf("dashboard-card-title")
                                                    unsafe {
                                                        raw(
                                                            """
                                                            <span style="display:inline-flex; align-items:center; gap:6px; position:relative;">
                                                                <span>Service Announcements</span>
                                                                <span id=\"ann-help-trigger\" class=\"material-icons\" tabindex=\"0\" aria-label=\"How to use and why to use Service Announcements\" aria-controls=\"ann-help-popover\" aria-expanded=\"false\" style=\"font-size:18px; color: var(--text-secondary); cursor: help;\">info</span>
                                                            </span>
                                                            <div id=\"ann-help-popover\" role=\"dialog\" aria-hidden=\"true\" aria-labelledby=\"ann-help-title\" style=\"position: fixed; display:none; z-index: 9999; max-width: 380px; padding: 12px 14px; border-radius: 10px; background: var(--endpoint-bg, var(--card-bg)); color: var(--text-color); border: 1px solid var(--endpoint-border, var(--card-border)); box-shadow: 0 8px 24px var(--card-shadow);\">\n                                                              <div id=\"ann-help-title\" style=\"font-weight:700; margin-bottom:6px; color: var(--card-title); display:flex; align-items:center; gap:6px;\">\n                                                                <span class=\"material-icons\" style=\"font-size:18px; color:#6366f1;\">campaign</span>\n                                                                <span>Service announcements guide</span>\n                                                              </div>\n                                                              <div style=\"font-size: 0.92rem; line-height: 1.5;\">\n                                                                <div style=\"margin-bottom:8px;\">\n                                                                  <strong>How to use</strong>\n                                                                  <ul style=\"margin:6px 0 0 18px;\">\n                                                                    <li>Write a clear message for all users.</li>\n                                                                    <li>Select severity: Info, Warning, or Critical.</li>\n                                                                    <li>Optionally set Duration (minutes) or an Until date-time.</li>\n                                                                    <li>If both time fields are empty, the announcement remains until you clear it.</li>\n                                                                  </ul>\n                                                                </div>\n                                                                <div>\n                                                                  <strong>When to use (examples)</strong>\n                                                                  <ul style=\"margin:6px 0 0 18px;\">\n                                                                    <li>Planned maintenance: warning with an until time.</li>\n                                                                    <li>Service outage: critical with short duration.</li>\n                                                                    <li>New feature rollout: info without time.</li>\n                                                                  </ul>\n                                                                </div>\n                                                              </div>\n                                                            </div>\n                                                            <script>\n                                                            (function(){\n                                                              if (window.__annHelpBound) return;\n                                                              window.__annHelpBound = true;\n                                                              function initAnnPopover(){\n                                                                try {\n                                                                  var trigger = document.getElementById('ann-help-trigger');\n                                                                  var pop = document.getElementById('ann-help-popover');\n                                                                  if (!trigger || !pop) return;\n                                                                  var hoverTimeout;\n                                                                  function show(){\n                                                                    if (!pop) return;\n                                                                    if (hoverTimeout) { clearTimeout(hoverTimeout); }\n                                                                    // Position near the icon (viewport coords)\n                                                                    var rect = trigger.getBoundingClientRect();\n                                                                    pop.style.left = Math.max(12, Math.min(rect.left, window.innerWidth - 400)) + 'px';\n                                                                    pop.style.top = (rect.bottom + 8) + 'px';\n                                                                    pop.style.display = 'block';\n                                                                    pop.setAttribute('aria-hidden','false');\n                                                                    trigger.setAttribute('aria-expanded','true');\n                                                                  }\n                                                                  function hide(){\n                                                                    hoverTimeout = setTimeout(function(){\n                                                                      if (!pop) return;\n                                                                      pop.style.display = 'none';\n                                                                      pop.setAttribute('aria-hidden','true');\n                                                                      trigger.setAttribute('aria-expanded','false');\n                                                                    }, 120);\n                                                                  }\n                                                                  trigger.addEventListener('mouseenter', show);\n                                                                  trigger.addEventListener('mouseleave', hide);\n                                                                  pop.addEventListener('mouseenter', function(){ if (hoverTimeout) clearTimeout(hoverTimeout); });\n                                                                  pop.addEventListener('mouseleave', hide);\n                                                                  trigger.addEventListener('click', function(e){ e.stopPropagation(); if (pop.style.display === 'block') { pop.style.display = 'none'; pop.setAttribute('aria-hidden','true'); trigger.setAttribute('aria-expanded','false'); } else { show(); } });\n                                                                  document.addEventListener('click', function(e){ if (!pop || pop.style.display !== 'block') return; if (!pop.contains(e.target) && e.target !== trigger) { pop.style.display = 'none'; pop.setAttribute('aria-hidden','true'); trigger.setAttribute('aria-expanded','false'); }});\n                                                                  document.addEventListener('keydown', function(e){ if (e.key === 'Escape') { if (!pop) return; pop.style.display = 'none'; pop.setAttribute('aria-hidden','true'); trigger.setAttribute('aria-expanded','false'); }});\n                                                                  window.addEventListener('resize', function(){ if (pop && pop.style.display === 'block') { var r = trigger.getBoundingClientRect(); pop.style.left = Math.max(12, Math.min(r.left, window.innerWidth - 400)) + 'px'; pop.style.top = (r.bottom + 8) + 'px'; }});\n                                                                  window.addEventListener('scroll', function(){ if (pop && pop.style.display === 'block') { var r2 = trigger.getBoundingClientRect(); pop.style.left = Math.max(12, Math.min(r2.left, window.innerWidth - 400)) + 'px'; pop.style.top = (r2.bottom + 8) + 'px'; }}, true);\n                                                                } catch (err) { console.error('Failed to init announcement help popover', err); }\n                                                              }\n                                                              if (document.readyState === 'loading') { document.addEventListener('DOMContentLoaded', initAnnPopover); } else { initAnnPopover(); }\n                                                            })();\n                                                            </script>\n                                                            """
                                                        )
                                                    }
                                                }
                                                div {
                                                    classes = setOf("dashboard-card-content")
                                                    unsafe {
                                                        raw(
                                                            """
                                                            <div id="ann-preview" class="announcement-preview" aria-live="polite"></div>
                                                            <form id="ann-form" class="form form-compact" onsubmit="return false;">
                                                                <div class="form-group">
                                                                    <label for="ann-message" class="form-label">Message</label>
                                                                    <textarea id="ann-message" class="form-control" rows="3" placeholder="Enter announcement message"></textarea>
                                                                </div>
                                                                <div id="ann-severity-group" class="radio-group" role="radiogroup" aria-label="Announcement severity">
                                                                    <label class="badge pill-radio"><input type="radio" name="ann-sev" value="info" checked> Info</label>
                                                                    <label class="badge pill-radio"><input type="radio" name="ann-sev" value="warning"> Warning</label>
                                                                    <label class="badge pill-radio"><input type="radio" name="ann-sev" value="critical"> Critical</label>
                                                                </div>
                                                                <div class="form-row">
                                                                    <input id="ann-duration" class="form-control" type="number" min="0" placeholder="Duration (minutes)">
                                                                    <input id="ann-until" class="form-control" type="datetime-local" placeholder="Until (date and time)">
                                                                </div>
                                                                <div class="form-actions">
                                                                    <button type="button" id="ann-set-btn" class="btn btn-primary">Save <span id=\"ann-set-spinner\" class=\"loading-spinner\" style=\"display:none;margin-left:6px;width:14px;height:14px;border-width:2px;\"></span></button>
                                                                    <button type="button" id="ann-clear-btn" class="btn btn-secondary">Clear <span id=\"ann-clear-spinner\" class=\"loading-spinner\" style=\"display:none;margin-left:6px;width:14px;height:14px;border-width:2px;\"></span></button>
                                                                </div>
                                                            </form>
                                                            """
                                                        )
                                                    }
                                                }

                                            }
                                        }
                                    }
                                }
                            }

                        }
                        // Footer
                        createFooter(this)
                    }
                }
            }
                }
            }
        }
    }

}
