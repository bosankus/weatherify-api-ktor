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
    },
    notify(email, payload) {
        const url = `/admin/users/${encodeURIComponent(email)}/notify`;
        return fetch(url, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(payload || { title: 'Promotion', body: 'Enjoy new features in our app!' })
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


        // Bind JWT Token Inspector tool
        const jwtBtn = document.getElementById('jwt-inspector-btn');
        if (jwtBtn && !jwtBtn.dataset.bound) {
            jwtBtn.addEventListener('click', function(){
                try {
                    const content = buildJwtInspectorContent();
                    showModal('JWT Token Inspector', content);
                    bindJwtInspectorModal();
                } catch(e) { console.error('Failed to open JWT Inspector', e); }
            });
            jwtBtn.dataset.bound = 'true';
        }

        // Ensure footer exists (if server-side footer is missing)
        try {
            if (!document.querySelector('.footer')) {
                const container = document.querySelector('.container.main-container') || document.querySelector('.container');
                if (container) {
                    const year = new Date().getFullYear();
                    const footer = document.createElement('footer');
                    footer.className = 'footer';
                    footer.innerHTML = `<div class="footer-content"><div class="footer-copyright">© ${year} Androidplay. All rights reserved.</div></div>`;
                    container.appendChild(footer);
                }
            }
        } catch(e) { console.warn('Failed to inject dashboard footer', e); }

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
        cell.colSpan = 6;
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

        // Actions cell (overflow menu at end of row)
        const actionsCell = createActionsCell(user);
        row.appendChild(actionsCell);

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


// ================= JWT Token Inspector =================
function base64UrlDecodeToString(input){
    if (typeof input !== 'string') return '';
    let str = input.replace(/-/g, '+').replace(/_/g, '/');
    const pad = str.length % 4;
    if (pad === 2) str += '==';
    else if (pad === 3) str += '=';
    else if (pad === 1) throw new Error('Invalid base64url string');
    try { return atob(str); } catch(e){ throw new Error('Base64 decode failed'); }
}

function safeJsonParse(str){
    try { return JSON.parse(str); } catch(e){ throw new Error('Invalid JSON'); }
}

function decodeJwtToken(token){
    if (!token || typeof token !== 'string') throw new Error('No token provided');
    const parts = token.split('.');
    if (parts.length !== 3) throw new Error('Invalid JWT format');
    const [h, p, s] = parts;
    const headerStr = base64UrlDecodeToString(h);
    const payloadStr = base64UrlDecodeToString(p);
    const header = safeJsonParse(headerStr);
    const payload = safeJsonParse(payloadStr);
    const signature = s || '';
    return { header, payload, signature, raw: { header:h, payload:p, signature:s } };
}

function tsToLocal(tsSec){
    if (typeof tsSec !== 'number') return 'n/a';
    try { return new Date(tsSec * 1000).toLocaleString(); } catch(e){ return String(tsSec); }
}

function computeTimeValidity(payload){
    const nowSec = Math.floor(Date.now()/1000);
    const exp = typeof payload.exp === 'number' ? payload.exp : null;
    const nbf = typeof payload.nbf === 'number' ? payload.nbf : null;
    const iat = typeof payload.iat === 'number' ? payload.iat : null;
    let status = 'Unknown';
    if (exp && nowSec > exp) status = 'Expired';
    else if (nbf && nowSec < nbf) status = 'Not yet valid';
    else status = 'Valid by time';
    return { nowSec, exp, nbf, iat, status };
}

function renderJwtResult(decoded){
    const time = computeTimeValidity(decoded.payload || {});
    const claims = decoded.payload || {};
    const keys = Object.keys(claims).sort();
    const claimRows = keys.map(k => `<tr><td style="font-weight:600;color:var(--card-title)">${escapeHtml(k)}</td><td><code>${escapeHtml(String(claims[k]))}</code></td></tr>`).join('');
    const hasSig = (decoded.signature || '').length > 0;
    return [
        '<div style="display:grid; gap:12px">',
        '<div class="message info-message">Client-side decoding only. Signature verification is not performed here.</div>',
        '<div style="display:grid; gap:6px">',
        '<div style="font-weight:700;color:var(--card-title)">Time Validity</div>',
        `<div>Status: <span style="font-weight:700;${time.status==='Expired'?'color:#ef4444':(time.status==='Valid by time'?'color:#10b981':'color:#f59e0b')}">${time.status}</span></div>`,
        `<div>exp: ${time.exp != null ? tsToLocal(time.exp) + ` (${time.exp})` : 'n/a'}</div>`,
        `<div>nbf: ${time.nbf != null ? tsToLocal(time.nbf) + ` (${time.nbf})` : 'n/a'}</div>`,
        `<div>iat: ${time.iat != null ? tsToLocal(time.iat) + ` (${time.iat})` : 'n/a'}</div>`,
        '</div>',
        '<div style="display:grid; gap:6px">',
        '<div style="font-weight:700;color:var(--card-title)">Header</div>',
        `<pre style="white-space:pre-wrap;background:var(--card-bg);border:1px solid var(--card-border);border-radius:8px;padding:10px">${escapeHtml(JSON.stringify(decoded.header, null, 2))}</pre>`,
        '</div>',
        '<div style="display:grid; gap:6px">',
        '<div style="font-weight:700;color:var(--card-title)">Payload</div>',
        `<pre style="white-space:pre-wrap;background:var(--card-bg);border:1px solid var(--card-border);border-radius:8px;padding:10px">${escapeHtml(JSON.stringify(decoded.payload, null, 2))}</pre>`,
        '</div>',
        '<div style="display:grid; gap:6px">',
        '<div style="font-weight:700;color:var(--card-title)">Claims</div>',
        `<div style="overflow:auto"><table style="width:100%;border-collapse:collapse">${claimRows || '<tr><td>No claims</td></tr>'}</table></div>`,
        '</div>',
        `<div>Signature present: <strong>${hasSig ? 'Yes' : 'No'}</strong> (not verified)</div>`,
        '</div>'
    ].join('');
}

function buildJwtInspectorContent(){
    const token = '';
    return [
        '<div style="display:grid; gap:10px">',
        '<div class="message info-message">Paste a JWT below to decode its header and payload. Do not include the "Bearer " prefix.</div>',
        '<textarea id="jwt-input" class="form-control" rows="5" placeholder="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiZW1haWwiOiJ1c2VyQGV4YW1wbGUuY29tIiwiZXhwIjoxNzAwMDAwMDAwfQ.signature"></textarea>',
        '<div style="display:flex; gap:8px; align-items:center">',
        '<button id="jwt-decode-btn" class="btn btn-primary">Decode</button>',
        '<button id="jwt-clear-btn" class="btn btn-secondary">Clear</button>',
        '</div>',
        '<div id="jwt-error" class="message error-message" style="display:none"></div>',
        '<div id="jwt-result"></div>',
        '</div>'
    ].join('');
}

function bindJwtInspectorModal(){
    const input = document.getElementById('jwt-input');
    const decodeBtn = document.getElementById('jwt-decode-btn');
    const clearBtn = document.getElementById('jwt-clear-btn');
    const result = document.getElementById('jwt-result');
    const err = document.getElementById('jwt-error');
    if (!input || !decodeBtn || !result) return;

    function showErr(msg){ if (!err) return; err.textContent = msg || 'Error'; err.style.display = 'block'; }
    function hideErr(){ if (err) err.style.display = 'none'; }

    decodeBtn.addEventListener('click', function(){
        hideErr();
        result.innerHTML = '';
        let value = (input.value || '').trim();
        if (!value) { showErr('Please paste a JWT token'); return; }
        if (value.toLowerCase().startsWith('bearer ')) value = value.slice(7).trim();
        try {
            const decoded = decodeJwtToken(value);
            result.innerHTML = renderJwtResult(decoded);
            showToast('success', 'Token decoded');
        } catch(e){
            console.error('JWT decode error', e);
            showErr(e && e.message ? e.message : 'Failed to decode token');
        }
    });

    if (clearBtn) clearBtn.addEventListener('click', function(){ input.value=''; result.innerHTML=''; hideErr(); });
}

// =============== End JWT Token Inspector ===============


// ================= Reports (Usage Analytics) =================
(function(){
    const state = {
        initialized: false,
        chart: null,
        users: []
    };

    function q(id){ return document.getElementById(id); }
    function cssVar(name){
        try { return getComputedStyle(document.documentElement).getPropertyValue(name).trim(); } catch(_) { return ''; }
    }

    function parseDateSafe(v){
        try {
            const d = new Date(v);
            return isNaN(d.getTime()) ? null : d;
        } catch(_) { return null; }
    }

    function endOfDay(d){ const nd = new Date(d); nd.setHours(23,59,59,999); return nd; }

    function getRangeDays(){
        const sel = q('reports-range');
        const n = sel ? parseInt(sel.value, 10) : 30;
        return isNaN(n) ? 30 : n;
    }

    function buildEmptySeries(days){
        const labels = [];
        const data = [];
        const now = new Date();
        for (let i = days - 1; i >= 0; i--) {
            const d = new Date(now);
            d.setDate(d.getDate() - i);
            const key = d.toISOString().slice(0,10);
            labels.push(key);
            data.push(0);
        }
        return { labels, data };
    }

    function aggregateDaily(users, days){
        const series = buildEmptySeries(days);
        const byKey = Object.create(null);
        series.labels.forEach((k, idx) => { byKey[k] = idx; });
        const cutoff = new Date();
        cutoff.setDate(cutoff.getDate() - (days - 1));
        users.forEach(u => {
            const d = parseDateSafe(u.createdAt);
            if (!d) return;
            if (d < new Date(cutoff.toDateString())) return;
            const key = d.toISOString().slice(0,10);
            const idx = byKey[key];
            if (typeof idx === 'number') series.data[idx]++;
        });
        return series;
    }

    function updateKpis(users, days, series){
        try {
            const totalNew = series.data.reduce((a,b)=>a+b,0);
            const activeCount = users.filter(u => u.isActive === true).length;
            const activeRate = users.length > 0 ? Math.round((activeCount / users.length) * 100) : 0;
            const admins = users.filter(u => (u.role||'').toUpperCase() === 'ADMIN').length;
            const premium = users.filter(u => u.isPremium === true).length;
            const elNew = q('kpi-new-users');
            const elAct = q('kpi-active-rate');
            const elAdm = q('kpi-admins');
            const elPre = q('kpi-premium');
            if (elNew) elNew.textContent = String(totalNew);
            if (elAct) elAct.textContent = activeRate + '%';
            if (elAdm) elAdm.textContent = String(admins);
            if (elPre) elPre.textContent = String(premium);
        } catch(e) { console.warn('Failed to update KPIs', e); }
    }

    function lineColor(){ return '#6366f1'; }
    function gridColor(){ return (cssVar('--card-border') || 'rgba(99,102,241,0.2)'); }
    function tickColor(){ return (cssVar('--text-secondary') || '#9ca3af'); }

    function destroyChart(){ try { if (state.chart) { state.chart.destroy(); state.chart = null; } } catch(_){} }

    function renderChart(series){
        const canvas = q('reports-chart');
        const empty = q('reports-empty');
        if (!canvas) return;
        const hasData = series.data.some(v => v > 0);
        if (!hasData) {
            if (empty) { empty.classList.remove('hidden'); empty.classList.add('visible'); }
        } else {
            if (empty) { empty.classList.add('hidden'); empty.classList.remove('visible'); }
        }
        if (typeof Chart === 'undefined') {
            console.warn('Chart.js not available');
            if (empty) { empty.classList.remove('hidden'); empty.classList.add('visible'); empty.textContent = 'Chart library not available'; }
            return;
        }
        destroyChart();
        const ctx = canvas.getContext('2d');
        const glowPlugin = {
            id: 'glowLine',
            beforeDatasetDraw(chart, args) {
                if (!args || !args.meta || args.meta.type !== 'line') return;
                const ctx = chart.ctx;
                const ds = chart.data && chart.data.datasets ? chart.data.datasets[args.index] : null;
                ctx.save();
                ctx.shadowColor = (ds && ds.borderColor) || lineColor();
                ctx.shadowBlur = 1;
                ctx.shadowOffsetX = 0;
                ctx.shadowOffsetY = 0;
            },
            afterDatasetDraw(chart, args) {
                const ctx = chart.ctx;
                try { ctx.restore(); } catch(_) {}
            }
        };

        // gradient fill for a subtle modern look
        let gradient;
        try {
            gradient = ctx.createLinearGradient(0, 0, 0, canvas.height || 260);
            gradient.addColorStop(0, 'rgba(99, 102, 241, 0.22)');
            gradient.addColorStop(1, 'rgba(99, 102, 241, 0.02)');
        } catch(_) {
            gradient = 'rgba(99, 102, 241, 0.12)';
        }

        state.chart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: series.labels.map(k => {
                    try { const d = new Date(k); return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }); } catch(_) { return k; }
                }),
                datasets: [{
                    label: 'New users',
                    data: series.data,
                    borderColor: lineColor(),
                    backgroundColor: gradient,
                    fill: true,
                    cubicInterpolationMode: 'monotone',
                    tension: 0.6,
                    borderWidth: 2.5,
                    pointRadius: 0,
                    pointHoverRadius: 3
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 700, easing: 'easeOutCubic' },
                interaction: { mode: 'index', intersect: false },
                elements: {
                    line: { borderJoinStyle: 'round', capBezierPoints: true },
                    point: { hitRadius: 10 }
                },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        enabled: true,
                        backgroundColor: 'rgba(17, 24, 39, 0.9)',
                        titleColor: '#ffffff',
                        bodyColor: '#ffffff',
                        displayColors: false,
                        padding: 10,
                        mode: 'index',
                        intersect: false
                    }
                },
                scales: {
                    x: {
                        grid: { display: false, drawBorder: false },
                        ticks: { color: tickColor(), maxTicksLimit: 8, maxRotation: 0 }
                    },
                    y: {
                        display: false,
                        beginAtZero: true,
                        grid: { display: false, drawBorder: false },
                        ticks: { display: false }
                    }
                }
            },
            plugins: [glowPlugin]
        });
    }

    function render(){
        const days = getRangeDays();
        const series = aggregateDaily(state.users, days);
        updateKpis(state.users, days, series);
        renderChart(series);
    }

    function fetchUsers(){
        // Load up to 100 latest users for aggregation
        if (!window.UserRoute || typeof window.UserRoute.listUsers !== 'function') return Promise.reject(new Error('Users API unavailable'));
        return window.UserRoute.listUsers(1, 100).then(({ users }) => {
            state.users = Array.isArray(users) ? users : [];
            return state.users;
        });
    }

    function bindRange(){
        const sel = q('reports-range');
        if (sel && !sel.dataset.bound) {
            sel.addEventListener('change', render);
            sel.dataset.bound = 'true';
        }
    }

    function bindThemeReactivity(){
        // Observe html class changes to refresh chart colors
        try {
            const html = document.documentElement;
            const obs = new MutationObserver(() => { if (state.chart) { render(); } });
            obs.observe(html, { attributes: true, attributeFilter: ['class'] });
        } catch(_) {}
        const toggle = document.getElementById('theme-toggle');
        if (toggle && !toggle.dataset.reportBound) {
            toggle.addEventListener('change', () => { if (state.chart) { render(); } });
            toggle.dataset.reportBound = 'true';
        }
    }

    function ensureInitialized(){
        if (state.initialized) return;
        const panel = document.getElementById('reports');
        if (!panel) return;
        // show loader message in empty div while fetching
        const empty = q('reports-empty');
        if (empty) { empty.textContent = 'Loading report…'; empty.classList.remove('hidden'); empty.classList.add('visible'); }
        fetchUsers()
            .then(() => { bindRange(); bindThemeReactivity(); render(); state.initialized = true; })
            .catch(err => { console.warn('Failed to initialize reports', err); if (empty) { empty.textContent = 'Failed to load data'; } });
    }

    // Hook into admin init: click on Reports tab should trigger initialization
    document.addEventListener('DOMContentLoaded', function(){
        try {
            // Bind tab click
            const tab = document.querySelector('.tab[data-tab="reports"]');
            if (tab && !tab.dataset.reportsBound) {
                tab.addEventListener('click', ensureInitialized);
                tab.dataset.reportsBound = 'true';
            }
            // If already active (unlikely), init immediately
            const panel = document.getElementById('reports');
            if (panel && panel.classList.contains('active')) {
                ensureInitialized();
            }
        } catch(e) { console.warn('Reports binding failed', e); }
    });
})();


