/**
 * Admin Users Tab - Optimized UI/UX Module
 * Handles user management with improved performance and better user experience
 */

(function() {
    'use strict';

    // ============= State Management =============
    const UsersState = {
        currentPage: 1,
        pageSize: 10,
        totalPages: 1,
        totalUsers: 0,
        users: [],
        filters: {
            search: '',
            role: 'all',
            status: 'all',
            premium: 'all'
        },
        sortBy: 'createdAt',
        sortOrder: 'desc',
        isLoading: false,
        selectedUsers: new Set()
    };

    const SELECTION_ENABLED = false;

    // ============= Performance Optimizations =============

    /**
     * Debounce function for search input
     */
    function debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }

    /**
     * Virtual scrolling implementation for large datasets
     * Only renders visible rows to improve performance with large user lists
     * Automatically enables when user count exceeds threshold (50 users)
     */
    const VirtualScroll = {
        enabled: false, // Enable when user count exceeds threshold
        rowHeight: 60, // Estimated row height in pixels (will be calculated dynamically)
        visibleRows: 20, // Number of rows to render (with buffer)
        buffer: 5, // Extra rows to render above/below viewport
        scrollContainer: null,
        tableBody: null,
        allUsers: [],
        startIndex: 0,
        endIndex: 0,
        scrollTop: 0,
        containerHeight: 0,
        threshold: 50, // Enable virtual scroll when user count exceeds this
        measuredRowHeight: null, // Dynamically measured row height

        /**
         * Initialize virtual scrolling
         * @param {HTMLElement} container - Scrollable container element
         * @param {HTMLElement} tbody - Table body element
         */
        init(container, tbody) {
            this.scrollContainer = container;
            this.tableBody = tbody;

            if (!container || !tbody) {
                console.warn('VirtualScroll: Container or tbody not found');
                return;
            }

            // Calculate container height
            this.updateContainerHeight();

            // Bind scroll event with throttling
            let scrollTimer = null;
            container.addEventListener('scroll', () => {
                if (scrollTimer) return;
                scrollTimer = setTimeout(() => {
                    this.handleScroll();
                    scrollTimer = null;
                }, 10); // Throttle to ~100fps
            });

            // Handle window resize
            window.addEventListener('resize', () => {
                this.updateContainerHeight();
                this.render();
            });
        },

        /**
         * Update container height
         */
        updateContainerHeight() {
            if (!this.scrollContainer) return;
            const rect = this.scrollContainer.getBoundingClientRect();
            this.containerHeight = rect.height || window.innerHeight * 0.6;
        },

        /**
         * Measure actual row height from first rendered row
         */
        measureRowHeight() {
            if (!this.tableBody || this.measuredRowHeight) return;
            const firstRow = this.tableBody.querySelector('tr:not([style*="height"])');
            if (firstRow) {
                const height = firstRow.getBoundingClientRect().height;
                if (height > 0) {
                    this.measuredRowHeight = height;
                    this.rowHeight = height;
                    console.log(`[VirtualScroll] Measured row height: ${height}px`);
                }
            }
        },

        /**
         * Enable virtual scrolling if user count exceeds threshold
         * @param {number} userCount - Total number of users
         */
        enableIfNeeded(userCount) {
            this.enabled = userCount > this.threshold;
            if (this.enabled) {
                console.log(`[VirtualScroll] Enabled for ${userCount} users`);
            }
        },

        /**
         * Set users data and render
         * @param {Array} users - Array of all users to render
         */
        setUsers(users) {
            this.allUsers = users || [];
            this.enableIfNeeded(this.allUsers.length);
            this.render();
        },

        /**
         * Calculate visible range based on scroll position
         */
        calculateVisibleRange() {
            if (!this.enabled || this.allUsers.length === 0) {
                return { start: 0, end: this.allUsers.length };
            }

            // Calculate which rows should be visible
            const scrollTop = this.scrollContainer ? this.scrollContainer.scrollTop : 0;
            this.scrollTop = scrollTop;

            // Start index (with buffer above)
            const start = Math.max(0, Math.floor(scrollTop / this.rowHeight) - this.buffer);

            // End index (with buffer below)
            const visibleCount = Math.ceil(this.containerHeight / this.rowHeight);
            const end = Math.min(
                this.allUsers.length,
                start + visibleCount + (this.buffer * 2)
            );

            this.startIndex = start;
            this.endIndex = end;

            return { start, end };
        },

        /**
         * Handle scroll event
         */
        handleScroll() {
            if (!this.enabled) return;
            this.render();
        },

        /**
         * Render visible rows
         */
        render() {
            if (!this.tableBody) return;

            if (!this.enabled || this.allUsers.length === 0) {
                // Fallback to normal rendering
                return;
            }

            const { start, end } = this.calculateVisibleRange();
            const visibleUsers = this.allUsers.slice(start, end);

            // Clear and render visible rows
            const fragment = document.createDocumentFragment();

            // Calculate column count (6 columns: email, created, role, status, premium, actions)
            // If selection is enabled, add 1 more column
            const colCount = SELECTION_ENABLED ? 7 : 6;

            // Add spacer row for rows above viewport
            if (start > 0) {
                const spacer = document.createElement('tr');
                spacer.style.height = `${start * this.rowHeight}px`;
                spacer.innerHTML = `<td colspan="${colCount}"></td>`;
                fragment.appendChild(spacer);
            }

            // Render visible rows
            visibleUsers.forEach(user => {
                const row = createUserRow(user);
                fragment.appendChild(row);
            });

            // Add spacer row for rows below viewport
            const remaining = this.allUsers.length - end;
            if (remaining > 0) {
                const spacer = document.createElement('tr');
                spacer.style.height = `${remaining * this.rowHeight}px`;
                spacer.innerHTML = `<td colspan="${colCount}"></td>`;
                fragment.appendChild(spacer);
            }

            this.tableBody.replaceChildren(fragment);

            // Measure row height on first render if not already measured
            if (!this.measuredRowHeight && visibleUsers.length > 0) {
                // Use requestAnimationFrame to measure after DOM update
                requestAnimationFrame(() => {
                    this.measureRowHeight();
                    // Re-render with accurate row height
                    if (this.measuredRowHeight && this.measuredRowHeight !== 60) {
                        this.render();
                    }
                });
            }

            // Update selection checkboxes if enabled
            if (SELECTION_ENABLED) {
                updateBulkSelectUI();
            }
        },

        /**
         * Scroll to specific user by index
         * @param {number} index - User index in allUsers array
         */
        scrollToIndex(index) {
            if (!this.enabled || !this.scrollContainer) return;
            this.scrollContainer.scrollTop = index * this.rowHeight;
            this.render();
        },

        /**
         * Scroll to user by email
         * @param {string} email - User email
         */
        scrollToUser(email) {
            const index = this.allUsers.findIndex(user => user.email === email);
            if (index !== -1) {
                this.scrollToIndex(index);
            }
        },

        /**
         * Reset virtual scroll state
         */
        reset() {
            this.allUsers = [];
            this.startIndex = 0;
            this.endIndex = 0;
            this.scrollTop = 0;
            if (this.scrollContainer) {
                this.scrollContainer.scrollTop = 0;
            }
        }
    };


    // ============= UI Rendering =============

    /**
     * Render enhanced users table with improved UI
     * Uses virtual scrolling for large datasets (50+ users)
     */
    function renderUsersTable(users) {
        const tableBody = document.getElementById('users-table-body');
        if (!tableBody) return;

        // Show skeleton loader during render
        if (UsersState.isLoading) {
            VirtualScroll.reset();
            const loadingRow = typeof createLoadingTableRow === 'function'
                ? createLoadingTableRow(6, 'Loading users...')
                : generateSkeletonRows(UsersState.pageSize);
            if (loadingRow instanceof HTMLTableRowElement) {
                tableBody.replaceChildren(loadingRow);
            } else {
                tableBody.innerHTML = loadingRow;
            }
            return;
        }

        // Initialize virtual scroll if not already done
        if (!VirtualScroll.scrollContainer) {
            // Find scrollable container (table wrapper or parent)
            const table = tableBody.closest('table');
            const container = table?.parentElement || tableBody.parentElement;

            // Make container scrollable if it isn't already
            if (container && container.style.overflow !== 'auto') {
                container.style.overflowY = 'auto';
                container.style.maxHeight = '70vh'; // Limit height for scrolling
            }

            VirtualScroll.init(container, tableBody);
        }

        // Handle empty state
        if (users.length === 0) {
            VirtualScroll.reset();
            const row = createEmptyRow();
            tableBody.replaceChildren(row);
            return;
        }

        // Use virtual scrolling for large datasets
        VirtualScroll.setUsers(users);

        // If virtual scroll is disabled (small dataset), render normally
        if (!VirtualScroll.enabled) {
            const fragment = document.createDocumentFragment();
            users.forEach(user => {
                const row = createUserRow(user);
                fragment.appendChild(row);
            });
            tableBody.replaceChildren(fragment);
        }

        // Update selection checkboxes if enabled
        if (SELECTION_ENABLED) {
            updateBulkSelectUI();
        }
    }

    /**
     * Create enhanced user row with better UX
     * Uses consistent table row styling
     */
    function createUserRow(user) {
        const row = document.createElement('tr');
        row.className = 'user-row';
        row.dataset.email = user.email;

        // Apply consistent row styling
        if (typeof styleTableRow === 'function') {
            styleTableRow(row);
        }

        if (SELECTION_ENABLED) {
            // Selection checkbox
            const selectCell = document.createElement('td');
            selectCell.className = 'select-cell';
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.className = 'user-select-checkbox';
            checkbox.dataset.email = user.email;
            checkbox.checked = UsersState.selectedUsers.has(user.email);
            checkbox.addEventListener('change', (e) => handleUserSelect(user.email, e.target.checked));
            selectCell.appendChild(checkbox);
            row.appendChild(selectCell);
        }

        // Email cell with avatar and badges
        const emailCell = createEmailCell(user);
        row.appendChild(emailCell);

        // Created at cell with relative time
        const createdCell = createCreatedAtCell(user.createdAt);
        row.appendChild(createdCell);

        // Role cell with enhanced dropdown
        const roleCell = createRoleCell(user);
        row.appendChild(roleCell);

        // Status toggle with better visual feedback
        const statusCell = createStatusCell(user);
        row.appendChild(statusCell);

        // Premium toggle
        const premiumCell = createPremiumCell(user);
        row.appendChild(premiumCell);

        // Actions menu - ensure consistent styling
        const actionsCell = createActionsCell(user);
        if (actionsCell && !actionsCell.style.textAlign) {
            actionsCell.style.textAlign = 'center';
        }
        row.appendChild(actionsCell);

        return row;
    }


    /**
     * Create email cell with avatar and badges
     * Uses consistent table cell styling
     */
    function createEmailCell(user) {
        const cell = typeof createTableCell === 'function'
            ? createTableCell('', { className: 'email-cell' })
            : document.createElement('td');
        cell.className = 'email-cell';

        const container = document.createElement('div');
        container.className = 'email-container';
        container.style.display = 'flex';
        container.style.alignItems = 'center';
        container.style.gap = '0.75rem';

        // Avatar with first letter
        const avatar = document.createElement('div');
        avatar.className = 'user-avatar';
        avatar.textContent = user.email.charAt(0).toUpperCase();
        avatar.style.background = generateAvatarColor(user.email);
        avatar.style.width = '32px';
        avatar.style.height = '32px';
        avatar.style.borderRadius = '50%';
        avatar.style.display = 'flex';
        avatar.style.alignItems = 'center';
        avatar.style.justifyContent = 'center';
        avatar.style.color = 'white';
        avatar.style.fontWeight = '600';
        avatar.style.flexShrink = '0';
        container.appendChild(avatar);

        // Email and badges wrapper
        const textWrapper = document.createElement('div');
        textWrapper.className = 'email-text-wrapper';
        textWrapper.style.display = 'flex';
        textWrapper.style.flexDirection = 'column';
        textWrapper.style.gap = '0.25rem';
        textWrapper.style.minWidth = '0';
        textWrapper.style.flex = '1';

        const emailText = document.createElement('span');
        emailText.className = 'email-text';
        emailText.textContent = user.email;
        emailText.title = user.email;
        emailText.style.fontSize = '0.875rem';
        emailText.style.color = 'var(--text-color)';
        emailText.style.overflow = 'hidden';
        emailText.style.textOverflow = 'ellipsis';
        emailText.style.whiteSpace = 'nowrap';
        textWrapper.appendChild(emailText);

        container.appendChild(textWrapper);
        cell.appendChild(container);

        return cell;
    }

    /**
     * Create created at cell with relative time
     * Uses consistent table cell styling
     */
    function createCreatedAtCell(createdAt) {
        const cell = typeof createTableCell === 'function'
            ? createTableCell('', { className: 'created-cell' })
            : document.createElement('td');
        cell.className = 'created-cell';

        const container = document.createElement('div');
        container.className = 'date-container';
        container.style.display = 'flex';
        container.style.flexDirection = 'column';
        container.style.gap = '0.25rem';

        const dateText = document.createElement('span');
        dateText.className = 'date-text';
        dateText.textContent = formatDate(createdAt);
        dateText.style.fontSize = '0.875rem';
        dateText.style.color = 'var(--text-color)';
        container.appendChild(dateText);

        const relativeText = document.createElement('span');
        relativeText.className = 'date-relative';
        relativeText.textContent = getRelativeTime(createdAt);
        relativeText.style.fontSize = '0.75rem';
        relativeText.style.color = 'var(--text-secondary)';
        container.appendChild(relativeText);

        cell.appendChild(container);
        return cell;
    }


    /**
     * Create role cell with enhanced dropdown
     * Uses consistent table cell styling
     */
    function createRoleCell(user) {
        const cell = typeof createTableCell === 'function'
            ? createTableCell('', { className: 'role-cell' })
            : document.createElement('td');
        cell.className = 'role-cell';

        const select = document.createElement('select');
        select.className = 'role-select enhanced-select';
        select.dataset.email = user.email;
        select.dataset.prevValue = user.role || 'USER';
        select.style.fontSize = '0.875rem'; // Match table font size

        ['USER', 'MODERATOR', 'ADMIN'].forEach(role => {
            const option = document.createElement('option');
            option.value = role;
            option.textContent = role;
            option.selected = user.role === role;
            select.appendChild(option);
        });

        select.addEventListener('change', function() {
            handleRoleChange(user.email, this.value, this);
        });

        cell.appendChild(select);
        return cell;
    }

    /**
     * Create status cell with enhanced toggle
     * Uses consistent table cell styling
     */
    function createStatusCell(user) {
        const cell = typeof createTableCell === 'function'
            ? createTableCell('', { className: 'status-cell' })
            : document.createElement('td');
        cell.className = 'status-cell';

        const toggle = document.createElement('label');
        toggle.className = 'status-toggle enhanced-toggle';

        const input = document.createElement('input');
        input.type = 'checkbox';
        input.checked = !!user.isActive;
        input.dataset.email = user.email;
        input.dataset.prevChecked = String(!!user.isActive);

        input.setAttribute('aria-label', 'Toggle user status');
        input.addEventListener('change', function() {
            handleStatusChange(user.email, this.checked, this);
        });

        const slider = document.createElement('span');
        slider.className = 'status-slider';

        toggle.appendChild(input);
        toggle.appendChild(slider);
        cell.appendChild(toggle);

        return cell;
    }

    /**
     * Create premium cell with enhanced toggle
     */
    function createPremiumCell(user) {
        const cell = document.createElement('td');
        cell.className = 'premium-cell';

        const toggle = document.createElement('label');
        toggle.className = 'status-toggle enhanced-toggle premium-toggle';

        const input = document.createElement('input');
        input.type = 'checkbox';
        input.checked = !!user.isPremium;
        input.dataset.email = user.email;
        input.dataset.prevChecked = String(!!user.isPremium);

        input.setAttribute('aria-label', 'Toggle premium access');
        input.addEventListener('change', function() {
            handlePremiumChange(user.email, this.checked, this);
        });

        const slider = document.createElement('span');
        slider.className = 'status-slider premium-slider';

        toggle.appendChild(input);
        toggle.appendChild(slider);
        cell.appendChild(toggle);

        return cell;
    }


    /**
     * Create actions cell with dropdown menu
     */
    function createActionsCell(user) {
        const cell = typeof createTableCell === 'function'
            ? createTableCell('', { align: 'center', className: 'actions-cell' })
            : document.createElement('td');
        cell.className = 'actions-cell';
        if (!cell.style.textAlign) {
            cell.style.textAlign = 'center';
        }

        const menuContainer = document.createElement('div');
        menuContainer.className = 'actions-menu-container';

        const menuButton = document.createElement('button');
        menuButton.className = 'actions-menu-button';
        menuButton.innerHTML = '⋮';
        menuButton.setAttribute('aria-label', 'User actions');

        const menu = document.createElement('div');
        menu.className = 'actions-menu';

        // Send notification action
        const notifyAction = createMenuItem('Send Notification', () => openNotificationPanel(user));
        menu.appendChild(notifyAction);

        // Reset password action
        const resetAction = createMenuItem('Reset Password', () => showResetPasswordDialog(user));
        menu.appendChild(resetAction);

        // Delete user action (danger)
        const deleteAction = createMenuItem('Delete User', () => showDeleteUserDialog(user), 'danger');
        menu.appendChild(deleteAction);

        menuButton.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleActionsMenu(menuContainer);
        });

        menuContainer.appendChild(menuButton);
        menuContainer.appendChild(menu);
        cell.appendChild(menuContainer);

        return cell;
    }

    /**
     * Create menu item
     */
    function createMenuItem(text, onClick, variant = 'default') {
        const item = document.createElement('button');
        item.className = `menu-item menu-item-${variant}`;
        item.innerHTML = `<span class="menu-text">${text}</span>`;
        item.addEventListener('click', (e) => {
            e.stopPropagation();
            onClick();
            closeAllMenus();
        });
        return item;
    }

    /**
     * Toggle actions menu
     */
    function toggleActionsMenu(container) {
        const isOpen = container.classList.contains('menu-open');
        closeAllMenus();
        if (!isOpen) {
            container.classList.add('menu-open');
            document.addEventListener('click', closeAllMenus, { once: true });
        }
    }

    /**
     * Close all action menus
     */
    function closeAllMenus() {
        document.querySelectorAll('.actions-menu-container.menu-open').forEach(menu => {
            menu.classList.remove('menu-open');
        });
    }


    // ============= Filters and Search =============

    /**
     * Initialize filters UI
     */
    function initializeFilters() {
        const filtersContainer = document.getElementById('users-filters');
        if (!filtersContainer) return;

        filtersContainer.innerHTML = `
            <div class="filters-row">
                <div class="filter-group">
                    <input type="text"
                           id="user-search"
                           class="filter-input search-input"
                           placeholder="🔍 Search by email..."
                           value="${UsersState.filters.search}">
                </div>

                <div class="filter-group">
                    <select id="role-filter" class="filter-select">
                        <option value="all">All Roles</option>
                        <option value="USER">User</option>
                        <option value="MODERATOR">Moderator</option>
                        <option value="ADMIN">Admin</option>
                    </select>
                </div>

                <div class="filter-group">
                    <select id="status-filter" class="filter-select">
                        <option value="all">All Status</option>
                        <option value="active">Active</option>
                        <option value="inactive">Inactive</option>
                    </select>
                </div>

                <div class="filter-group">
                    <select id="premium-filter" class="filter-select">
                        <option value="all">All Users</option>
                        <option value="premium">Premium</option>
                        <option value="free">Free</option>
                    </select>
                </div>

                <button id="clear-filters" class="btn btn-secondary btn-sm">Clear Filters</button>
            </div>

            <div class="bulk-actions-bar" id="bulk-actions-bar" style="display: none;">
                <div class="bulk-info">
                    <input type="checkbox" id="select-all-users" class="bulk-select-checkbox">
                    <span id="selected-count">0 selected</span>
                </div>
                <div class="bulk-actions">
                    <button class="btn btn-sm" id="bulk-activate">Activate</button>
                    <button class="btn btn-sm" id="bulk-deactivate">Deactivate</button>
                    <button class="btn btn-sm" id="bulk-premium">Make Premium</button>
                    <button class="btn btn-sm btn-danger" id="bulk-delete">Delete</button>
                </div>
            </div>
        `;

        if (!SELECTION_ENABLED) {
            const bulkBar = document.getElementById('bulk-actions-bar');
            if (bulkBar) bulkBar.remove();
        }

        // Bind filter events
        bindFilterEvents();
    }

    /**
     * Bind filter events
     */
    function bindFilterEvents() {
        const searchInput = document.getElementById('user-search');
        const roleFilter = document.getElementById('role-filter');
        const statusFilter = document.getElementById('status-filter');
        const premiumFilter = document.getElementById('premium-filter');
        const clearBtn = document.getElementById('clear-filters');
        const selectAllCheckbox = document.getElementById('select-all-users');

        if (searchInput) {
            searchInput.addEventListener('input', debounce((e) => {
                UsersState.filters.search = e.target.value;
                applyFilters();
            }, 300));
        }

        if (roleFilter) {
            roleFilter.addEventListener('change', (e) => {
                UsersState.filters.role = e.target.value;
                applyFilters();
            });
        }

        if (statusFilter) {
            statusFilter.addEventListener('change', (e) => {
                UsersState.filters.status = e.target.value;
                applyFilters();
            });
        }

        if (premiumFilter) {
            premiumFilter.addEventListener('change', (e) => {
                UsersState.filters.premium = e.target.value;
                applyFilters();
            });
        }

        if (clearBtn) {
            clearBtn.addEventListener('click', clearFilters);
        }

        if (selectAllCheckbox && SELECTION_ENABLED) {
            selectAllCheckbox.addEventListener('change', (e) => {
                handleSelectAll(e.target.checked);
            });
        }

        // Bind bulk action buttons
        bindBulkActions();
    }

    /**
     * Apply filters to user list
     */
    function applyFilters() {
        let filtered = [...UsersState.users];

        // Search filter
        if (UsersState.filters.search) {
            const search = UsersState.filters.search.toLowerCase();
            filtered = filtered.filter(user =>
                user.email.toLowerCase().includes(search)
            );
        }

        // Role filter
        if (UsersState.filters.role !== 'all') {
            filtered = filtered.filter(user => user.role === UsersState.filters.role);
        }

        // Status filter
        if (UsersState.filters.status !== 'all') {
            const isActive = UsersState.filters.status === 'active';
            filtered = filtered.filter(user => user.isActive === isActive);
        }

        // Premium filter
        if (UsersState.filters.premium !== 'all') {
            const isPremium = UsersState.filters.premium === 'premium';
            filtered = filtered.filter(user => user.isPremium === isPremium);
        }

        renderUsersTable(filtered);
        updateFilterStats(filtered.length);
    }

    /**
     * Clear all filters
     */
    function clearFilters() {
        UsersState.filters = {
            search: '',
            role: 'all',
            status: 'all',
            premium: 'all'
        };

        document.getElementById('user-search').value = '';
        document.getElementById('role-filter').value = 'all';
        document.getElementById('status-filter').value = 'all';
        document.getElementById('premium-filter').value = 'all';

        applyFilters();
    }

    /**
     * Update filter stats
     */
    function updateFilterStats(count) {
        const statsEl = document.getElementById('filter-stats');
        if (statsEl) {
            statsEl.textContent = `Showing ${count} of ${UsersState.totalUsers} users`;
        }
    }


    // ============= Bulk Actions =============

    /**
     * Handle user selection
     */
    function handleUserSelect(email, isSelected) {
        if (isSelected) {
            UsersState.selectedUsers.add(email);
        } else {
            UsersState.selectedUsers.delete(email);
        }
        updateBulkActionsBar();
    }

    /**
     * Handle select all
     */
    function handleSelectAll(isSelected) {
        const checkboxes = document.querySelectorAll('.user-select-checkbox');
        checkboxes.forEach(checkbox => {
            checkbox.checked = isSelected;
            const email = checkbox.dataset.email;
            if (isSelected) {
                UsersState.selectedUsers.add(email);
            } else {
                UsersState.selectedUsers.delete(email);
            }
        });
        updateBulkActionsBar();
    }

    /**
     * Update bulk actions bar visibility
     */
    function updateBulkActionsBar() {
        const bar = document.getElementById('bulk-actions-bar');
        const count = document.getElementById('selected-count');

        if (UsersState.selectedUsers.size > 0) {
            bar.style.display = 'flex';
            count.textContent = `${UsersState.selectedUsers.size} selected`;
        } else {
            bar.style.display = 'none';
        }
    }

    /**
     * Update bulk select UI
     */
    function updateBulkSelectUI() {
        if (!SELECTION_ENABLED) return;
        const selectAllCheckbox = document.getElementById('select-all-users');
        if (!selectAllCheckbox) return;

        const checkboxes = document.querySelectorAll('.user-select-checkbox');
        const checkedCount = Array.from(checkboxes).filter(cb => cb.checked).length;

        selectAllCheckbox.checked = checkedCount > 0 && checkedCount === checkboxes.length;
        selectAllCheckbox.indeterminate = checkedCount > 0 && checkedCount < checkboxes.length;
    }

    /**
     * Bind bulk action buttons
     */
    function bindBulkActions() {
        if (!SELECTION_ENABLED) return;
        const bulkActivate = document.getElementById('bulk-activate');
        const bulkDeactivate = document.getElementById('bulk-deactivate');
        const bulkPremium = document.getElementById('bulk-premium');
        const bulkDelete = document.getElementById('bulk-delete');

        if (bulkActivate) {
            bulkActivate.addEventListener('click', () => handleBulkAction('activate'));
        }

        if (bulkDeactivate) {
            bulkDeactivate.addEventListener('click', () => handleBulkAction('deactivate'));
        }

        if (bulkPremium) {
            bulkPremium.addEventListener('click', () => handleBulkAction('premium'));
        }

        if (bulkDelete) {
            bulkDelete.addEventListener('click', () => handleBulkAction('delete'));
        }
    }

    /**
     * Handle bulk actions
     */
    async function handleBulkAction(action) {
        const selectedEmails = Array.from(UsersState.selectedUsers);

        if (selectedEmails.length === 0) {
            showMessage('warning', 'No users selected');
            return;
        }

        const confirmMsg = `Are you sure you want to ${action} ${selectedEmails.length} user(s)?`;
        if (!confirm(confirmMsg)) return;

        showMessage('info', `Processing ${action} for ${selectedEmails.length} users...`);

        // Process in batches for better performance
        const batchSize = 5;
        let successCount = 0;
        let errorCount = 0;

        for (let i = 0; i < selectedEmails.length; i += batchSize) {
            const batch = selectedEmails.slice(i, i + batchSize);
            const results = await Promise.allSettled(
                batch.map(email => executeBulkAction(email, action))
            );

            results.forEach(result => {
                if (result.status === 'fulfilled') successCount++;
                else errorCount++;
            });
        }

        // Clear selection
        UsersState.selectedUsers.clear();
        updateBulkActionsBar();

        // Refresh users list
        await refreshUsers();

        // Show result
        if (errorCount === 0) {
            showMessage('success', `Successfully ${action}d ${successCount} user(s)`);
        } else {
            showMessage('warning', `Completed with ${successCount} success, ${errorCount} errors`);
        }
    }

    /**
     * Execute bulk action for single user
     */
    async function executeBulkAction(email, action) {
        switch (action) {
            case 'activate':
                return window.UserRoute.updateStatus(email, true);
            case 'deactivate':
                return window.UserRoute.updateStatus(email, false);
            case 'premium':
                return window.UserRoute.updatePremium(email, true);
            case 'delete':
                // Implement delete endpoint
                throw new Error('Delete not implemented');
            default:
                throw new Error('Unknown action');
        }
    }


    // ============= Event Handlers =============

    /**
     * Handle role change with optimistic UI update
     */
    async function handleRoleChange(email, newRole, selectEl) {
        const prevValue = selectEl.dataset.prevValue;
        selectEl.disabled = true;

        try {
            const response = await window.UserRoute.updateRole(email, newRole);

            if (!response.ok) {
                // Show user-friendly error message
                let errorMsg = 'Unable to update user role. ';
                if (response.status === 401 || response.status === 403) {
                    errorMsg += 'Your session has expired. Please refresh the page and login again.';
                } else if (response.status === 404) {
                    errorMsg += 'User not found.';
                } else {
                    errorMsg += `Please try again (Error ${response.status}).`;
                }
                showMessage('error', errorMsg);
                selectEl.value = prevValue;
                return;
            }

            const data = await response.json();

            if (data.status === true) {
                selectEl.dataset.prevValue = newRole;
                showMessage('success', `Role updated to ${newRole} for ${email}`);

                // Invalidate cache
                if (window.CachedAPI) {
                    window.CachedAPI.invalidateUsers();
                }
            } else {
                // Show user-friendly error message
                const errorMsg = data.message || 'Unable to update user role. Please try again.';
                showMessage('error', errorMsg);
                selectEl.value = prevValue;
            }
        } catch (error) {
            // Handle unexpected errors (network errors, etc.)
            console.error('Error updating role:', error);
            selectEl.value = prevValue;
            const errorMsg = error.message || 'An unexpected error occurred while updating role. Please try again.';
            showMessage('error', errorMsg);
        } finally {
            selectEl.disabled = false;
        }
    }

    /**
     * Handle status change with optimistic UI update
     */
    async function handleStatusChange(email, isActive, checkboxEl) {
        const prevValue = checkboxEl.dataset.prevChecked === 'true';
        checkboxEl.disabled = true;

        try {
            const response = await window.UserRoute.updateStatus(email, isActive);

            if (!response.ok) {
                // Show user-friendly error message
                let errorMsg = 'Unable to update user status. ';
                if (response.status === 401 || response.status === 403) {
                    errorMsg += 'Your session has expired. Please refresh the page and login again.';
                } else if (response.status === 404) {
                    errorMsg += 'User not found.';
                } else {
                    errorMsg += `Please try again (Error ${response.status}).`;
                }
                showMessage('error', errorMsg);
                checkboxEl.checked = prevValue;
                return;
            }

            const data = await response.json();

            if (data.status === true) {
                checkboxEl.dataset.prevChecked = String(isActive);
                const statusText = isActive ? 'activated' : 'deactivated';
                showMessage('success', `User ${statusText} successfully`);

                // Update status label
                // Invalidate cache
                if (window.CachedAPI) {
                    window.CachedAPI.invalidateUsers();
                }
            } else {
                // Show user-friendly error message
                const errorMsg = data.message || 'Unable to update user status. Please try again.';
                showMessage('error', errorMsg);
                checkboxEl.checked = prevValue;
            }
        } catch (error) {
            // Handle unexpected errors (network errors, etc.)
            console.error('Error updating status:', error);
            checkboxEl.checked = prevValue;
            const errorMsg = error.message || 'An unexpected error occurred while updating status. Please try again.';
            showMessage('error', errorMsg);
        } finally {
            checkboxEl.disabled = false;
        }
    }

    /**
     * Handle premium change with optimistic UI update
     */
    async function handlePremiumChange(email, isPremium, checkboxEl) {
        const prevValue = checkboxEl.dataset.prevChecked === 'true';
        checkboxEl.disabled = true;

        try {
            const response = await window.UserRoute.updatePremium(email, isPremium);

            if (!response.ok) {
                // Show user-friendly error message
                let errorMsg = 'Unable to update premium status. ';
                if (response.status === 401 || response.status === 403) {
                    errorMsg += 'Your session has expired. Please refresh the page and login again.';
                } else if (response.status === 404) {
                    errorMsg += 'User not found.';
                } else {
                    errorMsg += `Please try again (Error ${response.status}).`;
                }
                showMessage('error', errorMsg);
                checkboxEl.checked = prevValue;
                return;
            }

            const data = await response.json();

            if (data.status === true) {
                checkboxEl.dataset.prevChecked = String(isPremium);
                const statusText = isPremium ? 'enabled' : 'disabled';
                showMessage('success', `Premium ${statusText} for ${email}`);

                // Invalidate cache
                if (window.CachedAPI) {
                    window.CachedAPI.invalidateUsers();
                }
            } else {
                // Show user-friendly error message
                const errorMsg = data.message || 'Unable to update premium status. Please try again.';
                showMessage('error', errorMsg);
                checkboxEl.checked = prevValue;
            }
        } catch (error) {
            // Handle unexpected errors (network errors, etc.)
            console.error('Error updating premium:', error);
            checkboxEl.checked = prevValue;
            const errorMsg = error.message || 'An unexpected error occurred while updating premium status. Please try again.';
            showMessage('error', errorMsg);
        } finally {
            checkboxEl.disabled = false;
        }
    }


    // ============= User Actions =============

    /**
     * Open notification sliding panel
     */
    function openNotificationPanel(user) {
        if (typeof window.showNotificationPanel === 'function') {
            window.showNotificationPanel(user.email);
            return;
        }
        showMessage('error', 'Notification panel is unavailable');
    }

    /**
     * Show reset password dialog
     */
    function showResetPasswordDialog(user) {
        const dialog = createUserActionDialog({
            title: 'Reset Password',
            subtitle: `Set a new password for ${escapeHtml(user.email)}`,
            confirmText: 'Update Password',
            confirmVariant: 'primary',
            bodyHtml: `
                <div class="user-action-field">
                    <label class="user-action-label" for="reset-password-input">New password</label>
                    <input id="reset-password-input" type="password" class="user-action-input" placeholder="Enter a strong password" autocomplete="new-password">
                </div>
                <div class="user-action-field">
                    <label class="user-action-label" for="reset-password-confirm">Confirm password</label>
                    <input id="reset-password-confirm" type="password" class="user-action-input" placeholder="Re-enter password" autocomplete="new-password">
                </div>
                <div class="user-action-hint">Use at least 8 characters with upper, lower, number, and special.</div>
                <div class="user-action-error" id="reset-password-error"></div>
            `
        });

        const passwordInput = dialog.container.querySelector('#reset-password-input');
        const confirmInput = dialog.container.querySelector('#reset-password-confirm');
        const errorEl = dialog.container.querySelector('#reset-password-error');

        dialog.onConfirm(async () => {
            const password = passwordInput?.value || '';
            const confirm = confirmInput?.value || '';

            if (!password || password.length < 8) {
                showDialogError(errorEl, 'Password must be at least 8 characters.');
                passwordInput?.focus();
                return;
            }
            if (password !== confirm) {
                showDialogError(errorEl, 'Passwords do not match.');
                confirmInput?.focus();
                return;
            }

            setDialogLoading(dialog, true, 'Updating...');
            try {
                if (!window.UserRoute || typeof window.UserRoute.resetPassword !== 'function') {
                    const errorMsg = 'Password reset feature is not available. Please contact support.';
                    showDialogError(errorEl, errorMsg);
                    return;
                }
                const response = await window.UserRoute.resetPassword(user.email, password);

                if (!response.ok) {
                    // Show user-friendly error message
                    let errorMsg = 'Unable to reset password. ';
                    if (response.status === 401 || response.status === 403) {
                        errorMsg = 'Your session has expired. Please refresh the page and login again.';
                    } else if (response.status === 404) {
                        errorMsg = 'User not found.';
                    } else {
                        errorMsg += `Please try again (Error ${response.status}).`;
                    }
                    showDialogError(errorEl, errorMsg);
                    return;
                }

                const data = await response.json();
                if (data.status !== true) {
                    const errorMsg = data.message || 'Unable to reset password. Please try again.';
                    showDialogError(errorEl, errorMsg);
                    return;
                }

                showMessage('success', `Password updated for ${user.email}`);
                dialog.close();
            } catch (error) {
                // Handle unexpected errors (network errors, etc.)
                console.error('Error resetting password:', error);
                const errorMsg = error.message || 'An unexpected error occurred while resetting password. Please try again.';
                showDialogError(errorEl, errorMsg);
            } finally {
                setDialogLoading(dialog, false, 'Update Password');
            }
        });
    }

    /**
     * Show delete confirmation dialog
     */
    function showDeleteUserDialog(user) {
        const dialog = createUserActionDialog({
            title: 'Delete User',
            subtitle: `This will permanently delete ${escapeHtml(user.email)}`,
            confirmText: 'Delete User',
            confirmVariant: 'danger',
            bodyHtml: `
                <p class="user-action-warning">
                    This action cannot be undone. Type the user's email to confirm.
                </p>
                <div class="user-action-field">
                    <label class="user-action-label" for="delete-user-confirm">Confirm email</label>
                    <input id="delete-user-confirm" type="text" class="user-action-input" placeholder="${escapeHtml(user.email)}" autocomplete="off">
                </div>
                <div class="user-action-error" id="delete-user-error"></div>
            `
        });

        const confirmInput = dialog.container.querySelector('#delete-user-confirm');
        const errorEl = dialog.container.querySelector('#delete-user-error');

        dialog.onConfirm(async () => {
            const typed = (confirmInput?.value || '').trim();
            if (typed !== user.email) {
                showDialogError(errorEl, 'Email does not match.');
                confirmInput?.focus();
                return;
            }

            setDialogLoading(dialog, true, 'Deleting...');
            try {
                if (!window.UserRoute || typeof window.UserRoute.deleteUser !== 'function') {
                    const errorMsg = 'User deletion feature is not available. Please contact support.';
                    showDialogError(errorEl, errorMsg);
                    return;
                }
                const response = await window.UserRoute.deleteUser(user.email);

                if (!response.ok) {
                    // Show user-friendly error message
                    let errorMsg = 'Unable to delete user. ';
                    if (response.status === 401 || response.status === 403) {
                        errorMsg = 'Your session has expired. Please refresh the page and login again.';
                    } else if (response.status === 404) {
                        errorMsg = 'User not found.';
                    } else {
                        errorMsg += `Please try again (Error ${response.status}).`;
                    }
                    showDialogError(errorEl, errorMsg);
                    return;
                }

                const data = await response.json();
                if (data.status !== true) {
                    const errorMsg = data.message || 'Unable to delete user. Please try again.';
                    showDialogError(errorEl, errorMsg);
                    return;
                }

                showMessage('success', `User deleted: ${user.email}`);
                dialog.close();
                await refreshUsers();
            } catch (error) {
                // Handle unexpected errors (network errors, etc.)
                console.error('Error deleting user:', error);
                const errorMsg = error.message || 'An unexpected error occurred while deleting user. Please try again.';
                showDialogError(errorEl, errorMsg);
            } finally {
                setDialogLoading(dialog, false, 'Delete User');
            }
        });
    }

    /**
     * Create a reusable dialog for user actions
     */
    function createUserActionDialog(options) {
        const existing = document.getElementById('user-action-dialog');
        if (existing) existing.remove();
        const existingBackdrop = document.getElementById('user-action-backdrop');
        if (existingBackdrop) existingBackdrop.remove();

        const backdrop = document.createElement('div');
        backdrop.id = 'user-action-backdrop';
        backdrop.className = 'user-action-backdrop';

        const dialog = document.createElement('div');
        dialog.id = 'user-action-dialog';
        dialog.className = 'user-action-dialog';

        dialog.innerHTML = `
            <div class="user-action-header">
                <div>
                    <h3>${options.title}</h3>
                    <p>${options.subtitle || ''}</p>
                </div>
                <button type="button" class="user-action-close" aria-label="Close dialog">×</button>
            </div>
            <div class="user-action-body">
                ${options.bodyHtml || ''}
            </div>
            <div class="user-action-footer">
                <button type="button" class="user-action-btn secondary" data-action="cancel">Cancel</button>
                <button type="button" class="user-action-btn ${options.confirmVariant || 'primary'}" data-action="confirm">${options.confirmText || 'Confirm'}</button>
            </div>
        `;

        document.body.appendChild(backdrop);
        document.body.appendChild(dialog);

        const close = () => {
            dialog.classList.remove('open');
            backdrop.classList.remove('open');
            setTimeout(() => {
                dialog.remove();
                backdrop.remove();
            }, 200);
        };

        const closeBtn = dialog.querySelector('.user-action-close');
        const cancelBtn = dialog.querySelector('[data-action="cancel"]');
        const confirmBtn = dialog.querySelector('[data-action="confirm"]');

        const onConfirmHandlers = [];
        const onConfirm = (handler) => {
            if (typeof handler === 'function') {
                onConfirmHandlers.push(handler);
            }
        };

        const handleConfirm = () => {
            onConfirmHandlers.forEach(handler => handler());
        };

        const escapeHandler = (event) => {
            if (event.key === 'Escape') {
                closeDialog();
            }
        };

        const closeDialog = () => {
            document.removeEventListener('keydown', escapeHandler);
            close();
        };

        closeBtn?.addEventListener('click', closeDialog);
        cancelBtn?.addEventListener('click', closeDialog);
        backdrop.addEventListener('click', closeDialog);
        confirmBtn?.addEventListener('click', handleConfirm);
        document.addEventListener('keydown', escapeHandler);

        requestAnimationFrame(() => {
            dialog.classList.add('open');
            backdrop.classList.add('open');
        });

        return {
            container: dialog,
            confirmButton: confirmBtn,
            close: closeDialog,
            onConfirm
        };
    }

    // Expose createUserActionDialog globally for use in other modules (e.g., JWT Inspector)
    window.createUserActionDialog = createUserActionDialog;
    window.showDialogError = showDialogError;
    window.setDialogLoading = setDialogLoading;

    function showDialogError(errorEl, message) {
        if (!errorEl) return;
        errorEl.textContent = message;
        errorEl.classList.add('show');
    }

    function setDialogLoading(dialog, isLoading, text) {
        if (!dialog || !dialog.confirmButton) return;
        const btn = dialog.confirmButton;
        if (!btn.dataset.originalText) {
            btn.dataset.originalText = btn.textContent || '';
        }
        btn.disabled = isLoading;
        if (isLoading) {
            btn.textContent = text || 'Working...';
        } else {
            btn.textContent = btn.dataset.originalText;
        }
    }


    // ============= Utility Functions =============

    /**
     * Generate avatar color based on email
     */
    function generateAvatarColor(email) {
        const colors = [
            '#6366f1', '#8b5cf6', '#ec4899', '#f43f5e',
            '#f59e0b', '#10b981', '#06b6d4', '#3b82f6'
        ];
        const hash = email.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
        return colors[hash % colors.length];
    }

    /**
     * Format date
     * Uses common formatDate from admin-utils.js with custom format
     */
    const formatDate = window.formatDate || function(dateString) {
        if (!dateString) return 'N/A';
        try {
            const date = new Date(dateString);
            return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
        } catch (error) {
            return dateString;
        }
    };

    /**
     * Get relative time
     */
    function getRelativeTime(dateString) {
        if (!dateString) return '';

        try {
            const date = new Date(dateString);
            const now = new Date();
            const diffMs = now - date;
            const diffSec = Math.floor(diffMs / 1000);
            const diffMin = Math.floor(diffSec / 60);
            const diffHour = Math.floor(diffMin / 60);
            const diffDay = Math.floor(diffHour / 24);
            const diffMonth = Math.floor(diffDay / 30);
            const diffYear = Math.floor(diffDay / 365);

            if (diffYear > 0) return `${diffYear} year${diffYear > 1 ? 's' : ''} ago`;
            if (diffMonth > 0) return `${diffMonth} month${diffMonth > 1 ? 's' : ''} ago`;
            if (diffDay > 0) return `${diffDay} day${diffDay > 1 ? 's' : ''} ago`;
            if (diffHour > 0) return `${diffHour} hour${diffHour > 1 ? 's' : ''} ago`;
            if (diffMin > 0) return `${diffMin} minute${diffMin > 1 ? 's' : ''} ago`;
            return 'Just now';
        } catch (error) {
            return '';
        }
    }

    /**
     * Escape HTML
     * Uses common escapeHtml from admin-utils.js
     */
    const escapeHtml = window.escapeHtml || function(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    };

    /**
     * Generate skeleton rows for loading state
     */
    function generateSkeletonRows(count) {
        let html = '';
        for (let i = 0; i < count; i++) {
            html += `
                <tr class="skeleton-row">
                    <td><div class="skeleton skeleton-text"></div></td>
                    <td><div class="skeleton skeleton-text"></div></td>
                    <td><div class="skeleton skeleton-select"></div></td>
                    <td><div class="skeleton skeleton-toggle"></div></td>
                    <td><div class="skeleton skeleton-toggle"></div></td>
                    <td><div class="skeleton skeleton-button"></div></td>
                </tr>
            `;
        }
        return html;
    }

    /**
     * Create empty row
     */
    function createEmptyRow() {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 6;
        cell.className = 'empty-cell';
        cell.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">👥</div>
                <div class="empty-text">No users found</div>
                <div class="empty-subtext">Try adjusting your filters</div>
            </div>
        `;
        row.appendChild(cell);
        return row;
    }

    /**
     * Show message (uses global showMessage if available)
     */
    function showMessage(type, message) {
        if (typeof window.showMessage === 'function') {
            window.showMessage(type, message);
        } else {
            console.log(`[${type.toUpperCase()}] ${message}`);
        }
    }


    // ============= Data Loading =============

    /**
     * Load users with caching
     */
    async function loadUsers(page = 1, pageSize = 10) {
        UsersState.isLoading = true;
        UsersState.currentPage = page;
        UsersState.pageSize = pageSize;

        try {
            const loader = document.getElementById('iam-loader');
            if (loader) loader.style.display = 'block';

            // Use cached API if available
            let result;
            if (window.CachedAPI && typeof window.CachedAPI.getUsers === 'function') {
                result = await window.CachedAPI.getUsers(page, pageSize);
            } else if (window.UserRoute && typeof window.UserRoute.listUsers === 'function') {
                result = await window.UserRoute.listUsers(page, pageSize);
            } else {
                // Show user-friendly error message
                const errorMsg = 'Unable to load users. The user management system is not available. Please refresh the page or contact support.';
                console.error(errorMsg);
                if (typeof showMessage === 'function') {
                    showMessage('error', errorMsg);
                }
                // Return empty result instead of throwing
                return {
                    users: [],
                    pagination: {
                        page: 1,
                        pageSize: pageSize,
                        totalPages: 0,
                        totalCount: 0
                    }
                };
            }

            UsersState.users = result.users || [];
            UsersState.totalPages = result.pagination?.totalPages || 1;
            UsersState.totalUsers = result.pagination?.totalCount || 0;

            applyFilters();
            renderPagination();

            if (loader) loader.style.display = 'none';
        } catch (error) {
            console.error('Error loading users:', error);
            showMessage('error', error.message || 'Failed to load users');

            const loader = document.getElementById('iam-loader');
            if (loader) loader.style.display = 'none';
        } finally {
            UsersState.isLoading = false;
        }
    }

    /**
     * Refresh users list
     */
    async function refreshUsers() {
        // Invalidate cache
        if (window.CachedAPI) {
            window.CachedAPI.invalidateUsers();
        }

        await loadUsers(UsersState.currentPage, UsersState.pageSize);
    }

    /**
     * Render pagination
     */
    function renderPagination() {
        const container = document.getElementById('pagination');
        if (!container) return;

        container.innerHTML = '';

        if (UsersState.totalPages <= 1) return;

        const fragment = document.createDocumentFragment();

        // Previous button
        const prevBtn = createPaginationButton('← Previous', UsersState.currentPage - 1, UsersState.currentPage === 1);
        fragment.appendChild(prevBtn);

        // Page numbers
        const maxButtons = 5;
        const startPage = Math.max(1, UsersState.currentPage - Math.floor(maxButtons / 2));
        const endPage = Math.min(UsersState.totalPages, startPage + maxButtons - 1);

        for (let i = startPage; i <= endPage; i++) {
            const pageBtn = createPaginationButton(i, i, false, i === UsersState.currentPage);
            fragment.appendChild(pageBtn);
        }

        // Next button
        const nextBtn = createPaginationButton('Next →', UsersState.currentPage + 1, UsersState.currentPage === UsersState.totalPages);
        fragment.appendChild(nextBtn);

        container.appendChild(fragment);
    }

    /**
     * Create pagination button
     */
    function createPaginationButton(text, page, disabled = false, active = false) {
        const button = document.createElement('button');
        button.className = `pagination-button ${active ? 'active' : ''} ${disabled ? 'disabled' : ''}`;
        button.textContent = text;
        button.disabled = disabled;

        if (!disabled) {
            button.addEventListener('click', () => {
                loadUsers(page, UsersState.pageSize);
            });
        }

        return button;
    }


    // ============= Initialization =============

    /**
     * Initialize users module
     */
    function initializeUsersModule() {
        console.log('[Users Module] Initializing...');

        // Initialize filters UI
        initializeFilters();

        // Load initial data if IAM tab is active
        const iamPanel = document.getElementById('iam');
        if (iamPanel && iamPanel.classList.contains('active')) {
            loadUsers(1, 10);
        }

        console.log('[Users Module] Initialized');
    }

    // ============= Export to Global Scope =============

    // Export functions for backward compatibility
    window.loadUsers = loadUsers;
    window.renderUsersTable = renderUsersTable;
    window.updateUserRole = handleRoleChange;
    window.updateUserStatus = handleStatusChange;
    window.updateUserPremium = handlePremiumChange;
    window.formatDate = formatDate;
    window.refreshUsers = refreshUsers;

    // Export module state and functions
    window.UsersModule = {
        state: UsersState,
        loadUsers,
        refreshUsers,
        applyFilters,
        clearFilters,
        initializeUsersModule,
        VirtualScroll // Export VirtualScroll for external access if needed
    };

    // Auto-initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeUsersModule);
    } else {
        // DOM already loaded, initialize after a short delay
        setTimeout(initializeUsersModule, 100);
    }

    console.log('[Admin Users Module] Loaded');
})();
