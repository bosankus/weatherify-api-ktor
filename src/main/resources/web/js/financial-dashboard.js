/**
 * Financial Dashboard module for admin dashboard
 * Handles payment history loading, financial metrics, and bill generation
 */

(function() {
    // Finance state
    const FinanceState = {
        currentPage: 1,
        pageSize: 50,
        totalPages: 1,
        statusFilter: '',
        startDate: '',
        endDate: ''
    };

    /**
     * Build the DOM structure for the finance tab
     */
    function buildFinanceDOM() {
        // Build metrics cards
        const metricsContainer = document.getElementById('finance-metrics');
        if (metricsContainer && !document.getElementById('total-revenue')) {
            const primaryMetrics = [
                { id: 'total-revenue',     label: 'Total Revenue',      icon: 'account_balance',  accent: 'indigo' },
                { id: 'monthly-revenue',   label: 'Monthly Revenue',    icon: 'trending_up',      accent: 'indigo' },
                { id: 'active-sub-revenue',label: 'Active Sub Revenue', icon: 'subscriptions',    accent: 'violet' },
                { id: 'total-payments',    label: 'Total Payments',     icon: 'receipt_long',     accent: 'violet' },
                { id: 'total-refunds',     label: 'Total Refunds',      icon: 'money_off',        accent: 'amber'  },
                { id: 'monthly-refunds',   label: 'Monthly Refunds',    icon: 'calendar_month',   accent: 'amber'  }
            ];
            const secondaryMetrics = [
                { id: 'refund-rate',          label: 'Refund Rate',        icon: 'percent',          accent: 'rose'   },
                { id: 'instant-refunds',      label: 'Instant Refunds',    icon: 'bolt',             accent: 'teal'   },
                { id: 'normal-refunds',       label: 'Normal Refunds',     icon: 'hourglass_bottom', accent: 'teal'   },
                { id: 'avg-processing-time',  label: 'Avg Processing',     icon: 'timer',            accent: 'sky'    }
            ];

            metricsContainer.innerHTML =
                '<div class="fin-kpi-grid fin-kpi-grid--primary">' +
                primaryMetrics.map(function(m) {
                    return '<div class="fin-kpi-card fin-kpi-card--' + m.accent + '">' +
                        '<div class="fin-kpi-card__header">' +
                        '<span class="material-icons fin-kpi-card__icon">' + m.icon + '</span>' +
                        '<span class="fin-kpi-card__label">' + m.label + '</span>' +
                        '</div>' +
                        '<div class="fin-kpi-card__value" id="' + m.id + '">--</div>' +
                        '</div>';
                }).join('') +
                '</div>' +
                '<div class="fin-kpi-grid fin-kpi-grid--secondary">' +
                secondaryMetrics.map(function(m) {
                    return '<div class="fin-kpi-card fin-kpi-card--sm fin-kpi-card--' + m.accent + '">' +
                        '<div class="fin-kpi-card__header">' +
                        '<span class="material-icons fin-kpi-card__icon">' + m.icon + '</span>' +
                        '<span class="fin-kpi-card__label">' + m.label + '</span>' +
                        '</div>' +
                        '<div class="fin-kpi-card__value fin-kpi-card__value--sm" id="' + m.id + '">--</div>' +
                        '</div>';
                }).join('') +
                '</div>';
        }

        // Build filter controls
        const filtersContainer = document.getElementById('finance-filters');
        if (filtersContainer && !document.getElementById('payment-status-filter')) {
            filtersContainer.innerHTML =
                '<div class="fin-filter-bar">' +
                    '<div class="fin-filter-bar__group">' +
                        '<label class="fin-filter-bar__label" for="payment-status-filter">' +
                            '<span class="material-icons" style="font-size:15px;vertical-align:middle;">filter_list</span> Status' +
                        '</label>' +
                        '<select id="payment-status-filter" class="fin-filter-bar__control">' +
                            '<option value="">All Statuses</option>' +
                            '<option value="PENDING">Pending</option>' +
                            '<option value="VERIFIED">Verified</option>' +
                            '<option value="CAPTURED">Captured</option>' +
                            '<option value="SUCCESS">Success</option>' +
                            '<option value="FAILED">Failed</option>' +
                            '<option value="REFUNDED">Refunded</option>' +
                        '</select>' +
                    '</div>' +
                    '<div class="fin-filter-bar__group">' +
                        '<label class="fin-filter-bar__label" for="payment-date-from">' +
                            '<span class="material-icons" style="font-size:15px;vertical-align:middle;">date_range</span> From' +
                        '</label>' +
                        '<input type="date" id="payment-date-from" class="fin-filter-bar__control" />' +
                    '</div>' +
                    '<div class="fin-filter-bar__group">' +
                        '<label class="fin-filter-bar__label" for="payment-date-to">To</label>' +
                        '<input type="date" id="payment-date-to" class="fin-filter-bar__control" />' +
                    '</div>' +
                    '<button id="generate-bill-btn" class="btn btn-secondary fin-filter-bar__btn">' +
                        '<span class="material-icons" style="font-size:16px;vertical-align:middle;margin-right:4px;">receipt</span>Generate Bill' +
                    '</button>' +
                '</div>';
        }

        // Build payment table
        const tableContainer = document.getElementById('finance-table-container');
        if (tableContainer && !document.getElementById('payments-table-body')) {
            tableContainer.innerHTML =
                '<div class="finance-table-shell">' +
                    '<div class="finance-table-wrapper">' +
                        '<table class="finance-table">' +
                            '<thead><tr>' +
                                '<th>Email</th>' +
                                '<th>Amount</th>' +
                                '<th>Currency</th>' +
                                '<th>Method</th>' +
                                '<th>Status</th>' +
                                '<th>Transaction ID</th>' +
                                '<th>Date</th>' +
                                '<th style="text-align:center;">Actions</th>' +
                            '</tr></thead>' +
                            '<tbody id="payments-table-body">' +
                                '<tr><td colspan="8" style="text-align:center;padding:2.5rem;color:var(--text-secondary);">Loading payments...</td></tr>' +
                            '</tbody>' +
                        '</table>' +
                    '</div>' +
                '</div>';
        }

        // Set up pagination container
        const paginationContainer = document.getElementById('finance-pagination');
        if (paginationContainer && !document.getElementById('payments-pagination')) {
            paginationContainer.id = 'payments-pagination';
            paginationContainer.className = 'fin-pagination';
        }
    }

    /**
     * Load financial metrics
     */
    function loadFinancialMetrics() {
        var token = localStorage.getItem('jwt_token');

        fetch('/finance/metrics', {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? 'Bearer ' + token : ''
            }
        })
        .then(function(response) {
            if (!response.ok) throw new Error('Failed to load financial metrics');
            return response.json();
        })
        .then(function(data) {
            if (data.status === true && data.data) {
                updateFinancialMetrics(data.data);
            }
        })
        .catch(function(error) {
            console.error('Error loading financial metrics:', error);
        });
    }

    /**
     * Update financial metrics in the UI
     */
    function updateFinancialMetrics(metrics) {
        setMetricText('total-revenue', '\u20B9' + Number(metrics.totalRevenue || 0).toFixed(2));
        setMetricText('monthly-revenue', '\u20B9' + Number(metrics.monthlyRevenue || 0).toFixed(2));
        setMetricText('active-sub-revenue', '\u20B9' + Number(metrics.activeSubscriptionsRevenue || 0).toFixed(2));
        setMetricText('total-payments', String(Number(metrics.totalPaymentsCount || 0)));

        setMetricText('total-refunds', '\u20B9' + Number(metrics.totalRefunds || 0).toFixed(2));
        setMetricText('monthly-refunds', '\u20B9' + Number(metrics.monthlyRefunds || 0).toFixed(2));
        setMetricText('refund-rate', Number(metrics.refundRate || 0).toFixed(2) + '%');
        setMetricText('instant-refunds', String(Number(metrics.instantRefundCount || 0)));
        setMetricText('normal-refunds', String(Number(metrics.normalRefundCount || 0)));
        setMetricText('avg-processing-time', Number(metrics.averageProcessingTimeHours || 0).toFixed(1) + 'h');
    }

    function setMetricText(id, value) {
        var el = document.getElementById(id);
        if (el) el.textContent = value;
    }

    /**
     * Load payment history with pagination and filters
     */
    function loadPaymentHistory(page, pageSize, status, startDate, endDate) {
        page = page || FinanceState.currentPage;
        pageSize = pageSize || FinanceState.pageSize;
        status = status !== undefined ? status : FinanceState.statusFilter;
        startDate = startDate !== undefined ? startDate : FinanceState.startDate;
        endDate = endDate !== undefined ? endDate : FinanceState.endDate;

        FinanceState.currentPage = page;
        FinanceState.pageSize = pageSize;
        FinanceState.statusFilter = status;
        FinanceState.startDate = startDate;
        FinanceState.endDate = endDate;

        var token = localStorage.getItem('jwt_token');
        var params = new URLSearchParams({
            page: page.toString(),
            pageSize: pageSize.toString()
        });

        if (status) params.append('status', status);
        if (startDate) params.append('startDate', startDate);
        if (endDate) params.append('endDate', endDate);

        var tbody = document.getElementById('payments-table-body');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:2.5rem;color:var(--text-secondary);">Loading...</td></tr>';
        }

        fetch('/finance/payments?' + params.toString(), {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? 'Bearer ' + token : ''
            }
        })
        .then(function(response) {
            if (!response.ok) throw new Error('Failed to load payment history');
            return response.json();
        })
        .then(function(data) {
            if (data.status === true && data.data) {
                renderPaymentHistory(data.data.payments || []);
                renderPaymentPagination(data.data.pagination || {});
            } else {
                throw new Error(data.message || 'Failed to load payment history');
            }
        })
        .catch(function(error) {
            console.error('Error loading payment history:', error);
            if (tbody) {
                tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:2.5rem;color:var(--error-color,#ef4444);">' + escapeHtml(error.message) + '</td></tr>';
            }
        });
    }

    /**
     * Build a status badge element
     */
    function buildStatusBadge(status) {
        var s = (status || 'PENDING').toLowerCase();
        var classes = 'fin-status-pill';
        if (s === 'verified' || s === 'success' || s === 'captured') {
            classes += ' fin-status-pill--success';
        } else if (s === 'pending') {
            classes += ' fin-status-pill--pending';
        } else if (s === 'failed') {
            classes += ' fin-status-pill--failed';
        } else if (s === 'refunded') {
            classes += ' fin-status-pill--refunded';
        } else {
            classes += ' fin-status-pill--neutral';
        }
        return '<span class="' + classes + '">' + escapeHtml(status || 'PENDING') + '</span>';
    }

    /**
     * Render payment history table
     */
    function renderPaymentHistory(payments) {
        var tbody = document.getElementById('payments-table-body');
        if (!tbody) return;

        if (payments.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:2.5rem;color:var(--text-secondary);">No payments found</td></tr>';
            return;
        }

        var fragment = document.createDocumentFragment();

        payments.forEach(function(payment) {
            var row = document.createElement('tr');

            var emailCell = document.createElement('td');
            emailCell.innerHTML = '<span class="fin-cell-email">' + escapeHtml(payment.userEmail || 'N/A') + '</span>';
            row.appendChild(emailCell);

            var amountCell = document.createElement('td');
            amountCell.innerHTML = '<span class="fin-cell-amount">\u20B9' + Number(payment.amount || 0).toFixed(2) + '</span>';
            row.appendChild(amountCell);

            var currencyCell = document.createElement('td');
            currencyCell.textContent = payment.currency || 'INR';
            currencyCell.style.color = 'var(--text-secondary)';
            row.appendChild(currencyCell);

            var methodCell = document.createElement('td');
            methodCell.textContent = payment.paymentMethod || 'N/A';
            row.appendChild(methodCell);

            var statusCell = document.createElement('td');
            statusCell.innerHTML = buildStatusBadge(payment.status);
            row.appendChild(statusCell);

            var txnCell = document.createElement('td');
            txnCell.innerHTML = '<code class="fin-cell-txn">' + escapeHtml(payment.transactionId || 'N/A') + '</code>';
            row.appendChild(txnCell);

            var dateCell = document.createElement('td');
            dateCell.textContent = formatDate(payment.createdAt);
            dateCell.style.fontSize = '0.8rem';
            dateCell.style.color = 'var(--text-secondary)';
            row.appendChild(dateCell);

            var actionsCell = document.createElement('td');
            actionsCell.className = 'payment-actions';
            // Always render a Details button immediately so the column is never empty
            actionsCell.innerHTML = buildInitialActionButtons(payment);
            row.appendChild(actionsCell);

            fragment.appendChild(row);

            // Async: check refund status and update actions cell
            if (Number(payment.amount || 0) > 0) {
                checkAndDisplayRefundStatus(row, payment, actionsCell);
            }
        });

        tbody.innerHTML = '';
        tbody.appendChild(fragment);

        // Bind click handlers for initial Details buttons
        bindInitialActionButtons(tbody, payments);
    }

    /**
     * Build initial action buttons HTML (rendered immediately, before async refund check)
     */
    function buildInitialActionButtons(payment) {
        var status = (payment.status || '').toLowerCase();
        var amount = Number(payment.amount || 0);
        if (status === 'verified' || status === 'success' || status === 'captured') {
            return '<div class="payment-actions-group">' +
                '<button class="btn-details btn-sm fin-action-details" data-txn="' + escapeHtml(payment.transactionId || '') + '" title="View details">Details</button>' +
                '</div>';
        }
        return '<div class="payment-actions-group">' +
            '<button class="btn-details btn-sm fin-action-details" data-txn="' + escapeHtml(payment.transactionId || '') + '" title="View details">Details</button>' +
            '</div>';
    }

    /**
     * Bind click handlers for initial Details buttons in the table
     */
    function bindInitialActionButtons(tbody, payments) {
        var buttons = tbody.querySelectorAll('.fin-action-details');
        buttons.forEach(function(btn) {
            btn.addEventListener('click', function() {
                var txn = this.getAttribute('data-txn');
                var payment = payments.find(function(p) { return (p.transactionId || '') === txn; });
                if (payment && typeof showPaymentDetailsModal === 'function') {
                    showPaymentDetailsModal(payment);
                }
            });
        });
    }

    /**
     * Check refund status from Razorpay and update the actions cell
     */
    function checkAndDisplayRefundStatus(row, payment, actionsCell) {
        var token = localStorage.getItem('jwt_token');
        var paymentId = payment.transactionId || payment.paymentId;

        if (!paymentId) {
            if (typeof addRefundButtonToPaymentRow === 'function') {
                addRefundButtonToPaymentRow(row, payment, null);
            }
            return;
        }

        fetch('/refunds/payment/' + paymentId + '/check', {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? 'Bearer ' + token : ''
            }
        })
        .then(function(response) {
            if (!response.ok) return null;
            return response.json();
        })
        .then(function(data) {
            var refundData = null;

            if (data && data.status === true && data.data) {
                var refundSummary = data.data;

                if (refundSummary.refunds && refundSummary.refunds.length > 0) {
                    refundData = {
                        totalRefunded: refundSummary.totalRefunded || 0,
                        originalAmount: refundSummary.originalAmount || 0,
                        isFullyRefunded: refundSummary.isFullyRefunded || false,
                        refunds: refundSummary.refunds,
                        summary: refundSummary
                    };

                    payment.refundedAmount = refundData.totalRefunded;
                    if (refundData.isFullyRefunded) {
                        payment.status = 'REFUNDED';
                    }
                }
            }

            if (typeof addRefundButtonToPaymentRow === 'function') {
                addRefundButtonToPaymentRow(row, payment, refundData);
            }
        })
        .catch(function(error) {
            console.debug('Refund check failed for payment:', paymentId, error);
            if (typeof addRefundButtonToPaymentRow === 'function') {
                addRefundButtonToPaymentRow(row, payment, null);
            }
        });
    }

    /**
     * Render payment pagination
     */
    function renderPaymentPagination(pagination) {
        var container = document.getElementById('payments-pagination');
        if (!container) return;

        container.innerHTML = '';

        var currentPage = pagination.page || 1;
        var totalPages = pagination.totalPages || 1;

        if (totalPages <= 1) return;

        var prevBtn = document.createElement('button');
        prevBtn.className = 'btn btn-secondary fin-pagination__btn' + (currentPage === 1 ? ' disabled' : '');
        prevBtn.innerHTML = '<span class="material-icons" style="font-size:16px;">chevron_left</span>';
        prevBtn.disabled = currentPage === 1;
        prevBtn.title = 'Previous page';
        prevBtn.addEventListener('click', function() {
            if (currentPage > 1) loadPaymentHistory(currentPage - 1);
        });
        container.appendChild(prevBtn);

        var pageInfo = document.createElement('span');
        pageInfo.className = 'fin-pagination__info';
        pageInfo.textContent = 'Page ' + currentPage + ' of ' + totalPages;
        container.appendChild(pageInfo);

        var maxButtons = 5;
        var startPage = Math.max(1, currentPage - Math.floor(maxButtons / 2));
        var endPage = Math.min(totalPages, startPage + maxButtons - 1);

        for (var i = startPage; i <= endPage; i++) {
            (function(pageNum) {
                var pageBtn = document.createElement('button');
                pageBtn.className = 'btn btn-secondary fin-pagination__btn' + (pageNum === currentPage ? ' active' : '');
                pageBtn.textContent = pageNum.toString();
                pageBtn.addEventListener('click', function() {
                    if (pageNum !== currentPage) loadPaymentHistory(pageNum);
                });
                container.appendChild(pageBtn);
            })(i);
        }

        var nextBtn = document.createElement('button');
        nextBtn.className = 'btn btn-secondary fin-pagination__btn' + (currentPage === totalPages ? ' disabled' : '');
        nextBtn.innerHTML = '<span class="material-icons" style="font-size:16px;">chevron_right</span>';
        nextBtn.disabled = currentPage === totalPages;
        nextBtn.title = 'Next page';
        nextBtn.addEventListener('click', function() {
            if (currentPage < totalPages) loadPaymentHistory(currentPage + 1);
        });
        container.appendChild(nextBtn);
    }

    function formatDate(dateString) {
        if (!dateString) return 'N/A';
        try {
            return new Date(dateString).toLocaleString();
        } catch (error) {
            return dateString;
        }
    }

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    /**
     * Bind filter controls for finance tab
     */
    function bindFilterControls() {
        var statusFilter = document.getElementById('payment-status-filter');
        var dateFrom = document.getElementById('payment-date-from');
        var dateTo = document.getElementById('payment-date-to');

        if (statusFilter && !statusFilter.dataset.bound) {
            statusFilter.addEventListener('change', function() {
                loadPaymentHistory(1, undefined, this.value);
            });
            statusFilter.dataset.bound = 'true';
        }

        if (dateFrom && !dateFrom.dataset.bound) {
            dateFrom.addEventListener('change', function() {
                var startDate = this.value;
                var endDate = dateTo ? dateTo.value : '';
                if (startDate && endDate) {
                    loadPaymentHistory(1, undefined, undefined, startDate, endDate);
                }
            });
            dateFrom.dataset.bound = 'true';
        }

        if (dateTo && !dateTo.dataset.bound) {
            dateTo.addEventListener('change', function() {
                var startDate = dateFrom ? dateFrom.value : '';
                var endDate = this.value;
                if (startDate && endDate) {
                    loadPaymentHistory(1, undefined, undefined, startDate, endDate);
                }
            });
            dateTo.dataset.bound = 'true';
        }

        var generateBillBtn = document.getElementById('generate-bill-btn');
        if (generateBillBtn && !generateBillBtn.dataset.bound) {
            generateBillBtn.addEventListener('click', function() {
                if (typeof showMessage === 'function') {
                    showMessage('info', 'Bill generation feature coming soon');
                }
            });
            generateBillBtn.dataset.bound = 'true';
        }
    }

    // Register the module on window
    window.FinancialDashboard = {
        initialize: async function() {
            console.log('[FinancialDashboard] Initializing...');
            buildFinanceDOM();
            loadFinancialMetrics();
            loadPaymentHistory();
            bindFilterControls();
            console.log('[FinancialDashboard] Initialized');
        },
        refresh: function() {
            loadFinancialMetrics();
            loadPaymentHistory();
        },
        loadPaymentHistory: loadPaymentHistory
    };

    console.log('[FinancialDashboard] Module loaded');
})();
