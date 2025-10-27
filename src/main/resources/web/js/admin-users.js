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
     * Virtual scrolling for large datasets (future enhancement)
     */
    const VirtualScroll = {
        enabled: false,
        rowHeight: 60,
        visibleRows: 20
    };


    // ============= UI Rendering =============

    /**
     * Render enhanced users table with improved UI
     */
    function renderUsersTable(users) {
        const tableBody = document.getElementById('users-table-body');
        if (!tableBody) return;

        // Show skeleton loader during render
        if (UsersState.isLoading) {
            tableBody.innerHTML = generateSkeletonRows(UsersState.pageSize);
            return;
        }

        // Use DocumentFragment for better performance
        const fragment = document.createDocumentFragment();

        if (users.length === 0) {
            const row = createEmptyRow();
            fragment.appendChild(row);
        } else {
            users.forEach(user => {
                const row = createUserRow(user);
                fragment.appendChild(row);
            });
        }

        // Batch DOM update
        tableBody.replaceChildren(fragment);
        
        // Update selection checkboxes
        updateBulkSelectUI();
    }

    /**
     * Create enhanced user row with better UX
     */
    function createUserRow(user) {
        const row = document.createElement('tr');
        row.className = 'user-row';
        row.dataset.email = user.email;
        
        // Add hover effect class
        row.addEventListener('mouseenter', () => row.classList.add('row-hover'));
        row.addEventListener('mouseleave', () => row.classList.remove('row-hover'));

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

        // Actions menu
        const actionsCell = createActionsCell(user);
        row.appendChild(actionsCell);

        return row;
    }


    /**
     * Create email cell with avatar and badges
     */
    function createEmailCell(user) {
        const cell = document.createElement('td');
        cell.className = 'email-cell';
        
        const container = document.createElement('div');
        container.className = 'email-container';
        
        // Avatar with first letter
        const avatar = document.createElement('div');
        avatar.className = 'user-avatar';
        avatar.textContent = user.email.charAt(0).toUpperCase();
        avatar.style.background = generateAvatarColor(user.email);
        container.appendChild(avatar);
        
        // Email and badges wrapper
        const textWrapper = document.createElement('div');
        textWrapper.className = 'email-text-wrapper';
        
        const emailText = document.createElement('span');
        emailText.className = 'email-text';
        emailText.textContent = user.email;
        emailText.title = user.email;
        textWrapper.appendChild(emailText);
        
        // Badges container
        const badgesContainer = document.createElement('div');
        badgesContainer.className = 'badges-container';
        
        if (user.role === 'ADMIN') {
            const adminBadge = document.createElement('span');
            adminBadge.className = 'badge badge-admin';
            adminBadge.textContent = 'ADMIN';
            badgesContainer.appendChild(adminBadge);
        }
        
        if (user.isPremium === true) {
            const premiumBadge = document.createElement('span');
            premiumBadge.className = 'badge badge-premium';
            premiumBadge.textContent = 'PREMIUM';
            badgesContainer.appendChild(premiumBadge);
        }
        
        textWrapper.appendChild(badgesContainer);
        container.appendChild(textWrapper);
        cell.appendChild(container);
        
        return cell;
    }

    /**
     * Create created at cell with relative time
     */
    function createCreatedAtCell(createdAt) {
        const cell = document.createElement('td');
        cell.className = 'created-cell';
        
        const container = document.createElement('div');
        container.className = 'date-container';
        
        const dateText = document.createElement('span');
        dateText.className = 'date-text';
        dateText.textContent = formatDate(createdAt);
        container.appendChild(dateText);
        
        const relativeText = document.createElement('span');
        relativeText.className = 'date-relative';
        relativeText.textContent = getRelativeTime(createdAt);
        container.appendChild(relativeText);
        
        cell.appendChild(container);
        return cell;
    }


    /**
     * Create role cell with enhanced dropdown
     */
    function createRoleCell(user) {
        const cell = document.createElement('td');
        cell.className = 'role-cell';
        
        const select = document.createElement('select');
        select.className = 'role-select enhanced-select';
        select.dataset.email = user.email;
        select.dataset.prevValue = user.role || 'USER';
        
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
     */
    function createStatusCell(user) {
        const cell = document.createElement('td');
        cell.className = 'status-cell';
        
        const toggle = document.createElement('label');
        toggle.className = 'status-toggle enhanced-toggle';
        
        const input = document.createElement('input');
        input.type = 'checkbox';
        input.checked = !!user.isActive;
        input.dataset.email = user.email;
        input.dataset.prevChecked = String(!!user.isActive);
        
        input.addEventListener('change', function() {
            handleStatusChange(user.email, this.checked, this);
        });
        
        const slider = document.createElement('span');
        slider.className = 'status-slider';
        
        const statusLabel = document.createElement('span');
        statusLabel.className = 'status-label';
        statusLabel.textContent = user.isActive ? 'Active' : 'Inactive';
        
        toggle.appendChild(input);
        toggle.appendChild(slider);
        toggle.appendChild(statusLabel);
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
        const cell = document.createElement('td');
        cell.className = 'actions-cell';
        
        const menuContainer = document.createElement('div');
        menuContainer.className = 'actions-menu-container';
        
        const menuButton = document.createElement('button');
        menuButton.className = 'actions-menu-button';
        menuButton.innerHTML = '⋮';
        menuButton.setAttribute('aria-label', 'User actions');
        
        const menu = document.createElement('div');
        menu.className = 'actions-menu';
        
        // View details action
        const viewAction = createMenuItem('👤', 'View Details', () => showUserDetails(user));
        menu.appendChild(viewAction);
        
        // Send notification action
        const notifyAction = createMenuItem('🔔', 'Send Notification', () => showNotificationModal(user));
        menu.appendChild(notifyAction);
        
        // Reset password action
        const resetAction = createMenuItem('🔑', 'Reset Password', () => handlePasswordReset(user));
        menu.appendChild(resetAction);
        
        // Delete user action (danger)
        const deleteAction = createMenuItem('🗑️', 'Delete User', () => handleDeleteUser(user), 'danger');
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
    function createMenuItem(icon, text, onClick, variant = 'default') {
        const item = document.createElement('button');
        item.className = `menu-item menu-item-${variant}`;
        item.innerHTML = `<span class="menu-icon">${icon}</span><span class="menu-text">${text}</span>`;
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
                <button id="refresh-users" class="btn btn-primary btn-sm">
                    <span class="refresh-icon">↻</span> Refresh
                </button>
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
        const refreshBtn = document.getElementById('refresh-users');
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
        
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => {
                refreshUsers();
            });
        }
        
        if (selectAllCheckbox) {
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
                throw new Error('Failed to update role');
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
                throw new Error(data.message || 'Failed to update role');
            }
        } catch (error) {
            console.error('Error updating role:', error);
            selectEl.value = prevValue;
            showMessage('error', error.message || 'Failed to update role');
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
                throw new Error('Failed to update status');
            }
            
            const data = await response.json();
            
            if (data.status === true) {
                checkboxEl.dataset.prevChecked = String(isActive);
                const statusText = isActive ? 'activated' : 'deactivated';
                showMessage('success', `User ${statusText} successfully`);
                
                // Update status label
                const label = checkboxEl.parentElement.querySelector('.status-label');
                if (label) {
                    label.textContent = isActive ? 'Active' : 'Inactive';
                }
                
                // Invalidate cache
                if (window.CachedAPI) {
                    window.CachedAPI.invalidateUsers();
                }
            } else {
                throw new Error(data.message || 'Failed to update status');
            }
        } catch (error) {
            console.error('Error updating status:', error);
            checkboxEl.checked = prevValue;
            showMessage('error', error.message || 'Failed to update status');
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
                throw new Error('Failed to update premium status');
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
                throw new Error(data.message || 'Failed to update premium status');
            }
        } catch (error) {
            console.error('Error updating premium:', error);
            checkboxEl.checked = prevValue;
            showMessage('error', error.message || 'Failed to update premium status');
        } finally {
            checkboxEl.disabled = false;
        }
    }


    // ============= User Actions =============

    /**
     * Show user details modal
     */
    function showUserDetails(user) {
        const content = `
            <div class="user-details-modal">
                <div class="detail-section">
                    <h3>User Information</h3>
                    <div class="detail-row">
                        <span class="label">Email:</span>
                        <span class="value">${escapeHtml(user.email)}</span>
                    </div>
                    <div class="detail-row">
                        <span class="label">Role:</span>
                        <span class="value">${escapeHtml(user.role)}</span>
                    </div>
                    <div class="detail-row">
                        <span class="label">Status:</span>
                        <span class="value">${user.isActive ? '✓ Active' : '✗ Inactive'}</span>
                    </div>
                    <div class="detail-row">
                        <span class="label">Premium:</span>
                        <span class="value">${user.isPremium ? '⭐ Yes' : 'No'}</span>
                    </div>
                    <div class="detail-row">
                        <span class="label">Created:</span>
                        <span class="value">${formatDate(user.createdAt)}</span>
                    </div>
                    <div class="detail-row">
                        <span class="label">Member for:</span>
                        <span class="value">${getRelativeTime(user.createdAt)}</span>
                    </div>
                </div>
            </div>
        `;
        
        if (typeof showModal === 'function') {
            showModal('User Details', content);
        }
    }

    /**
     * Show notification modal
     */
    function showNotificationModal(user) {
        const content = `
            <div class="notification-modal">
                <form id="notification-form">
                    <div class="form-group">
                        <label for="notif-title">Title</label>
                        <input type="text" id="notif-title" class="form-control" 
                               placeholder="Notification title" required>
                    </div>
                    <div class="form-group">
                        <label for="notif-body">Message</label>
                        <textarea id="notif-body" class="form-control" rows="4" 
                                  placeholder="Notification message" required></textarea>
                    </div>
                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary">Send Notification</button>
                        <button type="button" class="btn btn-secondary" onclick="closeModal()">Cancel</button>
                    </div>
                </form>
            </div>
        `;
        
        if (typeof showModal === 'function') {
            showModal('Send Notification', content);
            
            // Bind form submit
            setTimeout(() => {
                const form = document.getElementById('notification-form');
                if (form) {
                    form.addEventListener('submit', async (e) => {
                        e.preventDefault();
                        const title = document.getElementById('notif-title').value;
                        const body = document.getElementById('notif-body').value;
                        
                        try {
                            const response = await window.UserRoute.notify(user.email, { title, body });
                            const data = await response.json();
                            
                            if (data.status === true) {
                                showMessage('success', 'Notification sent successfully');
                                if (typeof closeModal === 'function') closeModal();
                            } else {
                                throw new Error(data.message || 'Failed to send notification');
                            }
                        } catch (error) {
                            showMessage('error', error.message || 'Failed to send notification');
                        }
                    });
                }
            }, 100);
        }
    }

    /**
     * Handle password reset
     */
    function handlePasswordReset(user) {
        if (confirm(`Send password reset email to ${user.email}?`)) {
            showMessage('info', 'Password reset feature coming soon');
        }
    }

    /**
     * Handle delete user
     */
    function handleDeleteUser(user) {
        if (confirm(`Are you sure you want to delete user ${user.email}? This action cannot be undone.`)) {
            showMessage('warning', 'Delete user feature coming soon');
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
     */
    function formatDate(dateString) {
        if (!dateString) return 'N/A';
        try {
            const date = new Date(dateString);
            return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
        } catch (error) {
            return dateString;
        }
    }

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
     */
    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    /**
     * Generate skeleton rows for loading state
     */
    function generateSkeletonRows(count) {
        let html = '';
        for (let i = 0; i < count; i++) {
            html += `
                <tr class="skeleton-row">
                    <td><div class="skeleton skeleton-checkbox"></div></td>
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
        cell.colSpan = 7;
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
                throw new Error('No user loading method available');
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
        const refreshBtn = document.getElementById('refresh-users');
        const refreshIcon = refreshBtn?.querySelector('.refresh-icon');
        
        if (refreshIcon) {
            refreshIcon.classList.add('spinning');
        }
        
        // Invalidate cache
        if (window.CachedAPI) {
            window.CachedAPI.invalidateUsers();
        }
        
        await loadUsers(UsersState.currentPage, UsersState.pageSize);
        
        if (refreshIcon) {
            setTimeout(() => {
                refreshIcon.classList.remove('spinning');
            }, 500);
        }
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
        showUserDetails,
        initializeUsersModule
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
