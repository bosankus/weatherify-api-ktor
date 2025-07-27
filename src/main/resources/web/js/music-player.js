/**
 * Music player functionality for Weatherify web pages
 * Handles background music playback and persistence across pages
 */

/**
 * Initialize background music
 * Sets up the music player and controls
 */
function initializeBackgroundMusic() {
    try {
        const musicContainer = document.getElementById('music-container');
        const audioElement = document.getElementById('background-music');
        const musicToggle = document.getElementById('music-toggle');
        
        if (!musicContainer || !audioElement || !musicToggle) {
            console.warn('Music elements not found');
            return;
        }
        
        // Check if music should be playing from localStorage
        const musicState = localStorage.getItem('musicPlaying');
        const shouldPlay = musicState === null ? true : musicState === 'true';
        
        // Set initial state
        musicToggle.checked = !shouldPlay;
        
        // Function to play/pause music
        function toggleMusic() {
            if (audioElement.paused) {
                audioElement.play()
                    .then(() => {
                        console.log('Music started playing');
                        localStorage.setItem('musicPlaying', 'true');
                    })
                    .catch(error => {
                        console.warn('Could not play music automatically:', error);
                        // Keep the toggle in paused state if autoplay fails
                        musicToggle.checked = true;
                        localStorage.setItem('musicPlaying', 'false');
                    });
            } else {
                audioElement.pause();
                console.log('Music paused');
                localStorage.setItem('musicPlaying', 'false');
            }
        }
        
        // Add event listener to toggle button
        musicToggle.addEventListener('change', function() {
            toggleMusic();
        });
        
        // Add click event listener to the music toggle label for better touch/click response
        const musicToggleLabel = document.querySelector('.music-toggle');
        if (musicToggleLabel) {
            musicToggleLabel.addEventListener('click', function(e) {
                // Prevent the click from triggering the checkbox's change event twice
                if (e.target !== musicToggle) {
                    e.preventDefault();
                    musicToggle.checked = !musicToggle.checked;
                    toggleMusic();
                }
            });
        }
        
        // Try to autoplay music if it should be playing
        if (shouldPlay) {
            audioElement.play()
                .then(() => {
                    console.log('Music started playing automatically');
                })
                .catch(error => {
                    console.warn('Could not autoplay music:', error);
                    // Many browsers block autoplay, so we'll need user interaction
                    musicToggle.checked = true;
                    
                    // Add a one-time click listener to the document to start music
                    document.addEventListener('click', function musicStartHandler() {
                        if (localStorage.getItem('musicPlaying') === 'true') {
                            audioElement.play()
                                .then(() => {
                                    console.log('Music started after user interaction');
                                    musicToggle.checked = false;
                                })
                                .catch(err => {
                                    console.error('Still could not play music:', err);
                                });
                        }
                        // Remove the listener after first interaction
                        document.removeEventListener('click', musicStartHandler);
                    }, { once: true });
                });
        }
    } catch (error) {
        console.error('Error initializing background music:', error);
    }
}