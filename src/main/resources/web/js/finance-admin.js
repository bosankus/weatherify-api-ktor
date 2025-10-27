/**
 * Finance management functionality for admin dashboard
 * Handles payment history loading, financial metrics, and bill generation
 */

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
 * Load financial metrics
 */
function loadFinancialMetrics() {
    const token = localStorage.getItem('jwt_token');

    fetch('/admin/finance/metrics', {
        method: 'GET',
        credentials: 'include',
        headers: {
            'Accept': 'application/json',
            'Authorization': token ? `Bearer ${token}` : ''
        }
    })
    .then(response => {
        if (!response.ok) throw new Error('Failed to load financial metrics');
        return response.json();
    })
    .then(data => {
        if (data.status === true && data.data) {
            updateFinancialMetrics(data.data);
        }
    })
    .catch(error => {
        console.error('Error loading financial metrics:', error);
    });
}

/**
 * Update financial metrics in the UI
 */
function updateFinancialMetrics(metrics) {
    const totalRevenueEl = document.getElementById('total-revenue');
    const monthlyRevenueEl = document.getElementById('monthly-revenue');
    const activeSubRevenueEl = document.getElementById('active-sub-revenue');
    const totalPaymentsEl = document.getElementById('total-payments');

    // Metrics are already in rupees from backend (converted from paise)
    const tr = Number(metrics.totalRevenue || 0);
    const mr = Number(metrics.monthlyRevenue || 0);
    const asr = Number(metrics.activeSubscriptionsRevenue || 0);
    const tpc = Number(metrics.totalPaymentsCount || 0);

    if (totalRevenueEl) totalRevenueEl.textContent = `₹${tr.toFixed(2)}`;
    if (monthlyRevenueEl) monthlyRevenueEl.textContent = `₹${mr.toFixed(2)}`;
    if (activeSubRevenueEl) activeSubRevenueEl.textContent = `₹${asr.toFixed(2)}`;
    if (totalPaymentsEl) totalPaymentsEl.textContent = String(tpc);

    // Update refund metrics from top-level fields
    updateRefundMetrics(metrics);
}

/**
 * Update refund metrics in the UI
 */
function updateRefundMetrics(refundMetrics) {
    const totalRefundsEl = document.getElementById('total-refunds');
    const monthlyRefundsEl = document.getElementById('monthly-refunds');
    const refundRateEl = document.getElementById('refund-rate');
    const instantRefundsEl = document.getElementById('instant-refunds');
    const normalRefundsEl = document.getElementById('normal-refunds');
    const avgProcessingTimeEl = document.getElementById('avg-processing-time');

    const tr = Number(refundMetrics.totalRefunds || 0);
    const mr = Number(refundMetrics.monthlyRefunds || 0);
    const rr = Number(refundMetrics.refundRate || 0);
    const ic = Number(refundMetrics.instantRefundCount || 0);
    const nc = Number(refundMetrics.normalRefundCount || 0);
    const avg = Number(refundMetrics.averageProcessingTimeHours || 0);

    if (totalRefundsEl) totalRefundsEl.textContent = `₹${tr.toFixed(2)}`;
    if (monthlyRefundsEl) monthlyRefundsEl.textContent = `₹${mr.toFixed(2)}`;
    if (refundRateEl) refundRateEl.textContent = `${rr.toFixed(2)}%`;
    if (instantRefundsEl) instantRefundsEl.textContent = String(ic);
    if (normalRefundsEl) normalRefundsEl.textContent = String(nc);
    if (avgProcessingTimeEl) avgProcessingTimeEl.textContent = `${avg.toFixed(1)}h`;
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

    // Update state
    FinanceState.currentPage = page;
    FinanceState.pageSize = pageSize;
    FinanceState.statusFilter = status;
    FinanceState.startDate = startDate;
    FinanceState.endDate = endDate;

    const token = localStorage.getItem('jwt_token');
    const params = new URLSearchParams({
        page: page.toString(),
        pageSize: pageSize.toString()
    });

    if (status) params.append('status', status);
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);

    const tbody = document.getElementById('payments-table-body');
    if (tbody) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; padding: 2rem;">Loading...</td></tr>';
    }

    fetch(`/admin/finance/payments?${params.toString()}`, {
        method: 'GET',
        credentials: 'include',
        headers: {
            'Accept': 'application/json',
            'Authorization': token ? `Bearer ${token}` : ''
        }
    })
    .then(response => {
        if (!response.ok) throw new Error('Failed to load payment history');
        return response.json();
    })
    .then(data => {
        if (data.status === true && data.data) {
            renderPaymentHistory(data.data.payments || []);
            renderPaymentPagination(data.data.pagination || {});
        } else {
            throw new Error(data.message || 'Failed to load payment history');
        }
    })
    .catch(error => {
        console.error('Error loading payment history:', error);
        if (tbody) {
            tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; padding: 2rem; color: var(--error-color, #ef4444);">${escapeHtml(error.message)}</td></tr>`;
        }
    });
}

