/**
 * Service Catalog Tab (fresh implementation)
 * Minimal, reliable list view with search, status filter, and pagination.
 */
(function() {
    'use strict';

    const DEFAULT_PAGE_SIZE = 10;
    const SEARCH_DEBOUNCE_MS = 300;

    const state = {
        initialized: false,
        loading: false,
        page: 1,
        pageSize: DEFAULT_PAGE_SIZE,
        totalCount: 0,
        totalPages: 1,
        status: 'ALL',
        search: '',
        searchTimer: null
    };

    function getEl(id) {
        return document.getElementById(id);
    }

    function safeText(value, fallback = '—') {
        if (value === null || value === undefined || value === '') {
            return fallback;
        }
        return String(value);
    }

    function formatCurrency(amount, currency) {
        if (typeof amount !== 'number' || Number.isNaN(amount)) {
            return '—';
        }
        const code = currency || 'INR';
        return `${code} ${(amount / 100).toFixed(2)}`;
    }

    function formatStatus(status) {
        const normalized = String(status || '').toLowerCase();
        const className = normalized ? `status-${normalized}` : 'status-unknown';
        return `<span class="status-badge ${className}">${safeText(status, 'UNKNOWN')}</span>`;
    }

    async function fetchServices() {
        const params = new URLSearchParams({
            page: state.page.toString(),
            pageSize: state.pageSize.toString()
        });

        if (state.status && state.status !== 'ALL') {
            params.append('status', state.status);
        }

        if (state.search) {
            params.append('search', state.search);
        }

        const request = (typeof window.authFetch === 'function') ? window.authFetch : fetch;
        const response = await request(`/services?${params.toString()}`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json'
            }
        });

        if (!response.ok) {
            let message = 'Failed to load services';
            try {
                const payload = await response.json();
                message = payload?.message || message;
            } catch (_) {}
            throw new Error(message);
        }

        const payload = await response.json();
        if (!payload || payload.status !== true || !payload.data) {
            throw new Error(payload?.message || 'Invalid service catalog response');
        }

        return payload.data;
    }

    function updateMeta() {
        const meta = getEl('service-catalog-meta');
        if (!meta) return;
        meta.textContent = `Showing ${state.totalCount.toLocaleString()} services`;
    }

    function renderLoading(isLoading) {
        const loader = getEl('service-catalog-loader');
        if (loader) {
            loader.style.display = isLoading ? 'block' : 'none';
        }
    }

    function renderEmpty(message) {
        const empty = getEl('service-catalog-empty');
        const list = getEl('service-catalog-list');
        if (list) list.replaceChildren();
        if (empty) {
            empty.textContent = message || 'No services found.';
            empty.classList.remove('hidden');
        }
    }

    function clearEmpty() {
        const empty = getEl('service-catalog-empty');
        if (empty) {
            empty.classList.add('hidden');
        }
    }

    function renderRows(services) {
        const list = getEl('service-catalog-list');
        if (!list) return;

        list.replaceChildren();

        if (!services || services.length === 0) {
            renderEmpty(state.search || state.status !== 'ALL'
                ? 'No services match the current filters.'
                : 'No services available.');
            return;
        }

        clearEmpty();

        const fragment = document.createDocumentFragment();

        services.forEach(service => {
            const row = document.createElement('div');
            row.className = 'data-row';
            row.innerHTML = `
                <div class="data-cell data-cell--wrap">
                    <div style="font-weight: 600;">${safeText(service.displayName)}</div>
                    <div style="font-size: 0.85rem; color: var(--text-secondary);">${safeText(service.serviceCode)}</div>
                </div>
                <div class="data-cell">${formatStatus(service.status)}</div>
                <div class="data-cell data-cell--right">${safeText(service.totalPurchases, '0')}</div>
                <div class="data-cell data-cell--right">${formatCurrency(service.lowestPrice, service.currency)}</div>
                <div class="data-cell data-cell--right">${typeof window.formatDate === 'function' ? window.formatDate(service.createdAt) : safeText(service.createdAt)}</div>
            `;
            fragment.appendChild(row);
        });

        list.replaceChildren(fragment);
    }

    function renderPagination() {
        const container = getEl('service-catalog-pagination');
        if (!container) return;

        container.innerHTML = '';

        if (state.totalPages <= 1) {
            return;
        }

        const prev = document.createElement('button');
        prev.className = 'pagination-button';
        prev.textContent = 'Previous';
        prev.disabled = state.page <= 1;
        prev.addEventListener('click', () => changePage(state.page - 1));
        container.appendChild(prev);

        const pageLabel = document.createElement('span');
        pageLabel.style.alignSelf = 'center';
        pageLabel.style.color = 'var(--text-secondary)';
        pageLabel.textContent = `Page ${state.page} of ${state.totalPages}`;
        container.appendChild(pageLabel);

        const next = document.createElement('button');
        next.className = 'pagination-button';
        next.textContent = 'Next';
        next.disabled = state.page >= state.totalPages;
        next.addEventListener('click', () => changePage(state.page + 1));
        container.appendChild(next);
    }

    function changePage(nextPage) {
        if (nextPage < 1 || nextPage > state.totalPages) return;
        state.page = nextPage;
        loadServices();
    }

    function bindControls() {
        const searchInput = getEl('service-catalog-search');
        if (searchInput && !searchInput.dataset.bound) {
            searchInput.addEventListener('input', (event) => {
                const value = event.target.value;
                if (state.searchTimer) {
                    clearTimeout(state.searchTimer);
                }
                state.searchTimer = setTimeout(() => {
                    state.search = value.trim();
                    state.page = 1;
                    loadServices();
                }, SEARCH_DEBOUNCE_MS);
            });
            searchInput.dataset.bound = 'true';
        }

        const statusSelect = getEl('service-catalog-status');
        if (statusSelect && !statusSelect.dataset.bound) {
            statusSelect.addEventListener('change', (event) => {
                state.status = event.target.value || 'ALL';
                state.page = 1;
                loadServices();
            });
            statusSelect.dataset.bound = 'true';
        }

        const pageSizeSelect = getEl('service-catalog-page-size');
        if (pageSizeSelect && !pageSizeSelect.dataset.bound) {
            pageSizeSelect.addEventListener('change', (event) => {
                state.pageSize = parseInt(event.target.value, 10) || DEFAULT_PAGE_SIZE;
                state.page = 1;
                loadServices();
            });
            pageSizeSelect.dataset.bound = 'true';
        }

        const refreshBtn = getEl('service-catalog-refresh');
        if (refreshBtn && !refreshBtn.dataset.bound) {
            refreshBtn.addEventListener('click', () => loadServices());
            refreshBtn.dataset.bound = 'true';
        }
    }

    async function loadServices() {
        if (state.loading) return;
        state.loading = true;
        renderLoading(true);

        try {
            const data = await fetchServices();
            const services = Array.isArray(data.services) ? data.services : [];

            state.totalCount = Number(data.totalCount || services.length || 0);
            state.totalPages = Math.max(1, Math.ceil(state.totalCount / state.pageSize));

            renderRows(services);
            renderPagination();
            updateMeta();
        } catch (error) {
            console.error('[Service Catalog] Failed to load services:', error);
            renderEmpty(error.message || 'Failed to load services.');
            if (typeof window.showMessage === 'function') {
                window.showMessage('error', error.message || 'Failed to load services');
            }
        } finally {
            state.loading = false;
            renderLoading(false);
        }
    }

    async function initializeServiceCatalog(options = {}) {
        if (!state.initialized) {
            bindControls();
            state.initialized = true;
        }

        if (options.forceRefresh) {
            state.page = 1;
        }

        await loadServices();
    }

    window.initializeServiceCatalog = initializeServiceCatalog;
    window.ServiceCatalogTab = {
        initialize: initializeServiceCatalog,
        refresh: loadServices
    };
})();
/**
 * Service Catalog Management functionality for admin dashboard
 * Handles service CRUD operations, analytics, and history tracking
 */

// Service Catalog state management
const ServiceCatalogState = {
    services: [],
    visibleServices: [],
    rawTotalCount: 0,
    currentPage: 1,
    pageSize: 10,
    totalPages: 1,
    totalCount: 0,
    statusFilter: null,
    searchQuery: '',
    selectedService: null,
    searchDebounceTimer: null
};

