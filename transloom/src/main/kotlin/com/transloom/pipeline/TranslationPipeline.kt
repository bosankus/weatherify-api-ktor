package com.transloom.pipeline

import com.transloom.domain.ChangeType
import com.transloom.domain.CulturalAnalysis
import com.transloom.domain.Project
import com.transloom.domain.TargetConfig
import com.transloom.repository.ProjectRepository
import com.transloom.repository.TranslationRepository
import com.transloom.model.AppConfig
import com.transloom.model.SourceConfig
import com.transloom.model.TransloomConfig
import com.transloom.queue.WebhookPayload
import com.transloom.repository.SharedTranslationMemoryRepository
import com.transloom.services.BillingService
import com.transloom.services.GitHubService
import com.transloom.services.PipelineEventBus
import com.transloom.services.CulturalSensitivityAnalyzer
import com.transloom.services.SemanticChangeAnalyzer
import com.transloom.services.StringParserService
import com.transloom.services.TranslationContext
import com.transloom.services.TranslationService
import com.transloom.services.requiredPluralForms
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

fun buildConfigWithGlossary(project: Project, glossary: Map<String, Map<String, String>>): TransloomConfig {
    // Use the filename as the map key so multiple files of the same type can coexist.
    val filesMap = project.sourceFilePaths.associateBy { it.substringAfterLast('/') }
    return TransloomConfig(
        app = AppConfig(name = project.name, category = project.category, tone = project.tone),
        source = SourceConfig(
            language = "en",
            files = filesMap,
            watch_branch = project.watchBranch
        ),
        targets = project.targets,
        glossary = glossary.ifEmpty { null }
    )
}

// Plural form quantities recognised in Android string key names (e.g. "item_count.one")
private val PLURAL_QUANTITIES = setOf("zero", "one", "two", "few", "many", "other")

