package util

import com.androidplay.core.secrets.getSecretValue
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Low-level profile photo object storage (GCS / Firebase Storage bucket).
 * Object keys are UUIDs only. The user document stores the object name, never bytes or URLs.
 */
interface ProfilePhotoStorage {
    /** True when credentials are loaded and the client can talk to the bucket. */
    fun isAvailable(): Boolean

    /** Upload or overwrite [objectName] with [bytes]. */
    fun upload(objectName: String, bytes: ByteArray, contentType: String)

    /** V4 signed GET URL for [objectName], valid for [ttlMinutes]. */
    fun signedUrl(objectName: String, ttlMinutes: Long = 15): String

    /** Delete [objectName] if it exists. Missing objects are not an error. */
    fun delete(objectName: String)
}

/**
 * Object-key policy for profile photos: reuse an existing non-blank key, otherwise mint a UUID.
 * Unit-tested; no I/O.
 */
object ProfilePhotoKeys {
    fun reuseOrCreate(existing: String?): String {
        val trimmed = existing?.trim().orEmpty()
        return if (trimmed.isNotEmpty()) trimmed else UUID.randomUUID().toString()
    }
}

/**
 * GCS-backed [ProfilePhotoStorage] using the existing firebase-service-account-key secret.
 * Bucket: weatherify-mvvm.firebasestorage.app (Architect-confirmed; do not create a bucket).
 * Fail-closed when the service account JSON is blank or unusable — no fake URLs.
 */
class GcsProfilePhotoStorage(
    private val bucketName: String = BUCKET_NAME,
    private val storageFactory: () -> Storage? = ::defaultStorage
) : ProfilePhotoStorage {
    private val logger = LoggerFactory.getLogger(GcsProfilePhotoStorage::class.java)

    @Volatile
    private var storage: Storage? = null

    private fun client(): Storage? {
        storage?.let { return it }
        synchronized(this) {
            storage?.let { return it }
            val created = try {
                storageFactory()
            } catch (e: Exception) {
                logger.error("Failed to create GCS client for profile photos", e)
                null
            }
            storage = created
            return created
        }
    }

    override fun isAvailable(): Boolean = client() != null

    override fun upload(objectName: String, bytes: ByteArray, contentType: String) {
        val gcs = client() ?: error(UNAVAILABLE)
        require(objectName.isNotBlank()) { "objectName is required" }
        require(bytes.isNotEmpty()) { "photo bytes are required" }
        val info = BlobInfo.newBuilder(BlobId.of(bucketName, objectName))
            .setContentType(contentType.ifBlank { "application/octet-stream" })
            .build()
        gcs.create(info, bytes)
    }

    override fun signedUrl(objectName: String, ttlMinutes: Long): String {
        val gcs = client() ?: error(UNAVAILABLE)
        require(objectName.isNotBlank()) { "objectName is required" }
        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, objectName)).build()
        return gcs.signUrl(
            blobInfo,
            ttlMinutes,
            TimeUnit.MINUTES,
            Storage.SignUrlOption.withV4Signature(),
            Storage.SignUrlOption.httpMethod(com.google.cloud.storage.HttpMethod.GET)
        ).toString()
    }

    override fun delete(objectName: String) {
        val gcs = client() ?: error(UNAVAILABLE)
        if (objectName.isBlank()) return
        gcs.delete(BlobId.of(bucketName, objectName))
    }

    companion object {
        const val BUCKET_NAME = "weatherify-mvvm.firebasestorage.app"
        const val UNAVAILABLE =
            "Profile photo storage is unavailable: firebase service account is not configured"

        private fun defaultStorage(): Storage? {
            val logger = LoggerFactory.getLogger(GcsProfilePhotoStorage::class.java)
            val serviceAccountJson = try {
                val secret = getSecretValue(Constants.Auth.FIREBASE_SERVICE_ACCOUNT_KEY)
                if (secret.isNotBlank() &&
                    !secret.startsWith("dummy_") &&
                    !secret.startsWith("fallback_")
                ) {
                    secret
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.debug("Could not load firebase-service-account-key: ${e.message}")
                null
            }

            val credentials = when {
                serviceAccountJson != null -> {
                    GoogleCredentials.fromStream(ByteArrayInputStream(serviceAccountJson.toByteArray()))
                        .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
                }
                else -> {
                    val path = System.getenv("FIREBASE_SERVICE_ACCOUNT_KEY") ?: "./serviceAccountKey.json"
                    val file = java.io.File(path)
                    if (!file.exists()) {
                        logger.warn("Profile photo storage fail-closed: no firebase service account JSON")
                        return null
                    }
                    GoogleCredentials.fromStream(file.inputStream())
                        .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
                }
            }

            return StorageOptions.newBuilder()
                .setCredentials(credentials)
                .build()
                .service
        }
    }
}

