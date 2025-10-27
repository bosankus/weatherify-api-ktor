function initializeAdminHeader(config = {}) {
  try {
    // Validate config
    if (config && typeof config !== 'object') {
      console.warn('Invalid admin header config, using defaults');
      config = {};
    }

    // Set defaults
    const defaults = {
      title: 'Androidplay',
      subtitle: 'ADMIN PORTAL'
    };

    const settings = { ...defaults, ...config };

    // Update title and subtitle
    updateHeaderText(settings.title, settings.subtitle);

    // Initialize theme toggle (delegates to theme.js)
    initializeAdminTheme();

  } catch (error) {
    console.error('Error initializing admin header:', error);
    // Component fails gracefully - header displays with default HTML
  }
}

/**
 * Update the header title and subtitle text dynamically
 *
 * This helper function updates the DOM elements for the title and subtitle.
 * It can be called after initialization to change the header text dynamically.
 *
 * @param {string} title - The title text to display
 * @param {string} subtitle - The subtitle text to display (will be displayed in uppercase)
 *
 * @example
 * // Change header text after page load
 * updateHeaderText('New Title', 'NEW SUBTITLE');
 */
function updateHeaderText(title, subtitle) {
  try {
    const titleEl = document.getElementById('admin-header-title');
    const subtitleEl = document.getElementById('admin-header-subtitle');

    if (titleEl) {
      titleEl.textContent = title;
    } else {
      console.warn('Admin header title element not found');
    }

    if (subtitleEl) {
      subtitleEl.textContent = subtitle;
    } else {
      console.warn('Admin header subtitle element not found');
    }
  } catch (error) {
    console.error('Error updating header text:', error);
  }
}

/**
 * Initialize theme toggle functionality for admin header
 *
 * This function integrates with the existing theme.js module to provide
 * consistent theme management across the application. The theme toggle
 * checkbox in the admin header (ID: 'admin-theme-toggle') is automatically
 * detected and managed by theme.js.
 *
 * Theme preferences are persisted in localStorage and restored on page load.
 *
 * NOTE: Requires theme.js to be loaded before this function is called.
 * If theme.js is not available, a warning is logged but the component
 * continues to function (theme toggle just won't work).
 */
function initializeAdminTheme() {
  try {
    // Check if theme.js is available
    if (typeof initializeTheme === 'function') {
      // Use the existing theme.js initialization
      initializeTheme();
    } else {
      console.warn('theme.js not loaded - theme toggle will not function');
    }
  } catch (error) {
    console.error('Error initializing admin theme:', error);
  }
}

// Expose functions globally on window object
if (typeof window !== 'undefined') {
  window.initializeAdminHeader = initializeAdminHeader;
  window.updateHeaderText = updateHeaderText;
  window.initializeAdminTheme = initializeAdminTheme;
}