// ===== IAM Actions Menu (Users row overflow menu) =====
function closeAllOverflowMenus(){
    try {
        document.querySelectorAll('.overflow-menu.open').forEach(menu => {
            menu.classList.remove('open');
            menu.style.display = 'none';
        });
    } catch (_) {}
}

function ensureGlobalMenuCloseHandler(){
    if (window.__iamMenuCloseInstalled) return;
    try {
        document.addEventListener('click', function(e){
            const target = e.target;
            if (!target) return closeAllOverflowMenus();
            if (!target.closest) return closeAllOverflowMenus();
            if (!target.closest('.actions-wrapper')) closeAllOverflowMenus();
        });
        window.__iamMenuCloseInstalled = true;
    } catch (_) {}
}

function createActionsCell(user){
    const td = document.createElement('td');
    td.className = 'actions-cell';

    const wrapper = document.createElement('div');
    wrapper.className = 'actions-wrapper';
    wrapper.style.position = 'relative';
    wrapper.style.display = 'flex';
    wrapper.style.justifyContent = 'flex-end';

    // Button
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'icon-button';
    btn.setAttribute('aria-haspopup', 'true');
    btn.setAttribute('aria-expanded', 'false');
    btn.title = 'Actions';
    Object.assign(btn.style, {
        border: 'none',
        background: 'transparent',
        cursor: 'pointer',
        padding: '4px',
        borderRadius: '50%'
    });
    const icon = document.createElement('span');
    icon.className = 'material-icons';
    icon.textContent = 'more_vert';
    icon.style.fontSize = '20px';
    icon.style.color = 'var(--icon-color)';
    btn.appendChild(icon);

    // Menu
    const menu = document.createElement('div');
    menu.className = 'overflow-menu';
    Object.assign(menu.style, {
        position: 'absolute',
        right: '0',
        top: '28px',
        background: 'var(--card-bg)',
        border: '1px solid var(--card-border)',
        borderRadius: '8px',
        boxShadow: '0 8px 16px rgba(0,0,0,0.15)',
        minWidth: '220px',
        zIndex: '2000',
        display: 'none',
        overflow: 'hidden'
    });

    const item = document.createElement('button');
    item.type = 'button';
    item.className = 'overflow-menu-item';
    item.textContent = 'Send Promotional Notification';
    Object.assign(item.style, {
        display: 'block',
        width: '100%',
        textAlign: 'left',
        background: 'transparent',
        border: 'none',
        padding: '10px 12px',
        color: 'var(--text-color)',
        cursor: 'pointer'
    });
    item.addEventListener('mouseenter', function(){ this.style.background = 'var(--card-hover-bg)'; });
    item.addEventListener('mouseleave', function(){ this.style.background = 'transparent'; });

    item.addEventListener('click', function(e){
        e.stopPropagation();
        closeAllOverflowMenus();
        try {
            const email = user.email;
            const content = buildSendNotificationContent(email);
            showModal('Send Promotional Notification', content);
            bindSendNotificationModal(email);
        } catch (err) {
            console.error('Failed to open notification modal', err);
            try { showErrorMessage('Failed to open notification dialog'); } catch(_){}
        }
    });

    menu.appendChild(item);

    btn.addEventListener('click', function(e){
        e.stopPropagation();

        // Ripple feedback
        try {
            const rect = btn.getBoundingClientRect();
            const size = Math.max(rect.width, rect.height);
            const ripple = document.createElement('span');
            ripple.className = 'ripple';
            ripple.style.width = size + 'px';
            ripple.style.height = size + 'px';
            ripple.style.left = (e.clientX - rect.left - size / 2) + 'px';
            ripple.style.top = (e.clientY - rect.top - size / 2) + 'px';
            btn.appendChild(ripple);
            setTimeout(() => { try { ripple.remove(); } catch(_){} }, 650);
        } catch(_) {}

        const isOpen = menu.classList.contains('open');
        closeAllOverflowMenus();
        if (!isOpen) {
            // Show first to measure
            menu.style.display = 'block';
            menu.classList.add('open');
            btn.setAttribute('aria-expanded', 'true');

            // Positioning: if menu would overflow viewport bottom, open upwards
            try {
                const menuRect = menu.getBoundingClientRect();
                const spaceBelow = window.innerHeight - menuRect.top;
                const wouldOverflow = spaceBelow < (menuRect.height + 16);
                if (wouldOverflow) {
                    menu.style.top = 'auto';
                    menu.style.bottom = '28px';
                } else {
                    menu.style.bottom = 'auto';
                    menu.style.top = '28px';
                }
            } catch(_) {}
        } else {
            menu.style.display = 'none';
            menu.classList.remove('open');
            btn.setAttribute('aria-expanded', 'false');
        }
    });

    ensureGlobalMenuCloseHandler();

    wrapper.appendChild(btn);
    wrapper.appendChild(menu);
    td.appendChild(wrapper);
    return td;
}

