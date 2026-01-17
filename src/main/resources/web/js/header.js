/**
 * Header Component JavaScript
 * Provides initialization and configuration for the common header component
 * 
 * Dependencies:
 * -------------
 * - auth.js: Required for user-info and logout actions (getUserInfo, logout functions)
 * - theme.js: Required for theme-toggle action (theme toggle functionality)
 * - header.css: Required for styling
 * - header.html: Required HTML template structure
 * 
 * Basic Usage:
 * ------------
 * // Initialize with default configuration (Weather API subtitle, all actions)
 * initializeHeader();
 * 
 * // Custom configuration
 * initializeHeader({
 *     homeUrl: '/dashboard',
 *     subtitle: 'My Dashboard',
 *     actions: [
 *         { type: 'theme-toggle' },
 *         { type: 'user-info' },
 *         { type: 'logout' }
 *     ]
 * });
 * 
 * Common Use Cases:
 * -----------------
 * 
 * 1. Login Page (minimal header, no user actions)
 * ------------------------------------------------
 * initializeHeader({
 *     homeUrl: '/',
 *     subtitle: 'Sign In',
 *     actions: [
 *         { type: 'theme-toggle' }
 *     ]
 * });
 * 
 * 2. Dashboard Page (full header with user info)
 * -----------------------------------------------
 * initializeHeader({
 *     homeUrl: '/weather',
 *     subtitle: 'Weather Dashboard',
 *     actions: [
 *         { type: 'theme-toggle' },
 *         { type: 'user-info' },
 *         { type: 'logout' }
 *     ]
 * });
 * 
 * 3. Terms/Privacy Page (public page, no authentication)
 * -------------------------------------------------------
 * initializeHeader({
 *     homeUrl: '/',
 *     subtitle: 'Terms of Service',
 *     actions: [
 *         { type: 'theme-toggle' },
 *         {
 *             type: 'custom',
 *             html: '<a href="/" style="color: var(--text-primary); text-decoration: none; padding: 0.5rem 1rem;">Back to Home</a>'
 *         }
 *     ]
 * });
 * 
 * 4. Admin Panel (custom actions)
 * --------------------------------
 * initializeHeader({
 *     homeUrl: '/',
 *     subtitle: 'Admin Panel',
 *     actions: [
 *         { type: 'theme-toggle' },
 *         {
 *             type: 'custom',
 *             html: '<button class="admin-btn">Settings</button>',
 *             onClick: () => window.location.href = '/settings'
 *         },
 *         { type: 'user-info' },
 *         { type: 'logout' }
 *     ]
 * });
 * 
 * 5. Minimal Header (no subtitle, only theme toggle)
 * ---------------------------------------------------
 * initializeHeader({
 *     subtitle: null,
 *     actions: [{ type: 'theme-toggle' }]
 * });
 * 
 * Advanced Configuration:
 * -----------------------
 * 
 * // Custom user label
 * initializeHeader({
 *     actions: [
 *         { type: 'user-info', label: 'admin@example.com' }
 *     ]
 * });
 * 
 * // Multiple custom actions
 * initializeHeader({
 *     subtitle: 'My App',
 *     actions: [
 *         { type: 'theme-toggle' },
 *         {
 *             type: 'custom',
 *             html: '<button>🔔</button>',
 *             onClick: () => showNotifications()
 *         },
 *         {
 *             type: 'custom',
 *             html: '<button>⚙️</button>',
 *             onClick: () => openSettings()
 *         },
 *         { type: 'logout' }
 *     ]
 * });
 */

/**
 * Configuration options for the header component
 * @typedef {Object} HeaderConfig
 * @property {string} homeUrl - URL for the logo link (default: '/weather')
 * @property {string|null} subtitle - Subtitle text (null to hide)
 * @property {Array<ActionIcon>} actions - Array of action icon configurations
 */

/**
 * Action icon configuration
 * @typedef {Object} ActionIcon
 * @property {string} type - Icon type: 'theme-toggle', 'user-info', 'logout', 'custom'
 * @property {string} [label] - Label text for user-info type
 * @property {Function} [onClick] - Click handler for custom type
 * @property {string} [html] - Custom HTML for custom type
 */

