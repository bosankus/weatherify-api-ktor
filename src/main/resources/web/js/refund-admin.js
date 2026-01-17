/**
 * Refund management functionality for admin dashboard
 * Handles refund initiation, viewing refund history, and refund analytics
 */

// Refund API wrapper
window.RefundAPI = window.RefundAPI || {
    /**
     * Initiate a refund for a payment
     * @param {string} paymentId - The payment ID to refund
     * @param {number|null} amount - Optional partial refund amount
     * @param {string} reason - Reason for refund
     * @param {string} notes - Additional notes
     * @returns {Promise} API response
     */
    initiateRefund(paymentId, amount, reason, notes) {
        const token = localStorage.getItem('jwt_token');
        return fetch('/refunds/initiate', {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            },
            body: JSON.stringify({
                paymentId,
                amount,
                reason: reason || 'CUSTOMER_REQUEST',
                notes: notes || ''
            })
        }).then(response => {
            if (!response.ok) {
                if (response.status === 403) {
                    throw new Error('You do not have permission to initiate refunds');
                }
                throw new Error('Failed to initiate refund');
            }
            return response.json();
        });
    },

    /**
     * Get refund details by refund ID
     * @param {string} refundId - The refund ID
     * @returns {Promise} API response
     */
    getRefund(refundId) {
        const token = localStorage.getItem('jwt_token');
        return fetch(`/refunds/${encodeURIComponent(refundId)}`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            }
        }).then(response => {
            if (!response.ok) throw new Error('Failed to fetch refund details');
            return response.json();
        });
    },

    /**
     * Get all refunds for a payment
     * @param {string} paymentId - The payment ID
     * @returns {Promise} API response
     */
    getRefundsForPayment(paymentId) {
        const token = localStorage.getItem('jwt_token');
        return fetch(`/refunds/payment/${encodeURIComponent(paymentId)}`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            }
        }).then(response => {
            if (!response.ok) throw new Error('Failed to fetch payment refunds');
            return response.json();
        });
    },

    /**
     * Get refund history with pagination
     * @param {number} page - Page number
     * @param {number} pageSize - Page size
     * @param {string|null} status - Filter by status
     * @param {string|null} startDate - Start date filter
     * @param {string|null} endDate - End date filter
     * @returns {Promise} API response
     */
    getRefundHistory(page, pageSize, status, startDate, endDate) {
        const token = localStorage.getItem('jwt_token');
        const params = new URLSearchParams({
            page: page.toString(),
            pageSize: pageSize.toString()
        });
        if (status) params.append('status', status);
        if (startDate) params.append('startDate', startDate);
        if (endDate) params.append('endDate', endDate);

        return fetch(`/refunds/history?${params.toString()}`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            }
        }).then(response => {
            if (!response.ok) throw new Error('Failed to fetch refund history');
            return response.json();
        });
    },

    /**
     * Get refund metrics
     * @returns {Promise} API response
     */
    getRefundMetrics() {
        const token = localStorage.getItem('jwt_token');
        return fetch('/refunds/metrics', {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            }
        }).then(response => {
            if (!response.ok) throw new Error('Failed to fetch refund metrics');
            return response.json();
        });
    },

    /**
     * Check refund status from Razorpay (refresh status)
     * @param {string} paymentId - The payment ID
     * @returns {Promise} API response
     */
    checkRefundStatus(paymentId) {
        const token = localStorage.getItem('jwt_token');
        return fetch(`/refunds/payment/${encodeURIComponent(paymentId)}/check`, {
            method: 'GET',
            credentials: 'include',
            headers: {
                'Accept': 'application/json',
                'Authorization': token ? `Bearer ${token}` : ''
            }
        }).then(response => {
            if (!response.ok) throw new Error('Failed to check refund status');
            return response.json();
        });
    }
};

function getRefundPanelLoadingContent(message) {
    if (typeof getFinancePanelLoadingContent === 'function') {
        return getFinancePanelLoadingContent(message);
    }
    const safeMessage = escapeHtml(message || 'Loading...');
    return `
        <div class="modal-loading">
            <div class="loading-spinner"></div>
            <div class="loading-text">${safeMessage}</div>
        </div>
    `;
}

/**
 * Show refund initiation modal
 * @param {Object} payment - Payment object containing payment details
 */
function showRefundModal(payment) {
    if (!payment || !payment.razorpayPaymentId) {
        showMessage('error', 'Invalid payment data');
        return;
    }

    // Check if payment is already fully refunded
    if (payment.status === 'REFUNDED') {
        showMessage('info', 'This payment has already been fully refunded');
        return;
    }

    // Show loading modal while fetching fresh refund data
    const loadingContent = getRefundPanelLoadingContent('Loading refund information...');
    showFinancePanel('Initiate Refund', loadingContent);

    // Fetch fresh refund data from API to get accurate refundable amount
    window.RefundAPI.getRefundsForPayment(payment.razorpayPaymentId)
        .then(response => {
            if (response.status === true && response.data) {
                const summary = response.data;
                const amount = summary.originalAmount || payment.amount || 0;
                const refundedAmount = summary.totalRefunded || 0;
                const refundableAmount = summary.remainingRefundable || (amount - refundedAmount);
                const currency = payment.currency || 'INR';

                if (refundableAmount <= 0) {
                    if (typeof closeFinancePanel === 'function') closeFinancePanel();
                    showMessage('info', 'No refundable amount remaining for this payment');
                    return;
                }

                // Show the actual refund form with fresh data
                showRefundFormModal(payment, amount, refundedAmount, refundableAmount, currency);
            } else {
                throw new Error(response.message || 'Failed to fetch refund data');
            }
        })
        .catch(error => {
            console.error('Error fetching refund data:', error);
            if (typeof closeFinancePanel === 'function') closeFinancePanel();
            showMessage('error', 'Failed to load refund information. Please try again.');
        });
}

