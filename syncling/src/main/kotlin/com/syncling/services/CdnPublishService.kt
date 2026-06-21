package com.syncling.services

import com.androidplay.core.secrets.getSecretValue
import com.syncling.repository.CdnPublishRepository
import com.syncling.repository.TranslationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val minJson = Json { prettyPrint = false }

/**
 * Master key for HMAC-SHA256 bundle signatures. Per-project signing key is derived as
 * HMAC-SHA256(projectId, masterKey), so each project gets a stable, independent key
 * without requiring per-project key management. Null if the secret is not configured —
 * signature files are silently skipped so the feature degrades gracefully.
 */
private val BUNDLE_SIGNING_KEY: ByteArray? by lazy {
    getSecretValue("bundle-signing-key").takeIf { it.isNotBlank() }?.toByteArray(Charsets.UTF_8)
}

data class PublishReceipt(
    val publishedAt: Long,
    val locales: List<String>,
    val bundleVersion: String,
    val skipped: Boolean = false,
    val skipReason: String? = null,
    /** True when this publish was promoted to the active pointer. */
    val promoted: Boolean = false,
    /**
     * "active" when promoted to full traffic, "canary" when staged for partial rollout,
     * null when not promoted (staged only).
     */
    val pointer: String? = null
)

class CdnPublishService(
    private val translationRepository: TranslationRepository,
    private val cfKv: CloudflareR2Service,
    private val publishLog: CdnPublishRepository,
    /**
     * Gate for whether a project's owner is on a paid plan. CDN delivery (OTA) is a paid-only
     * feature: free projects are never published to the edge, and any pre-existing bundle for a
     * project whose owner has since downgraded stops being served. Defaults to always-eligible
     * so tests and callers that don't care can ignore it.
     */
    private val isCdnEligible: suspend (projectId: String) -> Boolean = { true }
) {

    private val log = LoggerFactory.getLogger(CdnPublishService::class.java)

    /**
     * Compile approved translations into per-locale bundles, write each one as an immutable
     * versioned R2 object (`{projectId}/{locale}/{version}`). When [promote] is true:
     * - [rolloutPercent] == 100 (default): writes the `active` pointer (full traffic).
     * - [rolloutPercent] in 1..99: writes the `canary` pointer (partial rollout). Client SDKs
     *   route users to canary when `hash(userId) % 100 < rolloutPercent`.
     * - [rolloutPercent] == 0: writes no pointer (staged only, manual promotion required).
     */
    suspend fun publish(projectId: String, promote: Boolean = true, rolloutPercent: Int = 100): PublishReceipt {
        if (!isCdnEligible(projectId)) {
            log.info("CDN publish skipped: project={} owner not on a paid plan (CDN delivery is paid-only)", projectId)
            return PublishReceipt(
                publishedAt = System.currentTimeMillis(),
                locales = emptyList(),
                bundleVersion = "",
                skipped = true,
                skipReason = "plan_not_eligible"
            )
        }

        val translations = translationRepository.getPublishableTranslations(projectId)

        if (translations.isEmpty()) {
            log.info("CDN publish: no publishable translations for project={}", projectId)
            return PublishReceipt(
                publishedAt = System.currentTimeMillis(),
                locales = emptyList(),
                bundleVersion = "",
                skipped = true,
                skipReason = "no_approved_strings"
            )
        }

        // Group by locale: locale → { stringKey → translatedText }
        val byLocale: Map<String, Map<String, String>> = translations
            .groupBy { it.targetLanguage }
            .mapValues { (_, entries) -> entries.associate { it.stringKey to it.translatedText } }

        val bundleVersion = computeBundleVersion(byLocale)
        val locales = byLocale.keys.sorted()

        // Skip versioned writes if this exact bundle is already published. Still re-promote
        // if asked, in case the pointer was rolled back and the caller wants this version live again.
        val alreadyPublished = runCatching { publishLog.findByVersion(projectId, bundleVersion) }.getOrNull()
        if (alreadyPublished != null && alreadyPublished.status == "success") {
            val activeNow = runCatching { publishLog.getActiveVersion(projectId) }.getOrNull()
            if (promote && activeNow != bundleVersion) {
                val ptr = promoteInternal(projectId, bundleVersion, locales, rolloutPercent)
                log.info("CDN publish: bundle {} already on R2, re-promoted {} pointer for project={}", bundleVersion, ptr, projectId)
                return PublishReceipt(
                    publishedAt = alreadyPublished.publishedAt,
                    locales = locales,
                    bundleVersion = bundleVersion,
                    promoted = true,
                    pointer = ptr
                )
            }
            log.info("CDN publish: bundle unchanged (version={}) for project={}, skipping R2 writes", bundleVersion, projectId)
            return PublishReceipt(
                publishedAt = alreadyPublished.publishedAt,
                locales = locales,
                bundleVersion = bundleVersion,
                skipped = true,
                skipReason = "bundle_unchanged",
                promoted = activeNow == bundleVersion
            )
        }

        // Write each locale bundle to R2 in parallel, under an immutable versioned key.
        // Also write a per-locale HMAC-SHA256 signature file for client-side integrity verification.
        val metadataTemplate = """{"bundleVersion":"$bundleVersion"}"""
        val publishedAt = System.currentTimeMillis()
        val derivedSigningKey = deriveProjectKey(projectId)

        coroutineScope {
            byLocale.entries.map { (locale, bundle) ->
                async {
                    val kvKey = versionedKey(projectId, locale, bundleVersion)
                    val kvValue = minJson.encodeToString(bundle)
                    cfKv.put(kvKey, kvValue, metadataTemplate, expirationTtl = 2_592_000)
                    log.debug("CDN R2 written: key={} strings={}", kvKey, bundle.size)
                    if (derivedSigningKey != null) {
                        val sigHex = hmacSha256Hex(kvValue.toByteArray(Charsets.UTF_8), derivedSigningKey)
                        runCatching { cfKv.put(sigKey(projectId, locale, bundleVersion), sigHex) }
                            .onFailure { e -> log.warn("Signature write failed for key={}: {}", kvKey, e.message) }
                    }
                }
            }.awaitAll()
        }

        val entry = runCatching {
            publishLog.log(projectId, bundleVersion, locales, "success")
        }.getOrElse { e ->
            log.warn("CDN publish log write failed for project={}: {}", projectId, e.message)
            null
        }

        val ptr = if (promote) promoteInternal(projectId, bundleVersion, locales, rolloutPercent) else null

        log.info("CDN publish complete: project={} locales={} version={} pointer={}", projectId, locales, bundleVersion, ptr)
        return PublishReceipt(
            publishedAt = entry?.publishedAt ?: publishedAt,
            locales = locales,
            bundleVersion = bundleVersion,
            promoted = promote,
            pointer = ptr
        )
    }

    /**
     * Promote a previously-published version to the `active` pointer (full traffic).
     * Manual promotions always write `active` and clear the `canary` pointer.
     * Fails if the version was never published.
     */
    suspend fun promote(projectId: String, bundleVersion: String): PublishReceipt {
        if (!isCdnEligible(projectId)) {
            throw CdnPublishException("CDN delivery is a paid feature — upgrade to promote bundles to the edge.")
        }
        val record = publishLog.findByVersion(projectId, bundleVersion)
            ?: throw CdnPublishException("Version '$bundleVersion' was never published for project '$projectId'")
        promoteInternal(projectId, bundleVersion, record.locales, rolloutPercent = 100)
        log.info("CDN promote: project={} version={} locales={}", projectId, bundleVersion, record.locales)
        return PublishReceipt(
            publishedAt = record.publishedAt,
            locales = record.locales,
            bundleVersion = bundleVersion,
            promoted = true,
            pointer = "active"
        )
    }

    /**
     * Roll back to the second-most-recent successful publish. Returns null if there's nothing to roll back to.
     */
    suspend fun rollback(projectId: String): PublishReceipt? {
        if (!isCdnEligible(projectId)) return null
        val recent = publishLog.listPublishes(projectId, limit = 10).filter { it.status == "success" }
        val active = publishLog.getActiveVersion(projectId)
        // Pick the most recent version that is not the current active one.
        val target = recent.firstOrNull { it.bundleVersion != active } ?: return null
        promoteInternal(projectId, target.bundleVersion, target.locales, rolloutPercent = 100)
        log.info("CDN rollback: project={} from={} to={}", projectId, active, target.bundleVersion)
        return PublishReceipt(
            publishedAt = target.publishedAt,
            locales = target.locales,
            bundleVersion = target.bundleVersion,
            promoted = true,
            pointer = "active"
        )
    }

    /**
     * Resolve the bundle signing key for a project (for client SDK setup).
     * Returns the hex-encoded derived key, or null if signing is not configured.
     */
    fun signingKeyForProject(projectId: String): String? {
        val derived = deriveProjectKey(projectId) ?: return null
        return derived.joinToString("") { "%02x".format(it) }
    }

    /**
     * Routes the version pointer based on [rolloutPercent]:
     * - 100: writes `active` pointer and clears `canary`.
     * - 1–99: writes `canary` pointer only (leaves `active` for stable traffic).
     * - 0: no pointer written (staged only).
     * Returns the pointer name written ("active" | "canary"), or null if nothing was written.
     */
    private suspend fun promoteInternal(
        projectId: String,
        bundleVersion: String,
        locales: List<String>,
        rolloutPercent: Int
    ): String? {
        val pointerName = when {
            rolloutPercent >= 100 -> "active"
            rolloutPercent > 0 -> "canary"
            else -> return null
        }
        // Write the appropriate pointer for each locale in parallel.
        coroutineScope {
            locales.map { locale ->
                async { cfKv.put(pointerKey(projectId, locale, pointerName), bundleVersion) }
            }.awaitAll()
        }
        // When promoting to active, also clear any stale canary pointer.
        if (pointerName == "active") {
            coroutineScope {
                locales.map { locale ->
                    async {
                        runCatching { cfKv.delete(pointerKey(projectId, locale, "canary")) }
                    }
                }.awaitAll()
            }
            runCatching { publishLog.setActiveVersion(projectId, bundleVersion) }
                .onFailure { log.warn("Failed to record active version in Mongo: project={} version={}: {}", projectId, bundleVersion, it.message) }
        }
        return pointerName
    }

    /**
     * Resolve the active bundle JSON for a (project, locale). Returns null if no active version exists
     * or if the underlying versioned bundle is missing.
     */
    suspend fun fetchActiveBundle(projectId: String, locale: String): ResolvedBundle? {
        // Delivery is paid-only: stop serving bundles for projects whose owner is on the free plan
        // (e.g. after a downgrade), even though the versioned objects may still exist on R2.
        if (!isCdnEligible(projectId)) return null
        val version = cfKv.get(pointerKey(projectId, locale, "active")) ?: return null
        val bundle = cfKv.get(versionedKey(projectId, locale, version)) ?: return null
        return ResolvedBundle(version = version, json = bundle)
    }

    private fun computeBundleVersion(byLocale: Map<String, Map<String, String>>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        byLocale.entries.sortedBy { it.key }.forEach { (locale, bundle) ->
            bundle.entries.sortedBy { it.key }.forEach { (k, v) ->
                digest.update("$locale:$k=$v\n".toByteArray(Charsets.UTF_8))
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun deriveProjectKey(projectId: String): ByteArray? {
        val master = BUNDLE_SIGNING_KEY ?: return null
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(master, "HmacSHA256"))
        return mac.doFinal(projectId.toByteArray(Charsets.UTF_8))
    }

    private fun hmacSha256Hex(data: ByteArray, key: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    companion object {
        internal fun versionedKey(projectId: String, locale: String, version: String) = "$projectId/$locale/$version"
        internal fun sigKey(projectId: String, locale: String, version: String) = "$projectId/$locale/$version.sig"
        internal fun pointerKey(projectId: String, locale: String, pointer: String) = "$projectId/$locale/$pointer"
    }
}

data class ResolvedBundle(val version: String, val json: String)
