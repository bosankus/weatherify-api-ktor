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
        // Initialize shared header component
        if (typeof initializeHeader === 'function') {
            initializeHeader({
                homeUrl: '/admin',
                subtitle: 'ADMIN PORTAL',
                actions: [ { type: 'theme-toggle' }, { type: 'user-info' }, { type: 'logout' } ]
            });
        }
        // After rendering header actions, (re)initialize the theme toggle to ensure listeners are bound
        if (typeof initializeTheme === 'function') {
            initializeTheme();
        }

        ensureAdminModal();
        ensureCheckboxStyles();
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
                        showMessage('success', 'Weather cache cleared');
                    })
                    .catch(err => {
                        console.error('Clear cache failed', err);
                        showMessage('error', err && err.message ? err.message : 'Failed to clear cache');
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
                        showMessage(overallOk ? 'success' : 'error', overallOk ? 'Health check passed' : 'Health check has failures');
                    })
                    .catch(err => {
                        console.error('Health check failed', err);
                        showMessage('error', err && err.message ? err.message : 'Health check failed');
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
                        showMessage(data.ok ? 'success' : 'error', data.ok ? 'Warmup completed' : 'Warmup completed with failures');
                    })
                    .catch(err => {
                        console.error('Warmup failed', err);
                        showMessage('error', err && err.message ? err.message : 'Warmup failed');
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
            showMessage('success', `Role updated successfully for ${email}`);
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
            showMessage('success', `User ${email} ${resultText} successfully`);
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
            showMessage('success', `${resultText} for ${email}`);
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
 * Ensure custom checkbox styles are injected
 */
function ensureCheckboxStyles() {
    if (document.getElementById('custom-checkbox-styles')) return;
    const style = document.createElement('style');
    style.id = 'custom-checkbox-styles';
    style.textContent = `
        /* Custom Checkbox Styles */
        input[type="checkbox"] {
            appearance: none;
            -webkit-appearance: none;
            -moz-appearance: none;
            width: 20px;
            height: 20px;
            border: 2px solid var(--card-border, #d1d5db);
            border-radius: 6px;
            background: var(--card-bg, #ffffff);
            cursor: pointer;
            position: relative;
            transition: all 0.2s ease;
            flex-shrink: 0;
        }

        input[type="checkbox"]:hover {
            border-color: #6366f1;
            box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
        }

        input[type="checkbox"]:checked {
            background: #6366f1;
            border-color: #6366f1;
        }

        input[type="checkbox"]:checked::after {
            content: '';
            position: absolute;
            left: 6px;
            top: 2px;
            width: 5px;
            height: 10px;
            border: solid white;
            border-width: 0 2px 2px 0;
            transform: rotate(45deg);
        }

        input[type="checkbox"]:focus {
            outline: none;
            border-color: #6366f1;
            box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
        }

        input[type="checkbox"]:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }

        /* Larger checkboxes for specific contexts */
        .payment-checkbox,
        .subscription-checkbox {
            width: 20px;
            height: 20px;
        }

        /* Status toggle checkboxes (keep existing toggle style) */
        .status-toggle input[type="checkbox"] {
            opacity: 0;
            width: 0;
            height: 0;
            appearance: initial;
            -webkit-appearance: initial;
        }
    `;
    document.head.appendChild(style);
}

/**
 * Ensure banner styles are injected
 */
function ensureBannerStyles() {
    if (document.getElementById('banner-styles')) return;
    const style = document.createElement('style');
    style.id = 'banner-styles';
    style.textContent = `
        .banner-container {
            position: fixed;
            top: 20px;
            right: 20px;
            left: auto;
            z-index: 10000;
            display: flex;
            flex-direction: column;
            gap: 12px;
            pointer-events: none;
            max-width: 420px;
        }

        .banner {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 16px 18px;
            border-radius: 12px;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12), 0 2px 8px rgba(0, 0, 0, 0.08);
            backdrop-filter: blur(20px) saturate(180%);
            -webkit-backdrop-filter: blur(20px) saturate(180%);
            border: 1px solid rgba(255, 255, 255, 0.18);
            opacity: 0;
            transform: translateX(100%);
            transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
            pointer-events: auto;
            cursor: pointer;
            min-width: 320px;
            margin-left: auto;
        }

        .banner-visible {
            opacity: 1;
            transform: translateX(0);
        }

        .banner-hide {
            opacity: 0;
            transform: translateX(100%);
        }

        .banner-icon-wrapper {
            flex-shrink: 0;
            width: 32px;
            height: 32px;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 50%;
            font-size: 16px;
            font-weight: 700;
            background: rgba(255, 255, 255, 0.25);
            backdrop-filter: blur(8px);
            -webkit-backdrop-filter: blur(8px);
        }

        .banner-content {
            flex: 1;
            min-width: 0;
            display: flex;
            flex-direction: column;
            gap: 3px;
        }

        .banner-title {
            font-weight: 600;
            font-size: 14px;
            line-height: 1.4;
        }

        .banner-message {
            font-size: 13px;
            line-height: 1.5;
            opacity: 0.95;
            word-wrap: break-word;
        }

        .banner-close {
            flex-shrink: 0;
            width: 28px;
            height: 28px;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 50%;
            font-size: 18px;
            opacity: 0.7;
            transition: all 0.2s ease;
            cursor: pointer;
            margin-left: 4px;
            background: rgba(255, 255, 255, 0.1);
        }

        .banner-close:hover {
            opacity: 1;
            background: rgba(255, 255, 255, 0.2);
            transform: scale(1.1);
        }

        .banner-success {
            background: rgba(16, 185, 129, 0.15);
            border-color: rgba(16, 185, 129, 0.3);
            color: #10b981;
        }

        .banner-error {
            background: rgba(239, 68, 68, 0.15);
            border-color: rgba(239, 68, 68, 0.3);
            color: #ef4444;
        }

        .banner-info {
            background: rgba(59, 130, 246, 0.15);
            border-color: rgba(59, 130, 246, 0.3);
            color: #3b82f6;
        }

        .banner-warning {
            background: rgba(245, 158, 11, 0.15);
            border-color: rgba(245, 158, 11, 0.3);
            color: #f59e0b;
        }

        @media (max-width: 768px) {
            .banner-container {
                top: 10px;
                right: 10px;
                left: auto;
                max-width: calc(100vw - 20px);
            }

            .banner {
                min-width: auto;
                padding: 14px 16px;
            }

            .banner-title {
                font-size: 13px;
            }

            .banner-message {
                font-size: 12px;
            }
        }
    `;
    document.head.appendChild(style);
}

/**
 * Banner notification (top sliding banner)
 * @param {'success'|'error'|'info'|'warning'} type
 * @param {string} message
 * @param {number} [timeout]
 * @param {string} [title]
 */
function showMessage(type, message, timeout, title) {
    try {
        ensureBannerStyles();

        const duration = typeof timeout === 'number' ? timeout : 4000;
        let container = document.getElementById('banner-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'banner-container';
            container.className = 'banner-container';
            document.body.appendChild(container);
        }

        const banner = document.createElement('div');
        const t = ['success', 'error', 'info', 'warning'].includes(type) ? type : 'info';
        banner.className = `banner banner-${t}`;

        // Icon wrapper
        const iconWrapper = document.createElement('div');
        iconWrapper.className = 'banner-icon-wrapper';
        const iconMap = {
            success: '✓',
            error: '✕',
            info: 'ℹ',
            warning: '⚠'
        };
        iconWrapper.textContent = iconMap[t] || 'ℹ';

        // Content wrapper
        const content = document.createElement('div');
        content.className = 'banner-content';

        // Title (optional)
        if (title) {
            const titleEl = document.createElement('div');
            titleEl.className = 'banner-title';
            titleEl.textContent = title;
            content.appendChild(titleEl);
        }

        // Message
        const text = document.createElement('div');
        text.className = 'banner-message';
        text.textContent = message || '';
        content.appendChild(text);

        // Close button
        const closeBtn = document.createElement('div');
        closeBtn.className = 'banner-close';
        closeBtn.textContent = '×';
        closeBtn.setAttribute('aria-label', 'Close');

        banner.appendChild(iconWrapper);
        banner.appendChild(content);
        banner.appendChild(closeBtn);

        container.appendChild(banner);

        // Animate in
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                banner.classList.add('banner-visible');
            });
        });

        const hide = () => {
            banner.classList.remove('banner-visible');
            banner.classList.add('banner-hide');
            setTimeout(() => {
                if (banner.parentNode) banner.parentNode.removeChild(banner);
            }, 400);
        };

        const timer = setTimeout(hide, duration);

        // Click to dismiss
        banner.addEventListener('click', function() {
            clearTimeout(timer);
            hide();
        });

        closeBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            clearTimeout(timer);
            hide();
        });
    } catch (e) {
        console.warn('Banner failed:', e);
    }
}

/**
 * Show a success message
 * @param {string} message - The message to display
 * @param {string} [title] - Optional title
 */
function showSuccessMessage(message, title) {
    try { showMessage('success', message || 'Success', 4000, title); } catch (_) {}
}

/**
 * Show an error message
 * @param {string} message - The message to display
 * @param {string} [title] - Optional title
 */
function showErrorMessage(message, title) {
    // Parse and format common error messages
    let formattedMessage = message || 'Something went wrong';
    let errorTitle = title || 'Error';

    // Handle FCM-specific errors
    if (formattedMessage.includes('No FCM token registered')) {
        errorTitle = 'Notification Failed';
        formattedMessage = 'This user has not registered for push notifications yet.';
    } else if (formattedMessage.includes('FCM')) {
        errorTitle = 'Notification Error';
    } else if (formattedMessage.includes('permission')) {
        errorTitle = 'Permission Denied';
    } else if (formattedMessage.includes('authentication') || formattedMessage.includes('auth')) {
        errorTitle = 'Authentication Error';
    }

    try { showMessage('error', formattedMessage, 5000, errorTitle); } catch (_) {}
}

/**
 * Show an info message
 * @param {string} message - The message to display
 * @param {string} [title] - Optional title
 */
function showInfoMessage(message, title) {
    try { showMessage('info', message || '', 4000, title); } catch (_) {}
}

/**
 * Show a warning message
 * @param {string} message - The message to display
 * @param {string} [title] - Optional title
 */