function sendPromotionalNotification(email){
    const payload = { title: 'Promotion', body: 'Enjoy new features in our app!' };
    return window.UserRoute.notify(email, payload)
        .then(response => {
            if (!response.ok) {
                return response.json().catch(() => ({})).then(data => {
                    const msg = (data && data.message) || 'Failed to send notification';
                    throw new Error(msg);
                });
            }
            return response.json();
        })
        .then(data => {
            if (data && data.status === true) {
                showSuccessMessage('Notification sent successfully');
            } else {
                throw new Error((data && data.message) || 'Failed to send notification');
            }
        })
        .catch(err => {
            showErrorMessage(err.message || 'Failed to send notification');
        });
}


// ===== Send Promotional Notification Modal =====
function buildSendNotificationContent(email){
    try { email = String(email || ''); } catch(_) { email = ''; }
    return [
        '<div style="display:grid; gap:10px">',
        `<div class="message info-message">Send a promotional push notification to <strong>${escapeHtml(email)}</strong>.</div>`,
        '<div style="display:grid; gap:6px">',
        '<label for="notif-title" style="color:var(--text-secondary)">Title</label>',
        '<input id="notif-title" class="form-control" type="text" placeholder="Promotion" />',
        '</div>',
        '<div style="display:grid; gap:6px">',
        '<label for="notif-body" style="color:var(--text-secondary)">Body</label>',
        '<textarea id="notif-body" class="form-control" rows="4" placeholder="Write your message"></textarea>',
        '</div>',
        '<div id="notif-error" class="message error-message" style="display:none"></div>',
        '<div style="display:flex; gap:8px; align-items:center">',
        '<button id="notif-send-btn" class="btn btn-primary">Send</button>',
        '<button id="notif-cancel-btn" class="btn btn-secondary">Cancel</button>',
        '</div>',
        '</div>'
    ].join('');
}

