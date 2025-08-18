/**
 * Minimal authentication utilities for admin frontend.
 * Handles JWT token storage, user info, and navigation helpers.
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
 * Navigate to an admin page with a valid token
 * @param {string} url - The admin page URL to navigate to
 * @returns {Promise} Promise that resolves when navigation is complete
 */
function navigateToAdminPage(url) {
    const token = getToken();
    if (!token) {
        window.location.href = '/admin/login?error=auth_required';
        return;
    }
    window.location.href = url;
}

/**
 * Logout user by removing token, invalidating session on backend, and redirecting to login page
 */
function logout() {
    // POST to common /logout to clear server-side cookie/session for all user types
    fetch('/logout', {
        method: 'POST',
        credentials: 'include'
    })
    .then(() => {
        removeToken();
        // Optionally clear sessionStorage if used
        if (typeof sessionStorage !== "undefined") {
            sessionStorage.clear();
        }
        const isAdminPortal = window.location && window.location.pathname && window.location.pathname.startsWith('/admin');
        window.location.href = isAdminPortal ? '/admin/login' : '/login';
    })
    .catch(() => {
        // Even if request fails, clear local/session storage and redirect appropriately
        removeToken();
        if (typeof sessionStorage !== "undefined") {
            sessionStorage.clear();
        }
        const isAdminPortal = window.location && window.location.pathname && window.location.pathname.startsWith('/admin');
        window.location.href = isAdminPortal ? '/admin/login' : '/login';
    });
}

/**
 * Parse a JWT token to extract its payload
 * @param {string} token - The JWT token to parse
 * @returns {Object|null} The decoded payload or null if invalid
 */
function parseJwt(token) {
    if (!token) return null;
    
    try {
        // Split the token and get the payload part (second part)
        const base64Url = token.split('.')[1];
        if (!base64Url) return null;
        
        // Replace characters for base64 decoding
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        
        // Decode the payload
        const jsonPayload = decodeURIComponent(
            atob(base64)
                .split('')
                .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
                .join('')
        );
        
        return JSON.parse(jsonPayload);
    } catch (error) {
        console.error('Failed to parse JWT token:', error);
        return null;
    }
}

// Expose functions globally if needed
window.storeToken = storeToken;
window.storeUserInfo = storeUserInfo;
window.getToken = getToken;
window.getUserInfo = getUserInfo;
window.removeToken = removeToken;
window.isAuthenticated = isAuthenticated;
window.isAdmin = isAdmin;
window.navigateToAdminPage = navigateToAdminPage;
window.logout = logout;
window.parseJwt = parseJwt;

// Fallback logout button binding to ensure logout works even if admin.js init doesn't run
// or if localStorage token is missing but cookie auth is present.
document.addEventListener('DOMContentLoaded', function() {
    try {
        const btn = document.getElementById('logout-button');
        if (btn && !btn.dataset.logoutBound) {
            btn.addEventListener('click', function(e) {
                e.preventDefault();
                if (typeof window.logout === 'function') {
                    window.logout();
                }
            });
            btn.dataset.logoutBound = 'true';
        }
    } catch (e) {
        console.error('Failed to bind logout button:', e);
    }
});

// Optionally clear localStorage JWT if redirected to login due to session expiration/auth required
(function() {
    const params = new URLSearchParams(window.location.search);
    if (
        window.location.pathname === '/admin/login' &&
        (params.get('error') === 'session_expired' || params.get('error') === 'auth_required')
    ) {
        removeToken();
        if (typeof sessionStorage !== "undefined") {
            sessionStorage.clear();
        }
    }
})();


// --- Added for admin dashboard authenticated fetch helpers ---
/**
 * Get a valid token. Placeholder for future refresh logic.
 * @returns {Promise<string|null>} token or null
 */
function getValidToken() {
    try {
        const token = getToken();
        return Promise.resolve(token || null);
    } catch (e) {
        return Promise.resolve(null);
    }
}

/**
 * Fetch with Authorization when token exists and include cookies for fallback auth.
 * @param {string} url
 * @param {RequestInit} options
 */
function authFetch(url, options) {
    const opts = options ? { ...options } : {};
    const headers = new Headers(opts.headers || {});
    const token = getToken();
    if (token) {
        headers.set('Authorization', 'Bearer ' + token);
    }
    opts.headers = headers;
    // Include cookies for routes using cookie-based auth
    if (!opts.credentials) opts.credentials = 'include';
    return fetch(url, opts);
}

// Expose helpers
window.getValidToken = getValidToken;
window.authFetch = authFetch;