function showWarningMessage(message, title) {
    try { showMessage('warning', message || '', 4000, title); } catch (_) {}
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

// ================= Validation Utilities =================
/**
 * Validation utilities for form inputs
 */
const ValidationUtils = {
    /**
     * Validate email format
     * @param {string} email - Email address to validate
     * @returns {boolean} True if valid, false otherwise
     */
    isValidEmail(email) {
        if (!email || typeof email !== 'string') return false;
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(email.trim());
    },

    /**
     * Validate date format (YYYY-MM-DD)
     * @param {string} date - Date string to validate
     * @returns {boolean} True if valid, false otherwise
     */
    isValidDate(date) {
        if (!date || typeof date !== 'string') return false;
        const dateRegex = /^\d{4}-\d{2}-\d{2}$/;
        if (!dateRegex.test(date)) return false;

        // Check if it's a valid date
        const d = new Date(date);
        return d instanceof Date && !isNaN(d.getTime());
    },

    /**
     * Validate date range (start date must be before or equal to end date)
     * @param {string} startDate - Start date string (YYYY-MM-DD)
     * @param {string} endDate - End date string (YYYY-MM-DD)
     * @returns {Object} { valid: boolean, message: string }
     */
    validateDateRange(startDate, endDate) {
        if (!startDate || !endDate) {
            return { valid: false, message: 'Both start date and end date are required' };
        }

        if (!this.isValidDate(startDate)) {
            return { valid: false, message: 'Invalid start date format. Use YYYY-MM-DD' };
        }

        if (!this.isValidDate(endDate)) {
            return { valid: false, message: 'Invalid end date format. Use YYYY-MM-DD' };
        }

        if (startDate > endDate) {
            return { valid: false, message: 'Start date must be before or equal to end date' };
        }

        return { valid: true, message: '' };
    },

    /**
     * Validate required field
     * @param {string} value - Value to validate
     * @param {string} fieldName - Name of the field for error message
     * @returns {Object} { valid: boolean, message: string }
     */
    validateRequired(value, fieldName) {
        if (!value || (typeof value === 'string' && value.trim() === '')) {
            return { valid: false, message: `${fieldName} is required` };
        }
        return { valid: true, message: '' };
    },

    /**
     * Validate array is not empty
     * @param {Array} array - Array to validate
     * @param {string} fieldName - Name of the field for error message
     * @returns {Object} { valid: boolean, message: string }
     */
    validateNotEmpty(array, fieldName) {
        if (!Array.isArray(array) || array.length === 0) {
            return { valid: false, message: `${fieldName} cannot be empty` };
        }
        return { valid: true, message: '' };
    },

    /**
     * Sanitize input to prevent XSS
     * @param {string} input - Input string to sanitize
     * @returns {string} Sanitized string
     */
    sanitizeInput(input) {
        if (typeof input !== 'string') return '';
        return input.replace(/[<>]/g, '');
    }
};

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
            showMessage('success', 'Token decoded');
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
    // Enhanced ReportsState object to manage users data, time range, and loading states
    const ReportsState = {
        // Data
        users: [],              // All user data
        filteredUsers: [],      // Users within selected time range
        
        // Time range
        timeRange: '30',        // Selected time range in days (default: 30)
        customStart: null,      // Custom range start date
        customEnd: null,        // Custom range end date
        
        // UI state
        lastUpdated: null,      // Timestamp of last data fetch
        isLoading: false,       // Loading state
        error: null,            // Error message if any
        
        // Charts
        chart: null,            // Chart.js instance for registration trend
        roleChart: null,        // Chart.js instance for role distribution
        activeChart: null,      // Chart.js instance for active/inactive
        initialized: false,     // Initialization flag
        
        // Performance optimization
        cacheExpiry: 5 * 60 * 1000,  // Cache duration: 5 minutes in milliseconds
        debounceTimer: null     // Timer for debouncing time range changes
    };

    // State update functions for managing report data
    const ReportsStateManager = {
        /**
         * Set users data and update filtered users based on current time range
         * @param {Array} users - Array of user objects
         */
        setUsers(users) {
            ReportsState.users = Array.isArray(users) ? users : [];
            ReportsState.lastUpdated = Date.now();
            this.updateFilteredUsers();
        },

        /**
         * Update filtered users based on current time range
         */
        updateFilteredUsers() {
            const days = parseInt(ReportsState.timeRange, 10) || 30;
            const cutoff = new Date();
            cutoff.setDate(cutoff.getDate() - days);
            cutoff.setHours(0, 0, 0, 0);

            ReportsState.filteredUsers = ReportsState.users.filter(user => {
                const createdAt = parseDateSafe(user.createdAt);
                return createdAt && createdAt >= cutoff;
            });
        },

        /**
         * Set time range and update filtered users
         * @param {string|number} days - Number of days for time range
         */
        setTimeRange(days) {
            ReportsState.timeRange = String(days);
            ReportsState.customStart = null;
            ReportsState.customEnd = null;
            this.updateFilteredUsers();
        },

        /**
         * Set custom date range
         * @param {string} startDate - Start date (ISO format)
         * @param {string} endDate - End date (ISO format)
         * @returns {boolean} True if valid, false if invalid
         */
        setCustomRange(startDate, endDate) {
            ReportsState.customStart = startDate;
            ReportsState.customEnd = endDate;
            ReportsState.timeRange = 'custom';
            
            // Filter users by custom range
            const start = parseDateSafe(startDate);
            const end = parseDateSafe(endDate);
            
            // Validate date range
            if (!start || !end) {
                this.setError('Invalid date format. Please select valid dates.');
                return false;
            }
            
            if (start > end) {
                this.setError('End date must be after start date.');
                return false;
            }
            
            // Check if date range is too far in the future
            const now = new Date();
            if (start > now) {
                this.setError('Start date cannot be in the future.');
                return false;
            }
            
            // Clear any previous errors
            this.clearError();
            
            if (start && end) {
                ReportsState.filteredUsers = ReportsState.users.filter(user => {
                    const createdAt = parseDateSafe(user.createdAt);
                    return createdAt && createdAt >= start && createdAt <= endOfDay(end);
                });
            }
            
            return true;
        },

        /**
         * Set loading state
         * @param {boolean} isLoading - Loading state
         */
        setLoading(isLoading) {
            ReportsState.isLoading = !!isLoading;
        },

        /**
         * Set error state
         * @param {string|null} error - Error message or null to clear
         */
        setError(error) {
            ReportsState.error = error;
        },

        /**
         * Clear error state
         */
        clearError() {
            ReportsState.error = null;
        },

        /**
         * Reset state to initial values
         */
        reset() {
            ReportsState.users = [];
            ReportsState.filteredUsers = [];
            ReportsState.timeRange = '30';
            ReportsState.customStart = null;
            ReportsState.customEnd = null;
            ReportsState.lastUpdated = null;
            ReportsState.isLoading = false;
            ReportsState.error = null;
            if (ReportsState.chart) {
                try {
                    ReportsState.chart.destroy();
                } catch (e) {
                    console.warn('Failed to destroy chart:', e);
                }
                ReportsState.chart = null;
            }
            ReportsState.initialized = false;
        },

        /**
         * Get current state snapshot
         * @returns {Object} Current state
         */
        getState() {
            return {
                users: ReportsState.users,
                filteredUsers: ReportsState.filteredUsers,
                timeRange: ReportsState.timeRange,
                customStart: ReportsState.customStart,
                customEnd: ReportsState.customEnd,
                lastUpdated: ReportsState.lastUpdated,
                isLoading: ReportsState.isLoading,
                error: ReportsState.error,
                initialized: ReportsState.initialized
            };
        }
    };

    // Legacy state reference for backward compatibility
    const state = ReportsState;

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

    // ================= KPI Calculation Functions =================
    
    /**
     * Count users created within the specified time range
     * @param {Array} users - Array of all users
     * @param {number} days - Number of days to look back
     * @returns {number} Count of users created in the time range
     */
    function countUsersInRange(users, days) {
        if (!Array.isArray(users) || users.length === 0) return 0;
        
        const cutoff = new Date();
        cutoff.setDate(cutoff.getDate() - days);
        cutoff.setHours(0, 0, 0, 0);
        
        return users.filter(user => {
            const createdAt = parseDateSafe(user.createdAt);
            return createdAt && createdAt >= cutoff;
        }).length;
    }

    /**
     * Calculate active user rate as a percentage
     * @param {Array} users - Array of users to analyze
     * @returns {string} Active rate as percentage string (e.g., "75%")
     */
    function calculateActiveRate(users) {
        if (!Array.isArray(users) || users.length === 0) return '0%';
        
        const activeCount = users.filter(u => u.isActive === true).length;
        const rate = Math.round((activeCount / users.length) * 100);
        
        return rate + '%';
    }

    /**
     * Calculate user growth rate comparing current period to previous period
     * @param {Array} allUsers - Array of all users
     * @param {number} days - Number of days for current period
     * @returns {string} Growth rate as percentage string with +/- sign (e.g., "+25%", "-10%")
     */
    function calculateGrowthRate(allUsers, days) {
        if (!Array.isArray(allUsers) || allUsers.length === 0) return '0%';
        
        const now = new Date();
        
        // Current period cutoff
        const currentCutoff = new Date(now);
        currentCutoff.setDate(currentCutoff.getDate() - days);
        currentCutoff.setHours(0, 0, 0, 0);
        
        // Previous period cutoff (same duration, but earlier)
        const previousCutoff = new Date(currentCutoff);
        previousCutoff.setDate(previousCutoff.getDate() - days);
        previousCutoff.setHours(0, 0, 0, 0);
        
        // Count users in current period
        const currentCount = allUsers.filter(user => {
            const createdAt = parseDateSafe(user.createdAt);
            return createdAt && createdAt >= currentCutoff && createdAt < now;
        }).length;
        
        // Count users in previous period
        const previousCount = allUsers.filter(user => {
            const createdAt = parseDateSafe(user.createdAt);
            return createdAt && createdAt >= previousCutoff && createdAt < currentCutoff;
        }).length;
        
        // Calculate growth rate
        if (previousCount === 0) {
            // If no users in previous period, show current count as growth
            return currentCount > 0 ? '+100%' : '0%';
        }
        
        const growthRate = Math.round(((currentCount - previousCount) / previousCount) * 100);
        const sign = growthRate > 0 ? '+' : '';
        
        return sign + growthRate + '%';
    }

    /**
     * Calculate user retention rate
     * Retention is defined as: users created before the period who are still active
     * @param {Array} allUsers - Array of all users
     * @param {number} days - Number of days for the period
     * @returns {string} Retention rate as percentage string (e.g., "85%")
     */
    function calculateRetentionRate(allUsers, days) {
        if (!Array.isArray(allUsers) || allUsers.length === 0) return '0%';
        
        const cutoff = new Date();
        cutoff.setDate(cutoff.getDate() - days);
        cutoff.setHours(0, 0, 0, 0);
        
        // Users created before the period (existing users at start of period)
        const existingUsers = allUsers.filter(user => {
            const createdAt = parseDateSafe(user.createdAt);
            return createdAt && createdAt < cutoff;
        });
        
        if (existingUsers.length === 0) return '0%';
        
        // Count how many of those existing users are still active
        const retainedUsers = existingUsers.filter(u => u.isActive === true).length;
        
        const retentionRate = Math.round((retainedUsers / existingUsers.length) * 100);
        
        return retentionRate + '%';
    }

    /**
     * Calculate all KPIs for the reports dashboard
     * @param {Array} allUsers - Array of all users
     * @param {Array} filteredUsers - Array of users within the selected time range
     * @param {number} days - Number of days for the time range
     * @returns {Object} Object containing all calculated KPIs
     */
    function calculateKPIs(allUsers, filteredUsers, days) {
        const totalUsers = Array.isArray(allUsers) ? allUsers.length : 0;
        const newUsers = countUsersInRange(allUsers, days);
        const activeRate = calculateActiveRate(allUsers);
        const growthRate = calculateGrowthRate(allUsers, days);
        const retentionRate = calculateRetentionRate(allUsers, days);
        
        // Additional metrics
        const premiumUsers = Array.isArray(allUsers) 
            ? allUsers.filter(u => u.isPremium === true).length 
            : 0;
        
        const adminUsers = Array.isArray(allUsers)
            ? allUsers.filter(u => (u.role || '').toUpperCase() === 'ADMIN').length
            : 0;
        
        const moderators = Array.isArray(allUsers)
            ? allUsers.filter(u => (u.role || '').toUpperCase() === 'MODERATOR').length
            : 0;
        
        const activeUsers = Array.isArray(allUsers)
            ? allUsers.filter(u => u.isActive === true).length
            : 0;
        
        const inactiveUsers = totalUsers - activeUsers;
        
        return {
            totalUsers,
            newUsers,
            activeRate,
            premiumUsers,
            adminUsers,
            moderators,
            growthRate,
            retentionRate,
            activeUsers,
            inactiveUsers
        };
    }

    // ================= End KPI Calculation Functions =================

    function updateKpis(users, days, series){
        try {
            // Use the comprehensive calculateKPIs function
            const kpis = calculateKPIs(ReportsState.users, users, days);
            
            // Update DOM elements with calculated KPIs
            const elNew = q('kpi-new-users');
            const elAct = q('kpi-active-rate');
            const elAdm = q('kpi-admins');
            const elPre = q('kpi-premium');
            const elGrowth = q('kpi-growth-rate');
            const elRetention = q('kpi-retention-rate');
            
            if (elNew) elNew.textContent = String(kpis.newUsers);
            if (elAct) elAct.textContent = kpis.activeRate;
            if (elAdm) elAdm.textContent = String(kpis.adminUsers);
            if (elPre) elPre.textContent = String(kpis.premiumUsers);
            if (elGrowth) elGrowth.textContent = kpis.growthRate;
            if (elRetention) elRetention.textContent = kpis.retentionRate;
        } catch(e) { console.warn('Failed to update KPIs', e); }
    }

    function lineColor(){ return '#6366f1'; }
    function gridColor(){ return (cssVar('--card-border') || 'rgba(99,102,241,0.2)'); }
    function tickColor(){ return (cssVar('--text-secondary') || '#9ca3af'); }

    function destroyChart(){ 
        try { 
            if (ReportsState.chart) { 
                ReportsState.chart.destroy(); 
                ReportsState.chart = null; 
            }
            if (ReportsState.roleChart) {
                ReportsState.roleChart.destroy();
                ReportsState.roleChart = null;
            }
            if (ReportsState.activeChart) {
                ReportsState.activeChart.destroy();
                ReportsState.activeChart = null;
            }
        } catch(e) {
            console.warn('Failed to destroy charts:', e);
        }
    }

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
        
        // Performance optimization: Detect large datasets (>1000 users)
        const isLargeDataset = ReportsState.users.length > 1000;
        const dataPointCount = series.data.length;
        
        // Disable glow effect for large datasets to improve performance
        const glowPlugin = !isLargeDataset ? {
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
        } : null;

        // gradient fill for a subtle modern look
        let gradient;
        try {
            gradient = ctx.createLinearGradient(0, 0, 0, canvas.height || 260);
            gradient.addColorStop(0, 'rgba(99, 102, 241, 0.22)');
            gradient.addColorStop(1, 'rgba(99, 102, 241, 0.02)');
        } catch(_) {
            gradient = 'rgba(99, 102, 241, 0.12)';
        }

        // Performance optimization: Reduce animation duration for large datasets
        const animationDuration = isLargeDataset ? 0 : 700;
        
        // Performance optimization: Simplify line rendering for large datasets
        const tension = isLargeDataset ? 0 : 0.6;
        const borderWidth = isLargeDataset ? 2 : 2.5;
        
        console.log(`Rendering chart with ${dataPointCount} data points (${ReportsState.users.length} total users). Large dataset optimization: ${isLargeDataset ? 'enabled' : 'disabled'}`);

        const plugins = glowPlugin ? [glowPlugin] : [];

        ReportsState.chart = new Chart(ctx, {
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
                    cubicInterpolationMode: isLargeDataset ? 'default' : 'monotone',
                    tension: tension,
                    borderWidth: borderWidth,
                    pointRadius: 0,
                    pointHoverRadius: isLargeDataset ? 0 : 3
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: animationDuration, easing: 'easeOutCubic' },
                interaction: { 
                    mode: 'index', 
                    intersect: false,
                    // Make touch-friendly
                    axis: 'x'
                },
                elements: {
                    line: { borderJoinStyle: 'round', capBezierPoints: true },
                    point: { 
                        hitRadius: isLargeDataset ? 5 : 10,
                        // Larger hit radius for touch devices
                        hoverRadius: isLargeDataset ? 0 : 5
                    }
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
                        intersect: false,
                        // Touch-friendly tooltip
                        position: 'nearest'
                    }
                },
                scales: {
                    x: {
                        grid: { display: false, drawBorder: false },
                        ticks: { 
                            color: tickColor(), 
                            maxTicksLimit: window.innerWidth < 768 ? 5 : 8, 
                            maxRotation: 0,
                            autoSkip: true,
                            autoSkipPadding: 10
                        }
                    },
                    y: {
                        display: false,
                        beginAtZero: true,
                        grid: { display: false, drawBorder: false },
                        ticks: { display: false }
                    }
                }
            },
            plugins: plugins
        });
    }

    /**
     * Show error banner for API failures
     * @param {string} message - Error message to display
     * @param {boolean} showRetry - Whether to show retry button
     */
    function showErrorBanner(message, showRetry) {
        const errorBanner = q('reports-error-banner');
        
        if (errorBanner) {
            const retryButton = showRetry ? 
                '<button id="reports-error-retry-btn" class="btn btn-secondary" style="margin-left: 1rem;">Retry</button>' : 
                '';
            
            errorBanner.innerHTML = `
                <div style="display: flex; align-items: center; justify-content: space-between; padding: 1rem; background: var(--error-bg, #fee); border: 1px solid var(--error-border, #fcc); border-radius: 8px; margin-bottom: 1rem;">
                    <div style="display: flex; align-items: center; gap: 0.75rem;">
                        <span style="font-size: 1.5rem;">⚠️</span>
                        <div>
                            <div style="font-weight: 600; color: var(--error-color, #c00); margin-bottom: 0.25rem;">Error</div>
                            <div style="color: var(--text-color);">${escapeHtml(message)}</div>
                        </div>
                    </div>
                    <div style="display: flex; gap: 0.5rem;">
                        ${retryButton}
                        <button id="reports-error-dismiss-btn" class="btn btn-secondary">Dismiss</button>
                    </div>
                </div>
            `;
            
            errorBanner.style.display = 'block';
            
            // Bind dismiss button
            setTimeout(() => {
                const dismissBtn = q('reports-error-dismiss-btn');
                if (dismissBtn) {
                    dismissBtn.addEventListener('click', function() {
                        hideErrorBanner();
                        ReportsStateManager.clearError();
                    });
                }
                
                // Bind retry button if shown
                if (showRetry) {
                    const retryBtn = q('reports-error-retry-btn');
                    if (retryBtn) {
                        retryBtn.addEventListener('click', function() {
                            hideErrorBanner();
                            refreshReports();
                        });
                    }
                }
            }, 0);
        }
    }

    /**
     * Hide error banner
     */
    function hideErrorBanner() {
        const errorBanner = q('reports-error-banner');
        if (errorBanner) {
            errorBanner.style.display = 'none';
            errorBanner.innerHTML = '';
        }
    }

    /**
     * Display validation error for invalid date ranges
     * @param {string} message - Validation error message
     */
    function showValidationError(message) {
        try {
            showMessage('error', message);
        } catch (e) {
            console.warn('Failed to show validation error toast:', e);
        }
        
        // Also show in error banner
        showErrorBanner(message, false);
    }

    /**
     * Show empty state message
     * @param {string} message - The message to display
     * @param {string} type - Type of empty state: 'no-data' or 'no-results'
     */
    function showEmptyState(message, type) {
        const empty = q('reports-empty');
        const chartsContainer = q('reports-charts-container');
        const kpisContainer = q('reports-kpis-container');
        
        if (empty) {
            empty.textContent = message;
            empty.classList.remove('hidden');
            empty.classList.add('visible');
            
            // Add icon based on type
            if (type === 'no-data') {
                empty.innerHTML = `
                    <div style="text-align: center; padding: 3rem 1rem;">
                        <div style="font-size: 3rem; color: var(--text-secondary); margin-bottom: 1rem;">📊</div>
                        <div style="font-size: 1.25rem; font-weight: 600; color: var(--text-color); margin-bottom: 0.5rem;">No Data Available</div>
                        <div style="color: var(--text-secondary);">${message}</div>
                    </div>
                `;
            } else if (type === 'no-results') {
                empty.innerHTML = `
                    <div style="text-align: center; padding: 3rem 1rem;">
                        <div style="font-size: 3rem; color: var(--text-secondary); margin-bottom: 1rem;">🔍</div>
                        <div style="font-size: 1.25rem; font-weight: 600; color: var(--text-color); margin-bottom: 0.5rem;">No Results Found</div>
                        <div style="color: var(--text-secondary);">${message}</div>
                        <button id="reports-clear-filter-btn" class="btn btn-primary" style="margin-top: 1rem;">Clear Filters</button>
                    </div>
                `;
                
                // Bind clear filter button
                setTimeout(() => {
                    const clearBtn = q('reports-clear-filter-btn');
                    if (clearBtn) {
                        clearBtn.addEventListener('click', function() {
                            const rangeSelect = q('reports-range');
                            if (rangeSelect) {
                                rangeSelect.value = '30';
                                ReportsStateManager.setTimeRange('30');
                                render();
                            }
                        });
                    }
                }, 0);
            }
        }
        
        // Hide charts and KPIs when showing empty state
        if (chartsContainer) chartsContainer.style.display = 'none';
        if (kpisContainer) kpisContainer.style.display = 'none';
    }

    /**
     * Hide empty state message
     */
    function hideEmptyState() {
        const empty = q('reports-empty');
        const chartsContainer = q('reports-charts-container');
        const kpisContainer = q('reports-kpis-container');
        
        if (empty) {
            empty.classList.add('hidden');
            empty.classList.remove('visible');
        }
        
        // Show charts and KPIs when hiding empty state
        if (chartsContainer) chartsContainer.style.display = 'block';
        if (kpisContainer) kpisContainer.style.display = 'grid';
    }

    function render(){
        const days = getRangeDays();
        const series = aggregateDaily(ReportsState.filteredUsers, days);
        
        // Check for errors first
        if (ReportsState.error) {
            showErrorBanner(ReportsState.error, true);
            // Don't return - still try to render what we have
        } else {
            hideErrorBanner();
        }
        
        // Check for empty states
        if (!ReportsState.users || ReportsState.users.length === 0) {
            // No users exist at all
            showEmptyState('No users have been registered yet. Users will appear here once they sign up.', 'no-data');
            return;
        }
        
        if (!ReportsState.filteredUsers || ReportsState.filteredUsers.length === 0) {
            // No users in the selected time range
            const rangeText = days === 'custom' ? 'the selected date range' : `the last ${days} days`;
            showEmptyState(`No users found in ${rangeText}. Try selecting a different time range.`, 'no-results');
            return;
        }
        
        // Hide empty state and show data
        hideEmptyState();
        
        updateKpis(ReportsState.filteredUsers, days, series);
        renderChart(series);
        renderRoleChart();
        renderActiveInactiveChart();
    }

    // ================= Additional Chart Functions =================

    /**
     * Chart initialization configuration with theme colors
     * Returns default chart configuration that adapts to light/dark theme
     */
    function getChartDefaults() {
        return {
            responsive: true,
            maintainAspectRatio: false,
            animation: {
                duration: 700,
                easing: 'easeOutCubic'
            },
            plugins: {
                legend: {
                    display: true,
                    position: 'bottom',
                    labels: {
                        color: tickColor(),
                        padding: 15,
                        font: {
                            size: 12,
                            family: "'Space Grotesk', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
                        },
                        usePointStyle: true,
                        pointStyle: 'circle'
                    }
                },
                tooltip: {
                    enabled: true,
                    backgroundColor: 'rgba(17, 24, 39, 0.95)',
                    titleColor: '#ffffff',
                    bodyColor: '#ffffff',
                    borderColor: 'rgba(99, 102, 241, 0.5)',
                    borderWidth: 1,
                    padding: 12,
                    displayColors: true,
                    boxPadding: 6,
                    cornerRadius: 8
                }
            }
        };
    }

    /**
     * Get color palette for charts based on current theme
     */
    function getChartColors() {
        return {
            primary: '#6366f1',
            success: '#10b981',
            warning: '#f59e0b',
            error: '#ef4444',
            info: '#3b82f6',
            purple: '#8b5cf6',
            pink: '#ec4899',
            // Role colors
            user: '#3b82f6',
            moderator: '#f59e0b',
            admin: '#ef4444',
            // Status colors
            active: '#10b981',
            inactive: '#6b7280'
        };
    }

    /**
     * Render Users by Role pie chart
     * Aggregates users by role (USER, MODERATOR, ADMIN)
     * Renders pie chart with percentage labels and color coding
     */
    function renderRoleChart() {
        const canvas = q('reports-role-chart');
        if (!canvas) return;

        if (typeof Chart === 'undefined') {
            console.warn('Chart.js not available for role chart');
            return;
        }

        // Destroy existing chart if any
        if (ReportsState.roleChart) {
            try {
                ReportsState.roleChart.destroy();
                ReportsState.roleChart = null;
            } catch (e) {
                console.warn('Failed to destroy role chart:', e);
            }
        }

        // Aggregate users by role
        const users = ReportsState.users || [];
        const roleCounts = {
            USER: 0,
            MODERATOR: 0,
            ADMIN: 0
        };

        users.forEach(user => {
            const role = (user.role || 'USER').toUpperCase();
            if (roleCounts.hasOwnProperty(role)) {
                roleCounts[role]++;
            } else {
                roleCounts.USER++; // Default to USER if unknown role
            }
        });

        const colors = getChartColors();
        const chartData = {
            labels: ['Users', 'Moderators', 'Admins'],
            datasets: [{
                data: [roleCounts.USER, roleCounts.MODERATOR, roleCounts.ADMIN],
                backgroundColor: [
                    colors.user,
                    colors.moderator,
                    colors.admin
                ],
                borderColor: cssVar('--card-bg') || '#ffffff',
                borderWidth: 2,
                hoverOffset: 8
            }]
        };

        const ctx = canvas.getContext('2d');
        const defaults = getChartDefaults();
        
        // Performance optimization: Detect large datasets
        const isLargeDataset = ReportsState.users.length > 1000;
        
        // Disable animations for large datasets
        const animationConfig = isLargeDataset ? { duration: 0 } : defaults.animation;

        ReportsState.roleChart = new Chart(ctx, {
            type: 'pie',
            data: chartData,
            options: {
                ...defaults,
                animation: animationConfig,
                responsive: true,
                maintainAspectRatio: true,
                aspectRatio: window.innerWidth < 768 ? 1.5 : 2,
                plugins: {
                    ...defaults.plugins,
                    legend: {
                        ...defaults.plugins.legend,
                        position: window.innerWidth < 768 ? 'bottom' : 'right',
                        labels: {
                            ...defaults.plugins.legend.labels,
                            padding: window.innerWidth < 768 ? 10 : 15,
                            boxWidth: window.innerWidth < 768 ? 12 : 15
                        }
                    },
                    tooltip: {
                        ...defaults.plugins.tooltip,
                        callbacks: {
                            label: function(context) {
                                const label = context.label || '';
                                const value = context.parsed || 0;
                                const total = context.dataset.data.reduce((a, b) => a + b, 0);
                                const percentage = total > 0 ? Math.round((value / total) * 100) : 0;
                                return `${label}: ${value} (${percentage}%)`;
                            }
                        }
                    }
                },
                // Touch-friendly interactions
                interaction: {
                    mode: 'point',
                    intersect: true
                }
            }
        });
    }

    /**
     * Render Active vs Inactive donut chart
     * Calculates active and inactive user counts
     * Renders donut chart with center text showing total
     * Uses success/error colors for active/inactive
     */
    function renderActiveInactiveChart() {
        const canvas = q('reports-active-chart');
        if (!canvas) return;

        if (typeof Chart === 'undefined') {
            console.warn('Chart.js not available for active/inactive chart');
            return;
        }

        // Destroy existing chart if any
        if (ReportsState.activeChart) {
            try {
                ReportsState.activeChart.destroy();
                ReportsState.activeChart = null;
            } catch (e) {
                console.warn('Failed to destroy active chart:', e);
            }
        }

        // Calculate active and inactive counts
        const users = ReportsState.users || [];
        const activeCount = users.filter(u => u.isActive === true).length;
        const inactiveCount = users.length - activeCount;

        const colors = getChartColors();
        const chartData = {
            labels: ['Active', 'Inactive'],
            datasets: [{
                data: [activeCount, inactiveCount],
                backgroundColor: [
                    colors.active,
                    colors.inactive
                ],
                borderColor: cssVar('--card-bg') || '#ffffff',
                borderWidth: 2,
                hoverOffset: 8
            }]
        };

        const ctx = canvas.getContext('2d');
        const defaults = getChartDefaults();
        
        // Performance optimization: Detect large datasets
        const isLargeDataset = ReportsState.users.length > 1000;
        
        // Disable animations for large datasets
        const animationConfig = isLargeDataset ? { duration: 0 } : defaults.animation;

        // Plugin to draw center text
        const centerTextPlugin = {
            id: 'centerText',
            beforeDraw: function(chart) {
                if (chart.config.type !== 'doughnut') return;
                
                const width = chart.width;
                const height = chart.height;
                const ctx = chart.ctx;
                
                ctx.restore();
                const fontSize = (height / 114).toFixed(2);
                ctx.font = `bold ${fontSize}em 'Space Grotesk', sans-serif`;
                ctx.textBaseline = 'middle';
                ctx.fillStyle = tickColor();
                
                const total = users.length;
                const text = String(total);
                const textX = Math.round((width - ctx.measureText(text).width) / 2);
                const textY = height / 2;
                
                ctx.fillText(text, textX, textY);
                
                // Draw "Total Users" label below
                ctx.font = `${(fontSize * 0.4).toFixed(2)}em 'Space Grotesk', sans-serif`;
                ctx.fillStyle = cssVar('--text-secondary') || '#9ca3af';
                const labelText = 'Total Users';
                const labelX = Math.round((width - ctx.measureText(labelText).width) / 2);
                const labelY = textY + (fontSize * 20);
                ctx.fillText(labelText, labelX, labelY);
                
                ctx.save();
            }
        };

        ReportsState.activeChart = new Chart(ctx, {
            type: 'doughnut',
            data: chartData,
            options: {
                ...defaults,
                animation: animationConfig,
                responsive: true,
                maintainAspectRatio: true,
                aspectRatio: window.innerWidth < 768 ? 1.5 : 2,
                cutout: '70%',
                plugins: {
                    ...defaults.plugins,
                    legend: {
                        ...defaults.plugins.legend,
                        position: 'bottom',
                        labels: {
                            ...defaults.plugins.legend.labels,
                            padding: window.innerWidth < 768 ? 10 : 15,
                            boxWidth: window.innerWidth < 768 ? 12 : 15
                        }
                    },
                    tooltip: {
                        ...defaults.plugins.tooltip,
                        callbacks: {
                            label: function(context) {
                                const label = context.label || '';
                                const value = context.parsed || 0;
                                const total = context.dataset.data.reduce((a, b) => a + b, 0);
                                const percentage = total > 0 ? Math.round((value / total) * 100) : 0;
                                return `${label}: ${value} (${percentage}%)`;
                            }
                        }
                    }
                },
                // Touch-friendly interactions
                interaction: {
                    mode: 'point',
                    intersect: true
                }
            },
            plugins: [centerTextPlugin]
        });
    }

    // ================= End Additional Chart Functions =================

    /**
     * Fetch all users with pagination support
     * Implements loop to fetch all user pages from `/admin/users` endpoint
     * Handles API errors and displays appropriate error messages
     * Stores fetched data in ReportsState
     * Implements data caching for 5 minutes to reduce API calls
     * @param {boolean} forceRefresh - Force refresh even if cache is valid
     * @returns {Promise<Array>} Promise resolving to array of all users
     */
    async function fetchAllUsers(forceRefresh = false) {
        if (!window.UserRoute || typeof window.UserRoute.listUsers !== 'function') {
            return Promise.reject(new Error('Users API unavailable'));
        }
        
        // Check if we have cached data that's still valid (within 5 minutes)
        if (!forceRefresh && ReportsState.users.length > 0 && ReportsState.lastUpdated) {
            const cacheAge = Date.now() - ReportsState.lastUpdated;
            if (cacheAge < ReportsState.cacheExpiry) {
                console.log(`Using cached data (age: ${Math.round(cacheAge / 1000)}s)`);
                return Promise.resolve(ReportsState.users);
            } else {
                console.log(`Cache expired (age: ${Math.round(cacheAge / 1000)}s), fetching fresh data`);
            }
        }
        
        ReportsStateManager.setLoading(true);
        ReportsStateManager.clearError();
        
        try {
            let allUsers = [];
            let page = 1;
            let hasMore = true;
            const pageSize = 100; // Fetch 100 users per page
            
            // Loop through all pages to fetch all users
            while (hasMore) {
                try {
                    const { users, pagination } = await window.UserRoute.listUsers(page, pageSize);
                    
                    if (Array.isArray(users) && users.length > 0) {
                        allUsers = allUsers.concat(users);
                    }
                    
                    // Check if there are more pages
                    if (pagination && pagination.totalPages) {
                        hasMore = page < pagination.totalPages;
                        page++;
                    } else {
                        // If no pagination info or no more users, stop
                        hasMore = false;
                    }
                    
                    // Safety check: prevent infinite loops
                    if (page > 1000) {
                        console.warn('Reached maximum page limit (1000), stopping pagination');
                        hasMore = false;
                    }
                } catch (pageError) {
                    console.error(`Error fetching page ${page}:`, pageError);
                    // If a page fails, log it but continue with what we have
                    hasMore = false;
                }
            }
            
            // Store fetched data in ReportsState
            ReportsStateManager.setUsers(allUsers);
            ReportsStateManager.setLoading(false);
            
            console.log(`Fetched ${allUsers.length} users across ${page - 1} pages`);
            return allUsers;
            
        } catch (error) {
            ReportsStateManager.setLoading(false);
            const errorMessage = error.message || 'Failed to load users';
            ReportsStateManager.setError(errorMessage);
            
            // Display error message to user
            try {
                showMessage('error', errorMessage);
            } catch (e) {
                console.warn('Failed to show error toast:', e);
            }
            
            throw error;
        }
    }

    /**
     * Legacy fetchUsers function for backward compatibility
     * Now calls fetchAllUsers internally
     */
    function fetchUsers() {
        return fetchAllUsers();
    }

    /**
     * Filter users by date range based on createdAt timestamp
     * Supports preset time ranges (7, 30, 90, 180, 365 days) and custom date range filtering
     * @param {Array} users - Array of user objects
     * @param {number|string} timeRange - Number of days or 'custom' for custom range
     * @param {string} customStart - Custom start date (ISO format) - optional
     * @param {string} customEnd - Custom end date (ISO format) - optional
     * @returns {Array} Filtered array of users
     */
    function filterUsersByDateRange(users, timeRange, customStart, customEnd) {
        if (!Array.isArray(users)) {
            return [];
        }

        // Handle custom date range
        if (timeRange === 'custom' && customStart && customEnd) {
            const start = parseDateSafe(customStart);
            const end = parseDateSafe(customEnd);
            
            if (!start || !end) {
                console.warn('Invalid custom date range provided');
                return users;
            }
            
            // Set end date to end of day for inclusive filtering
            const endOfDayDate = endOfDay(end);
            
            return users.filter(user => {
                const createdAt = parseDateSafe(user.createdAt);
                return createdAt && createdAt >= start && createdAt <= endOfDayDate;
            });
        }

        // Handle preset time ranges (7, 30, 90, 180, 365 days)
        const days = parseInt(timeRange, 10);
        if (isNaN(days) || days <= 0) {
            console.warn('Invalid time range provided, returning all users');
            return users;
        }

        // Calculate cutoff date
        const cutoff = new Date();
        cutoff.setDate(cutoff.getDate() - days);
        cutoff.setHours(0, 0, 0, 0);

        return users.filter(user => {
            const createdAt = parseDateSafe(user.createdAt);
            return createdAt && createdAt >= cutoff;
        });
    }

    /**
     * Count users created within a specific time range
     * @param {Array} users - Array of user objects
     * @param {number} days - Number of days to look back
     * @returns {number} Count of users created in the range
     */
    function countUsersInRange(users, days) {
        if (!Array.isArray(users) || isNaN(days) || days <= 0) {
            return 0;
        }

        const cutoff = new Date();
        cutoff.setDate(cutoff.getDate() - days);
        cutoff.setHours(0, 0, 0, 0);

        return users.filter(user => {
            const createdAt = parseDateSafe(user.createdAt);
            return createdAt && createdAt >= cutoff;
        }).length;
    }

    /**
     * Calculate active user rate as a percentage
     * @param {Array} users - Array of user objects
     * @returns {number} Active rate as a percentage (0-100)
     */
    function calculateActiveRate(users) {
        if (!Array.isArray(users) || users.length === 0) {
            return 0;
        }

        const activeCount = users.filter(user => user.isActive === true).length;
        return Math.round((activeCount / users.length) * 100);
    }

    /**
     * Calculate user growth rate comparing two time periods
     * @param {Array} users - Array of user objects
     * @param {number} days - Number of days for current period
     * @returns {string} Growth rate as a percentage string (e.g., "+15%", "-5%")
     */
    function calculateGrowthRate(users, days) {
        if (!Array.isArray(users) || isNaN(days) || days <= 0) {
            return '0%';
        }

        // Current period: last N days
        const currentPeriodStart = new Date();
        currentPeriodStart.setDate(currentPeriodStart.getDate() - days);
        currentPeriodStart.setHours(0, 0, 0, 0);

        // Previous period: N days before that
        const previousPeriodStart = new Date(currentPeriodStart);
        previousPeriodStart.setDate(previousPeriodStart.getDate() - days);

        const currentPeriodUsers = users.filter(user => {
            const createdAt = parseDateSafe(user.createdAt);
            return createdAt && createdAt >= currentPeriodStart;
        }).length;

        const previousPeriodUsers = users.filter(user => {
            const createdAt = parseDateSafe(user.createdAt);
            return createdAt && createdAt >= previousPeriodStart && createdAt < currentPeriodStart;
        }).length;

        if (previousPeriodUsers === 0) {
            return currentPeriodUsers > 0 ? '+100%' : '0%';
        }

        const growthRate = ((currentPeriodUsers - previousPeriodUsers) / previousPeriodUsers) * 100;
        const sign = growthRate > 0 ? '+' : '';
        return sign + Math.round(growthRate) + '%';
    }

    /**
     * Calculate user retention rate
     * Retention rate = (users still active from N days ago) / (total users from N days ago)
     * @param {Array} users - Array of user objects
     * @param {number} days - Number of days to look back
     * @returns {string} Retention rate as a percentage string (e.g., "85%")
     */
    function calculateRetentionRate(users, days) {
        if (!Array.isArray(users) || isNaN(days) || days <= 0) {
            return '0%';
        }

        // Get users created N or more days ago
        const cutoff = new Date();
        cutoff.setDate(cutoff.getDate() - days);
        cutoff.setHours(0, 0, 0, 0);

        const oldUsers = users.filter(user => {
            const createdAt = parseDateSafe(user.createdAt);
            return createdAt && createdAt < cutoff;
        });

        if (oldUsers.length === 0) {
            return '0%';
        }

        // Count how many of those old users are still active
        const activeOldUsers = oldUsers.filter(user => user.isActive === true).length;

        const retentionRate = (activeOldUsers / oldUsers.length) * 100;
        return Math.round(retentionRate) + '%';
    }

    /**
     * Calculate all KPI metrics for the reports dashboard
     * @param {Array} users - Array of user objects
     * @param {number} days - Number of days for the time range
     * @returns {Object} Object containing all KPI metrics
     */
    function calculateKPIs(users, days) {
        if (!Array.isArray(users)) {
            return {
                totalUsers: 0,
                newUsers: 0,
                activeRate: '0%',
                premiumUsers: 0,
                adminUsers: 0,
                moderators: 0,
                growthRate: '0%',
                retentionRate: '0%'
            };
        }

        return {
            totalUsers: users.length,
            newUsers: countUsersInRange(users, days),
            activeRate: calculateActiveRate(users) + '%',
            premiumUsers: users.filter(u => u.isPremium === true).length,
            adminUsers: users.filter(u => (u.role || '').toUpperCase() === 'ADMIN').length,
            moderators: users.filter(u => (u.role || '').toUpperCase() === 'MODERATOR').length,
            growthRate: calculateGrowthRate(users, days),
            retentionRate: calculateRetentionRate(users, days)
        };
    }

    /**
     * Debounced render function to avoid excessive re-renders
     * Delays rendering by 300ms after the last change
     */
    function debouncedRender() {
        // Clear existing timer
        if (ReportsState.debounceTimer) {
            clearTimeout(ReportsState.debounceTimer);
        }
        
        // Set new timer
        ReportsState.debounceTimer = setTimeout(() => {
            render();
            ReportsState.debounceTimer = null;
        }, 300); // 300ms debounce delay
    }

    /**
     * Bind time range selector and custom date range inputs
     * Implements session persistence - stores selected time range in sessionStorage
     * Restores time range on page reload
     * Debounces time range changes by 300ms to avoid excessive re-renders
     */
    function bindRange(){
        const sel = q('reports-range');
        if (sel && !sel.dataset.bound) {
            sel.addEventListener('change', function() {
                const days = sel.value;
                
                // Clear any previous errors
                ReportsStateManager.clearError();
                hideErrorBanner();
                
                if (days === 'custom') {
                    // Show custom date range inputs
                    const customDateRange = q('custom-date-range');
                    if (customDateRange) customDateRange.style.display = 'flex';
                    
                    // Show custom date inputs if they exist
                    const customInputs = q('reports-custom-dates');
                    if (customInputs) customInputs.style.display = 'block';
                } else {
                    // Hide custom date inputs
                    const customDateRange = q('custom-date-range');
                    if (customDateRange) customDateRange.style.display = 'none';
                    
                    const customInputs = q('reports-custom-dates');
                    if (customInputs) customInputs.style.display = 'none';
                    
                    // Apply preset time range
                    ReportsStateManager.setTimeRange(days);
                    
                    // Persist to sessionStorage
                    persistTimeRange();
                    
                    // Use debounced render to avoid excessive re-renders
                    debouncedRender();
                }
            });
            sel.dataset.bound = 'true';
        }
        
        // Bind custom date range inputs
        const startInput = q('reports-start-date');
        const endInput = q('reports-end-date');
        const applyBtn = q('apply-custom-range');
        
        if (applyBtn && !applyBtn.dataset.bound) {
            applyBtn.addEventListener('click', function() {
                const startDate = startInput ? startInput.value : '';
                const endDate = endInput ? endInput.value : '';
                
                if (!startDate || !endDate) {
                    showValidationError('Please select both start and end dates.');
                    return;
                }
                
                // Validate and apply custom range
                const isValid = ReportsStateManager.setCustomRange(startDate, endDate);
                
                if (isValid) {
                    // Persist to sessionStorage
                    persistTimeRange();
                    
                    // Use debounced render to avoid excessive re-renders
                    debouncedRender();
                } else {
                    // Error message already set by setCustomRange
                    showValidationError(ReportsState.error || 'Invalid date range.');
                }
            });
            applyBtn.dataset.bound = 'true';
        }
    }

    /**
     * Bind theme reactivity to update charts when theme changes
     * Ensures charts adapt to light/dark mode transitions
     * Uses CSS variables for consistent theming across dashboard
     */
    function bindThemeReactivity(){
        // Observe html class changes to refresh chart colors when theme switches
        try {
            const html = document.documentElement;
            const obs = new MutationObserver(() => { 
                if (ReportsState.initialized && ReportsState.chart) { 
                    console.log('Theme changed, re-rendering charts with new colors');
                    render(); 
                } 
            });
            obs.observe(html, { attributes: true, attributeFilter: ['class', 'data-theme'] });
        } catch(e) {
            console.warn('Failed to observe theme changes:', e);
        }
        
        // Also listen to theme toggle button directly
        const toggle = document.getElementById('theme-toggle');
        if (toggle && !toggle.dataset.reportBound) {
            toggle.addEventListener('change', () => { 
                if (ReportsState.initialized && ReportsState.chart) { 
                    console.log('Theme toggle changed, re-rendering charts');
                    // Small delay to allow CSS variables to update
                    setTimeout(() => render(), 50);
                } 
            });
            toggle.dataset.reportBound = 'true';
        }
        
        // Listen for custom theme change events if they exist
        document.addEventListener('themeChanged', function() {
            if (ReportsState.initialized && ReportsState.chart) {
                console.log('Theme change event received, re-rendering charts');
                setTimeout(() => render(), 50);
            }
        });
    }

    /**
     * Bind window resize handler for responsive chart updates
     * Debounces resize events to avoid excessive re-renders
     */
    function bindResponsiveHandler() {
        let resizeTimer;
        const handleResize = () => {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(() => {
                if (ReportsState.initialized && ReportsState.chart) {
                    // Re-render charts with new responsive settings
                    render();
                }
            }, 250); // Debounce by 250ms
        };
        
        if (!window.__reportsResizeBound) {
            window.addEventListener('resize', handleResize);
            window.__reportsResizeBound = true;
        }
    }

    // ================= CSV Export Functions =================

    /**
     * Transform current report data to CSV format
     * Includes all KPIs and user details
     * @returns {string} CSV formatted string
     */
    function generateReportCSV() {
        try {
            const users = ReportsState.users || [];
            const filteredUsers = ReportsState.filteredUsers || [];
            const days = getRangeDays();
            
            // Calculate KPIs
            const kpis = calculateKPIs(users, filteredUsers, days);
            
            // CSV Header
            const lines = [];
            lines.push('# Analytics & Reports Export');
            lines.push(`# Generated: ${new Date().toLocaleString()}`);
            lines.push(`# Time Range: ${days === 'custom' ? 'Custom' : 'Last ' + days + ' days'}`);
            lines.push('');
            
            // KPI Summary Section
            lines.push('## Key Performance Indicators');
            lines.push('Metric,Value');
            lines.push(`Total Users,${kpis.totalUsers}`);
            lines.push(`New Users (in period),${kpis.newUsers}`);
            lines.push(`Active Rate,${kpis.activeRate}`);
            lines.push(`Premium Users,${kpis.premiumUsers}`);
            lines.push(`Admin Users,${kpis.adminUsers}`);
            lines.push(`Moderators,${kpis.moderators}`);
            lines.push(`Growth Rate,${kpis.growthRate}`);
            lines.push(`Retention Rate,${kpis.retentionRate}`);
            lines.push('');
            
            // User Details Section
            lines.push('## User Details');
            lines.push('Email,Role,Status,Premium,Created At');
            
            // Add all users to CSV
            users.forEach(user => {
                const email = escapeCSV(user.email || '');
                const role = escapeCSV(user.role || 'USER');
                const status = user.isActive ? 'Active' : 'Inactive';
                const premium = user.isPremium ? 'Yes' : 'No';
                const createdAt = user.createdAt ? new Date(user.createdAt).toLocaleString() : 'N/A';
                
                lines.push(`${email},${role},${status},${premium},${createdAt}`);
            });
            
            return lines.join('\n');
        } catch (error) {
            console.error('Error generating CSV:', error);
            throw new Error('Failed to generate CSV export');
        }
    }

    /**
     * Escape CSV field values to handle commas, quotes, and newlines
     * @param {string} value - Value to escape
     * @returns {string} Escaped value
     */
    function escapeCSV(value) {
        if (value == null) return '';
        
        const str = String(value);
        
        // If value contains comma, quote, or newline, wrap in quotes and escape quotes
        if (str.includes(',') || str.includes('"') || str.includes('\n')) {
            return '"' + str.replace(/"/g, '""') + '"';
        }
        
        return str;
    }

    /**
     * Generate downloadable CSV file from report data
     * Triggers browser download
     */
    function exportReportToCSV() {
        try {
            // Generate CSV content
            const csvContent = generateReportCSV();
            
            // Create blob
            const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
            
            // Create download link
            const link = document.createElement('a');
            const url = URL.createObjectURL(blob);
            
            // Generate filename with timestamp
            const timestamp = new Date().toISOString().slice(0, 19).replace(/:/g, '-');
            const filename = `analytics-report-${timestamp}.csv`;
            
            link.setAttribute('href', url);
            link.setAttribute('download', filename);
            link.style.visibility = 'hidden';
            
            // Trigger download
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            
            // Clean up
            URL.revokeObjectURL(url);
            
            // Show success notification
            try {
                showMessage('success', 'Report exported successfully');
            } catch (e) {
                console.warn('Failed to show success toast:', e);
            }
            
            return true;
        } catch (error) {
            console.error('Error exporting CSV:', error);
            
            // Show error notification
            try {
                showMessage('error', 'Failed to export report');
            } catch (e) {
                console.warn('Failed to show error toast:', e);
            }
            
            return false;
        }
    }

    /**
     * Bind export button click event
     */
    function bindExportButton() {
        const exportBtn = q('reports-export-btn');
        if (exportBtn && !exportBtn.dataset.bound) {
            exportBtn.addEventListener('click', function() {
                // Check if data is available
                if (!ReportsState.users || ReportsState.users.length === 0) {
                    try {
                        showMessage('error', 'No data available to export');
                    } catch (e) {
                        console.warn('Failed to show error toast:', e);
                    }
                    return;
                }
                
                // Export to CSV
                exportReportToCSV();
            });
            exportBtn.dataset.bound = 'true';
        }
    }

    // ================= End CSV Export Functions =================

    /**
     * Update last updated timestamp display
     */
    function updateLastUpdatedTimestamp() {
        const timestampEl = q('reports-last-updated');
        if (timestampEl && ReportsState.lastUpdated) {
            try {
                const date = new Date(ReportsState.lastUpdated);
                const timeStr = date.toLocaleTimeString(undefined, { 
                    hour: '2-digit', 
                    minute: '2-digit' 
                });
                const dateStr = date.toLocaleDateString(undefined, { 
                    month: 'short', 
                    day: 'numeric' 
                });
                timestampEl.textContent = `Last updated: ${dateStr} at ${timeStr}`;
            } catch (e) {
                console.warn('Failed to format timestamp:', e);
                timestampEl.textContent = 'Last updated: Just now';
            }
        }
    }

    /**
     * Refresh reports data
     * Re-fetches all user data and updates the display
     * Forces a fresh fetch, bypassing the cache
     */
    function refreshReports() {
        const refreshBtn = q('reports-refresh-btn');
        const refreshSpinner = q('reports-refresh-spinner');
        
        // Show loading indicator
        if (refreshBtn) refreshBtn.disabled = true;
        if (refreshSpinner) refreshSpinner.style.display = 'inline-block';
        
        // Clear error state
        ReportsStateManager.clearError();
        
        // Re-fetch all users with forceRefresh=true to bypass cache
        fetchAllUsers(true)
            .then(() => {
                // Re-render the reports
                render();
                
                // Update timestamp
                updateLastUpdatedTimestamp();
                
                // Show success notification
                try {
                    showMessage('success', 'Reports refreshed successfully');
                } catch (e) {
                    console.warn('Failed to show success toast:', e);
                }
            })
            .catch(err => {
                console.error('Failed to refresh reports:', err);
                
                // Show error notification
                try {
                    showMessage('error', ReportsState.error || 'Failed to refresh reports');
                } catch (e) {
                    console.warn('Failed to show error toast:', e);
                }
            })
            .finally(() => {
                // Hide loading indicator
                if (refreshBtn) refreshBtn.disabled = false;
                if (refreshSpinner) refreshSpinner.style.display = 'none';
            });
    }

    /**
     * Bind refresh button click event
     */
    function bindRefreshButton() {
        const refreshBtn = q('reports-refresh-btn');
        if (refreshBtn && !refreshBtn.dataset.bound) {
            refreshBtn.addEventListener('click', refreshReports);
            refreshBtn.dataset.bound = 'true';
        }
    }

    function ensureInitialized(){
        if (ReportsState.initialized) return;
        const panel = document.getElementById('reports');
        if (!panel) return;
        
        // Show loader message in empty div while fetching
        const empty = q('reports-empty');
        if (empty) { 
            empty.textContent = 'Loading report…'; 
            empty.classList.remove('hidden'); 
            empty.classList.add('visible'); 
        }
        
        fetchUsers()
            .then(() => { 
                bindRange(); 
                bindThemeReactivity(); 
                bindExportButton(); 
                bindRefreshButton(); 
                bindResponsiveHandler();
                render(); 
                updateLastUpdatedTimestamp();
                ReportsState.initialized = true; 
            })
            .catch(err => { 
                console.warn('Failed to initialize reports', err);
                
                // Display error message
                if (empty) { 
                    empty.textContent = ReportsState.error || 'Failed to load data'; 
                }
                
                // Show error toast
                try {
                    showMessage('error', ReportsState.error || 'Failed to load report data');
                } catch (e) {
                    console.warn('Failed to show error toast:', e);
                }
            });
    }

    /**
     * Inject responsive CSS styles for Reports tab
     * Ensures KPI grid adapts to mobile screens, charts are responsive and touch-friendly,
     * and time range selector adjusts for mobile
     * 
     * STYLING CONSISTENCY:
     * - Uses existing CSS variables for colors (--card-bg, --card-border, --text-color, etc.)
     * - Matches card styles with other dashboard sections (border-radius, padding, shadows)
     * - Ensures theme compatibility (light/dark mode) through CSS variable usage
     * - Follows existing dashboard design patterns for consistency
     */
    function injectResponsiveStyles() {
        // Check if styles already injected
        if (document.getElementById('reports-responsive-styles')) {
            return;
        }
        
        const style = document.createElement('style');
        style.id = 'reports-responsive-styles';
        style.textContent = `
            /* Reports Responsive Styles 
             * Note: All colors use CSS variables for theme compatibility
             * Matches existing dashboard card styles for consistency
             */
            
            /* KPI Grid - Responsive adjustments */
            #reports-kpis-container {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                gap: 1rem;
                margin-bottom: 2rem;
            }
            
            @media (max-width: 768px) {
                #reports-kpis-container {
                    grid-template-columns: repeat(2, 1fr);
                    gap: 0.75rem;
                }
            }
            
            @media (max-width: 480px) {
                #reports-kpis-container {
                    grid-template-columns: 1fr;
                    gap: 0.5rem;
                }
            }
            
            /* KPI Cards - Mobile adjustments */
            .kpi-card {
                padding: 1rem;
                min-height: 100px;
            }
            
            @media (max-width: 768px) {
                .kpi-card {
                    padding: 0.75rem;
                    min-height: 80px;
                }
                
                .kpi-card .kpi-value {
                    font-size: 1.5rem;
                }
                
                .kpi-card .kpi-label {
                    font-size: 0.85rem;
                }
            }
            
            /* Charts - Responsive and touch-friendly */
            .chart-container {
                position: relative;
                width: 100%;
                margin-bottom: 1.5rem;
                touch-action: pan-y;
            }
            
            #reports-chart,
            #reports-role-chart,
            #reports-active-chart {
                max-width: 100%;
                height: auto !important;
                touch-action: manipulation;
            }
            
            @media (max-width: 768px) {
                .chart-container {
                    margin-bottom: 1rem;
                }
                
                #reports-chart {
                    min-height: 200px;
                }
                
                #reports-role-chart,
                #reports-active-chart {
                    min-height: 250px;
                }
            }
            
            /* Charts Grid - Stack on mobile */
            #reports-charts-container {
                display: grid;
                grid-template-columns: 1fr;
                gap: 1.5rem;
            }
            
            @media (min-width: 769px) {
                #reports-charts-container .charts-row {
                    display: grid;
                    grid-template-columns: repeat(2, 1fr);
                    gap: 1.5rem;
                }
            }
            
            /* Time Range Selector - Mobile adjustments */
            .reports-controls {
                display: flex;
                flex-wrap: wrap;
                gap: 1rem;
                align-items: center;
                margin-bottom: 1.5rem;
            }
            
            @media (max-width: 768px) {
                .reports-controls {
                    flex-direction: column;
                    align-items: stretch;
                    gap: 0.75rem;
                }
                
                .reports-controls select,
                .reports-controls input,
                .reports-controls button {
                    width: 100%;
                }
            }
            
            /* Custom Date Range Inputs - Mobile */
            #reports-custom-dates {
                display: flex;
                gap: 0.5rem;
                align-items: center;
                flex-wrap: wrap;
            }
            
            @media (max-width: 768px) {
                #reports-custom-dates {
                    flex-direction: column;
                    width: 100%;
                }
                
                #reports-custom-dates input {
                    width: 100%;
                }
            }
            
            /* Header Section - Mobile */
            .reports-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 1.5rem;
                flex-wrap: wrap;
                gap: 1rem;
            }
            
            @media (max-width: 768px) {
                .reports-header {
                    flex-direction: column;
                    align-items: flex-start;
                }
                
                .reports-header-actions {
                    width: 100%;
                    display: flex;
                    justify-content: space-between;
                }
            }
            
            /* Export Button - Mobile */
            #reports-export-btn {
                min-width: 120px;
            }
            
            @media (max-width: 480px) {
                #reports-export-btn {
                    width: 100%;
                }
            }
            
            /* Error Banner - Mobile */
            #reports-error-banner {
                margin-bottom: 1rem;
            }
            
            @media (max-width: 768px) {
                #reports-error-banner > div {
                    flex-direction: column;
                    align-items: flex-start !important;
                    gap: 0.75rem;
                }
                
                #reports-error-banner button {
                    width: 100%;
                }
            }
            
            /* Empty State - Mobile */
            #reports-empty {
                padding: 2rem 1rem;
            }
            
            @media (max-width: 480px) {
                #reports-empty {
                    padding: 1.5rem 0.75rem;
                }
                
                #reports-empty > div {
                    font-size: 0.9rem;
                }
            }
            
            /* Touch-friendly buttons */
            @media (max-width: 768px) {
                .btn {
                    min-height: 44px;
                    padding: 0.75rem 1rem;
                    font-size: 1rem;
                }
                
                .pagination-button {
                    min-width: 44px;
                    min-height: 44px;
                }
            }
            
            /* Improve chart legend readability on mobile */
            @media (max-width: 768px) {
                .chart-container canvas {
                    max-height: 300px;
                }
            }
        `;
        
        document.head.appendChild(style);
    }

    /**
     * Initialize Reports tab when activated
     * Implements lazy loading - data is only fetched when tab is first activated
     * Prevents duplicate data fetching with initialization flag
     */
    function initializeReportsTab() {
        // Check if already initialized to prevent duplicate fetching
        if (ReportsState.initialized) {
            console.log('Reports already initialized, skipping re-initialization');
            return;
        }
        
        console.log('Initializing Reports tab...');
        ensureInitialized();
    }

    /**
     * Store selected time range in sessionStorage for persistence
     * Restores time range on page reload
     */
    function persistTimeRange() {
        try {
            const timeRange = ReportsState.timeRange;
            const customStart = ReportsState.customStart;
            const customEnd = ReportsState.customEnd;
            
            const persistData = {
                timeRange,
                customStart,
                customEnd,
                timestamp: Date.now()
            };
            
            sessionStorage.setItem('reports_time_range', JSON.stringify(persistData));
        } catch (e) {
            console.warn('Failed to persist time range:', e);
        }
    }

    /**
     * Restore time range from sessionStorage on page reload
     */
    function restoreTimeRange() {
        try {
            const stored = sessionStorage.getItem('reports_time_range');
            if (!stored) return;
            
            const persistData = JSON.parse(stored);
            
            // Check if data is not too old (max 24 hours)
            const maxAge = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
            if (Date.now() - persistData.timestamp > maxAge) {
                sessionStorage.removeItem('reports_time_range');
                return;
            }
            
            // Restore time range
            if (persistData.timeRange === 'custom' && persistData.customStart && persistData.customEnd) {
                ReportsStateManager.setCustomRange(persistData.customStart, persistData.customEnd);
                
                // Update UI
                const rangeSelect = q('reports-range');
                if (rangeSelect) rangeSelect.value = 'custom';
                
                const startInput = q('reports-start-date');
                const endInput = q('reports-end-date');
                if (startInput) startInput.value = persistData.customStart;
                if (endInput) endInput.value = persistData.customEnd;
                
                // Show custom date inputs
                const customDateRange = q('custom-date-range');
                if (customDateRange) customDateRange.style.display = 'flex';
            } else if (persistData.timeRange) {
                ReportsStateManager.setTimeRange(persistData.timeRange);
                
                // Update UI
                const rangeSelect = q('reports-range');
                if (rangeSelect) rangeSelect.value = persistData.timeRange;
            }
            
            console.log('Restored time range from session:', persistData);
        } catch (e) {
            console.warn('Failed to restore time range:', e);
        }
    }

    /**
     * Clear session data on logout
     * Removes persisted time range and resets state
     */
    function clearReportsSession() {
        try {
            sessionStorage.removeItem('reports_time_range');
            ReportsStateManager.reset();
            console.log('Cleared Reports session data');
        } catch (e) {
            console.warn('Failed to clear Reports session:', e);
        }
    }

    // Hook into admin init: click on Reports tab should trigger initialization
    document.addEventListener('DOMContentLoaded', function(){
        try {
            // Inject responsive styles
            injectResponsiveStyles();
            
            // Restore time range from session if available
            restoreTimeRange();
            
            // Bind tab click for lazy loading
            const tab = document.querySelector('.tab[data-tab="reports"]');
            if (tab && !tab.dataset.reportsBound) {
                tab.addEventListener('click', function() {
                    // Initialize Reports tab when clicked (lazy loading)
                    initializeReportsTab();
                });
                tab.dataset.reportsBound = 'true';
            }
            
            // If Reports tab is already active on page load, initialize immediately
            const panel = document.getElementById('reports');
            if (panel && panel.classList.contains('active')) {
                initializeReportsTab();
            }
            
            // Bind logout to clear session
            const logoutBtn = document.getElementById('logout-button');
            if (logoutBtn && !logoutBtn.dataset.reportsClearBound) {
                logoutBtn.addEventListener('click', clearReportsSession);
                logoutBtn.dataset.reportsClearBound = 'true';
            }
        } catch(e) { console.warn('Reports binding failed', e); }
    });
    
    // Expose functions for external use
    window.ReportsModule = {
        initialize: initializeReportsTab,
        clearSession: clearReportsSession,
        persistTimeRange: persistTimeRange,
        restoreTimeRange: restoreTimeRange
    };
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
                    try { showMessage('success', 'Notification sent successfully'); } catch(_) {}
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


// ================= Subscriptions Management =================
(function(){
    const state = {
        initialized: false,
        currentPage: 1,
        pageSize: 10,
        totalPages: 1,
        totalCount: 0,
        statusFilter: ''
    };

    function q(id){ return document.getElementById(id); }

    /**
     * Load subscriptions from the API with pagination and filtering
     * @param {number} page - The page number to load
     * @param {number} pageSize - The number of subscriptions per page
     * @param {string} statusFilter - Optional status filter (ACTIVE, EXPIRED, CANCELLED, GRACE_PERIOD)
     */
    function loadSubscriptions(page, pageSize, statusFilter) {
        page = page || 1;
        pageSize = pageSize || 10;
        statusFilter = statusFilter || '';

        // Show loader
        const loader = q('subscriptions-loader');
        if (loader) loader.style.display = 'block';

        // Build URL with query parameters
        let url = `/admin/subscriptions?page=${encodeURIComponent(page)}&pageSize=${encodeURIComponent(pageSize)}`;
        if (statusFilter) {
            url += `&status=${encodeURIComponent(statusFilter)}`;
        }

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        fetch(url, {
            method: 'GET',
            credentials: 'include',
            headers
        })
        .then(response => {
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to access subscriptions');
                }
                throw new Error('Failed to load subscriptions');
            }
            return response.json();
        })
        .then(payload => {
            if (!payload || payload.status !== true || !payload.data) {
                throw new Error(payload && payload.message ? payload.message : 'Invalid response while loading subscriptions');
            }

            const data = payload.data;
            const subscriptions = Array.isArray(data.subscriptions) ? data.subscriptions : [];

            // Update state
            state.currentPage = data.page || page;
            state.pageSize = data.pageSize || pageSize;
            state.totalPages = data.totalPages || 1;
            state.totalCount = data.totalCount || 0;
            state.statusFilter = statusFilter;

            // Hide loader
            if (loader) loader.style.display = 'none';

            // Render subscriptions table
            renderSubscriptionsTable(subscriptions);

            // Render pagination
            renderSubscriptionsPagination();

            console.log('Loaded subscriptions:', subscriptions);
        })
        .catch(error => {
            console.error('Error loading subscriptions:', error);
            if (loader) loader.style.display = 'none';
            showErrorMessage(error.message || 'Failed to load subscriptions');

            // If error is due to authentication, redirect to login
            if (error.message.includes('permission') || error.message.includes('authentication')) {
                setTimeout(() => {
                    window.location.href = '/admin/login?error=auth_required';
                }, 2000);
            }
        });
    }

    /**
     * Load subscription analytics
     */
    function loadSubscriptionAnalytics() {
        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        fetch('/admin/subscriptions/analytics', {
            method: 'GET',
            credentials: 'include',
            headers
        })
        .then(response => {
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to access analytics');
                }
                throw new Error('Failed to load analytics');
            }
            return response.json();
        })
        .then(payload => {
            if (!payload || payload.status !== true || !payload.data) {
                throw new Error(payload && payload.message ? payload.message : 'Invalid response while loading analytics');
            }

            const analytics = payload.data;

            // Update analytics cards
            const statActive = q('stat-active');
            const statRevenue = q('stat-revenue');
            const statExpired = q('stat-expired');
            const statCancelled = q('stat-cancelled');

            if (statActive) statActive.textContent = analytics.totalActive || '0';
            if (statRevenue) {
                // Revenue is already in rupees from backend (converted from paise)
                const revenue = Number(analytics.totalRevenue || 0);
                statRevenue.textContent = '₹' + revenue.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
            }
            if (statExpired) statExpired.textContent = analytics.totalExpired || '0';
            if (statCancelled) statCancelled.textContent = analytics.totalCancelled || '0';

            console.log('Loaded analytics:', analytics);
        })
        .catch(error => {
            console.error('Error loading analytics:', error);
            showErrorMessage(error.message || 'Failed to load analytics');
        });
    }

    /**
     * Render the subscriptions table
     * @param {Array} subscriptions - Array of subscription objects
     */
    function renderSubscriptionsTable(subscriptions) {
        const tableBody = q('subscriptions-table-body');
        if (!tableBody) return;

        const fragment = document.createDocumentFragment();

        if (subscriptions.length === 0) {
            const row = document.createElement('tr');
            const cell = document.createElement('td');
            cell.colSpan = 8;
            cell.textContent = 'No subscriptions found';
            cell.style.textAlign = 'center';
            row.appendChild(cell);
            fragment.appendChild(row);
            tableBody.replaceChildren(fragment);
            return;
        }

        console.log('[Subscriptions] Rendering', subscriptions.length, 'subscriptions');
        subscriptions.forEach(sub => {
            console.log('[Subscriptions] Subscription data:', {
                userEmail: sub.userEmail,
                paymentId: sub.paymentId,
                amount: sub.amount,
                status: sub.status,
                hasPaymentId: !!sub.paymentId
            });
            
            const row = document.createElement('tr');

            // User Email
            const emailCell = document.createElement('td');
            emailCell.textContent = sub.userEmail || 'N/A';
            row.appendChild(emailCell);

            // Service
            const serviceCell = document.createElement('td');
            serviceCell.textContent = sub.service || 'N/A';
            row.appendChild(serviceCell);

            // Start Date
            const startCell = document.createElement('td');
            startCell.textContent = formatDate(sub.startDate);
            row.appendChild(startCell);

            // End Date
            const endCell = document.createElement('td');
            endCell.textContent = formatDate(sub.endDate);
            row.appendChild(endCell);

            // Status as plain text
            const statusCell = document.createElement('td');
            const status = sub.status || 'UNKNOWN';
            statusCell.textContent = status.replace('_', ' ');
            statusCell.style.textAlign = 'center';
            row.appendChild(statusCell);

            // Days Left
            const daysCell = document.createElement('td');
            if (sub.daysRemaining != null && sub.daysRemaining >= 0) {
                daysCell.textContent = sub.daysRemaining + ' days';
            } else {
                daysCell.textContent = '-';
            }
            row.appendChild(daysCell);

            // Amount - Convert from paise to rupees (Razorpay stores amounts in paise)
            const amountCell = document.createElement('td');
            if (sub.amount != null && sub.amount > 0) {
                // Amount is always in paise, convert to rupees
                const amountInRupees = sub.amount / 100;
                amountCell.textContent = '₹' + amountInRupees.toLocaleString('en-IN', { 
                    minimumFractionDigits: 2, 
                    maximumFractionDigits: 2 
                });
            } else {
                amountCell.textContent = '-';
            }
            row.appendChild(amountCell);

            // Actions
            const actionsCell = document.createElement('td');
            actionsCell.className = 'actions-col';
            actionsCell.style.minWidth = '180px';
            
            // Create action buttons container
            const actionsContainer = document.createElement('div');
            actionsContainer.style.display = 'flex';
            actionsContainer.style.gap = '0.5rem';
            actionsContainer.style.alignItems = 'center';
            actionsContainer.style.flexWrap = 'wrap';
            
            if (status === 'ACTIVE' || status === 'GRACE_PERIOD') {
                // Refund button (show first if payment ID exists)
                if (sub.paymentId) {
                    console.log('[Subscriptions] Adding refund button for:', sub.userEmail, 'paymentId:', sub.paymentId);
                    
                    // Create a container for the refund button
                    const refundContainer = document.createElement('div');
                    refundContainer.style.display = 'inline-flex';
                    refundContainer.style.alignItems = 'center';
                    
                    // Call renderRefundButton to handle refund status and button rendering
                    renderRefundButton(sub.paymentId, refundContainer);
                    
                    actionsContainer.appendChild(refundContainer);
                } else {
                    console.warn('[Subscriptions] No payment ID for:', sub.userEmail);
                }
                
                // Cancel button
                const cancelBtn = document.createElement('button');
                cancelBtn.className = 'btn btn-secondary';
                cancelBtn.style.fontSize = '0.85rem';
                cancelBtn.style.padding = '0.5rem 1rem';
                cancelBtn.style.display = 'flex';
                cancelBtn.style.alignItems = 'center';
                cancelBtn.style.gap = '0.3rem';
                cancelBtn.style.minWidth = '90px';
                cancelBtn.style.justifyContent = 'center';
                
                const cancelText = document.createElement('span');
                cancelText.textContent = 'Cancel';
                cancelBtn.appendChild(cancelText);
                
                const cancelSpinner = document.createElement('span');
                cancelSpinner.className = 'loading-spinner';
                cancelSpinner.style.display = 'none';
                cancelSpinner.style.width = '12px';
                cancelSpinner.style.height = '12px';
                cancelSpinner.style.border = '2px solid rgba(0,0,0,0.3)';
                cancelSpinner.style.borderTopColor = 'var(--text-color)';
                cancelSpinner.style.borderRadius = '50%';
                cancelSpinner.style.animation = 'spin 0.6s linear infinite';
                cancelBtn.appendChild(cancelSpinner);
                
                cancelBtn.addEventListener('click', function() {
                    console.log('[Cancel] Attempting to cancel subscription for:', sub.userEmail);
                    if (confirm(`Cancel subscription for ${sub.userEmail}?`)) {
                        // Show loading state
                        cancelText.style.display = 'none';
                        cancelSpinner.style.display = 'inline-block';
                        cancelBtn.disabled = true;
                        
                        console.log('[Cancel] Calling cancelUserSubscription with:', {
                            userEmail: sub.userEmail,
                            hasButton: !!cancelBtn,
                            hasText: !!cancelText,
                            hasSpinner: !!cancelSpinner
                        });
                        
                        cancelUserSubscription(sub.userEmail, cancelBtn, cancelText, cancelSpinner);
                    }
                });
                actionsContainer.appendChild(cancelBtn);
            } else {
                // Show status message for non-active subscriptions
                const statusMsg = document.createElement('span');
                statusMsg.style.fontSize = '0.85rem';
                statusMsg.style.color = 'var(--text-secondary)';
                statusMsg.textContent = status === 'CANCELLED' ? 'Cancelled' : 'No actions';
                actionsContainer.appendChild(statusMsg);
            }
            
            actionsCell.appendChild(actionsContainer);
            row.appendChild(actionsCell);

            fragment.appendChild(row);
        });

        tableBody.replaceChildren(fragment);
    }

    /**
     * Render pagination controls for subscriptions
     */
    function renderSubscriptionsPagination() {
        const paginationContainer = q('subscriptions-pagination');
        if (!paginationContainer) return;

        paginationContainer.innerHTML = '';

        if (state.totalPages <= 1) {
            return;
        }

        // Previous button
        const prevButton = document.createElement('button');
        prevButton.className = `pagination-button ${state.currentPage === 1 ? 'disabled' : ''}`;
        prevButton.textContent = 'Previous';
        prevButton.disabled = state.currentPage === 1;
        prevButton.addEventListener('click', () => {
            if (state.currentPage > 1) {
                loadSubscriptions(state.currentPage - 1, state.pageSize, state.statusFilter);
            }
        });
        paginationContainer.appendChild(prevButton);

        // Page buttons
        const maxButtons = 5;
        const startPage = Math.max(1, state.currentPage - Math.floor(maxButtons / 2));
        const endPage = Math.min(state.totalPages, startPage + maxButtons - 1);

        for (let i = startPage; i <= endPage; i++) {
            const pageButton = document.createElement('button');
            pageButton.className = `pagination-button ${i === state.currentPage ? 'active' : ''}`;
            pageButton.textContent = i;
            pageButton.addEventListener('click', () => {
                if (i !== state.currentPage) {
                    loadSubscriptions(i, state.pageSize, state.statusFilter);
                }
            });
            paginationContainer.appendChild(pageButton);
        }

        // Next button
        const nextButton = document.createElement('button');
        nextButton.className = `pagination-button ${state.currentPage === state.totalPages ? 'disabled' : ''}`;
        nextButton.textContent = 'Next';
        nextButton.disabled = state.currentPage === state.totalPages;
        nextButton.addEventListener('click', () => {
            if (state.currentPage < state.totalPages) {
                loadSubscriptions(state.currentPage + 1, state.pageSize, state.statusFilter);
            }
        });
        paginationContainer.appendChild(nextButton);
    }

    /**
     * Cancel a user's subscription (admin action)
     * @param {string} userEmail - The email of the user whose subscription to cancel
     * @param {HTMLElement} btn - The cancel button element (optional)
     * @param {HTMLElement} textEl - The button text element (optional)
     * @param {HTMLElement} spinnerEl - The button spinner element (optional)
     */
    function cancelUserSubscription(userEmail, btn, textEl, spinnerEl) {
        console.log('[cancelUserSubscription] Called with:', { userEmail, hasBtn: !!btn, hasTextEl: !!textEl, hasSpinnerEl: !!spinnerEl });
        
        if (!userEmail) {
            console.error('[cancelUserSubscription] No user email provided');
            showErrorMessage('User email is required');
            if (btn && textEl && spinnerEl) {
                textEl.style.display = 'inline';
                spinnerEl.style.display = 'none';
                btn.disabled = false;
            }
            return;
        }

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        const requestBody = { userEmail: userEmail };
        console.log('[cancelUserSubscription] Sending request:', {
            url: '/admin/subscriptions/cancel',
            method: 'POST',
            body: requestBody,
            hasToken: !!token
        });

        fetch('/admin/subscriptions/cancel', {
            method: 'POST',
            credentials: 'include',
            headers,
            body: JSON.stringify({ userEmail: userEmail })
        })
        .then(response => {
            console.log('[cancelUserSubscription] Response received:', {
                status: response.status,
                ok: response.ok,
                statusText: response.statusText
            });
            
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to cancel subscriptions');
                }
                // Try to parse error response
                return response.text().then(text => {
                    console.error('[cancelUserSubscription] Error response text:', text);
                    try {
                        const data = JSON.parse(text);
                        console.error('[cancelUserSubscription] Parsed error data:', data);
                        throw new Error(data.message || 'Failed to cancel subscription');
                    } catch (e) {
                        console.error('[cancelUserSubscription] Failed to parse error response:', e);
                        throw new Error(`Failed to cancel subscription (${response.status}): ${text || 'Unknown error'}`);
                    }
                });
            }
            return response.json();
        })
        .then(payload => {
            console.log('[cancelUserSubscription] Success payload:', payload);
            
            // Reset button state on success
            if (btn && textEl && spinnerEl) {
                textEl.style.display = 'inline';
                spinnerEl.style.display = 'none';
                btn.disabled = false;
            }
            
            if (payload && payload.status === true) {
                console.log('[cancelUserSubscription] Subscription cancelled successfully');
                showMessage('success', `Subscription cancelled successfully for ${userEmail}`);
                // Reload subscriptions and analytics
                loadSubscriptions(state.currentPage, state.pageSize, state.statusFilter);
                loadSubscriptionAnalytics();
            } else {
                console.error('[cancelUserSubscription] Unexpected payload status:', payload);
                throw new Error(payload.message || 'Failed to cancel subscription');
            }
        })
        .catch(error => {
            console.error('[cancelUserSubscription] Caught error:', error);
            showErrorMessage(error.message || 'Failed to cancel subscription');
            
            // Reset button state on error
            if (btn && textEl && spinnerEl) {
                textEl.style.display = 'inline';
                spinnerEl.style.display = 'none';
                btn.disabled = false;
            }
        });
    }

    /**
     * Initialize subscriptions tab
     */
    function ensureInitialized() {
        if (state.initialized) return;
        const panel = q('subscriptions');
        if (!panel) return;

        // Load analytics
        loadSubscriptionAnalytics();

        // Load subscriptions with default pagination
        loadSubscriptions(1, 10, '');

        // Bind status filter
        const statusFilter = q('status-filter');
        if (statusFilter && !statusFilter.dataset.bound) {
            statusFilter.addEventListener('change', function() {
                const filter = this.value;
                loadSubscriptions(1, state.pageSize, filter);
            });
            statusFilter.dataset.bound = 'true';
        }

        // Add event listener for reload requests from refund module
        if (!window._subscriptionReloadListenerBound) {
            document.addEventListener('reloadSubscriptions', function(event) {
                const detail = event.detail || {};
                loadSubscriptions(
                    detail.page || state.currentPage, 
                    detail.pageSize || state.pageSize, 
                    detail.statusFilter || state.statusFilter
                );
                loadSubscriptionAnalytics();
            });
            window._subscriptionReloadListenerBound = true;
        }

        state.initialized = true;
    }

    // Hook into admin init: click on Subscriptions tab should trigger initialization
    document.addEventListener('DOMContentLoaded', function(){
        try {
            // Bind tab click
            const tab = document.querySelector('.tab[data-tab="subscriptions"]');
            if (tab && !tab.dataset.subscriptionsBound) {
                tab.addEventListener('click', ensureInitialized);
                tab.dataset.subscriptionsBound = 'true';
            }
            // If already active (unlikely), init immediately
            const panel = q('subscriptions');
            if (panel && panel.classList.contains('active')) {
                ensureInitialized();
            }
        } catch(e) { console.warn('Subscriptions binding failed', e); }
    });

    // Expose state and functions globally for refund module integration
    window._subscriptionState = state;
    window.SubscriptionsModule = {
        loadSubscriptions,
        loadSubscriptionAnalytics,
        cancelUserSubscription,
        getState: () => state
    };
})();