// Service Catalog API wrapper
window.ServiceCatalogAPI = window.ServiceCatalogAPI || {
    /**
     * List all services with pagination and filtering
     * @param {number} page - Page number
     * @param {number} pageSize - Page size
     * @param {string|null} status - Filter by status (ACTIVE, INACTIVE, ARCHIVED)
     * @param {string|null} search - Search query
     * @returns {Promise} API response
     */
    listServices(page, pageSize, status, search) {
        const params = new URLSearchParams({
            page: page.toString(),
            pageSize: pageSize.toString()
        });
        if (status) params.append('status', status);
        if (search) params.append('search', search);

        const request = (typeof authFetch === 'function' ? authFetch : fetch);
        return request(`/services?${params.toString()}`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json'
            }
        }).then(async response => {
            if (!response.ok) {
                let detail = 'Failed to fetch services';
                try {
                    const payload = await response.json();
                    detail = payload?.message || detail;
                } catch (_) {}
                throw new Error(detail);
            }
            return response.json();
        });
    },

    /**
     * Get service details by ID
     * @param {string} id - Service ID
     * @returns {Promise} API response
     */
    getService(id) {
        const request = (typeof authFetch === 'function' ? authFetch : fetch);
        return request(`/services/${encodeURIComponent(id)}`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json'
            }
        }).then(async response => {
            if (!response.ok) {
                let detail = 'Failed to fetch service details';
                try {
                    const payload = await response.json();
                    detail = payload?.message || detail;
                } catch (_) {}
                throw new Error(detail);
            }
            return response.json();
        });
    },

    /**
     * Create a new service
     * @param {Object} serviceData - Service data
     * @returns {Promise} API response
     */
    createService(serviceData) {
        const request = (typeof authFetch === 'function' ? authFetch : fetch);
        return request('/services', {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(serviceData)
        }).then(async response => {
            if (!response.ok) {
                let detail = 'Failed to create service';
                try {
                    const payload = await response.json();
                    detail = payload?.message || detail;
                } catch (_) {}
                throw new Error(detail);
            }
            return response.json();
        });
    },

    /**
     * Update an existing service
     * @param {string} id - Service ID
     * @param {Object} serviceData - Updated service data
     * @returns {Promise} API response
     */
    updateService(id, serviceData) {
        const request = (typeof authFetch === 'function' ? authFetch : fetch);
        return request(`/services/${encodeURIComponent(id)}`, {
            method: 'PUT',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(serviceData)
        }).then(async response => {
            if (!response.ok) {
                let detail = 'Failed to update service';
                try {
                    const payload = await response.json();
                    detail = payload?.message || detail;
                } catch (_) {}
                throw new Error(detail);
            }
            return response.json();
        });
    },

    /**
     * Change service status
     * @param {string} id - Service ID
     * @param {string} status - New status (ACTIVE, INACTIVE, ARCHIVED)
     * @returns {Promise} API response
     */
    changeStatus(id, status) {
        const request = (typeof authFetch === 'function' ? authFetch : fetch);
        return request(`/services/${encodeURIComponent(id)}/status`, {
            method: 'PATCH',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({ status })
        }).then(async response => {
            if (!response.ok) {
                let detail = 'Failed to change service status';
                try {
                    const payload = await response.json();
                    detail = payload?.message || detail;
                } catch (_) {}
                throw new Error(detail);
            }
            return response.json();
        });
    },

    /**
     * Clone an existing service
     * @param {string} id - Source service ID
     * @param {string} newServiceCode - New service code
     * @returns {Promise} API response
     */
    cloneService(id, newServiceCode) {
        const request = (typeof authFetch === 'function' ? authFetch : fetch);
        return request(`/services/${encodeURIComponent(id)}/clone`, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({ newServiceCode })
        }).then(async response => {
            if (!response.ok) {
                let detail = 'Failed to clone service';
                try {
                    const payload = await response.json();
                    detail = payload?.message || detail;
                } catch (_) {}
                throw new Error(detail);
            }
            return response.json();
        });
    },

    /**
     * Get service analytics
     * @param {string} id - Service ID
     * @returns {Promise} API response
     */
    getAnalytics(id) {
        const request = (typeof authFetch === 'function' ? authFetch : fetch);
        return request(`/services/${encodeURIComponent(id)}/analytics`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json'
            }
        }).then(async response => {
            if (!response.ok) {
                let detail = 'Failed to fetch analytics';
                try {
                    const payload = await response.json();
                    detail = payload?.message || detail;
                } catch (_) {}
                throw new Error(detail);
            }
            return response.json();
        });
    }
};

/**
 * Load services with current filters and pagination
 */
function loadServices() {
    const loader = document.getElementById('service-catalog-loader');
    if (loader) loader.style.display = 'block';
    const list = document.getElementById('services-list');
    const empty = document.getElementById('services-empty');
    const grid = document.getElementById('service-catalog-grid');
    if (empty) {
        empty.classList.add('hidden');
        empty.classList.remove('visible');
    }
    if (grid) grid.style.display = 'block';
    if (list) {
        const card = document.createElement('div');
        card.className = 'service-card';
        card.innerHTML = `
            <div class="service-card-header">
                <div class="service-card-main">
                    <div class="service-card-title">Loading services...</div>
                    <div class="service-card-meta">Fetching latest catalog data</div>
                </div>
            </div>
        `;
        list.replaceChildren(card);
    }

    return window.ServiceCatalogAPI.listServices(
        ServiceCatalogState.currentPage,
        ServiceCatalogState.pageSize,
        ServiceCatalogState.statusFilter,
        ServiceCatalogState.searchQuery
    )
        .then(async response => {
            if (response.status === true && response.data) {
                const data = response.data;
                const totalPages = data.totalPages || 1;
                const pageSize = data.pageSize || ServiceCatalogState.pageSize;
                let allServices = data.services || [];

                if (totalPages > 1) {
                    const pageRequests = [];
                    for (let page = 2; page <= totalPages; page += 1) {
                        pageRequests.push(
                            window.ServiceCatalogAPI.listServices(
                                page,
                                pageSize,
                                ServiceCatalogState.statusFilter,
                                ServiceCatalogState.searchQuery
                            ).then(nextResponse => {
                                if (nextResponse.status === true && nextResponse.data) {
                                    return nextResponse.data.services || [];
                                }
                                return [];
                            })
                        );
                    }

                    try {
                        const pages = await Promise.all(pageRequests);
                        pages.forEach(pageServices => {
                            allServices = allServices.concat(pageServices);
                        });
                    } catch (error) {
                        console.warn('Unable to load all service pages:', error);
                    }
                }

                ServiceCatalogState.services = allServices;
                ServiceCatalogState.rawTotalCount = allServices.length;
                ServiceCatalogState.visibleServices = allServices.filter(isServiceAvailableToday);
                ServiceCatalogState.totalCount = ServiceCatalogState.visibleServices.length;
                ServiceCatalogState.totalPages = 1;
                ServiceCatalogState.currentPage = 1;

                renderServiceList();
                renderServicePagination();
                updateServiceCatalogSummary();
                if (loader) loader.style.display = 'none';
                return response;
            }
            throw new Error(response.message || 'Failed to load services');
        })
        .catch(error => {
            if (loader) loader.style.display = 'none';
            console.error('Error loading services:', error);
            showMessage('error', error.message || 'Failed to load services');
            const empty = document.getElementById('services-empty');
            const grid = document.getElementById('service-catalog-grid');
            if (grid) grid.style.display = 'none';
            if (empty) {
                empty.textContent = error.message || 'Failed to load services';
                empty.classList.remove('hidden');
                empty.classList.add('visible');
            }
            throw error;
        });
}

/**
 * Handle search input with debouncing
 * @param {string} query - Search query
 */
function handleServiceSearch(query) {
    // Clear existing timer
    if (ServiceCatalogState.searchDebounceTimer) {
        clearTimeout(ServiceCatalogState.searchDebounceTimer);
    }

    // Set new timer
    ServiceCatalogState.searchDebounceTimer = setTimeout(() => {
        ServiceCatalogState.searchQuery = query;
        ServiceCatalogState.currentPage = 1; // Reset to first page
        loadServices();
    }, 300); // 300ms debounce
}

/**
 * Handle status filter change
 * @param {string|null} status - Status filter
 */
function handleStatusFilter(status) {
    ServiceCatalogState.statusFilter = status;
    ServiceCatalogState.currentPage = 1; // Reset to first page
    loadServices();
}

/**
 * Handle page change
 * @param {number} page - Page number
 */
function handlePageChange(page) {
    if (page < 1 || page > ServiceCatalogState.totalPages) return;
    ServiceCatalogState.currentPage = page;
    loadServices();
}

/**
 * Initialize service catalog module
 */
function initializeServiceCatalog() {
    try {
        // Bind search input
        const searchInput = document.getElementById('service-search-input');
        if (searchInput && !searchInput.dataset.bound) {
            searchInput.addEventListener('input', function() {
                handleServiceSearch(this.value);
            });
            searchInput.dataset.bound = 'true';
        }

        // Bind status filter
        const statusFilter = document.getElementById('service-status-filter');
        if (statusFilter && !statusFilter.dataset.bound) {
            statusFilter.addEventListener('change', function() {
                const value = this.value === 'ALL' ? null : this.value;
                handleStatusFilter(value);
            });
            statusFilter.dataset.bound = 'true';
        }

        // Bind refresh button
        const refreshBtn = document.getElementById('service-refresh-btn');
        if (refreshBtn && !refreshBtn.dataset.bound) {
            refreshBtn.addEventListener('click', function() {
                loadServices();
            });
            refreshBtn.dataset.bound = 'true';
        }

        // Bind create service button
        const createBtn = document.getElementById('create-service-btn');
        if (createBtn && !createBtn.dataset.bound) {
            createBtn.addEventListener('click', function() {
                showServiceFormModal(null); // null = create mode
            });
            createBtn.dataset.bound = 'true';
        }

        // Load services on initialization
        loadServices();
    } catch (error) {
        console.error('Error initializing service catalog:', error);
    }
}

