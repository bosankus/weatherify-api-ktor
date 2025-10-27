/**
 * Service Catalog Management functionality for admin dashboard
 * Handles service CRUD operations, analytics, and history tracking
 */

// Service Catalog state management
const ServiceCatalogState = {
    services: [],
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
        const token = localStorage.getItem('jwt_token');
        const params = new URLSearchParams({
            page: page.toString(),
            pageSize: pageSize.toString()
        });
        if (status) params.append('status', status);
        if (search) params.append('search', search);

        return fetch(`/admin/services?${params.toString()}`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            }
        }).then(response => {
            if (!response.ok) throw new Error('Failed to fetch services');
            return response.json();
        });
    },

    /**
     * Get service details by ID
     * @param {string} id - Service ID
     * @returns {Promise} API response
     */
    getService(id) {
        const token = localStorage.getItem('jwt_token');
        return fetch(`/admin/services/${encodeURIComponent(id)}`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            }
        }).then(response => {
            if (!response.ok) throw new Error('Failed to fetch service details');
            return response.json();
        });
    },

    /**
     * Create a new service
     * @param {Object} serviceData - Service data
     * @returns {Promise} API response
     */
    createService(serviceData) {
        const token = localStorage.getItem('jwt_token');
        return fetch('/admin/services', {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            },
            body: JSON.stringify(serviceData)
        }).then(response => {
            if (!response.ok) throw new Error('Failed to create service');
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
        const token = localStorage.getItem('jwt_token');
        return fetch(`/admin/services/${encodeURIComponent(id)}`, {
            method: 'PUT',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            },
            body: JSON.stringify(serviceData)
        }).then(response => {
            if (!response.ok) throw new Error('Failed to update service');
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
        const token = localStorage.getItem('jwt_token');
        return fetch(`/admin/services/${encodeURIComponent(id)}/status`, {
            method: 'PATCH',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            },
            body: JSON.stringify({ status })
        }).then(response => {
            if (!response.ok) throw new Error('Failed to change service status');
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
        const token = localStorage.getItem('jwt_token');
        return fetch(`/admin/services/${encodeURIComponent(id)}/clone`, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            },
            body: JSON.stringify({ newServiceCode })
        }).then(response => {
            if (!response.ok) throw new Error('Failed to clone service');
            return response.json();
        });
    },

    /**
     * Get service analytics
     * @param {string} id - Service ID
     * @returns {Promise} API response
     */
    getAnalytics(id) {
        const token = localStorage.getItem('jwt_token');
        return fetch(`/admin/services/${encodeURIComponent(id)}/analytics`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            }
        }).then(response => {
            if (!response.ok) throw new Error('Failed to fetch analytics');
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

    window.ServiceCatalogAPI.listServices(
        ServiceCatalogState.currentPage,
        ServiceCatalogState.pageSize,
        ServiceCatalogState.statusFilter,
        ServiceCatalogState.searchQuery
    )
        .then(response => {
            if (loader) loader.style.display = 'none';

            if (response.status === true && response.data) {
                const data = response.data;
                ServiceCatalogState.services = data.services || [];
                ServiceCatalogState.totalCount = data.totalCount || 0;
                ServiceCatalogState.totalPages = data.totalPages || 1;
                ServiceCatalogState.currentPage = data.page || 1;

                renderServiceList();
                renderServicePagination();
            } else {
                throw new Error(response.message || 'Failed to load services');
            }
        })
        .catch(error => {
            if (loader) loader.style.display = 'none';
            console.error('Error loading services:', error);
            showMessage('error', error.message || 'Failed to load services');
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
}

/**
 * Render the service list table
 */
function renderServiceList() {
    const tableBody = document.getElementById('services-table-body');
    if (!tableBody) return;

    const fragment = document.createDocumentFragment();

    if (ServiceCatalogState.services.length === 0) {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 6;
        cell.textContent = ServiceCatalogState.searchQuery || ServiceCatalogState.statusFilter
            ? 'No services found matching your criteria'
            : 'No services available. Create your first service to get started.';
        cell.style.textAlign = 'center';
        cell.style.padding = '2rem';
        cell.style.color = 'var(--text-secondary)';
        row.appendChild(cell);
        fragment.appendChild(row);
        tableBody.replaceChildren(fragment);
        return;
    }

    ServiceCatalogState.services.forEach(service => {
        const row = document.createElement('tr');

        // Service Code cell
        const codeCell = document.createElement('td');
        const codeWrapper = document.createElement('div');
        codeWrapper.style.display = 'flex';
        codeWrapper.style.flexDirection = 'column';
        codeWrapper.style.gap = '0.25rem';
        
        const codeText = document.createElement('code');
        codeText.textContent = service.serviceCode || 'N/A';
        codeText.style.fontWeight = '600';
        codeText.style.color = 'var(--text-color)';
        codeWrapper.appendChild(codeText);
        
        const nameText = document.createElement('span');
        nameText.textContent = service.displayName || '';
        nameText.style.fontSize = '0.85rem';
        nameText.style.color = 'var(--text-secondary)';
        codeWrapper.appendChild(nameText);
        
        codeCell.appendChild(codeWrapper);
        row.appendChild(codeCell);

        // Status cell
        const statusCell = document.createElement('td');
        statusCell.style.textAlign = 'center';
        const statusBadge = document.createElement('span');
        statusBadge.className = 'status-badge';
        const status = service.status || 'UNKNOWN';
        statusBadge.classList.add(`status-${status.toLowerCase()}`);
        statusBadge.textContent = status;
        statusCell.appendChild(statusBadge);
        row.appendChild(statusCell);

        // Active Subscriptions cell
        const subsCell = document.createElement('td');
        subsCell.style.textAlign = 'center';
        subsCell.textContent = service.activeSubscriptions || 0;
        row.appendChild(subsCell);

        // Price cell
        const priceCell = document.createElement('td');
        priceCell.style.textAlign = 'right';
        const price = service.lowestPrice || 0;
        const currency = service.currency || 'INR';
        priceCell.textContent = `${currency} ${(price / 100).toFixed(2)}`;
        row.appendChild(priceCell);

        // Created At cell
        const createdCell = document.createElement('td');
        createdCell.textContent = formatDate(service.createdAt);
        row.appendChild(createdCell);

        // Actions cell
        const actionsCell = document.createElement('td');
        actionsCell.style.textAlign = 'center';
        actionsCell.style.whiteSpace = 'nowrap';
        
        const viewBtn = document.createElement('button');
        viewBtn.className = 'btn-details btn-sm';
        viewBtn.textContent = 'View';
        viewBtn.style.marginRight = '0.5rem';
        viewBtn.addEventListener('click', () => showServiceDetailModal(service.id));
        actionsCell.appendChild(viewBtn);

        const editBtn = document.createElement('button');
        editBtn.className = 'btn-primary btn-sm';
        editBtn.textContent = 'Edit';
        editBtn.style.marginRight = '0.5rem';
        editBtn.addEventListener('click', () => showServiceFormModal(service.id));
        actionsCell.appendChild(editBtn);

        const cloneBtn = document.createElement('button');
        cloneBtn.className = 'btn-secondary btn-sm';
        cloneBtn.textContent = 'Clone';
        cloneBtn.addEventListener('click', () => showCloneServiceModal(service.id));
        actionsCell.appendChild(cloneBtn);

        row.appendChild(actionsCell);
        fragment.appendChild(row);
    });

    tableBody.replaceChildren(fragment);
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
        <div style="text-align: center; padding: 2rem;">
            <div class="loading-spinner" style="display: inline-block; margin-bottom: 1rem;"></div>
            <div>Loading service form...</div>
        </div>
    `;
    showModal(modalTitle, loadingContent);

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
                closeModal();
                showMessage('error', error.message || 'Failed to load service');
            });
    } else {
        // Create mode - show empty form
        renderServiceForm(null, false);
    }
}

/**
 * Render the service form
 * @param {Object|null} service - Service data for edit mode
 * @param {boolean} isEditMode - Whether in edit mode
 */
function renderServiceForm(service, isEditMode) {
    const modalTitle = isEditMode ? 'Edit Service' : 'Create New Service';
    
    const modalContent = `
        <form id="service-form" class="service-form">
            <!-- Basic Information -->
            <div class="form-section">
                <h3 class="form-section-title">Basic Information</h3>
                
                <div class="form-group">
                    <label for="service-code">Service Code *</label>
                    <input 
                        type="text" 
                        id="service-code" 
                        name="serviceCode" 
                        required
                        ${isEditMode ? 'disabled' : ''}
                        value="${escapeHtml(service?.serviceCode || '')}"
                        placeholder="PREMIUM_PLUS"
                        pattern="[A-Z0-9_]+"
                        title="Uppercase letters, numbers, and underscores only"
                    />
                    <small>Uppercase alphanumeric with underscores (e.g., PREMIUM_PLUS)</small>
                </div>

                <div class="form-group">
                    <label for="display-name">Display Name *</label>
                    <input 
                        type="text" 
                        id="display-name" 
                        name="displayName" 
                        required
                        value="${escapeHtml(service?.displayName || '')}"
                        placeholder="Premium Plus"
                    />
                </div>

                <div class="form-group">
                    <label for="description">Description *</label>
                    <textarea 
                        id="description" 
                        name="description" 
                        required
                        rows="3"
                        maxlength="500"
                        placeholder="Describe the service offering..."
                    >${escapeHtml(service?.description || '')}</textarea>
                    <small>Maximum 500 characters</small>
                </div>
            </div>

            <!-- Pricing Tiers -->
            <div class="form-section">
                <h3 class="form-section-title">Pricing Tiers</h3>
                <div id="pricing-tiers-container"></div>
                <button type="button" class="btn-secondary btn-sm" id="add-pricing-tier-btn">
                    + Add Pricing Tier
                </button>
            </div>

            <!-- Features -->
            <div class="form-section">
                <h3 class="form-section-title">Features</h3>
                <div id="features-container"></div>
                <button type="button" class="btn-secondary btn-sm" id="add-feature-btn">
                    + Add Feature
                </button>
            </div>

            <!-- Limits Configuration -->
            <div class="form-section">
                <h3 class="form-section-title">Limits Configuration (Optional)</h3>
                <div id="limits-container"></div>
                <button type="button" class="btn-secondary btn-sm" id="add-limit-btn">
                    + Add Limit
                </button>
            </div>

            <!-- Availability Dates -->
            <div class="form-section">
                <h3 class="form-section-title">Availability (Optional)</h3>
                
                <div class="form-group">
                    <label for="availability-start">Start Date</label>
                    <input 
                        type="datetime-local" 
                        id="availability-start" 
                        name="availabilityStart"
                        value="${service?.availabilityStart ? formatDateTimeLocal(service.availabilityStart) : ''}"
                    />
                </div>

                <div class="form-group">
                    <label for="availability-end">End Date</label>
                    <input 
                        type="datetime-local" 
                        id="availability-end" 
                        name="availabilityEnd"
                        value="${service?.availabilityEnd ? formatDateTimeLocal(service.availabilityEnd) : ''}"
                    />
                </div>
            </div>

            <!-- Form Actions -->
            <div class="form-actions">
                <button type="button" class="btn btn-secondary" onclick="closeModal()">Cancel</button>
                <button type="submit" class="btn btn-primary" id="service-submit-btn">
                    <span id="service-submit-text">${isEditMode ? 'Update Service' : 'Create Service'}</span>
                    <span id="service-submit-spinner" class="loading-spinner" style="display: none;"></span>
                </button>
            </div>
        </form>
    `;

    showModal(modalTitle, modalContent);

    // Initialize form after rendering
    setTimeout(() => {
        initializeServiceForm(service, isEditMode);
    }, 100);
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
    
    if (service && service.pricingTiers && service.pricingTiers.length > 0) {
        service.pricingTiers.forEach(tier => addPricingTierRow(tier));
    } else {
        // Add one empty tier by default
        addPricingTierRow(null);
    }

    if (addPricingTierBtn) {
        addPricingTierBtn.addEventListener('click', () => addPricingTierRow(null));
    }

    // Initialize features
    const featuresContainer = document.getElementById('features-container');
    const addFeatureBtn = document.getElementById('add-feature-btn');
    
    if (service && service.features && service.features.length > 0) {
        service.features.forEach(feature => addFeatureRow(feature));
    } else {
        // Add one empty feature by default
        addFeatureRow(null);
    }

    if (addFeatureBtn) {
        addFeatureBtn.addEventListener('click', () => addFeatureRow(null));
    }

    // Initialize limits
    const limitsContainer = document.getElementById('limits-container');
    const addLimitBtn = document.getElementById('add-limit-btn');
    
    if (service && service.limits) {
        Object.entries(service.limits).forEach(([key, limit]) => {
            addLimitRow({ key, ...limit });
        });
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
 * Add a pricing tier row
 * @param {Object|null} tier - Tier data
 */
function addPricingTierRow(tier) {
    const container = document.getElementById('pricing-tiers-container');
    if (!container) return;

    const row = document.createElement('div');
    row.className = 'dynamic-row';
    row.innerHTML = `
        <div class="dynamic-row-content">
            <div class="form-row">
                <div class="form-group">
                    <label>Amount (in paise) *</label>
                    <input 
                        type="number" 
                        class="pricing-tier-amount" 
                        required
                        min="1"
                        value="${tier?.amount || ''}"
                        placeholder="99900"
                    />
                </div>
                <div class="form-group">
                    <label>Currency</label>
                    <input 
                        type="text" 
                        class="pricing-tier-currency" 
                        value="${tier?.currency || 'INR'}"
                        placeholder="INR"
                    />
                </div>
                <div class="form-group">
                    <label>Duration *</label>
                    <input 
                        type="number" 
                        class="pricing-tier-duration" 
                        required
                        min="1"
                        value="${tier?.duration || ''}"
                        placeholder="1"
                    />
                </div>
                <div class="form-group">
                    <label>Duration Type *</label>
                    <select class="pricing-tier-duration-type" required>
                        <option value="DAYS" ${tier?.durationType === 'DAYS' ? 'selected' : ''}>Days</option>
                        <option value="MONTHS" ${tier?.durationType === 'MONTHS' ? 'selected' : ''}>Months</option>
                        <option value="YEARS" ${tier?.durationType === 'YEARS' ? 'selected' : ''}>Years</option>
                    </select>
                </div>
            </div>
            <div class="form-row">
                <div class="form-group">
                    <label>
                        <input type="checkbox" class="pricing-tier-default" ${tier?.isDefault ? 'checked' : ''}>
                        Default Tier
                    </label>
                </div>
                <div class="form-group">
                    <label>
                        <input type="checkbox" class="pricing-tier-featured" ${tier?.isFeatured ? 'checked' : ''}>
                        Featured
                    </label>
                </div>
                <div class="form-group">
                    <label>Display Order</label>
                    <input 
                        type="number" 
                        class="pricing-tier-order" 
                        min="0"
                        value="${tier?.displayOrder || 0}"
                    />
                </div>
            </div>
        </div>
        <button type="button" class="btn-remove" title="Remove tier">×</button>
    `;

    const removeBtn = row.querySelector('.btn-remove');
    removeBtn.addEventListener('click', () => {
        row.remove();
    });

    container.appendChild(row);
}

/**
 * Add a feature row
 * @param {Object|null} feature - Feature data
 */
function addFeatureRow(feature) {
    const container = document.getElementById('features-container');
    if (!container) return;

    const row = document.createElement('div');
    row.className = 'dynamic-row';
    row.innerHTML = `
        <div class="dynamic-row-content">
            <div class="form-group" style="flex: 1;">
                <label>Feature Description *</label>
                <input 
                    type="text" 
                    class="feature-description" 
                    required
                    maxlength="200"
                    value="${escapeHtml(feature?.description || '')}"
                    placeholder="Unlimited API calls"
                />
            </div>
            <div class="form-group">
                <label>
                    <input type="checkbox" class="feature-highlighted" ${feature?.isHighlighted ? 'checked' : ''}>
                    Highlighted
                </label>
            </div>
            <div class="form-group">
                <label>Display Order</label>
                <input 
                    type="number" 
                    class="feature-order" 
                    min="0"
                    value="${feature?.displayOrder || 0}"
                />
            </div>
        </div>
        <button type="button" class="btn-remove" title="Remove feature">×</button>
    `;

    const removeBtn = row.querySelector('.btn-remove');
    removeBtn.addEventListener('click', () => {
        row.remove();
    });

    container.appendChild(row);
}

/**
 * Add a limit row
 * @param {Object|null} limit - Limit data
 */
function addLimitRow(limit) {
    const container = document.getElementById('limits-container');
    if (!container) return;

    const row = document.createElement('div');
    row.className = 'dynamic-row';
    row.innerHTML = `
        <div class="dynamic-row-content">
            <div class="form-group">
                <label>Limit Key *</label>
                <input 
                    type="text" 
                    class="limit-key" 
                    required
                    value="${escapeHtml(limit?.key || '')}"
                    placeholder="api_calls"
                />
            </div>
            <div class="form-group">
                <label>Value *</label>
                <input 
                    type="number" 
                    class="limit-value" 
                    required
                    min="0"
                    value="${limit?.value || ''}"
                    placeholder="10000"
                />
            </div>
            <div class="form-group">
                <label>Unit *</label>
                <input 
                    type="text" 
                    class="limit-unit" 
                    required
                    value="${escapeHtml(limit?.unit || '')}"
                    placeholder="requests/day"
                />
            </div>
            <div class="form-group">
                <label>Type *</label>
                <select class="limit-type" required>
                    <option value="HARD" ${limit?.type === 'HARD' ? 'selected' : ''}>Hard</option>
                    <option value="SOFT" ${limit?.type === 'SOFT' ? 'selected' : ''}>Soft</option>
                </select>
            </div>
        </div>
        <button type="button" class="btn-remove" title="Remove limit">×</button>
    `;

    const removeBtn = row.querySelector('.btn-remove');
    removeBtn.addEventListener('click', () => {
        row.remove();
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

    // Collect form data
    const serviceCode = document.getElementById('service-code').value.trim();
    const displayName = document.getElementById('display-name').value.trim();
    const description = document.getElementById('description').value.trim();
    const availabilityStart = document.getElementById('availability-start').value || null;
    const availabilityEnd = document.getElementById('availability-end').value || null;

    // Collect pricing tiers
    const pricingTiers = [];
    document.querySelectorAll('#pricing-tiers-container .dynamic-row').forEach(row => {
        const tier = {
            amount: parseInt(row.querySelector('.pricing-tier-amount').value),
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
    if (!serviceCode || !displayName || !description) {
        showMessage('error', 'Please fill in all required fields');
        return;
    }

    if (pricingTiers.length === 0) {
        showMessage('error', 'At least one pricing tier is required');
        return;
    }

    if (features.length === 0) {
        showMessage('error', 'At least one feature is required');
        return;
    }

    // Build request payload
    const payload = {
        displayName,
        description,
        pricingTiers,
        features,
        limits,
        availabilityStart: availabilityStart ? new Date(availabilityStart).toISOString() : null,
        availabilityEnd: availabilityEnd ? new Date(availabilityEnd).toISOString() : null
    };

    // Add serviceCode only for create mode
    if (!isEditMode) {
        payload.serviceCode = serviceCode;
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
                closeModal();
                loadServices(); // Reload the list
            } else {
                throw new Error(response.message || `Failed to ${isEditMode ? 'update' : 'create'} service`);
            }
        })
        .catch(error => {
            console.error('Service form submission error:', error);
            showMessage('error', error.message || `Failed to ${isEditMode ? 'update' : 'create'} service`);
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
 * Show service detail modal with tabs
 * @param {string} serviceId - Service ID
 */
function showServiceDetailModal(serviceId) {
    // Show loading modal
    const loadingContent = `
        <div style="text-align: center; padding: 2rem;">
            <div class="loading-spinner" style="display: inline-block; margin-bottom: 1rem;"></div>
            <div>Loading service details...</div>
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
            <h3>Subscription Metrics</h3>
            <div class="metrics-grid">
                <div class="metric-card">
                    <div class="metric-label">Active Subscriptions</div>
                    <div class="metric-value">${analytics.activeSubscriptions || 0}</div>
                </div>
                <div class="metric-card">
                    <div class="metric-label">Total Subscriptions</div>
                    <div class="metric-value">${analytics.totalSubscriptions || 0}</div>
                </div>
                <div class="metric-card">
                    <div class="metric-label">Total Revenue</div>
                    <div class="metric-value">₹${(analytics.totalRevenue || 0).toFixed(2)}</div>
                </div>
                <div class="metric-card">
                    <div class="metric-label">Avg. Duration</div>
                    <div class="metric-value">${(analytics.averageDuration || 0).toFixed(1)} days</div>
                </div>
            </div>
        </div>

        <div class="analytics-section">
            <h3>Popular Pricing Tier</h3>
            <div class="popular-tier">
                ${analytics.popularPricingTier 
                    ? `<code>${escapeHtml(analytics.popularPricingTier)}</code>`
                    : '<span style="color: var(--text-secondary);">No data available</span>'
                }
            </div>
        </div>

        <div class="analytics-section">
            <h3>Monthly Subscription Trend (Last 12 Months)</h3>
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
                    <div class="chart-bar chart-bar-count" style="height: ${countHeight}%" title="${month.count} subscriptions">
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
                <span>Subscriptions</span>
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