// ================= Refund Management =================
(function(){
    /**
     * Render refund button for a subscription payment
     * @param {Object} subscription - Subscription object with payment details
     * @returns {string} HTML string for refund button
     */
    function renderRefundButton(subscription) {
        if (!subscription || !subscription.paymentId) {
            return '';
        }

        // Don't show refund button if subscription is already cancelled or expired
        const status = subscription.status || '';
        if (status === 'CANCELLED' || status === 'EXPIRED') {
            return '';
        }

        return `
            <button class="btn btn-secondary" 
                    style="font-size: 0.85rem; padding: 0.4rem 0.8rem; margin-left: 0.5rem;"
                    onclick="window.RefundModule.openRefundModal('${subscription.paymentId}', ${subscription.amount || 0}, '${subscription.userEmail || ''}')">
                Refund
            </button>
        `;
    }

    /**
     * Open refund modal for a payment
     * @param {string} paymentId - The payment ID to refund
     * @param {number} amount - The original payment amount
     * @param {string} userEmail - The user's email
     */
    function openRefundModal(paymentId, amount, userEmail) {
        if (!paymentId) {
            showErrorMessage('Payment ID is required');
            return;
        }

        // Store current refund context
        window._currentRefundContext = {
            paymentId,
            amount,
            userEmail
        };

        const amountInRupees = (amount / 100).toFixed(2);

        const modalContent = `
            <div class="refund-modal-content">
                <div class="refund-payment-details" style="background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 1rem; margin-bottom: 1.5rem;">
                    <h3 style="margin: 0 0 0.5rem 0; color: var(--card-title); font-size: 1rem;">Payment Details</h3>
                    <div style="display: grid; gap: 0.5rem; font-size: 0.9rem;">
                        <div><strong>Payment ID:</strong> <code style="background: rgba(0,0,0,0.2); padding: 2px 6px; border-radius: 4px;">${escapeHtml(paymentId)}</code></div>
                        <div><strong>User Email:</strong> ${escapeHtml(userEmail)}</div>
                        <div><strong>Original Amount:</strong> ₹${amountInRupees}</div>
                    </div>
                </div>

                <div class="refund-form">
                    <div class="form-group" style="margin-bottom: 1rem;">
                        <label style="display: block; margin-bottom: 0.5rem; font-weight: 600; color: var(--text-color);">Refund Amount</label>
                        <div style="display: flex; gap: 1rem; margin-bottom: 0.5rem;">
                            <label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer;">
                                <input type="radio" name="refund-type" value="full" checked onchange="window.RefundModule.toggleRefundAmount(this)">
                                <span>Full Refund (₹${amountInRupees})</span>
                            </label>
                            <label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer;">
                                <input type="radio" name="refund-type" value="partial" onchange="window.RefundModule.toggleRefundAmount(this)">
                                <span>Partial Refund</span>
                            </label>
                        </div>
                        <input type="number" 
                               id="refund-amount-input" 
                               class="form-input" 
                               placeholder="Enter amount in rupees" 
                               min="1" 
                               max="${amountInRupees}"
                               step="0.01"
                               disabled
                               style="width: 100%; padding: 0.5rem; border: 1px solid var(--card-border); border-radius: 4px; background: var(--card-bg); color: var(--text-color);">
                    </div>

                    <div class="form-group" style="margin-bottom: 1rem;">
                        <label style="display: block; margin-bottom: 0.5rem; font-weight: 600; color: var(--text-color);">Refund Speed</label>
                        <select id="refund-speed-select" 
                                class="form-select" 
                                style="width: 100%; padding: 0.5rem; border: 1px solid var(--card-border); border-radius: 4px; background: var(--card-bg); color: var(--text-color);">
                            <option value="OPTIMUM">Instant (if eligible)</option>
                            <option value="NORMAL">Normal (5-7 business days)</option>
                        </select>
                    </div>

                    <div class="form-group" style="margin-bottom: 1rem;">
                        <label style="display: block; margin-bottom: 0.5rem; font-weight: 600; color: var(--text-color);">Reason (Optional)</label>
                        <textarea id="refund-reason-input" 
                                  class="form-textarea" 
                                  placeholder="Enter reason for refund (max 500 characters)" 
                                  maxlength="500" 
                                  rows="3"
                                  style="width: 100%; padding: 0.5rem; border: 1px solid var(--card-border); border-radius: 4px; background: var(--card-bg); color: var(--text-color); resize: vertical;"></textarea>
                    </div>

                    <div class="form-actions" style="display: flex; gap: 1rem; justify-content: flex-end; margin-top: 1.5rem;">
                        <button class="btn btn-secondary" onclick="closeModal()" style="padding: 0.6rem 1.2rem;">Cancel</button>
                        <button class="btn btn-primary" onclick="window.RefundModule.submitRefund()" style="padding: 0.6rem 1.2rem; background: #10b981;">
                            <span id="refund-submit-text">Submit Refund</span>
                            <span id="refund-submit-spinner" style="display: none;">Processing...</span>
                        </button>
                    </div>
                </div>
            </div>
        `;

        showModal('Initiate Refund', modalContent);
    }

    /**
     * Toggle refund amount input based on refund type
     * @param {HTMLInputElement} radio - The radio button element
     */
    function toggleRefundAmount(radio) {
        const amountInput = document.getElementById('refund-amount-input');
        if (!amountInput) return;

        if (radio.value === 'partial') {
            amountInput.disabled = false;
            amountInput.focus();
        } else {
            amountInput.disabled = true;
            amountInput.value = '';
        }
    }

    /**
     * Submit refund request
     */
    function submitRefund() {
        const context = window._currentRefundContext;
        if (!context) {
            showErrorMessage('Refund context not found');
            return;
        }

        // Get form values
        const refundType = document.querySelector('input[name="refund-type"]:checked')?.value;
        const amountInput = document.getElementById('refund-amount-input');
        const speedSelect = document.getElementById('refund-speed-select');
        const reasonInput = document.getElementById('refund-reason-input');

        // Validate refund amount
        let refundAmount = null;
        if (refundType === 'partial') {
            const amountValue = parseFloat(amountInput?.value || '0');
            if (!amountValue || amountValue <= 0) {
                showErrorMessage('Please enter a valid refund amount');
                return;
            }
            if (amountValue > (context.amount / 100)) {
                showErrorMessage('Refund amount cannot exceed original payment amount');
                return;
            }
            // Convert to paise
            refundAmount = Math.round(amountValue * 100);
        }

        const speed = speedSelect?.value || 'OPTIMUM';
        const reason = reasonInput?.value?.trim() || null;

        // Show loading state
        const submitText = document.getElementById('refund-submit-text');
        const submitSpinner = document.getElementById('refund-submit-spinner');
        const submitBtn = document.querySelector('.btn-primary');
        
        if (submitText) submitText.style.display = 'none';
        if (submitSpinner) submitSpinner.style.display = 'inline';
        if (submitBtn) submitBtn.disabled = true;

        // Prepare request body
        const requestBody = {
            paymentId: context.paymentId,
            amount: refundAmount,
            speed: speed,
            reason: reason
        };

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        };
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }

        // Submit refund request
        fetch('/admin/refunds/initiate', {
            method: 'POST',
            credentials: 'include',
            headers,
            body: JSON.stringify(requestBody)
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(data => {
                    throw new Error(data.message || 'Failed to initiate refund');
                });
            }
            return response.json();
        })
        .then(payload => {
            if (payload && payload.success) {
                showMessage('success', `Refund initiated successfully for ${context.userEmail}. Cancelling subscription...`);
                
                // Automatically cancel the subscription after successful refund
                return fetch('/admin/subscriptions/cancel', {
                    method: 'POST',
                    credentials: 'include',
                    headers,
                    body: JSON.stringify({ userEmail: context.userEmail })
                })
                .then(cancelResponse => {
                    if (cancelResponse.ok) {
                        return cancelResponse.json().then(cancelPayload => {
                            if (cancelPayload && cancelPayload.status === true) {
                                showMessage('success', `Refund processed and subscription cancelled for ${context.userEmail}`);
                            }
                        });
                    }
                    // Don't throw error if cancel fails - refund was successful
                    console.warn('Refund successful but subscription cancellation failed');
                })
                .catch(cancelError => {
                    console.warn('Refund successful but subscription cancellation failed:', cancelError);
                    // Still show success for refund
                });
            } else {
                throw new Error(payload.message || 'Failed to initiate refund');
            }
        })
        .then(() => {
            closeModal();
            
            // Refresh subscription view - use the subscription module's state
            const subscriptionsPanel = document.getElementById('subscriptions');
            if (subscriptionsPanel) {
                // Try to find the loadSubscriptions function in the subscriptions module scope
                const subState = window._subscriptionState || { currentPage: 1, pageSize: 10, statusFilter: '' };
                // Trigger a reload by dispatching a custom event
                const reloadEvent = new CustomEvent('reloadSubscriptions', { 
                    detail: { 
                        page: subState.currentPage, 
                        pageSize: subState.pageSize, 
                        statusFilter: subState.statusFilter 
                    } 
                });
                document.dispatchEvent(reloadEvent);
            }
            
            // Reload financial metrics to reflect refund in total revenue
            if (typeof loadFinancialMetrics === 'function') {
                loadFinancialMetrics();
            }
            
            // Reload subscription analytics to reflect updated revenue
            if (typeof window.SubscriptionsModule !== 'undefined' && 
                typeof window.SubscriptionsModule.loadSubscriptionAnalytics === 'function') {
                window.SubscriptionsModule.loadSubscriptionAnalytics();
            }
            
            // Clear context
            window._currentRefundContext = null;
        })
        .catch(error => {
            console.error('Error initiating refund:', error);
            showErrorMessage(error.message || 'Failed to initiate refund');
        })
        .finally(() => {
            if (submitText) submitText.style.display = 'inline';
            if (submitSpinner) submitSpinner.style.display = 'none';
            if (submitBtn) submitBtn.disabled = false;
        });
    }

    /**
     * Render refund status badge for a subscription
     * @param {Object} subscription - Subscription object
     * @param {Object} refundSummary - Refund summary object
     * @returns {string} HTML string for refund status
     */
    function renderRefundStatus(subscription, refundSummary) {
        if (!refundSummary || !refundSummary.totalRefunded || refundSummary.totalRefunded === 0) {
            return '';
        }

        const isFullyRefunded = refundSummary.isFullyRefunded;
        const badgeClass = 'badge';
        const badgeStyle = isFullyRefunded ? 'background: #8b5cf6;' : 'background: #f59e0b;';
        const badgeText = isFullyRefunded ? 'Fully Refunded' : 'Partially Refunded';
        const refundedAmount = (refundSummary.totalRefunded / 100).toFixed(2);

        return `
            <div style="display: inline-flex; align-items: center; gap: 0.5rem; margin-left: 0.5rem;">
                <span class="${badgeClass}" style="${badgeStyle}">${badgeText}</span>
                <span style="color: var(--text-secondary); font-size: 0.85rem;">₹${refundedAmount} refunded</span>
                <button class="btn btn-link" 
                        style="font-size: 0.85rem; padding: 0.2rem 0.5rem; text-decoration: underline;"
                        onclick="window.RefundModule.viewRefundHistory('${subscription.paymentId}')">
                    View History
                </button>
            </div>
        `;
    }

    /**
     * View refund history for a payment
     * @param {string} paymentId - The payment ID
     */
    function viewRefundHistory(paymentId) {
        if (!paymentId) {
            showErrorMessage('Payment ID is required');
            return;
        }

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }

        // Show loading modal
        showModal('Refund History', '<div style="text-align: center; padding: 2rem;">Loading refund history...</div>');

        fetch(`/admin/refunds/payment/${encodeURIComponent(paymentId)}`, {
            method: 'GET',
            credentials: 'include',
            headers
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(data => {
                    throw new Error(data.message || 'Failed to load refund history');
                });
            }
            return response.json();
        })
        .then(data => {
            if (!data || !data.refunds) {
                throw new Error('Invalid response from server');
            }

            const summary = data;
            const refunds = summary.refunds || [];

            let historyHtml = `
                <div class="refund-history-content">
                    <div class="refund-summary" style="background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 1rem; margin-bottom: 1.5rem;">
                        <h3 style="margin: 0 0 0.5rem 0; color: var(--card-title); font-size: 1rem;">Payment Summary</h3>
                        <div style="display: grid; gap: 0.5rem; font-size: 0.9rem;">
                            <div><strong>Payment ID:</strong> <code style="background: rgba(0,0,0,0.2); padding: 2px 6px; border-radius: 4px;">${escapeHtml(summary.paymentId)}</code></div>
                            <div><strong>Original Amount:</strong> ₹${(summary.originalAmount / 100).toFixed(2)}</div>
                            <div><strong>Total Refunded:</strong> ₹${(summary.totalRefunded / 100).toFixed(2)}</div>
                            <div><strong>Remaining Refundable:</strong> ₹${(summary.remainingRefundable / 100).toFixed(2)}</div>
                            <div><strong>Status:</strong> ${summary.isFullyRefunded ? '<span class="badge" style="background: #8b5cf6;">Fully Refunded</span>' : '<span class="badge" style="background: #10b981;">Partially Refunded</span>'}</div>
                        </div>
                    </div>

                    <h3 style="margin: 0 0 1rem 0; color: var(--card-title); font-size: 1rem;">Refund History</h3>
            `;

            if (refunds.length === 0) {
                historyHtml += '<div style="text-align: center; padding: 2rem; color: var(--text-secondary);">No refunds found for this payment</div>';
            } else {
                historyHtml += `
                    <div class="refund-history-table" style="overflow-x: auto;">
                        <table style="width: 100%; border-collapse: collapse;">
                            <thead>
                                <tr style="border-bottom: 2px solid var(--card-border);">
                                    <th style="padding: 0.75rem; text-align: left; font-weight: 600; color: var(--card-title);">Refund ID</th>
                                    <th style="padding: 0.75rem; text-align: left; font-weight: 600; color: var(--card-title);">Amount</th>
                                    <th style="padding: 0.75rem; text-align: left; font-weight: 600; color: var(--card-title);">Status</th>
                                    <th style="padding: 0.75rem; text-align: left; font-weight: 600; color: var(--card-title);">Speed</th>
                                    <th style="padding: 0.75rem; text-align: left; font-weight: 600; color: var(--card-title);">Date</th>
                                    <th style="padding: 0.75rem; text-align: left; font-weight: 600; color: var(--card-title);">Processed By</th>
                                </tr>
                            </thead>
                            <tbody>
                `;

                refunds.forEach(refund => {
                    const statusColor = refund.status === 'PROCESSED' ? '#10b981' : 
                                       refund.status === 'FAILED' ? '#ef4444' : '#f59e0b';
                    
                    historyHtml += `
                        <tr style="border-bottom: 1px solid var(--card-border);">
                            <td style="padding: 0.75rem; font-family: monospace; font-size: 0.85rem;">${escapeHtml(refund.refundId)}</td>
                            <td style="padding: 0.75rem;">₹${refund.amount.toFixed(2)}</td>
                            <td style="padding: 0.75rem;"><span class="badge" style="background: ${statusColor};">${refund.status}</span></td>
                            <td style="padding: 0.75rem;">${refund.speedProcessed || refund.speedRequested}</td>
                            <td style="padding: 0.75rem;">${formatDate(refund.createdAt)}</td>
                            <td style="padding: 0.75rem;">${escapeHtml(refund.processedBy)}</td>
                        </tr>
                    `;

                    if (refund.reason) {
                        historyHtml += `
                            <tr style="border-bottom: 1px solid var(--card-border);">
                                <td colspan="6" style="padding: 0.5rem 0.75rem; font-size: 0.85rem; color: var(--text-secondary);">
                                    <strong>Reason:</strong> ${escapeHtml(refund.reason)}
                                </td>
                            </tr>
                        `;
                    }
                });

                historyHtml += `
                            </tbody>
                        </table>
                    </div>
                `;
            }

            historyHtml += '</div>';

            showModal('Refund History', historyHtml);
        })
        .catch(error => {
            console.error('Error loading refund history:', error);
            showModal('Refund History', `<div style="text-align: center; padding: 2rem; color: #ef4444;">Error: ${escapeHtml(error.message || 'Failed to load refund history')}</div>`);
        });
    }

    // Expose functions globally
    window.RefundModule = {
        renderRefundButton,
        openRefundModal,
        submitRefund,
        toggleRefundAmount,
        renderRefundStatus,
        viewRefundHistory
    };
})();


