/**
 * Common table utilities for consistent table rendering across Finance, Users, and Subscriptions tabs
 * This ensures all tables have the same styling, alignment, padding, and behavior
 */

/**
 * Create a table cell with consistent styling
 * @param {string|HTMLElement} content - Text content or HTML element
 * @param {Object} options - Cell options
 * @param {string} options.align - Text alignment: 'left', 'center', 'right'
 * @param {string} options.className - Additional CSS classes
 * @param {string} options.fontSize - Font size override
 * @returns {HTMLTableCellElement}
 */
function createTableCell(content, options = {}) {
    const cell = document.createElement('td');
    
    // Set content
    if (typeof content === 'string') {
        cell.textContent = content;
    } else if (content instanceof HTMLElement) {
        cell.appendChild(content);
    }
    
    // Apply alignment
    if (options.align) {
        cell.style.textAlign = options.align;
    }
    
    // Apply font size (default is 0.875rem from CSS, but can override)
    if (options.fontSize) {
        cell.style.fontSize = options.fontSize;
    }
    
    // Apply additional classes
    if (options.className) {
        cell.className = options.className;
    }
    
    return cell;
}

/**
 * Create a table header cell with consistent styling
 * @param {string} text - Header text
 * @param {Object} options - Header options
 * @param {string} options.align - Text alignment: 'left', 'center', 'right'
 * @returns {HTMLTableCellElement}
 */
function createTableHeader(text, options = {}) {
    const th = document.createElement('th');
    th.textContent = text;
    
    if (options.align) {
        th.style.textAlign = options.align;
    }
    
    return th;
}

/**
 * Create an empty state row for tables
 * @param {number} colSpan - Number of columns to span
 * @param {string} message - Empty state message
 * @returns {HTMLTableRowElement}
 */
function createEmptyTableRow(colSpan, message = 'No data found') {
    const row = document.createElement('tr');
    const cell = createTableCell(message, {
        align: 'center',
        className: 'empty-state-cell'
    });
    cell.colSpan = colSpan;
    cell.style.padding = '2rem';
    cell.style.color = 'var(--text-secondary)';
    row.appendChild(cell);
    return row;
}

/**
 * Create a loading state row for tables
 * @param {number} colSpan - Number of columns to span
 * @param {string} message - Loading message
 * @returns {HTMLTableRowElement}
 */
function createLoadingTableRow(colSpan, message = 'Loading...') {
    const row = document.createElement('tr');
    const cell = createTableCell(message, {
        align: 'center',
        className: 'loading-state-cell'
    });
    cell.colSpan = colSpan;
    cell.style.padding = '2rem';
    row.appendChild(cell);
    return row;
}

/**
 * Create an error state row for tables
 * @param {number} colSpan - Number of columns to span
 * @param {string} message - Error message
 * @returns {HTMLTableRowElement}
 */
function createErrorTableRow(colSpan, message) {
    const row = document.createElement('tr');
    const cell = createTableCell(message, {
        align: 'center',
        className: 'error-state-cell'
    });
    cell.colSpan = colSpan;
    cell.style.padding = '2rem';
    cell.style.color = 'var(--error-color, #ef4444)';
    row.appendChild(cell);
    return row;
}

/**
 * Apply consistent table row styling
 * @param {HTMLTableRowElement} row - Table row element
 */
function styleTableRow(row) {
    // Row already gets styling from CSS, but we can add data attributes if needed
    row.style.transition = 'background 0.2s ease';
}

/**
 * Format date consistently across all tables
 * @param {string} dateString - ISO date string
 * @returns {string} Formatted date
 */
function formatTableDate(dateString) {
    if (!dateString) return 'N/A';
    try {
        const date = new Date(dateString);
        return date.toLocaleString();
    } catch (error) {
        return dateString;
    }
}

/**
 * Format currency consistently
 * @param {number} amount - Amount in rupees (or paise if isPaise=true)
 * @param {boolean} isPaise - Whether amount is in paise (needs conversion)
 * @returns {string} Formatted currency string
 */
function formatTableCurrency(amount, isPaise = false) {
    if (amount == null || amount === 0) return '-';
    const amountInRupees = isPaise ? amount / 100 : amount;
    return `₹${amountInRupees.toLocaleString('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    })}`;
}

/**
 * Apply finance table UI classes to align with Users tab styling.
 * @param {HTMLElement} tableBody - Table body element to locate parent table
 */
function applyFinanceTableStyles(tableBody) {
    if (!tableBody || typeof tableBody.closest !== 'function') return;
    const table = tableBody.closest('table');
    if (!table) return;

    table.classList.add('finance-table');

    const wrapper = table.parentElement;
    if (wrapper && wrapper.tagName === 'DIV') {
        wrapper.classList.add('finance-table-wrapper');
    }

    const shell = wrapper?.parentElement;
    if (shell && shell.tagName === 'DIV') {
        shell.classList.add('finance-table-shell');
    }
}

// Export functions to global scope
if (typeof window !== 'undefined') {
    window.createTableCell = createTableCell;
    window.createTableHeader = createTableHeader;
    window.createEmptyTableRow = createEmptyTableRow;
    window.createLoadingTableRow = createLoadingTableRow;
    window.createErrorTableRow = createErrorTableRow;
    window.styleTableRow = styleTableRow;
    window.formatTableDate = formatTableDate;
    window.formatTableCurrency = formatTableCurrency;
    window.applyFinanceTableStyles = applyFinanceTableStyles;
}

