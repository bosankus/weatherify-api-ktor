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
        return fetch('/admin/refunds/initiate', {
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
        return fetch(`/admin/refunds/${encodeURIComponent(refundId)}`, {
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
        return fetch(`/admin/refunds/payment/${encodeURIComponent(paymentId)}`, {
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

        return fetch(`/admin/refunds/history?${params.toString()}`, {
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
        return fetch('/admin/refunds/metrics', {
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
        return fetch(`/admin/refunds/payment/${encodeURIComponent(paymentId)}/check`, {
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
    const loadingContent = `
        <div style="text-align: center; padding: 2rem;">
            <div class="loading-spinner" style="display: inline-block; margin-bottom: 1rem;"></div>
            <div>Loading refund information...</div>
        </div>
    `;
    showModal('Initiate Refund', loadingContent);

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
                    closeModal();
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
            closeModal();
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
        <div class="refund-modal-content">
            <div class="refund-details">
                <div class="detail-row">
                    <span class="label">Payment ID:</span>
                    <span class="value"><code>${escapeHtml(payment.razorpayPaymentId)}</code></span>
                </div>
                <div class="detail-row">
                    <span class="label">User Email:</span>
                    <span class="value">${escapeHtml(payment.userEmail || 'N/A')}</span>
                </div>
                <div class="detail-row">
                    <span class="label">Original Amount:</span>
                    <span class="value">₹${amountInRupees}</span>
                </div>
                <div class="detail-row">
                    <span class="label">Already Refunded:</span>
                    <span class="value">₹${refundedInRupees}</span>
                </div>
                <div class="detail-row">
                    <span class="label">Refundable:</span>
                    <span class="value refundable">₹${refundableInRupees}</span>
                </div>
            </div>

            <form id="refund-form" class="refund-form">
                <div class="form-group">
                    <label for="refund-type">Refund Type</label>
                    <select id="refund-type" name="refundType" required>
                        <option value="full">Full Refund (₹${refundableInRupees})</option>
                        <option value="partial">Partial Refund</option>
                    </select>
                </div>

                <div class="form-group" id="partial-amount-group" style="display: none;">
                    <label for="refund-amount">Refund Amount (₹)</label>
                    <input 
                        type="number" 
                        id="refund-amount" 
                        name="amount" 
                        min="0.01" 
                        max="${refundableInRupees}"
                        step="0.01"
                        placeholder="Enter amount in rupees"
                    />
                    <small style="display: block; color: var(--text-secondary); margin-top: 0.25rem;">
                        Maximum: ₹${refundableInRupees}
                    </small>
                </div>

                <div class="form-group">
                    <label for="refund-reason">Reason</label>
                    <select id="refund-reason" name="reason" required>
                        <option value="CUSTOMER_REQUEST">Customer Request</option>
                        <option value="DUPLICATE_PAYMENT">Duplicate Payment</option>
                        <option value="FRAUDULENT">Fraudulent Transaction</option>
                        <option value="SERVICE_NOT_PROVIDED">Service Not Provided</option>
                        <option value="OTHER">Other</option>
                    </select>
                </div>

                <div class="form-group">
                    <label for="refund-notes">Notes (Optional)</label>
                    <textarea 
                        id="refund-notes" 
                        name="notes" 
                        rows="2"
                        placeholder="Add any additional notes..."
                    ></textarea>
                </div>

                <div class="form-actions">
                    <button type="button" class="btn btn-secondary" onclick="closeModal()">Cancel</button>
                    <button type="submit" class="btn btn-primary" id="refund-submit-btn">
                        <span id="refund-submit-text">Initiate Refund</span>
                        <span id="refund-submit-spinner" class="loading-spinner" style="display: none;"></span>
                    </button>
                </div>
            </form>
        </div>
    `;

    showModal('Initiate Refund', modalContent);

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
                            closeModal();
                            // Reload payment history to reflect changes
                            if (typeof loadPaymentHistory === 'function') {
                                loadPaymentHistory();
                            }
                            // Reload financial metrics to reflect refund in total revenue
                            if (typeof loadFinancialMetrics === 'function') {
                                loadFinancialMetrics();
                            }
                            // Reload subscription analytics to reflect updated revenue
                            if (typeof window.SubscriptionsModule !== 'undefined' && 
                                typeof window.SubscriptionsModule.loadSubscriptionAnalytics === 'function') {
                                window.SubscriptionsModule.loadSubscriptionAnalytics();
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
 * Show refund history modal for a payment
 * @param {string} paymentId - The payment ID
 */
function showRefundHistoryModal(paymentId) {
    if (!paymentId) {
        showMessage('error', 'Invalid payment ID');
        return;
    }

    const loadingContent = `
        <div style="text-align: center; padding: 2rem;">
            <div class="loading-spinner" style="display: inline-block; margin-bottom: 1rem;"></div>
            <div>Loading refund history...</div>
        </div>
    `;

    showModal('Refund History', loadingContent);

    window.RefundAPI.getRefundsForPayment(paymentId)
        .then(response => {
            if (response.status === true && response.data) {
                const data = response.data;
                const refunds = data.refunds || [];
                const summary = data.summary || {};

                let refundsHtml = '';
                if (refunds.length === 0) {
                    refundsHtml = '<div style="text-align: center; padding: 2rem; color: var(--text-secondary);">No refunds found for this payment</div>';
                } else {
                    refundsHtml = refunds.map(refund => {
                        const statusClass = refund.status === 'PROCESSED' ? 'badge-processed' :
                            refund.status === 'FAILED' ? 'badge-failed' : 'badge-pending';
                        return `
                            <div class="refund-history-item">
                                <div class="refund-header">
                                    <span class="refund-id">${escapeHtml(refund.refundId || 'N/A')}</span>
                                    <span class="badge ${statusClass}">${escapeHtml(refund.status || 'UNKNOWN')}</span>
                                </div>
                                <div class="detail-row">
                                    <span class="label">Amount:</span>
                                    <span class="value">${refund.currency || 'INR'} ${((refund.amount || 0) / 100).toFixed(2)}</span>
                                </div>
                                <div class="detail-row">
                                    <span class="label">Reason:</span>
                                    <span class="value">${escapeHtml(refund.reason || 'N/A')}</span>
                                </div>
                                ${refund.notes ? `
                                <div class="detail-row">
                                    <span class="label">Notes:</span>
                                    <span class="value">${escapeHtml(refund.notes)}</span>
                                </div>
                                ` : ''}
                                <div class="detail-row">
                                    <span class="label">Initiated By:</span>
                                    <span class="value">${escapeHtml(refund.initiatedBy || 'N/A')}</span>
                                </div>
                                <div class="detail-row">
                                    <span class="label">Created At:</span>
                                    <span class="value">${formatDate(refund.createdAt)}</span>
                                </div>
                            </div>
                        `;
                    }).join('');
                }

                const modalContent = `
                    <div class="refund-history-modal">
                        <div class="summary-section">
                            <h3>Payment Summary</h3>
                            <div class="detail-row">
                                <span class="label">Payment ID:</span>
                                <span class="value"><code>${escapeHtml(paymentId)}</code></span>
                            </div>
                            <div class="detail-row">
                                <span class="label">Total Amount:</span>
                                <span class="value">${summary.currency || 'INR'} ${((summary.totalAmount || 0) / 100).toFixed(2)}</span>
                            </div>
                            <div class="detail-row">
                                <span class="label">Total Refunded:</span>
                                <span class="value">${summary.currency || 'INR'} ${((summary.totalRefunded || 0) / 100).toFixed(2)}</span>
                            </div>
                            <div class="detail-row">
                                <span class="label">Refundable Amount:</span>
                                <span class="value refundable">${summary.currency || 'INR'} ${((summary.refundableAmount || 0) / 100).toFixed(2)}</span>
                            </div>
                        </div>

                        <div class="refunds-section">
                            <h3>Refund History (${refunds.length})</h3>
                            ${refundsHtml}
                        </div>
                    </div>
                `;

                showModal('Refund History', modalContent);
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
                    <button class="btn btn-secondary" onclick="closeModal()" style="margin-top: 1rem;">Close</button>
                </div>
            `;
            showModal('Refund History', errorContent);
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
    actionsCell.style.whiteSpace = 'nowrap';
    actionsCell.style.textAlign = 'center';

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
        actionsCell.appendChild(detailsBtn);
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
        actionsCell.appendChild(badge);
    } else if (hasPartialRefund) {
        // Show partial refund badge styled like Details button and refund button
        const badge = document.createElement('button');
        badge.className = 'btn-details btn-sm';
        badge.textContent = `Partial (₹${(refundedAmount / 100).toFixed(2)})`;
        badge.style.marginRight = '8px';
        badge.title = 'Click to view refund history';
        badge.addEventListener('click', () => {
            const paymentId = payment.razorpayPaymentId || payment.transactionId;
            if (paymentId) {
                loadAndShowRefundDetails(paymentId);
            } else {
                showMessage('error', 'Payment ID not found');
            }
        });
        actionsCell.appendChild(badge);

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
        actionsCell.appendChild(refundBtn);
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
        actionsCell.appendChild(refundBtn);
    } else {
        // For other statuses, show details button
        const detailsBtn = document.createElement('button');
        detailsBtn.className = 'btn-details btn-sm';
        detailsBtn.textContent = 'Details';
        detailsBtn.addEventListener('click', () => showPaymentDetailsModal(payment));
        actionsCell.appendChild(detailsBtn);
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
    const loadingContent = `
        <div style="text-align: center; padding: 2rem;">
            <div class="loading-spinner" style="display: inline-block; margin-bottom: 1rem;"></div>
            <div>Loading refund details...</div>
        </div>
    `;
    showModal('Refund Details', loadingContent);

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
            closeModal();
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
    const paymentId = refundSummary.paymentId;
    
    // Generate status banner based on refund statuses
    let statusBanner = '';
    const hasProcessed = refunds.some(r => r.status === 'PROCESSED');
    const hasPending = refunds.some(r => r.status === 'PENDING');
    const hasFailed = refunds.some(r => r.status === 'FAILED');
    
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
                <div class="banner-icon">⏳</div>
                <div class="banner-content">
                    <div class="banner-title">Refund Processing</div>
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
        const statusClass = refund.status === 'PROCESSED' ? 'badge-processed' :
            refund.status === 'FAILED' ? 'badge-failed' : 'badge-pending';
        
        const statusIcon = refund.status === 'PROCESSED' ? '✓' :
            refund.status === 'FAILED' ? '✗' : '⏳';
        
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
            <div class="refund-card" style="
                background: var(--card-bg);
                border: 1px solid var(--card-border);
                border-radius: 8px;
                padding: 1.25rem;
                margin-bottom: 1rem;
                box-shadow: 0 2px 4px var(--card-shadow);
            ">
                <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 1rem;">
                    <div>
                        <div style="font-size: 0.875rem; color: var(--text-secondary); margin-bottom: 0.25rem;">
                            Refund #${index + 1}
                        </div>
                        <div style="font-family: monospace; font-size: 0.875rem; color: var(--text-color);">
                            ${escapeHtml(refund.refundId || 'N/A')}
                        </div>
                    </div>
                    <span class="badge ${statusClass}" style="display: inline-flex; align-items: center; gap: 0.25rem;">
                        <span>${statusIcon}</span>
                        <span>${escapeHtml(refund.status || 'UNKNOWN')}</span>
                    </span>
                </div>
                
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 0.75rem; margin-bottom: 0.75rem;">
                    <div>
                        <div style="font-size: 0.75rem; color: var(--text-secondary); margin-bottom: 0.25rem;">Amount</div>
                        <div style="font-size: 1.125rem; font-weight: 600; color: var(--text-color);">
                            ₹${((refund.amount || 0) / 100).toFixed(2)}
                        </div>
                    </div>
                    
                    <div>
                        <div style="font-size: 0.75rem; color: var(--text-secondary); margin-bottom: 0.25rem;">Processing Time</div>
                        <div style="font-size: 0.875rem; font-weight: 500; color: var(--text-color);">
                            ${processingTime}
                        </div>
                    </div>
                    
                    <div>
                        <div style="font-size: 0.75rem; color: var(--text-secondary); margin-bottom: 0.25rem;">Speed</div>
                        <div style="font-size: 0.875rem; color: var(--text-color);">
                            ${escapeHtml(refund.speedRequested || 'N/A')}${refund.speedProcessed ? ` → ${refund.speedProcessed}` : ''}
                        </div>
                    </div>
                </div>
                
                <div style="border-top: 1px solid var(--card-border); padding-top: 0.75rem; margin-top: 0.75rem;">
                    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 0.5rem; font-size: 0.875rem;">
                        <div>
                            <span style="color: var(--text-secondary);">Reason:</span>
                            <span style="color: var(--text-color); margin-left: 0.5rem;">${escapeHtml(refund.reason || 'N/A')}</span>
                        </div>
                        <div>
                            <span style="color: var(--text-secondary);">Processed By:</span>
                            <span style="color: var(--text-color); margin-left: 0.5rem;">${escapeHtml(refund.processedBy || 'N/A')}</span>
                        </div>
                        <div>
                            <span style="color: var(--text-secondary);">Created:</span>
                            <span style="color: var(--text-color); margin-left: 0.5rem;">${formatDate(refund.createdAt)}</span>
                        </div>
                        ${refund.processedAt ? `
                        <div>
                            <span style="color: var(--text-secondary);">Processed:</span>
                            <span style="color: var(--text-color); margin-left: 0.5rem;">${formatDate(refund.processedAt)}</span>
                        </div>
                        ` : ''}
                    </div>
                </div>
            </div>
        `;
    }).join('');

    const modalContent = `
        <div class="refund-details-modal" style="max-width: 900px;">
            ${statusBanner}
            
            <div style="
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                border-radius: 12px;
                padding: 1.5rem;
                margin-bottom: 1.5rem;
                color: #ffffff;
                box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
            ">
                <div style="font-size: 0.875rem; opacity: 0.95; margin-bottom: 0.5rem; font-weight: 500;">Payment ID</div>
                <div style="font-family: monospace; font-size: 0.875rem; margin-bottom: 1.5rem; opacity: 1; background: rgba(255,255,255,0.15); padding: 0.5rem; border-radius: 6px;">
                    ${escapeHtml(paymentId)}
                </div>
                
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 1rem;">
                    <div style="background: rgba(255,255,255,0.1); padding: 1rem; border-radius: 8px;">
                        <div style="font-size: 0.75rem; opacity: 0.9; margin-bottom: 0.5rem; font-weight: 500;">Original</div>
                        <div style="font-size: 1.5rem; font-weight: 700; color: #ffffff;">
                            ₹${((refundSummary.originalAmount || 0) / 100).toFixed(2)}
                        </div>
                    </div>
                    
                    <div style="background: rgba(255,255,255,0.1); padding: 1rem; border-radius: 8px;">
                        <div style="font-size: 0.75rem; opacity: 0.9; margin-bottom: 0.5rem; font-weight: 500;">Refunded</div>
                        <div style="font-size: 1.5rem; font-weight: 700; color: #ffffff;">
                            ₹${((refundSummary.totalRefunded || 0) / 100).toFixed(2)}
                        </div>
                    </div>
                    
                    <div style="background: rgba(255,255,255,0.1); padding: 1rem; border-radius: 8px;">
                        <div style="font-size: 0.75rem; opacity: 0.9; margin-bottom: 0.5rem; font-weight: 500;">Remaining</div>
                        <div style="font-size: 1.5rem; font-weight: 700; color: #ffffff;">
                            ₹${((refundSummary.remainingRefundable || 0) / 100).toFixed(2)}
                        </div>
                    </div>
                </div>
            </div>

            <div class="refunds-section">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
                    <h3 style="margin: 0; font-size: 1.125rem; color: var(--heading-color); font-weight: 600;">
                        Refund History (${refunds.length})
                    </h3>
                    <button class="btn btn-secondary btn-sm" id="refresh-status-btn" onclick="refreshRefundStatus('${escapeHtml(paymentId)}')">
                        <span id="refresh-icon">🔄</span> Refresh Status
                    </button>
                </div>
                ${refundsHtml || '<div style="text-align: center; padding: 2rem; color: var(--text-secondary);">No refunds found</div>'}
            </div>
        </div>
    `;

    showModal('Refund Details', modalContent);
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

    const modalContent = `
        <div class="payment-details-modal">
            <div class="detail-row">
                <span class="label">Payment ID:</span>
                <span class="value"><code>${escapeHtml(payment.id || 'N/A')}</code></span>
            </div>
            <div class="detail-row">
                <span class="label">Transaction ID:</span>
                <span class="value"><code>${escapeHtml(payment.transactionId || 'N/A')}</code></span>
            </div>
            <div class="detail-row">
                <span class="label">User Email:</span>
                <span class="value">${escapeHtml(payment.userEmail || 'N/A')}</span>
            </div>
            <div class="detail-row">
                <span class="label">Amount:</span>
                <span class="value">${payment.currency || 'INR'} ${(payment.amount || 0).toFixed(2)}</span>
            </div>
            <div class="detail-row">
                <span class="label">Payment Method:</span>
                <span class="value">${escapeHtml(payment.paymentMethod || 'N/A')}</span>
            </div>
            <div class="detail-row">
                <span class="label">Status:</span>
                <span class="value"><span class="payment-status payment-status-${(payment.status || 'pending').toLowerCase()}">${escapeHtml(payment.status || 'PENDING')}</span></span>
            </div>
            <div class="detail-row">
                <span class="label">Created At:</span>
                <span class="value">${formatDate(payment.createdAt)}</span>
            </div>
        </div>
        <div style="margin-top: 1.5rem; text-align: right;">
            <button class="btn btn-secondary" onclick="closeModal()">Close</button>
        </div>
    `;

    showModal('Payment Details', modalContent);
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
