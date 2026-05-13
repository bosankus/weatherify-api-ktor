package com.transloom.repository.mongo

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.domain.User
import com.transloom.repository.UserRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import org.bson.Document
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MongoUserRepository(
    db: MongoDatabase,
    private val encryptionKey: String
) : UserRepository {

    private val collection = db.getCollection<Document>("transloom_users")

    // Derive a 32-byte AES-256 key from the provided string via SHA-256
    private val aesKey: SecretKeySpec by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(encryptionKey.toByteArray(Charsets.UTF_8))
        SecretKeySpec(keyBytes, "AES")
    }

    private fun encrypt(plaintext: String): String {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.getDecoder().decode(encoded)
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    override suspend fun upsert(
        githubId: Long,
        username: String,
        email: String?,
        avatarUrl: String?,
        githubToken: String?
    ): User {
        val now = Clock.System.now().toEpochMilliseconds()
        val encryptedToken = githubToken?.let { encrypt(it) }

        val setFields = mutableListOf(
            Updates.set("githubId", githubId),
            Updates.set("githubUsername", username),
            Updates.set("updatedAt", now)
        )
        email?.let { setFields.add(Updates.set("email", it)) }
        avatarUrl?.let { setFields.add(Updates.set("avatarUrl", it)) }
        encryptedToken?.let { setFields.add(Updates.set("githubToken", it)) }

        val update = Updates.combine(
            Updates.combine(setFields),
            Updates.setOnInsert("_id", UUID.randomUUID().toString()),
            Updates.setOnInsert("createdAt", now)
        )

        val options = FindOneAndUpdateOptions()
            .upsert(true)
            .returnDocument(ReturnDocument.AFTER)

        val doc = collection.findOneAndUpdate(eq("githubId", githubId), update, options)
            ?: error("upsert returned null for githubId=$githubId")

        return doc.toUser()
    }

    override suspend fun findByGithubId(githubId: Long): User? {
        return collection.find(eq("githubId", githubId)).firstOrNull()?.toUser()
    }

    override suspend fun findById(userId: String): User? {
        return collection.find(eq("_id", userId)).firstOrNull()?.toUser()
    }

    private fun Document.toUser(): User {
        val rawToken = getString("githubToken")
        val decryptedToken = rawToken?.let {
            try { decrypt(it) } catch (_: Exception) { null }
        }
        return User(
            id = getString("_id"),
            githubId = getLong("githubId"),
            githubUsername = getString("githubUsername") ?: "",
            email = getString("email"),
            githubToken = decryptedToken,
            avatarUrl = getString("avatarUrl")
        )
    }
}
