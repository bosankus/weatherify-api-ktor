package com.transloom.services

import com.transloom.repository.CdnPublishRepository
import com.transloom.repository.TranslationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.MessageDigest

private val minJson = Json { prettyPrint = false }

data class PublishReceipt(
    val publishedAt: Long,
    val locales: List<String>,
    val bundleVersion: String,
    val skipped: Boolean = false,
    val skipReason: String? = null,
    /** True when this publish was promoted to the active pointer. */
    val promoted: Boolean = false
)

class CdnPublishService(
    private val translationRepository: TranslationRepository,
    private val cfKv: CloudflareR2Service,
    private val publishLog: CdnPublishRepository
) {

    private val log = LoggerFactory.getLogger(CdnPublishService::class.java)

    /**
     * Compile approved translations into per-locale bundles, write each one as an immutable
     * versioned R2 object (`{projectId}/{locale}/{version}`). When [promote] is true, also update
     * the per-locale active pointer (`{projectId}/{locale}/active`) and record the active version
     * for the project.
     */
    suspend fun publish(projectId: String, promote: Boolean = true): PublishReceipt {
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
                promoteInternal(projectId, bundleVersion, locales)
                log.info("CDN publish: bundle {} already on R2, re-promoted active pointer for project={}", bundleVersion, projectId)
                return PublishReceipt(
                    publishedAt = alreadyPublished.publishedAt,
                    locales = locales,
                    bundleVersion = bundleVersion,
                    promoted = true
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
        val metadataTemplate = """{"bundleVersion":"$bundleVersion"}"""
        val publishedAt = System.currentTimeMillis()

        coroutineScope {
            byLocale.entries.map { (locale, bundle) ->
                async {
                    val kvKey = versionedKey(projectId, locale, bundleVersion)
                    val kvValue = minJson.encodeToString(bundle)
                    cfKv.put(kvKey, kvValue, metadataTemplate, expirationTtl = 2_592_000)
                    log.debug("CDN R2 written: key={} strings={}", kvKey, bundle.size)
                }
            }.awaitAll()
        }

        val entry = runCatching {
            publishLog.log(projectId, bundleVersion, locales, "success")
        }.getOrElse { e ->
            log.warn("CDN publish log write failed for project={}: {}", projectId, e.message)
            null
        }

        if (promote) {
            promoteInternal(projectId, bundleVersion, locales)
        }

        log.info("CDN publish complete: project={} locales={} version={} promoted={}", projectId, locales, bundleVersion, promote)
        return PublishReceipt(
            publishedAt = entry?.publishedAt ?: publishedAt,
            locales = locales,
            bundleVersion = bundleVersion,
            promoted = promote
        )
    }

    /**
     * Promote a previously-published version to active. Fails if the version was never published.
     */
    suspend fun promote(projectId: String, bundleVersion: String): PublishReceipt {
        val record = publishLog.findByVersion(projectId, bundleVersion)
            ?: throw CdnPublishException("Version '$bundleVersion' was never published for project '$projectId'")
        promoteInternal(projectId, bundleVersion, record.locales)
        log.info("CDN promote: project={} version={} locales={}", projectId, bundleVersion, record.locales)
        return PublishReceipt(
            publishedAt = record.publishedAt,
            locales = record.locales,
            bundleVersion = bundleVersion,
            promoted = true
        )
    }

    /**
     * Roll back to the second-most-recent successful publish. Returns null if there's nothing to roll back to.
     */
    suspend fun rollback(projectId: String): PublishReceipt? {
        val recent = publishLog.listPublishes(projectId, limit = 10).filter { it.status == "success" }
        val active = publishLog.getActiveVersion(projectId)
        // Pick the most recent version that is not the current active one.
        val target = recent.firstOrNull { it.bundleVersion != active } ?: return null
        promoteInternal(projectId, target.bundleVersion, target.locales)
        log.info("CDN rollback: project={} from={} to={}", projectId, active, target.bundleVersion)
        return PublishReceipt(
            publishedAt = target.publishedAt,
            locales = target.locales,
            bundleVersion = target.bundleVersion,
            promoted = true
        )
    }

    private suspend fun promoteInternal(projectId: String, bundleVersion: String, locales: List<String>) {
        // The active pointer is *both* a per-locale R2 object (so the read path can resolve without
        // round-tripping to Mongo) and a Mongo record (so the dashboard can read it cheaply).
        coroutineScope {
            locales.map { locale ->
                async { cfKv.put(activeKey(projectId, locale), bundleVersion) }
            }.awaitAll()
        }
        runCatching { publishLog.setActiveVersion(projectId, bundleVersion) }
            .onFailure { log.warn("Failed to record active version in Mongo: project={} version={}: {}", projectId, bundleVersion, it.message) }
    }

    /**
     * Resolve the active bundle JSON for a (project, locale). Returns null if no active version exists
     * or if the underlying versioned bundle is missing.
     */
    suspend fun fetchActiveBundle(projectId: String, locale: String): ResolvedBundle? {
        val version = cfKv.get(activeKey(projectId, locale)) ?: return null
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

    companion object {
        internal fun versionedKey(projectId: String, locale: String, version: String) = "$projectId/$locale/$version"
        internal fun activeKey(projectId: String, locale: String) = "$projectId/$locale/active"
    }
}

data class ResolvedBundle(val version: String, val json: String)
