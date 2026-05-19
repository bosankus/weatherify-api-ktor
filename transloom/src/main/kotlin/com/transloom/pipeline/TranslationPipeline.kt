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
import com.transloom.services.BillingService
import com.transloom.services.GitHubService
import com.transloom.services.PipelineEventBus
import com.transloom.services.CulturalSensitivityAnalyzer
import com.transloom.services.SemanticChangeAnalyzer
import com.transloom.services.StringParserService
import com.transloom.services.TranslationContext
import com.transloom.services.TranslationService
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
    val fileType = when {
        project.sourceFilePath.endsWith(".strings") -> "ios"
        project.sourceFilePath.endsWith(".json") || project.sourceFilePath.endsWith(".arb") -> "json"
        else -> "android"
    }
    return TransloomConfig(
        app = AppConfig(name = project.name, category = project.category, tone = project.tone),
        source = SourceConfig(
            language = "en",
            files = mapOf(fileType to project.sourceFilePath),
            watch_branch = project.watchBranch
        ),
        targets = project.targets,
        glossary = glossary.ifEmpty { null }
    )
}

class TranslationPipeline(
    private val gitHubService: GitHubService,
    private val translationService: TranslationService,
    private val billingService: BillingService,
    private val projectRepository: ProjectRepository,
    private val translationRepository: TranslationRepository,
    private val eventBus: PipelineEventBus,
    private val semanticChangeAnalyzer: SemanticChangeAnalyzer,
    private val culturalSensitivityAnalyzer: CulturalSensitivityAnalyzer
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

        // ── Step: Fetch source file ────────────────────────────────────────────
        val sourceFilePath = config.source.files.values.firstOrNull()
        if (sourceFilePath == null) {
            eventBus.stepError(userId, runId, "FETCHING_STRINGS", "No source file configured")
            eventBus.finishRun(userId, runId, error = "No source file configured for project ${project.id}")
            log.warn("No source file configured for project={} — skipping pipeline", project.id)
            return
        }
        eventBus.stepRunning(userId, runId, "FETCHING_STRINGS")
        val sourceContent = try {
            gitHubService.fetchFileContent(
                repo = payload.repositoryFullName, branch = payload.commitHash,
                filePath = sourceFilePath, token = githubToken
            )
        } catch (e: Exception) {
            eventBus.stepError(userId, runId, "FETCHING_STRINGS", e.message)
            eventBus.finishRun(userId, runId, error = "Could not read source file: ${e.message}")
            log.warn("Failed to fetch source file for repo={}: {}", payload.repositoryFullName, e.message)
            return
        }
        eventBus.stepDone(userId, runId, "FETCHING_STRINGS")

        // Hash-based fast skip: if the source file is byte-for-byte identical to the last
        // successfully processed version, skip all DB reads and AI calls entirely.
        val incomingHash = sha256(sourceContent)
        if (incomingHash == project.lastSourceFileHash) {
            listOf("DETECTING_CHANGES", "BILLING_CHECK", "TRANSLATING", "CREATING_PR").forEach {
                eventBus.stepSkipped(userId, runId, it)
            }
            eventBus.finishRun(userId, runId)
            log.info("Source file unchanged (hash match) for repo={} commit={} — pipeline skipped",
                payload.repositoryFullName, payload.commitHash.take(7))
            return
        }

        // ── Step: Detect changes ───────────────────────────────────────────────
        eventBus.stepRunning(userId, runId, "DETECTING_CHANGES")
        val allSourceStrings = when {
            sourceFilePath.endsWith(".xml") -> StringParserService.parseAndroidXml(sourceContent)
                .filterValues { !it.startsWith("@") }  // skip @string/ref, @drawable/ref etc.
            sourceFilePath.endsWith(".strings") -> StringParserService.parseIosStrings(sourceContent)
            sourceFilePath.endsWith(".json") || sourceFilePath.endsWith(".arb") ->
                StringParserService.parseJsonStrings(sourceContent)
            else -> {
                log.warn("Unsupported source file format: {} — no strings extracted", sourceFilePath)
                emptyMap()
            }
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
                    val (approved, count) = processTarget(payload, project, config, target, addedStrings)
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
            eventBus.finishRun(userId, runId, prUrl = pr.prUrl, surfaceSkipped = surfaceKeys.size)
            projectRepository.updateSourceFileHash(project.id, incomingHash)
            log.info("Translation PR created: {}", pr.prUrl)
        } else {
            eventBus.stepSkipped(userId, runId, "CREATING_PR", "No translatable strings approved")
            eventBus.finishRun(userId, runId, surfaceSkipped = surfaceKeys.size)
            projectRepository.updateSourceFileHash(project.id, incomingHash)
        }

        val total = translatedCounts.values.sum()
        if (total > 0) billingService.recordUsage(project.ownerId, total)
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
        addedStrings: Map<String, String>
    ): Pair<Map<String, String>, Int> {
        data class StringResult(val key: String, val text: String, val status: String)

        // Group strings into batches; limit concurrent batch calls to avoid rate-limit bursts.
        val semaphore = Semaphore(4)
        val batches = addedStrings.entries.chunked(BATCH_SIZE).map { chunk -> chunk.associate { it.key to it.value } }

        val results: List<StringResult?> = coroutineScope {
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
                                    translationRepository.upsertTranslation(stringId, target.code, target.region, "", "blocked", "Missing from batch")
                                    null
                                }
                                outcome.isSuccess -> {
                                    val r = outcome.getOrThrow()
                                    val status = if (r.flags.isNotEmpty()) "review" else "auto"
                                    val stringId = translationRepository.upsertString(project.id, key, sourceText)
                                    translationRepository.upsertTranslation(stringId, target.code, target.region, r.text, status)
                                    if (status != "auto") log.info("'{}' → {} flagged: {}", key, target.code, r.flags)
                                    StringResult(key, r.text, status)
                                }
                                else -> {
                                    val error = outcome.exceptionOrNull()?.message
                                    log.warn("Failed key='{}' lang={}: {}", key, target.code, error)
                                    val stringId = translationRepository.upsertString(project.id, key, sourceText)
                                    translationRepository.upsertTranslation(stringId, target.code, target.region, "", "blocked", error)
                                    null
                                }
                            }
                        }
                    }
                }
            }.awaitAll().flatten()
        }

        // ── Phase 2: Cultural sensitivity check (only when opted in per-project) ──
        // All auto-approved strings for this target are sent in one batched Gemini call.
        // Any string that fails the cultural check is moved to "review" with a
        // "Cultural: ..." blockReason visible in the review portal.
        // Defaults to "no issues" on any error — never floods the review queue.
        val finalResults: List<StringResult?> = if (!project.culturalSensitivityEnabled) results else {
            val autoResults = results.filterNotNull().filter { it.status == "auto" }
            val analysisInputs = autoResults.mapNotNull { r ->
                val srcText = addedStrings[r.key] ?: return@mapNotNull null
                r.key to (r.text to srcText)
            }.toMap()

            val analyses = culturalSensitivityAnalyzer.analyzeBatch(
                analysisInputs, target.name, target.region, config.app.category, config.app.tone
            )

            results.map { r ->
                if (r == null || r.status != "auto") return@map r
                val analysis = analyses[r.key] ?: return@map r
                if (!analysis.needsReview) return@map r
                val srcText = addedStrings[r.key] ?: return@map r
                val notes = "Cultural: ${analysis.issues.joinToString("; ")}"
                val stringId = translationRepository.upsertString(project.id, r.key, srcText)
                translationRepository.upsertTranslation(stringId, target.code, target.region, r.text, "review", notes)
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
        private const val BATCH_SIZE = 10

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