// ================= Finance Tab Management =================
(function(){
    const state = {
        initialized: false,
        currentPage: 1,
        pageSize: 50,
        totalPages: 1,
        totalCount: 0,
        statusFilter: '',
        startDate: '',
        endDate: ''
    };

    function q(id){ return document.getElementById(id); }

    /**
     * Load financial metrics from the API
     */
    function loadFinanceMetrics() {
        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        fetch('/admin/finance/metrics', {
            method: 'GET',
            credentials: 'include',
            headers
        })
        .then(response => {
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to access financial metrics');
                }
                throw new Error('Failed to load financial metrics');
            }
            return response.json();
        })
        .then(payload => {
            if (!payload || payload.status !== true || !payload.data) {
                throw new Error(payload && payload.message ? payload.message : 'Invalid response while loading financial metrics');
            }

            const metrics = payload.data;

            // Update metric cards with formatted currency
            const totalRevenue = q('total-revenue');
            const monthlyRevenue = q('monthly-revenue');
            const activeSubRevenue = q('active-sub-revenue');
            const totalPayments = q('total-payments');

            if (totalRevenue) {
                totalRevenue.textContent = formatCurrency(metrics.totalRevenue || 0);
            }
            if (monthlyRevenue) {
                monthlyRevenue.textContent = formatCurrency(metrics.monthlyRevenue || 0);
            }
            if (activeSubRevenue) {
                activeSubRevenue.textContent = formatCurrency(metrics.activeSubscriptionsRevenue || 0);
            }
            if (totalPayments) {
                totalPayments.textContent = (metrics.totalPaymentsCount || 0).toLocaleString();
            }

            // Render revenue chart if data is available
            if (metrics.monthlyRevenueChart && Array.isArray(metrics.monthlyRevenueChart)) {
                renderRevenueChart(metrics.monthlyRevenueChart);
            }

            console.log('Loaded financial metrics:', metrics);
        })
        .catch(error => {
            console.error('Error loading financial metrics:', error);
            showErrorMessage(error.message || 'Failed to load financial metrics');

            // If error is due to authentication, redirect to login
            if (error.message.includes('permission') || error.message.includes('authentication')) {
                setTimeout(() => {
                    window.location.href = '/admin/login?error=auth_required';
                }, 2000);
            }
        });
    }

    /**
     * Format currency value
     * @param {number} value - The value to format
     * @returns {string} Formatted currency string
     */
    function formatCurrency(value) {
        if (typeof value !== 'number') value = 0;
        return '₹' + value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    /**
     * Render revenue chart using Chart.js
     * @param {Array} monthlyData - Array of monthly revenue data
     */
    function renderRevenueChart(monthlyData) {
        const canvas = q('revenue-chart');
        if (!canvas) return;

        if (typeof Chart === 'undefined') {
            console.warn('Chart.js not available');
            return;
        }

        // Destroy existing chart if any
        if (state.revenueChart) {
            try {
                state.revenueChart.destroy();
                state.revenueChart = null;
            } catch(e) {
                console.warn('Failed to destroy previous chart', e);
            }
        }

        // Prepare data for chart
        const labels = monthlyData.map(item => {
            try {
                const date = new Date(item.month + '-01');
                return date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
            } catch(e) {
                return item.month;
            }
        });

        const data = monthlyData.map(item => item.revenue || 0);

        const ctx = canvas.getContext('2d');

        // Create gradient fill
        let gradient;
        try {
            gradient = ctx.createLinearGradient(0, 0, 0, canvas.height || 260);
            gradient.addColorStop(0, 'rgba(99, 102, 241, 0.22)');
            gradient.addColorStop(1, 'rgba(99, 102, 241, 0.02)');
        } catch(e) {
            gradient = 'rgba(99, 102, 241, 0.12)';
        }

        // Get theme colors
        const lineColor = '#6366f1';
        const gridColor = getComputedStyle(document.documentElement).getPropertyValue('--card-border').trim() || 'rgba(99,102,241,0.2)';
        const tickColor = getComputedStyle(document.documentElement).getPropertyValue('--text-secondary').trim() || '#9ca3af';

        state.revenueChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Revenue',
                    data: data,
                    borderColor: lineColor,
                    backgroundColor: gradient,
                    fill: true,
                    cubicInterpolationMode: 'monotone',
                    tension: 0.4,
                    borderWidth: 2.5,
                    pointRadius: 3,
                    pointHoverRadius: 5,
                    pointBackgroundColor: lineColor,
                    pointBorderColor: '#fff',
                    pointBorderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 700, easing: 'easeOutCubic' },
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        enabled: true,
                        backgroundColor: 'rgba(17, 24, 39, 0.9)',
                        titleColor: '#ffffff',
                        bodyColor: '#ffffff',
                        displayColors: false,
                        padding: 10,
                        callbacks: {
                            label: function(context) {
                                return 'Revenue: ' + formatCurrency(context.parsed.y);
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { display: false, drawBorder: false },
                        ticks: { color: tickColor, maxRotation: 0 }
                    },
                    y: {
                        beginAtZero: true,
                        grid: { color: gridColor, drawBorder: false },
                        ticks: {
                            color: tickColor,
                            callback: function(value) {
                                return '₹' + value.toLocaleString('en-IN');
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Load payment history from the API with pagination and filters
     * @param {number} page - The page number to load
     * @param {number} pageSize - The number of payments per page
     * @param {string} statusFilter - Optional status filter
     * @param {string} startDate - Optional start date filter
     * @param {string} endDate - Optional end date filter
     */
    function loadPaymentHistory(page, pageSize, statusFilter, startDate, endDate) {
        page = page || 1;
        pageSize = pageSize || 50;
        statusFilter = statusFilter || '';
        startDate = startDate || '';
        endDate = endDate || '';

        // Show loader
        const loader = q('payments-loader');
        if (loader) loader.style.display = 'block';

        // Build URL with query parameters
        let url = `/admin/finance/payments?page=${encodeURIComponent(page)}&pageSize=${encodeURIComponent(pageSize)}`;
        if (statusFilter) {
            url += `&status=${encodeURIComponent(statusFilter)}`;
        }
        if (startDate) {
            url += `&startDate=${encodeURIComponent(startDate)}`;
        }
        if (endDate) {
            url += `&endDate=${encodeURIComponent(endDate)}`;
        }

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        fetch(url, {
            method: 'GET',
            credentials: 'include',
            headers
        })
        .then(response => {
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to access payment history');
                }
                throw new Error('Failed to load payment history');
            }
            return response.json();
        })
        .then(payload => {
            if (!payload || payload.status !== true || !payload.data) {
                throw new Error(payload && payload.message ? payload.message : 'Invalid response while loading payment history');
            }

            const data = payload.data;
            const payments = Array.isArray(data.payments) ? data.payments : [];

            // Update state
            state.currentPage = data.pagination?.page || page;
            state.pageSize = data.pagination?.pageSize || pageSize;
            state.totalPages = data.pagination?.totalPages || 1;
            state.totalCount = data.pagination?.totalCount || 0;
            state.statusFilter = statusFilter;
            state.startDate = startDate;
            state.endDate = endDate;

            // Hide loader
            if (loader) loader.style.display = 'none';

            // Render payments table
            renderPaymentsTable(payments);

            // Render pagination
            renderPaymentsPagination();

            console.log('Loaded payment history:', payments);
        })
        .catch(error => {
            console.error('Error loading payment history:', error);
            if (loader) loader.style.display = 'none';
            showErrorMessage(error.message || 'Failed to load payment history');

            // If error is due to authentication, redirect to login
            if (error.message.includes('permission') || error.message.includes('authentication')) {
                setTimeout(() => {
                    window.location.href = '/admin/login?error=auth_required';
                }, 2000);
            }
        });
    }

    /**
     * Render the payments table
     * @param {Array} payments - Array of payment objects
     */
    function renderPaymentsTable(payments) {
        const tableBody = q('payments-table-body');
        if (!tableBody) return;

        const fragment = document.createDocumentFragment();

        if (payments.length === 0) {
            const row = document.createElement('tr');
            const cell = document.createElement('td');
            cell.colSpan = 7;
            cell.textContent = 'No payments found';
            cell.style.textAlign = 'center';
            row.appendChild(cell);
            fragment.appendChild(row);
            tableBody.replaceChildren(fragment);
            return;
        }

        payments.forEach(payment => {
            const row = document.createElement('tr');

            // User Email
            const emailCell = document.createElement('td');
            emailCell.textContent = payment.userEmail || 'N/A';
            row.appendChild(emailCell);

            // Amount
            const amountCell = document.createElement('td');
            if (payment.amount != null) {
                amountCell.textContent = formatCurrency(payment.amount);
            } else {
                amountCell.textContent = '-';
            }
            row.appendChild(amountCell);

            // Currency
            const currencyCell = document.createElement('td');
            currencyCell.textContent = payment.currency || 'INR';
            row.appendChild(currencyCell);

            // Payment Method
            const methodCell = document.createElement('td');
            methodCell.textContent = payment.paymentMethod || 'N/A';
            row.appendChild(methodCell);

            // Status with badge
            const statusCell = document.createElement('td');
            const statusBadge = document.createElement('span');
            statusBadge.className = 'badge';
            const status = payment.status || 'UNKNOWN';

            // Add status-specific styling
            if (status === 'SUCCESS') {
                statusBadge.style.background = '#10b981';
            } else if (status === 'FAILED') {
                statusBadge.style.background = '#ef4444';
            } else if (status === 'PENDING') {
                statusBadge.style.background = '#f59e0b';
            } else if (status === 'REFUNDED') {
                statusBadge.style.background = '#8b5cf6';
            }

            statusBadge.textContent = status;
            statusCell.appendChild(statusBadge);
            row.appendChild(statusCell);

            // Transaction ID
            const txnCell = document.createElement('td');
            txnCell.textContent = payment.transactionId || '-';
            txnCell.style.fontFamily = 'monospace';
            txnCell.style.fontSize = '0.85em';
            row.appendChild(txnCell);

            // Date
            const dateCell = document.createElement('td');
            dateCell.textContent = formatDate(payment.createdAt);
            row.appendChild(dateCell);

            fragment.appendChild(row);
        });

        tableBody.replaceChildren(fragment);
    }

    /**
     * Render pagination controls for payments
     */
    function renderPaymentsPagination() {
        const paginationContainer = q('payments-pagination');
        if (!paginationContainer) return;

        paginationContainer.innerHTML = '';

        if (state.totalPages <= 1) {
            return;
        }

        // Previous button
        const prevButton = document.createElement('button');
        prevButton.className = `pagination-button ${state.currentPage === 1 ? 'disabled' : ''}`;
        prevButton.textContent = 'Previous';
        prevButton.disabled = state.currentPage === 1;
        prevButton.addEventListener('click', () => {
            if (state.currentPage > 1) {
                loadPaymentHistory(state.currentPage - 1, state.pageSize, state.statusFilter, state.startDate, state.endDate);
            }
        });
        paginationContainer.appendChild(prevButton);

        // Page buttons
        const maxButtons = 5;
        const startPage = Math.max(1, state.currentPage - Math.floor(maxButtons / 2));
        const endPage = Math.min(state.totalPages, startPage + maxButtons - 1);

        for (let i = startPage; i <= endPage; i++) {
            const pageButton = document.createElement('button');
            pageButton.className = `pagination-button ${i === state.currentPage ? 'active' : ''}`;
            pageButton.textContent = i;
            pageButton.addEventListener('click', () => {
                if (i !== state.currentPage) {
                    loadPaymentHistory(i, state.pageSize, state.statusFilter, state.startDate, state.endDate);
                }
            });
            paginationContainer.appendChild(pageButton);
        }

        // Next button
        const nextButton = document.createElement('button');
        nextButton.className = `pagination-button ${state.currentPage === state.totalPages ? 'disabled' : ''}`;
        nextButton.textContent = 'Next';
        nextButton.disabled = state.currentPage === state.totalPages;
        nextButton.addEventListener('click', () => {
            if (state.currentPage < state.totalPages) {
                loadPaymentHistory(state.currentPage + 1, state.pageSize, state.statusFilter, state.startDate, state.endDate);
            }
        });
        paginationContainer.appendChild(nextButton);
    }

    /**
     * Filter payments based on status and date range
     */
    function filterPayments() {
        const statusFilter = q('payment-status-filter');
        const dateFrom = q('payment-date-from');
        const dateTo = q('payment-date-to');

        const status = statusFilter ? statusFilter.value : '';
        const startDate = dateFrom ? dateFrom.value.trim() : '';
        const endDate = dateTo ? dateTo.value.trim() : '';

        // Validate date range if both dates are provided
        if (startDate && endDate) {
            const dateValidation = ValidationUtils.validateDateRange(startDate, endDate);
            if (!dateValidation.valid) {
                showErrorMessage(dateValidation.message);
                return;
            }
        }

        // Validate individual dates if only one is provided
        if (startDate && !ValidationUtils.isValidDate(startDate)) {
            showErrorMessage('Invalid start date format. Use YYYY-MM-DD');
            return;
        }

        if (endDate && !ValidationUtils.isValidDate(endDate)) {
            showErrorMessage('Invalid end date format. Use YYYY-MM-DD');
            return;
        }

        // Load payment history with filters
        loadPaymentHistory(1, state.pageSize, status, startDate, endDate);
    }

    /**
     * Initialize Finance tab
     */
    function ensureInitialized() {
        if (state.initialized) return;
        const panel = q('finance');
        if (!panel) return;

        console.log('[Finance Module] Initializing finance tab...');

        // Load financial metrics
        loadFinanceMetrics();

        // Load payment history with default pagination
        loadPaymentHistory(1, 50, '', '', '');

        // Bind status filter
        const statusFilter = q('payment-status-filter');
        if (statusFilter && !statusFilter.dataset.bound) {
            statusFilter.addEventListener('change', filterPayments);
            statusFilter.dataset.bound = 'true';
        }

        // Bind date filters
        const dateFrom = q('payment-date-from');
        if (dateFrom && !dateFrom.dataset.bound) {
            dateFrom.addEventListener('change', filterPayments);
            dateFrom.dataset.bound = 'true';
        }

        const dateTo = q('payment-date-to');
        if (dateTo && !dateTo.dataset.bound) {
            dateTo.addEventListener('change', filterPayments);
            dateTo.dataset.bound = 'true';
        }

        // Bind Generate Bill button
        const generateBillBtn = q('generate-bill-btn');
        if (generateBillBtn && !generateBillBtn.dataset.bound) {
            generateBillBtn.addEventListener('click', showBillGeneratorModal);
            generateBillBtn.dataset.bound = 'true';
        }

        // Initialize refund analytics when finance tab loads
        console.log('[Finance Module] Checking for RefundAnalyticsModule...', typeof window.RefundAnalyticsModule);
        if (typeof window.RefundAnalyticsModule !== 'undefined' && 
            typeof window.RefundAnalyticsModule.ensureInitialized === 'function') {
            console.log('[Finance Module] Initializing refund analytics...');
            try {
                window.RefundAnalyticsModule.ensureInitialized();
            } catch (e) {
                console.error('[Finance Module] Error initializing refund analytics:', e);
            }
        } else {
            console.warn('[Finance Module] RefundAnalyticsModule not available');
        }

        state.initialized = true;
        console.log('[Finance Module] Finance tab initialized');
    }

    /**
     * Show the bill generator modal
     */
    function showBillGeneratorModal() {
        const content = `
            <div class="bill-generator-form" style="display:grid; gap:20px; max-width:700px; margin:0 auto;">
                <div class="message info-message" style="background:rgba(59,130,246,0.1);border:1px solid rgba(59,130,246,0.3);border-radius:8px;padding:12px;">
                    <div style="display:flex;align-items:start;gap:10px;">
                        <div>
                            <strong style="display:block;margin-bottom:4px;">Generate Invoice</strong>
                            <span style="font-size:0.9rem;color:var(--text-secondary);">Search for a user by email to view their transactions and generate a bill.</span>
                        </div>
                    </div>
                </div>

                <div style="display:grid; gap:10px;">
                    <label for="bill-user-email" style="color:var(--card-title);font-weight:600;font-size:0.95rem;">
                        User Email <span style="color:#ef4444;">*</span>
                    </label>
                    <div style="display:flex; gap:10px;">
                        <input type="email" id="bill-user-email" class="form-control"
                               placeholder="user@example.com"
                               style="flex:1;padding:10px 14px;border:1px solid var(--card-border);border-radius:8px;background:var(--card-bg);color:var(--text-color);font-size:0.95rem;transition:border-color 0.2s;"
                               required
                               pattern="[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"
                               title="Please enter a valid email address" />
                        <button id="search-user-btn" class="btn btn-primary" style="padding:10px 24px;white-space:nowrap;">
                            <span style="display:flex;align-items:center;gap:6px;">
                                <span>🔍</span>
                                <span>Search</span>
                            </span>
                        </button>
                    </div>
                </div>

                <div id="bill-search-spinner" class="loading-spinner" style="display:none;margin:20px auto;"></div>
                <div id="bill-search-error" class="message error-message" style="display:none;"></div>

                <div id="user-transactions" style="display:none;">
                    <div style="background:var(--endpoint-bg);border:1px solid var(--endpoint-border);border-radius:10px;padding:16px;margin-bottom:16px;">
                        <div style="display:flex;align-items:center;gap:10px;margin-bottom:12px;">
                            <span style="font-size:24px;">👤</span>
                            <div>
                                <h3 style="color:var(--card-title);margin:0;font-size:1.1rem;">User Transactions</h3>
                                <p id="user-email-display" style="color:var(--text-secondary);margin:4px 0 0 0;font-size:0.9rem;"></p>
                            </div>
                        </div>
                    </div>

                    <div class="transaction-section" style="margin-bottom:20px;">
                        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;">
                            <h4 style="color:var(--card-title);font-size:1rem;margin:0;display:flex;align-items:center;gap:8px;">
                                <span>💳</span>
                                <span>Payments</span>
                            </h4>
                            <button id="select-all-payments" class="btn btn-secondary" style="padding:4px 12px;font-size:0.85rem;">Select All</button>
                        </div>
                        <div id="payment-checkboxes" style="display:grid;gap:10px;max-height:250px;overflow-y:auto;padding:12px;border:1px solid var(--card-border);border-radius:8px;background:var(--card-bg);"></div>
                    </div>

                    <div class="transaction-section" style="margin-bottom:20px;">
                        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;">
                            <h4 style="color:var(--card-title);font-size:1rem;margin:0;display:flex;align-items:center;gap:8px;">
                                <span>📅</span>
                                <span>Subscriptions</span>
                            </h4>
                            <button id="select-all-subscriptions" class="btn btn-secondary" style="padding:4px 12px;font-size:0.85rem;">Select All</button>
                        </div>
                        <div id="subscription-checkboxes" style="display:grid;gap:10px;max-height:250px;overflow-y:auto;padding:12px;border:1px solid var(--card-border);border-radius:8px;background:var(--card-bg);"></div>
                    </div>

                    <div style="display:grid;gap:16px;background:var(--endpoint-bg);border:1px solid var(--endpoint-border);border-radius:10px;padding:16px;">
                        <label style="display:flex;align-items:center;gap:10px;cursor:pointer;padding:10px;border:1px solid var(--card-border);border-radius:8px;transition:background 0.2s;" onmouseover="this.style.background='var(--hover-bg,rgba(99,102,241,0.05))'" onmouseout="this.style.background='transparent'">
                            <input type="checkbox" id="send-via-email" style="width:20px;height:20px;cursor:pointer;" />
                            <div>
                                <div style="color:var(--text-color);font-weight:600;">Send via Email</div>
                                <div style="color:var(--text-secondary);font-size:0.85rem;margin-top:2px;">Email the generated bill directly to the user</div>
                            </div>
                        </label>

                        <div style="display:flex;gap:10px;">
                            <button id="generate-bill-submit" class="btn btn-primary" style="flex:1;padding:12px;font-weight:600;">
                                <span style="display:flex;align-items:center;justify-content:center;gap:8px;">
                                    <span>📄</span>
                                    <span>Generate Bill</span>
                                </span>
                            </button>
                            <button id="cancel-bill-btn" class="btn btn-secondary" style="padding:12px 20px;">
                                Cancel
                            </button>
                        </div>
                        <div id="bill-generate-spinner" class="loading-spinner" style="display:none;margin:10px auto;"></div>
                        <div id="bill-generate-error" class="message error-message" style="display:none;"></div>
                    </div>
                </div>
            </div>
        `;

        showModal('Generate Bill', content);
        bindBillGeneratorEvents();
    }

    /**
     * Search for user transactions
     * @param {string} email - User email to search for
     */
    function searchUserTransactions(email) {
        const errorEl = q('bill-search-error');
        const spinner = q('bill-search-spinner');
        const searchBtn = q('search-user-btn');
        const transactionsDiv = q('user-transactions');
        const userEmailDisplay = q('user-email-display');

        // Validate email is provided
        const emailValidation = ValidationUtils.validateRequired(email, 'User email');
        if (!emailValidation.valid) {
            if (errorEl) {
                errorEl.textContent = emailValidation.message;
                errorEl.style.display = 'block';
            }
            return;
        }

        // Validate email format
        if (!ValidationUtils.isValidEmail(email.trim())) {
            if (errorEl) {
                errorEl.textContent = 'Please enter a valid email address';
                errorEl.style.display = 'block';
            }
            return;
        }

        // Hide previous errors and transactions
        if (errorEl) errorEl.style.display = 'none';
        if (transactionsDiv) transactionsDiv.style.display = 'none';

        // Show spinner
        if (spinner) spinner.style.display = 'block';
        if (searchBtn) searchBtn.disabled = true;

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        // Fetch user's transactions using the new endpoint
        const url = `/admin/finance/user-transactions?userEmail=${encodeURIComponent(email)}`;

        fetch(url, { method: 'GET', credentials: 'include', headers })
            .then(r => {
                if (!r.ok) {
                    if (r.status === 404) {
                        throw new Error('User not found or has no transactions');
                    }
                    throw new Error('Failed to fetch user transactions');
                }
                return r.json();
            })
            .then(payload => {
                if (!payload || payload.status !== true || !payload.data) {
                    throw new Error(payload?.message || 'Invalid response from server');
                }

                const data = payload.data;
                const payments = data.payments || [];
                const subscriptions = data.subscriptions || [];

                if (payments.length === 0 && subscriptions.length === 0) {
                    throw new Error('No transactions found for this user');
                }

                // Display user email
                if (userEmailDisplay) {
                    userEmailDisplay.textContent = email;
                }

                // Display transactions as checkboxes
                displayTransactionCheckboxes(payments, subscriptions);

                // Show transactions section
                if (transactionsDiv) transactionsDiv.style.display = 'block';
            })
            .catch(error => {
                console.error('Error searching user transactions:', error);

                // Provide user-friendly error messages
                let errorMessage = error.message || 'Failed to search user transactions';

                if (errorMessage.includes('No transactions found')) {
                    errorMessage = 'No transactions found for this user. The user may not have any successful payments or subscriptions.';
                } else if (errorMessage.includes('permission')) {
                    errorMessage = 'You do not have permission to view user transactions.';
                } else if (errorMessage.includes('authentication') || errorMessage.includes('auth')) {
                    errorMessage = 'Your session has expired. Please log in again.';
                } else if (errorMessage.includes('not found') || errorMessage.includes('User not found')) {
                    errorMessage = 'User not found or has no transactions. Please check the email address.';
                }

                if (errorEl) {
                    errorEl.textContent = errorMessage;
                    errorEl.style.display = 'block';
                }
            })
            .finally(() => {
                if (spinner) spinner.style.display = 'none';
                if (searchBtn) searchBtn.disabled = false;
            });
    }

    /**
     * Display transaction checkboxes
     * @param {Array} payments - Array of payment objects
     * @param {Array} subscriptions - Array of subscription objects
     */
    function displayTransactionCheckboxes(payments, subscriptions) {
        const paymentCheckboxesDiv = q('payment-checkboxes');
        const subscriptionCheckboxesDiv = q('subscription-checkboxes');

        // Clear previous checkboxes
        if (paymentCheckboxesDiv) paymentCheckboxesDiv.innerHTML = '';
        if (subscriptionCheckboxesDiv) subscriptionCheckboxesDiv.innerHTML = '';

        // Display payments
        if (payments.length === 0) {
            if (paymentCheckboxesDiv) {
                paymentCheckboxesDiv.innerHTML = '<div style="color:var(--text-secondary);padding:12px;text-align:center;font-style:italic;">No successful payments found</div>';
            }
        } else {
            payments.forEach(payment => {
                const label = document.createElement('label');
                label.style.cssText = 'display:flex;align-items:start;gap:10px;padding:12px;border:1px solid var(--card-border);border-radius:8px;cursor:pointer;transition:all 0.2s;background:var(--endpoint-bg);';
                label.addEventListener('mouseenter', function() {
                    this.style.background = 'var(--hover-bg, rgba(99,102,241,0.08))';
                    this.style.borderColor = 'var(--card-hover-border)';
                });
                label.addEventListener('mouseleave', function() {
                    this.style.background = 'var(--endpoint-bg)';
                    this.style.borderColor = 'var(--card-border)';
                });

                const checkbox = document.createElement('input');
                checkbox.type = 'checkbox';
                checkbox.className = 'payment-checkbox';
                checkbox.value = payment.id;
                checkbox.style.cssText = 'margin-top:2px;';

                const content = document.createElement('div');
                content.style.cssText = 'flex:1;';

                const mainText = document.createElement('div');
                mainText.style.cssText = 'color:var(--text-color);font-weight:600;margin-bottom:4px;';
                mainText.textContent = formatCurrency(payment.amount);

                const subText = document.createElement('div');
                subText.style.cssText = 'color:var(--text-secondary);font-size:0.85rem;';
                subText.textContent = `${payment.paymentMethod || 'N/A'} • ${formatDate(payment.createdAt)}`;

                content.appendChild(mainText);
                content.appendChild(subText);

                label.appendChild(checkbox);
                label.appendChild(content);
                if (paymentCheckboxesDiv) paymentCheckboxesDiv.appendChild(label);
            });
        }

        // Display subscriptions
        if (subscriptions.length === 0) {
            if (subscriptionCheckboxesDiv) {
                subscriptionCheckboxesDiv.innerHTML = '<div style="color:var(--text-secondary);padding:12px;text-align:center;font-style:italic;">No subscriptions found</div>';
            }
        } else {
            subscriptions.forEach(subscription => {
                const label = document.createElement('label');
                label.style.cssText = 'display:flex;align-items:start;gap:10px;padding:12px;border:1px solid var(--card-border);border-radius:8px;cursor:pointer;transition:all 0.2s;background:var(--endpoint-bg);';
                label.addEventListener('mouseenter', function() {
                    this.style.background = 'var(--hover-bg, rgba(99,102,241,0.08))';
                    this.style.borderColor = 'var(--card-hover-border)';
                });
                label.addEventListener('mouseleave', function() {
                    this.style.background = 'var(--endpoint-bg)';
                    this.style.borderColor = 'var(--card-border)';
                });

                const checkbox = document.createElement('input');
                checkbox.type = 'checkbox';
                checkbox.className = 'subscription-checkbox';
                checkbox.value = subscription.id;
                checkbox.style.cssText = 'margin-top:2px;';

                const content = document.createElement('div');
                content.style.cssText = 'flex:1;';

                const mainText = document.createElement('div');
                mainText.style.cssText = 'color:var(--text-color);font-weight:600;margin-bottom:4px;';
                const amount = subscription.amount ? formatCurrency(subscription.amount) : 'N/A';
                const statusText = subscription.status ? ` (${subscription.status})` : '';
                mainText.textContent = `${subscription.service || 'Subscription'} - ${amount}${statusText}`;

                const subText = document.createElement('div');
                subText.style.cssText = 'color:var(--text-secondary);font-size:0.85rem;';
                subText.textContent = `${formatDate(subscription.startDate)} → ${formatDate(subscription.endDate)}`;

                content.appendChild(mainText);
                content.appendChild(subText);

                label.appendChild(checkbox);
                label.appendChild(content);
                if (subscriptionCheckboxesDiv) subscriptionCheckboxesDiv.appendChild(label);
            });
        }
    }

    /**
     * Generate bill with selected transactions
     */
    function generateBill() {
        const emailInput = q('bill-user-email');
        const sendViaEmailCheckbox = q('send-via-email');
        const spinner = q('bill-generate-spinner');
        const errorEl = q('bill-generate-error');
        const submitBtn = q('generate-bill-submit');

        const userEmail = emailInput ? emailInput.value.trim() : '';

        // Hide previous errors
        if (errorEl) errorEl.style.display = 'none';

        // Validate email is provided
        const emailValidation = ValidationUtils.validateRequired(userEmail, 'User email');
        if (!emailValidation.valid) {
            if (errorEl) {
                errorEl.textContent = emailValidation.message;
                errorEl.style.display = 'block';
            }
            return;
        }

        // Validate email format
        if (!ValidationUtils.isValidEmail(userEmail)) {
            if (errorEl) {
                errorEl.textContent = 'Please enter a valid email address';
                errorEl.style.display = 'block';
            }
            return;
        }

        // Collect selected payment IDs
        const paymentCheckboxes = document.querySelectorAll('.payment-checkbox:checked');
        const paymentIds = Array.from(paymentCheckboxes).map(cb => cb.value);

        // Collect selected subscription IDs
        const subscriptionCheckboxes = document.querySelectorAll('.subscription-checkbox:checked');
        const subscriptionIds = Array.from(subscriptionCheckboxes).map(cb => cb.value);

        // Validate at least one transaction is selected
        if (paymentIds.length === 0 && subscriptionIds.length === 0) {
            if (errorEl) {
                errorEl.textContent = 'Please select at least one transaction to include in the bill';
                errorEl.style.display = 'block';
            }
            return;
        }

        const sendViaEmail = sendViaEmailCheckbox ? sendViaEmailCheckbox.checked : false;

        // Hide previous errors
        if (errorEl) errorEl.style.display = 'none';

        // Show spinner
        if (spinner) spinner.style.display = 'block';
        if (submitBtn) submitBtn.disabled = true;

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        const requestBody = {
            userEmail: userEmail,
            paymentIds: paymentIds,
            subscriptionIds: subscriptionIds,
            sendViaEmail: sendViaEmail
        };

        fetch('/admin/finance/generate-bill', {
            method: 'POST',
            credentials: 'include',
            headers: headers,
            body: JSON.stringify(requestBody)
        })
        .then(response => {
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to generate bills');
                }
                // Try to parse error message
                return response.json().then(data => {
                    throw new Error(data.message || 'Failed to generate bill');
                }).catch(() => {
                    throw new Error('Failed to generate bill');
                });
            }

            // Check if response is PDF or JSON
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/pdf')) {
                // Download PDF
                return response.blob().then(blob => {
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = `invoice-${userEmail}-${Date.now()}.pdf`;
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    window.URL.revokeObjectURL(url);

                    showMessage('success', 'Bill generated and downloaded successfully');
                    closeModal();
                });
            } else {
                // JSON response (email sent)
                return response.json().then(data => {
                    if (data.status === true) {
                        if (data.data && data.data.emailSent === false) {
                            showMessage('warning', 'Bill generated but email failed to send. Please try downloading instead.', 6000);
                        } else {
                            showMessage('success', 'Bill generated and sent via email successfully');
                        }
                        closeModal();
                    } else {
                        throw new Error(data.message || 'Failed to generate bill');
                    }
                });
            }
        })
        .catch(error => {
            console.error('Error generating bill:', error);

            // Provide user-friendly error messages
            let errorMessage = error.message || 'Failed to generate bill';

            if (errorMessage.includes('permission')) {
                errorMessage = 'You do not have permission to generate bills. Please contact an administrator.';
            } else if (errorMessage.includes('authentication') || errorMessage.includes('auth')) {
                errorMessage = 'Your session has expired. Please log in again.';
                setTimeout(() => {
                    window.location.href = '/admin/login?error=session_expired';
                }, 2000);
            } else if (errorMessage.includes('not found') || errorMessage.includes('No user')) {
                errorMessage = 'User not found. Please check the email address and try again.';
            } else if (errorMessage.includes('email')) {
                errorMessage = 'Invalid email address. Please enter a valid email.';
            } else if (errorMessage.includes('transaction')) {
                errorMessage = 'No valid transactions found. Please select at least one transaction.';
            }

            if (errorEl) {
                errorEl.textContent = errorMessage;
                errorEl.style.display = 'block';
            }
            showMessage('error', errorMessage);
        })
        .finally(() => {
            if (spinner) spinner.style.display = 'none';
            if (submitBtn) submitBtn.disabled = false;
        });
    }

    /**
     * Bind bill generator modal events
     */
    function bindBillGeneratorEvents() {
        const searchBtn = q('search-user-btn');
        const emailInput = q('bill-user-email');
        const submitBtn = q('generate-bill-submit');
        const cancelBtn = q('cancel-bill-btn');
        const selectAllPaymentsBtn = q('select-all-payments');
        const selectAllSubscriptionsBtn = q('select-all-subscriptions');

        // Bind search button
        if (searchBtn) {
            searchBtn.addEventListener('click', function() {
                const email = emailInput ? emailInput.value.trim() : '';
                searchUserTransactions(email);
            });
        }

        // Bind enter key on email input
        if (emailInput) {
            emailInput.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    const email = emailInput.value.trim();
                    searchUserTransactions(email);
                }
            });
        }

        // Bind select all payments button
        if (selectAllPaymentsBtn) {
            selectAllPaymentsBtn.addEventListener('click', function() {
                const checkboxes = document.querySelectorAll('.payment-checkbox');
                const allChecked = Array.from(checkboxes).every(cb => cb.checked);
                checkboxes.forEach(cb => cb.checked = !allChecked);
                this.textContent = allChecked ? 'Select All' : 'Deselect All';
            });
        }

        // Bind select all subscriptions button
        if (selectAllSubscriptionsBtn) {
            selectAllSubscriptionsBtn.addEventListener('click', function() {
                const checkboxes = document.querySelectorAll('.subscription-checkbox');
                const allChecked = Array.from(checkboxes).every(cb => cb.checked);
                checkboxes.forEach(cb => cb.checked = !allChecked);
                this.textContent = allChecked ? 'Select All' : 'Deselect All';
            });
        }

        // Bind generate bill button
        if (submitBtn) {
            submitBtn.addEventListener('click', generateBill);
        }

        // Bind cancel button
        if (cancelBtn) {
            cancelBtn.addEventListener('click', closeModal);
        }
    }

    // Hook into admin init: click on Finance tab should trigger initialization
    document.addEventListener('DOMContentLoaded', function(){
        try {
            // Bind tab click
            const tab = document.querySelector('.tab[data-tab="finance"]');
            if (tab && !tab.dataset.financeBound) {
                tab.addEventListener('click', ensureInitialized);
                tab.dataset.financeBound = 'true';
            }
            // If already active (unlikely), init immediately
            const panel = q('finance');
            if (panel && panel.classList.contains('active')) {
                ensureInitialized();
            }
        } catch(e) { console.warn('Finance tab binding failed', e); }
    });

    // Expose functions globally for testing/debugging
    window.FinanceModule = {
        loadFinanceMetrics,
        loadPaymentHistory,
        filterPayments,
        renderRevenueChart,
        showBillGeneratorModal,
        searchUserTransactions,
        generateBill
    };
})();