/**
 * Render payment history table
 */
function renderPaymentHistory(payments) {
    const tbody = document.getElementById('payments-table-body');
    if (!tbody) return;

    if (payments.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; padding: 2rem; color: var(--text-secondary);">No payments found</td></tr>';
        return;
    }

    const fragment = document.createDocumentFragment();

    payments.forEach(payment => {
        const row = document.createElement('tr');

        // User Email
        const emailCell = document.createElement('td');
        emailCell.textContent = payment.userEmail || 'N/A';
        row.appendChild(emailCell);

        // Amount (already in rupees from backend)
        const amountCell = document.createElement('td');
        const amt = Number(payment.amount || 0);
        amountCell.textContent = `₹${amt.toFixed(2)}`;
        row.appendChild(amountCell);

        // Currency
        const currencyCell = document.createElement('td');
        currencyCell.textContent = payment.currency || 'INR';
        row.appendChild(currencyCell);

        // Payment Method
        const methodCell = document.createElement('td');
        methodCell.textContent = payment.paymentMethod || 'N/A';
        row.appendChild(methodCell);

        // Status
        const statusCell = document.createElement('td');
        statusCell.textContent = payment.status || 'PENDING';
        row.appendChild(statusCell);

        // Transaction ID
        const txnCell = document.createElement('td');
        const txnCode = document.createElement('code');
        txnCode.textContent = payment.transactionId || 'N/A';
        txnCode.style.fontSize = '0.85rem';
        txnCell.appendChild(txnCode);
        row.appendChild(txnCell);

        // Date
        const dateCell = document.createElement('td');
        dateCell.textContent = formatDate(payment.createdAt);
        dateCell.style.fontSize = '0.875rem';
        row.appendChild(dateCell);

        // Actions (Refund button or status)
        const actionsCell = document.createElement('td');
        actionsCell.className = 'payment-actions';
        row.appendChild(actionsCell);

        fragment.appendChild(row);

        // Check refund status if amount > 0, then add action buttons
        const paymentAmount = Number(payment.amount || 0);
        if (paymentAmount > 0) {
            checkAndDisplayRefundStatus(row, payment, actionsCell);
        } else {
            // For zero amount payments, add button immediately
            if (typeof addRefundButtonToPaymentRow === 'function') {
                addRefundButtonToPaymentRow(row, payment, null);
            }
        }
    });

    tbody.innerHTML = '';
    tbody.appendChild(fragment);
}

/**
 * Check refund status from Razorpay and display badge if refunded
 */
