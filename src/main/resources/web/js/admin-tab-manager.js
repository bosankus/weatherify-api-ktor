/**
 * Admin Dashboard Tab Manager
 * Optimizes tab switching by tracking loaded state and avoiding redundant API calls
 * Implements lazy loading and intelligent data refresh strategies
 */

(function () {
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
            loadFn: async function (forceRefresh = false) {
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
            loadFn: async function (forceRefresh = false) {
                console.log('[Tab] Loading Reports tab', { forceRefresh });

                const tab = window.AdminTabManager.getTab('reports');
                if (!forceRefresh && tab && tab.loaded && !window.AdminTabManager.needsRefresh('reports')) {
                    console.log('[Tab] Reports already loaded, skipping API call');
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
                        window.ReportsChartsModule.initialize();
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
            loadFn: async function (forceRefresh = false) {
                console.log('[Tab] Loading Finance tab', { forceRefresh });

                const tab = window.AdminTabManager.getTab('finance');
                if (!forceRefresh && tab && tab.loaded &&
                    !window.AdminTabManager.needsRefresh('finance', 3 * 60 * 1000)) {
                    console.log('[Tab] Finance already loaded, skipping');
                    return;
                }

                window.AdminTabManager.markLoading('finance');

                try {
                    if (window.FinancialDashboard &&
                        typeof window.FinancialDashboard.initialize === 'function') {
                        await window.FinancialDashboard.initialize();
                    } else {
                        console.error('[Tab] FinancialDashboard module not found');
                        throw new Error('FinancialDashboard module not loaded');
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
            loadFn: async function (forceRefresh = false) {
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
            loadFn: async function (forceRefresh = false) {
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
            loadFn: async function (forceRefresh = false) {
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

        'notes': {
            name: 'Notes',
            loadFn: async function (forceRefresh = false) {
                console.log('[Tab] Loading Notes tab', { forceRefresh });

                const tab = window.AdminTabManager.getTab('notes');
                if (!forceRefresh && tab && tab.loaded && !window.AdminTabManager.needsRefresh('notes', 5 * 60 * 1000)) {
                    console.log('[Tab] Notes already loaded, skipping API call');
                    return;
                }

                window.AdminTabManager.markLoading('notes');

                try {
                    // Wait for TipTap to be loaded
                    if (!window._tiptapReady) {
                        console.warn('[Tab] TipTap not ready, waiting...');
                        await new Promise(resolve => setTimeout(resolve, 500));
                    }
                    if (window._tiptapReady) {
                        await window._tiptapReady;
                    }

                    if (window.NotesTab && typeof window.NotesTab.initialize === 'function') {
                        await window.NotesTab.initialize({ forceRefresh: true });
                    } else {
                        console.warn('[Tab] Notes module not available');
                    }

                    window.AdminTabManager.markLoaded('notes');
                } catch (error) {
                    console.error('[Tab] Error loading Notes:', error);
                    window.AdminTabManager.markError('notes', error.message);
                    throw error;
                }
            },
            refreshInterval: 5 * 60 * 1000 // 5 minutes
        },

        'tools': {
            name: 'Tools',
            loadFn: async function () {
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
        },

        'support': {
            name: 'Support',
            loadFn: async function (forceRefresh = false) {
                console.log('[Tab] Loading Support tab', { forceRefresh });

                const tab = window.AdminTabManager.getTab('support');
                if (!forceRefresh && tab && tab.loaded && !window.AdminTabManager.needsRefresh('support', 2 * 60 * 1000)) {
                    console.log('[Tab] Support already loaded, skipping API call');
                    return;
                }

                window.AdminTabManager.markLoading('support');

                try {
                    if (typeof window.loadSupportTickets === 'function') {
                        await window.loadSupportTickets(forceRefresh);
                    }
                    window.AdminTabManager.markLoaded('support');
                } catch (error) {
                    console.error('[Tab] Error loading Support:', error);
                    window.AdminTabManager.markError('support', error.message);
                    throw error;
                }
            },
            refreshInterval: 2 * 60 * 1000 // 2 minutes
        }
    };

    // ── Support Tickets Module ────────────────────────────────────────────────

    let _supportTickets = [];

    function getCookieToken() {
        const m = document.cookie.match(/(?:^|;\s*)jwt_token=([^;]*)/);
        return m ? decodeURIComponent(m[1]) : null;
    }

    function adminHeaders() {
        const token = getCookieToken() || localStorage.getItem('jwt_token');
        return token
            ? { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' }
            : { 'Content-Type': 'application/json' };
    }

    function escHtml(s) {
        const d = document.createElement('div');
        d.textContent = String(s == null ? '' : s);
        return d.innerHTML;
    }

    function fmtDate(ms) {
        return new Date(ms).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    }

    function ticketAge(ms) {
        const diff = Date.now() - ms;
        const h = Math.floor(diff / 3600000);
        if (h < 1) return { label: 'just now', color: '#10b981' };
        if (h < 24) return { label: h + 'h ago', color: '#10b981' };
        const d = Math.floor(h / 24);
        if (d < 2) return { label: d + 'd ago', color: '#f59e0b' };
        if (d < 7) return { label: d + 'd ago', color: '#f97316' };
        return { label: d + 'd ago', color: '#ef4444' };
    }

    const CAT_COLORS = {
        bug: 'background:rgba(239,68,68,.15);color:#ef4444;border:1px solid rgba(239,68,68,.3)',
        question: 'background:rgba(16,185,129,.15);color:#10b981;border:1px solid rgba(16,185,129,.3)',
        feature: 'background:rgba(139,92,246,.15);color:#8b5cf6;border:1px solid rgba(139,92,246,.3)',
        billing: 'background:rgba(245,158,11,.15);color:#f59e0b;border:1px solid rgba(245,158,11,.3)',
    };
    const ST_COLORS = {
        open: 'background:rgba(245,158,11,.15);color:#f59e0b;border:1px solid rgba(245,158,11,.3)',
        acknowledged: 'background:rgba(99,102,241,.15);color:#6366f1;border:1px solid rgba(99,102,241,.3)',
        resolved: 'background:rgba(107,114,128,.15);color:#6b7280;border:1px solid rgba(107,114,128,.3)',
    };

    // Sort: open first, then acknowledged, then resolved; within each group newest first
    function sortTickets(tickets) {
        const order = { open: 0, acknowledged: 1, resolved: 2 };
        return [...tickets].sort((a, b) => {
            const so = (order[a.status] ?? 3) - (order[b.status] ?? 3);
            return so !== 0 ? so : b.createdAt - a.createdAt;
        });
    }

    function renderSupportTable(tickets) {
        const tbody = document.getElementById('support-tickets-body');
        if (!tbody) return;
        const sorted = sortTickets(tickets || []);
        if (!sorted.length) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:2.5rem;color:var(--text-secondary);">No tickets found.</td></tr>';
            return;
        }
        tbody.innerHTML = sorted.map(t => {
            const catStyle = CAT_COLORS[t.category] || '';
            const stStyle = ST_COLORS[t.status] || '';
            const age = ticketAge(t.createdAt);
            const hasNote = t.adminNote && t.adminNote.trim().length > 0;
            return `<tr style="cursor:pointer;" onclick="window.openSupportDetail('${escHtml(t.id)}')">
                <td><code style="font-size:0.78rem">${escHtml(t.id.substring(0, 8))}</code></td>
                <td style="font-size:0.82rem;max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="${escHtml(t.userEmail || t.userId)}">${escHtml(t.userEmail || t.userId.substring(0, 14))}</td>
                <td><span class="status-badge" style="${catStyle}">${escHtml(t.category)}</span></td>
                <td style="max-width:240px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="${escHtml(t.subject)}">${escHtml(t.subject)}${hasNote ? ' <span title="Has admin note" style="font-size:10px;opacity:.65;">📝</span>' : ''}</td>
                <td><span class="status-badge" style="${stStyle}">${escHtml(t.status)}</span></td>
                <td style="font-size:0.8rem;color:${age.color};font-weight:500;">${age.label}</td>
            </tr>`;
        }).join('');
    }

    function updateOpenBadge(tickets) {
        const badge = document.getElementById('support-open-badge');
        if (!badge) return;
        const openCount = (tickets || []).filter(t => t.status === 'open').length;
        if (openCount > 0) {
            badge.textContent = String(openCount);
            badge.style.display = 'inline';
        } else {
            badge.style.display = 'none';
        }
    }

    window.loadSupportTickets = async function (forceRefresh = false) {
        const statusFilter = document.getElementById('support-status-filter');
        const status = statusFilter ? statusFilter.value : '';
        const url = '/admin/support/tickets' + (status ? '?status=' + encodeURIComponent(status) : '');
        const loader = document.getElementById('support-loader');
        if (loader) loader.style.display = 'block';
        try {
            const res = await fetch(url, { headers: adminHeaders() });
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            _supportTickets = data.data && data.data.tickets ? data.data.tickets : [];
            renderSupportTable(_supportTickets);
            updateOpenBadge(_supportTickets);
            const label = document.getElementById('support-count-label');
            if (label) label.textContent = _supportTickets.length + ' ticket' + (_supportTickets.length !== 1 ? 's' : '');
        } catch (e) {
            console.error('[Support] Failed to load tickets:', e);
            const tbody = document.getElementById('support-tickets-body');
            if (tbody) tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:2rem;color:#ef4444;">Failed to load tickets: ' + escHtml(e.message) + '</td></tr>';
        } finally {
            if (loader) loader.style.display = 'none';
        }
    };

    let _currentChatTicketId = null;

    function _renderChatStatusPills(t) {
        const container = document.getElementById('support-chat-status-pills');
        if (!container || !t) return;
        container.innerHTML = ['open', 'acknowledged', 'resolved'].map(s => {
            const active = t.status === s;
            return `<button class="support-chat-pill" onclick="window.updateTicketStatus('${escHtml(t.id)}','${s}')" style="border:1px solid ${active ? 'transparent' : 'var(--card-border)'};background:${active ? '#6366f1' : 'transparent'};color:${active ? '#fff' : 'var(--text-color)'};font-weight:${active ? '600' : '400'};">${s.charAt(0).toUpperCase() + s.slice(1)}</button>`;
        }).join('');
    }

    function ago(ms){
        if(!ms) return '';
        const diff=Date.now()-ms, m=Math.floor(diff/60000), h=Math.floor(m/60), dy=Math.floor(h/24);
        if(m<1)return 'just now';if(m<60)return m+'m ago';if(h<24)return h+'h ago';
        if(dy<7)return dy+'d ago';
        return new Date(ms).toLocaleDateString(undefined,{month:'short',day:'numeric'});
    }

    function _renderChatMessages(t) {
        const area = document.getElementById('support-chat-messages');
        if (!area) return;
        
        let msgs = t.messages || [];
        if (!msgs.length) {
            // fallback
            if (t.message) {
                msgs.push({ senderType: 'user', content: t.message, sentAt: t.createdAt });
            }
            if (t.adminReply) {
                msgs.push({ senderType: 'admin', content: t.adminReply, sentAt: t.createdAt });
            }
        }
        
        if (!msgs.length) {
            area.innerHTML = '<div style="flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;color:var(--text-secondary);font-size:0.84rem;opacity:.6;"><span class="material-icons" style="font-size:32px;">forum</span>No messages yet</div>';
            return;
        }
        
        area.innerHTML = msgs.map(m => {
            const role = m.senderType === 'admin' ? 'admin' : 'user';
            const label = role === 'admin' ? 'Support (You)' : (t.userEmail || t.userId);
            const timeStr = m.sentAt ? ago(m.sentAt) : '';
            return `
            <div class="chat-msg-row ${role}">
                <div class="chat-bubble ${role}">${escHtml(m.content)}</div>
                <div class="chat-meta ${role}">
                    <div style="font-weight:700;">${escHtml(label)}</div>
                    <div>${escHtml(timeStr)}</div>
                </div>
            </div>`;
        }).join('');
        area.scrollTop = area.scrollHeight;
    }

    window.updateTicketStatus = async function (id, newStatus) {
        const t = _supportTickets.find(x => x.id === id);
        const prev = t ? t.status : null;
        if (t) t.status = newStatus;
        renderSupportTable(_supportTickets);
        updateOpenBadge(_supportTickets);
        const modal = document.getElementById('support-chat-modal');
        if (modal && modal.style.display !== 'none' && modal.dataset.ticketId === id) {
            _renderChatStatusPills(t);
            const badges = document.getElementById('support-chat-badges');
            if (badges && t) {
                const stStyle = ST_COLORS[t.status] || '';
                badges.innerHTML = `<span class="status-badge" style="${stStyle}">${escHtml(t.status)}</span>`;
            }
        }
        try {
            const res = await fetch('/admin/support/tickets/' + encodeURIComponent(id) + '/status', {
                method: 'PATCH',
                headers: adminHeaders(),
                body: JSON.stringify({ status: newStatus }),
            });
            if (!res.ok) throw new Error('HTTP ' + res.status);
        } catch (e) {
            if (t && prev) t.status = prev;
            renderSupportTable(_supportTickets);
            updateOpenBadge(_supportTickets);
            if (modal && modal.style.display !== 'none' && modal.dataset.ticketId === id) _renderChatStatusPills(t);
            if (typeof showBanner === 'function') showBanner('error', 'Failed to update status: ' + e.message);
            else console.error('Failed to update status:', e.message);
        }
    };

    window.sendSupportChatReply = async function () {
        const id = _currentChatTicketId;
        if (!id) return;
        const ta = document.getElementById('support-chat-reply-ta');
        if (!ta) return;
        const reply = ta.value.trim();
        if (!reply) return;
        const btn = document.getElementById('support-chat-send-btn');
        if (btn) { btn.disabled = true; btn.innerHTML = '<span class="material-icons" style="font-size:14px;">hourglass_top</span>Sending…'; }
        try {
            const res = await fetch('/admin/support/tickets/' + encodeURIComponent(id) + '/reply', {
                method: 'PATCH',
                headers: adminHeaders(),
                body: JSON.stringify({ reply }),
            });
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            const newMsg = data.data; // The returned SupportMessage object
            
            const t = _supportTickets.find(x => x.id === id);
            if (t) {
                if (!t.messages) t.messages = [];
                t.messages.push(newMsg);
                t.adminReply = reply;
                if (t.status === 'open') t.status = 'acknowledged';
            }
            ta.value = '';
            _renderChatMessages(t);
            renderSupportTable(_supportTickets);
            if (btn) { btn.disabled = false; btn.innerHTML = '<span class="material-icons" style="font-size:14px;">send</span>Send'; }
        } catch (e) {
            if (btn) { btn.disabled = false; btn.innerHTML = '<span class="material-icons" style="font-size:14px;">send</span>Send'; }
            if (typeof showBanner === 'function') showBanner('error', 'Failed to send reply: ' + e.message);
        }
    };

    window.saveSupportChatNote = async function () {
        const id = _currentChatTicketId;
        if (!id) return;
        const ta = document.getElementById('support-chat-note-ta');
        if (!ta) return;
        const note = ta.value.trim();
        const btn = document.getElementById('support-chat-note-save-btn');
        if (btn) { btn.disabled = true; btn.textContent = 'Saving…'; }
        try {
            const res = await fetch('/admin/support/tickets/' + encodeURIComponent(id) + '/note', {
                method: 'PATCH',
                headers: adminHeaders(),
                body: JSON.stringify({ note }),
            });
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const t = _supportTickets.find(x => x.id === id);
            if (t) t.adminNote = note || null;
            renderSupportTable(_supportTickets);
            if (btn) { btn.disabled = false; btn.textContent = 'Saved ✓'; setTimeout(() => { if (btn) btn.textContent = 'Save note'; }, 1800); }
        } catch (e) {
            if (btn) { btn.disabled = false; btn.textContent = 'Save note'; }
            if (typeof showBanner === 'function') showBanner('error', 'Failed to save note: ' + e.message);
        }
    };

    window.toggleSupportChatNote = function () {
        const body = document.getElementById('support-chat-note-body');
        const chevron = document.getElementById('support-chat-note-chevron');
        if (!body) return;
        const open = body.classList.toggle('open');
        if (chevron) chevron.style.transform = open ? 'rotate(180deg)' : '';
    };

    window.resolveSupportChat = async function () {
        const id = _currentChatTicketId;
        if (!id) return;
        await window.updateTicketStatus(id, 'resolved');
        window.closeSupportDetail();
    };

    window.openSupportDetail = async function (id) {
        const t = _supportTickets.find(x => x.id === id);
        if (!t) return;
        _currentChatTicketId = id;
        const modal = document.getElementById('support-chat-modal');
        const backdrop = document.getElementById('support-chat-backdrop');
        if (!modal) return;
        modal.dataset.ticketId = id;

        // Header
        const titleEl = document.getElementById('support-chat-title');
        if (titleEl) titleEl.textContent = 'Ticket #' + t.id.substring(0, 8);
        const subjectEl = document.getElementById('support-chat-subject');
        if (subjectEl) subjectEl.textContent = (t.userEmail || t.userId) + ' · ' + t.subject;
        const badges = document.getElementById('support-chat-badges');
        if (badges) {
            const catStyle = CAT_COLORS[t.category] || '';
            const stStyle = ST_COLORS[t.status] || '';
            badges.innerHTML = `<span class="status-badge" style="${catStyle}">${escHtml(t.category)}</span><span class="status-badge" style="${stStyle}">${escHtml(t.status)}</span>`;
        }
        
        const area = document.getElementById('support-chat-messages');
        if (area) area.innerHTML = '<div style="flex:1;display:flex;align-items:center;justify-content:center;color:var(--text-secondary);">Loading thread...</div>';
        
        if (backdrop) backdrop.style.display = 'block';
        modal.style.display = 'flex';
        requestAnimationFrame(() => {
            modal.classList.add('open');
            if (backdrop) backdrop.classList.add('open');
        });
        
        try {
            const res = await fetch('/admin/support/tickets/' + encodeURIComponent(id), { headers: adminHeaders() });
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            const detail = data.data;
            t.messages = detail.messages;
        } catch (e) {
            console.error('Failed to load thread:', e);
            if (area) area.innerHTML = '<div style="color:#ef4444;text-align:center;">Failed to load thread.</div>';
            return;
        }

        // Messages
        _renderChatMessages(t);

        // Status pills
        _renderChatStatusPills(t);

        // Note
        const noteTa = document.getElementById('support-chat-note-ta');
        if (noteTa) noteTa.value = t.adminNote || '';
        const noteBody = document.getElementById('support-chat-note-body');
        if (noteBody) noteBody.classList.remove('open');
        const chevron = document.getElementById('support-chat-note-chevron');
        if (chevron) chevron.style.transform = '';

        // Reply
        const replyTa = document.getElementById('support-chat-reply-ta');
        if (replyTa) replyTa.value = '';
    };

    window.closeSupportDetail = function () {
        const modal = document.getElementById('support-chat-modal');
        const backdrop = document.getElementById('support-chat-backdrop');
        if (modal) {
            modal.classList.remove('open');
            setTimeout(() => { modal.style.display = 'none'; }, 260);
        }
        if (backdrop) {
            backdrop.classList.remove('open');
            setTimeout(() => { backdrop.style.display = 'none'; }, 260);
        }
        _currentChatTicketId = null;
    };

    // Escape key closes the chat modal
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            const modal = document.getElementById('support-chat-modal');
            if (modal && modal.style.display !== 'none') window.closeSupportDetail();
        }
    });

    // Wire status filter dropdown + refresh button once DOM is ready
    function wireSupportControls() {
        const filter = document.getElementById('support-status-filter');
        if (filter) filter.addEventListener('change', () => window.loadSupportTickets(true));
        const refresh = document.getElementById('support-refresh-btn');
        if (refresh) refresh.addEventListener('click', () => window.loadSupportTickets(true));
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', wireSupportControls);
    } else {
        wireSupportControls();
    }

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

        // Save scroll position before the page unloads so we can restore it after reload
        window.addEventListener('beforeunload', function() {
            try { sessionStorage.setItem('adminScrollY', String(window.scrollY)); } catch (_) {}
        });

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

            newButton.addEventListener('click', async function (e) {
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
     * Detect and load initial active tab.
     * Priority: sessionStorage saved tab → DOM .active class → first tab in DOM.
     */
    function detectInitialTab() {
        let tabId = null;

        // 1. Restore from sessionStorage (survives F5 / Cmd+R reload)
        try {
            const saved = sessionStorage.getItem('adminActiveTab');
            if (saved && TAB_CONFIGS[saved]) {
                tabId = saved;
                console.log(`[Tab Manager] Restoring tab from session: ${tabId}`);
            }
        } catch (_) {}

        // 2. Fall back to whichever tab the server rendered as active
        if (!tabId) {
            const activeButton = document.querySelector('[data-tab].active');
            if (activeButton) {
                tabId = activeButton.getAttribute('data-tab');
                console.log(`[Tab Manager] Initial active tab from DOM: ${tabId}`);
            }
        }

        // 3. Last resort: first tab in DOM
        if (!tabId) {
            const firstTab = document.querySelector('[data-tab]');
            if (firstTab) {
                tabId = firstTab.getAttribute('data-tab');
                console.log(`[Tab Manager] No saved/active tab found, defaulting to: ${tabId}`);
            }
        }

        if (tabId) {
            // Signal that scroll should be restored once this tab's data finishes loading
            _restoreScrollAfterLoad = true;
            updateTabVisibility(tabId);
            activateTab(tabId, false);
        }
    }

    /**
     * Activate a tab and load its data
     */
    async function activateTab(tabId, forceRefresh = false) {
        console.log(`[Tab Manager] Activating tab: ${tabId}`, { forceRefresh });
        console.log(`[Tab Manager] Available tabs:`, Object.keys(TAB_CONFIGS));
        console.log(`[Tab Manager] Config for ${tabId}:`, TAB_CONFIGS[tabId]);

        const config = TAB_CONFIGS[tabId];
        if (!config) {
            console.warn(`[Tab Manager] Unknown tab: ${tabId}`);
            return;
        }

        // Update active tab
        window.AdminTabManager.setActive(tabId);

        // Persist active tab so it survives a page reload
        try { sessionStorage.setItem('adminActiveTab', tabId); } catch (_) {}

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

            // Restore saved scroll position (only on initial page load, not on manual tab switch)
            if (_restoreScrollAfterLoad) {
                _restoreScrollAfterLoad = false;
                try {
                    const savedY = sessionStorage.getItem('adminScrollY');
                    if (savedY !== null) {
                        setTimeout(function() { window.scrollTo(0, parseInt(savedY, 10) || 0); }, 50);
                    }
                } catch (_) {}
            }
        } catch (error) {
            console.error(`[Tab Manager] Error activating tab ${tabId}:`, error);
            hideTabLoading(tabId);

            // Show error message
            if (typeof window.showMessage === 'function') {
                window.showMessage('error', `Failed to load ${config.name} data: ${error.message}`);
            }
        }
    }

    // Flag: restore scroll position after the initial page-load tab activates
    let _restoreScrollAfterLoad = false;

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