/**
 * Show the actual refund form modal with calculated amounts
 * @param {Object} payment - Payment object
 * @param {number} amount - Original payment amount in paise
 * @param {number} refundedAmount - Already refunded amount in paise
 * @param {number} refundableAmount - Remaining refundable amount in paise
 * @param {string} currency - Currency code
 */
function showRefundFormModal(payment, amount, refundedAmount, refundableAmount, currency) {
    // Convert paise to rupees for display
    const amountInRupees = (amount / 100).toFixed(2);
    const refundedInRupees = (refundedAmount / 100).toFixed(2);
    const refundableInRupees = (refundableAmount / 100).toFixed(2);

    const modalContent = `
        <div class="payment-details-modal refund-initiate-modal">
            <div class="finance-hero">
                <div class="finance-kv__label">Refundable balance</div>
                <div class="finance-hero-value">₹${refundableInRupees}</div>
                <div class="finance-kv__label">Initiate refund for this payment</div>
            </div>

            <div class="finance-card">
                <div class="finance-kv">
                    <span class="finance-kv__label">Original amount</span>
                    <span class="finance-kv__value">₹${amountInRupees}</span>
                </div>
                <div class="finance-kv">
                    <span class="finance-kv__label">Already refunded</span>
                    <span class="finance-kv__value">₹${refundedInRupees}</span>
                </div>
                <div class="finance-kv">
                    <span class="finance-kv__label">Remaining refundable</span>
                    <span class="finance-kv__value">₹${refundableInRupees}</span>
                </div>
                <div class="finance-kv">
                    <span class="finance-kv__label">Currency</span>
                    <span class="finance-kv__value">${escapeHtml(currency || 'INR')}</span>
                </div>
                <div class="finance-kv">
                    <span class="finance-kv__label">Payment ID</span>
                    <span class="finance-kv__value"><span class="finance-code">${escapeHtml(payment.razorpayPaymentId)}</span></span>
                </div>
                <div class="finance-kv">
                    <span class="finance-kv__label">User</span>
                    <span class="finance-kv__value">${escapeHtml(payment.userEmail || 'N/A')}</span>
                </div>
            </div>

            <div class="finance-card">
                <form id="refund-form" class="modal-form">
                    <div class="modal-form-group">
                        <label for="refund-type" class="modal-form-label required">Refund Type</label>
                        <select id="refund-type" name="refundType" class="modal-form-select" required>
                            <option value="full">Full refund (₹${refundableInRupees})</option>
                            <option value="partial">Partial refund</option>
                        </select>
                        <span class="modal-form-helper">Full refunds apply the remaining refundable amount.</span>
                    </div>

                    <div class="modal-form-group" id="partial-amount-group" style="display: none;">
                        <label for="refund-amount" class="modal-form-label required">Amount (₹)</label>
                        <input 
                            type="number" 
                            id="refund-amount" 
                            name="amount" 
                            class="modal-form-input"
                            min="0.01" 
                            max="${refundableInRupees}"
                            step="0.01"
                            placeholder="Enter amount"
                        />
                        <span class="modal-form-helper">Max refundable: ₹${refundableInRupees}</span>
                    </div>

                    <div class="modal-form-group">
                        <label for="refund-reason" class="modal-form-label required">Reason</label>
                        <select id="refund-reason" name="reason" class="modal-form-select" required>
                            <option value="CUSTOMER_REQUEST">Customer Request</option>
                            <option value="DUPLICATE_PAYMENT">Duplicate Payment</option>
                            <option value="FRAUDULENT">Fraudulent</option>
                            <option value="SERVICE_NOT_PROVIDED">Service Not Provided</option>
                            <option value="OTHER">Other</option>
                        </select>
                    </div>

                    <div class="modal-form-group">
                        <label for="refund-notes" class="modal-form-label">Notes (Optional)</label>
                        <textarea 
                            id="refund-notes" 
                            name="notes" 
                            class="modal-form-textarea"
                            rows="3"
                            placeholder="Add internal notes for this refund..."
                        ></textarea>
                    </div>
                </form>
            </div>
        </div>
    `;

    const modalFooter = `
        <button type="button" class="modal-btn modal-btn-secondary" onclick="closeFinancePanel()">Cancel</button>
        <button type="submit" form="refund-form" class="modal-btn modal-btn-primary" id="refund-submit-btn">
            <span id="refund-submit-text">Initiate Refund</span>
            <span id="refund-submit-spinner" class="loading-spinner" style="display: none; width: 1rem; height: 1rem; margin-left: 0.5rem;"></span>
        </button>
    `;

    showFinancePanel('Initiate Refund', modalContent, { footer: modalFooter });

    // Bind form events
    setTimeout(() => {
        const form = document.getElementById('refund-form');
        const submitBtn = document.getElementById('refund-submit-btn');
        const submitText = document.getElementById('refund-submit-text');
        const submitSpinner = document.getElementById('refund-submit-spinner');
        const refundTypeSelect = document.getElementById('refund-type');
        const partialAmountGroup = document.getElementById('partial-amount-group');
        const refundAmountInput = document.getElementById('refund-amount');

        // Toggle partial amount input based on refund type
        if (refundTypeSelect) {
            refundTypeSelect.addEventListener('change', function() {
                if (this.value === 'partial') {
                    partialAmountGroup.style.display = 'block';
                    refundAmountInput.required = true;
                } else {
                    partialAmountGroup.style.display = 'none';
                    refundAmountInput.required = false;
                    refundAmountInput.value = '';
                }
            });
        }

        // Handle form submission
        if (form) {
            form.addEventListener('submit', function (e) {
                e.preventDefault();

                const refundType = document.getElementById('refund-type').value;
                const reason = document.getElementById('refund-reason').value;
                const notes = document.getElementById('refund-notes').value;

                // Determine refund amount based on type
                let refundAmount;
                if (refundType === 'partial') {
                    const amountInRupees = parseFloat(refundAmountInput.value);
                    
                    // Validate partial amount
                    if (isNaN(amountInRupees) || amountInRupees <= 0) {
                        showMessage('error', 'Please enter a valid refund amount');
                        return;
                    }
                    
                    // Convert rupees to paise
                    refundAmount = Math.round(amountInRupees * 100);
                    
                    if (refundAmount > refundableAmount) {
                        showMessage('error', `Refund amount cannot exceed ₹${(refundableAmount / 100).toFixed(2)}`);
                        return;
                    }
                } else {
                    // Full refund
                    refundAmount = refundableAmount;
                }

                // Disable submit button and show spinner
                submitBtn.disabled = true;
                submitText.style.display = 'none';
                submitSpinner.style.display = 'inline-block';

                // Call API to initiate refund
                window.RefundAPI.initiateRefund(payment.razorpayPaymentId, refundAmount, reason, notes)
                    .then(response => {
                        if (response.status === true) {
                            showMessage('success', response.message || 'Refund initiated successfully');
            if (typeof closeFinancePanel === 'function') closeFinancePanel();
                            
                            // Update only the specific payment row instead of reloading entire list
                            updatePaymentRowAfterRefund(payment.razorpayPaymentId);
                            
                            // Reload financial metrics to reflect refund in total revenue
                            if (typeof loadFinancialMetrics === 'function') {
                                loadFinancialMetrics();
                            }
                        } else {
                            throw new Error(response.message || 'Failed to initiate refund');
                        }
                    })
                    .catch(error => {
                        console.error('Refund initiation error:', error);
                        showMessage('error', error.message || 'Failed to initiate refund');
                    })
                    .finally(() => {
                        submitBtn.disabled = false;
                        submitText.style.display = 'inline';
                        submitSpinner.style.display = 'none';
                    });
            });
        }
    }, 100);
}