function checkAndDisplayRefundStatus(row, payment, actionsCell) {
    const token = localStorage.getItem('jwt_token');
    const paymentId = payment.transactionId || payment.paymentId;
    
    if (!paymentId) {
        // No payment ID, add button immediately
        if (typeof addRefundButtonToPaymentRow === 'function') {
            addRefundButtonToPaymentRow(row, payment, null);
        }
        return;
    }

    fetch(`/admin/refunds/payment/${paymentId}/check`, {
        method: 'GET',
        credentials: 'include',
        headers: {
            'Accept': 'application/json',
            'Authorization': token ? `Bearer ${token}` : ''
        }
    })
    .then(response => {
        if (!response.ok) {
            // Silently fail - payment might not have refunds
            return null;
        }
        return response.json();
    })
    .then(data => {
        let refundData = null;
        
        if (data && data.status === true && data.data) {
            const refundSummary = data.data;
            
            // If payment has refunds, prepare refund data
            if (refundSummary.refunds && refundSummary.refunds.length > 0) {
                const totalRefunded = refundSummary.totalRefunded || 0;
                const originalAmount = refundSummary.originalAmount || 0;
                const isFullyRefunded = refundSummary.isFullyRefunded || false;
                
                refundData = {
                    totalRefunded,
                    originalAmount,
                    isFullyRefunded,
                    refunds: refundSummary.refunds,
                    summary: refundSummary
                };
                
                // Update payment object with refund info
                payment.refundedAmount = totalRefunded;
                if (isFullyRefunded) {
                    payment.status = 'REFUNDED';
                }
            }
        }
        
        // Now add action buttons with refund data
        if (typeof addRefundButtonToPaymentRow === 'function') {
            addRefundButtonToPaymentRow(row, payment, refundData);
        }
    })
    .catch(error => {
        // Silently fail - don't show errors for refund checks
        console.debug('Refund check failed for payment:', paymentId, error);
        
        // Still add action buttons even if refund check fails
        if (typeof addRefundButtonToPaymentRow === 'function') {
            addRefundButtonToPaymentRow(row, payment, null);
        }
    });
}

/**
 * Show refund details modal
 */
function showRefundDetailsModal(refundSummary) {
    if (typeof showModal !== 'function') {
        console.error('showModal function not available');
        return;
    }
    
    const originalAmount = (refundSummary.originalAmount || 0) / 100;
    const totalRefunded = (refundSummary.totalRefunded || 0) / 100;
    const remainingRefundable = (refundSummary.remainingRefundable || 0) / 100;
    
    let content = `
        <div class="refund-modal-content">
            <div class="refund-details">
                <h3 style="margin-top: 0; margin-bottom: 1rem; color: var(--heading-color);">Refund Summary</h3>
                <div class="detail-row">
                    <span class="label">Payment ID:</span>
                    <span class="value"><code>${escapeHtml(refundSummary.paymentId)}</code></span>
                </div>
                <div class="detail-row">
                    <span class="label">Original Amount:</span>
                    <span class="value">₹${originalAmount.toFixed(2)}</span>
                </div>
                <div class="detail-row">
                    <span class="label">Total Refunded:</span>
                    <span class="value" style="color: #ef4444; font-weight: 600;">₹${totalRefunded.toFixed(2)}</span>
                </div>
                <div class="detail-row">
                    <span class="label">Remaining Refundable:</span>
                    <span class="value">₹${remainingRefundable.toFixed(2)}</span>
                </div>
                <div class="detail-row">
                    <span class="label">Status:</span>
                    <span class="value">
                        <span class="badge ${refundSummary.isFullyRefunded ? 'badge-refunded' : 'badge-partial-refund'}">
                            ${refundSummary.isFullyRefunded ? 'FULLY REFUNDED' : 'PARTIALLY REFUNDED'}
                        </span>
                    </span>
                </div>
            </div>
            
            <div class="refunds-section">
                <h3 style="margin-top: 0; margin-bottom: 1rem; color: var(--heading-color);">Refund History</h3>
    `;
    
    if (refundSummary.refunds && refundSummary.refunds.length > 0) {
        refundSummary.refunds.forEach(refund => {
            const refundAmount = (refund.amount || 0);
            const statusClass = (refund.status || 'pending').toLowerCase();
            
            content += `
                <div class="refund-history-item">
                    <div class="refund-header">
                        <span class="refund-id">${escapeHtml(refund.refundId)}</span>
                        <span class="badge badge-${statusClass}">${escapeHtml(refund.status || 'PENDING')}</span>
                    </div>
                    <div class="detail-row">
                        <span class="label">Amount:</span>
                        <span class="value">₹${refundAmount.toFixed(2)}</span>
                    </div>
                    <div class="detail-row">
                        <span class="label">Speed:</span>
                        <span class="value">${escapeHtml(refund.speedProcessed || refund.speedRequested || 'N/A')}</span>
                    </div>
                    <div class="detail-row">
                        <span class="label">Processed By:</span>
                        <span class="value">${escapeHtml(refund.processedBy || 'N/A')}</span>
                    </div>
                    ${refund.reason ? `
                    <div class="detail-row">
                        <span class="label">Reason:</span>
                        <span class="value">${escapeHtml(refund.reason)}</span>
                    </div>
                    ` : ''}
                    <div class="detail-row">
                        <span class="label">Created:</span>
                        <span class="value">${formatDate(refund.createdAt)}</span>
                    </div>
                    ${refund.processedAt ? `
                    <div class="detail-row">
                        <span class="label">Processed:</span>
                        <span class="value">${formatDate(refund.processedAt)}</span>
                    </div>
                    ` : ''}
                </div>
            `;
        });
    } else {
        content += '<p style="color: var(--text-secondary);">No refunds found.</p>';
    }
    
    content += `
            </div>
        </div>
    `;
    
    showModal('Refund Details', content);
}

