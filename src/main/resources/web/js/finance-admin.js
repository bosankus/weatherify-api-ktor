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

    fetch('/finance/metrics', {
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
    const totalPaymentsEl = document.getElementById('total-payments');

    // Metrics are already in rupees from backend (converted from paise)
    const tr = Number(metrics.totalRevenue || 0);
    const mr = Number(metrics.monthlyRevenue || 0);
    const tpc = Number(metrics.totalPaymentsCount || 0);

    if (totalRevenueEl) totalRevenueEl.textContent = `₹${tr.toFixed(2)}`;
    if (monthlyRevenueEl) monthlyRevenueEl.textContent = `₹${mr.toFixed(2)}`;
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
        const loadingRow = typeof createLoadingTableRow === 'function'
            ? createLoadingTableRow(9, 'Loading...')
            : (() => {
                const row = document.createElement('tr');
                const cell = document.createElement('td');
                cell.colSpan = 9;
                cell.textContent = 'Loading...';
                cell.style.textAlign = 'center';
                cell.style.padding = '2rem';
                row.appendChild(cell);
                return row;
            })();
        tbody.replaceChildren(loadingRow);
    }

    fetch(`/finance/payments?${params.toString()}`, {
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
            const errorRow = typeof createErrorTableRow === 'function'
                ? createErrorTableRow(9, escapeHtml(error.message))
                : (() => {
                    const row = document.createElement('tr');
                    const cell = document.createElement('td');
                    cell.colSpan = 9;
                    cell.textContent = error.message;
                    cell.style.textAlign = 'center';
                    cell.style.padding = '2rem';
                    cell.style.color = 'var(--error-color, #ef4444)';
                    row.appendChild(cell);
                    return row;
                })();
            tbody.replaceChildren(errorRow);
        }
    });
}

/**
 * Render payment history table
 */
function renderPaymentHistory(payments) {
    const tbody = document.getElementById('payments-table-body');
    if (!tbody) return;
    if (typeof applyFinanceTableStyles === 'function') {
        applyFinanceTableStyles(tbody);
    }

    if (payments.length === 0) {
        const emptyRow = typeof createEmptyTableRow === 'function'
            ? createEmptyTableRow(9, 'No payments found')
            : (() => {
                const row = document.createElement('tr');
                const cell = document.createElement('td');
                cell.colSpan = 9;
                cell.textContent = 'No payments found';
                cell.style.textAlign = 'center';
                cell.style.padding = '2rem';
                cell.style.color = 'var(--text-secondary)';
                row.appendChild(cell);
                return row;
            })();
        tbody.replaceChildren(emptyRow);
        return;
    }

    const fragment = document.createDocumentFragment();

    payments.forEach(payment => {
        const row = document.createElement('tr');

        // Apply consistent row styling
        if (typeof styleTableRow === 'function') {
            styleTableRow(row);
        }

        // User Email - use consistent cell creation
        const emailCell = typeof createTableCell === 'function'
            ? createTableCell(payment.userEmail || 'N/A')
            : (() => {
                const cell = document.createElement('td');
                cell.textContent = payment.userEmail || 'N/A';
                return cell;
            })();
        row.appendChild(emailCell);

        // Amount (already in rupees from backend) - use consistent currency formatting
        const amt = Number(payment.amount || 0);
        const amountText = typeof formatTableCurrency === 'function'
            ? formatTableCurrency(amt, false) // false = already in rupees
            : `₹${amt.toFixed(2)}`;
        const amountCell = typeof createTableCell === 'function'
            ? createTableCell(amountText)
            : (() => {
                const cell = document.createElement('td');
                cell.textContent = amountText;
                return cell;
            })();
        row.appendChild(amountCell);

        // Currency
        const currencyCell = typeof createTableCell === 'function'
            ? createTableCell(payment.currency || 'INR')
            : (() => {
                const cell = document.createElement('td');
                cell.textContent = payment.currency || 'INR';
                return cell;
            })();
        row.appendChild(currencyCell);

        // Payment Method
        const methodCell = typeof createTableCell === 'function'
            ? createTableCell(payment.paymentMethod || 'N/A')
            : (() => {
                const cell = document.createElement('td');
                cell.textContent = payment.paymentMethod || 'N/A';
                return cell;
            })();
        row.appendChild(methodCell);

        // Status
        const statusCell = typeof createTableCell === 'function'
            ? createTableCell(payment.status || 'PENDING', { align: 'center' })
            : (() => {
                const cell = document.createElement('td');
                cell.textContent = payment.status || 'PENDING';
                cell.style.textAlign = 'center';
                return cell;
            })();
        row.appendChild(statusCell);

        // Transaction ID
        const txnCode = document.createElement('code');
        txnCode.textContent = payment.transactionId || 'N/A';
        txnCode.style.fontSize = '0.85rem';
        const txnCell = typeof createTableCell === 'function'
            ? createTableCell(txnCode)
            : (() => {
                const cell = document.createElement('td');
                cell.appendChild(txnCode);
                return cell;
            })();
        row.appendChild(txnCell);

        // Date - use consistent date formatting
        const dateText = typeof formatTableDate === 'function'
            ? formatTableDate(payment.createdAt)
            : formatDate(payment.createdAt);
        const dateCell = typeof createTableCell === 'function'
            ? createTableCell(dateText)
            : (() => {
                const cell = document.createElement('td');
                cell.textContent = dateText;
                cell.style.fontSize = '0.875rem';
                return cell;
            })();
        row.appendChild(dateCell);

        // Actions (Refund button or status)
        const actionsCell = document.createElement('td');
        actionsCell.className = 'payment-actions';
        row.appendChild(actionsCell);

        // Bill action
        const billCell = document.createElement('td');
        billCell.className = 'payment-bill';
        row.appendChild(billCell);

        fragment.appendChild(row);

        // Check refund status if amount > 0, then add action buttons
        const paymentAmount = Number(payment.amount || 0);
        if (paymentAmount > 0) {
            renderBillActionCell(billCell, payment, { state: 'checking' });
            checkAndDisplayRefundStatus(row, payment, actionsCell, billCell);
        } else {
            // For zero amount payments, add button immediately
            if (typeof addRefundButtonToPaymentRow === 'function') {
                addRefundButtonToPaymentRow(row, payment, null);
            }
            renderBillActionCell(billCell, payment, {
                state: 'disabled',
                reason: 'zero-amount',
                tooltip: 'Payment amount is zero'
            });
        }
    });

    tbody.innerHTML = '';
    tbody.appendChild(fragment);
}