/**
 * Update a specific payment row after refund without reloading entire list
 * @param {string} paymentId - The payment ID that was refunded
 */
function updatePaymentRowAfterRefund(paymentId) {
    if (!paymentId) return;

    // Find the payment row in the table
    const tbody = document.getElementById('payments-table-body');
    if (!tbody) return;

    // Find the row with this payment ID
    const rows = tbody.querySelectorAll('tr');
    let targetRow = null;
    let payment = null;

    for (const row of rows) {
        const txnCell = row.querySelector('td:nth-child(6) code'); // Transaction ID is 6th column
        if (txnCell && txnCell.textContent === paymentId) {
            targetRow = row;
            
            // Extract payment data from the row
            payment = {
                userEmail: row.querySelector('td:nth-child(1)')?.textContent || 'N/A',
                amount: parseFloat(row.querySelector('td:nth-child(2)')?.textContent.replace('₹', '') || '0') * 100, // Convert to paise
                currency: row.querySelector('td:nth-child(3)')?.textContent || 'INR',
                paymentMethod: row.querySelector('td:nth-child(4)')?.textContent || 'N/A',
                status: row.querySelector('td:nth-child(5)')?.textContent || 'PENDING',
                transactionId: paymentId,
                razorpayPaymentId: paymentId,
                createdAt: row.querySelector('td:nth-child(7)')?.textContent || ''
            };
            break;
        }
    }

    if (!targetRow || !payment) {
        console.debug('Payment row not found, falling back to full reload');
        if (typeof loadPaymentHistory === 'function') {
            loadPaymentHistory();
        }
        return;
    }

    // Fetch fresh refund data for this payment
    window.RefundAPI.getRefundsForPayment(paymentId)
        .then(response => {
            if (response.status === true && response.data) {
                const refundSummary = response.data;
                const totalRefunded = refundSummary.totalRefunded || 0;
                const originalAmount = refundSummary.originalAmount || 0;
                const isFullyRefunded = refundSummary.isFullyRefunded || false;

                // Update payment object with refund info
                payment.refundedAmount = totalRefunded;
                if (isFullyRefunded) {
                    payment.status = 'REFUNDED';
                    // Update status cell
                    const statusCell = targetRow.querySelector('td:nth-child(5)');
                    if (statusCell) {
                        statusCell.textContent = 'REFUNDED';
                    }
                }

                // Prepare refund data
                const refundData = {
                    totalRefunded,
                    originalAmount,
                    isFullyRefunded,
                    refunds: refundSummary.refunds,
                    summary: refundSummary
                };

                // Update the actions cell with new refund button/badge
                const actionsCell = targetRow.querySelector('.payment-actions');
                if (actionsCell && typeof addRefundButtonToPaymentRow === 'function') {
                    // Clear existing content
                    actionsCell.innerHTML = '';
                    // Re-render the action buttons with updated refund data
                    addRefundButtonToPaymentRow(targetRow, payment, refundData);
                }
            }
        })
        .catch(error => {
            console.error('Error updating payment row:', error);
            // Fallback to full reload on error
            if (typeof loadPaymentHistory === 'function') {
                loadPaymentHistory();
            }
        });
}