// ================= Refund Analytics Module =================
(function(){
    const state = {
        initialized: false,
        currentPage: 1,
        pageSize: 20,
        totalPages: 1,
        totalCount: 0,
        statusFilter: '',
        startDate: '',
        endDate: ''
    };

    function q(id){ return document.getElementById(id); }

    /**
     * Load refund metrics from the API
     */
    function loadRefundMetrics() {
        console.log('[Refund Analytics] loadRefundMetrics called');
        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        console.log('[Refund Analytics] Fetching /admin/refunds/metrics...');
        fetch('/admin/refunds/metrics', {
            method: 'GET',
            credentials: 'include',
            headers
        })
        .then(response => {
            console.log('[Refund Analytics] Response status:', response.status, response.statusText);
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to access refund metrics');
                }
                throw new Error('Failed to load refund metrics');
            }
            return response.json();
        })
        .then(payload => {
            console.log('[Refund Analytics] Received payload:', payload);
            if (!payload || payload.status !== true || !payload.data) {
                throw new Error(payload && payload.message ? payload.message : 'Invalid response while loading refund metrics');
            }

            const metrics = payload.data;
            console.log('[Refund Analytics] Metrics data:', metrics);

            // Render refund metrics cards
            renderRefundMetricsCards(metrics);

            // Render refund chart if data is available
            if (metrics.monthlyRefundChart && Array.isArray(metrics.monthlyRefundChart)) {
                renderRefundChart(metrics.monthlyRefundChart);
            }

            console.log('[Refund Analytics] Refund metrics loaded successfully');
        })
        .catch(error => {
            console.error('[Refund Analytics] Error loading refund metrics:', error);
            showErrorMessage(error.message || 'Failed to load refund metrics');
        });
    }

    /**
     * Render refund metrics cards
     * @param {Object} metrics - Refund metrics data
     */
    function renderRefundMetricsCards(metrics) {
        // Update metric cards with formatted currency
        const totalRefunds = q('total-refunds');
        const monthlyRefunds = q('monthly-refunds');
        const refundRate = q('refund-rate');
        const instantRefunds = q('instant-refunds');
        const normalRefunds = q('normal-refunds');
        const avgProcessingTime = q('avg-processing-time');

        if (totalRefunds) {
            totalRefunds.textContent = formatCurrency(metrics.totalRefunds || 0);
        }
        if (monthlyRefunds) {
            monthlyRefunds.textContent = formatCurrency(metrics.monthlyRefunds || 0);
        }
        if (refundRate) {
            refundRate.textContent = (metrics.refundRate || 0).toFixed(2) + '%';
        }
        if (instantRefunds) {
            instantRefunds.textContent = (metrics.instantRefundCount || 0).toLocaleString();
        }
        if (normalRefunds) {
            normalRefunds.textContent = (metrics.normalRefundCount || 0).toLocaleString();
        }
        if (avgProcessingTime) {
            avgProcessingTime.textContent = (metrics.averageProcessingTimeHours || 0).toFixed(1) + 'h';
        }
    }

    /**
     * Format currency value
     * @param {number} value - The value to format
     * @returns {string} Formatted currency string
     */
    function formatCurrency(value) {
        if (typeof value !== 'number') value = 0;
        return '₹' + value.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    /**
     * Render refund chart using Chart.js
     * @param {Array} monthlyData - Array of monthly refund data
     */
    function renderRefundChart(monthlyData) {
        const canvas = q('refund-chart');
        if (!canvas) return;

        if (typeof Chart === 'undefined') {
            console.warn('Chart.js not available');
            return;
        }

        // Destroy existing chart if any
        if (state.refundChart) {
            try {
                state.refundChart.destroy();
                state.refundChart = null;
            } catch(e) {
                console.warn('Failed to destroy previous refund chart', e);
            }
        }

        // Prepare data for chart
        const labels = monthlyData.map(item => {
            try {
                const date = new Date(item.month + '-01');
                return date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
            } catch(e) {
                return item.month;
            }
        });

        const data = monthlyData.map(item => item.refundAmount || 0);

        const ctx = canvas.getContext('2d');

        // Create gradient fill
        let gradient;
        try {
            gradient = ctx.createLinearGradient(0, 0, 0, canvas.height || 260);
            gradient.addColorStop(0, 'rgba(239, 68, 68, 0.22)');
            gradient.addColorStop(1, 'rgba(239, 68, 68, 0.02)');
        } catch(e) {
            gradient = 'rgba(239, 68, 68, 0.12)';
        }

        // Get theme colors
        const lineColor = '#ef4444';
        const gridColor = getComputedStyle(document.documentElement).getPropertyValue('--card-border').trim() || 'rgba(239,68,68,0.2)';
        const tickColor = getComputedStyle(document.documentElement).getPropertyValue('--text-secondary').trim() || '#9ca3af';

        state.refundChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Refunds',
                    data: data,
                    borderColor: lineColor,
                    backgroundColor: gradient,
                    fill: true,
                    cubicInterpolationMode: 'monotone',
                    tension: 0.4,
                    borderWidth: 2.5,
                    pointRadius: 3,
                    pointHoverRadius: 5,
                    pointBackgroundColor: lineColor,
                    pointBorderColor: '#fff',
                    pointBorderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 700, easing: 'easeOutCubic' },
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        enabled: true,
                        backgroundColor: 'rgba(17, 24, 39, 0.9)',
                        titleColor: '#ffffff',
                        bodyColor: '#ffffff',
                        displayColors: false,
                        padding: 10,
                        callbacks: {
                            label: function(context) {
                                return 'Refunds: ' + formatCurrency(context.parsed.y);
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { display: false, drawBorder: false },
                        ticks: { color: tickColor, maxRotation: 0 }
                    },
                    y: {
                        beginAtZero: true,
                        grid: { color: gridColor, drawBorder: false },
                        ticks: {
                            color: tickColor,
                            callback: function(value) {
                                return '₹' + value.toLocaleString('en-IN');
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Load refund history from the API with pagination and filters
     * @param {number} page - The page number to load
     * @param {number} pageSize - The number of refunds per page
     * @param {string} statusFilter - Optional status filter
     * @param {string} startDate - Optional start date filter
     * @param {string} endDate - Optional end date filter
     */
    function loadRefundHistory(page, pageSize, statusFilter, startDate, endDate) {
        page = page || 1;
        pageSize = pageSize || 20;
        statusFilter = statusFilter || '';
        startDate = startDate || '';
        endDate = endDate || '';

        // Show loader
        const loader = q('refunds-loader');
        if (loader) loader.style.display = 'block';

        // Build URL with query parameters
        let url = `/admin/refunds/history?page=${encodeURIComponent(page)}&pageSize=${encodeURIComponent(pageSize)}`;
        if (statusFilter) {
            url += `&status=${encodeURIComponent(statusFilter)}`;
        }
        if (startDate) {
            url += `&startDate=${encodeURIComponent(startDate)}`;
        }
        if (endDate) {
            url += `&endDate=${encodeURIComponent(endDate)}`;
        }

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        fetch(url, {
            method: 'GET',
            credentials: 'include',
            headers
        })
        .then(response => {
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to access refund history');
                }
                throw new Error('Failed to load refund history');
            }
            return response.json();
        })
        .then(payload => {
            if (!payload || payload.status !== true || !payload.data) {
                throw new Error(payload && payload.message ? payload.message : 'Invalid response while loading refund history');
            }

            const data = payload.data;
            const refunds = Array.isArray(data.refunds) ? data.refunds : [];

            // Update state
            state.currentPage = data.pagination?.page || page;
            state.pageSize = data.pagination?.pageSize || pageSize;
            state.totalPages = data.pagination?.totalPages || 1;
            state.totalCount = data.pagination?.totalCount || 0;
            state.statusFilter = statusFilter;
            state.startDate = startDate;
            state.endDate = endDate;

            // Hide loader
            if (loader) loader.style.display = 'none';

            // Render refunds table
            renderRefundHistoryTable(refunds);

            // Render pagination
            renderRefundsPagination();

            console.log('Loaded refund history:', refunds);
        })
        .catch(error => {
            console.error('Error loading refund history:', error);
            if (loader) loader.style.display = 'none';
            showErrorMessage(error.message || 'Failed to load refund history');
        });
    }

    /**
     * Render the refund history table
     * @param {Array} refunds - Array of refund objects
     */
    function renderRefundHistoryTable(refunds) {
        const tableBody = q('refunds-table-body');
        if (!tableBody) return;

        const fragment = document.createDocumentFragment();

        if (refunds.length === 0) {
            const row = document.createElement('tr');
            const cell = document.createElement('td');
            cell.colSpan = 8;
            cell.textContent = 'No refunds found';
            cell.style.textAlign = 'center';
            row.appendChild(cell);
            fragment.appendChild(row);
            tableBody.replaceChildren(fragment);
            return;
        }

        refunds.forEach(refund => {
            const row = document.createElement('tr');

            // Refund ID
            const refundIdCell = document.createElement('td');
            refundIdCell.textContent = refund.refundId || 'N/A';
            refundIdCell.style.fontFamily = 'monospace';
            refundIdCell.style.fontSize = '0.85em';
            row.appendChild(refundIdCell);

            // User Email
            const emailCell = document.createElement('td');
            emailCell.textContent = refund.userEmail || 'N/A';
            row.appendChild(emailCell);

            // Amount
            const amountCell = document.createElement('td');
            if (refund.amount != null) {
                amountCell.textContent = formatCurrency(refund.amount);
            } else {
                amountCell.textContent = '-';
            }
            row.appendChild(amountCell);

            // Status with badge
            const statusCell = document.createElement('td');
            const status = refund.status || 'UNKNOWN';
            const statusBadge = document.createElement('span');
            statusBadge.className = 'status-badge';
            
            // Add status-specific class
            if (status === 'PROCESSED') {
                statusBadge.classList.add('status-processed');
            } else if (status === 'FAILED') {
                statusBadge.classList.add('status-failed');
            } else if (status === 'PENDING') {
                statusBadge.classList.add('status-pending');
            }

            statusBadge.textContent = status;
            statusCell.appendChild(statusBadge);
            row.appendChild(statusCell);

            // Type (speed)
            const typeCell = document.createElement('td');
            const speed = refund.speedProcessed || refund.speedRequested || 'N/A';
            typeCell.textContent = speed.charAt(0).toUpperCase() + speed.slice(1).toLowerCase();
            row.appendChild(typeCell);

            // Processed By
            const processedByCell = document.createElement('td');
            processedByCell.textContent = refund.processedBy || 'N/A';
            row.appendChild(processedByCell);

            // Date
            const dateCell = document.createElement('td');
            dateCell.textContent = formatDate(refund.createdAt);
            row.appendChild(dateCell);

            // Actions
            const actionsCell = document.createElement('td');
            actionsCell.className = 'refund-actions';
            const viewBtn = document.createElement('button');
            viewBtn.className = 'btn-details btn-sm';
            viewBtn.textContent = 'View';
            viewBtn.addEventListener('click', function() {
                viewRefundDetails(refund.refundId);
            });
            actionsCell.appendChild(viewBtn);
            row.appendChild(actionsCell);

            fragment.appendChild(row);
        });

        tableBody.replaceChildren(fragment);
    }

    /**
     * Render pagination controls for refunds
     */
    function renderRefundsPagination() {
        const paginationContainer = q('refunds-pagination');
        if (!paginationContainer) return;

        paginationContainer.innerHTML = '';

        if (state.totalPages <= 1) {
            return;
        }

        // Previous button
        const prevButton = document.createElement('button');
        prevButton.className = `pagination-button ${state.currentPage === 1 ? 'disabled' : ''}`;
        prevButton.textContent = 'Previous';
        prevButton.disabled = state.currentPage === 1;
        prevButton.addEventListener('click', () => {
            if (state.currentPage > 1) {
                loadRefundHistory(state.currentPage - 1, state.pageSize, state.statusFilter, state.startDate, state.endDate);
            }
        });
        paginationContainer.appendChild(prevButton);

        // Page buttons
        const maxButtons = 5;
        const startPage = Math.max(1, state.currentPage - Math.floor(maxButtons / 2));
        const endPage = Math.min(state.totalPages, startPage + maxButtons - 1);

        for (let i = startPage; i <= endPage; i++) {
            const pageButton = document.createElement('button');
            pageButton.className = `pagination-button ${i === state.currentPage ? 'active' : ''}`;
            pageButton.textContent = i;
            pageButton.addEventListener('click', () => {
                if (i !== state.currentPage) {
                    loadRefundHistory(i, state.pageSize, state.statusFilter, state.startDate, state.endDate);
                }
            });
            paginationContainer.appendChild(pageButton);
        }

        // Next button
        const nextButton = document.createElement('button');
        nextButton.className = `pagination-button ${state.currentPage === state.totalPages ? 'disabled' : ''}`;
        nextButton.textContent = 'Next';
        nextButton.disabled = state.currentPage === state.totalPages;
        nextButton.addEventListener('click', () => {
            if (state.currentPage < state.totalPages) {
                loadRefundHistory(state.currentPage + 1, state.pageSize, state.statusFilter, state.startDate, state.endDate);
            }
        });
        paginationContainer.appendChild(nextButton);
    }

    /**
     * Filter refunds based on status and date range
     */
    function filterRefunds() {
        const statusFilter = q('refund-status-filter');
        const dateFrom = q('refund-date-from');
        const dateTo = q('refund-date-to');

        const status = statusFilter ? statusFilter.value : '';
        const startDate = dateFrom ? dateFrom.value.trim() : '';
        const endDate = dateTo ? dateTo.value.trim() : '';

        // Validate date range if both dates are provided
        if (startDate && endDate) {
            const dateValidation = ValidationUtils.validateDateRange(startDate, endDate);
            if (!dateValidation.valid) {
                showErrorMessage(dateValidation.message);
                return;
            }
        }

        // Validate individual dates if only one is provided
        if (startDate && !ValidationUtils.isValidDate(startDate)) {
            showErrorMessage('Invalid start date format. Use YYYY-MM-DD');
            return;
        }

        if (endDate && !ValidationUtils.isValidDate(endDate)) {
            showErrorMessage('Invalid end date format. Use YYYY-MM-DD');
            return;
        }

        // Load refund history with filters
        loadRefundHistory(1, state.pageSize, status, startDate, endDate);
    }

    /**
     * Export refunds to CSV
     * @param {string} startDate - Start date for export
     * @param {string} endDate - End date for export
     */
    function exportRefunds(startDate, endDate) {
        if (!startDate || !endDate) {
            showErrorMessage('Please select both start and end dates for export');
            return;
        }

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'text/csv' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        const url = `/admin/refunds/export?startDate=${encodeURIComponent(startDate)}&endDate=${encodeURIComponent(endDate)}`;

        fetch(url, {
            method: 'GET',
            credentials: 'include',
            headers
        })
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to export refunds');
            }
            return response.text();
        })
        .then(csvData => {
            downloadCSV(csvData, `refunds_${startDate}_to_${endDate}.csv`);
            showMessage('success', 'Refunds exported successfully');
        })
        .catch(error => {
            console.error('Error exporting refunds:', error);
            showErrorMessage(error.message || 'Failed to export refunds');
        });
    }

    /**
     * Download CSV data as a file
     * @param {string} csvData - CSV data string
     * @param {string} filename - Filename for download
     */
    function downloadCSV(csvData, filename) {
        const blob = new Blob([csvData], { type: 'text/csv' });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
    }

    /**
     * View refund details in a modal
     * @param {string} refundId - The refund ID to view
     */
    function viewRefundDetails(refundId) {
        if (!refundId) {
            showErrorMessage('Refund ID is required');
            return;
        }

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        // Show loading modal
        showModal('Refund Details', '<div style="text-align: center; padding: 2rem;">Loading refund details...</div>');

        fetch(`/admin/refunds/${encodeURIComponent(refundId)}`, {
            method: 'GET',
            credentials: 'include',
            headers
        })
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to load refund details');
            }
            return response.json();
        })
        .then(payload => {
            if (!payload || payload.status !== true || !payload.data) {
                throw new Error(payload && payload.message ? payload.message : 'Invalid response');
            }

            const refund = payload.data;

            // Build refund details HTML
            const detailsHtml = `
                <div class="refund-details-content">
                    <div class="refund-detail-section" style="background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 1rem; margin-bottom: 1rem;">
                        <h3 style="margin: 0 0 0.75rem 0; color: var(--card-title); font-size: 1rem;">Refund Information</h3>
                        <div style="display: grid; gap: 0.5rem; font-size: 0.9rem;">
                            <div><strong>Refund ID:</strong> <code style="background: rgba(0,0,0,0.2); padding: 2px 6px; border-radius: 4px;">${escapeHtml(refund.refundId)}</code></div>
                            <div><strong>Payment ID:</strong> <code style="background: rgba(0,0,0,0.2); padding: 2px 6px; border-radius: 4px;">${escapeHtml(refund.paymentId)}</code></div>
                            <div><strong>Amount:</strong> ${formatCurrency(refund.amount)}</div>
                            <div><strong>Currency:</strong> ${escapeHtml(refund.currency || 'INR')}</div>
                            <div><strong>Status:</strong> <span class="badge" style="background: ${refund.status === 'PROCESSED' ? '#10b981' : refund.status === 'FAILED' ? '#ef4444' : '#f59e0b'}">${escapeHtml(refund.status)}</span></div>
                            <div><strong>Speed Requested:</strong> ${escapeHtml((refund.speedRequested || 'N/A').charAt(0).toUpperCase() + (refund.speedRequested || 'N/A').slice(1).toLowerCase())}</div>
                            <div><strong>Speed Processed:</strong> ${escapeHtml((refund.speedProcessed || 'N/A').charAt(0).toUpperCase() + (refund.speedProcessed || 'N/A').slice(1).toLowerCase())}</div>
                        </div>
                    </div>

                    <div class="refund-detail-section" style="background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 1rem; margin-bottom: 1rem;">
                        <h3 style="margin: 0 0 0.75rem 0; color: var(--card-title); font-size: 1rem;">User Information</h3>
                        <div style="display: grid; gap: 0.5rem; font-size: 0.9rem;">
                            <div><strong>User Email:</strong> ${escapeHtml(refund.userEmail)}</div>
                            <div><strong>Processed By:</strong> ${escapeHtml(refund.processedBy)}</div>
                        </div>
                    </div>

                    <div class="refund-detail-section" style="background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 1rem; margin-bottom: 1rem;">
                        <h3 style="margin: 0 0 0.75rem 0; color: var(--card-title); font-size: 1rem;">Timestamps</h3>
                        <div style="display: grid; gap: 0.5rem; font-size: 0.9rem;">
                            <div><strong>Created At:</strong> ${formatDate(refund.createdAt)}</div>
                            ${refund.processedAt ? `<div><strong>Processed At:</strong> ${formatDate(refund.processedAt)}</div>` : ''}
                        </div>
                    </div>

                    ${refund.reason ? `
                    <div class="refund-detail-section" style="background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 1rem;">
                        <h3 style="margin: 0 0 0.75rem 0; color: var(--card-title); font-size: 1rem;">Reason</h3>
                        <div style="font-size: 0.9rem; color: var(--text-color);">${escapeHtml(refund.reason)}</div>
                    </div>
                    ` : ''}
                </div>
            `;

            showModal('Refund Details', detailsHtml);
        })
        .catch(error => {
            console.error('Error loading refund details:', error);
            showModal('Error', `<div style="color: #ef4444; text-align: center; padding: 2rem;">${escapeHtml(error.message || 'Failed to load refund details')}</div>`);
        });
    }

    /**
     * Initialize Refund Analytics section
     */
    function ensureInitialized() {
        console.log('[Refund Analytics] ensureInitialized called, state.initialized:', state.initialized);
        if (state.initialized) {
            console.log('[Refund Analytics] Already initialized, skipping');
            return;
        }
        const panel = q('finance');
        if (!panel) {
            console.warn('[Refund Analytics] Finance panel not found');
            return;
        }

        console.log('[Refund Analytics] Initializing refund analytics...');

        // Load refund metrics
        console.log('[Refund Analytics] Loading refund metrics...');
        loadRefundMetrics();

        // Load refund history with default pagination
        console.log('[Refund Analytics] Loading refund history...');
        loadRefundHistory(1, 20, '', '', '');

        // Bind status filter
        const statusFilter = q('refund-status-filter');
        if (statusFilter && !statusFilter.dataset.bound) {
            statusFilter.addEventListener('change', filterRefunds);
            statusFilter.dataset.bound = 'true';
        }

        // Bind date filters
        const dateFrom = q('refund-date-from');
        if (dateFrom && !dateFrom.dataset.bound) {
            dateFrom.addEventListener('change', filterRefunds);
            dateFrom.dataset.bound = 'true';
        }

        const dateTo = q('refund-date-to');
        if (dateTo && !dateTo.dataset.bound) {
            dateTo.addEventListener('change', filterRefunds);
            dateTo.dataset.bound = 'true';
        }

        // Bind export button
        const exportBtn = q('export-refunds-btn');
        if (exportBtn && !exportBtn.dataset.bound) {
            exportBtn.addEventListener('click', function() {
                const startDate = dateFrom ? dateFrom.value.trim() : '';
                const endDate = dateTo ? dateTo.value.trim() : '';
                exportRefunds(startDate, endDate);
            });
            exportBtn.dataset.bound = 'true';
        }

        state.initialized = true;
        console.log('[Refund Analytics] Refund analytics initialized successfully');
    }

    // Bind initialization to finance tab activation
    document.addEventListener('DOMContentLoaded', function() {
        try {
            const tab = document.querySelector('[data-tab="finance"]');
            if (tab && !tab.dataset.refundBound) {
                tab.addEventListener('click', ensureInitialized);
                tab.dataset.refundBound = 'true';
            }
            // If already active, init immediately
            const panel = q('finance');
            if (panel && panel.classList.contains('active')) {
                ensureInitialized();
            }
        } catch(e) { console.warn('Refund analytics tab binding failed', e); }
    });

    // Expose functions globally for testing/debugging
    window.RefundAnalyticsModule = {
        ensureInitialized,
        loadRefundMetrics,
        loadRefundHistory,
        filterRefunds,
        renderRefundChart,
        exportRefunds,
        viewRefundDetails
    };
})();


