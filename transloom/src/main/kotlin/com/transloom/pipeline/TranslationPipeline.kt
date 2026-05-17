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
import com.transloom.services.StringParserService
import com.transloom.services.TranslationContext
import com.transloom.services.TranslationService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

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
    private val translationRepository: TranslationRepository
) {
    private val log = LoggerFactory.getLogger(TranslationPipeline::class.java)

    suspend fun processWebhookPayload(
        payload: WebhookPayload,
        project: Project,
        config: TransloomConfig,
        githubToken: String
    ) {
        log.info("Pipeline: repo={} branch={} commit={}", payload.repositoryFullName, payload.branchName, payload.commitHash.take(7))

        val sourceFilePath = config.source.files.values.firstOrNull()
        if (sourceFilePath == null) {
            log.warn("No source file configured for project={} — skipping pipeline", project.id)
            return
        }
        val sourceContent = gitHubService.fetchFileContent(
            repo = payload.repositoryFullName, branch = payload.commitHash,
            filePath = sourceFilePath, token = githubToken
        )

        val allSourceStrings = when {
            sourceFilePath.endsWith(".xml") -> StringParserService.parseAndroidXml(sourceContent)
            sourceFilePath.endsWith(".strings") -> StringParserService.parseIosStrings(sourceContent)
            else -> emptyMap()
        }

        val existingDbStrings = translationRepository.getStringKeysAndTexts(project.id)
        val addedStrings = allSourceStrings.filter { (key, text) -> existingDbStrings[key] != text }

        if (addedStrings.isEmpty()) {
            log.info("No new/modified strings in {} — skipping", payload.commitHash.take(7))
            return
        }
        log.info("{} new/modified string(s) × {} language(s)", addedStrings.size, config.targets.size)

        try {
            val projectCount = projectRepository.countForUser(project.ownerId)
            billingService.checkAndEnforceLimits(project.ownerId, addedStrings.size * config.targets.size, projectCount)
        } catch (e: Exception) {
            log.warn("Billing limit reached for {}: {}", payload.repositoryFullName, e.message)
            return
        }

        val updatedFiles = ConcurrentHashMap<String, String>()
        val translatedCounts = ConcurrentHashMap<String, Int>()

        coroutineScope {
            config.targets.map { target ->
                async {
                    val (approved, count) = processTarget(payload, project, config, target, addedStrings)
                    translatedCounts[target.code] = count
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

        if (updatedFiles.isNotEmpty()) {
            val pr = gitHubService.createBranchAndPr(
                repo = payload.repositoryFullName,
                baseBranch = payload.branchName,
                files = updatedFiles,
                commitMessage = "chore(i18n): auto-translate strings to ${config.targets.map { it.code }.joinToString()}",
                prTitle = "Transloom: Auto-Translations for ${payload.commitHash.take(7)}",
                prBody = buildPrBody(addedStrings.size, config.targets.size),
                token = githubToken
            )
            log.info("Translation PR created: {}", pr.prUrl)
        }

        val total = translatedCounts.values.sum()
        if (total > 0) billingService.recordUsage(project.ownerId, total)
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