/**
 * Orchestrates profile photo upload / signed URL / delete against storage + user row.
 * Object key is reused on update; delete removes the GCS object and clears [photoObject].
 */
class ProfilePhotoActions(
    private val storage: ProfilePhotoStorage,
    private val userRepository: com.androidplay.weatherify.repository.UserRepository
) {
    data class UploadResult(val objectKey: String, val photoUrl: String)

    suspend fun upload(
        email: String,
        bytes: ByteArray,
        contentType: String,
        filename: String? = null
    ): com.androidplay.core.common.Result<UploadResult> {
        if (!storage.isAvailable()) {
            return com.androidplay.core.common.Result.error(GcsProfilePhotoStorage.UNAVAILABLE)
        }
        val user = when (val found = userRepository.findUserByEmail(email)) {
            is com.androidplay.core.common.Result.Success -> found.data
                ?: return com.androidplay.core.common.Result.error("User not found")
            is com.androidplay.core.common.Result.Error ->
                return com.androidplay.core.common.Result.error(found.message, found.exception)
        }
        val prepared = try {
            ProfilePhotoBytes.prepare(bytes, contentType, filename)
        } catch (e: ProfilePhotoRejectedException) {
            return com.androidplay.core.common.Result.error(e.message ?: "Unsupported photo")
        }
        val previousKey = user.photoObject?.trim().orEmpty()
        val objectKey = ProfilePhotoKeys.reuseOrCreate(previousKey)
        val createdNewObject = previousKey.isEmpty()
        return try {
            storage.upload(objectKey, prepared.bytes, prepared.contentType)
            when (val updated = userRepository.updatePhotoObjectByEmail(email, objectKey)) {
                is com.androidplay.core.common.Result.Success -> {
                    val url = storage.signedUrl(objectKey)
                    com.androidplay.core.common.Result.success(UploadResult(objectKey, url))
                }
                is com.androidplay.core.common.Result.Error -> {
                    if (createdNewObject) {
                        deleteOrphan(objectKey)
                    }
                    com.androidplay.core.common.Result.error(updated.message, updated.exception)
                }
            }
        } catch (e: Exception) {
            com.androidplay.core.common.Result.error(
                "Failed to store profile photo: ${e.message}",
                e
            )
        }
    }

    private fun deleteOrphan(objectKey: String) {
        try {
            storage.delete(objectKey)
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(ProfilePhotoActions::class.java)
                .warn("Failed to delete orphan profile photo object: ${e.message}")
        }
    }

    suspend fun signedUrlFor(email: String): com.androidplay.core.common.Result<String?> {
        if (!storage.isAvailable()) {
            return com.androidplay.core.common.Result.error(GcsProfilePhotoStorage.UNAVAILABLE)
        }
        val user = when (val found = userRepository.findUserByEmail(email)) {
            is com.androidplay.core.common.Result.Success -> found.data
                ?: return com.androidplay.core.common.Result.error("User not found")
            is com.androidplay.core.common.Result.Error ->
                return com.androidplay.core.common.Result.error(found.message, found.exception)
        }
        val key = user.photoObject?.trim().orEmpty()
        if (key.isEmpty()) {
            return com.androidplay.core.common.Result.success(null)
        }
        return try {
            com.androidplay.core.common.Result.success(storage.signedUrl(key))
        } catch (e: Exception) {
            com.androidplay.core.common.Result.error(
                "Failed to sign profile photo URL: ${e.message}",
                e
            )
        }
    }

    /**
     * Deletes the GCS object (when present) and clears photoObject on the user document.
     */
    suspend fun delete(email: String): com.androidplay.core.common.Result<Boolean> {
        if (!storage.isAvailable()) {
            return com.androidplay.core.common.Result.error(GcsProfilePhotoStorage.UNAVAILABLE)
        }
        val user = when (val found = userRepository.findUserByEmail(email)) {
            is com.androidplay.core.common.Result.Success -> found.data
                ?: return com.androidplay.core.common.Result.error("User not found")
            is com.androidplay.core.common.Result.Error ->
                return com.androidplay.core.common.Result.error(found.message, found.exception)
        }
        val key = user.photoObject?.trim().orEmpty()
        return try {
            if (key.isNotEmpty()) {
                storage.delete(key)
            }
            userRepository.clearPhotoObjectByEmail(email)
        } catch (e: Exception) {
            com.androidplay.core.common.Result.error(
                "Failed to delete profile photo: ${e.message}",
                e
            )
        }
    }
}