/**
 * Show refund history modal for a payment
 * @param {string} paymentId - The payment ID
 */
function showRefundHistoryModal(paymentId) {
    if (!paymentId) {
        showMessage('error', 'Invalid payment ID');
        return;
    }

    const loadingContent = getRefundPanelLoadingContent('Loading refund history...');
    showFinancePanel('Refund History', loadingContent);

    window.RefundAPI.getRefundsForPayment(paymentId)
        .then(response => {
            if (response.status === true && response.data) {
                const data = response.data;
                const refunds = data.refunds || [];
                const summary = data.summary || {};
                const totalAmount = (summary.totalAmount || summary.originalAmount || 0) / 100;
                const totalRefunded = (summary.totalRefunded || 0) / 100;
                const refundableAmount = (summary.refundableAmount || summary.remainingRefundable || 0) / 100;

                let refundsHtml = '';
                if (refunds.length === 0) {
                    refundsHtml = '<div class="refund-details-empty">No refunds found for this payment</div>';
                } else {
                    refundsHtml = refunds.map(refund => {
                        const statusClass = refund.status === 'PROCESSED' ? 'refund-status--success' :
                            refund.status === 'FAILED' ? 'refund-status--error' : 'refund-status--warning';
                        const refundAmount = ((refund.amount || 0) / 100).toFixed(2);
                        return `
                            <div class="refund-history-card refund-card">
                                <div class="refund-history-header">
                                    <div class="refund-history-meta">
                                        <div class="refund-eyebrow">Refund</div>
                                        <div class="refund-history-id">${escapeHtml(refund.refundId || 'N/A')}</div>
                                    </div>
                                    <span class="refund-status-pill ${statusClass}">${escapeHtml(refund.status || 'UNKNOWN')}</span>
                                </div>
                                <div class="refund-history-grid">
                                    <div>
                                        <div class="refund-kv-label">Amount</div>
                                        <div class="refund-kv-value refund-amount">₹${refundAmount}</div>
                                    </div>
                                    <div>
                                        <div class="refund-kv-label">Initiated By</div>
                                        <div class="refund-kv-value">${escapeHtml(refund.initiatedBy || 'N/A')}</div>
                                    </div>
                                    <div>
                                        <div class="refund-kv-label">Created</div>
                                        <div class="refund-kv-value">${formatDate(refund.createdAt)}</div>
                                    </div>
                                </div>
                                <div class="refund-kv">
                                    <div class="refund-kv-row">
                                        <span class="refund-kv-label">Reason</span>
                                        <span class="refund-kv-value">${escapeHtml(refund.reason || 'N/A')}</span>
                                    </div>
                                    ${refund.notes ? `
                                    <div class="refund-kv-row">
                                        <span class="refund-kv-label">Notes</span>
                                        <span class="refund-kv-value">${escapeHtml(refund.notes)}</span>
                                    </div>
                                    ` : ''}
                                </div>
                            </div>
                        `;
                    }).join('');
                }

                const modalContent = `
                    <div class="refund-history-modal">
                        <div class="refund-details-shell">
                            <div class="refund-details-header">
                                <div>
                                    <p class="refund-eyebrow">Refund history</p>
                                    <h3 class="refund-details-title">Payment refunds</h3>
                                    <p class="refund-details-subtitle">${escapeHtml(paymentId)} · ${refunds.length} refund${refunds.length === 1 ? '' : 's'}</p>
                                </div>
                                <span class="refund-status-pill ${totalRefunded > 0 ? 'refund-status--success' : 'refund-status--neutral'}">
                                    ${totalRefunded > 0 ? 'Refunded' : 'No refunds'}
                                </span>
                            </div>

                            <div class="refund-details-grid">
                                <div class="refund-details-section">
                                    <div class="refund-section-title">Summary</div>
                                    <div class="refund-kv">
                                        <div class="refund-kv-row">
                                            <span class="refund-kv-label">Payment ID</span>
                                            <span class="refund-kv-value"><span class="refund-code">${escapeHtml(paymentId)}</span></span>
                                        </div>
                                        <div class="refund-kv-row">
                                            <span class="refund-kv-label">Refunds</span>
                                            <span class="refund-kv-value">${refunds.length}</span>
                                        </div>
                                        <div class="refund-kv-row">
                                            <span class="refund-kv-label">Currency</span>
                                            <span class="refund-kv-value">${escapeHtml(summary.currency || 'INR')}</span>
                                        </div>
                                    </div>
                                </div>

                                <div class="refund-details-section refund-amount-card">
                                    <div class="refund-amount-label">Total refunded</div>
                                    <div class="refund-amount-value">₹${totalRefunded.toFixed(2)}</div>
                                    <div class="refund-details-inline-grid">
                                        <div class="refund-detail-item">
                                            <div class="refund-kv-label">Total amount</div>
                                            <div class="refund-detail-value">₹${totalAmount.toFixed(2)}</div>
                                        </div>
                                        <div class="refund-detail-item">
                                            <div class="refund-kv-label">Refundable</div>
                                            <div class="refund-detail-value">₹${refundableAmount.toFixed(2)}</div>
                                        </div>
                                    </div>
                                </div>

                                <div class="refund-details-section refund-details-section--full">
                                    <div class="refund-section-title">Refund History</div>
                                    <div class="refund-details-list">
                                        ${refundsHtml}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                `;

                showFinancePanel('Refund History', modalContent);
            } else {
                throw new Error(response.message || 'Failed to load refund history');
            }
        })
        .catch(error => {
            console.error('Error loading refund history:', error);
            const errorContent = `
                <div style="text-align: center; padding: 2rem;">
                    <div style="color: var(--error-color, #ef4444); margin-bottom: 1rem;">⚠️</div>
                    <div>${escapeHtml(error.message || 'Failed to load refund history')}</div>
                    <button class="btn btn-secondary" onclick="closeFinancePanel()" style="margin-top: 1rem;">Close</button>
                </div>
            `;
            showFinancePanel('Refund History', errorContent);
        });
}

