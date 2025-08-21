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
        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }
        return fetch(url, {
            method: 'GET',
            credentials: 'include',
            headers
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
    },
    updatePremium(email, isPremium) {
        const url = `/admin/users/${encodeURIComponent(email)}/premium`;
        return fetch(url, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({ isPremium: !!isPremium })
        });
    }
};

/**
 * Call admin endpoint to clear weather cache
 */
function clearWeatherCache() {
    return fetch('/admin/cache/clear', {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Accept': 'application/json'
        }
    }).then(resp => {
        if (!resp.ok) {
            if (resp.status === 403) throw new Error('Access denied');
            throw new Error('Failed to clear cache');
        }
        return resp.json();
    }).then(payload => {
        if (payload && payload.status === true) return true;
        throw new Error((payload && payload.message) || 'Failed to clear cache');
    });
}

function runHealthCheck() {
    return fetch('/admin/tools/health', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Accept': 'application/json' }
    }).then(resp => {
        if (!resp.ok) {
            if (resp.status === 403) throw new Error('Access denied');
            throw new Error('Health check failed');
        }
        return resp.json();
    }).then(payload => {
        if (!payload || payload.status !== true || !payload.data) {
            throw new Error((payload && payload.message) || 'Invalid health check response');
        }
        return payload.data;
    });
}

function runWarmup() {
    return fetch('/admin/tools/warmup', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
        body: JSON.stringify({})
    }).then(resp => {
        if (!resp.ok) {
            if (resp.status === 403) throw new Error('Access denied');
            throw new Error('Warmup failed');
        }
        return resp.json();
    }).then(payload => {
        if (!payload || payload.status !== true || !payload.data) {
            throw new Error((payload && payload.message) || 'Invalid warmup response');
        }
        return payload.data;
    });
}





function escapeHtml(str){
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// Ensure modal CSS/HTML and helpers exist for Admin dashboard (mirrors HomeRoute modal UI)
function ensureAdminModal(){
    try {
        // Inject CSS once
        if (!document.getElementById('admin-modal-style')) {
            const style = document.createElement('style');
            style.id = 'admin-modal-style';
            style.textContent = `
            .modal { display: none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0, 0, 0, 0.8); -webkit-backdrop-filter: blur(10px); backdrop-filter: blur(10px); opacity: 0; transition: opacity 0.3s ease; }
            .modal-content { background: var(--modal-bg, var(--card-bg)); -webkit-backdrop-filter: blur(20px); backdrop-filter: blur(20px); border: 1px solid var(--modal-border, var(--card-border)); border-radius: 12px; margin: 5% auto; padding: 2rem; width: 90%; max-width: 800px; max-height: 80vh; overflow-y: auto; position: relative; transition: background 0.3s ease, border-color 0.3s ease, transform 0.3s ease; transform: translateY(20px); }
            .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor: pointer; position: absolute; right: 1.5rem; top: 1rem; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border-radius: 50%; transition: all 0.2s ease; user-select: none; }
            .close:hover, .close:focus { color: #fff; background-color: rgba(255, 255, 255, 0.1); }
            .modal h2 { color: var(--modal-title, var(--card-title)); margin-bottom: 1.5rem; font-size: 1.75rem; font-weight: 600; }
            `;
            document.head.appendChild(style);
        }
        // Inject HTML once
        if (!document.getElementById('apiModal')) {
            const modal = document.createElement('div');
            modal.id = 'apiModal';
            modal.className = 'modal';
            modal.innerHTML = `
                <div class="modal-content">
                    <span class="close" id="modal-close">×</span>
                    <h2 id="modalTitle">Details</h2>
                    <div id="modalContent"></div>
                </div>
            `;
            document.body.appendChild(modal);
            const closeBtn = modal.querySelector('#modal-close');
            if (closeBtn) closeBtn.addEventListener('click', closeModal);
            window.addEventListener('click', function(event){ if (event.target === modal) closeModal(); });
        }
    } catch (e) { console.warn('Failed to ensure admin modal', e); }
}

function showModal(title, content) {
    try {
        ensureAdminModal();
        const modal = document.getElementById('apiModal');
        const modalTitle = document.getElementById('modalTitle');
        const modalContent = document.getElementById('modalContent');
        const modalContentDiv = modal ? modal.querySelector('.modal-content') : null;
        if (!modal || !modalTitle || !modalContent || !modalContentDiv) return;
        modalTitle.textContent = title;
        modalContent.innerHTML = content;
        modal.style.opacity = '0';
        modalContentDiv.style.transform = 'translateY(20px)';
        modal.style.display = 'block';
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                modal.style.opacity = '1';
                modalContentDiv.style.transform = 'translateY(0)';
            });
        });
        document.body.style.overflow = 'hidden';
        document.addEventListener('keydown', handleEscKey);
    } catch (e) { console.error('Error showing modal', e); }
}

