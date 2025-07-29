/**
 * Authentication utilities for Weatherify API
 * Handles JWT token storage and authorization
 */

/**
 * Store JWT token in localStorage
 * @param {string} token - The JWT token to store
 */
function storeToken(token) {
    if (!token) {
        console.error('No token provided to store');
        return;
    }
    
    try {
        localStorage.setItem('jwt_token', token);
        console.log('Token stored successfully');
    } catch (error) {
        console.error('Failed to store token:', error);
    }
}

/**
 * Store user information in localStorage
 * @param {Object} userInfo - The user information to store
 */
function storeUserInfo(userInfo) {
    if (!userInfo) {
        console.error('No user info provided to store');
        return;
    }
    
    try {
        localStorage.setItem('user_info', JSON.stringify(userInfo));
        console.log('User info stored successfully');
    } catch (error) {
        console.error('Failed to store user info:', error);
    }
}

/**
 * Get user information from localStorage
 * @returns {Object|null} The user information or null if not found
 */
function getUserInfo() {
    try {
        const userInfo = localStorage.getItem('user_info');
        return userInfo ? JSON.parse(userInfo) : null;
    } catch (error) {
        console.error('Failed to retrieve user info:', error);
        return null;
    }
}

/**
 * Get JWT token from localStorage
 * @returns {string|null} The JWT token or null if not found
 */
function getToken() {
    try {
        return localStorage.getItem('jwt_token');
    } catch (error) {
        console.error('Failed to retrieve token:', error);
        return null;
    }
}

/**
 * Remove JWT token and user info from localStorage
 */
function removeToken() {
    try {
        localStorage.removeItem('jwt_token');
        localStorage.removeItem('user_info');
        console.log('Token and user info removed successfully');
    } catch (error) {
        console.error('Failed to remove token and user info:', error);
    }
}

/**
 * Check if user is authenticated (has a token)
 * @returns {boolean} True if authenticated, false otherwise
 */
function isAuthenticated() {
    return !!getToken();
}

/**
 * Check if the current user is an admin
 * @returns {boolean} True if the user is an admin, false otherwise
 */
function isAdmin() {
    const userInfo = getUserInfo();
    return userInfo && userInfo.role === 'ADMIN';
}

/**
 * Check if the current user is a moderator
 * @returns {boolean} True if the user is a moderator, false otherwise
 */
function isModerator() {
    const userInfo = getUserInfo();
    return userInfo && (userInfo.role === 'MODERATOR' || userInfo.role === 'ADMIN');
}

/**
 * Redirect to the appropriate dashboard based on user role
 */
function redirectToDashboard() {
    if (isAdmin()) {
        window.location.href = '/admin';
    } else {
        window.location.href = '/weather.html';
    }
}

/**
 * Add Authorization header with JWT token to fetch options
 * @param {Object} options - The fetch options object
 * @returns {Object} The updated fetch options with Authorization header
 */
function addAuthHeader(options = {}) {
    const token = getToken();
    if (!token) {
        console.warn('No token available for request');
        return options;
    }
    
    const headers = options.headers || {};
    return {
        ...options,
        headers: {
            ...headers,
            'Authorization': `Bearer ${token}`
        }
    };
}

/**
 * Make an authenticated fetch request
 * @param {string} url - The URL to fetch
 * @param {Object} options - The fetch options
 * @returns {Promise} The fetch promise
 */
function authFetch(url, options = {}) {
    return fetch(url, addAuthHeader(options));
}

/**
 * Handle login response and store token and user info
 * @param {Object} response - The login response object
 * @returns {boolean} True if login was successful, false otherwise
 */
function handleLoginResponse(response) {
    if (response && response.status === true && response.data) {
        const { token, email, role, isActive } = response.data;
        
        if (!token) {
            console.error('No token in login response');
            return false;
        }
        
        // Store token
        storeToken(token);
        
        // Store user info
        storeUserInfo({
            email: email || '',
            role: role || 'USER',
            isActive: isActive !== false
        });
        
        return true;
    }
    return false;
}

/**
 * Login user with email and password
 * @param {string} email - The user's email
 * @param {string} password - The user's password
 * @returns {Promise} Promise that resolves to login success (boolean)
 */
function login(email, password) {
    return fetch('/login', {
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
        const success = handleLoginResponse(data);
        if (success) {
            // Redirect to appropriate dashboard based on role
            setTimeout(() => {
                redirectToDashboard();
            }, 1000); // Short delay to show success message
        }
        return success;
    })
    .catch(error => {
        console.error('Login error:', error);
        return false;
    });
}

/**
 * Logout user by removing token and redirecting to login page
 */
function logout() {
    removeToken();
    window.location.href = '/login.html';
}