/**
 * Add refund button to payment row
 * @param {HTMLElement} row - The table row element
 * @param {Object} payment - Payment object
 * @param {Object|null} refundData - Refund data from API (if available)
 */
function addRefundButtonToPaymentRow(row, payment, refundData) {
    if (!row || !payment) return;

    // Find the last cell (or create actions cell if needed)
    let actionsCell = row.querySelector('.payment-actions');
    if (!actionsCell) {
        actionsCell = document.createElement('td');
        actionsCell.className = 'payment-actions';
        row.appendChild(actionsCell);
    }

    // Clear existing content
    actionsCell.innerHTML = '';
    actionsCell.style.whiteSpace = 'normal';
    actionsCell.style.textAlign = 'center';

    const actionsGroup = document.createElement('div');
    actionsGroup.className = 'payment-actions-group';
    actionsCell.appendChild(actionsGroup);

    const totalAmount = payment.amount || 0;
    
    // Use refundData if available, otherwise fall back to payment object
    let refundedAmount = 0;
    let isFullyRefunded = false;
    let hasPartialRefund = false;
    
    if (refundData) {
        refundedAmount = refundData.totalRefunded || 0;
        isFullyRefunded = refundData.isFullyRefunded || false;
        hasPartialRefund = !isFullyRefunded && refundedAmount > 0;
    } else {
        refundedAmount = payment.refundedAmount || 0;
        isFullyRefunded = totalAmount > 0 && (payment.status === 'REFUNDED' || refundedAmount >= totalAmount);
        hasPartialRefund = totalAmount > 0 && refundedAmount > 0 && refundedAmount < totalAmount;
    }

    // If amount is 0, just show a details button
    if (totalAmount === 0) {
        const detailsBtn = document.createElement('button');
        detailsBtn.className = 'btn-details btn-sm';
        detailsBtn.textContent = 'Details';
        detailsBtn.addEventListener('click', () => showPaymentDetailsModal(payment));
        actionsGroup.appendChild(detailsBtn);
        return;
    }

    // If fully refunded, show ONLY refunded badge (no refund button)
    if (isFullyRefunded) {
        // Update the status cell to show REFUNDED as plain text
        const statusCell = row.querySelector('td:nth-child(5)'); // Status is the 5th column
        if (statusCell) {
            statusCell.innerHTML = '';
            statusCell.style.textAlign = 'center';
            statusCell.textContent = 'REFUNDED';
        }
        
        // Show refunded badge styled like Details button (clickable to view history)
        const badge = document.createElement('button');
        badge.className = 'btn-details btn-sm';
        badge.textContent = 'View Refund';
        badge.title = 'Click to view refund history';
        badge.addEventListener('click', () => {
            const paymentId = payment.razorpayPaymentId || payment.transactionId;
            if (paymentId) {
                loadAndShowRefundDetails(paymentId);
            } else {
                showMessage('error', 'Payment ID not found');
            }
        });
        const refundedTag = document.createElement('span');
        refundedTag.className = 'payment-actions-badge';
        refundedTag.textContent = 'Refunded';
        actionsGroup.appendChild(refundedTag);
        actionsGroup.appendChild(badge);
    } else if (hasPartialRefund) {
        // Show partial refund tag + view history + refund more
        const badge = document.createElement('span');
        badge.className = 'payment-actions-badge payment-actions-badge--partial';
        badge.textContent = `₹${(refundedAmount / 100).toFixed(2)} refunded`;
        actionsGroup.appendChild(badge);

        const viewBtn = document.createElement('button');
        viewBtn.className = 'btn-details btn-sm';
        viewBtn.textContent = 'View refund';
        viewBtn.title = 'Click to view refund history';
        viewBtn.addEventListener('click', () => {
            const paymentId = payment.razorpayPaymentId || payment.transactionId;
            if (paymentId) {
                loadAndShowRefundDetails(paymentId);
            } else {
                showMessage('error', 'Payment ID not found');
            }
        });
        actionsGroup.appendChild(viewBtn);

        const refundBtn = document.createElement('button');
        refundBtn.className = 'btn-refund btn-sm';
        refundBtn.textContent = 'Refund More';
        refundBtn.addEventListener('click', () => {
            // Update payment object with refund data
            payment.razorpayPaymentId = payment.razorpayPaymentId || payment.transactionId;
            if (refundData) {
                payment.refundedAmount = refundData.totalRefunded || 0;
            }
            showRefundModal(payment);
        });
        actionsGroup.appendChild(refundBtn);
    } else if (payment.status === 'verified' || payment.status === 'SUCCESS') {
        // Show refund button for successful payments with amount > 0
        const refundBtn = document.createElement('button');
        refundBtn.className = 'btn-refund btn-sm';
        refundBtn.textContent = 'Refund';
        refundBtn.addEventListener('click', () => {
            // Add razorpayPaymentId if not present
            payment.razorpayPaymentId = payment.razorpayPaymentId || payment.transactionId;
            showRefundModal(payment);
        });
        actionsGroup.appendChild(refundBtn);
    } else {
        // For other statuses, show details button
        const detailsBtn = document.createElement('button');
        detailsBtn.className = 'btn-details btn-sm';
        detailsBtn.textContent = 'Details';
        detailsBtn.addEventListener('click', () => showPaymentDetailsModal(payment));
        actionsGroup.appendChild(detailsBtn);
    }
}