function closeModal() {
    try {
        const modal = document.getElementById('apiModal');
        const modalContentDiv = modal ? modal.querySelector('.modal-content') : null;
        if (!modal || !modalContentDiv) return;
        const transitionDuration = (typeof getTransitionDuration === 'function') ? getTransitionDuration(modal) : 300;
        requestAnimationFrame(() => {
            modal.style.opacity = '0';
            modalContentDiv.style.transform = 'translateY(20px)';
            setTimeout(() => { modal.style.display = 'none'; document.body.style.overflow = 'auto'; }, transitionDuration);
        });
        document.removeEventListener('keydown', handleEscKey);
    } catch (e) { console.error('Error closing modal', e); }
}

function handleEscKey(event) { if (event.key === 'Escape') closeModal(); }

function buildHealthHtml(data){
    const badge = (b) => b ? '<span style="color:#10b981;font-weight:600;">OK</span>' : '<span style="color:#ef4444;font-weight:600;">FAIL</span>';
    const row = (k, v) => `<div style="display:flex; gap:8px; margin:2px 0;"><div style="min-width:180px;color:var(--text-color);font-weight:600;">${k}</div><div>${v}</div></div>`;
    const section = (title, body) => `<div style="border:1px solid var(--card-border);background:var(--card-bg);border-radius:8px;padding:12px;margin-top:10px;"><div style="font-weight:700;color:var(--card-title);margin-bottom:6px;">${title}</div>${body}</div>`;
    const wBody = [
        row('Base URL', `<code>${escapeHtml(String(data.weatherUrl || ''))}</code>`),
        row('Probe URL', `<code>${escapeHtml(String(data.probeWeatherUrl || ''))}</code>`),
        row('HTTP Status', `${data.weatherStatusCode}${data.weatherStatusText ? ' ' + escapeHtml(String(data.weatherStatusText)) : ''} · ${badge(!!data.weatherOk)}`),
        row('Latency', `${data.weatherLatencyMs} ms`),
        row('Content-Type', `${escapeHtml(String(data.weatherContentType || 'n/a'))}`),
        row('Bytes', `${data.weatherBytes != null ? data.weatherBytes : 'n/a'}`),
        data.weatherError ? row('Error', `<span style="color:#ef4444">${escapeHtml(String(data.weatherError))}</span>`) : ''
    ].join('');
    const aBody = [
        row('Base URL', `<code>${escapeHtml(String(data.airUrl || ''))}</code>`),
        row('Probe URL', `<code>${escapeHtml(String(data.probeAirUrl || ''))}</code>`),
        row('HTTP Status', `${data.airStatusCode}${data.airStatusText ? ' ' + escapeHtml(String(data.airStatusText)) : ''} · ${badge(!!data.airOk)}`),
        row('Latency', `${data.airLatencyMs} ms`),
        row('Content-Type', `${escapeHtml(String(data.airContentType || 'n/a'))}`),
        row('Bytes', `${data.airBytes != null ? data.airBytes : 'n/a'}`),
        data.airError ? row('Error', `<span style="color:#ef4444">${escapeHtml(String(data.airError))}</span>`) : ''
    ].join('');
    return [
        section('Weather Service', wBody),
        section('Air Pollution Service', aBody),
        `<div style="margin-top:8px;color:var(--text-secondary)">Checked At: ${new Date(data.timestamp || Date.now()).toLocaleString()}</div>`
    ].join('');
}