// Expose functions globally
if (typeof window !== 'undefined') {
    window.initializeServiceCatalog = initializeServiceCatalog;
    window.loadServices = loadServices;
    window.handleServiceSearch = handleServiceSearch;
    window.handleStatusFilter = handleStatusFilter;
    window.handlePageChange = handlePageChange;
    window.renderServiceList = renderServiceList;
    window.renderServicePagination = renderServicePagination;
    window.ServiceCatalogState = ServiceCatalogState;
}

/**
 * Render the service list table
 */
function renderServiceList() {
    const list = document.getElementById('services-list');
    const empty = document.getElementById('services-empty');
    const grid = document.getElementById('service-catalog-grid');
    if (!list) return;

    list.replaceChildren();

    const servicesToRender = ServiceCatalogState.visibleServices;

    if (servicesToRender.length === 0) {
        if (grid) grid.style.display = 'none';
        if (empty) {
            empty.textContent = ServiceCatalogState.searchQuery || ServiceCatalogState.statusFilter
                ? 'No services available today for the selected filters.'
                : 'No services available today. Create a service or adjust availability.';
            empty.classList.remove('hidden');
            empty.classList.add('visible');
        }
        return;
    }

    if (grid) grid.style.display = 'block';
    if (empty) {
        empty.classList.add('hidden');
        empty.classList.remove('visible');
    }

    const fragment = document.createDocumentFragment();
    servicesToRender.forEach(service => {
        const code = service.serviceCode || 'N/A';
        const name = service.displayName || '—';
        const serviceId = service.id || '—';
        const status = service.status || 'UNKNOWN';
        const totalPurchases = typeof service.totalPurchases === 'number' ? service.totalPurchases : 0;
        const price = typeof service.lowestPrice === 'number' ? service.lowestPrice : 0;
        const currency = service.currency || 'INR';
        const createdAt = service.createdAt ? formatDate(service.createdAt) : '—';
        const normalizedStatus = status.toLowerCase();
        const statusClass = ['active', 'inactive', 'archived'].includes(normalizedStatus)
            ? `service-status-pill--${normalizedStatus}`
            : 'service-status-pill--neutral';

        const card = document.createElement('div');
        card.className = 'service-card';

        const header = document.createElement('div');
        header.className = 'service-card-header';

        const main = document.createElement('div');
        main.className = 'service-card-main';

        const title = document.createElement('div');
        title.className = 'service-card-title';
        title.textContent = name;

        const meta = document.createElement('div');
        meta.className = 'service-card-meta';

        const codeLine = document.createElement('span');
        codeLine.className = 'service-card-code';
        codeLine.textContent = code;

        const idLine = document.createElement('span');
        idLine.className = 'service-card-id';
        idLine.textContent = `ID: ${serviceId}`;

        meta.appendChild(codeLine);
        meta.appendChild(idLine);
        main.appendChild(title);
        main.appendChild(meta);

        const statusPill = document.createElement('span');
        statusPill.className = `service-status-pill ${statusClass}`;
        statusPill.textContent = status;

        header.appendChild(main);
        header.appendChild(statusPill);

        const stats = document.createElement('div');
        stats.className = 'service-card-stats';

        const purchasesStat = document.createElement('div');
        purchasesStat.className = 'service-stat';
        purchasesStat.innerHTML = `
            <div class="service-stat-label">Purchases</div>
            <div class="service-stat-value">${totalPurchases.toLocaleString()}</div>
        `;

        const priceStat = document.createElement('div');
        priceStat.className = 'service-stat';
        priceStat.innerHTML = `
            <div class="service-stat-label">Starting Price</div>
            <div class="service-stat-value">${price > 0 ? `${currency} ${(price / 100).toFixed(2)}` : '—'}</div>
        `;

        const createdStat = document.createElement('div');
        createdStat.className = 'service-stat';
        createdStat.innerHTML = `
            <div class="service-stat-label">Created</div>
            <div class="service-stat-value">${createdAt}</div>
        `;

        stats.appendChild(purchasesStat);
        stats.appendChild(priceStat);
        stats.appendChild(createdStat);

        const actions = document.createElement('div');
        actions.className = 'service-card-actions';

        const viewBtn = document.createElement('button');
        viewBtn.className = 'btn btn-secondary btn-sm';
        viewBtn.textContent = 'View';
        viewBtn.addEventListener('click', () => showServiceDetailModal(service.id));
        actions.appendChild(viewBtn);

        const editBtn = document.createElement('button');
        editBtn.className = 'btn btn-primary btn-sm';
        editBtn.textContent = 'Edit';
        editBtn.addEventListener('click', () => showServiceFormModal(service.id));
        actions.appendChild(editBtn);

        const cloneBtn = document.createElement('button');
        cloneBtn.className = 'btn btn-secondary btn-sm';
        cloneBtn.textContent = 'Clone';
        cloneBtn.addEventListener('click', () => showCloneServiceModal(service.id));
        actions.appendChild(cloneBtn);

        card.appendChild(header);
        card.appendChild(stats);
        card.appendChild(actions);
        fragment.appendChild(card);
    });

    list.replaceChildren(fragment);
}

/**
 * Update service catalog summary cards and count label
 */
function updateServiceCatalogSummary() {
    const totalEl = document.getElementById('service-total-count');
    const activeEl = document.getElementById('service-active-count');
    const inactiveEl = document.getElementById('service-inactive-count');
    const archivedEl = document.getElementById('service-archived-count');
    const countLabel = document.getElementById('service-count-label');

    const servicesToCount = ServiceCatalogState.visibleServices;
    const totalCount = servicesToCount.length;
    const counts = servicesToCount.reduce(
        (acc, service) => {
            const status = String(service.status || '').toUpperCase();
            if (status === 'ACTIVE') acc.active += 1;
            else if (status === 'INACTIVE') acc.inactive += 1;
            else if (status === 'ARCHIVED') acc.archived += 1;
            return acc;
        },
        { active: 0, inactive: 0, archived: 0 }
    );

    if (totalEl) totalEl.textContent = totalCount.toLocaleString();
    if (activeEl) activeEl.textContent = counts.active.toLocaleString();
    if (inactiveEl) inactiveEl.textContent = counts.inactive.toLocaleString();
    if (archivedEl) archivedEl.textContent = counts.archived.toLocaleString();
    if (countLabel) {
        countLabel.textContent = totalCount > 0
            ? `Showing ${totalCount} services available today`
            : 'Showing 0 services available today';
    }
}

/**
 * Check if a service is available today based on availability window
 * @param {Object} service - Service data
 * @returns {boolean}
 */
function isServiceAvailableToday(service) {
    if (!service) return false;
    const now = new Date();
    const start = service.availabilityStart ? new Date(service.availabilityStart) : null;
    const end = service.availabilityEnd ? new Date(service.availabilityEnd) : null;

    if (start && !isNaN(start.getTime()) && now < start) return false;
    if (end && !isNaN(end.getTime()) && now > end) return false;
    return true;
}

/**
 * Render pagination controls
 */
function renderServicePagination() {
    const paginationContainer = document.getElementById('service-pagination');
    if (!paginationContainer) return;

    paginationContainer.innerHTML = '';

    if (ServiceCatalogState.totalPages <= 1) {
        return; // Don't show pagination if only one page
    }

    // Previous button
    const prevButton = document.createElement('button');
    prevButton.className = `pagination-button ${ServiceCatalogState.currentPage === 1 ? 'disabled' : ''}`;
    prevButton.textContent = 'Previous';
    prevButton.disabled = ServiceCatalogState.currentPage === 1;
    prevButton.addEventListener('click', () => {
        if (ServiceCatalogState.currentPage > 1) {
            handlePageChange(ServiceCatalogState.currentPage - 1);
        }
    });
    paginationContainer.appendChild(prevButton);

    // Page buttons
    const maxButtons = 5;
    const startPage = Math.max(1, ServiceCatalogState.currentPage - Math.floor(maxButtons / 2));
    const endPage = Math.min(ServiceCatalogState.totalPages, startPage + maxButtons - 1);

    for (let i = startPage; i <= endPage; i++) {
        const pageButton = document.createElement('button');
        pageButton.className = `pagination-button ${i === ServiceCatalogState.currentPage ? 'active' : ''}`;
        pageButton.textContent = i;
        pageButton.addEventListener('click', () => {
            if (i !== ServiceCatalogState.currentPage) {
                handlePageChange(i);
            }
        });
        paginationContainer.appendChild(pageButton);
    }

    // Next button
    const nextButton = document.createElement('button');
    nextButton.className = `pagination-button ${ServiceCatalogState.currentPage === ServiceCatalogState.totalPages ? 'disabled' : ''}`;
    nextButton.textContent = 'Next';
    nextButton.disabled = ServiceCatalogState.currentPage === ServiceCatalogState.totalPages;
    nextButton.addEventListener('click', () => {
        if (ServiceCatalogState.currentPage < ServiceCatalogState.totalPages) {
            handlePageChange(ServiceCatalogState.currentPage + 1);
        }
    });
    paginationContainer.appendChild(nextButton);
}