/**
 * Initialize the header component with configuration
 * 
 * This is the main entry point for configuring the header component.
 * It handles merging user configuration with defaults, updating DOM elements,
 * and rendering action icons. The function is designed to fail gracefully
 * if elements are missing or configuration is invalid.
 * 
 * @param {HeaderConfig} config - Header configuration options
 * 
 * @example
 * // Initialize with defaults
 * initializeHeader();
 * 
 * @example
 * // Custom configuration
 * initializeHeader({
 *     homeUrl: '/dashboard',
 *     subtitle: 'Dashboard',
 *     actions: [{ type: 'theme-toggle' }, { type: 'logout' }]
 * });
 */
function initializeHeader(config = {}) {
    try {
        // Validate that config is an object (not null, array, or primitive)
        // If invalid, reset to empty object to use defaults
        if (config && typeof config !== 'object') {
            console.warn('Invalid header config, using defaults');
            config = {};
        }

        // Default configuration provides sensible defaults for a typical authenticated page
        // These can be overridden by passing custom values in the config parameter
        const defaults = {
            homeUrl: '/weather',           // Logo link destination
            subtitle: 'Weather API',       // Badge text next to logo
            actions: [                     // Action icons in the header
                { type: 'theme-toggle' },  // Light/dark mode toggle
                { type: 'user-info' },     // Display user email
                { type: 'logout' }         // Logout button
            ]
        };

        // Merge user config with defaults using spread operator
        // User config takes precedence over defaults
        const settings = { ...defaults, ...config };

        // Update the logo link href attribute
        // This allows the logo to navigate to different pages based on context
        const logoLink = document.getElementById('header-logo-link');
        if (logoLink) {
            logoLink.href = settings.homeUrl;
        } else {
            console.warn('Header logo link element not found');
        }

        // Update or hide the subtitle badge
        // Setting subtitle to null or empty string will hide it
        const subtitle = document.getElementById('header-subtitle');
        if (subtitle) {
            if (settings.subtitle) {
                // Show subtitle with provided text
                subtitle.textContent = settings.subtitle;
                subtitle.classList.remove('hidden');
            } else {
                // Hide subtitle by adding hidden class
                subtitle.classList.add('hidden');
            }
        } else {
            console.warn('Header subtitle element not found');
        }

        // Dynamically render action icons based on configuration
        // This allows different pages to show different sets of actions
        renderActions(settings.actions);

    } catch (error) {
        console.error('Error initializing header:', error);
        // Header fails gracefully - if initialization fails, the page remains functional
        // The header will display with its default HTML structure
    }
}

/**
 * Render action icons in the header
 * 
 * This function dynamically populates the header actions container with
 * action elements based on the provided configuration. It clears any existing
 * actions first, then creates new elements for each action in the array.
 * 
 * Each action is processed independently, so if one fails, others will still render.
 * 
 * @param {Array<ActionIcon>} actions - Array of action configurations
 * 
 * @example
 * renderActions([
 *     { type: 'theme-toggle' },
 *     { type: 'user-info' },
 *     { type: 'logout' }
 * ]);
 */
function renderActions(actions) {
    // Get the container element where actions will be rendered
    const actionsContainer = document.getElementById('header-actions');
    if (!actionsContainer) {
        console.warn('Header actions container not found');
        return;
    }

    // Clear any existing action elements to prevent duplicates
    // This allows re-initialization without accumulating elements
    actionsContainer.innerHTML = '';

    // Validate that actions is an array before attempting to iterate
    if (!Array.isArray(actions)) {
        console.warn('Actions must be an array');
        return;
    }

    // Create and append each action element
    // Using forEach with try-catch ensures one failing action doesn't break others
    actions.forEach(action => {
        try {
            // Create the appropriate element based on action type
            const element = createActionElement(action);
            
            // Only append if element was successfully created
            if (element) {
                actionsContainer.appendChild(element);
            }
        } catch (error) {
            // Log error but continue processing remaining actions
            console.error('Error creating action element:', error);
        }
    });
}

