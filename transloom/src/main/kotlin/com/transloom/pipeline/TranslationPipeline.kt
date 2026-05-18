package com.transloom.pipeline

import com.transloom.domain.Project
import com.transloom.domain.TargetConfig
import com.transloom.repository.ProjectRepository
import com.transloom.repository.TranslationMemoryRepository
import com.transloom.repository.TranslationRepository
import com.transloom.model.AppConfig
import com.transloom.model.SourceConfig
import com.transloom.model.TransloomConfig
import com.transloom.queue.WebhookPayload
import com.transloom.services.BillingService
import com.transloom.services.GitHubService
import com.transloom.services.PipelineEventBus
import com.transloom.services.StringParserService
import com.transloom.services.TranslationContext
import com.transloom.services.TranslationService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

fun buildConfig(project: Project): TransloomConfig {
    val glossary = null // glossary loaded separately when needed
    val fileType = if (project.sourceFilePath.endsWith(".strings")) "ios" else "android"
    return TransloomConfig(
        app = AppConfig(name = project.name, category = project.category, tone = project.tone),
        source = SourceConfig(
            language = "en",
            files = mapOf(fileType to project.sourceFilePath),
            watch_branch = project.watchBranch
        ),
        targets = project.targets,
        glossary = glossary
    )
}

fun buildConfigWithGlossary(project: Project, glossary: Map<String, Map<String, String>>): TransloomConfig {
    val fileType = if (project.sourceFilePath.endsWith(".strings")) "ios" else "android"
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
    private val eventBus: PipelineEventBus
) {
    private val log = LoggerFactory.getLogger(TranslationPipeline::class.java)

    suspend fun processWebhookPayload(
        payload: WebhookPayload,
        project: Project,
        config: TransloomConfig,
        githubToken: String
    ) {
        val userId = project.ownerId
        val runId = eventBus.startRun(userId, payload.repositoryFullName, payload.branchName, payload.commitHash.take(7))
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

        // ── Step: Detect changes ───────────────────────────────────────────────
        eventBus.stepRunning(userId, runId, "DETECTING_CHANGES")
        val allSourceStrings = when {
            sourceFilePath.endsWith(".xml") -> StringParserService.parseAndroidXml(sourceContent)
            sourceFilePath.endsWith(".strings") -> StringParserService.parseIosStrings(sourceContent)
            else -> emptyMap()
        }
        val existingDbStrings = translationRepository.getStringKeysAndTexts(project.id)
        val addedStrings = allSourceStrings.filter { (key, text) -> existingDbStrings[key] != text }

        if (addedStrings.isEmpty()) {
            eventBus.stepDone(userId, runId, "DETECTING_CHANGES", "No changes")
            listOf("BILLING_CHECK", "TRANSLATING", "CREATING_PR").forEach {
                eventBus.stepSkipped(userId, runId, it)
            }
            eventBus.finishRun(userId, runId)
            log.info("No new/modified strings in {} — skipping", payload.commitHash.take(7))
            return
        }
        eventBus.stepDone(userId, runId, "DETECTING_CHANGES",
            "${addedStrings.size} string${if (addedStrings.size != 1) "s" else ""} changed")
        log.info("{} new/modified string(s) × {} language(s)", addedStrings.size, config.targets.size)

        // ── Step: Billing check ────────────────────────────────────────────────
        eventBus.stepRunning(userId, runId, "BILLING_CHECK")
        try {
            val projectCount = projectRepository.countForUser(project.ownerId)
            billingService.checkAndEnforceLimits(project.ownerId, addedStrings.size * config.targets.size, projectCount)
        } catch (e: Exception) {
            val friendlyMsg = e.message?.let { humanizeBillingError(it) } ?: "Plan limit reached"
            eventBus.stepError(userId, runId, "BILLING_CHECK", friendlyMsg)
            eventBus.finishRun(userId, runId, error = friendlyMsg)
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
                    prBody = buildPrBody(addedStrings.size, config.targets.size),
                    token = githubToken
                )
            } catch (e: Exception) {
                eventBus.stepError(userId, runId, "CREATING_PR", e.message)
                eventBus.finishRun(userId, runId, error = "PR creation failed: ${e.message}")
                log.warn("PR creation failed for {}: {}", payload.repositoryFullName, e.message)
                return
            }
            eventBus.stepDone(userId, runId, "CREATING_PR", pr.prUrl)
            eventBus.finishRun(userId, runId, prUrl = pr.prUrl)
            log.info("Translation PR created: {}", pr.prUrl)
        } else {
            eventBus.stepSkipped(userId, runId, "CREATING_PR", "No translatable strings approved")
            eventBus.finishRun(userId, runId)
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
        val approved = mutableMapOf<String, String>()
        var count = 0
        for ((key, sourceText) in addedStrings) {
            val context = TranslationContext(
                appId = payload.repositoryFullName, appName = config.app.name,
                category = config.app.category, tone = config.app.tone,
                glossary = config.glossary?.get(target.code),
                sourceText = sourceText, targetLanguage = target.name, targetRegion = target.region
            )
            try {
                val result = translationService.translateWithFlags(context)
                val status = if (result.flags.isNotEmpty()) "review" else "auto"
                val stringId = translationRepository.upsertString(project.id, key, sourceText)
                translationRepository.upsertTranslation(stringId, target.code, target.region, result.text, status)
                if (status == "auto") approved[key] = result.text
                else log.info("'{}' → {} flagged: {}", key, target.code, result.flags)
                count++
            } catch (e: Exception) {
                log.warn("Failed key='{}' lang={}: {}", key, target.code, e.message)
                val stringId = translationRepository.upsertString(project.id, key, sourceText)
                translationRepository.upsertTranslation(stringId, target.code, target.region, "", "blocked", e.message)
            }
        }
        return approved to count
    }

    private fun buildPrBody(stringCount: Int, languageCount: Int): String = """
        ## Transloom Auto-Translation

        | | |
        |---|---|
        | **Strings** | $stringCount |
        | **Languages** | $languageCount |

        > Flagged strings are in the review portal. A follow-up PR is created automatically on approval.

        *Generated by [Transloom](https://transloom.dev)*
    """.trimIndent()
}