// Automatically check authentication on protected pages
document.addEventListener('DOMContentLoaded', function() {
    const currentPath = window.location.pathname;
    
    // Define public pages that don't require authentication
    const publicPages = [
        '/',
        '',
        '/login.html',
        '/register.html',
        '/not-found',
        '/favicon.ico',
        '/weather.html',
        '/air-pollution',
        '/feedback',
        '/wfy/terms-and-conditions',
        '/wfy/privacy-policy',
        '/admin/login'
    ];
    
    // Define admin-only pages that require ADMIN role
    const adminOnlyPages = [
        '/admin',
        '/admin/dashboard'
    ];
    
    // Check if current path is an admin page
    const isAdminPage = adminOnlyPages.some(page => 
        currentPath === page || currentPath.startsWith(`${page}/`)
    );
    
    // Check if current path is a public page
    const isPublicPage = publicPages.some(page => 
        currentPath === page || (page !== '/' && page !== '' && currentPath.startsWith(`${page}/`))
    );
    
    // Function to detect and prevent redirect loops with improved error handling
    function safeRedirect(url) {
        // Get redirect history from session storage
        const redirectHistory = JSON.parse(sessionStorage.getItem('redirectHistory') || '[]');
        const currentUrl = window.location.pathname + window.location.search;
        
        // Check if we're in a potential redirect loop (same URL in history multiple times)
        const redirectCount = redirectHistory.filter(item => item === url).length;
        
        if (redirectCount >= 2) {
            console.error('Redirect loop detected! Breaking the cycle.');
            // Clear the redirect history
            sessionStorage.removeItem('redirectHistory');
            
            // Extract error type from URL if present
            let errorType = 'unknown';
            try {
                const urlObj = new URL(url, window.location.origin);
                errorType = urlObj.searchParams.get('error') || 'unknown';
            } catch (e) {
                console.error('Failed to parse URL:', e);
            }
            
            // Show a more specific error message based on the error type
            let errorMessage;
            switch (errorType) {
                case 'auth_required':
                    errorMessage = 'Authentication required: Please log in to access this page.';
                    break;
                case 'access_denied':
                    errorMessage = 'Access denied: You do not have permission to access this page. Admin privileges are required.';
                    break;
                case 'admin_required':
                    errorMessage = 'Admin access required: This page is only accessible to administrators.';
                    break;
                case 'account_inactive':
                    errorMessage = 'Account inactive: Your account is not active. Please contact support.';
                    break;
                case 'session_expired':
                    errorMessage = 'Session expired: Your session has expired. Please log in again.';
                    break;
                default:
                    errorMessage = 'Authentication error: Redirect loop detected. Please try clearing your cookies or contact support.';
            }
            
            // Display error message
            const errorDiv = document.createElement('div');
            errorDiv.className = 'auth-error-message';
            errorDiv.innerHTML = `
                <div class="auth-error-container">
                    <h3>Authentication Error</h3>
                    <p>${errorMessage}</p>
                    <button onclick="window.location.href='/admin/login'">Go to Login</button>
                </div>
            `;
            
            // Add some basic styling
            const style = document.createElement('style');
            style.textContent = `
                .auth-error-message {
                    position: fixed;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                    background-color: rgba(0, 0, 0, 0.7);
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    z-index: 9999;
                }
                .auth-error-container {
                    background-color: white;
                    padding: 20px;
                    border-radius: 8px;
                    max-width: 400px;
                    text-align: center;
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
                }
                .auth-error-container h3 {
                    color: #e53e3e;
                    margin-top: 0;
                }
                .auth-error-container button {
                    background-color: #3182ce;
                    color: white;
                    border: none;
                    padding: 8px 16px;
                    border-radius: 4px;
                    cursor: pointer;
                    margin-top: 10px;
                }
                .auth-error-container button:hover {
                    background-color: #2c5282;
                }
            `;
            
            document.head.appendChild(style);
            document.body.appendChild(errorDiv);
            
            return false;
        }
        
        // Add current URL to history before redirecting
        redirectHistory.push(url);
        // Keep only the last 5 redirects to prevent the history from growing too large
        if (redirectHistory.length > 5) {
            redirectHistory.shift();
        }
        sessionStorage.setItem('redirectHistory', JSON.stringify(redirectHistory));
        
        // Store the original URL the user was trying to access
        if (!currentUrl.includes('/login') && !currentUrl.includes('/admin/login')) {
            sessionStorage.setItem('intendedDestination', currentUrl);
        }
        
        // Perform the redirect
        window.location.href = url;
        return true;
    }
    
    // Skip authentication checks for login page to prevent redirect loops
    if (currentPath === '/admin/login') {
        // If already authenticated as admin on login page, redirect to dashboard
        if (isAuthenticated() && isAdmin()) {
            console.log('Admin user already authenticated, redirecting to dashboard');
            safeRedirect('/admin/dashboard');
            return;
        }
        // Otherwise, allow access to login page without further checks
        return;
    }
    
    // Handle admin pages - require authentication and ADMIN role
    if (isAdminPage) {
        if (!isAuthenticated()) {
            console.warn('User not authenticated, redirecting to login');
            safeRedirect('/admin/login?error=auth_required');
            return;
        }
        
        if (!isAdmin()) {
            console.warn('Non-admin user attempted to access admin page');
            safeRedirect('/admin/login?error=access_denied');
            return;
        }
    } 
    // Handle protected pages - require authentication
    else if (!isPublicPage) {
        if (!isAuthenticated()) {
            console.warn('User not authenticated, redirecting to login');
            safeRedirect('/admin/login?error=auth_required');
        }
    }
    
    // Check if we should redirect to intended destination after login
    if (isAuthenticated() && (currentPath === '/admin/login' || currentPath === '/login.html' || currentPath === '/login')) {
        const intendedDestination = sessionStorage.getItem('intendedDestination');
        if (intendedDestination) {
            console.log('Redirecting to intended destination:', intendedDestination);
            sessionStorage.removeItem('intendedDestination');
            safeRedirect(intendedDestination);
        } else if (isAdmin()) {
            // Default redirect for admin users
            safeRedirect('/admin/dashboard');
        } else {
            // Default redirect for regular users
            safeRedirect('/weather.html');
        }
    }
});