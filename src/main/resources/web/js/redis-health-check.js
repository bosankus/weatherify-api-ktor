/**
 * Redis Health Check Tool for Admin Dashboard
 * Provides UI for testing Redis cache connection and visualizing results
 */

(function () {
    'use strict';

    /**
     * Format latency in milliseconds with appropriate unit
     */
    function formatLatency(ms) {
        if (ms < 1000) {
            return `${ms}ms`;
        } else {
            return `${(ms / 1000).toFixed(2)}s`;
        }
    }

    /**
     * Get status badge HTML based on status
     */
    function getStatusBadge(status) {
        const badgeClasses = {
            'connected': 'badge-success',
            'error': 'badge-danger',
            'disconnected': 'badge-warning'
        };

        const badgeClass = badgeClasses[status] || 'badge-neutral';
        const statusText = status.charAt(0).toUpperCase() + status.slice(1);

        return `<span class="status-badge ${badgeClass}">${statusText}</span>`;
    }

    /**
     * Create result card HTML
     */
    function createResultCard(data) {
        const statusClass = data.status === 'connected' ? 'success' : 'error';
        const icon = data.status === 'connected'
            ? '<span class="material-icons">check_circle</span>'
            : '<span class="material-icons">error</span>';

        let resultHtml = `
            <div class="redis-result-card ${statusClass}">
                <div class="redis-result-header">
                    <div class="redis-result-icon">${icon}</div>
                    <div class="redis-result-title">
                        <h3>${data.message}</h3>
                        <p class="redis-result-subtitle">${getStatusBadge(data.status)}</p>
                    </div>
                </div>

                <div class="redis-result-metrics">
                    <div class="metric">
                        <div class="metric-label">Latency</div>
                        <div class="metric-value">${formatLatency(data.latencyMs)}</div>
                    </div>
                    <div class="metric">
                        <div class="metric-label">Status</div>
                        <div class="metric-value">${data.status === 'connected' ? 'Ready' : 'Failed'}</div>
                    </div>
                    <div class="metric">
                        <div class="metric-label">Checked At</div>
                        <div class="metric-value">${new Date(data.timestamp).toLocaleTimeString()}</div>
                    </div>
                </div>
        `;

        if (data.error) {
            resultHtml += `
                <div class="redis-result-error">
                    <div class="error-label">Error Details</div>
                    <div class="error-message">${escapeHtml(data.error)}</div>
                </div>
            `;
        }

        resultHtml += `</div>`;
        return resultHtml;
    }

    /**
     * Escape HTML special characters
     */
    function escapeHtml(text) {
        const map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return text.replace(/[&<>"']/g, m => map[m]);
    }

    /**
     * Initialize Redis health check functionality
     */
    function initializeRedisHealthCheck() {
        const btn = document.getElementById('redis-health-btn');
        const spinner = document.getElementById('redis-health-spinner');

        if (!btn) {
            console.warn('[Redis Health] Button not found');
            return;
        }

        btn.addEventListener('click', async function () {
            // Show spinner
            btn.disabled = true;
            spinner.style.display = 'inline-block';

            try {
                const response = await fetch('/tools/redis-health', {
                    method: 'POST',
                    credentials: 'include',
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json'
                    }
                });

                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }

                const result = await response.json();
                const data = result.data || result;

                // Show result modal
                showRedisResultModal(data);

                // Show success message
                if (typeof window.showMessage === 'function') {
                    const msgType = data.status === 'connected' ? 'success' : 'warning';
                    window.showMessage(msgType, data.message);
                }
            } catch (error) {
                console.error('[Redis Health] Error:', error);

                // Show error message
                if (typeof window.showMessage === 'function') {
                    window.showMessage('error', `Redis health check failed: ${error.message}`);
                }

                // Show error result
                showRedisResultModal({
                    status: 'error',
                    message: 'Failed to connect to Redis',
                    error: error.message,
                    latencyMs: 0,
                    timestamp: Date.now()
                });
            } finally {
                // Hide spinner
                btn.disabled = false;
                spinner.style.display = 'none';
            }
        });
    }

    /**
     * Display Redis result in a modal
     */
    function showRedisResultModal(data) {
        // Check if modal already exists
        let modal = document.getElementById('redis-result-modal');

        if (!modal) {
            // Create modal
            modal = document.createElement('div');
            modal.id = 'redis-result-modal';
            modal.className = 'modal';
            modal.style.display = 'none';
            document.body.appendChild(modal);
        }

        const resultHTML = createResultCard(data);

        modal.innerHTML = `
            <div class="modal-overlay"></div>
            <div class="modal-container">
                <div class="modal-header">
                    <h3 class="modal-title">Redis Health Check Result</h3>
                    <button class="modal-close" aria-label="Close modal">
                        <span class="material-icons">close</span>
                    </button>
                </div>
                <div class="modal-body">
                    ${resultHTML}
                </div>
            </div>
        `;

        // Show modal
        modal.style.display = 'flex';

        // Handle close button
        const closeBtn = modal.querySelector('.modal-close');
        const overlay = modal.querySelector('.modal-overlay');

        function closeModal() {
            modal.style.display = 'none';
        }

        closeBtn.addEventListener('click', closeModal);
        overlay.addEventListener('click', closeModal);

        // Close on Escape key
        const handleEscape = (e) => {
            if (e.key === 'Escape') {
                closeModal();
                document.removeEventListener('keydown', handleEscape);
            }
        };

        document.addEventListener('keydown', handleEscape);
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeRedisHealthCheck);
    } else {
        initializeRedisHealthCheck();
    }

    console.log('[Redis Health Check] Module loaded');
})();