// ================= Financial Export Tool =================
(function(){
    function q(id){ return document.getElementById(id); }

    /**
     * Show the financial export modal
     */
    function showFinancialExportModal() {
        const content = `
            <div class="export-form" style="display:grid; gap:16px;">
                <div class="message info-message">
                    Export payment and subscription records to CSV format for financial reconciliation and reporting.
                </div>

                <div style="display:grid; gap:8px;">
                    <label style="color:var(--text-secondary);font-weight:600;">Export Type</label>
                    <div class="radio-group" style="display:grid;gap:8px;">
                        <label style="display:flex;align-items:center;gap:8px;cursor:pointer;padding:8px;border:1px solid var(--card-border);border-radius:6px;transition:background 0.2s;">
                            <input type="radio" name="export-type" value="payments" checked
                                   style="width:18px;height:18px;cursor:pointer;" />
                            <span style="color:var(--text-color);">Payments</span>
                        </label>
                        <label style="display:flex;align-items:center;gap:8px;cursor:pointer;padding:8px;border:1px solid var(--card-border);border-radius:6px;transition:background 0.2s;">
                            <input type="radio" name="export-type" value="subscriptions"
                                   style="width:18px;height:18px;cursor:pointer;" />
                            <span style="color:var(--text-color);">Subscriptions</span>
                        </label>
                        <label style="display:flex;align-items:center;gap:8px;cursor:pointer;padding:8px;border:1px solid var(--card-border);border-radius:6px;transition:background 0.2s;">
                            <input type="radio" name="export-type" value="both"
                                   style="width:18px;height:18px;cursor:pointer;" />
                            <span style="color:var(--text-color);">Both</span>
                        </label>
                    </div>
                </div>

                <div style="display:grid; gap:8px;">
                    <label for="export-start-date" style="color:var(--text-secondary);font-weight:600;">Start Date <span style="color:#ef4444;">*</span></label>
                    <input type="date" id="export-start-date" class="form-control" required
                           title="Please select a start date" />
                </div>

                <div style="display:grid; gap:8px;">
                    <label for="export-end-date" style="color:var(--text-secondary);font-weight:600;">End Date <span style="color:#ef4444;">*</span></label>
                    <input type="date" id="export-end-date" class="form-control" required
                           title="Please select an end date" />
                </div>

                <div id="export-error" class="message error-message" style="display:none;border-left:4px solid #ef4444;padding:12px 16px;display:flex;align-items:start;gap:12px;">
                    <span class="material-icons" style="color:#ef4444;font-size:20px;flex-shrink:0;">error</span>
                    <div style="flex:1;">
                        <div style="font-weight:600;margin-bottom:4px;color:#ef4444;">Error</div>
                        <div id="export-error-text" style="color:var(--text-color);font-size:14px;line-height:1.5;"></div>
                    </div>
                </div>

                <div style="display:flex;gap:8px;">
                    <button id="export-submit-btn" class="btn btn-primary" style="flex:1;">
                        Export to CSV
                    </button>
                    <button id="export-cancel-btn" class="btn btn-secondary">
                        Cancel
                    </button>
                </div>
                <div id="export-spinner" class="loading-spinner" style="display:none;margin:10px auto;"></div>
            </div>
        `;

        showModal('Export Financial Data', content);
        bindExportModalEvents();

        // Add hover effects to radio labels
        try {
            document.querySelectorAll('.radio-group label').forEach(label => {
                label.addEventListener('mouseenter', function() {
                    this.style.background = 'var(--hover-bg, rgba(99,102,241,0.05))';
                });
                label.addEventListener('mouseleave', function() {
                    this.style.background = 'transparent';
                });
            });
        } catch(e) { console.warn('Failed to bind radio hover effects', e); }
    }

    /**
     * Export financial data to CSV
     */
    function exportFinancialData() {
        const startDateInput = q('export-start-date');
        const endDateInput = q('export-end-date');
        const errorEl = q('export-error');
        const errorTextEl = q('export-error-text');
        const submitBtn = q('export-submit-btn');
        const spinner = q('export-spinner');

        // Get selected export type
        const exportTypeRadio = document.querySelector('input[name="export-type"]:checked');
        const exportType = exportTypeRadio ? exportTypeRadio.value : 'payments';

        // Get date range
        const startDate = startDateInput ? startDateInput.value.trim() : '';
        const endDate = endDateInput ? endDateInput.value.trim() : '';

        // Hide previous errors
        if (errorEl) errorEl.style.display = 'none';

        // Validate date range using ValidationUtils
        const dateValidation = ValidationUtils.validateDateRange(startDate, endDate);
        if (!dateValidation.valid) {
            if (errorEl && errorTextEl) {
                errorTextEl.textContent = dateValidation.message;
                errorEl.style.display = 'flex';
            }
            return;
        }

        // Show spinner and disable button
        if (spinner) spinner.style.display = 'block';
        if (submitBtn) submitBtn.disabled = true;

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = {
            'Accept': 'text/csv, application/json',
            'Content-Type': 'application/json'
        };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        const requestBody = {
            exportType: exportType.toUpperCase(),
            startDate: startDate,
            endDate: endDate
        };

        fetch('/admin/tools/export-financial-data', {
            method: 'POST',
            credentials: 'include',
            headers: headers,
            body: JSON.stringify(requestBody)
        })
        .then(response => {
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to export financial data');
                }
                // Try to parse error message
                return response.json().then(data => {
                    throw new Error(data.message || 'Failed to export financial data');
                }).catch(() => {
                    throw new Error('Failed to export financial data');
                });
            }

            // Check if response is CSV
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('text/csv')) {
                // Download CSV file
                return response.blob().then(blob => {
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;

                    // Generate filename with timestamp
                    const timestamp = new Date().toISOString().slice(0, 10);
                    const filename = `financial-export-${exportType}-${timestamp}.csv`;
                    a.download = filename;

                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    window.URL.revokeObjectURL(url);

                    showMessage('success', 'Financial data exported successfully');
                    closeModal();
                });
            } else {
                // Unexpected response type
                throw new Error('Unexpected response format');
            }
        })
        .catch(error => {
            console.error('Error exporting financial data:', error);

            // Provide user-friendly error messages
            let errorMessage = error.message || 'Failed to export financial data';

            if (errorMessage.includes('permission')) {
                errorMessage = 'You do not have permission to export financial data. Please contact an administrator.';
            } else if (errorMessage.includes('authentication') || errorMessage.includes('auth')) {
                errorMessage = 'Your session has expired. Please log in again.';
                setTimeout(() => {
                    window.location.href = '/admin/login?error=session_expired';
                }, 2000);
            } else if (errorMessage.includes('date range')) {
                errorMessage = 'The selected date range is invalid or too large. Please select a smaller range.';
            } else if (errorMessage.includes('date format')) {
                errorMessage = 'Invalid date format. Please select valid dates.';
            }

            if (errorEl && errorTextEl) {
                errorTextEl.textContent = errorMessage;
                errorEl.style.display = 'flex';
            }
            showMessage('error', errorMessage);
        })
        .finally(() => {
            if (spinner) spinner.style.display = 'none';
            if (submitBtn) submitBtn.disabled = false;
        });
    }

    /**
     * Bind export modal events
     */
    function bindExportModalEvents() {
        const submitBtn = q('export-submit-btn');
        const cancelBtn = q('export-cancel-btn');

        // Bind export button
        if (submitBtn) {
            submitBtn.addEventListener('click', exportFinancialData);
        }

        // Bind cancel button
        if (cancelBtn) {
            cancelBtn.addEventListener('click', closeModal);
        }
    }

    /**
     * Initialize export tool button binding
     */
    function initializeExportTool() {
        const exportBtn = q('export-financial-btn');
        const exportSpinner = q('export-financial-spinner');

        if (exportBtn && !exportBtn.dataset.bound) {
            exportBtn.addEventListener('click', function() {
                showFinancialExportModal();
            });
            exportBtn.dataset.bound = 'true';
        }
    }

    // Initialize on DOM ready
    document.addEventListener('DOMContentLoaded', function(){
        try {
            initializeExportTool();
        } catch(e) {
            console.warn('Financial export tool binding failed', e);
        }
    });

    // Expose functions globally for testing/debugging
    window.FinancialExportModule = {
        showFinancialExportModal,
        exportFinancialData
    };
})();


