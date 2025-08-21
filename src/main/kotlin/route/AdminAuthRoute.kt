package bose.ankush.route

import bose.ankush.data.model.UserRole
import bose.ankush.route.common.WebResources
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import com.auth0.jwt.interfaces.Payload
import config.Environment
import config.JwtConfig
import domain.model.Result
import domain.service.AuthService
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receive
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.html.DIV
import kotlinx.html.HEAD
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import util.Constants
import java.time.Year

/**
 * Helper function to check if a user is an admin using the JWT payload
 * Delegates to JwtConfig.isAdmin for consistent role checking across the application
 */
fun isAdminFromPayload(payload: Payload): Boolean {
    // Use JwtConfig.isAdmin for consistent role checking
    return JwtConfig.isAdmin(payload)
}

@Serializable
data class AdminLoginRequest(val email: String, val password: String)

/**
 * Helper function to set up common HTML head elements
 */
fun HEAD.setupHead(title: String, includeAdminJs: Boolean = false) {
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
 * Helper function to create the header with logo and theme toggle
 */
fun createHeader(container: DIV) {
    container.div {
        classes = setOf("header")
        div {
            classes = setOf("brand-text")
            h1 {
                classes = setOf("logo")
                +"Androidplay"
            }
            span {
                classes = setOf("subtitle")
                +"Admin Portal"
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
                style = "position: relative; cursor: pointer; margin-right: 0.5rem;"

                input {
                    type = InputType.checkBox
                    id = "theme-toggle"
                }

                div {
                    // This div becomes the toggle button
                }
            }

            // GitHub icon
            span {
                classes = setOf("material-icons", "nav-icon", "github-link")
                id = "github-link"
                attributes["data-url"] = "https://github.com/bosankus/weatherify-api-ktor"
                +"code"
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
                            // Initialize theme toggle functionality
                            initializeTheme();

                            // Initialize login form
                            initializeLoginForm();

                            // Initialize GitHub link
                            const githubLink = document.getElementById('github-link');
                            if (githubLink) {
                                githubLink.addEventListener('click', function() {
                                    const url = this.getAttribute('data-url');
                                    if (url) {
                                        window.open(url, '_blank');
                                    }
                                });
                            }
                        }

                        function initializeLoginForm() {
                            const loginForm = document.getElementById('login-form');
                            const loginButton = document.getElementById('login-button');
                            const errorMessage = document.getElementById('error-message');
                            const successMessage = document.getElementById('success-message');

                            if (!loginForm || !loginButton) return;

                            // Check URL parameters for error messages
                            const urlParams = new URLSearchParams(window.location.search);
                            const errorParam = urlParams.get('error');

                            if (errorParam) {
                                if (errorParam === 'auth_required') {
                                    showError('Please login to access the admin dashboard');
                                } else if (errorParam === 'session_expired') {
                                    showError('Your session has expired. Please login again.');
                                } else if (errorParam === 'access_denied') {
                                    showError('Access denied. You do not have administrator privileges.');
                                }
                            }

                            // Check if already logged in with admin role
                            const token = localStorage.getItem('jwt_token');
                            if (token) {
                                // Verify if token is valid and has admin role
                                fetch('/admin/dashboard', {
                                    headers: {
                                        'Authorization': 'Bearer ' + token
                                    }
                                })
                                .then(response => {
                                    if (response.ok) {
                                        // User is authenticated and has admin role, redirect to dashboard
                                        window.location.href = '/admin/dashboard';
                                    }
                                })
                                .catch(error => {
                                    console.error('Error checking authentication:', error);
                                    // Clear invalid token
                                    localStorage.removeItem('jwt_token');
                                });
                            }

                            // Handle form submission
                            loginForm.addEventListener('submit', function(e) {
                                e.preventDefault();

                                const email = document.getElementById('email').value;
                                const password = document.getElementById('password').value;

                                // Validate inputs
                                if (!email || !password) {
                                    showError('Please enter both email and password');
                                    return;
                                }

                                // Show loading state
                                loginButton.classList.add('loading');
                                loginButton.disabled = true;

                                // Send login request
                                fetch('/admin/login', {
                                    method: 'POST',
                                    headers: {
                                        'Content-Type': 'application/json'
                                    },
                                    body: JSON.stringify({
                                        email: email,
                                        password: password
                                    })
                                })
                                .then(response => response.json())
                                .then(data => {
                                    if (data.status === true) {
                                        // Login successful
                                        showSuccess('Login successful! Redirecting to dashboard...');

                                        // Store token using consistent key
                                        console.log('Storing token in localStorage:', data.data.token.substring(0, 10) + '...');
                                        localStorage.setItem('jwt_token', data.data.token);

                                        // Store user info for role-based access
                                        try {
                                            // Parse the JWT to get user info
                                            const token = data.data.token;
                                            const tokenParts = token.split('.');
                                            if (tokenParts.length === 3) {
                                                const payload = JSON.parse(atob(tokenParts[1]));
                                                const userInfo = {
                                                    email: payload.email || email,
                                                    role: payload.role || 'USER',
                                                    // Handle isActive as either boolean or string
                                                    isActive: payload.isActive === 'false' ? false : Boolean(payload.isActive)
                                                };
                                                localStorage.setItem('user_info', JSON.stringify(userInfo));
                                            }
                                        } catch (e) {
                                            console.error('Error parsing JWT token:', e);
                                        }

                                        // Redirect to intended destination or dashboard
                                        setTimeout(() => {
                                            const intendedDestination = sessionStorage.getItem('intendedDestination');

                                            // Use the navigateToAdminPage function which handles token authentication properly
                                            if (typeof navigateToAdminPage === 'function') {
                                                console.log('Using navigateToAdminPage function for redirection');
                                                if (intendedDestination && intendedDestination.startsWith('/admin')) {
                                                    console.log('Redirecting to intended destination:', intendedDestination);
                                                    sessionStorage.removeItem('intendedDestination');
                                                    navigateToAdminPage(intendedDestination);
                                                } else {
                                                    console.log('Redirecting to dashboard');
                                                    navigateToAdminPage('/admin/dashboard');
                                                }
                                            } else {
                                                // Fallback if navigateToAdminPage is not available
                                                console.error('navigateToAdminPage function not found, this should not happen');
                                                // Redirect to login page with error
                                                window.location.href = '/admin/login?error=auth_required';
                                            }
                                        }, 1000);
                                    } else {
                                        // Login failed
                                        loginButton.classList.remove('loading');
                                        loginButton.disabled = false;
                                        showError(data.message || 'Invalid email or password');
                                    }
                                })
                                .catch(error => {
                                    // Remove loading state
                                    loginButton.classList.remove('loading');
                                    loginButton.disabled = false;
                                    showError('An error occurred. Please try again.');
                                    console.error('Login error:', error);
                                });
                            });
                        }

                        // Helper function to show error message
                        function showError(message) {
                            const errorMessage = document.getElementById('error-message');
                            const successMessage = document.getElementById('success-message');

                            if (!errorMessage || !successMessage) return;

                            errorMessage.textContent = message;
                            errorMessage.classList.add('visible');
                            successMessage.classList.remove('visible');

                            // Scroll to the error message to ensure it's visible
                            errorMessage.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

                            // Hide error after 6 seconds
                            setTimeout(() => {
                                errorMessage.classList.remove('visible');
                            }, 6000);
                        }

                        // Helper function to show success message
                        function showSuccess(message) {
                            const errorMessage = document.getElementById('error-message');
                            const successMessage = document.getElementById('success-message');

                            if (!errorMessage || !successMessage) return;

                            successMessage.textContent = message;
                            successMessage.classList.add('visible');
                            errorMessage.classList.remove('visible');

                            // Scroll to the success message to ensure it's visible
                            successMessage.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                        }
                        """
                    )
                }
            }
        }
        body {
            div {
                classes = setOf("container")

                // Header with logo and theme toggle
                createHeader(this)

                // Content area with login form
                div {
                    classes = setOf("content-area")
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

                        // Error and success messages (moved below login button)
                        div {
                            id = "error-message"
                            classes = setOf(
                                "message",
                                "error-message",
                                if (errorMessage != null) "visible" else ""
                            )
                            style = "margin-top: 1.5rem; width: 100%;"
                            if (errorMessage != null) {
                                +errorMessage
                            }
                        }
                        div {
                            id = "success-message"
                            classes = setOf("message", "success-message")
                            style = "margin-top: 1.5rem; width: 100%;"
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
            // Manually check for JWT token in the Authorization header
            val authHeader = call.request.headers["Authorization"]
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    // Extract the token
                    val token = authHeader.substring(7)
                    // Verify the token
                    val decodedJWT = JwtConfig.verifier.verify(token)

                    logger.info("JWT token manually verified: ${decodedJWT.token}")

                    // Check if user has ADMIN role
                    if (JwtConfig.isAdmin(decodedJWT)) {
                        // User is already authenticated and has ADMIN role, set auth cookie and redirect to dashboard
                        val userEmail =
                            decodedJWT.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
                        logger.info("Admin user already authenticated: $userEmail, setting cookie and redirecting to dashboard")

                        // Set jwt_token cookie so that browser navigation to /admin/dashboard is authenticated
                        try {
                            val maxAgeSeconds =
                                (Environment.getJwtExpiration() / 1000).toInt()
                            call.response.cookies.append(
                                Cookie(
                                    name = "jwt_token",
                                    value = token,
                                    path = "/",
                                    httpOnly = true,
                                    secure = true,
                                    maxAge = maxAgeSeconds
                                )
                            )
                        } catch (e: Exception) {
                            logger.warn("Failed to set auth cookie on /admin/login redirect: ${e.message}")
                        }

                        call.respondRedirect("/admin/dashboard", permanent = false)
                        return@get
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to validate JWT token: ${e.message}")
                    // Continue to login page if token validation fails
                }
            }

            // If no valid token or not an admin, check for principal (for backward compatibility)
            val principal = call.principal<JWTPrincipal>()
            logger.info("JWT Payload from principal: ${principal?.payload?.toString()}")
            if (principal != null && isAdminFromPayload(principal.payload)) {
                val userEmail =
                    principal.payload.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
                logger.info("Admin user already authenticated: $userEmail, redirecting to dashboard")
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
                                call.response.cookies.append(
                                    Cookie(
                                        name = "jwt_token",
                                        value = token,
                                        path = "/",
                                        httpOnly = true,
                                        secure = true,
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
                    call.response.cookies.append(
                        Cookie(
                            name = "jwt_token",
                            value = "",
                            path = "/",
                            httpOnly = true,
                            secure = true,
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

        // --- MOVE /admin/dashboard OUTSIDE authenticate("jwt-auth") ---
        get("/dashboard") {
            val authHeader = call.request.headers["Authorization"]
            var jwtToken: String?

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwtToken = authHeader.substring(7)
            } else {
                jwtToken = call.request.cookies["jwt_token"]
                if (jwtToken != null) {
                    logger.info("JWT token found in cookie for /admin/dashboard")
                }
            }

            var principal: JWTPrincipal? = null
            var userEmail: String? = null

            if (jwtToken != null) {
                try {
                    val decodedJWT = JwtConfig.verifier.verify(jwtToken)
                    userEmail = decodedJWT.getClaim(Constants.Auth.JWT_CLAIM_EMAIL).asString()
                    principal = JWTPrincipal(decodedJWT)
                    logger.info("JWT principal created for user: $userEmail")
                } catch (e: Exception) {
                    logger.warn("Failed to verify JWT: ${e.message}")
                    call.respondRedirect("/admin/login?error=session_expired", permanent = false)
                    return@get
                }
            }

            if (principal?.payload == null) {
                logger.warn("No JWT principal found for dashboard access attempt")
                call.respondRedirect("/admin/login?error=auth_required", permanent = false)
                return@get
            }

            if (!JwtConfig.isAdmin(principal.payload)) {
                logger.warn("Non-admin user ($userEmail) attempted to access admin dashboard")
                call.respondRedirect("/admin/login?error=access_denied", permanent = false)
                return@get
            }

            // User is authenticated and has ADMIN role, serve the admin dashboard
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
    /* Align with shared container/content-area widths */
    position: relative;
    width: 100%;
    max-width: 1200px;
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
                                .dashboard-card-content table {
                                    width: 100%;
                                    table-layout: fixed;
                                    border-collapse: separate !important;
                                    border-spacing: 0 10px;
                                }
                                .dashboard-card-content table.users-table th,
                                .dashboard-card-content table.users-table td {
                                    overflow: hidden;
                                    text-overflow: ellipsis;
                                    white-space: nowrap;
                                }
                                .dashboard-card-content table.users-table th:nth-child(1),
                                .dashboard-card-content table.users-table td:nth-child(1) { width: 34%; }
                                .dashboard-card-content table.users-table th:nth-child(2),
                                .dashboard-card-content table.users-table td:nth-child(2) { width: 18%; }
                                .dashboard-card-content table.users-table th:nth-child(3),
                                .dashboard-card-content table.users-table td:nth-child(3) { width: 18%; }
                                .dashboard-card-content table.users-table th:nth-child(4),
                                .dashboard-card-content table.users-table td:nth-child(4) { width: 15%; }
                                .dashboard-card-content table.users-table th:nth-child(5),
                                .dashboard-card-content table.users-table td:nth-child(5) { width: 15%; }
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

                                /* Toast notifications */
                                .toast-container { position: fixed; top: 1rem; right: 1rem; display: flex; flex-direction: column; gap: 0.5rem; z-index: 9999; }
                                .toast { min-width: 260px; max-width: 420px; padding: 0.75rem 1rem; border-radius: 8px; background: var(--endpoint-bg, var(--card-bg)); color: var(--text-color); border: 1px solid var(--endpoint-border, var(--card-border)); box-shadow: 0 8px 24px var(--card-shadow); display: flex; align-items: center; gap: 0.6rem; transform: translateX(120%); opacity: 0; transition: transform 0.25s ease, opacity 0.25s ease; }
                                .toast-visible { transform: translateX(0); opacity: 1; }
                                .toast-hide { transform: translateX(120%); opacity: 0; }
                                .toast .toast-icon { font-size: 20px; line-height: 1; }
                                .toast-success { border-left: 4px solid #10b981; }
                                .toast-error { border-left: 4px solid #ef4444; }
                                .toast-info { border-left: 4px solid #3b82f6; }
                                .toast-success .toast-icon { color: #10b981; }
                                .toast-error .toast-icon { color: #ef4444; }
                                .toast-info .toast-icon { color: #3b82f6; }
                                .toast-message { flex: 1; }

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
                                            function activateTab(name){
                                                tabs.forEach(t => t.classList.toggle('active', t.dataset.tab === name));
                                                panels.forEach(p => p.classList.toggle('active', p.id === name));
                                                if(name === 'iam' && !iamLoaded){
                                                    iamLoaded = true;
                                                    if (typeof loadUsers === 'function') {
                                                        loadUsers(1, 10);
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
                            "max-width: 1200px; width: 100%; margin: 0 auto; box-sizing: border-box;" // <-- Ensure fixed width

                        div {
                            classes = setOf("header")
                            style =
                                "margin-top: 0 !important; margin-bottom: 0;" // Reduce top margin for dashboard header
                            div {
                                classes = setOf("brand-text")
                                h1 {
                                    classes = setOf("logo")
                                    +"Androidplay"
                                }
                                span {
                                    classes = setOf("subtitle")
                                    +"Admin Portal"
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
                                div {
                                    classes = setOf("admin-user-info")
                                    span { +"Logged in as: " }
                                    span {
                                        classes = setOf("admin-user-email")
                                        id = "admin-email"
                                        +(userEmail ?: "Unknown User")
                                    }
                                    span {
                                        classes = setOf("admin-logout")
                                        id = "logout-button"
                                        +"Logout"
                                    }
                                }
                            }

                            // Dashboard content
                            div {
                                classes = setOf("dashboard-content")
                                style =
                                    "display: grid; gap: 2rem; width: 100%; box-sizing: border-box;" // <-- Always full width

                                // Welcome section (dismissible; shown once)
                                div {
                                    classes = setOf("dashboard-section")
                                    id = "welcome-section"
                                    style =
                                        "position: relative; min-width: 0; width: 100%; box-sizing: border-box;" // <-- Always full width
                                    div {
                                        classes = setOf("dashboard-card")
                                        div {
                                            classes = setOf("dashboard-card-title")
                                            +"Overview"
                                        }
                                        div {
                                            classes = setOf("dashboard-card-content")
                                            +"This is the admin dashboard for the Androidplay Weather API. Here you can manage users, view statistics, and perform administrative tasks."
                                        }
                                    }
                                }

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
                                                                    <col style="width:34%">
                                                                    <col style="width:18%">
                                                                    <col style="width:18%">
                                                                    <col style="width:15%">
                                                                    <col style="width:15%">
                                                                </colgroup>
                                                                <thead>
                                                                    <tr>
                                                                        <th>Email</th>
                                                                        <th>Created At</th>
                                                                        <th>Role</th>
                                                                        <th>Status</th>
                                                                        <th>Premium</th>
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
                                            div {
                                                classes = setOf("dashboard-card-content")
                                                +"Financial metrics and billing summaries will appear here."
                                            }
                                        }

                                        // Reports Panel
                                        div {
                                            classes = setOf("tab-panel")
                                            id = "reports"
                                            style =
                                                "width: 100%; box-sizing: border-box;" // <-- Always full width
                                            div {
                                                classes = setOf("dashboard-card-content")
                                                +"Reports and analytics will be shown here."
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
                                                            </div>
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
                    }
                }
            }
        }
    }

}
