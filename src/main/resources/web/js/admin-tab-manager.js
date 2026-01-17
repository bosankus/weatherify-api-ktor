/**
 * Admin Dashboard Tab Manager
 * Optimizes tab switching by tracking loaded state and avoiding redundant API calls
 * Implements lazy loading and intelligent data refresh strategies
 */

(function() {
    'use strict';

    /**
     * Tab state manager
     */
    class TabStateManager {
        constructor() {
            this.tabs = new Map();
            this.activeTab = null;
            this.initialized = false;
        }

        /**
         * Register a tab with its configuration
         */
        registerTab(tabId, config) {
            this.tabs.set(tabId, {
                id: tabId,
                loaded: false,
                loading: false,
                lastLoaded: null,
                error: null,
                data: null,
                ...config
            });
        }

        /**
         * Get tab state
         */
        getTab(tabId) {
            return this.tabs.get(tabId);
        }

        /**
         * Mark tab as loaded
         */
        markLoaded(tabId, data = null) {
            const tab = this.tabs.get(tabId);
            if (tab) {
                tab.loaded = true;
                tab.loading = false;
                tab.lastLoaded = Date.now();
                tab.error = null;
                if (data !== null) {
                    tab.data = data;
                }
                this.tabs.set(tabId, tab);
            }
        }

        /**
         * Mark tab as loading
         */
        markLoading(tabId) {
            const tab = this.tabs.get(tabId);
            if (tab) {
                tab.loading = true;
                tab.error = null;
                this.tabs.set(tabId, tab);
            }
        }

        /**
         * Mark tab as error
         */
        markError(tabId, error) {
            const tab = this.tabs.get(tabId);
            if (tab) {
                tab.loading = false;
                tab.error = error;
                this.tabs.set(tabId, tab);
            }
        }

        /**
         * Check if tab needs refresh
         */
        needsRefresh(tabId, maxAge = 5 * 60 * 1000) {
            const tab = this.tabs.get(tabId);
            if (!tab || !tab.loaded || !tab.lastLoaded) {
                return true;
            }
            
            const age = Date.now() - tab.lastLoaded;
            return age > maxAge;
        }

        /**
         * Invalidate tab (force reload on next activation)
         */
        invalidate(tabId) {
            const tab = this.tabs.get(tabId);
            if (tab) {
                tab.loaded = false;
                tab.lastLoaded = null;
                tab.data = null;
                this.tabs.set(tabId, tab);
            }
        }

        /**
         * Set active tab
         */
        setActive(tabId) {
            this.activeTab = tabId;
        }

        /**
         * Get active tab
         */
        getActive() {
            return this.activeTab;
        }

        /**
         * Get all tab states
         */
        getAllStates() {
            const states = {};
            for (const [id, tab] of this.tabs.entries()) {
                states[id] = {
                    loaded: tab.loaded,
                    loading: tab.loading,
                    lastLoaded: tab.lastLoaded,
                    hasError: !!tab.error,
                    age: tab.lastLoaded ? Date.now() - tab.lastLoaded : null
                };
            }
            return states;
        }
    }

    // Create global tab state manager
    window.AdminTabManager = new TabStateManager();

    /**
     * Tab configuration with load functions
     */
    const TAB_CONFIGS = {
        'iam': {
            name: 'IAM',
            loadFn: async function(forceRefresh = false) {
                console.log('[Tab] Loading IAM tab', { forceRefresh });
                
                // Check if already loaded and not forcing refresh
                const tab = window.AdminTabManager.getTab('iam');
                if (!forceRefresh && tab && tab.loaded && !window.AdminTabManager.needsRefresh('iam')) {
                    console.log('[Tab] IAM already loaded, skipping API call');
                    return;
                }

                window.AdminTabManager.markLoading('iam');
                
                try {
                    // Use cached API if available
                    if (typeof window.CachedAPI !== 'undefined') {
                        const data = await window.CachedAPI.getUsers(1, 10, forceRefresh);
                        window.AdminTabManager.markLoaded('iam', data);
                        
                        // Render users if loadUsers function exists
                        if (typeof window.loadUsers === 'function') {
                            window.loadUsers(1, 10);
                        }
                    } else if (typeof window.loadUsers === 'function') {
                        // Fallback to direct load
                        window.loadUsers(1, 10);
                        window.AdminTabManager.markLoaded('iam');
                    }
                } catch (error) {
                    console.error('[Tab] Error loading IAM:', error);
                    window.AdminTabManager.markError('iam', error.message);
                    throw error;
                }
            },
            refreshInterval: 5 * 60 * 1000 // 5 minutes
        },

        'reports': {
            name: 'Reports',
            loadFn: async function(forceRefresh = false) {
                console.log('[Tab] Loading Reports tab', { forceRefresh });
                
                const tab = window.AdminTabManager.getTab('reports');
                if (!forceRefresh && tab && tab.loaded && !window.AdminTabManager.needsRefresh('reports')) {
                    console.log('[Tab] Reports already loaded, skipping API call');
                    // Still ensure UI is visible
                    if (typeof window.ReportsModule !== 'undefined' && 
                        typeof window.ReportsModule.initialize === 'function') {
                        window.ReportsModule.initialize();
                    }
                    return;
                }

                window.AdminTabManager.markLoading('reports');
                
                try {
                    // Initialize reports using ReportsModule - this will fetch and display data
                    if (typeof window.ReportsModule !== 'undefined' && 
                        typeof window.ReportsModule.initialize === 'function') {
                        window.ReportsModule.initialize();
                        // Wait a bit for data to load
                        await new Promise(resolve => setTimeout(resolve, 500));
                    } else {
                        // Fallback: try to call ensureInitialized directly if available
                        const reportsPanel = document.getElementById('reports');
                        if (reportsPanel) {
                            console.warn('[Tab] ReportsModule not available, reports may not load properly');
                        }
                    }
                    
                    // Initialize Reports charts (revenue and refund charts)
                    if (typeof window.ReportsChartsModule !== 'undefined' && 
                        typeof window.ReportsChartsModule.initialize === 'function') {
                        // Small delay to ensure DOM is ready
                        setTimeout(() => {
                            window.ReportsChartsModule.initialize();
                        }, 200);
                    }
                    
                    window.AdminTabManager.markLoaded('reports');
                } catch (error) {
                    console.error('[Tab] Error loading Reports:', error);
                    window.AdminTabManager.markError('reports', error.message);
                    throw error;
                }
            },
            refreshInterval: 5 * 60 * 1000 // 5 minutes
        },

        'finance': {
            name: 'Finance',
            loadFn: async function(forceRefresh = false) {
                console.log('[Tab] Loading Finance tab', { forceRefresh });

                const tab = window.AdminTabManager.getTab('finance');
                if (!forceRefresh && tab && tab.loaded && !window.AdminTabManager.needsRefresh('finance', 3 * 60 * 1000)) {
                    console.log('[Tab] Finance already loaded, skipping API call');
                    // Ensure UI is initialized even when cached
                    if (window.FinanceModule && typeof window.FinanceModule.ensureInitialized === 'function') {
                        window.FinanceModule.ensureInitialized();
                    } else if (typeof window.initializeFinanceTab === 'function') {
                        window.initializeFinanceTab();
                    }
                    return;
                }

                window.AdminTabManager.markLoading('finance');

                try {
                    if (window.FinanceModule && typeof window.FinanceModule.ensureInitialized === 'function') {
                        window.FinanceModule.ensureInitialized();
                    } else if (typeof window.initializeFinanceTab === 'function') {
                        window.initializeFinanceTab();
                    } else {
                        console.warn('[Tab] Finance initialization functions not found');
                    }

                    window.AdminTabManager.markLoaded('finance');
                } catch (error) {
                    console.error('[Tab] Error loading Finance:', error);
                    window.AdminTabManager.markError('finance', error.message);
                    throw error;
                }
            },
            refreshInterval: 3 * 60 * 1000 // 3 minutes
        },

        'payments': {
            name: 'Payments',
            loadFn: async function(forceRefresh = false) {
                console.log('[Tab] Loading Payments tab', { forceRefresh });
                
                const tab = window.AdminTabManager.getTab('payments');
                if (!forceRefresh && tab && tab.loaded && !window.AdminTabManager.needsRefresh('payments', 3 * 60 * 1000)) {
                    console.log('[Tab] Payments already loaded, skipping API call');
                    return;
                }

                window.AdminTabManager.markLoading('payments');
                
                try {
                    if (typeof window.CachedAPI !== 'undefined') {
                        const data = await window.CachedAPI.getPayments(1, 20, {}, forceRefresh);
                        window.AdminTabManager.markLoaded('payments', data);
                        
                        // Render payments if function exists
                        if (typeof window.loadPayments === 'function') {
                            window.loadPayments(1, 20);
                        }
                    }
                } catch (error) {
                    console.error('[Tab] Error loading Payments:', error);
                    window.AdminTabManager.markError('payments', error.message);
                    throw error;
                }
            },
            refreshInterval: 3 * 60 * 1000 // 3 minutes
        },

        'refunds': {
            name: 'Refunds',
            loadFn: async function(forceRefresh = false) {
                console.log('[Tab] Loading Refunds tab', { forceRefresh });
                
                const tab = window.AdminTabManager.getTab('refunds');
                if (!forceRefresh && tab && tab.loaded && !window.AdminTabManager.needsRefresh('refunds', 3 * 60 * 1000)) {
                    console.log('[Tab] Refunds already loaded, skipping API call');
                    return;
                }

                window.AdminTabManager.markLoading('refunds');
                
                try {
                    if (typeof window.CachedAPI !== 'undefined') {
                        // Load both refunds and metrics in parallel
                        const [refundsData, metricsData] = await Promise.all([
                            window.CachedAPI.getRefunds(1, 20, {}, forceRefresh),
                            window.CachedAPI.getRefundMetrics(forceRefresh)
                        ]);
                        
                        window.AdminTabManager.markLoaded('refunds', { refunds: refundsData, metrics: metricsData });
                        
                        // Render refunds if function exists
                        if (typeof window.loadRefunds === 'function') {
                            window.loadRefunds(1, 20);
                        }
                        if (typeof window.loadRefundMetrics === 'function') {
                            window.loadRefundMetrics();
                        }
                    }
                } catch (error) {
                    console.error('[Tab] Error loading Refunds:', error);
                    window.AdminTabManager.markError('refunds', error.message);
                    throw error;
                }
            },
            refreshInterval: 3 * 60 * 1000 // 3 minutes
        },

        'service-catalog': {
            name: 'Service Catalog',
            loadFn: async function(forceRefresh = false) {
                console.log('[Tab] Loading Service Catalog tab', { forceRefresh });

                const tab = window.AdminTabManager.getTab('service-catalog');
                if (!forceRefresh && tab && tab.loaded && !window.AdminTabManager.needsRefresh('service-catalog', 5 * 60 * 1000)) {
                    console.log('[Tab] Service Catalog already loaded, skipping API call');
                    return;
                }

                window.AdminTabManager.markLoading('service-catalog');

                try {
                    if (typeof window.initializeServiceCatalog === 'function') {
                        await window.initializeServiceCatalog({ forceRefresh: true });
                    } else if (window.ServiceCatalogTab && typeof window.ServiceCatalogTab.initialize === 'function') {
                        await window.ServiceCatalogTab.initialize({ forceRefresh: true });
                    } else {
                        console.warn('[Tab] Service Catalog module not available');
                    }

                    window.AdminTabManager.markLoaded('service-catalog');
                } catch (error) {
                    console.error('[Tab] Error loading Service Catalog:', error);
                    window.AdminTabManager.markError('service-catalog', error.message);
                    throw error;
                }
            },
            refreshInterval: 5 * 60 * 1000 // 5 minutes
        },

        'tools': {
            name: 'Tools',
            loadFn: async function() {
                console.log('[Tab] Loading Tools tab');
                
                // Tools tab doesn't need data loading, but ensure buttons are bound and UI is visible
                // The buttons are already bound in initializeAdmin(), but we can verify
                try {
                    // Ensure admin is initialized (this binds tool buttons)
                    if (typeof window.initializeAdmin === 'function') {
                        // Only initialize if not already done
                        if (!window.__adminInitialized) {
                            window.initializeAdmin();
                            window.__adminInitialized = true;
                        }
                    }
                    
                    // Ensure tools panel is visible and has proper styling
                    const toolsPanel = document.getElementById('tools');
                    if (toolsPanel) {
                        toolsPanel.style.display = 'block';
                        toolsPanel.classList.add('active');
                        
                    // Ensure tools grid is visible
                    const toolsGrid = toolsPanel.querySelector('.data-grid');
                    if (toolsGrid) {
                        toolsGrid.style.display = 'grid';
                        toolsGrid.style.visibility = 'visible';
                    }
                    } else {
                        console.warn('[Tab] Tools panel not found in DOM');
                    }
                } catch (error) {
                    console.warn('[Tab] Error ensuring tools initialization:', error);
                }
                
                window.AdminTabManager.markLoaded('tools');
            },
            refreshInterval: null // No auto-refresh
        }
    };

    /**
     * Initialize tab manager
     */
    function initializeTabManager() {
        if (window.AdminTabManager.initialized) {
            console.log('[Tab Manager] Already initialized');
            return;
        }

        console.log('[Tab Manager] Initializing...');

        // Register all tabs
        for (const [tabId, config] of Object.entries(TAB_CONFIGS)) {
            window.AdminTabManager.registerTab(tabId, config);
        }

        // Set up tab click handlers
        setupTabHandlers();

        // Detect initial active tab
        detectInitialTab();

        window.AdminTabManager.initialized = true;
        console.log('[Tab Manager] Initialized');
    }

    /**
     * Setup tab click handlers
     */
    function setupTabHandlers() {
        const tabButtons = document.querySelectorAll('[data-tab]');
        
        tabButtons.forEach(button => {
            // Remove existing listeners to avoid duplicates
            const newButton = button.cloneNode(true);
            button.parentNode.replaceChild(newButton, button);
            
            newButton.addEventListener('click', async function(e) {
                e.preventDefault();
                e.stopPropagation();
                const tabId = this.getAttribute('data-tab');
                console.log(`[Tab Manager] Tab clicked: ${tabId}`);
                await activateTab(tabId);
            });
        });

        console.log(`[Tab Manager] Set up ${tabButtons.length} tab handlers`);
    }

    /**
     * Detect and load initial active tab
     */
    function detectInitialTab() {
        // Find active tab button
        const activeButton = document.querySelector('[data-tab].active');
        if (activeButton) {
            const tabId = activeButton.getAttribute('data-tab');
            console.log(`[Tab Manager] Initial active tab: ${tabId}`);
            window.AdminTabManager.setActive(tabId);
            
            // Update visibility first
            updateTabVisibility(tabId);
            
            // Load initial tab data
            activateTab(tabId, false);
        } else {
            // No active tab found, default to first tab or 'iam'
            const firstTab = document.querySelector('[data-tab]');
            if (firstTab) {
                const tabId = firstTab.getAttribute('data-tab');
                console.log(`[Tab Manager] No active tab found, defaulting to: ${tabId}`);
                updateTabVisibility(tabId);
                activateTab(tabId, false);
            }
        }
    }

    /**
     * Activate a tab and load its data
     */
    async function activateTab(tabId, forceRefresh = false) {
        console.log(`[Tab Manager] Activating tab: ${tabId}`, { forceRefresh });
        
        const config = TAB_CONFIGS[tabId];
        if (!config) {
            console.warn(`[Tab Manager] Unknown tab: ${tabId}`);
            return;
        }

        // Update active tab
        window.AdminTabManager.setActive(tabId);

        // Show/hide tab panels and update tab buttons
        updateTabVisibility(tabId);

        // Show loading indicator
        showTabLoading(tabId);

        try {
            // Load tab data
            if (config.loadFn) {
                await config.loadFn(forceRefresh);
            }

            // Hide loading indicator
            hideTabLoading(tabId);
        } catch (error) {
            console.error(`[Tab Manager] Error activating tab ${tabId}:`, error);
            hideTabLoading(tabId);
            
            // Show error message
            if (typeof window.showMessage === 'function') {
                window.showMessage('error', `Failed to load ${config.name} data: ${error.message}`);
            }
        }
    }

    /**
     * Update tab visibility - show active tab panel and hide others
     */
    function updateTabVisibility(activeTabId) {
        try {
            // Update tab buttons
            const tabs = document.querySelectorAll('.tab[data-tab]');
            tabs.forEach(tab => {
                const isActive = tab.getAttribute('data-tab') === activeTabId;
                if (isActive) {
                    tab.classList.add('active');
                } else {
                    tab.classList.remove('active');
                }
            });

            // Update tab panels - use both class and ensure visibility
            const panels = document.querySelectorAll('.tab-panel');
            let activePanelFound = false;
            
            panels.forEach(panel => {
                const panelId = panel.id;
                const isActive = panelId === activeTabId;
                
                if (isActive) {
                    panel.classList.add('active');
                    panel.style.display = 'block';
                    panel.style.visibility = 'visible';
                    panel.style.opacity = '1';
                    activePanelFound = true;
                } else {
                    panel.classList.remove('active');
                    panel.style.display = 'none';
                }
            });

            // Special handling for tools panel to ensure content is visible
            if (activeTabId === 'tools') {
                const toolsPanel = document.getElementById('tools');
                if (toolsPanel) {
                    toolsPanel.style.display = 'block';
                    toolsPanel.classList.add('active');
                    
                    // Ensure all tool items are visible
                    const toolList = toolsPanel.querySelector('.tool-list');
                    if (toolList) {
                        toolList.style.display = 'grid';
                        toolList.style.visibility = 'visible';
                    }
                    
                    const toolItems = toolsPanel.querySelectorAll('.tool-item');
                    toolItems.forEach(item => {
                        item.style.display = 'flex';
                        item.style.visibility = 'visible';
                    });
                    
                    const dashboardCard = toolsPanel.querySelector('.dashboard-card');
                    if (dashboardCard) {
                        dashboardCard.style.display = 'block';
                        dashboardCard.style.visibility = 'visible';
                    }
                }
            }

            console.log(`[Tab Manager] Updated visibility for tab: ${activeTabId}`, { activePanelFound });
        } catch (error) {
            console.warn(`[Tab Manager] Error updating tab visibility:`, error);
        }
    }

    /**
     * Show loading indicator for tab
     */
    function showTabLoading(tabId) {
        const loader = document.getElementById(`${tabId}-loader`);
        if (loader) {
            loader.style.display = 'block';
        }
    }

    /**
     * Hide loading indicator for tab
     */
    function hideTabLoading(tabId) {
        const loader = document.getElementById(`${tabId}-loader`);
        if (loader) {
            loader.style.display = 'none';
        }
    }

    /**
     * Refresh current active tab
     */
    async function refreshActiveTab() {
        const activeTab = window.AdminTabManager.getActive();
        if (activeTab) {
            console.log(`[Tab Manager] Refreshing active tab: ${activeTab}`);
            await activateTab(activeTab, true);
        }
    }

    /**
     * Invalidate and refresh a specific tab
     */
    function invalidateTab(tabId) {
        console.log(`[Tab Manager] Invalidating tab: ${tabId}`);
        window.AdminTabManager.invalidate(tabId);
        
        // If it's the active tab, refresh it
        if (window.AdminTabManager.getActive() === tabId) {
            activateTab(tabId, true);
        }
    }

    /**
     * Get tab manager stats
     */
    function getTabStats() {
        return window.AdminTabManager.getAllStates();
    }

    // Export functions to global scope
    window.initializeTabManager = initializeTabManager;
    window.activateTab = activateTab;
    window.refreshActiveTab = refreshActiveTab;
    window.invalidateTab = invalidateTab;
    window.getTabStats = getTabStats;

    // Auto-initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeTabManager);
    } else {
        // DOM already loaded
        setTimeout(initializeTabManager, 100);
    }

    console.log('[Admin Tab Manager] Module loaded');
})();
