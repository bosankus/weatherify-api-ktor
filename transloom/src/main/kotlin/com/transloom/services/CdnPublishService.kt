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
    val skipReason: String? = null
)

class CdnPublishService(
    private val translationRepository: TranslationRepository,
    private val cfKv: CloudflareKvService,
    private val publishLog: CdnPublishRepository
) {

    private val log = LoggerFactory.getLogger(CdnPublishService::class.java)

    suspend fun publish(projectId: String): PublishReceipt {
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

        // Skip if nothing changed since last publish.
        val last = runCatching { publishLog.lastPublish(projectId) }.getOrNull()
        if (last?.bundleVersion == bundleVersion && last.status == "success") {
            log.info("CDN publish: bundle unchanged (version={}) for project={}, skipping KV writes", bundleVersion, projectId)
            return PublishReceipt(
                publishedAt = last.publishedAt,
                locales = locales,
                bundleVersion = bundleVersion,
                skipped = true,
                skipReason = "bundle_unchanged"
            )
        }

        // Write each locale bundle to KV in parallel.
        val metadataTemplate = """{"bundleVersion":"$bundleVersion"}"""
        val publishedAt = System.currentTimeMillis()

        coroutineScope {
            byLocale.entries.map { (locale, bundle) ->
                async {
                    val kvKey = "$projectId:$locale"
                    val kvValue = minJson.encodeToString(bundle)
                    cfKv.put(kvKey, kvValue, metadataTemplate)
                    log.debug("CDN KV written: key={} strings={}", kvKey, bundle.size)
                }
            }.awaitAll()
        }

        val entry = runCatching {
            publishLog.log(projectId, bundleVersion, locales, "success")
        }.getOrElse { e ->
            log.warn("CDN publish log write failed for project={}: {}", projectId, e.message)
            null
        }

        log.info("CDN publish complete: project={} locales={} version={}", projectId, locales, bundleVersion)
        return PublishReceipt(
            publishedAt = entry?.publishedAt ?: publishedAt,
            locales = locales,
            bundleVersion = bundleVersion
        )
    }

    private fun computeBundleVersion(byLocale: Map<String, Map<String, String>>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // Sort deterministically so the same content always produces the same hash.
        byLocale.entries.sortedBy { it.key }.forEach { (locale, bundle) ->
            bundle.entries.sortedBy { it.key }.forEach { (k, v) ->
                digest.update("$locale:$k=$v\n".toByteArray(Charsets.UTF_8))
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }
}