/**
 * Show service creation/edit form modal
 * @param {string|null} serviceId - Service ID for edit mode, null for create mode
 */
function showServiceFormModal(serviceId) {
    const isEditMode = serviceId !== null;
    const modalTitle = isEditMode ? 'Edit Service' : 'Create New Service';

    // Show loading modal first
    const loadingContent = `
        <div class="modal-loading">
            <div class="loading-spinner"></div>
            <div class="loading-text">Loading service form...</div>
        </div>
    `;
    if (typeof showFinancePanel === 'function') {
        showFinancePanel(modalTitle, loadingContent);
    } else {
        showModal(modalTitle, loadingContent);
    }

    // If edit mode, fetch service data first
    if (isEditMode) {
        window.ServiceCatalogAPI.getService(serviceId)
            .then(response => {
                if (response.status === true && response.data) {
                    renderServiceForm(response.data.service, true);
                } else {
                    throw new Error(response.message || 'Failed to load service');
                }
            })
            .catch(error => {
                closeServiceFormPanel();
                showMessage('error', error.message || 'Failed to load service');
            });
    } else {
        // Create mode - show empty form
        renderServiceForm(null, false);
    }
}

/**
 * Render the service form with minimal, clean design
 * @param {Object|null} service - Service data for edit mode
 * @param {boolean} isEditMode - Whether in edit mode
 */
function renderServiceForm(service, isEditMode) {
    const modalTitle = isEditMode ? 'Edit Service' : 'Create New Service';
    const modalSubtitle = isEditMode 
        ? `Update service details for ${escapeHtml(service?.displayName || 'this service')}`
        : 'Add a new service to your catalog with pricing, features, and configuration options.';
    
    const modalContent = `
        <div class="finance-hero" style="margin-bottom: 1.25rem;">
            <div class="finance-kv__label">${isEditMode ? 'Update service' : 'New service'}</div>
            <div class="finance-hero-value">${isEditMode ? escapeHtml(service?.displayName || 'Service') : 'Service setup'}</div>
            <div class="modal-form-helper" style="margin-top: 0.5rem;">${modalSubtitle}</div>
        </div>
        <form id="service-form" class="modal-form">
            <!-- Service Name -->
            <div class="modal-form-group">
                <label for="service-name" class="modal-form-label required">Service Name</label>
                <input 
                    type="text" 
                    id="service-name" 
                    name="displayName" 
                    class="modal-form-input"
                    required
                    value="${escapeHtml(service?.displayName || '')}"
                    placeholder="e.g., Premium Plus"
                    autocomplete="off"
                />
                <span class="modal-form-helper">A friendly name for this service</span>
            </div>

            <!-- Service Code (only in create mode) -->
            ${!isEditMode ? `
            <div class="modal-form-group">
                <label for="service-code" class="modal-form-label required">Service Code</label>
                <input 
                    type="text" 
                    id="service-code" 
                    name="serviceCode" 
                    class="modal-form-input"
                    required
                    value="${escapeHtml(service?.serviceCode || '')}"
                    placeholder="PREMIUM_PLUS"
                    pattern="[A-Z0-9_]+"
                    title="Uppercase letters, numbers, and underscores only"
                    autocomplete="off"
                />
                <span class="modal-form-helper">Unique identifier (uppercase alphanumeric with underscores)</span>
            </div>
            ` : ''}

            <!-- Service Type / Category -->
            <div class="modal-form-group">
                <label for="service-type" class="modal-form-label">Service Type</label>
                <select id="service-type" name="serviceType" class="modal-form-select">
                    <option value="">Select a type...</option>
                    <option value="SUBSCRIPTION" ${service?.serviceType === 'SUBSCRIPTION' ? 'selected' : ''}>Subscription</option>
                    <option value="ONE_TIME" ${service?.serviceType === 'ONE_TIME' ? 'selected' : ''}>One-time Purchase</option>
                    <option value="USAGE_BASED" ${service?.serviceType === 'USAGE_BASED' ? 'selected' : ''}>Usage-based</option>
                </select>
                <span class="modal-form-helper">How customers will be billed for this service</span>
            </div>

            <!-- Description -->
            <div class="modal-form-group">
                <label for="description" class="modal-form-label required">Description</label>
                <textarea 
                    id="description" 
                    name="description" 
                    class="modal-form-textarea"
                    required
                    rows="3"
                    maxlength="500"
                    placeholder="Describe what this service offers and its key benefits..."
                >${escapeHtml(service?.description || '')}</textarea>
                <span class="modal-form-helper">Max 500 characters</span>
            </div>

            <!-- Environment / Scope (if applicable) -->
            <div class="modal-form-group">
                <label for="environment" class="modal-form-label">Environment</label>
                <select id="environment" name="environment" class="modal-form-select">
                    <option value="">All environments</option>
                    <option value="PRODUCTION" ${service?.environment === 'PRODUCTION' ? 'selected' : ''}>Production</option>
                    <option value="STAGING" ${service?.environment === 'STAGING' ? 'selected' : ''}>Staging</option>
                    <option value="DEVELOPMENT" ${service?.environment === 'DEVELOPMENT' ? 'selected' : ''}>Development</option>
                </select>
                <span class="modal-form-helper">Restrict service availability to specific environment</span>
            </div>

            <!-- Enable Immediately Toggle -->
            <div class="modal-form-group">
                <label style="display: flex; align-items: center; gap: 0.75rem; cursor: pointer; font-weight: 500; color: var(--text-color);">
                    <input 
                        type="checkbox" 
                        id="enable-immediately" 
                        name="enableImmediately"
                        style="width: 18px; height: 18px; cursor: pointer; accent-color: #6366f1;"
                        ${service?.status === 'ACTIVE' || !service ? 'checked' : ''}
                    />
                    <span>Enable immediately</span>
                </label>
                <span class="modal-form-helper">Service will be available to customers right away</span>
            </div>

            <!-- Advanced Options (Collapsible) -->
            <details class="modal-form-group" style="margin-top: 1rem;">
                <summary style="cursor: pointer; font-weight: 500; color: var(--text-color); padding: 0.5rem 0; list-style: none; user-select: none;">
                    <span style="display: inline-flex; align-items: center; gap: 0.5rem;">
                        <span>Advanced Options</span>
                        <span style="font-size: 0.75rem; color: var(--text-secondary);">(Optional)</span>
                    </span>
                </summary>
                <div style="margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--card-border); display: flex; flex-direction: column; gap: 1.5rem;">
                    <!-- Pricing Tiers -->
                    <div>
                        <label style="display: block; font-weight: 500; color: var(--text-color); margin-bottom: 0.75rem; font-size: 0.875rem;">Pricing Tiers</label>
                        <div id="pricing-tiers-container" style="display: flex; flex-direction: column; gap: 0.75rem; margin-bottom: 0.75rem;"></div>
                        <button type="button" class="modal-btn modal-btn-secondary" id="add-pricing-tier-btn" style="width: auto; padding: 0.5rem 1rem; font-size: 0.875rem;">
                            + Add Pricing Tier
                        </button>
                    </div>

                    <!-- Features -->
                    <div>
                        <label style="display: block; font-weight: 500; color: var(--text-color); margin-bottom: 0.75rem; font-size: 0.875rem;">Features</label>
                        <div id="features-container" style="display: flex; flex-direction: column; gap: 0.75rem; margin-bottom: 0.75rem;"></div>
                        <button type="button" class="modal-btn modal-btn-secondary" id="add-feature-btn" style="width: auto; padding: 0.5rem 1rem; font-size: 0.875rem;">
                            + Add Feature
                        </button>
                    </div>

                    <!-- Limits -->
                    <div>
                        <label style="display: block; font-weight: 500; color: var(--text-color); margin-bottom: 0.75rem; font-size: 0.875rem;">Limits</label>
                        <div id="limits-container" style="display: flex; flex-direction: column; gap: 0.75rem; margin-bottom: 0.75rem;"></div>
                        <button type="button" class="modal-btn modal-btn-secondary" id="add-limit-btn" style="width: auto; padding: 0.5rem 1rem; font-size: 0.875rem;">
                            + Add Limit
                        </button>
                    </div>

                    <!-- Availability -->
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
                        <div class="modal-form-group" style="margin: 0;">
                            <label for="availability-start" class="modal-form-label">Start Date</label>
                            <input 
                                type="datetime-local" 
                                id="availability-start" 
                                name="availabilityStart"
                                class="modal-form-input"
                                value="${service?.availabilityStart ? formatDateTimeLocal(service.availabilityStart) : ''}"
                            />
                        </div>
                        <div class="modal-form-group" style="margin: 0;">
                            <label for="availability-end" class="modal-form-label">End Date</label>
                            <input 
                                type="datetime-local" 
                                id="availability-end" 
                                name="availabilityEnd"
                                class="modal-form-input"
                                value="${service?.availabilityEnd ? formatDateTimeLocal(service.availabilityEnd) : ''}"
                            />
                        </div>
                    </div>
                </div>
            </details>

            <!-- Status message area -->
            <div id="service-form-status" style="display: none;"></div>
        </form>
    `;

    const modalFooter = `
        <button type="button" class="modal-btn modal-btn-secondary" onclick="closeServiceFormPanel()">Cancel</button>
        <button type="submit" form="service-form" class="modal-btn modal-btn-primary" id="service-submit-btn">
            <span id="service-submit-text">${isEditMode ? 'Update' : 'Create'}</span>
            <span id="service-submit-spinner" class="loading-spinner" style="display: none; width: 1rem; height: 1rem; margin-left: 0.5rem;"></span>
        </button>
    `;

    if (typeof showFinancePanel === 'function') {
        showFinancePanel(modalTitle, modalContent, { footer: modalFooter });
    } else {
        showModal(modalTitle, modalContent, { subtitle: modalSubtitle, footer: modalFooter });
    }

    // Initialize form after rendering
    setTimeout(() => {
        initializeServiceForm(service, isEditMode);
    }, 100);
}

