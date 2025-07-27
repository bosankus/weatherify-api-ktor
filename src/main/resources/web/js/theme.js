/**
 * Theme toggle functionality for Weatherify web pages
 * Handles switching between light and dark themes with animation
 */

/**
 * Toggle between light and dark themes
 * Uses View Transitions API if available, with fallbacks for other browsers
 */
function toggleTheme() {
    try {
        const htmlElement = document.documentElement;
        const isDarkTheme = !htmlElement.classList.contains('light-theme');

        // Get toggle position for animation
        const toggleElement = document.querySelector('.toggle');
        let x = window.innerWidth / 2;
        let y = window.innerHeight / 2;

        if (toggleElement) {
            const rect = toggleElement.getBoundingClientRect();
            x = rect.left + rect.width / 2;
            y = rect.top + rect.height / 2;
        }

        // Set animation variables
        htmlElement.style.setProperty('--x', x + 'px');
        htmlElement.style.setProperty('--y', y + 'px');

        // Enhanced animation approach that works across browsers
        // Create a fade overlay with circular reveal effect
        const overlay = document.createElement('div');
        overlay.id = 'theme-transition-overlay';
        overlay.style.position = 'fixed';
        overlay.style.top = '0';
        overlay.style.left = '0';
        overlay.style.width = '100%';
        overlay.style.height = '100%';
        overlay.style.backgroundColor = isDarkTheme ? '#ffffff' : '#1a1a2e';
        overlay.style.opacity = '0';
        overlay.style.zIndex = '9999';
        overlay.style.pointerEvents = 'none';
        overlay.style.transition = 'opacity 0.3s ease';
        
        // Add circular reveal effect using clip-path
        const clipPath = `circle(0% at ${x}px ${y}px)`;
        overlay.style.clipPath = clipPath;
        overlay.style.webkitClipPath = clipPath; // For Safari support
        
        // Remove any existing overlay first to prevent duplicates
        const existingOverlay = document.getElementById('theme-transition-overlay');
        if (existingOverlay) {
            document.body.removeChild(existingOverlay);
        }

        document.body.appendChild(overlay);

        // Force browser to process the DOM changes
        overlay.getBoundingClientRect();

        // Use requestAnimationFrame for smoother animation
        requestAnimationFrame(() => {
            // First make the overlay visible
            overlay.style.opacity = '0.5';
            
            // Then expand the clip-path for circular reveal
            requestAnimationFrame(() => {
                const expandedClipPath = `circle(150% at ${x}px ${y}px)`;
                overlay.style.clipPath = expandedClipPath;
                overlay.style.webkitClipPath = expandedClipPath;
                
                // Apply theme change after animation starts
                setTimeout(() => {
                    if (isDarkTheme) {
                        htmlElement.classList.add('light-theme');
                    } else {
                        htmlElement.classList.remove('light-theme');
                    }
                    
                    // Fade out overlay
                    setTimeout(() => {
                        overlay.style.opacity = '0';
                        
                        // Remove overlay after animation completes
                        setTimeout(() => {
                            if (document.body.contains(overlay)) {
                                document.body.removeChild(overlay);
                            }
                        }, 300);
                    }, 150);
                }, 150);
            });
        });

        // Save preference to localStorage
        localStorage.setItem('theme', isDarkTheme ? 'light' : 'dark');
    } catch (error) {
        console.error('Error toggling theme:', error);
        // Fallback to basic theme toggle without animation
        try {
            const htmlElement = document.documentElement;
            const isDarkTheme = !htmlElement.classList.contains('light-theme');
            if (isDarkTheme) {
                htmlElement.classList.add('light-theme');
            } else {
                htmlElement.classList.remove('light-theme');
            }
            localStorage.setItem('theme', isDarkTheme ? 'light' : 'dark');
        } catch (e) {
            console.error('Critical error in theme toggle:', e);
        }
    }
}

/**
 * Initialize theme toggle functionality
 * Sets up the theme based on saved preference or system preference
 */
function initializeTheme() {
    try {
        const themeToggle = document.getElementById('theme-toggle');
        if (!themeToggle) {
            console.warn('Theme toggle element not found');
            return;
        }

        // Check for saved preference or system preference
        const savedTheme = localStorage.getItem('theme');
        const prefersDark = window.matchMedia && 
            window.matchMedia('(prefers-color-scheme: dark)').matches;

        // Apply saved theme or system preference immediately
        if (savedTheme === 'light' || (!savedTheme && !prefersDark)) {
            document.documentElement.classList.add('light-theme');
            if (themeToggle) themeToggle.checked = true;
        } else {
            document.documentElement.classList.remove('light-theme');
            if (themeToggle) themeToggle.checked = false;
        }

        // Add event listener for toggle
        themeToggle.addEventListener('change', toggleTheme);
        
        // Add click event listener to the theme toggle label for better touch/click response
        const themeToggleLabel = document.querySelector('.toggle');
        if (themeToggleLabel) {
            themeToggleLabel.addEventListener('click', function(e) {
                // Prevent the click from triggering the checkbox's change event twice
                if (e.target !== themeToggle) {
                    e.preventDefault();
                    themeToggle.checked = !themeToggle.checked;
                    toggleTheme();
                }
            });
        }

        // Listen for system theme changes
        if (window.matchMedia) {
            const colorSchemeQuery = window.matchMedia('(prefers-color-scheme: dark)');
            if (colorSchemeQuery.addEventListener) {
                colorSchemeQuery.addEventListener('change', (e) => {
                    if (!localStorage.getItem('theme')) {
                        if (e.matches) {
                            document.documentElement.classList.remove('light-theme');
                            themeToggle.checked = false;
                        } else {
                            document.documentElement.classList.add('light-theme');
                            themeToggle.checked = true;
                        }
                    }
                });
            } else if (colorSchemeQuery.addListener) {
                // Fallback for Safari
                colorSchemeQuery.addListener((e) => {
                    if (!localStorage.getItem('theme')) {
                        if (e.matches) {
                            document.documentElement.classList.remove('light-theme');
                            themeToggle.checked = false;
                        } else {
                            document.documentElement.classList.add('light-theme');
                            themeToggle.checked = true;
                        }
                    }
                });
            }
        }
    } catch (error) {
        console.error('Error initializing theme:', error);
    }
}