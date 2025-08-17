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
import kotlinx.html.h3
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
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Year

/**
 * Helper function to check if a user is an admin using the JWT payload
 * Delegates to JwtConfig.isAdmin for consistent role checking across the application
 */
fun isAdminFromPayload(payload: Payload): Boolean {
    // Use JwtConfig.isAdmin for consistent role checking
    return JwtConfig.isAdmin(payload)
}

/**
 * Helper function to set up common HTML head elements
 */
fun setupHead(head: HEAD, title: String, includeAdminJs: Boolean = false) {
    head.title { +title }
    head.meta { charset = "UTF-8" }
    head.meta {
        name = "viewport"
        content = "width=device-width, initial-scale=1.0"
    }
    head.link {
        rel = "preconnect"
        href = "https://fonts.googleapis.com"
    }
    head.link {
        rel = "preconnect"
        href = "https://fonts.gstatic.com"
        attributes["crossorigin"] = ""
    }
    head.link {
        rel = "stylesheet"
        href =
            "https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap"
    }
    head.link {
        rel = "stylesheet"
        href = "https://fonts.googleapis.com/icon?family=Material+Icons"
    }

    // Include shared CSS
    WebResources.includeSharedCss(head)

    // Include shared JavaScript
    WebResources.includeSharedJs(head)

    // Always include auth.js for authentication functions
    head.script {
        src = "/web/js/auth.js"
        defer = true
    }

    // Include admin JavaScript if needed
    if (includeAdminJs) {
        WebResources.includeAdminJs(head)
    }
}

/**
 * Helper function to create the header with logo and theme toggle
 */
fun createHeader(container: DIV, subtitle: String) {
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
                +subtitle
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
            setupHead(this, "Admin Login - Androidplay Weather API")

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
                createHeader(this, "Admin")

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
    // Ensure static resource check runs when routes are registered
    AdminStaticResourceChecker

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
                    setupHead(this, pageName, includeAdminJs = true)

                    // Include page-specific CSS if needed
                    style {
                        unsafe {
                            raw(
                                """
                                /* Admin dashboard specific styles */
                                .admin-container {
                                    max-width: 1200px;
                                    margin: 2rem auto;
                                    padding: 2rem;
                                    background: var(--card-bg);
                                    border: 1px solid var(--card-border);
                                    border-radius: 12px;
                                    box-shadow: 0 4px 12px var(--card-shadow);
                                    backdrop-filter: blur(10px);
                                    -webkit-backdrop-filter: blur(10px);
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
                                    background: var(--card-bg);
                                    border: 1px solid var(--card-border);
                                    border-radius: 8px;
                                    padding: 1.5rem;
                                    transition: all 0.3s ease;
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
                                    border-radius: 6px;
                                    padding: 1rem;
                                    margin-bottom: 1rem;
                                }
                                
                                .dashboard-card-title {
                                    font-weight: 600;
                                    color: var(--card-title);
                                    margin-bottom: 0.5rem;
                                }
                                
                                .dashboard-card-content {
                                    color: var(--text-secondary);
                                    line-height: 1.6;
                                }
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
                            div {
                                classes = setOf("admin-header")
                                h2 {
                                    classes = setOf("admin-title")
                                    +"Admin Dashboard"
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
                                div {
                                    classes = setOf("dashboard-section")
                                    h3 {
                                        classes = setOf("dashboard-section-title")
                                        +"Welcome to the Admin Dashboard"
                                    }
                                    div {
                                        classes = setOf("dashboard-card")
                                        div {
                                            classes = setOf("dashboard-card-title")
                                            +"Dashboard Overview"
                                        }
                                        div {
                                            classes = setOf("dashboard-card-content")
                                            +"This is the admin dashboard for the Androidplay Weather API. Here you can manage users, view statistics, and perform administrative tasks."
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

/**
 * Request body for admin login
 */
@Serializable
data class AdminLoginRequest(
    val email: String,
    val password: String
)

/**
 * Deployment Note:
 * Ensure that the file 'auth.js' is present in your static resources directory (e.g., /static/web/js/auth.js).
 * If you use Ktor's static file serving, place 'auth.js' in the correct location so that '/web/js/auth.js' is accessible.
 * Example: /resources/static/web/js/auth.js or /public/web/js/auth.js depending on your setup.
 *
 * Optional: Add a startup check to log a warning if the file is missing.
 */
object AdminStaticResourceChecker {
    init {
        val path = Paths.get("static/web/js/auth.js") // Adjust path as per your deployment
        if (!Files.exists(path)) {
            LoggerFactory.getLogger("Startup")
                .warn("Missing static resource: /web/js/auth.js. Admin login/dashboard will fail to load JS.")
        }
    }
}