function closeServiceFormPanel() {
    if (typeof closeFinancePanel === 'function') {
        closeFinancePanel();
        return;
    }
    if (typeof closeModal === 'function') {
        closeModal();
    }
}

/**
 * Initialize service form with event handlers and dynamic sections
 * @param {Object|null} service - Service data for edit mode
 * @param {boolean} isEditMode - Whether in edit mode
 */
function initializeServiceForm(service, isEditMode) {
    // Initialize pricing tiers
    const pricingTiersContainer = document.getElementById('pricing-tiers-container');
    const addPricingTierBtn = document.getElementById('add-pricing-tier-btn');
    
    if (pricingTiersContainer) {
        if (service && service.pricingTiers && service.pricingTiers.length > 0) {
            service.pricingTiers.forEach(tier => addPricingTierRow(tier));
        }
        // Don't add empty tier by default - let user add when needed
    }

    if (addPricingTierBtn) {
        addPricingTierBtn.addEventListener('click', () => addPricingTierRow(null));
    }

    // Initialize features
    const featuresContainer = document.getElementById('features-container');
    const addFeatureBtn = document.getElementById('add-feature-btn');
    
    if (featuresContainer) {
        if (service && service.features && service.features.length > 0) {
            service.features.forEach(feature => addFeatureRow(feature));
        }
        // Don't add empty feature by default
    }

    if (addFeatureBtn) {
        addFeatureBtn.addEventListener('click', () => addFeatureRow(null));
    }

    // Initialize limits
    const limitsContainer = document.getElementById('limits-container');
    const addLimitBtn = document.getElementById('add-limit-btn');
    
    if (limitsContainer) {
        if (service && service.limits) {
            Object.entries(service.limits).forEach(([key, limit]) => {
                addLimitRow({ key, ...limit });
            });
        }
    }

    if (addLimitBtn) {
        addLimitBtn.addEventListener('click', () => addLimitRow(null));
    }

    // Handle form submission
    const form = document.getElementById('service-form');
    if (form) {
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            handleServiceFormSubmit(service?.id, isEditMode);
        });
    }
}

/**
 * Add a pricing tier row with minimal design
 * @param {Object|null} tier - Tier data
 */
function addPricingTierRow(tier) {
    const container = document.getElementById('pricing-tiers-container');
    if (!container) return;

    const row = document.createElement('div');
    row.className = 'dynamic-row';
    row.style.cssText = 'background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 1rem; position: relative; transition: all 0.2s ease;';
    row.innerHTML = `
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; margin-bottom: 0.75rem;">
            <div>
                <label style="font-size: 0.8125rem; font-weight: 500; color: var(--text-color); margin-bottom: 0.375rem; display: block;">Amount (₹) *</label>
                <input 
                    type="number" 
                    class="pricing-tier-amount modal-form-input" 
                    required
                    min="0.01"
                    step="0.01"
                    value="${tier?.amount ? (tier.amount / 100).toFixed(2) : ''}"
                    placeholder="999.00"
                    style="width: 100%;"
                />
            </div>
            <div>
                <label style="font-size: 0.8125rem; font-weight: 500; color: var(--text-color); margin-bottom: 0.375rem; display: block;">Duration *</label>
                <div style="display: flex; gap: 0.5rem;">
                    <input 
                        type="number" 
                        class="pricing-tier-duration modal-form-input" 
                        required
                        min="1"
                        value="${tier?.duration || ''}"
                        placeholder="1"
                        style="flex: 1;"
                    />
                    <select class="pricing-tier-duration-type modal-form-select" required style="flex: 1;">
                        <option value="DAYS" ${tier?.durationType === 'DAYS' ? 'selected' : ''}>Days</option>
                        <option value="MONTHS" ${tier?.durationType === 'MONTHS' ? 'selected' : ''}>Months</option>
                        <option value="YEARS" ${tier?.durationType === 'YEARS' ? 'selected' : ''}>Years</option>
                    </select>
                </div>
            </div>
        </div>
        <div style="display: flex; gap: 1rem; align-items: center; font-size: 0.875rem; padding-top: 0.75rem; border-top: 1px solid var(--card-border);">
            <label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer; font-weight: 400; color: var(--text-color);">
                <input type="checkbox" class="pricing-tier-default" ${tier?.isDefault ? 'checked' : ''} style="width: 16px; height: 16px; cursor: pointer; accent-color: #6366f1;">
                <span>Default</span>
            </label>
            <label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer; font-weight: 400; color: var(--text-color);">
                <input type="checkbox" class="pricing-tier-featured" ${tier?.isFeatured ? 'checked' : ''} style="width: 16px; height: 16px; cursor: pointer; accent-color: #6366f1;">
                <span>Featured</span>
            </label>
            <input type="hidden" class="pricing-tier-currency" value="${tier?.currency || 'INR'}">
            <input type="hidden" class="pricing-tier-order" value="${tier?.displayOrder || 0}">
            <button type="button" class="btn-remove" title="Remove" style="margin-left: auto; background: transparent; border: 1px solid var(--card-border); color: var(--text-secondary); font-size: 1.125rem; cursor: pointer; padding: 0.25rem 0.5rem; width: 28px; height: 28px; line-height: 1; border-radius: 6px; transition: all 0.2s ease; display: flex; align-items: center; justify-content: center;">×</button>
        </div>
    `;

    const removeBtn = row.querySelector('.btn-remove');
    removeBtn.addEventListener('click', () => {
        row.remove();
    });
    removeBtn.addEventListener('mouseenter', () => {
        removeBtn.style.background = 'rgba(239, 68, 68, 0.1)';
        removeBtn.style.borderColor = 'rgba(239, 68, 68, 0.3)';
        removeBtn.style.color = '#ef4444';
    });
    removeBtn.addEventListener('mouseleave', () => {
        removeBtn.style.background = 'transparent';
        removeBtn.style.borderColor = 'var(--card-border)';
        removeBtn.style.color = 'var(--text-secondary)';
    });

    container.appendChild(row);
}

/**
 * Add a feature row with minimal design
 * @param {Object|null} feature - Feature data
 */
