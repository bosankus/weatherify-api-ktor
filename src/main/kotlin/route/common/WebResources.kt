package bose.ankush.route.common

import kotlinx.html.HEAD
import kotlinx.html.script
import kotlinx.html.style
import kotlinx.html.unsafe

/**
 * Helper class for including shared web resources in HTML responses
 */
object WebResources {

    // CSS file contents
    private val baseCss = readResourceFile("/web/css/base.css")
    private val componentsCss = readResourceFile("/web/css/components.css")
    private val themeToggleCss = readResourceFile("/web/css/theme-toggle.css")
    private val musicPlayerCss = readResourceFile("/web/css/music-player.css")

    // JavaScript file contents
    private val themeJs = readResourceFile("/web/js/theme.js")
    private val musicPlayerJs = readResourceFile("/web/js/music-player.js")
    private val utilsJs = readResourceFile("/web/js/utils.js")

    /**
     * Read a file from the resources directory
     * @param path The path to the file in the resources directory
     * @return The contents of the file as a string
     */
    private fun readResourceFile(path: String): String {
        return try {
            val resourceUrl = WebResources::class.java.getResource(path)
            if (resourceUrl != null) {
                resourceUrl.readText()
            } else {
                println("Resource not found: $path")
                ""
            }
        } catch (e: Exception) {
            println("Error reading resource: $path - ${e.message}")
            ""
        }
    }

    /**
     * Include shared CSS in the HTML head
     * @param head The HEAD element to add the CSS to
     */
    fun includeSharedCss(head: HEAD) {
        // Include all CSS directly in the HTML
        head.style {
            unsafe {
                raw(baseCss)
                raw(componentsCss)
                raw(themeToggleCss)
                raw(musicPlayerCss)
            }
        }
    }

    /**
     * Include shared JavaScript in the HTML head
     * @param head The HEAD element to add the JavaScript to
     */
    fun includeSharedJs(head: HEAD) {
        // Include all JavaScript directly in the HTML
        head.script {
            unsafe {
                raw(themeJs)
                raw(musicPlayerJs)
                raw(utilsJs)

                // Initialize the app with a single DOMContentLoaded event listener
                raw(
                    """
                    // Single initialization point for the entire application
                    document.addEventListener('DOMContentLoaded', function() {
                        // Ensure music container exists before initializing
                        ensureMusicContainerExists();
                        
                        // Initialize all application components
                        initializeApp();
                    });
                    
                    // Function to ensure music container exists
                    function ensureMusicContainerExists() {
                        if (!document.getElementById('music-container')) {
                            const musicContainer = document.createElement('div');
                            musicContainer.id = 'music-container';
                            musicContainer.style.display = 'none';
                            
                            const audioElement = document.createElement('audio');
                            audioElement.id = 'background-music';
                            audioElement.loop = true;
                            audioElement.preload = 'auto';
                            
                            const sourceElement = document.createElement('source');
                            sourceElement.src = 'https://assets.mixkit.co/music/preview/mixkit-tech-house-vibes-130.mp3';
                            sourceElement.type = 'audio/mpeg';
                            
                            audioElement.appendChild(sourceElement);
                            musicContainer.appendChild(audioElement);
                            document.body.appendChild(musicContainer);
                            
                            console.log('Music container created');
                        }
                    }
                """
                )
            }
        }
    }
}