/**
 * Check refund status from Razorpay and display badge if refunded
 */
function checkAndDisplayRefundStatus(row, payment, actionsCell, billCell) {
    const token = localStorage.getItem('jwt_token');
    const paymentId = payment.transactionId || payment.paymentId;
    
    if (!paymentId) {
        // No payment ID, add button immediately
        if (typeof addRefundButtonToPaymentRow === 'function') {
            addRefundButtonToPaymentRow(row, payment, null);
        }
        if (billCell) {
            renderBillActionCell(billCell, payment, {
                state: 'disabled',
                reason: 'missing-id',
                tooltip: 'Cannot generate bill'
            });
        }
        return;
    }

    fetch(`/refunds/payment/${paymentId}/check`, {
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
            const totalRefunded = refundSummary.totalRefunded || 0;
            const originalAmount = refundSummary.originalAmount || 0;
            const isFullyRefunded = refundSummary.isFullyRefunded || false;
            
            refundData = {
                totalRefunded,
                originalAmount,
                isFullyRefunded,
                refunds: refundSummary.refunds || [],
                summary: refundSummary
            };
            
            // Update payment object with refund info
            payment.refundedAmount = totalRefunded;
            if (isFullyRefunded) {
                payment.status = 'REFUNDED';
            }
        }
        
        // Now add action buttons with refund data
        if (typeof addRefundButtonToPaymentRow === 'function') {
            addRefundButtonToPaymentRow(row, payment, refundData);
        }
        if (billCell) {
            renderBillActionCell(billCell, payment, {
                state: 'ready',
                refundData
            });
        }
    })
    .catch(error => {
        // Silently fail - don't show errors for refund checks
        console.debug('Refund check failed for payment:', paymentId, error);
        
        // Still add action buttons even if refund check fails
        if (typeof addRefundButtonToPaymentRow === 'function') {
            addRefundButtonToPaymentRow(row, payment, null);
        }
        if (billCell) {
            renderBillActionCell(billCell, payment, {
                state: 'disabled',
                reason: 'refund-status-unavailable',
                tooltip: 'Cannot generate bill'
            });
        }
    });
}

