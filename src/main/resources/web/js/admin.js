/**
 * Admin dashboard functionality for Androidplay Weather API
 * Handles user management (view, update roles, activate/deactivate)
 */

// Current page for pagination
let currentPage = 1;
// Page size for pagination
let pageSize = 10;
// Total number of pages
let totalPages = 1;
// Total number of users
let totalUsers = 0;

// UserRoute API wrapper to call backend UserRoute endpoints
// This provides a clear contract for admin.js to use backend routes without hardcoding URLs inline
window.UserRoute = window.UserRoute || {
    listUsers(page, pageSize) {
        const url = `/admin/users?page=${encodeURIComponent(page)}&pageSize=${encodeURIComponent(pageSize)}`;
        return fetch(url, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json'
            }
        })
        .then(response => {
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to access this resource');
                }
                throw new Error('Failed to load users');
            }
            return response.json();
        })
        .then(payload => {
            if (!payload || payload.status !== true || !payload.data) {
                throw new Error(payload && payload.message ? payload.message : 'Invalid response while loading users');
            }
            // FIX: Do not force !!u.isActive or !!u.isPremium, use the value as returned (should be boolean from backend)
            const mappedUsers = Array.isArray(payload.data.users) ? payload.data.users.map(u => ({
                email: u.email,
                role: u.role,
                isActive: u.isActive,
                isPremium: u.isPremium,
                createdAt: u.createdAt
            })) : [];
            return {
                users: mappedUsers,
                pagination: payload.data.pagination || { page, pageSize, totalPages: 1, totalCount: mappedUsers.length }
            };
        });
    },
    updateRole(email, role) {
        const url = `/admin/users/${encodeURIComponent(email)}/role`;
        return fetch(url, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({ role })
        });
    },
    updateStatus(email, isActive) {
        const url = `/admin/users/${encodeURIComponent(email)}/status`;
        return fetch(url, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({ isActive: !!isActive })
        });
    }
};

/**
 * Initialize the admin dashboard
 */
function initializeAdmin() {
    try {
        // Bind logout
        const logoutBtn = document.getElementById('logout-button');
        if (logoutBtn && !logoutBtn.dataset.bound) {
            logoutBtn.addEventListener('click', function(e){ e.preventDefault(); if (typeof logout === 'function') logout(); });
            logoutBtn.dataset.bound = 'true';
        }


        // Guarded initial load for IAM users: run once if IAM panel exists and table is empty
        try {
            const iamPanel = document.getElementById('iam');
            const tbody = document.getElementById('users-table-body');
            if (iamPanel && tbody && !window.__iamLoadedOnce) {
                const hasRows = Array.isArray(tbody.rows) ? tbody.rows.length > 0 : tbody.children.length > 0;
                if (!hasRows && typeof loadUsers === 'function') {
                    window.__iamLoadedOnce = true;
                    loadUsers(1, 10);
                }
            }
        } catch (e) {
            console.warn('IAM initial load guard failed:', e);
        }

        // Do not redirect here; rely on server-side auth via cookie or Authorization header.
        // The IAM tab activation script will trigger loading users when needed.
    } catch (e) {
        console.error('Error initializing admin dashboard:', e);
    }
}

/**
 * Load users from the API with pagination
 * @param {number} page - The page number to load
 * @param {number} pageSize - The number of users per page
 */
function loadUsers(page, pageSize) {
    // Show inline loader only (avoid layout shift and flicker)
    try {
        const loader = document.getElementById('iam-loader');
        if (loader) loader.style.display = 'block';
    } catch (e) { /* ignore */ }

    // Make API request to get users via UserRoute
    window.UserRoute.listUsers(page, pageSize)
        .then(({ users, pagination }) => {
            // Hide loader
            try {
                const loader = document.getElementById('iam-loader');
                if (loader) loader.style.display = 'none';
            } catch (e) {}

            // Log users to console
            console.log('Fetched users:', users);

            // Clear any previous messages
            clearMessages();

            // Update pagination variables
            currentPage = pagination.page;
            pageSize = pagination.pageSize;
            totalPages = pagination.totalPages;
            totalUsers = pagination.totalCount;

            // Render users table
            renderUsersTable(users);

            // Render pagination
            renderPagination();
        })
        .catch(error => {
            console.error('Error loading users:', error);
            try {
                const loader = document.getElementById('iam-loader');
                if (loader) loader.style.display = 'none';
            } catch (e) {}
            showErrorMessage(error.message || 'An error occurred while loading users');

            // If error is due to authentication, redirect to login
            if (error.message.includes('permission') || error.message.includes('authentication')) {
                setTimeout(() => {
                    window.location.href = '/admin/login?error=auth_required';
                }, 2000);
            }
        });
}