/**
 * Create an action element based on type
 * 
 * Factory function that routes action creation to the appropriate specialized
 * function based on the action type. This provides a single entry point for
 * creating all types of action elements.
 * 
 * @param {ActionIcon} action - Action configuration object
 * @returns {HTMLElement|null} - Created DOM element or null if invalid
 * 
 * @example
 * const themeToggle = createActionElement({ type: 'theme-toggle' });
 * const userInfo = createActionElement({ type: 'user-info', label: 'user@example.com' });
 * const customBtn = createActionElement({ 
 *     type: 'custom', 
 *     html: '<button>Help</button>',
 *     onClick: () => alert('Help clicked')
 * });
 */
function createActionElement(action) {
    // Validate that action object exists and has a type property
    if (!action || !action.type) {
        console.warn('Invalid action configuration');
        return null;
    }

    // Route to appropriate creation function based on action type
    switch (action.type) {
        case 'theme-toggle':
            // Creates a checkbox-based theme toggle (requires theme.js)
            return createThemeToggle();
            
        case 'user-info':
            // Creates a span displaying user email (requires auth.js)
            return createUserInfo(action.label);
            
        case 'logout':
            // Creates a logout button (requires auth.js)
            return createLogoutButton();
            
        case 'custom':
            // Creates a custom action with user-provided HTML and handler
            return createCustomAction(action);
            
        default:
            // Unknown action type - log warning and return null
            console.warn(`Unknown action type: ${action.type}`);
            return null;
    }
}

/**
 * Create theme toggle element
 * 
 * Creates a checkbox-based theme toggle control. The actual theme switching
 * logic is handled by theme.js, which should be included on the page.
 * The toggle uses the 'toggle' class for styling from header.css.
 * 
 * @returns {HTMLElement} - Theme toggle label element containing checkbox
 * 
 * @example
 * const toggle = createThemeToggle();
 * container.appendChild(toggle);
 */
function createThemeToggle() {
    // Create label wrapper for the toggle
    const label = document.createElement('label');
    label.className = 'toggle';  // Styled by header.css
    label.style.position = 'relative';
    label.style.cursor = 'pointer';

    // Create checkbox input
    // The 'theme-toggle' ID is used by theme.js to attach event listeners
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.id = 'theme-toggle';

    // Create visual toggle element (styled by CSS)
    const div = document.createElement('div');

    // Assemble the toggle structure
    label.appendChild(input);
    label.appendChild(div);

    return label;
}

/**
 * Create user info element
 * 
 * Creates a span element that displays user information (typically email).
 * Attempts to retrieve user info from auth.js getUserInfo() function.
 * Falls back to provided label or empty string if auth.js is unavailable.
 * 
 * @param {string} [label] - Optional user label to display (overrides getUserInfo)
 * @returns {HTMLElement} - Span element containing user info
 * 
 * @example
 * // Auto-fetch from auth.js
 * const userInfo = createUserInfo();
 * 
 * @example
 * // Use custom label
 * const userInfo = createUserInfo('admin@example.com');
 */
function createUserInfo(label) {
    // Create span element for displaying user info
    const span = document.createElement('span');
    span.className = 'app-header__user-info';
    span.id = 'header-user-email';
    span.style.fontSize = '0.875rem';
    span.style.color = 'var(--text-secondary)';

    // Attempt to get user info from auth.js
    // This requires auth.js to be loaded and expose getUserInfo function
    try {
        // Check if getUserInfo function exists (from auth.js)
        if (typeof getUserInfo === 'function') {
            const userInfo = getUserInfo();
            // Priority: provided label > user email from auth > empty string
            span.textContent = label || userInfo?.email || '';
        } else {
            // auth.js not loaded or getUserInfo not available
            console.warn('getUserInfo function not available from auth.js');
            span.textContent = label || '';
        }
    } catch (error) {
        // Handle any errors during user info retrieval
        console.error('Error getting user info:', error);
        span.textContent = label || '';
    }

    return span;
}