/**
 * Render payment pagination
 */
function renderPaymentPagination(pagination) {
    const container = document.getElementById('payments-pagination');
    if (!container) return;

    container.innerHTML = '';

    const currentPage = pagination.page || 1;
    const totalPages = pagination.totalPages || 1;

    if (totalPages <= 1) return;

    // Previous button
    const prevBtn = document.createElement('button');
    prevBtn.className = `btn btn-secondary ${currentPage === 1 ? 'disabled' : ''}`;
    prevBtn.textContent = 'Previous';
    prevBtn.disabled = currentPage === 1;
    prevBtn.addEventListener('click', () => {
        if (currentPage > 1) {
            loadPaymentHistory(currentPage - 1);
        }
    });
    container.appendChild(prevBtn);

    // Page numbers
    const maxButtons = 5;
    const startPage = Math.max(1, currentPage - Math.floor(maxButtons / 2));
    const endPage = Math.min(totalPages, startPage + maxButtons - 1);

    for (let i = startPage; i <= endPage; i++) {
        const pageBtn = document.createElement('button');
        pageBtn.className = `btn btn-secondary ${i === currentPage ? 'active' : ''}`;
        pageBtn.textContent = i.toString();
        pageBtn.addEventListener('click', () => {
            if (i !== currentPage) {
                loadPaymentHistory(i);
            }
        });
        container.appendChild(pageBtn);
    }

    // Next button
    const nextBtn = document.createElement('button');
    nextBtn.className = `btn btn-secondary ${currentPage === totalPages ? 'disabled' : ''}`;
    nextBtn.textContent = 'Next';
    nextBtn.disabled = currentPage === totalPages;
    nextBtn.addEventListener('click', () => {
        if (currentPage < totalPages) {
            loadPaymentHistory(currentPage + 1);
        }
    });
    container.appendChild(nextBtn);
}

/**
 * Initialize finance tab
 */
function initializeFinanceTab() {
    // Load financial metrics
    loadFinancialMetrics();

    // Load payment history
    loadPaymentHistory();

    // Bind filter controls
    const statusFilter = document.getElementById('payment-status-filter');
    const dateFrom = document.getElementById('payment-date-from');
    const dateTo = document.getElementById('payment-date-to');

    if (statusFilter) {
        statusFilter.addEventListener('change', function() {
            loadPaymentHistory(1, undefined, this.value);
        });
    }

    if (dateFrom) {
        dateFrom.addEventListener('change', function() {
            const startDate = this.value;
            const endDate = dateTo ? dateTo.value : '';
            if (startDate && endDate) {
                loadPaymentHistory(1, undefined, undefined, startDate, endDate);
            }
        });
    }

    if (dateTo) {
        dateTo.addEventListener('change', function() {
            const startDate = dateFrom ? dateFrom.value : '';
            const endDate = this.value;
            if (startDate && endDate) {
                loadPaymentHistory(1, undefined, undefined, startDate, endDate);
            }
        });
    }

    // Bind generate bill button
    const generateBillBtn = document.getElementById('generate-bill-btn');
    if (generateBillBtn && !generateBillBtn.dataset.bound) {
        generateBillBtn.addEventListener('click', function() {
            showMessage('info', 'Bill generation feature coming soon');
        });
        generateBillBtn.dataset.bound = 'true';
    }
}

// Helper function to format date
function formatDate(dateString) {
    if (!dateString) return 'N/A';
    try {
        const date = new Date(dateString);
        return date.toLocaleString();
    } catch (error) {
        return dateString;
    }
}

// Helper function to escape HTML
function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

console.log('Finance admin module loaded');