/**
 * Render the users table with the provided users
 * @param {Array} users - The array of users to display
 */
function renderUsersTable(users) {
    const tableBody = document.getElementById('users-table-body');
    const fragment = document.createDocumentFragment();

    if (users.length === 0) {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 4;
        cell.textContent = 'No users found';
        cell.style.textAlign = 'center';
        row.appendChild(cell);
        fragment.appendChild(row);
        tableBody.replaceChildren(fragment);
        return;
    }

    users.forEach(user => {
        const row = document.createElement('tr');

        // Email cell with badges
        const emailCell = document.createElement('td');
        const emailText = document.createElement('span');
        emailText.textContent = user.email;
        emailCell.appendChild(emailText);

        if (user.role === 'ADMIN') {
            const adminBadge = document.createElement('span');
            adminBadge.className = 'badge badge-admin';
            adminBadge.textContent = 'ADMIN';
            emailCell.appendChild(adminBadge);
        }
        if (user.isPremium === true) {
            const premiumBadge = document.createElement('span');
            premiumBadge.className = 'badge badge-premium';
            premiumBadge.textContent = 'PREMIUM';
            emailCell.appendChild(premiumBadge);
        }
        row.appendChild(emailCell);

        // Created at cell
        const createdAtCell = document.createElement('td');
        createdAtCell.textContent = formatDate(user.createdAt);
        row.appendChild(createdAtCell);

        // Role cell
        const roleCell = document.createElement('td');
        const roleSelect = document.createElement('select');
        roleSelect.className = 'role-select';
        roleSelect.dataset.email = user.email;

        ['USER', 'MODERATOR', 'ADMIN'].forEach(role => {
            const option = document.createElement('option');
            option.value = role;
            option.textContent = role;
            option.selected = user.role === role;
            roleSelect.appendChild(option);
        });
        roleSelect.dataset.prevValue = user.role || 'USER';

        roleSelect.addEventListener('change', function() {
            updateUserRole(user.email, this.value, this);
        });

        roleCell.appendChild(roleSelect);
        row.appendChild(roleCell);

        // Status cell
        const statusCell = document.createElement('td');
        const statusToggle = document.createElement('label');
        statusToggle.className = 'status-toggle';

        const statusInput = document.createElement('input');
        statusInput.type = 'checkbox';
        // Fix: ensure checked is set correctly for active users
        statusInput.checked = !!user.isActive;
        statusInput.dataset.email = user.email;
        statusInput.dataset.prevChecked = String(!!user.isActive);

        statusInput.addEventListener('change', function() {
            updateUserStatus(user.email, this.checked, this);
        });

        const statusSlider = document.createElement('span');
        statusSlider.className = 'status-slider';

        statusToggle.appendChild(statusInput);
        statusToggle.appendChild(statusSlider);
        statusCell.appendChild(statusToggle);
        row.appendChild(statusCell);

        fragment.appendChild(row);
    });
    tableBody.replaceChildren(fragment);
}

/**
 * Render pagination controls
 */