function bindSendNotificationModal(email){
    const titleEl = document.getElementById('notif-title');
    const bodyEl = document.getElementById('notif-body');
    const sendBtn = document.getElementById('notif-send-btn');
    const cancelBtn = document.getElementById('notif-cancel-btn');
    const errEl = document.getElementById('notif-error');
    if (!sendBtn || !cancelBtn || !bodyEl) return;

    // Pre-fill recommended default title
    try { if (titleEl && !titleEl.value) titleEl.value = 'Promotion'; } catch(_){}

    function showErr(msg){ if (errEl){ errEl.textContent = msg || 'Error'; errEl.style.display = 'block'; } }
    function clearErr(){ if (errEl){ errEl.textContent = ''; errEl.style.display = 'none'; } }

    cancelBtn.addEventListener('click', function(){ closeModal(); });

    sendBtn.addEventListener('click', function(){
        clearErr();
        const title = (titleEl && typeof titleEl.value === 'string' && titleEl.value.trim()) ? titleEl.value.trim() : 'Promotion';
        const body = (bodyEl && typeof bodyEl.value === 'string') ? bodyEl.value.trim() : '';
        if (!body) { showErr('Body is required'); return; }
        sendBtn.disabled = true;
        const prevText = sendBtn.textContent;
        sendBtn.textContent = 'Sending...';
        window.UserRoute.notify(email, { title, body })
            .then(response => {
                if (!response.ok) {
                    return response.json().catch(() => ({})).then(data => {
                        const msg = (data && data.message) || 'Failed to send notification';
                        throw new Error(msg);
                    });
                }
                return response.json();
            })
            .then(data => {
                if (data && data.status === true) {
                    try { showToast('success', 'Notification sent successfully'); } catch(_) {}
                    closeModal();
                } else {
                    throw new Error((data && data.message) || 'Failed to send notification');
                }
            })
            .catch(err => {
                showErr(err && err.message ? err.message : 'Failed to send notification');
            })
            .finally(() => {
                sendBtn.disabled = false;
                sendBtn.textContent = prevText || 'Send';
            });
    });
}
// ===== End Send Promotional Notification Modal =====