function addFeatureRow(feature) {
    const container = document.getElementById('features-container');
    if (!container) return;

    const row = document.createElement('div');
    row.className = 'dynamic-row';
    row.style.cssText = 'background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 0.875rem; position: relative; display: flex; gap: 0.75rem; align-items: flex-start; transition: all 0.2s ease;';
    row.innerHTML = `
        <input 
            type="text" 
            class="feature-description modal-form-input" 
            required
            maxlength="200"
            value="${escapeHtml(feature?.description || '')}"
            placeholder="Feature description..."
            style="flex: 1;"
        />
        <label style="display: flex; align-items: center; gap: 0.5rem; cursor: pointer; font-weight: 400; color: var(--text-color); font-size: 0.875rem; white-space: nowrap;">
            <input type="checkbox" class="feature-highlighted" ${feature?.isHighlighted ? 'checked' : ''} style="width: 16px; height: 16px; cursor: pointer; accent-color: #6366f1;">
            <span>Highlight</span>
        </label>
        <input type="hidden" class="feature-order" value="${feature?.displayOrder || 0}">
        <button type="button" class="btn-remove" title="Remove" style="background: transparent; border: 1px solid var(--card-border); color: var(--text-secondary); font-size: 1.125rem; cursor: pointer; padding: 0.25rem 0.5rem; width: 28px; height: 28px; line-height: 1; border-radius: 6px; transition: all 0.2s ease; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">×</button>
    `;

    const removeBtn = row.querySelector('.btn-remove');
    removeBtn.addEventListener('click', () => {
        row.remove();
    });
    removeBtn.addEventListener('mouseenter', () => {
        removeBtn.style.background = 'rgba(239, 68, 68, 0.1)';
        removeBtn.style.borderColor = 'rgba(239, 68, 68, 0.3)';
        removeBtn.style.color = '#ef4444';
    });
    removeBtn.addEventListener('mouseleave', () => {
        removeBtn.style.background = 'transparent';
        removeBtn.style.borderColor = 'var(--card-border)';
        removeBtn.style.color = 'var(--text-secondary)';
    });

    container.appendChild(row);
}

/**
 * Add a limit row with minimal design
 * @param {Object|null} limit - Limit data
 */
function addLimitRow(limit) {
    const container = document.getElementById('limits-container');
    if (!container) return;

    const row = document.createElement('div');
    row.className = 'dynamic-row';
    row.style.cssText = 'background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 0.875rem; position: relative; transition: all 0.2s ease;';
    row.innerHTML = `
        <div style="display: grid; grid-template-columns: 1fr 1fr 1fr 1fr auto; gap: 0.75rem; align-items: end;">
            <div>
                <label style="font-size: 0.8125rem; font-weight: 500; color: var(--text-color); margin-bottom: 0.375rem; display: block;">Key *</label>
                <input 
                    type="text" 
                    class="limit-key modal-form-input" 
                    required
                    value="${escapeHtml(limit?.key || '')}"
                    placeholder="api_calls"
                    style="width: 100%;"
                />
            </div>
            <div>
                <label style="font-size: 0.8125rem; font-weight: 500; color: var(--text-color); margin-bottom: 0.375rem; display: block;">Value *</label>
                <input 
                    type="number" 
                    class="limit-value modal-form-input" 
                    required
                    min="0"
                    value="${limit?.value || ''}"
                    placeholder="10000"
                    style="width: 100%;"
                />
            </div>
            <div>
                <label style="font-size: 0.8125rem; font-weight: 500; color: var(--text-color); margin-bottom: 0.375rem; display: block;">Unit *</label>
                <input 
                    type="text" 
                    class="limit-unit modal-form-input" 
                    required
                    value="${escapeHtml(limit?.unit || '')}"
                    placeholder="requests/day"
                    style="width: 100%;"
                />
            </div>
            <div>
                <label style="font-size: 0.8125rem; font-weight: 500; color: var(--text-color); margin-bottom: 0.375rem; display: block;">Type *</label>
                <select class="limit-type modal-form-select" required style="width: 100%;">
                    <option value="HARD" ${limit?.type === 'HARD' ? 'selected' : ''}>Hard</option>
                    <option value="SOFT" ${limit?.type === 'SOFT' ? 'selected' : ''}>Soft</option>
                </select>
            </div>
            <button type="button" class="btn-remove" title="Remove" style="background: transparent; border: 1px solid var(--card-border); color: var(--text-secondary); font-size: 1.125rem; cursor: pointer; padding: 0.25rem 0.5rem; width: 28px; height: 28px; line-height: 1; border-radius: 6px; transition: all 0.2s ease; display: flex; align-items: center; justify-content: center; margin-bottom: 0.375rem;">×</button>
        </div>
    `;

    const removeBtn = row.querySelector('.btn-remove');
    removeBtn.addEventListener('click', () => {
        row.remove();
    });
    removeBtn.addEventListener('mouseenter', () => {
        removeBtn.style.background = 'rgba(239, 68, 68, 0.1)';
        removeBtn.style.borderColor = 'rgba(239, 68, 68, 0.3)';
        removeBtn.style.color = '#ef4444';
    });
    removeBtn.addEventListener('mouseleave', () => {
        removeBtn.style.background = 'transparent';
        removeBtn.style.borderColor = 'var(--card-border)';
        removeBtn.style.color = 'var(--text-secondary)';
    });

    container.appendChild(row);
}

/**
 * Handle service form submission
 * @param {string|null} serviceId - Service ID for edit mode
 * @param {boolean} isEditMode - Whether in edit mode
 */
function handleServiceFormSubmit(serviceId, isEditMode) {
    const submitBtn = document.getElementById('service-submit-btn');
    const submitText = document.getElementById('service-submit-text');
    const submitSpinner = document.getElementById('service-submit-spinner');
    const statusDiv = document.getElementById('service-form-status');

    // Collect form data
    const serviceCodeInput = document.getElementById('service-code');
    const serviceCode = serviceCodeInput ? serviceCodeInput.value.trim() : null;
    const displayNameInput = document.getElementById('service-name');
    const displayName = displayNameInput ? displayNameInput.value.trim() : '';
    const description = document.getElementById('description').value.trim();
    const availabilityStart = document.getElementById('availability-start')?.value || null;
    const availabilityEnd = document.getElementById('availability-end')?.value || null;
    const enableImmediately = document.getElementById('enable-immediately')?.checked || false;

    // Collect pricing tiers
    const pricingTiers = [];
    document.querySelectorAll('#pricing-tiers-container .dynamic-row').forEach(row => {
        const amountInRupees = parseFloat(row.querySelector('.pricing-tier-amount').value);
        const tier = {
            amount: Math.round(amountInRupees * 100), // Convert rupees to paise
            currency: row.querySelector('.pricing-tier-currency').value.trim(),
            duration: parseInt(row.querySelector('.pricing-tier-duration').value),
            durationType: row.querySelector('.pricing-tier-duration-type').value,
            isDefault: row.querySelector('.pricing-tier-default').checked,
            isFeatured: row.querySelector('.pricing-tier-featured').checked,
            displayOrder: parseInt(row.querySelector('.pricing-tier-order').value) || 0
        };
        pricingTiers.push(tier);
    });

    // Collect features
    const features = [];
    document.querySelectorAll('#features-container .dynamic-row').forEach(row => {
        const feature = {
            description: row.querySelector('.feature-description').value.trim(),
            isHighlighted: row.querySelector('.feature-highlighted').checked,
            displayOrder: parseInt(row.querySelector('.feature-order').value) || 0
        };
        features.push(feature);
    });

    // Collect limits
    const limits = {};
    document.querySelectorAll('#limits-container .dynamic-row').forEach(row => {
        const key = row.querySelector('.limit-key').value.trim();
        if (key) {
            limits[key] = {
                value: parseInt(row.querySelector('.limit-value').value),
                unit: row.querySelector('.limit-unit').value.trim(),
                type: row.querySelector('.limit-type').value
            };
        }
    });

    // Validate
    if (!isEditMode && !serviceCode) {
        showFormError('Service code is required');
        return;
    }
    
    if (!displayName || !description) {
        showFormError('Please fill in all required fields');
        return;
    }

    // Pricing tiers, features, and limits are optional in the new minimal design
    // But we'll still validate if they exist
    if (pricingTiers.length === 0) {
        // Show warning but allow submission - user can add tiers later
        console.warn('No pricing tiers added');
    }

    if (features.length === 0) {
        // Show warning but allow submission - user can add features later
        console.warn('No features added');
    }
    
    // Clear any previous errors
    if (statusDiv) {
        statusDiv.style.display = 'none';
        statusDiv.className = '';
        statusDiv.textContent = '';
    }

    // Build request payload
    const payload = {
        displayName,
        description,
        pricingTiers: pricingTiers.length > 0 ? pricingTiers : [],
        features: features.length > 0 ? features : [],
        limits: Object.keys(limits).length > 0 ? limits : {},
        availabilityStart: availabilityStart ? new Date(availabilityStart).toISOString() : null,
        availabilityEnd: availabilityEnd ? new Date(availabilityEnd).toISOString() : null
    };

    // Add serviceCode only for create mode
    if (!isEditMode && serviceCode) {
        payload.serviceCode = serviceCode;
    }
    
    // Add status if enable immediately is checked
    if (enableImmediately) {
        payload.status = 'ACTIVE';
    }

    // Disable submit button and show spinner
    submitBtn.disabled = true;
    submitText.style.display = 'none';
    submitSpinner.style.display = 'inline-block';

    // Call API
    const apiCall = isEditMode
        ? window.ServiceCatalogAPI.updateService(serviceId, payload)
        : window.ServiceCatalogAPI.createService(payload);

    apiCall
        .then(response => {
            if (response.status === true) {
                showMessage('success', response.message || `Service ${isEditMode ? 'updated' : 'created'} successfully`);
                closeServiceFormPanel();
                loadServices(); // Reload the list
            } else {
                throw new Error(response.message || `Failed to ${isEditMode ? 'update' : 'create'} service`);
            }
        })
        .catch(error => {
            console.error('Service form submission error:', error);
            showFormError(error.message || `Failed to ${isEditMode ? 'update' : 'create'} service`);
        })
        .finally(() => {
            submitBtn.disabled = false;
            submitText.style.display = 'inline';
            submitSpinner.style.display = 'none';
        });
}