/**
 * Create logout button element
 * 
 * Creates a styled logout button that calls the logout() function from auth.js.
 * Includes hover effects and fallback behavior if auth.js is unavailable.
 * The button uses CSS custom properties for theming support.
 * 
 * @returns {HTMLElement} - Logout button element
 * 
 * @example
 * const logoutBtn = createLogoutButton();
 * container.appendChild(logoutBtn);
 */
function createLogoutButton() {
    // Create button element
    const button = document.createElement('button');
    button.className = 'app-header__logout-btn';
    button.id = 'header-logout-button';
    button.textContent = 'Logout';
    
    // Apply inline styles using CSS custom properties for theme support
    button.style.padding = '0.5rem 1rem';
    button.style.borderRadius = '6px';
    button.style.border = '1px solid var(--card-border)';
    button.style.background = 'var(--card-bg)';
    button.style.color = 'var(--text-primary)';
    button.style.cursor = 'pointer';
    button.style.fontSize = '0.875rem';
    button.style.fontWeight = '500';
    button.style.transition = 'all 0.2s ease';

    // Add hover effect via event listeners
    // Using event listeners instead of :hover pseudo-class for dynamic styling
    button.addEventListener('mouseenter', () => {
        button.style.background = 'var(--hover-bg, rgba(107, 125, 187, 0.1))';
    });

    button.addEventListener('mouseleave', () => {
        button.style.background = 'var(--card-bg)';
    });

    // Add click handler for logout functionality
    button.addEventListener('click', (e) => {
        e.preventDefault();  // Prevent any default button behavior
        
        try {
            // Attempt to call logout function from auth.js
            if (typeof logout === 'function') {
                logout();  // Handles token clearing and redirect
            } else {
                // auth.js not loaded - log error and use fallback
                console.error('Logout function not available from auth.js');
                // Fallback: redirect to login page
                window.location.href = '/login';
            }
        } catch (error) {
            // Handle any errors during logout process
            console.error('Error during logout:', error);
            // Fallback: redirect to login page
            window.location.href = '/login';
        }
    });

    return button;
}

/**
 * Create custom action element
 * 
 * Creates a custom action element with user-provided HTML and click handler.
 * This allows for flexible extension of the header with custom buttons,
 * links, or other interactive elements.
 * 
 * @param {ActionIcon} action - Custom action configuration
 * @param {string} action.html - HTML string to render inside the action
 * @param {Function} [action.onClick] - Optional click handler function
 * @returns {HTMLElement} - Custom action container element
 * 
 * @example
 * // Simple custom button
 * const helpBtn = createCustomAction({
 *     html: '<button class="help-btn">Help</button>',
 *     onClick: () => window.location.href = '/help'
 * });
 * 
 * @example
 * // Custom link without click handler
 * const link = createCustomAction({
 *     html: '<a href="/about">About</a>'
 * });
 */
function createCustomAction(action) {
    // Create container div for the custom action
    const container = document.createElement('div');
    container.className = 'app-header__custom-action';

    // Set custom HTML content if provided
    // Using innerHTML allows for flexible HTML structures
    if (action.html) {
        container.innerHTML = action.html;
    }

    // Add click handler if provided
    // This allows custom actions to have interactive behavior
    if (action.onClick && typeof action.onClick === 'function') {
        container.addEventListener('click', (e) => {
            try {
                // Call the user-provided click handler
                // Pass the event object for flexibility
                action.onClick(e);
            } catch (error) {
                // Log errors but don't break the page
                console.error('Error in custom action click handler:', error);
            }
        });
        
        // Add pointer cursor to indicate clickability
        container.style.cursor = 'pointer';
    }

    return container;
}

// Expose functions globally
window.initializeHeader = initializeHeader;
window.renderActions = renderActions;
window.createActionElement = createActionElement;
window.createThemeToggle = createThemeToggle;
window.createUserInfo = createUserInfo;
window.createLogoutButton = createLogoutButton;
window.createCustomAction = createCustomAction;
