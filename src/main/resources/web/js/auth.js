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
 * Remove JWT token from localStorage
 */
function removeToken() {
    try {
        localStorage.removeItem('jwt_token');
        console.log('Token removed successfully');
    } catch (error) {
        console.error('Failed to remove token:', error);
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
 * Handle login response and store token
 * @param {Object} response - The login response object
 * @returns {boolean} True if login was successful, false otherwise
 */
function handleLoginResponse(response) {
    if (response && response.status === true && response.data && response.data.token) {
        storeToken(response.data.token);
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
            // Redirect to weather dashboard after successful login
            window.location.href = '/weather.html';
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
    // Check if this is a protected page (not login or register)
    const isLoginPage = window.location.pathname.includes('login');
    const isRegisterPage = window.location.pathname.includes('register');
    
    if (!isLoginPage && !isRegisterPage) {
        // This is a protected page, check authentication
        if (!isAuthenticated()) {
            // Not authenticated, redirect to login
            console.warn('User not authenticated, redirecting to login');
            window.location.href = '/login.html';
        }
    }
});