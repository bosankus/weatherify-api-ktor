package bose.ankush.route

import bose.ankush.data.model.UserRole
import bose.ankush.route.common.WebResources
import bose.ankush.route.common.respondError
import bose.ankush.route.common.respondSuccess
import domain.model.Result
import domain.repository.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receive
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.html.ButtonType
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
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.unsafe
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import util.FilteredUser
import util.PrivacyLevel
import util.UserPrivacyFilter

/**
 * Admin routes for user management
 */
fun Route.adminRoute() {
    val userRepository: UserRepository by application.inject()
    val pageName = "Admin Dashboard"
    val logger = LoggerFactory.getLogger("AdminRoute")

    /**
     * Verifies that the user has admin access
     *
     * @param call The ApplicationCall to check
     * @param isApiCall Whether this is an API call (true) or a UI route (false)
     * @return true if the user has admin access, false otherwise
     */
    suspend fun verifyAdminAccess(call: ApplicationCall, isApiCall: Boolean = false): Boolean {
        // Get the JWT principal from the call
        val principal = call.principal<JWTPrincipal>()
        val email = principal?.payload?.getClaim("email")?.asString()

        // Check if the user is authenticated
        if (email == null) {
            logger.warn("Unauthenticated access attempt to admin route")
            if (isApiCall) {
                // For API calls, return 401 Unauthorized
                call.respondError(
                    "Authentication required",
                    Unit,
                    HttpStatusCode.Unauthorized
                )
            } else {
                // For UI routes, redirect to login page
                call.respondRedirect("/admin/login")
            }
            return false
        }

        // Get the user to check if they have admin role
        val userResult = userRepository.findUserByEmail(email)
        if (userResult is Result.Error) {
            logger.error("Failed to verify admin access: ${userResult.message}")
            val errorMessage = "Failed to verify admin access: ${userResult.message}"
            if (isApiCall) {
                call.respondError(
                    errorMessage,
                    Unit,
                    HttpStatusCode.InternalServerError
                )
            } else {
                call.respondRedirect("/admin/login?error=server_error")
            }
            return false
        }

        val user = (userResult as Result.Success).data
        if (user == null || user.role != UserRole.ADMIN) {
            logger.warn("Non-admin access attempt by user: $email")
            if (isApiCall) {
                call.respondError(
                    "Admin access required",
                    Unit,
                    HttpStatusCode.Forbidden
                )
            } else {
                call.respondRedirect("/admin/login?error=admin_required")
            }
            return false
        }

        // User has admin access
        return true
    }

    // Admin Login Route
    route("/admin/login") {
        get {
            val loginPageName = "Admin Login"

            call.respondHtml(HttpStatusCode.OK) {
                attributes["lang"] = "en"
                head {
                    title { +loginPageName }
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
                                /* Login form styles */
                                .login-container {
                                    max-width: 450px;
                                    margin: 3rem auto;
                                    padding: 2.5rem;
                                    background: var(--card-bg);
                                    border: 1px solid var(--card-border);
                                    border-radius: 12px;
                                    box-shadow: 0 4px 12px var(--card-shadow);
                                }
                                
                                .login-form {
                                    display: flex;
                                    flex-direction: column;
                                    gap: 2rem;
                                }
                                
                                .form-group {
                                    display: flex;
                                    flex-direction: column;
                                    gap: 0.75rem;
                                }
                                
                                .form-group label {
                                    font-weight: 500;
                                    color: var(--text-primary);
                                    font-size: 1.05rem;
                                }
                                
                                .form-group input {
                                    padding: 0.85rem 1.2rem;
                                    border: 1px solid var(--input-border);
                                    border-radius: 8px;
                                    background: var(--input-bg);
                                    color: var(--text-primary);
                                    font-family: 'Space Grotesk', sans-serif;
                                    font-size: 1rem;
                                    transition: border-color 0.2s, box-shadow 0.2s;
                                }
                                
                                .form-group input:focus {
                                    border-color: var(--primary-color);
                                    box-shadow: 0 0 0 2px rgba(79, 70, 229, 0.2);
                                    outline: none;
                                }
                                
                                .login-button {
                                    position: relative;
                                    padding: 0.85rem 1.2rem;
                                    background: linear-gradient(135deg, #3b4f7d, #2d3748);
                                    color: white;
                                    border: none;
                                    border-radius: 8px;
                                    font-weight: 600;
                                    font-size: 1.05rem;
                                    cursor: pointer;
                                    transition: transform 0.1s, box-shadow 0.2s;
                                    overflow: hidden;
                                }
                                
                                .login-button:hover {
                                    transform: translateY(-1px);
                                    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
                                }
                                
                                .login-button:active {
                                    transform: translateY(0);
                                }
                                
                                /* Loading animation for login button */
                                .login-button.loading {
                                    color: transparent;
                                }
                                
                                .login-button.loading::after {
                                    content: "";
                                    position: absolute;
                                    top: 50%;
                                    left: 50%;
                                    width: 20px;
                                    height: 20px;
                                    margin: -10px 0 0 -10px;
                                    border: 3px solid rgba(255, 255, 255, 0.3);
                                    border-radius: 50%;
                                    border-top-color: white;
                                    animation: spin 1s ease-in-out infinite;
                                }
                                
                                @keyframes spin {
                                    to { transform: rotate(360deg); }
                                }
                                
                                .error-message {
                                    color: #f87171;
                                    font-size: 0.95rem;
                                    margin-top: 0.75rem;
                                    display: none;
                                }
                                
                                .error-message.visible {
                                    display: block;
                                }
                                """
                            )
                        }
                    }

                    // Include shared JavaScript
                    WebResources.includeSharedJs(this)

                    // Include login-specific JavaScript
                    script {
                        unsafe {
                            raw(
                                """
                                // Login form handling
                                document.addEventListener('DOMContentLoaded', function() {
                                    const loginForm = document.getElementById('login-form');
                                    const errorMessage = document.getElementById('error-message');
                                    const loginButton = document.querySelector('.login-button');
                                    
                                    
                                    // Check for error parameter in URL
                                    const urlParams = new URLSearchParams(window.location.search);
                                    const errorParam = urlParams.get('error');
                                    if (errorParam === 'admin_required') {
                                        showError('Admin access required. Please login with an admin account.');
                                    } else if (errorParam === 'session_expired') {
                                        showError('Your session has expired. Please login again.');
                                    } else if (errorParam === 'server_error') {
                                        showError('A server error occurred. Please try again later.');
                                    } else if (errorParam === 'authentication_failed') {
                                        showError('Authentication failed. Please check your credentials and try again.');
                                    } else if (errorParam === 'invalid_token') {
                                        showError('Your authentication token is invalid. Please login again.');
                                    } else if (errorParam) {
                                        // Handle any other error parameters
                                        showError('An error occurred: ' + errorParam.replace(/_/g, ' '));
                                    }
                                    
                                    // Check if already logged in
                                    const token = localStorage.getItem('admin_token');
                                    if (token) {
                                        // Try to validate token by making a request to the admin API
                                        // Don't show loading state during automatic validation
                                        fetch('/admin/users', {
                                            headers: {
                                                'Authorization': 'Bearer ' + token
                                            }
                                        })
                                        .then(response => {
                                            if (response.ok) {
                                                // Token is valid, redirect to admin dashboard
                                                window.location.href = '/admin';
                                            }
                                        })
                                        .catch(error => {
                                            // Token validation failed, clear it
                                            localStorage.removeItem('admin_token');
                                            console.error('Token validation failed:', error);
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
                                        
                                        // Send login request
                                        fetch('/login', {
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
                                                const token = data.data.token;
                                                
                                                // Store token in localStorage
                                                localStorage.setItem('admin_token', token);
                                                
                                                // Log for debugging
                                                console.log('Login successful, token stored');
                                                
                                                // Add a small delay to ensure token is stored before redirect
                                                setTimeout(() => {
                                                    // Make a test request to verify token is working
                                                    fetch('/admin/users', {
                                                        headers: {
                                                            'Authorization': 'Bearer ' + token
                                                        }
                                                    })
                                                    .then(response => {
                                                        if (response.ok) {
                                                            console.log('Token validation successful');
                                                            // Redirect to admin dashboard
                                                            window.location.href = '/admin';
                                                        } else {
                                                            console.error('Token validation failed after login');
                                                            loginButton.classList.remove('loading');
                                                            showError('Authentication error. Please try again.');
                                                        }
                                                    })
                                                    .catch(error => {
                                                        console.error('Token validation error:', error);
                                                        loginButton.classList.remove('loading');
                                                        showError('Authentication error. Please try again.');
                                                    });
                                                }, 100);
                                            } else {
                                                // Login failed
                                                loginButton.classList.remove('loading');
                                                showError(data.message || 'Login failed');
                                            }
                                        })
                                        .catch(error => {
                                            // Remove loading state
                                            loginButton.classList.remove('loading');
                                            showError('An error occurred. Please try again.');
                                            console.error('Login error:', error);
                                        });
                                    });
                                    
                                    // Helper function to show error message
                                    function showError(message) {
                                        errorMessage.textContent = message;
                                        errorMessage.classList.add('visible');
                                        
                                        // Hide error after 5 seconds
                                        setTimeout(() => {
                                            errorMessage.classList.remove('visible');
                                        }, 5000);
                                    }
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
                                    +"Admin Login"
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

                                // GitHub icon
                                span {
                                    classes = setOf("material-icons", "nav-icon", "github-link")
                                    id = "github-link"
                                    attributes["data-url"] =
                                        "https://github.com/bosankus/weatherify-api-ktor"
                                    +"code"
                                }
                            }
                        }

                        // Login form
                        div {
                            classes = setOf("login-container")
                            h2 { +"Admin Login" }
                            p { +"Please enter your credentials to access the admin dashboard." }

                            form {
                                id = "login-form"
                                classes = setOf("login-form")

                                div {
                                    classes = setOf("form-group")
                                    label {
                                        htmlFor = "email"
                                        +"Email"
                                    }
                                    input {
                                        type = InputType.email
                                        id = "email"
                                        name = "email"
                                        placeholder = "Enter your email"
                                        required = true
                                    }
                                }

                                div {
                                    classes = setOf("form-group")
                                    label {
                                        htmlFor = "password"
                                        +"Password"
                                    }
                                    input {
                                        type = InputType.password
                                        id = "password"
                                        name = "password"
                                        placeholder = "Enter your password"
                                        required = true
                                    }
                                }

                                div {
                                    id = "error-message"
                                    classes = setOf("error-message")
                                }

                                button {
                                    type = ButtonType.submit
                                    classes = setOf("login-button")
                                    +"Login"
                                }
                            }
                        }

                        // Footer
                        footer {
                            classes = setOf("footer")
                            div {
                                classes = setOf("footer-content")
                                p { +"© ${java.time.Year.now().value} Androidplay API Admin Dashboard" }
                            }
                        }
                    }
                }
            }
        }
    }

    // Admin Dashboard UI Route
    authenticate("jwt-auth") {
        route("/admin") {
            get {
                // Verify admin access
                if (!verifyAdminAccess(call)) {
                    return@get
                }

                // Get the admin user's email for the dashboard
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim("email")?.asString()!!

                // Get all users for the dashboard
                val result = userRepository.getAllUsers(
                    filter = null,
                    sortBy = "email",
                    sortOrder = 1,
                    page = 1,
                    pageSize = 100
                )

                when (result) {
                    is Result.Success -> {
                        val (users, totalCount) = result.data

                        // Respond with HTML dashboard
                        call.respondHtml(HttpStatusCode.OK) {
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
                                            /* Admin-specific styles */
                                            .admin-table {
                                                width: 100%;
                                                border-collapse: collapse;
                                                margin-top: 1rem;
                                                background: var(--card-bg);
                                                border-radius: 8px;
                                                overflow: hidden;
                                                box-shadow: 0 4px 12px var(--card-shadow);
                                            }
                                            
                                            .admin-table th, .admin-table td {
                                                padding: 1rem;
                                                text-align: left;
                                                border-bottom: 1px solid var(--card-border);
                                            }
                                            
                                            .admin-table th {
                                                background: linear-gradient(135deg, #3b4f7d, #2d3748);
                                                color: white;
                                                font-weight: 600;
                                            }
                                            
                                            .admin-table tr:hover {
                                                background: var(--card-hover-bg);
                                            }
                                            
                                            .admin-table tr:last-child td {
                                                border-bottom: none;
                                            }
                                            
                                            .admin-card {
                                                background: var(--card-bg);
                                                border: 1px solid var(--card-border);
                                                border-radius: 12px;
                                                padding: 2rem;
                                                margin-bottom: 2rem;
                                                box-shadow: 0 4px 12px var(--card-shadow);
                                            }
                                            
                                            .admin-card h2 {
                                                margin-bottom: 1rem;
                                                color: var(--card-title);
                                            }
                                            
                                            .status-badge {
                                                display: inline-block;
                                                padding: 0.25rem 0.75rem;
                                                border-radius: 20px;
                                                font-size: 0.875rem;
                                                font-weight: 500;
                                            }
                                            
                                            .status-active {
                                                background: rgba(34, 197, 94, 0.15);
                                                color: #4ade80;
                                                border: 1px solid rgba(34, 197, 94, 0.25);
                                            }
                                            
                                            .status-inactive {
                                                background: rgba(239, 68, 68, 0.15);
                                                color: #f87171;
                                                border: 1px solid rgba(239, 68, 68, 0.25);
                                            }
                                            
                                            .role-badge {
                                                display: inline-block;
                                                padding: 0.25rem 0.75rem;
                                                border-radius: 20px;
                                                font-size: 0.875rem;
                                                font-weight: 500;
                                            }
                                            
                                            .role-admin {
                                                background: rgba(79, 70, 229, 0.15);
                                                color: #818cf8;
                                                border: 1px solid rgba(79, 70, 229, 0.25);
                                            }
                                            
                                            .role-moderator {
                                                background: rgba(245, 158, 11, 0.15);
                                                color: #fbbf24;
                                                border: 1px solid rgba(245, 158, 11, 0.25);
                                            }
                                            
                                            .role-user {
                                                background: rgba(59, 130, 246, 0.15);
                                                color: #60a5fa;
                                                border: 1px solid rgba(59, 130, 246, 0.25);
                                            }
                                            
                                            /* Action buttons */
                                            .action-button {
                                                padding: 0.25rem 0.5rem;
                                                border-radius: 4px;
                                                font-size: 0.75rem;
                                                font-weight: 500;
                                                cursor: pointer;
                                                margin-right: 0.25rem;
                                                margin-bottom: 0.25rem;
                                                border: 1px solid;
                                                transition: background-color 0.2s, transform 0.1s;
                                                display: inline-block;
                                            }
                                            
                                            .action-button:hover {
                                                transform: translateY(-1px);
                                            }
                                            
                                            .action-button:active {
                                                transform: translateY(0);
                                            }
                                            
                                            .activate {
                                                background: rgba(34, 197, 94, 0.15);
                                                color: #4ade80;
                                                border-color: rgba(34, 197, 94, 0.25);
                                            }
                                            
                                            .activate:hover {
                                                background: rgba(34, 197, 94, 0.25);
                                            }
                                            
                                            .deactivate {
                                                background: rgba(239, 68, 68, 0.15);
                                                color: #f87171;
                                                border-color: rgba(239, 68, 68, 0.25);
                                            }
                                            
                                            .deactivate:hover {
                                                background: rgba(239, 68, 68, 0.25);
                                            }
                                            
                                            .promote {
                                                background: rgba(79, 70, 229, 0.15);
                                                color: #818cf8;
                                                border-color: rgba(79, 70, 229, 0.25);
                                            }
                                            
                                            .promote:hover {
                                                background: rgba(79, 70, 229, 0.25);
                                            }
                                            
                                            .demote {
                                                background: rgba(245, 158, 11, 0.15);
                                                color: #fbbf24;
                                                border-color: rgba(245, 158, 11, 0.25);
                                            }
                                            
                                            .demote:hover {
                                                background: rgba(245, 158, 11, 0.25);
                                            }
                                        """
                                        )
                                    }
                                }

                                // Include shared JavaScript
                                WebResources.includeSharedJs(this)

                                // Include admin-specific JavaScript
                                script {
                                    unsafe {
                                        raw(
                                            """
                                            // Admin dashboard JavaScript
                                            document.addEventListener('DOMContentLoaded', function() {
                                                // Check if token exists
                                                const token = localStorage.getItem('admin_token');
                                                if (!token) {
                                                    // Redirect to login if no token
                                                    window.location.href = '/admin/login';
                                                    return;
                                                }
                                                
                                                // Verify token is valid by making a test request
                                                fetch('/admin/users', {
                                                    headers: {
                                                        'Authorization': 'Bearer ' + token
                                                    }
                                                })
                                                .then(response => {
                                                    if (!response.ok) {
                                                        // Token is invalid, redirect to login
                                                        console.error('Token validation failed on dashboard load');
                                                        localStorage.removeItem('admin_token');
                                                        window.location.href = '/admin/login?error=authentication_failed';
                                                    }
                                                })
                                                .catch(error => {
                                                    // Error occurred, redirect to login
                                                    console.error('Token validation error on dashboard load:', error);
                                                    localStorage.removeItem('admin_token');
                                                    window.location.href = '/admin/login?error=authentication_failed';
                                                });
                                                
                                                // Add click handlers for user management actions
                                                document.querySelectorAll('.user-action').forEach(button => {
                                                    button.addEventListener('click', function() {
                                                        const action = this.dataset.action;
                                                        const email = this.dataset.email;
                                                        const role = this.dataset.role;
                                                        
                                                        if (action === 'toggle-status') {
                                                            const isActive = this.dataset.active === 'true';
                                                            // Use new API endpoint if available, fall back to old endpoint
                                                            if (typeof updateUserStatus === 'function') {
                                                                updateUserStatus(email, !isActive);
                                                            } else {
                                                                updateUser(email, !isActive, null);
                                                            }
                                                        } else if (action === 'change-role') {
                                                            // Use new API endpoint if available, fall back to old endpoint
                                                            if (typeof updateUserRole === 'function') {
                                                                updateUserRole(email, role);
                                                            } else {
                                                                updateUser(email, null, role);
                                                            }
                                                        }
                                                    });
                                                });
                                                
                                                // Function to update user status using new API endpoint
                                                function updateUserStatus(userId, isActive) {
                                                    // Make API call with JWT token
                                                    fetch(`/admin/api/users/${'$'}{userId}/status`, {
                                                        method: 'PUT',
                                                        headers: {
                                                            'Content-Type': 'application/json',
                                                            'Authorization': 'Bearer ' + token
                                                        },
                                                        body: JSON.stringify({
                                                            isActive: isActive
                                                        })
                                                    })
                                                    .then(response => {
                                                        if (response.status === 401) {
                                                            // Unauthorized - token expired or invalid
                                                            localStorage.removeItem('admin_token');
                                                            window.location.href = '/admin/login?error=session_expired';
                                                            return null;
                                                        }
                                                        return response.json();
                                                    })
                                                    .then(data => {
                                                        if (data && data.status === true) {
                                                            // Success - reload page to show updated data
                                                            window.location.reload();
                                                        } else if (data) {
                                                            // Error with message
                                                            alert('Error: ' + data.message);
                                                        }
                                                    })
                                                    .catch(error => {
                                                        console.error('Update failed:', error);
                                                        alert('Failed to update user status. Please try again.');
                                                        // Fall back to old endpoint if new one fails
                                                        updateUser(userId, isActive, null);
                                                    });
                                                }
                                                
                                                // Function to update user role using new API endpoint
                                                function updateUserRole(userId, role) {
                                                    // Make API call with JWT token
                                                    fetch(`/admin/api/users/${'$'}{userId}/role`, {
                                                        method: 'PUT',
                                                        headers: {
                                                            'Content-Type': 'application/json',
                                                            'Authorization': 'Bearer ' + token
                                                        },
                                                        body: JSON.stringify({
                                                            role: role
                                                        })
                                                    })
                                                    .then(response => {
                                                        if (response.status === 401) {
                                                            // Unauthorized - token expired or invalid
                                                            localStorage.removeItem('admin_token');
                                                            window.location.href = '/admin/login?error=session_expired';
                                                            return null;
                                                        }
                                                        return response.json();
                                                    })
                                                    .then(data => {
                                                        if (data && data.status === true) {
                                                            // Success - reload page to show updated data
                                                            window.location.reload();
                                                        } else if (data) {
                                                            // Error with message
                                                            alert('Error: ' + data.message);
                                                        }
                                                    })
                                                    .catch(error => {
                                                        console.error('Update failed:', error);
                                                        alert('Failed to update user role. Please try again.');
                                                        // Fall back to old endpoint if new one fails
                                                        updateUser(userId, null, role);
                                                    });
                                                }
                                                
                                                // Original function to update user (for backward compatibility)
                                                function updateUser(email, isActive, role) {
                                                    const updateData = { email: email };
                                                    
                                                    if (isActive !== null) {
                                                        updateData.isActive = isActive;
                                                    }
                                                    
                                                    if (role !== null) {
                                                        updateData.role = role;
                                                    }
                                                    
                                                    // Make API call with JWT token
                                                    fetch('/admin/users', {
                                                        method: 'PATCH',
                                                        headers: {
                                                            'Content-Type': 'application/json',
                                                            'Authorization': 'Bearer ' + token
                                                        },
                                                        body: JSON.stringify(updateData)
                                                    })
                                                    .then(response => {
                                                        if (response.status === 401) {
                                                            // Unauthorized - token expired or invalid
                                                            localStorage.removeItem('admin_token');
                                                            window.location.href = '/admin/login?error=session_expired';
                                                            return null;
                                                        }
                                                        return response.json();
                                                    })
                                                    .then(data => {
                                                        if (data && data.status === true) {
                                                            // Success - reload page to show updated data
                                                            window.location.reload();
                                                        } else if (data) {
                                                            // Error with message
                                                            alert('Error: ' + data.message);
                                                        }
                                                    })
                                                    .catch(error => {
                                                        console.error('Update failed:', error);
                                                        alert('Failed to update user. Please try again.');
                                                    });
                                                }
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
                                                +"Admin Dashboard"
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


                                            // Home link
                                            span {
                                                classes = setOf("material-icons", "nav-icon")
                                                id = "home-link"
                                                attributes["data-url"] = "/"
                                                +"home"
                                            }
                                        }
                                    }

                                    // Main content
                                    div {
                                        classes = setOf("admin-card")
                                        h2 { +"User Management" }
                                        p { +"Total users: $totalCount" }

                                        // Users table
                                        table {
                                            classes = setOf("admin-table")
                                            thead {
                                                tr {
                                                    th { +"Email" }
                                                    th { +"Status" }
                                                    th { +"Role" }
                                                    th { +"Created At" }
                                                    th { +"Device" }
                                                    th { +"Actions" }
                                                }
                                            }
                                            tbody {
                                                users.forEach { user ->
                                                    tr {
                                                        td { +user.email }
                                                        td {
                                                            span {
                                                                classes = setOf(
                                                                    "status-badge",
                                                                    if (user.isActive) "status-active" else "status-inactive"
                                                                )
                                                                +(if (user.isActive) "Active" else "Inactive")
                                                            }
                                                        }
                                                        td {
                                                            span {
                                                                classes = setOf(
                                                                    "role-badge",
                                                                    "role-${
                                                                        user.role.toString()
                                                                            .lowercase()
                                                                    }"
                                                                )
                                                                +user.role.toString()
                                                            }
                                                        }
                                                        td { +user.createdAt }
                                                        td { +(user.deviceModel ?: "N/A") }
                                                        td {
                                                            // Toggle status button
                                                            button {
                                                                classes = setOf(
                                                                    "user-action",
                                                                    "action-button",
                                                                    if (user.isActive) "deactivate" else "activate"
                                                                )
                                                                attributes["data-action"] =
                                                                    "toggle-status"
                                                                attributes["data-email"] =
                                                                    user.email
                                                                attributes["data-active"] =
                                                                    user.isActive.toString()
                                                                +(if (user.isActive) "Deactivate" else "Activate")
                                                            }

                                                            // Only show role change buttons if not the current admin
                                                            if (user.email != email) {
                                                                // Change role buttons
                                                                if (user.role != UserRole.ADMIN) {
                                                                    button {
                                                                        classes = setOf(
                                                                            "user-action",
                                                                            "action-button",
                                                                            "promote"
                                                                        )
                                                                        attributes["data-action"] =
                                                                            "change-role"
                                                                        attributes["data-email"] =
                                                                            user.email
                                                                        attributes["data-role"] =
                                                                            "ADMIN"
                                                                        +"Make Admin"
                                                                    }
                                                                }

                                                                if (user.role != UserRole.MODERATOR) {
                                                                    button {
                                                                        classes = setOf(
                                                                            "user-action",
                                                                            "action-button",
                                                                            "promote"
                                                                        )
                                                                        attributes["data-action"] =
                                                                            "change-role"
                                                                        attributes["data-email"] =
                                                                            user.email
                                                                        attributes["data-role"] =
                                                                            "MODERATOR"
                                                                        +"Make Moderator"
                                                                    }
                                                                }

                                                                if (user.role != UserRole.USER) {
                                                                    button {
                                                                        classes = setOf(
                                                                            "user-action",
                                                                            "action-button",
                                                                            "demote"
                                                                        )
                                                                        attributes["data-action"] =
                                                                            "change-role"
                                                                        attributes["data-email"] =
                                                                            user.email
                                                                        attributes["data-role"] =
                                                                            "USER"
                                                                        +"Make User"
                                                                    }
                                                                }
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
                                            p { +"© ${java.time.Year.now().value} Androidplay API Admin Dashboard" }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is Result.Error -> {
                        call.respondError(
                            "Failed to retrieve users: ${result.message}",
                            Unit,
                            HttpStatusCode.InternalServerError
                        )
                    }
                }
            }
        }
    }

    // Catch-all route for any other admin paths
    authenticate("jwt-auth") {
        route("/admin/{...}") {
            get {
                // Verify admin access
                if (!verifyAdminAccess(call)) {
                    return@get
                }

                // If we get here, the user is authenticated and has admin role,
                // but the specific path doesn't exist, so redirect to the main admin dashboard
                call.respondRedirect("/admin")
            }
        }
    }

    // API Routes
    authenticate("jwt-auth") {
        // Original API endpoints (for backward compatibility)
        route("/admin/users") {
            /**
             * Get all users with optional filtering, sorting, and pagination
             * GET /admin/users
             * Query parameters:
             * - email: Filter by email (partial match)
             * - isActive: Filter by active status (true/false)
             * - role: Filter by role (ADMIN, MODERATOR, USER)
             * - deviceModel: Filter by device model (partial match)
             * - operatingSystem: Filter by operating system (partial match)
             * - registrationSource: Filter by registration source (partial match)
             * - sortBy: Field to sort by
             * - sortOrder: Sort order (1 for ascending, -1 for descending)
             * - page: Page number (1-based)
             * - pageSize: Number of items per page
             */
            get {
                // Verify admin access
                if (!verifyAdminAccess(call, isApiCall = true)) {
                    return@get
                }

                // Parse query parameters
                val queryParams = call.request.queryParameters

                // Build filter map
                val filter = mutableMapOf<String, Any>()

                queryParams["email"]?.let { filter["email"] = it }
                queryParams["isActive"]?.let { filter["isActive"] = it.toBoolean() }
                queryParams["role"]?.let { filter["role"] = it }
                queryParams["deviceModel"]?.let { filter["deviceModel"] = it }
                queryParams["operatingSystem"]?.let { filter["operatingSystem"] = it }
                queryParams["registrationSource"]?.let { filter["registrationSource"] = it }

                // Parse sorting parameters
                val sortBy = queryParams["sortBy"]
                val sortOrder = queryParams["sortOrder"]?.toIntOrNull()

                // Parse pagination parameters
                val page = queryParams["page"]?.toIntOrNull()
                val pageSize = queryParams["pageSize"]?.toIntOrNull()

                // Parse privacy level parameter
                val privacyLevelParam = queryParams["privacyLevel"]?.uppercase() ?: "LOW"
                val privacyLevel = try {
                    PrivacyLevel.valueOf(privacyLevelParam)
                } catch (_: IllegalArgumentException) {
                    PrivacyLevel.LOW // Default to LOW if invalid
                }

                // Get users
                val result = userRepository.getAllUsers(
                    filter = if (filter.isEmpty()) null else filter,
                    sortBy = sortBy,
                    sortOrder = sortOrder,
                    page = page,
                    pageSize = pageSize
                )

                when (result) {
                    is Result.Success -> {
                        val (users, totalCount) = result.data

                        // Apply privacy filter to users
                        val filteredUsers = UserPrivacyFilter.filterUsers(users, privacyLevel)

                        // Create response with pagination metadata
                        val response = UsersResponse(
                            users = filteredUsers,
                            totalCount = totalCount,
                            page = page ?: 1,
                            pageSize = pageSize ?: users.size,
                            totalPages = if (pageSize != null && pageSize > 0)
                                Math.ceil(totalCount.toDouble() / pageSize).toInt()
                            else 1,
                            privacyLevel = privacyLevel
                        )

                        call.respondSuccess(
                            "Users retrieved successfully",
                            response,
                            HttpStatusCode.OK
                        )
                    }

                    is Result.Error -> {
                        call.respondError(
                            "Failed to retrieve users: ${result.message}",
                            Unit,
                            HttpStatusCode.InternalServerError
                        )
                    }
                }
            }

            /**
             * Update user status or role
             * PATCH /admin/users
             * Request body: UserUpdateRequest
             */
            patch {
                // Verify admin access
                if (!verifyAdminAccess(call, isApiCall = true)) {
                    return@patch
                }

                // Get the admin user's email and user object for additional checks
                val principal = call.principal<JWTPrincipal>()
                val adminEmail = principal?.payload?.getClaim("email")?.asString()!!

                // Get the admin user object for role modification check
                val adminResult = userRepository.findUserByEmail(adminEmail)
                val adminUser = (adminResult as Result.Success).data!!

                // Parse request body
                val request = call.receive<UserUpdateRequest>()

                // Get the user to update
                val userResult = userRepository.findUserByEmail(request.email)
                if (userResult is Result.Error) {
                    call.respondError(
                        "Failed to find user: ${userResult.message}",
                        Unit,
                        HttpStatusCode.InternalServerError
                    )
                    return@patch
                }

                val user = (userResult as Result.Success).data
                if (user == null) {
                    call.respondError(
                        "User not found",
                        Unit,
                        HttpStatusCode.NotFound
                    )
                    return@patch
                }

                // Prevent admin from modifying their own role (security measure)
                if (user.email == adminUser.email && request.role != null && request.role != user.role) {
                    call.respondError(
                        "Admins cannot modify their own role",
                        Unit,
                        HttpStatusCode.Forbidden
                    )
                    return@patch
                }

                // Update user
                val updatedUser = user.copy(
                    isActive = request.isActive ?: user.isActive,
                    role = request.role ?: user.role
                )

                // Save updated user
                val updateResult = userRepository.updateUser(updatedUser)
                when (updateResult) {
                    is Result.Success -> {
                        call.respondSuccess(
                            "User updated successfully",
                            updatedUser,
                            HttpStatusCode.OK
                        )
                    }

                    is Result.Error -> {
                        call.respondError(
                            "Failed to update user: ${updateResult.message}",
                            Unit,
                            HttpStatusCode.InternalServerError
                        )
                    }
                }
            }
        }

        // New API endpoints as per requirements
        route("/admin/api/users") {
            /**
             * Get all users with optional filtering, sorting, and pagination
             * GET /admin/api/users
             * Query parameters:
             * - email: Filter by email (partial match)
             * - isActive: Filter by active status (true/false)
             * - role: Filter by role (ADMIN, MODERATOR, USER)
             * - deviceModel: Filter by device model (partial match)
             * - operatingSystem: Filter by operating system (partial match)
             * - registrationSource: Filter by registration source (partial match)
             * - sortBy: Field to sort by
             * - sortOrder: Sort order (1 for ascending, -1 for descending)
             * - page: Page number (1-based)
             * - pageSize: Number of items per page
             */
            get {
                // Verify admin access
                if (!verifyAdminAccess(call, isApiCall = true)) {
                    return@get
                }

                // Parse query parameters
                val queryParams = call.request.queryParameters

                // Build filter map
                val filter = mutableMapOf<String, Any>()

                queryParams["email"]?.let { filter["email"] = it }
                queryParams["isActive"]?.let { filter["isActive"] = it.toBoolean() }
                queryParams["role"]?.let { filter["role"] = it }
                queryParams["deviceModel"]?.let { filter["deviceModel"] = it }
                queryParams["operatingSystem"]?.let { filter["operatingSystem"] = it }
                queryParams["registrationSource"]?.let { filter["registrationSource"] = it }

                // Parse sorting parameters
                val sortBy = queryParams["sortBy"]
                val sortOrder = queryParams["sortOrder"]?.toIntOrNull()

                // Parse pagination parameters
                val page = queryParams["page"]?.toIntOrNull()
                val pageSize = queryParams["pageSize"]?.toIntOrNull()

                // Parse privacy level parameter
                val privacyLevelParam = queryParams["privacyLevel"]?.uppercase() ?: "LOW"
                val privacyLevel = try {
                    PrivacyLevel.valueOf(privacyLevelParam)
                } catch (_: IllegalArgumentException) {
                    PrivacyLevel.LOW // Default to LOW if invalid
                }

                // Get users
                val result = userRepository.getAllUsers(
                    filter = filter.ifEmpty { null },
                    sortBy = sortBy,
                    sortOrder = sortOrder,
                    page = page,
                    pageSize = pageSize
                )

                when (result) {
                    is Result.Success -> {
                        val (users, totalCount) = result.data

                        // Apply privacy filter to users
                        val filteredUsers = UserPrivacyFilter.filterUsers(users, privacyLevel)

                        // Create response with pagination metadata
                        val response = UsersResponse(
                            users = filteredUsers,
                            totalCount = totalCount,
                            page = page ?: 1,
                            pageSize = pageSize ?: users.size,
                            totalPages = if (pageSize != null && pageSize > 0)
                                Math.ceil(totalCount.toDouble() / pageSize).toInt()
                            else 1,
                            privacyLevel = privacyLevel
                        )

                        call.respondSuccess(
                            "Users retrieved successfully",
                            response,
                            HttpStatusCode.OK
                        )
                    }

                    is Result.Error -> {
                        call.respondError(
                            "Failed to retrieve users: ${result.message}",
                            Unit,
                            HttpStatusCode.InternalServerError
                        )
                    }
                }
            }

            /**
             * Update user status
             * PUT /admin/api/users/{userId}/status
             * Request body: UserStatusUpdateRequest
             */
            route("/{userId}/status") {
                put {
                    // Verify admin access
                    if (!verifyAdminAccess(call, isApiCall = true)) {
                        return@put
                    }

                    // Get the user ID from the path parameter
                    val userId = call.parameters["userId"] ?: run {
                        call.respondError(
                            "User ID is required",
                            Unit,
                            HttpStatusCode.BadRequest
                        )
                        return@put
                    }

                    // Parse request body
                    val request = call.receive<UserStatusUpdateRequest>()

                    // Get the user to update
                    val userResult = userRepository.findUserByEmail(userId)
                    if (userResult is Result.Error) {
                        call.respondError(
                            "Failed to find user: ${userResult.message}",
                            Unit,
                            HttpStatusCode.InternalServerError
                        )
                        return@put
                    }

                    val user = (userResult as Result.Success).data
                    if (user == null) {
                        call.respondError(
                            "User not found",
                            Unit,
                            HttpStatusCode.NotFound
                        )
                        return@put
                    }

                    // Update user status
                    val updatedUser = user.copy(
                        isActive = request.isActive
                    )

                    // Save updated user
                    val updateResult = userRepository.updateUser(updatedUser)
                    when (updateResult) {
                        is Result.Success -> {
                            // Apply privacy filter to the updated user
                            val filteredUser =
                                UserPrivacyFilter.filterUser(updatedUser, PrivacyLevel.LOW)

                            call.respondSuccess(
                                "User status updated successfully",
                                filteredUser,
                                HttpStatusCode.OK
                            )
                        }

                        is Result.Error -> {
                            call.respondError(
                                "Failed to update user status: ${updateResult.message}",
                                Unit,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                }
            }

            /**
             * Update user role
             * PUT /admin/api/users/{userId}/role
             * Request body: UserRoleUpdateRequest
             */
            route("/{userId}/role") {
                put {
                    // Verify admin access
                    if (!verifyAdminAccess(call, isApiCall = true)) {
                        return@put
                    }

                    // Get the admin user's email and user object for additional checks
                    val principal = call.principal<JWTPrincipal>()
                    val adminEmail = principal?.payload?.getClaim("email")?.asString()!!

                    // Get the admin user object for role modification check
                    val adminResult = userRepository.findUserByEmail(adminEmail)
                    val adminUser = (adminResult as Result.Success).data!!

                    // Get the user ID from the path parameter
                    val userId = call.parameters["userId"] ?: run {
                        call.respondError(
                            "User ID is required",
                            Unit,
                            HttpStatusCode.BadRequest
                        )
                        return@put
                    }

                    // Parse request body
                    val request = call.receive<UserRoleUpdateRequest>()

                    // Get the user to update
                    val userResult = userRepository.findUserByEmail(userId)
                    if (userResult is Result.Error) {
                        call.respondError(
                            "Failed to find user: ${userResult.message}",
                            Unit,
                            HttpStatusCode.InternalServerError
                        )
                        return@put
                    }

                    val user = (userResult as Result.Success).data
                    if (user == null) {
                        call.respondError(
                            "User not found",
                            Unit,
                            HttpStatusCode.NotFound
                        )
                        return@put
                    }

                    // Prevent admin from modifying their own role (security measure)
                    if (user.email == adminUser.email) {
                        call.respondError(
                            "Admins cannot modify their own role",
                            Unit,
                            HttpStatusCode.Forbidden
                        )
                        return@put
                    }

                    // Update user role
                    val updatedUser = user.copy(
                        role = request.role
                    )

                    // Save updated user
                    val updateResult = userRepository.updateUser(updatedUser)
                    when (updateResult) {
                        is Result.Success -> {
                            // Apply privacy filter to the updated user
                            val filteredUser =
                                UserPrivacyFilter.filterUser(updatedUser, PrivacyLevel.LOW)

                            call.respondSuccess(
                                "User role updated successfully",
                                filteredUser,
                                HttpStatusCode.OK
                            )
                        }

                        is Result.Error -> {
                            call.respondError(
                                "Failed to update user role: ${updateResult.message}",
                                Unit,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Request body for updating a user
 */
@Serializable
data class UserUpdateRequest(
    val email: String,
    val isActive: Boolean? = null,
    val role: UserRole? = null
)

/**
 * Request body for updating a user's status
 */
@Serializable
data class UserStatusUpdateRequest(
    val isActive: Boolean
)

/**
 * Request body for updating a user's role
 */
@Serializable
data class UserRoleUpdateRequest(
    val role: UserRole
)

/**
 * Response body for users list with pagination
 */
@Serializable
data class UsersResponse(
    val users: List<FilteredUser>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val privacyLevel: PrivacyLevel
)