// ================= Refund Button Integration =================

/**
 * Fetch refund summary for a payment
 * @param {string} paymentId - The payment ID
 * @returns {Promise<Object>} Refund summary object (PaymentRefundSummary)
 */
async function fetchRefundSummary(paymentId) {
    try {
        const token = localStorage.getItem('jwt_token');
        if (!token) {
            throw new Error('Authentication required');
        }

        const response = await fetch(`/admin/refunds/payment/${encodeURIComponent(paymentId)}`, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Accept': 'application/json'
            },
            credentials: 'include'
        });

        if (!response.ok) {
            if (response.status === 401 || response.status === 403) {
                throw new Error('Authentication failed. Please login again.');
            }
            throw new Error(`Failed to fetch refund summary: ${response.status}`);
        }

        const data = await response.json();
        
        if (!data || data.status !== true || !data.data) {
            throw new Error(data && data.message ? data.message : 'Invalid response from server');
        }

        return data.data; // Returns PaymentRefundSummary object
    } catch (error) {
        console.error('Error fetching refund summary:', error);
        throw error;
    }
}

/**
 * Format currency amount from paise to rupees with symbol
 * @param {number} amountPaise - Amount in paise
 * @returns {string} Formatted currency string (e.g., "₹1,234.56")
 */
function formatCurrency(amountPaise) {
    if (typeof amountPaise !== 'number' || isNaN(amountPaise)) {
        return '₹0.00';
    }
    const rupees = amountPaise / 100;
    return '₹' + rupees.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/**
 * Render refund button or badge based on payment refund status
 * @param {string} paymentId - The payment ID to check
 * @param {HTMLElement} container - Container element to render into
 */
async function renderRefundButton(paymentId, container) {
    if (!paymentId || !container) {
        console.warn('renderRefundButton: Missing paymentId or container');
        return;
    }

    try {
        // Fetch refund summary for payment
        const summary = await fetchRefundSummary(paymentId);
        
        // Clear container
        container.innerHTML = '';
        
        if (summary.isFullyRefunded) {
            // Show "Fully Refunded" badge (clickable to view history)
            const badge = document.createElement('span');
            badge.className = 'badge badge-refunded';
            badge.textContent = `Fully Refunded: ${formatCurrency(summary.totalRefunded)}`;
            badge.style.cursor = 'pointer';
            badge.title = 'Click to view refund history';
            badge.addEventListener('click', function() {
                viewRefundHistory(paymentId);
            });
            container.appendChild(badge);
        } else if (summary.totalRefunded > 0) {
            // Show "Partially Refunded" badge + refund button
            const wrapper = document.createElement('div');
            wrapper.style.display = 'flex';
            wrapper.style.gap = '8px';
            wrapper.style.alignItems = 'center';
            wrapper.style.flexWrap = 'wrap';
            
            const badge = document.createElement('span');
            badge.className = 'badge badge-partial-refund';
            badge.textContent = `Partially Refunded: ${formatCurrency(summary.totalRefunded)}`;
            badge.style.cursor = 'pointer';
            badge.title = 'Click to view refund history';
            badge.addEventListener('click', function() {
                viewRefundHistory(paymentId);
            });
            
            const button = document.createElement('button');
            button.className = 'btn btn-sm btn-refund';
            button.textContent = 'Refund Remaining';
            button.addEventListener('click', function() {
                openRefundModal(paymentId);
            });
            
            wrapper.appendChild(badge);
            wrapper.appendChild(button);
            container.appendChild(wrapper);
        } else {
            // Show refund button only
            const button = document.createElement('button');
            button.className = 'btn btn-sm btn-refund';
            button.textContent = 'Refund';
            button.addEventListener('click', function() {
                openRefundModal(paymentId);
            });
            container.appendChild(button);
        }
    } catch (error) {
        console.error('Error rendering refund button:', error);
        // Show error state or fallback UI
        container.innerHTML = '<span style="color: var(--text-secondary); font-size: 0.875rem;">Refund unavailable</span>';
    }
}

/**
 * Open refund modal for a payment
 * @param {string} paymentId - The payment ID to refund
 */
async function openRefundModal(paymentId) {
    if (!paymentId) {
        showMessage('error', 'Payment ID is required');
        return;
    }

    try {
        // Fetch payment and refund details
        const summary = await fetchRefundSummary(paymentId);
        
        // Build modal content
        const modalContent = `
            <div class="refund-modal-content">
                <div class="refund-details">
                    <div class="detail-row">
                        <span class="label">Payment ID:</span>
                        <span class="value">${escapeHtml(paymentId)}</span>
                    </div>
                    <div class="detail-row">
                        <span class="label">Original Amount:</span>
                        <span class="value">${formatCurrency(summary.originalAmount)}</span>
                    </div>
                    <div class="detail-row">
                        <span class="label">Already Refunded:</span>
                        <span class="value">${formatCurrency(summary.totalRefunded)}</span>
                    </div>
                    <div class="detail-row">
                        <span class="label">Refundable Amount:</span>
                        <span class="value refundable">${formatCurrency(summary.remainingRefundable)}</span>
                    </div>
                </div>
                
                <form id="refund-form" class="refund-form">
                    <div class="form-group">
                        <label>Refund Type</label>
                        <div class="radio-group">
                            <label>
                                <input type="radio" name="refundType" value="full" checked 
                                       onchange="togglePartialAmount()">
                                Full Refund
                            </label>
                            <label>
                                <input type="radio" name="refundType" value="partial" 
                                       onchange="togglePartialAmount()">
                                Partial Refund
                            </label>
                        </div>
                    </div>
                    
                    <div id="partial-amount-group" class="form-group" style="display:none;">
                        <label for="refund-amount">Refund Amount (₹)</label>
                        <input type="number" id="refund-amount" name="amount" 
                               min="1" max="${(summary.remainingRefundable / 100).toFixed(2)}" 
                               step="0.01" placeholder="Enter amount in rupees">
                    </div>
                    
                    <div class="form-group">
                        <label for="refund-speed">Refund Speed</label>
                        <select id="refund-speed" name="speed">
                            <option value="NORMAL">Normal (5-7 days)</option>
                            <option value="OPTIMUM">Instant (if available)</option>
                        </select>
                    </div>
                    
                    <div class="form-group">
                        <label for="refund-reason">Reason (Optional)</label>
                        <textarea id="refund-reason" name="reason" rows="3" 
                                  placeholder="Enter reason for refund"></textarea>
                    </div>
                    
                    <div class="form-actions">
                        <button type="button" class="btn btn-secondary" onclick="closeModal()">
                            Cancel
                        </button>
                        <button type="submit" class="btn btn-primary">
                            Process Refund
                        </button>
                    </div>
                </form>
            </div>
        `;
        
        showModal('Process Refund', modalContent);
        
        // Bind form submission
        const form = document.getElementById('refund-form');
        if (form) {
            form.addEventListener('submit', function(e) {
                e.preventDefault();
                submitRefund(paymentId, summary.remainingRefundable);
            });
        }
        
    } catch (error) {
        console.error('Failed to open refund modal:', error);
        showMessage('error', error.message || 'Failed to load refund details');
    }
}

/**
 * Toggle partial amount input visibility based on refund type selection
 */
function togglePartialAmount() {
    const refundTypeRadios = document.getElementsByName('refundType');
    let refundType = 'full';
    
    // Find selected radio button
    for (const radio of refundTypeRadios) {
        if (radio.checked) {
            refundType = radio.value;
            break;
        }
    }
    
    const partialGroup = document.getElementById('partial-amount-group');
    const amountInput = document.getElementById('refund-amount');
    
    if (partialGroup) {
        if (refundType === 'partial') {
            partialGroup.style.display = 'block';
        } else {
            partialGroup.style.display = 'none';
            // Clear partial amount value when switching to full refund
            if (amountInput) {
                amountInput.value = '';
            }
        }
    }
}

/**
 * Submit refund request
 * @param {string} paymentId - The payment ID
 * @param {number} maxRefundable - Maximum refundable amount in paise
 */
async function submitRefund(paymentId, maxRefundable) {
    const form = document.getElementById('refund-form');
    if (!form) {
        showMessage('error', 'Refund form not found');
        return;
    }
    
    const formData = new FormData(form);
    
    const refundType = formData.get('refundType');
    const speed = formData.get('speed');
    const reason = formData.get('reason');
    
    // Calculate amount in paise
    let amountPaise = null;
    if (refundType === 'partial') {
        const amountRupees = parseFloat(formData.get('amount'));
        if (isNaN(amountRupees) || amountRupees <= 0) {
            showMessage('error', 'Please enter a valid refund amount');
            return;
        }
        amountPaise = Math.round(amountRupees * 100);
        
        if (amountPaise > maxRefundable) {
            showMessage('error', 'Refund amount exceeds refundable amount');
            return;
        }
    }
    // For full refund, amountPaise remains null (backend will use full amount)
    
    const requestBody = {
        paymentId: paymentId,
        amount: amountPaise,
        speed: speed,
        reason: reason || null
    };
    
    try {
        const token = localStorage.getItem('jwt_token');
        if (!token) {
            throw new Error('Authentication required');
        }
        
        const response = await fetch('/admin/refunds/initiate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + token,
                'Accept': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(requestBody)
        });
        
        const data = await response.json();
        
        if (data.status === true && data.data && data.data.success) {
            showMessage('success', data.data.message || 'Refund processed successfully');
            closeModal();
            // Refresh the subscription view if the function exists
            if (typeof loadSubscriptions === 'function') {
                loadSubscriptions();
            }
        } else {
            showMessage('error', (data.data && data.data.message) || data.message || 'Refund failed');
        }
    } catch (error) {
        console.error('Refund submission failed:', error);
        showMessage('error', error.message || 'Failed to process refund');
    }
}

/**
 * Render refund status badge for a payment
 * Displays "Fully Refunded" or "Partially Refunded" badge with amounts
 * Makes badges clickable to view refund history
 * 
 * @param {string} paymentId - The payment ID to check
 * @param {HTMLElement} container - Container element to render into
 */
async function renderRefundStatus(paymentId, container) {
    if (!paymentId || !container) {
        console.warn('renderRefundStatus: Missing paymentId or container');
        return;
    }

    try {
        // Fetch refund summary for payment
        const summary = await fetchRefundSummary(paymentId);
        
        // Clear container
        container.innerHTML = '';
        
        if (summary.isFullyRefunded) {
            // Display "Fully Refunded" badge with amount
            const badge = document.createElement('span');
            badge.className = 'badge badge-refunded';
            badge.textContent = `Fully Refunded: ${formatCurrency(summary.totalRefunded)}`;
            badge.style.cursor = 'pointer';
            badge.title = 'Click to view refund history';
            badge.addEventListener('click', function() {
                viewRefundHistory(paymentId);
            });
            container.appendChild(badge);
        } else if (summary.totalRefunded > 0) {
            // Display "Partially Refunded" badge with amounts
            const badge = document.createElement('span');
            badge.className = 'badge badge-partial-refund';
            badge.textContent = `Partially Refunded: ${formatCurrency(summary.totalRefunded)} / ${formatCurrency(summary.originalAmount)}`;
            badge.style.cursor = 'pointer';
            badge.title = 'Click to view refund history';
            badge.addEventListener('click', function() {
                viewRefundHistory(paymentId);
            });
            container.appendChild(badge);
        }
        // If no refunds, don't display any badge
    } catch (error) {
        console.error('Error rendering refund status:', error);
        // Silently fail - don't show error badge for refund status
    }
}

/**
 * View refund history for a payment in a modal
 * Fetches refund summary and displays all refunds with details
 * 
 * @param {string} paymentId - The payment ID
 */
async function viewRefundHistory(paymentId) {
    if (!paymentId) {
        showMessage('error', 'Payment ID is required');
        return;
    }

    try {
        // Show loading modal
        showModal('Refund History', '<div style="text-align: center; padding: 2rem; color: var(--text-secondary);">Loading refund history...</div>');
        
        // Fetch refund summary for payment
        const summary = await fetchRefundSummary(paymentId);
        
        // Build payment summary section
        const summaryHtml = `
            <div class="summary-section" style="background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 1rem; margin-bottom: 1.5rem;">
                <h3 style="margin: 0 0 0.75rem 0; color: var(--card-title); font-size: 1rem; font-weight: 600;">Payment Summary</h3>
                <div style="display: grid; gap: 0.5rem;">
                    <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--card-border);">
                        <span style="font-weight: 600; color: var(--text-secondary);">Payment ID:</span>
                        <span style="color: var(--text-color);"><code style="background: rgba(0,0,0,0.1); padding: 2px 6px; border-radius: 4px;">${escapeHtml(paymentId)}</code></span>
                    </div>
                    <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--card-border);">
                        <span style="font-weight: 600; color: var(--text-secondary);">Original Amount:</span>
                        <span style="color: var(--text-color); font-weight: 600;">${formatCurrency(summary.originalAmount)}</span>
                    </div>
                    <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--card-border);">
                        <span style="font-weight: 600; color: var(--text-secondary);">Total Refunded:</span>
                        <span style="color: #ef4444; font-weight: 600;">${formatCurrency(summary.totalRefunded)}</span>
                    </div>
                    <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0;">
                        <span style="font-weight: 600; color: var(--text-secondary);">Remaining:</span>
                        <span style="color: #10b981; font-weight: 600;">${formatCurrency(summary.remainingRefundable)}</span>
                    </div>
                </div>
            </div>
        `;
        
        // Build refunds list section
        let refundsHtml = '';
        if (summary.refunds && summary.refunds.length > 0) {
            refundsHtml = `
                <div class="refunds-section">
                    <h3 style="margin: 0 0 1rem 0; color: var(--card-title); font-size: 1rem; font-weight: 600;">Refund History (${summary.refunds.length})</h3>
                    <div style="display: grid; gap: 1rem;">
                        ${summary.refunds.map(refund => `
                            <div class="refund-history-item" style="background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 1rem;">
                                <div class="refund-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem; padding-bottom: 0.75rem; border-bottom: 1px solid var(--card-border);">
                                    <span class="refund-id" style="font-family: 'Courier New', monospace; font-size: 0.875rem; color: var(--text-color); font-weight: 600;">${escapeHtml(refund.refundId)}</span>
                                    <span class="badge badge-${refund.status.toLowerCase()}" style="padding: 0.25rem 0.75rem; border-radius: 12px; font-size: 0.75rem; font-weight: 600; ${
                                        refund.status === 'PROCESSED' ? 'background: #d1fae5; color: #065f46;' :
                                        refund.status === 'FAILED' ? 'background: #fee2e2; color: #991b1b;' :
                                        'background: #fef3c7; color: #92400e;'
                                    }">${escapeHtml(refund.status)}</span>
                                </div>
                                <div class="refund-details" style="display: grid; gap: 0.5rem; font-size: 0.875rem;">
                                    <div style="display: flex; justify-content: space-between;">
                                        <span style="color: var(--text-secondary);">Amount:</span>
                                        <span style="color: var(--text-color); font-weight: 600;">${formatCurrency(refund.amount * 100)}</span>
                                    </div>
                                    <div style="display: flex; justify-content: space-between;">
                                        <span style="color: var(--text-secondary);">Type:</span>
                                        <span style="color: var(--text-color);">${escapeHtml((refund.speedProcessed || refund.speedRequested || 'N/A').charAt(0).toUpperCase() + (refund.speedProcessed || refund.speedRequested || 'N/A').slice(1).toLowerCase())}</span>
                                    </div>
                                    <div style="display: flex; justify-content: space-between;">
                                        <span style="color: var(--text-secondary);">Processed By:</span>
                                        <span style="color: var(--text-color);">${escapeHtml(refund.processedBy)}</span>
                                    </div>
                                    <div style="display: flex; justify-content: space-between;">
                                        <span style="color: var(--text-secondary);">Date:</span>
                                        <span style="color: var(--text-color);">${formatDate(refund.createdAt)}</span>
                                    </div>
                                    ${refund.reason ? `
                                    <div style="margin-top: 0.5rem; padding-top: 0.5rem; border-top: 1px solid var(--card-border);">
                                        <div style="color: var(--text-secondary); margin-bottom: 0.25rem;">Reason:</div>
                                        <div style="color: var(--text-color); font-style: italic;">${escapeHtml(refund.reason)}</div>
                                    </div>
                                    ` : ''}
                                </div>
                            </div>
                        `).join('')}
                    </div>
                </div>
            `;
        } else {
            refundsHtml = `
                <div class="refunds-section">
                    <h3 style="margin: 0 0 1rem 0; color: var(--card-title); font-size: 1rem; font-weight: 600;">Refund History</h3>
                    <div style="text-align: center; padding: 2rem; color: var(--text-secondary); background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px;">
                        <p style="margin: 0;">No refunds found for this payment</p>
                    </div>
                </div>
            `;
        }
        
        const modalContent = `
            <div class="refund-history-modal">
                ${summaryHtml}
                ${refundsHtml}
            </div>
        `;
        
        showModal('Refund History', modalContent);
    } catch (error) {
        console.error('Failed to load refund history:', error);
        showModal('Error', `
            <div style="text-align: center; padding: 2rem;">
                <div style="color: #ef4444; font-size: 1.25rem; margin-bottom: 1rem;">⚠️</div>
                <div style="color: var(--text-color); font-weight: 600; margin-bottom: 0.5rem;">Failed to Load Refund History</div>
                <div style="color: var(--text-secondary); font-size: 0.875rem;">${escapeHtml(error.message || 'An error occurred while loading refund history')}</div>
            </div>
        `);
    }
}

/**
 * Integrate refund button into subscription payment rendering
 * This function should be called when rendering subscription details that include payment information
 * 
 * @param {Object} subscription - Subscription object containing payment details
 * @param {HTMLElement} container - Container element where subscription is being rendered
 * 
 * Usage example:
 * When rendering a subscription with payment, call this function to add refund controls:
 * 
 * const subscriptionCard = document.createElement('div');
 * subscriptionCard.className = 'subscription-card';
 * // ... render subscription details ...
 * 
 * // Add refund button if payment exists
 * if (subscription.paymentId) {
 *     const refundContainer = document.createElement('div');
 *     refundContainer.className = 'refund-button-container';
 *     subscriptionCard.appendChild(refundContainer);
 *     integrateRefundButton(subscription, refundContainer);
 * }
 */
function integrateRefundButton(subscription, container) {
    if (!subscription || !container) {
        console.warn('integrateRefundButton: Missing subscription or container');
        return;
    }

    // Check if subscription has a payment ID
    const paymentId = subscription.paymentId || subscription.payment_id || subscription.razorpayPaymentId;
    
    if (!paymentId) {
        console.log('No payment ID found for subscription, skipping refund button');
        return;
    }

    // Create a wrapper for the refund button
    const refundWrapper = document.createElement('div');
    refundWrapper.className = 'refund-button-wrapper';
    refundWrapper.style.marginTop = '12px';
    
    // Render the refund button asynchronously
    renderRefundButton(paymentId, refundWrapper);
    
    // Append to container
    container.appendChild(refundWrapper);
}

/**
 * Helper function to add refund button to an existing payment display element
 * This can be used to retrofit refund buttons into existing subscription/payment UI
 * 
 * @param {string} paymentId - The payment ID
 * @param {string} containerSelector - CSS selector for the container element
 * 
 * Usage example:
 * addRefundButtonToPayment('pay_abc123', '#payment-details-container');
 */
function addRefundButtonToPayment(paymentId, containerSelector) {
    const container = document.querySelector(containerSelector);
    if (!container) {
        console.warn(`Container not found: ${containerSelector}`);
        return;
    }
    
    // Check if refund button already exists
    if (container.querySelector('.refund-button-wrapper')) {
        console.log('Refund button already exists in container');
        return;
    }
    
    const refundWrapper = document.createElement('div');
    refundWrapper.className = 'refund-button-wrapper';
    refundWrapper.style.marginTop = '12px';
    
    renderRefundButton(paymentId, refundWrapper);
    container.appendChild(refundWrapper);
}