/**
 * Format datetime for datetime-local input
 * @param {string} isoString - ISO datetime string
 * @returns {string} Formatted datetime for input
 */
function formatDateTimeLocal(isoString) {
    if (!isoString) return '';
    try {
        const date = new Date(isoString);
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    } catch (e) {
        return '';
    }
}

/**
 * Show form error message in modal
 * @param {string} message - Error message
 */
function showFormError(message) {
    const statusDiv = document.getElementById('service-form-status');
    if (statusDiv) {
        statusDiv.className = 'modal-status modal-status-error';
        statusDiv.textContent = message;
        statusDiv.style.display = 'flex';
        statusDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    } else {
        showMessage('error', message);
    }
}

/**
 * Show service detail modal with tabs
 * @param {string} serviceId - Service ID
 */
function showServiceDetailModal(serviceId) {
    // Show loading modal
    const loadingContent = `
        <div class="modal-loading">
            <div class="loading-spinner"></div>
            <div class="loading-text">Loading service details...</div>
        </div>
    `;
    showModal('Service Details', loadingContent);

    // Fetch service details
    window.ServiceCatalogAPI.getService(serviceId)
        .then(response => {
            if (response.status === true && response.data) {
                renderServiceDetailModal(response.data);
            } else {
                throw new Error(response.message || 'Failed to load service details');
            }
        })
        .catch(error => {
            closeModal();
            showMessage('error', error.message || 'Failed to load service details');
        });
}

/**
 * Render service detail modal with tabs
 * @param {Object} data - Service detail data
 */
function renderServiceDetailModal(data) {
    const service = data.service;
    const analytics = data.analytics;
    const history = data.history;

    const modalContent = `
        <div class="service-detail-modal">
            <!-- Service Header -->
            <div class="service-detail-header">
                <div>
                    <h2 style="margin: 0 0 0.5rem 0;">${escapeHtml(service.displayName)}</h2>
                    <code style="font-size: 0.9rem; color: var(--text-secondary);">${escapeHtml(service.serviceCode)}</code>
                </div>
                <div style="display: flex; gap: 0.5rem; align-items: center;">
                    <span class="status-badge status-${service.status.toLowerCase()}">${service.status}</span>
                </div>
            </div>

            <!-- Action Buttons -->
            <div class="service-detail-actions">
                <button class="btn btn-primary btn-sm" onclick="showServiceFormModal('${service.id}')">
                    Edit Service
                </button>
                <button class="btn btn-secondary btn-sm" onclick="showCloneServiceModal('${service.id}')">
                    Clone Service
                </button>
                ${service.status === 'ACTIVE' ? `
                    <button class="btn btn-warning btn-sm" onclick="changeServiceStatus('${service.id}', 'INACTIVE')">
                        Deactivate
                    </button>
                ` : ''}
                ${service.status === 'INACTIVE' ? `
                    <button class="btn btn-success btn-sm" onclick="changeServiceStatus('${service.id}', 'ACTIVE')">
                        Activate
                    </button>
                    <button class="btn btn-danger btn-sm" onclick="changeServiceStatus('${service.id}', 'ARCHIVED')">
                        Archive
                    </button>
                ` : ''}
                ${service.status === 'ARCHIVED' ? `
                    <button class="btn btn-secondary btn-sm" onclick="changeServiceStatus('${service.id}', 'INACTIVE')">
                        Restore
                    </button>
                ` : ''}
            </div>

            <!-- Tabs -->
            <div class="tabs">
                <div class="tab active" data-tab="overview">Overview</div>
                <div class="tab" data-tab="analytics">Analytics</div>
                <div class="tab" data-tab="history">History</div>
            </div>

            <!-- Tab Panels -->
            <div class="tab-content">
                <!-- Overview Tab -->
                <div class="tab-panel active" data-panel="overview">
                    ${renderServiceOverview(service)}
                </div>

                <!-- Analytics Tab -->
                <div class="tab-panel" data-panel="analytics">
                    ${renderServiceAnalytics(analytics)}
                </div>

                <!-- History Tab -->
                <div class="tab-panel" data-panel="history">
                    ${renderServiceHistory(history)}
                </div>
            </div>
        </div>
    `;

    showModal('Service Details', modalContent);

    // Initialize tab switching
    setTimeout(() => {
        initializeServiceDetailTabs();
    }, 100);
}

/**
 * Render service overview tab
 * @param {Object} service - Service data
 * @returns {string} HTML content
 */
function renderServiceOverview(service) {
    const pricingTiersHtml = service.pricingTiers.map(tier => `
        <div class="pricing-tier-card">
            <div class="tier-header">
                <span class="tier-amount">${tier.currency} ${(tier.amount / 100).toFixed(2)}</span>
                <span class="tier-duration">/ ${tier.duration} ${tier.durationType.toLowerCase()}</span>
            </div>
            <div class="tier-badges">
                ${tier.isDefault ? '<span class="badge badge-default">Default</span>' : ''}
                ${tier.isFeatured ? '<span class="badge badge-featured">Featured</span>' : ''}
            </div>
        </div>
    `).join('');

    const featuresHtml = service.features.map(feature => `
        <div class="feature-item ${feature.isHighlighted ? 'highlighted' : ''}">
            <span class="feature-icon">${feature.isHighlighted ? '⭐' : '✓'}</span>
            <span class="feature-text">${escapeHtml(feature.description)}</span>
        </div>
    `).join('');

    const limitsHtml = Object.entries(service.limits || {}).map(([key, limit]) => `
        <div class="limit-item">
            <span class="limit-key">${escapeHtml(key)}</span>
            <span class="limit-value">${limit.value} ${escapeHtml(limit.unit)}</span>
            <span class="badge badge-${limit.type.toLowerCase()}">${limit.type}</span>
        </div>
    `).join('') || '<div style="color: var(--text-secondary); text-align: center; padding: 1rem;">No limits configured</div>';

    return `
        <div class="overview-section">
            <h3>Description</h3>
            <p>${escapeHtml(service.description)}</p>
        </div>

        <div class="overview-section">
            <h3>Pricing Tiers</h3>
            <div class="pricing-tiers-grid">
                ${pricingTiersHtml}
            </div>
        </div>

        <div class="overview-section">
            <h3>Features</h3>
            <div class="features-list">
                ${featuresHtml}
            </div>
        </div>

        <div class="overview-section">
            <h3>Limits</h3>
            <div class="limits-list">
                ${limitsHtml}
            </div>
        </div>

        <div class="overview-section">
            <h3>Availability</h3>
            <div class="detail-row">
                <span class="label">Start Date:</span>
                <span class="value">${service.availabilityStart ? formatDate(service.availabilityStart) : 'Not set'}</span>
            </div>
            <div class="detail-row">
                <span class="label">End Date:</span>
                <span class="value">${service.availabilityEnd ? formatDate(service.availabilityEnd) : 'Not set'}</span>
            </div>
        </div>

        <div class="overview-section">
            <h3>Metadata</h3>
            <div class="detail-row">
                <span class="label">Created At:</span>
                <span class="value">${formatDate(service.createdAt)}</span>
            </div>
            <div class="detail-row">
                <span class="label">Created By:</span>
                <span class="value">${escapeHtml(service.createdBy)}</span>
            </div>
            <div class="detail-row">
                <span class="label">Updated At:</span>
                <span class="value">${formatDate(service.updatedAt)}</span>
            </div>
            <div class="detail-row">
                <span class="label">Updated By:</span>
                <span class="value">${escapeHtml(service.updatedBy)}</span>
            </div>
        </div>
    `;
}

/**
 * Initialize tab switching for service detail modal
 */
function initializeServiceDetailTabs() {
    const tabs = document.querySelectorAll('.service-detail-modal .tab');
    const panels = document.querySelectorAll('.service-detail-modal .tab-panel');

    tabs.forEach(tab => {
        tab.addEventListener('click', function() {
            const targetTab = this.dataset.tab;

            // Remove active class from all tabs and panels
            tabs.forEach(t => t.classList.remove('active'));
            panels.forEach(p => p.classList.remove('active'));

            // Add active class to clicked tab and corresponding panel
            this.classList.add('active');
            const targetPanel = document.querySelector(`.service-detail-modal .tab-panel[data-panel="${targetTab}"]`);
            if (targetPanel) {
                targetPanel.classList.add('active');
            }
        });
    });
}

/**
 * Change service status
 * @param {string} serviceId - Service ID
 * @param {string} newStatus - New status
 */