function renderBillActionCell(cell, payment, options) {
    if (!cell) return;
    cell.innerHTML = '';

    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'bill-action-button';
    button.title = options.tooltip || 'Generate Bill';

    if (options.state === 'checking') {
        button.classList.add('is-disabled');
        button.disabled = true;
        button.title = 'Checking refund status...';
    }

    if (options.state === 'disabled') {
        button.classList.add('is-disabled');
        button.disabled = true;
        if (options.tooltip) {
            button.title = options.tooltip;
        }
    }

    if (options.state === 'ready') {
        const decision = getBillDecision(payment, options.refundData);
        if (decision.mode === 'disabled') {
            button.classList.add('is-disabled');
            button.disabled = true;
            button.title = decision.tooltip || 'Cannot generate bill';
            if (decision.reason === 'refund-overage' && !payment._billWarningShown) {
                showMessage('error', 'Refund amount exceeds original amount');
                payment._billWarningShown = true;
            }
        } else {
            button.addEventListener('click', () => {
                handleBillActionClick(payment, options.refundData, decision, button);
            });
        }
    }

    button.appendChild(createBillButtonIcon());
    cell.appendChild(button);
}

function createBillButtonIcon() {
    const icon = document.createElement('span');
    icon.className = 'material-icons';
    icon.textContent = 'receipt_long';
    return icon;
}

function getBillDecision(payment, refundData) {
    if (!refundData || typeof refundData.totalRefunded !== 'number' || typeof refundData.originalAmount !== 'number') {
        return {
            mode: 'disabled',
            reason: 'refund-status-unavailable',
            tooltip: 'Cannot generate bill'
        };
    }

    const status = String(payment.status || '').toUpperCase();
    const originalAmountPaise = refundData.originalAmount;
    const refundedPaise = refundData.totalRefunded;

    if (!Number.isFinite(originalAmountPaise) || originalAmountPaise <= 0) {
        return {
            mode: 'disabled',
            reason: 'zero-amount',
            tooltip: 'Payment amount is zero'
        };
    }

    if (!Number.isFinite(refundedPaise) || refundedPaise < 0) {
        return {
            mode: 'disabled',
            reason: 'invalid-refund',
            tooltip: 'Cannot generate bill'
        };
    }

    if (refundedPaise > originalAmountPaise) {
        return {
            mode: 'disabled',
            reason: 'refund-overage',
            tooltip: 'Refund amount exceeds original amount'
        };
    }

    if (refundedPaise === 0 || status === 'PAID' || status === 'SUCCESS' || status === 'VERIFIED') {
        return {
            mode: 'direct',
            billType: 'ORIGINAL_BILL'
        };
    }

    if (refundedPaise > 0 && refundedPaise < originalAmountPaise) {
        return {
            mode: 'options',
            options: [
                { label: 'Original Bill', billType: 'ORIGINAL_BILL' },
                { label: 'Refund Adjustment Bill', billType: 'REFUND_ADJUSTMENT_BILL' },
                { label: 'Net Amount Bill', billType: 'NET_AMOUNT_BILL' }
            ]
        };
    }

    if (refundedPaise === originalAmountPaise) {
        return {
            mode: 'direct',
            billType: 'REFUND_RECEIPT'
        };
    }

    return {
        mode: 'disabled',
        reason: 'unknown',
        tooltip: 'Cannot generate bill'
    };
}

function handleBillActionClick(payment, refundData, decision, button) {
    if (!button || button.dataset.loading === 'true') return;

    if (decision.mode === 'direct') {
        generateBillForPayment(payment, decision.billType, button);
        return;
    }

    if (decision.mode === 'options') {
        showBillTypeDialog(payment, refundData, decision.options, button);
    }
}

