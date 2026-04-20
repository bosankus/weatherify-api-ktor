/**
 * Service Catalog Tab (fresh implementation)
 * Minimal, reliable list view with search, status filter, and pagination.
 */
(function () {
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
            } catch (_) { }
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
                <div class="data-cell data-cell--right">
                    <button class="action-icon-btn service-edit-btn" data-id="${service.id}" aria-label="Edit service ${safeText(service.displayName)}" title="Edit Service">
                        <span class="material-icons">edit</span>
                    </button>
                </div>
            `;

            const editBtn = row.querySelector('.service-edit-btn');
            if (editBtn) {
                editBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    openServicePanel(service.id);
                });
            }

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

    // Sliding Panel Implementation - Row Layout
    function renderServicePanel() {
        if (document.getElementById('service-sliding-panel')) return;

        // Ensure styles are injected (in case CSS file is missing or cached)
        injectPanelStyles();

        // Remove old modal if present to avoid ID conflicts
        const oldModal = document.getElementById('service-form-modal');
        if (oldModal) oldModal.remove();

        const panel = document.createElement('div');
        panel.id = 'service-sliding-panel';
        panel.className = 'sliding-panel';
        // Add padding-top: 0 to body to allow header to stick properly if needed,
        // but here we use the structure from the request
        panel.innerHTML = `
            <!-- Header -->
            <div class="sliding-panel-header" style="padding: 1.25rem 2rem; border-bottom: 1px solid var(--card-border); display: flex; align-items: center; justify-content: space-between; background: var(--card-bg); position: sticky; top: 0; z-index: 10;">
                <div style="display:flex; flex-direction:column;">
                    <h3 id="service-panel-title" style="margin:0; font-size: 1.1rem; font-weight: 700; color: var(--heading-color);">Edit service details</h3>
                </div>
                <!-- Top actions -->
                <div style="display:flex; gap: 0.75rem;">
                    <button type="button" class="btn btn-secondary" id="service-panel-cancel" style="border-radius: 6px; padding: 0.5rem 1rem; font-size: 0.9rem;">Cancel</button>
                    <button type="button" class="btn btn-primary" id="service-panel-submit" style="border-radius: 6px; padding: 0.5rem 1.25rem; font-size: 0.9rem; background: #2563eb; border: none; color: white;">Save</button>
                </div>
            </div>

            <!-- Body -->
            <div class="sliding-panel-body" style="padding: 0 2rem 2rem 2rem;">
                <form id="service-form">
                    
                    <!-- Row 1: Display Name -->
                    <div class="service-edit-row">
                        <div class="service-edit-label-col">
                            <span class="service-edit-label">Service Title</span>
                            <span class="service-edit-sublabel">It must describe one service only.</span>
                        </div>
                        <div class="service-edit-input-col">
                            <input type="text" id="service-display-name" class="premium-input" placeholder="e.g. Premium Monthly Subscription" required>
                        </div>
                    </div>

                    <!-- Row 2: Service Code -->
                    <div class="service-edit-row">
                        <div class="service-edit-label-col">
                            <span class="service-edit-label">Service Code</span>
                            <span class="service-edit-sublabel">Unique identifier (uppercase, numbers, underscores).</span>
                        </div>
                        <div class="service-edit-input-col">
                            <input type="text" id="service-code" class="premium-input" placeholder="e.g. PREMIUM_MONTHLY" pattern="[A-Z0-9_]+" required>
                        </div>
                    </div>

                    <!-- Row 3: Description -->
                    <div class="service-edit-row">
                        <div class="service-edit-label-col">
                            <span class="service-edit-label">Description</span>
                            <span class="service-edit-sublabel">Provide a short description about the service. Keep it short and to the point.</span>
                        </div>
                        <div class="service-edit-input-col">
                            <div style="border: 1px solid var(--card-border); border-radius: 8px; overflow: hidden; background: var(--bg-color);">
                                <div style="padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--card-border); background: var(--card-hover-bg); display: flex; gap: 1rem; color: var(--text-secondary);">
                                    <span class="material-icons" style="font-size: 16px; cursor: pointer;">format_bold</span>
                                    <span class="material-icons" style="font-size: 16px; cursor: pointer;">format_italic</span>
                                    <span class="material-icons" style="font-size: 16px; cursor: pointer;">format_underlined</span>
                                    <span class="material-icons" style="font-size: 16px; cursor: pointer;">link</span>
                                    <span class="material-icons" style="font-size: 16px; cursor: pointer;">format_list_bulleted</span>
                                </div>
                                <textarea id="service-description" class="premium-textarea" rows="6" placeholder="Description" style="border: none; border-radius: 0; background: transparent;"></textarea>
                            </div>
                        </div>
                    </div>

                    <!-- Row 4: Pricing -->
                    <div class="service-edit-row">
                        <div class="service-edit-label-col">
                            <span class="service-edit-label">Pricing details</span>
                            <span class="edit-chip" style="font-size: 0.7rem; align-self: flex-start; margin-top: 0.25rem;">Required</span>
                        </div>
                        <div class="service-edit-input-col">
                            <div style="display: grid; grid-template-columns: 2fr 1fr; gap: 1rem;">
                                <input type="number" id="service-price" class="premium-input" placeholder="Price (in paise, e.g. 9900)" required>
                                <select id="service-currency" class="premium-select">
                                    <option value="INR">INR (₹)</option>
                                    <option value="USD">USD ($)</option>
                                    <option value="EUR">EUR (€)</option>
                                </select>
                            </div>
                        </div>
                    </div>

                    <!-- Row 5: Features (Requirements) -->
                    <div class="service-edit-row">
                        <div class="service-edit-label-col">
                            <span class="service-edit-label">Service Features</span>
                            <span class="edit-chip" style="font-size: 0.7rem; align-self: flex-start; margin-top: 0.25rem;">Optional</span>
                            <span class="service-edit-sublabel" style="margin-top: 0.25rem;">Are there any specific features?</span>
                        </div>
                        <div class="service-edit-input-col">
                            <div class="chip-container" id="service-features-chips">
                                <!-- Chips will be rendered here -->
                            </div>
                            <button type="button" class="add-chip-btn" id="add-feature-btn">
                                + Add feature
                            </button>
                        </div>
                    </div>

                    <!-- Row 6: Status & Config -->
                    <div class="service-edit-row">
                        <div class="service-edit-label-col">
                            <span class="service-edit-label">Configuration</span>
                            <span class="service-edit-sublabel">Pick one or multiple options</span>
                        </div>
                        <div class="service-edit-input-col">
                            <div class="checkbox-grid">
                                <label class="checkbox-card">
                                    <input type="radio" name="service-status-radio" value="ACTIVE" id="status-active" checked>
                                    <span>Active</span>
                                </label>
                                <label class="checkbox-card">
                                    <input type="radio" name="service-status-radio" value="INACTIVE" id="status-inactive">
                                    <span>Inactive</span>
                                </label>
                                <label class="checkbox-card">
                                    <input type="radio" name="service-status-radio" value="ARCHIVED" id="status-archived">
                                    <span>Archived</span>
                                </label>
                            </div>
                            <div style="margin-top: 0.5rem;">
                                <label class="checkbox-card" style="display: inline-flex; width: auto; padding-right: 1.5rem;">
                                    <input type="checkbox" id="service-featured">
                                    <span>Featured Service</span>
                                </label>
                            </div>
                            <!-- Hidden select for compatibility -->
                            <select id="service-status" style="display:none">
                                <option value="ACTIVE">Active</option>
                                <option value="INACTIVE">Inactive</option>
                                <option value="ARCHIVED">Archived</option>
                            </select>
                        </div>
                    </div>

                    <!-- Row 7: Availability -->
                    <div class="service-edit-row">
                        <div class="service-edit-label-col">
                            <span class="service-edit-label">Availability Schedule</span>
                            <span class="edit-chip" style="font-size: 0.7rem; align-self: flex-start; margin-top: 0.25rem;">Optional</span>
                            <span class="service-edit-sublabel" style="margin-top: 0.25rem;">You can pick a valid date range.</span>
                        </div>
                        <div class="service-edit-input-col">
                             <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
                                <div>
                                    <label style="font-size: 0.85rem; color: var(--text-secondary); margin-bottom: 0.25rem; display: block;">From</label>
                                    <input type="date" id="service-available-from" class="premium-input">
                                </div>
                                <div>
                                    <label style="font-size: 0.85rem; color: var(--text-secondary); margin-bottom: 0.25rem; display: block;">To</label>
                                    <input type="date" id="service-available-until" class="premium-input">
                                </div>
                             </div>
                        </div>
                    </div>

                </form>
                <div id="service-form-error" class="message error-message hidden" style="margin-top: 1.5rem;"></div>
            </div>

            <!-- Hidden Close Button for Accessibility/Logic -->
            <button id="service-panel-close" style="display: none;"></button>
        `;
        document.body.appendChild(panel);

        const backdrop = document.createElement('div');
        backdrop.id = 'service-panel-backdrop';
        backdrop.className = 'sliding-backdrop';
        document.body.appendChild(backdrop);

        // Bind events
        const closeHandler = () => closeServicePanel();
        getEl('service-panel-close').addEventListener('click', closeHandler);
        getEl('service-panel-cancel').addEventListener('click', closeHandler);
        getEl('service-panel-backdrop').addEventListener('click', closeHandler);

        getEl('service-panel-submit').addEventListener('click', handleServiceFormSubmit);

        // Add Feature Button
        const addFeatBtn = getEl('add-feature-btn');
        if (addFeatBtn) {
            addFeatBtn.addEventListener('click', promptAndAddFeature);
        }

        // Bind radio buttons to hidden select (for compatibility)
        const radios = panel.querySelectorAll('input[name="service-status-radio"]');
        radios.forEach(radio => {
            radio.addEventListener('change', (e) => {
                if (e.target.checked) {
                    const select = getEl('service-status');
                    if (select) select.value = e.target.value;
                }
            });
        });
    }

    function promptAndAddFeature() {
        // Use a simple prompt for now, or build a mini-inline form if preferred.
        // The request says "Job Details" style often implies just a list of chips.
        const feature = prompt("Enter feature description:");
        if (feature && feature.trim()) {
            addFeatureChip(feature.trim());
        }
    }

    function addFeatureChip(text) {
        const container = getEl('service-features-chips');
        if (!container) return;

        const chip = document.createElement('div');
        chip.className = 'edit-chip';
        chip.dataset.feature = text;
        chip.innerHTML = `
            <span>${safeText(text)}</span>
            <span class="material-icons edit-chip-remove" style="font-size: 16px;">close</span>
        `;
        chip.querySelector('.edit-chip-remove').addEventListener('click', () => chip.remove());
        container.appendChild(chip);
    }

    function openServicePanel(serviceId = null) {
        renderServicePanel();
        const panel = getEl('service-sliding-panel');
        const backdrop = getEl('service-panel-backdrop');
        const title = getEl('service-panel-title');

        const form = getEl('service-form');
        const errorDiv = getEl('service-form-error');

        // Reset form
        if (form) {
            form.reset();
            delete form.dataset.editId;
        }

        const codeInput = getEl('service-code');
        if (codeInput) codeInput.disabled = false;

        // Clear features
        const featuresContainer = getEl('service-features-chips');
        if (featuresContainer) featuresContainer.innerHTML = '';

        // Reset status radio to active
        const statusActive = getEl('status-active');
        if (statusActive) statusActive.checked = true;
        const statusSelect = getEl('service-status');
        if (statusSelect) statusSelect.value = 'ACTIVE';

        if (errorDiv) {
            errorDiv.classList.add('hidden');
            errorDiv.textContent = '';
        }

        const isEditMode = serviceId !== null;
        if (title) title.textContent = isEditMode ? 'Edit service details' : 'Create new service';

        if (isEditMode) {
            loadServiceDataIntoForm(serviceId);
        }

        // Show panel
        requestAnimationFrame(() => {
            if (panel) panel.classList.add('active');
            if (backdrop) backdrop.classList.add('active');
        });
        document.body.style.overflow = 'hidden';
    }

    function closeServicePanel() {
        const panel = getEl('service-sliding-panel');
        const backdrop = getEl('service-panel-backdrop');
        if (panel) panel.classList.remove('active');
        if (backdrop) backdrop.classList.remove('active');
        document.body.style.overflow = '';
    }

    async function loadServiceDataIntoForm(serviceId) {
        try {
            const response = await fetch(`/services/${encodeURIComponent(serviceId)}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Accept': 'application/json' }
            });

            if (!response.ok) throw new Error('Failed to load service details');

            const data = await response.json();
            if (data.status && data.data) {
                const service = data.data.service || data.data;

                // Populate form fields
                const codeInput = getEl('service-code');
                const nameInput = getEl('service-display-name');
                const descInput = getEl('service-description');
                const priceInput = getEl('service-price');
                const currencySelect = getEl('service-currency');
                const statusSelect = getEl('service-status');
                const fromInput = getEl('service-available-from');
                const untilInput = getEl('service-available-until');

                if (codeInput) {
                    codeInput.value = service.serviceCode || '';
                    codeInput.disabled = true;
                }
                if (nameInput) nameInput.value = service.displayName || '';
                if (descInput) descInput.value = service.description || '';

                if (priceInput && service.pricingTiers && service.pricingTiers.length > 0) {
                    priceInput.value = service.pricingTiers[0].amount || 0;
                    if (currencySelect) currencySelect.value = service.pricingTiers[0].currency || 'INR';
                } else if (priceInput) {
                    priceInput.value = service.lowestPrice || 0;
                    if (currencySelect) currencySelect.value = service.currency || 'INR';
                }

                // Handle Status
                const statusVal = service.status || 'ACTIVE';
                if (statusSelect) statusSelect.value = statusVal;
                // Sync Radio
                const radio = document.querySelector(`input[name="service-status-radio"][value="${statusVal}"]`);
                if (radio) radio.checked = true;

                const featuredCheckbox = getEl('service-featured');
                if (featuredCheckbox) {
                    featuredCheckbox.checked = service.pricingTiers?.some(t => t.isFeatured) || false;
                }

                // Populate Features Chips
                const featuresContainer = getEl('service-features-chips');
                if (featuresContainer) {
                    featuresContainer.innerHTML = '';
                    if (service.features && service.features.length > 0) {
                        service.features.forEach(f => addFeatureChip(f.description));
                    }
                }

                if (fromInput && (service.availabilityStart || service.availableFrom)) {
                    const date = (service.availabilityStart || service.availableFrom).split('T')[0];
                    fromInput.value = date;
                }
                if (untilInput && (service.availabilityEnd || service.availableUntil)) {
                    const date = (service.availabilityEnd || service.availableUntil).split('T')[0];
                    untilInput.value = date;
                }

                // Store ID on form for submit (removed from dataset on reset, add here)
                const form = getEl('service-form');
                if (form) form.dataset.editId = serviceId;
            }
        } catch (error) {
            console.error('Error loading service data:', error);
            if (typeof window.showMessage === 'function') {
                window.showMessage('error', 'Failed to load service details');
            }
        }
    }

    async function handleServiceFormSubmit(event) {
        if (event) event.preventDefault();

        const errorDiv = getEl('service-form-error');
        const submitBtn = getEl('service-panel-submit');

        if (errorDiv) {
            errorDiv.classList.add('hidden');
            errorDiv.textContent = '';
        }

        // Get form values
        const form = getEl('service-form');
        const isEditMode = form && !!form.dataset.editId;
        const serviceId = form ? form.dataset.editId : null;

        const serviceCode = getEl('service-code')?.value.trim();
        const displayName = getEl('service-display-name')?.value.trim();
        const description = getEl('service-description')?.value.trim();
        const price = parseInt(getEl('service-price')?.value || '0', 10);
        const currency = getEl('service-currency')?.value || 'INR';
        const status = getEl('service-status')?.value || 'ACTIVE';
        const isFeatured = getEl('service-featured')?.checked || false;
        const availableFrom = getEl('service-available-from')?.value || null;
        const availableUntil = getEl('service-available-until')?.value || null;

        // Collect features from CHIPS
        const features = [];
        const chips = document.querySelectorAll('#service-features-chips .edit-chip');
        chips.forEach((chip, index) => {
            const text = chip.dataset.feature;
            if (text) {
                features.push({
                    description: text,
                    displayOrder: index,
                    isHighlighted: false
                });
            }
        });

        // Validate
        if (!displayName || price < 0 || (!isEditMode && !serviceCode)) {
            if (errorDiv) {
                errorDiv.textContent = 'Please fill in all required fields correctly';
                errorDiv.classList.remove('hidden');
            }
            return;
        }

        // Build request body
        let requestBody;
        let url = '/services';
        let method = 'POST';

        const payloadStart = availableFrom ? `${availableFrom}T00:00:00Z` : null;
        const payloadEnd = availableUntil ? `${availableUntil}T23:59:59Z` : null;

        const pricingTiers = [{
            amount: price,
            currency: currency,
            duration: 1,
            durationType: "MONTHS",
            isDefault: true,
            isFeatured: isFeatured
        }];

        const basePayload = {
            displayName,
            description: description || "",
            pricingTiers,
            features,
            availabilityStart: payloadStart,
            availabilityEnd: payloadEnd,
            status: status
        };

        if (isEditMode) {
            url = `/services/${encodeURIComponent(serviceId)}`;
            method = 'PUT';
            requestBody = basePayload;
        } else {
            requestBody = {
                serviceCode,
                ...basePayload
            };
        }

        // Disable submit button
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.innerHTML = `Saving...`;
        }

        try {
            const response = await fetch(url, {
                method: method,
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `Failed to ${isEditMode ? 'update' : 'create'} service`);
            }

            const data = await response.json();

            if (data.status) {
                if (typeof window.showMessage === 'function') {
                    window.showMessage('success', `Service ${isEditMode ? 'updated' : 'created'} successfully!`);
                }
                closeServicePanel();
                await loadServices(); // Reload the list
            } else {
                throw new Error(data.message || `Failed to ${isEditMode ? 'update' : 'create'} service`);
            }
        } catch (error) {
            console.error(`Error ${isEditMode ? 'updating' : 'creating'} service:`, error);
            if (errorDiv) {
                errorDiv.textContent = error.message || `Failed to ${isEditMode ? 'update' : 'create'} service`;
                errorDiv.classList.remove('hidden');
            }
        } finally {
            // Re-enable submit button
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = 'Save';
            }
        }
    }

    function bindPanelControls() {
        const createBtn = getEl('create-service-btn');
        if (createBtn && !createBtn.dataset.bound) {
            createBtn.addEventListener('click', () => openServicePanel(null));
            createBtn.dataset.bound = 'true';
        }

        // Close panel on Escape key
        if (!document.body.dataset.panelEscapeBound) {
            document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    const panel = getEl('service-sliding-panel');
                    if (panel && panel.classList.contains('active')) {
                        closeServicePanel();
                    }
                }
            });
            document.body.dataset.panelEscapeBound = 'true';
        }
    }

    async function initializeServiceCatalog(options = {}) {
        if (!state.initialized) {
            bindControls();
            bindPanelControls();
            state.initialized = true;
        }

        if (options.forceRefresh) {
            state.page = 1;
        }

        await loadServices();
    }

    function injectPanelStyles() {
        if (document.getElementById('service-panel-styles')) {
            document.getElementById('service-panel-styles').remove();
        }

        const style = document.createElement('style');
        style.id = 'service-panel-styles';
        style.textContent = `
            /* Row Layout Design */
            .service-edit-row {
                display: grid; 
                grid-template-columns: 200px 1fr; 
                gap: 2rem;
                padding: 1.5rem 0; 
                border-bottom: 1px solid var(--card-border, #f1f5f9);
            }
            .service-edit-row:last-child { border-bottom: none; }
            
            .service-edit-label-col { 
                display: flex; flex-direction: column; gap: 0.35rem; 
                padding-top: 0.5rem; /* Align simpler labels with input top */
            }
            .service-edit-label { 
                font-size: 0.95rem; font-weight: 600; 
                color: var(--heading-color, #0f172a); 
            }
            .service-edit-sublabel { 
                font-size: 0.8rem; color: var(--text-secondary, #64748b); 
                line-height: 1.5; 
            }
            
            .service-edit-input-col { 
                display: flex; flex-direction: column; gap: 0.75rem; 
                min-width: 0; /* Prevent overflow grid blowout */
            }
            
            /* Inputs */
            .premium-input, .premium-select, .premium-textarea {
                width: 100%; 
                padding: 0.75rem 1rem; 
                border-radius: 8px;
                border: 1px solid var(--card-border, #cbd5e1);
                background: var(--bg-color, #ffffff); 
                color: var(--text-color, #334155);
                font-family: inherit; font-size: 0.95rem;
                transition: all 0.2s ease;
            }
            .premium-input:focus, .premium-select:focus, .premium-textarea:focus {
                border-color: #3b82f6;
                box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
                outline: none;
            }
            
            /* Chip UI */
            .chip-container { display: flex; flex-wrap: wrap; gap: 0.5rem; }
            .edit-chip {
                display: inline-flex; align-items: center; gap: 0.5rem;
                padding: 0.4rem 0.85rem; 
                background: var(--card-hover-bg, #f8fafc);
                border: 1px solid var(--card-border, #e2e8f0); 
                border-radius: 999px;
                font-size: 0.85rem; font-weight: 500;
                color: var(--text-color, #334155);
                transition: all 0.2s;
            }
            .edit-chip:hover {
                background: #eef2ff; border-color: #c7d2fe; color: #4338ca;
            }
            .edit-chip-remove { 
                cursor: pointer; color: #94a3b8; font-size: 16px; 
                display: flex; align-items: center; justify-content: center;
                transition: color 0.2s;
            }
            .edit-chip-remove:hover { color: #ef4444; }
            
            .add-chip-btn {
                align-self: flex-start;
                background: none; border: none; padding: 0.5rem 0;
                color: #2563eb; font-weight: 600; font-size: 0.9rem;
                cursor: pointer; display: inline-flex; align-items: center; gap: 0.25rem;
            }
            .add-chip-btn:hover { text-decoration: underline; }

            /* Selection Cards */
            .checkbox-grid { 
                display: grid; 
                grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); 
                gap: 0.75rem; 
            }
            .checkbox-card {
                display: flex; align-items: center; gap: 0.75rem; padding: 0.85rem;
                border: 1px solid var(--card-border, #e2e8f0); 
                border-radius: 10px; cursor: pointer;
                background: var(--card-bg, #ffffff);
                transition: all 0.2s;
            }
            .checkbox-card:hover {
                border-color: #bfdbfe; background: #eff6ff;
            }
            .checkbox-card:has(input:checked) {
                border-color: #3b82f6; background: #eff6ff;
            }
            
            /* Responsive Utilities */
            @media (max-width: 768px) {
                .service-edit-row {
                    grid-template-columns: 1fr;
                    gap: 0.75rem;
                    padding: 1.25rem 0;
                }
                .service-edit-label-col {
                    flex-direction: row;
                    align-items: baseline;
                    justify-content: space-between;
                    flex-wrap: wrap;
                    gap: 0.5rem;
                }
                .service-edit-sublabel { display: none; }
                .checkbox-grid { grid-template-columns: 1fr 1fr; }
            }

            @media (max-width: 480px) {
                .checkbox-grid { grid-template-columns: 1fr; }
                .btn { padding: 0.5rem 0.85rem; font-size: 0.85rem; }
            }
        `;
        document.head.appendChild(style);
    }

    window.initializeServiceCatalog = initializeServiceCatalog;
    window.ServiceCatalogTab = {
        initialize: initializeServiceCatalog,
        refresh: loadServices,
        showCreateModal: () => openServicePanel(null),
        showEditModal: (id) => openServicePanel(id)
    };
})();
