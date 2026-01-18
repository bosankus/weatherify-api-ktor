/**
 * Admin Dashboard Common Utilities
 * Shared helper functions used across admin modules
 * This file consolidates duplicate functions from various admin JS files
 */

(function() {
    'use strict';

    /**
     * Get element by ID (shorthand)
     * @param {string} id - Element ID
     * @returns {HTMLElement|null}
     */
    function q(id) {
        return document.getElementById(id);
    }

    /**
     * Format date string to readable format
     * @param {string} dateString - ISO date string
     * @returns {string} Formatted date string
     */
    function formatDate(dateString) {
        if (!dateString) return 'N/A';
        try {
            const date = new Date(dateString);
            if (isNaN(date.getTime())) return dateString;
            return date.toLocaleString('en-US', {
                year: 'numeric',
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (error) {
            console.warn('Error formatting date:', error);
            return dateString;
        }
    }

    /**
     * Format currency amount
     * Handles both paise and rupees
     * @param {number} amountPaise - Amount in paise
     * @returns {string} Formatted currency string (e.g., "₹1,234.56")
     */
    function formatCurrency(amountPaise) {
        if (typeof amountPaise !== 'number' || isNaN(amountPaise)) {
            return '₹0.00';
        }
        // Convert paise to rupees
        const rupees = amountPaise / 100;
        return '₹' + rupees.toLocaleString('en-IN', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    /**
     * Format currency value already in rupees (not paise)
     * @param {number} value - The value to format (in rupees)
     * @returns {string} Formatted currency string
     */
    function formatCurrencyRupees(value) {
        if (typeof value !== 'number' || isNaN(value)) {
            return '₹0.00';
        }
        return '₹' + value.toLocaleString('en-IN', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    /**
     * Escape HTML to prevent XSS attacks
     * @param {string} str - String to escape
     * @returns {string} Escaped string
     */
    function escapeHtml(str) {
        if (str == null) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    /**
     * Parse date safely, returning null if invalid
     * @param {string} dateString - Date string to parse
     * @returns {Date|null} Parsed date or null
     */
    function parseDateSafe(dateString) {
        if (!dateString) return null;
        try {
            const date = new Date(dateString);
            if (isNaN(date.getTime())) return null;
            return date;
        } catch (error) {
            return null;
        }
    }

    /**
     * Get end of day for a date
     * @param {Date} date - Date object
     * @returns {Date} Date set to end of day (23:59:59.999)
     */
    function endOfDay(date) {
        if (!date) return null;
        const end = new Date(date);
        end.setHours(23, 59, 59, 999);
        return end;
    }

    /**
     * Get CSS variable value
     * @param {string} varName - CSS variable name (with or without --)
     * @returns {string} CSS variable value
     */
    function cssVar(varName) {
        const name = varName.startsWith('--') ? varName : `--${varName}`;
        return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    }

    /**
     * Show message banner (success, error, info, warning)
     * @param {string} type - Message type: 'success', 'error', 'info', 'warning'
     * @param {string} message - Message text
     * @param {number} timeout - Auto-dismiss timeout in ms (default: 4000)
     */
    function showMessage(type, message, timeout = 4000) {
        // Use global showMessage if available, otherwise fallback
        if (typeof window.showMessage === 'function') {
            window.showMessage(type, message, timeout);
        } else {
            console.log(`[${type.toUpperCase()}] ${message}`);
        }
    }

    /**
     * Show error message
     * @param {string} message - Error message
     * @param {string} title - Error title (optional)
     */
    function showErrorMessage(message, title) {
        if (typeof window.showErrorMessage === 'function') {
            window.showErrorMessage(message, title);
        } else {
            showMessage('error', message);
        }
    }

    // Export to global scope
    if (typeof window !== 'undefined') {
        window.AdminUtils = {
            q,
            formatDate,
            formatCurrency,
            formatCurrencyRupees,
            escapeHtml,
            parseDateSafe,
            endOfDay,
            cssVar,
            showMessage,
            showErrorMessage
        };

        // Also export commonly used functions directly to window for backward compatibility
        window.q = q;
        window.formatDate = formatDate;
        window.formatCurrency = formatCurrency;
        window.escapeHtml = escapeHtml;
    }

    console.log('[Admin Utils] Common utilities loaded');
})();