function buildWarmupHtml(data){
    if (!data || !Array.isArray(data.results)) return '<div>No data</div>';
    const badge = (b) => b ? '<span style="color:#10b981;font-weight:600;">OK</span>' : '<span style="color:#ef4444;font-weight:600;">FAIL</span>';
    const header = `<div style="display:grid;grid-template-columns: 1.2fr 1fr 1fr 1fr 1fr;gap:6px;font-weight:700;color:var(--card-title);margin-top:6px;">
        <div>Location</div><div>Weather</div><div>Latency</div><div>Air</div><div>Latency</div>
    </div>`;
    const rows = data.results.map(r => {
        const location = escapeHtml(r.name || `${r.lat.toFixed(4)}, ${r.lon.toFixed(4)}`);
        const w = `${r.weatherStatusCode} · ${badge(!!r.weatherOk)}`;
        const a = `${r.airStatusCode} · ${badge(!!r.airOk)}`;
        const wlat = (r.weatherLatencyMs >= 0 ? r.weatherLatencyMs + ' ms' : 'n/a');
        const alat = (r.airLatencyMs >= 0 ? r.airLatencyMs + ' ms' : 'n/a');
        return `<div style="display:grid;grid-template-columns: 1.2fr 1fr 1fr 1fr 1fr;gap:6px;padding:6px;border:1px solid var(--card-border);border-radius:6px;margin-top:6px;background:var(--card-bg);">
            <div>${location}</div>
            <div>${w}${r.weatherError ? ` <span style='color:#ef4444' title='${escapeHtml(r.weatherError)}'>!</span>` : ''}</div>
            <div>${wlat}</div>
            <div>${a}${r.airError ? ` <span style='color:#ef4444' title='${escapeHtml(r.airError)}'>!</span>` : ''}</div>
            <div>${alat}</div>
        </div>`;
    }).join('');
    const footer = `<div style="margin-top:8px;color:var(--text-secondary)">Completed: ${new Date(data.timestamp || Date.now()).toLocaleString()} · Overall: ${badge(!!data.ok)}</div>`;
    return header + rows + footer;
}

/**
 * Initialize the admin dashboard
 */