function showBillTypeDialog(payment, refundData, options, sourceButton) {
    if (typeof showFinancePanel !== 'function') {
        showMessage('error', 'Bill options panel not available');
        return;
    }

    const paymentId = payment.transactionId || payment.paymentId || payment.razorpayPaymentId || '';
    const panelId = `bill-type-panel-${paymentId.replace(/[^a-zA-Z0-9_-]/g, '_')}`;
    const optionButtons = options.map(option => {
        return `<button class="btn btn-secondary bill-type-option" data-bill-type="${option.billType}">${option.label}</button>`;
    }).join('');

    const content = `
        <div id="${panelId}" class="bill-type-panel">
            <div class="bill-type-list">
                ${optionButtons}
            </div>
            <button class="btn btn-secondary bill-type-cancel" type="button">Cancel</button>
        </div>
    `;

    showFinancePanel('Select Bill Type', content);

    setTimeout(() => {
        const panel = document.getElementById(panelId);
        if (!panel) return;
        const optionEls = panel.querySelectorAll('.bill-type-option');
        optionEls.forEach(optionEl => {
            optionEl.addEventListener('click', () => {
                const billType = optionEl.dataset.billType;
                if (billType) {
                    generateBillForPayment(payment, billType, sourceButton);
                    if (typeof closeFinancePanel === 'function') {
                        closeFinancePanel();
                    }
                }
            });
        });

        const cancelBtn = panel.querySelector('.bill-type-cancel');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', () => {
                if (typeof closeFinancePanel === 'function') {
                    closeFinancePanel();
                }
            });
        }
    }, 0);
}

function generateBillForPayment(payment, billType, button) {
    if (!payment) return;
    if (!navigator.onLine) {
        showMessage('error', 'No connection. Please try again once online.');
        return;
    }

    const paymentId = payment.transactionId || payment.paymentId || payment.razorpayPaymentId;
    if (!paymentId) {
        showMessage('error', 'Payment ID not found');
        return;
    }

    const token = localStorage.getItem('jwt_token');
    const previousContent = button.innerHTML;
    button.dataset.loading = 'true';
    button.classList.add('is-loading');
    button.disabled = true;
    button.innerHTML = '<span class="loading-spinner" style="width:16px;height:16px;border-width:2px;"></span>';

    fetch(`/finance/payments/${encodeURIComponent(paymentId)}/bill`, {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Accept': 'application/pdf',
            'Content-Type': 'application/json',
            'Authorization': token ? `Bearer ${token}` : ''
        },
        body: JSON.stringify({ billType })
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Failed to generate bill');
        }
        return response.blob();
    })
    .then(blob => {
        const today = new Date().toISOString().slice(0, 10);
        const typeLabel = getBillTypeFileLabel(billType);
        const filename = `Bill_${typeLabel}_${paymentId}_${today}.pdf`;
        const file = new File([blob], filename, { type: 'application/pdf' });

        const canShare = navigator.canShare && navigator.canShare({ files: [file] });
        if (canShare && window.confirm('Share the generated bill? Click Cancel to download.')) {
            return navigator.share({
                files: [file],
                title: filename,
                text: 'Generated bill'
            }).catch(() => {
                downloadFile(blob, filename);
            });
        }

        downloadFile(blob, filename);
        return null;
    })
    .then(() => {
        showMessage('success', 'Bill generated successfully');
    })
    .catch(error => {
        console.error('Bill generation failed:', error);
        showMessage('error', 'Bill generation failed');
        if (window.confirm('Bill generation failed. Retry?')) {
            generateBillForPayment(payment, billType, button);
        }
    })
    .finally(() => {
        button.dataset.loading = 'false';
        button.classList.remove('is-loading');
        button.disabled = false;
        button.innerHTML = previousContent;
    });
}

function downloadFile(blob, filename) {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
}

function getBillTypeFileLabel(billType) {
    switch (billType) {
        case 'ORIGINAL_BILL':
            return 'Original';
        case 'REFUND_ADJUSTMENT_BILL':
            return 'Refund_Adjustment';
        case 'NET_AMOUNT_BILL':
            return 'Net_Amount';
        case 'REFUND_RECEIPT':
            return 'Refund_Receipt';
        default:
            return 'Bill';
    }
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
    
    showFinancePanel('Refund Details', content);
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

}

// Use common utilities from admin-utils.js
const formatDate = window.formatDate || function(dateString) {
    if (!dateString) return 'N/A';
    try {
        const date = new Date(dateString);
        return date.toLocaleString();
    } catch (error) {
        return dateString;
    }
};

const escapeHtml = window.escapeHtml || function(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
};

console.log('Finance admin module loaded');
