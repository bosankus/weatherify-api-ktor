package com.transloom.repository.mongo

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.lt
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.transloom.domain.OnboardingStep
import com.transloom.domain.User
import com.transloom.repository.UserRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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
    ): UserRepository.UpsertResult {
        val now = Clock.System.now().toEpochMilliseconds()
        val encryptedToken = githubToken?.let { encrypt(it) }

        // Detect insert vs update before mutating — `setOnInsert` doesn't expose this
        // distinction in the returned document, and we need it for SIGNED_UP vs LOGGED_IN.
        val existed = collection.find(eq("githubId", githubId)).firstOrNull() != null

        val setFields = mutableListOf(
            Updates.set("githubId", githubId),
            Updates.set("githubUsername", username),
            Updates.set("updatedAt", now),
            Updates.set("lastActiveAt", now)
        )
        email?.let { setFields.add(Updates.set("email", it)) }
        avatarUrl?.let { setFields.add(Updates.set("avatarUrl", it)) }
        encryptedToken?.let { setFields.add(Updates.set("githubToken", it)) }

        val update = Updates.combine(
            Updates.combine(setFields),
            Updates.setOnInsert("_id", UUID.randomUUID().toString()),
            Updates.setOnInsert("createdAt", now),
            Updates.setOnInsert("signupAt", now),
            Updates.setOnInsert("onboardingStep", OnboardingStep.SIGNED_UP.name)
        )

        val options = FindOneAndUpdateOptions()
            .upsert(true)
            .returnDocument(ReturnDocument.AFTER)

        val doc = collection.findOneAndUpdate(eq("githubId", githubId), update, options)
            ?: error("upsert returned null for githubId=$githubId")

        return UserRepository.UpsertResult(doc.toUser(), isNewUser = !existed)
    }

    override suspend fun findByGithubId(githubId: Long): User? =
        collection.find(eq("githubId", githubId)).firstOrNull()?.toUser()

    override suspend fun findById(userId: String): User? =
        collection.find(eq("_id", userId)).firstOrNull()?.toUser()

    override suspend fun touchLastActive(userId: String, at: Instant) {
        collection.updateOne(
            eq("_id", userId),
            Updates.set("lastActiveAt", at.toEpochMilliseconds())
        )
    }

    override suspend fun advanceOnboarding(userId: String, step: OnboardingStep, at: Instant) {
        val current = collection.find(eq("_id", userId)).firstOrNull() ?: return
        val currentStep = runCatching { OnboardingStep.valueOf(current.getString("onboardingStep") ?: "") }
            .getOrDefault(OnboardingStep.SIGNED_UP)
        if (step.ordinal <= currentStep.ordinal) return  // monotonic — never roll back
        val updates = mutableListOf(
            Updates.set("onboardingStep", step.name)
        )
        if (step == OnboardingStep.COMPLETED) {
            updates.add(Updates.set("onboardingCompletedAt", at.toEpochMilliseconds()))
        }
        collection.updateOne(eq("_id", userId), Updates.combine(updates))
    }

    override suspend fun setOnboardingDismissed(userId: String, at: Instant) {
        collection.updateOne(
            eq("_id", userId),
            Updates.set("onboardingDismissedAt", at.toEpochMilliseconds())
        )
    }

    override suspend fun clearOnboardingDismissed(userId: String) {
        collection.updateOne(
            eq("_id", userId),
            Updates.unset("onboardingDismissedAt")
        )
    }

    override suspend fun listAll(limit: Int): List<User> =
        collection.find()
            .sort(Sorts.descending("lastActiveAt"))
            .limit(limit)
            .toList()
            .map { it.toUser() }

    override suspend fun findStuckOnboarding(signedUpBefore: Instant): List<User> =
        collection.find(
            and(
                `in`("onboardingStep", listOf(OnboardingStep.SIGNED_UP.name, OnboardingStep.PROJECT_CREATED.name)),
                lt("signupAt", signedUpBefore.toEpochMilliseconds())
            )
        ).toList().map { it.toUser() }

    private fun Document.toUser(): User {
        val rawToken = getString("githubToken")
        val decryptedToken = rawToken?.let {
            try { decrypt(it) } catch (_: Exception) { null }
        }
        val signupAt = (get("signupAt") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
            ?: (get("createdAt") as? Number)?.toLong()?.let { Instant.fromEpochMilliseconds(it) }
        val lastActiveAt = (get("lastActiveAt") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
        val onboardingStep = runCatching { OnboardingStep.valueOf(getString("onboardingStep") ?: "") }
            .getOrDefault(OnboardingStep.SIGNED_UP)
        val onboardingCompletedAt = (get("onboardingCompletedAt") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
        val onboardingDismissedAt = (get("onboardingDismissedAt") as? Number)?.toLong()
            ?.let { Instant.fromEpochMilliseconds(it) }
        return User(
            id = getString("_id"),
            githubId = getLong("githubId"),
            githubUsername = getString("githubUsername") ?: "",
            email = getString("email"),
            githubToken = decryptedToken,
            avatarUrl = getString("avatarUrl"),
            signupAt = signupAt,
            lastActiveAt = lastActiveAt,
            onboardingStep = onboardingStep,
            onboardingCompletedAt = onboardingCompletedAt,
            onboardingDismissedAt = onboardingDismissedAt
        )
    }
}
