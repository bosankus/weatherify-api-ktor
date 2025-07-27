/**
 * Utility functions for Weatherify web pages
 * Contains common helper functions used across multiple pages
 */

/**
 * Initialize the application
 * Sets up theme toggle, music player, and other common functionality
 */
function initializeApp() {
    try {
        // Initialize theme
        initializeTheme();
        
        // Initialize background music
        initializeBackgroundMusic();
        
        // Initialize click feedback for cards and other elements
        initializeClickFeedback();
        
        // Initialize navigation icons
        initializeNavIcons();
        
        // Initialize event listeners for cards and modal
        if (typeof initializeEventListeners === 'function') {
            initializeEventListeners();
        }
        
        // Add any other initialization here
    } catch (error) {
        console.error('Error during application initialization:', error);
    }
}

/**
 * Initialize navigation icons
 * Adds click event listeners to elements with the nav-icon class
 */
function initializeNavIcons() {
    try {
        const navIcons = document.querySelectorAll('.nav-icon[data-url]');
        
        if (navIcons.length === 0) {
            console.warn('No navigation icons found');
            return;
        }
        
        navIcons.forEach(icon => {
            icon.addEventListener('click', function() {
                const url = this.getAttribute('data-url');
                if (url) {
                    window.open(url, '_blank');
                }
            });
        });
        
        console.log('Navigation icons initialized:', navIcons.length);
    } catch (error) {
        console.error('Error initializing navigation icons:', error);
    }
}

/**
 * Add ripple effect for click feedback on interactive elements
 * @param {HTMLElement} element - The element to add click feedback to
 */
function addClickFeedback(element) {
    if (!element) return;

    element.addEventListener('click', function(e) {
        try {
            // Create ripple element
            const ripple = document.createElement('div');
            ripple.className = 'ripple';

            // Position the ripple
            const rect = element.getBoundingClientRect();
            const size = Math.max(rect.width, rect.height);
            const x = e.clientX - rect.left - size / 2;
            const y = e.clientY - rect.top - size / 2;

            ripple.style.width = ripple.style.height = size + 'px';
            ripple.style.left = x + 'px';
            ripple.style.top = y + 'px';

            // Add ripple to element
            element.appendChild(ripple);

            // Remove ripple after animation
            setTimeout(() => {
                if (ripple.parentNode === element) {
                    element.removeChild(ripple);
                }
            }, 600);
        } catch (error) {
            console.error('Error adding click feedback:', error);
        }
    });
}

/**
 * Initialize click feedback for interactive elements
 * Adds ripple effect to buttons, cards, and other clickable elements
 */
function initializeClickFeedback() {
    const clickableElements = document.querySelectorAll(
        '.card, .endpoint, .nav-icon, .close, .toggle, .music-toggle'
    );

    if (clickableElements.length === 0) {
        console.warn('No clickable elements found for feedback');
        return;
    }

    clickableElements.forEach(addClickFeedback);
}

/**
 * Get transition duration from computed style
 * @param {HTMLElement} element - The element to check
 * @returns {number} - Transition duration in milliseconds
 */
function getTransitionDuration(element) {
    if (!element) return 300;

    try {
        const style = window.getComputedStyle(element);
        const duration = style.transitionDuration || '0.3s';

        // Convert to milliseconds
        if (duration.indexOf('ms') > -1) {
            return parseFloat(duration);
        } else if (duration.indexOf('s') > -1) {
            return parseFloat(duration) * 1000;
        }
        return 300;
    } catch (error) {
        console.warn('Error getting transition duration:', error);
        return 300;
    }
}