function renderPagination() {
    const paginationContainer = document.getElementById('pagination');
    paginationContainer.innerHTML = '';
    
    if (totalPages <= 1) {
        // If only one page, don't show pagination
        return;
    }
    
    // Previous button
    const prevButton = document.createElement('button');
    prevButton.className = `pagination-button ${currentPage === 1 ? 'disabled' : ''}`;
    prevButton.textContent = 'Previous';
    prevButton.disabled = currentPage === 1;
    prevButton.addEventListener('click', () => {
        if (currentPage > 1) {
            loadUsers(currentPage - 1, pageSize);
        }
    });
    paginationContainer.appendChild(prevButton);
    
    // Page buttons
    const maxButtons = 5; // Maximum number of page buttons to show
    const startPage = Math.max(1, currentPage - Math.floor(maxButtons / 2));
    const endPage = Math.min(totalPages, startPage + maxButtons - 1);
    
    for (let i = startPage; i <= endPage; i++) {
        const pageButton = document.createElement('button');
        pageButton.className = `pagination-button ${i === currentPage ? 'active' : ''}`;
        pageButton.textContent = i;
        pageButton.addEventListener('click', () => {
            if (i !== currentPage) {
                loadUsers(i, pageSize);
            }
        });
        paginationContainer.appendChild(pageButton);
    }
    
    // Next button
    const nextButton = document.createElement('button');
    nextButton.className = `pagination-button ${currentPage === totalPages ? 'disabled' : ''}`;
    nextButton.textContent = 'Next';
    nextButton.disabled = currentPage === totalPages;
    nextButton.addEventListener('click', () => {
        if (currentPage < totalPages) {
            loadUsers(currentPage + 1, pageSize);
        }
    });
    paginationContainer.appendChild(nextButton);
}

/**
 * Update a user's role
 * @param {string} email - The user's email
 * @param {string} role - The new role
 */
function updateUserRole(email, role, selectEl) {
    const prev = selectEl ? (selectEl.dataset.prevValue || 'USER') : null;
    if (selectEl) selectEl.disabled = true;
    // Make API request to update role
    window.UserRoute.updateRole(email, role)
    .then(response => {
        if (!response.ok) {
            if (response.status === 403) {
                throw new Error('You do not have permission to update user roles');
            } else {
                throw new Error('Failed to update user role');
            }
        }
        return response.json();
    })
    .then(data => {
        if (data.status === true) {
            if (selectEl) selectEl.dataset.prevValue = role;
            showToast('success', `Role updated successfully for ${email}`);
        } else {
            if (selectEl && prev) selectEl.value = prev;
            showErrorMessage(data.message || 'Failed to update user role');
        }
    })
    .catch(error => {
        console.error('Error updating user role:', error);
        if (selectEl && prev) selectEl.value = prev;
        showErrorMessage(error.message || 'An error occurred while updating user role');
    })
    .finally(() => {
        if (selectEl) selectEl.disabled = false;
    });
}

/**
 * Update a user's active status
 * @param {string} email - The user's email
 * @param {boolean} isActive - The new active status
 */
function updateUserStatus(email, isActive, checkboxEl) {
    const prev = checkboxEl ? (checkboxEl.dataset.prevChecked === 'true') : null;
    // If nothing changed, do nothing
    if (prev !== null && prev === isActive) {
        return;
    }
    if (checkboxEl) checkboxEl.disabled = true;
    const statusText = isActive ? 'activating' : 'deactivating';
    // Make API request to update status
    window.UserRoute.updateStatus(email, isActive)
    .then(response => {
        if (!response.ok) {
            if (response.status === 403) {
                throw new Error('You do not have permission to update user status');
            } else {
                throw new Error(`Failed to ${statusText} user`);
            }
        }
        return response.json();
    })
    .then(data => {
        if (data.status === true) {
            const resultText = isActive ? 'activated' : 'deactivated';
            if (checkboxEl) checkboxEl.dataset.prevChecked = String(isActive);
            showToast('success', `User ${email} ${resultText} successfully`);
        } else {
            if (checkboxEl && prev !== null) checkboxEl.checked = prev;
            showErrorMessage(data.message || `Failed to ${statusText} user`);
        }
    })
    .catch(error => {
        console.error(`Error ${statusText} user:`, error);
        if (checkboxEl && prev !== null) checkboxEl.checked = prev;
        showErrorMessage(error.message || `An error occurred while ${statusText} user`);
    })
    .finally(() => {
        if (checkboxEl) checkboxEl.disabled = false;
    });
}

/**
 * Format a date string to a more readable format
 * @param {string} dateString - The date string to format
 * @returns {string} The formatted date string
 */
function formatDate(dateString) {
    if (!dateString) return 'N/A';
    
    try {
        const date = new Date(dateString);
        return date.toLocaleString();
    } catch (error) {
        console.error('Error formatting date:', error);
        return dateString;
    }
}