class TranslationPipeline(
    private val gitHubService: GitHubService,
    private val translationService: TranslationService,
    private val billingService: BillingService,
    private val projectRepository: ProjectRepository,
    private val translationRepository: TranslationRepository,
    private val eventBus: PipelineEventBus,
    private val semanticChangeAnalyzer: SemanticChangeAnalyzer,
    private val culturalSensitivityAnalyzer: CulturalSensitivityAnalyzer,
    private val cdnPublishService: com.transloom.services.CdnPublishService,
    private val sharedMemoryRepository: SharedTranslationMemoryRepository? = null,
    /** Max concurrent Gemini batch calls per translation run. */
    private val translationConcurrency: Int = 8
) {
    private val log = LoggerFactory.getLogger(TranslationPipeline::class.java)

    suspend fun processWebhookPayload(
        payload: WebhookPayload,
        project: Project,
        config: TransloomConfig,
        githubToken: String
    ) {
        val userId = project.ownerId
        val runId = eventBus.startRun(
            userId, payload.repositoryFullName, payload.branchName, payload.commitHash.take(7),
            projectId = project.id, retriedFromRunId = payload.retriedFromRunId
        )
        log.info("Pipeline: repo={} branch={} commit={}", payload.repositoryFullName, payload.branchName, payload.commitHash.take(7))

        // ── Pre-flight billing check ───────────────────────────────────────────
        // Fail fast before any GitHub API calls or MongoDB reads if the user has
        // already hit their plan limit — avoids wasting quota on a doomed run.
        if (billingService.isLimitAlreadyExceeded(userId)) {
            val friendlyMsg = "Monthly string quota reached — upgrade your plan to resume translations."
            eventBus.stepSkipped(userId, runId, "FETCHING_STRINGS")
            eventBus.stepSkipped(userId, runId, "DETECTING_CHANGES")
            eventBus.stepError(userId, runId, "BILLING_CHECK", friendlyMsg)
            listOf("TRANSLATING", "CREATING_PR", "CDN_PUBLISH").forEach { eventBus.stepSkipped(userId, runId, it) }
            eventBus.finishRun(userId, runId, error = friendlyMsg)
            log.info("Pipeline skipped — userId={} has exceeded plan limit", userId)
            return
        }

        // ── Step: Fetch source files ───────────────────────────────────────────
        val sourceFilePaths = config.source.files.values.toList()
        if (sourceFilePaths.isEmpty()) {
            eventBus.stepError(userId, runId, "FETCHING_STRINGS", "No source file configured")
            eventBus.finishRun(userId, runId, error = "No source file configured for project ${project.id}")
            log.warn("No source file configured for project={} — skipping pipeline", project.id)
            return
        }
        eventBus.stepRunning(userId, runId, "FETCHING_STRINGS")
        val sourceContentByPath: Map<String, String> = try {
            coroutineScope {
                sourceFilePaths.map { filePath ->
                    async {
                        filePath to gitHubService.fetchFileContent(
                            repo = payload.repositoryFullName, branch = payload.commitHash,
                            filePath = filePath, token = githubToken
                        )
                    }
                }.awaitAll().toMap()
            }
        } catch (e: Exception) {
            eventBus.stepError(userId, runId, "FETCHING_STRINGS", e.message)
            eventBus.finishRun(userId, runId, error = "Could not read source file: ${e.message}")
            log.warn("Failed to fetch source file for repo={}: {}", payload.repositoryFullName, e.message)
            return
        }
        eventBus.stepDone(userId, runId, "FETCHING_STRINGS")

        // Hash-based fast skip: hash is computed over all source files combined (sorted by path for
        // determinism). If byte-for-byte identical to the last successfully processed version, skip.
        val incomingHash = sha256(sourceContentByPath.entries.sortedBy { it.key }.joinToString("\n") { it.value })
        if (incomingHash == project.lastSourceFileHash) {
            listOf("DETECTING_CHANGES", "BILLING_CHECK", "TRANSLATING", "CREATING_PR").forEach {
                eventBus.stepSkipped(userId, runId, it)
            }
            eventBus.finishRun(userId, runId)
            log.info("Source files unchanged (hash match) for repo={} commit={} — pipeline skipped",
                payload.repositoryFullName, payload.commitHash.take(7))
            return
        }

        // ── Step: Detect changes ───────────────────────────────────────────────
        eventBus.stepRunning(userId, runId, "DETECTING_CHANGES")
        // Merge strings from all source files; if keys collide across files the last file wins.
        val allSourceStrings: Map<String, String> = sourceContentByPath.entries
            .sortedBy { it.key }
            .fold(mutableMapOf()) { acc, (filePath, content) ->
                val parsed = when {
                    filePath.endsWith(".xml") -> StringParserService.parseAndroidXml(content)
                        .filterValues { !it.startsWith("@") }  // skip @string/ref, @drawable/ref etc.
                    filePath.endsWith(".strings") -> StringParserService.parseIosStrings(content)
                    filePath.endsWith(".json") || filePath.endsWith(".arb") ->
                        StringParserService.parseJsonStrings(content)
                    else -> {
                        log.warn("Unsupported source file format: {} — no strings extracted", filePath)
                        emptyMap()
                    }
                }
                acc.apply { putAll(parsed) }
            }
        val existingDbStrings = translationRepository.getStringKeysAndTexts(project.id)
        val allChangedStrings = allSourceStrings.filter { (key, text) -> existingDbStrings[key] != text }

        if (allChangedStrings.isEmpty()) {
            eventBus.stepDone(userId, runId, "DETECTING_CHANGES", "No changes")
            listOf("BILLING_CHECK", "TRANSLATING", "CREATING_PR").forEach {
                eventBus.stepSkipped(userId, runId, it)
            }
            eventBus.finishRun(userId, runId)
            projectRepository.updateSourceFileHash(project.id, incomingHash)
            log.info("No new/modified strings in {} — skipping", payload.commitHash.take(7))
            return
        }

        // Split all changed strings into brand-new ones and modifications to existing ones.
        // Modifications are then classified: surface-only rewrites skip retranslation;
        // semantic changes (meaning shifted) retranslate all targets.
        val newStrings = allChangedStrings.filter { (key, _) -> !existingDbStrings.containsKey(key) }
        val modifiedStrings = allChangedStrings.filter { (key, _) -> existingDbStrings.containsKey(key) }

        val (surfaceKeys, semanticKeys) = classifyModifiedStrings(modifiedStrings, existingDbStrings)

        // Surface changes: source text drifted cosmetically (capitalization, punctuation, phrasing).
        // Persist the updated text but skip retranslation — existing translations stay valid.
        for (key in surfaceKeys) {
            translationRepository.upsertString(project.id, key, modifiedStrings.getValue(key))
        }
        if (surfaceKeys.isNotEmpty()) {
            log.info("{} surface change(s) in {} — source text updated, retranslation skipped", surfaceKeys.size, payload.commitHash.take(7))
        }

        val addedStrings = newStrings + modifiedStrings.filter { (key, _) -> key in semanticKeys }

        if (addedStrings.isEmpty()) {
            val msg = "All ${surfaceKeys.size} change${if (surfaceKeys.size != 1) "s" else ""} were surface-level — retranslation skipped"
            eventBus.stepDone(userId, runId, "DETECTING_CHANGES", msg)
            listOf("BILLING_CHECK", "TRANSLATING", "CREATING_PR").forEach {
                eventBus.stepSkipped(userId, runId, it)
            }
            eventBus.finishRun(userId, runId, surfaceSkipped = surfaceKeys.size)
            projectRepository.updateSourceFileHash(project.id, incomingHash)
            return
        }

        val detectionSummary = buildString {
            val parts = mutableListOf<String>()
            if (newStrings.isNotEmpty()) parts += "${newStrings.size} new"
            if (semanticKeys.isNotEmpty()) parts += "${semanticKeys.size} semantic"
            if (surfaceKeys.isNotEmpty()) parts += "${surfaceKeys.size} surface skipped"
            append(parts.joinToString(", "))
        }
        eventBus.stepDone(userId, runId, "DETECTING_CHANGES", detectionSummary)
        log.info("{} string(s) to translate × {} language(s) ({} surface skipped)",
            addedStrings.size, config.targets.size, surfaceKeys.size)

        // ── Step: Billing check ────────────────────────────────────────────────
        eventBus.stepRunning(userId, runId, "BILLING_CHECK")
        try {
            val projectCount = projectRepository.countForUser(project.ownerId)
            billingService.checkAndEnforceLimits(project.ownerId, addedStrings.size * config.targets.size, projectCount)
        } catch (e: Exception) {
            val friendlyMsg = e.message?.let { humanizeBillingError(it) } ?: "Plan limit reached"
            eventBus.stepError(userId, runId, "BILLING_CHECK", friendlyMsg)
            eventBus.finishRun(userId, runId, error = friendlyMsg, surfaceSkipped = surfaceKeys.size)
            log.warn("Billing limit reached for {}: {}", payload.repositoryFullName, e.message)
            return
        }
        eventBus.stepDone(userId, runId, "BILLING_CHECK")

        // ── Step: Translate ────────────────────────────────────────────────────
        val totalLangs = config.targets.size
        val completedLangs = AtomicInteger(0)
        eventBus.stepRunning(userId, runId, "TRANSLATING", "0 / $totalLangs languages")

        val updatedFiles = ConcurrentHashMap<String, String>()
        val translatedCounts = ConcurrentHashMap<String, Int>()

        coroutineScope {
            config.targets.map { target ->
                async {
                    val (approved, count) = processTarget(payload, project, config, target, addedStrings, runId = runId, commitShort = payload.commitHash.take(7))
                    translatedCounts[target.code] = count
                    val done = completedLangs.incrementAndGet()
                    eventBus.stepRunning(userId, runId, "TRANSLATING", "$done / $totalLangs languages")
                    if (approved.isNotEmpty()) {
                        val existing = gitHubService.fetchFileContent(
                            repo = payload.repositoryFullName, branch = payload.branchName,
                            filePath = target.file, token = githubToken
                        )
                        val merged = when {
                            target.file.endsWith(".xml") -> StringParserService.mergeAndroidXml(existing, approved)
                            target.file.endsWith(".strings") -> StringParserService.mergeIosStrings(existing, approved)
                            target.file.endsWith(".json") || target.file.endsWith(".arb") ->
                                StringParserService.mergeJsonStrings(existing, approved)
                            else -> existing
                        }
                        updatedFiles[target.file] = merged
                    }
                }
            }.awaitAll()
        }
        eventBus.stepDone(userId, runId, "TRANSLATING",
            "$totalLangs language${if (totalLangs != 1) "s" else ""} done")

        // ── Step: Create PR ────────────────────────────────────────────────────
        if (updatedFiles.isNotEmpty()) {
            eventBus.stepRunning(userId, runId, "CREATING_PR")
            val pr = try {
                gitHubService.createBranchAndPr(
                    repo = payload.repositoryFullName,
                    baseBranch = payload.branchName,
                    files = updatedFiles,
                    commitMessage = "chore(i18n): auto-translate strings to ${config.targets.map { it.code }.joinToString()}",
                    prTitle = "Transloom: Auto-Translations for ${payload.commitHash.take(7)}",
                    prBody = buildPrBody(newStrings.size, semanticKeys.size, surfaceKeys.size, config.targets.size),
                    token = githubToken
                )
            } catch (e: Exception) {
                eventBus.stepError(userId, runId, "CREATING_PR", e.message)
                eventBus.finishRun(userId, runId, error = "PR creation failed: ${e.message}", surfaceSkipped = surfaceKeys.size)
                log.warn("PR creation failed for {}: {}", payload.repositoryFullName, e.message)
                return
            }
            eventBus.stepDone(userId, runId, "CREATING_PR", pr.prUrl)
            eventBus.finishRun(userId, runId, prUrl = pr.prUrl, prBranch = pr.branchName, surfaceSkipped = surfaceKeys.size)
            projectRepository.updateSourceFileHash(project.id, incomingHash)
            log.info("Translation PR created: {}", pr.prUrl)
            maybePublishCdn(project, userId, runId)
        } else {
            eventBus.stepSkipped(userId, runId, "CREATING_PR", "No translatable strings approved")
            eventBus.finishRun(userId, runId, surfaceSkipped = surfaceKeys.size)
            projectRepository.updateSourceFileHash(project.id, incomingHash)
            maybePublishCdn(project, userId, runId)
        }

        val total = translatedCounts.values.sum()
        if (total > 0) billingService.recordUsage(project.ownerId, total)
    }

    private suspend fun maybePublishCdn(project: Project, userId: String, runId: String) {
        if (!project.otaEnabled) {
            eventBus.stepSkipped(userId, runId, "CDN_PUBLISH", "OTA disabled for project")
            return
        }
        runCatching { runCdnPublish(userId, runId, project.id, project.autoPromote) }
            .onFailure { log.warn("CDN publish failed for project={}: {}", project.id, it.message) }
    }

    private suspend fun runCdnPublish(userId: String, runId: String, projectId: String, promote: Boolean) {
        eventBus.stepRunning(userId, runId, "CDN_PUBLISH", "Compiling bundles…")
        val receipt = cdnPublishService.publish(projectId, promote = promote)
        if (receipt.skipped) {
            val reason = when (receipt.skipReason) {
                "bundle_unchanged" -> "Bundle unchanged — already live"
                "no_approved_strings" -> "No approved strings to publish"
                else -> "Skipped"
            }
            eventBus.stepSkipped(userId, runId, "CDN_PUBLISH", reason)
        } else {
            val promotedNote = if (!promote) " (staged, not promoted)" else ""
            val detail = "${receipt.locales.size} locale${if (receipt.locales.size != 1) "s" else ""} live on edge$promotedNote"
            eventBus.stepDone(userId, runId, "CDN_PUBLISH", detail)
            if (promote) eventBus.emitCdnReady(userId, runId, receipt.bundleVersion, receipt.locales)
        }
        log.info("CDN publish done: project={} locales={} version={}", projectId, receipt.locales, receipt.bundleVersion)
    }

    private fun humanizeBillingError(msg: String): String = when {
        "Project limit" in msg -> "Project limit reached for your plan"
        "Monthly string limit" in msg -> "Monthly string quota reached for your plan"
        else -> msg
    }

    private suspend fun processTarget(
        payload: WebhookPayload,
        project: Project,
        config: TransloomConfig,
        target: TargetConfig,
        addedStrings: Map<String, String>,
        runId: String,
        commitShort: String
    ): Pair<Map<String, String>, Int> {
        data class StringResult(val key: String, val text: String, val status: String)

        // ── Phase 1a: Separate plural forms from simple strings ────────────────
        // Keys like "item_count.one" / "item_count.other" are plural variants.
        // Group them so Gemini can translate all forms together and generate
        // missing CLDR categories (e.g. Russian needs one/few/many/other).
        val pluralGroups = mutableMapOf<String, MutableMap<String, String>>()
        val simpleStrings = mutableMapOf<String, String>()

        for ((key, text) in addedStrings) {
            val lastSegment = key.substringAfterLast(".")
            val baseName = key.substringBeforeLast(".")
            if (lastSegment in PLURAL_QUANTITIES && baseName != key) {
                pluralGroups.getOrPut(baseName) { mutableMapOf() }[lastSegment] = text
            } else {
                simpleStrings[key] = text
            }
        }

        val allResults = mutableListOf<StringResult?>()

        // ── Phase 1b: Translate plural groups ─────────────────────────────────
        if (pluralGroups.isNotEmpty()) {
            val sampleSource = addedStrings.values.firstOrNull() ?: ""
            val pluralContext = TranslationContext(
                appId = payload.repositoryFullName, appName = config.app.name,
                category = config.app.category, tone = config.app.tone,
                glossary = config.glossary?.get(target.code),
                sourceText = sampleSource, targetLanguage = target.name, targetRegion = target.region
            )
            val pluralResults = runCatching {
                translationService.translatePluralBatch(pluralGroups, pluralContext)
            }.getOrElse { e ->
                log.warn("Plural batch failed for lang={}: {}", target.code, e.message)
                emptyMap()
            }

            for ((baseName, forms) in pluralGroups) {
                val translatedForms = pluralResults[baseName]
                if (translatedForms.isNullOrEmpty()) {
                    // Fallback: store each form as blocked
                    for ((quantity, sourceText) in forms) {
                        val key = "$baseName.$quantity"
                        val stringId = translationRepository.upsertString(project.id, key, sourceText)
                        translationRepository.upsertTranslation(stringId, project.id, project.ownerId, key, sourceText, project.name, target.code, target.region, "", "blocked", "Plural translation failed", pipelineRunId = runId, commitShort = commitShort)
                    }
                } else {
                    // Store all translated forms — including any new quantities Gemini generated
                    for ((quantity, translatedText) in translatedForms) {
                        val key = "$baseName.$quantity"
                        val sourceText = forms[quantity] ?: forms["other"] ?: forms.values.firstOrNull() ?: ""
                        val stringId = translationRepository.upsertString(project.id, key, sourceText)
                        translationRepository.upsertTranslation(stringId, project.id, project.ownerId, key, sourceText, project.name, target.code, target.region, translatedText, "auto", pipelineRunId = runId, commitShort = commitShort)
                        allResults += StringResult(key, translatedText, "auto")
                        if (project.sharedMemoryOptIn) {
                            runCatching { sharedMemoryRepository?.contribute(sourceText, target.name, translatedText) }
                        }
                    }
                }
            }
        }

        // ── Phase 1c: Translate simple strings ────────────────────────────────
        val semaphore = Semaphore(translationConcurrency)
        val batches = simpleStrings.entries.chunked(BATCH_SIZE).map { chunk -> chunk.associate { it.key to it.value } }

        val simpleResults: List<StringResult?> = coroutineScope {
            batches.map { batch ->
                async {
                    semaphore.withPermit {
                        val keyedContexts = batch.mapValues { (_, sourceText) ->
                            TranslationContext(
                                appId = payload.repositoryFullName, appName = config.app.name,
                                category = config.app.category, tone = config.app.tone,
                                glossary = config.glossary?.get(target.code),
                                sourceText = sourceText, targetLanguage = target.name, targetRegion = target.region
                            )
                        }
                        val batchResults = translationService.translateBatch(keyedContexts)
                        batch.keys.map { key ->
                            val sourceText = batch[key]!!
                            val outcome = batchResults[key]
                            when {
                                outcome == null -> {
                                    log.warn("Missing batch result for key='{}' lang={}", key, target.code)
                                    val stringId = translationRepository.upsertString(project.id, key, sourceText)
                                    translationRepository.upsertTranslation(stringId, project.id, project.ownerId, key, sourceText, project.name, target.code, target.region, "", "blocked", "Missing from batch", pipelineRunId = runId, commitShort = commitShort)
                                    null
                                }
                                outcome.isSuccess -> {
                                    val r = outcome.getOrThrow()
                                    val status = if (r.flags.isNotEmpty()) "review" else "auto"
                                    val stringId = translationRepository.upsertString(project.id, key, sourceText)
                                    translationRepository.upsertTranslation(stringId, project.id, project.ownerId, key, sourceText, project.name, target.code, target.region, r.text, status, pipelineRunId = runId, commitShort = commitShort)
                                    if (status != "auto") log.info("'{}' → {} flagged: {}", key, target.code, r.flags)
                                    // Contribute auto-approved strings to shared pool if opted in
                                    if (status == "auto" && project.sharedMemoryOptIn && sharedMemoryRepository != null) {
                                        runCatching { sharedMemoryRepository.contribute(sourceText, target.name, r.text) }
                                    }
                                    StringResult(key, r.text, status)
                                }
                                else -> {
                                    val error = outcome.exceptionOrNull()?.message
                                    log.warn("Failed key='{}' lang={}: {}", key, target.code, error)
                                    val stringId = translationRepository.upsertString(project.id, key, sourceText)
                                    translationRepository.upsertTranslation(stringId, project.id, project.ownerId, key, sourceText, project.name, target.code, target.region, "", "blocked", error, pipelineRunId = runId, commitShort = commitShort)
                                    null
                                }
                            }
                        }
                    }
                }
            }.awaitAll().flatten()
        }

        allResults.addAll(simpleResults)

        // ── Phase 2: Cultural sensitivity check ───────────────────────────────
        val finalResults: List<StringResult?> = if (!project.culturalSensitivityEnabled) allResults else {
            val autoResults = allResults.filterNotNull().filter { it.status == "auto" }
            val analysisInputs = autoResults.mapNotNull { r ->
                val srcText = addedStrings[r.key] ?: return@mapNotNull null
                r.key to (r.text to srcText)
            }.toMap()

            val analyses = culturalSensitivityAnalyzer.analyzeBatch(
                analysisInputs, target.name, target.region, config.app.category, config.app.tone
            )

            allResults.map { r ->
                if (r == null || r.status != "auto") return@map r
                val analysis = analyses[r.key] ?: return@map r
                if (!analysis.needsReview) return@map r
                val srcText = addedStrings[r.key] ?: return@map r
                val notes = "Cultural: ${analysis.issues.joinToString("; ")}"
                val stringId = translationRepository.upsertString(project.id, r.key, srcText)
                translationRepository.upsertTranslation(stringId, project.id, project.ownerId, r.key, srcText, project.name, target.code, target.region, r.text, "review", notes, pipelineRunId = runId, commitShort = commitShort)
                log.info("Cultural flag key='{}' lang={}: {}", r.key, target.code, analysis.issues)
                StringResult(r.key, r.text, "review")
            }
        }

        val approved = finalResults.filterNotNull()
            .filter { it.status == "auto" }
            .associate { it.key to it.text }
        val count = finalResults.count { it != null }
        return approved to count
    }

    private suspend fun classifyModifiedStrings(
        modifiedStrings: Map<String, String>,
        existingDbStrings: Map<String, String>
    ): Pair<Set<String>, Set<String>> {
        if (modifiedStrings.isEmpty()) return emptySet<String>() to emptySet()

        val inputPairs = modifiedStrings.mapValues { (key, newText) -> existingDbStrings.getValue(key) to newText }
        val batchResults = semanticChangeAnalyzer.analyzeBatch(inputPairs)

        val surfaceKeys = batchResults.filter { it.value.changeType == ChangeType.SURFACE }.keys
        val semanticKeys = batchResults.filter { it.value.changeType == ChangeType.SEMANTIC }.keys
        return surfaceKeys to semanticKeys
    }

    companion object {
        private const val BATCH_SIZE = 25

        private fun sha256(content: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(content.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    private fun buildPrBody(newCount: Int, semanticCount: Int, surfaceSkipped: Int, languageCount: Int): String {
        val rows = buildString {
            if (newCount > 0) appendLine("| **New strings** | $newCount |")
            if (semanticCount > 0) appendLine("| **Semantically changed** | $semanticCount |")
            if (surfaceSkipped > 0) appendLine("| **Surface changes skipped** | $surfaceSkipped |")
            appendLine("| **Languages** | $languageCount |")
        }.trimEnd()
        val skipNote = if (surfaceSkipped > 0)
            "\n> **$surfaceSkipped string${if (surfaceSkipped != 1) "s" else ""} skipped** — only capitalization, punctuation, or phrasing changed. Existing translations remain valid.\n"
        else ""
        return """
            ## Transloom Auto-Translation

            | | |
            |---|---|
            $rows

            $skipNote> Flagged strings are in the review portal. A follow-up PR is created automatically on approval.

            *Generated by [Transloom](https://transloom.dev)*
        """.trimIndent()
    }
}