/**
 * Load and show refund details by fetching fresh data from API
 * @param {string} paymentId - The payment ID
 */
function loadAndShowRefundDetails(paymentId) {
    if (!paymentId) {
        showMessage('error', 'Payment ID is required');
        return;
    }

    // Show loading modal
    const loadingContent = getRefundPanelLoadingContent('Loading refund details...');
    showFinancePanel('Refund Details', loadingContent);

    // Fetch refund data from API
    window.RefundAPI.getRefundsForPayment(paymentId)
        .then(response => {
            if (response.status === true && response.data) {
                showRefundDetailsModal(response.data);
            } else {
                throw new Error(response.message || 'Failed to load refund details');
            }
        })
        .catch(error => {
            console.error('Error loading refund details:', error);
            if (typeof closeFinancePanel === 'function') closeFinancePanel();
            showMessage('error', error.message || 'Failed to load refund details');
        });
}

/**
 * Show refund details modal with status banner and refresh functionality
 * @param {Object} refundSummary - Refund summary object
 */
function showRefundDetailsModal(refundSummary) {
    if (!refundSummary) {
        showMessage('error', 'Invalid refund data');
        return;
    }

    const refunds = refundSummary.refunds || [];
    const paymentId = refundSummary.paymentId || 'N/A';
    const originalAmount = (refundSummary.originalAmount || 0) / 100;
    const totalRefunded = (refundSummary.totalRefunded || 0) / 100;
    const remainingRefundable = (refundSummary.remainingRefundable || 0) / 100;
    
    // Generate status banner based on refund statuses
    let statusBanner = '';
    const hasProcessed = refunds.some(r => r.status === 'PROCESSED');
    const hasPending = refunds.some(r => r.status === 'PENDING');
    const hasFailed = refunds.some(r => r.status === 'FAILED');
    const overallStatusClass = hasFailed ? 'refund-status--error' : hasPending ? 'refund-status--warning' : hasProcessed ? 'refund-status--success' : 'refund-status--neutral';
    const overallStatusLabel = hasFailed ? 'Refund Failed' : hasPending ? 'Processing' : hasProcessed ? 'Refund Completed' : 'No Refunds';
    
    if (hasFailed) {
        statusBanner = `
            <div class="status-banner status-banner-error">
                <div class="banner-icon">⚠️</div>
                <div class="banner-content">
                    <div class="banner-title">Refund Failed</div>
                    <div class="banner-message">One or more refunds have failed. Please check the details below.</div>
                </div>
            </div>
        `;
    } else if (hasPending) {
        statusBanner = `
            <div class="status-banner status-banner-warning">
                <div class="banner-icon">⟳</div>
                <div class="banner-content">
                    <div class="banner-title">Processing</div>
                    <div class="banner-message">Refund is being processed. This may take a few minutes.</div>
                </div>
            </div>
        `;
    } else if (hasProcessed) {
        statusBanner = `
            <div class="status-banner status-banner-success">
                <div class="banner-icon">✓</div>
                <div class="banner-content">
                    <div class="banner-title">Refund Completed</div>
                    <div class="banner-message">All refunds have been processed successfully.</div>
                </div>
            </div>
        `;
    }

    const refundsHtml = refunds.map((refund, index) => {
        const statusClass = refund.status === 'PROCESSED' ? 'refund-status--success' :
            refund.status === 'FAILED' ? 'refund-status--error' : 'refund-status--warning';
        
        const statusIcon = refund.status === 'PROCESSED' ? '✓' :
            refund.status === 'FAILED' ? '✗' : '⟳';
        const refundAmount = ((refund.amount || 0) / 100).toFixed(2);
        
        // Calculate processing time
        let processingTime = 'N/A';
        if (refund.status === 'PROCESSED' && refund.createdAt && refund.processedAt) {
            const created = new Date(refund.createdAt);
            const processed = new Date(refund.processedAt);
            const diffMs = processed - created;
            const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
            const diffMins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
            processingTime = `${diffHours}h ${diffMins}m`;
        } else if (refund.status === 'PENDING' && refund.createdAt) {
            const created = new Date(refund.createdAt);
            const now = new Date();
            const diffMs = now - created;
            const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
            const diffMins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
            processingTime = `${diffHours}h ${diffMins}m (pending)`;
        }
        
        return `
            <div class="refund-history-card refund-card">
                <div class="refund-history-header">
                    <div class="refund-history-meta">
                        <div class="refund-eyebrow">Refund #${index + 1}</div>
                        <div class="refund-history-id">${escapeHtml(refund.refundId || 'N/A')}</div>
                    </div>
                    <span class="refund-status-pill ${statusClass}">
                        ${statusIcon} ${escapeHtml(refund.status || 'UNKNOWN')}
                    </span>
                </div>
                <div class="refund-history-grid">
                    <div>
                        <div class="refund-kv-label">Amount</div>
                        <div class="refund-kv-value refund-amount">₹${refundAmount}</div>
                    </div>
                    <div>
                        <div class="refund-kv-label">Processing Time</div>
                        <div class="refund-kv-value">${processingTime}</div>
                    </div>
                    <div>
                        <div class="refund-kv-label">Speed</div>
                        <div class="refund-kv-value">${escapeHtml(refund.speedRequested || 'N/A')}${refund.speedProcessed ? ` → ${refund.speedProcessed}` : ''}</div>
                    </div>
                </div>
                <div class="refund-kv">
                    <div class="refund-kv-row">
                        <span class="refund-kv-label">Reason</span>
                        <span class="refund-kv-value">${escapeHtml(refund.reason || 'N/A')}</span>
                    </div>
                    <div class="refund-kv-row">
                        <span class="refund-kv-label">Processed By</span>
                        <span class="refund-kv-value">${escapeHtml(refund.processedBy || 'N/A')}</span>
                    </div>
                    <div class="refund-kv-row">
                        <span class="refund-kv-label">Created</span>
                        <span class="refund-kv-value">${formatDate(refund.createdAt)}</span>
                    </div>
                    ${refund.processedAt ? `
                    <div class="refund-kv-row">
                        <span class="refund-kv-label">Processed</span>
                        <span class="refund-kv-value">${formatDate(refund.processedAt)}</span>
                    </div>
                    ` : ''}
                </div>
            </div>
        `;
    }).join('');

    const modalContent = `
        <div class="refund-details-modal">
            ${statusBanner ? `<div class="finance-banner">${statusBanner}</div>` : ''}

            <div class="refund-details-shell">
                <div class="refund-details-header">
                    <div>
                        <h3 class="refund-details-title">Refund details</h3>
                        <p class="refund-details-subtitle">${escapeHtml(paymentId)} · ${refunds.length} refund${refunds.length === 1 ? '' : 's'}</p>
                    </div>
                    <div class="refund-details-actions">
                        <span class="refund-status-pill ${overallStatusClass}">${overallStatusLabel}</span>
                        <button class="btn btn-secondary btn-sm" id="refresh-status-btn" onclick="refreshRefundStatus('${escapeHtml(paymentId)}')">
                            <span id="refresh-icon" class="refund-refresh-icon">↻</span> Refresh
                        </button>
                    </div>
                </div>

                <div class="refund-metric-grid">
                    <div class="refund-metric-card refund-metric-card--neutral">
                        <div class="refund-metric-label">Original amount</div>
                        <div class="refund-metric-value">₹${originalAmount.toFixed(2)}</div>
                    </div>
                    <div class="refund-metric-card refund-metric-card--negative">
                        <div class="refund-metric-label">Total refunded</div>
                        <div class="refund-metric-value">₹${totalRefunded.toFixed(2)}</div>
                    </div>
                    <div class="refund-metric-card refund-metric-card--positive">
                        <div class="refund-metric-label">Remaining refundable</div>
                        <div class="refund-metric-value">₹${remainingRefundable.toFixed(2)}</div>
                    </div>
                </div>

                <div class="refund-details-grid">
                    <div class="refund-details-section">
                        <div class="refund-section-title">Summary</div>
                        <div class="refund-kv">
                            <div class="refund-kv-row">
                                <span class="refund-kv-label">Payment ID</span>
                                <span class="refund-kv-value"><span class="refund-code">${escapeHtml(paymentId)}</span></span>
                            </div>
                            <div class="refund-kv-row">
                                <span class="refund-kv-label">Refund Status</span>
                                <span class="refund-kv-value">${refundSummary.isFullyRefunded ? 'Fully refunded' : refunds.length ? 'Partially refunded' : 'Not refunded'}</span>
                            </div>
                            <div class="refund-kv-row">
                                <span class="refund-kv-label">Refund Count</span>
                                <span class="refund-kv-value">${refunds.length}</span>
                            </div>
                        </div>
                    </div>

                    <div class="refund-details-section">
                        <div class="refund-section-title">Balance</div>
                        <div class="refund-kv">
                            <div class="refund-kv-row">
                                <span class="refund-kv-label">Original</span>
                                <span class="refund-kv-value">₹${originalAmount.toFixed(2)}</span>
                            </div>
                            <div class="refund-kv-row">
                                <span class="refund-kv-label">Refunded</span>
                                <span class="refund-kv-value">₹${totalRefunded.toFixed(2)}</span>
                            </div>
                            <div class="refund-kv-row">
                                <span class="refund-kv-label">Remaining</span>
                                <span class="refund-kv-value">₹${remainingRefundable.toFixed(2)}</span>
                            </div>
                        </div>
                    </div>

                    <div class="refund-details-section refund-details-section--full">
                        <div class="refund-section-title">Refund history</div>
                        <div class="refund-details-list">
                            ${refundsHtml || '<div class="refund-details-empty">No refunds found</div>'}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;

    showFinancePanel('Refund Details', modalContent);
}

/**
 * Refresh refund status from Razorpay
 * @param {string} paymentId - The payment ID
 */
function refreshRefundStatus(paymentId) {
    const refreshBtn = document.getElementById('refresh-status-btn');
    const refreshIcon = document.getElementById('refresh-icon');
    
    if (!refreshBtn || !paymentId) return;
    
    // Disable button and show loading state
    refreshBtn.disabled = true;
    if (refreshIcon) {
        refreshIcon.style.animation = 'spin 1s linear infinite';
    }
    
    window.RefundAPI.checkRefundStatus(paymentId)
        .then(response => {
            if (response.status === true && response.data) {
                showMessage('success', 'Refund status updated successfully');
                // Reload the modal with updated data
                showRefundDetailsModal(response.data);
            } else {
                throw new Error(response.message || 'Failed to refresh status');
            }
        })
        .catch(error => {
            console.error('Error refreshing refund status:', error);
            showMessage('error', error.message || 'Failed to refresh refund status');
        })
        .finally(() => {
            if (refreshBtn) refreshBtn.disabled = false;
            if (refreshIcon) refreshIcon.style.animation = '';
        });
}

/**
 * Show payment details modal
 * @param {Object} payment - Payment object
 */
function showPaymentDetailsModal(payment) {
    if (!payment) {
        showMessage('error', 'Invalid payment data');
        return;
    }

    const amount = (payment.amount || 0).toFixed(2);
    const status = payment.status || 'PENDING';
    const statusClass = status === 'verified' || status === 'SUCCESS' ? 'status-processed' : 
                       status === 'FAILED' ? 'status-failed' : 'status-pending';

    const modalContent = `
        <div class="payment-details-modal">
            <div class="finance-hero">
                <div class="finance-kv__label">Amount</div>
                <div class="finance-hero-value">₹${amount}</div>
                <div class="finance-kv__label">${payment.currency || 'INR'}</div>
            </div>

            <div class="finance-card">
                <div class="finance-kv">
                    <span class="finance-kv__label">Status</span>
                    <span class="finance-kv__value">
                        <span class="finance-pill ${statusClass}">${escapeHtml(status)}</span>
                    </span>
                </div>
                <div class="finance-kv">
                    <span class="finance-kv__label">User Email</span>
                    <span class="finance-kv__value">${escapeHtml(payment.userEmail || 'N/A')}</span>
                </div>
                <div class="finance-kv">
                    <span class="finance-kv__label">Payment Method</span>
                    <span class="finance-kv__value">${escapeHtml(payment.paymentMethod || 'N/A')}</span>
                </div>
                <div class="finance-kv">
                    <span class="finance-kv__label">Transaction ID</span>
                    <span class="finance-kv__value"><span class="finance-code">${escapeHtml(payment.transactionId || 'N/A')}</span></span>
                </div>
                <div class="finance-kv">
                    <span class="finance-kv__label">Created</span>
                    <span class="finance-kv__value">${formatDate(payment.createdAt)}</span>
                </div>
            </div>

            <div style="text-align: right;">
                <button class="btn btn-secondary" onclick="closeFinancePanel()">Close</button>
            </div>
        </div>
    `;

    showFinancePanel('Payment Details', modalContent);
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

console.log('Refund admin module loaded');