/**
 * Toast notification (top-right slide-in/out)
 * @param {'success'|'error'|'info'} type
 * @param {string} message
 * @param {number} [timeout]
 */
function showToast(type, message, timeout) {
    try {
        const duration = typeof timeout === 'number' ? timeout : 4000;
        let container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            container.className = 'toast-container';
            document.body.appendChild(container);
        }
        const toast = document.createElement('div');
        const t = type === 'success' || type === 'error' || type === 'info' ? type : 'info';
        toast.className = `toast toast-${t}`;
        const icon = document.createElement('span');
        icon.className = 'material-icons toast-icon';
        icon.textContent = t === 'success' ? 'check_circle' : (t === 'error' ? 'error' : 'info');
        const text = document.createElement('div');
        text.className = 'toast-message';
        text.textContent = message || '';
        toast.appendChild(icon);
        toast.appendChild(text);
        container.appendChild(toast);
        requestAnimationFrame(() => { toast.classList.add('toast-visible'); });
        const hide = () => {
            toast.classList.remove('toast-visible');
            toast.classList.add('toast-hide');
            setTimeout(() => { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 250);
        };
        const timer = setTimeout(hide, duration);
        toast.addEventListener('click', function(){ clearTimeout(timer); hide(); });
    } catch (e) { console.warn('Toast failed:', e); }
}

/**
 * Show a success message
 * @param {string} message - The message to display
 */
function showSuccessMessage(message) {
    const successMessage = document.getElementById('success-message');
    const errorMessage = document.getElementById('error-message');
    const infoMessage = document.getElementById('info-message');
    if (!successMessage) return;
    successMessage.textContent = message;
    successMessage.classList.remove('hidden');
    successMessage.classList.add('visible');

    // Hide error and info messages
    if (errorMessage) { errorMessage.classList.remove('visible'); errorMessage.classList.add('hidden'); }
    if (infoMessage) { infoMessage.classList.remove('visible'); infoMessage.classList.add('hidden'); }

    // Hide message after 5 seconds
    setTimeout(() => {
        successMessage.classList.remove('visible');
        successMessage.classList.add('hidden');
    }, 5000);
}

/**
 * Show an error message
 * @param {string} message - The message to display
 */
function showErrorMessage(message) {
    const errorMessage = document.getElementById('error-message');
    const successMessage = document.getElementById('success-message');
    const infoMessage = document.getElementById('info-message');
    if (!errorMessage) return;
    errorMessage.textContent = message;
    errorMessage.classList.remove('hidden');
    errorMessage.classList.add('visible');

    // Hide success and info messages
    if (successMessage) { successMessage.classList.remove('visible'); successMessage.classList.add('hidden'); }
    if (infoMessage) { infoMessage.classList.remove('visible'); infoMessage.classList.add('hidden'); }

    // Hide message after 5 seconds
    setTimeout(() => {
        errorMessage.classList.remove('visible');
        errorMessage.classList.add('hidden');
    }, 5000);
}

/**
 * Show an info message
 * @param {string} message - The message to display
 */
function showInfoMessage(message) {
    const infoMessage = document.getElementById('info-message');
    const successMessage = document.getElementById('success-message');
    const errorMessage = document.getElementById('error-message');
    if (!infoMessage) return;
    infoMessage.textContent = message;
    infoMessage.classList.remove('hidden');
    infoMessage.classList.add('visible');

    // Hide success and error messages
    if (successMessage) { successMessage.classList.remove('visible'); successMessage.classList.add('hidden'); }
    if (errorMessage) { errorMessage.classList.remove('visible'); errorMessage.classList.add('hidden'); }
}

/**
 * Clear all messages
 */
function clearMessages() {
    const s = document.getElementById('success-message');
    const e = document.getElementById('error-message');
    const i = document.getElementById('info-message');
    if (s) { s.classList.remove('visible'); s.classList.add('hidden'); }
    if (e) { e.classList.remove('visible'); e.classList.add('hidden'); }
    if (i) { i.classList.remove('visible'); i.classList.add('hidden'); }
}

// Initialize admin dashboard when DOM is loaded
document.addEventListener('DOMContentLoaded', initializeAdmin);