function initializeAdmin() {
    try {
        ensureAdminModal();
        // Bind logout
        const logoutBtn = document.getElementById('logout-button');
        if (logoutBtn && !logoutBtn.dataset.bound) {
            logoutBtn.addEventListener('click', function(e){ e.preventDefault(); if (typeof logout === 'function') logout(); });
            logoutBtn.dataset.bound = 'true';
        }

        // Bind Clear Cache tool
        const clearBtn = document.getElementById('clear-cache-btn');
        const spinner = document.getElementById('clear-cache-spinner');
        if (clearBtn && !clearBtn.dataset.bound) {
            clearBtn.addEventListener('click', function(){
                if (spinner) spinner.style.display = 'inline-block';
                clearBtn.disabled = true;
                clearWeatherCache()
                    .then(() => {
                        showToast('success', 'Weather cache cleared');
                    })
                    .catch(err => {
                        console.error('Clear cache failed', err);
                        showToast('error', err && err.message ? err.message : 'Failed to clear cache');
                    })
                    .finally(() => {
                        if (spinner) spinner.style.display = 'none';
                        clearBtn.disabled = false;
                    });
            });
            clearBtn.dataset.bound = 'true';
        }

        // Bind Health Check tool
        const healthBtn = document.getElementById('run-health-btn');
        const healthSpinner = document.getElementById('run-health-spinner');
        if (healthBtn && !healthBtn.dataset.bound) {
            healthBtn.addEventListener('click', function(){
                if (healthSpinner) healthSpinner.style.display = 'inline-block';
                healthBtn.disabled = true;
                runHealthCheck()
                    .then((data) => {
                        try { showModal('Health Check Results', buildHealthHtml(data)); } catch(e) { console.warn('Failed to show health modal', e); }
                        const overallOk = (data.weatherOk === true) && (data.airOk === true);
                        showToast(overallOk ? 'success' : 'error', overallOk ? 'Health check passed' : 'Health check has failures');
                    })
                    .catch(err => {
                        console.error('Health check failed', err);
                        showToast('error', err && err.message ? err.message : 'Health check failed');
                    })
                    .finally(() => {
                        if (healthSpinner) healthSpinner.style.display = 'none';
                        healthBtn.disabled = false;
                    });
            });
            healthBtn.dataset.bound = 'true';
        }

        // Bind Warmup tool
        const warmBtn = document.getElementById('warmup-btn');
        const warmSpinner = document.getElementById('warmup-spinner');
        if (warmBtn && !warmBtn.dataset.bound) {
            warmBtn.addEventListener('click', function(){
                if (warmSpinner) warmSpinner.style.display = 'inline-block';
                warmBtn.disabled = true;
                runWarmup()
                    .then((data) => {
                        try { showModal('Warmup Results', buildWarmupHtml(data)); } catch(e) { console.warn('Failed to show warmup modal', e); }
                        showToast(data.ok ? 'success' : 'error', data.ok ? 'Warmup completed' : 'Warmup completed with failures');
                    })
                    .catch(err => {
                        console.error('Warmup failed', err);
                        showToast('error', err && err.message ? err.message : 'Warmup failed');
                    })
                    .finally(() => {
                        if (warmSpinner) warmSpinner.style.display = 'none';
                        warmBtn.disabled = false;
                    });
            });
            warmBtn.dataset.bound = 'true';
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
        cell.colSpan = 5;
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

        // Premium cell
        const premiumCell = document.createElement('td');
        const premiumToggle = document.createElement('label');
        premiumToggle.className = 'status-toggle';

        const premiumInput = document.createElement('input');
        premiumInput.type = 'checkbox';
        premiumInput.checked = !!user.isPremium;
        premiumInput.dataset.email = user.email;
        premiumInput.dataset.prevChecked = String(!!user.isPremium);

        premiumInput.addEventListener('change', function() {
            updateUserPremium(user.email, this.checked, this);
        });

        const premiumSlider = document.createElement('span');
        premiumSlider.className = 'status-slider';

        premiumToggle.appendChild(premiumInput);
        premiumToggle.appendChild(premiumSlider);
        premiumCell.appendChild(premiumToggle);
        row.appendChild(premiumCell);

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

function updateUserPremium(email, isPremium, checkboxEl) {
    const prev = checkboxEl ? (checkboxEl.dataset.prevChecked === 'true') : null;
    if (prev !== null && prev === isPremium) {
        return;
    }
    if (checkboxEl) checkboxEl.disabled = true;
    const actionText = isPremium ? 'enabling premium for' : 'disabling premium for';
    window.UserRoute.updatePremium(email, isPremium)
    .then(response => {
        if (!response.ok) {
            if (response.status === 403) {
                throw new Error('You do not have permission to update premium status');
            } else {
                throw new Error(`Failed while ${actionText} user`);
            }
        }
        return response.json();
    })
    .then(data => {
        if (data.status === true) {
            if (checkboxEl) checkboxEl.dataset.prevChecked = String(isPremium);
            const resultText = isPremium ? 'Premium enabled' : 'Premium disabled';
            showToast('success', `${resultText} for ${email}`);
        } else {
            if (checkboxEl && prev !== null) checkboxEl.checked = prev;
            showErrorMessage(data.message || 'Failed to update premium status');
        }
    })
    .catch(error => {
        console.error('Error updating premium status:', error);
        if (checkboxEl && prev !== null) checkboxEl.checked = prev;
        showErrorMessage(error.message || 'An error occurred while updating premium status');
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
    // Route all success notifications through top-right toast
    try { showToast('success', message || 'Success'); } catch (_) {}
}

/**
 * Show an error message
 * @param {string} message - The message to display
 */
function showErrorMessage(message) {
    // Route all error notifications through top-right toast
    try { showToast('error', message || 'Something went wrong'); } catch (_) {}
}

/**
 * Show an info message
 * @param {string} message - The message to display
 */
function showInfoMessage(message) {
    // Route all info notifications through top-right toast
    try { showToast('info', message || ''); } catch (_) {}
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