/**
 * Batch add refund buttons to multiple subscription elements
 * Useful for adding refund buttons to a list of subscriptions after they're rendered
 * 
 * @param {Array} subscriptions - Array of subscription objects with paymentId
 * @param {string} subscriptionCardSelector - CSS selector pattern for subscription cards
 * 
 * Usage example:
 * After rendering subscriptions:
 * batchAddRefundButtons(subscriptions, '.subscription-card');
 */
function batchAddRefundButtons(subscriptions, subscriptionCardSelector) {
    if (!Array.isArray(subscriptions)) {
        console.warn('batchAddRefundButtons: subscriptions must be an array');
        return;
    }
    
    const subscriptionCards = document.querySelectorAll(subscriptionCardSelector);
    
    subscriptions.forEach((subscription, index) => {
        const paymentId = subscription.paymentId || subscription.payment_id || subscription.razorpayPaymentId;
        
        if (paymentId && subscriptionCards[index]) {
            // Find or create refund container within the subscription card
            let refundContainer = subscriptionCards[index].querySelector('.refund-button-container');
            
            if (!refundContainer) {
                refundContainer = document.createElement('div');
                refundContainer.className = 'refund-button-container';
                subscriptionCards[index].appendChild(refundContainer);
            }
            
            integrateRefundButton(subscription, refundContainer);
        }
    });
}

// ================= End Refund Button Integration =================


// ================= Refund Metrics Module =================
(function(){
    const state = {
        initialized: false,
        currentPage: 1,
        pageSize: 20,
        totalPages: 1,
        totalCount: 0,
        statusFilter: '',
        startDate: '',
        endDate: '',
        refundChart: null
    };

    function q(id){ return document.getElementById(id); }

    /**
     * Load refund metrics from the API
     * Sub-task 6.1: Create loadRefundMetrics function in admin.js
     */
    function loadRefundMetrics() {
        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        fetch('/admin/refunds/metrics', {
            method: 'GET',
            credentials: 'include',
            headers
        })
        .then(response => {
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to access refund metrics');
                }
                throw new Error('Failed to load refund metrics');
            }
            return response.json();
        })
        .then(payload => {
            if (!payload || payload.status !== true || !payload.data) {
                throw new Error(payload && payload.message ? payload.message : 'Invalid response while loading refund metrics');
            }

            const metrics = payload.data;

            // Call renderRefundMetricsCards with metrics data
            renderRefundMetricsCards(metrics);

            // Call renderRefundChart with monthly chart data
            if (metrics.monthlyRefundChart && Array.isArray(metrics.monthlyRefundChart)) {
                renderRefundChart(metrics.monthlyRefundChart);
            }

            console.log('Loaded refund metrics:', metrics);
        })
        .catch(error => {
            console.error('Error loading refund metrics:', error);
            showErrorMessage(error.message || 'Failed to load refund metrics');

            // If error is due to authentication, redirect to login
            if (error.message.includes('permission') || error.message.includes('authentication')) {
                setTimeout(() => {
                    window.location.href = '/admin/login?error=auth_required';
                }, 2000);
            }
        });
    }

    /**
     * Render refund metrics cards
     * Sub-task 6.2: Create renderRefundMetricsCards function in admin.js
     * @param {Object} metrics - Refund metrics object
     */
    function renderRefundMetricsCards(metrics) {
        // Update Total Refunds card
        const totalRefunds = q('total-refunds');
        if (totalRefunds) {
            const amount = (metrics.totalRefunds || 0) * 100; // Convert from rupees to paise
            totalRefunds.textContent = formatCurrency(amount);
            
            // Add count subtitle if element exists
            const totalRefundsCard = totalRefunds.closest('.metric-card');
            if (totalRefundsCard) {
                let subtitle = totalRefundsCard.querySelector('.metric-subtitle');
                if (!subtitle) {
                    subtitle = document.createElement('div');
                    subtitle.className = 'metric-subtitle';
                    totalRefundsCard.appendChild(subtitle);
                }
                subtitle.textContent = `${metrics.totalRefundCount || 0} refunds`;
            }
        }

        // Update Monthly Refunds card
        const monthlyRefunds = q('monthly-refunds');
        if (monthlyRefunds) {
            const amount = (metrics.monthlyRefunds || 0) * 100; // Convert from rupees to paise
            monthlyRefunds.textContent = formatCurrency(amount);
            
            // Add count subtitle
            const monthlyRefundsCard = monthlyRefunds.closest('.metric-card');
            if (monthlyRefundsCard) {
                let subtitle = monthlyRefundsCard.querySelector('.metric-subtitle');
                if (!subtitle) {
                    subtitle = document.createElement('div');
                    subtitle.className = 'metric-subtitle';
                    monthlyRefundsCard.appendChild(subtitle);
                }
                subtitle.textContent = `${metrics.monthlyRefundCount || 0} this month`;
            }
        }

        // Update Refund Rate card
        const refundRate = q('refund-rate');
        if (refundRate) {
            const rate = metrics.refundRate || 0;
            refundRate.textContent = rate.toFixed(2) + '%';
            
            // Add subtitle
            const refundRateCard = refundRate.closest('.metric-card');
            if (refundRateCard) {
                let subtitle = refundRateCard.querySelector('.metric-subtitle');
                if (!subtitle) {
                    subtitle = document.createElement('div');
                    subtitle.className = 'metric-subtitle';
                    refundRateCard.appendChild(subtitle);
                }
                subtitle.textContent = 'of total revenue';
            }
        }

        // Update Instant Refunds card
        const instantRefunds = q('instant-refunds');
        if (instantRefunds) {
            instantRefunds.textContent = (metrics.instantRefundCount || 0).toString();
        }

        // Update Normal Refunds card
        const normalRefunds = q('normal-refunds');
        if (normalRefunds) {
            normalRefunds.textContent = (metrics.normalRefundCount || 0).toString();
        }

        // Update Average Processing Time card
        const avgProcessingTime = q('avg-processing-time');
        if (avgProcessingTime) {
            const hours = metrics.averageProcessingTimeHours || 0;
            avgProcessingTime.textContent = hours.toFixed(1) + 'h';
            
            // Add subtitle with instant/normal breakdown
            const avgTimeCard = avgProcessingTime.closest('.metric-card');
            if (avgTimeCard) {
                let subtitle = avgTimeCard.querySelector('.metric-subtitle');
                if (!subtitle) {
                    subtitle = document.createElement('div');
                    subtitle.className = 'metric-subtitle';
                    avgTimeCard.appendChild(subtitle);
                }
                subtitle.textContent = `Instant: ${metrics.instantRefundCount || 0} | Normal: ${metrics.normalRefundCount || 0}`;
            }
        }
    }

    /**
     * Render refund trend chart using Chart.js
     * Sub-task 6.3: Create renderRefundChart function in admin.js
     * @param {Array} monthlyData - Array of monthly refund data
     */
    function renderRefundChart(monthlyData) {
        const canvas = q('refund-chart');
        if (!canvas) {
            console.warn('Refund chart canvas not found');
            return;
        }

        if (typeof Chart === 'undefined') {
            console.warn('Chart.js not available');
            return;
        }

        // Destroy existing chart instance if present
        if (state.refundChart) {
            try {
                state.refundChart.destroy();
                state.refundChart = null;
            } catch(e) {
                console.warn('Failed to destroy previous refund chart', e);
            }
        }

        // Prepare data for chart
        const labels = monthlyData.map(item => {
            try {
                const date = new Date(item.month + '-01');
                return date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
            } catch(e) {
                return item.month;
            }
        });

        const data = monthlyData.map(item => item.refundAmount || 0);

        const ctx = canvas.getContext('2d');

        // Create gradient fill
        let gradient;
        try {
            gradient = ctx.createLinearGradient(0, 0, 0, canvas.height || 260);
            gradient.addColorStop(0, 'rgba(239, 68, 68, 0.22)');
            gradient.addColorStop(1, 'rgba(239, 68, 68, 0.02)');
        } catch(e) {
            gradient = 'rgba(239, 68, 68, 0.12)';
        }

        // Get theme colors
        const lineColor = '#ef4444';
        const gridColor = getComputedStyle(document.documentElement).getPropertyValue('--card-border').trim() || 'rgba(239,68,68,0.2)';
        const tickColor = getComputedStyle(document.documentElement).getPropertyValue('--text-secondary').trim() || '#9ca3af';

        // Create Chart.js line chart for monthly refund trend
        state.refundChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Refund Amount',
                    data: data,
                    borderColor: lineColor,
                    backgroundColor: gradient,
                    fill: true,
                    cubicInterpolationMode: 'monotone',
                    tension: 0.4,
                    borderWidth: 2.5,
                    pointRadius: 3,
                    pointHoverRadius: 5,
                    pointBackgroundColor: lineColor,
                    pointBorderColor: '#fff',
                    pointBorderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 700, easing: 'easeOutCubic' },
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    legend: { 
                        display: true,
                        position: 'top'
                    },
                    tooltip: {
                        enabled: true,
                        backgroundColor: 'rgba(17, 24, 39, 0.9)',
                        titleColor: '#ffffff',
                        bodyColor: '#ffffff',
                        displayColors: false,
                        padding: 10,
                        callbacks: {
                            label: function(context) {
                                // Format currency values in tooltips
                                return 'Refunds: ' + formatCurrency(context.parsed.y * 100);
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { display: false, drawBorder: false },
                        ticks: { color: tickColor, maxRotation: 0 }
                    },
                    y: {
                        beginAtZero: true,
                        grid: { color: gridColor, drawBorder: false },
                        ticks: {
                            color: tickColor,
                            callback: function(value) {
                                return '₹' + value.toLocaleString('en-IN');
                            }
                        }
                    }
                }
            }
        });

        // Store chart instance in window.refundChartInstance for external access
        window.refundChartInstance = state.refundChart;
    }

    /**
     * Load refund history with pagination
     * Sub-task 6.4: Create loadRefundHistory function in admin.js
     * @param {number} page - Page number
     * @param {number} pageSize - Items per page
     */
    function loadRefundHistory(page = 1, pageSize = 20) {
        page = page || 1;
        pageSize = pageSize || 20;

        const loader = q('refunds-loader');
        if (loader) loader.style.display = 'block';

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        // Build URL with pagination parameters
        let url = `/admin/refunds/history?page=${encodeURIComponent(page)}&pageSize=${encodeURIComponent(pageSize)}`;

        // Add filters if set
        if (state.statusFilter) {
            url += `&status=${encodeURIComponent(state.statusFilter)}`;
        }
        if (state.startDate) {
            url += `&startDate=${encodeURIComponent(state.startDate)}`;
        }
        if (state.endDate) {
            url += `&endDate=${encodeURIComponent(state.endDate)}`;
        }

        fetch(url, {
            method: 'GET',
            credentials: 'include',
            headers
        })
        .then(response => {
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to access refund history');
                }
                throw new Error('Failed to load refund history');
            }
            return response.json();
        })
        .then(payload => {
            if (loader) loader.style.display = 'none';

            if (!payload || payload.status !== true || !payload.data) {
                throw new Error(payload && payload.message ? payload.message : 'Invalid response while loading refund history');
            }

            const history = payload.data;

            // Update state
            state.currentPage = history.pagination.page;
            state.pageSize = history.pagination.pageSize;
            state.totalPages = history.pagination.totalPages;
            state.totalCount = history.pagination.totalCount;

            // Call renderRefundHistoryTable with refunds array
            renderRefundHistoryTable(history.refunds);

            // Call renderRefundPagination with pagination info
            renderRefundPagination(history.pagination);

            console.log('Loaded refund history:', history);
        })
        .catch(error => {
            if (loader) loader.style.display = 'none';
            console.error('Error loading refund history:', error);
            showErrorMessage(error.message || 'Failed to load refund history');

            // If error is due to authentication, redirect to login
            if (error.message.includes('permission') || error.message.includes('authentication')) {
                setTimeout(() => {
                    window.location.href = '/admin/login?error=auth_required';
                }, 2000);
            }
        });
    }

    /**
     * Render refund history table
     * Sub-task 6.5: Create renderRefundHistoryTable function in admin.js
     * @param {Array} refunds - Array of refund objects
     */
    function renderRefundHistoryTable(refunds) {
        const tbody = q('refunds-table-body');
        if (!tbody) {
            console.warn('Refunds table body not found');
            return;
        }

        // Handle empty state
        if (!refunds || refunds.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; padding: 2rem; color: var(--text-secondary);">No refunds found</td></tr>';
            return;
        }

        // Build table rows HTML for each refund
        const rows = refunds.map(refund => {
            // Format amounts as currency (convert from rupees to paise)
            const amount = formatCurrency((refund.amount || 0) * 100);
            
            // Add status badges with appropriate colors
            let statusBadge = '';
            const status = (refund.status || '').toUpperCase();
            if (status === 'PENDING') {
                statusBadge = '<span class="badge badge-pending">Pending</span>';
            } else if (status === 'PROCESSED') {
                statusBadge = '<span class="badge badge-processed">Processed</span>';
            } else if (status === 'FAILED') {
                statusBadge = '<span class="badge badge-failed">Failed</span>';
            } else {
                statusBadge = `<span class="badge">${escapeHtml(status)}</span>`;
            }

            // Format date
            const date = formatDate(refund.createdAt);

            // Escape user input for XSS prevention
            const refundId = escapeHtml(refund.refundId || '');
            const userEmail = escapeHtml(refund.userEmail || '');
            const type = escapeHtml(refund.speedProcessed || refund.speedRequested || 'N/A');
            const processedBy = escapeHtml(refund.processedBy || 'System');

            return `
                <tr>
                    <td><code style="font-size: 0.85em;">${refundId}</code></td>
                    <td>${userEmail}</td>
                    <td>${amount}</td>
                    <td>${statusBadge}</td>
                    <td>${type}</td>
                    <td>${processedBy}</td>
                    <td>${date}</td>
                    <td>
                        <button class="btn btn-sm btn-secondary" onclick="viewRefundDetails('${refund.refundId}')">
                            View
                        </button>
                    </td>
                </tr>
            `;
        }).join('');

        // Insert into refund-history-tbody element
        tbody.innerHTML = rows;
    }

    /**
     * Render refund pagination controls
     * Sub-task 6.6: Create renderRefundPagination function in admin.js
     * @param {Object} pagination - Pagination info object
     */
    function renderRefundPagination(pagination) {
        const container = q('refunds-pagination');
        if (!container) {
            console.warn('Refunds pagination container not found');
            return;
        }

        const currentPage = pagination.page || 1;
        const totalPages = pagination.totalPages || 1;

        if (totalPages <= 1) {
            container.innerHTML = '';
            return;
        }

        // Build pagination controls HTML
        let html = '';

        // Add previous button
        const prevDisabled = currentPage === 1;
        html += `
            <button 
                class="pagination-button ${prevDisabled ? 'disabled' : ''}" 
                ${prevDisabled ? 'disabled' : ''}
                onclick="window.RefundMetricsModule.loadRefundHistory(${currentPage - 1}, ${state.pageSize})">
                Previous
            </button>
        `;

        // Add page number buttons
        const maxButtons = 5;
        const startPage = Math.max(1, currentPage - Math.floor(maxButtons / 2));
        const endPage = Math.min(totalPages, startPage + maxButtons - 1);

        for (let i = startPage; i <= endPage; i++) {
            const isActive = i === currentPage;
            html += `
                <button 
                    class="pagination-button ${isActive ? 'active' : ''}"
                    onclick="window.RefundMetricsModule.loadRefundHistory(${i}, ${state.pageSize})">
                    ${i}
                </button>
            `;
        }

        // Add next button
        const nextDisabled = currentPage === totalPages;
        html += `
            <button 
                class="pagination-button ${nextDisabled ? 'disabled' : ''}" 
                ${nextDisabled ? 'disabled' : ''}
                onclick="window.RefundMetricsModule.loadRefundHistory(${currentPage + 1}, ${state.pageSize})">
                Next
            </button>
        `;

        container.innerHTML = html;
    }

    /**
     * View refund details in a modal
     * @param {string} refundId - The refund ID to view
     */
    function viewRefundDetails(refundId) {
        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        const headers = { 'Accept': 'application/json' };
        if (token) { headers['Authorization'] = 'Bearer ' + token; }

        fetch(`/admin/refunds/${encodeURIComponent(refundId)}`, {
            method: 'GET',
            credentials: 'include',
            headers
        })
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to load refund details');
            }
            return response.json();
        })
        .then(payload => {
            if (!payload || payload.status !== true || !payload.data) {
                throw new Error('Invalid response while loading refund details');
            }

            const refund = payload.data;
            
            // Build modal content
            const content = `
                <div style="display: grid; gap: 1rem;">
                    <div class="refund-details" style="background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 1rem;">
                        <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--card-border);">
                            <span style="font-weight: 600; color: var(--text-secondary);">Refund ID:</span>
                            <span><code>${escapeHtml(refund.refundId)}</code></span>
                        </div>
                        <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--card-border);">
                            <span style="font-weight: 600; color: var(--text-secondary);">Payment ID:</span>
                            <span><code>${escapeHtml(refund.paymentId)}</code></span>
                        </div>
                        <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--card-border);">
                            <span style="font-weight: 600; color: var(--text-secondary);">User Email:</span>
                            <span>${escapeHtml(refund.userEmail)}</span>
                        </div>
                        <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--card-border);">
                            <span style="font-weight: 600; color: var(--text-secondary);">Amount:</span>
                            <span style="font-weight: 700; color: #10b981;">${formatCurrency(refund.amount * 100)}</span>
                        </div>
                        <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--card-border);">
                            <span style="font-weight: 600; color: var(--text-secondary);">Status:</span>
                            <span class="badge badge-${refund.status.toLowerCase()}">${escapeHtml(refund.status)}</span>
                        </div>
                        <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--card-border);">
                            <span style="font-weight: 600; color: var(--text-secondary);">Type:</span>
                            <span>${escapeHtml(refund.speedProcessed || refund.speedRequested || 'N/A')}</span>
                        </div>
                        <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--card-border);">
                            <span style="font-weight: 600; color: var(--text-secondary);">Processed By:</span>
                            <span>${escapeHtml(refund.processedBy)}</span>
                        </div>
                        <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--card-border);">
                            <span style="font-weight: 600; color: var(--text-secondary);">Created:</span>
                            <span>${formatDate(refund.createdAt)}</span>
                        </div>
                        ${refund.processedAt ? `
                        <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--card-border);">
                            <span style="font-weight: 600; color: var(--text-secondary);">Processed:</span>
                            <span>${formatDate(refund.processedAt)}</span>
                        </div>
                        ` : ''}
                        ${refund.reason ? `
                        <div class="detail-row" style="display: flex; justify-content: space-between; padding: 0.5rem 0;">
                            <span style="font-weight: 600; color: var(--text-secondary);">Reason:</span>
                            <span>${escapeHtml(refund.reason)}</span>
                        </div>
                        ` : ''}
                    </div>
                </div>
            `;

            showModal('Refund Details', content);
        })
        .catch(error => {
            console.error('Error loading refund details:', error);
            showErrorMessage(error.message || 'Failed to load refund details');
        });
    }

    /**
     * Apply filters to refund history
     */
    function applyRefundFilters() {
        const statusFilter = q('refund-status-filter');
        const dateFrom = q('refund-date-from');
        const dateTo = q('refund-date-to');

        if (statusFilter) state.statusFilter = statusFilter.value;
        if (dateFrom) state.startDate = dateFrom.value;
        if (dateTo) state.endDate = dateTo.value;

        // Reload with filters
        loadRefundHistory(1, state.pageSize);
    }

    /**
     * Initialize refund metrics module
     * Sub-task 6.8: Integrate refund metrics loading into finance tab activation
     */
    function ensureInitialized() {
        if (state.initialized) return;

        const panel = q('finance');
        if (!panel) {
            console.warn('[Refund Metrics] Finance panel not found');
            return;
        }

        console.log('[Refund Metrics] Initializing refund metrics...');

        // Call loadRefundMetrics when finance tab is activated
        loadRefundMetrics();

        // Call loadRefundHistory when finance tab is activated
        loadRefundHistory(1, state.pageSize);

        // Bind filter controls
        const statusFilter = q('refund-status-filter');
        const dateFrom = q('refund-date-from');
        const dateTo = q('refund-date-to');
        const exportBtn = q('export-refunds-btn');

        if (statusFilter && !statusFilter.dataset.bound) {
            statusFilter.addEventListener('change', applyRefundFilters);
            statusFilter.dataset.bound = 'true';
        }

        if (dateFrom && !dateFrom.dataset.bound) {
            dateFrom.addEventListener('change', applyRefundFilters);
            dateFrom.dataset.bound = 'true';
        }

        if (dateTo && !dateTo.dataset.bound) {
            dateTo.addEventListener('change', applyRefundFilters);
            dateTo.dataset.bound = 'true';
        }

        if (exportBtn && !exportBtn.dataset.bound) {
            exportBtn.addEventListener('click', exportRefunds);
            exportBtn.dataset.bound = 'true';
        }

        // Ensure data loads only once per tab activation
        state.initialized = true;
        console.log('[Refund Metrics] Refund metrics initialized');
    }

    /**
     * Export refunds to CSV
     */
    function exportRefunds() {
        const dateFrom = q('refund-date-from');
        const dateTo = q('refund-date-to');

        let url = '/admin/refunds/export?';
        
        if (dateFrom && dateFrom.value) {
            url += `startDate=${encodeURIComponent(dateFrom.value)}&`;
        }
        if (dateTo && dateTo.value) {
            url += `endDate=${encodeURIComponent(dateTo.value)}&`;
        }

        const token = (typeof localStorage !== 'undefined') ? localStorage.getItem('jwt_token') : null;
        if (token) {
            url += `token=${encodeURIComponent(token)}`;
        }

        // Trigger download
        window.open(url, '_blank');
        showMessage('success', 'Refund export started');
    }

    // Bind initialization to finance tab activation
    document.addEventListener('DOMContentLoaded', function() {
        try {
            const tab = document.querySelector('[data-tab="finance"]');
            if (tab && !tab.dataset.refundMetricsBound) {
                tab.addEventListener('click', ensureInitialized);
                tab.dataset.refundMetricsBound = 'true';
            }

            // If already active, init immediately
            const panel = q('finance');
            if (panel && panel.classList.contains('active')) {
                ensureInitialized();
            }
        } catch(e) {
            console.warn('[Refund Metrics] Tab binding failed', e);
        }
    });

    // Expose functions globally for external access
    window.RefundMetricsModule = {
        loadRefundMetrics,
        loadRefundHistory,
        renderRefundMetricsCards,
        renderRefundChart,
        renderRefundHistoryTable,
        renderRefundPagination,
        viewRefundDetails,
        applyRefundFilters,
        exportRefunds,
        ensureInitialized
    };

})();

// ================= End Refund Metrics Module =================


// ================= Refund Export Functionality =================

/**
 * Format currency amount from paise to rupees with symbol
 * @param {number} amountPaise - Amount in paise
 * @returns {string} Formatted currency string (e.g., "₹1,234.56")
 */
function formatCurrency(amountPaise) {
    if (typeof amountPaise !== 'number' || isNaN(amountPaise)) return '₹0.00';
    const rupees = amountPaise / 100;
    return '₹' + rupees.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/**
 * Download CSV file with given content
 * @param {string} csvContent - CSV content as string
 * @param {string} filename - Filename for the download
 */
function downloadCSV(csvContent, filename) {
    try {
        // Create a Blob from the CSV content
        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        
        // Create a temporary download link
        const link = document.createElement('a');
        const url = URL.createObjectURL(blob);
        
        link.setAttribute('href', url);
        link.setAttribute('download', filename);
        link.style.visibility = 'hidden';
        
        // Append to body, click, and remove
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        
        // Clean up the URL object
        URL.revokeObjectURL(url);
        
        showMessage('success', 'Refunds exported successfully');
    } catch (error) {
        console.error('Failed to download CSV:', error);
        showMessage('error', 'Failed to download export file');
    }
}

/**
 * Export refunds to CSV file with date range filtering
 */
async function exportRefunds() {
    try {
        // Get date range inputs
        const startDateInput = document.getElementById('refund-export-start-date');
        const endDateInput = document.getElementById('refund-export-end-date');
        
        if (!startDateInput || !endDateInput) {
            showMessage('error', 'Date range inputs not found');
            return;
        }
        
        const startDate = startDateInput.value;
        const endDate = endDateInput.value;
        
        // Validate date range
        const validation = ValidationUtils.validateDateRange(startDate, endDate);
        if (!validation.valid) {
            showMessage('error', validation.message);
            return;
        }
        
        // Show loading state
        const exportBtn = document.getElementById('export-refunds-btn');
        if (exportBtn) {
            exportBtn.disabled = true;
            exportBtn.textContent = 'Exporting...';
        }
        
        // Build API URL with date parameters
        const params = new URLSearchParams({
            startDate: startDate,
            endDate: endDate
        });
        
        const url = `/admin/refunds/export?${params.toString()}`;
        
        // Call export API
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Authorization': 'Bearer ' + localStorage.getItem('jwt_token'),
                'Accept': 'text/csv'
            }
        });
        
        if (!response.ok) {
            if (response.status === 403) {
                throw new Error('You do not have permission to export refunds');
            } else if (response.status === 404) {
                throw new Error('No refunds found for the selected date range');
            } else {
                throw new Error('Failed to export refunds');
            }
        }
        
        // Get CSV content from response
        const csvContent = await response.text();
        
        // Generate filename with date range
        const filename = `refunds_${startDate}_to_${endDate}.csv`;
        
        // Trigger download
        downloadCSV(csvContent, filename);
        
    } catch (error) {
        console.error('Error exporting refunds:', error);
        showMessage('error', error.message || 'Failed to export refunds');
    } finally {
        // Reset button state
        const exportBtn = document.getElementById('export-refunds-btn');
        if (exportBtn) {
            exportBtn.disabled = false;
            exportBtn.textContent = 'Export Refunds';
        }
    }
}

/**
 * Initialize refund export functionality
 * Binds event listeners to export button
 */
function initializeRefundExport() {
    const exportBtn = document.getElementById('export-refunds-btn');
    if (exportBtn && !exportBtn.dataset.bound) {
        exportBtn.addEventListener('click', exportRefunds);
        exportBtn.dataset.bound = 'true';
    }
    
    // Set default date range (last 30 days)
    const endDateInput = document.getElementById('refund-export-end-date');
    const startDateInput = document.getElementById('refund-export-start-date');
    
    if (endDateInput && startDateInput) {
        const today = new Date();
        const thirtyDaysAgo = new Date();
        thirtyDaysAgo.setDate(today.getDate() - 30);
        
        // Format dates as YYYY-MM-DD
        endDateInput.value = today.toISOString().split('T')[0];
        startDateInput.value = thirtyDaysAgo.toISOString().split('T')[0];
    }
}

// ================= End Refund Export Functionality =================


// ================= Refund Export Initialization Note =================
/*
 * IMPORTANT: Call initializeRefundExport() when the finance tab is activated
 * 
 * Example usage:
 * 
 * // When finance tab is clicked or activated:
 * function onFinanceTabActivated() {
 *     // Load refund metrics and history
 *     if (typeof loadRefundMetrics === 'function') {
 *         loadRefundMetrics();
 *     }
 *     if (typeof loadRefundHistory === 'function') {
 *         loadRefundHistory();
 *     }
 *     
 *     // Initialize export functionality
 *     initializeRefundExport();
 * }
 * 
 * // Or add to existing tab activation logic:
 * document.querySelector('[data-tab="finance"]').addEventListener('click', function() {
 *     // ... existing tab switching code ...
 *     initializeRefundExport();
 * });
 */
// ================= End Refund Export Initialization Note =================
