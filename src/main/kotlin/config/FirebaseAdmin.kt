package config

import bose.ankush.util.getSecretValue
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import util.Constants
import java.io.ByteArrayInputStream
import java.io.FileInputStream

/**
 * Firebase Admin SDK initialization singleton.
 * Handles initialization of Firebase for sending push notifications.
 */
object FirebaseAdmin {
    private val logger = LoggerFactory.getLogger(FirebaseAdmin::class.java)
    private var initialized = false

    /**
     * Initialize Firebase Admin SDK.
     * This should be called once during application startup.
     *
     * The service account key can be configured via:
     * 1. Google Cloud Secret Manager: firebase-service-account-key (recommended for production)
     * 2. Environment variable: FIREBASE_SERVICE_ACCOUNT_KEY (path to JSON file)
     * 3. Default location: ./serviceAccountKey.json
     *
     * If the service account key is not found, initialization will be skipped
     * and notifications will not be sent.
     */
    fun initialize() {
        if (initialized) {
            logger.info("Firebase Admin SDK already initialized")
            return
        }

        try {
            // Try to get from Secret Manager first (for production)
            val serviceAccountJson = try {
                val secret = getSecretValue(Constants.Auth.FIREBASE_SERVICE_ACCOUNT_KEY)
                if (secret.isNotBlank() && !secret.startsWith("dummy_") && !secret.startsWith("fallback_")) {
                    logger.info("Loading Firebase credentials from Secret Manager")
                    secret
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.debug("Could not load Firebase credentials from Secret Manager: ${e.message}")
                null
            }

            val credentialsStream = if (serviceAccountJson != null) {
                // Use credentials from Secret Manager
                ByteArrayInputStream(serviceAccountJson.toByteArray())
            } else {
                // Fallback to file-based credentials
                val serviceAccountPath = System.getenv("FIREBASE_SERVICE_ACCOUNT_KEY")
                    ?: "./serviceAccountKey.json"

                val serviceAccountFile = java.io.File(serviceAccountPath)

                if (!serviceAccountFile.exists()) {
                    logger.warn(
                        "Firebase service account key not found at: $serviceAccountPath. " +
                            "Push notifications will be disabled. " +
                            "Configure firebase-service-account-key in Secret Manager or " +
                            "set FIREBASE_SERVICE_ACCOUNT_KEY environment variable or " +
                            "place serviceAccountKey.json in the root directory."
                    )
                    return
                }

                logger.info("Loading Firebase credentials from file: $serviceAccountPath")
                FileInputStream(serviceAccountFile)
            }

            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                .build()

            FirebaseApp.initializeApp(options)
            initialized = true
            logger.info("Firebase Admin SDK initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize Firebase Admin SDK", e)
            logger.warn("Push notifications will be disabled")
        }
    }

    /**
     * Check if Firebase Admin SDK is initialized and ready to send notifications.
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean = initialized
}
