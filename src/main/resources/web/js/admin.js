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

/**
 * Initialize the admin dashboard
 */
function initializeAdmin() {
    // Check if user is authenticated
    if (!isAuthenticated()) {
        // Redirect to login page
        window.location.href = '/login?error=auth_required';
        return;
    }

    // Set up event listeners
    document.getElementById('logout-button').addEventListener('click', logout);
    
    // Load users
    loadUsers(currentPage, pageSize);
}

/**
 * Load users from the API with pagination
 * @param {number} page - The page number to load
 * @param {number} pageSize - The number of users per page
 */
function loadUsers(page, pageSize) {
    // Show loading message
    showInfoMessage('Loading users...');
    
    // Make API request to get users
    authFetch(`/admin/users?page=${page}&pageSize=${pageSize}`)
        .then(response => {
            if (!response.ok) {
                // If response is not OK, throw error
                if (response.status === 403) {
                    throw new Error('You do not have permission to access this resource');
                } else {
                    throw new Error('Failed to load users');
                }
            }
            return response.json();
        })
        .then(data => {
            if (data.status === true && data.data) {
                // Clear any previous messages
                clearMessages();
                
                // Update pagination variables
                currentPage = data.data.pagination.page;
                pageSize = data.data.pagination.pageSize;
                totalPages = data.data.pagination.totalPages;
                totalUsers = data.data.pagination.totalCount;
                
                // Render users table
                renderUsersTable(data.data.users);
                
                // Render pagination
                renderPagination();
            } else {
                showErrorMessage(data.message || 'Failed to load users');
            }
        })
        .catch(error => {
            console.error('Error loading users:', error);
            showErrorMessage(error.message || 'An error occurred while loading users');
            
            // If error is due to authentication, redirect to login
            if (error.message.includes('permission') || error.message.includes('authentication')) {
                setTimeout(() => {
                    window.location.href = '/login?error=auth_required';
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
    tableBody.innerHTML = '';
    
    if (users.length === 0) {
        // If no users, show message
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 4;
        cell.textContent = 'No users found';
        cell.style.textAlign = 'center';
        row.appendChild(cell);
        tableBody.appendChild(row);
        return;
    }
    
    // Create a row for each user
    users.forEach(user => {
        const row = document.createElement('tr');
        
        // Email cell
        const emailCell = document.createElement('td');
        emailCell.textContent = user.email;
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
        
        // Add options for each role
        ['USER', 'MODERATOR', 'ADMIN'].forEach(role => {
            const option = document.createElement('option');
            option.value = role;
            option.textContent = role;
            option.selected = user.role === role;
            roleSelect.appendChild(option);
        });
        
        // Add event listener for role change
        roleSelect.addEventListener('change', function() {
            updateUserRole(user.email, this.value);
        });
        
        roleCell.appendChild(roleSelect);
        row.appendChild(roleCell);
        
        // Status cell
        const statusCell = document.createElement('td');
        const statusToggle = document.createElement('label');
        statusToggle.className = 'status-toggle';
        
        const statusInput = document.createElement('input');
        statusInput.type = 'checkbox';
        statusInput.checked = user.isActive;
        statusInput.dataset.email = user.email;
        
        // Add event listener for status change
        statusInput.addEventListener('change', function() {
            updateUserStatus(user.email, this.checked);
        });
        
        const statusSlider = document.createElement('span');
        statusSlider.className = 'status-slider';
        
        statusToggle.appendChild(statusInput);
        statusToggle.appendChild(statusSlider);
        statusCell.appendChild(statusToggle);
        row.appendChild(statusCell);
        
        tableBody.appendChild(row);
    });
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
function updateUserRole(email, role) {
    // Show loading message
    showInfoMessage(`Updating role for ${email}...`);
    
    // Make API request to update role
    authFetch(`/admin/users/${encodeURIComponent(email)}/role`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            role: role
        })
    })
    .then(response => {
        if (!response.ok) {
            // If response is not OK, throw error
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
            showSuccessMessage(`Role updated successfully for ${email}`);
        } else {
            showErrorMessage(data.message || 'Failed to update user role');
        }
    })
    .catch(error => {
        console.error('Error updating user role:', error);
        showErrorMessage(error.message || 'An error occurred while updating user role');
    });
}

/**
 * Update a user's active status
 * @param {string} email - The user's email
 * @param {boolean} isActive - The new active status
 */
function updateUserStatus(email, isActive) {
    // Show loading message
    const statusText = isActive ? 'activating' : 'deactivating';
    showInfoMessage(`${statusText} user ${email}...`);
    
    // Make API request to update status
    authFetch(`/admin/users/${encodeURIComponent(email)}/status`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            isActive: isActive
        })
    })
    .then(response => {
        if (!response.ok) {
            // If response is not OK, throw error
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
            showSuccessMessage(`User ${email} ${resultText} successfully`);
        } else {
            showErrorMessage(data.message || `Failed to ${statusText} user`);
        }
    })
    .catch(error => {
        console.error(`Error ${statusText} user:`, error);
        showErrorMessage(error.message || `An error occurred while ${statusText} user`);
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
 * Show a success message
 * @param {string} message - The message to display
 */
function showSuccessMessage(message) {
    const successMessage = document.getElementById('success-message');
    successMessage.textContent = message;
    successMessage.classList.remove('hidden');
    
    // Hide error and info messages
    document.getElementById('error-message').classList.add('hidden');
    document.getElementById('info-message').classList.add('hidden');
    
    // Hide message after 5 seconds
    setTimeout(() => {
        successMessage.classList.add('hidden');
    }, 5000);
}

/**
 * Show an error message
 * @param {string} message - The message to display
 */
function showErrorMessage(message) {
    const errorMessage = document.getElementById('error-message');
    errorMessage.textContent = message;
    errorMessage.classList.remove('hidden');
    
    // Hide success and info messages
    document.getElementById('success-message').classList.add('hidden');
    document.getElementById('info-message').classList.add('hidden');
    
    // Hide message after 5 seconds
    setTimeout(() => {
        errorMessage.classList.add('hidden');
    }, 5000);
}

/**
 * Show an info message
 * @param {string} message - The message to display
 */
function showInfoMessage(message) {
    const infoMessage = document.getElementById('info-message');
    infoMessage.textContent = message;
    infoMessage.classList.remove('hidden');
    
    // Hide success and error messages
    document.getElementById('success-message').classList.add('hidden');
    document.getElementById('error-message').classList.add('hidden');
}

/**
 * Clear all messages
 */
function clearMessages() {
    document.getElementById('success-message').classList.add('hidden');
    document.getElementById('error-message').classList.add('hidden');
    document.getElementById('info-message').classList.add('hidden');
}

// Initialize admin dashboard when DOM is loaded
document.addEventListener('DOMContentLoaded', initializeAdmin);