function changeServiceStatus(serviceId, newStatus) {
    const confirmMessage = newStatus === 'ARCHIVED'
        ? 'Are you sure you want to archive this service? This action can be reversed later.'
        : `Are you sure you want to change the service status to ${newStatus}?`;

    if (!confirm(confirmMessage)) {
        return;
    }

    window.ServiceCatalogAPI.changeStatus(serviceId, newStatus)
        .then(response => {
            if (response.status === true) {
                showMessage('success', response.message || 'Service status updated successfully');
                closeModal();
                loadServices(); // Reload the list
            } else {
                throw new Error(response.message || 'Failed to change service status');
            }
        })
        .catch(error => {
            console.error('Status change error:', error);
            showMessage('error', error.message || 'Failed to change service status');
        });
}

/**
 * Show clone service modal
 * @param {string} sourceServiceId - Source service ID
 */
function showCloneServiceModal(sourceServiceId) {
    const modalContent = `
        <form id="clone-service-form" class="clone-service-form">
            <p>Enter a new service code for the cloned service. All other settings will be copied from the original service.</p>
            
            <div class="form-group">
                <label for="clone-service-code">New Service Code *</label>
                <input 
                    type="text" 
                    id="clone-service-code" 
                    name="newServiceCode" 
                    required
                    placeholder="PREMIUM_ENTERPRISE"
                    pattern="[A-Z0-9_]+"
                    title="Uppercase letters, numbers, and underscores only"
                />
                <small>Uppercase alphanumeric with underscores (e.g., PREMIUM_ENTERPRISE)</small>
            </div>

            <div class="form-actions">
                <button type="button" class="btn btn-secondary" onclick="closeModal()">Cancel</button>
                <button type="submit" class="btn btn-primary" id="clone-submit-btn">
                    <span id="clone-submit-text">Clone Service</span>
                    <span id="clone-submit-spinner" class="loading-spinner" style="display: none;"></span>
                </button>
            </div>
        </form>
    `;

    showModal('Clone Service', modalContent);

    // Handle form submission
    setTimeout(() => {
        const form = document.getElementById('clone-service-form');
        if (form) {
            form.addEventListener('submit', (e) => {
                e.preventDefault();
                handleCloneServiceSubmit(sourceServiceId);
            });
        }
    }, 100);
}

/**
 * Handle clone service form submission
 * @param {string} sourceServiceId - Source service ID
 */
function handleCloneServiceSubmit(sourceServiceId) {
    const submitBtn = document.getElementById('clone-submit-btn');
    const submitText = document.getElementById('clone-submit-text');
    const submitSpinner = document.getElementById('clone-submit-spinner');
    const newServiceCode = document.getElementById('clone-service-code').value.trim();

    if (!newServiceCode) {
        showMessage('error', 'Please enter a new service code');
        return;
    }

    // Disable submit button and show spinner
    submitBtn.disabled = true;
    submitText.style.display = 'none';
    submitSpinner.style.display = 'inline-block';

    window.ServiceCatalogAPI.cloneService(sourceServiceId, newServiceCode)
        .then(response => {
            if (response.status === true) {
                showMessage('success', response.message || 'Service cloned successfully');
                closeModal();
                loadServices(); // Reload the list
            } else {
                throw new Error(response.message || 'Failed to clone service');
            }
        })
        .catch(error => {
            console.error('Clone service error:', error);
            showMessage('error', error.message || 'Failed to clone service');
        })
        .finally(() => {
            submitBtn.disabled = false;
            submitText.style.display = 'inline';
            submitSpinner.style.display = 'none';
        });
}

/**
 * Render service analytics tab
 * @param {Object} analytics - Analytics data
 * @returns {string} HTML content
 */
function renderServiceAnalytics(analytics) {
    if (!analytics) {
        return '<div style="text-align: center; padding: 2rem; color: var(--text-secondary);">No analytics data available</div>';
    }

    const monthlyTrendHtml = analytics.monthlyTrend && analytics.monthlyTrend.length > 0
        ? renderMonthlyTrendChart(analytics.monthlyTrend)
        : '<div style="text-align: center; padding: 1rem; color: var(--text-secondary);">No trend data available</div>';

    return `
        <div class="analytics-section">
            <h3>Purchase Metrics</h3>
            <div class="metrics-grid">
                <div class="metric-card">
                    <div class="metric-label">Total Purchases</div>
                    <div class="metric-value">${analytics.totalPurchases || 0}</div>
                </div>
                <div class="metric-card">
                    <div class="metric-label">Total Revenue</div>
                    <div class="metric-value">₹${(analytics.totalRevenue || 0).toFixed(2)}</div>
                </div>
            </div>
        </div>

        <div class="analytics-section">
            <h3>Monthly Purchase Trend (Last 12 Months)</h3>
            ${monthlyTrendHtml}
        </div>
    `;
}

/**
 * Render monthly trend chart
 * @param {Array} monthlyTrend - Monthly trend data
 * @returns {string} HTML content
 */
function renderMonthlyTrendChart(monthlyTrend) {
    // Find max values for scaling
    const maxCount = Math.max(...monthlyTrend.map(m => m.count || 0), 1);
    const maxRevenue = Math.max(...monthlyTrend.map(m => m.revenue || 0), 1);

    const barsHtml = monthlyTrend.map(month => {
        const countHeight = ((month.count || 0) / maxCount) * 100;
        const revenueHeight = ((month.revenue || 0) / maxRevenue) * 100;

        return `
            <div class="chart-bar-group">
                <div class="chart-bars">
                    <div class="chart-bar chart-bar-count" style="height: ${countHeight}%" title="${month.count} purchases">
                        <span class="bar-value">${month.count || 0}</span>
                    </div>
                    <div class="chart-bar chart-bar-revenue" style="height: ${revenueHeight}%" title="₹${(month.revenue || 0).toFixed(2)}">
                        <span class="bar-value">₹${(month.revenue || 0).toFixed(0)}</span>
                    </div>
                </div>
                <div class="chart-label">${escapeHtml(month.month || '')}</div>
            </div>
        `;
    }).join('');

    return `
        <div class="chart-legend">
            <div class="legend-item">
                <span class="legend-color legend-count"></span>
                <span>Purchases</span>
            </div>
            <div class="legend-item">
                <span class="legend-color legend-revenue"></span>
                <span>Revenue</span>
            </div>
        </div>
        <div class="monthly-trend-chart">
            ${barsHtml}
        </div>
    `;
}

/**
 * Render service history tab
 * @param {Array} history - History entries
 * @returns {string} HTML content
 */
function renderServiceHistory(history) {
    if (!history || history.length === 0) {
        return '<div style="text-align: center; padding: 2rem; color: var(--text-secondary);">No history available</div>';
    }

    const historyHtml = history.map(entry => {
        const changeTypeClass = entry.changeType.toLowerCase().replace('_', '-');
        const changesHtml = entry.changes && Object.keys(entry.changes).length > 0
            ? renderHistoryChanges(entry.changes)
            : '<div style="color: var(--text-secondary); font-style: italic;">No detailed changes recorded</div>';

        return `
            <div class="history-entry">
                <div class="history-header">
                    <div class="history-meta">
                        <span class="history-badge history-${changeTypeClass}">${entry.changeType.replace('_', ' ')}</span>
                        <span class="history-date">${formatDate(entry.changedAt)}</span>
                    </div>
                    <div class="history-user">
                        <span class="history-user-label">By:</span>
                        <span class="history-user-email">${escapeHtml(entry.changedBy)}</span>
                    </div>
                </div>
                <div class="history-changes">
                    ${changesHtml}
                </div>
            </div>
        `;
    }).join('');

    return `
        <div class="history-timeline">
            ${historyHtml}
        </div>
    `;
}

/**
 * Render history changes
 * @param {Object} changes - Changes object
 * @returns {string} HTML content
 */
function renderHistoryChanges(changes) {
    const changesHtml = Object.entries(changes).map(([key, change]) => {
        const fieldName = change.field || key;
        const oldValue = change.oldValue !== null && change.oldValue !== undefined
            ? escapeHtml(String(change.oldValue))
            : '<span style="color: var(--text-secondary); font-style: italic;">None</span>';
        const newValue = change.newValue !== null && change.newValue !== undefined
            ? escapeHtml(String(change.newValue))
            : '<span style="color: var(--text-secondary); font-style: italic;">None</span>';

        return `
            <div class="change-item">
                <div class="change-field">${escapeHtml(fieldName)}</div>
                <div class="change-values">
                    <div class="change-old">
                        <span class="change-label">Before:</span>
                        <span class="change-value">${oldValue}</span>
                    </div>
                    <div class="change-arrow">→</div>
                    <div class="change-new">
                        <span class="change-label">After:</span>
                        <span class="change-value">${newValue}</span>
                    </div>
                </div>
            </div>
        `;
    }).join('');

    return `<div class="changes-list">${changesHtml}</div>`;
}
